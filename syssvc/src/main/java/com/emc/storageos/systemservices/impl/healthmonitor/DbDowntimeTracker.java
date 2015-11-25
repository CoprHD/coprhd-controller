/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.healthmonitor;

import java.lang.String;
import java.util.List;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.service.InterProcessLockHolder;
import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.DbOfflineEventInfo;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.services.util.TimeUtils;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.storageos.systemservices.impl.jobs.common.JobConstants;

/**
 * DbDowntimeTracker is to track the downtime of dbsvc and geodbsvc.
 * It monitors dbsvc and geodbsvc online/offline event and record downtime in ZK
 */
public class DbDowntimeTracker {
    private static final Logger log = LoggerFactory.getLogger(DbDowntimeTracker.class);
    private List<String> serviceNames = Arrays.asList(Constants.DBSVC_NAME, Constants.GEODBSVC_NAME);
    private static final String DB_TRACKER_LOCK = "dbDowntimeTracker";
    // Tracker check service status every 15 mins by default
    private static final long TRACKER_CHECK_INTERVAL = JobConstants.LAG_BETWEEN_RUNS_ALERTS * TimeUtils.SECONDS;
    private static final long NO_NEED_UPDATE_LIMIT = 5 * TimeUtils.MINUTES;

    @Autowired
    private CoordinatorClientExt coordinator;

    public DbDowntimeTracker() {
    }

    /**
     * Monitor dbsvc and geodbsvc online/offline event and record downtime in ZK
     */
    public void run() {
        log.info("Monitoring dbsvc and geodbsvc status");
        try (AutoCloseable lock = getTrackerLock()) {
            for (String serviceName : serviceNames) {
                log.info("Check status for {} begin", serviceName);
                List<String> availableNodes = coordinator.getServiceAvailableNodes(serviceName);
                updateTrackerInfo(serviceName, availableNodes);
                log.info("Check status for {} finish", serviceName);
            }
        } catch (Exception e) {
            log.warn("Failed to monitor db status", e);
        }
    }

    private AutoCloseable getTrackerLock() throws Exception {
        return new InterProcessLockHolder(this.coordinator.getCoordinatorClient(), DB_TRACKER_LOCK, this.log);
    }

    /**
     * Update db offline event info in ZK.
     */
    private void updateTrackerInfo(String serviceName, List<String> activeNodes) {
        log.info("Querying db tracker info from zk");
        Configuration config = coordinator.getCoordinatorClient().queryConfiguration(
                Constants.DB_DOWNTIME_TRACKER_CONFIG, serviceName);
        DbOfflineEventInfo dbOfflineEventInfo = new DbOfflineEventInfo(config);

        long currentTimeStamp = TimeUtils.getCurrentTime();
        Long lastUpdateTimestamp = dbOfflineEventInfo.getLastUpdateTimestamp();
        lastUpdateTimestamp = (lastUpdateTimestamp == null) ? currentTimeStamp : lastUpdateTimestamp;
        long interval = Math.min((currentTimeStamp - lastUpdateTimestamp), TRACKER_CHECK_INTERVAL);
        if (interval < NO_NEED_UPDATE_LIMIT) {
            log.info("Have already updated within a few minutes, skipping this update");
        }

        dbOfflineEventInfo.setLastUpdateTimestamp(currentTimeStamp);
        log.info("Db tracker last check time: {}, current check time: {}", lastUpdateTimestamp, currentTimeStamp);

        int nodeCount = coordinator.getNodeCount();
        for (int i = 1; i <= nodeCount; i++) {
            String nodeId = "vipr" + i;
            if (activeNodes.contains(nodeId)) {
                dbOfflineEventInfo.setLastActiveTimestamp(nodeId, currentTimeStamp);
                log.info(String.format("Service(%s) of node(%s) last active timestamp has been updated to %s",
                        serviceName, nodeId, currentTimeStamp));

                if (dbOfflineEventInfo.getOfflineTimeInMS(nodeId) != null) {
                    dbOfflineEventInfo.setOfflineTimeInMS(nodeId, null);
                    log.info("Service({}) of node({}) is recovered", serviceName, nodeId);
                }
            } else {
                Long lastOfflineInMS = dbOfflineEventInfo.getOfflineTimeInMS(nodeId);
                lastOfflineInMS = (lastOfflineInMS == null) ? 0 : lastOfflineInMS;
                long newOfflineTime = lastOfflineInMS + interval;
                dbOfflineEventInfo.setOfflineTimeInMS(nodeId, newOfflineTime);
                log.info(String.format("Service(%s) of node(%s) has been unavailable for %s mins",
                        serviceName, nodeId, newOfflineTime / TimeUtils.MINUTES));
            }
        }
        config = dbOfflineEventInfo.toConfiguration(serviceName);
        coordinator.getCoordinatorClient().persistServiceConfiguration(config);
        log.info("Persist db tracker info to zk successfully");
    }
}
