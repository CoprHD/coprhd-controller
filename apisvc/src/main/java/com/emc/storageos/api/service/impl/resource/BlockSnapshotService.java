/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.BlockMapper.map;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;
import static com.emc.storageos.svcs.errorhandling.resources.ServiceCode.API_BAD_REQUEST;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.functions.MapBlockSnapshot;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.placement.PlacementManager;
import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyManager;
import com.emc.storageos.api.service.impl.resource.utils.ExportUtils;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.api.service.impl.response.BulkList.PermissionsEnforcingResourceFilter;
import com.emc.storageos.api.service.impl.response.BulkList.ResourceFilter;
import com.emc.storageos.api.service.impl.response.ProjOwnedSnapResRepFilter;
import com.emc.storageos.api.service.impl.response.ResRepFilter;
import com.emc.storageos.api.service.impl.response.SearchedResRepList;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.ContainmentPrefixConstraint;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.BulkRestRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.BlockSnapshotBulkRep;
import com.emc.storageos.model.block.BlockSnapshotRestRep;
import com.emc.storageos.model.block.VolumeFullCopyCreateParam;
import com.emc.storageos.model.block.export.ITLBulkRep;
import com.emc.storageos.model.block.export.ITLRestRepList;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCodeException;
import com.emc.storageos.volumecontroller.BlockController;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;

/**
 * @author burckb
 * 
 */
@Path("/block/snapshots")
@DefaultPermissions(readRoles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN },
        readAcls = { ACL.ANY },
        writeRoles = { Role.TENANT_ADMIN },
        writeAcls = { ACL.ANY })
public class BlockSnapshotService extends TaskResourceService {
    private static final String EVENT_SERVICE_TYPE = "BlockSnapshot";

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    private static final Logger _log = LoggerFactory.getLogger(BlockSnapshotService.class);

    // Block service implementations
    private Map<String, BlockServiceApi> _blockServiceApis;

    // A reference to the placement manager.
    private PlacementManager _placementManager;

    public void setBlockServiceApis(final Map<String, BlockServiceApi> serviceInterfaces) {
        _blockServiceApis = serviceInterfaces;
    }

    private BlockServiceApi getBlockServiceImpl(final String type) {
        return _blockServiceApis.get(type);
    }

    /**
     * Setter for the placement manager.
     * 
     * @param placementManager A reference to the placement manager.
     */
    public void setPlacementManager(PlacementManager placementManager) {
        _placementManager = placementManager;
    }

    /**
     * Get snapshot details
     * 
     * @prereq none
     * @param id the URN of a ViPR snapshot
     * @brief Show snapshot
     * @return Block snapshot
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public BlockSnapshotRestRep getSnapshot(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, BlockSnapshot.class, "id");
        BlockSnapshot snap = (BlockSnapshot) queryResource(id);
        return map(_dbClient, snap);
    }

    @Override
    protected BlockObject queryResource(URI id) {
        ArgValidator.checkUri(id);
        BlockObject blockObj = BlockObject.fetch(_dbClient, id);
        ArgValidator.checkEntityNotNull(blockObj, id, isIdEmbeddedInURL(id));
        return blockObj;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        BlockSnapshot snapshot = (BlockSnapshot) queryResource(id);
        URI projectUri = snapshot.getProject().getURI();
        ArgValidator.checkUri(projectUri);

        Project project = _permissionsHelper.getObjectById(projectUri, Project.class);
        ArgValidator.checkEntityNotNull(project, projectUri, isIdEmbeddedInURL(projectUri));
        return project.getTenantOrg().getURI();
    }

    /**
     * Deactivate volume snapshot, this will move the snapshot to a "marked-for-delete" state.
     * It will be deleted by the garbage collector on a subsequent iteration
     * If this snapshot was created from a volume that is part of a consistency group,
     * then all the related snapshots will be deactivated, as well.
     * 
     * @prereq none
     * @param id the URN of a ViPR snapshot
     * @brief Delete snapshot
     * @return Snapshot information
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/deactivate")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskList deactivateSnapshot(@PathParam("id") URI id) {

        BlockSnapshot snap = (BlockSnapshot) queryResource(id);
        String task = UUID.randomUUID().toString();
        TaskList response = new TaskList();

        ArgValidator.checkReference(BlockSnapshot.class, id, checkForDelete(snap));

        // Not an error if the snapshot we try to delete is already deleted
        if (snap.getInactive()) {
            Operation op = new Operation();
            op.ready("The snapshot has already been deleted");
            op.setResourceType(ResourceOperationTypeEnum.DELETE_VOLUME_SNAPSHOT);
            _dbClient.createTaskOpStatus(BlockSnapshot.class, snap.getId(), task, op);
            response.getTaskList().add(toTask(snap, task, op));
            return response;
        }

        StorageSystem device = _dbClient.queryObject(StorageSystem.class, snap.getStorageController());

        List<BlockSnapshot> snapshots = new ArrayList<BlockSnapshot>();

        final URI cgId = snap.getConsistencyGroup();
        if (!NullColumnValueGetter.isNullURI(cgId)) {
            // Collect all the BlockSnapshots if part of a CG.
            snapshots = ControllerUtils.getBlockSnapshotsBySnapsetLabelForProject(snap, _dbClient);
        } else {
            // Snap is not part of a CG so only delete the snap
            snapshots.add(snap);
        }

        // Get the block service API implementation for the snapshot parent volume.
        Volume parentVolume = _permissionsHelper.getObjectById(snap.getParent(), Volume.class);

        // Check that there are no pending tasks for these snapshots.
        checkForPendingTasks(Arrays.asList(parentVolume.getTenant().getURI()), snapshots);

        for (BlockSnapshot snapshot : snapshots) {
            Operation snapOp = _dbClient.createTaskOpStatus(BlockSnapshot.class, snapshot.getId(), task,
                    ResourceOperationTypeEnum.DELETE_VOLUME_SNAPSHOT);
            response.getTaskList().add(toTask(snapshot, task, snapOp));
        }

        // Note that for snapshots of VPLEX volumes, the parent volume for the
        // snapshot is the source side backend volume, which will have the same
        // vpool as the VPLEX volume and therefore, the correct implementation
        // should be returned.
        try {
            BlockServiceApi blockServiceApiImpl = BlockService.getBlockServiceImpl(parentVolume, _dbClient);
            blockServiceApiImpl.deleteSnapshot(snap, task);
        } catch (APIException | InternalException e) {
            String errorMsg = String.format("Exception attempting to delete snapshot %s: %s", snap.getId(), e.getMessage());
            _log.error(errorMsg);
            for (TaskResourceRep taskResourceRep : response.getTaskList()) {
                taskResourceRep.setState(Operation.Status.error.name());
                taskResourceRep.setMessage(errorMsg);
                _dbClient.error(BlockSnapshot.class, taskResourceRep.getResource().getId(), task, e);
            }
        }

        auditOp(OperationTypeEnum.DELETE_VOLUME_SNAPSHOT, true, AuditLogManager.AUDITOP_BEGIN,
                id.toString(), snap.getLabel(), snap.getParent().getName(), device.getId().toString());

        return response;
    }

    /**
     * Get list of snapshot exports
     * Returns the initiator-target pairings for this snapshot. The tenant user specifies the initiators
     * using the export snapshot call. The system selects the target ports.
     * Format of initiator is "21:11:22:33:44:55:66:77:10:00:00:00:c9:5c:90:43" for Fiber Channel
     * or "iqn.emc.com:myhost" for iSCSI.
     * 
     * @prereq none
     * @param id the URN of a ViPR Snapshot
     * @brief List snapshot exports
     * @return List of exports
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/exports")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public ITLRestRepList getSnapshotExports(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, BlockSnapshot.class, "id");
        queryResource(id);
        return ExportUtils.getBlockObjectInitiatorTargets(id, _dbClient, isIdEmbeddedInURL(id));
    }

    /**
     * Call will restore this snapshot to the volume that it is associated with.
     * If this snapshot was created from a volume in a consistency group, then all
     * related snapshots will be restored to their respective volumes.
     * 
     * @prereq none
     * @param id [required] - the URN of a ViPR block snapshot to restore from
     * @brief Restore snapshot to volume
     * @return TaskResourceRep - Task resource object for tracking this operation
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    @Path("/{id}/restore")
    public TaskResourceRep restore(@PathParam("id") URI id) {

        // Validate an get the snapshot to be restored.
        ArgValidator.checkFieldUriType(id, BlockSnapshot.class, "id");
        BlockSnapshot snapshot = (BlockSnapshot) queryResource(id);

        // Get the block service API implementation for the snapshot parent volume.
        Volume parentVolume = _permissionsHelper.getObjectById(snapshot.getParent(), Volume.class);

        // Make sure that we don't have some pending
        // operation against the parent volume
        checkForPendingTasks(Arrays.asList(parentVolume.getTenant().getURI()), Arrays.asList(parentVolume));

        // Get the storage system for the volume
        StorageSystem storage = _permissionsHelper.getObjectById(parentVolume.getStorageController(), StorageSystem.class);
        if (storage.checkIfVmax3()) {
            if (snapshot.getSettingsInstance() == null) {
                throw APIException.badRequests.snapshotNullSettingsInstance(snapshot.getLabel());
            }
        }

        // restore for OpenStack storage system type is not supported
        if (Type.openstack.name().equalsIgnoreCase(storage.getSystemType()))
        {
            throw APIException.methodNotAllowed.notSupportedWithReason(
                    String.format("Snapshot restore is not possible on third-party storage systems"));
        }

        BlockServiceApi blockServiceApiImpl = BlockService.getBlockServiceImpl(parentVolume, _dbClient);

        // Validate the restore snapshot request.
        blockServiceApiImpl.validateRestoreSnapshot(snapshot, parentVolume);

        // Create the task identifier.
        String taskId = UUID.randomUUID().toString();

        // Create the operation status entry in the status map for the snapshot.
        Operation op = new Operation();
        op.setResourceType(ResourceOperationTypeEnum.RESTORE_VOLUME_SNAPSHOT);
        _dbClient.createTaskOpStatus(BlockSnapshot.class, snapshot.getId(), taskId, op);
        snapshot.getOpStatus().put(taskId, op);

        // Restore the snapshot.
        blockServiceApiImpl.restoreSnapshot(snapshot, parentVolume, taskId);

        // Create the audit log entry.
        auditOp(OperationTypeEnum.RESTORE_VOLUME_SNAPSHOT, true, AuditLogManager.AUDITOP_BEGIN,
                id.toString(), parentVolume.getId().toString(), snapshot.getStorageController().toString());

        return toTask(snapshot, taskId, op);
    }

    /**
     * Call will resynchronize this snapshot from the volume that it is associated with.
     * If this snapshot was created from a volume in a consistency group, then all
     * related snapshots will be resynchronized.
     * 
     * @prereq none
     * @param id [required] - the URN of a ViPR block snapshot to restore from
     * @brief Resynchronize snapshot
     * @return TaskResourceRep - Task resource object for tracking this operation
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    @Path("/{id}/resynchronize")
    public TaskResourceRep resynchronize(@PathParam("id") URI id) {

        // Validate an get the snapshot to be restored.
        ArgValidator.checkFieldUriType(id, BlockSnapshot.class, "id");
        BlockSnapshot snapshot = (BlockSnapshot) queryResource(id);

        // Get the block service API implementation for the snapshot parent volume.
        Volume volume = _permissionsHelper.getObjectById(snapshot.getParent(), Volume.class);
        // Get the storage system for the volume
        StorageSystem storage = _permissionsHelper.getObjectById(volume.getStorageController(), StorageSystem.class);
        if (storage.checkIfVmax3()) {
            if (snapshot.getSettingsInstance() == null) {
                throw APIException.badRequests.snapshotNullSettingsInstance(snapshot.getLabel());
            }
        }

        // resync for OpenStack storage system type is not supported
        if (Type.openstack.name().equalsIgnoreCase(storage.getSystemType()))
        {
            throw APIException.methodNotAllowed.notSupportedWithReason(
                    String.format("Snapshot restore is not possible on third-party storage systems"));
        }

        // resync for VNX storage system type is not supported
        if (Type.vnxblock.name().equalsIgnoreCase(storage.getSystemType()))
        {
            throw APIException.methodNotAllowed.notSupportedWithReason(
                    "Snapshot resynchronization is not supported on VNX storage systems");
        }

        BlockServiceApi blockServiceApiImpl = BlockService.getBlockServiceImpl(volume, _dbClient);

        // Validate the resync snapshot request.
        blockServiceApiImpl.validateResynchronizeSnapshot(snapshot, volume);

        // Create the task identifier.
        String taskId = UUID.randomUUID().toString();

        // Create the operation status entry in the status map for the snapshot.
        Operation op = new Operation();
        op.setResourceType(ResourceOperationTypeEnum.RESYNCHRONIZE_VOLUME_SNAPSHOT);
        _dbClient.createTaskOpStatus(BlockSnapshot.class, snapshot.getId(), taskId, op);
        snapshot.getOpStatus().put(taskId, op);

        // Resync the snapshot.
        blockServiceApiImpl.resynchronizeSnapshot(snapshot, volume, taskId);

        // Create the audit log entry.
        auditOp(OperationTypeEnum.RESYNCHRONIZE_VOLUME_SNAPSHOT, true, AuditLogManager.AUDITOP_BEGIN,
                id.toString(), volume.getId().toString(), snapshot.getStorageController().toString());

        return toTask(snapshot, taskId, op);
    }

    /**
     * Call will activate this snapshot, essentially establishing the synchronization
     * between the source and target. The "heavy lifting" of getting the snapshot
     * to the point where it can be activated should have been done by the create.
     * 
     * @prereq Create snapshot as inactive
     * @param id [required] - the URN of a ViPR block snapshot to restore from
     * @brief Activate snapshot
     * @return TaskResourceRep - Task resource object for tracking this operation
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    @Path("/{id}/activate")
    public TaskResourceRep activate(@PathParam("id") URI id) {

        Operation op = new Operation();
        op.setResourceType(ResourceOperationTypeEnum.ACTIVATE_VOLUME_SNAPSHOT);
        ArgValidator.checkFieldUriType(id, BlockSnapshot.class, "id");
        BlockSnapshot snapshot = (BlockSnapshot) queryResource(id);

        // Get the block service API implementation for the snapshot parent volume.
        Volume parentVolume = _permissionsHelper.getObjectById(snapshot.getParent(), Volume.class);

        // Make sure that we don't have some pending
        // operation against the parent volume
        checkForPendingTasks(Arrays.asList(parentVolume.getTenant().getURI()), Arrays.asList(parentVolume));

        StorageSystem device = _dbClient.queryObject(StorageSystem.class, snapshot.getStorageController());
        BlockController controller = getController(BlockController.class, device.getSystemType());

        String task = UUID.randomUUID().toString();

        // If the snapshot is already active, there would be no need to queue
        // another request to activate it again.
        if (snapshot.getIsSyncActive()) {
            op.ready("Snapshot is already active");
            _dbClient.createTaskOpStatus(BlockSnapshot.class, snapshot.getId(), task, op);
            return toTask(snapshot, task, op);
        }

        _dbClient.createTaskOpStatus(BlockSnapshot.class, snapshot.getId(), task, op);

        List<URI> snapshotList = new ArrayList<URI>();
        if (!NullColumnValueGetter.isNullURI(snapshot.getConsistencyGroup())) {
            List<BlockSnapshot> snapshots = ControllerUtils.getBlockSnapshotsBySnapsetLabelForProject(snapshot, _dbClient);
            for (BlockSnapshot snap : snapshots) {
                snapshotList.add(snap.getId());
            }
        } else {
            snapshotList.add(id);
        }

        // If the volume is under protection
        if (snapshot.getEmName() != null) {
            // RP snapshots cannot be activated so throw exception
            throw new ServiceCodeException(API_BAD_REQUEST, "RecoverPoint snapshots cannot be activated.",
                    null);
        } else {
            controller.activateSnapshot(device.getId(), snapshotList, task);
        }

        auditOp(OperationTypeEnum.ACTIVATE_VOLUME_SNAPSHOT, true, AuditLogManager.AUDITOP_BEGIN,
                snapshot.getId().toString(), snapshot.getLabel());
        return toTask(snapshot, task, op);
    }

    /**
     * Generates a group synchronized between volume Replication group
     * and snapshot Replication group.
     * 
     * @prereq There should be existing Storage synchronized relations
     *         between volumes and snapshots.
     * 
     * @param id [required] - the URN of a ViPR block snapshot
     * 
     * @return TaskList
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    @Path("/{id}/start")
    public TaskResourceRep startSnapshot(@PathParam("id") URI id)
            throws InternalException {

        // Validate and get the snapshot.
        ArgValidator.checkFieldUriType(id, BlockSnapshot.class, "id");
        BlockSnapshot snapshot = (BlockSnapshot) queryResource(id);

        // Get the block service API implementation for the snapshot parent volume.
        Volume volume = _permissionsHelper.getObjectById(snapshot.getParent(), Volume.class);
        // Get the storage system for the volume
        StorageSystem storage = _permissionsHelper.getObjectById(volume.getStorageController(), StorageSystem.class);

        if (!snapshot.getIsSyncActive()) {
            throw APIException.badRequests.cannotEstablishGroupRelationForInactiveSnapshot(snapshot.getLabel());
        }

        if (!volume.hasConsistencyGroup() ||
                !snapshot.hasConsistencyGroup()) {
            throw APIException.badRequests.blockObjectHasNoConsistencyGroup();
        }

        // Create the task identifier.
        String taskId = UUID.randomUUID().toString();

        BlockServiceApi blockServiceApiImpl = getBlockServiceImpl("default");

        // Create the audit log entry.
        auditOp(OperationTypeEnum.ESTABLISH_VOLUME_SNAPSHOT, true, AuditLogManager.AUDITOP_BEGIN,
                id.toString(), volume.getId().toString(), snapshot.getStorageController().toString());

        return blockServiceApiImpl.establishVolumeAndSnapshotGroupRelation(storage, volume, snapshot, taskId);
    }

    /**
     * Create a full copy as a volume of the specified snapshot.
     * 
     * @brief Create full copies as volumes
     * 
     * @prereq none
     * 
     * @param id Source snapshot URI
     * @param param POST data containing full copy creation information
     * 
     * @return TaskList
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/full-copies")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskList createFullCopy(@PathParam("id") URI id,
            VolumeFullCopyCreateParam param) throws InternalException {
        BlockSnapshot snapshot = (BlockSnapshot) queryResource(id);

        // Get the block service API implementation for the snapshot parent volume.
        Volume parentVolume = _permissionsHelper.getObjectById(snapshot.getParent(), Volume.class);

        // Make sure that we don't have some pending
        // operation against the parent volume
        checkForPendingTasks(Arrays.asList(parentVolume.getTenant().getURI()), Arrays.asList(parentVolume));

        return getFullCopyManager().createFullCopy(id, param);
    }

    /**
     * Creates and returns an instance of the block full copy manager to handle
     * a full copy request.
     * 
     * @return BlockFullCopyManager
     */
    private BlockFullCopyManager getFullCopyManager() {
        BlockFullCopyManager fcManager = new BlockFullCopyManager(_dbClient,
                _permissionsHelper, _auditMgr, _coordinator, _placementManager, sc, uriInfo,
                _request, null);
        return fcManager;
    }

    /**
     * Record audit log for BlockSnapshot service
     * 
     * @param auditType Type of AuditLog
     * @param operationalStatus Status of operation
     * @param operationStage Stage of operation.
     *            For sync operation, it should be null;
     *            For async operation, it should be "BEGIN" or "END";
     * @param descparams Description paramters
     */
    public void auditBlockSnapshot(OperationTypeEnum auditType,
            String operationalStatus,
            String operationStage,
            Object... descparams) {

        _auditMgr.recordAuditLog(URI.create(getUserFromContext().getTenantId()),
                URI.create(getUserFromContext().getName()),
                EVENT_SERVICE_TYPE,
                auditType,
                System.currentTimeMillis(),
                operationalStatus,
                operationStage,
                descparams);
    }

    /**
     * Retrieve resource representations based on input ids.
     * 
     * @prereq none
     * @param param POST data containing the id list.
     * @brief List data of block snapshot resources
     * @return list of representations.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public BlockSnapshotBulkRep getBulkResources(BulkIdParam param) {
        return (BlockSnapshotBulkRep) super.getBulkResources(param);
    }

    /**
     * Return all the export information related to snaphot ids passed.
     * This will be in the form of a list of initiator / target pairs
     * for all the initiators that have been paired with a target
     * storage port.
     * 
     * 
     * @prereq none
     * 
     * @param param POST data containing the id list.
     * 
     * @brief Show export information for snapshots
     * @return List of exports
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/exports/bulk")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public ITLBulkRep getSnapshotsExports(BulkIdParam param) {
        List<URI> snapshotIdList = param.getIds();
        ITLBulkRep list = new ITLBulkRep();
        for (URI snapshotId : snapshotIdList) {
            ArgValidator.checkFieldUriType(snapshotId, BlockSnapshot.class, "id");
            queryResource(snapshotId);
            list.getExportList().addAll(
                    ExportUtils.getBlockObjectInitiatorTargets(snapshotId, _dbClient, isIdEmbeddedInURL(snapshotId)).getExportList());
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<BlockSnapshot> getResourceClass() {
        return BlockSnapshot.class;
    }

    /**
     * Retrieve BlockSnapshot representations based on input ids.
     * 
     * @return list of BlockSnapshot representations.
     */
    @Override
    public BlockSnapshotBulkRep queryBulkResourceReps(List<URI> ids) {

        Iterator<BlockSnapshot> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        return new BlockSnapshotBulkRep(BulkList.wrapping(_dbIterator, MapBlockSnapshot.getInstance(_dbClient)));
    }

    @Override
    protected BulkRestRep queryFilteredBulkResourceReps(
            List<URI> ids) {

        Iterator<BlockSnapshot> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        ResourceFilter<BlockSnapshot> filter = new BlockSnapshotFilter(getUserFromContext(), _permissionsHelper);
        return new BlockSnapshotBulkRep(BulkList.wrapping(_dbIterator, MapBlockSnapshot.getInstance(_dbClient), filter));

    }

    private class BlockSnapshotFilter extends PermissionsEnforcingResourceFilter<BlockSnapshot> {

        protected BlockSnapshotFilter(StorageOSUser user,
                PermissionsHelper permissionsHelper) {
            super(user, permissionsHelper);
        }

        @Override
        public boolean isAccessible(BlockSnapshot resource) {
            boolean ret = false;
            ret = isTenantAccessible(getTenantOwner(resource.getId()));
            if (!ret) {
                NamedURI proj = resource.getProject();
                if (proj != null) {
                    ret = isProjectAccessible(proj.getURI());
                }
            }
            return ret;
        }
    }

    /**
     * Block snapshot is not a zone level resource
     */
    @Override
    protected boolean isZoneLevelResource() {
        return false;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.BLOCK_SNAPSHOT;
    }

    /**
     * Get search results by name in zone or project.
     * 
     * @return SearchedResRepList
     */
    @Override
    protected SearchedResRepList getNamedSearchResults(String name, URI projectId) {
        SearchedResRepList resRepList = new SearchedResRepList(getResourceType());
        if (projectId == null) {
            _dbClient.queryByConstraint(
                    PrefixConstraint.Factory.getLabelPrefixConstraint(getResourceClass(), name),
                    resRepList);
        } else {
            _dbClient.queryByConstraint(
                    ContainmentPrefixConstraint.Factory.getBlockSnapshotUnderProjectConstraint(
                            projectId, name), resRepList);
        }
        return resRepList;
    }

    /**
     * Get search results by project alone.
     * 
     * @return SearchedResRepList
     */
    @Override
    protected SearchedResRepList getProjectSearchResults(URI projectId) {
        SearchedResRepList resRepList = new SearchedResRepList(getResourceType());
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getProjectBlockSnapshotConstraint(projectId),
                resRepList);
        return resRepList;
    }

    /**
     * Get object specific permissions filter
     * 
     */
    @Override
    public ResRepFilter<? extends RelatedResourceRep> getPermissionFilter(StorageOSUser user,
            PermissionsHelper permissionsHelper) {
        return new ProjOwnedSnapResRepFilter(user, permissionsHelper, BlockSnapshot.class);
    }

    /**
     * Exposes the target volume associated with BlockSnapshot instance with the passed id
     * as a ViPR Volume. Currently, the BlockSnapshot instance must represent a snapshot
     * whose parent volume is the backend volume for a VPLEX volume. That is, it must be a
     * VPLEX snapshot. The purpose is to expose the backend snapshot as a VPLEX volume so
     * that access to the snapshot is via the VPLEX rather than directly via the backend
     * storage system.
     * 
     * @brief Expose snapshot as a volume.
     * 
     * @prereq None
     * 
     * @param id The URI of the BlockSnapshot instance.
     * 
     * @return A TaskResourceRep.
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/expose")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskResourceRep exposeSnapshotAsVolume(@PathParam("id") URI id) {

        // Validate and get the block snapshot.
        ArgValidator.checkFieldUriType(id, BlockSnapshot.class, "id");
        BlockSnapshot snapshot = (BlockSnapshot) queryResource(id);

        // Verify it is a block snapshot for a VPLEX volume.
        URI sourceVolumeURI = snapshot.getParent().getURI();
        Volume sourceVolume = _dbClient.queryObject(Volume.class, sourceVolumeURI);
        if (!Volume.checkForVplexBackEndVolume(_dbClient, sourceVolume)) {
            throw APIException.badRequests.cantExposeNonVPLEXSnapshot(id.toString());
        }

        // Verify it is not marked for deletion.
        if (snapshot.getInactive()) {
            throw APIException.badRequests.cantExposeInactiveSnapshot(id.toString());
        }

        // Verify that it has been activated such that the target volume
        // reflects the source volume data. Otherwise, when the snapshot
        // is activated, the read cache for the VPLEX volume would be invalid.
        if (!snapshot.getIsSyncActive()) {
            throw APIException.badRequests.cantExposeUnsynchronizedSnapshot(id.toString());
        }

        // Verify it has yet to be used to create a VPLEX volume.
        // A snapshot can only be used to create a single VPLEX volume.
        // This is because multiple VPLEX volumes cannot use the same
        // backend volume. We know it has already been imported to
        // VPLEX if there is an active volume in the database with the
        // same native GUID as the snapshot.
        String snapshotNativeGuid = snapshot.getNativeGuid();
        if (!CustomQueryUtility.getActiveVolumeByNativeGuid(_dbClient, snapshotNativeGuid).isEmpty()) {
            throw APIException.badRequests.cantExposeSnapshotAlreadyExposed(id.toString());
        }

        // Get the virtual pool of the snapshot source volume. We need to set
        // a virtual pool for the VPLEX volume that will be created. Currently,
        // we use the virtual pool for the source volume, even though it may not
        // apply for the VPLEX volume we create. It really should not matter much
        // as we will limit the operations on this VPLEX volume to Export/Unexport,
        // so really, the only parameters we need are the PATH parameters, which
        // implies this VPLEX volume would have the same path constraints. The
        // alternative is to create a generic internal virtual pool.
        VirtualPool sourceVolumeVpool = _dbClient.queryObject(VirtualPool.class, sourceVolume.getVirtualPool());

        // Create a new INTERNAL Volume instance using the volume info
        // in the block snapshot. VPLEX volumes are created from Volume
        // instances rather then BlockObject instances, so we cannot use
        // the BlockSnapshot directly. The other alternative is to modify
        // all code involved with VPLEX volume creation and all code that
        // accesses or modifies the backend volumes for VPLEX volumes. This
        // would be a large, invasive undertaking.
        Volume backendVolume = prepareVPLEXBackendVolumeFromSnapshot(snapshot, sourceVolume);

        // Create a unique task identifier.
        String taskId = UUID.randomUUID().toString();

        // Get the VPLEX block service implementation and call the routine to
        // import a non-VPLEX volume into VPLEX.
        try {
            VPlexBlockServiceApiImpl vplexBlocSvcApi = (VPlexBlockServiceApiImpl) getBlockServiceImpl("vplex");
            vplexBlocSvcApi.importVirtualVolume(snapshot.getStorageController(), backendVolume, sourceVolumeVpool, taskId);
        } catch (Exception e) {
            _log.error("Exception importing snapshot to VPLEX", e);
            backendVolume.setInactive(true);
            _dbClient.persistObject(backendVolume);
            throw e;
        }

        // Create and return the task for this request. NOte that we
        // create the operation on the snapshot, but the import routine
        // will expect the operation to be on the volume. We updated the
        // completer to update the status on the snapshot as well if we
        // find a snapshot with the same native guid as the volume.
        Operation op = new Operation();
        op.setResourceType(ResourceOperationTypeEnum.CREATE_VPLEX_VOLUME_FROM_SNAPSHOT);
        _dbClient.createTaskOpStatus(BlockSnapshot.class, id, taskId, op);
        snapshot.getOpStatus().put(taskId, op);

        // Return the task.
        return toTask(snapshot, taskId, op);
    }

    /**
     * Creates a Volume instance to represent the snapshot target volume represented
     * by the passed BlockSnapshot instance.
     * 
     * @param snapshot A reference to a BlockSnapshot instance.
     * @param sourceVolume A reference to the snapshot source Volume.
     * 
     * @return A new INTERNAL Volume instance representing the snapshot target volume.
     */
    private Volume prepareVPLEXBackendVolumeFromSnapshot(BlockSnapshot snapshot, Volume sourceVolume) {

        // Create a new Volume instance and populate its fields with the
        // target volume information in the passed snapshot. Note that
        // not all fields need be populated. This is an INTERNAL volume
        // and the operations on the VPLEX volume created on top of it
        // will be limited. We set the fields that accurately reflect
        // the volume and are necessary to allow it to be used as a
        // backend volume for a VPLEX volume and to allow that VPLEX
        // volume to subsequently be exported to hosts/clusters.
        Volume volume = new Volume();
        volume.setId(URIUtil.createId(Volume.class));
        volume.setLabel(snapshot.getLabel());
        volume.setWWN(snapshot.getWWN());
        volume.setNativeId(snapshot.getNativeId());
        volume.setNativeGuid(snapshot.getNativeGuid());
        volume.setAlternateName(snapshot.getAlternateName());
        volume.setDeviceLabel(snapshot.getDeviceLabel());
        volume.setProvisionedCapacity(snapshot.getProvisionedCapacity());
        volume.setAllocatedCapacity(snapshot.getAllocatedCapacity());
        volume.setVirtualArray(snapshot.getVirtualArray());
        volume.setProject(snapshot.getProject());
        volume.setStorageController(snapshot.getStorageController());
        StringSet protocols = new StringSet();
        protocols.addAll(snapshot.getProtocol());
        volume.setProtocol(protocols);
        volume.addInternalFlags(DataObject.Flag.INTERNAL_OBJECT);
        volume.setOpStatus(new OpStatusMap());

        // Some of the volume parameters must come from the source volume
        // of the passed block snapshot including the Tenant.
        volume.setTenant(sourceVolume.getTenant());

        // The snapshot does not contain the user requested capacity.
        volume.setCapacity(sourceVolume.getCapacity());

        // The snapshot target should be thin if the source volume was
        // thinly provisioned. This will control be used to control whether
        // or not thin rebuilds occur when the volume we create is
        // distributed.
        volume.setThinlyProvisioned(sourceVolume.getThinlyProvisioned());

        // The source side backend volume for a VPLEX volume always
        // has the same virtual pool as the VPLEX volume, and we will
        // use the source volume virtual pool for the VPLEX volume.
        volume.setVirtualPool(sourceVolume.getVirtualPool());

        // Set auto tier policy from the source volume. This comes
        // into play in vmax3 export operations.
        volume.setAutoTieringPolicyUri(sourceVolume.getAutoTieringPolicyUri());

        // The pool is not currently set as the BlockSnapshot does not
        // keep the storage pool for the target volume. This did not
        // cause issues for the allowed operations (export/unexport/delete)
        // for the VPLEX volume built on the snapshot. We could alternatively
        // try and set the storage pool in the block snapshot instance so
        // we have the actual storage pool.
        // volume.setPool(TBD);

        // Create the instance in the database.
        _dbClient.createObject(volume);

        return volume;
    }
}
