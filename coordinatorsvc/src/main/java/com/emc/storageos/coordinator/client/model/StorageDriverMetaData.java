package com.emc.storageos.coordinator.client.model;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;

/**
 * This class is to hold meta data in ZK during storage driver upgrade use case
 * Explanation: In storage driver upgrade use case, coordinator node will store
 * new meta data in ZK, then uninstall old driver, after which meta data in ZK
 * will be fetched and inserted into DB and deleted from ZK.
 */
public class StorageDriverMetaData {

    // key string definitions
    private static final String KEY_DRIVER_NAME = "driverName";
    private static final String KEY_DRIVER_VERSION = "driverVersion";
    private static final String KEY_STORAGE_NAME = "storageName";
    private static final String KEY_STORAGE_DISPLAY_NAME = "storageDisplayName";
    private static final String KEY_PROVIDER_NAME = "providerName";
    private static final String KEY_PROVIDER_DISPLAY_NAME = "providerName";
    private static final String KEY_META_TYPE = "metaType";
    private static final String KEY_ENABLE_SSL = "enableSsl";
    private static final String KEY_SSL_PORT = "sslPort";
    private static final String KEY_NON_SSL_PORT = "nonSslPort";
    private static final String KEY_DRIVER_CLASS_NAME = "driverClassName";
    private static final String KEY_DRIVER_FILE_NAME = "driverFileName";

    // kind
    private static final String KIND = "toUpgradeDriver";

    // constructor
    public StorageDriverMetaData(Configuration cfg) {
        if (cfg != null) {
            fromConfiguration(cfg);
        }
    }

    public StorageDriverMetaData() {
    }

    // fields, getters, setters
    private String driverName;
    private String driverVersion;
    private String storageName;
    private String storageDisplayName;
    private String providerName;
    private String providerDisplayName;
    private String metaType;
    private boolean enableSsl;
    private long sslPort;
    private long nonSslPort;
    private String driverClassName;
    private String driverFileName;

    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }

    public String getDriverVersion() {
        return driverVersion;
    }

    public void setDriverVersion(String driverVersion) {
        this.driverVersion = driverVersion;
    }

    public String getStorageName() {
        return storageName;
    }

    public void setStorageName(String storageName) {
        this.storageName = storageName;
    }

    public String getStorageDisplayName() {
        return storageDisplayName;
    }

    public void setStorageDisplayName(String storageDisplayName) {
        this.storageDisplayName = storageDisplayName;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getProviderDisplayName() {
        return providerDisplayName;
    }

    public void setProviderDisplayName(String providerDisplayName) {
        this.providerDisplayName = providerDisplayName;
    }

    public String getMetaType() {
        return metaType;
    }

    public void setMetaType(String metaType) {
        this.metaType = metaType;
    }

    public boolean isEnableSsl() {
        return enableSsl;
    }

    public void setEnableSsl(boolean enableSsl) {
        this.enableSsl = enableSsl;
    }

    public long getSslPort() {
        return sslPort;
    }

    public void setSslPort(long sslPort) {
        this.sslPort = sslPort;
    }

    public long getNonSslPort() {
        return nonSslPort;
    }

    public void setNonSslPort(long nonSslPort) {
        this.nonSslPort = nonSslPort;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    public String getDriverFileName() {
        return driverFileName;
    }

    public void setDriverFileName(String driverFileName) {
        this.driverFileName = driverFileName;
    }

    // configuration converters
    public Configuration toConfiguration() {
        ConfigurationImpl config = new ConfigurationImpl();
        config.setKind(KIND);
        config.setId(driverName);
        if (driverName != null) {
            config.setConfig(KEY_DRIVER_NAME, driverName);
        }
        if (driverVersion != null) {
            config.setConfig(KEY_DRIVER_VERSION, driverVersion);
        }
        if (storageName != null) {
            config.setConfig(KEY_STORAGE_NAME, storageName);
        }
        if (storageDisplayName != null) {
            config.setConfig(KEY_STORAGE_DISPLAY_NAME, storageDisplayName);
        }
        if (providerName != null) {
            config.setConfig(KEY_PROVIDER_NAME, providerName);
        }
        if (providerDisplayName != null) {
            config.setConfig(KEY_PROVIDER_DISPLAY_NAME, providerDisplayName);
        }
        if (metaType != null) {
            config.setConfig(KEY_META_TYPE, metaType);
        }
        config.setConfig(KEY_ENABLE_SSL, String.valueOf(enableSsl));
        config.setConfig(KEY_SSL_PORT, String.valueOf(sslPort));
        config.setConfig(KEY_NON_SSL_PORT, String.valueOf(nonSslPort));

        if (driverClassName != null) {
            config.setConfig(KEY_DRIVER_CLASS_NAME, driverClassName);
        }
        if (driverFileName != null) {
            config.setConfig(KEY_DRIVER_FILE_NAME, driverFileName);
        }
        return config;
    }

    private void fromConfiguration(Configuration config) {
        if (StringUtils.equals(KIND, config.getKind())) {
            throw new IllegalArgumentException("Unexpected configuration kind for StorageDriverMetaData");
        }
        try {
            driverName = config.getConfig(KEY_DRIVER_NAME);
            driverVersion = config.getConfig(KEY_DRIVER_VERSION);
            storageName = config.getConfig(KEY_STORAGE_NAME);
            storageDisplayName = config.getConfig(KEY_STORAGE_DISPLAY_NAME);
            providerName = config.getConfig(KEY_PROVIDER_NAME);
            providerDisplayName = config.getConfig(KEY_PROVIDER_DISPLAY_NAME);
            metaType = config.getConfig(KEY_META_TYPE);
            String enableSslStr = config.getConfig(KEY_ENABLE_SSL);
            if (enableSslStr != null) {
                enableSsl = Boolean.valueOf(enableSslStr);
            }
            String sslPortStr = config.getConfig(KEY_SSL_PORT);
            if (sslPortStr != null) {
                sslPort = Long.valueOf(sslPortStr);
            }
            String nonSslPortStr = config.getConfig(KEY_NON_SSL_PORT);
            if (nonSslPortStr != null) {
                nonSslPort = Long.valueOf(nonSslPortStr);
            }
            driverClassName = config.getConfig(KEY_DRIVER_CLASS_NAME);
            driverFileName = config.getConfig(KEY_DRIVER_FILE_NAME);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unrecognized configuration data for StorageDriverMetaData", e);
        }
    }
}
