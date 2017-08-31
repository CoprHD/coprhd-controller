/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.util;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.StorageDriverMetaData;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.StorageSystemType;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.storagedriver.StorageProfile;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

public final class DriverMetadataUtil {

    public static final String META_DEF_FILE_NAME = "metadata.properties";

    private static final Logger log = LoggerFactory.getLogger(DriverMetadataUtil.class);

    private static final int MAX_DISPLAY_STRING_LENGTH = 50;
    private static final int MAX_NON_NATIVE_DRIVER_NUMBER = 25;
    private static final int DRIVER_VERSION_NUM_SIZE = 4;
    private static final String DRIVER_NAME = "driver_name";
    private static final String DRIVER_VERSION = "driver_version";
    private static final Pattern DRIVER_VERSION_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.\\d+\\.\\d+$");
    private static final String STORAGE_NAME = "storage_name";
    private static final String STORAGE_DISPLAY_NAME = "storage_display_name";
    private static final String PROVIDER_NAME = "provider_name";
    private static final String PROVIDER_DISPLAY_NAME = "provider_display_name";
    private static final String STORAGE_META_TYPE = "meta_type";
    private static final String ENABLE_SSL = "enable_ssl";
    private static final String NON_SSL_PORT = "non_ssl_port";
    private static final String SSL_PORT = "ssl_port";
    private static final String DRIVER_CLASS_NAME = "driver_class_name";
    private static final String SUPPORT_AUTO_TIER_POLICY = "support_auto_tier_policy";
    private static final String SUPPORTED_STORAGE_PROFILES = "supported_storage_profiles";
    private static final Set<String> VALID_META_TYPES = new HashSet<String>(
            Arrays.asList(new String[] { "block", "file", "block_and_file", "object" }));

    private DriverMetadataUtil() {
    }

    /**
     * meta data will be inserted after parsing and checking if it doesn't exist;
     * otherwise, this is regarded as an upgrade case, new driver version should
     * be higher, under which condition, meta data will be substituted.
     *
     * @param props properties loaded from in-tree driver jar's metadata.properties file
     * @param driverFileName name or driver jar file that contains metadata.properties file
     */
    public static void insertIntreeDriverMetadata(Properties props, String driverFileName, DbClient dbClient) {
        try {
            StorageDriverMetaData metaData = parseMetadata(props, driverFileName);
            metaData.setNative(true);

            List<StorageSystemType> types = getTypesByDriverName(metaData.getDriverName(), dbClient);
            if (types.isEmpty()) { 
                precheckForMetaData(metaData, dbClient, false, false);
            } else {
                precheckForMetaData(metaData, dbClient, true, false);
                // Remote old metadata first if new one has newer version
                dbClient.removeObject(types.toArray(new StorageSystemType[types.size()]));
            }

            // Insert new meta data
            types = new ArrayList<>();
            for (StorageSystemType type : DriverMetadataUtil.map(metaData)) {
                type.setDriverStatus(StorageSystemType.STATUS.ACTIVE.toString());
                type.setIsNative(true);
                types.add(type);
            }
            dbClient.createObject(types);
        } catch (Exception e) {
            log.warn("Failed to insert meta data parsed from {}. It's normal if it's caused by same meta data version.",
                    driverFileName, e);
        }
    }

    /**
     * @return supported StorageSystemTypes by this driver. The first is the
     *         storage system, and the second is storage provider if there is.
     */
    public static List<StorageSystemType> map(StorageDriverMetaData driver) {
        List<StorageSystemType> types = new ArrayList<StorageSystemType>();
        StorageSystemType type = new StorageSystemType();
        type.setStorageTypeName(driver.getStorageName());
        type.setStorageTypeDispName(driver.getStorageDisplayName());
        type.setDriverName(driver.getDriverName());
        type.setDriverVersion(driver.getDriverVersion());
        type.setDriverFileName(driver.getDriverFileName());
        type.setMetaType(driver.getMetaType());
        URI uri = URIUtil.createId(StorageSystemType.class);
        type.setId(uri);
        type.setStorageTypeId(uri.toString());
        type.setIsDefaultSsl(driver.isEnableSsl());
        type.setSslPort(Long.toString(driver.getSslPort()));
        type.setNonSslPort(Long.toString(driver.getNonSslPort()));
        type.setSupportAutoTierPolicy(driver.isSupportAutoTierPolicy());
        type.setSupportedStorageProfiles(new StringSet(driver.getSupportedStorageProfiles()));
        type.setDriverClassName(driver.getDriverClassName());
        types.add(type);

        if (StringUtils.isNotEmpty(driver.getProviderName())
                && StringUtils.isNotEmpty(driver.getProviderDisplayName())) {
            StorageSystemType provider = new StorageSystemType();
            provider.setStorageTypeName(driver.getProviderName());
            provider.setStorageTypeDispName(driver.getProviderDisplayName());
            provider.setIsSmiProvider(true);
            provider.setDriverName(driver.getDriverName());
            provider.setDriverVersion(driver.getDriverVersion());
            provider.setDriverFileName(driver.getDriverFileName());
            provider.setMetaType(driver.getMetaType());
            uri = URIUtil.createId(StorageSystemType.class);
            provider.setId(uri);
            provider.setStorageTypeId(uri.toString());
            provider.setIsDefaultSsl(driver.isEnableSsl());
            provider.setSslPort(Long.toString(driver.getSslPort()));
            provider.setNonSslPort(Long.toString(driver.getNonSslPort()));
            provider.setSupportAutoTierPolicy(driver.isSupportAutoTierPolicy());
            provider.setSupportedStorageProfiles(new StringSet(driver.getSupportedStorageProfiles()));
            provider.setDriverClassName(driver.getDriverClassName());
            type.setManagedBy(provider.getStorageTypeId());
            types.add(provider);
        }
        return types;
    }

    public static StorageDriverMetaData parseMetadata(Properties props, String driverFileName) {
        StorageDriverMetaData metaData = new StorageDriverMetaData();
        // check driver name
        String driverName = props.getProperty(DRIVER_NAME);
        precheckForDriverName(driverName);
        metaData.setDriverName(driverName);
        // check driver version and format
        String driverVersion = props.getProperty(DRIVER_VERSION);
        precheckForDriverVersion(driverVersion);
        metaData.setDriverVersion(driverVersion);
        // check storage name
        String storageName = props.getProperty(STORAGE_NAME);
        precheckForNotEmptyField("storage_name", storageName);
        metaData.setStorageName(storageName);
        // check storage display name
        String storageDisplayName = props.getProperty(STORAGE_DISPLAY_NAME);
        precheckForNotEmptyField("storage_display_name", storageDisplayName);
        metaData.setStorageDisplayName(storageDisplayName);
        // check provider name and provider display name
        String providerName = props.getProperty(PROVIDER_NAME);
        String providerDisplayName = props.getProperty(PROVIDER_DISPLAY_NAME);
        precheckForProviderName(providerName, providerDisplayName, metaData);
        // check meta type
        String metaType = props.getProperty(STORAGE_META_TYPE);
        precheckForMetaType(metaType);
        metaData.setMetaType(metaType.toLowerCase());
        // check enable_ssl
        String enableSslStr = props.getProperty(ENABLE_SSL);
        if (StringUtils.isNotEmpty(enableSslStr)) {
            boolean enableSsl = Boolean.valueOf(enableSslStr);
            metaData.setEnableSsl(enableSsl);
        } else {
            // default to false
            metaData.setEnableSsl(false);
        }
        // check ssl port
        try {
            String sslPortStr = props.getProperty(SSL_PORT);
            if (StringUtils.isNotEmpty(sslPortStr)) {
                long sslPort = 0L;
                sslPort = Long.valueOf(sslPortStr);
                metaData.setSslPort(sslPort);
            }
        } catch (NumberFormatException e) {
            throw APIException.internalServerErrors.installDriverPrecheckFailed("SSL port format is not valid");
        }
        // check non ssl port
        try {
            String nonSslPortStr = props.getProperty(NON_SSL_PORT);
            if (StringUtils.isNotEmpty(nonSslPortStr)) {
                long nonSslPort = 0L;
                nonSslPort = Long.valueOf(nonSslPortStr);
                metaData.setNonSslPort(nonSslPort);
            }
        } catch (NumberFormatException e) {
            throw APIException.internalServerErrors.installDriverPrecheckFailed("SSL port format is not valid");
        }
        // check supported storage profiles
        try {
            String supportedStorageProfilesStr = props.getProperty(SUPPORTED_STORAGE_PROFILES);
            precheckForNotEmptyField("supported_storage_profiles", supportedStorageProfilesStr);
            for (String profileStr : supportedStorageProfilesStr.split(",")) {
                StorageProfile profile = Enum.valueOf(StorageProfile.class, profileStr);
                metaData.getSupportedStorageProfiles().add(profile.toString());
            }
        } catch (IllegalArgumentException | NullPointerException e) {
            throw APIException.internalServerErrors.installDriverPrecheckFailed("Supported storage profiles value are not valid");
        }
        // check driver class name
        String driverClassName = props.getProperty(DRIVER_CLASS_NAME);
        precheckForNotEmptyField("driver_class_name", driverClassName);
        metaData.setDriverClassName(driverClassName);
        // check if support auto-tier policy
        String supportAutoTierStr = props.getProperty(SUPPORT_AUTO_TIER_POLICY);
        if (StringUtils.isNotEmpty(supportAutoTierStr)) {
            boolean supportAutoTierPolicy = Boolean.valueOf(supportAutoTierStr);
            metaData.setSupportAutoTierPolicy(supportAutoTierPolicy);
        } else {
            // default to false
            metaData.setSupportAutoTierPolicy(false);
        }
        metaData.setDriverFileName(driverFileName);
        log.info("Parsed result from jar file: {}", metaData.toString());
        return metaData;
    }

    public static Set<String> getAllDriverNames(DbClient dbClient) {
        List<StorageSystemType> types = listStorageSystemTypes(dbClient);
        Set<String> drivers = new HashSet<String>();
        for (StorageSystemType type : types) {
            drivers.add(type.getDriverName());
        }
        return drivers;
    }

    public static List<StorageSystemType> getTypesByDriverName(String driverName, DbClient dbClient) {
        List<StorageSystemType> types = new ArrayList<>();
        for (StorageSystemType type : listStorageSystemTypes(dbClient)) {
            if (StringUtils.equals(driverName, type.getDriverName())) {
                types.add(type);
            }
        }
        return types;
    }

    public static List<StorageSystemType> listStorageSystemTypes(DbClient dbClient) {
        List<StorageSystemType> result = new ArrayList<StorageSystemType>();
        List<URI> ids = dbClient.queryByType(StorageSystemType.class, true);
        Iterator<StorageSystemType> it = dbClient.queryIterativeObjects(StorageSystemType.class, ids);
        while (it.hasNext()) {
            result.add(it.next());
        }
        return result;
    }

    public static boolean hasForbiddenChar(String name) {
        for (int i = 0; i < name.length(); i ++) {
            char c = name.charAt(i);
            if (c != '_' && c != '-' && !Character.isLetter(c) && !Character.isDigit(c)) {
                return true;
            }
        }
        return false;
    }

    public static void precheckForNotEmptyField(String fieldName, String value) {
        if (StringUtils.isEmpty(value)) {
            throw APIException.internalServerErrors
                    .installDriverPrecheckFailed(String.format("%s field value is not provided", fieldName));
        }
    }

    public static void precheckForDupField(String existingValue, String newValue, String fieldName) {
        if (StringUtils.equals(existingValue, newValue)) {
            throw APIException.internalServerErrors.installDriverPrecheckFailed(
                    String.format("duplicate %s: %s", fieldName, newValue));
        }
    }

    public static void precheckForMetaData(StorageDriverMetaData metaData, DbClient dbClient) {
        precheckForMetaData(metaData, dbClient, false, false);
    }

    public static void precheckForMetaData(StorageDriverMetaData metaData, DbClient dbClient,
            boolean upgrade, boolean force) {
        Set<String> nonNativeDrivers = new HashSet<String>();
        boolean driverNameExists = false;
        List<StorageSystemType> types = listStorageSystemTypes(dbClient);
        for (StorageSystemType type : types) {
            if (upgrade && StringUtils.equals(type.getDriverName(), metaData.getDriverName())) {
                driverNameExists = true;
                if (!force) {
                    String oldVersion = type.getDriverVersion();
                    String newVersion = metaData.getDriverVersion();
                    compareVersion(oldVersion, newVersion);
                }
            } else {
                precheckForDupField(type.getDriverName(), metaData.getDriverName(), "driver name");
                precheckForDupField(type.getStorageTypeName(), metaData.getStorageName(), "storage name");
                precheckForDupField(type.getStorageTypeDispName(), metaData.getStorageDisplayName(), "display name");
                precheckForDupField(type.getStorageTypeName(), metaData.getProviderName(), "provider name");
                precheckForDupField(type.getStorageTypeDispName(), metaData.getProviderDisplayName(), "provider display name");
                precheckForDupField(type.getDriverClassName(), metaData.getDriverClassName(), "driver class name");
            }
            if (!(metaData.isNative() && upgrade)) {
                // bypass duplicate driver file checking when upgrading in-tree driver
                precheckForDupField(type.getDriverFileName(), metaData.getDriverFileName(), "driver file name");
            }

            if (type.getIsNative() != null && type.getIsNative() == false) {
                nonNativeDrivers.add(type.getDriverName());
            }
        }
        if (upgrade && !driverNameExists) {
            throw APIException.internalServerErrors.upgradeDriverPrecheckFailed(
                    String.format("Can't find specified driver name: %s", metaData.getDriverName()));
        }
        if (!upgrade && nonNativeDrivers.size() >= MAX_NON_NATIVE_DRIVER_NUMBER) {
            throw APIException.internalServerErrors.installDriverPrecheckFailed(String
                    .format("Can't install more drivers as max driver number %s has been reached", MAX_NON_NATIVE_DRIVER_NUMBER));
        }
    }

    public static boolean isIntreeDriverPath(String filePath) {
        if (StringUtils.isNotEmpty(filePath) && filePath.startsWith("file:/opt/storageos/lib/")) {
            return true;
        }
        return false;
    }

    /**
     * New version should be higher than old version, otherwise exception is thrown
     * @param oldVersionStr
     * @param newVersionStr
     */
    private static void compareVersion(String oldVersionStr, String newVersionStr) {
        String[] oldVersionSegs = oldVersionStr.split("\\.");
        String[] newVersionSegs = newVersionStr.split("\\.");
        if (oldVersionSegs.length != DRIVER_VERSION_NUM_SIZE || newVersionSegs.length != DRIVER_VERSION_NUM_SIZE) {
            throw APIException.internalServerErrors.upgradeDriverPrecheckFailed(
                    String.format("Invalid driver version format (four numbers separated by dot), old: %s, new: %s",
                            oldVersionStr, newVersionStr));
        }
        for (int i = 0; i < DRIVER_VERSION_NUM_SIZE; i ++) {
            int oldVersion = Integer.valueOf(oldVersionSegs[i]);
            int newVersion = Integer.valueOf(newVersionSegs[i]);
            if (newVersion > oldVersion) {
                return;
            } else if (newVersion < oldVersion) {
                throw APIException.internalServerErrors.upgradeDriverPrecheckFailed(String.format(
                        "new version (%s) should be higher than the old one (%s)", newVersionStr, oldVersionStr));
            }
        }
        throw APIException.internalServerErrors.upgradeDriverPrecheckFailed(String.format(
                "new version (%s) should be higher than the old one (%s)", newVersionStr, oldVersionStr));
    }

    private static void precheckForDriverName(String name) {
        precheckForNotEmptyField("driver_name", name);
        if (name.length() > MAX_DISPLAY_STRING_LENGTH) {
            throw APIException.internalServerErrors.installDriverPrecheckFailed(
                    String.format("driver name is longer than %s", MAX_DISPLAY_STRING_LENGTH));
        }
        if (hasForbiddenChar(name)) {
            throw APIException.internalServerErrors
            .installDriverPrecheckFailed("driver name should only contain letter, digit, dash or underline");
        }
    }

    private static void precheckForDriverVersion(String driverVersion) {
        precheckForNotEmptyField("driver_version", driverVersion);
        if (!DRIVER_VERSION_PATTERN.matcher(driverVersion).find()) {
            throw APIException.internalServerErrors.installDriverPrecheckFailed(
                    "driver_version field value should be four numbers concatenated by dot");
        }
    }

    private static void precheckForProviderName(String providerName, String providerDisplayName,
            StorageDriverMetaData metaData) {
        if (StringUtils.isNotEmpty(providerName) && StringUtils.isNotEmpty(providerDisplayName)) {
            metaData.setProviderName(providerName);
            metaData.setProviderDisplayName(providerDisplayName);
        } else if (StringUtils.isEmpty(providerName) && StringUtils.isEmpty(providerDisplayName)) {
            // This driver doesn't support provider, which is allowed, so do
            // nothing
        } else {
            // This is ambiguous input, which should cause exception
            throw APIException.internalServerErrors.installDriverPrecheckFailed(
                    "provider_name and provider_display_name fields values should be both providerd or not");
        }
    }

    private static boolean isValidMetaType(String metaType) {
        if (StringUtils.isEmpty(metaType)) {
            return false;
        }
        return VALID_META_TYPES.contains(metaType);
    }

    private static void precheckForMetaType(String metaType) {
        if (!isValidMetaType(metaType)) {
            throw APIException.internalServerErrors.installDriverPrecheckFailed("meta_type field value is not valid");
        }
    }
}
