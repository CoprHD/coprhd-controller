/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.southbound;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystemType;
import com.emc.storageos.services.util.StorageDriverManager;

public class StorageDriverManagerProxy extends StorageDriverManager {
    private static final Logger log = LoggerFactory.getLogger(StorageDriverManagerProxy.class);

    private DbClient dbClient;
    private StorageDriverManager manager;

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public StorageDriverManager getManager() {
        return manager;
    }

    public void setManager(StorageDriverManager manager) {
        this.manager = manager;
    }

    private Map<String, String> storageSystemsMap = new HashMap<>();
    private Map<String, String> storageProvidersMap = new HashMap<>();
    private Set<String> blockSystems = new HashSet<>();
    private Set<String> fileSystems = new HashSet<>();
    private Set<String> providerManaged = new HashSet<>();
    private Set<String> directlyManaged = new HashSet<>();
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
        List<StorageSystemType> types = listNonNativeTypes();
        for (StorageSystemType type : types) {
            String typeName = type.getStorageTypeName();
            String driverName = type.getDriverName();
            if (type.getIsSmiProvider()) {
                storageProvidersMap.put(driverName, typeName);
                log.info("Driver info for storage system type {} has been set into storageDriverManagerProxy instance", typeName);
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
            if (type.getSupportedStorageProfiles() != null) {
                supportedStorageProfiles.put(typeName, type.getSupportedStorageProfiles());
            }
            log.info("Driver info for storage system type {} has been set into storageDriverManagerProxy instance", typeName);
        }
    }

    private List<StorageSystemType> listNonNativeTypes() {
        List<StorageSystemType> result = new ArrayList<StorageSystemType>();
        List<URI> ids = dbClient.queryByType(StorageSystemType.class, true);
        Iterator<StorageSystemType> it = dbClient.queryIterativeObjects(StorageSystemType.class, ids);
        while (it.hasNext()) {
            StorageSystemType type = it.next();
            if (type.getIsNative() == null || type.getIsNative()) {
                continue;
            }
            if (StringUtils.equals(type.getDriverStatus(), StorageSystemType.STATUS.ACTIVE.toString())) {
                result.add(type);
            }
        }
        return result;
    }

    private <T> Set<T> mergeSet(Set<T> s1, Set<T> s2) {
        Set<T> s = new HashSet<T>(s1.size() + s2.size());
        s.addAll(s1);
        s.addAll(s2);
        return s;
    }

    private <K, V> Map<K, V> mergeMap(Map<K, V> m1, Map<K, V> m2) {
        Map<K, V> m = new HashMap<K, V>();
        m.putAll(m1);
        m.putAll(m2);
        return m;
    }

    @Override
    public boolean isBlockStorageSystem(String storageSystemType) {
        refreshInfo();
        return manager.isBlockStorageSystem(storageSystemType) || blockSystems.contains(storageSystemType);
    }

    @Override
    public boolean isFileStorageSystem(String storageSystemType) {
        refreshInfo();
        return manager.isFileStorageSystem(storageSystemType) || fileSystems.contains(storageSystemType);
    }

    @Override
    public boolean isDriverManaged(String type) {
        refreshInfo();
        return manager.isDriverManaged(type) || storageSystemsMap.values().contains(type) || storageProvidersMap.values().contains(type);
    }

    @Override
    public boolean isProvider(String type) {
        refreshInfo();
        return manager.isProvider(type) || storageProvidersMap.values().contains(type);
    }

    @Override
    public boolean isProviderStorageSystem(String storageSystemType) {
        refreshInfo();
        return manager.isProviderStorageSystem(storageSystemType) || storageProvidersMap.values().contains(storageSystemType);
    }

    @Override
    public boolean isDirectlyManagedStorageSystem(String storageSystemType) {
        refreshInfo();
        return manager.isDirectlyManagedStorageSystem(storageSystemType) || directlyManaged.contains(storageSystemType);
    }

    @Override
    public Map<String, String> getStorageSystemsMap() {
        refreshInfo();
        return mergeMap(manager.getStorageSystemsMap(), storageSystemsMap);
    }

    @Override
    public Set<String> getBlockSystems() {
        refreshInfo();
        return mergeSet(manager.getBlockSystems(), blockSystems);
    }

    @Override
    public Set<String> getFileSystems() {
        refreshInfo();
        return mergeSet(manager.getFileSystems(), fileSystems);
    }

    @Override
    public Set<String> getProviderManaged() {
        refreshInfo();
        return mergeSet(manager.getProviderManaged(), providerManaged);
    }

    @Override
    public Set<String> getDirectlyManaged() {
        refreshInfo();
        return mergeSet(manager.getDirectlyManaged(), directlyManaged);
    }

    @Override
    public Map<String, String> getStorageProvidersMap() {
        refreshInfo();
        return mergeMap(manager.getStorageProvidersMap(), storageProvidersMap);
    }

    @Override
    public Map<String, Set<String>> getSupportedStorageProfiles() {
        refreshInfo();
        return mergeMap(manager.getSupportedStorageProfiles(), supportedStorageProfiles);
    }

    @Override
    public Set<String> getSupportedStorageProfilesForType(String type) {
        Set<String> profiles = getSupportedStorageProfiles().get(type);
        return profiles != null ? profiles : new HashSet<String>();
    }

}
