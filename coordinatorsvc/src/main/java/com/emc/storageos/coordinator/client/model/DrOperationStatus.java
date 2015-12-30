package com.emc.storageos.coordinator.client.model;

import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;

// Under /config/disasterRecoveryOperationStatus would have multiple nodes
public class DrOperationStatus {

    public static final String CONFIG_KIND = "disasterRecoveryOperationStatus";
    public static final String KEY_SITE_UUID = "siteUuid";
    public static final String KEY_SITE_STATE = "siteState";

    private String siteUuid;

    private SiteState siteState;

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

    public SiteState getSiteState() {
        return siteState;
    }

    public void setSiteState(SiteState siteState) {
        this.siteState = siteState;
    }

    public Configuration toConfiguration() {
        ConfigurationImpl config = new ConfigurationImpl();
        config.setKind(CONFIG_KIND);
        config.setId(siteUuid);
        config.setConfig(KEY_SITE_UUID, siteUuid);
        config.setConfig(KEY_SITE_STATE, siteState.toString());
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
        siteState = Enum.valueOf(SiteState.class, config.getConfig(KEY_SITE_STATE));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Site [uuid=").append(siteUuid).append(",");
        sb.append("state=").append(siteState.toString()).append("]");
        return sb.toString();
    }
}
