/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.healthmonitor;

import java.lang.String;
import java.util.List;
import java.util.Arrays;

import com.emc.storageos.coordinator.client.model.DbTrackerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.service.InterProcessLockHolder;
import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.services.util.TimeUtils;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.storageos.systemservices.impl.jobs.JobConstants;

/**
 * DbDowntimeTracker is to track the downtime of dbsvc and geodbsvc.
 * It monitors dbsvc and geodbsvc online/offline event and record downtime in ZK
 */
public class DbDowntimeTracker {
    private static final Logger log = LoggerFactory.getLogger(DbDowntimeTracker.class);
    private List<String> serviceNames = Arrays.asList(Constants.DBSVC_NAME, Constants.GEODBSVC_NAME);
    private static final String DB_TRACKER_LOCK = "dbtracker";
    // Tracker check service status every 15 mins by default
    private static final long TRACKER_CHECK_INTERVAL = JobConstants.LAG_BETWEEN_RUNS_ALERTS * TimeUtils.SECONDS;

    @Autowired
    private CoordinatorClientExt coordinator;

    public DbDowntimeTracker() {
    }

    /**
     * Monitor dbsvc and geodbsvc online/offline event and record downtime in ZK
     */
    public void run() {
        log.info("Try to track dbsvc and geodbsvc status");
        try (AutoCloseable lock = getTrackerLock()) {
            for (String serviceName : serviceNames) {
                log.info("Track status for {} begin", serviceName);
                List<String> availableNodes = coordinator.getServiceAvailableNodes(serviceName);
                updateTrackerInfo(serviceName, availableNodes);
                log.info("Track status for {} finish", serviceName);
            }
        } catch (Exception e) {
            log.warn("Failed to track db status", e);
        }
    }

    private AutoCloseable getTrackerLock() throws Exception {
        return new InterProcessLockHolder(this.coordinator.getCoordinatorClient(), DB_TRACKER_LOCK, this.log);
    }

    /**
     * Update db tracker info in ZK.
     */
    private void updateTrackerInfo(String serviceName, List<String> activeNodes) {
        log.info("Query db tracker info from zk");
        Configuration config = coordinator.getCoordinatorClient().queryConfiguration(
                Constants.DB_DOWNTIME_TRACKER_CONFIG, serviceName);
        DbTrackerInfo dbTrackerInfo = new DbTrackerInfo(config);

        long currentTimeStamp = TimeUtils.getCurrentTime();
        Long lastUpdateTimestamp = dbTrackerInfo.getLastUpdateTimestamp();
        lastUpdateTimestamp = (lastUpdateTimestamp == null) ? currentTimeStamp : lastUpdateTimestamp;
        dbTrackerInfo.setLastUpdateTimestamp(currentTimeStamp);
        log.info("Db tracker last check time: {}, current check time: {}", lastUpdateTimestamp, currentTimeStamp);

        int nodeCount = coordinator.getNodeCount();
        for (int i = 1; i <= nodeCount; i++) {
            String nodeId = "vipr" + i;
            if (activeNodes.contains(nodeId)) {
                dbTrackerInfo.setLastActiveTimestamp(nodeId, currentTimeStamp);
                log.info(String.format("Service(%s) of node(%s) last active timestamp has been updated to %s",
                        serviceName, nodeId, currentTimeStamp));

                if (dbTrackerInfo.getOfflineTimeInMS(nodeId) != null) {
                    dbTrackerInfo.setOfflineTimeInMS(nodeId, null);
                    log.info("Service({}) of node({}) is recovered", serviceName, nodeId);
                }
            } else {
                Long lastOfflineInMS = dbTrackerInfo.getOfflineTimeInMS(nodeId);
                lastOfflineInMS = (lastOfflineInMS == null) ? 0 : lastOfflineInMS;
                long interval = Math.min((currentTimeStamp - lastUpdateTimestamp), TRACKER_CHECK_INTERVAL);
                long newOfflineTime = lastOfflineInMS + interval;
                log.info(String.format("Service(%s) of node(%s) has been unavailable for %s mins",
                        serviceName, nodeId, newOfflineTime / TimeUtils.MINUTES));
            }
        }
        config = dbTrackerInfo.toConfiguration();
        coordinator.getCoordinatorClient().persistServiceConfiguration(config);
        log.info("Persist service monitor info to zk successfully");
    }
}
