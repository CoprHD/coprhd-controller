/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */

package com.emc.storageos.api.service.impl.resource.cinder;

import static com.emc.storageos.api.mapper.TaskMapper.toTask;
import static com.emc.storageos.db.client.constraint.ContainmentConstraint.Factory.getBlockSnapshotByConsistencyGroup;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.DbObjectMapper;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.placement.PlacementManager;
import com.emc.storageos.api.service.impl.placement.StorageScheduler;
import com.emc.storageos.api.service.impl.placement.VpoolUse;
import com.emc.storageos.api.service.impl.resource.BlockService;
import com.emc.storageos.api.service.impl.resource.BlockServiceApi;
import com.emc.storageos.api.service.impl.resource.TaskResourceService;
import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyManager;
import com.emc.storageos.api.service.impl.resource.utils.BlockServiceUtils;
import com.emc.storageos.api.service.impl.resource.utils.CapacityUtils;
import com.emc.storageos.api.service.impl.resource.utils.CinderApiUtils;
import com.emc.storageos.api.service.impl.response.ProjOwnedResRepFilter;
import com.emc.storageos.api.service.impl.response.ResRepFilter;
import com.emc.storageos.api.service.impl.response.SearchedResRepList;
import com.emc.storageos.cinder.CinderConstants.ComponentStatus;
import com.emc.storageos.cinder.model.Attachment;
import com.emc.storageos.cinder.model.CinderVolume;
import com.emc.storageos.cinder.model.UsageStats;
import com.emc.storageos.cinder.model.VolumeCreateRequestGen;
import com.emc.storageos.cinder.model.VolumeDetail;
import com.emc.storageos.cinder.model.VolumeDetails;
import com.emc.storageos.cinder.model.VolumeUpdateRequestGen;
import com.emc.storageos.cinder.model.VolumesRestResp;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshot.TechnologyType;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.QuotaOfCinder;
import com.emc.storageos.db.client.model.ScopedLabel;
import com.emc.storageos.db.client.model.ScopedLabelSet;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.util.TaskUtils;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.model.block.VolumeFullCopyCreateParam;
import com.emc.storageos.model.search.SearchResultResourceRep;
import com.emc.storageos.model.vpool.ProtectionType;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

@DefaultPermissions(readRoles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN },
        readAcls = { ACL.OWN, ACL.ALL },
        writeRoles = { Role.TENANT_ADMIN },
        writeAcls = { ACL.OWN, ACL.ALL })
@SuppressWarnings({ "unchecked", "rawtypes" })
public class VolumeService extends TaskResourceService {

    private static final Logger _log = LoggerFactory.getLogger(VolumeService.class);
    private static final String EVENT_SERVICE_TYPE = "block";
    private static final String DELETE_TASK_ID = "delete_taskid";
    private static final long halfGB = 512 * 1024 * 1024;
    private static final long GB = 1024 * 1024 * 1024;
    private static final long ZERO_BYTES = 0;

    private static final String VOLUME_FROM_SNAPSHOT = "Volume created from snapshot";
    private static final String VOLUME_FROM_CLONE = "Volume created from clone";
    private static final int MAX_VMAX_COPY_SESSIONS = 8;
    private static final int CLONE_COUNT = 1;
    private static final int SNAP_COUNT = 1;
    private static final String PROJECT_TENANTID_NULL = "Both Project and Tenant Id are null";
    private static final String TRUE = "true";

    private PlacementManager _placementManager;
    private CinderHelpers helper;// = new CinderHelpers(_dbClient , _permissionsHelper);

    public void setPlacementManager(PlacementManager placementManager) {
        _placementManager = placementManager;
    }

    @Override
    public Class<Volume> getResourceClass() {
        return Volume.class;
    }

    protected CinderHelpers getCinderHelper() {
        return CinderHelpers.getInstance(_dbClient, _permissionsHelper);
    }

    private QuotaHelper getQuotaHelper() {
        return QuotaHelper.getInstance(_dbClient, _permissionsHelper);
    }
    
    /**
     * Get the summary list of all volumes for the given tenant
     * 
     * 
     * @prereq none
     * 
     * @param tenant_id the URN of the tenant
     * 
     * @brief List volumes
     * @return Volume list
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public Response getVolumeList(@PathParam("tenant_id") String openstackTenantId, @HeaderParam("X-Cinder-V1-Call") String isV1Call,
            @Context HttpHeaders header) {
        VolumesRestResp volumes = new VolumesRestResp();

        URIQueryResultList uris = getVolumeUris(openstackTenantId);
        if (uris != null) {
            while (uris.iterator().hasNext()) {
            	URI volumeUri = uris.iterator().next();
                Volume volume = _dbClient.queryObject(Volume.class, volumeUri);
                if (volume != null && !volume.getInactive()) {
                    CinderVolume cinder_volume = new CinderVolume();
                    cinder_volume.id = getCinderHelper().trimId(volume.getId().toString());
                    cinder_volume.setLink(DbObjectMapper.toLink(volume));
                    cinder_volume.name = volume.getLabel();
                    volumes.getVolumes().add(cinder_volume);
                }
            }
        }
        return CinderApiUtils.getCinderResponse(volumes, header, false);
    }

    /**
     * Get the detailed list of all volumes for the given tenant
     * 
     * 
     * @prereq none
     * 
     * @param tenant_id the URN of the tenant
     * 
     * @brief List volumes in detail
     * @return Volume detailed list
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/detail")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public Response getDetailedVolumeList(@PathParam("tenant_id") String openstackTenantId,
            @HeaderParam("X-Cinder-V1-Call") String isV1Call, @Context HttpHeaders header) {
        _log.debug("START get detailed volume list");
        URIQueryResultList uris = getVolumeUris(openstackTenantId);
        // convert to detailed format
        VolumeDetails volumeDetails = new VolumeDetails();
        if (uris != null) {
            for (URI volumeUri : uris) {
                Volume vol = _dbClient.queryObject(Volume.class, volumeUri);
                if (vol != null && !vol.getInactive()) {
                    VolumeDetail volumeDetail = getVolumeDetail(vol, isV1Call, openstackTenantId);
                    volumeDetails.getVolumes().add(volumeDetail);
                }
            }
        }
        return CinderApiUtils.getCinderResponse(volumeDetails, header, false);
    }

    /**
     * Get the details of a specific volume
     * 
     * 
     * @prereq none
     * 
     * @param tenant_id the URN of the tenant
     * @param volume_id the URN of the volume
     * 
     * @brief Show volume
     * @return Volume details
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{volume_id}")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public Response getVolume(@PathParam("tenant_id") String openstackTenantId,
            @PathParam("volume_id") String volumeId, @HeaderParam("X-Cinder-V1-Call") String isV1Call, @Context HttpHeaders header) {

        VolumeDetail response = new VolumeDetail();
        Volume vol = findVolume(volumeId, openstackTenantId);

        if (vol != null) {
            response = getVolumeDetail(vol, isV1Call, openstackTenantId);
        }
        return CinderApiUtils.getCinderResponse(response, header, true);
    }

    /**
     * The fundamental abstraction in the Block Store is a
     * volume. A volume is a unit of block storage capacity that has been
     * allocated by a consumer to a project. This API allows the user to
     * create one or more volumes. The volumes are created in the same
     * storage pool.
     * 
     * NOTE: This is an asynchronous operation.
     * 
     * 
     * @prereq none
     * 
     * @param param POST data containing the volume creation information.
     * 
     * @brief Create volume
     * @return Details of the newly created volume
     * @throws InternalException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public Response createVolume(@PathParam("tenant_id") String openstackTenantId,
            @HeaderParam("X-Cinder-V1-Call") String isV1Call, VolumeCreateRequestGen param, @Context HttpHeaders header)
            throws InternalException {
        // Step 1: Parameter validation
        Project project = getCinderHelper().getProject(openstackTenantId, getUserFromContext());
        String snapshotId = param.volume.snapshot_id;
        String sourceVolId = param.volume.source_volid;
        String imageId = param.volume.imageRef;
        String consistencygroup_id = param.volume.consistencygroup_id;
        String volume_type = param.volume.volume_type;
        boolean hasConsistencyGroup = false;

        if (project == null) {
            if (openstackTenantId != null) {
                throw APIException.badRequests.projectWithTagNonexistent(openstackTenantId);
            } else {
                throw APIException.badRequests.parameterIsNullOrEmpty(PROJECT_TENANTID_NULL);
            }
        }
        URI tenantUri = project.getTenantOrg().getURI();
        TenantOrg tenant = _dbClient.queryObject(TenantOrg.class, tenantUri);

        if (tenant == null)
            throw APIException.notFound.unableToFindUserScopeOfSystem();

        _log.debug("Create volume: project = {}, tenant = {}", project.getLabel(), tenant.getLabel());

        long requestedSize = param.volume.size * GB;
        // convert volume type from name to vpool
        VirtualPool vpool = getVpool(param.volume.volume_type);
        
        Volume sourceVolume = null;
        
        if (vpool == null){
        	if(sourceVolId != null){
        		sourceVolume = findVolume(sourceVolId, openstackTenantId);
        		if(sourceVolume == null){
        			throw APIException.badRequests.parameterIsNotValid(param.volume.source_volid);
        		}
        		vpool = _dbClient.queryObject(VirtualPool.class, sourceVolume.getVirtualPool());        		
        	}
        	else{
        		throw APIException.badRequests.parameterIsNotValid(param.volume.volume_type);
        	}
        }
        
        
        if (!validateVolumeCreate(openstackTenantId, null, requestedSize)) {
            _log.info("The volume can not be created because of insufficient project quota.");
            throw APIException.badRequests.insufficientQuotaForProject(project.getLabel(), "volume");
        }
        else if (!validateVolumeCreate(openstackTenantId, vpool, requestedSize)) {
            _log.info("The volume can not be created because of insufficient quota for virtual pool.");
            throw APIException.badRequests.insufficientQuotaForVirtualPool(vpool.getLabel(), "virtual pool");
        }

        
        _log.debug("Create volume: vpool = {}", vpool.getLabel());
        VirtualArray varray = getCinderHelper().getVarray(param.volume.availability_zone, getUserFromContext());
        if ((snapshotId == null) && (sourceVolId == null) && (varray == null)) {
        	//snapshotId and sourceVolId is optional can be null
        	//when snapshotId and sourceVolId values are absent varry values has to be provided
        	//otherwise availability_zone exception will be thrown
            throw APIException.badRequests.parameterIsNotValid(param.volume.availability_zone);
        }

        // Validating consistency group
        URI blockConsistencyGroupId = null;
        BlockConsistencyGroup blockConsistencyGroup = null;
        if (consistencygroup_id != null) {
            _log.info("Verifying for consistency group : " + consistencygroup_id);
            blockConsistencyGroup = (BlockConsistencyGroup) getCinderHelper().queryByTag(URI.create(consistencygroup_id), getUserFromContext(), BlockConsistencyGroup.class);
            blockConsistencyGroupId = blockConsistencyGroup.getId();
            if (blockConsistencyGroup.getTag() != null) {
                for (ScopedLabel tag : blockConsistencyGroup.getTag()) {
                    if (tag.getScope().equals("volume_types")) {
                        if (tag.getLabel().equals(volume_type)) {
                            hasConsistencyGroup = true;
                        } else {
                            return CinderApiUtils.createErrorResponse(404,
                                    "Invalid volume: No consistency group exist for volume : "
                                            + param.volume.display_name);
                        }
                    }
                }
            }
        }

        BlockSnapshot snapshot = null;
        URI snapUri = null;

        if (snapshotId != null) {
            snapshot = (BlockSnapshot) getCinderHelper().queryByTag(URI.create(snapshotId), getUserFromContext(),BlockSnapshot.class);
            if (snapshot == null) {
                throw APIException.badRequests.parameterIsNotValid(snapshotId);
            } else {
                snapUri = snapshot.getId();
                URI varrayUri = snapshot.getVirtualArray();
                if (varray == null) {
                    varray = _dbClient.queryObject(VirtualArray.class, varrayUri);
                }
            }
        }

        if (varray != null)
            _log.info("Create volume: varray = {}", varray.getLabel());
        String name = null;
        String description = null;

        _log.info("is isV1Call {}", isV1Call);
        _log.info("name = {},  description  = {}", name, description);

        if (isV1Call != null) {
            name = param.volume.display_name;
            description = param.volume.display_description;
        }
        else {
            name = param.volume.name;
            description = param.volume.description;
        }

        _log.info("param.volume.name = {}, param.volume.display_name = {}", param.volume.name,param.volume.display_name);
        _log.info("param.volume.description = {}, param.volume.display_description = {}", param.volume.description, param.volume.display_description);

        if (name == null || (name.length() <= 2))
            throw APIException.badRequests.parameterIsNotValid(name);
        URI projectUri = project.getId();
        checkForDuplicateName(name, Volume.class, projectUri, "project", _dbClient);

        // Step 2: Check if the user has rights for volume create
        verifyUserIsAuthorizedForRequest(project, vpool, varray);

        // Step 3: Check capacity Quotas
        _log.debug(" volume name = {}, size = {} GB", name, param.volume.size);

        int volumeCount = 1;
        VolumeCreate volumeCreate = new VolumeCreate(name,
                Long.toString(requestedSize), volumeCount, vpool.getId(),
                varray.getId(), project.getId());

        BlockServiceApi api = getBlockServiceImpl(vpool, _dbClient);
        CapacityUtils.validateQuotasForProvisioning(_dbClient, vpool, project,
                tenant, requestedSize, "volume");

        // Step 4: Call out placementManager to get the recommendation for placement.
        VirtualPoolCapabilityValuesWrapper capabilities = new VirtualPoolCapabilityValuesWrapper();
        capabilities.put(VirtualPoolCapabilityValuesWrapper.RESOURCE_COUNT, volumeCount);
        capabilities.put(VirtualPoolCapabilityValuesWrapper.SIZE, requestedSize);

        // Create a unique task id if one is not passed in the request.
        String task = UUID.randomUUID().toString();

        TaskList tasklist = null;
        BlockFullCopyManager blkFullCpManager = new BlockFullCopyManager(_dbClient,
                _permissionsHelper, _auditMgr, _coordinator, _placementManager, sc, uriInfo,
                _request, null);
        if (hasConsistencyGroup && blockConsistencyGroupId != null) {
            try {
                checkForConsistencyGroup(vpool, blockConsistencyGroup, project, api, varray, capabilities, blkFullCpManager);
                volumeCreate.setConsistencyGroup(blockConsistencyGroupId);
            } catch (APIException exp) {
                CinderApiUtils.createErrorResponse(400, "Bad Request : can't create volume for the consistency group : "
                        + blockConsistencyGroupId);
            }
        }
        if (sourceVolId != null)
        {
            _log.debug("Creating New Volume from Volume : Source volume ID ={}", sourceVolId);
            if (sourceVolume != null) {
                tasklist = volumeClone(name, project, sourceVolId, varray, volumeCount, sourceVolume, blkFullCpManager);
            } else {
                _log.debug("Creating Clone Volume failed : Null Source volume ");
                throw APIException.badRequests.parameterIsNotValid(sourceVolId);
            }

        } else if (snapshotId != null)
        {
            _log.debug("Creating New Volume from Snapshot ID ={}", snapshotId);
            tasklist = volumeFromSnapshot(name, project, snapshotId, varray, param, volumeCount, blkFullCpManager, snapUri, snapshot);

        } else if ((snapshotId == null) && (sourceVolId == null))
        {
            _log.debug("Creating New Volume where snapshotId and sourceVolId are null");
            tasklist = newVolume(volumeCreate, project, api, capabilities, varray, task, vpool, param, volumeCount, requestedSize, name);
        }

        if (imageId != null) {
            _log.debug("Creating New Volume from imageid ={}", imageId);
            // will be implemented
            tasklist = volumeFromImage(name, project, varray, param, volumeCount, blkFullCpManager, imageId);
        }

        if (!(tasklist.getTaskList().isEmpty())) {
            for (TaskResourceRep rep : tasklist.getTaskList()) {
                URI volumeUri = rep.getResource().getId();
                Volume vol = _dbClient.queryObject(Volume.class, volumeUri);

                if (vol != null) {
                    StringMap extensions = vol.getExtensions();
                    if (extensions == null)
                        extensions = new StringMap();

                    extensions.put("display_description", (description == null) ? "" : description);
                    vol.setExtensions(extensions);

                    ScopedLabelSet tagSet = new ScopedLabelSet();
                    vol.setTag(tagSet);

                    String[] splits = volumeUri.toString().split(":");
                    String tagName = splits[3];

                    if (tagName == null || tagName.isEmpty() || tagName.length() < 2) {
                        throw APIException.badRequests.parameterTooShortOrEmpty("Tag", 2);
                    }

                    URI tenantOwner = vol.getTenant().getURI();
                    ScopedLabel tagLabel = new ScopedLabel(tenantOwner.toString(), tagName);
                    tagSet.add(tagLabel);

                    _dbClient.updateAndReindexObject(vol);
                    return CinderApiUtils.getCinderResponse(getVolumeDetail(vol, isV1Call, openstackTenantId), header, true);
                }
                else {
                    throw APIException.badRequests.parameterIsNullOrEmpty("Volume");
                }
            }
        }
        return CinderApiUtils.getCinderResponse(new VolumeDetail(), header, true);
    }

    /**
     * Update a specific volume
     * 
     * 
     * @prereq none
     * 
     * @param tenant_id the URN of the tenant
     * @param volume_id the URN of the volume
     * 
     * @brief Update volume
     * @return Volume details
     */
    @PUT
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{volume_id}")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public Response updateVolume(@PathParam("tenant_id") String openstackTenantId,
            @PathParam("volume_id") String volumeId, @HeaderParam("X-Cinder-V1-Call") String isV1Call,
            VolumeUpdateRequestGen param, @Context HttpHeaders header) {
        Volume vol = findVolume(volumeId, openstackTenantId);
        if (vol == null)
            throw APIException.badRequests.parameterIsNotValid(volumeId);
        _log.debug("Update volume {}: ", vol.getLabel());
        String label = null;
        String description = null;
        if (isV1Call != null) {
            label = param.volume.display_name;
            description = param.volume.display_description;
        }
        else {
            label = param.volume.name;
            description = param.volume.description;
        }

        _log.debug("new name = {}, description = {}", label, description);

        if (label != null && (label.length() > 2)) {
            if (!vol.getLabel().equals(label)) {
                URI projectUri = vol.getProject().getURI();
                checkForDuplicateName(label, Volume.class, projectUri, "project", _dbClient);
                _log.debug("Update volume : not a duplicate name");
                vol.setLabel(label);

            }
        }
        if (description != null && (description.length() > 2)) {
            StringMap extensions = vol.getExtensions();
            if (extensions == null)
                extensions = new StringMap();
            extensions.put("display_description", description);
            _log.debug("Update volume : stored description");
            vol.setExtensions(extensions);
        }
        _dbClient.updateObject(vol);
        return CinderApiUtils.getCinderResponse(getVolumeDetail(vol, isV1Call, openstackTenantId), header, true);
    }

    /**
     * Delete a specific volume
     * 
     * 
     * @prereq none
     * 
     * @param tenant_id the URN of the tenant
     * @param volume_id the URN of the volume
     * @return
     * 
     * @brief Delete volume
     * @return Task result
     */
    @DELETE
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{volume_id}")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public Response deleteVolume(@PathParam("tenant_id") String openstackTenantId,
            @PathParam("volume_id") String volumeId) {
        _log.info("Delete volume: id = {} tenant: id ={}", volumeId, openstackTenantId);
        Volume vol = findVolume(volumeId, openstackTenantId);
        if (vol == null) {
            return Response.status(404).build();
        }
        BlockServiceApi api = BlockService.getBlockServiceImpl(vol, _dbClient);
        if ((api.getSnapshots(vol) != null) && (!api.getSnapshots(vol).isEmpty())) {
            return CinderApiUtils.createErrorResponse(400, "Invalid volume: Volume still has one or more dependent snapshots");
        }
        verifyUserCanModifyVolume(vol);

        // Now delete it
        String task = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(
                Volume.class, vol.getId(), task,
                ResourceOperationTypeEnum.DELETE_BLOCK_VOLUME);
        URI systemUri = vol.getStorageController();
        List<URI> volumeURIs = new ArrayList<URI>();
        volumeURIs.add(vol.getId());
        api.deleteVolumes(systemUri, volumeURIs, "FULL", null);

        if (vol.getExtensions() == null) {
            vol.setExtensions(new StringMap());
        }

        vol.getExtensions().put("status", ComponentStatus.DELETING.getStatus().toLowerCase());
        vol.getExtensions().put(DELETE_TASK_ID, task);
        _dbClient.updateObject(vol);

        return Response.status(202).build();
    }

    // INTERNAL FUNCTIONS
    protected VolumeDetail getVolumeDetail(Volume vol, String isV1Call, String openstackTenantId) {
        VolumeDetail detail = new VolumeDetail();
        int sizeInGB = (int) ((vol.getCapacity() + halfGB) / GB);
        detail.size = sizeInGB;
        detail.id = getCinderHelper().trimId(vol.getId().toString());
        detail.host_name = getCinderHelper().trimId(vol.getStorageController().toString());
        detail.tenant_id = openstackTenantId;

        detail.attachments = new ArrayList<Attachment>();
        if (vol.getInactive()) {
            detail.status = "deleted";
        }
        else {
            if (vol.getExtensions() == null) {
                vol.setExtensions(new StringMap());
            }

            if (vol.getProvisionedCapacity() == ZERO_BYTES) {
                detail.status = ComponentStatus.CREATING.getStatus().toLowerCase();
            }
            else if (vol.getExtensions().containsKey("status")
                    && vol.getExtensions().get("status").equals(ComponentStatus.DELETING.getStatus().toLowerCase())) {
                Task taskObj = null;
                String task = vol.getExtensions().get(DELETE_TASK_ID).toString();
                taskObj = TaskUtils.findTaskForRequestId(_dbClient, vol.getId(), task);
                if (taskObj != null) {
                    if (taskObj.getStatus().equals("error")) {
                        _log.info(String.format(
                                "Error Deleting volume %s, but moving volume to original state so that it will be usable:  ", detail.name));
                        vol.getExtensions().put("status", ComponentStatus.AVAILABLE.getStatus().toLowerCase());
                        vol.getExtensions().remove(DELETE_TASK_ID);
                        detail.status = ComponentStatus.AVAILABLE.getStatus().toLowerCase();
                    }
                    else if (taskObj.getStatus().equals("pending")) {
                        detail.status = ComponentStatus.DELETING.getStatus().toLowerCase();
                    }
                    _dbClient.updateObject(vol);
                }
                else {
                    detail.status = ComponentStatus.AVAILABLE.getStatus().toLowerCase();
                }
            }
            else if (vol.getExtensions().containsKey("status")
                    && vol.getExtensions().get("status").equals(ComponentStatus.EXTENDING.getStatus().toLowerCase())) {
                _log.info("Extending Volume {}", vol.getId().toString());
                Task taskObj = null;
                String task = vol.getExtensions().get("task_id").toString();
                taskObj = TaskUtils.findTaskForRequestId(_dbClient, vol.getId(), task);
                _log.debug("THE TASKOBJ is {}, task_id {}", taskObj.toString(), task);
                _log.debug("THE TASKOBJ STATUS is {}", taskObj.getStatus().toString());
                if (taskObj != null) {
                    if (taskObj.getStatus().equals("ready")) {
                        detail.status = ComponentStatus.AVAILABLE.getStatus().toLowerCase();
                        _log.debug(" STATUS is {}", detail.status);
                        vol.getExtensions().remove("task_id");
                        vol.getExtensions().put("status", "");
                    }
                    else if (taskObj.getStatus().equals("error")) {
                        detail.status = ComponentStatus.AVAILABLE.getStatus().toLowerCase();
                        _log.info(String.format(
                                "Error in Extending volume %s, but moving volume to original state so that it will be usable:  ",
                                detail.name));
                        vol.getExtensions().remove("task_id");
                        vol.getExtensions().put("status", "");
                    }
                    else {
                        detail.status = ComponentStatus.EXTENDING.getStatus().toLowerCase();
                        _log.info("STATUS is {}", detail.status);
                    }
                } else {
                    detail.status = ComponentStatus.AVAILABLE.getStatus().toLowerCase();
                    _log.info(String.format(
                            "Error in Extending volume %s, but moving volume to original state so that it will be usable:  ", detail.name));
                    vol.getExtensions().remove("task_id");
                    vol.getExtensions().put("status", "");
                }
                _dbClient.updateObject(vol);
            }
            else if (vol.getExtensions().containsKey("status") && !vol.getExtensions().get("status").equals("")) {
                detail.status = vol.getExtensions().get("status").toString().toLowerCase();
            }
            else {
                detail.status = ComponentStatus.AVAILABLE.getStatus().toLowerCase();
            }
        }

        detail.created_at = date(vol.getCreationTime().getTimeInMillis());
        URI vpoolUri = vol.getVirtualPool();
        VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, vpoolUri);
        if (vpool != null) {
            detail.volume_type = vpool.getLabel();
        }
        URI varrayUri = vol.getVirtualArray();
        VirtualArray varray = _dbClient.queryObject(VirtualArray.class, varrayUri);
        if (varray != null) {
            detail.availability_zone = varray.getLabel();
        }

        if (vol.getExtensions().containsKey("bootable") && vol.getExtensions().get("bootable").equals(TRUE)) {
            detail.bootable = true;
            _log.debug("Volumes Bootable Flag is TRUE");
        }
        else {
            detail.bootable = false;
            _log.debug("Volumes Bootable Flag is False");
        }

        detail.setLink(DbObjectMapper.toLink(vol));
        detail.metadata = new HashMap<String, String>();
        String description = null;
        StringMap extensions = vol.getExtensions();
        if (extensions != null) {
            description = extensions.get("display_description");
        }

        if (isV1Call != null) {
            detail.display_name = vol.getLabel();
            detail.display_description = (description == null) ? "" : description;
            detail.description = null;
            detail.name = null;
        }
        else {
            detail.name = vol.getLabel();
            detail.description = (description == null) ? "" : description;
            detail.display_name = null;
            detail.display_description = null;
        }

        if (vol.getExtensions().containsKey("readonly") && vol.getExtensions().get("readonly").equals(TRUE)) {
            _log.debug("Volumes Readonly Flag is TRUE");
            detail.metadata.put("readonly", "true");

        }
        else {
            _log.debug("Volumes Readonly Flag is FALSE");
            detail.metadata.put("readonly", "false");
        }

        if (detail.status.equals("in-use")) {
            if (vol.getExtensions() != null && vol.getExtensions().containsKey("OPENSTACK_NOVA_INSTANCE_ID")) {
                detail.metadata.put("attached_mode", vol.getExtensions().get("OPENSTACK_ATTACH_MODE"));
                detail.attachments = getVolumeAttachments(vol);
            }
        }

        if (vol.getConsistencyGroup() != null) {
            detail.consistencygroup_id = CinderApiUtils.splitString(vol.getConsistencyGroup().toString(), ":", 3);
        }

        return detail;
    }

    private boolean validateVolumeCreate(String openstackTenantId, VirtualPool pool, long requestedSize) {
        QuotaOfCinder objQuota = null;
        boolean isValidVolume = false;

        if (pool == null)
            objQuota = getQuotaHelper().getProjectQuota(openstackTenantId, getUserFromContext());
        else
            objQuota = getQuotaHelper().getVPoolQuota(openstackTenantId, pool, getUserFromContext());

        if (objQuota == null) {
            _log.info("Unable to retrive the Quota information");
            return false;
        }

        Project proj = getCinderHelper().getProject(openstackTenantId, getUserFromContext());

        long totalVolumesUsed = 0;
        long totalSizeUsed = 0;
        UsageStats stats = null;

        if (pool != null)
            stats = getQuotaHelper().getStorageStats(pool.getId(), proj.getId());
        else
            stats = getQuotaHelper().getStorageStats(null, proj.getId());

        totalVolumesUsed = stats.volumes;
        totalSizeUsed = stats.spaceUsed;

        _log.info(String.format("VolumesLimit():%s ,TotalQuota:%s , TotalSizeUsed:%s, TotalVolumesUsed:%s, RequestedConsumption:%s",
                objQuota.getVolumesLimit(), objQuota.getTotalQuota(), totalSizeUsed, totalVolumesUsed,
                (totalSizeUsed + (long) (requestedSize / GB))));

        if ((objQuota.getVolumesLimit() != QuotaService.DEFAULT_VOLUME_TYPE_VOLUMES_QUOTA)
                && (objQuota.getVolumesLimit() <= totalVolumesUsed))
        {
            return isValidVolume;
        }
        else if ((objQuota.getTotalQuota() != QuotaService.DEFAULT_VOLUME_TYPE_TOTALGB_QUOTA)
                && (objQuota.getTotalQuota() <= (totalSizeUsed + (long) (requestedSize / GB))))
        {
            return isValidVolume;
        }
        else
        {
        	isValidVolume = true;
            return isValidVolume;
        }
    }

    protected ExportGroup findExportGroup(Volume vol) {
        // Get export group for the volume
        SearchedResRepList resRepList = new SearchedResRepList(ResourceTypeEnum.EXPORT_GROUP);
        _dbClient.queryByConstraint(ContainmentConstraint.Factory.getVolumeExportGroupConstraint(vol.getId()),
                resRepList);
        if (resRepList.iterator() != null) {
            for (SearchResultResourceRep res : resRepList) {
                ExportGroup group = _dbClient.queryObject(ExportGroup.class, res.getId());
                if ((group != null) && !(group.getInactive())) {
                    return group;
                }
            }
        }
        return null; // if not found
    }

    private List<Attachment> getVolumeAttachments(Volume vol) {
        List<Attachment> attachments = new ArrayList<Attachment>();
        Attachment attachment = new Attachment();
        attachment.id = getCinderHelper().trimId(vol.getId().toString());
        attachment.volume_id = attachment.id;
        attachment.server_id = vol.getExtensions().get("OPENSTACK_NOVA_INSTANCE_ID");
        attachment.id = getCinderHelper().trimId(vol.getId().toString());
        attachment.volume_id = getCinderHelper().trimId(vol.getId().toString());
        attachment.device = vol.getExtensions().get("OPENSTACK_NOVA_INSTANCE_MOUNTPOINT");
        attachments.add(attachment);
        return attachments;
    }

    private URIQueryResultList getVolumeUris(String openstackTenantId) {
        URIQueryResultList uris = new URIQueryResultList();
        Project project = getCinderHelper().getProject(openstackTenantId, getUserFromContext());
        if (project == null)   // return empty list
            return null;

        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getProjectVolumeConstraint(project.getId()),
                uris);

        return uris;
    }

    /* Get vpool from the given label */
    private VirtualPool getVpool(String vpoolName) {
        if ( (vpoolName == null) || (vpoolName.length()==0) )
            return null;

        URIQueryResultList uris = new URIQueryResultList();
        _dbClient.queryByConstraint(
                PrefixConstraint.Factory.getLabelPrefixConstraint(
                        VirtualPool.class, vpoolName),
                uris);
        for (URI vpoolUri : uris) {
            VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, vpoolUri);
            if (vpool != null && vpool.getType().equals(VirtualPool.Type.block.name()))
                return vpool;
        }
        return null;  // no matching vpool found
    }

    /**
     * Verify the user is authorized for a volume creation request.
     * 
     * @param project The reference to the Project.
     * @param vpool The reference to the Virtual Pool.
     * @param varray The reference to the Virtual Array.
     * 
     * @throws APIException when the user is not authorized.
     */
    private void verifyUserIsAuthorizedForRequest(Project project, VirtualPool vpool, VirtualArray varray) {
        StorageOSUser user = getUserFromContext();
        if (!(_permissionsHelper.userHasGivenRole(user, project.getTenantOrg().getURI(),
                Role.TENANT_ADMIN) || _permissionsHelper.userHasGivenACL(user,
                project.getId(), ACL.OWN, ACL.ALL))) {
            throw APIException.forbidden.insufficientPermissionsForUser(user.getName());
        }
        URI vipr_tenantId = URI.create(user.getTenantId());
        _permissionsHelper.checkTenantHasAccessToVirtualPool(vipr_tenantId, vpool);
        _permissionsHelper.checkTenantHasAccessToVirtualArray(vipr_tenantId, varray);
    }

    protected void verifyUserCanModifyVolume(Volume vol) {
        StorageOSUser user = getUserFromContext();
        URI projectId = vol.getProject().getURI();
        if (!(_permissionsHelper.userHasGivenRole(user, vol.getTenant().getURI(),
                Role.TENANT_ADMIN) || _permissionsHelper.userHasGivenACL(user,
                projectId, ACL.OWN, ACL.ALL))) {
            throw APIException.forbidden.insufficientPermissionsForUser(user.getName());
        }
    }

    protected Volume findVolume(String volume_id, String openstackTenantId) {
        Volume vol = (Volume)getCinderHelper().queryByTag(URI.create(volume_id), getUserFromContext(), Volume.class);
        Project project = getCinderHelper().getProject(openstackTenantId, getUserFromContext());
        if (project == null) {
            throw APIException.badRequests.projectWithTagNonexistent(openstackTenantId);
        }
        if (vol != null) {
            if ((project != null) &&
                    (vol.getProject().getURI().toString().equalsIgnoreCase(project.getId().toString()))) {
                // volume is part of the project
                return vol;
            }
        }
        return null;
    }

    static String date(Long timeInMillis) {
        return new java.text.SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z").format(new java.util.Date(timeInMillis));
    }

    @Override
    protected URI getTenantOwner(URI id) {
        Volume volume = (Volume) queryResource(id);
        return volume.getTenant().getURI();
    }

    /**
     * Volume is not a zone level resource
     */
    @Override
    protected boolean isZoneLevelResource() {
        return false;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.VOLUME;
    }

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    /**
     * Get object specific permissions filter
     * 
     */
    @Override
    protected ResRepFilter<? extends RelatedResourceRep> getPermissionFilter(StorageOSUser user,
            PermissionsHelper permissionsHelper)
    {
        return new ProjOwnedResRepFilter(user, permissionsHelper, Volume.class);
    }

    @Override
    protected DataObject queryResource(URI id) {
        return _dbClient.queryObject(Volume.class, id);
    }

    // will be implemented
    protected TaskList volumeFromImage(String name, Project project, VirtualArray varray,
            VolumeCreateRequestGen param, int volumeCount, BlockFullCopyManager blkFullCpManager, String imageId)
    {
        return null;
    }

    protected TaskList volumeFromSnapshot(String name, Project project, String snapshotId,
            VirtualArray varray, VolumeCreateRequestGen param, int volumeCount,
            BlockFullCopyManager blkFullCpManager, URI snapUri, BlockSnapshot sourceSnapshot)
    {
        if (sourceSnapshot != null) {
            // Don't operate on VPLEX backend or RP Journal volumes.
            _log.debug("Volume from Snapshot is not supported on VPLEX backend or RP Journal volumes. snapUri = {}", snapUri);
            BlockServiceUtils.validateNotAnInternalBlockObject(sourceSnapshot, false);

            // verify if full copy is supported on the snapshot
            verifyFullCopySupportedOnSnapshot(sourceSnapshot);

            if (sourceSnapshot.getProtectionSet() != null) {
                throw APIException.badRequests.protectedVolumesNotSupported();
            }

            String volname = null;
            if (param.volume.name != null)
                volname = param.volume.name;
            else
                volname = param.volume.display_name;

            auditOp(OperationTypeEnum.CREATE_BLOCK_VOLUME, true, AuditLogManager.AUDITOP_BEGIN,
                    volname, volumeCount, varray.getId().toString(), project.getId().toString(),
                    snapshotId, VOLUME_FROM_SNAPSHOT);

            // Setting createInactive to true for openstack as it is not needed to
            // wait for synchronization to complete and detach.
            _log.debug("Block Service API call for : Create Volume from Snapshot ");
            Boolean createInactive = true; // from Blocksnapshot service check
            VolumeFullCopyCreateParam fullCopyParam = new VolumeFullCopyCreateParam(ProtectionType.full_copy.toString(), name,
                    SNAP_COUNT, createInactive);
            return blkFullCpManager.createFullCopy(snapUri, fullCopyParam);

        } else {
            return null;
        }
    }

    protected TaskList volumeClone(String volName, Project project, String sourceVolId,
            VirtualArray varray, int volumeCount,
            Volume sourceVolume, BlockFullCopyManager blkFullCpManager)
    {

        Volume vol = (Volume) getCinderHelper().queryByTag(URI.create(sourceVolId), getUserFromContext(), Volume.class);
        URI volumeUri = vol.getId();
        validateSourceVolumeHasExported(sourceVolume);

        Boolean createInactive = true;
        VolumeFullCopyCreateParam fullCopyParam = new VolumeFullCopyCreateParam(ProtectionType.full_copy.toString(), volName,
                CLONE_COUNT, createInactive);

        validateRequestedFullCopyCount(fullCopyParam, sourceVolume);
        if (sourceVolume.getProtectionSet() != null)
            throw APIException.badRequests.protectedVolumesNotSupported();
        auditOp(OperationTypeEnum.ACTIVATE_VOLUME_FULL_COPY, true, AuditLogManager.AUDITOP_BEGIN,
                volName, volumeCount, varray.getId().toString(), project.getId().toString(),
                sourceVolId, VOLUME_FROM_CLONE);
        _log.debug("Block Service API call for : Create Clone Volume ");

        return blkFullCpManager.createFullCopy(volumeUri, fullCopyParam);

    }

    protected TaskList newVolume(VolumeCreate volumeCreate, Project project, BlockServiceApi api,
            VirtualPoolCapabilityValuesWrapper capabilities,
            VirtualArray varray, String task, VirtualPool vpool,
            VolumeCreateRequestGen param, int volumeCount, long requestedSize, String name)
    {
        List recommendations = _placementManager.getRecommendationsForVolumeCreateRequest(
                varray, project, vpool, capabilities);
        Map<VpoolUse, List<Recommendation>> recommendationsMap = new HashMap<VpoolUse, List<Recommendation>>();
        recommendationsMap.put(VpoolUse.ROOT, recommendations);

        if (recommendations.isEmpty()) {
            throw APIException.badRequests.noMatchingStoragePoolsForVpoolAndVarray(vpool.getLabel(), varray.getLabel());
        }

        String volname = null;
        if (param.volume.name != null)
            volname = param.volume.name;
        else
            volname = param.volume.display_name;

        auditOp(OperationTypeEnum.CREATE_BLOCK_VOLUME, true, AuditLogManager.AUDITOP_BEGIN,
                volname, volumeCount, varray.getId().toString(), project.getId().toString());

        _log.debug("Block Service API call for : Create New Volume ");
        TaskList passedTaskist = createTaskList(requestedSize, project, varray, vpool, name, task, volumeCount);
        return api.createVolumes(volumeCreate, project, varray, vpool, recommendationsMap, passedTaskist, task,
                capabilities);
    }


    /**
     * Verify that the snapshot is not on vmax and hds, and not in a consistency group
     * and the array has full copy enabled
     * 
     * @param snapshot
     */
    private void verifyFullCopySupportedOnSnapshot(BlockSnapshot snapshot) {
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class,
                snapshot.getStorageController());

        _log.debug("For Vmax/HDS  does not support full copy on snapshots");

        if ((storageSystem != null)) {
            _log.debug("Storage System Type ={}", storageSystem.getSystemType());
            // vmax/HDS does not support full copy on snapshots
            if ((DiscoveredDataObject.Type.vmax.name().equals(storageSystem
                    .getSystemType())) || (DiscoveredDataObject.Type.hds.name().equals(storageSystem
                    .getSystemType()))) {
                throw APIException.badRequests.fullCopyNotSupportedFromSnapshot(storageSystem
                        .getSystemType(), snapshot.getId());
            }

        }

        // snapshot in a consistencyGroup is not supported for full copy operation
        URI cgUri = snapshot.getConsistencyGroup();
        if (!NullColumnValueGetter.isNullURI(cgUri)) {
            _log.debug("Snapshot in a consistencyGroup is not supported for full copy operation ");
            throw APIException.badRequests.fullCopyNotSupportedForConsistencyGroup();
        }
        Volume volume = _dbClient.queryObject(Volume.class, snapshot.getParent());

    }

    private TaskList createTaskList(long size, Project project, VirtualArray varray, VirtualPool vpool, String label, String task,
            Integer volumeCount) {
        TaskList taskList = new TaskList();

        // For each volume requested, pre-create a volume object/task object
        // long lsize = SizeUtil.translateSize(size);
        for (int i = 0; i < volumeCount; i++) {
            Volume volume = StorageScheduler.prepareEmptyVolume(_dbClient, size, project, varray, vpool, label, i, volumeCount);
            Operation op = _dbClient.createTaskOpStatus(Volume.class, volume.getId(),
                    task, ResourceOperationTypeEnum.CREATE_BLOCK_VOLUME);
            volume.getOpStatus().put(task, op);
            TaskResourceRep volumeTask = toTask(volume, task, op);
            taskList.getTaskList().add(volumeTask);
            _log.info(String.format("Volume and Task Pre-creation Objects [Init]--  Source Volume: %s, Task: %s, Op: %s",
                    volume.getId(), volumeTask.getId(), task));
        }

        return taskList;
    }

    private void validateSourceVolumeHasExported(Volume requestedVolume) {
        URI id = requestedVolume.getId();
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, requestedVolume.getStorageController());
        if (storageSystem != null && DiscoveredDataObject.Type.hds.name().equals(storageSystem
                .getSystemType())) {
            if (!requestedVolume.isVolumeExported(_dbClient)) {
                throw APIException.badRequests.sourceNotExported(id);
            }
        }
    }

    public static void checkOperationSupportedOnIngestedVolume(Volume volume,
            ResourceOperationTypeEnum operation, DbClient dbClient) {
        if (volume.isIngestedVolume(dbClient)) {
            switch (operation) {
                case CREATE_VOLUME_FULL_COPY:
                case CREATE_VOLUME_SNAPSHOT:
                case EXPAND_BLOCK_VOLUME:
                case CREATE_VOLUME_MIRROR:
                case CHANGE_BLOCK_VOLUME_VARRAY:
                case UPDATE_CONSISTENCY_GROUP:
                    _log.error("Operation {} is not permitted on ingested volumes.", operation.getName());
                    throw APIException.badRequests.operationNotPermittedOnIngestedVolume(
                            operation.getName(), volume.getLabel());
                default:
                    return;
            }
        }
    }

    public static void validateNotAnInternalBlockObject(BlockObject blockObject,
            boolean force) {
        if (blockObject != null) {
            if (blockObject.checkInternalFlags(Flag.INTERNAL_OBJECT)
                    && !blockObject.checkInternalFlags(Flag.SUPPORTS_FORCE)) {
                throw APIException.badRequests.notSupportedForInternalVolumes();
            }
            else if (blockObject.checkInternalFlags(Flag.INTERNAL_OBJECT)
                    && blockObject.checkInternalFlags(Flag.SUPPORTS_FORCE)
                    && !force) {
                throw APIException.badRequests.notSupportedForInternalVolumes();
            }
        }
    }

    private void validateRequestedFullCopyCount(VolumeFullCopyCreateParam param, Volume source) {
        StorageSystem system = _dbClient.queryObject(StorageSystem.class, source.getStorageController());
        if (system != null && system.getSystemType().equalsIgnoreCase(StorageSystem.Type.vmax.toString())) {
            if (param.getCount() > MAX_VMAX_COPY_SESSIONS) {
                throw APIException.badRequests.maxFullCopySessionLimitExceeded(source.getId(), MAX_VMAX_COPY_SESSIONS);
            }
        }
    }

    /**
     * Method to support consistency group for a volume
     * 
     * @param vpool
     * @param consistencyGroup
     * @param project
     * @param blockServiceImpl
     * @param varray
     * @param capabilities
     * @param blkFullCpManager
     */
    public void checkForConsistencyGroup(VirtualPool vpool,
            BlockConsistencyGroup consistencyGroup, Project project,
            BlockServiceApi blockServiceImpl, VirtualArray varray, VirtualPoolCapabilityValuesWrapper capabilities,
            BlockFullCopyManager blkFullCpManager) {

        Integer volumeCount = 1;

        final Boolean isMultiVolumeConsistencyOn = vpool.getMultivolumeConsistency() == null ? Boolean.FALSE
                : vpool.getMultivolumeConsistency();

        _log.info("*********MultiVolumeConsistencyOn************** : " + isMultiVolumeConsistencyOn);

        /*
         * Validate Consistency Group:
         * 1. CG should be active in the database
         * 2. CG project and Volume project should match
         * 3. The storage system that the CG is bonded to is associated to the
         * request virtual array
         */

        ArrayList<String> requestedTypes = new ArrayList<String>();
        final URI actualId = project.getId();
        if (consistencyGroup != null) {
            // Check that the Volume project and the CG project are the same
            final URI expectedId = consistencyGroup.getProject().getURI();
            final boolean condition = actualId.equals(expectedId);
            if (!condition) {
                throw APIException.badRequests.invalidProjectConflict(expectedId);
            }
            // If the Consistency Group was provided, MultiVolumeConsistency
            // attribute should be true
            if (!isMultiVolumeConsistencyOn) {
                throw APIException.badRequests.invalidParameterConsistencyGroupProvidedButVirtualPoolHasNoMultiVolumeConsistency(
                        consistencyGroup.getId(), URI.create(vpool.toString()));
            }
            // Find all volumes assigned to the group
            final List<Volume> activeCGVolumes = blockServiceImpl.getActiveCGVolumes(consistencyGroup);
            // Validate that the number of volumes in the group plus the number
            // to be added by this request does not exceed the maximum volumes
            // in a CG.
            int cgMaxVolCount = blockServiceImpl.getMaxVolumesForConsistencyGroup(consistencyGroup);
            if ((activeCGVolumes.size() + volumeCount.intValue()) > cgMaxVolCount) {
                throw APIException.badRequests.requestedVolumeCountExceedsLimitsForCG(
                        volumeCount.intValue(), cgMaxVolCount, consistencyGroup.getLabel());
            }

            // If the consistency group is not yet created, verify the name is OK.
            if (!consistencyGroup.created()) {
                blockServiceImpl.validateConsistencyGroupName(consistencyGroup);
            }

            // Consistency Group is already a Target, hence cannot be used to create source volume
            if (consistencyGroup.srdfTarget()) {
                throw APIException.badRequests.consistencyGroupBelongsToTarget(consistencyGroup.getId());
            }

            if (VirtualPool.vPoolSpecifiesSRDF(vpool)
                    && (consistencyGroup.getLabel().length() > 8 || !isAlphaNumeric(consistencyGroup.getLabel()))) {
                throw APIException.badRequests.groupNameCannotExceedEightCharactersoronlyAlphaNumericAllowed();
            }

            if (!VirtualPool.vPoolSpecifiesSRDF(vpool) && consistencyGroup.checkForType(Types.SRDF)) {
                throw APIException.badRequests.nonSRDFVolumeCannotbeAddedToSRDFCG();
            }

            // check if CG's storage system is associated to the requested virtual array
            validateCGValidWithVirtualArray(consistencyGroup, varray);

            if (VirtualPool.vPoolSpecifiesProtection(vpool)) {
                requestedTypes.add(Types.RP.name());
            }

            // Note that for ingested VPLEX CGs or CGs created in releases
            // prior to 2.2, there will be no corresponding native
            // consistency group. We don't necessarily want to fail
            // volume creations in these CGs, so we don't require the
            // LOCAL type.
            if (VirtualPool.vPoolSpecifiesHighAvailability(vpool)) {
                requestedTypes.add(Types.VPLEX.name());
            }

            if (VirtualPool.vPoolSpecifiesSRDF(vpool)) {
                requestedTypes.add(Types.SRDF.name());
            }

            if (!VirtualPool.vPoolSpecifiesProtection(vpool)
                    && !VirtualPool.vPoolSpecifiesHighAvailability(vpool)
                    && !VirtualPool.vPoolSpecifiesSRDF(vpool)
                    && vpool.getMultivolumeConsistency()) {
                requestedTypes.add(Types.LOCAL.name());
            }

            // Validate the CG type. We want to make sure the volume create request is appropriate
            // the CG's previously requested types.
            if (consistencyGroup.creationInitiated()) {
                if (!consistencyGroup.getRequestedTypes().containsAll(requestedTypes)) {
                    throw APIException.badRequests.consistencyGroupIsNotCompatibleWithRequest(
                            consistencyGroup.getId(), consistencyGroup.getTypes().toString(), requestedTypes.toString());
                }
            }

            // RP consistency group validation
            if (VirtualPool.vPoolSpecifiesProtection(vpool)) {
                // If an RP protected vpool is specified, ensure that the CG selected is empty or contains only RP volumes.
                if (activeCGVolumes != null && !activeCGVolumes.isEmpty()
                        && !consistencyGroup.getTypes().contains(BlockConsistencyGroup.Types.RP.toString())) {
                    throw APIException.badRequests.consistencyGroupMustBeEmptyOrContainRpVolumes(consistencyGroup.getId());
                }

                if (!activeCGVolumes.isEmpty()) {
                    // Find the first existing source volume for source/target varray comparison.
                    Volume existingSourceVolume = null;
                    for (Volume cgVolume : activeCGVolumes) {
                        if (cgVolume.getPersonality() != null &&
                                cgVolume.getPersonality().equals(Volume.PersonalityTypes.SOURCE.toString())) {
                            existingSourceVolume = cgVolume;
                            break;
                        }
                    }

                    if (existingSourceVolume != null) {
                        VirtualPool existingVpool = _dbClient.queryObject(
                                VirtualPool.class, existingSourceVolume.getVirtualPool());
                        VirtualPool requestedVpool = _dbClient.queryObject(
                                VirtualPool.class, URI.create(vpool.toString()));

                        // The source virtual arrays must much
                        if (existingVpool.getVirtualArrays().size() != requestedVpool.getVirtualArrays().size()
                                || !existingVpool.getVirtualArrays().containsAll(requestedVpool.getVirtualArrays())) {
                            // The source virtual arrays are not compatible with the CG
                            throw APIException.badRequests.vPoolSourceVarraysNotCompatibleForCG(consistencyGroup.getLabel());
                        }

                        // Validate that the request is not attempting to mix VPlex Metro volumes and
                        // MetroPoint volumes in the same CG.
                        if (VirtualPool.vPoolSpecifiesHighAvailability(existingVpool) &&
                                VirtualPool.vPoolSpecifiesHighAvailability(requestedVpool)) {
                            // If the requested and CG assigned virtual pools both specify VPlex, ensure
                            // that we are not trying to mix MetroPoint volumes with Metro volumes.
                            if ((!VirtualPool.vPoolSpecifiesMetroPoint(requestedVpool) &&
                                    VirtualPool.vPoolSpecifiesMetroPoint(existingVpool)) ||
                                    (VirtualPool.vPoolSpecifiesMetroPoint(requestedVpool) &&
                                    !VirtualPool.vPoolSpecifiesMetroPoint(existingVpool))) {
                                throw APIException.badRequests.cannotMixMetroPointAndNonMetroPointVolumes(consistencyGroup.getLabel());
                            }
                        }

                        // Check the target virtual arrays
                        StringMap existingProtectionVarraySettings = existingVpool.getProtectionVarraySettings();

                        if (existingProtectionVarraySettings == null) {
                            // The existing CG source volume's protection settings are null. This can only happen when a swap
                            // is performed on the CG. This would have been a former target volume so its virtual pool
                            // references a target virtual pool, which has no protection settings defined. We do not support
                            // adding a volume to an existing CG whose volumes have been swapped.
                            // NOTE: This will be supported in the future through Jira CTRL-10129
                            throw APIException.badRequests.cannotAddVolumesToSwappedCG(consistencyGroup.getLabel());
                        }

                        StringMap requestedProtectionVarraySettings = requestedVpool.getProtectionVarraySettings();

                        if (existingProtectionVarraySettings.size() != requestedProtectionVarraySettings.size()) {
                            // The target virtual arrays are not compatible with the CG
                            throw APIException.badRequests.vPoolTargetVarraysNotCompatibleForCG(consistencyGroup.getLabel());
                        }

                        for (String targetVarray : requestedProtectionVarraySettings.keySet()) {
                            if (!existingProtectionVarraySettings.containsKey(targetVarray)) {
                                // The target virtual arrays are not compatible with the CG
                                throw APIException.badRequests.vPoolTargetVarraysNotCompatibleForCG(consistencyGroup.getLabel());
                            }
                        }
                    }
                }
            }

            checkCGForSnapshots(consistencyGroup);

            // Creating new volumes in a consistency group is
            // not supported when the consistency group has
            // volumes with full copies to which they are still
            // attached or has volumes that are full copies that
            // are still attached to their source volumes.
            blkFullCpManager.verifyNewVolumesCanBeCreatedInConsistencyGroup(consistencyGroup,
                    activeCGVolumes);

            capabilities.put(VirtualPoolCapabilityValuesWrapper.BLOCK_CONSISTENCY_GROUP,
                    consistencyGroup.getId());
            if (consistencyGroup != null) {
                consistencyGroup.addRequestedTypes(requestedTypes);
                _dbClient.updateAndReindexObject(consistencyGroup);
            }
        } else if (VirtualPool.vPoolSpecifiesProtection(vpool)) {
            // The consistency group param is null and RP protection has been specified. When RP
            // protection is specified, a consistency group must be selected.
            throw APIException.badRequests.consistencyGroupMissingForRpProtection();
        }

    }

    /*
     * Validate if the physical array that the consistency group bonded to is associated
     * with the virtual array
     * 
     * @param consistencyGroup
     * 
     * @param varray virtual array
     */
    private void validateCGValidWithVirtualArray(BlockConsistencyGroup consistencyGroup,
            VirtualArray varray) {
        URI storageSystemUri = consistencyGroup.getStorageController();
        if (NullColumnValueGetter.isNullURI(storageSystemUri)) {
            return;
        }
        URIQueryResultList storagePortURIs = new URIQueryResultList();
        URIQueryResultList assignedStoragePortURIs = new URIQueryResultList();
        boolean isAssociated = false;
        String systemString = storageSystemUri.toString();
        // get all assigned storage ports
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getVirtualArrayStoragePortsConstraint(varray.getId().toString()),
                assignedStoragePortURIs);
        isAssociated = anyPortsMatchStorageDevice(assignedStoragePortURIs, systemString);

        if (!isAssociated) {
            // get all network storage ports in the virtual array
            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getImplicitVirtualArrayStoragePortsConstraint(varray.getId().toString()),
                    storagePortURIs);
            isAssociated = anyPortsMatchStorageDevice(storagePortURIs, systemString);
        }

        if (!isAssociated) {
            throw APIException.badRequests.invalidParameterConsistencyGroupStorageSystemMismatchVirtualArray(
                    consistencyGroup.getId(), varray.getId());
        }
    }

    /**
     * Check if any port in the list is from the storage system
     * 
     * @param ports
     * @param systemUri
     * @return
     */
    private boolean anyPortsMatchStorageDevice(URIQueryResultList ports, String systemUri) {
        boolean isMatched = false;
        for (URI spUri : ports) {
            StoragePort storagePort = _dbClient.queryObject(StoragePort.class, spUri);
            if (storagePort != null) {
                URI system = storagePort.getStorageDevice();
                if (system != null && system.toString().equals(systemUri)) {
                    isMatched = true;
                    break;
                }
            }
        }
        return isMatched;
    }

    // Validate consistency group name
    private boolean isAlphaNumeric(String consistencyGroupName) {
        String pattern = "^[a-zA-Z0-9]*$";
        if (consistencyGroupName.matches(pattern)) {
            return true;
        }
        return false;
    }

    /**
     * Checks existing CG for non-RP snapshots. If non-RP snapshots exist,
     * we cannot create/add a volume to the CG.
     * 
     * @param consistencyGroup the consistency group to validate.
     */
    private void checkCGForSnapshots(BlockConsistencyGroup consistencyGroup) {
        // If the Consistency Group has Snapshot(s), then Volume can not be created.
        final URIQueryResultList cgSnapshotsResults = new URIQueryResultList();
        _dbClient.queryByConstraint(getBlockSnapshotByConsistencyGroup(consistencyGroup.getId()),
                cgSnapshotsResults);
        Iterator<BlockSnapshot> blockSnapshotIterator = _dbClient.queryIterativeObjects(BlockSnapshot.class, cgSnapshotsResults);

        while (blockSnapshotIterator.hasNext()) {
            BlockSnapshot next = blockSnapshotIterator.next();
            // RP BlockSnapshots do not prevent other volumes from being created/added to the
            // consistency group.
            if (!next.getTechnologyType().equalsIgnoreCase(TechnologyType.RP.name())) {
                throw APIException.badRequests.cannotCreateVolumeAsConsistencyGroupHasSnapshots(consistencyGroup.getLabel(),
                        consistencyGroup.getId());
            }
        }

    }

    /**
     * Returns the bean responsible for servicing the request
     * 
     * @param vpool Virtual Pool
     * @return block service implementation object
     */
    private static BlockServiceApi getBlockServiceImpl(VirtualPool vpool, DbClient dbClient) {
        // Mutually exclusive logic that selects an implementation of the block service
        if (VirtualPool.vPoolSpecifiesProtection(vpool)) {
            return BlockService.getBlockServiceImpl(DiscoveredDataObject.Type.rp.name());
        } else if (VirtualPool.vPoolSpecifiesHighAvailability(vpool)) {
            return BlockService.getBlockServiceImpl(DiscoveredDataObject.Type.vplex.name());
        } else if (VirtualPool.vPoolSpecifiesSRDF(vpool)) {
            return BlockService.getBlockServiceImpl(DiscoveredDataObject.Type.srdf.name());
        } else if (VirtualPool.vPoolSpecifiesMirrors(vpool, dbClient)) {
            return BlockService.getBlockServiceImpl("mirror");
        } else if (vpool.getMultivolumeConsistency() != null && vpool.getMultivolumeConsistency()) {
            return BlockService.getBlockServiceImpl("group");
        }

        return BlockService.getBlockServiceImpl("default");
    }
}
