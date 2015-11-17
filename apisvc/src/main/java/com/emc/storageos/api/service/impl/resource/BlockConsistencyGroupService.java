/*
 * Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.BlockMapper.map;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;
import static com.emc.storageos.db.client.constraint.ContainmentConstraint.Factory.getBlockSnapshotByConsistencyGroup;
import static com.emc.storageos.db.client.constraint.ContainmentConstraint.Factory.getVolumesByConsistencyGroup;
import static com.emc.storageos.db.client.util.CommonTransformerFunctions.fctnDataObjectToID;
import static com.emc.storageos.model.block.Copy.SyncDirection.SOURCE_TO_TARGET;
import static com.emc.storageos.svcs.errorhandling.resources.ServiceCode.CONTROLLER_ERROR;
import static com.google.common.collect.Collections2.transform;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.BlockMapper;
import com.emc.storageos.api.mapper.TaskMapper;
import com.emc.storageos.api.mapper.functions.MapBlockConsistencyGroup;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.placement.PlacementManager;
import com.emc.storageos.api.service.impl.resource.BlockService.ProtectionOp;
import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyManager;
import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyUtils;
import com.emc.storageos.api.service.impl.resource.utils.BlockServiceUtils;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.api.service.impl.response.ProjOwnedResRepFilter;
import com.emc.storageos.api.service.impl.response.ResRepFilter;
import com.emc.storageos.api.service.impl.response.SearchedResRepList;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.ContainmentPrefixConstraint;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshot.TechnologyType;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.PersonalityTypes;
import com.emc.storageos.db.client.model.util.BlockConsistencyGroupUtils;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.BulkRestRep;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.SnapshotList;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.BlockConsistencyGroupBulkRep;
import com.emc.storageos.model.block.BlockConsistencyGroupCreate;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.storageos.model.block.BlockConsistencyGroupSnapshotCreate;
import com.emc.storageos.model.block.BlockConsistencyGroupUpdate;
import com.emc.storageos.model.block.BlockSnapshotRestRep;
import com.emc.storageos.model.block.BulkDeleteParam;
import com.emc.storageos.model.block.CopiesParam;
import com.emc.storageos.model.block.Copy;
import com.emc.storageos.model.block.NamedVolumesList;
import com.emc.storageos.model.block.VolumeDeleteTypeEnum;
import com.emc.storageos.model.block.VolumeFullCopyCreateParam;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.protectioncontroller.RPController;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.srdfcontroller.SRDFController;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCodeException;
import com.emc.storageos.volumecontroller.BlockController;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;

@Path("/block/consistency-groups")
@DefaultPermissions(readRoles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, readAcls = { ACL.OWN,
        ACL.ALL }, writeRoles = { Role.TENANT_ADMIN }, writeAcls = { ACL.OWN, ACL.ALL })
public class BlockConsistencyGroupService extends TaskResourceService {

    private static final Logger _log = LoggerFactory.getLogger(BlockConsistencyGroupService.class);
    private static final int CG_MAX_LIMIT = 64;

    // A reference to the placement manager.
    private PlacementManager _placementManager;

    // A reference to the block service.
    private BlockService _blockService;

    // Block service implementations
    private Map<String, BlockServiceApi> _blockServiceApis;

    /**
     * Setter for the placement manager.
     *
     * @param placementManager A reference to the placement manager.
     */
    public void setPlacementManager(PlacementManager placementManager) {
        _placementManager = placementManager;
    }

    /**
     * Setter for the block service.
     *
     * @param blockService A reference to the block service.
     */
    public void setBlockService(BlockService blockService) {
        _blockService = blockService;
    }

    public void setBlockServiceApis(final Map<String, BlockServiceApi> serviceInterfaces) {
        _blockServiceApis = serviceInterfaces;
    }

    private BlockServiceApi getBlockServiceImpl(final String type) {
        return _blockServiceApis.get(type);
    }

    private BlockServiceApi getBlockServiceImpl(final BlockConsistencyGroup cg) {
        BlockServiceApi blockServiceApiImpl = null;
        if (cg.checkForType(Types.RP)) {
            blockServiceApiImpl = getBlockServiceImpl(BlockConsistencyGroup.Types.RP.toString().toLowerCase());
        } else if (cg.checkForType(Types.VPLEX)) {
            blockServiceApiImpl = getBlockServiceImpl(BlockConsistencyGroup.Types.VPLEX.toString().toLowerCase());
        } else {
            blockServiceApiImpl = getBlockServiceImpl("group");
        }
        return blockServiceApiImpl;
    }

    /**
     * Get the specific BlockServiceApiImpl based on the storage system type.
     *
     * @param system The storage system instance
     * @return BloackServiceApiImpl for the storage system type.
     */
    private BlockServiceApi getBlockServiceImpl(final StorageSystem system) {
        BlockServiceApi blockServiceApiImpl = null;
        String systemType = system.getSystemType();
        if (systemType.equals(DiscoveredDataObject.Type.rp.name()) ||
                systemType.equals(DiscoveredDataObject.Type.vplex.name())) {
            blockServiceApiImpl = getBlockServiceImpl(systemType);
        } else {
            blockServiceApiImpl = getBlockServiceImpl("group");
        }
        return blockServiceApiImpl;
    }

    @Override
    protected DataObject queryResource(final URI id) {
        ArgValidator.checkUri(id);

        final Class<? extends DataObject> clazz = URIUtil.isType(id, BlockSnapshot.class) ? BlockSnapshot.class
                : BlockConsistencyGroup.class;

        final DataObject consistencyGroup = _permissionsHelper.getObjectById(id, clazz);

        ArgValidator.checkEntityNotNull(consistencyGroup, id, isIdEmbeddedInURL(id));

        return consistencyGroup;
    }

    @Override
    protected URI getTenantOwner(final URI id) {
        return null;
    }

    /**
     * Create a new consistency group
     *
     * You can create a consistency group, but adding volumes into it will be done using in the
     * volume create operations:
     *
     * 1. Create CG object in Bourne 2. Operation will be synchronous
     *
     *
     * @prereq none
     *
     * @param param
     *
     * @brief Create consistency group
     * @return Consistency Group created
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public BlockConsistencyGroupRestRep createConsistencyGroup(
            final BlockConsistencyGroupCreate param) {
        checkForDuplicateName(param.getName(), BlockConsistencyGroup.class);

        // Validate name
        ArgValidator.checkFieldNotEmpty(param.getName(), "name");

        // Validate name not greater than 64 characters
        ArgValidator.checkFieldLengthMaximum(param.getName(), CG_MAX_LIMIT, "name");

        // Validate project
        ArgValidator.checkFieldUriType(param.getProject(), Project.class, "project");
        final Project project = _dbClient.queryObject(Project.class, param.getProject());
        ArgValidator
                .checkEntity(project, param.getProject(), isIdEmbeddedInURL(param.getProject()));
        // Verify the user is authorized.
        verifyUserIsAuthorizedForRequest(project);

        // Create Consistency Group in db
        final BlockConsistencyGroup consistencyGroup = new BlockConsistencyGroup();
        consistencyGroup.setId(URIUtil.createId(BlockConsistencyGroup.class));
        consistencyGroup.setLabel(param.getName());
        consistencyGroup.setProject(new NamedURI(project.getId(), param.getName()));
        consistencyGroup.setTenant(new NamedURI(project.getTenantOrg().getURI(), param.getName()));

        _dbClient.createObject(consistencyGroup);

        return map(consistencyGroup, null, _dbClient);

    }

    /**
     * Show details for a specific consistency group
     *
     *
     * @prereq none
     *
     * @param id the URN of a ViPR Consistency group
     *
     * @brief Show consistency group
     * @return Consistency group
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public BlockConsistencyGroupRestRep getConsistencyGroup(@PathParam("id") final URI id) {
        ArgValidator.checkFieldUriType(id, BlockConsistencyGroup.class, "id");

        // Query for the consistency group
        final BlockConsistencyGroup consistencyGroup = (BlockConsistencyGroup) queryResource(id);

        // Get the implementation for the CG.
        BlockServiceApi blockServiceApiImpl = getBlockServiceImpl(consistencyGroup);

        // Get the CG volumes
        List<Volume> volumes = blockServiceApiImpl.getActiveCGVolumes(consistencyGroup);

        // If no volumes, just return the consistency group
        if (volumes.isEmpty()) {
            return map(consistencyGroup, null, _dbClient);
        }

        Set<URI> volumeURIs = new HashSet<URI>();
        for (Volume volume : volumes) {
            volumeURIs.add(volume.getId());
        }
        return map(consistencyGroup, volumeURIs, _dbClient);
    }

    /**
     * Deletes a consistency group
     *
     * Do not delete if snapshots exist for consistency group
     *
     *
     * @prereq Dependent snapshot resources must be deleted
     *
     * @param id the URN of a ViPR Consistency group
     *
     * @brief Delete consistency group
     * @return TaskResourceRep
     *
     * @throws InternalException
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/deactivate")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskResourceRep deleteConsistencyGroup(@PathParam("id") final URI id)
            throws InternalException {
        // Query for the given id
        final BlockConsistencyGroup consistencyGroup = (BlockConsistencyGroup) queryResource(id);
        ArgValidator.checkReference(BlockConsistencyGroup.class, id,
                checkForDelete(consistencyGroup));
        String task = UUID.randomUUID().toString();

        // srdf/rp cgs can be deleted from vipr only if there are no more volumes associated.
        // If the consistency group is inactive or has yet to be created on
        // a storage system, then the deletion is not controller specific.

        // RP + VPlex CGs cannot be be deleted without VPlex controller intervention.
        if (consistencyGroup.getTypes().contains(Types.SRDF.toString()) ||
                (consistencyGroup.getTypes().contains(Types.RP.toString()) &&
                !consistencyGroup.getTypes().contains(Types.VPLEX.toString())) ||
                deleteUncreatedConsistencyGroup(consistencyGroup)) {
            final URIQueryResultList cgVolumesResults = new URIQueryResultList();
            _dbClient.queryByConstraint(getVolumesByConsistencyGroup(consistencyGroup.getId()),
                    cgVolumesResults);
            while (cgVolumesResults.iterator().hasNext()) {
                Volume volume = _dbClient.queryObject(Volume.class, cgVolumesResults.iterator().next());
                if (!volume.getInactive()) {
                    throw APIException.badRequests.deleteOnlyAllowedOnEmptyCGs(
                            consistencyGroup.getTypes().toString());
                }
            }
            consistencyGroup.setStorageController(null);
            consistencyGroup.setInactive(true);
            _dbClient.persistObject(consistencyGroup);
            return finishDeactivateTask(consistencyGroup, task);
        }

        final StorageSystem storageSystem = consistencyGroup.created() ? _permissionsHelper
                .getObjectById(consistencyGroup.getStorageController(), StorageSystem.class) : null;

        // If the consistency group has been created, and the system
        // is a VPlex, then we need to do VPlex related things to destroy
        // the consistency groups on the system. If the consistency group
        // has not been created on the system or the system is not a VPlex
        // revert to the default.
        BlockServiceApi blockServiceApi = getBlockServiceImpl("group");
        if (storageSystem != null) {
            String systemType = storageSystem.getSystemType();
            if (DiscoveredDataObject.Type.vplex.name().equals(systemType)) {
                blockServiceApi = getBlockServiceImpl(systemType);
            }
            _log.info(String.format("BlockConsistencyGroup %s is associated to StorageSystem %s. Going to delete it on that array.",
                    consistencyGroup.getLabel(), storageSystem.getNativeGuid()));
            // Otherwise, invoke operation to delete CG from the array.
            return blockServiceApi.deleteConsistencyGroup(storageSystem, consistencyGroup, task);
        }
        _log.info(String.format("BlockConsistencyGroup %s was not associated with any storage. Deleting it from ViPR only.",
                consistencyGroup.getLabel()));
        return finishDeactivateTask(consistencyGroup, task);
    }

    /**
     * Check to see if the consistency group is active and not created. In
     * this case we can delete the consistency group. Otherwise we should
     * not delete the consistency group.
     *
     * @param consistencyGroup
     *            A reference to the CG.
     *
     * @return True if the CG is active and not created.
     */
    private boolean deleteUncreatedConsistencyGroup(
            final BlockConsistencyGroup consistencyGroup) {
        // If the consistency group is active and not created we can delete it,
        // otherwise we cannot.
        return (!consistencyGroup.getInactive() && !consistencyGroup.created());
    }

    /**
     * Retrieve resource representations based on input ids.
     *
     *
     * @prereq none
     *
     * @param param
     *            POST data containing the id list.
     *
     * @brief List data of consistency group resources
     * @return list of representations.
     *
     * @throws DatabaseException
     *             When an error occurs querying the database.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public BlockConsistencyGroupBulkRep getBulkResources(final BulkIdParam param) {
        return (BlockConsistencyGroupBulkRep) super.getBulkResources(param);
    }

    /**
     * Creates a consistency group snapshot
     *
     *
     * @prereq none
     *
     * @param consistencyGroupId
     *            - Consistency group URI
     * @param param
     *
     * @brief Create consistency group snapshot
     * @return TaskList
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/snapshots")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN }, acls = { ACL.ANY })
    public TaskList createConsistencyGroupSnapshot(@PathParam("id") final URI consistencyGroupId,
            final BlockConsistencyGroupSnapshotCreate param) {
        // Query Consistency Group
        final BlockConsistencyGroup consistencyGroup = (BlockConsistencyGroup) queryResource(consistencyGroupId);

        // Ensure that the Consistency Group has been created on all of its defined
        // system types.
        if (!consistencyGroup.created()) {
            throw APIException.badRequests.consistencyGroupNotCreated();
        }

        // Snapshots of RecoverPoint consistency groups is not supported.
        if (consistencyGroup.checkForType(Types.RP)) {
            throw APIException.badRequests.snapshotsNotSupportedForRPCGs();
        }

        // Maintain pre-2.2 functionality for VPLEX CGs created prior to
        // release 2.2, which does not allow snapping a consistency group.
        URI cgStorageControllerURI = consistencyGroup.getStorageController();
        if (!NullColumnValueGetter.isNullURI(cgStorageControllerURI)) {
            // No snapshots for VPLEX consistency groups.
            StorageSystem cgStorageController = _dbClient.queryObject(
                    StorageSystem.class, cgStorageControllerURI);
            if (DiscoveredDataObject.Type.vplex.name().equals(cgStorageController
                    .getSystemType()) && (!consistencyGroup.checkForType(Types.LOCAL)
                    || BlockConsistencyGroupUtils.getLocalSystemsInCG(consistencyGroup, _dbClient).isEmpty())) {
                _log.error("{} Group Snapshot operations not supported when there is no backend CG", consistencyGroup.getId());
                throw APIException.badRequests.cannotCreateSnapshotOfVplexCG();
            }
        }

        // Get the block service implementation
        BlockServiceApi blockServiceApiImpl = getBlockServiceImpl(consistencyGroup);

        // Get the volumes in the consistency group.
        List<Volume> volumeList = blockServiceApiImpl.getActiveCGVolumes(consistencyGroup);

        // Generate task id
        String taskId = UUID.randomUUID().toString();

        // Set snapshot type.
        String snapshotType = BlockSnapshot.TechnologyType.NATIVE.toString();
        if (consistencyGroup.checkForType(BlockConsistencyGroup.Types.RP)) {
            snapshotType = BlockSnapshot.TechnologyType.RP.toString();
        }

        // Determine the snapshot volume for RP.
        Volume snapVolume = null;
        if (consistencyGroup.checkForType(BlockConsistencyGroup.Types.RP)) {
            for (Volume volumeToSnap : volumeList) {
                // Get the RP source volume.
                if (volumeToSnap.getPersonality() != null
                        && volumeToSnap.getPersonality().equals(Volume.PersonalityTypes.SOURCE.toString())) {
                    snapVolume = volumeToSnap;
                    break;
                }
            }
        } else if (!volumeList.isEmpty()) {
            // Any volume.
            snapVolume = volumeList.get(0);
        }

        // Validate the snapshot request.
        String snapshotName = param.getName();
        blockServiceApiImpl.validateCreateSnapshot(snapVolume, volumeList, snapshotType, snapshotName, getFullCopyManager());

        // Set the create inactive flag.
        final Boolean createInactive = param.getCreateInactive() == null ? Boolean.FALSE
                : param.getCreateInactive();

        final Boolean readOnly = param.getReadOnly() == null ? Boolean.FALSE : param.getReadOnly();

        // Prepare and create the snapshots for the group.
        List<URI> snapIdList = new ArrayList<URI>();
        List<BlockSnapshot> snapshotList = new ArrayList<BlockSnapshot>();
        TaskList response = new TaskList();
        snapshotList.addAll(blockServiceApiImpl.prepareSnapshots(
                volumeList, snapshotType, snapshotName, snapIdList, taskId));
        for (BlockSnapshot snapshot : snapshotList) {
            response.getTaskList().add(toTask(snapshot, taskId));
        }

        addConsistencyGroupTask(consistencyGroup, response, taskId,
                ResourceOperationTypeEnum.CREATE_CONSISTENCY_GROUP_SNAPSHOT);

        blockServiceApiImpl.createSnapshot(snapVolume, snapIdList, snapshotType, createInactive, readOnly, taskId);

        auditBlockConsistencyGroup(OperationTypeEnum.CREATE_CONSISTENCY_GROUP_SNAPSHOT,
                AuditLogManager.AUDITLOG_SUCCESS, AuditLogManager.AUDITOP_BEGIN, param.getName(),
                consistencyGroup.getId().toString());

        return response;
    }

    /**
     * List snapshots in the consistency group
     *
     *
     * @prereq none
     *
     * @param consistencyGroupId
     *            - Consistency group URI
     *
     * @brief List snapshots in the consistency group
     * @return The list of snapshots in the consistency group
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/snapshots")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public SnapshotList getConsistencyGroupSnapshots(@PathParam("id") final URI consistencyGroupId) {

        ArgValidator.checkUri(consistencyGroupId);
        final Class<? extends DataObject> clazz = URIUtil.isType(consistencyGroupId,
                BlockSnapshot.class) ? BlockSnapshot.class : BlockConsistencyGroup.class;
        final DataObject consistencyGroup = _permissionsHelper.getObjectById(consistencyGroupId,
                clazz);
        ArgValidator.checkEntityNotNull(consistencyGroup, consistencyGroupId,
                isIdEmbeddedInURL(consistencyGroupId));

        SnapshotList list = new SnapshotList();
        List<URI> snapshotsURIs = new ArrayList<URI>();
        // Find all volumes assigned to the group
        final URIQueryResultList cgSnapshotsResults = new URIQueryResultList();
        _dbClient.queryByConstraint(getBlockSnapshotByConsistencyGroup(consistencyGroupId),
                cgSnapshotsResults);

        if (!cgSnapshotsResults.iterator().hasNext()) {
            return list;
        }
        while (cgSnapshotsResults.iterator().hasNext()) {
            URI snapshot = cgSnapshotsResults.iterator().next();
            snapshotsURIs.add(snapshot);
        }

        List<BlockSnapshot> snapshots = _dbClient.queryObject(BlockSnapshot.class, snapshotsURIs);
        List<NamedRelatedResourceRep> activeSnapshots = new ArrayList<NamedRelatedResourceRep>();
        List<NamedRelatedResourceRep> inactiveSnapshots = new ArrayList<NamedRelatedResourceRep>();
        for (BlockSnapshot snapshot : snapshots) {
            if (snapshot.getInactive()) {
                inactiveSnapshots.add(toNamedRelatedResource(snapshot));
            } else {
                activeSnapshots.add(toNamedRelatedResource(snapshot));
            }
        }

        list.getSnapList().addAll(inactiveSnapshots);
        list.getSnapList().addAll(activeSnapshots);

        return list;
    }

    /**
     * Show the specified Consistency Group Snapshot
     *
     *
     * @prereq none
     *
     * @param consistencyGroupId
     *            - Consistency group URI
     * @param snapshotId
     *            - Consistency group snapshot URI
     *
     * @brief Show consistency group snapshot
     * @return BlockSnapshotRestRep
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/snapshots/{sid}")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public BlockSnapshotRestRep getConsistencyGroupSnapshot(
            @PathParam("id") final URI consistencyGroupId, @PathParam("sid") final URI snapshotId) {
        final BlockConsistencyGroup consistencyGroup = (BlockConsistencyGroup) queryResource(consistencyGroupId);
        final BlockSnapshot snapshot = (BlockSnapshot) queryResource(snapshotId);
        verifySnapshotIsForConsistencyGroup(snapshot, consistencyGroup);
        return BlockMapper.map(_dbClient, snapshot);
    }

    /**
     * Activate the specified Consistency Group Snapshot
     *
     *
     * @prereq Create consistency group snapshot as inactive
     *
     * @param consistencyGroupId
     *            - Consistency group URI
     * @param snapshotId
     *            - Consistency group snapshot URI
     *
     * @brief Activate consistency group snapshot
     * @return TaskResourceRep
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/snapshots/{sid}/activate")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskResourceRep activateConsistencyGroupSnapshot(
            @PathParam("id") final URI consistencyGroupId, @PathParam("sid") final URI snapshotId) {

        Operation op = new Operation();
        op.setResourceType(ResourceOperationTypeEnum.ACTIVATE_CONSISTENCY_GROUP_SNAPSHOT);

        final BlockConsistencyGroup consistencyGroup = (BlockConsistencyGroup) queryResource(consistencyGroupId);
        final BlockSnapshot snapshot = (BlockSnapshot) queryResource(snapshotId);
        verifySnapshotIsForConsistencyGroup(snapshot, consistencyGroup);
        // check for backend CG
        if (BlockConsistencyGroupUtils.getLocalSystemsInCG(consistencyGroup, _dbClient).isEmpty()) {
            _log.error("{} Group Snapshot operations not supported when there is no backend CG", consistencyGroup.getId());
            throw APIException.badRequests.cannotCreateSnapshotOfVplexCG();
        }
        final StorageSystem device = _dbClient.queryObject(StorageSystem.class,
                snapshot.getStorageController());
        final BlockController controller = getController(BlockController.class,
                device.getSystemType());

        final String task = UUID.randomUUID().toString();

        // If the snapshot is already active, there would be no need to queue another request to
        // activate it again.
        if (snapshot.getIsSyncActive()) {
            op.ready();
            op.setMessage("The consistency group snapshot is already active.");
            _dbClient.createTaskOpStatus(BlockSnapshot.class, snapshot.getId(), task, op);
            return toTask(snapshot, task, op);
        }

        _dbClient.createTaskOpStatus(BlockSnapshot.class, snapshot.getId(), task, op);

        try {
            final List<URI> snapshotList = new ArrayList<URI>();
            // Query all the snapshots by snapshot label
            final List<BlockSnapshot> snaps = ControllerUtils.getBlockSnapshotsBySnapsetLabelForProject(snapshot, _dbClient);

            // Build a URI list with all the snapshots ids
            for (BlockSnapshot snap : snaps) {
                snapshotList.add(snap.getId());
            }

            // Activate snapshots
            controller.activateSnapshot(device.getId(), snapshotList, task);

        } catch (final ControllerException e) {
            throw new ServiceCodeException(
                    CONTROLLER_ERROR,
                    e,
                    "An exception occurred when activating consistency group snapshot {0}. Caused by: {1}",
                    new Object[] { snapshotId, e.getMessage() });
        }

        auditBlockConsistencyGroup(OperationTypeEnum.ACTIVATE_CONSISTENCY_GROUP_SNAPSHOT,
                AuditLogManager.AUDITLOG_SUCCESS, AuditLogManager.AUDITOP_BEGIN, snapshot.getId()
                        .toString(), snapshot.getLabel());
        return toTask(snapshot, task, op);
    }

    /**
     * Deactivate the specified Consistency Group Snapshot
     *
     *
     * @prereq none
     *
     * @param consistencyGroupId
     *            - Consistency group URI
     * @param snapshotId
     *            - Consistency group snapshot URI
     *
     * @brief Deactivate consistency group snapshot
     * @return TaskResourceRep
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/snapshots/{sid}/deactivate")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskList deactivateConsistencyGroupSnapshot(
            @PathParam("id") final URI consistencyGroupId, @PathParam("sid") final URI snapshotId) {

        final BlockConsistencyGroup consistencyGroup = (BlockConsistencyGroup) queryResource(consistencyGroupId);

        // Snapshots of RecoverPoint consistency groups is not supported.
        if (consistencyGroup.checkForType(Types.RP)) {
            throw APIException.badRequests.snapshotsNotSupportedForRPCGs();
        }

        // check for backend CG
        if (BlockConsistencyGroupUtils.getLocalSystemsInCG(consistencyGroup, _dbClient).isEmpty()) {
            _log.error("{} Group Snapshot operations not supported when there is no backend CG", consistencyGroup.getId());
            throw APIException.badRequests.cannotCreateSnapshotOfVplexCG();
        }

        final BlockSnapshot snapshot = (BlockSnapshot) queryResource(snapshotId);

        verifySnapshotIsForConsistencyGroup(snapshot, consistencyGroup);

        // Generate task id
        final String task = UUID.randomUUID().toString();
        TaskList response = new TaskList();

        // Not an error if the snapshot we try to delete is already deleted
        if (snapshot.getInactive()) {
            Operation op = new Operation();
            op.ready("The consistency group snapshot has already been deactivated");
            op.setResourceType(ResourceOperationTypeEnum.DELETE_CONSISTENCY_GROUP_SNAPSHOT);
            _dbClient.createTaskOpStatus(BlockSnapshot.class, snapshot.getId(), task, op);
            response.getTaskList().add(toTask(snapshot, task, op));
            return response;
        }

        List<BlockSnapshot> snapshots = new ArrayList<BlockSnapshot>();

        if (snapshot.getConsistencyGroup() != null) {
            // Collect all the BlockSnapshots if part of a CG.
            snapshots = ControllerUtils.getBlockSnapshotsBySnapsetLabelForProject(snapshot, _dbClient);
        }

        for (BlockSnapshot snap : snapshots) {
            Operation snapOp = _dbClient.createTaskOpStatus(BlockSnapshot.class, snap.getId(), task,
                    ResourceOperationTypeEnum.DEACTIVATE_VOLUME_SNAPSHOT);
            response.getTaskList().add(toTask(snap, task, snapOp));
        }

        addConsistencyGroupTask(consistencyGroup, response, task,
                ResourceOperationTypeEnum.DEACTIVATE_CONSISTENCY_GROUP_SNAPSHOT);

        Volume volume = _permissionsHelper.getObjectById(snapshot.getParent(), Volume.class);
        BlockServiceApi blockServiceApiImpl = BlockService.getBlockServiceImpl(volume, _dbClient);

        blockServiceApiImpl.deleteSnapshot(snapshot, task);

        auditBlockConsistencyGroup(OperationTypeEnum.DELETE_CONSISTENCY_GROUP_SNAPSHOT,
                AuditLogManager.AUDITLOG_SUCCESS, AuditLogManager.AUDITOP_BEGIN, snapshot.getId()
                        .toString(), snapshot.getLabel());

        return response;
    }

    /**
     * Restore the specified consistency group snapshot
     *
     *
     * @prereq Activate consistency group snapshot
     *
     * @param consistencyGroupId
     *            - Consistency group URI
     * @param snapshotId
     *            - Consistency group snapshot URI
     *
     * @brief Restore consistency group snapshot
     * @return TaskResourceRep
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/snapshots/{sid}/restore")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskResourceRep restoreConsistencyGroupSnapshot(
            @PathParam("id") final URI consistencyGroupId, @PathParam("sid") final URI snapshotId) {

        // Get the consistency group and snapshot and verify the snapshot
        // is actually associated with the consistency group.
        final BlockConsistencyGroup consistencyGroup = (BlockConsistencyGroup) queryResource(consistencyGroupId);
        final BlockSnapshot snapshot = (BlockSnapshot) queryResource(snapshotId);
        verifySnapshotIsForConsistencyGroup(snapshot, consistencyGroup);

        // check for backend CG
        if (BlockConsistencyGroupUtils.getLocalSystemsInCG(consistencyGroup, _dbClient).isEmpty()) {
            _log.error("{} Group Snapshot operations not supported when there is no backend CG", consistencyGroup.getId());
            throw APIException.badRequests.cannotCreateSnapshotOfVplexCG();
        }

        // Get the parent volume.
        final Volume snapshotParentVolume = _permissionsHelper.getObjectById(snapshot.getParent(), Volume.class);

        // Get the block implementation
        BlockServiceApi blockServiceApiImpl = getBlockServiceImpl(consistencyGroup);

        // Validate the snapshot restore.
        blockServiceApiImpl.validateRestoreSnapshot(snapshot, snapshotParentVolume);

        // Create the restore operation task for the snapshot.
        final String taskId = UUID.randomUUID().toString();
        final Operation op = _dbClient.createTaskOpStatus(BlockSnapshot.class,
                snapshot.getId(), taskId, ResourceOperationTypeEnum.RESTORE_CONSISTENCY_GROUP_SNAPSHOT);

        // Restore the snapshot.
        blockServiceApiImpl.restoreSnapshot(snapshot, snapshotParentVolume, taskId);

        auditBlockConsistencyGroup(OperationTypeEnum.RESTORE_CONSISTENCY_GROUP_SNAPSHOT,
                AuditLogManager.AUDITLOG_SUCCESS, AuditLogManager.AUDITOP_BEGIN,
                snapshotId.toString(), consistencyGroupId.toString(), snapshot.getStorageController().toString());

        return toTask(snapshot, taskId, op);
    }

    /**
     * Resynchronize the specified consistency group snapshot
     *
     *
     * @prereq Activate consistency group snapshot
     *
     * @param consistencyGroupId
     *            - Consistency group URI
     * @param snapshotId
     *            - Consistency group snapshot URI
     *
     * @brief Resynchronize consistency group snapshot
     * @return TaskResourceRep
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/snapshots/{sid}/resynchronize")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskResourceRep resynchronizeConsistencyGroupSnapshot(
            @PathParam("id") final URI consistencyGroupId, @PathParam("sid") final URI snapshotId) {

        // Get the consistency group and snapshot and verify the snapshot
        // is actually associated with the consistency group.
        final BlockConsistencyGroup consistencyGroup = (BlockConsistencyGroup) queryResource(consistencyGroupId);
        final BlockSnapshot snapshot = (BlockSnapshot) queryResource(snapshotId);
        verifySnapshotIsForConsistencyGroup(snapshot, consistencyGroup);

        // check for backend CG
        if (BlockConsistencyGroupUtils.getLocalSystemsInCG(consistencyGroup, _dbClient).isEmpty()) {
            _log.error("{} Group Snapshot operations not supported when there is no backend CG", consistencyGroup.getId());
            throw APIException.badRequests.cannotCreateSnapshotOfVplexCG();
        }

        // Get the storage system for the consistency group.
        StorageSystem storage = _permissionsHelper.getObjectById(consistencyGroup.getStorageController(), StorageSystem.class);

        // resync for OpenStack storage system type is not supported
        if (Type.openstack.name().equalsIgnoreCase(storage.getSystemType())) {
            throw APIException.methodNotAllowed.notSupportedWithReason(
                    String.format("Snapshot resynchronization is not possible on third-party storage systems"));
        }

        // resync for VNX storage system type is not supported
        if (Type.vnxblock.name().equalsIgnoreCase(storage.getSystemType())) {
            throw APIException.methodNotAllowed.notSupportedWithReason(
                    "Snapshot resynchronization is not supported on VNX storage systems");
        }
        // Get the parent volume.
        final Volume snapshotParentVolume = _permissionsHelper.getObjectById(snapshot.getParent(), Volume.class);

        // Get the block implementation
        BlockServiceApi blockServiceApiImpl = getBlockServiceImpl(consistencyGroup);

        // Validate the snapshot restore.
        blockServiceApiImpl.validateResynchronizeSnapshot(snapshot, snapshotParentVolume);

        // Create the restore operation task for the snapshot.
        final String taskId = UUID.randomUUID().toString();
        final Operation op = _dbClient.createTaskOpStatus(BlockSnapshot.class,
                snapshot.getId(), taskId, ResourceOperationTypeEnum.RESYNCHRONIZE_CONSISTENCY_GROUP_SNAPSHOT);

        // Resync the snapshot.
        blockServiceApiImpl.resynchronizeSnapshot(snapshot, snapshotParentVolume, taskId);

        auditBlockConsistencyGroup(OperationTypeEnum.RESTORE_CONSISTENCY_GROUP_SNAPSHOT,
                AuditLogManager.AUDITLOG_SUCCESS, AuditLogManager.AUDITOP_BEGIN,
                snapshotId.toString(), consistencyGroupId.toString(), snapshot.getStorageController().toString());

        return toTask(snapshot, taskId, op);
    }

    /**
     * Record audit log for Block service
     *
     * @param auditType
     *            Type of AuditLog
     * @param operationalStatus
     *            Status of operation
     * @param operationStage
     *            Stage of operation. For sync operation, it should be null; For async operation, it
     *            should be "BEGIN" or "END";
     * @param descparams
     *            Description paramters
     */
    public void auditBlockConsistencyGroup(final OperationTypeEnum auditType,
            final String operationalStatus, final String operationStage, final Object... descparams) {

        _auditMgr.recordAuditLog(URI.create(getUserFromContext().getTenantId()),
                URI.create(getUserFromContext().getName()), "block", auditType,
                System.currentTimeMillis(), operationalStatus, operationStage, descparams);
    }

    private void verifyUserIsAuthorizedForRequest(final Project project) {
        StorageOSUser user = getUserFromContext();
        if (!(_permissionsHelper.userHasGivenRole(user, project.getTenantOrg().getURI(),
                Role.TENANT_ADMIN) || _permissionsHelper.userHasGivenACL(user, project.getId(),
                ACL.OWN, ACL.ALL))) {
            throw APIException.forbidden.insufficientPermissionsForUser(user.getName());
        }
    }

    /**
     * Block consistency group is not a zone level resource
     *
     * @return false
     */
    @Override
    protected boolean isZoneLevelResource() {
        return false;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.BLOCK_CONSISTENCY_GROUP;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<BlockConsistencyGroup> getResourceClass() {
        return BlockConsistencyGroup.class;
    }

    /**
     * Retrieve volume representations based on input ids.
     *
     * @return list of volume representations.
     *
     * @throws DatabaseException
     *             When an error occurs querying the database.
     */
    @Override
    public BlockConsistencyGroupBulkRep queryBulkResourceReps(final List<URI> ids) {
        Iterator<BlockConsistencyGroup> _dbIterator = _dbClient.queryIterativeObjects(
                getResourceClass(), ids);
        return new BlockConsistencyGroupBulkRep(BulkList.wrapping(_dbIterator,
                MapBlockConsistencyGroup.getInstance(_dbClient)));
    }

    @Override
    protected BulkRestRep queryFilteredBulkResourceReps(final List<URI> ids) {
        Iterator<BlockConsistencyGroup> _dbIterator = _dbClient.queryIterativeObjects(
                getResourceClass(), ids);
        BulkList.ResourceFilter<BlockConsistencyGroup> filter = new BulkList.ProjectResourceFilter<BlockConsistencyGroup>(
                getUserFromContext(), _permissionsHelper);
        return new BlockConsistencyGroupBulkRep(BulkList.wrapping(_dbIterator,
                MapBlockConsistencyGroup.getInstance(_dbClient), filter));
    }

    /**
     * Get search results by name in zone or project.
     *
     * @return SearchedResRepList
     */
    @Override
    protected SearchedResRepList getNamedSearchResults(final String name, final URI projectId) {
        SearchedResRepList resRepList = new SearchedResRepList(getResourceType());
        if (projectId == null) {
            _dbClient.queryByConstraint(
                    PrefixConstraint.Factory.getLabelPrefixConstraint(getResourceClass(), name),
                    resRepList);
        } else {
            _dbClient.queryByConstraint(ContainmentPrefixConstraint.Factory
                    .getConsistencyGroupUnderProjectConstraint(projectId, name), resRepList);
        }
        return resRepList;
    }

    /**
     * Get search results by project alone.
     *
     * @return SearchedResRepList
     */
    @Override
    protected SearchedResRepList getProjectSearchResults(final URI projectId) {
        SearchedResRepList resRepList = new SearchedResRepList(getResourceType());
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getProjectBlockConsistencyGroupConstraint(projectId),
                resRepList);
        return resRepList;
    }

    /**
     * Get object specific permissions filter
     *
     */
    @Override
    protected ResRepFilter<? extends RelatedResourceRep> getPermissionFilter(
            final StorageOSUser user, final PermissionsHelper permissionsHelper) {
        return new ProjOwnedResRepFilter(user, permissionsHelper, BlockConsistencyGroup.class);
    }

    /**
     * Update the specified consistency group
     *
     *
     * @prereq none
     *
     * @param id the URN of a ViPR Consistency group
     *
     * @brief Update consistency group
     * @return TaskResourceRep
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskResourceRep updateConsistencyGroup(@PathParam("id") final URI id,
            final BlockConsistencyGroupUpdate param) {
        // Get the consistency group.
        BlockConsistencyGroup consistencyGroup = (BlockConsistencyGroup) queryResource(id);

        // Verify a volume was specified to be added or removed.
        if (!param.hasEitherAddOrRemoveVolumes()) {
            throw APIException.badRequests.noVolumesToBeAddedRemovedFromCG();
        }

        // cannot update RP consistency groups
        if (consistencyGroup.getTypes().contains(Types.RP.toString())) {
            throw APIException.badRequests.notAllowedOnRPConsistencyGroups();
        }

        // TODO require a check if requested list contains all volumes/replicas?
        // For replicas, check replica count with volume count in CG

        StorageSystem cgStorageSystem = null;

        // if consistency group is not created yet, then get the storage system from the block object to be added
        // This method also supports adding volumes or replicas to CG (VMAX - SMIS 8.0.x)
        if (!consistencyGroup.created()
                && param.hasVolumesToAdd()) { // we just need to check the case of add volumes in this case
            BlockObject bo = BlockObject.fetch(_dbClient, param.getAddVolumesList().getVolumes().get(0));
            cgStorageSystem = _permissionsHelper.getObjectById(
                    bo.getStorageController(), StorageSystem.class);
        } else {
            // Get the storage system for the consistency group.
            cgStorageSystem = _permissionsHelper.getObjectById(
                    consistencyGroup.getStorageController(), StorageSystem.class);
        }

        // VPlex, VNX, ScaleIO, and VMax volumes only
        String systemType = cgStorageSystem.getSystemType();
        if (!systemType.equals(DiscoveredDataObject.Type.vplex.name())
                && !systemType.equals(DiscoveredDataObject.Type.vnxblock.name())
                && !systemType.equals(DiscoveredDataObject.Type.vmax.name())
                && !systemType.equals(DiscoveredDataObject.Type.vnxe.name())
                && !systemType.equals(DiscoveredDataObject.Type.ibmxiv.name())
                && !systemType.equals(DiscoveredDataObject.Type.scaleio.name())
                && !systemType.equals(DiscoveredDataObject.Type.xtremio.name())) {
            throw APIException.methodNotAllowed.notSupported();
        }

        // Get the specific BlockServiceApiImpl based on the storage system type.
        BlockServiceApi blockServiceApiImpl = getBlockServiceImpl(cgStorageSystem);

        List<URI> volIds = null;
        Set<URI> addSet = new HashSet<URI>();
        boolean isReplica = true;
        if (param.hasVolumesToAdd()) {
            volIds = param.getAddVolumesList().getVolumes();
            addSet.addAll(volIds);
            URI volId = volIds.get(0);
            if (URIUtil.isType(volId, Volume.class)) {
                Volume volume = _permissionsHelper.getObjectById(volId, Volume.class);
                ArgValidator.checkEntity(volume, volId, false);
                if (!BlockFullCopyUtils.isVolumeFullCopy(volume, _dbClient)) {
                    isReplica = false;
                }
            }
        }

        List<Volume> cgVolumes = blockServiceApiImpl.getActiveCGVolumes(consistencyGroup);
        // check if add volume list is same as existing volumes in CG
        boolean volsAlreadyInCG = false;
        if (!isReplica && cgVolumes != null && !cgVolumes.isEmpty()) {
            Collection<URI> cgVolIds = transform(cgVolumes, fctnDataObjectToID());
            if (addSet.size() == cgVolIds.size()) {
                volsAlreadyInCG = addSet.containsAll(cgVolIds);
            }
        }

        // Verify that the add and remove lists do not contain the same volume.
        if (param.hasBothAddAndRemoveVolumes()) {
            /*
             * Make sure the add and remove lists are unique by getting the intersection and
             * verifying the size is 0.
             */
            Set<URI> removeSet = new HashSet<URI>(param.getRemoveVolumesList().getVolumes());
            addSet.retainAll(removeSet);
            if (!addSet.isEmpty()) {
                throw APIException.badRequests.sameVolumesInAddRemoveList();
            }
        }

        if (param.hasVolumesToRemove() || (!isReplica && !volsAlreadyInCG)) {
            // CG cannot have replicas when adding/removing volumes to/from CG
            // Check snapshots
            // Adding/removing volumes to/from a consistency group
            // is not supported when the consistency group has active
            // snapshots.
            URIQueryResultList cgSnapshotsResults = new URIQueryResultList();
            _dbClient.queryByConstraint(getBlockSnapshotByConsistencyGroup(id), cgSnapshotsResults);
            Iterator<URI> cgSnapshotsIter = cgSnapshotsResults.iterator();
            while (cgSnapshotsIter.hasNext()) {
                BlockSnapshot cgSnapshot = _dbClient.queryObject(BlockSnapshot.class, cgSnapshotsIter.next());
                if ((cgSnapshot != null) && (!cgSnapshot.getInactive())) {
                    throw APIException.badRequests.notAllowedWhenCGHasSnapshots();
                }
            }

            // Check mirrors
            // Adding/removing volumes to/from a consistency group
            // is not supported when existing volumes in CG have mirrors.
            if (cgVolumes != null && !cgVolumes.isEmpty()) {
                Volume firstVolume = cgVolumes.get(0);
                StringSet mirrors = firstVolume.getMirrors();
                if (mirrors != null && !mirrors.isEmpty()) {
                    throw APIException.badRequests.notAllowedWhenCGHasMirrors();
                }
            }

            // Check clones
            // Adding/removing volumes to/from a consistency group
            // is not supported when the consistency group has
            // volumes with full copies to which they are still
            // attached or has volumes that are full copies that
            // are still attached to their source volumes.
            getFullCopyManager().verifyConsistencyGroupCanBeUpdated(consistencyGroup,
                    cgVolumes);
        }

        // Verify the volumes to be removed.
        List<URI> removeVolumesList = new ArrayList<URI>();
        if (param.hasVolumesToRemove()) {
            for (URI volumeURI : param.getRemoveVolumesList().getVolumes()) {
                // Validate the volume to be removed exists.
                if (URIUtil.isType(volumeURI, Volume.class)) {
                    Volume volume = _permissionsHelper.getObjectById(volumeURI, Volume.class);
                    ArgValidator.checkEntity(volume, volumeURI, false);
                    /**
                     * Remove SRDF volume from CG is not supported.
                     */
                    if (volume.checkForSRDF()) {
                        throw APIException.badRequests.notAllowedOnSRDFConsistencyGroups();
                    }
                    if (!BlockFullCopyUtils.isVolumeFullCopy(volume, _dbClient)) {
                        blockServiceApiImpl.verifyRemoveVolumeFromCG(volume, cgVolumes);
                    }
                }
                removeVolumesList.add(volumeURI);
            }
        }

        URI xivPoolURI = null;
        if (systemType.equals(DiscoveredDataObject.Type.ibmxiv.name()) && !cgVolumes.isEmpty()) {
            Volume firstVolume = cgVolumes.get(0);
            xivPoolURI = firstVolume.getPool();
        }

        // Verify the volumes to be added.
        List<URI> addVolumesList = new ArrayList<URI>();
        List<Volume> volumes = new ArrayList<Volume>();
        if (param.hasVolumesToAdd()) {
            for (URI volumeURI : param.getAddVolumesList().getVolumes()) {
                // Validate the volume to be added exists.
                Volume volume = null;
                if (!isReplica) {
                    volume = _permissionsHelper.getObjectById(volumeURI, Volume.class);
                    ArgValidator.checkEntity(volume, volumeURI, false);
                    blockServiceApiImpl.verifyAddVolumeToCG(volume, consistencyGroup,
                            cgVolumes, cgStorageSystem);
                    volumes.add(volume);
                } else {
                    verifyAddReplicaToCG(volumeURI, consistencyGroup, cgStorageSystem);
                }

                // IBM XIV specific checking
                if (systemType.equals(DiscoveredDataObject.Type.ibmxiv.name())) {
                    // all volumes should be on the same storage pool
                    if (xivPoolURI == null) {
                        xivPoolURI = volume.getPool();
                    } else {
                        if (!xivPoolURI.equals(volume.getPool())) {
                            throw APIException.badRequests
                                    .invalidParameterIBMXIVConsistencyGroupVolumeNotInPool(volumeURI, xivPoolURI);
                        }
                    }
                }

                // Add the volume to list.
                addVolumesList.add(volumeURI);
            }

            if (!volumes.isEmpty()) {
                blockServiceApiImpl.verifyReplicaCount(volumes, cgVolumes, volsAlreadyInCG);
            }
        }

        // Create the task id;
        String taskId = UUID.randomUUID().toString();

        // Call the block service API to update the consistency group.
        return blockServiceApiImpl.updateConsistencyGroup(cgStorageSystem, cgVolumes,
                consistencyGroup, addVolumesList, removeVolumesList, taskId);
    }

    /**
     * Validates the replicas to be added to Consistency group.
     * - verifies that the replicas are not internal objects,
     * - checks if the given CG is its source volume's CG,
     * - validates that the replica is not in any other CG,
     * - verifies the project for the replicas to be added is same
     * as the project for the consistency group.
     */
    private void verifyAddReplicaToCG(URI blockURI, BlockConsistencyGroup cg,
            StorageSystem cgStorageSystem) {
        BlockObject blockObject = BlockObject.fetch(_dbClient, blockURI);

        // Don't allow partially ingested object to be added to CG.
        BlockServiceUtils.validateNotAnInternalBlockObject(blockObject, false);

        URI sourceVolumeURI = null;
        URI blockProjectURI = null;
        if (blockObject instanceof BlockSnapshot) {
            BlockSnapshot snapshot = (BlockSnapshot) blockObject;
            blockProjectURI = snapshot.getProject().getURI();
            sourceVolumeURI = snapshot.getParent().getURI();
        } else if (blockObject instanceof BlockMirror) {
            BlockMirror mirror = (BlockMirror) blockObject;
            blockProjectURI = mirror.getProject().getURI();
            sourceVolumeURI = mirror.getSource().getURI();
        } else if (blockObject instanceof Volume) {
            Volume volume = (Volume) blockObject;
            blockProjectURI = volume.getProject().getURI();
            sourceVolumeURI = volume.getAssociatedSourceVolume();
        }

        // check if the given CG is its source volume's CG
        Volume sourceVolume = null;
        if (!NullColumnValueGetter.isNullURI(sourceVolumeURI)) {
            sourceVolume = _dbClient.queryObject(Volume.class, sourceVolumeURI);
        }
        if (sourceVolume == null
                || !cg.getId().equals(sourceVolume.getConsistencyGroup())) {
            throw APIException.badRequests
                    .invalidParameterSourceVolumeNotInGivenConsistencyGroup(
                            sourceVolumeURI, cg.getId());
        }

        // Validate that the replica is not in any other CG.
        if (!NullColumnValueGetter.isNullURI(blockObject.getConsistencyGroup())
                && !cg.getId().equals(blockObject.getConsistencyGroup())) {
            throw APIException.badRequests
                    .invalidParameterVolumeAlreadyInAConsistencyGroup(
                            cg.getId(), blockObject.getConsistencyGroup());
        }

        // Verify the project for the replicas to be added is same
        // as the project for the consistency group.
        URI cgProjectURI = cg.getProject().getURI();
        if (!blockProjectURI.equals(cgProjectURI)) {
            List<Project> projects = _dbClient.queryObjectField(Project.class,
                    "label", Arrays.asList(cgProjectURI, blockProjectURI));
            throw APIException.badRequests
                    .consistencyGroupAddVolumeThatIsInDifferentProject(
                            blockObject.getLabel(), projects.get(0).getLabel(),
                            projects.get(1).getLabel());
        }
    }

    /**
     * For APIs that act on a snapshot for a consistency group, ensures that
     * the passed snapshot is associated with the passed consistency group, else
     * throws a bad request exception.
     *
     * @param snapshot A reference to a snapshot
     * @param consistencyGroup A reference to a consistency group
     */
    private void verifySnapshotIsForConsistencyGroup(BlockSnapshot snapshot,
            BlockConsistencyGroup consistencyGroup) {
        URI snapshotCGURI = snapshot.getConsistencyGroup();
        if ((NullColumnValueGetter.isNullURI(snapshotCGURI)) || (!snapshotCGURI.equals(consistencyGroup.getId()))) {
            throw APIException.badRequests.snapshotIsNotForConsistencyGroup(
                    snapshot.getLabel(), consistencyGroup.getLabel());
        }
    }

    /**
     * Simply return a task that indicates that the operation completed.
     *
     * @param consistencyGroup [in] BlockConsistencyGroup object
     * @param task [in] - Operation task ID
     * @return
     */
    private TaskResourceRep finishDeactivateTask(BlockConsistencyGroup consistencyGroup, String task) {
        URI id = consistencyGroup.getId();
        Operation op = new Operation();
        op.ready();
        op.setProgress(100);
        op.setResourceType(ResourceOperationTypeEnum.DELETE_CONSISTENCY_GROUP);
        Operation status = _dbClient.createTaskOpStatus(BlockConsistencyGroup.class, id, task, op);
        return toTask(consistencyGroup, task, status);
    }

    /**
     * Creates a consistency group full copy.
     *
     * @prereq none
     *
     * @param cgURI The URI of the consistency group.
     * @param param The request data specifying the parameters for the request.
     *
     * @brief Create consistency group full copy.
     *
     * @return TaskList
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/full-copies")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN }, acls = { ACL.ANY })
    public TaskList createConsistencyGroupFullCopy(@PathParam("id") URI cgURI,
            VolumeFullCopyCreateParam param) {
        // Verify the consistency group in the requests and get the
        // volumes in the consistency group.
        List<Volume> cgVolumes = verifyCGForFullCopyRequest(cgURI);

        // Grab the first volume and call the block full copy
        // manager to create the full copies for the volumes
        // in the CG. Note that it will take into account the
        // fact that the volume is in a CG.
        return getFullCopyManager().createFullCopy(cgVolumes.get(0).getId(), param);
    }

    /**
     * Activate the specified consistency group full copy.
     *
     * @prereq Create consistency group full copy as inactive.
     *
     * @param cgURI The URI of the consistency group.
     * @param fullCopyURI The URI of the full copy.
     *
     * @brief Activate consistency group full copy.
     *
     * @return TaskList
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/full-copies/{fcid}/activate")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskList activateConsistencyGroupFullCopy(
            @PathParam("id") URI cgURI, @PathParam("fcid") URI fullCopyURI) {
        // Verify the consistency group in the request and get the
        // volumes in the consistency group.
        List<Volume> cgVolumes = verifyCGForFullCopyRequest(cgURI);

        // Verify the full copy.
        URI fcSourceURI = verifyFullCopyForCopyRequest(fullCopyURI, cgVolumes);

        // Activate the full copy. Note that it will take into account the
        // fact that the volume is in a CG.
        return getFullCopyManager().activateFullCopy(fcSourceURI, fullCopyURI);
    }

    /**
     * Detach the specified consistency group full copy.
     *
     * @prereq Create consistency group full copy as active.
     *
     * @param cgURI The URI of the consistency group.
     * @param fullCopyURI The URI of the full copy.
     *
     * @brief Detach consistency group full copy.
     *
     * @return TaskList
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/full-copies/{fcid}/detach")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskList detachConsistencyGroupFullCopy(
            @PathParam("id") URI cgURI, @PathParam("fcid") URI fullCopyURI) {
        // Verify the consistency group in the request and get the
        // volumes in the consistency group.
        List<Volume> cgVolumes = verifyCGForFullCopyRequest(cgURI);

        // Get the full copy source.
        Volume fullCopyVolume = (Volume) BlockFullCopyUtils.queryFullCopyResource(
                fullCopyURI, uriInfo, false, _dbClient);
        URI fcSourceURI = fullCopyVolume.getAssociatedSourceVolume();
        if (!NullColumnValueGetter.isNullURI(fcSourceURI)) {
            verifyFullCopyForCopyRequest(fullCopyURI, cgVolumes);
        }
        // Detach the full copy. Note that it will take into account the
        // fact that the volume is in a CG.
        return getFullCopyManager().detachFullCopy(fcSourceURI, fullCopyURI);
    }

    /**
     * Restore the specified consistency group full copy.
     *
     * @prereq Create consistency group full copy as active.
     *
     * @param cgURI The URI of the consistency group.
     * @param fullCopyURI The URI of the full copy.
     *
     * @brief Restore consistency group full copy.
     *
     * @return TaskList
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/full-copies/{fcid}/restore")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskList restoreConsistencyGroupFullCopy(
            @PathParam("id") URI cgURI, @PathParam("fcid") URI fullCopyURI) {
        // Verify the consistency group in the request and get the
        // volumes in the consistency group.
        List<Volume> cgVolumes = verifyCGForFullCopyRequest(cgURI);

        // Verify the full copy.
        URI fcSourceURI = verifyFullCopyForCopyRequest(fullCopyURI, cgVolumes);

        // Restore the full copy. Note that it will take into account the
        // fact that the volume is in a CG.
        return getFullCopyManager().restoreFullCopy(fcSourceURI, fullCopyURI);
    }

    /**
     * Resynchronize the specified consistency group full copy.
     *
     * @prereq Create consistency group full copy as active.
     *
     * @param cgURI The URI of the consistency group.
     * @param fullCopyURI The URI of the full copy.
     *
     * @brief Resynchronize consistency group full copy.
     *
     * @return TaskList
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/full-copies/{fcid}/resynchronize")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskList resynchronizeConsistencyGroupFullCopy(
            @PathParam("id") URI cgURI, @PathParam("fcid") URI fullCopyURI) {
        // Verify the consistency group in the request and get the
        // volumes in the consistency group.
        List<Volume> cgVolumes = verifyCGForFullCopyRequest(cgURI);

        // Verify the full copy.
        URI fcSourceURI = verifyFullCopyForCopyRequest(fullCopyURI, cgVolumes);

        // Resynchronize the full copy. Note that it will take into account the
        // fact that the volume is in a CG.
        return getFullCopyManager().resynchronizeFullCopy(fcSourceURI, fullCopyURI);
    }

    /**
     * Deactivate the specified consistency group full copy.
     *
     * @prereq none
     *
     * @param cgURI The URI of the consistency group.
     * @param fullCopyURI The URI of the full copy.
     *
     * @brief Deactivate consistency group full copy.
     *
     * @return TaskList
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/full-copies/{fcid}/deactivate")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskList deactivateConsistencyGroupFullCopy(@PathParam("id") URI cgURI,
            @PathParam("fcid") URI fullCopyURI) {

        // Verify the consistency group in the request and get the
        // volumes in the consistency group.
        List<Volume> cgVolumes = verifyCGForFullCopyRequest(cgURI);

        // Verify the full copy.
        verifyFullCopyForCopyRequest(fullCopyURI, cgVolumes);

        // Full copies are volumes, and volumes are deleted by the
        // block service. The block service deactivate method will
        // ensure that full copies in CGs results in all associated
        // being deleted under appropriate conditions.
        BulkDeleteParam param = new BulkDeleteParam();
        param.setIds(Arrays.asList(fullCopyURI));
        return _blockService.deleteVolumes(param, false, VolumeDeleteTypeEnum.FULL.name());
    }

    /**
     * List full copies for a consistency group
     *
     * @prereq none
     *
     * @param cgURI The URI of the consistency group.
     *
     * @brief List full copies for a consistency group
     *
     * @return The list of full copies for the consistency group
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/full-copies")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public NamedVolumesList getConsistencyGroupFullCopies(@PathParam("id") URI cgURI) {
        // Verify the consistency group in the request and get the
        // volumes in the consistency group.
        List<Volume> cgVolumes = verifyCGForFullCopyRequest(cgURI);

        // Cycle over the volumes in the consistency group and
        // get the full copies for each volume in the group.
        NamedVolumesList cgFullCopyList = new NamedVolumesList();
        for (Volume cgVolume : cgVolumes) {
            NamedVolumesList cgVolumeFullCopies = getFullCopyManager().getFullCopiesForSource(cgVolume.getId());
            cgFullCopyList.getVolumes().addAll(cgVolumeFullCopies.getVolumes());
        }

        return cgFullCopyList;
    }

    /**
     * Get the specified consistency group full copy.
     *
     * @prereq none
     *
     * @param cgURI The URI of the consistency group.
     * @param fullCopyURI The URI of the full copy.
     *
     * @brief Get the specified consistency group full copy.
     *
     * @return The full copy volume.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/full-copies/{fcid}")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public VolumeRestRep getConsistencyGroupFullCopy(@PathParam("id") URI cgURI,
            @PathParam("fcid") URI fullCopyURI) {
        // Verify the consistency group in the request and get the
        // volumes in the consistency group.
        List<Volume> cgVolumes = verifyCGForFullCopyRequest(cgURI);

        // Verify the full copy.
        verifyFullCopyForCopyRequest(fullCopyURI, cgVolumes);

        // Get and return the full copy.
        return getFullCopyManager().getFullCopy(fullCopyURI);
    }

    /**
     * Request to reverse the replication direction, i.e. R1 and R2 are interchanged.
     *
     * @prereq none
     *
     * @param id the URI of a BlockConsistencyGroup
     * @param param Copy to swap
     *
     * @brief reversing roles of source and target
     * @return TaskList
     *
     * @throws ControllerException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/continuous-copies/swap")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskList swap(@PathParam("id") URI id, CopiesParam param) throws ControllerException {
        TaskResourceRep taskResp = null;
        TaskList taskList = new TaskList();
        // Validate the source volume URI
        ArgValidator.checkFieldUriType(id, BlockConsistencyGroup.class, "id");
        // Validate the list of copies
        ArgValidator.checkFieldNotEmpty(param.getCopies(), "copies");

        // Query Consistency Group
        final BlockConsistencyGroup consistencyGroup = (BlockConsistencyGroup) queryResource(id);

        // Ensure that the Consistency Group has been created on all of its defined
        // system types.
        if (!consistencyGroup.created()) {
            throw APIException.badRequests.consistencyGroupNotCreated();
        }

        List<Copy> copies = param.getCopies();
        if (copies.size() > 1) {
            throw APIException.badRequests.swapCopiesParamCanOnlyBeOne();
        }
        Copy copy = copies.get(0);
        ArgValidator.checkFieldUriType(copy.getCopyID(), VirtualArray.class, "copyId");
        ArgValidator.checkFieldNotEmpty(copy.getType(), "type");

        if (TechnologyType.RP.name().equalsIgnoreCase(copy.getType())) {
            taskResp = performProtectionAction(id, copy.getCopyID(), ProtectionOp.SWAP.getRestOp());
            taskList.getTaskList().add(taskResp);
        } else if (TechnologyType.SRDF.name().equalsIgnoreCase(copy.getType())) {
            taskResp = performSRDFProtectionAction(id, copy, ProtectionOp.SWAP.getRestOp());
            taskList.getTaskList().add(taskResp);
        } else {
            throw APIException.badRequests.invalidCopyType(copy.getType());
        }
        return taskList;
    }

    /**
     *
     * Request to failover the protection link associated with the copy. The target
     * copy is specified by identifying the virtual array in param.copyId.
     *
     * NOTE: This is an asynchronous operation.
     *
     * If volume is srdf protected, then invoking failover internally triggers
     * SRDF SWAP on volume pairs.
     *
     * @prereq none
     *
     * @param id the URI of a BlockConsistencyGroup
     * @param param Copy to failover to
     *
     * @brief Failover the protection link
     * @return TaskList
     *
     * @throws ControllerException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/continuous-copies/failover")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskList failoverProtection(@PathParam("id") URI id, CopiesParam param) throws ControllerException {

        TaskResourceRep taskResp = null;
        TaskList taskList = new TaskList();

        // Validate the source volume URI
        ArgValidator.checkFieldUriType(id, BlockConsistencyGroup.class, "id");
        // Validate the list of copies
        ArgValidator.checkFieldNotEmpty(param.getCopies(), "copies");

        // Query Consistency Group
        final BlockConsistencyGroup consistencyGroup = (BlockConsistencyGroup) queryResource(id);

        // Ensure that the Consistency Group has been created on all of its defined
        // system types.
        if (!consistencyGroup.created()) {
            throw APIException.badRequests.consistencyGroupNotCreated();
        }

        List<Copy> copies = param.getCopies();
        if (copies.size() > 1) {
            throw APIException.badRequests.failoverCopiesParamCanOnlyBeOne();
        }
        Copy copy = copies.get(0);
        ArgValidator.checkFieldUriType(copy.getCopyID(), VirtualArray.class, "copyId");
        ArgValidator.checkFieldNotEmpty(copy.getType(), "type");

        if (TechnologyType.RP.name().equalsIgnoreCase(copy.getType())) {
            taskResp = performProtectionAction(id, copy.getCopyID(), ProtectionOp.FAILOVER.getRestOp());
            taskList.getTaskList().add(taskResp);
        } else if (TechnologyType.SRDF.name().equalsIgnoreCase(copy.getType())) {
            taskResp = performSRDFProtectionAction(id, copy, ProtectionOp.FAILOVER.getRestOp());
            taskList.getTaskList().add(taskResp);
        } else {
            throw APIException.badRequests.invalidCopyType(copy.getType());
        }

        return taskList;
    }

    /**
     * Request to cancel fail over on already failed over consistency group.
     *
     * @prereq none
     *
     * @param id the URI of the BlockConsistencyGroup.
     * @param param Copy to fail back
     *
     * @brief fail back to source again
     * @return TaskList
     *
     * @throws ControllerException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/continuous-copies/failover-cancel")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskList failoverCancel(@PathParam("id") URI id, CopiesParam param) throws ControllerException {
        TaskResourceRep taskResp = null;
        TaskList taskList = new TaskList();
        // Validate the source volume URI
        ArgValidator.checkFieldUriType(id, BlockConsistencyGroup.class, "id");
        // Validate the list of copies
        ArgValidator.checkFieldNotEmpty(param.getCopies(), "copies");

        // Query Consistency Group
        final BlockConsistencyGroup consistencyGroup = (BlockConsistencyGroup) queryResource(id);

        // Ensure that the Consistency Group has been created on all of its defined
        // system types.
        if (!consistencyGroup.created()) {
            throw APIException.badRequests.consistencyGroupNotCreated();
        }

        List<Copy> copies = param.getCopies();
        if (copies.size() > 1) {
            throw APIException.badRequests.failOverCancelCopiesParamCanOnlyBeOne();
        }
        Copy copy = copies.get(0);
        ArgValidator.checkFieldUriType(copy.getCopyID(), VirtualArray.class, "copyId");
        ArgValidator.checkFieldNotEmpty(copy.getType(), "type");

        if (TechnologyType.RP.name().equalsIgnoreCase(copy.getType())) {
            taskResp = performProtectionAction(id, copy.getCopyID(), ProtectionOp.FAILOVER_CANCEL.getRestOp());
            taskList.getTaskList().add(taskResp);
        } else if (TechnologyType.SRDF.name().equalsIgnoreCase(copy.getType())) {
            taskResp = performSRDFProtectionAction(id, copy, ProtectionOp.FAILOVER_CANCEL.getRestOp());
            taskList.getTaskList().add(taskResp);
        } else {
            throw APIException.badRequests.invalidCopyType(copy.getType());
        }
        return taskList;
    }

    /**
     * Since all of the protection operations are very similar, this method does all of the work.
     * We keep the actual REST methods separate mostly for the purpose of documentation generators.
     *
     * @param consistencyGroupId the URI of the BlockConsistencyGroup to perform the protection action against.
     * @param targetVarrayId the target virtual array.
     * @param op operation to perform (pause, stop, failover, etc)
     * @return task resource rep
     * @throws InternalException
     */
    private TaskResourceRep performProtectionAction(URI consistencyGroupId, URI targetVarrayId, String op) throws InternalException {
        ArgValidator.checkFieldUriType(consistencyGroupId, BlockConsistencyGroup.class, "id");
        ArgValidator.checkFieldUriType(targetVarrayId, VirtualArray.class, "copyId");

        // Get the BlockConsistencyGroup and target VirtualArray associated with the request.
        final BlockConsistencyGroup consistencyGroup = (BlockConsistencyGroup) queryResource(consistencyGroupId);
        final VirtualArray targetVirtualArray = _permissionsHelper.getObjectById(targetVarrayId, VirtualArray.class);

        ArgValidator.checkEntity(consistencyGroup, consistencyGroupId, true);
        ArgValidator.checkEntity(targetVirtualArray, targetVarrayId, true);

        // The consistency group needs to be associated with RecoverPoint in order to perform the operation.
        if (!consistencyGroup.checkForType(Types.RP)) {
            // Attempt to do protection link management on unprotected CG
            throw APIException.badRequests.consistencyGroupMustBeRPProtected(consistencyGroupId);
        }

        // Verify that the supplied target Virtual Array is being referenced by at least one target volume in the CG.
        List<Volume> targetVolumes = getTargetVolumes(consistencyGroup, targetVarrayId);

        if (targetVolumes == null || targetVolumes.isEmpty()) {
            // The supplied target varray is not referenced by any target volumes in the CG.
            throw APIException.badRequests.targetVirtualArrayDoesNotMatch(consistencyGroupId, targetVarrayId);
        }

        // Get the first target volume
        Volume targetVolume = targetVolumes.get(0);

        String task = UUID.randomUUID().toString();
        Operation status = new Operation();
        status.setResourceType(ProtectionOp.getResourceOperationTypeEnum(op));
        _dbClient.createTaskOpStatus(BlockConsistencyGroup.class, consistencyGroupId, task, status);

        ProtectionSystem system = _dbClient.queryObject(ProtectionSystem.class,
                targetVolume.getProtectionController());
        String deviceType = system.getSystemType();

        if (!deviceType.equals(DiscoveredDataObject.Type.rp.name())) {
            throw APIException.badRequests.protectionForRpClusters();
        }

        RPController controller = getController(RPController.class, system.getSystemType());

        controller.performProtectionOperation(system.getId(), consistencyGroupId, targetVolume.getId(), op, task);
        /*
         * auditOp(OperationTypeEnum.PERFORM_PROTECTION_ACTION, true, AuditLogManager.AUDITOP_BEGIN,
         * op, copyID.toString(), id.toString(), system.getId().toString());
         */
        return toTask(consistencyGroup, task, status);
    }

    /**
     * Performs the SRDF Protection operation.
     *
     * @param consistencyGroupId the URI of the BlockConsistencyGroup to perform the protection action against.
     * @param copy the copy to operate on
     * @param op operation to perform (pause, stop, failover, etc)
     * @return task resource rep
     * @throws InternalException
     */
    private TaskResourceRep performSRDFProtectionAction(URI consistencyGroupId, Copy copy, String op)
            throws InternalException {
        URI targetVarrayId = copy.getCopyID();
        ArgValidator.checkFieldUriType(targetVarrayId, VirtualArray.class, "copyID");
        ArgValidator.checkFieldUriType(consistencyGroupId, BlockConsistencyGroup.class, "id");

        // Get the BlockConsistencyGroup and target VirtualArray associated with the request.
        final BlockConsistencyGroup consistencyGroup = (BlockConsistencyGroup) queryResource(consistencyGroupId);
        final VirtualArray targetVirtualArray = _permissionsHelper.getObjectById(targetVarrayId, VirtualArray.class);

        ArgValidator.checkEntity(consistencyGroup, consistencyGroupId, true);
        ArgValidator.checkEntity(targetVirtualArray, targetVarrayId, true);

        // The consistency group needs to be associated with SRDF in order to perform the operation.
        if (!consistencyGroup.checkForType(Types.SRDF)) {
            // Attempting to perform an SRDF operation on a non-SRDF consistency group
            throw APIException.badRequests.consistencyGroupMustBeSRDFProtected(consistencyGroupId);
        }

        // Get the block service implementation
        BlockServiceApi blockServiceApiImpl = getBlockServiceImpl(consistencyGroup);

        // Get a list of CG volumes.
        List<Volume> volumeList = blockServiceApiImpl.getActiveCGVolumes(consistencyGroup);

        if (volumeList == null || volumeList.isEmpty()) {
            throw APIException.badRequests.consistencyGroupContainsNoVolumes(consistencyGroup.getId());
        }

        Volume srcVolume = null;
        // Find a source volume in the SRDF local CG
        for (Volume volume : volumeList) {
            if (volume.getPersonality() != null &&
                    volume.getPersonality().equals(PersonalityTypes.SOURCE.name())) {
                srcVolume = volume;
                break;
            }
        }

        if (srcVolume == null) {
            // CG contains no source volumes.
            throw APIException.badRequests.srdfCgContainsNoSourceVolumes(consistencyGroup.getId());
        }

        // Find the target volume that corresponds to the source whose Virtual Array matches
        // the specified target Virtual Array. From that, obtain the remote SRDF CG.
        BlockConsistencyGroup targetCg = null;
        if (srcVolume.getSrdfTargets() != null && !srcVolume.getSrdfTargets().isEmpty()) {
            for (String uri : srcVolume.getSrdfTargets()) {
                Volume target = _dbClient.queryObject(Volume.class, URI.create(uri));
                if (target.getVirtualArray().equals(targetVarrayId)) {
                    targetCg = _dbClient.queryObject(BlockConsistencyGroup.class, target.getConsistencyGroup());
                    break;
                }
            }
        }

        // Get all target CG target volumes for validation
        List<Volume> targetVolumes = getTargetVolumes(targetCg, targetVarrayId);

        for (Volume tgtVolume : targetVolumes) {
            if (!Volume.isSRDFProtectedTargetVolume(tgtVolume)) {
                // All target volumes matching specified target virtual array must be SRDF
                // protected.
                throw APIException.badRequests.volumeMustBeSRDFProtected(tgtVolume.getId());
            }
        }

        if (targetVolumes == null || targetVolumes.isEmpty()) {
            // The supplied target varray is not referenced by any target volumes in the CG.
            throw APIException.badRequests.targetVirtualArrayDoesNotMatch(targetCg.getId(), targetVarrayId);
        }

        // Get the first volume
        Volume targetVolume = targetVolumes.get(0);

        String task = UUID.randomUUID().toString();
        Operation status = new Operation();
        status.setResourceType(ProtectionOp.getResourceOperationTypeEnum(op));
        _dbClient.createTaskOpStatus(Volume.class, targetVolume.getId(), task, status);

        if (op.equalsIgnoreCase(ProtectionOp.FAILOVER_TEST_CANCEL.getRestOp()) ||
                op.equalsIgnoreCase(ProtectionOp.FAILOVER_TEST.getRestOp())) {
            _dbClient.ready(BlockConsistencyGroup.class, consistencyGroupId, task);
            // Task is associated to the first target volume we find in the target CG.
            // TODO: Task should reference the BlockConsistencyGroup. This requires several
            // changes to the SRDF protection completers to handle both volumes and
            // consistency groups.
            return toTask(targetVolume, task, status);
        }

        /*
         * CTRL-6972: In the absence of a /restore API, we re-use /sync with a syncDirection parameter for
         * specifying either SMI-S Resume or Restore:
         * SOURCE_TO_TARGET -> ViPR Resume -> SMI-S Resume -> SRDF Incremental Establish (R1 overwrites R2)
         * TARGET_TO_SOURCE -> ViPR Sync -> SMI-S Restore -> SRDF Full Restore (R2 overwrites R1)
         */
        if (op.equalsIgnoreCase(ProtectionOp.SYNC.getRestOp()) &&
                SOURCE_TO_TARGET.toString().equalsIgnoreCase(copy.getSyncDirection())) {
            op = ProtectionOp.RESUME.getRestOp();
        } else if (BlockService.isSuspendCopyRequest(op, copy)) {
            op = ProtectionOp.SUSPEND.getRestOp();
        }

        StorageSystem system = _dbClient.queryObject(StorageSystem.class,
                targetVolume.getStorageController());
        SRDFController controller = getController(SRDFController.class,
                system.getSystemType());

        // Create a new duplicate copy of the original copy. Update the copyId field to be the
        // ID of the target volume. Existing SRDF controller logic needs the target volume
        // to operate off of, not the virtual array.
        Copy updatedCopy = new Copy(copy.getType(), copy.getSync(), targetVolume.getId(),
                copy.getName(), copy.getCount());

        controller.performProtectionOperation(system.getId(), updatedCopy, op, task);

        return toTask(targetVolume, task, status);
    }

    /**
     * Gets all target volumes from the given consistency group that references the specified
     * virtual array.
     *
     * @param consistencyGroup the consistency group.
     * @param targetVarrayId the target virtual array.
     * @return a list of matching target volumes from the consistency group.
     */
    private List<Volume> getTargetVolumes(BlockConsistencyGroup consistencyGroup, URI targetVarrayId) {
        List<Volume> targetVolumes = new ArrayList<Volume>();

        if (consistencyGroup != null && targetVarrayId != null) {
            // Get the block service implementation
            BlockServiceApi blockServiceApiImpl = getBlockServiceImpl(consistencyGroup);

            // Get a list of CG volumes.
            List<Volume> volumeList = blockServiceApiImpl.getActiveCGVolumes(consistencyGroup);

            if (volumeList == null || volumeList.isEmpty()) {
                throw APIException.badRequests.consistencyGroupContainsNoVolumes(consistencyGroup.getId());
            }

            // Find all target volumes in the CG that match the specified target virtual array.
            for (Volume volume : volumeList) {
                if (volume.getPersonality() != null &&
                        volume.getPersonality().equals(PersonalityTypes.TARGET.name()) &&
                        volume.getVirtualArray() != null && volume.getVirtualArray().equals(targetVarrayId)) {
                    targetVolumes.add(volume);
                }
            }

            if (targetVolumes.isEmpty()) {
                _log.info(String
                        .format("Unable to find any target volumes in consistency group %s.  There are no target volumes matching target virtual array %s.",
                                consistencyGroup.getId(), targetVarrayId));
            }
        }

        return targetVolumes;
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
     * Verifies the CG passe din the request is valid and contains volumes.
     *
     * @param cgURI The URI of a consistency group.
     *
     * @return The volumes in the consistency group.
     */
    private List<Volume> verifyCGForFullCopyRequest(URI cgURI) {
        // Query Consistency Group.
        BlockConsistencyGroup consistencyGroup = (BlockConsistencyGroup) queryResource(cgURI);

        // Ensure that the Consistency Group has been created on all of its
        // defined system types.
        if (!consistencyGroup.created()) {
            throw APIException.badRequests.consistencyGroupNotCreated();
        }

        // Get the block service implementation.
        BlockServiceApi blockServiceApiImpl = getBlockServiceImpl(consistencyGroup);

        // Get the volumes in the consistency group.
        List<Volume> cgVolumes = blockServiceApiImpl.getActiveCGVolumes(consistencyGroup);
        if (cgVolumes.isEmpty()) {
            throw APIException.badRequests
                    .fullCopyOperationNotAllowedOnEmptyCG(consistencyGroup.getLabel());
        }

        return cgVolumes;
    }

    /**
     * Verifies that the passed full copy URI and ensure that it
     * represents a full copy for a volume in the passed list of
     * volumes, which are the volumes for a specific consistency
     * group.
     *
     * @param fullCopyURI The URI of a full copy volume.
     * @param cgVolumes The list of volumes in a consistency group.
     *
     * @return The URI of the full copy source.
     */
    private URI verifyFullCopyForCopyRequest(URI fullCopyURI, List<Volume> cgVolumes) {
        // Get the full copy source.
        Volume fullCopyVolume = (Volume) BlockFullCopyUtils.queryFullCopyResource(
                fullCopyURI, uriInfo, true, _dbClient);
        URI fcSourceURI = fullCopyVolume.getAssociatedSourceVolume();
        if (NullColumnValueGetter.isNullURI(fcSourceURI)) {
            throw APIException.badRequests
                    .fullCopyOperationNotAllowedNotAFullCopy(fullCopyVolume.getLabel());
        }

        // Verify the source is in the consistency group.
        boolean sourceInCG = false;
        for (Volume cgVolume : cgVolumes) {
            if (cgVolume.getId().equals(fcSourceURI)) {
                sourceInCG = true;
                break;
            }
        }
        if (!sourceInCG) {
            throw APIException.badRequests
                    .fullCopyOperationNotAllowedSourceNotInCG(fullCopyVolume.getLabel());
        }
        return fcSourceURI;
    }

    /**
     * Creates tasks against consistency groups associated with a request and adds them to the given task list.
     *
     * @param group
     * @param taskList
     * @param taskId
     * @param operationTypeEnum
     */
    protected void addConsistencyGroupTask(BlockConsistencyGroup group, TaskList taskList,
            String taskId,
            ResourceOperationTypeEnum operationTypeEnum) {
        Operation op = _dbClient.createTaskOpStatus(BlockConsistencyGroup.class, group.getId(), taskId,
                operationTypeEnum);
        taskList.getTaskList().add(TaskMapper.toTask(group, taskId, op));
    }
}
