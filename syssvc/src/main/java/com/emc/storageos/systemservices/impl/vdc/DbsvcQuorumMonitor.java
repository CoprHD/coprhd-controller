/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.vdc;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.emc.storageos.coordinator.client.model.*;
import com.emc.storageos.db.client.util.VdcConfigUtil;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.common.DbConfigConstants;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * A thread scheduled in syssvc of the active site to monitor the db quorum availability of each standby site.
 * If a STANDBY_SYNCED site has lost db quorum for more than STANDBY_DEGRADED_THRESHOLD, it will be degraded.
 * If a STANDBY_DEGRADED site has all db instances running, it will be rejoined.
 */
public class DbsvcQuorumMonitor implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(DbsvcQuorumMonitor.class);

    private static final int STANDBY_DEGRADED_THRESHOLD = 1000 * 60 * 15; // 15 minutes, in milliseconds

    private DrUtil drUtil;
    private String myNodeId;
    private CoordinatorClient coordinatorClient;
    private Properties dbCommonInfo;

    public DbsvcQuorumMonitor(String myNodeId, CoordinatorClient coordinatorClient, Properties dbCommonInfo) {
        this.drUtil = new DrUtil(coordinatorClient);
        this.myNodeId = myNodeId;
        this.coordinatorClient = coordinatorClient;
        this.dbCommonInfo = dbCommonInfo;
    }

    @Override
    public void run() {
        String state = drUtil.getLocalCoordinatorMode(myNodeId);
        if (!DrUtil.ZOOKEEPER_MODE_LEADER.equals(state)) {
            log.info("Current node is not ZK leader. Do nothing");
            return;
        }

        List<Site> standbySites = drUtil.listStandbySites();
        List<Site> sitesToDegrade = new ArrayList<>();
        for (Site standbySite : standbySites) {
            SiteState siteState = standbySite.getState();
            if (siteState.equals(SiteState.STANDBY_DEGRADED)) {
                checkAndRejoinSite(standbySite);
            }

            if (siteState.equals(SiteState.STANDBY_SYNCED)) {
                SiteMonitorResult monitorResult = updateSiteMonitorResult(standbySite);
                long quorumLostTime = monitorResult.getDbQuorumLostSince();
                if (quorumLostTime != 0 && System.currentTimeMillis() - quorumLostTime >=
                        drUtil.getDrIntConfig(DrUtil.KEY_STANDBY_DEGRADE_THRESHOLD, STANDBY_DEGRADED_THRESHOLD)) {
                    log.info("Db quorum lost over 15 minutes, degrading site {}", standbySite.getUuid());
                    standbySite.setLastLostQuorumTime(quorumLostTime);
                    sitesToDegrade.add(standbySite);
                }
            }
        }

        // degrade all standby sites in a single batch
        if (!sitesToDegrade.isEmpty()) {
            degradeSites(sitesToDegrade);
        }
    }

    private void degradeSites(List<Site> sitesToDegrade) {
        InterProcessLock lock;
        try {
            lock = drUtil.getDROperationLock();
        } catch (APIException e) {
            log.warn("There are ongoing dr operations. Try again later.");
            return;
        }

        try {
            long vdcVersion = DrUtil.newVdcConfigVersion();

            // Update degraded sites
            for (Site standbySite : sitesToDegrade) {
                standbySite.setState(SiteState.STANDBY_DEGRADING);
                coordinatorClient.persistServiceConfiguration(standbySite.toConfiguration());
                drUtil.updateVdcTargetVersion(standbySite.getUuid(), SiteInfo.DR_OP_DEGRADE_STANDBY, vdcVersion);
            }

            // Update all other connected sites
            List<Site> connectedSites = getOtherConnectedSites(sitesToDegrade);
            for (Site site: connectedSites) {
                drUtil.updateVdcTargetVersion(site.getUuid(), SiteInfo.NONE, vdcVersion);
            }

            // Update local site
            drUtil.updateVdcTargetVersion(coordinatorClient.getSiteId(), SiteInfo.DR_OP_DEGRADE_STANDBY, vdcVersion);
        } catch (Exception e) {
            log.error("Failed to initiate degrade standby operation. Try again later", e);
        } finally {
            try {
                lock.release();
            } catch (Exception e) {
                log.error("Failed to release the dr operation lock", e);
            }
        }
    }

    private List<Site> getOtherConnectedSites(List<Site> excludedSites) {
        List<Site> sites = new ArrayList<>();

        for (Site site : drUtil.listStandbySites()) {
            if (!excludedSites.contains(site)) {
                sites.add(site);
            }

        }
        return sites;
    }

    private void checkAndRejoinSite(Site standbySite) {
        String siteId = standbySite.getUuid();
        int nodeCount = standbySite.getNodeCount();
        // We must wait until all the dbsvc/geodbsvc instances are back
        // or the data sync will fail anyways
        if (drUtil.getNumberOfLiveServices(siteId, Constants.DBSVC_NAME) == nodeCount ||
                drUtil.getNumberOfLiveServices(siteId, Constants.GEODBSVC_NAME) == nodeCount) {
            log.info("All the dbsvc/geodbsvc instances are back. Rejoining site {}", standbySite.getUuid());

            // in seconds
            int gcGracePeriod = DbConfigConstants.DEFAULT_GC_GRACE_PERIOD;
            String strVal = dbCommonInfo.getProperty(DbClientImpl.DB_CASSANDRA_INDEX_GC_GRACE_PERIOD);
            if (strVal != null) {
                gcGracePeriod = Integer.parseInt(strVal);
            }
            SiteMonitorResult monitorResult = coordinatorClient.getTargetInfo(siteId, SiteMonitorResult.class);

            InterProcessLock lock;
            try {
                lock = drUtil.getDROperationLock();
            } catch (APIException e) {
                log.warn("There are ongoing dr operations. Try again later.");
                return;
            }

            try {
                long vdcVersion = DrUtil.newVdcConfigVersion();

                // Update version on the target standby
                if ((System.currentTimeMillis() - monitorResult.getDbQuorumLostSince()) / 1000 >= gcGracePeriod
                        + drUtil.getDrIntConfig(DrUtil.KEY_STANDBY_DEGRADE_THRESHOLD, STANDBY_DEGRADED_THRESHOLD) / 1000) {
                    log.error("site {} has been degraded for too long, we will re-init the target standby", siteId);
                    standbySite.setState(SiteState.STANDBY_SYNCING);
                    coordinatorClient.persistServiceConfiguration(standbySite.toConfiguration());
                    drUtil.updateVdcTargetVersion(standbySite.getUuid(), SiteInfo.DR_OP_CHANGE_DATA_REVISION, vdcVersion);
                } else {
                    drUtil.updateVdcTargetVersion(standbySite.getUuid(), SiteInfo.DR_OP_REJOIN_STANDBY, vdcVersion);
                }

                // Update version on other connected standby sites if any
                for (Site site : drUtil.listSites()) {
                    if (site.equals(standbySite) ||
                            site.getUuid().equals(coordinatorClient.getSiteId())) { // target site or local site
                        continue;
                    }
                    drUtil.updateVdcTargetVersion(site.getUuid(), SiteInfo.NONE, vdcVersion);
                }

                // Update version on active site but do nothing
                drUtil.updateVdcTargetVersion(coordinatorClient.getSiteId(), SiteInfo.NONE, vdcVersion);
            } catch (Exception e) {
                log.error("Failed to initiate rejoin standby operation. Try again later", e);
            } finally {
                try {
                    lock.release();
                } catch (Exception e) {
                    log.error("Failed to release the dr operation lock", e);
                }
            }
        }
    }

    private SiteMonitorResult updateSiteMonitorResult(Site standbySite) {
        String siteId = standbySite.getUuid();
        SiteMonitorResult monitorResult = coordinatorClient.getTargetInfo(siteId, SiteMonitorResult.class);
        if (monitorResult == null) {
            monitorResult = new SiteMonitorResult();
        }

        int nodeCount = standbySite.getNodeCount();
        boolean quorumLost = drUtil.getNumberOfLiveServices(siteId, Constants.DBSVC_NAME) <= nodeCount / 2 ||
                drUtil.getNumberOfLiveServices(siteId, Constants.GEODBSVC_NAME) <= nodeCount / 2;
        if (quorumLost && monitorResult.getDbQuorumLostSince() == 0) {
            log.warn("Db quorum lost for site {}", siteId);
            monitorResult.setDbQuorumLostSince(System.currentTimeMillis());
            coordinatorClient.setTargetInfo(siteId, monitorResult);
        } else if (!quorumLost && monitorResult.getDbQuorumLostSince() != 0) {
            // reset the timer
            log.info("Db quorum restored for site {}", siteId);
            monitorResult.setDbQuorumLostSince(0);
            coordinatorClient.setTargetInfo(siteId, monitorResult);
        }
        return monitorResult;
    }
}
