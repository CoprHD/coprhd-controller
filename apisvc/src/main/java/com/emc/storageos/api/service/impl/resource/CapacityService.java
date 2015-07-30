/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.api.service.impl.resource;

import java.io.IOException;
import java.net.URI;
import java.util.Iterator;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.PropertyListDataObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.model.systems.StorageSystemModelsManagedCapacity;
import com.emc.storageos.model.systems.StorageSystemModelsManagedCapacity.StorageSystemModelManagedCapacity;
import com.emc.storageos.model.vpool.ManagedResourcesCapacity;
import com.emc.storageos.model.vpool.ManagedResourcesCapacity.ManagedResourceCapacity;
import com.emc.storageos.model.vpool.ManagedResourcesCapacity.CapacityResourceType;
import static com.emc.storageos.db.client.model.mapper.PropertyListDataObjectMapper.map;
import com.emc.storageos.volumecontroller.impl.ManagedCapacityImpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * API for obtaining the current provisioning managed capacity.
 */
@Path("/internal/system")
public class CapacityService extends ResourceService {

    private static final Logger _log = LoggerFactory.getLogger(CapacityService.class);

    /**
     * Get the provisioning managed capacity.
     * 
     * @return
     * @throws IOException
     */
    @GET
    @Path("/managed-capacity")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public ManagedResourcesCapacity getManagedCapacity() {

        ManagedResourcesCapacity resources = null;
        try {
            resources = getCapacityDataResource();
        } catch (Exception ex) {
            // failed to find capacity in the database, try to compute directly
            try {
                resources = ManagedCapacityImpl.getManagedCapacity(_dbClient);
            } catch (InterruptedException ignore) {
                // impossible
            }
        }
        return resources;
    }

    private ManagedResourcesCapacity getCapacityDataResource() throws Exception {

        ManagedResourcesCapacity capacities = new ManagedResourcesCapacity();
        for (CapacityResourceType capType : CapacityResourceType.values()) {
            ManagedCapacityImpl.CapacityPropertyListTypes resourceType = ManagedCapacityImpl.mapCapacityType(capType);
            List<URI> dataResourcesURI = _dbClient.queryByConstraint(
                    AlternateIdConstraint.Factory.getConstraint(PropertyListDataObject.class,
                            "resourceType",
                            resourceType.toString()));
            if (dataResourcesURI.isEmpty()) {
                _log.error("Failed to find capacity of type {} in the database, recompute", resourceType);
                throw new Exception("Failed to find capacity in the database");
            }
            PropertyListDataObject resource = _dbClient.queryObject(PropertyListDataObject.class, dataResourcesURI.get(0));
            ManagedResourceCapacity mCap = map(resource, ManagedResourceCapacity.class);
            capacities.getResourceCapacityList().add(mCap);
        }
        return capacities;
    }

    /**
     * Get the provisioning managed capacity.
     *
     * @return
     * @throws IOException
     */
    @GET
    @Path("/storagesystemmodels-managed-capacity")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public StorageSystemModelsManagedCapacity getStorageSystemModelsManagedCapacity() {

        StorageSystemModelsManagedCapacity resources = null;
        try {
            resources = getStorageSystemModelsManagedCapacityDataResource();
        } catch (Exception ex) {
            // failed to find capacity in the database, try to compute directly
            try {
                resources = ManagedCapacityImpl.getStorageSystemModelsManagedCapacity(_dbClient);
            } catch (InterruptedException ignore) {
                // impossible
            }
        }
        return resources;
    }

    private StorageSystemModelsManagedCapacity getStorageSystemModelsManagedCapacityDataResource() throws Exception {

        StorageSystemModelsManagedCapacity storageSystemModelsManagedCapacity = new StorageSystemModelsManagedCapacity();

        _log.info("Iterating StorageSystem CF ...");
        Iterator<URI> storageIter = _dbClient.queryByType(StorageSystem.class,true).iterator();
        while(storageIter.hasNext()) {
            URI storageId = storageIter.next();

            StorageSystem storagesystem = _dbClient.queryObject(StorageSystem.class, storageId);
            StorageSystemModelManagedCapacity modelManagedCapacity =
                    storageSystemModelsManagedCapacity.getModelCapacityMap().get(storagesystem.getModel());
            if (modelManagedCapacity == null) {
                List<URI> dataResourcesURI = _dbClient.queryByConstraint(
                        AlternateIdConstraint.Factory.getConstraint(PropertyListDataObject.class,
                                "resourceType", storagesystem.getModel()));
                if (dataResourcesURI.isEmpty()) {
                    _log.error("Failed to find capacity of type {} in the database, recompute", storagesystem.getModel());
                    throw new Exception("Failed to find capacity in the database");
                }

                PropertyListDataObject resource = _dbClient.queryObject(PropertyListDataObject.class, dataResourcesURI.get(0));
                modelManagedCapacity = map(resource, StorageSystemModelManagedCapacity.class);
                storageSystemModelsManagedCapacity.getModelCapacityMap().put(storagesystem.getModel(),modelManagedCapacity);
            }
        }
        return storageSystemModelsManagedCapacity;
    }

}
