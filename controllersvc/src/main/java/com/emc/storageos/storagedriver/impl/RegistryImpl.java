package com.emc.storageos.storagedriver.impl;


import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.AbstractChangeTrackingSet;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.storagedriver.DriverRegistryRecord;
import com.emc.storageos.storagedriver.Registry;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Implementation of persistant registry for device drivers
 */
public class RegistryImpl implements Registry {

    private static Registry registry;
    private DbClient dbClient;

    private RegistryImpl(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public static Registry getInstance(DbClient dbClient) {
        if (registry == null) {
            registry = new RegistryImpl(dbClient);
        }

        return registry;
    }

    @Override
    public void setDriverAttributesForKey(String driverName, String key, Map<String, List<String>> attributes) {

        DriverRegistryRecord registryEntryForKey = null;
        // find existing entry for driver name and a given key
        boolean existingEntry = false;
        URIQueryResultList registryEntriesUris = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.getDriverRegistryEntriesByDriverName(driverName), registryEntriesUris);
        while (registryEntriesUris.iterator().hasNext()) {
            URI registryEntryUri = registryEntriesUris.iterator().next();
            DriverRegistryRecord registryEntry = dbClient.queryObject(DriverRegistryRecord.class, registryEntryUri);
            if (registryEntry.getRegistryKey().equals(key)) {
                registryEntryForKey = registryEntry;
                existingEntry = true;
                break;
            }
        }
        // have not find existing driver registry entry for a key, create a new entry for this key
        if (registryEntryForKey == null) {
            registryEntryForKey = new DriverRegistryRecord();
            registryEntryForKey.setId(URIUtil.createId(DriverRegistryRecord.class));
            registryEntryForKey.setDriverName(driverName);
            registryEntryForKey.setRegistryKey(key);
        }

        // update/add attribute map for the key entry
        StringSetMap attributesMap = new StringSetMap();
        for (Map.Entry<String, List<String>> entry : attributes.entrySet()) {
            StringSet values = new StringSet(entry.getValue());
            attributesMap.put(entry.getKey(), values);
        }
        registryEntryForKey.setAttributes(attributesMap);
        if (existingEntry) {
            dbClient.updateObject(registryEntryForKey);
        } else {
            dbClient.createObject(registryEntryForKey);
        }
    }

    @Override
    public Map<String, List<String>> getDriverAttributesForKey(String driverName, String key) {

        Map<String, List<String>> attributesMap = new HashMap<>();
        // find existing entry for driver name and a given key
        URIQueryResultList registryEntriesUris = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.getDriverRegistryEntriesByDriverName(driverName), registryEntriesUris);
        while (registryEntriesUris.iterator().hasNext()) {
            URI registryEntryUri = registryEntriesUris.iterator().next();
            DriverRegistryRecord registryEntry = dbClient.queryObject(DriverRegistryRecord.class, registryEntryUri);
            if (registryEntry.getRegistryKey().equals(key)) {
                StringSetMap attributes = registryEntry.getAttributes();
                for (Map.Entry<String, AbstractChangeTrackingSet<String>> entry : attributes.entrySet()) {
                    attributesMap.put(entry.getKey(), new ArrayList<>(entry.getValue()));
                }
                break;
            }
        }
        return attributesMap;
    }

    @Override
    public Map<String, Map<String, List<String>>> getDriverAttributes(String driverName) {

        Map<String, Map<String, List<String>>> keyMap = new HashMap<>();
        // find existing entries for driver name
        URIQueryResultList registryEntriesUris = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.getDriverRegistryEntriesByDriverName(driverName), registryEntriesUris);
        while (registryEntriesUris.iterator().hasNext()) {
            Map<String, List<String>> attributesMap = new HashMap<>();
            URI registryEntryUri = registryEntriesUris.iterator().next();
            DriverRegistryRecord registryEntry = dbClient.queryObject(DriverRegistryRecord.class, registryEntryUri);
            StringSetMap attributes = registryEntry.getAttributes();
            for (Map.Entry<String, AbstractChangeTrackingSet<String>> entry : attributes.entrySet()) {
                attributesMap.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
            keyMap.put(registryEntry.getRegistryKey(), attributesMap);
        }
        return keyMap;
    }

    @Override
    public void clearDriverAttributes(String driverName) {
        URIQueryResultList registryEntriesUris = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.getDriverRegistryEntriesByDriverName(driverName), registryEntriesUris);
        while (registryEntriesUris.iterator().hasNext()) {
            URI registryEntryUri = registryEntriesUris.iterator().next();
            DriverRegistryRecord registryEntry = dbClient.queryObject(DriverRegistryRecord.class, registryEntryUri);
            dbClient.markForDeletion(registryEntry);
        }
    }

    @Override
    public void clearDriverAttributesForKey(String driverName, String key) {
        URIQueryResultList registryEntriesUris = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.getDriverRegistryEntriesByDriverName(driverName), registryEntriesUris);
        while (registryEntriesUris.iterator().hasNext()) {
            URI registryEntryUri = registryEntriesUris.iterator().next();
            DriverRegistryRecord registryEntry = dbClient.queryObject(DriverRegistryRecord.class, registryEntryUri);
            if (registryEntry.getRegistryKey().equals(key)) {
                // remove this entry from db
                dbClient.markForDeletion(registryEntry);
                break;
            }
        }
    }

    @Override
    public void clearDriverAttributeForKey(String driverName, String key, String attribute) {
        URIQueryResultList registryEntriesUris = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.getDriverRegistryEntriesByDriverName(driverName), registryEntriesUris);
        while (registryEntriesUris.iterator().hasNext()) {
            URI registryEntryUri = registryEntriesUris.iterator().next();
            DriverRegistryRecord registryEntry = dbClient.queryObject(DriverRegistryRecord.class, registryEntryUri);
            if (registryEntry.getRegistryKey().equals(key)) {
                // remove attribute from registry entry attribute map
                registryEntry.getAttributes().remove(attribute);
                if (registryEntry.getAttributes().isEmpty()) {
                    dbClient.markForDeletion(registryEntry);
                } else {
                    dbClient.updateObject(registryEntry);
                }
                break;
            }
        }
    }

    @Override
    public void addDriverAttributeForKey(String driverName, String key, String attribute, List<String> value) {
        DriverRegistryRecord registryEntryForKey = null;
        // find existing entry for a driver name and a given key
        boolean existingKey = false;
        URIQueryResultList registryEntriesUris = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.getDriverRegistryEntriesByDriverName(driverName), registryEntriesUris);
        while (registryEntriesUris.iterator().hasNext()) {
            URI registryEntryUri = registryEntriesUris.iterator().next();
            registryEntryForKey = dbClient.queryObject(DriverRegistryRecord.class, registryEntryUri);
            if (registryEntryForKey.getRegistryKey().equals(key)) {
                existingKey = true;
                StringSetMap attributes = registryEntryForKey.getAttributes();
                StringSet attributeValue = new StringSet(value);
                attributes.put(attribute, attributeValue);
                break;
            }
        }
        if (existingKey == false) {
            // no entry for this key in the registry, create a new entry
            registryEntryForKey = new DriverRegistryRecord();
            registryEntryForKey.setId(URIUtil.createId(DriverRegistryRecord.class));
            registryEntryForKey.setDriverName(driverName);
            registryEntryForKey.setRegistryKey(key);
            StringSetMap attributesMap = new StringSetMap();
            StringSet values = new StringSet(value);
            attributesMap.put(attribute, values);
            registryEntryForKey.setAttributes(attributesMap);
        }
        // update db
        if (existingKey) {
            dbClient.updateObject(registryEntryForKey);
        } else {
            dbClient.createObject(registryEntryForKey);
        }
    }
}
