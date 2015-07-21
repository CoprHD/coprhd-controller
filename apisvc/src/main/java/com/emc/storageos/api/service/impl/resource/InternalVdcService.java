/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.db.client.model.VirtualDataCenterInUse;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.vdc.VirtualDataCenterList;
import com.emc.storageos.model.vdc.VirtualDataCenterRestRep;
import com.emc.storageos.security.geo.GeoServiceHelper;
import com.emc.storageos.security.geo.GeoServiceJob.JobType;
import com.emc.storageos.services.OperationTypeEnum;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.VirtualDataCenterMapper.map;

/**
 * Internal API for maintaining Vdc information
 */

@Path("/internal/vdc")
public class InternalVdcService extends ResourceService{
    private static final Logger log = LoggerFactory.getLogger(InternalVdcService.class);
    
    private static final String EVENT_SERVICE_TYPE = "internalVdcService";

    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    @GET
    @Path("/{id}")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})    
    public VirtualDataCenterRestRep getVdc(@PathParam("id") URI id) {
        ArgValidator.checkUri(id);
        VirtualDataCenter vdc = _dbClient.queryObject(VirtualDataCenter.class, id);
        return map(vdc);
    }
    
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})    
    public VirtualDataCenterList listVdc() {
        VirtualDataCenterList vdcList = new VirtualDataCenterList();

        List<URI> ids = _dbClient.queryByType(VirtualDataCenter.class, true);
        Iterator<VirtualDataCenter> iter = _dbClient.queryIterativeObjects(VirtualDataCenter.class, ids);
        while(iter.hasNext())  {
            vdcList.getVirtualDataCenters().add(toNamedRelatedResource(iter.next()));
        }
        return vdcList;
    }
    
    @PUT
    @Path("/{id}")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})    
    public Response setVdcInUse(@PathParam("id") URI id, @QueryParam("inuse") Boolean inUse) {
        ArgValidator.checkUri(id);
        VirtualDataCenterInUse vdcInUse = _dbClient.queryObject(VirtualDataCenterInUse.class, id);
        if(vdcInUse == null) {
        	vdcInUse = new VirtualDataCenterInUse();
        	vdcInUse.setId(id);
        	vdcInUse.setInUse(inUse);
        	_dbClient.createObject(vdcInUse);
        } else {
        	vdcInUse.setInUse(inUse);
        	_dbClient.updateAndReindexObject(vdcInUse);
        }
        return Response.ok().build();
    }
}
