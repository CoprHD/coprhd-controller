package com.emc.storageos.coordinator.client.model;

import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;

public class DisasterRecoveryConfig {
    public static final String CONFIG_KIND = "disasterRecoveryConfig";
    public static final String CONFIG_ID = "global";

    private static final String KEY_ADD_STANDBY_TIMEOUT = "add_standby_timeout_millis";
    private static final String KEY_REMOVE_STANDBY_TIMEOUT = "remove_standby_timeout_millis";
    private static final String KEY_PAUSE_STANDBY_TIMEOUT = "pause_standby_timout_millis";
    private static final String KEY_RESUME_STANDBY_TIMEOUT = "resume_standby_timeout_millis";
    private static final String KEY_DATA_SYNC_TIMEOUT = "data_sync_timeout_millis";
    private static final String KEY_SWITCHOVER_TIMEOUT = "switchover_timeout_millis";

    private int addStandbyTimeoutMillis = 20 * 60 * 1000;
    private int removeStandbyTimeoutMillis = 20 * 60 * 1000;
    private int pauseStandbyTimeoutMillis = 20 * 60 * 1000;
    private int resumeStandbyTimeoutMillis = 20 * 60 * 1000;
    private int dataSyncTimeoutMillis = 20 * 60 * 1000;
    private int switchoverTimeoutMillis = 20 * 60 * 1000;

    public DisasterRecoveryConfig() {
        // Keep all configuration items as default
    }

    public DisasterRecoveryConfig(Configuration config) {
        if (config == null || !config.getKind().equals(CONFIG_KIND) || !config.getId().equals(CONFIG_ID)) {
            return;
        }
        this.addStandbyTimeoutMillis = Integer.valueOf(config.getConfig(KEY_ADD_STANDBY_TIMEOUT));
        this.removeStandbyTimeoutMillis = Integer.valueOf(config.getConfig(KEY_REMOVE_STANDBY_TIMEOUT));
        this.pauseStandbyTimeoutMillis = Integer.valueOf(config.getConfig(KEY_PAUSE_STANDBY_TIMEOUT));
        this.resumeStandbyTimeoutMillis = Integer.valueOf(config.getConfig(KEY_RESUME_STANDBY_TIMEOUT));
        this.dataSyncTimeoutMillis = Integer.valueOf(config.getConfig(KEY_DATA_SYNC_TIMEOUT));
        this.switchoverTimeoutMillis = Integer.valueOf(config.getConfig(KEY_SWITCHOVER_TIMEOUT));
    }

    public int getAddStandbyTimeoutMillis() {
        return addStandbyTimeoutMillis;
    }

    public int getRemoveStandbyTimeoutMillis() {
        return removeStandbyTimeoutMillis;
    }

    public int getPauseStandbyTimeoutMillis() {
        return pauseStandbyTimeoutMillis;
    }

    public int getResumeStandbyTimeoutMillis() {
        return resumeStandbyTimeoutMillis;
    }

    public int getDataSyncTimeoutMillis() {
        return dataSyncTimeoutMillis;
    }

    public int getSwitchoverTimeoutMillis() {
        return switchoverTimeoutMillis;
    }

    public Configuration toConfiguration() {
        ConfigurationImpl config = new ConfigurationImpl();
        config.setKind(CONFIG_KIND);
        config.setId(CONFIG_ID);

        config.setConfig(KEY_ADD_STANDBY_TIMEOUT, String.valueOf(addStandbyTimeoutMillis));
        config.setConfig(KEY_REMOVE_STANDBY_TIMEOUT, String.valueOf(removeStandbyTimeoutMillis));
        config.setConfig(KEY_PAUSE_STANDBY_TIMEOUT, String.valueOf(pauseStandbyTimeoutMillis));
        config.setConfig(KEY_RESUME_STANDBY_TIMEOUT, String.valueOf(resumeStandbyTimeoutMillis));
        config.setConfig(KEY_DATA_SYNC_TIMEOUT, String.valueOf(dataSyncTimeoutMillis));
        config.setConfig(KEY_SWITCHOVER_TIMEOUT, String.valueOf(switchoverTimeoutMillis));

        return config;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("DisasterRecoveryConfig [ ");
        sb.append(KEY_ADD_STANDBY_TIMEOUT + "= ");
        sb.append(addStandbyTimeoutMillis);
        sb.append(", " + KEY_REMOVE_STANDBY_TIMEOUT + "= ");
        sb.append(removeStandbyTimeoutMillis);
        sb.append(", " + KEY_PAUSE_STANDBY_TIMEOUT + "= ");
        sb.append(pauseStandbyTimeoutMillis);
        sb.append(", " + KEY_RESUME_STANDBY_TIMEOUT + "= ");
        sb.append(resumeStandbyTimeoutMillis);
        sb.append(", " + KEY_DATA_SYNC_TIMEOUT + "= ");
        sb.append(dataSyncTimeoutMillis);
        sb.append(", " + KEY_SWITCHOVER_TIMEOUT + "= ");
        sb.append(switchoverTimeoutMillis);
        sb.append("]");
        return sb.toString();
    }
}
