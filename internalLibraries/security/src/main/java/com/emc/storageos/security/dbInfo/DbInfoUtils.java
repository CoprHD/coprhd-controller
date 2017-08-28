/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

/**
 * utils to check db/geodb related info
 */
package com.emc.storageos.security.dbInfo;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.DbOfflineEventInfo;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.services.util.AlertsLogger;
import com.emc.storageos.services.util.FileUtils;
import com.emc.storageos.services.util.PlatformUtils;
import com.emc.storageos.services.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import java.io.File;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public final class DbInfoUtils {
    private static final Logger _log = LoggerFactory.getLogger(DbInfoUtils.class);
    // Service outage time should be less than 5 days, or else service will not be allowed to get started any more.
    // As we checked the downtime every 15 mins, to avoid actual downtime undervalued, setting the max value as 4 days.
    public static final long MAX_SERVICE_OUTAGE_TIME = 4 * TimeUtils.DAYS;
    public static final List<String> serviceNames = Arrays.asList(Constants.DBSVC_NAME, Constants.GEODBSVC_NAME);

    private DbInfoUtils() {
    }
    /**
     * Check offline event info to see if dbsvc/geodbsvc on this node could get started
     */
    public static void checkDBOfflineInfo(CoordinatorClient coordinator, String serviceName ,String dbDir, boolean enableAlert) {

        DbOfflineEventInfo dbOfflineEventInfo = getDbOfflineEventInfo(coordinator, serviceName);

        String localNodeId = coordinator.getInetAddessLookupMap().getNodeId();
        Long lastActiveTimestamp = dbOfflineEventInfo.geLastActiveTimestamp(localNodeId);
        long zkTimeStamp = (lastActiveTimestamp == null) ? TimeUtils.getCurrentTime() : lastActiveTimestamp;

        File localDbDir = new File(dbDir);
        Date lastModified = FileUtils.getLastModified(localDbDir);
        boolean isDirEmpty =  lastModified == null || localDbDir.list().length == 0;
        long localTimeStamp = (isDirEmpty) ? TimeUtils.getCurrentTime() : lastModified.getTime();

        _log.info("Service timestamp in ZK is {}, local file is: {}", zkTimeStamp, localTimeStamp);
        long diffTime = (zkTimeStamp > localTimeStamp) ? (zkTimeStamp - localTimeStamp) : 0;
        checkDiffTime(diffTime, enableAlert);
        Long offlineTime = dbOfflineEventInfo.getOfflineTimeInMS(localNodeId);
        checkOfflineTime(offlineTime, isDirEmpty, enableAlert);
    }

    private static void checkDiffTime(long diffTime,boolean enableAlert) {
        if (diffTime >= MAX_SERVICE_OUTAGE_TIME) {
            String errMsg = String.format("We detect database files on local disk are more than %s days older " +
                    "than last time it was seen in the cluster. It may bring stale data into the database, " +
                    "so the service cannot continue to boot. It may be the result of a VM snapshot rollback. " +
                    "Please contact with EMC support engineer for solution.", diffTime/TimeUtils.DAYS);
            if (enableAlert) AlertsLogger.getAlertsLogger().error(errMsg);
            throw new java.lang.IllegalStateException(errMsg);
        }
    }

    private static void checkOfflineTime(Long offlineTime, boolean isDirEmpty, boolean enableAlert) {
        if (!isDirEmpty && offlineTime != null && offlineTime >= MAX_SERVICE_OUTAGE_TIME) {
            StringBuilder errMsgSb = new StringBuilder(String.format("This node is offline for more than %s days. ",offlineTime/TimeUtils.DAYS));
            errMsgSb.append("It may bring stale data into database, so the service cannot continue to boot. Please ");
            if (!PlatformUtils.isVMwareVapp()) {
                errMsgSb.append("poweroff this node and ");
            }
            errMsgSb.append("follow our node recovery procedure to recover this node");
            if (enableAlert) AlertsLogger.getAlertsLogger().error(errMsgSb.toString());
            throw new java.lang.IllegalStateException(errMsgSb.toString());
        }
    }

    public static Long getDbOfflineTime (CoordinatorClient coordinator, String serviceName, String nodeId) {
        DbOfflineEventInfo dbOfflineEventInfo = getDbOfflineEventInfo(coordinator, serviceName);
        return dbOfflineEventInfo.getOfflineTimeInMS(nodeId);
    }

    private static DbOfflineEventInfo getDbOfflineEventInfo (CoordinatorClient coordinator, String serviceName) {
        Configuration config = coordinator.queryConfiguration(coordinator.getSiteId(), Constants.DB_DOWNTIME_TRACKER_CONFIG,
                serviceName);
        return new DbOfflineEventInfo(config);
    }
}
