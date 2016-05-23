/*
 * Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.Bucket;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.PropertyListDataObject;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.joiner.Joiner;
import com.emc.storageos.model.vpool.ManagedResourcesCapacity;
import com.emc.storageos.model.vpool.ManagedResourcesCapacity.ManagedResourceCapacity;

import static com.emc.storageos.db.client.model.mapper.PropertyListDataObjectMapper.map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class ManagedCapacityImpl implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ManagedCapacityImpl.class);
    public static final long KB = 1024L;

    private DbClient dbClient;

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public DbClient getDbClient() {
        return this.dbClient;
    }

    public void run() {
        if (Thread.currentThread().isInterrupted()) {
            return;
        }
        else {
            try {
                List<ManagedResourceCapacity> capList = getManagedCapacity().getResourceCapacityList();
                for (ManagedResourceCapacity cap : capList) {
                    CapacityPropertyListTypes type = mapCapacityType(cap.getType());
                    PropertyListDataObject resource = map(cap, type.toString());

                    List<URI> dataResourcesURI = dbClient.queryByConstraint(
                            AlternateIdConstraint.Factory.getConstraint(PropertyListDataObject.class,
                                    "resourceType",
                                    type.toString()));
                    if (!dataResourcesURI.isEmpty()) {
                        resource.setId(dataResourcesURI.get(0));
                        resource.setCreationTime(Calendar.getInstance());
                        dbClient.updateAndReindexObject(resource);
                    }
                    else {
                        resource.setId(URIUtil.createId(PropertyListDataObject.class));
                        dbClient.createObject(resource);
                    }
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public ManagedResourcesCapacity getManagedCapacity() throws InterruptedException {

        log.info("Getting provisioning managed capacity");
        double total = 0;
        StringBuilder logMessage = new StringBuilder("");
        logMessage.append('\n');
        logMessage.append("------------------------------------------------------------------------\n");
        logMessage.append("|                 |                |                                   |\n");
        logMessage.append("|  RESOURCE       | MANAGED_QTY    |  TOTAL CAPACITY                   |\n");
        logMessage.append("|                 |                |                                   |\n");
        logMessage.append("------------------------------------------------------------------------\n");
        ManagedResourcesCapacity resources = getManagedCapacity(dbClient);
        List<ManagedResourceCapacity> capacities = resources.getResourceCapacityList();
        for (ManagedResourcesCapacity.ManagedResourceCapacity cap : capacities) {
            total += cap.getResourceCapacity();

            logMessage.append("|                 |                |                                   |\n");
            logMessage.append(
                    String.format("|  %13s  |   %10d   |  %30s   |%n", cap.getType(),
                            cap.getNumResources(), Double.toString(cap.getResourceCapacity())
                            ));
            logMessage.append("|                 |                |                                   |\n");
        }
        logMessage.append("|                 |                |                                   |\n");
        logMessage.append("------------------------------------------------------------------------\n");
        logMessage.append("|                 |                |                                   |\n");
        logMessage.append(
                String.format("|   TOTAL         |                |  %30s   |%n", Double.toString(total)));
        logMessage.append("|                 |                |                                   |\n");
        logMessage.append("------------------------------------------------------------------------\n");
        logMessage.append('\n');
        log.info(logMessage.toString());

        return resources;
    }

    static public ManagedResourcesCapacity getManagedCapacity(DbClient dbClient) throws InterruptedException {
        ManagedResourcesCapacity resourcesCapacity = new ManagedResourcesCapacity();

        ManagedResourcesCapacity.ManagedResourceCapacity manCap;

        CustomQueryUtility.AggregatedValue aggr = null;

        if (Thread.currentThread().interrupted()) {
            throw new InterruptedException();
        }
        manCap = new ManagedResourcesCapacity.ManagedResourceCapacity();
        manCap.setType(ManagedResourcesCapacity.CapacityResourceType.VOLUME);
        aggr = CustomQueryUtility.aggregatedPrimitiveField(dbClient, Volume.class, "allocatedCapacity");
        manCap.setNumResources(aggr.getCount());
        manCap.setResourceCapacity(aggr.getValue());
        resourcesCapacity.getResourceCapacityList().add(manCap);

        if (Thread.currentThread().interrupted()) {
            throw new InterruptedException();
        }
        manCap = new ManagedResourcesCapacity.ManagedResourceCapacity();
        manCap.setType(ManagedResourcesCapacity.CapacityResourceType.FILESHARE);
        aggr = CustomQueryUtility.aggregatedPrimitiveField(dbClient, FileShare.class, "usedCapacity");
        manCap.setNumResources(aggr.getCount());
        manCap.setResourceCapacity(aggr.getValue());
        resourcesCapacity.getResourceCapacityList().add(manCap);
        if (Thread.currentThread().interrupted()) {
            throw new InterruptedException();
        }

        manCap = new ManagedResourcesCapacity.ManagedResourceCapacity();
        manCap.setType(ManagedResourcesCapacity.CapacityResourceType.POOL);
        aggr = CustomQueryUtility.aggregatedPrimitiveField(dbClient, StoragePool.class, "freeCapacity");
        manCap.setNumResources(aggr.getCount());
        double capacity = aggr.getValue();

        // We must consider storage systems with sharedStorageCapacity == true (e.g. Ceph),
        // because each their pool reports total storage free capacity.
        // We get all such systems and subtract its pool size multiplied by (pools count - 1) from total capacity.
        // Get all StoragePools where storageDevice is a StorageSystem where sharedStorageCapacity is true
        Joiner j = new Joiner(dbClient).join(StorageSystem.class, "ss").match("sharedStorageCapacity", true)
                .join("ss", StoragePool.class, "sp", "storageDevice").go();
        Map<StorageSystem, Collection<URI>> ssToPoolMap = j.pushList("ss").pushUris("sp").map();
        // From the joiner, get the StorageSystems (which is a small amount of objects) and the SPs (which is large, so get URIs and use query)
        for (Entry<StorageSystem, Collection<URI>> ssToPoolEntry : ssToPoolMap.entrySet()) {
            Collection<URI> poolURIs = ssToPoolEntry.getValue();
            int extraPoolCount = poolURIs.size() - 1;
            if (extraPoolCount <= 0) {
                // Do nothing if none of the only pool belongs to Storage System
                continue;
            }
            StoragePool pool = dbClient.queryObject(StoragePool.class, poolURIs.iterator().next());
            capacity -= extraPoolCount * pool.getFreeCapacity();
        }

        manCap.setResourceCapacity(capacity * KB);
        resourcesCapacity.getResourceCapacityList().add(manCap);
        if (Thread.currentThread().interrupted()) {
            throw new InterruptedException();
        }
        
        manCap = new ManagedResourcesCapacity.ManagedResourceCapacity();
        manCap.setType(ManagedResourcesCapacity.CapacityResourceType.BUCKET);
        aggr = CustomQueryUtility.aggregatedPrimitiveField(dbClient, Bucket.class, "hardQuota");
        manCap.setNumResources(aggr.getCount());
        manCap.setResourceCapacity(aggr.getValue());
        resourcesCapacity.getResourceCapacityList().add(manCap);
        if (Thread.currentThread().interrupted()) {
            throw new InterruptedException();
        }

        return resourcesCapacity;
    }

    public static enum CapacityPropertyListTypes {
        POOL_MANAGED_CAPACITY,
        VOLUME_MANAGED_CAPACITY,
        FILE_MANAGED_CAPACITY,
        OBJECT_MANAGED_CAPACITY,
    }

    public static ManagedResourcesCapacity.CapacityResourceType mapCapacityType(CapacityPropertyListTypes resourceType) {
        ManagedResourcesCapacity.CapacityResourceType type = ManagedResourcesCapacity.CapacityResourceType.POOL;
        switch (resourceType) {
            case POOL_MANAGED_CAPACITY:
                type = ManagedResourcesCapacity.CapacityResourceType.POOL;
                break;
            case VOLUME_MANAGED_CAPACITY:
                type = ManagedResourcesCapacity.CapacityResourceType.VOLUME;
                break;
            case FILE_MANAGED_CAPACITY:
                type = ManagedResourcesCapacity.CapacityResourceType.FILESHARE;
                break;
            case OBJECT_MANAGED_CAPACITY:
                type = ManagedResourcesCapacity.CapacityResourceType.BUCKET;
                break;
        }
        return type;
    }

    public static CapacityPropertyListTypes mapCapacityType(ManagedResourcesCapacity.CapacityResourceType resourceType) {
        CapacityPropertyListTypes type = CapacityPropertyListTypes.POOL_MANAGED_CAPACITY;
        switch (resourceType) {
            case POOL:
                type = CapacityPropertyListTypes.POOL_MANAGED_CAPACITY;
                break;
            case VOLUME:
                type = CapacityPropertyListTypes.VOLUME_MANAGED_CAPACITY;
                break;
            case FILESHARE:
                type = CapacityPropertyListTypes.FILE_MANAGED_CAPACITY;
                break;
            case BUCKET:
                type = CapacityPropertyListTypes.OBJECT_MANAGED_CAPACITY;
                break;
        }
        return type;
    }

}
