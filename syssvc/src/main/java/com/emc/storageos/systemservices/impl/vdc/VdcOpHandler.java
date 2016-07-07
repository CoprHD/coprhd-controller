/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.vdc;

import static com.emc.storageos.coordinator.client.model.Constants.KEY_DATA_REVISION;
import static com.emc.storageos.coordinator.client.model.Constants.KEY_DATA_REVISION_COMMITTED;
import static com.emc.storageos.coordinator.client.model.Constants.KEY_PREV_DATA_REVISION;
import static com.emc.storageos.coordinator.client.model.Constants.KEY_VDC_CONFIG_VERSION;
import static com.emc.storageos.services.util.FileUtils.readValueFromFile;

import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.apache.curator.framework.recipes.barriers.DistributedBarrier;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.DrOperationStatus.InterState;
import com.emc.storageos.coordinator.client.model.PropertyInfoExt;
import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteError;
import com.emc.storageos.coordinator.client.model.SiteInfo;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.client.service.DistributedDoubleBarrier;
import com.emc.storageos.coordinator.client.service.DrPostFailoverHandler.Factory;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.coordinator.common.impl.ZkPath;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.util.VdcConfigUtil;
import com.emc.storageos.management.jmx.recovery.DbManagerOps;
import com.emc.storageos.services.util.Waiter;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.emc.storageos.systemservices.exceptions.CoordinatorClientException;
import com.emc.storageos.systemservices.impl.client.SysClientFactory;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.storageos.systemservices.impl.upgrade.LocalRepository;
import com.emc.storageos.systemservices.impl.util.MailHandler;

/**
 * Operation handler for vdc config change. A vdc config change may represent 
 * cluster topology change in DR or GEO ensemble. Any extra actions that take place need be 
 * encapsulated within a VdcOpHandler instance
 */
public abstract class VdcOpHandler {
    private static final Logger log = LoggerFactory.getLogger(VdcOpHandler.class);
    
    // 6 mins for barrier timeout. it should be longer than loopInterval
    private static final int VDC_OP_BARRIER_TIMEOUT = 360; 
    private static final int SWITCHOVER_ZK_WRITALE_WAIT_INTERVAL = 1000 * 5;
    private static final int FAILOVER_ZK_WRITALE_WAIT_INTERVAL = 1000 * 15;
    private static final int MAX_ZK_WAIT_RETRY_TIMES = 12;
    
    private static final int MAX_PAUSE_RETRY = 20;
    private static final int IPSEC_RESTART_DELAY = 1000 * 60; // 1 min
    
    private static final String URI_INTERNAL_POWEROFF = "/control/internal/cluster/poweroff";
    private static final String LOCK_REMOVE_STANDBY="drRemoveStandbyLock";
    private static final String LOCK_FAILOVER_REMOVE_OLD_ACTIVE="drFailoverRemoveOldActiveLock";
    private static final String LOCK_PAUSE_STANDBY="drPauseStandbyLock";
    private static final String LOCK_DEGRADE_STANDBY="drDegradeStandbyLock";
    private static final String NTPSERVERS = "network_ntpservers";

    protected CoordinatorClientExt coordinator;
    protected LocalRepository localRepository;
    protected DrUtil drUtil;
    protected DbClient dbClient;
    protected Service service;
    protected final Waiter waiter = new Waiter();
    protected PropertyInfoExt targetVdcPropInfo;
    protected PropertyInfoExt localVdcPropInfo;
    protected SiteInfo targetSiteInfo;
    private boolean concurrentRebootNeeded = false;
    private boolean rollingRebootNeeded = false;
    private VdcPropertyBarrier vdcPropBarrier; 
    
    public VdcOpHandler() {
    }
    
    public abstract void execute() throws Exception;
    
    public boolean isConcurrentRebootNeeded() {
        return concurrentRebootNeeded;
    }
    
    public void setConcurrentRebootNeeded(boolean rebootRequired) {
        this.concurrentRebootNeeded = rebootRequired;
    }

    public boolean isRollingRebootNeeded() {
        return rollingRebootNeeded;
    }

    public void setRollingRebootNeeded(boolean rollingRebootRequired) {
        this.rollingRebootNeeded = rollingRebootRequired;
    }


    /**
     * No-op - simply do nothing
     */
    public static class NoopOpHandler extends VdcOpHandler{
        public NoopOpHandler() {
        }
        
        @Override
        public void execute() {
            if (isGeoConfigChange()) {
                log.info("Geo config change detected. set rolling reboot to true");
                setRollingRebootNeeded(true);
            }
        }
    }
    
    /**
     * Rotate IPSec key
     */
    public static class IPSecRotateOpHandler extends VdcOpHandler {
        public IPSecRotateOpHandler() {
        }
        
        /**
         * Reconfig IPsec when vdc properties (key, IPs or both) get changed.
         * @throws Exception
         */
        @Override
        public void execute() throws Exception {
            String backCompatPreYoda = targetVdcPropInfo.getProperty(VdcConfigUtil.BACK_COMPAT_PREYODA);
            if (Boolean.valueOf(backCompatPreYoda)) {
                log.info("Set back_comp_preyoda flag to true so that ipsec can start");
                targetVdcPropInfo.addProperty(VdcConfigUtil.BACK_COMPAT_PREYODA, String.valueOf(false));
                setRollingRebootNeeded(true);
            }
            
            if (isGeoConfigChange()) {
                log.info("Geo config change detected. set concurrent reboot to true");
                setConcurrentRebootNeeded(true);
            }
            String ipsecKeyZk = targetVdcPropInfo.getProperty(Constants.IPSEC_KEY);
            String ipsecKeyLocal = localVdcPropInfo.getProperty(Constants.IPSEC_KEY);
            if (ipsecKeyLocal == null || !ipsecKeyLocal.equals(ipsecKeyZk)) {
                log.info("Local ipsec key doesn't match with new key in zk. Flush to local");
                syncFlushVdcConfigToLocal();
            }
            refreshIPsec();
        }
    }

    /**
     * Reconfigure the new redeployed nodes for node recovery in DR environment
     */
    public static class DrNodeRecoveryHandler extends VdcOpHandler {
        private String dbDir;
        private String geodbDir;

        public DrNodeRecoveryHandler() {
        }

        /**
         * Reconfigure(refresh firewall/ipsec/ssh/...) the recovering node
         * @throws Exception
         */
        @Override
        public void execute() throws Exception {
            if (isHibernating()) {
                log.info("Hibernate flag detected. Reconfigure to refresh local properties..");
                reconfigVdc(false);
            }
        }

        public void setDbDir(String dbDir) {
            this.dbDir = dbDir;
        }

        public void setGeodbDir(String geodbDir) {
            this.geodbDir = geodbDir;
        }

        private boolean isHibernating() {
            List<String> dbFolders = Arrays.asList(dbDir, geodbDir);
            for (String dbFolder : dbFolders) {
                String modeType = readStartupModeFromDisk(dbFolder);
                if (modeType != null && Constants.STARTUPMODE_HIBERNATE.equalsIgnoreCase(modeType)) {
                    return true;
                }
            }
            return false;
        }

        private String readStartupModeFromDisk(String folder) {
            String modeType = null;
            try {
                File startupModeFile = new File(folder, Constants.STARTUPMODE);
                modeType = readValueFromFile(startupModeFile, Constants.STARTUPMODE);
                log.info("On disk startup mode found {}", modeType);
            } catch (Exception e) {
                log.error("Failed to read startup mode file under {}", folder, e);
            }
            return modeType;
        }
    }

    /**
     * Geo config change - add/remove vdc 
     */
    public static class GeoConfigChangeOpHandler extends VdcOpHandler {
        public GeoConfigChangeOpHandler() {
        }
        
        /**
         * Reconfig IPsec/firewall when vdc properties (key, IPs or both) get changed. 
         * Rolling restart the cluster if it is remove-vdc
         * 
         * @throws Exception
         */
        @Override
        public void execute() throws Exception {
            syncFlushVdcConfigToLocal();
            refreshIPsec();
            refreshFirewall();
            
            if (isRemovingVdc()) {
                setRollingRebootNeeded(true);
            }
        }
        
        private boolean isRemovingVdc() {
            String newVdcIds =  targetVdcPropInfo.getProperty(VdcConfigUtil.VDC_IDS);
            String origVdcIds = localVdcPropInfo.getProperty(VdcConfigUtil.VDC_IDS);
            return newVdcIds.length() < origVdcIds.length();
        }
    }
    
    /**
     * Process DR config change for add-standby op on all existing sites
     *  - flush vdc config to disk, regenerate config files and reload services for ipsec, firewall, coordinator, db
     */
    public static class DrAddStandbyHandler extends VdcOpHandler {
        public DrAddStandbyHandler() {
        }
        
        @Override
        public void execute() throws Exception {
            reconfigVdc();
        }
    }

    /**
     * Process DR config change for add-standby on newly added site, or resume-standby. 
     *   flush ntp config to local properties, increase data revision and reboot. After reboot, it sync db/zk data from active sites during db/zk startup
     *   We don't flush vdc properties to local until data revision is changed successfully. The reason is - we don't want to change anything at local
     *   if data revision change fails. 
     *   
     * A data revision indicates a point-in-time copy of db, geodb and zk on all nodes in a cluster. Data revision is persisted at a local property file 
     * at /.volumes/bootfs/etc/datarevision.properties.  It contains the following properties -
     *   vdc_config_version=1467062122040              Config version which is triggering PIT copy change 
     *   target_data_revision= 1467062122040           Major version of this PIT copy. See /etc/datatool to know what is major/minor version of a PIT copy
     *   target_data_revision=true|false               For 2 phase commit. True for successful commit. False means inconsistent commit.  
     *   previous_data_revision=1467062122039          Previous data revision. We keep previous data revision for rollback if PIT switch fails
     *   
     * Genconfig uses those properties to prepare data directories(db, geodb, zk).   
     */
    public static class DrChangeDataRevisionHandler extends VdcOpHandler {
        private DistributedDoubleBarrier barrier;

        public DrChangeDataRevisionHandler() {
        }

        @Override
        public void execute() throws Exception {
            if (barrier == null) {
                String barrierPath = String.format("%s/%s/DataRevisionBarrier", ZkPath.SITES, coordinator.getCoordinatorClient().getSiteId());
                barrier = coordinator.getCoordinatorClient().getDistributedDoubleBarrier(barrierPath, coordinator.getNodeCount());
            }

            flushNtpConfigToLocal();
            checkDataRevision();
        }
        
        private void checkDataRevision() throws Exception {
            try {
                long targetDataRevision = Long.parseLong(targetSiteInfo.getTargetDataRevision());
                long targetVdcConfigVersion = targetSiteInfo.getVdcConfigVersion();
                log.info("Check if target data revision is changed - data revision {}, vdc config version {}", targetDataRevision, targetVdcConfigVersion);
                
                long localVdcConfigVersion = 0;
                PropertyInfoExt localRevisionProps = localRepository.getDataRevisionPropertyInfo();
                String committed = localRevisionProps.getProperty(Constants.KEY_DATA_REVISION_COMMITTED);
                if (committed != null && Boolean.valueOf(committed)) {
                    String configVersion = localRevisionProps.getProperty(Constants.KEY_VDC_CONFIG_VERSION);
                    localVdcConfigVersion = Long.parseLong(configVersion);
                }
                String localRevision = localRevisionProps.getProperty(Constants.KEY_DATA_REVISION);
                log.info("Local data revision is {}, vdc config version is {}", localRevision, localVdcConfigVersion);
                
                if (localVdcConfigVersion != targetVdcConfigVersion) {
                    if (localVdcConfigVersion > targetVdcConfigVersion) { 
                        processRollback(localRevisionProps);
                    } else {
                        // Go ahead to apply the change if data revision increases
                        updateDataRevision(localRevisionProps);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to update data revision. {}", e);
                throw e;
            }
        }
 
        private void processRollback(PropertyInfoExt localRevisionProps) throws Exception{
            log.info("Local vdc config version is greater than target revision. Local db revision was rollbacked due to error in middle of previous data syncing");
            if (!coordinator.isStandby()) {
                return; 
            }

            Site localSite = drUtil.getLocalSite();
            // ZK leader node available at local site - means no connectivity to
            // active site
            if (coordinator.isZKLeaderAlive(localSite)) {
                log.info("No ZK connectivity to active site. Skip the data revision change and keep current revision until connected back to active");
                if (localSite.getState() == SiteState.STANDBY_RESUMING) {
                    localSite.setState(SiteState.STANDBY_PAUSED);
                    coordinator.getCoordinatorClient().persistServiceConfiguration(localSite.toConfiguration());
                    log.info("Change local site state from RESUMING to PAUSED");
                }
            } 
        }
       
        /**
         * Switch to target revision and reboot the whole cluster simultaneously.
         * 
         * The data revision switch is implemented as 2-phase commit protocol to ensure no partial commit
         * 
         * @throws Exception
         */
        private void updateDataRevision(PropertyInfoExt localRevisionProps) throws Exception {
            String targetDataRevision = targetSiteInfo.getTargetDataRevision();
            long vdcConfigVersion = targetSiteInfo.getVdcConfigVersion();
            
            PropertyInfoExt newPropInfo = new PropertyInfoExt(localRevisionProps.getAllProperties()); // clone to new properties
            newPropInfo.addProperty(KEY_DATA_REVISION, targetDataRevision == null ? "" : targetDataRevision);
            newPropInfo.addProperty(KEY_VDC_CONFIG_VERSION, String.valueOf(vdcConfigVersion));
            
            // if target data revision is same as previous vdc config version at local, it means we are doing data revision rollback
            if (StringUtils.equals(String.valueOf(targetDataRevision), localRevisionProps.getProperty(KEY_PREV_DATA_REVISION))) {
                // no need to save data revision pointer any more.
                log.info("Rollbacking to previous data revision {}. Skip previous data revision pointer", targetDataRevision);
                newPropInfo.removeProperty(KEY_PREV_DATA_REVISION);
            } else {
                // as default - save previous data revision pointer to local, so that we may rollback later
                String prevDataRevision = localRevisionProps.getProperty(KEY_DATA_REVISION);
                if (StringUtils.isNotEmpty(prevDataRevision)) {
                    newPropInfo.addProperty(KEY_PREV_DATA_REVISION, prevDataRevision);
                }
            }
            
            log.info("Trying to reach agreement with timeout for data revision change");
            String barrierPath = String.format("%s/%s/DataRevisionBarrier", ZkPath.SITES, coordinator.getCoordinatorClient().getSiteId());
            DistributedDoubleBarrier barrier = coordinator.getCoordinatorClient().getDistributedDoubleBarrier(barrierPath, coordinator.getNodeCount());
            try {
                boolean phase1Agreed = barrier.enter(VDC_OP_BARRIER_TIMEOUT, TimeUnit.SECONDS);
                if (phase1Agreed) {
                    // reach phase 1 agreement, we can start write to local property file
                    log.info("Reach phase 1 agreement for data revision change");
                    newPropInfo.addProperty(KEY_DATA_REVISION_COMMITTED, String.valueOf(Boolean.FALSE)); // flush to local disk but no gurantee on consistency with other nodes
                    localRepository.setDataRevisionPropertyInfo(newPropInfo);
                    boolean phase2Agreed = barrier.leave(VDC_OP_BARRIER_TIMEOUT, TimeUnit.SECONDS);
                    if (phase2Agreed) {
                        // phase 2 agreement is received, we can make sure data revision change is written to local property file
                        log.info("Reach phase 2 agreement for data revision change");
                        newPropInfo.addProperty(KEY_DATA_REVISION_COMMITTED, String.valueOf(Boolean.TRUE));
                        localRepository.setDataRevisionPropertyInfo(newPropInfo); // committed to local disk
                        setConcurrentRebootNeeded(true);
                    } else {
                        log.info("Failed to reach phase 2 agreement. Rollback revision change");
                        localRepository.setDataRevisionPropertyInfo(localRevisionProps);
                        throw new IllegalStateException("Failed to reach phase 2 agreement on data revision change");
                    }
                } else {
                    log.warn("Failed to reach agreement among all the nodes. Delay data revision change until next run");
                    throw new IllegalStateException("Failed to reach phase 1 agreement on data revision change");
                }
            } catch (Exception ex) {
                log.warn("Internal error happens when negotiating data revision change", ex);
                throw ex;
            }
        }

        /**
         * Flush NTP config to local disk, so NTP take effect after reboot
         */
        private void flushNtpConfigToLocal() {
            String ntpServers = coordinator.getTargetInfo(PropertyInfoExt.class).getProperty("network_ntpservers");
            if (ntpServers == null) {
                return;
            }
            PropertyInfoExt localProps = localRepository.getOverrideProperties();
            localProps.addProperty(NTPSERVERS, ntpServers);
            localRepository.setOverrideProperties(localProps);
        }
    }

    /**
     * Purge old data revisions on given site
     *  - call /etc/systool --purge-data-revision to cleanup unused data revisions on local disk
     */
    public static class DrPurgeDataRevisionHandler extends VdcOpHandler {
        public DrPurgeDataRevisionHandler() {
        }
        
        @Override
        public void execute() throws Exception {
            SiteInfo siteInfo = coordinator.getCoordinatorClient().getTargetInfo(SiteInfo.class);
            String purgeSiteId = siteInfo.getSourceSiteUUID();
            log.info("Purging data revision should be done on site {}", purgeSiteId);
            if (drUtil.getLocalSite().getUuid().equals(purgeSiteId)) {
                // purge data directories for unused versions
                localRepository.purgeDataRevision();
                
                // purge previous revision from local data revision properties
                PropertyInfoExt localRevisionProps = localRepository.getDataRevisionPropertyInfo();
                localRevisionProps.removeProperty(Constants.KEY_PREV_DATA_REVISION);
                localRepository.setDataRevisionPropertyInfo(localRevisionProps);
            }
        }
    }
    
    /**
     * Process DR config change for remove-standby op
     *  - active site: power off to-be-removed standby, remove the db nodes from 
     *                 gossip/strategy options, reconfig/restart ipsec/firewall/coordinator
     *  - other standbys: wait for site removed from zk, reconfig/restart ipsec/firewall/coordinator 
     *  - to-be-removed standby - do nothing, go ahead to reboot
     */
    public static class DrRemoveStandbyHandler extends VdcOpHandler {
        private static final int MAX_WAIT_TIME_IN_MIN = 30;

        public DrRemoveStandbyHandler() {
        }
        
        @Override
        public void execute() throws Exception {
            log.info("Processing standby removal");
            if (drUtil.isActiveSite()) {

                log.info("Standby removal op - try to power off all removed sites");
                poweroffRemovedSites();

                log.info("Standby removal op - reconfig all services");
                reconfigVdc(false);

                log.info("Active site - start removing db nodes from gossip and strategy options");
                removeDbNodes();
            } else {
                log.info("Standby site - waiting for completion of db removal from active site");
                Site localSite = drUtil.getLocalSite();
                if (localSite.getState().equals(SiteState.STANDBY_REMOVING)) {
                    log.info("Current standby site is removed from DR. You can power it on and promote it as active later");
                    // cleanup site error 
                    SiteError siteError = coordinator.getCoordinatorClient().getTargetInfo(localSite.getUuid(), SiteError.class);
                    if (siteError != null) {
                        siteError.cleanup();
                        coordinator.getCoordinatorClient().setTargetInfo(localSite.getUuid(), siteError);
                    }
                    return;
                } else {
                    log.info("Standby removal op - reconfig all services");
                    reconfigVdc(false);

                    long start = System.currentTimeMillis();
                    log.info("Waiting for completion of site removal from active site");
                    while (drUtil.hasSiteInState(SiteState.STANDBY_REMOVING) && drUtil.getLocalSite().getState() != SiteState.STANDBY_PAUSED) {
                        if (System.currentTimeMillis() - start > MAX_WAIT_TIME_IN_MIN * 60 * 1000) {
                            throw new IllegalStateException("Timeout reached when wait for site to be removed"); 
                        }
                        
                        log.info("Waiting for completion of site removal from active site");
                        retrySleep();
                    }
                }
            }
            // refresh ssh to exclude removed nodes from ssh config files
            refreshSsh();
        }
        
        private void removeDbNodes() throws Exception {
            InterProcessLock lock = coordinator.getCoordinatorClient().getSiteLocalLock(LOCK_REMOVE_STANDBY);
            while (drUtil.hasSiteInState(SiteState.STANDBY_REMOVING)) {
                log.info("Acquiring lock {}", LOCK_REMOVE_STANDBY); 
                lock.acquire();
                log.info("Acquired lock {}", LOCK_REMOVE_STANDBY); 
                List<Site> toBeRemovedSites = drUtil.listSitesInState(SiteState.STANDBY_REMOVING);
                try {

                    for (Site site : toBeRemovedSites) {
                        try {
                            removeDbNodesFromGossip(site);
                        } catch (Exception e) { 
                            populateStandbySiteErrorIfNecessary(site, APIException.internalServerErrors.removeStandbyReconfigFailed(e.getMessage()));
                            throw e;
                        }
                    }
                    for (Site site : toBeRemovedSites) {
                        try {
                            removeDbNodesFromStrategyOptions(site);
                            drUtil.removeSite(site);
                        } catch (Exception e) { 
                            populateStandbySiteErrorIfNecessary(site, APIException.internalServerErrors.removeStandbyReconfigFailed(e.getMessage()));
                            throw e;
                        }
                    }
                }  finally {
                    lock.release();
                    log.info("Release lock {}", LOCK_REMOVE_STANDBY);   
                }
            }
        }
        
    }


    /**
     * Process DR config change for pause-standby op
     *  - All existing sites - exclude paused site from vdc config and reconfig, remove db nodes of paused site 
     *  - To-be-paused site - nothing
     */
    public static class DrPauseStandbyHandler extends VdcOpHandler {
        
        public DrPauseStandbyHandler() {
        }
        
        @Override
        public void execute() throws Exception {
            SiteState localSiteState = drUtil.getLocalSite().getState();
            if (localSiteState.equals(SiteState.STANDBY_PAUSING) || localSiteState.equals(SiteState.STANDBY_PAUSED)) {
                checkAndPauseOnStandby();
                flushVdcConfigToLocal();
                refreshIPsec();
                refreshFirewall();
                localRepository.restart(Constants.GEODBSVC_NAME);
                localRepository.restart(Constants.DBSVC_NAME);
                // manually paused site should never reconfigure to observer until resumed
                coordinator.stopCoordinatorSvcMonitor();
            } else {
                reconfigVdc(false);
                checkAndPauseOnActive();
            }
        }
        
        /**
         * Update the strategy options and remove the paused site from gossip ring on the active site.
         * This should be done after the firewall has been updated to block the paused site so that it's not affected.
         */
        private void checkAndPauseOnActive() {
            // this should only be done on the active site
            if (drUtil.isStandby()) {
                return;
            }

            InterProcessLock lock = coordinator.getCoordinatorClient().getSiteLocalLock(LOCK_PAUSE_STANDBY);
            while (drUtil.hasSiteInState(SiteState.STANDBY_PAUSING)) {
                try {
                    log.info("Acquiring lock {}", LOCK_PAUSE_STANDBY);
                    lock.acquire();
                    log.info("Acquired lock {}", LOCK_PAUSE_STANDBY);

                    if (!drUtil.hasSiteInState(SiteState.STANDBY_PAUSING)) {
                        // someone else updated the status already
                        break;
                    }

                    for (Site site : drUtil.listSitesInState(SiteState.STANDBY_PAUSING)) {
                        try {
                            removeDbNodesFromGossip(site);
                        }  catch (Exception e) {
                            populateStandbySiteErrorIfNecessary(site,
                                    APIException.internalServerErrors.pauseStandbyReconfigFailed(e.getMessage()));
                            throw e;
                        }
                    }

                    for (Site site : drUtil.listSitesInState(SiteState.STANDBY_PAUSING)) {
                        try {
                            removeDbNodesFromStrategyOptions(site);
                            // update the status to STANDBY_PAUSED
                            site.setState(SiteState.STANDBY_PAUSED);
                            coordinator.getCoordinatorClient().persistServiceConfiguration(site.toConfiguration());
                        } catch (Exception e) {
                            populateStandbySiteErrorIfNecessary(site,
                                    APIException.internalServerErrors.pauseStandbyReconfigFailed(e.getMessage()));
                            throw e;
                        }
                    }
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                } finally {
                    try {
                        log.info("Releasing lock {}", LOCK_PAUSE_STANDBY);
                        lock.release();
                        log.info("Released lock {}", LOCK_PAUSE_STANDBY);
                    } catch (Exception e) {
                        log.error("Failed to release lock {}", LOCK_PAUSE_STANDBY);
                    }
                }
            }
        }

        /**
         * Update the site state from PAUSING to PAUSED on the standby site
         */
        private void checkAndPauseOnStandby() {
            // wait for the coordinator to be blocked on the active site
            int retryCnt = 0;
            Site activeSite = drUtil.getActiveSite();
            while (coordinator.isZKLeaderAlive(activeSite)) {
                if (++retryCnt > MAX_PAUSE_RETRY) {
                    throw new IllegalStateException("timeout waiting for coordinatorsvc to be blocked on active site.");
                }
                log.info("short sleep before checking active site status again");
                retrySleep();
            }

            String localZkMode = drUtil.getLocalCoordinatorMode();
            if (DrUtil.ZOOKEEPER_MODE_READONLY.equals(localZkMode)) {
                coordinator.reconfigZKToWritable();
            }
            
            localRepository.rebaseZkSnapshot();
            
            Site localSite = drUtil.getLocalSite();
            if (localSite.getState() == SiteState.STANDBY_PAUSING && drUtil.isLeaderNode(localZkMode)) {
                localSite.setState(SiteState.STANDBY_PAUSED);
                log.info("Updating local site state to STANDBY_PAUSED");
                coordinator.getCoordinatorClient().persistServiceConfiguration(localSite.toConfiguration());
                // start checking network states of other DR sites for potential failover requests.
                coordinator.rescheduleDrSiteNetworkMonitor();

                for (Site standby : drUtil.listStandbySites()) {
                    if (standby.getState() == SiteState.STANDBY_PAUSING) {
                        // standby sites that are paused at the same time also block each other
                        standby.setState(SiteState.STANDBY_PAUSED);
                        log.info("Updating state of site {} to STANDBY_PAUSED", standby.getUuid());
                        coordinator.getCoordinatorClient().persistServiceConfiguration(standby.toConfiguration());
                    }
                }
            }
        }
    }

    /**
     * Process DR config change for resume-standby op
     *  - All existing sites - include resumed site to vdc config and apply the config
     *  - To-be-resumed site - rebuild db/zk data from active site and apply the config 
     */
    public static class DrResumeStandbyHandler extends VdcOpHandler {
        public DrResumeStandbyHandler() {
        }
        
        @Override
        public void execute() throws Exception {
            // on all sites, reconfig to enable firewall/ipsec
            if (drUtil.isActiveSite()) {
                reconfigVdc();
            } else {
                // ZooKeeper leader might be lost when active site reconfigures
                // so it might not be possible to use barrier here.
                reconfigVdc(false);
            }
        }
    }

    /**
     * Process DR config change for degrade-standby op
     *  - Active site - remove to-be-degraded sites from strategy options
     *  - To-be-degraded sites - restart dbsvc/geodbsvc
     *  - Other sites - will not be notified
     */
    public static class DrDegradeStandbyHandler extends VdcOpHandler {
        public DrDegradeStandbyHandler() {
        }

        @Autowired
        private MailHandler mailHandler;

        @Override
        public void execute() throws Exception {
            if(drUtil.isActiveSite()) {
                InterProcessLock lock = coordinator.getCoordinatorClient().getSiteLocalLock(LOCK_DEGRADE_STANDBY);
                while (drUtil.hasSiteInState(SiteState.STANDBY_DEGRADING)) {
                    try {
                        log.info("Acquiring lock {}", LOCK_DEGRADE_STANDBY);
                        lock.acquire();
                        log.info("Acquired lock {}", LOCK_DEGRADE_STANDBY);

                        if (!drUtil.hasSiteInState(SiteState.STANDBY_DEGRADING)) {
                            // someone else updated the status already
                            break;
                        }

                        for (Site site : drUtil.listSitesInState(SiteState.STANDBY_DEGRADING)) {
                            removeDbNodesFromGossip(site);
                        }

                        for (Site site : drUtil.listSitesInState(SiteState.STANDBY_DEGRADING)) {
                            removeDbNodesFromStrategyOptions(site);

                            log.info("Setting site {} to STANDBY_DEGRADED", site.getUuid());
                            site.setState(SiteState.STANDBY_DEGRADED);
                            coordinator.getCoordinatorClient().persistServiceConfiguration(site.toConfiguration());
                            sendDegradeAlertMail(site.getName());
                        }
                    } finally {
                        try {
                            log.info("Releasing lock {}", LOCK_DEGRADE_STANDBY);
                            lock.release();
                            log.info("Released lock {}", LOCK_DEGRADE_STANDBY);
                        } catch (Exception e) {
                            log.error("Failed to release lock {}", LOCK_DEGRADE_STANDBY);
                        }
                    }
                }
                flushVdcConfigToLocal();
            } else {
                flushVdcConfigToLocal();
                // restart dbsvc/geodbsvc so that the internode authenticator takes effect.
                localRepository.restart(Constants.DBSVC_NAME);
                localRepository.restart(Constants.GEODBSVC_NAME);
            }
        }

        private void sendDegradeAlertMail(String siteName) {
            try {
                mailHandler.sendSiteDegradedMail(siteName, System.currentTimeMillis());
            } catch (Exception e) {
                log.error(String.format("Failed to sending mail that site %s is degraded", siteName), e);
            }
        }
    }

    /**
     * Process DR config change for switchover
     * On old active site:
     * 1.Flush vdc config properties to local
     * 2.Stop controller, sa, vasa services
     * 3.All nodes enter double barrier (ZK path : /sites/{site_uuid}/switchoverBarrierActiveSite) and set site state to STANDBY_SYNCED
     * 4.Set new active site state from "STANDBY_SYNCED" to "STANDBY_SWITCHING_OVER"
     * 5.Wait for new active site's confirm to restart: wait for barrier created at step 3. All nodes will wait until restart barrier is
     * removed by new active site.
     * 6.Restart all nodes.
     * 
     * new active :
     * 1.Wait for old active site's state has been changed to STANDBY_SYNCED
     * 2.Check whether old active restart barrier exists (ZK path: /sites/{site_uuid}/switchoverRestartBarrier):
     *  a.If NO, do thing. It means barrier has been removed
     *  b. If Yes, all nodes enter barrier (ZK path: /sites/{site_uuid}/switchoverStandbySiteRemoveBarrier) and remove the barrier (created
     *  at old active site's step 5).
     * 3.Wait until there is no ZK leader
     * 4.Flush vdc config properties to local
     * 5.Reconfig ZK to participant
     * 6.Make sure all nodes enter barrier (ZK path: /sites/{site_uuid}/switchoverBarrierStandbySite) and set site state to "ACTIVE"
     * 7.Restart all nodes
     * 
     * other standby - flush new vdc config to disk, reconfig/reload coordinator
     */
    public static class DrSwitchoverHandler extends VdcOpHandler {
        
        private static final int TIME_WAIT_FOR_OLD_ACTIVE_SWITCHOVER_MS = 1000 * 5;
        private static final int MAX_WAIT_TIME_IN_MIN = 5;
        private boolean isRebootNeeded = true;
        private boolean hasSingleNodeSite = false;
        private VdcPropertyBarrier standbyRestartOldActiveBarrier; 
        private VdcPropertyBarrier setStateToSyncedBarrier;
        private VdcPropertyBarrier setStateToSwitchingoverBarrier;
        private VdcPropertyBarrier setStateToActiveBarrier;
        
        public DrSwitchoverHandler() {
        }
        
        @Override
        public boolean isConcurrentRebootNeeded() {
            return isRebootNeeded;
        }
        
        @Override
        public void execute() throws Exception {
            Site site = drUtil.getLocalSite();
            SiteInfo siteInfo = coordinator.getCoordinatorClient().getTargetInfo(SiteInfo.class);
            hasSingleNodeSite = hasSingleNodeSite();
            
            if (standbyRestartOldActiveBarrier == null) {
                standbyRestartOldActiveBarrier = new VdcPropertyBarrier(Constants.SWITCHOVER_BARRIER_STANDBY_RESTART_OLD_ACTIVE,
                    VDC_OP_BARRIER_TIMEOUT, site.getNodeCount(), false);
            }
            if (setStateToSyncedBarrier == null) {
                setStateToSyncedBarrier = new VdcPropertyBarrier(Constants.SWITCHOVER_BARRIER_SET_STATE_TO_SYNCED, 
                        VDC_OP_BARRIER_TIMEOUT, site.getNodeCount(), false);
            }
            if (setStateToSwitchingoverBarrier == null) {
                setStateToSwitchingoverBarrier = new VdcPropertyBarrier(Constants.SWITCHOVER_BARRIER_SET_STATE_TO_STANDBY_SWITCHINGOVER, 
                        VDC_OP_BARRIER_TIMEOUT, site.getNodeCount(), false);
            }
            if (setStateToActiveBarrier == null) {
                setStateToActiveBarrier = new VdcPropertyBarrier(Constants.SWITCHOVER_BARRIER_SET_STATE_TO_ACTIVE, 
                        VDC_OP_BARRIER_TIMEOUT, site.getNodeCount(), false);
            }
            
            // Update site state
            if (site.getUuid().equals(siteInfo.getSourceSiteUUID())) {
                isRebootNeeded = true;
                log.info("This is switchover active site (old active)");

                coordinator.stopCoordinatorSvcMonitor();
                
                flushVdcConfigToLocal();
                proccessSingleNodeSiteCase();
                stopActiveSiteRelatedServices();
                
                updateSwitchoverSiteState(site, SiteState.STANDBY_SYNCED, setStateToSyncedBarrier);
                Site newActiveSite = drUtil.getSiteFromLocalVdc(siteInfo.getTargetSiteUUID());
                drUtil.recordDrOperationStatus(newActiveSite.getUuid(), InterState.SWITCHINGOVER_STANDBY);
                updateSwitchoverSiteState(newActiveSite, SiteState.STANDBY_SWITCHING_OVER, setStateToSwitchingoverBarrier);
                waitForBarrierRemovedToRestart(site);
            } else if (site.getUuid().equals(siteInfo.getTargetSiteUUID())) {
                isRebootNeeded = true;
                log.info("This is switchover standby site (new active)");
                
                Site oldActiveSite = drUtil.getSiteFromLocalVdc(siteInfo.getSourceSiteUUID());
                log.info("Old active site is {}", oldActiveSite);
                
                waitForOldActiveSiteFinishOperations(oldActiveSite.getUuid());
                
                coordinator.stopCoordinatorSvcMonitor();
                
                notifyOldActiveSiteReboot(oldActiveSite, site);
                waitForOldActiveZKLeaderDown(oldActiveSite);
                
                flushVdcConfigToLocal();
                refreshCoordinator();
                
                updateSwitchoverSiteState(site, SiteState.ACTIVE, setStateToActiveBarrier);
            } else {
                isRebootNeeded = true;
                // The third site just goes ahead to reboot.
                // we need reconfig/restart zookeeper in order to make it treat new active site as Zk leader nodes. 
                // Each time we restart coordinatorsvc, we must restart all ZK client. Otherwise ZK error "Xid out of order. Got Xid 21 with err -119 expected Xid 20"
                // may happen due to inconsistent Zk Xid on different ZK observer node due to async zk replication
            }
        }

        private void waitForBarrierRemovedToRestart(Site site) throws Exception {
            String restartBarrierPath = getSingleBarrierPath(site.getUuid(), Constants.SWITCHOVER_BARRIER_RESTART);
            DistributedBarrier restartBarrier = coordinator.getCoordinatorClient().getDistributedBarrier(
                    restartBarrierPath);
            
            if (restartBarrier.waitOnBarrier(MAX_WAIT_TIME_IN_MIN, TimeUnit.MINUTES)) {
                log.info("Restart barrier has been removed, going to restart");
            } else {
                throw new IllegalStateException("Timeout when wait restart barrier");
            }
        }

        private void stopActiveSiteRelatedServices() {
            localRepository.stop("vasa");
            localRepository.stop("sa");
            localRepository.stop("controller");
        }

        private void proccessSingleNodeSiteCase() {
            if (hasSingleNodeSite) {
                log.info("Single node deployment detected. Need refresh firewall/ipsec");
                refreshIPsec();
                refreshFirewall();
            }
        }

        private void waitForOldActiveZKLeaderDown(Site oldActiveSite) throws InterruptedException {
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < MAX_WAIT_TIME_IN_MIN * 60 * 1000) {
                try {
                    if (coordinator.isZKLeaderAlive(oldActiveSite)) {
                        log.info("Old active site ZK leader is still alive, wait for another 10 seconds");
                        Thread.sleep(TIME_WAIT_FOR_OLD_ACTIVE_SWITCHOVER_MS);
                    } else {
                        log.info("ZK leader is gone from old active site, reconfig local ZK to select new leader");
                        return;
                    }
                } catch (Exception e) {
                    log.error("Failed to check active size ZK leader state, {}", e);
                }
            }
            
            log.warn("Timeout reached when wait for old active site's ZK leader down");
            throw new IllegalStateException("Timeout reached when wait for old active site's ZK leader down");
        }
        
        private void notifyOldActiveSiteReboot(Site oldActiveSite, Site site) throws Exception {
            String restartBarrierPath = getSingleBarrierPath(oldActiveSite.getUuid(), Constants.SWITCHOVER_BARRIER_RESTART);
            
            if (!coordinator.getCoordinatorClient().nodeExists(restartBarrierPath)) {
                log.info("Old active site restart barrier {} has been removed, no action needed", restartBarrierPath);
                return;
            }
            
            standbyRestartOldActiveBarrier.enter();
            try {
                if (coordinator.isVirtualIPHolder()) {
                    log.info("This node is virtual IP holder, notify remote old active site to reboot");
                    DistributedBarrier restartBarrier = coordinator.getCoordinatorClient().getDistributedBarrier(
                            restartBarrierPath);
                    restartBarrier.removeBarrier();
                }
            } finally {
                standbyRestartOldActiveBarrier.leave();
            }
            
            log.info("reboot remote old active site and go on");
        }

        private void waitForOldActiveSiteFinishOperations(String oldActiveSiteUUID) {
            
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < MAX_WAIT_TIME_IN_MIN * 60 * 1000) {
                try {
                    SiteInfo currentSiteInfo = this.coordinator.getTargetInfo(SiteInfo.class);
                    if (!SiteInfo.DR_OP_SWITCHOVER.equals(currentSiteInfo.getActionRequired())) {
                        log.warn("Current dr operation is not switchover, no need to wait old active anymore");
                        break;
                    }
                    
                    Site oldActiveSite = drUtil.getSiteFromLocalVdc(oldActiveSiteUUID);
                    if (!oldActiveSite.getState().equals(SiteState.STANDBY_SYNCED)) { 
                        log.info("Old active site {} is still doing switchover, wait for another 5 seconds", oldActiveSite);
                        Thread.sleep(TIME_WAIT_FOR_OLD_ACTIVE_SWITCHOVER_MS);
                    } else {
                        log.info("Old active site finish all switchover tasks, new active site begins to switchover");
                        return;
                    }
                } catch (Exception e) {
                    log.error("Failed to check old active site status", e);
                }
            }
            
            log.warn("Timeout reached or current dr operation is not switchover when wait for old active site finishing operations");
            throw new IllegalStateException(
                    "Timeout reached or current dr operation is not switchover when wait for old active site finishing operations");
        }
        
        private void updateSwitchoverSiteState(Site site, SiteState siteState, VdcPropertyBarrier barrier) throws Exception {
            if (site.getState().equals(siteState)) {
                log.info("Site state has been changed to {}, no actions needed.", siteState);
                return;
            }
            
            coordinator.blockUntilZookeeperIsWritableConnected(SWITCHOVER_ZK_WRITALE_WAIT_INTERVAL, MAX_ZK_WAIT_RETRY_TIMES);
            
            barrier.enter();
            try {
                log.info("Set state from {} to {}", site.getState(), siteState);
                site.setState(siteState);
                coordinator.getCoordinatorClient().persistServiceConfiguration(site.toConfiguration());
            } finally {
                barrier.leave();
            }
        }
        
        private String getSingleBarrierPath(String siteID, String barrierName) {
            return String.format("%s/%s/%s", ZkPath.SITES, siteID, barrierName);
        }
        
        // See coordinator hack for DR in CoordinatorImpl.java. If single node
        // ViPR is switching over, we need refresh firewall/ipsec
        private boolean hasSingleNodeSite() {
            for (Site site : drUtil.listSites()) {
                if (site.getState().equals(SiteState.ACTIVE_SWITCHING_OVER) || site.getState().equals(SiteState.STANDBY_SWITCHING_OVER)) {
                    if (site.getNodeCount() == 1) {
                        return true;
                    }
                }
            }
            return false;
        }
    }


    
    /**
     * Process DR config change for failover
     *  - new active site - remove the db nodes of old active from  gossip/strategy options, 
     *                      exclude old active from vdc config, and set state to active
     *  - old active site - do nothing
     *  - other standby   - exclude old active from vdc config and reconfig
     */
    public static class DrFailoverHandler extends VdcOpHandler {
        private Factory postHandlerFactory;
        private VdcPropertyBarrier failoverBarrier;
        
        public DrFailoverHandler() {
        }

        @Override
        public void execute() throws Exception {
            Site site = drUtil.getLocalSite();
            if (failoverBarrier == null) {
                failoverBarrier = new VdcPropertyBarrier(Constants.FAILOVER_BARRIER, VDC_OP_BARRIER_TIMEOUT, site.getNodeCount(), true);
            }

            if (isNewActiveSiteForFailover(site)) {
                setConcurrentRebootNeeded(true);
                coordinator.stopCoordinatorSvcMonitor();
                reconfigVdc(false);
                coordinator.blockUntilZookeeperIsWritableConnected(FAILOVER_ZK_WRITALE_WAIT_INTERVAL, MAX_ZK_WAIT_RETRY_TIMES);
                processFailover();
                localRepository.rebaseZkSnapshot();
                waitForAllNodesAndReboot(site);
            } 
            failoverBarrier = null;
        }
        
        public void setPostHandlerFactory(Factory postHandlerFactory) {
            this.postHandlerFactory = postHandlerFactory;
        }
        
        private boolean isNewActiveSiteForFailover(Site site) {
            return site.getState().equals(SiteState.STANDBY_FAILING_OVER);
        }
        
        private void processFailover() throws Exception {
            Site oldActiveSite = getOldActiveSiteForFailover();
            
            if (oldActiveSite == null) {
                log.info("Not old active site found.");
                postHandlerFactory.initializeAllHandlers();
                return;
            }
            
            InterProcessLock lock = null;
            try {
                lock = coordinator.getCoordinatorClient().getSiteLocalLock(LOCK_FAILOVER_REMOVE_OLD_ACTIVE);
                log.info("Acquiring lock {}", LOCK_FAILOVER_REMOVE_OLD_ACTIVE);
                
                lock.acquire();
                log.info("Acquired lock {}", LOCK_FAILOVER_REMOVE_OLD_ACTIVE); 
        
                // double check site state
                oldActiveSite = getOldActiveSiteForFailover();
                if (oldActiveSite == null) {
                    log.info("Old active site has been remove by other node, no action needed.");
                    return;
                }
                tryPoweroffRemoteSite(oldActiveSite);    
                removeDbNodesFromStrategyOptions(oldActiveSite);
                for (Site site : drUtil.listStandbySites()) {
                    if (!isNewActiveSiteForFailover(site)) {
                        removeDbNodesFromStrategyOptions(site);
                    }
                }
                postHandlerFactory.initializeAllHandlers();
                
                oldActiveSite.setState(SiteState.ACTIVE_DEGRADED);
                coordinator.getCoordinatorClient().persistServiceConfiguration(oldActiveSite.toConfiguration());
            } catch (Exception e) {
                log.error("Failed to remove old active site in failover, {}", e);
                throw e;
            } finally {
                if (lock != null) {
                    lock.release();
                }
            }
        }
        
        private Site getOldActiveSiteForFailover() {
            for (Site site : drUtil.listSites()) {
                if (site.getState().equals(SiteState.ACTIVE_FAILING_OVER)) {
                    return site;
                }
            }
            return null;
        }
        
        private void waitForAllNodesAndReboot(Site site) throws Exception {
            coordinator.blockUntilZookeeperIsWritableConnected(FAILOVER_ZK_WRITALE_WAIT_INTERVAL, MAX_ZK_WAIT_RETRY_TIMES);
            
            log.info("Wait for barrier to reboot cluster");
            
            failoverBarrier.enter();
            if (!failoverBarrier.leave()) {
                throw new IllegalStateException("Not all nodes leave the barrier");
            }
            log.info("Reboot this node after failover");
        }
    }

    /**
     * This handler will be triggered in active site when it detect there are other active sites exist.
     * Degraded itself to ACTIVE_DEGRADE and not provide any provisioning functions.
     * 
     */
    public static class DrFailbackDegradeHandler extends VdcOpHandler {
        public DrFailbackDegradeHandler() {
            setConcurrentRebootNeeded(true);
        }
        
        @Override
        public void execute() throws Exception {
            // Do nothing here since we are going to reboot. All properties will be flushed in VdcManager and
            // services will be refreshed during reboot
        }
    }

    /**
     * IP Change handler to update IPs info from ZK to vdcproperty
     */
    public static class IPChangeHandler extends VdcOpHandler {
        public IPChangeHandler() {
        }

        @Override
        public void execute() throws Exception {
            syncFlushVdcConfigToLocal();
        }
    }

    public CoordinatorClientExt getCoordinator() {
        return coordinator;
    }

    public void setCoordinator(CoordinatorClientExt coordinator) {
        this.coordinator = coordinator;
    }

    public LocalRepository getLocalRepository() {
        return localRepository;
    }

    public void setLocalRepository(LocalRepository localRepository) {
        this.localRepository = localRepository;
    }
    
    public PropertyInfoExt getTargetVdcPropInfo() {
        return targetVdcPropInfo;
    }

    public void setTargetVdcPropInfo(PropertyInfoExt targetVdcPropInfo) {
        this.targetVdcPropInfo = targetVdcPropInfo;
    }

    public PropertyInfoExt getLocalVdcPropInfo() {
        return localVdcPropInfo;
    }

    public void setLocalVdcPropInfo(PropertyInfoExt localVdcPropInfo) {
        this.localVdcPropInfo = localVdcPropInfo;
    }

    public SiteInfo getTargetSiteInfo() {
        return targetSiteInfo;
    }

    public void setTargetSiteInfo(SiteInfo targetSiteInfo) {
        this.targetSiteInfo = targetSiteInfo;
    }
    
    public DrUtil getDrUtil() {
        return drUtil;
    }

    public void setDrUtil(DrUtil drUtil) {
        this.drUtil = drUtil;
    }

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }
    
    public Service getService() {
        return service;
    }

    public void setService(Service service) {
        this.service = service;
    }

    /**
     * Flush vdc config to local disk /.volumes/boot/etc/vdcconfig.properties
     * Note: this flush will not write VDC_CONFIG_VERSION to disk to make sure if there are some errors, VdcManager can enter retry loop
     */
    protected void flushVdcConfigToLocal() {
        PropertyInfoExt vdcProperty = new PropertyInfoExt(targetVdcPropInfo.getAllProperties());
        vdcProperty.addProperty(VdcConfigUtil.VDC_CONFIG_VERSION, localVdcPropInfo.getProperty(VdcConfigUtil.VDC_CONFIG_VERSION));
        localRepository.setVdcPropertyInfo(vdcProperty);
    }

    /**
     * Simulaneously flush vdc config on all nodes in current site. via barrier
     */
    protected void syncFlushVdcConfigToLocal() throws Exception {
        if (vdcPropBarrier == null) {
            vdcPropBarrier = new VdcPropertyBarrier(targetSiteInfo, VDC_OP_BARRIER_TIMEOUT);
        }
        
        vdcPropBarrier.enter();
        try {
            flushVdcConfigToLocal();
        } finally {
            boolean allLeft = vdcPropBarrier.leave();

            // the additional sleep is for addressing COP-19315 -- hangs at barrier.leave()
            //
            // without sleep here, which means to restart ipsec immediately even barrier.leave() returned
            // as timed out, the first timed-out node restarting ipsec will cause ZK lose its quorum,
            // because of that, the second node will never be able to return from leave() function.
            //
            // with 1 minute delay to restart ipsec, it can make sure all live nodes are returned from leave()
            // function, as they are all enter() at the same time. the time difference among their leave() time
            // will be in the range of seconds.
            if (!allLeft) {
                log.info("wait 1 minute, so all nodes be able to return from leave()");
                Thread.sleep(IPSEC_RESTART_DELAY);
            }
        }
        // discard the barrier after current op is done. Next op we may need a different barrier based on
        // actionScopeType of targetSiteInfo
        vdcPropBarrier = null;  
    }
    
    protected void reconfigVdc() throws Exception {
        reconfigVdc(true);
    }
    
    protected void reconfigVdc(boolean allNodeSyncRequired) throws Exception {
        if (allNodeSyncRequired) {
            syncFlushVdcConfigToLocal();
        } else {
            flushVdcConfigToLocal();
        }
        refreshIPsec();
        refreshFirewall();
        refreshSsh();
        refreshCoordinator();
    }
    

    protected void refreshFirewall() {
        localRepository.reconfigProperties("firewall");
        localRepository.reload("firewall");
    }
    
    protected void refreshSsh() {
        // for re-generating /etc/ssh/ssh_known_hosts to include nodes of standby sites
        // no need to reload ssh service.
        localRepository.reconfigProperties("ssh");
    }
 
    protected void refreshIPsec() {
        localRepository.reconfigProperties("ipsec");
        localRepository.reload("ipsec");
    }
    
    protected void refreshCoordinator() {
        if (coordinator.isStandby() ||
                drUtil.getLocalSite().getState().equals(SiteState.STANDBY_FAILING_OVER)) {
            String localZkMode = drUtil.getLocalCoordinatorMode();
            if(drUtil.isParticipantNode(localZkMode)) {
                log.info("No need to reconfig coordinator on participant standby nodes");
                return;
            }
        }
        localRepository.reconfigProperties("coordinator");
        localRepository.restart("coordinatorsvc");

    }
    
    /**
     * remove a site from cassandra gossip ring of dbsvc and geodbsvc with force
     */
    protected void removeDbNodesFromGossip(Site site) {
        String dcName = drUtil.getCassandraDcId(site);
        try (DbManagerOps dbOps = new DbManagerOps(Constants.DBSVC_NAME);
                DbManagerOps geodbOps = new DbManagerOps(Constants.GEODBSVC_NAME)) {
            dbOps.removeDataCenter(dcName);
            geodbOps.removeDataCenter(dcName);
        }
    }

    protected void removeDbNodesFromStrategyOptions(Site site) {
        String dcName = drUtil.getCassandraDcId(site);
        ((DbClientImpl)dbClient).getLocalContext().removeDcFromStrategyOptions(dcName);
        ((DbClientImpl)dbClient).getGeoContext().removeDcFromStrategyOptions(dcName);
        log.info("Removed site {} configuration from db strategy options", site.getUuid());
    }

    /**
     * Find sites in STANDBY_REMOVING state and attempt to power them off
     */
    protected void poweroffRemovedSites() {
        List<Site> toBeRemovedSites = drUtil.listSitesInState(SiteState.STANDBY_REMOVING);
            for (Site site : toBeRemovedSites) {
                tryPoweroffRemoteSite(site);
            }
    }

    /**
     * Just try, don't guarantee successfully done
     */
    protected void tryPoweroffRemoteSite(Site site) {
        String siteId = site.getUuid();
        // all syssvc shares same port
        String baseNodeURL = String.format(SysClientFactory.BASE_URL_FORMAT, site.getVipEndPoint(), service.getEndpoint().getPort());
        try {
            SysClientFactory.getSysClient(URI.create(baseNodeURL)).post(URI.create(URI_INTERNAL_POWEROFF), null, null);
            log.info("Powering off site {}", siteId);
        } catch (Exception e) {
            log.warn("Error happened when trying to poweroff remove site {}", siteId, e);
        }
    }

    protected void retrySleep() {
        waiter.sleep(SWITCHOVER_ZK_WRITALE_WAIT_INTERVAL);
    }
    
    protected void populateStandbySiteErrorIfNecessary(Site site, InternalServerErrorException e) {

        SiteState operation = site.getState();
        SiteError error = new SiteError(e,operation.name());

        log.info("set site {} state to STANDBY_ERROR, set lastState to {}",site.getName(),site.getState());
        coordinator.getCoordinatorClient().setTargetInfo(site.getUuid(),  error);
        site.setLastState(site.getState());
        site.setState(SiteState.STANDBY_ERROR);
        coordinator.getCoordinatorClient().persistServiceConfiguration(site.toConfiguration());
    }

    protected void changeSiteState(SiteState from, SiteState to) {
        List<Site> newSites = drUtil.listSitesInState(from);
        for(Site newSite : newSites) {
            log.info("Change standby site {} state from {} to {}", new Object[]{newSite.getSiteShortId(), from, to});
            newSite.setLastState(from);
            newSite.setState(to);
            coordinator.getCoordinatorClient().persistServiceConfiguration(newSite.toConfiguration());
        }
    }
    
    protected boolean isGeoConfigChange() {
        boolean isGeo =  targetVdcPropInfo.getProperty(VdcConfigUtil.VDC_IDS).contains(",")
                    || localVdcPropInfo.getProperty(VdcConfigUtil.VDC_IDS).contains(",");
        return isGeo && !StringUtils.equals(targetVdcPropInfo.getProperty(VdcConfigUtil.VDC_IDS), localVdcPropInfo.getProperty(VdcConfigUtil.VDC_IDS));
    }
    
    /**
     * Util class to make sure no one node applies configuration until all nodes get synced to local bootfs.
     */
    private class VdcPropertyBarrier {

        DistributedDoubleBarrier barrier;
        int timeout = 0;
        String barrierPath;

        /**
         * create or get a barrier
         * @param siteInfo
         */
        public VdcPropertyBarrier(SiteInfo siteInfo, int timeout) {
            this.timeout = timeout;
            barrierPath = getBarrierPath(siteInfo);
            int nChildrenOnBarrier = getChildrenCountOnBarrier();
            this.barrier = coordinator.getCoordinatorClient().getDistributedDoubleBarrier(barrierPath, nChildrenOnBarrier);
            log.info("Created VdcPropBarrier on {} with the children number {}", barrierPath, nChildrenOnBarrier);
        }

        public VdcPropertyBarrier(String path, int timeout, int memberQty, boolean crossSite) {
            this.timeout = timeout;
            barrierPath = getBarrierPath(path, crossSite);
            this.barrier = coordinator.getCoordinatorClient().getDistributedDoubleBarrier(barrierPath, memberQty);
            log.info("Created VdcPropBarrier on {} with the children number {}", barrierPath, memberQty);
        }

        /**
         * Waiting for all nodes entering the VdcPropBarrier.
         * @return
         * @throws Exception
         */
        public void enter() throws Exception {
            log.info("Waiting for all nodes entering {}", barrierPath);

            boolean allEntered = barrier.enter(timeout, TimeUnit.SECONDS);
            if (allEntered) {
                log.info("All nodes entered VdcPropBarrier at path {}", barrierPath);
            } else {
                log.warn("Only Part of nodes entered within {} seconds at path {}", timeout, barrierPath);
                throw new IllegalStateException("Only Part of nodes entered within timeout");
            }
        }

        /**
         * Waiting for all nodes leaving the VdcPropBarrier.
         * @return true if all nodes left
         * @throws Exception
         */
        public boolean leave() throws Exception {
            // Even if part of nodes fail to leave this barrier within timeout, we still let it pass. The ipsec monitor will handle failure on other nodes.
            log.info("Waiting for all nodes leaving {}", barrierPath);

            boolean allLeft = barrier.leave(timeout, TimeUnit.SECONDS);
            if (allLeft) {
                log.info("All nodes left VdcPropBarrier path {}", barrierPath);
            } else {
                log.warn("Only Part of nodes left VdcPropBarrier path {} before timeout", barrierPath);
            }
            
            return allLeft;
        }

        private String getBarrierPath(SiteInfo siteInfo) {
            switch (siteInfo.getActionScope()) {
                case VDC:
                    return String.format("%s/VdcPropBarrier", ZkPath.BARRIER);
                case SITE:
                    return String.format("%s/%s/VdcPropBarrier", ZkPath.SITES, coordinator.getCoordinatorClient().getSiteId());
                default:
                    throw new RuntimeException("Unknown Action Scope: " + siteInfo.getActionScope());
            }
        }

        private String getBarrierPath(String path, boolean crossSite) {
            String barrierPath = crossSite ? String.format("%s/%s", ZkPath.SITES, path) :
                    String.format("%s/%s/%s", ZkPath.BARRIER, coordinator.getCoordinatorClient().getSiteId(), path);

            log.info("Barrier path is {}", barrierPath);
            return barrierPath;
        }

        /**
         * Get the number of nodes should involve the barrier. It's all nodes of a site when adding standby while nodes of a VDC when rotating key.
         * @return
         */
        private int getChildrenCountOnBarrier() {
            SiteInfo.ActionScope scope = targetSiteInfo.getActionScope();
            switch (scope) {
                case SITE:
                    return coordinator.getNodeCount();
                case VDC:
                    return drUtil.getNodeCountWithinVdc();
                default:
                    throw new RuntimeException("Unknown Action Scope is set in SiteInfo: " + scope);
            }
        }
    }
}
