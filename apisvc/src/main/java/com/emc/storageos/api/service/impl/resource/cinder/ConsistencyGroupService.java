/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */
package com.emc.storageos.api.service.impl.resource.cinder;

import static com.emc.storageos.db.client.constraint.ContainmentConstraint.Factory.getVolumesByConsistencyGroup;

import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.resource.BlockService;
import com.emc.storageos.api.service.impl.resource.BlockServiceApi;
import com.emc.storageos.api.service.impl.resource.utils.CinderApiUtils;
import com.emc.storageos.cinder.CinderConstants;
import com.emc.storageos.cinder.model.ConsistencyGroupCreateRequest;
import com.emc.storageos.cinder.model.ConsistencyGroupCreateResponse;
import com.emc.storageos.cinder.model.ConsistencyGroupDeleteRequest;
import com.emc.storageos.cinder.model.ConsistencyGroupDetail;
import com.emc.storageos.cinder.model.ConsistencyGroupsResponse;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.ScopedLabel;
import com.emc.storageos.db.client.model.ScopedLabelSet;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * This class provide consistency group CRUD service on openstack request.
 * 
 * @author singhc1
 * 
 */
@Path("/v2/{tenant_id}/consistencygroups")
@DefaultPermissions(readRoles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, readAcls = {
        ACL.OWN, ACL.ALL }, writeRoles = { Role.TENANT_ADMIN }, writeAcls = {
        ACL.OWN, ACL.ALL })
public class ConsistencyGroupService extends AbstractConsistencyGroupService {

    private static final Logger _log = LoggerFactory.getLogger(ConsistencyGroupService.class);

    // Consistency group name max character
    private static final int CG_MAX_LIMIT = 64;

    /**
     * This function handles Get request for a consistency group detail
     * 
     * @param openstackTenantId Openstack tenant id
     * @param consistencyGroupId Consistency group id
     * @param isV1Call openstack cinder V1 call
     * @param header HTTP header
     * @brief Get Consistency Group Info
     * @return Response
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{consistencyGroup_id}")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public Response getCosistencyGroup(@PathParam("tenant_id") String openstackTenantId,
            @PathParam("consistencyGroup_id") String consistencyGroupId, @HeaderParam("X-Cinder-V1-Call") String isV1Call,
            @Context HttpHeaders header) {
        Project project = getCinderHelper().getProject(openstackTenantId, getUserFromContext());
        if (project == null) {
            String message = "Bad Request: Project with the OpenStack Tenant Id : " + openstackTenantId + " does not exist";
            _log.error(message);
            return CinderApiUtils.createErrorResponse(400, message);
        }
        final BlockConsistencyGroup blockConsistencyGroup = findConsistencyGroup(consistencyGroupId, openstackTenantId);
        if (blockConsistencyGroup == null) {
            return CinderApiUtils.createErrorResponse(404, "Invalid Request: No Such Consistency Group Found");
        }else if (!consistencyGroupId.equals(CinderApiUtils.splitString(blockConsistencyGroup.getId().toString(), ":", 3))) {
            _log.error("Bad Request : There is no consistency group with id {} , please retry with correct consistency group id",
                    consistencyGroupId);
            return CinderApiUtils.createErrorResponse(400,
                    "Bad Request : There is no consistency group exist, please retry with correct consistency group id");
        } else {
            ConsistencyGroupDetail response = getConsistencyGroupDetail(blockConsistencyGroup);
            return CinderApiUtils.getCinderResponse(response, header, true,CinderConstants.STATUS_OK);
        }

    }

    /**
     * This function handles Get request for all consistency group list
     * 
     * @param openstackTenantId Openstack tenant id
     * @param header HTTP header
     * @brief get detail consistency group info
     * @return Response
     */
    @GET
    @Path("/detail")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public Response getDetailedConsistencyGroupList(
            @PathParam("tenant_id") String openstackTenantId,
            @Context HttpHeaders header) {
        ConsistencyGroupsResponse cgsResponse = new ConsistencyGroupsResponse();
        URIQueryResultList uris = getCinderHelper().getConsistencyGroupsUris(openstackTenantId, getUserFromContext());
        if (uris != null) {
            while (uris.iterator().hasNext()) {
                URI blockCGUri = uris.iterator().next();
                BlockConsistencyGroup blockCG = _dbClient.queryObject(
                        BlockConsistencyGroup.class, blockCGUri);
                if (blockCG != null && !blockCG.getInactive()) {
                    cgsResponse.addConsistencyGroup(getConsistencyGroupDetail(blockCG));
                }
            }
        }
        return CinderApiUtils.getCinderResponse(cgsResponse, header, false,CinderConstants.STATUS_OK);
    }

    /**
     * Create Consistency group
     * 
     * @param openstackTenantId openstack tenant id
     * @param param pojo class to bind request
     * @param isV1Call cinder V1 api
     * @param header HTTP header
     * @brief Create Consistency group
     * @return Response
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public Response createConsistencyGroup(
            @PathParam("tenant_id") String openstackTenantId,
            ConsistencyGroupCreateRequest param, @HeaderParam("X-Cinder-V1-Call") String isV1Call, @Context HttpHeaders header) {

        _log.info("Creating Consistency Group : " + param.consistencygroup.name);

        ConsistencyGroupCreateResponse cgResponse = new ConsistencyGroupCreateResponse();
        final Project project = getCinderHelper().getProject(openstackTenantId,
                getUserFromContext());
        final String volumeTypes = param.consistencygroup.volume_types;
        VirtualPool vPool = getCinderHelper().getVpool(volumeTypes);
        if (null != project && vPool != null ) {
        	if(!vPool.getMultivolumeConsistency()){
                _log.error("Bad Request : Multi volume consistency is not enabled in the volume type {}", volumeTypes);
                return CinderApiUtils.createErrorResponse(400, "Bad Request : Multi volume consistency is not enabled");
        	}
            // Validate name
            ArgValidator.checkFieldNotEmpty(param.consistencygroup.name, "name");
            
            checkForDuplicateName(param.consistencygroup.name, BlockConsistencyGroup.class);

            // Validate name not greater than 64 characters
            ArgValidator.checkFieldLengthMaximum(param.consistencygroup.name, CG_MAX_LIMIT,
                    "name");

            // Create Consistency Group in db
            final BlockConsistencyGroup consistencyGroup = new BlockConsistencyGroup();
            consistencyGroup.setId(URIUtil
                    .createId(BlockConsistencyGroup.class));
            consistencyGroup.setLabel(param.consistencygroup.name);
            consistencyGroup.setProject(new NamedURI(project.getId(),
                    project.getLabel()));
            consistencyGroup.setTenant(project.getTenantOrg());
            consistencyGroup.setCreationTime(Calendar.getInstance());
            ScopedLabelSet tagSet = new ScopedLabelSet();
            consistencyGroup.setTag(tagSet);
            tagSet.add(new ScopedLabel("volume_types",
                    volumeTypes));
            tagSet.add(new ScopedLabel("status", "available"));
            tagSet.add(new ScopedLabel("availability_zone",
                    (param.consistencygroup.availability_zone != null) ? param.consistencygroup.availability_zone : "nova"));
            tagSet.add(new ScopedLabel("description", (param.consistencygroup.description != null) ? param.consistencygroup.description
                    : "No Description"));
            tagSet.add(new ScopedLabel(project.getTenantOrg().getURI().toString(), CinderApiUtils.splitString(consistencyGroup.getId()
                    .toString(), ":", 3)));
            _dbClient.createObject(consistencyGroup);

            cgResponse.id = CinderApiUtils.splitString(consistencyGroup.getId().toString(), ":", 3);
            cgResponse.name = consistencyGroup.getLabel();
            return CinderApiUtils.getCinderResponse(cgResponse, header, true,CinderConstants.STATUS_OK);
        } else {
            return CinderApiUtils.createErrorResponse(400, "Bad Request : can't create consistency group due to invalid argument");
        }

    }

    /**
     * Delete consistency group
     * 
     * @param openstackTenantId openstack tenant id
     * @param consistencyGroupId consistency group id
     * @param param pojo class to bind request
     * @param isV1Call cinder V1 api
     * @param header HTTP header
     * @brief delete Consistency group
     * @return Response
     */
    @POST
    @Path("/{consistencyGroup_id}/delete")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public Response deleteConsistencyGroup(@PathParam("tenant_id") String openstackTenantId,
            @PathParam("consistencyGroup_id") String consistencyGroupId, ConsistencyGroupDeleteRequest param,
            @HeaderParam("X-Cinder-V1-Call") String isV1Call, @Context HttpHeaders header) {
        boolean isForced = param.consistencygroup.force;
        final BlockConsistencyGroup consistencyGroup = findConsistencyGroup(consistencyGroupId, openstackTenantId);

        if (consistencyGroup == null) {
            _log.error("Not Found : No Such Consistency Group Found {}", consistencyGroupId); 
            return CinderApiUtils.createErrorResponse(404, "Not Found : No Such Consistency Group Found");
        } else if (!consistencyGroupId.equals(CinderApiUtils.splitString(consistencyGroup.getId().toString(), ":", 3))) {
            _log.error("Bad Request : There is no consistency group with id {} , please retry with correct consistency group id",
                    consistencyGroupId);
            return CinderApiUtils.createErrorResponse(400,
                    "Bad Request : There is no consistency group exist, please retry with correct consistency group id");
        }

        String task = UUID.randomUUID().toString();
        TaskResourceRep taskRep = null;

        if (getCinderHelper().verifyConsistencyGroupHasSnapshot(consistencyGroup)) {
            _log.error("Bad Request : Consistency Group {} has Snapshot", consistencyGroupId);
            return CinderApiUtils.createErrorResponse(400, "Bad Request : Consistency Group has Snapshot ");
        }

        if (isForced) {
            final URIQueryResultList cgVolumesResults = new URIQueryResultList();
            _dbClient.queryByConstraint(getVolumesByConsistencyGroup(consistencyGroup.getId()),
                    cgVolumesResults);
            while (cgVolumesResults.iterator().hasNext()) {
                Volume volume = _dbClient.queryObject(Volume.class, cgVolumesResults.iterator().next());
                if (!volume.getInactive()) {
                    BlockServiceApi api = BlockService.getBlockServiceImpl(volume, _dbClient);
                    URI systemUri = volume.getStorageController();
                    List<URI> volumeURIs = new ArrayList<URI>();
                    volumeURIs.add(volume.getId());
                    api.deleteVolumes(systemUri, volumeURIs, "FULL", null);

                    if (volume.getExtensions() == null) {
                        volume.setExtensions(new StringMap());
                    }
                    volume.getExtensions().put("status", CinderConstants.ComponentStatus.DELETING.getStatus().toLowerCase());
                    volume.setInactive(true);
                    _dbClient.updateObject(volume);
                }
            }
        }

        try {
            ArgValidator.checkReference(BlockConsistencyGroup.class, consistencyGroup.getId(),
                    checkForDelete(consistencyGroup));
        } catch (APIException e) {
            _log.error("Bad Request : Consistency Group Contains active references of type : {}", e.getMessage());
            return CinderApiUtils.createErrorResponse(400, "Bad Request : Consistency Group Contains active references");
        }
        // srdf/rp cgs can be deleted from vipr only if there are no more volumes associated.
        // If the consistency group is inactive or has yet to be created on
        // a storage system, then the deletion is not controller specific.

        // RP + VPlex CGs cannot be be deleted without VPlex controller intervention.
        if (!consistencyGroup.getTypes().contains(Types.VPLEX.toString()) ||
                canDeleteConsistencyGroup(consistencyGroup)) {
            final URIQueryResultList cgVolumesResults = new URIQueryResultList();
            _dbClient.queryByConstraint(getVolumesByConsistencyGroup(consistencyGroup.getId()),
                    cgVolumesResults);
            while (cgVolumesResults.iterator().hasNext()) {
                Volume volume = _dbClient.queryObject(Volume.class, cgVolumesResults.iterator().next());
                if (!volume.getInactive()) {
                    return CinderApiUtils.createErrorResponse(400, "Bad Request : Try to delete consistency group with --force");
                }
            }
            consistencyGroup.setStorageController(null);
            consistencyGroup.setInactive(true);
            _dbClient.updateObject(consistencyGroup);
            taskRep = finishDeactivateTask(consistencyGroup, task);
            if (taskRep.getState().equals("ready") || taskRep.getState().equals("pending")) {
                return Response.status(202).build();
            }

        }

        final StorageSystem storageSystem = consistencyGroup.created() ? _permissionsHelper
                .getObjectById(consistencyGroup.getStorageController(), StorageSystem.class) : null;

        // If the consistency group has been created, and the system
        // is a VPlex, then we need to do VPlex related things to destroy
        // the consistency groups on the system. If the consistency group
        // has not been created on the system or the system is not a VPlex
        // revert to the default.
        BlockServiceApi blockServiceApi = BlockService.getBlockServiceImpl("group");
        if (storageSystem != null) {
            String systemType = storageSystem.getSystemType();
            if (DiscoveredDataObject.Type.vplex.name().equals(systemType)) {
                blockServiceApi = BlockService.getBlockServiceImpl(systemType);
            }
            _log.info(String.format("BlockConsistencyGroup %s is associated to StorageSystem %s. Going to delete it on that array.",
                    consistencyGroup.getLabel(), storageSystem.getNativeGuid()));
            // Otherwise, invoke operation to delete CG from the array.
            taskRep = blockServiceApi.deleteConsistencyGroup(storageSystem, consistencyGroup, task);
            if (taskRep.getState().equals("ready") || taskRep.getState().equals("pending")) {
                return Response.status(202).build();
            }
        }
        if (taskRep == null) {
            _log.info(String.format("BlockConsistencyGroup %s was not associated with any storage. Deleting it from ViPR only.",
                    consistencyGroup.getLabel()));
            TaskResourceRep resp = finishDeactivateTask(consistencyGroup, task);
            if (resp.getState().equals("ready") || resp.getState().equals("pending")) {
                return Response.status(202).build();
            }
        }
        return CinderApiUtils.createErrorResponse(400, "Bad Request");

    }
    

}
