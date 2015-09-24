/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.property;

import java.net.URI;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.DataRevision;
import com.emc.storageos.coordinator.client.model.PropertyInfoExt;
import com.emc.storageos.coordinator.client.model.SiteInfo;
import com.emc.storageos.coordinator.client.model.PowerOffState;
import com.emc.storageos.coordinator.client.service.NodeListener;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.util.VdcConfigUtil;
import com.emc.storageos.services.util.Exec;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.systemservices.exceptions.CoordinatorClientException;
import com.emc.storageos.systemservices.exceptions.InvalidLockOwnerException;
import com.emc.storageos.systemservices.impl.client.SysClientFactory;
import com.emc.storageos.systemservices.impl.util.AbstractManager;

public class VdcSiteManager extends AbstractManager {
    private static final Logger log = LoggerFactory.getLogger(VdcSiteManager.class);

    private static final String VDC_IDS_KEY = "vdc_ids";

    private DbClient dbClient;

    // local and target info properties
    private PropertyInfoExt localVdcPropInfo;
    private PropertyInfoExt targetVdcPropInfo;
    private SiteInfo targetVdcPropVersion;
    private DataRevision targetDataRevision;
    private PowerOffState targetPowerOffState;
    
    private static final String POWEROFFTOOL_COMMAND = "/etc/powerofftool";

    // set to 2.5 minutes since it takes over 2m for ssh to timeout on non-reachable hosts
    private static final long SHUTDOWN_TIMEOUT_MILLIS = 150000;
    
    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
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
            return String.format("/config/%s/%s", SiteInfo.CONFIG_KIND, SiteInfo.CONFIG_ID);
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

    /**
     * Register site info listener to monitor data revision
     */
    private void addDataRevisionListener() {
        try {
            coordinator.getCoordinatorClient().addNodeListener(new DataRevisionListener());
        } catch (Exception e) {
            log.error("Fail to add node listener for data revision target znode", e);
            throw APIException.internalServerErrors.addListenerFailed();
        }
        log.info("Successfully added node listener for data revision target znode");
    }

    /**
     * the listener class to listen on data revision change.
     */
    class DataRevisionListener implements NodeListener {
        public String getPath() {
            return String.format("/sites/%s/config/%s/%s", coordinator.getCoordinatorClient().getSiteId(), DataRevision.CONFIG_KIND, DataRevision.CONFIG_ID);
        }

        /**
         * called when user update the site
         */
        @Override
        public void nodeChanged() {
            log.info("Data revision changed. Waking up the vdc manager...");
            wakeup();
        }

        /**
         * called when connection state changed.
         */
        @Override
        public void connectionStateChanged(State state) {
            log.info("Data revision connection state changed to {}", state);
            if (state.equals(State.CONNECTED)) {
                log.info("Curator (re)connected. Waking up the vdc manager...");
                wakeup();
            }
        }
    }

    @Override
    protected void innerRun() {
        final String svcId = coordinator.getMySvcId();

        addSiteInfoListener();
        addDataRevisionListener();

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
                log.info("Step1b failed and will be retried: {}", e.getMessage());
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
            
            log.info("Step2: If VDC configuration is changed update");
            if (vdcPropertiesChanged()) {
                log.info("Step2: Current vdc properties are not same as target vdc properties. Updating.");
                log.debug("Current local vdc properties: " + localVdcPropInfo);
                log.debug("Target vdc properties: " + targetVdcPropInfo);

                try {
                    updateVdcProperties(svcId);
                } catch (Exception e) {
                    log.info("Step2: VDC properties update failed and will be retried: {}", e.getMessage());
                    // Restart the loop immediately so that we release the upgrade lock.
                    continue;
                }
                continue;
            }

            // Step3: change data revision
            log.info("Step3: check if target data revision is changed - {}", targetDataRevision);
            try {
                updateDataRevision();
            } catch (Exception e) {
                log.error("Step3: Failed to update data revision. {}", e);
            }
            
            // Step4: sleep
            log.info("Step4: sleep");
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
        targetVdcPropVersion = coordinator.getTargetInfo(SiteInfo.class);
        if (targetVdcPropVersion == null) {
            targetVdcPropVersion = new SiteInfo();
        }

        // Initialize vdc prop info
        localVdcPropInfo = localRepository.getVdcPropertyInfo();
        targetVdcPropInfo = loadVdcConfigFromDatabase();
        targetDataRevision = coordinator.getTargetInfo(DataRevision.class);
        if (localVdcPropInfo.getProperty(VdcConfigUtil.VDC_CONFIG_VERSION) == null) {
            localVdcPropInfo = new PropertyInfoExt(targetVdcPropInfo.getAllProperties());
            localVdcPropInfo.addProperty(VdcConfigUtil.VDC_CONFIG_VERSION,
                    String.valueOf(targetVdcPropVersion.getVersion()));
            localRepository.setVdcPropertyInfo(localVdcPropInfo);

            String vdc_ids = targetVdcPropInfo.getProperty(VDC_IDS_KEY);
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
     * Load the vdc vonfiguration from the database
     *
     * @return
     */
    private PropertyInfoExt loadVdcConfigFromDatabase() {
        VdcConfigUtil vdcConfigUtil = new VdcConfigUtil();
        vdcConfigUtil.setDbclient(dbClient);
        vdcConfigUtil.setCoordinator(coordinator.getCoordinatorClient());
        return new PropertyInfoExt((Map) vdcConfigUtil.genVdcProperties());
    }

    /**
     * Check if VDC configuration is different in the database vs. what is stored locally
     *
     * @return
     */
    private boolean vdcPropertiesChanged() {
        long localVdcConfigVersion = localVdcPropInfo.getProperty(VdcConfigUtil.VDC_CONFIG_VERSION) == null ? 0 :
                Long.parseLong(localVdcPropInfo.getProperty(VdcConfigUtil.VDC_CONFIG_VERSION));
        long targetVdcConfigVersion = targetVdcPropVersion.getVersion();

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
        if (targetVdcPropInfo.getProperty(VdcConfigUtil.VDC_IDS).contains(",")
                || localVdcPropInfo.getProperty(VdcConfigUtil.VDC_IDS).contains(",")) {
            log.info("Step2: Acquiring vdc lock for vdc properties change.");
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
                log.info("Step2: Setting vdc properties and rebooting for multi-vdc config change");
                localRepository.setVdcPropertyInfo(targetVdcPropInfo);
                reboot();
            }
        } else {
            log.info("Step2: Setting vdc properties not rebooting for single VDC change");
            PropertyInfoExt vdcProperty = new PropertyInfoExt(targetVdcPropInfo.getAllProperties());
            localRepository.setVdcPropertyInfo(vdcProperty);

            localRepository.reconfigProperties("coordinator");
            localRepository.restart("coordinatorsvc");

            log.info("Step2: Updating the hash code for local vdc properties");
            vdcProperty.addProperty(VdcConfigUtil.VDC_CONFIG_VERSION, String.valueOf(targetVdcPropVersion.getVersion()));
            localRepository.setVdcPropertyInfo(vdcProperty);
        }
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
    
    /**
     * Check if data revision is same as local one. If not, switch to target revision and reboot the whole cluster
     * simultaneously.
     * 
     * @throws Exception
     */
    private void updateDataRevision() throws Exception {
        DataRevision localRevision = localRepository.getDataRevision();
        log.info("Step3: local data revision is {}", localRevision);
        if (targetDataRevision != null && !targetDataRevision.equals(localRevision)) {
            localRepository.setDataRevision(targetDataRevision);
            log.info("Step3: Trying to reach agreement with timeout on cluster poweroff");
            if (checkAllNodesAgreeToPowerOff(false) && initiatePoweroff(false)) {
                log.info("Step3: Reach agreement on cluster poweroff");
                resetTargetPowerOffState();
                reboot();
            } else {
                log.warn("Step3: Failed to reach agreement among all the nodes. Delay data revision change until next run");
                publishNodePowerOffState(PowerOffState.State.NONE);
                resetTargetPowerOffState();
                localRepository.setDataRevision(localRevision);
            }
        }
    }    

    public void poweroffCluster() {
        log.info("powering off the cluster!");
        final String[] cmd = { POWEROFFTOOL_COMMAND };
        Exec.sudo(SHUTDOWN_TIMEOUT_MILLIS, cmd);
    }

}
