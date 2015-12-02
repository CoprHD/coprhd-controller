/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.vdc;

import java.net.URI;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.PowerOffState;
import com.emc.storageos.coordinator.client.model.PropertyInfoExt;
import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteError;
import com.emc.storageos.coordinator.client.model.SiteInfo;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.coordinator.client.service.NodeListener;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.util.VdcConfigUtil;
import com.emc.storageos.security.ipsec.IPsecConfig;
import com.emc.storageos.services.util.Exec;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.systemservices.exceptions.CoordinatorClientException;
import com.emc.storageos.systemservices.exceptions.InvalidLockOwnerException;
import com.emc.storageos.systemservices.impl.client.SysClientFactory;
import com.emc.storageos.systemservices.impl.util.AbstractManager;


/**
 * Manage configuration properties for multivdc and disaster recovery. It listens on
 * SiteInfo znode changes. Once getting notified, it fetch vdc config from local db
 * or standby sites config from zk and update vdcconfig.properties on local disk. Genconfig 
 * is supposed to be executed later to apply the new config changes.
 * 
 * Data revision change and simulatenous cluster poweroff are also managed here
 */
public class VdcManager extends AbstractManager {
    private static final Logger log = LoggerFactory.getLogger(VdcManager.class);

    private DbClient dbClient;
    private IPsecConfig ipsecConfig;

    // local and target info properties
    private PropertyInfoExt localVdcPropInfo;
    private PropertyInfoExt targetVdcPropInfo;
    private PowerOffState targetPowerOffState;
    
    private static final String POWEROFFTOOL_COMMAND = "/etc/powerofftool";
    
    // set to 2.5 minutes since it takes over 2m for ssh to timeout on non-reachable hosts
    private static final long SHUTDOWN_TIMEOUT_MILLIS = 150000;
    // Timeout in minutes for add/resume/data sync
    // If data synchronization takes long than this value, set site to error
    public static final int ADD_STANDBY_TIMEOUT_MILLIS = 20 * 60 * 1000; // 20 minutes
    public static final int PAUSE_STANDBY_TIMEOUT_MILLIS = 20 * 60 * 1000; // 20 minutes
    public static final int RESUME_STANDBY_TIMEOUT_MILLIS = 20 * 60 * 1000; // 20 minutes
    public static final int DATA_SYNC_TIMEOUT_MILLIS = 20 * 60 * 1000; // 20 minutes
    public static final int SWITCHOVER_TIMEOUT_MILLIS = 20 * 60 * 1000; // 20 minutes
    private static final int BACK_UPGRADE_RETRY_MILLIS = 30 * 1000; // 30 seconds
    
    private SiteInfo targetSiteInfo;
    private String currentSiteId;
    private DrUtil drUtil;
    private VdcConfigUtil vdcConfigUtil;
    private Map<String, VdcOpHandler> vdcOpHandlerMap;
    private Boolean backCompatPreYoda = false;
    
    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }
    
    public void setDrUtil(DrUtil drUtil) {
        this.drUtil = drUtil;
    }

    public void setIpsecConfig(IPsecConfig ipsecConfig) {
        this.ipsecConfig = ipsecConfig;
    }

    public void setVdcOpHandlerMap(Map<String, VdcOpHandler> vdcOpHandlerMap) {
        this.vdcOpHandlerMap = vdcOpHandlerMap;
    }

    public void setBackCompatPreYoda(Boolean backCompat) {
        backCompatPreYoda = backCompat;
    }
    
    @Override
    protected URI getWakeUpUrl() {
        return SysClientFactory.URI_WAKEUP_VDC_MANAGER;
    }

    /**
     * Register site info listener to monitor site changes
     */
    private void addSiteInfoListener() {
        try {
            coordinator.getCoordinatorClient().addNodeListener(new SiteInfoListener());
        } catch (Exception e) {
            log.error("Fail to add node listener for site info target znode", e);
            throw APIException.internalServerErrors.addListenerFailed();
        }
        log.info("Successfully added node listener for site info target znode");
    }

    /**
     * the listener class to listen the site target node change.
     */
    class SiteInfoListener implements NodeListener {
        public String getPath() {
            return String.format("/sites/%s/config/%s/%s", coordinator.getCoordinatorClient().getSiteId(), SiteInfo.CONFIG_KIND, SiteInfo.CONFIG_ID);
        }

        /**
         * called when user update the site
         */
        @Override
        public void nodeChanged() {
            log.info("Site info changed. Waking up the vdc manager...");
            wakeup();
        }

        /**
         * called when connection state changed.
         */
        @Override
        public void connectionStateChanged(State state) {
            log.info("Site info connection state changed to {}", state);
            if (state.equals(State.CONNECTED)) {
                log.info("Curator (re)connected. Waking up the vdc manager...");
                wakeup();
            }
        }
    }

    
    @Override
    protected void innerRun() {
        final String svcId = coordinator.getMySvcId();
        currentSiteId = coordinator.getCoordinatorClient().getSiteId();
        vdcConfigUtil = new VdcConfigUtil(coordinator.getCoordinatorClient());
        vdcConfigUtil.setBackCompatPreYoda(backCompatPreYoda);
        
        addSiteInfoListener();

        while (doRun) {
            log.debug("Main loop: Start");

            // Step0: check if we have the vdc lock
            boolean hasLock;
            try {
                hasLock = coordinator.hasPersistentLock(svcId, vdcLockId);
            } catch (Exception e) {
                log.info("Step1: Failed to verify if the current node has the vdc lock ", e);
                retrySleep();
                continue;
            }

            if (hasLock) {
                try {
                    coordinator.releasePersistentLock(svcId, vdcLockId);
                    log.info("Step0: Released vdc lock for node: {}", svcId);
                    wakeupOtherNodes();
                } catch (InvalidLockOwnerException e) {
                    log.error("Step0: Failed to release the vdc lock: Not owner.");
                } catch (Exception e) {
                    log.info("Step0: Failed to release the vdc lock and will retry: {}", e.getMessage());
                    retrySleep();
                    continue;
                }
            }

            // Step1: publish current state, and set target if empty
            try {
                initializeLocalAndTargetInfo();
            } catch (Exception e) {
                log.info("Step1b failed and will be retried:", e);
                retrySleep();
                continue;
            }
            
            // Step2: power off if all nodes agree.
            log.info("Step2: Power off if poweroff state != NONE. {}", targetPowerOffState);
            try {
                gracefulPoweroffCluster();
            } catch (Exception e) {
                log.error("Step2: Failed to poweroff. {}", e);
            }

            // Step3: update vdc configuration if changed
            log.info("Step3: If VDC configuration is changed update");
            if (vdcPropertiesChanged()) {
                log.info("Step3: Current vdc properties are not same as target vdc properties. Updating.");
                log.debug("Current local vdc properties: " + localVdcPropInfo);
                log.debug("Target vdc properties: " + targetVdcPropInfo);

                try {
                    updateVdcProperties(svcId);
                    //updateSwitchoverSiteState();
                    //updateFailoverSiteState();
                } catch (Exception e) {
                    log.info("Step3: VDC properties update failed and will be retried:", e);
                    // Restart the loop immediately so that we release the upgrade lock.
                    continue;
                }
                continue;
            }
            
            // Step4: set site error state if on acitve
            try {
                updateSiteErrors();
            } catch (RuntimeException e) {
                log.error("Step4: Failed to set site errors. {}", e);
                continue;
            }
            
            // Step 5 : check backward compatibile upgrade flag
            try {
                if (backCompatPreYoda) {
                    log.info("Check if pre-yoda upgrade is done");
                    checkPreYodaUpgrade();
                    continue;
                }
            } catch (Exception ex) {
                log.error("Step5: Failed to set back compat yoda upgrade. {}", ex);
                continue;
            }
            
            // Step6: sleep
            log.info("Step6: sleep");
            longSleep();
        }
    }

    /**
     * Initialize local and target info
     *
     * @throws Exception
     */
    private void initializeLocalAndTargetInfo() throws Exception {
        // set target if empty
        targetSiteInfo = coordinator.getTargetInfo(SiteInfo.class);
        if (targetSiteInfo == null) {
            targetSiteInfo = new SiteInfo();
            try {
                coordinator.setTargetInfo(targetSiteInfo, false);
                log.info("Step1b: Target site info set to: {}", targetSiteInfo);
            } catch (CoordinatorClientException e) {
                log.info("Step1b: Wait another control node to set target");
                retrySleep();
                throw e;
            }
        }

        // Initialize vdc prop info
        localVdcPropInfo = localRepository.getVdcPropertyInfo();
        // ipsec key is a vdc property as well and saved in ZK.
        // targetVdcPropInfo = loadVdcConfigFromDatabase();
        targetVdcPropInfo = loadVdcConfig();

        if (localVdcPropInfo.getProperty(VdcConfigUtil.VDC_CONFIG_VERSION) == null) {
            localVdcPropInfo = new PropertyInfoExt(targetVdcPropInfo.getAllProperties());
            localVdcPropInfo.addProperty(VdcConfigUtil.VDC_CONFIG_VERSION,
                    String.valueOf(targetSiteInfo.getVdcConfigVersion()));
            localRepository.setVdcPropertyInfo(localVdcPropInfo);

            String vdc_ids = targetVdcPropInfo.getProperty(VdcConfigUtil.VDC_IDS);
            String[] vdcIds = vdc_ids.split(",");
            if (vdcIds.length > 1) {
                log.info("More than one Vdc, rebooting");
                reboot();
            }
        }
        
        targetPowerOffState = coordinator.getTargetInfo(PowerOffState.class);
        if (targetPowerOffState == null) {
            // only control node can set target
            try {
                // Set the updated propperty info in coordinator
                coordinator.setTargetInfo(new PowerOffState(PowerOffState.State.NONE));
                targetPowerOffState = coordinator.getTargetInfo(PowerOffState.class);
                log.info("Step1b: Target poweroff state set to: {}", PowerOffState.State.NONE);
            } catch (CoordinatorClientException e) {
                log.info("Step1b: Wait another control node to set target");
                retrySleep();
                throw e;
            }
        }

    }

    /**
     * Load the vdc configurations
     * @return
     * @throws Exception
     */
    private PropertyInfoExt loadVdcConfig() throws Exception {
        targetVdcPropInfo = new PropertyInfoExt(vdcConfigUtil.genVdcProperties());
        targetVdcPropInfo.addProperty("ipsec_key", ipsecConfig.getPreSharedKey());
        return targetVdcPropInfo;
    }

    /**
     * Check if VDC configuration is different in the database vs. what is stored locally
     *
     * @return
     */
    private boolean vdcPropertiesChanged() {
        long localVdcConfigVersion = localVdcPropInfo.getProperty(VdcConfigUtil.VDC_CONFIG_VERSION) == null ? 0 :
                Long.parseLong(localVdcPropInfo.getProperty(VdcConfigUtil.VDC_CONFIG_VERSION));
        long targetVdcConfigVersion = targetSiteInfo.getVdcConfigVersion();

        return localVdcConfigVersion != targetVdcConfigVersion;
    }

    /**
     * Update vdc properties and reboot the node if
     *
     * @param svcId node service id
     * @throws Exception
     */
    private void updateVdcProperties(String svcId) throws Exception {
        // If the change is being done to create a multi VDC configuration or to reduce to a
        // multi VDC configuration a reboot is needed. If only operating on a single VDC
        // do not reboot the nodes.
        String action = targetSiteInfo.getActionRequired();
        if (targetVdcPropInfo.getProperty(VdcConfigUtil.VDC_IDS).contains(",")
                || localVdcPropInfo.getProperty(VdcConfigUtil.VDC_IDS).contains(",")) {
            log.info("Step3: Acquiring vdc lock for vdc properties change.");
            if (!getVdcLock(svcId)) {
                retrySleep();
            } else if (!isQuorumMaintained()) {
                try {
                    coordinator.releasePersistentLock(svcId, vdcLockId);
                } catch (Exception e) {
                    log.error("Failed to release the vdc lock:", e);
                }
                retrySleep();
            } else {
                log.info("Step3: Setting vdc properties and reboot");
                localRepository.setVdcPropertyInfo(targetVdcPropInfo);
                reboot();
            }
            return;
        }

        log.info("Step3: Setting vdc properties not rebooting for single VDC change, action = {}", action);
        VdcOpHandler opHandler = getOpHandler(action);
        opHandler.setTargetSiteInfo(targetSiteInfo);
        opHandler.setTargetVdcPropInfo(targetVdcPropInfo);
        opHandler.execute();
    }
    
    /**
     * Create an operation handler for current vdc config change
     * 
     * @param action
     * @return
     */
    private VdcOpHandler getOpHandler(String action) {
        VdcOpHandler opHandler = vdcOpHandlerMap.get(action);
        if (opHandler == null) {
            throw new IllegalStateException(String.format("No VdcOpHandler defined for action %s" , action));
        }
        return opHandler;
    }
    
    
    /**
     * Try to acquire the vdc lock, like upgrade lock, this also requires rolling reboot
     * so upgrade lock should be acquired at the same time
     *
     * @param svcId
     * @return
     */
    private boolean getVdcLock(String svcId) {
        if (!coordinator.getPersistentLock(svcId, vdcLockId)) {
            log.info("Acquiring vdc lock failed. Retrying...");
            return false;
        }

        if (!coordinator.getPersistentLock(svcId, upgradeLockId)) {
            log.info("Acquiring upgrade lock failed. Retrying...");
            return false;
        }

        // release the upgrade lock
        try {
            coordinator.releasePersistentLock(svcId, upgradeLockId);
        } catch (Exception e) {
            log.error("Failed to release the upgrade lock:", e);
        }
        log.info("Successfully acquired the vdc lock.");
        return true;
    }

    /**
     * If target poweroff state is not NONE, that means user has set it to STARTED.
     * in the checkAllNodesAgreeToPowerOff, all nodes, including control nodes and data nodes
     * will start to publish their poweroff state in the order of [NOTICED, ACKNOWLEDGED, POWEROFF].
     * Every node can publish the next state only if it sees the previous state are found on every other nodes.
     * By doing this, we can gaurantee that all nodes receive the acknowledgement of powering among each other,
     * we can then safely poweroff.
     * No matter the poweroff failed or not, at the end, we reset the target poweroff state back to NONE.
     * CTRL-11690: the new behavior is if an agreement cannot be reached, a best-effort attempt to poweroff the
     * remaining nodes will be made, as if the force parameter is provided.
     */
    private void gracefulPoweroffCluster() {
        if (targetPowerOffState != null && targetPowerOffState.getPowerOffState() != PowerOffState.State.NONE) {
            boolean forceSet = targetPowerOffState.getPowerOffState() == PowerOffState.State.FORCESTART;
            log.info("Step2: Trying to reach agreement with timeout on cluster poweroff");
            if (checkAllNodesAgreeToPowerOff(forceSet) && initiatePoweroff(forceSet)) {
                resetTargetPowerOffState();
                poweroffCluster();
            } else {
                log.warn("Step2: Failed to reach agreement among all the nodes. Proceed with best-effort poweroff");
                initiatePoweroff(true);
                resetTargetPowerOffState();
                poweroffCluster();
            }
        }
    }

    public void poweroffCluster() {
        log.info("powering off the cluster!");
        final String[] cmd = { POWEROFFTOOL_COMMAND };
        Exec.sudo(SHUTDOWN_TIMEOUT_MILLIS, cmd);
    }
    
    // TODO - let's see if we can move it to VdcOpHandler later
    private void updateSiteErrors() {
        CoordinatorClient coordinatorClient = coordinator.getCoordinatorClient();

        if (!drUtil.isActiveSite()) {
            log.info("Step5: current site is a standby, nothing to do");
            return;
        }

        for(Site site : drUtil.listSites()) {
            SiteError error = getSiteError(site);
            if (error != null) {
                coordinatorClient.setTargetInfo(site.getUuid(), error);

                site.setState(SiteState.STANDBY_ERROR);
                coordinatorClient.persistServiceConfiguration(site.getUuid(), site.toConfiguration());
            }
        }
    }

    private SiteError getSiteError(Site site) {
        SiteError error = null;
        SiteInfo targetSiteInfo = coordinator.getCoordinatorClient().getTargetInfo(site.getUuid(), SiteInfo.class);
        long lastSiteUpdateTime = targetSiteInfo.getVdcConfigVersion();
        long currentTime = System.currentTimeMillis();

        switch(site.getState()) {
            case STANDBY_ADDING:
                if (currentTime - lastSiteUpdateTime > ADD_STANDBY_TIMEOUT_MILLIS) {
                    log.info("Step5: Site {} set to error due to add standby timeout", site.getName());
                    error = new SiteError(APIException.internalServerErrors.addStandbyFailedTimeout(
                            ADD_STANDBY_TIMEOUT_MILLIS / 60 / 1000));
                }
                break;
            case STANDBY_PAUSING:
                if (currentTime - lastSiteUpdateTime > PAUSE_STANDBY_TIMEOUT_MILLIS) {
                    log.info("Step5: Site {} set to error due to pause standby timeout", site.getName());
                    error = new SiteError(APIException.internalServerErrors.pauseStandbyFailedTimeout(
                            PAUSE_STANDBY_TIMEOUT_MILLIS / 60 / 1000));
                }
                break;
            case STANDBY_RESUMING:
                if (currentTime - lastSiteUpdateTime > RESUME_STANDBY_TIMEOUT_MILLIS) {
                    log.info("Step5: Site {} set to error due to resume standby timeout", site.getName());
                    error = new SiteError(APIException.internalServerErrors.resumeStandbyFailedTimeout(
                            RESUME_STANDBY_TIMEOUT_MILLIS / 60 / 1000));
                }
                break;
            case STANDBY_SYNCING:
                if (currentTime - lastSiteUpdateTime > DATA_SYNC_TIMEOUT_MILLIS) {
                    log.info("Step5: Site {} set to error due to data sync timeout", site.getName());
                    error = new SiteError(APIException.internalServerErrors.dataSyncFailedTimeout(
                            DATA_SYNC_TIMEOUT_MILLIS / 60 / 1000));
                }
                break;
            case ACTIVE_SWITCHING_OVER:
                if (currentTime - lastSiteUpdateTime > SWITCHOVER_TIMEOUT_MILLIS) {
                    log.info("Step5: site {} set to error due to switchover timeout", site.getName());
                    error = new SiteError(APIException.internalServerErrors.switchoverActiveFailedTimeout(
                            site.getName(), DATA_SYNC_TIMEOUT_MILLIS / 60 / 1000));
                }
                break;
            case STANDBY_SWITCHING_OVER:
                if (currentTime - lastSiteUpdateTime > SWITCHOVER_TIMEOUT_MILLIS) {
                    log.info("Step5: site {} set to error due to switchover timeout", site.getName());
                    error = new SiteError(APIException.internalServerErrors.switchoverStandbyFailedTimeout(
                            site.getName(), DATA_SYNC_TIMEOUT_MILLIS / 60 / 1000));
                }
                break;
        }
        return error;
    }
    
    private void checkPreYodaUpgrade() throws Exception {
        String currentDbSchemaVersion = coordinator.getCurrentDbSchemaVersion();
        String targetDbSchemaVersion = coordinator.getCoordinatorClient().getTargetDbSchemaVersion();
        log.info("Current schema version {}", currentDbSchemaVersion);
        if (targetDbSchemaVersion.equals(currentDbSchemaVersion) && coordinator.isDBMigrationDone()) {
            log.info("Db migration is done. Switch to IPSec mode");
            vdcConfigUtil.setBackCompatPreYoda(false);
            targetVdcPropInfo = loadVdcConfig(); // refresh local vdc config
            VdcOpHandler opHandler = getOpHandler(SiteInfo.IPSEC_OP_ENABLE);
            opHandler.setTargetSiteInfo(targetSiteInfo);
            opHandler.setTargetVdcPropInfo(targetVdcPropInfo);
            opHandler.execute();
            backCompatPreYoda = false;
        } else {
            log.info("Migration to yoda is not completed. Sleep and retry later. isMigrationDone flag = {}", coordinator.isDBMigrationDone());
            sleep(BACK_UPGRADE_RETRY_MILLIS);
        }
    }
}
