package com.emc.storageos.coordinator.client.model;

import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;

// Under /config/disasterRecoveryOperationStatus would have multiple nodes
public class DrOperationStatus {

    public static final String CONFIG_KIND = "disasterRecoveryOperationStatus";
    public static final String KEY_SITE_UUID = "siteUuid";
    public static final String KEY_INTER_STATE = "interState";

    public enum InterState {
        ADDING_STANDBY,
        REMOVING_STANDBY,
        PAUSING_STANDBY,
        RESUMING_STANDBY,
        SWITCHINGOVER_ACTIVE,
        SWITCHINGOVER_STANDBY,
        FAILINGOVER_STANDBY,
        DEGRADING_STANDBY,
        REJOINING_STANDBY
    }

    private String siteUuid;

    private InterState interState;

    public DrOperationStatus() {
    }

    public DrOperationStatus(Configuration config) {
        if (config != null) {
            fromConfiguration(config);
        }
    }

    public String getSiteUuid() {
        return siteUuid;
    }

    public void setSiteUuid(String siteUuid) {
        this.siteUuid = siteUuid;
    }

    public InterState getInterState() {
        return interState;
    }

    public void setInterState(InterState interState) {
        this.interState = interState;
    }

    public Configuration toConfiguration() {
        ConfigurationImpl config = new ConfigurationImpl();
        config.setKind(CONFIG_KIND);
        config.setId(siteUuid);
        config.setConfig(KEY_SITE_UUID, siteUuid);
        config.setConfig(KEY_INTER_STATE, interState.toString());
        return config;
    }

    private void fromConfiguration(Configuration config) {
        if (config == null) {
            throw new IllegalArgumentException("Can't parse from null config");
        }
        if (!CONFIG_KIND.equals(config.getKind())) {
            throw new IllegalArgumentException("Unexpected configuration kind for DrOperationStatus");
        }

        siteUuid = config.getConfig(KEY_SITE_UUID);
        interState = Enum.valueOf(InterState.class, config.getConfig(KEY_INTER_STATE));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Site [uuid=").append(siteUuid).append(",");
        sb.append("state=").append(interState.toString()).append("]");
        return sb.toString();
    }
}
