/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.upgrade.callbacks;

import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.HostInterface;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ScaleIOPoolAndStorageSystemMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(ScaleIOPoolAndStorageSystemMigration.class);
    private static final Map<URI, StorageSystem> SIO_STORAGE_SYSTEMS = new HashMap<>();
    private static final List<String> STORAGE_SYSTEM_PROPERTIES =
            Arrays.asList("systemType", "label", "nativeGuid", "inactive");

    @Override
    public void process() throws MigrationCallbackException {
        log.info("Migrating any ScaleIO StoragePools");
        // First determine if there are any SIO based StorageSystems
        if (!haveAnySIOStorageSystems()) {
            log.info("There are no ScaleIO StorageSystems, skipping ScaleIOPoolAndStorageSystemMigration.");
            return;
        }

        log.info("Number ScaleIO StorageSystems found: {}", SIO_STORAGE_SYSTEMS.size());
        Map<URI, StoragePool> sioStoragePools = getSIOStoragePools();

        Set<URI> storageSystemURIs = new HashSet<>();
        for (StoragePool pool : sioStoragePools.values()) {
            StorageSystem associatedStorage = SIO_STORAGE_SYSTEMS.get(pool.getStorageDevice());
            if (associatedStorage != null) {
                log.info(String.format("StoragePool %s will be migrated", pool.getNativeGuid()));
                pool.setNativeGuid(pool.getNativeId());
                pool.setSupportedResourceTypes(StoragePool.SupportedResourceTypes.THICK_ONLY.name());
                pool.setMaximumThickVolumeSize(1048576L);
                pool.setMinimumThickVolumeSize(1L);
                pool.setMaximumThinVolumeSize(0L);
                pool.setMinimumThinVolumeSize(0L);
                dbClient.persistObject(pool);
                // Migrate the associated StorageSystem only once
                if (!storageSystemURIs.contains(associatedStorage.getId())) {
                    log.info(String.format("StorageSystem %s will be migrated", associatedStorage.getNativeGuid()));
                    associatedStorage.setLabel(associatedStorage.getNativeGuid());
                    dbClient.persistObject(associatedStorage);
                    storageSystemURIs.add(associatedStorage.getId());
                }
            }
        }

        List<URI> virtualPoolURIs = dbClient.queryByType(VirtualPool.class, true);
        Iterator<VirtualPool> virtualPoolIterator = dbClient.queryIterativeObjects(VirtualPool.class, virtualPoolURIs);
        while (virtualPoolIterator.hasNext()) {
            VirtualPool virtualPool = virtualPoolIterator.next();
            StringSet protocols = virtualPool.getProtocols();
            if (protocols != null && protocols.contains(HostInterface.Protocol.ScaleIO.name())) {
                log.info(String.format("VirtualPool %s will be migrated", virtualPool.getLabel()));
                virtualPool.setSupportedProvisioningType(VirtualPool.ProvisioningType.Thick.name());
                dbClient.persistObject(virtualPool);
            }
        }
    }

    /**
     * Examine SIO_STORAGE_SYSTEM and return a mapping of StoragePool URI to StoragePool objects
     * 
     * @return Map or StoragePool URI to StoragePool Object
     */
    Map<URI, StoragePool> getSIOStoragePools() {
        Map<URI, StoragePool> storagePoolMap = new HashMap<>();
        // Iterate through each SIO StorageSystem found and run constraint query to
        // look up the list of associated StoragePools
        for (StorageSystem storageSystem : SIO_STORAGE_SYSTEMS.values()) {
            List<StoragePool> storagePools = getStoragePoolsForStorageSystem(storageSystem);
            for (StoragePool storagePool : storagePools) {
                if (!storagePoolMap.containsKey(storagePool.getId())) {
                    storagePoolMap.put(storagePool.getId(), storagePool);
                }
            }
        }
        return storagePoolMap;
    }

    /**
     * Query StoragePools associated to the StorageSystem
     * 
     * @param storageSystem [in] - StorageSystem object
     * @return List of StoragePools associated with the StorageSystem
     * 
     */
    private List<StoragePool> getStoragePoolsForStorageSystem(StorageSystem storageSystem) {
        URIQueryResultList storagePoolURIs = new URIQueryResultList();
        AlternateIdConstraint storagePoolByStorageDevice = AlternateIdConstraint.Factory.
                getConstraint(StoragePool.class, "storageDevice", storageSystem.getId().toString());
        dbClient.queryByConstraint(storagePoolByStorageDevice, storagePoolURIs);
        List<StoragePool> storagePools = new ArrayList<>();
        Iterator<URI> storagePoolIter = storagePoolURIs.iterator();
        while (storagePoolIter.hasNext()) {
            URI storagePoolURI = storagePoolIter.next();
            StoragePool storagePool = dbClient.queryObject(StoragePool.class, storagePoolURI);
            if (storagePool != null && !storagePool.getInactive()) {
                storagePools.add(storagePool);
            }
        }
        return storagePools;
    }

    /**
     * Check if there are any ScaleIO StorageSystems in the DB.
     * 
     * @return true IFF there are 1 or more StorageSystems with systemType = scaleio. The SIO_STORAGE_SYSTEMS
     *         map will be populate with a mapping of the SIO StorageSystem URIs to StorageSystem object. Note, that
     *         the StorageSystems will have limited properties based on the query to populate them.
     * 
     */
    private boolean haveAnySIOStorageSystems() {
        List<URI> allStorageSystems = dbClient.queryByType(StorageSystem.class, true);
        Iterator<StorageSystem> storageSystemIterator =
                dbClient.queryIterativeObjectFields(StorageSystem.class, STORAGE_SYSTEM_PROPERTIES, allStorageSystems);
        while (storageSystemIterator.hasNext()) {
            StorageSystem storageSystem = storageSystemIterator.next();
            if (storageSystem.getSystemType().equals(DiscoveredDataObject.Type.scaleio.name())) {
                SIO_STORAGE_SYSTEMS.put(storageSystem.getId(), storageSystem);
            }
        }
        return !SIO_STORAGE_SYSTEMS.isEmpty();
    }

}
