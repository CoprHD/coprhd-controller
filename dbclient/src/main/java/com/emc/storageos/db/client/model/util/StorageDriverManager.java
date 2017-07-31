/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model.util;


import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystemType;

/**
 * This class contains data structures with storage system types of installed drivers.
 */

public class StorageDriverManager implements ApplicationContextAware {

    public static final String EXTERNAL_STORAGE_DEVICE = "externalBlockStorageDevice";
    private static final String STORAGE_DRIVER_MANAGER = "storageDriverManager";
    public static final String SIMULATOR = "Simulator";
    private static final Logger log = LoggerFactory.getLogger(StorageDriverManager.class);

    private static ApplicationContext _context;

    @Override
    public void setApplicationContext(ApplicationContext appContext)
            throws BeansException {
        _context = appContext;
    }

    public static StorageDriverManager getInstance() {
        log.info("Context value: {}", _context);
        if (_context == null) {
            return null;
        }
        return (StorageDriverManager) _context.getBean(STORAGE_DRIVER_MANAGER);
    }

    private DbClient dbClient;

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    private Map<String, String> storageSystemsMap = new HashMap<>();
    private Map<String, String> storageProvidersMap = new HashMap<>();
    private Set<String>  blockSystems = new HashSet<>();
    private Set<String>  fileSystems = new HashSet<>();
    private Set<String>  providerManaged = new HashSet<>();
    private Set<String>  directlyManaged = new HashSet<>();
    private Map<String, Set<String>> supportedStorageProfiles = new HashMap<>();

    private void clearInfo() {
        storageSystemsMap.clear();
        storageProvidersMap.clear();
        blockSystems.clear();
        fileSystems.clear();
        providerManaged.clear();
        directlyManaged.clear();
        supportedStorageProfiles.clear();
    }

    private void refreshInfo() {
        clearInfo();
        List<StorageSystemType> types = listDriverManagedTypes(dbClient);
        for (StorageSystemType type : types) {
            String typeName = type.getStorageTypeName();
            String driverName = type.getDriverName();
            if (type.getIsSmiProvider()) {
                storageProvidersMap.put(driverName, typeName);
                log.info("Driver info for storage system type {} has been set into storageDriverManager instance", typeName);
                continue;
            }
            storageSystemsMap.put(driverName, typeName);
            if (type.getManagedBy() != null) {
                providerManaged.add(typeName);
            } else {
                directlyManaged.add(typeName);
            }
            if (StringUtils.equals(type.getMetaType(), StorageSystemType.META_TYPE.FILE.toString())) {
                fileSystems.add(typeName);
            } else if (StringUtils.equals(type.getMetaType(), StorageSystemType.META_TYPE.BLOCK.toString())) {
                blockSystems.add(typeName);
            }
            Set<String> supportedProfiles = type.getSupportedStorageProfiles();
            if (CollectionUtils.isNotEmpty(supportedProfiles)) {
                supportedStorageProfiles.put(typeName, supportedProfiles);
            }
            log.info("Driver info for storage system type {} has been set into storageDriverManager instance", typeName);
        }
    }

    public static List<StorageSystemType> listDriverManagedTypes(DbClient dbClient) {
        List<StorageSystemType> result = new ArrayList<StorageSystemType>();
        List<URI> ids = dbClient.queryByType(StorageSystemType.class, true);
        Iterator<StorageSystemType> it = dbClient.queryIterativeObjects(StorageSystemType.class, ids);
        while (it.hasNext()) {
            StorageSystemType type = it.next();
            if (type.getDriverClassName() == null) {
                continue;
            }
            if (StringUtils.equals(type.getDriverStatus(), StorageSystemType.STATUS.ACTIVE.toString())) {
                result.add(type);
            }
        }
        return result;
    }

    public Map<String, Set<String>> getSupportedStorageProfiles() {
        refreshInfo();
        return supportedStorageProfiles;
    }

    public boolean isBlockStorageSystem(String storageSystemType) {
        refreshInfo();
        return blockSystems.contains(storageSystemType);
    }

    public boolean isFileStorageSystem(String storageSystemType) {
        refreshInfo();
        return fileSystems.contains(storageSystemType);
    }

    public boolean isDriverManaged(String type) {
        refreshInfo();
        return storageSystemsMap.values().contains(type) || storageProvidersMap.values().contains(type);
    }

    public boolean isProvider(String type) {
        refreshInfo();
        return storageProvidersMap.values().contains(type);
    }

    public boolean isProviderStorageSystem(String storageSystemType) {
        refreshInfo();
        return providerManaged.contains(storageSystemType);
    }

    public boolean isDirectlyManagedStorageSystem(String storageSystemType) {
        refreshInfo();
        return directlyManaged.contains(storageSystemType);
    }

    public Map<String, String>  getStorageSystemsMap() {
        refreshInfo();
        return storageSystemsMap;
    }

    public Set<String> getBlockSystems() {
        refreshInfo();
        return blockSystems;
    }

    public Set<String> getFileSystems() {
        refreshInfo();
        return fileSystems;
    }

    public Set<String> getProviderManaged() {
        refreshInfo();
        return providerManaged;
    }

    public Set<String> getDirectlyManaged() {
        refreshInfo();
        return directlyManaged;
    }

    public Map<String, String> getStorageProvidersMap() {
        refreshInfo();
        return storageProvidersMap;
    }
}
