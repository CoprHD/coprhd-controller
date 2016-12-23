/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.util;

import java.util.Arrays;
import java.util.List;

import com.emc.storageos.services.util.AlertsLogger;
import com.emc.storageos.security.dbInfo.DbInfoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.DbOfflineEventInfo;
import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.coordinator.client.service.InterProcessLockHolder;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.services.util.TimeUtils;
import com.emc.storageos.systemservices.impl.jobs.common.JobConstants;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;

/**
 * DbDowntimeTracker is to track the downtime of dbsvc and geodbsvc.
 * It monitors dbsvc and geodbsvc online/offline event and record downtime in ZK
 */
public class DbDowntimeTracker {
    private static final Logger log = LoggerFactory.getLogger(DbDowntimeTracker.class);
    private AlertsLogger _alertLog = AlertsLogger.getAlertsLogger();
    private List<String> serviceNames = Arrays.asList(Constants.DBSVC_NAME, Constants.GEODBSVC_NAME);
    private static final String DB_TRACKER_LOCK = "dbDowntimeTracker";
    // Tracker check service status every 15 mins by default
    private static final long TRACKER_CHECK_INTERVAL = JobConstants.LAG_BETWEEN_RUNS_ALERTS * TimeUtils.SECONDS;
    private static final long NO_NEED_UPDATE_LIMIT = 5 * TimeUtils.MINUTES;

    @Autowired
    private CoordinatorClientExt coordinator;

    @Autowired
    private MailHandler mailHandler;

    public DbDowntimeTracker() {
    }

    /**
     * Monitor dbsvc and geodbsvc online/offline event and record downtime in ZK
     */
    public void run() {
        DrUtil drUtil = new DrUtil(coordinator.getCoordinatorClient());
        if (drUtil.isStandby()) {
            log.info("Current site is standby, no need to monitor dbsvc and geodbsvc status");
            return;
        }
        log.info("Monitoring dbsvc and geodbsvc status");
        try (AutoCloseable lock = getTrackerLock()) {
            for (Site site : drUtil.listSites()) {
                updateSiteDbsvcStatus(site);
            }
        } catch (Exception e) {
            log.warn("Failed to monitor db status", e);
        }
    }

    private void updateSiteDbsvcStatus(Site site) {
        String siteId = site.getUuid();
        log.info("Start to check db/geodb status for site {}", siteId);
        for (String serviceName : serviceNames) {
            log.info("Check status for {} begin, site id: {}", serviceName, siteId);
            List<String> availableNodes = coordinator.getServiceAvailableNodes(siteId, serviceName);
            updateTrackerInfo(site, serviceName, availableNodes);
            log.info("Check status for {} finish, site id: {}", serviceName, siteId);
        }
    }

    private AutoCloseable getTrackerLock() throws Exception {
        return new InterProcessLockHolder(this.coordinator.getCoordinatorClient(), DB_TRACKER_LOCK, this.log);
    }

    /**
     * Update db offline event info in ZK.
     */
    private void updateTrackerInfo(Site site, String serviceName, List<String> activeNodes) {
        String siteId = site.getUuid();
        log.info("Querying db tracker info from zk");
        Configuration config = coordinator.getCoordinatorClient().queryConfiguration(siteId,
                Constants.DB_DOWNTIME_TRACKER_CONFIG, serviceName);
        DbOfflineEventInfo dbOfflineEventInfo = new DbOfflineEventInfo(config);

        long currentTimeStamp = TimeUtils.getCurrentTime();
        Long lastUpdateTimestamp = dbOfflineEventInfo.getLastUpdateTimestamp();
        long interval = 0L;
        if (lastUpdateTimestamp != null) {
            interval = Math.min((currentTimeStamp - lastUpdateTimestamp), TRACKER_CHECK_INTERVAL);
        }
        if (interval != 0L && interval < NO_NEED_UPDATE_LIMIT) {
            log.info("Have already updated within a few minutes, skipping this update");
            return;
        }

        dbOfflineEventInfo.setLastUpdateTimestamp(currentTimeStamp);
        log.info(String.format("Db tracker last check time: %d, current check time: %d, site: %s", lastUpdateTimestamp, currentTimeStamp, siteId));

        int nodeCount = site.getNodeCount();
        for (int i = 1; i <= nodeCount; i++) {
            String nodeId = "vipr" + i;
            if (activeNodes.contains(nodeId)) {
                dbOfflineEventInfo.setLastActiveTimestamp(nodeId, currentTimeStamp);
                log.info(String.format("Service(%s) of node(%s) last active timestamp has been updated to %s",
                        serviceName, nodeId, currentTimeStamp));

                if (dbOfflineEventInfo.getOfflineTimeInMS(nodeId) != null) {
                    dbOfflineEventInfo.setOfflineTimeInMS(nodeId, null);
                    dbOfflineEventInfo.setKeyOfflineAlertInDay(nodeId, null);
                    log.info("Service({}) of node({}) is recovered", serviceName, nodeId);
                }
            } else {
                Long lastOfflineInMS = dbOfflineEventInfo.getOfflineTimeInMS(nodeId);
                lastOfflineInMS = (lastOfflineInMS == null) ? 0 : lastOfflineInMS;
                long newOfflineTime = lastOfflineInMS + interval;
                dbOfflineEventInfo.setOfflineTimeInMS(nodeId, newOfflineTime);
                alertStatusCheck(nodeId, serviceName, dbOfflineEventInfo, newOfflineTime / TimeUtils.DAYS);
                log.info(String.format("Service(%s) of node(%s) has been unavailable for %s mins",
                        serviceName, nodeId, newOfflineTime / TimeUtils.MINUTES));
            }
        }
        config = dbOfflineEventInfo.toConfiguration(serviceName);
        coordinator.getCoordinatorClient().persistServiceConfiguration(siteId, config);
        log.info("Persist db tracker info to zk successfully");
    }
    private void alertStatusCheck(String nodeId, String serviceName, DbOfflineEventInfo dbOfflineEventInfo, long offLineTimeInDay) {
        if (offLineTimeInDay < 1) return ;
        Long alertDays = dbOfflineEventInfo.getOfflineAlertInDay(nodeId);
        if (alertDays != null) {
            if (offLineTimeInDay > alertDays) {
                if (offLineTimeInDay <= DbInfoUtils.MAX_SERVICE_OUTAGE_TIME) {
                    _alertLog.warn(String.format("DataBase service(%s) of node(%s) has been unavailable for %s days," +
                                    "please power on the node in timely manner",
                            serviceName, nodeId, offLineTimeInDay));
                    //send mail alert
                    sendDbsvcOfflineMail(nodeId, serviceName, offLineTimeInDay, false);
                }else {
                    //send mail alert with link
                    _alertLog.warn(String.format("DataBase service(%s) of node(%s) has been unavailable for %s days" +
                                    "node recovery would be needed to recovery it back",
                            serviceName, nodeId, offLineTimeInDay));
                    sendDbsvcOfflineMail(nodeId, serviceName, offLineTimeInDay, true);
                }
                dbOfflineEventInfo.setKeyOfflineAlertInDay(nodeId, offLineTimeInDay);
            }
        }else {
            _alertLog.warn(String.format("DataBase service(%s) of node(%s) has been unavailable for %s days," +
                            "please power on the node in timely manner",
                    serviceName, nodeId, offLineTimeInDay));
            dbOfflineEventInfo.setKeyOfflineAlertInDay(nodeId, offLineTimeInDay);
        }

    }

    private void sendDbsvcOfflineMail(String nodeId , String serviceNames, long offlineDays, boolean nodeRecoverable) {
        try {
            mailHandler.sendDbsvcOfflineMail(nodeId, serviceNames, offlineDays, nodeRecoverable);
        }catch (Exception e ) {
            log.error("Failed to sending mail for db offline alert", e);
        }
    }
}
