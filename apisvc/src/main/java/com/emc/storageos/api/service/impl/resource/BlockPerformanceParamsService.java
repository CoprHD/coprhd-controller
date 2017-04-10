/*
 * Copyright 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.BlockMapper;
import com.emc.storageos.api.mapper.DbObjectMapper;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.PerformanceParams;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VirtualPool.Type;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.block.BlockPerformanceParamsBulkRep;
import com.emc.storageos.model.block.BlockPerformanceParamsCreate;
import com.emc.storageos.model.block.BlockPerformanceParamsList;
import com.emc.storageos.model.block.BlockPerformanceParamsRestRep;
import com.emc.storageos.model.block.BlockPerformanceParamsUpdate;
import com.emc.storageos.model.vpool.BlockVirtualPoolBulkRep;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.security.geo.GeoServiceClient;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * API service for creating and managing PerformanceParams instances.
 */
@Path("/block/performance-params")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, readAcls = { ACL.USE },
writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class BlockPerformanceParamsService extends TaggedResource {

    // Logger reference
    private static final Logger logger = LoggerFactory.getLogger(BlockPerformanceParamsService.class);

    /**
     * Create a block PerformanceParams instance.
     *
     * @prereq none
     * 
     * @param param The details for the new PerformanceParams instance to be created.
     * 
     * @brief Create block PerformanceParams instance.
     * 
     * @return BlockPerformanceParamsRestRep
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public BlockPerformanceParamsRestRep createBlockPerformanceParams(BlockPerformanceParamsCreate param) {
        
        // Verify the name.
        String performanceParamsName = param.getName();
        ArgValidator.checkFieldNotEmpty(performanceParamsName, "Name");
        checkForDuplicateName(performanceParamsName, PerformanceParams.class);
        
        URI performanceParamsURI = URIUtil.createId(PerformanceParams.class);
        logger.info("Creating PerformanceParams instance {} with id {}", performanceParamsName, performanceParamsURI);

        PerformanceParams performanceParams = new PerformanceParams();
        performanceParams.setId(performanceParamsURI);
        performanceParams.setLabel(param.getName());
        performanceParams.setDescription(param.getDescription());
        performanceParams.setAutoTierPolicyName(param.getAutoTieringPolicyName());
        performanceParams.setCompressionEnabled(param.getCompressionEnabled());
        performanceParams.setHostIOLimitBandwidth(param.getHostIOLimitBandwidth());
        performanceParams.setHostIOLimitIOPs(param.getHostIOLimitIOPs());
        performanceParams.setThinVolumePreAllocationPercentage(param.getThinVolumePreAllocationPercentage());
        performanceParams.setDedupCapable(param.getDedupCapable());
        performanceParams.setFastExpansion(param.getFastExpansion());
        _dbClient.createObject(performanceParams);
        
        return BlockMapper.map(performanceParams);
    }

    /**
     * Update a block PerformanceParams instance.
     *
     * @prereq none
     * 
     * @param id The id of the PerformanceParams instance to update.
     * @param param The update values.
     * 
     * @brief Update block PerformanceParams instance.
     * 
     * @return BlockPerformanceParamsRestRep
     */
    @PUT
    @Path("/{id}")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public BlockPerformanceParamsRestRep updateBlockPerformanceParams(@PathParam("id") URI id, BlockPerformanceParamsUpdate param) {
        
        logger.info("Updating PerformanceParams with id {}", id);

        // Verify the instance is valid.
        ArgValidator.checkFieldUriType(id, PerformanceParams.class, "id");
        PerformanceParams performanceParams = _dbClient.queryObject(PerformanceParams.class, id);
        ArgValidator.checkEntity(performanceParams, id, isIdEmbeddedInURL(id));

        // Verify the name and update.
        String performanceParamsName = param.getName();
        if ((NullColumnValueGetter.isNotNullValue(performanceParamsName)) &&
                (!performanceParamsName.equals(performanceParams.getLabel()))) {
            checkForDuplicateName(performanceParamsName, PerformanceParams.class);
            performanceParams.setLabel(performanceParamsName);
        }

        // Update the description
        performanceParams.setDescription(param.getDescription());
        
        
        // Update the autotiering policy name.
        performanceParams.setAutoTierPolicyName(param.getAutoTieringPolicyName());
        
        // Update the compression setting.
        performanceParams.setCompressionEnabled(param.getCompressionEnabled());
        
        // Update the host I/O bandwidth limit.
        performanceParams.setHostIOLimitBandwidth(param.getHostIOLimitBandwidth());
        
        // Update the host I/o IOPS limit.
        performanceParams.setHostIOLimitIOPs(param.getHostIOLimitIOPs());
        
        // Update the thin volume pre-allocation percentage.
        performanceParams.setThinVolumePreAllocationPercentage(param.getThinVolumePreAllocationPercentage());
        
        // Update the deduplication setting.
        performanceParams.setDedupCapable(param.getDedupCapable());
        
        // Update the fast expansion setting.
        performanceParams.setFastExpansion(param.getFastExpansion());
        
        //Update the database and return the updated instance.
        _dbClient.updateObject(performanceParams);
        return BlockMapper.map(performanceParams);
    }

    /**
     * Delete a block PerformanceParams instance.
     *
     * @prereq none
     * 
     * @param id The id of the PerformanceParams instance to delete.
     * 
     * @brief Delete block PerformanceParams instance.
     * 
     * @return Response
     */
    @POST
    @Path("/{id}/deactivate")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public Response deleteBlockPerformanceParams(@PathParam("id") URI id) {
        
        logger.info("Deleting PerformanceParams with id {}", id);

        // Verify the instance is valid.
        ArgValidator.checkFieldUriType(id, PerformanceParams.class, "id");
        PerformanceParams performanceParams = _dbClient.queryObject(PerformanceParams.class, id);
        ArgValidator.checkEntity(performanceParams, id, isIdEmbeddedInURL(id));
        
        // Mark it for deletion.
        _dbClient.markForDeletion(performanceParams);
        
        return Response.ok().build();
    }

    /**
     * Get all block PerformanceParams instances.
     *
     * @prereq none
     * 
     * @brief Get all block PerformanceParams instances.
     * 
     * @return BlockPerformanceParamsRestRep
     */
    @GET
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public BlockPerformanceParamsList getAllPerformanceParams() {
        BlockPerformanceParamsList performanceParamsList = new BlockPerformanceParamsList();
        List<URI> performanceParamsURIs = _dbClient.queryByType(PerformanceParams.class, true);
        Iterator<PerformanceParams> performanceParamsIter = _dbClient.queryIterativeObjects(PerformanceParams.class, performanceParamsURIs);
        while (performanceParamsIter.hasNext()) {
            performanceParamsList.getPerformanceParams().add(DbObjectMapper.toNamedRelatedResource(performanceParamsIter.next()));
        }
        return performanceParamsList;  
    }

    /**
     * Get the block PerformanceParams instance with the passed id.
     *
     * @prereq none
     * 
     * @param id The id of the PerformanceParams instance.
     * 
     * @brief Get block PerformanceParams instance.
     * 
     * @return BlockPerformanceParamsRestRep
     */
    @GET
    @Path("/{id}")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public BlockPerformanceParamsRestRep getBlockPerformanceParams(@PathParam("id") URI id) {
        PerformanceParams performanceParams = (PerformanceParams) queryResource(id);
        return BlockMapper.map(performanceParams);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public BlockPerformanceParamsBulkRep queryBulkResourceReps(List<URI> ids) {
        BlockPerformanceParamsBulkRep bulkRespose = new BlockPerformanceParamsBulkRep();
        Iterator<PerformanceParams> performanceParamsIter = _dbClient.queryIterativeObjects(getResourceClass(), ids);
        while (performanceParamsIter.hasNext()) {
            bulkRespose.getPerformanceParams().add(BlockMapper.map(performanceParamsIter.next()));
        }
        return bulkRespose;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected DataObject queryResource(URI id) {
        PerformanceParams perfParams = _permissionsHelper.getObjectById(id, PerformanceParams.class);
        ArgValidator.checkEntityNotNull(perfParams, id, isIdEmbeddedInURL(id));
        return perfParams;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.BLOCK_VPOOL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }
}
