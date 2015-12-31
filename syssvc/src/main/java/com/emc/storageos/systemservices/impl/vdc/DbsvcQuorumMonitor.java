/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.vdc;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteInfo;
import com.emc.storageos.coordinator.client.model.SiteMonitorResult;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DrUtil;

public class DbsvcQuorumMonitor implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(DbsvcQuorumMonitor.class);

    private static final int STANDBY_DEGRADED_THRESHOLD = 1000 * 60 * 15; // 15 minutes, in milliseconds

    private DrUtil drUtil;
    private String myNodeId;
    private CoordinatorClient coordinatorClient;

    public DbsvcQuorumMonitor(DrUtil drUtil, String myNodeId, CoordinatorClient coordinatorClient) {
        this.drUtil = drUtil;
        this.myNodeId = myNodeId;
        this.coordinatorClient = coordinatorClient;
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
            if (!standbySite.getState().equals(SiteState.STANDBY_SYNCED)) {
                // skip those standby sites that are not synced yet
                continue;
            }

            SiteMonitorResult monitorResult = updateSiteMonitorResult(standbySite);
            if (monitorResult.getDbQuorumLostSince() == 0) {
                // Db quorum is intact
                continue;
            }

            if (System.currentTimeMillis() - monitorResult.getDbQuorumLostSince() >=
                    drUtil.getDrIntConfig(DrUtil.KEY_STANDBY_DEGRADE_THRESHOLD, STANDBY_DEGRADED_THRESHOLD)) {
                log.info("Db quorum lost over 15 minutes, degrading site {}");
                sitesToDegrade.add(standbySite);
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

    private SiteMonitorResult updateSiteMonitorResult(Site standbySite) {
        String siteId = standbySite.getUuid();
        SiteMonitorResult monitorResult = coordinatorClient.getTargetInfo(siteId, SiteMonitorResult.class);
        if (monitorResult == null) {
            monitorResult = new SiteMonitorResult();
        }

        boolean quorumLost = drUtil.isQuorumLost(standbySite, Constants.DBSVC_NAME) ||
                drUtil.isQuorumLost(standbySite, Constants.GEODBSVC_NAME);
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
