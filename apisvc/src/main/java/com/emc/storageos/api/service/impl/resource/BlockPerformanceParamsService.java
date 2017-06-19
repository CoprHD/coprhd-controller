/*
 * Copyright 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import java.net.URI;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.mapper.BlockMapper;
import com.emc.storageos.api.mapper.DbObjectMapper;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.utils.GeoVisibilityHelper;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.PerformanceParams;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.auth.ACLAssignmentChanges;
import com.emc.storageos.model.auth.ACLAssignments;
import com.emc.storageos.model.block.BlockPerformanceParamsBulkRep;
import com.emc.storageos.model.block.BlockPerformanceParamsCreate;
import com.emc.storageos.model.block.BlockPerformanceParamsList;
import com.emc.storageos.model.block.BlockPerformanceParamsRestRep;
import com.emc.storageos.model.block.BlockPerformanceParamsUpdate;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.security.geo.GeoServiceClient;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import com.google.common.collect.Lists;

/**
 * API service for creating and managing PerformanceParams instances.
 */
@Path("/block/performance-params")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, readAcls = { ACL.USE },
writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class BlockPerformanceParamsService extends TaggedResource {

    private static final String EVENT_SERVICE_TYPE = "PERFORMANCEPARAMS";
    
    @Autowired
    protected GeoVisibilityHelper _geoHelper;

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

        // Verify host I/O IOPs limit is non-negative.
        Integer hostIOLimitIOPs = param.getHostIOLimitIOPs();
        if ((hostIOLimitIOPs != null) && (hostIOLimitIOPs < 0)) {
            throw BadRequestException.badRequests.negativeHostIOLimitIOPs();
        }
        
        // Verify thin volume pre-allocation percentage is non-negative.
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
        auditOp(OperationTypeEnum.CREATE_PERFORMANCE_PARAMS, true, null, performanceParams.getId().toString(), performanceParams.getLabel());
        
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
            auditOp(OperationTypeEnum.UPDATE_PERFORMANCE_PARAMS, true, null, performanceParams.getId().toString(), performanceParams.getLabel());
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
        auditOp(OperationTypeEnum.DELETE_PERFORMANCE_PARAMS, true, null, performanceParams.getId().toString(), performanceParams.getLabel());
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
    public BlockPerformanceParamsList getAllPerformanceParams(
            @DefaultValue("") @QueryParam(TENANT_ID_QUERY_PARAM) String tenantId,
            @DefaultValue("") @QueryParam(VDC_ID_QUERY_PARAM) String shortVdcId) {
        _geoHelper.verifyVdcId(shortVdcId);
        return getPerformanceParamsList(shortVdcId, tenantId);
    }
    
    private BlockPerformanceParamsList getPerformanceParamsList(String shortVdcId, String tenantId) {

        BlockPerformanceParamsList performanceParamsList = new BlockPerformanceParamsList();

        // If input tenant is not empty and the user has no access to it, return an empty list.
        TenantOrg tenant = null;
        if (!StringUtils.isEmpty(tenantId)) {
            tenant = getTenantIfHaveAccess(tenantId);
            if (tenant == null) {
                return performanceParamsList;
            }
        }

        List<PerformanceParams> allPerformanceParams = null;
        if (_geoHelper.isLocalVdcId(shortVdcId)) {
            List<URI> performanceParamsURIs = _dbClient.queryByType(PerformanceParams.class, true);
            allPerformanceParams = _dbClient.queryObject(PerformanceParams.class, performanceParamsURIs);
        } else {
            GeoServiceClient geoClient = _geoHelper.getClient(shortVdcId);
            try {
                Iterator<URI> performanceParamsURIIter = geoClient.queryByType(PerformanceParams.class, true);
                List<URI> performanceParamsURIs = Lists.newArrayList(performanceParamsURIIter);
                Iterator<PerformanceParams> performanceParamsIter = geoClient.queryObjects(PerformanceParams.class, performanceParamsURIs);
                allPerformanceParams = Lists.newArrayList();
                while (performanceParamsIter.hasNext()) {
                    allPerformanceParams.add(performanceParamsIter.next());
                }
            } catch (Exception ex) {
                logger.error("Error retrieving performance parameters from vdc " + shortVdcId, ex);
                throw APIException.internalServerErrors.genericApisvcError("Error retrieving remote performance parameters", ex);
            }
        }

        // Return all if Role.SYSTEM_ADMIN or Role.SYSTEM_MONITOR and no tenant restrictions.
        // Otherwise, return only those for which the tenant has access.
        StorageOSUser user = getUserFromContext();
        if (_permissionsHelper.userHasGivenRole(user, null, Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR)) {
            for (PerformanceParams performanceParams : allPerformanceParams) {
                if (tenant == null || _permissionsHelper.tenantHasUsageACL(tenant.getId(), performanceParams)) {
                    performanceParamsList.getPerformanceParams().add(DbObjectMapper.toNamedRelatedResource(performanceParams));
                }
            }
        } else {
            URI tenantURI = null;
            if (tenant == null) {
                tenantURI = URI.create(user.getTenantId());
            } else {
                tenantURI = tenant.getId();
            }

            Set<PerformanceParams> allowedPerformanceParams = new HashSet<>();
            for (PerformanceParams performanceParams : allPerformanceParams) {
                if (_permissionsHelper.tenantHasUsageACL(tenantURI, performanceParams)) {
                    allowedPerformanceParams.add(performanceParams);
                }
            }

            // If no tenant is specified, include performance parameters for which 
            // sub-tenants of the user have access.
            if (tenant == null) {
                List<URI> subtenantURIs = _permissionsHelper.getSubtenantsWithRoles(user);
                for (PerformanceParams performanceParams : allPerformanceParams) {
                    if (_permissionsHelper.tenantHasUsageACL(subtenantURIs, performanceParams)) {
                        allowedPerformanceParams.add(performanceParams);
                    }
                }
            }

            for (PerformanceParams performanceParams : allowedPerformanceParams) {
                performanceParamsList.getPerformanceParams().add(DbObjectMapper.toNamedRelatedResource(performanceParams));
            }
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
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, acls = { ACL.USE })
    public BlockPerformanceParamsRestRep getBlockPerformanceParams(@PathParam("id") URI id) {
        PerformanceParams performanceParams = getPerformanceParams(id);
        return BlockMapper.map(performanceParams);
    }

    /**
     * Get the performance parameters instance with the passed id.
     * 
     * @param id The id of the PerformanceParams instance.
     * 
     * @return A reference to a PerformanceParams instance
     */
    private PerformanceParams getPerformanceParams(URI id) {
        ArgValidator.checkUri(id);
        PerformanceParams performanceParams = null;
        if (_geoHelper.isLocalURI(id)) {
            performanceParams = (PerformanceParams) queryResource(id);
        } else {
            String shortVdcId = VdcUtil.getVdcId(PerformanceParams.class, id).toString();
            GeoServiceClient geoClient = _geoHelper.getClient(shortVdcId);
            try {
                performanceParams = geoClient.queryObject(PerformanceParams.class, id);
                ArgValidator.checkEntityNotNull(performanceParams, id, isIdEmbeddedInURL(id));
            } catch (Exception ex) {
                logger.error("Error retrieving performance parameters from vdc " + shortVdcId, ex);
                throw APIException.internalServerErrors.genericApisvcError("Error retrieving remote performance parameters", ex);
            }
        }
        return performanceParams;
    }

    /**
     * Get performance parameter ACLs.
     *
     * @prereq none
     * 
     * @param id The id of the PerformanceParams instance.
     * 
     * @brief Show ACL assignment for performance parameters.
     * 
     * @return ACL assignment details.
     */
    @GET
    @Path("/{id}/acl")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public ACLAssignments getAcls(@PathParam("id") URI id) {
        return getPerformanceParamsAcls(id);
    }
    
    /**
     * Add or remove individual performance parameter ACL entry(s).
     * Request body must include at least one add or remove operation.
     *
     * @prereq none
     * 
     * @param id The id of the PerformanceParams instance.
     * @param aclAssignmentsChanges The ACL assignment changes.
     * 
     * @brief Add or remove performance parameter ACL entries.
     * 
     * @return No data returned in response body.
     */
    @PUT
    @Path("/{id}/acl")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN }, blockProxies = true)
    public ACLAssignments updateAcls(@PathParam("id") URI id, ACLAssignmentChanges aclAssignmentsChanges) {
        PerformanceParams performanceParams = (PerformanceParams) queryResource(id);
        ArgValidator.checkEntityNotNull(performanceParams, id, isIdEmbeddedInURL(id));
        _permissionsHelper.updateACLs(performanceParams, aclAssignmentsChanges,
                new PermissionsHelper.UsageACLFilter(_permissionsHelper, null));
        _dbClient.updateObject(performanceParams);
        auditOp(OperationTypeEnum.MODIFY_PERFORMANCE_PARAMS_ACL, true, null, performanceParams.getId().toString(), performanceParams.getLabel());
        return getPerformanceParamsAcls(id);
    }

    /**
     * Get performance parameter ACLs
     * 
     * @param id The id of the PerformanceParams instance.
     * 
     * @return ACL assignment details.
     */
    private ACLAssignments getPerformanceParamsAcls(URI id) {
        PerformanceParams performanceParams = (PerformanceParams) queryResource(id);
        ArgValidator.checkEntityNotNull(performanceParams, id, isIdEmbeddedInURL(id));
        ACLAssignments response = new ACLAssignments();
        response.setAssignments(PermissionsHelper.convertToACLEntries(performanceParams.getAcls()));
        return response;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BlockPerformanceParamsBulkRep queryBulkResourceReps(List<URI> ids) {
        String shortVdcId = VdcUtil.getVdcId(getResourceClass(), ids.iterator().next()).toString();
        Iterator<PerformanceParams> performanceParamsIter;
        if (shortVdcId.equals(VdcUtil.getLocalShortVdcId())) {
            performanceParamsIter = _dbClient.queryIterativeObjects(getResourceClass(), ids);
        } else {
            GeoServiceClient geoClient = _geoHelper.getClient(shortVdcId);
            try {
                performanceParamsIter = geoClient.queryObjects(getResourceClass(), ids);
            } catch (Exception ex) {
                logger.error("Error retrieving performance parameters from vdc " + shortVdcId, ex);
                throw APIException.internalServerErrors.genericApisvcError("Error retrieving remote performance parameters", ex);
            }
        }

        BlockPerformanceParamsBulkRep bulkRespose = new BlockPerformanceParamsBulkRep();
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
    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }
}
