/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller.impl;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.PropertyListDataObject;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.constraint.AggregatedConstraint;
import com.emc.storageos.db.client.constraint.AggregationQueryResultList;
import com.emc.storageos.db.client.model.*;

import com.emc.storageos.model.vpool.ManagedResourcesCapacity;
import com.emc.storageos.model.vpool.ManagedResourcesCapacity.ManagedResourceCapacity;
import com.emc.storageos.model.systems.StorageSystemModelsManagedCapacity;
import com.emc.storageos.model.systems.StorageSystemModelsManagedCapacity.StorageSystemModelManagedCapacity;

import static com.emc.storageos.db.client.model.mapper.PropertyListDataObjectMapper.map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Calendar;
import java.util.List;
import java.util.*;

public class ManagedCapacityImpl implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ManagedCapacityImpl.class);
    public static final long KB = 1024L;

    private static final String USED_CAPACITY_STR = "usedCapacity";
    private static final String ALLOCATED_CAPACITY_STR = "allocatedCapacity";
    private static final String FREE_CAPACITY_STR = "freeCapacity";

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

                refreshStorageSystemModelsManagedCapacity();

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
        manCap.setResourceCapacity(aggr.getValue() * KB);
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
        }
        return type;
    }

    /**
     * get storage system models' managed capacity and update it to DB (PropertyListDataObject CF)
     * @throws InterruptedException
     */
    public void refreshStorageSystemModelsManagedCapacity() throws InterruptedException {
        log.info("Refreshing current StorageSystemModels managed arrays' capacity and count ...");

        StorageSystemModelsManagedCapacity modelsManagedCapacity = getStorageSystemModelsManagedCapacity();
        Map<String, StorageSystemModelManagedCapacity> modelCapacityMap = modelsManagedCapacity.getModelCapacityMap();
        for (Map.Entry<String, StorageSystemModelManagedCapacity> entry : modelCapacityMap.entrySet()) {

            // Here the resource type is a specific storage system model
            // while the resource data is its managed arrays' total capacity and count info
            String resourceType = entry.getKey();
            StorageSystemModelManagedCapacity resourceData = entry.getValue();

            PropertyListDataObject resourceObj = map(resourceData, resourceType);
            List<URI> dataResourcesURI = dbClient.queryByConstraint(
                    AlternateIdConstraint.Factory.getConstraint(PropertyListDataObject.class,
                            "resourceType", resourceType));
            if (dataResourcesURI.size() > 0) {
                resourceObj.setId(dataResourcesURI.get(0));
                resourceObj.setCreationTime(Calendar.getInstance());
                dbClient.updateAndReindexObject(resourceObj);
            }
            else {
                resourceObj.setId(URIUtil.createId(PropertyListDataObject.class));
                dbClient.createObject(resourceObj);
            }
        }

        log.info("Finished to refresh current StorageSystemModels managed arrays' capacity and count.");
    }

    /**
     * Get all storage system models' managed capacity
     * @return
     * @throws InterruptedException
     */
    public StorageSystemModelsManagedCapacity getStorageSystemModelsManagedCapacity() throws InterruptedException {
        log.info("Getting StorageSystemModels managed capacity");
        StorageSystemModelsManagedCapacity modelsManagedCapacity = new StorageSystemModelsManagedCapacity();

        double totalCapacity = 0;
        log.info("Iterating StorageSystem CF ...");
        Iterator<URI> storageIter = dbClient.queryByType(StorageSystem.class,true).iterator();
        while(storageIter.hasNext()) {
            URI storageId = storageIter.next();
            double managedCapacity = getStorageSystemManagedCapacity(storageId);

            StorageSystem storagesystem = dbClient.queryObject(StorageSystem.class, storageId);

            double totalManagedCapacity = 0;
            long totalNumber = 0;
            StorageSystemModelManagedCapacity modelManagedCapacity = modelsManagedCapacity.getModelCapacityMap().get(storagesystem.getModel());
            if (modelManagedCapacity == null) {
                modelManagedCapacity = new StorageSystemModelManagedCapacity();
                totalManagedCapacity = managedCapacity;
                totalNumber = 1;
            } else {
                totalManagedCapacity = modelManagedCapacity.getCapacity() + managedCapacity;
                totalNumber = modelManagedCapacity.getNumber() + 1;
            }
            modelManagedCapacity.setCapacity(totalManagedCapacity);
            modelManagedCapacity.setNumber(totalNumber);

            totalCapacity += totalManagedCapacity;

            modelsManagedCapacity.getModelCapacityMap().put(storagesystem.getModel(), modelManagedCapacity);
        }

        StringBuilder logMessage = new StringBuilder("");
        logMessage.append('\n');
        logMessage.append("------------------------------------------------------------------------\n");
        logMessage.append("|                 |                |                                   |\n");
        logMessage.append("|  StorageSystem  | MANAGED COUNT  |  MANAGED CAPACITY                 |\n");
        logMessage.append("|      Model      |                |                                   |\n");
        logMessage.append("------------------------------------------------------------------------\n");

        Map<String, StorageSystemModelManagedCapacity> modelCapacityMap = modelsManagedCapacity.getModelCapacityMap();
        for (Map.Entry<String, StorageSystemModelManagedCapacity> entry : modelCapacityMap.entrySet()) {
            logMessage.append("|                 |                |                                   |\n");
            logMessage.append(
                String.format("|  %13s  |   %10d   |  %30s   |\n", entry.getKey(),
                        entry.getValue().getNumber(), entry.getValue().getCapacity()
                ));
            logMessage.append("|                 |                |                                   |\n");
        }
        logMessage.append("|                 |                |                                   |\n");
        logMessage.append("------------------------------------------------------------------------\n");
        logMessage.append("|                 |                |                                   |\n");
        logMessage.append(
            String.format("| TOTAL Capacity  |                |  %30s   |\n",Double.toString(totalCapacity)));
        logMessage.append("|                 |                |                                   |\n");
        logMessage.append("------------------------------------------------------------------------\n");
        logMessage.append('\n');
        log.info(logMessage.toString());

        return modelsManagedCapacity;
    }

    public double getStorageSystemManagedCapacity(URI storageId) throws InterruptedException{
        // Get managed capacity from all the volumes of the storage system
        String groupBy = "storageDevice";
        String groupByValue = storageId.toString();

        double volume_capacity = CustomQueryUtility.aggregatedPrimitiveField(dbClient,Volume.class,
                groupBy, groupByValue, ALLOCATED_CAPACITY_STR).getValue();

        double fileshare_capacity = CustomQueryUtility.aggregatedPrimitiveField(dbClient, FileShare.class,
                groupBy, groupByValue, USED_CAPACITY_STR).getValue();

        double storagepool_capacity = CustomQueryUtility.aggregatedPrimitiveField(dbClient, StoragePool.class,
                groupBy, groupByValue, FREE_CAPACITY_STR).getValue();

        double total = volume_capacity + fileshare_capacity + storagepool_capacity;

        StringBuilder logMessage = new StringBuilder("");
        logMessage.append(
                String.format("|  %20s  |  %20s  |   %20s   |  %20s   |  %20s   |\n", storageId.toString(),
                        Double.toString(volume_capacity),Double.toString(fileshare_capacity), Double.toString(storagepool_capacity),
                        total
                ));
        log.info(logMessage.toString());

        return total;
    }
}
