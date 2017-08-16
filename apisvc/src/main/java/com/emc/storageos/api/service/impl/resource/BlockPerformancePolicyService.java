/*
 * Copyright 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
import com.emc.storageos.api.mapper.TaskMapper;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.utils.BlockServiceUtils;
import com.emc.storageos.api.service.impl.resource.utils.GeoVisibilityHelper;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.PerformancePolicy;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.auth.ACLAssignmentChanges;
import com.emc.storageos.model.auth.ACLAssignments;
import com.emc.storageos.model.block.BlockPerformancePolicyBulkRep;
import com.emc.storageos.model.block.BlockPerformancePolicyCreate;
import com.emc.storageos.model.block.BlockPerformancePolicyList;
import com.emc.storageos.model.block.BlockPerformancePolicyRestRep;
import com.emc.storageos.model.block.BlockPerformancePolicyUpdate;
import com.emc.storageos.model.block.BlockPerformancePolicyVolumePolicyChange;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.security.geo.GeoServiceClient;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.BlockExportController;
import com.google.common.collect.Lists;

/**
 * API service for creating and managing PerformancePolicy instances.
 */
@Path("/block/performance-policies")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, readAcls = { ACL.USE },
writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class BlockPerformancePolicyService extends TaggedResource {

    private static final String EVENT_SERVICE_TYPE = "PERFORMANCEPOLICY";
    
    @Autowired
    protected GeoVisibilityHelper _geoHelper;

    // Logger reference
    private static final Logger logger = LoggerFactory.getLogger(BlockPerformancePolicyService.class);

    /**
     * Create a block PerformancePolicy instance.
     *
     * @prereq none
     * 
     * @param param The details for the new PerformancePolicy instance to be created.
     * 
     * @brief Create block PerformancePolicy instance.
     * 
     * @return BlockPerformancePolicyRestRep
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public BlockPerformancePolicyRestRep createBlockPerformancePolicy(BlockPerformancePolicyCreate param) {
        
        // Verify the name.
        String performancePolicyName = param.getName();
        ArgValidator.checkFieldNotEmpty(performancePolicyName, "Name");
        checkForDuplicateName(performancePolicyName, PerformancePolicy.class);
        
        URI performancePolicyURI = URIUtil.createId(PerformancePolicy.class);
        logger.info("Creating PerformancePolicy instance {} with id {}", performancePolicyName, performancePolicyURI);
        
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

        PerformancePolicy performancePolicy = new PerformancePolicy();
        performancePolicy.setId(performancePolicyURI);
        performancePolicy.setLabel(performancePolicyName);
        if (NullColumnValueGetter.isNotNullValue(param.getDescription())) {
            performancePolicy.setDescription(param.getDescription());
        }
        performancePolicy.setAutoTierPolicyName(param.getAutoTieringPolicyName());
        performancePolicy.setCompressionEnabled(param.getCompressionEnabled());
        performancePolicy.setHostIOLimitBandwidth(hostIOLimitBandwidth);
        performancePolicy.setHostIOLimitIOPs(hostIOLimitIOPs);
        performancePolicy.setThinVolumePreAllocationPercentage(param.getThinVolumePreAllocationPercentage());
        performancePolicy.setDedupCapable(param.getDedupCapable());
        performancePolicy.setFastExpansion(param.getFastExpansion());
        _dbClient.createObject(performancePolicy);
        auditOp(OperationTypeEnum.CREATE_PERFORMANCE_POLICY, true, null, performancePolicy.getId().toString(), performancePolicy.getLabel());
        
        return BlockMapper.map(performancePolicy);
    }

    /**
     * Update a block PerformancePolicy instance.
     *
     * @prereq none
     * 
     * @param id The id of the PerformancePolicy instance to update.
     * @param param The update values.
     * 
     * @brief Update block PerformancePolicy instance.
     * 
     * @return BlockPerformancePolicyRestRep
     */
    @PUT
    @Path("/{id}")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public BlockPerformancePolicyRestRep updateBlockPerformancePolicy(@PathParam("id") URI id, BlockPerformancePolicyUpdate param) {
        
        logger.info("Updating PerformancePolicy with id {}", id);

        // Verify the instance is valid.
        ArgValidator.checkFieldUriType(id, PerformancePolicy.class, "id");
        PerformancePolicy performancePolicy = (PerformancePolicy) queryResource(id);
        
        // If the performance policy is currently in use by a volume, then the
        // only parameters that can be updated are the name and description.
        List<Volume> performancePolicyVolumes = CustomQueryUtility.queryActiveResourcesByConstraint(
                _dbClient, Volume.class, ContainmentConstraint.Factory.getVolumePerformancePolicyConstraint(id));
        boolean performancePolicyInUse = !performancePolicyVolumes.isEmpty();

        // Verify the name and update.
        boolean isUpdated = false;
        String currentName = performancePolicy.getLabel();
        String performancePolicyName = param.getName();
        if ((NullColumnValueGetter.isNotNullValue(performancePolicyName)) &&
                (!performancePolicyName.equals(performancePolicy.getLabel()))) {
            checkForDuplicateName(performancePolicyName, PerformancePolicy.class);
            performancePolicy.setLabel(performancePolicyName);
            isUpdated = true;
        }

        // Update performance policy settings. If a value is not specified, then
        // do not modify that value.
        String description = param.getDescription();
        if ((NullColumnValueGetter.isNotNullValue(description)) &&
                (!description.equals(performancePolicy.getDescription()))) {
            performancePolicy.setDescription(description);
            isUpdated = true;
        }
        
        // Update the autotiering policy name.
        String autoTieringPolicyName = param.getAutoTieringPolicyName();
        if ((NullColumnValueGetter.isNotNullValue(autoTieringPolicyName)) &&
                (!autoTieringPolicyName.equals(performancePolicy.getAutoTierPolicyName()))) {
            if (!performancePolicyInUse) {
                performancePolicy.setAutoTierPolicyName(autoTieringPolicyName);
                isUpdated = true;
            } else {
                throw BadRequestException.badRequests.cantUpdatePerformancePolicyInUse(currentName);
            }
        }
        
        // Update the compression setting.
        Boolean compressionEnabled = param.getCompressionEnabled();
        if ((compressionEnabled != null) && (compressionEnabled != performancePolicy.getCompressionEnabled())) {
            if (!performancePolicyInUse) {
                performancePolicy.setCompressionEnabled(compressionEnabled);
                isUpdated = true;
            } else {
                throw BadRequestException.badRequests.cantUpdatePerformancePolicyInUse(currentName);                
            }
        }
        
        // Update the host I/O bandwidth limit.
        Integer hostIOLimitBandwidth = param.getHostIOLimitBandwidth();
        if ((hostIOLimitBandwidth != null) && (hostIOLimitBandwidth != performancePolicy.getHostIOLimitBandwidth())) {
            if (!performancePolicyInUse) {
                performancePolicy.setHostIOLimitBandwidth(hostIOLimitBandwidth);
                isUpdated = true;
            } else {
                throw BadRequestException.badRequests.cantUpdatePerformancePolicyInUse(currentName);                
            }
        }
        
        // Update the host I/O IOPS limit.
        Integer hostIOLimitIOPs = param.getHostIOLimitIOPs();
        if ((hostIOLimitIOPs != null) && (hostIOLimitIOPs != performancePolicy.getHostIOLimitIOPs())) {
            if (!performancePolicyInUse) {
                performancePolicy.setHostIOLimitIOPs(hostIOLimitIOPs);
                isUpdated = true;
            } else {
                throw BadRequestException.badRequests.cantUpdatePerformancePolicyInUse(currentName);                
            }
        }
        
        // Update the thin volume pre-allocation percentage.
        Integer thinVolumePreAllocPercentage = param.getThinVolumePreAllocationPercentage();
        if ((thinVolumePreAllocPercentage != null) && 
                (thinVolumePreAllocPercentage != performancePolicy.getThinVolumePreAllocationPercentage())) {
            if (!performancePolicyInUse) {
                performancePolicy.setThinVolumePreAllocationPercentage(thinVolumePreAllocPercentage);
                isUpdated = true;
            } else {
                throw BadRequestException.badRequests.cantUpdatePerformancePolicyInUse(currentName);
            }
        }
        
        // Update the deduplication setting.
        Boolean dedupCapable = param.getDedupCapable();
        if ((dedupCapable != null) && (dedupCapable != performancePolicy.getDedupCapable())) {
            if (!performancePolicyInUse) {
                performancePolicy.setDedupCapable(dedupCapable);
                isUpdated = true;
            } else {
                throw BadRequestException.badRequests.cantUpdatePerformancePolicyInUse(currentName);
            }
        }
        
        // Update the fast expansion setting.
        Boolean fastExpansion = param.getFastExpansion();
        if ((fastExpansion != null) && (fastExpansion != performancePolicy.getFastExpansion())) {
            if (!performancePolicyInUse) {
                performancePolicy.setFastExpansion(fastExpansion);
                isUpdated = true;
            } else {
                throw BadRequestException.badRequests.cantUpdatePerformancePolicyInUse(currentName);
            }
        }
        
        // Update the database and return the updated instance.
        if (isUpdated) {
            auditOp(OperationTypeEnum.UPDATE_PERFORMANCE_POLICY, true, null, performancePolicy.getId().toString(), performancePolicy.getLabel());
            _dbClient.updateObject(performancePolicy);
        }
        return BlockMapper.map(performancePolicy);
    }

    /**
     * Delete a block PerformancePolicy instance.
     *
     * @prereq none
     * 
     * @param id The id of the PerformancePolicy instance to delete.
     * 
     * @brief Delete block PerformancePolicy instance.
     * 
     * @return Response
     */
    @POST
    @Path("/{id}/deactivate")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public Response deleteBlockPerformancePolicy(@PathParam("id") URI id) {
        
        logger.info("Deleting PerformancePolicy with id {}", id);

        // Verify the instance is valid.
        ArgValidator.checkFieldUriType(id, PerformancePolicy.class, "id");
        PerformancePolicy performancePolicy = (PerformancePolicy) queryResource(id);
        
        // Verify that no volumes are referencing the instance to be deleted.
        checkForDelete(performancePolicy);
        
        // Mark it for deletion.
        auditOp(OperationTypeEnum.DELETE_PERFORMANCE_POLICY, true, null, performancePolicy.getId().toString(), performancePolicy.getLabel());
        _dbClient.markForDeletion(performancePolicy);
        
        return Response.ok().build();
    }

    /**
     * Get all block PerformancePolicy instances.
     *
     * @prereq none
     * 
     * @brief Get all block PerformancePolicy instances.
     * 
     * @return BlockPerformancePolicyList
     */
    @GET
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public BlockPerformancePolicyList getAllPerformancePolicies(
            @DefaultValue("") @QueryParam(TENANT_ID_QUERY_PARAM) String tenantId,
            @DefaultValue("") @QueryParam(VDC_ID_QUERY_PARAM) String shortVdcId) {
        _geoHelper.verifyVdcId(shortVdcId);
        return getPerformancePolicyList(shortVdcId, tenantId);
    }
    
    /**
     * Get all performance policies for the passed tenant for which the user has permission.
     * 
     * @param shortVdcId The VDC id.
     * @param tenantId The tenant id.
     * 
     * @return BlockPerformancePolicyList
     */
    private BlockPerformancePolicyList getPerformancePolicyList(String shortVdcId, String tenantId) {

        BlockPerformancePolicyList performancePolicyList = new BlockPerformancePolicyList();

        // If input tenant is not empty and the user has no access to it, return an empty list.
        TenantOrg tenant = null;
        if (!StringUtils.isEmpty(tenantId)) {
            tenant = getTenantIfHaveAccess(tenantId);
            if (tenant == null) {
                return performancePolicyList;
            }
        }

        List<PerformancePolicy> allPerformancePolicies = null;
        if (_geoHelper.isLocalVdcId(shortVdcId)) {
            List<URI> performancePolicyURIs = _dbClient.queryByType(PerformancePolicy.class, true);
            allPerformancePolicies = _dbClient.queryObject(PerformancePolicy.class, performancePolicyURIs);
        } else {
            GeoServiceClient geoClient = _geoHelper.getClient(shortVdcId);
            try {
                Iterator<URI> performancePolicyURIIter = geoClient.queryByType(PerformancePolicy.class, true);
                List<URI> performancePolicyURIs = Lists.newArrayList(performancePolicyURIIter);
                Iterator<PerformancePolicy> performancePolicyIter = geoClient.queryObjects(PerformancePolicy.class, performancePolicyURIs);
                allPerformancePolicies = Lists.newArrayList();
                while (performancePolicyIter.hasNext()) {
                    allPerformancePolicies.add(performancePolicyIter.next());
                }
            } catch (Exception ex) {
                logger.error("Error retrieving performance policies from vdc " + shortVdcId, ex);
                throw APIException.internalServerErrors.genericApisvcError("Error retrieving remote performance policies", ex);
            }
        }

        // Return all if Role.SYSTEM_ADMIN or Role.SYSTEM_MONITOR and no tenant restrictions.
        // Otherwise, return only those for which the tenant has access.
        StorageOSUser user = getUserFromContext();
        if (_permissionsHelper.userHasGivenRole(user, null, Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR)) {
            for (PerformancePolicy performancePolicy : allPerformancePolicies) {
                if (tenant == null || _permissionsHelper.tenantHasUsageACL(tenant.getId(), performancePolicy)) {
                    performancePolicyList.getPerformancePolicies().add(DbObjectMapper.toNamedRelatedResource(performancePolicy));
                }
            }
        } else {
            URI tenantURI = null;
            if (tenant == null) {
                tenantURI = URI.create(user.getTenantId());
            } else {
                tenantURI = tenant.getId();
            }

            Set<PerformancePolicy> allowedPerformancePolicies = new HashSet<>();
            for (PerformancePolicy performancePolicy : allPerformancePolicies) {
                if (_permissionsHelper.tenantHasUsageACL(tenantURI, performancePolicy)) {
                    allowedPerformancePolicies.add(performancePolicy);
                }
            }

            // If no tenant is specified, include performance policies for which 
            // sub-tenants of the user have access.
            if (tenant == null) {
                List<URI> subtenantURIs = _permissionsHelper.getSubtenantsWithRoles(user);
                for (PerformancePolicy performancePolicy : allPerformancePolicies) {
                    if (_permissionsHelper.tenantHasUsageACL(subtenantURIs, performancePolicy)) {
                        allowedPerformancePolicies.add(performancePolicy);
                    }
                }
            }

            for (PerformancePolicy performancePolicy : allowedPerformancePolicies) {
                performancePolicyList.getPerformancePolicies().add(DbObjectMapper.toNamedRelatedResource(performancePolicy));
            }
        }

        return performancePolicyList;
    }

    /**
     * Get the block PerformancePolicy instance with the passed id.
     *
     * @prereq none
     * 
     * @param id The id of the PerformancePolicy instance.
     * 
     * @brief Get block PerformancePolicy instance.
     * 
     * @return BlockPerformancePolicyRestRep
     */
    @GET
    @Path("/{id}")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, acls = { ACL.USE })
    public BlockPerformancePolicyRestRep getBlockPerformancePolicy(@PathParam("id") URI id) {
        PerformancePolicy performancePolicy = getPerformancePolicy(id);
        return BlockMapper.map(performancePolicy);
    }

    /**
     * Get the performance policy instance with the passed id.
     * 
     * @param id The id of the PerformancePolicy instance.
     * 
     * @return A reference to a PerformancePolicy instance
     */
    private PerformancePolicy getPerformancePolicy(URI id) {
        ArgValidator.checkUri(id);
        PerformancePolicy performancePolicy = null;
        if (_geoHelper.isLocalURI(id)) {
            performancePolicy = (PerformancePolicy) queryResource(id);
        } else {
            String shortVdcId = VdcUtil.getVdcId(PerformancePolicy.class, id).toString();
            GeoServiceClient geoClient = _geoHelper.getClient(shortVdcId);
            try {
                performancePolicy = geoClient.queryObject(PerformancePolicy.class, id);
                ArgValidator.checkEntityNotNull(performancePolicy, id, isIdEmbeddedInURL(id));
            } catch (Exception ex) {
                logger.error("Error retrieving performance policy from vdc " + shortVdcId, ex);
                throw APIException.internalServerErrors.genericApisvcError("Error retrieving remote performance policy", ex);
            }
        }
        return performancePolicy;
    }

    /**
     * Get performance policy ACLs.
     *
     * @prereq none
     * 
     * @param id The id of the PerformancePolicy instance.
     * 
     * @brief Show ACL assignment for performance policy.
     * 
     * @return ACL assignment details.
     */
    @GET
    @Path("/{id}/acl")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public ACLAssignments getAcls(@PathParam("id") URI id) {
        return getPerformancePolicyAcls(id);
    }
    
    /**
     * Add or remove individual performance policy ACL entry(s).
     * Request body must include at least one add or remove operation.
     *
     * @prereq none
     * 
     * @param id The id of the PerformancePolicy instance.
     * @param aclAssignmentsChanges The ACL assignment changes.
     * 
     * @brief Add or remove performance policy ACL entries.
     * 
     * @return No data returned in response body.
     */
    @PUT
    @Path("/{id}/acl")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN }, blockProxies = true)
    public ACLAssignments updateAcls(@PathParam("id") URI id, ACLAssignmentChanges aclAssignmentsChanges) {
        ArgValidator.checkFieldUriType(id, PerformancePolicy.class, "id");
        PerformancePolicy performancePolicy = (PerformancePolicy) queryResource(id);
        _permissionsHelper.updateACLs(performancePolicy, aclAssignmentsChanges,
                new PermissionsHelper.UsageACLFilter(_permissionsHelper, null));
        _dbClient.updateObject(performancePolicy);
        auditOp(OperationTypeEnum.MODIFY_PERFORMANCE_POLICY_ACL, true, null, performancePolicy.getId().toString(), performancePolicy.getLabel());
        return getPerformancePolicyAcls(id);
    }

    /**
     * Get performance policy ACLs
     * 
     * @param id The id of the PerformancePolicy instance.
     * 
     * @return ACL assignment details.
     */
    private ACLAssignments getPerformancePolicyAcls(URI id) {
        PerformancePolicy performancePolicy = (PerformancePolicy) queryResource(id);
        ArgValidator.checkEntityNotNull(performancePolicy, id, isIdEmbeddedInURL(id));
        ACLAssignments response = new ACLAssignments();
        response.setAssignments(PermissionsHelper.convertToACLEntries(performancePolicy.getAcls()));
        return response;
    }
    
    /**
     * Update the performance policy for the requested volumes to the requested policy.
     * 
     * NOTE: When updating the performance policy for a volume, only the auto tiering policy,
     * host I/O bandwidth limit, host I/O IOPS limit, and compression setting may be changed.
     * 
     * @prereq none
     *
     * @param param The request payload specifying the volumes to be modified and the new policy.
     * 
     * @brief Update performance policy for volumes.
     * 
     * @return A TaskList.
     */
    @POST
    @Path("/volumes/change-policy")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskList changePerformancePolicyForVolumes(BlockPerformancePolicyVolumePolicyChange param) {
        List<Volume> volumes = new ArrayList<>();
        PerformancePolicy newPerfPolicy = null;
        TaskList taskList = new TaskList();
        try {
            // Get and verify the volume ids field
            List<URI> volumeURIs = param.getVolumes();
            ArgValidator.checkFieldNotEmpty(volumeURIs, "volumes");
            volumes.addAll(getAndVerifyVolumesForPolicyChange(volumeURIs));
            
            // Get and verify the new performance policy URI.
            URI newPerfPolicyURI = param.getPolicy();
            ArgValidator.checkFieldUriType(newPerfPolicyURI, PerformancePolicy.class, "policy");
            newPerfPolicy = (PerformancePolicy)queryResource(newPerfPolicyURI);
            
            // TBD Heg - May have to make changes for volumes in a CG if that CG corresponds to a storage group.
    
            // Change the performance policy.
            changePerformancePolicy(volumes, null, newPerfPolicy, taskList);
        } catch (APIException | InternalException e) {
            String errorMsg = String.format("Error attempting to change the performance policy for volumes %s to policy %s: %s",
                    getVolumeInfo(volumes), (newPerfPolicy != null ? newPerfPolicy.getLabel() : "null"), e.getMessage());
            logger.error(errorMsg);
            for (TaskResourceRep task : taskList.getTaskList()) {
                task.setState(Operation.Status.error.name());
                task.setMessage(errorMsg);
                _dbClient.error(Volume.class, task.getResource().getId(), task.getOpId(), e);
            }
            throw e;
        } catch (Exception e) {
            String errorMsg = String.format("Error attempting to change the performance policy for volumes %s to policy %s: %s",
                    getVolumeInfo(volumes), (newPerfPolicy != null ? newPerfPolicy.getLabel() : "null"), e.getMessage());
            logger.error(errorMsg);
            APIException apie = APIException.internalServerErrors.unexpectedErrorChangingPerformanceProfile(errorMsg, e);
            for (TaskResourceRep task : taskList.getTaskList()) {
                task.setState(Operation.Status.error.name());
                task.setMessage(apie.getMessage());
                _dbClient.error(BlockSnapshot.class, task.getResource().getId(), task.getOpId(), apie);
            }
            throw apie;
        }
        
        // Record Audit operation on the volumes.
        for (Volume volume : volumes) {
            auditOp(OperationTypeEnum.CHANGE_VOLUME_PERFORMANCE_POLICY, true,
                    AuditLogManager.AUDITOP_BEGIN, volume.getLabel(), newPerfPolicy.getLabel());
        }        
        
        return taskList;
    }

    /**
     * Update the performance policy for the volumes in the specified CG to the requested policy.
     * 
     * NOTE: When updating the performance policy for a volume, only the auto tiering policy,
     * host I/O bandwidth limit, host I/O IOPS limit, and compression setting may be changed.

     * @prereq none
     *
     * @param id The URI of the consistency group.
     * @param policyId The URI of the new policy.
     * 
     * @brief Update performance policy for volumes.
     * 
     * @return A TaskList.
     */
    @POST
    @Path("/consistency-groups/{id}/change-policy")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskList changePerformancePolicyForConsistencyGroup(@PathParam("id") URI id, @QueryParam("policyId") URI policyId) {
        BlockConsistencyGroup cg = null;
        PerformancePolicy newPerfPolicy = null;
        List<Volume> cgVolumes = new ArrayList<>();
        TaskList taskList = new TaskList();
        try {
            // Verify the consistency group.
            ArgValidator.checkFieldUriType(id, BlockConsistencyGroup.class, "id");
            cg = _permissionsHelper.getObjectById(id, BlockConsistencyGroup.class);
            ArgValidator.checkEntity(cg, id, isIdEmbeddedInURL(id));
            
            // Verify the new performance policy.
            ArgValidator.checkFieldUriType(policyId, PerformancePolicy.class, "policyId");
            newPerfPolicy = _permissionsHelper.getObjectById(policyId, PerformancePolicy.class);
            ArgValidator.checkEntity(newPerfPolicy, policyId, isIdEmbeddedInURL(policyId));
            
            // Get the list of volumes in the consistency group.
            cgVolumes.addAll(CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, Volume.class,
                    ContainmentConstraint.Factory.getVolumesByConsistencyGroup(id)));
            if (cgVolumes.isEmpty()) {
                throw APIException.badRequests.EmptyConsistencyGroupForPerformancePolicyChange(cg.getLabel());
            }
            
            // Create a unique task id.
            String taskId = UUID.randomUUID().toString();

            // Create a task in the task list for the consistency group.
            Operation op = _dbClient.createTaskOpStatus(BlockConsistencyGroup.class, cg.getId(), taskId,
                    ResourceOperationTypeEnum.CHANGE_PERFORMANCE_POLICY);
            taskList.getTaskList().add(TaskMapper.toTask(cg, taskId, op));
            
            // Change the performance policy.
            changePerformancePolicy(cgVolumes, id, newPerfPolicy, taskList);
        } catch (APIException | InternalException e) {
            String errorMsg = String.format("Error attempting to change the performance policy for consistency group %s to policy %s: %s",
                    (cg != null ? cg.getLabel() : "null"), (newPerfPolicy != null ? newPerfPolicy.getLabel() : "null"), e.getMessage());
            logger.error(errorMsg);
            for (TaskResourceRep task : taskList.getTaskList()) {
                task.setState(Operation.Status.error.name());
                task.setMessage(errorMsg);
                _dbClient.error(Volume.class, task.getResource().getId(), task.getOpId(), e);
            }
            throw e;
        } catch (Exception e) {
            String errorMsg = String.format("Error attempting to change the performance policy for consistency group %s to policy %s: %s",
                    (cg != null ? cg.getLabel() : "null"), (newPerfPolicy != null ? newPerfPolicy.getLabel() : "null"), e.getMessage());
            logger.error(errorMsg);
            APIException apie = APIException.internalServerErrors.unexpectedErrorChangingPerformanceProfile(errorMsg, e);
            for (TaskResourceRep task : taskList.getTaskList()) {
                task.setState(Operation.Status.error.name());
                task.setMessage(apie.getMessage());
                _dbClient.error(BlockSnapshot.class, task.getResource().getId(), task.getOpId(), apie);
            }
            throw apie;
        }
        
        // Record Audit operation on the consistency group.
        auditOp(OperationTypeEnum.CHANGE_CG_PERFORMANCE_POLICY, true,
                AuditLogManager.AUDITOP_BEGIN, cg.getLabel(), newPerfPolicy.getLabel());    
         
        return taskList;
    }
    
    /**
     * Change the performance policy for the passed volumes to the passed policy.
     * 
     * @param volumes The volumes whose performance policy is to be changed.
     * @param cgURI The URI of the CG when the request is on a CG, else null.
     * @param newPerfPolicy The new performance policy
     * @param taskList A reference to the task list.
     */
    private void changePerformancePolicy(List<Volume> volumes, URI cgURI, PerformancePolicy newPerfPolicy, TaskList taskList) {
        // Verify the volumes for the request.
        String systemTypeForRequest = null;
        List<URI> volumeURIs = new ArrayList<>();
        for (Volume volume : volumes) {
            // Make sure that we don't have pending operations against the volume.
            BlockServiceUtils.checkForPendingTasks(volume.getTenant().getURI(), Arrays.asList(volume), _dbClient);
            
            // Get the project.
            URI projectURI = volume.getProject().getURI();
            Project project = _permissionsHelper.getObjectById(projectURI, Project.class);
            ArgValidator.checkEntity(project, projectURI, false);

            // Verify the user is authorized for the volume's project.
            BlockServiceUtils.verifyUserIsAuthorizedForRequest(project, getUserFromContext(), _permissionsHelper);
            
            // Only allow for VNX, VMAX, and HDS systems as these are the only systems supported by the controller.
            StorageSystem storageSystem = _permissionsHelper.getObjectById(volume.getStorageController(), StorageSystem.class);
            String systemType = storageSystem.getSystemType();
            if (!DiscoveredDataObject.Type.hds.name().equals(systemType) &&
                    !DiscoveredDataObject.Type.vnxblock.name().equals(systemType) &&
                    !DiscoveredDataObject.Type.isVmaxStorageSystem(systemType)) {
                throw BadRequestException.badRequests.InvalidSystemTypeForPerformancePolicyChange(volume.getLabel());
            }
            
            // Further, make sure that the volumes are all on systems of the same type. We do
            // not restrict them to the same system, just the same type. The allowed changes 
            // vary by system type. VNX and HDS support only auto tiering policy changes.
            // While VMAX can support change of all 4 modifiable attributes, these also 
            // vary by the type of VMAX.
            if (systemTypeForRequest == null) {
                systemTypeForRequest = systemType;
            } else if (!systemType.equals(systemTypeForRequest)) {
                throw BadRequestException.badRequests.InvalidSystemsForPerformancePolicyChange(volume.getLabel());                    
            }
            
            // Add the volume to the list.
            volumeURIs.add(volume.getId());
        }
        
        // Create a unique task id if the task list is empty. Otherwise, get the 
        // task id from a task in the task list. If this function is called when
        // changing the performance policy for a consistency group, the passed task
        // list will contain the consistency group task.
        String taskId = null;
        if (taskList.getTaskList().isEmpty()) {
            taskId = UUID.randomUUID().toString();
        } else {
            taskId = taskList.getTaskList().get(0).getOpId();
        }

        // Create a task for each volume and add to passed task list.
        for (Volume volume : volumes) {
            Operation op = _dbClient.createTaskOpStatus(Volume.class, volume.getId(), taskId,
                    ResourceOperationTypeEnum.CHANGE_PERFORMANCE_POLICY);
            taskList.getTaskList().add(TaskMapper.toTask(volume, taskId, op));
        }
        
        // Get the export controller and update the performance policy for the passed volumes.
        BlockExportController exportController = getController(BlockExportController.class, BlockExportController.EXPORT);
        exportController.updatePerformancePolicy(volumeURIs, cgURI, newPerfPolicy.getId(), taskId);        
    }

    /**
     * Gets the volumes with the passed URIs and verifies they exist and are active.
     * 
     * @param volumeURIs The URIs of the desired volumes.
     * 
     * @return The list of volumes.
     */
    private List<Volume> getAndVerifyVolumesForPolicyChange(List<URI> volumeURIs) {
        List<Volume> volumes = new ArrayList<>();
        if (volumeURIs != null) {
            for (URI volumeURI : volumeURIs) {
                // Get the volume.
                ArgValidator.checkFieldUriType(volumeURI, Volume.class, "volume");
                Volume volume = _permissionsHelper.getObjectById(volumeURI, Volume.class);
                ArgValidator.checkEntity(volume,  volumeURI, isIdEmbeddedInURL(volumeURI));
                volumes.add(volume);
            }
        }
        return volumes;
    }
    
    /**
     * Gets info about the passed volumes to be included in log and error messages.
     * 
     * @param volumes The list of volumes.
     * 
     * @return A string specifying the volume info.
     */
    private String getVolumeInfo(List<Volume> volumes) {
        StringBuilder volumeInfoBuilder = new StringBuilder();
        if (volumes != null) {
            for (Volume volume : volumes) {
                if (volumeInfoBuilder.length() > 0) {
                    volumeInfoBuilder.append(", ");
                }
                volumeInfoBuilder.append(volume.forDisplay());
            }
        }
        return volumeInfoBuilder.toString();
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public BlockPerformancePolicyBulkRep queryBulkResourceReps(List<URI> ids) {
        String shortVdcId = VdcUtil.getVdcId(getResourceClass(), ids.iterator().next()).toString();
        Iterator<PerformancePolicy> performancePolicyIter;
        if (shortVdcId.equals(VdcUtil.getLocalShortVdcId())) {
            performancePolicyIter = _dbClient.queryIterativeObjects(getResourceClass(), ids);
        } else {
            GeoServiceClient geoClient = _geoHelper.getClient(shortVdcId);
            try {
                performancePolicyIter = geoClient.queryObjects(getResourceClass(), ids);
            } catch (Exception ex) {
                logger.error("Error retrieving performance policy from vdc " + shortVdcId, ex);
                throw APIException.internalServerErrors.genericApisvcError("Error retrieving remote performance policy", ex);
            }
        }

        BlockPerformancePolicyBulkRep bulkRespose = new BlockPerformancePolicyBulkRep();
        while (performancePolicyIter.hasNext()) {
            bulkRespose.getPerformancePolicies().add(BlockMapper.map(performancePolicyIter.next()));
        }
        return bulkRespose;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected DataObject queryResource(URI id) {
        PerformancePolicy performancePolicy = _permissionsHelper.getObjectById(id, PerformancePolicy.class);
        ArgValidator.checkEntity(performancePolicy, id, isIdEmbeddedInURL(id));
        return performancePolicy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.BLOCK_PERFORMANCE_POLICY;
    }
    
    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    protected Class<PerformancePolicy> getResourceClass() {
        return PerformancePolicy.class;
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
