/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.vdc;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.emc.storageos.coordinator.client.model.*;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.security.geo.GeoClientCacheManager;
import com.emc.storageos.systemservices.impl.ipsec.IPsecManager;

import org.apache.commons.lang3.StringUtils;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.jsoup.helper.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.coordinator.client.service.NodeListener;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.exceptions.RetryableCoordinatorException;
import com.emc.storageos.db.client.util.VdcConfigUtil;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.ipsec.IPsecConfig;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.services.util.Exec;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.systemservices.exceptions.CoordinatorClientException;
import com.emc.storageos.systemservices.exceptions.InvalidLockOwnerException;
import com.emc.storageos.systemservices.impl.client.SysClientFactory;
import com.emc.storageos.systemservices.impl.upgrade.UpgradeManager;
import com.emc.storageos.systemservices.impl.util.AbstractManager;

import org.springframework.beans.factory.annotation.Autowired;


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

    private IPsecConfig ipsecConfig;
    @Autowired
    private IPsecManager ipsecMgr;
    @Autowired
    private UpgradeManager upgradeManager;
    @Autowired
    private AuditLogManager auditMgr;
    @Autowired
    DbClient dbClient;

    // local and target info properties
    private PropertyInfoExt localVdcPropInfo;
    private PropertyInfoExt targetVdcPropInfo;
    private PowerOffState targetPowerOffState;
    
    private static final String POWEROFFTOOL_COMMAND = "/etc/powerofftool";
    private static final String EVENT_SERVICE_TYPE = "DisasterRecovery";
    private static final String AUDIT_DR_OPERATION_LOCK = "auditdroperation";
    private static final int AUDIT_LOCK_WAIT_TIME_SEC = 5;
    
    // set to 2.5 minutes since it takes over 2m for ssh to timeout on non-reachable hosts
    private static final long SHUTDOWN_TIMEOUT_MILLIS = 150000;
    // Timeout in minutes for add/resume/data sync
    // If data synchronization takes long than this value, set site to error
    public static final int ADD_STANDBY_TIMEOUT_MILLIS = 20 * 60 * 1000; // 20 minutes
    public static final int PAUSE_STANDBY_TIMEOUT_MILLIS = 20 * 60 * 1000; // 20 minutes
    public static final int RESUME_STANDBY_TIMEOUT_MILLIS = 20 * 60 * 1000; // 20 minutes
    public static final int DATA_SYNC_TIMEOUT_MILLIS = 20 * 60 * 1000; // 20 minutes
    public static final int REMOVE_STANDBY_TIMEOUT_MILLIS = 20 * 60 * 1000; // 20 minutes
    public static final int SWITCHOVER_TIMEOUT_MILLIS = 20 * 60 * 1000; // 20 minutes
    private static final int BACK_UPGRADE_RETRY_MILLIS = 30 * 1000; // 30 seconds
    public static final int FAILOVER_STANDBY_SITE_TIMEOUT_MILLIS = 40 * 60 * 1000; // 40 minutes
    public static final int FAILOVER_ACTIVE_SITE_TIMEOUT_MILLIS = 40 * 60 * 1000; // 40 minutes
    
    private SiteInfo targetSiteInfo;
    private VdcConfigUtil vdcConfigUtil;
    private Map<String, VdcOpHandler> vdcOpHandlerMap;
    private Boolean backCompatPreYoda = false;

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
        // need to distinguish persistent locks acquired from UpgradeManager/VdcManager/PropertyManager
        // otherwise they might release locks acquired by others when they start
        final String svcId = String.format("%s,vdc", coordinator.getMySvcId());
        vdcConfigUtil = new VdcConfigUtil(coordinator.getCoordinatorClient());
        vdcConfigUtil.setBackCompatPreYoda(backCompatPreYoda);
        
        addSiteInfoListener();

        while (doRun) {
            log.debug("Main loop: Start");

            // Step0: check if we have the reboot lock
            boolean hasLock;
            try {
                hasLock = hasRebootLock(svcId);
            } catch (Exception e) {
                log.info("Step1: Failed to verify if the current node has the reboot lock ", e);
                retrySleep();
                continue;
            }

            if (hasLock) {
                try {
                    releaseRebootLock(svcId);
                    log.info("Step0: Released reboot lock for node: {}", svcId);
                    wakeupOtherNodes();
                } catch (InvalidLockOwnerException e) {
                    log.error("Step0: Failed to release the reboot lock: Not owner.");
                } catch (Exception e) {
                    log.info("Step0: Failed to release the reboot lock and will retry: {}", e.getMessage());
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
            
            // Step3: set site error state if on active
            try {
                updateSiteErrors();
            } catch (Exception e) {
                log.error("Step3: Failed to set site errors. {}", e);
            }
            
            // Step4: record DR operation audit log if on active
            try {
                auditCompletedDrOperation();
            } catch (RuntimeException e) {
                log.error("Step4: Failed to record DR operation audit log. {}", e);
            }

            // Step5: update vdc configuration if changed
            log.info("Step5: If VDC configuration is changed update");
            if (vdcPropertiesChanged()) {
                log.info("Step5: Current vdc properties are not same as target vdc properties. Updating.");
                log.debug("Current local vdc properties: " + localVdcPropInfo);
                log.debug("Target vdc properties: " + targetVdcPropInfo);

                try {
                    updateVdcProperties(svcId);
                } catch (Exception e) {
                    log.info("Step5: VDC properties update failed and will be retried:", e);
                    // Restart the loop immediately so that we release the upgrade lock.
                    continue;
                }
                continue;
            }
            
            // Step 6 : check backward compatible upgrade flag
            try {
                if (backCompatPreYoda) {
                    if (isGeoConfig() && !isLeadVdcForGeoUpgrade()) {
                        log.info("Skip pre-yoda upgrade handling for non lead vdc");
                    } else {
                        log.info("Check if pre-yoda upgrade is done");
                        checkPreYodaUpgrade();
                        continue;
                    }
                }
            } catch (Exception ex) {
                log.error("Step5: Failed to set back compat yoda upgrade. {}", ex);
                continue;
            }

            // Step7: sleep
            log.info("Step7: sleep");
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

        if (isGeoUpgradeFromPreYoda()) {
            log.info("Detect vdc properties from preyoda. Keep local vdc config properties unchanged until all vdc configs are migrated to zk");
            localVdcPropInfo.addProperty(VdcConfigUtil.VDC_CONFIG_VERSION,
                    String.valueOf(targetSiteInfo.getVdcConfigVersion()));
            localRepository.setVdcPropertyInfo(localVdcPropInfo);
        } else {
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

        // This ipsec_status and ipsec_key properties are not normal system properties,
        // as they need be protected by double barrier to make sure they be changed and
        // synced to all nodes at the SAME time, or else the quorum of zk and db will be
        // broken. This is why we don't put them in system property.
        targetVdcPropInfo.addProperty(Constants.IPSEC_STATUS,ipsecConfig.getIpsecStatus());
        targetVdcPropInfo.addProperty(Constants.IPSEC_KEY, ipsecConfig.getPreSharedKey());
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
        log.info("local vdc config version: {}, target vdc config version: {}", localVdcConfigVersion, targetVdcConfigVersion);
        return localVdcConfigVersion != targetVdcConfigVersion;
    }

    private boolean isGeoUpgradeFromPreYoda() {
        String vdcIds = localVdcPropInfo.getProperty(VdcConfigUtil.VDC_IDS);
        return !StringUtils.isEmpty(vdcIds) && vdcIds.contains(",") && StringUtils.isEmpty(localVdcPropInfo.getProperty(VdcConfigUtil.VDC_CONFIG_VERSION));
    }
    
    private boolean isGeoConfig() {
        return targetVdcPropInfo.getProperty(VdcConfigUtil.VDC_IDS).contains(",")
                || localVdcPropInfo.getProperty(VdcConfigUtil.VDC_IDS).contains(",");
    }
    
    /**
     * Update vdc properties and reboot the node if
     *
     * @param svcId node service id
     * @throws Exception
     */
    private void updateVdcProperties(String svcId) throws Exception {
        String action = targetSiteInfo.getActionRequired();
        
        log.info("Step5: Process vdc op handlers, action = {}", action);
        VdcOpHandler opHandler = getOpHandler(action);
        opHandler.setTargetSiteInfo(targetSiteInfo);
        opHandler.setTargetVdcPropInfo(targetVdcPropInfo);
        opHandler.setLocalVdcPropInfo(localVdcPropInfo);
        opHandler.execute();
        
        if (opHandler.isRollingRebootNeeded()) {
            log.info("Step5: Rolling reboot detected for vdc operation {}", action);
            rollingReboot(svcId); // keep same behaviour as previous releases. always do rolling reboot
        } else if (opHandler.isConcurrentRebootNeeded()) {
            log.info("Step5: Concurent reboot for operation handler {}", action);
            commitVdcConfigVersionToLocal();
            reboot();
        } else {
            commitVdcConfigVersionToLocal();
        }
    }
    
    private void commitVdcConfigVersionToLocal() {
        // Flush vdc properties includes VDC_CONFIG_VERSION to disk
        PropertyInfoExt vdcProperty = new PropertyInfoExt(targetVdcPropInfo.getAllProperties());
        vdcProperty.addProperty(VdcConfigUtil.VDC_CONFIG_VERSION, String.valueOf(targetSiteInfo.getVdcConfigVersion()));
        localRepository.setVdcPropertyInfo(vdcProperty);
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

    /**
     * Check if ongoing DR operation succeeded or failed, then record audit log accordingly and remove this operation record from ZK.
     */
    private void auditCompletedDrOperation() {
        if (!drUtil.isActiveSite()) {
            return;
        }
        InterProcessLock lock = coordinator.getCoordinatorClient().getSiteLocalLock(AUDIT_DR_OPERATION_LOCK);
        boolean hasLock = false;
        try {
            hasLock = lock.acquire(AUDIT_LOCK_WAIT_TIME_SEC, TimeUnit.SECONDS);
            if (!hasLock) {
                return;
            }
            log.info("Local site is active, local node acquired lock, starting audit complete DR operations ...");
            List<Configuration> configs = coordinator.getCoordinatorClient().queryAllConfiguration(DrOperationStatus.CONFIG_KIND);
            if (configs == null || configs.isEmpty()) {
                return;
            }
            for (Configuration config : configs) {
                DrOperationStatus operation = new DrOperationStatus(config);
                String siteId = operation.getSiteUuid();
                SiteState interState = operation.getSiteState();
                Site site = null;
                try {
                    site = drUtil.getSiteFromLocalVdc(siteId);
                } catch (RetryableCoordinatorException e) {
                    // It's expected that site id is not found if we're removing this site because it has been removed
                    // Under this situation, just record audit log and clear DR operation status
                    if (interState.equals(SiteState.STANDBY_REMOVING) &&e.getServiceCode() == ServiceCode.COORDINATOR_SITE_NOT_FOUND) {
                        this.auditMgr.recordAuditLog(null, null, EVENT_SERVICE_TYPE, getOperationType(interState), System.currentTimeMillis(),
                                AuditLogManager.AUDITLOG_SUCCESS, AuditLogManager.AUDITOP_END, siteId);
                        coordinator.getCoordinatorClient().removeServiceConfiguration(config);
                        log.info("DR operation status has been cleared: {}", operation);
                        continue;
                    }
                    throw e;
                }
                SiteState currentState = site.getState();
                if (currentState.equals(SiteState.STANDBY_ERROR)) {
                    // Failed
                    this.auditMgr.recordAuditLog(null, null, EVENT_SERVICE_TYPE, getOperationType(interState), System.currentTimeMillis(),
                            AuditLogManager.AUDITLOG_FAILURE, AuditLogManager.AUDITOP_END, site.toBriefString());
                } else if (!currentState.isDROperationOngoing()) {
                    // Succeeded
                    this.auditMgr.recordAuditLog(null, null, EVENT_SERVICE_TYPE, getOperationType(interState), System.currentTimeMillis(),
                            AuditLogManager.AUDITLOG_SUCCESS, AuditLogManager.AUDITOP_END, site.toBriefString());
                } else {
                    // Still ongoing, do nothing
                    continue;
                }
                log.info(String.format("Site %s state has transformed from %s to %s", siteId, interState, currentState));
                // clear this operation status
                coordinator.getCoordinatorClient().removeServiceConfiguration(config);
                log.info("DR operation status has been cleared: {}", operation);
            }
        } catch (Exception e) {
            log.error("Auditing DR operation failed with execption", e);
        } finally {
            try {
                if (hasLock) {
                    lock.release();
                }
            } catch (Exception e) {
                log.error("Failed to release DR operation audit lock", e);
            }
        }
    }

    private OperationTypeEnum getOperationType(SiteState state) {
        OperationTypeEnum operationType = null;
        switch(state) {
            case STANDBY_ADDING:
                operationType = OperationTypeEnum.ADD_STANDBY;
                break;
            case STANDBY_REMOVING:
                operationType = OperationTypeEnum.REMOVE_STANDBY;
                break;
            case STANDBY_PAUSING:
                operationType = OperationTypeEnum.PAUSE_STANDBY;
                break;
            case STANDBY_RESUMING:
                operationType = OperationTypeEnum.RESUME_STANDBY;
                break;
            case ACTIVE_SWITCHING_OVER:
                operationType = OperationTypeEnum.ACTIVE_SWITCHOVER;
                break;
            case STANDBY_SWITCHING_OVER:
                operationType = OperationTypeEnum.STANDBY_SWITCHOVER;
                break;
            case STANDBY_FAILING_OVER:
                operationType = OperationTypeEnum.STANDBY_FAILOVER;
                break;
        }
        return operationType;
    }
    
    // TODO - let's see if we can move it to VdcOpHandler later
    private void updateSiteErrors() {
        CoordinatorClient coordinatorClient = coordinator.getCoordinatorClient();

        if (!drUtil.isActiveSite()) {
            log.info("Step3: current site is a standby, nothing to do");
            return;
        }

        for(Site site : drUtil.listSites()) {
            SiteError error = getSiteError(site);
            if (error != null) {
                log.info("set site {} state to STANDBY_ERROR, set lastState to {}",site.getName(),site.getState());
                coordinatorClient.setTargetInfo(site.getUuid(), error);
                site.setState(SiteState.STANDBY_ERROR);
                coordinatorClient.persistServiceConfiguration(site.toConfiguration());
            }
        }
    }

    private SiteError getSiteError(Site site) {
        SiteError error = null;
        long lastSiteUpdateTime = site.getLastStateUpdateTime();
        long currentTime = System.currentTimeMillis();

        int drOpTimeoutMillis;
        switch(site.getState()) {
            case STANDBY_ADDING:
                drOpTimeoutMillis = drUtil.getDrIntConfig(DrUtil.KEY_ADD_STANDBY_TIMEOUT, ADD_STANDBY_TIMEOUT_MILLIS);
                if (currentTime - lastSiteUpdateTime > drOpTimeoutMillis) {
                    log.info("Step3: Site {} set to error due to add standby timeout", site.getName());
                    error = new SiteError(APIException.internalServerErrors.addStandbyFailedTimeout(
                            drOpTimeoutMillis / 60 / 1000),site.getState().name());
                }
                break;
            case STANDBY_PAUSING:
                drOpTimeoutMillis = drUtil.getDrIntConfig(DrUtil.KEY_PAUSE_STANDBY_TIMEOUT, PAUSE_STANDBY_TIMEOUT_MILLIS);
                if (currentTime - lastSiteUpdateTime > drOpTimeoutMillis) {
                    log.info("Step3: Site {} set to error due to pause standby timeout", site.getName());
                    error = new SiteError(APIException.internalServerErrors.pauseStandbyFailedTimeout(
                            drOpTimeoutMillis / 60 / 1000),site.getState().name());
                }
                break;
            case STANDBY_RESUMING:
                drOpTimeoutMillis = drUtil.getDrIntConfig(DrUtil.KEY_RESUME_STANDBY_TIMEOUT, RESUME_STANDBY_TIMEOUT_MILLIS);
                if (currentTime - lastSiteUpdateTime > drOpTimeoutMillis) {
                    log.info("Step3: Site {} set to error due to resume standby timeout", site.getName());
                    error = new SiteError(APIException.internalServerErrors.resumeStandbyFailedTimeout(
                            drOpTimeoutMillis / 60 / 1000),site.getState().name());
                }
                break;
            case STANDBY_REMOVING:
                drOpTimeoutMillis = drUtil.getDrIntConfig(DrUtil.KEY_REMOVE_STANDBY_TIMEOUT, REMOVE_STANDBY_TIMEOUT_MILLIS);
                if (currentTime - lastSiteUpdateTime > drOpTimeoutMillis) {
                    log.info("Step3: Site {} set to error due to remove standby timeout", site.getName());
                    error = new SiteError(APIException.internalServerErrors.removeStandbyFailedTimeout(
                            drOpTimeoutMillis / 60 / 1000),site.getState().name());
                }
                break;
            case ACTIVE_SWITCHING_OVER:
                drOpTimeoutMillis = drUtil.getDrIntConfig(DrUtil.KEY_SWITCHOVER_TIMEOUT, SWITCHOVER_TIMEOUT_MILLIS);
                if (currentTime - lastSiteUpdateTime > drOpTimeoutMillis) {
                    log.info("Step3: Site {} set to error due to switchover timeout", site.getName());
                    error = new SiteError(APIException.internalServerErrors.switchoverActiveFailedTimeout(
                            site.getName(), drOpTimeoutMillis / 60 / 1000),site.getState().name());
                }
                break;
            case STANDBY_SWITCHING_OVER:
                drOpTimeoutMillis = drUtil.getDrIntConfig(DrUtil.KEY_SWITCHOVER_TIMEOUT, SWITCHOVER_TIMEOUT_MILLIS);
                if (currentTime - lastSiteUpdateTime > drOpTimeoutMillis) {
                    log.info("Step3: Site {} set to error due to switchover timeout", site.getName());
                    error = new SiteError(APIException.internalServerErrors.switchoverStandbyFailedTimeout(
                            site.getName(), drOpTimeoutMillis / 60 / 1000),site.getState().name());
                }
                break;
            case STANDBY_FAILING_OVER:
                drOpTimeoutMillis = drUtil.getDrIntConfig(DrUtil.KEY_FAILOVER_STANDBY_SITE_TIMEOUT, FAILOVER_STANDBY_SITE_TIMEOUT_MILLIS);
                if (currentTime - lastSiteUpdateTime > drOpTimeoutMillis) {
                    log.info("Step3: Site {} set to error due to failover timeout", site.getName());
                    error = new SiteError(APIException.internalServerErrors.failoverFailedTimeout(
                            site.getName(), drOpTimeoutMillis / 60 / 1000),site.getState().name());
                }
                break;
            case ACTIVE_FAILING_OVER:
                drOpTimeoutMillis = drUtil.getDrIntConfig(DrUtil.KEY_FAILOVER_ACTIVE_SITE_TIMEOUT, FAILOVER_ACTIVE_SITE_TIMEOUT_MILLIS);
                if (currentTime - lastSiteUpdateTime > drOpTimeoutMillis) {
                    log.info("Step3: Site {} set to error due to failover timeout", site.getName());
                    error = new SiteError(APIException.internalServerErrors.failoverFailedTimeout(
                            site.getName(), drOpTimeoutMillis / 60 / 1000),site.getState().name());
                }
                break;
        }
        return error;
    }
    
    private void checkPreYodaUpgrade() throws Exception {
        if (!dbMigrationDone()) {
            log.info("Migration to yoda is not completed. Sleep and retry later. isMigrationDone flag = {}", coordinator.isDBMigrationDone());
            retrySleep();
            return;
        }
        if (!drUtil.isAllSitesStable()) {
            log.info("Current cluster is not stable. Skip and retry later");
            retrySleep();
            return;
        }
        if (isGeoConfig() && !allVdcGetUpgradedToYoda()) {
            log.info("Sleep and wait for all vdc upgraded to yoda.");
            retrySleep();
            return;
        }
        
        log.info("Db migration is done. Switch to IPSec mode");
        enableIpsec();
    }

    private void enableIpsec() throws Exception{
        InterProcessLock lock = null;
        try {
            lock = coordinator.getCoordinatorClient().getSiteLocalLock("ipseclock");
            lock.acquire();
            log.info("Acquired the lock {}", "ipseclock");
            
            String preSharedKey = ipsecConfig.getPreSharedKeyFromZK();
            if (StringUtil.isBlank(preSharedKey)) {
                log.info("No pre shared key in zk, generate a new key");
                ipsecMgr.rotateKey(true);
            } else {
                log.info("First ipsec key found in zk. No need to regenerate it");
            }
        } finally {
            lock.release();
        }
    }

    /**
     * We pick only one vdc assumes the role to rotate ipsec key in post yoda. As default the vdc with 
     * least vdc short id is the lead
     * 
     * @return true if current vdc is the lead 
     */
    private boolean isLeadVdcForGeoUpgrade() {
        String localId = drUtil.getLocalVdcShortId();
        String strVdcIds = targetVdcPropInfo.getProperty(VdcConfigUtil.VDC_IDS);
        String[] vdcIds = strVdcIds.split(",");

        for (String id : vdcIds) {
            if (localId.compareToIgnoreCase(id) > 0 ) {
                log.info("Current VDC {} is greater than {}.", localId, id);
                return false;
            }
        }

        log.info("Current VDC {} is the lead in current geo {}", localId, strVdcIds);
        return true;
    }

    private boolean dbMigrationDone() {
        String currentDbSchemaVersion = coordinator.getCurrentDbSchemaVersion();
        String targetDbSchemaVersion = coordinator.getCoordinatorClient().getTargetDbSchemaVersion();
        log.info("Current schema version {}", currentDbSchemaVersion);
        return targetDbSchemaVersion.equals(currentDbSchemaVersion) && coordinator.isDBMigrationDone();
    }

    private boolean allVdcGetUpgradedToYoda() {
        boolean toYOda = dbClient.checkGeoCompatible("2.5");
        log.info("If Geo DB is upgraded to Yoda: {}", toYOda);
        return toYOda;
    }
    
    private void rollingReboot(String svcId) {
        while (doRun) {
            log.info("Acquiring reboot lock for geo config change.");
            if (!getRebootLock(svcId)) {
                retrySleep();
            } else if (!isQuorumMaintained()) {
                releaseRebootLock(svcId);
                retrySleep();
            } else {
                commitVdcConfigVersionToLocal();
                reboot();
            }
        }
    }
}
