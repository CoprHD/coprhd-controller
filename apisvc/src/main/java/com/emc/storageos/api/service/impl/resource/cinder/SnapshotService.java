/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */

package com.emc.storageos.api.service.impl.resource.cinder;

import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
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

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.placement.PlacementManager;
import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.resource.BlockService;
import com.emc.storageos.api.service.impl.resource.BlockServiceApi;
import com.emc.storageos.api.service.impl.resource.TaskResourceService;
import com.emc.storageos.api.service.impl.resource.TenantsService;
import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyManager;
import com.emc.storageos.api.service.impl.resource.utils.BlockServiceUtils;
import com.emc.storageos.api.service.impl.resource.utils.CinderApiUtils;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.api.service.impl.response.ProjOwnedResRepFilter;
import com.emc.storageos.api.service.impl.response.ResRepFilter;
import com.emc.storageos.cinder.CinderConstants.ComponentStatus;
import com.emc.storageos.cinder.model.CinderSnapshot;
import com.emc.storageos.cinder.model.CinderSnapshotListRestResp;
import com.emc.storageos.cinder.model.CinderSnapshotMetadata;
import com.emc.storageos.cinder.model.SnapshotActionRequest;
import com.emc.storageos.cinder.model.SnapshotCreateRequestGen;
import com.emc.storageos.cinder.model.SnapshotCreateResponse;
import com.emc.storageos.cinder.model.SnapshotUpdateRequestGen;
import com.emc.storageos.cinder.model.UsageStats;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshot.TechnologyType;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.QuotaOfCinder;
import com.emc.storageos.db.client.model.ScopedLabel;
import com.emc.storageos.db.client.model.ScopedLabelSet;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.util.TaskUtils;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;

@Path("/v2/{tenant_id}/snapshots")
@DefaultPermissions(readRoles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, readAcls = {
        ACL.OWN, ACL.ALL }, writeRoles = { Role.TENANT_ADMIN }, writeAcls = {
        ACL.OWN, ACL.ALL })
@SuppressWarnings({ "unchecked", "rawtypes" })
public class SnapshotService extends TaskResourceService {

    private static final Logger _log = LoggerFactory
            .getLogger(SnapshotService.class);
    private static final String EVENT_SERVICE_TYPE = "block";

    private static final long HALF_GB = 512 * 1024 * 1024;
    private static final long GB = 1024 * 1024 * 1024;
    private static final String ZERO_PERCENT_COMPLETION = "0%";
    private static final String HUNDRED_PERCENT_COMPLETION = "100%";

    protected PlacementManager _placementManager;
    protected TenantsService _tenantsService;
    private CinderHelpers helper = null;

    public void setPlacementManager(PlacementManager placementManager) {
        _placementManager = placementManager;
    }

    public void setTenantsService(TenantsService tenantsService) {
        _tenantsService = tenantsService;
    }

    @Override
    public Class<Volume> getResourceClass() {
        return Volume.class;
    }

    private CinderHelpers getCinderHelper() {
        return CinderHelpers.getInstance(_dbClient, _permissionsHelper);
    }

    private QuotaHelper getQuotaHelper() {
        return QuotaHelper.getInstance(_dbClient, _permissionsHelper);
    }
    
    /**
     * The snapshot of a volume in Block Store is a point in time copy of the
     * volume. This API allows the user to create snapshot of a volume
     * NOTE: This is an asynchronous operation.
     * 
     * 
     * @prereq none
     * 
     * @param param
     *            POST data containing the snapshot creation information.
     * 
     * @brief Create snapshot
     * @return Details of the newly created snapshot
     * @throws InternalException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public SnapshotCreateResponse createSnapshot(
            @PathParam("tenant_id") String openstack_tenant_id,
            SnapshotCreateRequestGen param,
            @HeaderParam("X-Cinder-V1-Call") String isV1Call)
            throws InternalException {

        // Step 1: Parameter validation
        String snapshotName = null;
        String snapshotDescription = null;

        if (isV1Call != null) {
            snapshotName = param.snapshot.display_name;
            snapshotDescription = param.snapshot.display_description;
        } else {
            snapshotName = param.snapshot.name;
            snapshotDescription = param.snapshot.description;
        }

        if (snapshotName == null || (snapshotName.length() <= 2))
        {
            throw APIException.badRequests
            .parameterIsNotValid(param.snapshot.name);

        }
            
        URI volumeUri = null;
        Volume volume = null;

        volumeUri = URI.create(param.snapshot.volume_id);
        volume = queryVolumeResource(volumeUri, openstack_tenant_id);

        if (volume == null) {
            throw APIException.badRequests
                    .parameterIsNotValid(param.snapshot.volume_id);
        }

        VirtualPool pool = _dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());
        if (pool == null) {
            _log.info("Virtual Pool corresponding to the volume does not exist.");
            throw APIException.badRequests.parameterIsNotValid(volume
                    .getVirtualPool().toString());
        }

        if (!validateSnapshotCreate(openstack_tenant_id, pool,
                volume.getProvisionedCapacity())) {
            _log.info("The volume can not be created because of insufficient quota for virtual pool.");
            throw APIException.badRequests.insufficientQuotaForVirtualPool(
                    pool.getLabel(), "virtual pool");
        }
        if (!validateSnapshotCreate(openstack_tenant_id, null,
                volume.getProvisionedCapacity())) {
            _log.info("The volume can not be created because of insufficient quota for Project.");
            throw APIException.badRequests.insufficientQuotaForProject(
                    pool.getLabel(), "project");
        }

        BlockFullCopyManager fcManager = new BlockFullCopyManager(_dbClient,
                _permissionsHelper, _auditMgr, _coordinator, _placementManager, sc, uriInfo,
                _request, _tenantsService);

        VolumeIngestionUtil.checkOperationSupportedOnIngestedVolume(volume,
                ResourceOperationTypeEnum.CREATE_VOLUME_SNAPSHOT, _dbClient);

        // Don't operate on VPLEX backend volumes or RP journal volumes.
        BlockServiceUtils.validateNotAnInternalBlockObject(volume, false);

        validateSourceVolumeHasExported(volume);

        String snapshotType = TechnologyType.NATIVE.toString();
        Boolean createInactive = Boolean.FALSE;

        BlockServiceApi api = getBlockServiceImpl(pool, _dbClient);

        List<Volume> volumesToSnap = new ArrayList<Volume>();
        volumesToSnap.addAll(api.getVolumesToSnap(volume, snapshotType));

        api.validateCreateSnapshot(volume, volumesToSnap, snapshotType,
                snapshotName, fcManager);

        String taskId = UUID.randomUUID().toString();
        List<URI> snapshotURIs = new ArrayList<URI>();

        List<BlockSnapshot> snapshots = api.prepareSnapshots(volumesToSnap,
                snapshotType, snapshotName, snapshotURIs, taskId);

        TaskList response = new TaskList();
        for (BlockSnapshot snapshot : snapshots) {
            response.getTaskList().add(toTask(snapshot, taskId));
        }

        // Update the task status for the volumes task.
        _dbClient.createTaskOpStatus(Volume.class, volume.getId(), taskId,
                ResourceOperationTypeEnum.CREATE_VOLUME_SNAPSHOT);

        Boolean readOnly = false;
        // Invoke the block service API implementation to create the snapshot
        api.createSnapshot(volume, snapshotURIs, snapshotType, createInactive,
                readOnly, taskId);

        SnapshotCreateResponse snapCreateResp = new SnapshotCreateResponse();

        for (TaskResourceRep rep : response.getTaskList()) {
            URI snapshotUri = rep.getResource().getId();
            BlockSnapshot snap = _dbClient.queryObject(BlockSnapshot.class,
                    snapshotUri);

            if (snap != null) {
                if (snapshotDescription != null
                        && (snapshotDescription.length() > 2)) {
                    StringMap extensions = snap.getExtensions();
                    if (extensions == null)
                        extensions = new StringMap();
                    extensions.put("display_description", snapshotDescription);
                    extensions.put("taskid", rep.getId().toString());
                    _log.debug("Create snapshot : stored description");
                    snap.setExtensions(extensions);
                }

                ScopedLabelSet tagSet = new ScopedLabelSet();
                snap.setTag(tagSet);

                String[] splits = snapshotUri.toString().split(":");
                String tagName = splits[3];
                
                //this check will verify whether  retrieved data is not corrupted
                if (tagName == null || tagName.isEmpty()
                        || tagName.length() < 2) {
                    throw APIException.badRequests
                            .parameterTooShortOrEmpty("Tag", 2);
                }
                Volume parentVol = _permissionsHelper.getObjectById(
                        snap.getParent(), Volume.class);
                URI tenantOwner = parentVol.getTenant().getURI();
                ScopedLabel tagLabel = new ScopedLabel(
                        tenantOwner.toString(), tagName);
                tagSet.add(tagLabel);
                _dbClient.updateObject(snap);

                snapCreateResp.snapshot = snapCreateResp.new Snapshot();
                int sizeInGB = (int) ((snap.getProvisionedCapacity() + HALF_GB) / GB);
                snapCreateResp.snapshot.size = sizeInGB;
                snapCreateResp.snapshot.id = getCinderHelper().trimId(
                        snap.getId().toString());
                snapCreateResp.snapshot.name = snap.getLabel();
                snapCreateResp.snapshot.volume_id = snap.getParent().getURI()
                        .toString();
                snapCreateResp.snapshot.created_at = date(snap
                        .getCreationTime().getTimeInMillis());
                snapCreateResp.snapshot.status = ComponentStatus.CREATING.getStatus().toLowerCase(); // "creating";
                return snapCreateResp;
            }
        }

        return snapCreateResp;
    }

    /**
     * Update a specific snapshot
     * 
     * 
     * @prereq none
     * 
     * @param tenant_id
     *            the URN of the tenant
     * @param snapshot_id
     *            the URN of the snapshot
     * 
     * @brief Update snapshot
     * @return snapshot details
     */
    @PUT
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{snapshot_id}")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public Response updateSnapshot(
            @PathParam("tenant_id") String openstack_tenant_id,
            @PathParam("snapshot_id") String snapshot_id,
            SnapshotUpdateRequestGen param,
            @HeaderParam("X-Cinder-V1-Call") String isV1Call,
            @Context HttpHeaders header) {
        BlockSnapshot snap = findSnapshot(snapshot_id, openstack_tenant_id);
        if (snap == null) {
            throw APIException.badRequests.parameterIsNotValid(snapshot_id);
        }            

        _log.debug("Update snapshot {}: ", snap.getLabel());

        String label = null;
        String description = null;
        if (isV1Call != null) {
            label = param.snapshot.display_name;
            description = param.snapshot.display_description;
        } else {
            label = param.snapshot.name;
            description = param.snapshot.description;
        }
        _log.debug("new name = {}, description = {}", label, description);

        if (label != null && (label.length() > 2)) {
            URI volumeUri = snap.getParent().getURI();
            String snapshotType = TechnologyType.NATIVE.toString();

            Volume volume = queryVolumeResource(
                    URI.create(getCinderHelper().trimId(volumeUri.toString())),
                    openstack_tenant_id);

            URIQueryResultList uris = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getVolumeSnapshotConstraint(volume.getId()), uris);

            for (URI snuri : uris) {
                BlockSnapshot snapIter = _dbClient.queryObject(BlockSnapshot.class, snuri);
                if (snapIter != null && !snapIter.getInactive()
                        && snapIter.getLabel().equals(label)) {
                    
                    _log.info("Update snapshot: duplicate name");
                    throw APIException.badRequests.duplicateLabel(label);
                }
            }

            //ToDo if the backend system is vplex, rp  
            //we cannot use the default blockservice implemenation
            //we need to use other APIs(for vplex adn RP), that need to be implemented

            VirtualPool pool = _dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());
            if (pool == null) {
                _log.info("Virtual Pool corresponding to the volume does not exist.");
                throw APIException.badRequests.parameterIsNotValid(volume
                        .getVirtualPool().toString());
            }

            BlockServiceApi api = getBlockServiceImpl(pool, _dbClient);

            List<Volume> volumesToSnap = new ArrayList<Volume>();
            volumesToSnap.addAll(api.getVolumesToSnap(volume, snapshotType));

            BlockFullCopyManager fcManager = new BlockFullCopyManager(_dbClient,
                    _permissionsHelper, _auditMgr, _coordinator, _placementManager, sc, uriInfo,
                    _request, _tenantsService);

            api.validateCreateSnapshot(volume, volumesToSnap, snapshotType,
                    label, fcManager);

            _log.debug("Update snapshot: not a duplicate name");
            snap.setLabel(label);

        }
        if (description != null && (description.length() > 2)) {
            StringMap extensions = snap.getExtensions();
            if (extensions == null)
                extensions = new StringMap();
            extensions.put("display_description", description);
            _log.debug("Update volume : stored description");
            snap.setExtensions(extensions);
        }
        _dbClient.updateObject(snap);
        return CinderApiUtils.getCinderResponse(
                getSnapshotDetail(snap, isV1Call, openstack_tenant_id), header, true);
    }

    /**
     * Action could be snapshot status update operation
     * NOTE: This is an synchronous operation.
     * 
     * @prereq none
     * @param param POST data containing the snapshot action information.
     * @brief update snapshot status
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    @Path("/{snapshot_id}/action")
    public Object actionOnSnapshot(@PathParam("tenant_id") String openstack_tenant_id,
            @PathParam("snapshot_id") String snapshot_id,
            SnapshotActionRequest actionRequest) throws InternalException, InterruptedException {
        BlockSnapshot snap = findSnapshot(snapshot_id, openstack_tenant_id);
        if (snap.getExtensions() == null) {
            snap.setExtensions(new StringMap());
        }

        snap.getExtensions().put("status", actionRequest.updateStatus.status);
        _dbClient.updateObject(snap);
        return Response.status(202).build();

    }

    /**
     * Update a specific snapshot's metadata
     * 
     * 
     * @prereq none
     * 
     * @param tenant_id
     *            the URN of the tenant
     * @param snapshot_id
     *            the URN of the snapshot
     * 
     * @brief Update snapshot metadata
     * @return snapshot metadata details
     */
    @PUT
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{snapshot_id}/metadata")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public CinderSnapshotMetadata updateSnapshotMetadata(
            @PathParam("tenant_id") String openstack_tenant_id,
            @PathParam("snapshot_id") String snapshot_id,
            CinderSnapshotMetadata param) {
        BlockSnapshot snap = findSnapshot(snapshot_id, openstack_tenant_id);

        if (snap == null) {
            throw APIException.badRequests.parameterIsNotValid(snapshot_id);
        }
            

        _log.debug("Update metadata of snapshot {}: ", snap.getLabel());

        Map<String, String> metaMap = param.metadata;

        StringMap extensions = snap.getExtensions();
        if (extensions == null) {
            extensions = new StringMap();
        }
            

        for (String mapEntry : metaMap.keySet()) {
            String value = metaMap.get(mapEntry);
            extensions.put("METADATA_" + mapEntry, value);
        }

        _log.debug("Update snapshot metadata: stored metadata");
        snap.setExtensions(extensions);
        _dbClient.updateObject(snap);

        return getSnapshotMetadataDetail(snap);
    }

    /**
     * Get the summary list of all snapshots for the given tenant
     * 
     * 
     * @prereq none
     * 
     * @param tenant_id
     *            the URN of the tenant
     * 
     * @brief List snapshots
     * @return Snapshot list
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/detail")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public Response getSnapshotList(
            @PathParam("tenant_id") String openstack_tenant_id,
            @HeaderParam("X-Cinder-V1-Call") String isV1Call,
            @Context HttpHeaders header) {

        CinderSnapshotListRestResp snapshots = new CinderSnapshotListRestResp();

        URIQueryResultList uris = getSnapshotUris(openstack_tenant_id);

        if (uris != null) {
            while (uris.iterator().hasNext()) {
                URI snapshotUri = uris.iterator().next();
                BlockSnapshot snap = _dbClient.queryObject(BlockSnapshot.class,
                        snapshotUri);
                if (snap != null && !snap.getInactive()) {
                    CinderSnapshot cinder_snapshot = getSnapshotDetail(snap,
                            isV1Call, openstack_tenant_id);
                    snapshots.getSnapshots().add(cinder_snapshot);
                }
            }
        }
        return CinderApiUtils.getCinderResponse(snapshots, header, false);
    }

    /**
     * Delete a specific snapshot
     * 
     * 
     * @prereq none
     * 
     * @param tenant_id
     *            the URN of the tenant
     * @param snapshot_id
     *            the URN of the snapshot
     * 
     * @brief Delete Snapshot
     * @return Task result
     */
    @DELETE
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{snapshot_id}")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public void deleteSnapshot(
            @PathParam("tenant_id") String openstack_tenant_id,
            @PathParam("snapshot_id") String snapshot_id) {

        _log.info("Delete Snapshot: id = {}", snapshot_id);

        BlockSnapshot snap = findSnapshot(snapshot_id, openstack_tenant_id);
        if (snap == null) {
            throw APIException.badRequests.parameterIsNotValid(snapshot_id);
        }            

        URI snapshotURI = snap.getId();
        String task = UUID.randomUUID().toString();
        TaskList response = new TaskList();

        ArgValidator.checkReference(BlockSnapshot.class, snapshotURI, checkForDelete(snap));

        // Not an error if the snapshot we try to delete is already deleted
        if (snap.getInactive()) {
            Operation op = new Operation();
            op.ready("The snapshot has already been deleted");
            op.setResourceType(ResourceOperationTypeEnum.DELETE_VOLUME_SNAPSHOT);
            _dbClient.createTaskOpStatus(BlockSnapshot.class, snap.getId(), task, op);
            response.getTaskList().add(toTask(snap, task, op));
            return;
        }

        StorageSystem device = _dbClient.queryObject(StorageSystem.class, snap.getStorageController());
        List<BlockSnapshot> snapshots = new ArrayList<BlockSnapshot>();

        final URI cgId = snap.getConsistencyGroup();
        if (!NullColumnValueGetter.isNullURI(cgId)) {
            // Collect all the BlockSnapshots if part of a CG.
            URIQueryResultList results = new URIQueryResultList();
            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getBlockSnapshotsBySnapsetLabel(snap.getSnapsetLabel()),
                    results);
            while (results.iterator().hasNext()) {
                URI uri = results.iterator().next();
                _log.info("BlockSnapshot being deactivated: " + uri);
                BlockSnapshot snapshot = _dbClient.queryObject(BlockSnapshot.class, uri);
                if (snapshot != null) {
                    snapshots.add(snapshot);
                }
            }
        } else {
            // Snap is not part of a CG so only delete the snap
            snapshots.add(snap);
        }

        for (BlockSnapshot snapshot : snapshots) {
            Operation snapOp = _dbClient.createTaskOpStatus(
                    BlockSnapshot.class, snapshot.getId(), task,
                    ResourceOperationTypeEnum.DELETE_VOLUME_SNAPSHOT);
            response.getTaskList().add(toTask(snapshot, task, snapOp));
        }

        // Note that for snapshots of VPLEX volumes, the parent volume for the
        // snapshot is the source side backend volume, which will have the same
        // vpool as the VPLEX volume and therefore, the correct implementation
        // should be returned.
        Volume volume = _permissionsHelper.getObjectById(snap.getParent(), Volume.class);
        BlockServiceApi blockServiceApiImpl = BlockService.getBlockServiceImpl(volume, _dbClient);
        blockServiceApiImpl.deleteSnapshot(snap, task);

        StringMap extensions = snap.getExtensions();
        if (extensions == null) {
            extensions = new StringMap();
        }            

        for (TaskResourceRep rep : response.getTaskList()) {
            extensions.put("taskid", rep.getId().toString());
            break;
        }

        snap.setExtensions(extensions);
        _dbClient.updateObject(snap);

        auditOp(OperationTypeEnum.DELETE_VOLUME_SNAPSHOT, true,
                AuditLogManager.AUDITOP_BEGIN, snapshot_id, snap.getLabel(),
                snap.getParent().getName(), device.getId().toString());

        return;
    }

    /**
     * Get the details of a specific snapshot
     * 
     * 
     * @prereq none
     * 
     * @param tenant_id
     *            the URN of the tenant
     * @param snapshot_id
     *            the URN of the snapshot
     * 
     * @brief Show snapshot
     * @return snapshot details
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{snapshot_id}")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public Response getSnapShot(
            @PathParam("tenant_id") String openstack_tenant_id,
            @PathParam("snapshot_id") String snapshot_id,
            @HeaderParam("X-Cinder-V1-Call") String isV1Call,
            @Context HttpHeaders header) {
        CinderSnapshot response = new CinderSnapshot();
        _log.info("START get snapshot with id {}", snapshot_id);
        BlockSnapshot snapshot = findSnapshot(snapshot_id, openstack_tenant_id);
        
        if(snapshot==null) {
            throw APIException.badRequests.parameterIsNotValid(snapshot_id);
        }

        response = getSnapshotDetail(snapshot, isV1Call, openstack_tenant_id);            

        return CinderApiUtils.getCinderResponse(response, header, true);
    }

    /**
     * Get the meta-data of a specific snapshot
     * 
     * 
     * @prereq none
     * 
     * @param tenant_id
     *            the URN of the tenant
     * @param snapshot_id
     *            the URN of the snapshot
     * 
     * @brief Show snapshot meta-data
     * @return snapshot meta-data details
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{snapshot_id}/metadata")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public Response getSnapShotMetadata(
            @PathParam("tenant_id") String openstack_tenant_id,
            @PathParam("snapshot_id") String snapshot_id,
            @HeaderParam("X-Cinder-V1-Call") String isV1Call,
            @Context HttpHeaders header) {
        BlockSnapshot snapshot = findSnapshot(snapshot_id, openstack_tenant_id);
        if(snapshot==null) {
            throw APIException.badRequests.parameterIsNotValid(snapshot_id);
        }
        
        return CinderApiUtils.getCinderResponse(
               getSnapshotDetail(snapshot, isV1Call, openstack_tenant_id), header, true);
        
    }

    // INTERNAL FUNCTIONS
    protected CinderSnapshot getSnapshotDetail(BlockSnapshot snapshot,
            String isV1Call, String openstack_tenant_id) {
        CinderSnapshot detail = new CinderSnapshot();

        detail.id = getCinderHelper().trimId(snapshot.getId().toString());
        detail.volume_id = getCinderHelper().trimId(snapshot.getParent().getURI().toString());
        detail.created_at = date(snapshot.getCreationTime().getTimeInMillis());
        detail.project_id = openstack_tenant_id;
        detail.size = (int) ((snapshot.getProvisionedCapacity() + HALF_GB) / GB);

        StringMap extensions = snapshot.getExtensions();
        String description = null;
        Map<String, String> metaMap = new HashMap<String, String>();

        if (extensions != null) {
            description = extensions.get("display_description");

            _log.debug("Retreiving the tasks for snapshot id {}", snapshot.getId());
            List<Task> taskLst = TaskUtils.findResourceTasks(_dbClient, snapshot.getId());
            _log.debug("Retreived the tasks for snapshot id {}", snapshot.getId());
            String taskInProgressId = null;
            if (snapshot.getExtensions().containsKey("taskid"))
            {
                taskInProgressId = snapshot.getExtensions().get("taskid");
                //Task acttask = TaskUtils.findTaskForRequestId(_dbClient, snapshot.getId(), taskInProgressId);

                for (Task tsk : taskLst) {
                    if (tsk.getId().toString().equals(taskInProgressId)) {
                        if (tsk.getStatus().equals("ready"))
                        {
                            detail.status = ComponentStatus.AVAILABLE.getStatus().toLowerCase();
                            snapshot.getExtensions().put("status", ComponentStatus.AVAILABLE.getStatus().toLowerCase());
                            snapshot.getExtensions().remove("taskid");
                            _dbClient.updateObject(snapshot);
                        }
                        else if (tsk.getStatus().equals("pending")) {
                            if (tsk.getDescription().equals(ResourceOperationTypeEnum.CREATE_VOLUME_SNAPSHOT.getDescription()))
                            {
                                detail.status = ComponentStatus.CREATING.getStatus().toLowerCase();
                            } else if (tsk.getDescription().equals(ResourceOperationTypeEnum.DELETE_VOLUME_SNAPSHOT.getDescription()))
                            {
                                detail.status = ComponentStatus.DELETING.getStatus().toLowerCase();
                            }
                        }
                        else if (tsk.getStatus().equals("error"))
                        {
                            detail.status = ComponentStatus.ERROR.getStatus().toLowerCase();
                            snapshot.getExtensions().put("status", ComponentStatus.ERROR.getStatus().toLowerCase());
                            snapshot.getExtensions().remove("taskid");
                            _dbClient.updateObject(snapshot);
                        }
                        break;
                    }
                }
            }
            else if (snapshot.getExtensions().containsKey("status") &&
                    !snapshot.getExtensions().get("status").toString().toLowerCase().equals("")) {
                detail.status = snapshot.getExtensions().get("status").toString().toLowerCase();
            }
            else
            {
                detail.status = ComponentStatus.AVAILABLE.getStatus().toLowerCase(); // "available";
            }

            for (String mapEntry : extensions.keySet()) {
                if (mapEntry.startsWith("METADATA_"))
                {
                    String value = extensions.get(mapEntry);
                    metaMap.put(mapEntry.substring("METADATA_".length()), value);
                }
            }
        }
        if (isV1Call != null)
        {
            detail.display_name = snapshot.getLabel();
            detail.display_description = (description == null) ? ""
                    : description;
        } else
        {
            detail.name = snapshot.getLabel();
            detail.description = (description == null) ? "" : description;
        }

        detail.progress = ZERO_PERCENT_COMPLETION;// default
        if ((detail.status == ComponentStatus.CREATING.getStatus().toLowerCase())
                || (detail.status == ComponentStatus.DELETING.getStatus().toLowerCase()) ||
                (detail.status == ComponentStatus.ERROR.getStatus().toLowerCase())
                || (detail.status == ComponentStatus.ERROR_DELETING.getStatus().toLowerCase()))
        {
            detail.progress = ZERO_PERCENT_COMPLETION;
        }
        else if (detail.status == ComponentStatus.AVAILABLE.getStatus().toLowerCase())
        {
            detail.progress = HUNDRED_PERCENT_COMPLETION;
        }
        detail.metadata = metaMap;
        return detail;
    }

    // INTERNAL FUNCTIONS
    protected CinderSnapshotMetadata getSnapshotMetadataDetail(BlockSnapshot snapshot) {
        StringMap extensions = snapshot.getExtensions();
        if (extensions == null) {
            extensions = new StringMap();
        }            

        Map<String, String> metaMap = new HashMap<String, String>();
        for (String mapEntry : extensions.keySet()) {
            if (mapEntry.startsWith("METADATA_")) {
                String value = extensions.get(mapEntry);
                metaMap.put(mapEntry.substring("METADATA_".length()), value);
            }
        }

        CinderSnapshotMetadata resp = new CinderSnapshotMetadata();
        resp.metadata = metaMap;

        return resp;
    }

    private boolean validateSnapshotCreate(String openstack_tenant_id,
            VirtualPool pool, long requestedSize) {
        
        _log.info("requestedSize {}", requestedSize);
        QuotaOfCinder objQuota = null;

        if (pool == null) {
            objQuota = getQuotaHelper().getProjectQuota(openstack_tenant_id, getUserFromContext());
        } else {
            objQuota = getQuotaHelper().getVPoolQuota(openstack_tenant_id, pool, getUserFromContext());
        }

        Project proj = getCinderHelper().getProject(openstack_tenant_id, getUserFromContext());
        if (proj == null) {
            throw APIException.badRequests.projectWithTagNonexistent(openstack_tenant_id);
        }

        long totalSnapshotsUsed = 0;
        long totalSizeUsed = 0;
        
        UsageStats stats = null;
        if (pool != null) {
            stats = getQuotaHelper().getStorageStats(pool.getId(), proj.getId());
        } else {
            stats = getQuotaHelper().getStorageStats(null, proj.getId());
        }            

        totalSnapshotsUsed = stats.snapshots;
        totalSizeUsed = stats.spaceUsed;

        _log.info(String
                .format("objQuota.getVolumesLimit():%s ,objQuota.getSnapshotsLimit():%s,objQuota.getTotalQuota():%s,totalSizeUsed:%s,totalSnapshotsUsed:%s,willconsume:%s",
                        objQuota.getVolumesLimit(), objQuota.getSnapshotsLimit(), objQuota.getTotalQuota(),
                        totalSizeUsed, totalSnapshotsUsed, (totalSizeUsed + (long) (requestedSize / GB))));

        if ((objQuota.getSnapshotsLimit() != -1)
                && (objQuota.getSnapshotsLimit() <= totalSnapshotsUsed)) {
            return false;
        } else if ((objQuota.getTotalQuota() != -1)
                && (objQuota.getTotalQuota() <= (totalSizeUsed + (long) (requestedSize / GB)))) {
            return false;
        } else {
            return true;
        }
           
    }

    protected BlockSnapshot findSnapshot(String snapshot_id,
            String openstack_tenant_id) {
        BlockSnapshot snapshot = (BlockSnapshot) getCinderHelper().queryByTag(
                URI.create(snapshot_id), getUserFromContext(),BlockSnapshot.class);
        if (snapshot != null) {
            Project project = getCinderHelper().getProject(openstack_tenant_id, getUserFromContext());
            if ((project != null)
                    && (snapshot.getProject().getURI().toString()
                            .equalsIgnoreCase(project.getId().toString()))) {
                // snapshot is part of the project
                return snapshot;
            }
            else {
                throw APIException.badRequests.projectWithTagNonexistent(openstack_tenant_id);
            }
        }
        return null;
    }

    private URIQueryResultList getSnapshotUris(String openstack_tenant_id) {
        URIQueryResultList uris = new URIQueryResultList();
        Project project = getCinderHelper().getProject(openstack_tenant_id, getUserFromContext());
        if (project == null) // return empty list
            return null;

        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getProjectBlockSnapshotConstraint(project.getId()), uris);

        return uris;
    }

    protected BlockObject querySnapshotResource(URI id) {
        StorageOSUser user = getUserFromContext();
        URI vipr_tenantId = URI.create(user.getTenantId());
        URIQueryResultList uris = new URIQueryResultList();
        _dbClient.queryByConstraint(PrefixConstraint.Factory
                .getTagsPrefixConstraint(BlockSnapshot.class, id.toString(),
                        vipr_tenantId), uris);

        for (URI snapUri : uris) {
            BlockSnapshot snap = _dbClient.queryObject(BlockSnapshot.class,
                    snapUri);
            if (snap != null && isAuthorized(snapUri))
                return snap;
        }
        return null;
    }

    protected Volume queryVolumeResource(URI id, String openstack_tenant_id) {

        Volume vol = (Volume) getCinderHelper().queryByTag(id, getUserFromContext(), Volume.class);

        if (vol != null) {
            Project project = getCinderHelper().getProject(openstack_tenant_id, getUserFromContext());
            if ((project != null)
                    && (vol.getProject().getURI().toString().equalsIgnoreCase(project.getId().toString()))) {
                // volume is part of the project
                return vol;
            } else {
                throw APIException.badRequests.projectWithTagNonexistent(openstack_tenant_id);
            }
        }
        return null;
    }

    /**
     * Source volume should be exported to any host before performing hds
     * snap/clone/mirror creation
     * 
     * @param requestedVolume
     */
    private void validateSourceVolumeHasExported(Volume requestedVolume) {
        URI id = requestedVolume.getId();
        StorageSystem storageSystem = _dbClient.queryObject(
                StorageSystem.class, requestedVolume.getStorageController());
        if (storageSystem != null
                && DiscoveredDataObject.Type.hds.name().equals(
                        storageSystem.getSystemType())) {
            if (!requestedVolume.isVolumeExported(_dbClient)) {
                throw APIException.badRequests.sourceNotExported(id);
            }
        }

    }


    protected void verifyUserCanModifyVolume(Volume vol) {
        StorageOSUser user = getUserFromContext();
        URI projectId = vol.getProject().getURI();
        if (!(_permissionsHelper.userHasGivenRole(user, vol.getTenant()
                .getURI(), Role.TENANT_ADMIN) || _permissionsHelper
                .userHasGivenACL(user, projectId, ACL.OWN, ACL.ALL))) {
            throw APIException.forbidden.insufficientPermissionsForUser(user
                    .getName());
        }
    }

    static String date(Long timeInMillis) {
        return new java.text.SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z")
                .format(new java.util.Date(timeInMillis));
    }

    @Override
    protected URI getTenantOwner(URI id) {
        throw new UnsupportedOperationException();
    }

    /**
     * Snapshot is not a zone level resource
     */
    @Override
    protected boolean isZoneLevelResource() {
        return false;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.BLOCK_SNAPSHOT;
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
    protected ResRepFilter<? extends RelatedResourceRep> getPermissionFilter(
            StorageOSUser user, PermissionsHelper permissionsHelper) {
        return new ProjOwnedResRepFilter(user, permissionsHelper, Volume.class);
    }

    @Override
    protected DataObject queryResource(URI id) {
        return _dbClient.queryObject(BlockSnapshot.class, id);
    }

    /**
     * Returns the storagetype for block service Implementation
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
        } 

        return BlockService.getBlockServiceImpl("default");
    }

}
