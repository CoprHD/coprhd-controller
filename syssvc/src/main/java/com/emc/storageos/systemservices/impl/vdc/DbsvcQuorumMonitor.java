/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.vdc;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteInfo;
import com.emc.storageos.coordinator.client.model.SiteMonitorResult;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.common.DbConfigConstants;

public class DbsvcQuorumMonitor implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(DbsvcQuorumMonitor.class);

    private static final int STANDBY_DEGRADED_THRESHOLD = 1000 * 60 * 15; // 15 minutes, in milliseconds

    private DrUtil drUtil;
    private String myNodeId;
    private CoordinatorClient coordinatorClient;
    private Properties dbCommonInfo;

    public DbsvcQuorumMonitor(DrUtil drUtil, String myNodeId, CoordinatorClient coordinatorClient,
                              Properties dbCommonInfo) {
        this.drUtil = drUtil;
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
                if (monitorResult.getDbQuorumLostSince() != 0
                        && System.currentTimeMillis() - monitorResult.getDbQuorumLostSince() >=
                        drUtil.getDrIntConfig(DrUtil.KEY_STANDBY_DEGRADE_THRESHOLD, STANDBY_DEGRADED_THRESHOLD)) {
                    log.info("Db quorum lost over 15 minutes, degrading site {}", standbySite.getUuid());
                    sitesToDegrade.add(standbySite);
                }
            }
        }

        // degrade all standby sites in a single batch
        if (!sitesToDegrade.isEmpty()) {
            for (Site standbySite : sitesToDegrade) {
                standbySite.setState(SiteState.STANDBY_DEGRADING);
                coordinatorClient.persistServiceConfiguration(standbySite.toConfiguration());
                drUtil.updateVdcTargetVersion(standbySite.getUuid(), SiteInfo.DR_OP_DEGRADE_STANDBY);
            }
            drUtil.updateVdcTargetVersion(coordinatorClient.getSiteId(), SiteInfo.DR_OP_DEGRADE_STANDBY);
        }
    }

    private void checkAndRejoinSite(Site standbySite) {
        // TODO: obtain a DR lock
        String siteId = standbySite.getUuid();
        int nodeCount = standbySite.getNodeCount();
        // We must wait until all the dbsvc/geodbsvc instances are back
        // or the data sync will fail anyways
        if (drUtil.getNumberOfLiveServices(siteId, Constants.DBSVC_NAME) == nodeCount ||
                drUtil.getNumberOfLiveServices(siteId, Constants.GEODBSVC_NAME) == nodeCount) {
            log.info("All the dbsvc/geodbsvc instances are back. Rejoining site {}", standbySite.getUuid());

            int gcGracePeriod = DbConfigConstants.DEFAULT_GC_GRACE_PERIOD;
            String strVal = dbCommonInfo.getProperty(DbClientImpl.DB_CASSANDRA_INDEX_GC_GRACE_PERIOD);
            if (strVal != null) {
                gcGracePeriod = Integer.parseInt(strVal);
            }
            SiteMonitorResult monitorResult = coordinatorClient.getTargetInfo(siteId, SiteMonitorResult.class);
            if ((System.currentTimeMillis() - monitorResult.getDbQuorumLostSince()) / 1000 >= gcGracePeriod
                    + drUtil.getDrIntConfig(DrUtil.KEY_STANDBY_DEGRADE_THRESHOLD, STANDBY_DEGRADED_THRESHOLD) / 1000) {
                log.error("site {} has been degraded for too long, we will re-init the target standby", siteId);
                standbySite.setState(SiteState.STANDBY_SYNCING);
                coordinatorClient.persistServiceConfiguration(standbySite.toConfiguration());
                drUtil.updateVdcTargetVersion(standbySite.getUuid(), SiteInfo.DR_OP_CHANGE_DATA_REVISION,
                        System.currentTimeMillis());
            } else {
                drUtil.updateVdcTargetVersion(standbySite.getUuid(), SiteInfo.DR_OP_REJOIN_STANDBY);
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
