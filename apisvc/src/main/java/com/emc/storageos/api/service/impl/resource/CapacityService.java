/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.PropertyListDataObject;
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
                _log.warn("Failed to find capacity of type {} in the database, recompute", resourceType);
                throw new Exception("Failed to find capacity in the database");
            }
            PropertyListDataObject resource = _dbClient.queryObject(PropertyListDataObject.class, dataResourcesURI.get(0));
            ManagedResourceCapacity mCap = map(resource, ManagedResourceCapacity.class);
            capacities.getResourceCapacityList().add(mCap);
        }
        return capacities;
    }
}