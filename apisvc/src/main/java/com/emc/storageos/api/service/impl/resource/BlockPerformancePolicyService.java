/*
 * Copyright 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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

import org.apache.commons.collections.CollectionUtils;
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
import com.emc.storageos.db.client.model.VolumeTopology.VolumeTopologyRole;
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
import com.emc.storageos.model.block.BlockPerformancePolicyMapEntry;
import com.emc.storageos.model.block.BlockPerformancePolicyRestRep;
import com.emc.storageos.model.block.BlockPerformancePolicyUpdate;
import com.emc.storageos.model.block.BlockPerformancePolicyChange;
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
import com.emc.storageos.util.VPlexUtil;
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
     * Update the performance policies for the requested volumes to the requested policies.
     * 
     * NOTE: When updating the performance policy for a volume, only the auto tiering policy,
     * host I/O bandwidth limit, host I/O IOPS limit, and compression setting may be changed.
     * 
     * @prereq none
     *
     * @param param The request payload specifying the volumes to be modified and the new policies.
     * 
     * @brief Update performance policies for volumes.
     * 
     * @return A TaskList.
     */
    @POST
    @Path("/volumes/change-policy")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskList changePerformancePolicyForVolumes(BlockPerformancePolicyChange param) {
        List<Volume> volumes = new ArrayList<>();
        Map<String, Map<URI, PerformancePolicy>> newPerfPolicyMap = null;
        TaskList taskList = new TaskList();
        try {
            // Get and verify the volume ids field
            List<URI> volumeURIs = param.getVolumes();
            ArgValidator.checkFieldNotEmpty(volumeURIs, "volumes");
            volumes.addAll(getAndVerifyVolumesForPolicyChange(volumeURIs));
            
            // Determine if any of the volumes in the request are non-VPLEX volumes.
            boolean isNonVplex = isNonVplexVolumeForPolicyChange(volumes);
            
            // Get and verify the new performance policies. Note that there could be
            // multiple in the case of VPLEX volumes as a new policy can be specified
            // for the PRIMARY and HA sides of the VPLEX volume.
            List<BlockPerformancePolicyMapEntry> newPolicies = param.getPolicies();
            newPerfPolicyMap = getAndVerifyPoliciesForPolicyChange(newPolicies, isNonVplex);
            
            // TBD Heg - May have to make changes for volumes in a CG if that CG corresponds to a storage group.
    
            // Change the performance policy.
            changePerformancePolicy(volumes, null, newPerfPolicyMap, taskList);
        } catch (APIException | InternalException e) {
            String errorMsg = String.format("Error attempting performance policy change for volume(s) %s to %s: %s",
                    getVolumeInfo(volumes), getPerformancePolicyInfo(newPerfPolicyMap), e.getMessage());
            logger.error(errorMsg);
            for (TaskResourceRep task : taskList.getTaskList()) {
                task.setState(Operation.Status.error.name());
                task.setMessage(errorMsg);
                _dbClient.error(Volume.class, task.getResource().getId(), task.getOpId(), e);
            }
            throw e;
        } catch (Exception e) {
            String errorMsg = String.format("Error attempting performance policy change for volumes %s to %s: %s",
                    getVolumeInfo(volumes), getPerformancePolicyInfo(newPerfPolicyMap), e.getMessage());
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
                    AuditLogManager.AUDITOP_BEGIN, volume.getLabel(), getPerformancePolicyInfo(newPerfPolicyMap));
        }        
        
        return taskList;
    }

    /**
     * Update the performance policies for the volumes in the specified CG to the requested policies.
     * 
     * NOTE: When updating the performance policy for a volume, only the auto tiering policy,
     * host I/O bandwidth limit, host I/O IOPS limit, and compression setting may be changed.

     * @prereq none
     *
     * @param id The URI of the consistency group.
     * @param param The request payload specifying the new policy info.
     * 
     * @brief Update performance policies for a block consistency group.
     * 
     * @return A TaskList.
     */
    @SuppressWarnings("unchecked")
    @POST
    @Path("/consistency-groups/{id}/change-policy")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskList changePerformancePolicyForConsistencyGroup(@PathParam("id") URI id, BlockPerformancePolicyChange param) {
        BlockConsistencyGroup cg = null;
        List<Volume> cgVolumes = new ArrayList<>();
        Map<String, Map<URI, PerformancePolicy>> newPerfPolicyMap = null;
        TaskList taskList = new TaskList();
        try {
            // Verify the consistency group.
            ArgValidator.checkFieldUriType(id, BlockConsistencyGroup.class, "id");
            cg = _permissionsHelper.getObjectById(id, BlockConsistencyGroup.class);
            ArgValidator.checkEntity(cg, id, isIdEmbeddedInURL(id));
            
            // Get the list of volumes in the consistency group.
            cgVolumes.addAll(CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, Volume.class,
                    ContainmentConstraint.Factory.getVolumesByConsistencyGroup(id)));
            if (cgVolumes.isEmpty()) {
                throw APIException.badRequests.EmptyConsistencyGroupForPerformancePolicyChange(cg.getLabel());
            }
            
            // Determine if any of the volumes in the request are non-VPLEX volumes.
            boolean isNonVplex = isNonVplexVolumeForPolicyChange(cgVolumes);
            
            // Get and verify the new performance policies. Note that there could be
            // multiple in the case of VPLEX volumes as a new policy can be specified
            // for the PRIMARY and HA sides of the VPLEX volume.
            List<BlockPerformancePolicyMapEntry> newPolicies = param.getPolicies();
            newPerfPolicyMap = getAndVerifyPoliciesForPolicyChange(newPolicies, isNonVplex);
            
            // Create a unique task id.
            String taskId = UUID.randomUUID().toString();

            // Create a task in the task list for the consistency group.
            Operation op = _dbClient.createTaskOpStatus(BlockConsistencyGroup.class, cg.getId(), taskId,
                    ResourceOperationTypeEnum.CHANGE_PERFORMANCE_POLICY);
            taskList.getTaskList().add(TaskMapper.toTask(cg, taskId, op));
            
            // Change the performance policy.
            changePerformancePolicy(cgVolumes, id, newPerfPolicyMap, taskList);
        } catch (APIException | InternalException e) {
            String errorMsg = String.format("Error attempting performance policy change for consistency group %s to %s: %s",
                    (cg != null ? cg.getLabel() : "null"), getPerformancePolicyInfo(newPerfPolicyMap), e.getMessage());
            logger.error(errorMsg);
            for (TaskResourceRep task : taskList.getTaskList()) {
                task.setState(Operation.Status.error.name());
                task.setMessage(errorMsg);
                _dbClient.error(URIUtil.getModelClass(task.getResource().getId()), task.getResource().getId(), task.getOpId(), e);
            }
            throw e;
        } catch (Exception e) {
            String errorMsg = String.format("Error attempting performance policy change for consistency group %s to %s: %s",
                    (cg != null ? cg.getLabel() : "null"), getPerformancePolicyInfo(newPerfPolicyMap), e.getMessage());
            logger.error(errorMsg);
            APIException apie = APIException.internalServerErrors.unexpectedErrorChangingPerformanceProfile(errorMsg, e);
            for (TaskResourceRep task : taskList.getTaskList()) {
                task.setState(Operation.Status.error.name());
                task.setMessage(apie.getMessage());
                _dbClient.error(URIUtil.getModelClass(task.getResource().getId()), task.getResource().getId(), task.getOpId(), apie);
            }
            throw apie;
        }
        
        // Record Audit operation on the consistency group.
        auditOp(OperationTypeEnum.CHANGE_CG_PERFORMANCE_POLICY, true,
                AuditLogManager.AUDITOP_BEGIN, cg.getLabel(), getPerformancePolicyInfo(newPerfPolicyMap));    
         
        return taskList;
    }
    
    /**
     * Change the performance policy for the passed volumes to the passed policy.
     * 
     * @param volumes The volumes whose performance policy is to be changed.
     * @param cgURI The URI of the CG when the request is on a CG, else null.
     * @param newPerfPolicyMap A map of the new performance policies keyed by volume topology role.
     * @param taskList A reference to the task list.
     */
    private void changePerformancePolicy(List<Volume> volumes, URI cgURI,
            Map<String, Map<URI, PerformancePolicy>> newPerfPolicyMap, TaskList taskList) {
        // Verify the volumes for the request.
        String primarySystemTypeForRequest = null;
        String haSystemTypeForRequest = null;
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
            
            // Only allow for volumes on VNX, VMAX, and HDS systems and VPLEX where the backing volumes
            // are on these system types, as these are the only systems supported by the controller.
            // Further, make sure that the volumes are all on systems of the same type. We do not restrict
            // them to the same system, just the same type. The allowed changes vary by system type. VNX 
            // and HDS support only auto tiering policy changes. While VMAX systems can support change of 
            // all 4 modifiable attributes.
            StorageSystem storageSystem = _permissionsHelper.getObjectById(volume.getStorageController(), StorageSystem.class);
            String primarySystemType = storageSystem.getSystemType();
            String haSystemType = null;
            if (DiscoveredDataObject.Type.vplex.name().equals(primarySystemType)) {
                Volume primaryBackendVolume = VPlexUtil.getVPLEXBackendVolume(volume, true, _dbClient, true);
                StorageSystem primaryBackendstorageSystem = _permissionsHelper.getObjectById(primaryBackendVolume.getStorageController(), StorageSystem.class);
                primarySystemType = primaryBackendstorageSystem.getSystemType();
                Volume haBackendVolume = VPlexUtil.getVPLEXBackendVolume(volume, false, _dbClient, false);
                if (haBackendVolume != null) {
                    StorageSystem haBackendstorageSystem = _permissionsHelper.getObjectById(haBackendVolume.getStorageController(), StorageSystem.class);
                    haSystemType = haBackendstorageSystem.getSystemType();                    
                }
            }
            primarySystemTypeForRequest = verifySystemTypeForPolicyChange(volume, primarySystemType, primarySystemTypeForRequest);
            if (haSystemType != null) {
                haSystemTypeForRequest = verifySystemTypeForPolicyChange(volume, haSystemType, haSystemTypeForRequest);
            }

            // Add the volume to the list.
            volumeURIs.add(volume.getId());
        }
        
        // Convert new policies to a simple map specifying the new performance
        // policy URI for each volume topology role.
        Map<String, URI> newPerfPolicyURIMap = new HashMap<>();
        for (String role : newPerfPolicyMap.keySet()) {
            newPerfPolicyURIMap.put(role, newPerfPolicyMap.get(role).keySet().iterator().next());
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
        exportController.updatePerformancePolicy(volumeURIs, cgURI, newPerfPolicyURIMap, taskId);        
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
     * Determines if any of the volumes for a performance policy change is not a VPLEX volume.
     * 
     * @param volumes The list of volumes for the policy change request
     * 
     * @return true if any of the volumes in the request is not a VPLEX volume, else false.
     */
    private boolean isNonVplexVolumeForPolicyChange(List<Volume> volumes) {
        boolean isNonVplexVolume = false;
        for (Volume volume : volumes) {
            if (!VPlexUtil.isVplexVolume(volume, _dbClient)) {
                isNonVplexVolume = true;
                break;
            }
        }
        return isNonVplexVolume;
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
     * Verifies and returns the new performance policies for a performance policy change request.
     * 
     * @param policies The polices specified in the request.
     * @param isNonVplex true if the policy change request specifies a non-VPLEX volume, false otherwise.
     * 
     * @return A map of the the new policies by volume topology role.
     */
    private Map<String, Map<URI, PerformancePolicy>> getAndVerifyPoliciesForPolicyChange(
            List<BlockPerformancePolicyMapEntry> policies, boolean isNonVplex) {
        Map<String, Map<URI, PerformancePolicy>> policyMap = new HashMap<>();
        ArgValidator.checkFieldNotEmpty(policies, "policies");
        boolean hasPolicyForPrimaryRole = false;
        for (BlockPerformancePolicyMapEntry policyEntry : policies) {
            String role = policyEntry.getRole();
            if (!VolumeTopologyRole.PRIMARY.name().equals(role) && !VolumeTopologyRole.HA.equals(role)) {
                throw APIException.badRequests.InvalidRoleForPerformancePolicyChange(role);
            }
            
            if (VolumeTopologyRole.PRIMARY.name().equals(role)) {
                hasPolicyForPrimaryRole = true;
            }
            
            URI newPerfPolicyURI = policyEntry.getId();
            ArgValidator.checkFieldUriType(newPerfPolicyURI, PerformancePolicy.class, "id");
            PerformancePolicy newPerfPolicy = (PerformancePolicy)queryResource(newPerfPolicyURI);
            ArgValidator.checkEntity(newPerfPolicy, newPerfPolicyURI, isIdEmbeddedInURL(newPerfPolicyURI));
            
            if (!policyMap.containsKey(role)) {
                Map<URI, PerformancePolicy> perfPolicyURIMap = new HashMap<>();
                policyMap.put(role, perfPolicyURIMap);
            }
            policyMap.get(role).put(newPerfPolicyURI, newPerfPolicy);
        }
        
        // If the policy change request specifies a non-VPLEX volume, then
        // there must be a policy specified for the primary role.
        if (isNonVplex && !hasPolicyForPrimaryRole) {
            throw APIException.badRequests.NoPolicyForPrimaryRoleForPerformancePolicyChange();
        }
        
        return policyMap;
    }

    /**
     * Gets info about the passed performance policy map to be included in log and error messages.
     * 
     * @param perfPolicyMap A map of performance policies keyed by volume topology role.
     * 
     * @return A string specifying the policy info.
     */
    private String getPerformancePolicyInfo(Map<String, Map<URI, PerformancePolicy>> perfPolicyMap) {
        StringBuilder policyInfoBuilder = new StringBuilder();
        if (perfPolicyMap != null) {
            for (String role : perfPolicyMap.keySet()) {
                Map<URI, PerformancePolicy> perfPolicyURIMap = perfPolicyMap.get(role);
                if (policyInfoBuilder.length() > 0) {
                    policyInfoBuilder.append(", ");
                }
                policyInfoBuilder.append(role);
                policyInfoBuilder.append(":");
                policyInfoBuilder.append(perfPolicyURIMap.values().iterator().next().getLabel());
            }            
        }
        return policyInfoBuilder.toString();        
    }

    /**
     * Verifies the system types of the volumes for a performance policy change request.
     * 
     * @param volume A reference to a volume in the request.
     * @param systemType The system type for the volume.
     * @param requiredSystemType The system type required for the request, or null if not yet set.
     * 
     * @return The required system type for the request.
     */
    private String verifySystemTypeForPolicyChange(Volume volume, String systemType, String requiredSystemType) {
        String resultSystemType = requiredSystemType;
        
        // Only allow for volumes on VNX, VMAX, and HDS systems as these are the only systems
        // supported by the controller.
        if (!DiscoveredDataObject.Type.hds.name().equals(systemType) &&
                !DiscoveredDataObject.Type.vnxblock.name().equals(systemType) &&
                !DiscoveredDataObject.Type.isVmaxStorageSystem(systemType)) {
            throw BadRequestException.badRequests.InvalidSystemTypeForPerformancePolicyChange(volume.getLabel());
        }

        // Further, make sure that the volumes are all on systems of the same type. We do
        // not restrict them to the same system, just the same type. The allowed changes 
        // vary by system type. VNX and HDS support only auto tiering policy changes.
        // While VMAX systems can support change of all 4 modifiable attributes.
        if (requiredSystemType == null) {
            // The required system type has yet to be determined, so
            // set it now and return this value to the caller.
            resultSystemType = systemType;
        } else if (!systemType.equals(requiredSystemType)) {
            throw BadRequestException.badRequests.InvalidSystemsForPerformancePolicyChange(volume.getLabel());                    
        }
        
        return resultSystemType;
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
