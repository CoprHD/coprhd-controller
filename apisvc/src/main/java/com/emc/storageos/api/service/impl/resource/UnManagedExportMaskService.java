/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.BlockMapper.map;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.functions.MapUnmanagedExportMask;
import com.emc.storageos.api.service.impl.resource.unmanaged.UnmanagedVolumeReportingUtils;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.block.UnManagedExportMaskBulkRep;
import com.emc.storageos.model.block.UnManagedExportMaskRestRep;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;

@Path("/vdc/unmanaged/export-masks")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class UnManagedExportMaskService extends TaggedResource {

    private static final Logger _logger = LoggerFactory
            .getLogger(UnManagedExportMaskService.class);

    @Override
    protected DataObject queryResource(URI id) {
        ArgValidator.checkUri(id);
        UnManagedExportMask unManagedExportMask = _dbClient.queryObject(UnManagedExportMask.class, id);
        ArgValidator.checkEntityNotNull(unManagedExportMask, id, isIdEmbeddedInURL(id));
        return unManagedExportMask;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.UNMANAGED_EXPORT_MASKS;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<UnManagedExportMask> getResourceClass() {
        return UnManagedExportMask.class;
    }

    /**
     * Show the details of an UnManagedExportMask.
     * 
     * @param id the URN of a ViPR UnManagedExportMask
     * @brief Show details for an unmanaged export mask
     * @return UnManagedExportMaskRestRep
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public UnManagedExportMaskRestRep getUnManagedExportMaskInfo(@PathParam("id") URI id) {
        UnManagedExportMask uem = _dbClient.queryObject(UnManagedExportMask.class, id);
        ArgValidator.checkEntityNotNull(uem, id, isIdEmbeddedInURL(id));
        return map(uem);
    }

    /**
     * Get Bulk Resources
     * 
     * @brief List specified unmanaged export masks
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public UnManagedExportMaskBulkRep getBulkResources(BulkIdParam param) {
        return (UnManagedExportMaskBulkRep) super.getBulkResources(param);
    }

    @Override
    public UnManagedExportMaskBulkRep queryBulkResourceReps(List<URI> ids) {
        Iterator<UnManagedExportMask> _dbIterator = _dbClient.queryIterativeObjects(
                UnManagedExportMask.class, ids);
        return new UnManagedExportMaskBulkRep(BulkList.wrapping(_dbIterator, MapUnmanagedExportMask.getInstance()));
    }

    @Override
    public UnManagedExportMaskBulkRep queryFilteredBulkResourceReps(List<URI> ids) {
        verifySystemAdmin();
        return queryBulkResourceReps(ids);
    }

    
    
    
    
    
    
    

    /**
     *
     * Show the dependency details of unmanaged volume.
     *
     * @param id the URN of a ViPR unmanaged volume
     */
    @GET
    @Produces({ MediaType.TEXT_PLAIN, MediaType.TEXT_HTML })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public String getUnManagedExportMaskTree(@PathParam("id") URI id) {
        UnManagedExportMask unmanagedExportMask = _dbClient.queryObject(UnManagedExportMask.class, id);
        ArgValidator.checkEntityNotNull(unmanagedExportMask, id, isIdEmbeddedInURL(id));
        return UnmanagedVolumeReportingUtils.renderUnmanagedExportMaskDependencyTree(_dbClient, _coordinator, unmanagedExportMask);
    }

}
