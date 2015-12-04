/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.vdc;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.PropertyInfoExt;
import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteError;
import com.emc.storageos.coordinator.client.model.SiteInfo;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.client.service.DistributedDoubleBarrier;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.coordinator.common.impl.ZkPath;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.util.VdcConfigUtil;
import com.emc.storageos.management.backup.BackupConstants;
import com.emc.storageos.management.jmx.recovery.DbManagerOps;
import com.emc.storageos.services.util.Waiter;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.emc.storageos.systemservices.impl.client.SysClientFactory;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.storageos.systemservices.impl.upgrade.LocalRepository;

/**
 * Operation handler for vdc config change. A vdc config change may represent 
 * cluster topology change in DR or GEO ensemble. Any extra actions that take place need be 
 * encapsulated within a VdcOpHandler instance
 */
public abstract class VdcOpHandler {
    private static final Logger log = LoggerFactory.getLogger(VdcOpHandler.class);
    
    private static final int VDC_RPOP_BARRIER_TIMEOUT = 5*60; // 5 mins
    private static final int SWITCHOVER_ZK_WRITALE_WAIT_INTERVAL = 1000 * 5;
    private static final int FAILOVER_ZK_WRITALE_WAIT_INTERVAL = 1000 * 15;
    private static final int SWITCHOVER_BARRIER_TIMEOUT = 300;
    private static final int FAILOVER_BARRIER_TIMEOUT = 300;
    private static final int MAX_PAUSE_RETRY = 20;
    // data revision time out - 5 minutes
    private static final long DATA_REVISION_WAIT_TIMEOUT_SECONDS = 300;
    
    private static final String URI_INTERNAL_POWEROFF = "/control/internal/cluster/poweroff";
    private static final String LOCK_REMOVE_STANDBY="drRemoveStandbyLock";
    private static final String LOCK_FAILOVER_REMOVE_OLD_ACTIVE="drFailoverRemoveOldActiveLock";
    private static final String LOCK_PAUSE_STANDBY="drPauseStandbyLock";
    private static final String LOCK_ADD_STANDBY="drAddStandbyLock";
    
    protected CoordinatorClientExt coordinator;
    protected LocalRepository localRepository;
    protected DrUtil drUtil;
    protected DbClient dbClient;
    protected Service service;
    protected final Waiter waiter = new Waiter();
    protected PropertyInfoExt targetVdcPropInfo;
    protected SiteInfo targetSiteInfo;
    
    public VdcOpHandler() {
    }
    
    public abstract void execute() throws Exception;
    
    /**
     * No-op - flush vdc config to local only
     */
    public static class NoopOpHandler extends VdcOpHandler{
        public NoopOpHandler() {
        }
        
        @Override
        public void execute() {
            flushVdcConfigToLocal();
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
            syncFlushVdcConfigToLocal();
            try {
                refreshIPsec();
            } catch (Exception ex) {
                log.warn("Unexpected error happens during applying vdc config to local", ex);
                resetLocalVdcConfigVersion();
            }
        }
    };

    /**
     * Transit Cassandra native encryption to IPsec
     */
    public static class IPSecEnableHandler extends VdcOpHandler{
        public IPSecEnableHandler() {
        }
        
        @Override
        public void execute() throws Exception {
            syncFlushVdcConfigToLocal();
            try {
                localRepository.reconfig();
                localRepository.reload("ipsec");
                localRepository.restart("db");
                localRepository.restart("geodb");
            } catch (Exception ex) {
                log.warn("Unexpected error happens during applying vdc config to local", ex);
                resetLocalVdcConfigVersion();
            }
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
            if (drUtil.isActiveSite()) {
                log.info("Acquiring lock {} to update default properties of standby", LOCK_ADD_STANDBY);
                InterProcessLock lock = coordinator.getCoordinatorClient().getLock(LOCK_ADD_STANDBY);
                lock.acquire();
                log.info("Acquired lock successfully");
                try {
                    disableBackupSchedulerForStandby();
                } finally {
                    lock.release();
                    log.info("Released lock for {}", LOCK_ADD_STANDBY);
                }
            }
            reconfigVdc();
        }
        
        private void disableBackupSchedulerForStandby() {
            List<Site> sites = drUtil.listSitesInState(SiteState.STANDBY_ADDING);
            for (Site site : sites) {
                String siteId = site.getUuid();
                PropertyInfoExt sitePropInfo = coordinator.getSiteSpecificProperties(siteId);
                if (sitePropInfo == null) {
                    log.info("Disable backupscheduler for {}", site.getUuid());
                    Map<String, String> siteProps = new HashMap<String, String>();
                    siteProps.put(BackupConstants.SCHEDULER_ENABLED, "false");
                    coordinator.setSiteSpecificProperties(siteProps, siteId);
                }
            }
        }
    }

    /**
     * Process DR config change for add-standby on newly added site
     *   flush vdc config to disk, increase data revision and reboot. After reboot, it sync db/zk data from active sites during db/zk startup
     */
    public static class DrChangeDataRevisionHandler extends VdcOpHandler {
        public DrChangeDataRevisionHandler() {
        }
        
        @Override
        public void execute() throws Exception {
            flushVdcConfigToLocal();
            checkDataRevision();
        }
        
        private void checkDataRevision() {
            // Step4: change data revision
            String targetDataRevision = targetSiteInfo.getTargetDataRevision();
            log.info("Step4: check if target data revision is changed - {}", targetDataRevision);
            try {
                String localRevision = localRepository.getDataRevision();
                log.info("Step4: local data revision is {}", localRevision);
                if (!targetSiteInfo.isNullTargetDataRevision() && !targetDataRevision.equals(localRevision)) {
                    updateDataRevision();
                }
            } catch (Exception e) {
                log.error("Step4: Failed to update data revision. {}", e);
            }
        }
        
        /**
         * Check if data revision is same as local one. If not, switch to target revision and reboot the whole cluster
         * simultaneously.
         * 
         * The data revision switch is implemented as 2-phase commit protocol to ensure no partial commit
         * 
         * @throws Exception
         */
        private void updateDataRevision() throws Exception {
            String localRevision = localRepository.getDataRevision();
            String targetDataRevision = targetSiteInfo.getTargetDataRevision();
            log.info("Step3: Trying to reach agreement with timeout for data revision change");
            String barrierPath = String.format("%s/%s/DataRevisionBarrier", ZkPath.SITES, coordinator.getCoordinatorClient().getSiteId());
            DistributedDoubleBarrier barrier = coordinator.getCoordinatorClient().getDistributedDoubleBarrier(barrierPath, coordinator.getNodeCount());
            try {
                boolean phase1Agreed = barrier.enter(DATA_REVISION_WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (phase1Agreed) {
                    // reach phase 1 agreement, we can start write to local property file
                    log.info("Step3: Reach phase 1 agreement for data revision change");
                    localRepository.setDataRevision(targetDataRevision, false);
                    boolean phase2Agreed = barrier.leave(DATA_REVISION_WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    if (phase2Agreed) {
                        // phase 2 agreement is received, we can make sure data revision change is written to local property file
                        log.info("Step3: Reach phase 2 agreement for data revision change");
                        localRepository.setDataRevision(targetDataRevision, true);
                        localRepository.reboot();
                    } else {
                        log.info("Step3: Failed to reach phase 2 agreement. Rollback revision change");
                        localRepository.setDataRevision(localRevision, true);
                    }
                } 
                log.warn("Step3: Failed to reach agreement among all the nodes. Delay data revision change until next run");
            } catch (Exception ex) {
                log.warn("Step3. Internal error happens when negotiating data revision change", ex);
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
        public DrRemoveStandbyHandler() {
        }
        
        @Override
        public void execute() throws Exception {
            log.info("Processing standby removal");
            if (drUtil.isActiveSite()) {
                log.info("Active site - start removing db nodes from gossip and strategy options");
                removeDbNodes();
            } else {
                log.info("Standby site - waiting for completion of db removal from active site");
                Site localSite = drUtil.getLocalSite();
                if (localSite.getState().equals(SiteState.STANDBY_REMOVING)) {
                    log.info("Current standby site is removed from DR. You can power it on and promote it as acitve later");
                    // cleanup site error 
                    SiteError siteError = coordinator.getCoordinatorClient().getTargetInfo(localSite.getUuid(), SiteError.class);
                    if (siteError != null) {
                        siteError.cleanup();
                        coordinator.getCoordinatorClient().setTargetInfo(localSite.getUuid(), siteError);
                    }
                    return;
                } else {
                    log.info("Waiting for completion of site removal from acitve site");
                    while (drUtil.hasSiteInState(SiteState.STANDBY_REMOVING)) {
                        log.info("Waiting for completion of site removal from acitve site");
                        retrySleep();
                    }
                }
            }
            log.info("Standby removal op - reconfig all services");
            reconfigVdc();
        }
        
        private void removeDbNodes() throws Exception {
            InterProcessLock lock = coordinator.getCoordinatorClient().getLock(LOCK_REMOVE_STANDBY);
            while (drUtil.hasSiteInState(SiteState.STANDBY_REMOVING)) {
                log.info("Acquiring lock {}", LOCK_REMOVE_STANDBY); 
                lock.acquire();
                log.info("Acquired lock {}", LOCK_REMOVE_STANDBY); 
                List<Site> toBeRemovedSites = drUtil.listSitesInState(SiteState.STANDBY_REMOVING);
                try {
                        
                    for (Site site : toBeRemovedSites) {
                        try {
                            poweroffRemoteSite(site);
                            removeDbNodesFromGossip(site);
                        } catch (Exception e) { 
                            populateStandbySiteErrorIfNecessary(site, APIException.internalServerErrors.removeStandbyReconfigFailed(e.getMessage()));
                            throw e;
                        }
                    }
                    for (Site site : toBeRemovedSites) {
                        try {
                            removeDbNodesFromStrategyOptions(site);
                            drUtil.removeSiteConfiguration(site);
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
     * Process DR config change for add-standby op
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
            } else {
                reconfigVdc();
                checkAndPauseOnActive();
            }
        }
        
        /**
         * Update the strategy options and remove the paused site from gossip ring on the acitve site.
         * This should be done after the firewall has been updated to block the paused site so that it's not affected.
         */
        private void checkAndPauseOnActive() {
            // this should only be done on the active site
            if (drUtil.isStandby()) {
                return;
            }

            InterProcessLock lock = coordinator.getCoordinatorClient().getLock(LOCK_PAUSE_STANDBY);
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
                            waitForSiteUnreachable(site);
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
            while (coordinator.isActiveSiteStable()) {
                if (++retryCnt > MAX_PAUSE_RETRY) {
                    throw new IllegalStateException("timeout waiting for coordinatorsvc to be blocked on active site.");
                }
                log.info("short sleep before checking active site status again");
                retrySleep();
            }

            String state = drUtil.getLocalCoordinatorMode(coordinator.getMyNodeId());
            if (DrUtil.ZOOKEEPER_MODE_READONLY.equals(state)) {
                coordinator.reconfigZKToWritable(true);
            }

            Site localSite = drUtil.getLocalSite();
            if (localSite.getState().equals(SiteState.STANDBY_PAUSING)) {
                localSite.setState(SiteState.STANDBY_PAUSED);
                log.info("Updating local site state to STANDBY_PAUSED");
                coordinator.getCoordinatorClient().persistServiceConfiguration(localSite.toConfiguration());
            }
        }

        private void waitForSiteUnreachable(Site site) {
            int retryCnt = 0;
            while (!isSiteDbUnreachable(site)) {
                if (++retryCnt > MAX_PAUSE_RETRY) {
                    throw new IllegalStateException("timeout waiting for db nodes to go down on paused site.");
                }
                retrySleep();
            }
        }

        private boolean isSiteDbUnreachable(Site site) {
            String dcName = drUtil.getCassandraDcId(site);
            try (DbManagerOps dbOps = new DbManagerOps(Constants.DBSVC_NAME);
                 DbManagerOps geodbOps = new DbManagerOps(Constants.GEODBSVC_NAME)) {
                return dbOps.isDataCenterUnreachable(dcName) && geodbOps.isDataCenterUnreachable(dcName);
            }
        }
    }

    /**
     * Process DR config change for add-standby op
     *  - All existing sites - include resumed site to vdc config and apply the config
     *  - To-be-resumed site - rebuild db/zk data from active site and apply the config 
     */
    public static class DrResumeStandbyHandler extends VdcOpHandler {
        public DrResumeStandbyHandler() {
        }
        
        @Override
        public void execute() throws Exception {
            // on all sites, reconfig to enable firewall/ipsec
            reconfigVdc();
        }
    }

    /**
     * Process DR config change for switchover
     *  - old active site: flush new vdc config to disk, reconfig/reload coordinator(synchronized with new active site), 
     *                     update site state to STANDBY_SYNCED
     *  - new active : flush new vdc config to disk, reconfig/reload coordinator(synchronized with old active site), 
     *                 update site state to ACTIVE
     *  - other standby - flush new vdc config to disk, reconfig/reload coordinator
     */
    public static class DrSwitchoverHandler extends VdcOpHandler {
        
        public DrSwitchoverHandler() {
        }
        
        @Override
        public void execute() throws Exception {
            Site site = drUtil.getLocalSite();
            
            // Reload coordinator configuration on all sites
            flushVdcConfigToLocal();
            try {
                coordinator.stopCoordinatorSvcMonitor();
                if (hasSingleNodeSite()) {
                    log.info("Single node deployment detected. Need refresh firewall/ipsec");
                    refreshIPsec();
                    refreshFirewall();
                }
                
                refreshCoordinatorForSwitchover(site);
                // Update site state
                if (isOldActiveSiteForSwitchover(site)) {
                    // old active site
                    updateSwitchoverSiteStateOnOldActive(site);
                } else if (isNewActiveSiteForSwitchover(site)) {
                    // new active site
                    updateSwitchoverSiteStateOnNewActive(site);
                } 
                
            } catch (Exception ex) {
                log.warn("Unexpected error happens during refreshing coordinatorsvc for switchover", ex);
                resetLocalVdcConfigVersion();
            }
            
        }
        
        /**
         * Synchronously reconfig coordinator in old/new active so that we could minimize unavailable window of coordinatorsvc
         * 
         * @throws Exception
         */
        private void refreshCoordinatorForSwitchover(Site site) throws Exception {
            if (isOldActiveSiteForSwitchover(site) || isNewActiveSiteForSwitchover(site)) {
                int switchingNodeCount = getSwitchoverNodeCount();
                log.info("Wait for barrier to reconfig/restart coordinator when switchover");
                VdcPropertyBarrier barrier = new VdcPropertyBarrier(Constants.SWITCHOVER_BARRIER, SWITCHOVER_BARRIER_TIMEOUT, switchingNodeCount, true);
                barrier.enter();
                try {
                    localRepository.reconfigProperties("coordinator");
                } finally {
                    barrier.leave();
                }
                localRepository.restart("coordinatorsvc");
            } else {
                refreshCoordinator();
            }
        }
        
        private void updateSwitchoverSiteStateOnNewActive(Site site) throws Exception {
            log.info("This is switchover standby site (new active)");

            coordinator.blockUntilZookeeperIsWritableConnected(SWITCHOVER_ZK_WRITALE_WAIT_INTERVAL);
            
            VdcPropertyBarrier barrier = new VdcPropertyBarrier(Constants.SWITCHOVER_BARRIER, SWITCHOVER_BARRIER_TIMEOUT, getSwitchoverNodeCount(), true);
            barrier.enter();
            try {
                log.info("Set state to ACTIVE");
                site.setState(SiteState.ACTIVE);
                coordinator.getCoordinatorClient().persistServiceConfiguration(site.toConfiguration());
            } finally {
                barrier.leave();
            }

            log.info("Reboot this node after switchover");
            localRepository.reboot();
        }

        private void updateSwitchoverSiteStateOnOldActive(Site site) throws Exception {
            log.info("This is switchover acitve site (old acitve)");
            
            coordinator.blockUntilZookeeperIsWritableConnected(SWITCHOVER_ZK_WRITALE_WAIT_INTERVAL);

            VdcPropertyBarrier barrier = new VdcPropertyBarrier(Constants.SWITCHOVER_BARRIER, SWITCHOVER_BARRIER_TIMEOUT, getSwitchoverNodeCount(), true);
            barrier.enter();
            try {
                log.info("Set state to SYNCED");
                site.setState(SiteState.STANDBY_SYNCED);
                coordinator.getCoordinatorClient().persistServiceConfiguration(site.toConfiguration());
            } finally {
                barrier.leave();
            }

            log.info("Reboot this node after switchover");
            localRepository.reboot();
        }
        
        private int getSwitchoverNodeCount() {
            int count = 0;
            
            for (Site site : drUtil.listSites()) {
                if (site.getState().equals(SiteState.ACTIVE_SWITCHING_OVER) || site.getState().equals(SiteState.STANDBY_SWITCHING_OVER)) {
                    count += site.getNodeCount();
                }
            }
            
            log.info("Node count is switchover is {}", count);
            return count;
        }
        
        private boolean isOldActiveSiteForSwitchover(Site site) {
            return site.getState().equals(SiteState.ACTIVE_SWITCHING_OVER);
        }
        
        private boolean isNewActiveSiteForSwitchover(Site site) {
            return site.getState().equals(SiteState.STANDBY_SWITCHING_OVER);
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
        public DrFailoverHandler() {
        }
        
        @Override
        public void execute() throws Exception {
            Site site = drUtil.getLocalSite();
            if (isNewActiveSiteForFailover(site)) {
                coordinator.stopCoordinatorSvcMonitor();
                reconfigVdc();
                coordinator.blockUntilZookeeperIsWritableConnected(FAILOVER_ZK_WRITALE_WAIT_INTERVAL);
                removeDbNodesOfOldActiveSite();
                waitForAllNodesAndReboot(site);
            } else {
                reconfigVdc();
            }
        }
        
        private boolean isNewActiveSiteForFailover(Site site) {
            return site.getState().equals(SiteState.STANDBY_FAILING_OVER);
        }
        
        private void removeDbNodesOfOldActiveSite() throws Exception {
            Site oldActiveSite = getOldActiveSiteForFailover();
            
            if (oldActiveSite == null) {
                log.info("Not failover case, no action needed.");
                return;
            }
            
            InterProcessLock lock = null;
            try {
                lock = coordinator.getCoordinatorClient().getLock(LOCK_FAILOVER_REMOVE_OLD_ACTIVE);
                log.info("Acquiring lock {}", LOCK_FAILOVER_REMOVE_OLD_ACTIVE);
                
                lock.acquire();
                log.info("Acquired lock {}", LOCK_FAILOVER_REMOVE_OLD_ACTIVE); 
        
                // double check site state
                oldActiveSite = getOldActiveSiteForFailover();
                if (oldActiveSite == null) {
                    log.info("Old acitve site has been remove by other node, no action needed.");
                    return;
                }

                poweroffRemoteSite(oldActiveSite);    
                removeDbNodesFromGossip(oldActiveSite);
                removeDbNodesFromStrategyOptions(oldActiveSite);
                drUtil.removeSiteConfiguration(oldActiveSite);
            } catch (Exception e) {
                populateStandbySiteErrorIfNecessary(drUtil.getLocalSite(), APIException.internalServerErrors.failoverReconfigFailed(e.getMessage()));
                log.error("Failed to remove old acitve site in failover, {}", e);
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
            coordinator.blockUntilZookeeperIsWritableConnected(SWITCHOVER_ZK_WRITALE_WAIT_INTERVAL);
            
            log.info("Wait for barrier to reboot cluster");
            VdcPropertyBarrier barrier = new VdcPropertyBarrier(Constants.SWITCHOVER_BARRIER, SWITCHOVER_BARRIER_TIMEOUT, site.getNodeCount(), true);
            barrier.enter();
            
            log.info("Reboot this node after failover");
            localRepository.reboot();
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
     */
    protected void flushVdcConfigToLocal() {
        PropertyInfoExt vdcProperty = new PropertyInfoExt(targetVdcPropInfo.getAllProperties());
        vdcProperty.addProperty(VdcConfigUtil.VDC_CONFIG_VERSION,
                String.valueOf(targetSiteInfo.getVdcConfigVersion()));
        localRepository.setVdcPropertyInfo(vdcProperty);
    }

    /**
     * Simulaneously flush vdc config on all nodes in current site. via barrier
     */
    protected void syncFlushVdcConfigToLocal() throws Exception {
        VdcPropertyBarrier vdcBarrier = new VdcPropertyBarrier(targetSiteInfo, VDC_RPOP_BARRIER_TIMEOUT);
        vdcBarrier.enter();
        try {
            flushVdcConfigToLocal();
        } finally {
            vdcBarrier.leave();
        }
    }
    
    /**
     * Reset vdc config version if some error happens during processing. So that the processing can enter again 
     * in next loop
     */
    protected void resetLocalVdcConfigVersion() {
        log.info("Reset config version for local vdc properties");
        PropertyInfoExt vdcProperty = new PropertyInfoExt(targetVdcPropInfo.getAllProperties());
        // set the vdc_config_version to an invalid value so that it always gets retried on failure.
        vdcProperty.addProperty(VdcConfigUtil.VDC_CONFIG_VERSION, "-1");
        localRepository.setVdcPropertyInfo(vdcProperty);
    }
    
    protected void reconfigVdc() throws Exception {
        syncFlushVdcConfigToLocal();
        try {
            refreshIPsec();
            refreshFirewall();
            refreshSsh();
            refreshCoordinator();
        } catch (Exception ex) {
            log.warn("Unexpected error happens during applying vdc config to local", ex);
            resetLocalVdcConfigVersion();    
        }
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
        localRepository.reconfigProperties("coordinator");
        localRepository.restart("coordinatorsvc");
    }
    
    /**
     * remove a site from cassandra gossip ring of dbsvc and geodbsvc
     */
    protected void removeDbNodesFromGossip(Site site) {
        String dcName = drUtil.getCassandraDcId(site);
        try (DbManagerOps dbOps = new DbManagerOps(Constants.DBSVC_NAME);
                DbManagerOps geodbOps = new DbManagerOps(Constants.GEODBSVC_NAME)) {
            if (!dbOps.isDataCenterUnreachable(dcName)) {
                throw new IllegalStateException(String.format("dbsvc of site %s is still reachable", dcName));
            }
            if (!geodbOps.isDataCenterUnreachable(dcName)) {
                throw new IllegalStateException(String.format("geodbsvc of site %s is still reachable", dcName));
            }
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

    protected void poweroffRemoteSite(Site site) {
        String siteId = site.getUuid();
        if (!drUtil.isSiteUp(siteId)) {
            log.info("Site {} is down. no need to poweroff it", site.getUuid());
            return;
        }
        // all syssvc shares same port
        String baseNodeURL = String.format(SysClientFactory.BASE_URL_FORMAT, site.getVip(), service.getEndpoint().getPort());
        SysClientFactory.getSysClient(URI.create(baseNodeURL)).post(URI.create(URI_INTERNAL_POWEROFF), null, null);
        log.info("Powering off site {}", siteId);
        while(drUtil.isSiteUp(siteId)) {
            log.info("Short sleep and will check site status later");
            retrySleep();
        }
    }
    
    protected void retrySleep() {
        waiter.sleep(SWITCHOVER_ZK_WRITALE_WAIT_INTERVAL);
    }
    
    protected void populateStandbySiteErrorIfNecessary(Site site, InternalServerErrorException e) {
        SiteError error = new SiteError(e);
        
        log.info("Set error state for site: {}", site.getUuid());
        coordinator.getCoordinatorClient().setTargetInfo(site.getUuid(),  error);

        site.setState(SiteState.STANDBY_ERROR);
        coordinator.getCoordinatorClient().persistServiceConfiguration(site.getUuid(), site.toConfiguration());
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
                log.info("All nodes entered VdcPropBarrier");
            } else {
                log.warn("Only Part of nodes entered within {} seconds", timeout);
                throw new Exception("Only Part of nodes entered within timeout");
            }
        }

        /**
         * Waiting for all nodes leaving the VdcPropBarrier.
         * @throws Exception
         */
        public void leave() throws Exception {
            // Even if part of nodes fail to leave this barrier within timeout, we still let it pass. The ipsec monitor will handle failure on other nodes.
            log.info("Waiting for all nodes leaving {}", barrierPath);

            boolean allLeft = barrier.leave(timeout, TimeUnit.SECONDS);
            if (allLeft) {
                log.info("All nodes left VdcPropBarrier");
            } else {
                log.warn("Only Part of nodes left VdcPropBarrier before timeout");
            }
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
