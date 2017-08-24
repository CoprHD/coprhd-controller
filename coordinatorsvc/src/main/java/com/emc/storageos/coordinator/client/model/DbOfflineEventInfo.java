/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.coordinator.client.model;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;

/**
 * The info of dbsvc and geodbsvc offline event in ZK.
 */
public class DbOfflineEventInfo {
    private static final Logger log = LoggerFactory.getLogger(DbOfflineEventInfo.class);

    private static final String KEY_LAST_UPDATE_TIME_IN_MS = "lastUpdateTimeInMS";
    private static final String KEY_LAST_ACTIVE_TIME_IN_MS = "lastActiveTimeInMS";
    private static final String KEY_OFFLINE_TIME_IN_MS = "offlineTimeInMS";
    private static final String KEY_OFFLINE_ALERT_IN_DAY = "offlineAlertInDay";
    private static final String KEY_FORMAT = "%s_%s";

    private Map<String, Long> eventInfo = new HashMap<String, Long>();

    public DbOfflineEventInfo() {
    }

    public DbOfflineEventInfo(Configuration config) {
        if (config != null) {
            fromConfiguration(config);
        }
    }
    public Long getLastUpdateTimestamp() {
        return this.eventInfo.get(KEY_LAST_UPDATE_TIME_IN_MS);
    }

    public Long geLastActiveTimestamp(String nodeId) {
        String keyLastActiveTimestamp = String.format(KEY_FORMAT, nodeId, KEY_LAST_ACTIVE_TIME_IN_MS);
        return this.eventInfo.get(keyLastActiveTimestamp);
    }

    public Long getOfflineTimeInMS(String nodeId) {
        String keyOfflineTime = String.format(KEY_FORMAT, nodeId, KEY_OFFLINE_TIME_IN_MS);
        return this.eventInfo.get(keyOfflineTime);
    }

    public void setLastUpdateTimestamp(long lastUpdateTimestamp) {
        this.eventInfo.put(KEY_LAST_UPDATE_TIME_IN_MS, lastUpdateTimestamp);
    }

    public void setLastActiveTimestamp(String nodeId, long lastActiveTimestamp) {
        String keyLastActiveTimestamp = String.format(KEY_FORMAT, nodeId, KEY_LAST_ACTIVE_TIME_IN_MS);
        this.eventInfo.put(keyLastActiveTimestamp, lastActiveTimestamp);
    }

    public void setOfflineTimeInMS(String nodeId, Long offlineTime) {
        String keyOfflineTime = String.format(KEY_FORMAT, nodeId, KEY_OFFLINE_TIME_IN_MS);
        if (offlineTime == null) {
            this.eventInfo.remove(keyOfflineTime);
        } else {
            this.eventInfo.put(keyOfflineTime, offlineTime);
        }
    }

    public Long getOfflineAlertInDay(String nodeId) {
        String keyOfflineTime = String.format(KEY_FORMAT, nodeId, KEY_OFFLINE_ALERT_IN_DAY);
        return this.eventInfo.get(keyOfflineTime);
    }

    public void setKeyOfflineAlertInDay(String nodeId,Long alertDays) {
        String keyAlertInDay = String.format(KEY_FORMAT, nodeId, KEY_OFFLINE_ALERT_IN_DAY);
        if (alertDays == null) {
            this.eventInfo.remove(keyAlertInDay);
        } else {
            this.eventInfo.put(keyAlertInDay, alertDays);
        }
    }

    public Configuration toConfiguration(String configId) {
        ConfigurationImpl config = new ConfigurationImpl();
        config.setKind(Constants.DB_DOWNTIME_TRACKER_CONFIG);
        config.setId(configId);

        log.info("Set DB offline event info to ZK config: {}", eventInfo);
        for (Map.Entry<String, Long> entry : eventInfo.entrySet()) {
            config.setConfig(entry.getKey(), (entry.getValue() == null) ? null : String.valueOf(entry.getValue()));
        }
        return config;
    }

    private void fromConfiguration(Configuration config) {
        if (!config.getKind().equals(Constants.DB_DOWNTIME_TRACKER_CONFIG)) {
            throw new IllegalArgumentException("Unexpected configuration kind for DB tracker");
        }
        for (Map.Entry<String, String> entry : config.getAllConfigs(true).entrySet()) {
            this.eventInfo.put(entry.getKey(), (entry.getValue() == null) ? null : Long.parseLong(entry.getValue()));
        }
        log.info("Get DB offline event info from ZK config: {}", eventInfo);
    }

    public Map<String,Long> getEventInfo() {
        return eventInfo;
    }
}
