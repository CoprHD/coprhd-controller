/*
 * Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.BlockMapper.map;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.BlockMapper.toMigrationResource;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;
import static com.emc.storageos.db.client.constraint.ContainmentConstraint.Factory.getBlockSnapshotByConsistencyGroup;
import static com.emc.storageos.db.client.constraint.ContainmentConstraint.Factory.getVolumesByConsistencyGroup;
import static com.emc.storageos.db.client.util.CommonTransformerFunctions.FCTN_STRING_TO_URI;
import static com.emc.storageos.db.client.util.CommonTransformerFunctions.fctnDataObjectToID;
import static com.emc.storageos.model.block.Copy.SyncDirection.SOURCE_TO_TARGET;
import static com.emc.storageos.svcs.errorhandling.resources.ServiceCode.CONTROLLER_ERROR;
import static com.google.common.collect.Collections2.transform;
import static com.google.common.collect.Lists.newArrayList;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
import com.emc.storageos.api.service.impl.resource.migration.MigrationServiceApi;
import com.emc.storageos.api.service.impl.resource.snapshot.BlockSnapshotSessionManager;
import com.emc.storageos.api.service.impl.resource.snapshot.BlockSnapshotSessionUtils;
import com.emc.storageos.api.service.impl.resource.utils.BlockServiceUtils;
import com.emc.storageos.api.service.impl.resource.utils.ExportUtils;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.api.service.impl.response.ProjOwnedResRepFilter;
import com.emc.storageos.api.service.impl.response.ResRepFilter;
import com.emc.storageos.api.service.impl.response.SearchedResRepList;
import com.emc.storageos.computecontroller.HostRescanController;
import com.emc.storageos.db.client.DbClient;
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
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DataCollectionJobStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.ExportPathParams;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Migration;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.PersonalityTypes;
import com.emc.storageos.db.client.model.VolumeGroup;
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
import com.emc.storageos.model.block.BlockSnapshotSessionList;
import com.emc.storageos.model.block.BulkDeleteParam;
import com.emc.storageos.model.block.CopiesParam;
import com.emc.storageos.model.block.Copy;
import com.emc.storageos.model.block.MigrationCreateParam;
import com.emc.storageos.model.block.MigrationList;
import com.emc.storageos.model.block.MigrationZoneCreateParam;
import com.emc.storageos.model.block.NamedVolumesList;
import com.emc.storageos.model.block.SnapshotSessionCreateParam;
import com.emc.storageos.model.block.SnapshotSessionLinkTargetsParam;
import com.emc.storageos.model.block.SnapshotSessionRelinkTargetsParam;
import com.emc.storageos.model.block.SnapshotSessionUnlinkTargetParam;
import com.emc.storageos.model.block.SnapshotSessionUnlinkTargetsParam;
import com.emc.storageos.model.block.VolumeDeleteTypeEnum;
import com.emc.storageos.model.block.VolumeFullCopyCreateParam;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.networkcontroller.NetworkController;
import com.emc.storageos.protectioncontroller.RPController;
import com.emc.storageos.protectionorchestrationcontroller.ProtectionOrchestrationController;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.services.util.StorageDriverManager;
import com.emc.storageos.services.util.TimeUtils;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCodeException;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.util.VPlexUtil;
import com.emc.storageos.volumecontroller.BlockController;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.smis.SRDFOperations.Mode;
import com.emc.storageos.volumecontroller.placement.BlockStorageScheduler;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;

@Path("/block/consistency-groups")
@DefaultPermissions(readRoles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, readAcls = { ACL.OWN,
        ACL.ALL }, writeRoles = { Role.TENANT_ADMIN }, writeAcls = { ACL.OWN, ACL.ALL })
public class BlockConsistencyGroupService extends TaskResourceService {

    private static final String BLOCKSERVICEAPIIMPL_GROUP = "group";
    private static final Logger _log = LoggerFactory.getLogger(BlockConsistencyGroupService.class);
    private static final int CG_MAX_LIMIT = 64;
    private static final String FULL_COPY = "Full copy";
    private static final String ID_FIELD = "id";

    // A reference to the placement manager.
    private PlacementManager _placementManager;

    // A reference to the block service.
    private BlockService _blockService;

    // Block service implementations
    private Map<String, BlockServiceApi> _blockServiceApis;

    // Migration service implementations
    private Map<String, MigrationServiceApi> migrationServiceApis;

    private static volatile BlockStorageScheduler _blockStorageScheduler;

    public void setBlockStorageScheduler(BlockStorageScheduler blockStorageScheduler) {
        if (_blockStorageScheduler == null) {
            _blockStorageScheduler = blockStorageScheduler;
        }

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

    /**
     * Setter for the Migration BlockServiceApi called through Spring configuration.
     *
     * @param migrationServiceApis A reference to the migrationServiceApis.
     */
    public void setMigrationServiceApis(Map<String, MigrationServiceApi> migrationServiceApis) {
        this.migrationServiceApis = migrationServiceApis;
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
            blockServiceApiImpl = getBlockServiceImpl(BLOCKSERVICEAPIIMPL_GROUP);
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
            blockServiceApiImpl = getBlockServiceImpl(BLOCKSERVICEAPIIMPL_GROUP);
        }
        return blockServiceApiImpl;
    }

    private MigrationServiceApi getMigrationServiceImpl(String type) {
        return migrationServiceApis.get(type);
    }

    /**
     * Get the specific MigrationServiceApiImpl based on the storage system type.
     *
     * @param system The storage system instance
     * @return MigrationServiceApiImpl for the storage system type.
     */
    private MigrationServiceApi getMigrationServiceImpl(BlockConsistencyGroup cg) {
        StorageSystem system = _permissionsHelper.getObjectById(cg.getStorageController(),
                StorageSystem.class);
        return getMigrationServiceImpl(system.getSystemType());
    }

    @Override
    protected DataObject queryResource(final URI id) {
        ArgValidator.checkUri(id);

        final Class<? extends DataObject> clazz;
        if (URIUtil.isType(id, BlockSnapshotSession.class)) {
            clazz = BlockSnapshotSession.class;
        } else if (URIUtil.isType(id, BlockSnapshot.class)) {
            clazz = BlockSnapshot.class;
        } else {
            clazz = BlockConsistencyGroup.class;
        }

        final DataObject resource = _permissionsHelper.getObjectById(id, clazz);
        ArgValidator.checkEntityNotNull(resource, id, isIdEmbeddedInURL(id));
        return resource;
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

        ArgValidator.checkIsAlphaNumeric(param.getName());

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
        consistencyGroup.setProject(new NamedURI(project.getId(), project.getLabel()));
        consistencyGroup.setTenant(project.getTenantOrg());
        // disable array consistency if user has selected not to create backend replication group
        consistencyGroup.setArrayConsistency(param.getArrayConsistency());

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
        List<Volume> volumes = BlockConsistencyGroupUtils.getActiveVolumesInCG(consistencyGroup,
                _dbClient, null);

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
    public TaskResourceRep deleteConsistencyGroup(@PathParam("id") final URI id,
            @DefaultValue("FULL") @QueryParam("type") String type) throws InternalException {
        // Query for the given consistency group and verify it is valid.
        final BlockConsistencyGroup consistencyGroup = (BlockConsistencyGroup) queryResource(id);
        // We can ignore dependencies on Migration as it will also be marked for deletion along with CG.
        List<Class<? extends DataObject>> excludeTypes = null;
        if (VolumeDeleteTypeEnum.VIPR_ONLY.name().equals(type)) {
            excludeTypes = new ArrayList<Class<? extends DataObject>>();
            excludeTypes.add(Migration.class);
        }
        ArgValidator.checkReference(BlockConsistencyGroup.class, id, checkForDelete(consistencyGroup, excludeTypes));

        // Create a unique task identifier.
        String task = UUID.randomUUID().toString();

        // If the consistency group is inactive, has yet to be created on
        // a storage system, or this is a ViPR Only delete, then the deletion
        // is not controller specific. We essentially just mark the CG for
        // deletion. Note that the CG may be uncreated, but in the process of
        // being created, which means that volumes would reference the CG.
        // So, we do need to verify that no volumes reference the CG.
        if (deletingUncreatedConsistencyGroup(consistencyGroup) ||
                VolumeDeleteTypeEnum.VIPR_ONLY.name().equals(type)) {
            // If the CG is of MIGRATION type, delete the associated Migration object too.
            markCGMigrationForDeletion(consistencyGroup);
            markCGForDeletion(consistencyGroup);
            return finishDeactivateTask(consistencyGroup, task);
        }

        // Otherwise, we need to clean up the array consistency groups.
        TaskResourceRep taskRep = null;
        try {
            List<StorageSystem> vplexSystems = BlockConsistencyGroupUtils.getVPlexStorageSystems(consistencyGroup, _dbClient);
            if (!vplexSystems.isEmpty()) {
                // If there is a VPLEX system, then we simply call the VPLEX controller which
                // will delete all VPLEX CGS on all VPLEX systems, and also all local CGs on
                // all local systems.
                BlockServiceApi blockServiceApi = getBlockServiceImpl(DiscoveredDataObject.Type.vplex.name());
                taskRep = blockServiceApi.deleteConsistencyGroup(vplexSystems.get(0), consistencyGroup, task);
            } else {
                // Otherwise, we call the block controller to delete the local CGs on all local systems.
                List<URI> localSystemURIs = BlockConsistencyGroupUtils.getLocalSystems(consistencyGroup, _dbClient);
                if (!localSystemURIs.isEmpty()) {
                    boolean foundSystem = false;
                    for (URI localSystemURI : localSystemURIs) {
                        StorageSystem localSystem = _dbClient.queryObject(StorageSystem.class, localSystemURI);
                        if (localSystem != null) {
                            foundSystem = true;
                            BlockServiceApi blockServiceApi = getBlockServiceImpl(BLOCKSERVICEAPIIMPL_GROUP);
                            taskRep = blockServiceApi.deleteConsistencyGroup(localSystem, consistencyGroup, task);
                            if (Task.Status.error.name().equals(taskRep.getState())) {
                                break;
                            }
                        } else {
                            _log.warn("Local system {} for consistency group {} does not exist",
                                    localSystemURI, consistencyGroup.getLabel());
                        }
                    }

                    // Check to make sure we found at least one of these local systems.
                    if (!foundSystem) {
                        // For some reason we have a CG with local systems, but none of them
                        // are in the database. In this case, we will log a warning and mark
                        // it for deletion.
                        _log.warn("Deleting created consistency group {} where none of the local systems for the group exist",
                                consistencyGroup.getLabel());
                        markCGForDeletion(consistencyGroup);
                        return finishDeactivateTask(consistencyGroup, task);
                    }
                } else {
                    // For some reason the CG has no VPLEX or local systems but is
                    // marked as being active and created. In this case, we will log
                    // a warning and mark it for deletion.
                    _log.info("Deleting created consistency group {} with no local or VPLEX systems", consistencyGroup.getLabel());
                    markCGForDeletion(consistencyGroup);
                    return finishDeactivateTask(consistencyGroup, task);
                }
            }
        } catch (APIException | InternalException e) {
            String errorMsg = String.format("Exception attempting to delete consistency group %s: %s", consistencyGroup.getLabel(),
                    e.getMessage());
            _log.error(errorMsg);
            taskRep.setState(Operation.Status.error.name());
            taskRep.setMessage(errorMsg);
            _dbClient.error(BlockConsistencyGroup.class, taskRep.getResource().getId(), task, e);
        } catch (Exception e) {
            String errorMsg = String.format("Exception attempting to delete consistency group %s: %s", consistencyGroup.getLabel(),
                    e.getMessage());
            _log.error(errorMsg);
            APIException apie = APIException.internalServerErrors.genericApisvcError(errorMsg, e);
            taskRep.setState(Operation.Status.error.name());
            taskRep.setMessage(apie.getMessage());
            _dbClient.error(BlockConsistencyGroup.class, taskRep.getResource().getId(), task, apie);
        }

        // Make sure that the CG is marked for deletion if
        // the request was successful.
        if (Task.Status.ready.name().equals(taskRep.getState())) {
            markCGForDeletion(consistencyGroup);
        }

        return taskRep;
    }

    /**
     * Update the CG so it is deleted.
     *
     * @param consistencyGroup A reference to the consistency group.
     */
    private void markCGForDeletion(BlockConsistencyGroup consistencyGroup) {
        if (!consistencyGroup.getInactive()) {
            consistencyGroup.setStorageController(NullColumnValueGetter.getNullURI());
            consistencyGroup.setInactive(true);
            _dbClient.updateObject(consistencyGroup);
        }
    }

    /**
     * If the CG is of MIGRATION type, mark the associated migration objects for deletion.
     *
     * @param consistencyGroup the consistency group
     */
    private void markCGMigrationForDeletion(BlockConsistencyGroup consistencyGroup) {
        if (consistencyGroup.getTypes().contains(Types.MIGRATION.name())) {
            URIQueryResultList migrationURIs = new URIQueryResultList();
            _dbClient.queryByConstraint(
                    ContainmentConstraint.Factory.getMigrationConsistencyGroupConstraint(consistencyGroup.getId()), migrationURIs);
            Iterator<URI> migrationURIsIter = migrationURIs.iterator();
            while (migrationURIsIter.hasNext()) {
                URI migrationURI = migrationURIsIter.next();
                Migration migration = _permissionsHelper.getObjectById(migrationURI, Migration.class);
                migration.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                migration.setSourceSystem(NullColumnValueGetter.getNullURI());
                migration.setInactive(true);
                _dbClient.updateObject(migration);
            }
        }
    }

    /**
     * Check to see if the consistency group is active and not created. In
     * this case we can delete the consistency group. Otherwise we should
     * not delete the consistency group. Note if VPLEX CG has been created,
     * we should call the VPlexConsistencyGroupManager to remove it.
     *
     * @param consistencyGroup
     *            A reference to the CG.
     *
     * @return True if the CG is active and not created.
     */
    private boolean deletingUncreatedConsistencyGroup(
            final BlockConsistencyGroup consistencyGroup) {
        // If the consistency group is active and not created we can delete it,
        // otherwise we cannot.
        return (!consistencyGroup.getInactive() && !consistencyGroup.created()
                && !consistencyGroup.getTypes().contains(BlockConsistencyGroup.Types.VPLEX.name()));
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
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/snapshots")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN }, acls = { ACL.ANY })
    public TaskList createConsistencyGroupSnapshot(@PathParam("id") final URI consistencyGroupId,
            final BlockConsistencyGroupSnapshotCreate param) {
        ArgValidator.checkFieldUriType(consistencyGroupId, BlockConsistencyGroup.class, "id");

        // Query Consistency Group
        final BlockConsistencyGroup consistencyGroup = (BlockConsistencyGroup) queryResource(consistencyGroupId);

        // Ensure that the Consistency Group has been created on all of its defined
        // system types.
        if (!consistencyGroup.created()) {
            throw APIException.badRequests.consistencyGroupNotCreated();
        }

        // RP CG's must use applications to create snapshots
        if (isIdEmbeddedInURL(consistencyGroupId) && consistencyGroup.checkForType(Types.RP)) {
            throw APIException.badRequests.snapshotsNotSupportedForRPCGs();
        }

        // Validate CG information in the request
        validateVolumesInReplicationGroups(consistencyGroup, param.getVolumes(), _dbClient);

        // Get the block service implementation
        BlockServiceApi blockServiceApiImpl = getBlockServiceImpl(consistencyGroup);

        Table<URI, String, List<Volume>> storageRgToVolumes = null;
        if (!param.getVolumes().isEmpty()) {
            // Volume group snapshot
            // group volumes by backend storage system and replication group
            storageRgToVolumes = BlockServiceUtils.getReplicationGroupVolumes(param.getVolumes(), consistencyGroupId, _dbClient, uriInfo);
        } else {
            // CG snapshot
            storageRgToVolumes = BlockServiceUtils.getReplicationGroupVolumes(
                    blockServiceApiImpl.getActiveCGVolumes(consistencyGroup), _dbClient);
        }

        // Validation replication group volumes to ensure there aren't mixed meta and non-meta devices
        validateReplicationGroupDevices(storageRgToVolumes);

        TaskList taskList = new TaskList();
        for (Cell<URI, String, List<Volume>> cell : storageRgToVolumes.cellSet()) {
            List<Volume> volumeList = cell.getValue();
            if (volumeList == null || volumeList.isEmpty()) {
                _log.warn(String.format("No volume in replication group %s", cell.getColumnKey()));
                continue;
            }

            // Generate task id
            String taskId = UUID.randomUUID().toString();
            // Set snapshot type.
            String snapshotType = BlockSnapshot.TechnologyType.NATIVE.toString();
            // Validate the snapshot request.
            String snapshotName = TimeUtils.formatDateForCurrent(param.getName());
            // Set the read only flag.
            final Boolean readOnly = param.getReadOnly() == null ? Boolean.FALSE : param.getReadOnly();
            // Set the create inactive flag.
            final Boolean createInactive = param.getCreateInactive() == null ? Boolean.FALSE
                    : param.getCreateInactive();

            blockServiceApiImpl.validateCreateSnapshot(volumeList.get(0), volumeList, snapshotType, snapshotName, readOnly,
                    getFullCopyManager());

            // Prepare and create the snapshots for the group.
            List<URI> snapIdList = new ArrayList<URI>();
            List<BlockSnapshot> snapshotList = new ArrayList<BlockSnapshot>();
            snapshotList.addAll(blockServiceApiImpl.prepareSnapshots(
                    volumeList, snapshotType, snapshotName, snapIdList, taskId));
            for (BlockSnapshot snapshot : snapshotList) {
                taskList.getTaskList().add(toTask(snapshot, taskId));
            }

            addConsistencyGroupTask(consistencyGroup, taskList, taskId,
                    ResourceOperationTypeEnum.CREATE_CONSISTENCY_GROUP_SNAPSHOT);
            try {
                blockServiceApiImpl.createSnapshot(volumeList.get(0), snapIdList, snapshotType, createInactive, readOnly, taskId);
                auditBlockConsistencyGroup(OperationTypeEnum.CREATE_CONSISTENCY_GROUP_SNAPSHOT,
                        AuditLogManager.AUDITLOG_SUCCESS, AuditLogManager.AUDITOP_BEGIN, param.getName(),
                        consistencyGroup.getId().toString());
            } catch (Exception ex) {
                _log.error("Unexpected Exception occurred when creating snapshot for replication group {}",
                        cell.getColumnKey(), ex);
            }
        }

        return taskList;
    }

    /**
     * Validates the volumes in each replication group to ensure there aren't mixed meta and
     * non-meta volumes.
     *
     * @param storageRgToVolumes the list of volumes by storage system and replication group.
     */
    private void validateReplicationGroupDevices(Table<URI, String, List<Volume>> storageRgToVolumes) {
        for (Cell<URI, String, List<Volume>> cell : storageRgToVolumes.cellSet()) {
            List<Volume> volumeList = cell.getValue();
            boolean metaDevices = false;
            boolean nonMetaDevices = false;

            for (Volume vol : volumeList) {
                if (vol.getMetaMemberCount() != null && vol.getMetaMemberCount() > 0) {
                    metaDevices = true;
                } else {
                    nonMetaDevices = true;
                }
            }

            if (metaDevices && nonMetaDevices) {
                throw APIException.badRequests.cgSnapshotNotAllowedMixedDevices(cell.getColumnKey());
            }
        }
    }

    /**
     * Validate the volumes we are requested to snap all contain the proper replication group instance information.
     *
     * @param consistencyGroup consistency group object
     * @param volumes incoming request parameters
     * @param dbClient dbclient
     */
    private void validateVolumesInReplicationGroups(BlockConsistencyGroup consistencyGroup, List<URI> volumes,
            DbClient dbClient) {

        // Get all of the volumes from the consistency group
        Iterator<Volume> volumeIterator = null;
        if (volumes == null || volumes.isEmpty()) {
            URIQueryResultList uriQueryResultList = new URIQueryResultList();
            dbClient.queryByConstraint(getVolumesByConsistencyGroup(consistencyGroup.getId()), uriQueryResultList);
            volumeIterator = dbClient.queryIterativeObjects(Volume.class, uriQueryResultList);
        } else {
            volumeIterator = dbClient.queryIterativeObjects(Volume.class, volumes);
        }

        if (volumeIterator == null || !volumeIterator.hasNext()) {
            throw APIException.badRequests.cgReplicationNotAllowedMissingReplicationGroupNoVols(consistencyGroup.getLabel());
        }

        while (volumeIterator.hasNext()) {
            Volume volume = volumeIterator.next();
            if (volume.getInactive()) {
                continue;
            }
            // Ignore RP journal volume in this validation
            if (NullColumnValueGetter.isNullValue(volume.getPersonality())
                    || !Volume.PersonalityTypes.METADATA.name().equalsIgnoreCase(volume.getPersonality())) {
                // If it's a VPLEX volume, check both backing volumes to make sure they have replication group instance set
                if (volume.isVPlexVolume(dbClient)) {
                    Volume backendVolume = VPlexUtil.getVPLEXBackendVolume(volume, false, dbClient);
                    if (backendVolume != null && NullColumnValueGetter.isNullValue(backendVolume.getReplicationGroupInstance())) {
                        // Ignore HA volumes not in a consistency group if a CG is specified; no snap sessions on HA side
                        if (consistencyGroup != null && !NullColumnValueGetter.isNullURI(backendVolume.getConsistencyGroup())) {
                            throw APIException.badRequests.cgReplicationNotAllowedMissingReplicationGroup(backendVolume.getLabel());
                        }
                    }
                    backendVolume = VPlexUtil.getVPLEXBackendVolume(volume, true, dbClient);
                    if (backendVolume != null && NullColumnValueGetter.isNullValue(backendVolume.getReplicationGroupInstance())) {
                        throw APIException.badRequests.cgReplicationNotAllowedMissingReplicationGroup(backendVolume.getLabel());
                    }
                } else {
                    // Non-VPLEX, just check for replication group instance
                    if (NullColumnValueGetter.isNullValue(volume.getReplicationGroupInstance())) {
                        throw APIException.badRequests.cgReplicationNotAllowedMissingReplicationGroup(volume.getLabel());
                    }
                }
            }

        }
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

        List<Volume> volumes = ControllerUtils.getVolumesPartOfCG(consistencyGroupId, _dbClient);

        // if any of the source volumes are in an application, replica management must be done via the application
        for (Volume srcVol : volumes) {
            if (srcVol.getApplication(_dbClient) != null) {
                return new SnapshotList();
            }
        }

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
     * List snapshot sessions in the consistency group
     *
     *
     * @prereq none
     *
     * @param consistencyGroupId
     *            - Consistency group URI
     *
     * @brief List snapshot sessions in the consistency group
     * @return The list of snapshot sessions in the consistency group
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/snapshot-sessions")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public BlockSnapshotSessionList getConsistencyGroupSnapshotSessions(@PathParam("id") final URI consistencyGroupId) {
        ArgValidator.checkUri(consistencyGroupId);
        BlockConsistencyGroup consistencyGroup = _permissionsHelper.getObjectById(consistencyGroupId,
                BlockConsistencyGroup.class);
        ArgValidator.checkEntityNotNull(consistencyGroup, consistencyGroupId,
                isIdEmbeddedInURL(consistencyGroupId));

        return getSnapshotSessionManager().getSnapshotSessionsForConsistencyGroup(consistencyGroup);
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
            throw APIException.badRequests.cannotCreateSnapshotOfCG();
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
            final List<BlockSnapshot> snaps = ControllerUtils.getSnapshotsPartOfReplicationGroup(snapshot, _dbClient);

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
                        .toString(),
                snapshot.getLabel());
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
     * @brief Deactivate consistency group snapshot session
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
        if (isIdEmbeddedInURL(consistencyGroupId) && consistencyGroup.checkForType(Types.RP)) {
            throw APIException.badRequests.snapshotsNotSupportedForRPCGs();
        }

        // check for backend CG
        if (BlockConsistencyGroupUtils.getLocalSystemsInCG(consistencyGroup, _dbClient).isEmpty()) {
            _log.error("{} Group Snapshot operations not supported when there is no backend CG", consistencyGroup.getId());
            throw APIException.badRequests.cannotCreateSnapshotOfCG();
        }

        final BlockSnapshot snapshot = (BlockSnapshot) queryResource(snapshotId);

        verifySnapshotIsForConsistencyGroup(snapshot, consistencyGroup);

        // We can ignore dependencies on BlockSnapshotSession. In this case
        // the BlockSnapshot instance is a linked target for a BlockSnapshotSession
        // and we will unlink the snapshot from the session and delete it.
        List<Class<? extends DataObject>> excludeTypes = new ArrayList<Class<? extends DataObject>>();
        excludeTypes.add(BlockSnapshotSession.class);
        ArgValidator.checkReference(BlockSnapshot.class, snapshotId, checkForDelete(snapshot, excludeTypes));

        // Snapshot session linked targets must be unlinked instead.
        BlockSnapshotSession session = BlockSnapshotSessionUtils.getLinkedTargetSnapshotSession(snapshot, _dbClient);
        if (session != null) {
            return deactivateAndUnlinkTargetVolumesForSession(session, snapshot);
        }

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

        snapshots = ControllerUtils.getSnapshotsPartOfReplicationGroup(snapshot, _dbClient);

        // Get the snapshot parent volume.
        Volume parentVolume = _permissionsHelper.getObjectById(snapshot.getParent(), Volume.class);

        // Check that there are no pending tasks for these snapshots.
        checkForPendingTasks(Arrays.asList(parentVolume.getTenant().getURI()), snapshots);

        for (BlockSnapshot snap : snapshots) {
            Operation snapOp = _dbClient.createTaskOpStatus(BlockSnapshot.class, snap.getId(), task,
                    ResourceOperationTypeEnum.DEACTIVATE_VOLUME_SNAPSHOT);
            response.getTaskList().add(toTask(snap, task, snapOp));
        }

        addConsistencyGroupTask(consistencyGroup, response, task,
                ResourceOperationTypeEnum.DEACTIVATE_CONSISTENCY_GROUP_SNAPSHOT);

        try {
            BlockServiceApi blockServiceApiImpl = BlockService.getBlockServiceImpl(parentVolume, _dbClient);
            blockServiceApiImpl.deleteSnapshot(snapshot, snapshots, task, VolumeDeleteTypeEnum.FULL.name());
        } catch (APIException | InternalException e) {
            String errorMsg = String.format("Exception attempting to delete snapshot %s: %s", snapshot.getId(), e.getMessage());
            _log.error(errorMsg);
            for (TaskResourceRep taskResourceRep : response.getTaskList()) {
                taskResourceRep.setState(Operation.Status.error.name());
                taskResourceRep.setMessage(errorMsg);
                @SuppressWarnings({ "unchecked" })
                Class<? extends DataObject> clazz = URIUtil
                        .getModelClass(taskResourceRep.getResource().getId());
                _dbClient.error(clazz, taskResourceRep.getResource().getId(), task, e);
            }
            throw e;
        } catch (Exception e) {
            String errorMsg = String.format("Exception attempting to delete snapshot %s: %s", snapshot.getId(), e.getMessage());
            _log.error(errorMsg);
            APIException apie = APIException.internalServerErrors.genericApisvcError(errorMsg, e);
            for (TaskResourceRep taskResourceRep : response.getTaskList()) {
                taskResourceRep.setState(Operation.Status.error.name());
                taskResourceRep.setMessage(apie.getMessage());
                @SuppressWarnings("unchecked")
                Class<? extends DataObject> clazz = URIUtil
                        .getModelClass(taskResourceRep.getResource().getId());
                _dbClient.error(clazz, taskResourceRep.getResource().getId(), task, apie);
            }
            throw apie;
        }

        auditBlockConsistencyGroup(OperationTypeEnum.DELETE_CONSISTENCY_GROUP_SNAPSHOT,
                AuditLogManager.AUDITLOG_SUCCESS, AuditLogManager.AUDITOP_BEGIN, snapshot.getId()
                        .toString(),
                snapshot.getLabel());

        return response;
    }

    /**
     * Deactivate the specified Consistency Group Snapshot
     *
     *
     * @prereq none
     *
     * @param consistencyGroupId
     *            - Consistency group URI
     * @param snapshotSessionId
     *            - Consistency group snapshot URI
     *
     * @brief Deactivate consistency group snapshot
     * @return TaskResourceRep
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/snapshot-sessions/{sid}/deactivate")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskList deactivateConsistencyGroupSnapshotSession(
            @PathParam("id") final URI consistencyGroupId, @PathParam("sid") final URI snapshotSessionId) {
        return getSnapshotSessionManager().deleteSnapshotSession(snapshotSessionId, VolumeDeleteTypeEnum.FULL.name());
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
            throw APIException.badRequests.cannotCreateSnapshotOfCG();
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
        blockServiceApiImpl.restoreSnapshot(snapshot, snapshotParentVolume, null, taskId);

        auditBlockConsistencyGroup(OperationTypeEnum.RESTORE_CONSISTENCY_GROUP_SNAPSHOT,
                AuditLogManager.AUDITLOG_SUCCESS, AuditLogManager.AUDITOP_BEGIN,
                snapshotId.toString(), consistencyGroupId.toString(), snapshot.getStorageController().toString());

        return toTask(snapshot, taskId, op);
    }

    /**
     * Restores the data on the array snapshot point-in-time copy represented by the
     * BlockSnapshotSession instance with the passed id, to the snapshot session source
     * object.
     *
     * @brief Restore snapshot session to source
     *
     * @prereq None
     *
     * @param consistencyGroupId The URI of the BlockConsistencyGroup.
     * @param snapSessionId The URI of the BlockSnapshotSession instance to be restored.
     *
     * @return TaskResourceRep representing the snapshot session task.
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/snapshot-sessions/{sid}/restore")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskResourceRep restoreConsistencyGroupSnapshotSession(
            @PathParam("id") final URI consistencyGroupId, @PathParam("sid") final URI snapSessionId) {
        // Get the consistency group and snapshot and verify the snapshot session
        // is actually associated with the consistency group.
        final BlockConsistencyGroup consistencyGroup = (BlockConsistencyGroup) queryResource(consistencyGroupId);
        final BlockSnapshotSession snapSession = (BlockSnapshotSession) queryResource(snapSessionId);
        verifySnapshotSessionIsForConsistencyGroup(snapSession, consistencyGroup);
        return getSnapshotSessionManager().restoreSnapshotSession(snapSessionId);
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
            throw APIException.badRequests.cannotCreateSnapshotOfCG();
        }

        // Get the storage system for the consistency group.
        StorageSystem storage = _permissionsHelper.getObjectById(snapshot.getStorageController(), StorageSystem.class);

        // resync for OpenStack storage system type is not supported
        if (Type.openstack.name().equalsIgnoreCase(storage.getSystemType())) {
            throw APIException.methodNotAllowed.notSupportedWithReason(
                    String.format("Snapshot resynchronization is not possible on third-party storage systems"));
        }

        // resync for IBM XIV storage system type is not supported
        if (Type.ibmxiv.name().equalsIgnoreCase(storage.getSystemType())) {
            throw APIException.methodNotAllowed.notSupportedWithReason(
                    "Snapshot resynchronization is not supported on IBM XIV storage systems");
        }

        // resync for VNX storage system type is not supported
        if (Type.vnxblock.name().equalsIgnoreCase(storage.getSystemType())) {
            throw APIException.methodNotAllowed.notSupportedWithReason(
                    "Snapshot resynchronization is not supported on VNX storage systems");
        }

        if (storage.checkIfVmax3()) {
            throw APIException.methodNotAllowed.notSupportedWithReason(
                    "Snapshot resynchronization is not supported on VMAX3 storage systems");
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
                Role.TENANT_ADMIN)
                || _permissionsHelper.userHasGivenACL(user, project.getId(),
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
        StorageDriverManager storageDriverManager = (StorageDriverManager) StorageDriverManager.getApplicationContext().getBean(
                StorageDriverManager.STORAGE_DRIVER_MANAGER);

        // Verify a volume was specified to be added or removed.
        if (!param.hasEitherAddOrRemoveVolumes()) {
            throw APIException.badRequests.noVolumesToBeAddedRemovedFromCG();
        }

        // TODO require a check if requested list contains all volumes/replicas?
        // For replicas, check replica count with volume count in CG
        StorageSystem cgStorageSystem = null;

        // Throw exception if the operation is attempted on volumes that are in RP CG.
        if (consistencyGroup.isRPProtectedCG()) {
            throw APIException.badRequests.operationNotAllowedOnRPVolumes();
        }

        // if consistency group is not created yet, then get the storage system from the block object to be added
        // This method also supports adding volumes or replicas to CG (VMAX - SMIS 8.0.x)
        if ((!consistencyGroup.created() || NullColumnValueGetter.isNullURI(consistencyGroup.getStorageController()))
                && param.hasVolumesToAdd()) { // we just need to check the case of add volumes in this case
            BlockObject bo = BlockObject.fetch(_dbClient, param.getAddVolumesList().getVolumes().get(0));
            cgStorageSystem = _permissionsHelper.getObjectById(
                    bo.getStorageController(), StorageSystem.class);
        } else {
            cgStorageSystem = _permissionsHelper.getObjectById(
                    consistencyGroup.getStorageController(), StorageSystem.class);
        }

        // IBMXIV, XtremIO, VPlex, VNX, ScaleIO, and VMax volumes only
        String systemType = cgStorageSystem.getSystemType();
        if (!storageDriverManager.isDriverManaged(cgStorageSystem.getSystemType())) {
            if (!systemType.equals(DiscoveredDataObject.Type.vplex.name())
                    && !systemType.equals(DiscoveredDataObject.Type.vnxblock.name())
                    && !systemType.equals(DiscoveredDataObject.Type.vmax.name())
                    && !systemType.equals(DiscoveredDataObject.Type.vnxe.name())
                    && !systemType.equals(DiscoveredDataObject.Type.unity.name())
                    && !systemType.equals(DiscoveredDataObject.Type.ibmxiv.name())
                    && !systemType.equals(DiscoveredDataObject.Type.scaleio.name())
                    && !systemType.equals(DiscoveredDataObject.Type.xtremio.name())) {
                throw APIException.methodNotAllowed.notSupported();
            }
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

        if (cgStorageSystem.getUsingSmis80() && cgStorageSystem.deviceIsType(Type.vmax)) {
            // CG can have replicas
            if (_log.isDebugEnabled()) {
                _log.debug("CG can have replicas for VMAX with SMI-S 8.x");
            }
        } else if (param.hasVolumesToRemove() || (!isReplica && !volsAlreadyInCG)) {
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

            // VNX group clones and mirrors are just list of replicas, no corresponding group on array side
            if (!cgStorageSystem.deviceIsType(Type.vnxblock)) {
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
     * For APIs that act on a snapshot session for a consistency group, ensures that
     * the passed snapshot session is associated with the passed consistency group, else
     * throws a bad request exception.
     *
     * @param snapSession A reference to a snapshot session.
     * @param consistencyGroup A reference to a consistency group
     */
    private void verifySnapshotSessionIsForConsistencyGroup(BlockSnapshotSession snapSession,
            BlockConsistencyGroup consistencyGroup) {
        URI snapSessionCGURI = snapSession.getConsistencyGroup();
        if ((NullColumnValueGetter.isNullURI(snapSessionCGURI)) || (!snapSessionCGURI.equals(consistencyGroup.getId()))) {
            throw APIException.badRequests.snapshotSessionIsNotForConsistencyGroup(
                    snapSession.getLabel(), consistencyGroup.getLabel());
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
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/full-copies")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN }, acls = { ACL.ANY })
    public TaskList createConsistencyGroupFullCopy(@PathParam("id") URI cgURI,
            VolumeFullCopyCreateParam param) {
        // Verify the consistency group in the requests and get the
        // volumes in the consistency group.
        List<Volume> cgVolumes = verifyCGForFullCopyRequest(cgURI);

        // Get the storage system for the consistency group.
        StorageSystem storage = _permissionsHelper.getObjectById(cgVolumes.get(0).getStorageController(), StorageSystem.class);

        // Group clone for IBM XIV storage system type is not supported
        if (Type.ibmxiv.name().equalsIgnoreCase(storage.getSystemType())) {
            throw APIException.methodNotAllowed.notSupportedWithReason(
                    "Consistency Group Full Copy is not supported on IBM XIV storage systems");
        }

        // block CG operation if any of its volumes is in COPY type VolumeGroup (Application)
        validateVolumeNotPartOfApplication(cgVolumes, FULL_COPY);

        // Grab the first volume and call the block full copy
        // manager to create the full copies for the volumes
        // in the CG. Note that it will take into account the
        // fact that the volume is in a CG.
        return getFullCopyManager().createFullCopy(cgVolumes.get(0).getId(), param);
    }

    /**
     * Creates a consistency group snapshot session
     *
     * @prereq none
     * @param consistencyGroupId Consistency group URI
     * @param param
     * @brief Create consistency group snapshot session
     * @return TaskList
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/snapshot-sessions")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN }, acls = { ACL.ANY })
    public TaskList createConsistencyGroupSnapshotSession(@PathParam("id") URI consistencyGroupId,
            SnapshotSessionCreateParam param) {

        // Grab the first volume and call the block snapshot session
        // manager to create the snapshot sessions for the volumes
        // in the CG. Note that it will take into account the
        // fact that the volume is in a CG.
        BlockConsistencyGroup cg = queryObject(BlockConsistencyGroup.class, consistencyGroupId, true);

        // RP CG's must use applications to create snapshots
        if (isIdEmbeddedInURL(consistencyGroupId) && cg.checkForType(Types.RP)) {
            throw APIException.badRequests.snapshotsNotSupportedForRPCGs();
        }

        // Validate CG information in the request
        validateVolumesInReplicationGroups(cg, param.getVolumes(), _dbClient);
        return getSnapshotSessionManager().createSnapshotSession(cg, param, getFullCopyManager());
    }

    /**
     * The method implements the API to create and link new target
     * volumes to an existing BlockSnapshotSession instance.
     *
     * @brief Link target volumes to a snapshot session.
     *
     * @prereq The block snapshot session has been created and the maximum
     *         number of targets has not already been linked to the snapshot sessions
     *         for the source object.
     *
     * @param id The URI of the BlockSnapshotSession instance to which the
     *            new targets will be linked.
     * @param param The linked target information.
     *
     * @return A TaskList representing the snapshot session task.
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/snapshot-sessions/{sid}/link-targets")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskList linkTargetVolumes(@PathParam("id") URI id,
            @PathParam("sid") URI sessionId,
            SnapshotSessionLinkTargetsParam param) {
        validateSessionPartOfConsistencyGroup(id, sessionId);
        return getSnapshotSessionManager().linkTargetVolumesToSnapshotSession(sessionId, param);
    }

    /**
     * This method implements the API to re-link a target to either its current
     * snapshot session or to a different snapshot session of the same source.
     *
     * @brief Relink target volumes to snapshot sessions.
     *
     * @prereq The target volumes are linked to a snapshot session of the same source object.
     *
     * @param id The URI of the BlockConsistencyGroup instance to which the
     *            the session is created for.
     * @param sessionId The URI of the BlockSnapshotSession instance to which the
     *            the targets will be re-linked.
     * @param param The linked target information.
     *
     * @return A TaskList representing the snapshot session tasks.
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/snapshot-sessions/{sid}/relink-targets")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskList relinkTargetVolumes(@PathParam("id") URI id,
            @PathParam("sid") URI sessionId,
            SnapshotSessionRelinkTargetsParam param) {
        validateSessionPartOfConsistencyGroup(id, sessionId);
        return getSnapshotSessionManager().relinkTargetVolumesToSnapshotSession(sessionId, param);
    }

    /**
     * The method implements the API to unlink target volumes from an existing
     * BlockSnapshotSession instance and optionally delete those target volumes.
     *
     * @brief Unlink target volumes from a snapshot session.
     *
     * @prereq A snapshot session is created and target volumes have previously
     *         been linked to that snapshot session.
     *
     * @param id The URI of the BlockSnapshotSession instance to which the targets are linked.
     * @param param The linked target information.
     *
     * @return A TaskResourceRep representing the snapshot session task.
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/snapshot-sessions/{sid}/unlink-targets")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskResourceRep unlinkTargetVolumesForSession(@PathParam("id") URI id,
            @PathParam("sid") URI sessionId,
            SnapshotSessionUnlinkTargetsParam param) {
        validateSessionPartOfConsistencyGroup(id, sessionId);
        return unlinkTargetVolumesFromSnapshotSession(sessionId, param, OperationTypeEnum.UNLINK_SNAPSHOT_SESSION_TARGET);
    }

    /**
     * Unlink target volumes from an existing BlockSnapshotSession instance and optionally delete
     * those target volumes.
     *
     * @param sessionURI The URI of the BlockSnapshotSession instance to which the targets are linked.
     * @param param he linked target information.
     * @param opType The operation type for the audit log.
     *
     * @return A TaskResourceRep representing the snapshot session task.
     */
    private TaskResourceRep unlinkTargetVolumesFromSnapshotSession(URI sessionURI, SnapshotSessionUnlinkTargetsParam param,
            OperationTypeEnum opType) {
        return getSnapshotSessionManager().unlinkTargetVolumesFromSnapshotSession(sessionURI, param, opType);
    }

    /**
     * This method is called when a linked BlockSnapshot for a BlockSnapshotSession is passed to
     * {@link #deactivateConsistencyGroupSnapshot(URI, URI)} and we must instead unlink&delete it.
     *
     * @param session The BlockSnapshotSession.
     * @param snapshot The BlockSnapshot.
     * @return TaskList wrapping the single TaskResourceRep.
     */
    private TaskList deactivateAndUnlinkTargetVolumesForSession(BlockSnapshotSession session, BlockSnapshot snapshot) {
        SnapshotSessionUnlinkTargetParam unlink = new SnapshotSessionUnlinkTargetParam(snapshot.getId(), true);
        SnapshotSessionUnlinkTargetsParam param = new SnapshotSessionUnlinkTargetsParam(newArrayList(unlink));
        TaskResourceRep task = unlinkTargetVolumesFromSnapshotSession(session.getId(), param,
                OperationTypeEnum.DELETE_CONSISTENCY_GROUP_SNAPSHOT);
        return new TaskList(newArrayList(task));
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

        // block CG operation if any of its volumes is in COPY type VolumeGroup (Application)
        if (isIdEmbeddedInURL(cgURI)) {
            validateVolumeNotPartOfApplication(cgVolumes, FULL_COPY);
        }

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

        // block CG operation if any of its volumes is in COPY type VolumeGroup (Application)
        if (isIdEmbeddedInURL(cgURI)) {
            validateVolumeNotPartOfApplication(cgVolumes, FULL_COPY);
        }

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

        // block CG operation if any of its volumes is in COPY type VolumeGroup (Application)
        if (isIdEmbeddedInURL(cgURI)) {
            validateVolumeNotPartOfApplication(cgVolumes, FULL_COPY);
        }

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

        // block CG operation if any of its volumes is in COPY type VolumeGroup (Application)
        if (isIdEmbeddedInURL(cgURI)) {
            validateVolumeNotPartOfApplication(cgVolumes, FULL_COPY);
        }

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

        // block CG operation if any of its volumes is in COPY type VolumeGroup (Application)
        if (isIdEmbeddedInURL(cgURI)) {
            validateVolumeNotPartOfApplication(cgVolumes, FULL_COPY);
        }

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

        // if any of the source volumes are in an application, replica management must be done via the application
        for (Volume srcVol : cgVolumes) {
            if (srcVol.getApplication(_dbClient) != null) {
                return new NamedVolumesList();
            }
        }

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
     * @brief Reverse roles of source and target
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
            taskResp = performProtectionAction(id, copy, ProtectionOp.SWAP.getRestOp());
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

        // Validate the consistency group URI
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
            taskResp = performProtectionAction(id, copy, ProtectionOp.FAILOVER.getRestOp());
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
     * Request to change the access mode on the provided copy.
     *
     * NOTE: This is an asynchronous operation.
     *
     * Currently only supported for RecoverPoint protected volumes. If volume is SRDF protected,
     * then we do nothing and return the task.
     *
     * @prereq none
     *
     * @param id the URN of a ViPR Source volume
     * @param param Copy to change access mode on
     *
     * @brief Change the access mode for a copy.
     * @return TaskList
     *
     * @throws ControllerException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/continuous-copies/accessmode")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskList changeAccessMode(@PathParam("id") URI id, CopiesParam param) throws ControllerException {

        TaskResourceRep taskResp = null;
        TaskList taskList = new TaskList();

        // Validate the consistency group URI
        ArgValidator.checkFieldUriType(id, BlockConsistencyGroup.class, "id");
        // Validate the list of copies
        ArgValidator.checkFieldNotEmpty(param.getCopies(), "copies");

        List<Copy> copies = param.getCopies();

        if (copies.size() != 1) {
            // Change access mode operations can only be performed on a single copy
            throw APIException.badRequests.changeAccessCopiesParamCanOnlyBeOne();
        }

        Copy copy = copies.get(0);

        ArgValidator.checkFieldNotEmpty(copy.getType(), "type");
        ArgValidator.checkFieldNotEmpty(copy.getAccessMode(), "accessMode");
        if (copy.getType().equalsIgnoreCase(TechnologyType.RP.toString())) {
            taskResp = performProtectionAction(id, copy, ProtectionOp.CHANGE_ACCESS_MODE.getRestOp());
            taskList.getTaskList().add(taskResp);
        } else if (copy.getType().equalsIgnoreCase(TechnologyType.SRDF.toString())) {
            _log.warn("Changing access mode is currently not supported for SRDF.  Returning empty task list (no-op).");
            return taskList;
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
     * @brief Cancel a failover and return to source
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
            taskResp = performProtectionAction(id, copy, ProtectionOp.FAILOVER_CANCEL.getRestOp());
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
     * @param pointInTime any point in time, specified in UTC.
     *            Allowed values: "yyyy-MM-dd_HH:mm:ss" formatted date or datetime in milliseconds.
     * @param op operation to perform (pause, stop, failover, etc)
     * @return task resource rep
     * @throws InternalException
     */
    private TaskResourceRep
            performProtectionAction(URI consistencyGroupId, Copy copy, String op)
                    throws InternalException {
        ArgValidator.checkFieldUriType(consistencyGroupId, BlockConsistencyGroup.class, "id");
        ArgValidator.checkFieldUriType(copy.getCopyID(), VirtualArray.class, "copyId");

        // Get the BlockConsistencyGroup and target VirtualArray associated with the request.
        final BlockConsistencyGroup consistencyGroup = (BlockConsistencyGroup) queryResource(consistencyGroupId);
        final VirtualArray targetVirtualArray = _permissionsHelper.getObjectById(copy.getCopyID(), VirtualArray.class);

        ArgValidator.checkEntity(consistencyGroup, consistencyGroupId, true);
        ArgValidator.checkEntity(targetVirtualArray, copy.getCopyID(), true);

        // The consistency group needs to be associated with RecoverPoint in order to perform the operation.
        if (!consistencyGroup.checkForType(Types.RP)) {
            // Attempt to do protection link management on unprotected CG
            throw APIException.badRequests.consistencyGroupMustBeRPProtected(consistencyGroupId);
        }

        if (op.equalsIgnoreCase(ProtectionOp.SWAP.getRestOp()) && !NullColumnValueGetter.isNullURI(consistencyGroupId)) {
            ExportUtils.validateConsistencyGroupBookmarksExported(_dbClient, consistencyGroupId);
        }

        // Catch any attempts to use an invalid access mode
        if (op.equalsIgnoreCase(ProtectionOp.CHANGE_ACCESS_MODE.getRestOp()) &&
                !Copy.ImageAccessMode.DIRECT_ACCESS.name().equalsIgnoreCase(copy.getAccessMode())) {
            throw APIException.badRequests.unsupportedAccessMode(copy.getAccessMode());
        }

        // Verify that the supplied target Virtual Array is being referenced by at least one target volume in the CG.
        List<Volume> targetVolumes = getTargetVolumes(consistencyGroup, copy.getCopyID());

        if (targetVolumes == null || targetVolumes.isEmpty()) {
            // The supplied target varray is not referenced by any target volumes in the CG.
            throw APIException.badRequests.targetVirtualArrayDoesNotMatch(consistencyGroupId, copy.getCopyID());
        }

        // Get the first target volume
        Volume targetVolume = targetVolumes.get(0);

        ProtectionSystem system = _dbClient.queryObject(ProtectionSystem.class,
                targetVolume.getProtectionController());
        String deviceType = system.getSystemType();

        if (!deviceType.equals(DiscoveredDataObject.Type.rp.name())) {
            throw APIException.badRequests.protectionForRpClusters();
        }

        String task = UUID.randomUUID().toString();
        Operation status = new Operation();
        status.setResourceType(ProtectionOp.getResourceOperationTypeEnum(op));
        _dbClient.createTaskOpStatus(BlockConsistencyGroup.class, consistencyGroupId, task, status);

        RPController controller = getController(RPController.class, system.getSystemType());

        controller.performProtectionOperation(system.getId(), consistencyGroupId, targetVolume.getId(), copy.getPointInTime(),
                copy.getAccessMode(), op, task);
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
        List<Volume> volumeList = BlockConsistencyGroupUtils.getActiveVolumesInCG(consistencyGroup, _dbClient, null);

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
            if (!Volume.isSRDFProtectedVolume(tgtVolume)) {
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

        // COP-25377. We need to block failover and swap operations for SRDF ACTIVE COPY MODE
        if (Mode.ACTIVE.toString().equalsIgnoreCase(targetVolume.getSrdfCopyMode())
                && (op.equalsIgnoreCase(ProtectionOp.FAILOVER_CANCEL.getRestOp()) ||
                        op.equalsIgnoreCase(ProtectionOp.FAILOVER.getRestOp()) || op.equalsIgnoreCase(ProtectionOp.SWAP.getRestOp()))) {
            throw BadRequestException.badRequests.operationNotPermittedOnSRDFActiveCopyMode(op);
        }

        String task = UUID.randomUUID().toString();
        Operation status = new Operation();
        status.setResourceType(ProtectionOp.getResourceOperationTypeEnum(op));
        _dbClient.createTaskOpStatus(BlockConsistencyGroup.class, targetCg.getId(), task, status);

        if (op.equalsIgnoreCase(ProtectionOp.FAILOVER_TEST_CANCEL.getRestOp()) ||
                op.equalsIgnoreCase(ProtectionOp.FAILOVER_TEST.getRestOp())) {
            _dbClient.ready(BlockConsistencyGroup.class, targetCg.getId(), task);
            return toTask(targetCg, task, status);
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
        ProtectionOrchestrationController controller = getController(ProtectionOrchestrationController.class,
                ProtectionOrchestrationController.PROTECTION_ORCHESTRATION_DEVICE);

        // Create a new duplicate copy of the original copy. Update the copyId field to be the
        // ID of the target volume. Existing SRDF controller logic needs the target volume
        // to operate off of, not the virtual array.
        Copy updatedCopy = new Copy(copy.getType(), copy.getSync(), targetVolume.getId(),
                copy.getName(), copy.getCount());

        controller.performSRDFProtectionOperation(system.getId(), updatedCopy, op, task);

        return toTask(targetCg, task, status);
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
            List<Volume> volumeList = null;
            if (consistencyGroup.checkForType(Types.RP)) {
                volumeList = blockServiceApiImpl.getActiveCGVolumes(consistencyGroup);
            } else {
                volumeList = BlockConsistencyGroupUtils.getActiveNonVplexVolumesInCG(consistencyGroup, _dbClient, null);
            }

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
     * Check if any of the given CG volumes is part of an application.
     * If so, throw an error indicating replica operation is not supported on CG
     * and it should be performed at application level.
     *
     * @param volume the CG volume
     */
    private void validateVolumeNotPartOfApplication(List<Volume> volumes, String replicaType) {
        for (Volume volume : volumes) {
            VolumeGroup volumeGroup = volume.getApplication(_dbClient);
            if (volumeGroup != null) {
                throw APIException.badRequests.replicaOperationNotAllowedOnCGVolumePartOfCopyTypeVolumeGroup(volumeGroup.getLabel(),
                        replicaType);
            }
        }
    }

    private void validateSessionPartOfConsistencyGroup(URI cgId, URI sessionId) {
        ArgValidator.checkUri(sessionId);
        ArgValidator.checkUri(cgId);

        BlockSnapshotSession session = _dbClient.queryObject(BlockSnapshotSession.class, sessionId);
        BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, cgId);

        if (session == null) {
            throw APIException.notFound.unableToFindEntityInURL(sessionId);
        }
        if (cg == null) {
            throw APIException.notFound.unableToFindEntityInURL(cgId);
        }
        if (!cg.getId().equals(session.getConsistencyGroup())) {
            throw APIException.badRequests.snapshotSessionIsNotForConsistencyGroup(session.getLabel(), cg.getLabel());
        }
    }

    /**
     * Create/Initiate the Migration of a Storage Group.
     * 
     * This automatically provisions equivalent storage on the target system. The target devices
     * are assigned the identity of the source devices and are configured in a pass-through mode
     * that allows the data to be accessed from both the source and target devices.
     * 
     * @prereq Migration environment is created between source and target systems.
     * @param id the URN of Block Consistency Group
     * @param param MigrationCreateParam
     * @return A TaskResourceRep for the Migration associated with Block Consistency Group
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/migration/create")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep migrationCreate(@PathParam("id") URI id, MigrationCreateParam param) {
        // validate input
        ArgValidator.checkFieldUriType(id, BlockConsistencyGroup.class, ID_FIELD);
        ArgValidator.checkFieldUriType(param.getTargetStorageSystem(), StorageSystem.class, "target_storage_system");
        if (param.getSrp() != null) {
            ArgValidator.checkFieldUriType(param.getSrp(), StoragePool.class, "srp");
        }

        BlockConsistencyGroup cg = (BlockConsistencyGroup) queryResource(id);
        validateBlockConsistencyGroupForMigration(cg);

        // prepare Migration object.
        Migration migration = prepareMigration(cg, cg.getStorageController(), param.getTargetStorageSystem());

        // Create a unique task id.
        String taskId = UUID.randomUUID().toString();

        MigrationServiceApi migrationApiImpl = getMigrationServiceImpl(cg);
        migrationApiImpl.migrationCreate(id, migration.getId(), param, taskId);

        migration.setJobStatus(DataCollectionJobStatus.IN_PROGRESS.name());
        _dbClient.updateObject(migration);
        // Create a task for the migration object associated and set the initial task state to pending.
        Operation op = _dbClient.createTaskOpStatus(Migration.class, migration.getId(), taskId,
                ResourceOperationTypeEnum.MIGRATION_CREATE_MIGRATION);

        TaskResourceRep task = toTask(migration, taskId, op);
        return task;
    }

    /**
     * Cutover the Migration of a Storage Group.
     * 
     * A cutover operation moves the target devices out of pass-through mode, initiates data
     * synchronization from the source to the target and makes the paths to the source array
     * inactive so that all I/Os are being serviced by the target system.
     * 
     * @prereq Storage group on the target side is created. Host zoning and rescan is done.
     * @param id the URN of Block Consistency Group
     * @return A TaskResourceRep
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/migration/cutover")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep migrationCutover(@PathParam("id") URI id) {
        // validate input
        ArgValidator.checkFieldUriType(id, BlockConsistencyGroup.class, ID_FIELD);

        BlockConsistencyGroup cg = (BlockConsistencyGroup) queryResource(id);
        validateBlockConsistencyGroupForMigration(cg);

        // get Migration object associated with consistency group
        Migration migration = getMigrationForConsistencyGroup(cg);

        // Create a unique task id.
        String taskId = UUID.randomUUID().toString();

        MigrationServiceApi migrationApiImpl = getMigrationServiceImpl(cg);
        migrationApiImpl.migrationCutover(id, migration.getId(), taskId);

        migration.setJobStatus(DataCollectionJobStatus.IN_PROGRESS.name());
        _dbClient.updateObject(migration);
        // Create a task for the migration object associated and set the initial task state to pending.
        Operation op = _dbClient.createTaskOpStatus(Migration.class, migration.getId(), taskId,
                ResourceOperationTypeEnum.MIGRATION_CUTOVER);

        TaskResourceRep task = toTask(migration, taskId, op);
        return task;
    }

    /**
     * Commit the Migration of a Storage Group.
     * 
     * After the source to target data synchronization is complete and all application data has
     * been migrated to the target system, a commit operation is performed. It completes the migration
     * by releasing temporary resources allocated to perform the migration, permanently disabling
     * access to the source devices, and assigning the target device ID to the source devices.
     * 
     * @prereq Storage group on the target system has all the data migrated from the source system .
     * @param id the URN of Block Consistency Group
     * @return A TaskResourceRep
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/migration/commit")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep migrationCommit(@PathParam("id") URI id) {
        // validate input
        ArgValidator.checkFieldUriType(id, BlockConsistencyGroup.class, ID_FIELD);

        BlockConsistencyGroup cg = (BlockConsistencyGroup) queryResource(id);
        validateBlockConsistencyGroupForMigration(cg);

        // get Migration object associated with consistency group
        Migration migration = getMigrationForConsistencyGroup(cg);

        // Create a unique task id.
        String taskId = UUID.randomUUID().toString();

        MigrationServiceApi migrationApiImpl = getMigrationServiceImpl(cg);
        migrationApiImpl.migrationCommit(id, migration.getId(), taskId);

        migration.setJobStatus(DataCollectionJobStatus.IN_PROGRESS.name());
        _dbClient.updateObject(migration);
        // Create a task for the migration object associated and set the initial task state to pending.
        Operation op = _dbClient.createTaskOpStatus(Migration.class, migration.getId(), taskId,
                ResourceOperationTypeEnum.MIGRATION_COMMIT);

        TaskResourceRep task = toTask(migration, taskId, op);
        return task;
    }

    /**
     * Cancel the Migration of a Storage Group.
     *
     * Ends a migration that has not been committed. It removes storage provisioned for the migration
     * on the target system, releases resources allocated to perform the migration, and places the
     * source devices into the state they were in before the Create operation was run.
     * 
     * @prereq Storage group on the target system is created and not committed yet.
     * @param id the URN of Block Consistency Group
     * @return A TaskResourceRep
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/migration/cancel")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep migrationCancel(@PathParam("id") URI id) {
        // validate input
        ArgValidator.checkFieldUriType(id, BlockConsistencyGroup.class, ID_FIELD);

        BlockConsistencyGroup cg = (BlockConsistencyGroup) queryResource(id);
        validateBlockConsistencyGroupForMigration(cg);

        // get Migration object associated with consistency group
        Migration migration = getMigrationForConsistencyGroup(cg);

        // Create a unique task id.
        String taskId = UUID.randomUUID().toString();

        MigrationServiceApi migrationApiImpl = getMigrationServiceImpl(cg);
        migrationApiImpl.migrationCancel(id, migration.getId(), taskId);

        migration.setJobStatus(DataCollectionJobStatus.IN_PROGRESS.name());
        _dbClient.updateObject(migration);
        // Create a task for the migration object associated and set the initial task state to pending.
        Operation op = _dbClient.createTaskOpStatus(Migration.class, migration.getId(), taskId,
                ResourceOperationTypeEnum.MIGRATION_CANCEL);

        TaskResourceRep task = toTask(migration, taskId, op);
        return task;
    }

    /**
     * Refresh the migration status for the Storage Group.
     * 
     * @prereq Storage group on the source system is discovered.
     * @param id the URN of Block Consistency Group
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/migration/refresh")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep migrationRefresh(@PathParam("id") URI id) {
        // validate input
        ArgValidator.checkFieldUriType(id, BlockConsistencyGroup.class, ID_FIELD);

        BlockConsistencyGroup cg = (BlockConsistencyGroup) queryResource(id);
        validateBlockConsistencyGroupForMigration(cg);

        // get Migration object associated with consistency group
        Migration migration = getMigrationForConsistencyGroup(cg);

        // Create a unique task id.
        String taskId = UUID.randomUUID().toString();

        MigrationServiceApi migrationApiImpl = getMigrationServiceImpl(cg);
        migrationApiImpl.migrationRefresh(id, migration.getId(), taskId);

        migration.setJobStatus(DataCollectionJobStatus.IN_PROGRESS.name());
        _dbClient.updateObject(migration);
        // Create a task for the migration object associated and set the initial task state to pending.
        Operation op = _dbClient.createTaskOpStatus(Migration.class, migration.getId(), taskId,
                ResourceOperationTypeEnum.MIGRATION_REFRESH);

        TaskResourceRep task = toTask(migration, taskId, op);
        return task;
    }

    /**
     * Recovers the migration for a Storage Group from a failed state.
     * 
     * @prereq Migration for a storage system is created, but not yet committed.
     * @param id the URN of Block Consistency Group
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/migration/recover")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep migrationRecover(@PathParam("id") URI id, @DefaultValue("false") @QueryParam("force") boolean force) {
        // validate input
        ArgValidator.checkFieldUriType(id, BlockConsistencyGroup.class, ID_FIELD);

        BlockConsistencyGroup cg = (BlockConsistencyGroup) queryResource(id);
        validateBlockConsistencyGroupForMigration(cg);

        // get Migration object associated with consistency group
        Migration migration = getMigrationForConsistencyGroup(cg);

        // Create a unique task id.
        String taskId = UUID.randomUUID().toString();

        MigrationServiceApi migrationApiImpl = getMigrationServiceImpl(cg);
        migrationApiImpl.migrationRecover(id, migration.getId(), force, taskId);

        migration.setJobStatus(DataCollectionJobStatus.IN_PROGRESS.name());
        _dbClient.updateObject(migration);
        // Create a task for the migration object associated and set the initial task state to pending.
        Operation op = _dbClient.createTaskOpStatus(Migration.class, migration.getId(), taskId,
                ResourceOperationTypeEnum.MIGRATION_RECOVER);

        TaskResourceRep task = toTask(migration, taskId, op);
        return task;
    }

    /**
     * Sync - Controls the replication from the target side devices to the source side devices
     * after a cutover is done and all data has been migrated to the target side.
     * Sync Stop - Stops the writes to the source volumes during a migration.
     * 
     * @prereq Migration for a storage group is created, cutover operation is performed,
     *         and waiting to commit.
     * @param id the URN of Block Consistency Group
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/migration/sync-stop")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep migrationSyncStop(@PathParam("id") URI id) {
        // validate input
        ArgValidator.checkFieldUriType(id, BlockConsistencyGroup.class, ID_FIELD);

        BlockConsistencyGroup cg = (BlockConsistencyGroup) queryResource(id);
        validateBlockConsistencyGroupForMigration(cg);

        // get Migration object associated with consistency group
        Migration migration = getMigrationForConsistencyGroup(cg);

        // Create a unique task id.
        String taskId = UUID.randomUUID().toString();

        MigrationServiceApi migrationApiImpl = getMigrationServiceImpl(cg);
        migrationApiImpl.migrationSyncStop(id, migration.getId(), taskId);

        migration.setJobStatus(DataCollectionJobStatus.IN_PROGRESS.name());
        _dbClient.updateObject(migration);
        // Create a task for the migration object associated and set the initial task state to pending.
        Operation op = _dbClient.createTaskOpStatus(Migration.class, migration.getId(), taskId,
                ResourceOperationTypeEnum.MIGRATION_SYNCSTOP);

        TaskResourceRep task = toTask(migration, taskId, op);
        return task;
    }

    /**
     * Sync - Controls the replication from the target side devices to the source side devices
     * after a cutover is done and all data has been migrated to the target side.
     * Sync Start - Synchronizes the source volumes with target volumes during a migration.
     * 
     * @prereq Migration for a storage group is created, cutover operation is performed,
     *         and waiting to commit.
     * @param id the URN of Block Consistency Group
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/migration/sync-start")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep migrationSyncStart(@PathParam("id") URI id) {
        // validate input
        ArgValidator.checkFieldUriType(id, BlockConsistencyGroup.class, ID_FIELD);

        BlockConsistencyGroup cg = (BlockConsistencyGroup) queryResource(id);
        validateBlockConsistencyGroupForMigration(cg);

        // get Migration object associated with consistency group
        Migration migration = getMigrationForConsistencyGroup(cg);

        // Create a unique task id.
        String taskId = UUID.randomUUID().toString();

        MigrationServiceApi migrationApiImpl = getMigrationServiceImpl(cg);
        migrationApiImpl.migrationSyncStart(id, migration.getId(), taskId);

        migration.setJobStatus(DataCollectionJobStatus.IN_PROGRESS.name());
        _dbClient.updateObject(migration);
        // Create a task for the migration object associated and set the initial task state to pending.
        Operation op = _dbClient.createTaskOpStatus(Migration.class, migration.getId(), taskId,
                ResourceOperationTypeEnum.MIGRATION_SYNCSTART);

        TaskResourceRep task = toTask(migration, taskId, op);
        return task;
    }

    /**
     * Creates new zones based on given path parameters and storage ports.
     * The code understands existing zones and creates the remaining if needed.
     * 
     * @param MigrationZoneCreateParam
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/migration/create-zones")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskList createZonesForMigration(@PathParam("id") URI id,
            MigrationZoneCreateParam createZoneParam) {
        // validate input
        TaskList taskList = new TaskList();
        ArgValidator.checkFieldUriType(id, BlockConsistencyGroup.class, ID_FIELD);
        ArgValidator.checkUri(createZoneParam.getCompute());
        ArgValidator.checkUri(createZoneParam.getTargetStorageSystem());
        if (createZoneParam.getTargetVirtualArray() != null) {
            ArgValidator.checkUri(createZoneParam.getTargetVirtualArray());
        }

        URI computeURI = createZoneParam.getCompute();
        BlockConsistencyGroup cg = (BlockConsistencyGroup) queryResource(id);
        validateBlockConsistencyGroupForMigration(cg);

        // get Migration object associated with consistency group
        Migration migration = prepareMigration(cg, cg.getStorageController(), createZoneParam.getTargetStorageSystem());

        List<URI> hostInitiatorList = new ArrayList<URI>();
        // Get Initiators from the storage Group if compute is not provided.
        hostInitiatorList.addAll(Collections2.transform(cg.getInitiators(), FCTN_STRING_TO_URI));

        StorageSystem system = _dbClient.queryObject(StorageSystem.class, createZoneParam.getTargetStorageSystem());
        ExportPathParams pathParam = new ExportPathParams(createZoneParam.getPathParam());

        if (URIUtil.isType(computeURI, Cluster.class)) {
            // Invoke port allocation by passing all the initiators.
            taskList.addTask(invokeCreateZonesForGivenCompute(hostInitiatorList, computeURI, createZoneParam.getPathParam()
                    .getStoragePorts(), system, createZoneParam.getTargetVirtualArray(), pathParam, migration));
        } else {
            // Group Initiators by Host and invoke port allocation.
            Map<String, List<URI>> hostInitiatorMap = com.emc.storageos.util.ExportUtils.mapInitiatorsToHostResource(null,
                    hostInitiatorList, _dbClient);
            for (Entry<String, List<URI>> hostEntry : hostInitiatorMap.entrySet()) {
                taskList.addTask(invokeCreateZonesForGivenCompute(hostEntry.getValue(), URIUtil.uri(hostEntry.getKey()),
                        createZoneParam.getPathParam().getStoragePorts(), system, createZoneParam.getTargetVirtualArray(),
                        pathParam, migration));
            }
        }
        return taskList;
    }

    /**
     * Run port allocations for each Host, if the SG is associated with more than one Host and not part of cluster.
     * 
     * @param initiatorURIs
     * @param computeURI
     * @param storagePortURIs
     * @param system
     * @param varray
     * @param pathParam
     * @param migration
     * @return
     */
    private TaskResourceRep invokeCreateZonesForGivenCompute(List<URI> initiatorURIs, URI computeURI,
            List<URI> storagePortURIs, StorageSystem system, URI varray, ExportPathParams pathParam, Migration migration) {
        _log.info("Invoking create Zones for compute {} with initiators {}",
                computeURI, Joiner.on(",").join(initiatorURIs));
        List<StoragePort> storagePorts = new ArrayList<StoragePort>();
        List<Initiator> initiators = _dbClient.queryObject(Initiator.class, initiatorURIs);

        if (null != storagePortURIs) {
            storagePorts = _dbClient.queryObject(StoragePort.class, storagePortURIs);
        } else {
            storagePorts.addAll(ConnectivityUtil.getTargetStoragePortsConnectedtoInitiator(initiators, system, _dbClient));
        }

        if (null == varray) {
            varray = ConnectivityUtil.pickVirtualArrayHavingMostNumberOfPorts(storagePorts);
        }
        _log.info("Selected Virtual Array {} for Host {}", varray, computeURI);

        Map<URI, List<URI>> generatedIniToStoragePort = new HashMap<URI, List<URI>>();
        generatedIniToStoragePort.putAll(_blockStorageScheduler.assignStoragePorts(system, varray, initiators,
                pathParam, new StringSetMap(), null));
        String task = UUID.randomUUID().toString();
        NetworkController controller = getNetworkController(system.getSystemType());
        controller.createSanZones(initiatorURIs, computeURI, generatedIniToStoragePort, migration.getId(), task);

        migration.setJobStatus(DataCollectionJobStatus.IN_PROGRESS.name());
        _dbClient.updateObject(migration);
        Operation op = _dbClient.createTaskOpStatus(Migration.class, migration.getId(), task,
                ResourceOperationTypeEnum.ADD_SAN_ZONE);
        return toTask(migration, task, op);

    }

    /**
     * Rescan Hosts associated with Block Consistency Group
     * 
     * @param id
     * @return
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    @Path("/{id}/migration/rescan-hosts")
    public TaskList rescanHostsForMigration(@PathParam("id") URI id) {

        TaskList taskList = new TaskList();
        ArgValidator.checkFieldUriType(id, BlockConsistencyGroup.class, ID_FIELD);

        BlockConsistencyGroup cg = (BlockConsistencyGroup) queryResource(id);
        List<URI> hostInitiatorList = new ArrayList<URI>();
        // Get Initiators from the storage Group if compute is not provided.
        hostInitiatorList.addAll(Collections2.transform(cg.getInitiators(), FCTN_STRING_TO_URI));

        // Group Initiators by Host and invoke port allocation.
        Map<String, List<URI>> hostInitiatorMap = com.emc.storageos.util.ExportUtils.mapInitiatorsToHostResource(null,
                hostInitiatorList, _dbClient);
        for (Entry<String, List<URI>> hostEntry : hostInitiatorMap.entrySet()) {
            _log.info("Rescan Host {}", hostEntry.getKey());
            Host host = _dbClient.queryObject(Host.class, URIUtil.uri(hostEntry.getKey()));
            if (host == null || host.getInactive()) {
                _log.info(String.format("Host not found or inactive: %s", id));
                // TODO throw exception
            }

            if (!host.getDiscoverable()) {
                _log.info(String.format("Host %s is not discoverable, so cannot rescan", host.getHostName()));
                // TODO throw exception
            }
            String task = UUID.randomUUID().toString();

            Operation op = _dbClient.createTaskOpStatus(Host.class, host.getId(), task, ResourceOperationTypeEnum.HOST_RESCAN);
            HostRescanController reScanController = getHostController("host");
            reScanController.rescanHostStoragePaths(host.getId(), task);
            taskList.addTask(toTask(host, task, op));
        }

        return taskList;
    }

    /**
     * Get Host Controller
     * 
     * @param deviceType
     * @return
     */
    private HostRescanController getHostController(String deviceType) {
        HostRescanController controller = getController(HostRescanController.class, "host");
        return controller;
    }

    /**
     * Returns a list of the migrations associated with the consistency group.
     *
     * @param id the URN of Block Consistency Group
     * @return A list specifying the id, name, and self link of the migrations
     *         associated with the consistency group
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/migrations")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR, Role.TENANT_ADMIN })
    public MigrationList getConsistencyGroupMigrations(@PathParam("id") URI id) {
        // validate input
        ArgValidator.checkFieldUriType(id, BlockConsistencyGroup.class, ID_FIELD);

        BlockConsistencyGroup cg = (BlockConsistencyGroup) queryResource(id);
        validateBlockConsistencyGroupForMigration(cg);

        MigrationList cgMigrations = new MigrationList();
        URIQueryResultList migrationURIs = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory.getMigrationConsistencyGroupConstraint(id), migrationURIs);
        Iterator<URI> migrationURIsIter = migrationURIs.iterator();
        while (migrationURIsIter.hasNext()) {
            URI migrationURI = migrationURIsIter.next();
            Migration migration = _permissionsHelper.getObjectById(migrationURI, Migration.class);
            cgMigrations.getMigrations().add(toMigrationResource(migration));
        }

        return cgMigrations;
    }

    /**
     * Prepares a migration object for the passed consistency group specifying the source
     * and target storage systems for the migration.
     * If migration was already initiated for this consistency group, there will be an
     * existing Migration object. In that case, return that Migration object.
     *
     * @param cgURI The URI of the consistency group.
     * @param sourceURI The URI of the source system for the migration.
     * @param targetURI The URI of the target system for the migration.
     *
     * @return A reference to a newly created Migration or a pre-created one.
     */
    private Migration prepareMigration(BlockConsistencyGroup cg, URI sourceURI, URI targetURI) {
        Migration migration = null;
        StorageSystem sourceSystem = _permissionsHelper.getObjectById(sourceURI, StorageSystem.class);
        StorageSystem targetSystem = _permissionsHelper.getObjectById(targetURI, StorageSystem.class);

        URIQueryResultList migrationURIs = new URIQueryResultList();
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getMigrationConsistencyGroupConstraint(cg.getId()), migrationURIs);
        Iterator<URI> migrationURIsIter = migrationURIs.iterator();
        // If migration was already initiated for consistency group, get that migration object.
        if (migrationURIsIter.hasNext()) {
            URI migrationURI = migrationURIsIter.next();
            migration = _permissionsHelper.getObjectById(migrationURI, Migration.class);
        } else {
            migration = new Migration();
            migration.setId(URIUtil.createId(Migration.class));
            migration.setConsistencyGroup(cg.getId());
            migration.setLabel(cg.getLabel());
            migration.setSourceSystem(sourceURI);
            migration.setTargetSystem(targetURI);
            migration.setSourceSystemSerialNumber(sourceSystem.getSerialNumber());
            migration.setTargetSystemSerialNumber(targetSystem.getSerialNumber());
            migration.setJobStatus(DataCollectionJobStatus.CREATED.name());
            _dbClient.createObject(migration);
        }
        return migration;
    }

    /**
     * Gets the migration object for consistency group.
     *
     * @param cg the consistency group
     * @return the migration for consistency group
     */
    private Migration getMigrationForConsistencyGroup(BlockConsistencyGroup cg) {
        Migration migration = null;
        URIQueryResultList migrationURIs = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory.getMigrationConsistencyGroupConstraint(cg.getId()), migrationURIs);
        Iterator<URI> migrationURIsIter = migrationURIs.iterator();
        // There will be only one migration object created when migration is initiated for consistency group.
        if (migrationURIsIter.hasNext()) {
            URI migrationURI = migrationURIsIter.next();
            migration = _permissionsHelper.getObjectById(migrationURI, Migration.class);
        } else {
            throw APIException.badRequests.noMigrationFoundForConsistencyGroup(cg.getId(), cg.getLabel());
        }
        return migration;
    }

    /**
     * Validate block consistency group for migration.
     *
     * @param cg the block consistency group
     */
    private void validateBlockConsistencyGroupForMigration(BlockConsistencyGroup cg) {
        if (!cg.getTypes().contains(Types.MIGRATION.name())) {
            throw APIException.methodNotAllowed.notSupportedWithReason(
                    "Migration Operation is supported only for Block Consistency Groups of type 'Migration'");
        }
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

    /**
     * Creates and returns an instance of the block snapshot session manager to handle
     * a snapshot session creation request.
     *
     * @return BlockSnapshotSessionManager
     */
    private BlockSnapshotSessionManager getSnapshotSessionManager() {
        BlockSnapshotSessionManager snapshotSessionManager = new BlockSnapshotSessionManager(_dbClient,
                _permissionsHelper, _auditMgr, _coordinator, sc, uriInfo, _request);
        return snapshotSessionManager;
    }

    /**
     * Given a device type ("mds", or "brocade"), return the appropriate NetworkController.
     * 
     * @param deviceType
     * @return NetworkController
     */
    private NetworkController getNetworkController(String deviceType) {
        NetworkController controller = getController(NetworkController.class, deviceType);
        return controller;
    }

}
