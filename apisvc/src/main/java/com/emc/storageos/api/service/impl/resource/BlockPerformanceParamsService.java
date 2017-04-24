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
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.PerformanceParams;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.block.BlockPerformanceParamsBulkRep;
import com.emc.storageos.model.block.BlockPerformanceParamsCreate;
import com.emc.storageos.model.block.BlockPerformanceParamsList;
import com.emc.storageos.model.block.BlockPerformanceParamsRestRep;
import com.emc.storageos.model.block.BlockPerformanceParamsUpdate;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;

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
        
        // Verify host I/O bandwidth limit is non-negative.
        Integer hostIOLimitBandwidth = param.getHostIOLimitBandwidth();
        if ((hostIOLimitBandwidth != null) && (hostIOLimitBandwidth < 0)) {
            throw BadRequestException.badRequests.negativeHostIOLimitBadwidth();
        }

        // Verify host I/O bandwidth limit is non-negative.
        Integer hostIOLimitIOPs = param.getHostIOLimitIOPs();
        if ((hostIOLimitIOPs != null) && (hostIOLimitIOPs < 0)) {
            throw BadRequestException.badRequests.negativeHostIOLimitIOPs();
        }
        
        // Verify host I/O bandwidth limit is non-negative.
        Integer thinVolumePreAllocationPercentage = param.getThinVolumePreAllocationPercentage();
        if ((thinVolumePreAllocationPercentage != null) && (thinVolumePreAllocationPercentage < 0)) {
            throw BadRequestException.badRequests.negativeThinVolumePreAllocationPercentage();
        }        

        PerformanceParams performanceParams = new PerformanceParams();
        performanceParams.setId(performanceParamsURI);
        performanceParams.setLabel(performanceParamsName);
        if (NullColumnValueGetter.isNotNullValue(param.getDescription())) {
            performanceParams.setDescription(param.getDescription());
        }
        performanceParams.setAutoTierPolicyName(param.getAutoTieringPolicyName());
        performanceParams.setCompressionEnabled(param.getCompressionEnabled());
        performanceParams.setHostIOLimitBandwidth(hostIOLimitBandwidth);
        performanceParams.setHostIOLimitIOPs(hostIOLimitIOPs);
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
        
        // If the performance parameters is currently in use by a volume, then the
        // only parameters that can be updated are the name and description.
        List<Volume> performanceParamsVolumes = CustomQueryUtility.queryActiveResourcesByConstraint(
                _dbClient, Volume.class, ContainmentConstraint.Factory.getVolumePerformanceParamsConstraint(id));
        boolean performanceParamsInUse = !performanceParamsVolumes.isEmpty();

        // Verify the name and update.
        boolean isUpdated = false;
        String currentName = performanceParams.getLabel();
        String performanceParamsName = param.getName();
        if ((NullColumnValueGetter.isNotNullValue(performanceParamsName)) &&
                (!performanceParamsName.equals(performanceParams.getLabel()))) {
            checkForDuplicateName(performanceParamsName, PerformanceParams.class);
            performanceParams.setLabel(performanceParamsName);
            isUpdated = true;
        }

        // Update performance params settings. If a value is not specified, then
        // do not modify that value.
        String description = param.getDescription();
        if ((NullColumnValueGetter.isNotNullValue(description)) &&
                (!description.equals(performanceParams.getDescription()))) {
            performanceParams.setDescription(description);
            isUpdated = true;
        }
        
        // Update the autotiering policy name.
        String autoTieringPolicyName = param.getAutoTieringPolicyName();
        if ((NullColumnValueGetter.isNotNullValue(autoTieringPolicyName)) &&
                (!autoTieringPolicyName.equals(performanceParams.getAutoTierPolicyName()))) {
            if (!performanceParamsInUse) {
                performanceParams.setAutoTierPolicyName(autoTieringPolicyName);
                isUpdated = true;
            } else {
                throw BadRequestException.badRequests.cantUpdatePerformanceParamsInUse(currentName);
            }
        }
        
        // Update the compression setting.
        Boolean compressionEnabled = param.getCompressionEnabled();
        if ((compressionEnabled != null) && (compressionEnabled != performanceParams.getCompressionEnabled())) {
            if (!performanceParamsInUse) {
                performanceParams.setCompressionEnabled(compressionEnabled);
                isUpdated = true;
            } else {
                throw BadRequestException.badRequests.cantUpdatePerformanceParamsInUse(currentName);                
            }
        }
        
        // Update the host I/O bandwidth limit.
        Integer hostIOLimitBandwidth = param.getHostIOLimitBandwidth();
        if ((hostIOLimitBandwidth != null) && (hostIOLimitBandwidth != performanceParams.getHostIOLimitBandwidth())) {
            if (!performanceParamsInUse) {
                performanceParams.setHostIOLimitBandwidth(hostIOLimitBandwidth);
                isUpdated = true;
            } else {
                throw BadRequestException.badRequests.cantUpdatePerformanceParamsInUse(currentName);                
            }
        }
        
        // Update the host I/o IOPS limit.
        Integer hostIOLimitIOPs = param.getHostIOLimitIOPs();
        if ((hostIOLimitIOPs != null) && (hostIOLimitIOPs != performanceParams.getHostIOLimitIOPs())) {
            if (!performanceParamsInUse) {
                performanceParams.setHostIOLimitIOPs(hostIOLimitIOPs);
                isUpdated = true;
            } else {
                throw BadRequestException.badRequests.cantUpdatePerformanceParamsInUse(currentName);                
            }
        }
        
        // Update the thin volume pre-allocation percentage.
        Integer thinVolumePreAllocPercentage = param.getThinVolumePreAllocationPercentage();
        if ((thinVolumePreAllocPercentage != null) && 
                (thinVolumePreAllocPercentage != performanceParams.getThinVolumePreAllocationPercentage())) {
            if (!performanceParamsInUse) {
                performanceParams.setThinVolumePreAllocationPercentage(thinVolumePreAllocPercentage);
                isUpdated = true;
            } else {
                throw BadRequestException.badRequests.cantUpdatePerformanceParamsInUse(currentName);
            }
        }
        
        // Update the deduplication setting.
        Boolean dedupCapable = param.getDedupCapable();
        if ((dedupCapable != null) && (dedupCapable != performanceParams.getDedupCapable())) {
            if (!performanceParamsInUse) {
                performanceParams.setDedupCapable(dedupCapable);
                isUpdated = true;
            } else {
                throw BadRequestException.badRequests.cantUpdatePerformanceParamsInUse(currentName);
            }
        }
        
        // Update the fast expansion setting.
        Boolean fastExpansion = param.getFastExpansion();
        if ((fastExpansion != null) && (fastExpansion != performanceParams.getFastExpansion())) {
            if (!performanceParamsInUse) {
                performanceParams.setFastExpansion(fastExpansion);
                isUpdated = true;
            } else {
                throw BadRequestException.badRequests.cantUpdatePerformanceParamsInUse(currentName);
            }
        }
        
        // Update the database and return the updated instance.
        if (isUpdated) {
            _dbClient.updateObject(performanceParams);
        }
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
        
        // Verify that no volumes are referencing the instance to be deleted.
        checkForDelete(performanceParams);
        
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
        return ResourceTypeEnum.BLOCK_PERFORMANCE_PARAMS;
    }
    
    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    protected Class<PerformanceParams> getResourceClass() {
        return PerformanceParams.class;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }
}
