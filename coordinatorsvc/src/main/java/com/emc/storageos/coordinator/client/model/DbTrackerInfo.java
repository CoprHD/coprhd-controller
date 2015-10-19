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
 * DB tracker info in ZK.
 * /config/downtimeTracker/dbsvc lastUpdateTimestamp=XXX
 * /config/downtimeTracker/dbsvc viprx-lastActiveTimestamp=XXX
 * /config/downtimeTracker/dbsvc viprx-offlineTimeInMS=XXX
 *
 * /config/downtimeTracker/geodbsvc lastUpdateTimestamp=XXX
 * /config/downtimeTracker/geodbsvc viprx-lastActiveTimestamp=XXX
 * /config/downtimeTracker/geodbsvc viprx-offlineTimeInMS=XXX
 */
public class DbTrackerInfo {
    private static final Logger log = LoggerFactory.getLogger(DbTrackerInfo.class);

    private static final String KEY_LAST_UPDATE_TIMESTAMP = "lastUpdateTimestamp";
    private static final String KEY_LAST_ACTIVE_TIMESTAMP = "lastActiveTimestamp";
    private static final String KEY_OFFLINE_TIME_IN_MS = "offlineTimeInMS";
    private static final String KEY_FORMAT = "%s-%s";

    private String configId;
    private Map<String, Long> trackerInfo;

    public DbTrackerInfo() {
    }

    public DbTrackerInfo(Configuration config) {
        if (config != null) {
            fromConfiguration(config);
        }
    }
    public Long getLastUpdateTimestamp() {
        return this.trackerInfo.get(KEY_LAST_UPDATE_TIMESTAMP);
    }

    public Long geLastActiveTimestamp(String nodeId) {
        String keyLastActiveTimestamp = String.format(KEY_FORMAT, nodeId, KEY_LAST_ACTIVE_TIMESTAMP);
        return this.trackerInfo.get(keyLastActiveTimestamp);
    }

    public Long getOfflineTimeInMS(String nodeId) {
        String keyOfflineTime = String.format(KEY_FORMAT, nodeId, KEY_OFFLINE_TIME_IN_MS);
        return this.trackerInfo.get(keyOfflineTime);
    }

    public void setLastUpdateTimestamp(long lastUpdateTimestamp) {
        this.trackerInfo.put(KEY_LAST_UPDATE_TIMESTAMP, lastUpdateTimestamp);
    }

    public void setLastActiveTimestamp(String nodeId, Long lastActiveTimestamp) {
        String keyLastActiveTimestamp = String.format(KEY_FORMAT, nodeId, KEY_LAST_ACTIVE_TIMESTAMP);
        if (lastActiveTimestamp == null) {
            this.trackerInfo.remove(keyLastActiveTimestamp);
        } else {
            this.trackerInfo.put(keyLastActiveTimestamp, lastActiveTimestamp);
        }
    }

    public void setOfflineTimeInMS(String nodeId, Long offlineTime) {
        String keyOfflineTime = String.format(KEY_FORMAT, nodeId, KEY_OFFLINE_TIME_IN_MS);
        if (offlineTime == null) {
            this.trackerInfo.remove(keyOfflineTime);
        } else {
            this.trackerInfo.put(keyOfflineTime, offlineTime);
        }
    }

    public Configuration toConfiguration() {
        ConfigurationImpl config = new ConfigurationImpl();
        config.setKind(Constants.DB_DOWNTIME_TRACKER_CONFIG);
        config.setId(this.configId);

        for (Map.Entry<String, Long> entry : trackerInfo.entrySet()) {
            config.setConfig(entry.getKey(), (entry.getValue() == null) ? null : String.valueOf(entry.getValue()));
        }
        return config;
    }

    private void fromConfiguration(Configuration config) {
        if (!config.getKind().equals(Constants.DB_DOWNTIME_TRACKER_CONFIG)) {
            throw new IllegalArgumentException("Unexpected configuration kind for DB tracker");
        }
        this.configId = config.getId();
        for (Map.Entry<String, String> entry : config.getAllConfigs(true).entrySet()) {
            this.trackerInfo.put(entry.getKey(), (entry.getValue() == null) ? null : Long.parseLong(entry.getValue()));
        }
    }
}
