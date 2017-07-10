/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.coordinator.client.model;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;

/**
 * This model has 2 usages:
 *   - Store meta data in memory parsed from metadata.properties
 *   - Hold meta data of new driver in ZK during upgrade
 */
public class StorageDriverMetaData {

    private static final String ENCODING_SEPERATOR = "\0";

    // key string definitions
    private static final String KEY_DRIVER_NAME = "driverName";
    private static final String KEY_DRIVER_VERSION = "driverVersion";
    private static final String KEY_STORAGE_NAME = "storageName";
    private static final String KEY_STORAGE_DISPLAY_NAME = "storageDisplayName";
    private static final String KEY_PROVIDER_NAME = "providerName";
    private static final String KEY_PROVIDER_DISPLAY_NAME = "providerDisplayName";
    private static final String KEY_META_TYPE = "metaType";
    private static final String KEY_ENABLE_SSL = "enableSsl";
    private static final String KEY_SSL_PORT = "sslPort";
    private static final String KEY_NON_SSL_PORT = "nonSslPort";
    private static final String KEY_DRIVER_CLASS_NAME = "driverClassName";
    private static final String KEY_DRIVER_FILE_NAME = "driverFileName";
    private static final String KEY_SUPPORT_AUTOTIER_POLICY = "supportAutoTierPolicy";
    private static final String KEY_SUPPORTED_STORAGE_PROFILES = "supportedStorageProfiles";

    private static final Logger log = LoggerFactory.getLogger(StorageDriverMetaData.class);

    // kind
    public static final String KIND = "toUpgradeDriver";

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
    private boolean supportAutoTierPolicy;
    private Set<String> supportedStorageProfiles = new HashSet<>();

    public Set<String> getSupportedStorageProfiles() {
        return supportedStorageProfiles;
    }

    public void setSupportedStorageProfiles(Set<String> supportedStorageProfiles) {
        this.supportedStorageProfiles = supportedStorageProfiles;
    }

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

    public boolean isSupportAutoTierPolicy() {
        return supportAutoTierPolicy;
    }

    public void setSupportAutoTierPolicy(boolean supportAutoTierPolicy) {
        this.supportAutoTierPolicy = supportAutoTierPolicy;
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
        config.setConfig(KEY_SUPPORT_AUTOTIER_POLICY, String.valueOf(supportAutoTierPolicy));

        if (driverClassName != null) {
            config.setConfig(KEY_DRIVER_CLASS_NAME, driverClassName);
        }
        if (driverFileName != null) {
            config.setConfig(KEY_DRIVER_FILE_NAME, driverFileName);
        }
        if (supportedStorageProfiles != null && !supportedStorageProfiles.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            for (String profile : supportedStorageProfiles) {
                builder.append(profile).append(ENCODING_SEPERATOR);
            }
            config.setConfig(KEY_SUPPORTED_STORAGE_PROFILES, builder.toString());
        }
        return config;
    }

    private void fromConfiguration(Configuration config) {
        if (!StringUtils.equals(KIND, config.getKind())) {
            log.error("Unexpected configuration kind for StorageDriverMetaData");
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
            String supportAutoTierStr = config.getConfig(KEY_SUPPORT_AUTOTIER_POLICY);
            if (supportAutoTierStr != null) {
                supportAutoTierPolicy = Boolean.valueOf(supportAutoTierStr);
            }
            driverClassName = config.getConfig(KEY_DRIVER_CLASS_NAME);
            driverFileName = config.getConfig(KEY_DRIVER_FILE_NAME);
            String supportedStorageProfilesStr = config.getConfig(KEY_SUPPORTED_STORAGE_PROFILES);
            if (supportedStorageProfilesStr != null) {
                for (String profile : supportedStorageProfilesStr.split(ENCODING_SEPERATOR)) {
                    supportedStorageProfiles.add(profile);
                }
            }
        } catch (Exception e) {
            log.error("Unrecognized configuration data for StorageDriverMetaData", e);
            throw new IllegalArgumentException("Unrecognized configuration data for StorageDriverMetaData", e);
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("StorageDriverMetaData [driverName=").append(driverName)
               .append(", driverVersion=") .append(driverVersion)
               .append(", storageName=").append(storageName)
               .append(", storaeDisplayName=").append(storageDisplayName)
               .append(", providerName=").append(providerName)
               .append(", providerDisplayName=").append(providerDisplayName)
               .append(", metaType=").append(metaType)
               .append(", enableSsl=").append(enableSsl)
               .append(", sslPort=").append(sslPort)
               .append(", nonSslPort=").append(nonSslPort)
               .append(", supportAutoTierPolicy").append(supportAutoTierPolicy)
               .append(", supportedStorageProfiles").append(supportedStorageProfiles)
               .append(", driverClassName=").append(driverClassName)
               .append(", driverFileName=").append(driverFileName);
        return builder.toString();
    }
}