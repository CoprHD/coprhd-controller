/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.BlockMapper.map;
import static com.emc.storageos.api.mapper.DbObjectMapper.map;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;
import static com.emc.storageos.db.client.util.NullColumnValueGetter.isNullURI;

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
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.BlockMigrationMapper;
import com.emc.storageos.api.service.impl.placement.VPlexScheduler;
import com.emc.storageos.api.service.impl.resource.utils.BlockServiceUtils;
import com.emc.storageos.api.service.impl.resource.utils.VirtualPoolChangeAnalyzer;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Migration;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VplexMirror;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.BlockMigrationBulkRep;
import com.emc.storageos.model.block.MigrationList;
import com.emc.storageos.model.block.MigrationParam;
import com.emc.storageos.model.block.MigrationRestRep;
import com.emc.storageos.model.block.NamedVolumesList;
import com.emc.storageos.model.block.MigrateVolumeVirtualArrayParam;
import com.emc.storageos.model.host.HostList;
import com.emc.storageos.model.host.InitiatorList;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.emc.storageos.vplex.api.VPlexApiConstants;
import com.emc.storageos.vplex.api.VPlexApiException;
import com.emc.storageos.vplex.api.VPlexMigrationInfo;
import com.emc.storageos.vplexcontroller.VPlexController;
import com.google.common.base.Function;

/**
 * Service used to manage resource migrations.
 */
@Path("/block/migrations")
@DefaultPermissions(readRoles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, readAcls = {
        ACL.OWN, ACL.ALL }, writeRoles = { Role.TENANT_ADMIN }, writeAcls = { ACL.OWN,
        ACL.ALL })
public class MigrationService extends TaskResourceService {
    // A reference to the BlockServiceApi for VPlex.
    VPlexBlockServiceApiImpl _vplexBlockServiceApi = null;

    // Migration Service Implementations
    static volatile private Map<String, MigrationServiceApi> _migrationServiceApis;

    // A logger reference.
    private static final Logger s_logger = LoggerFactory
            .getLogger(MigrationService.class);

    static public MigrationServiceApi getMigrationServiceImpl(String type) {
        return _migrationServiceApis.get(type);
    }

    /**
     * Setter for the VPlex BlockServiceApi called through Spring configuration.
     * 
     * @param vplexBlockServiceApi A reference to the BlockServiceApi for VPlex.
     */
    public void setVplexBlockServiceApi(VPlexBlockServiceApiImpl vplexBlockServiceApi) {
        _vplexBlockServiceApi = vplexBlockServiceApi;
    }

    /**
     * Setter for the MigrationServiceApi called through Spring configuration.
     *
     * @param migrationServiceApi A reference to the MigrationServiceApi.
     */
    public void setMigrationServiceApi(Map<String, MigrationServiceApi> serviceInterfaces) {
        _migrationServiceApis = serviceInterfaces;
    }

    /**
     * Returns the bean responsible for servicing the request
     *
     * @param volume block volume
     * @return migration service implementation object
     */
    private MigrationServiceApi getMigrationServiceImpl(Volume volume) {
        return getMigrationServiceImpl("default");
    }

    /**
     * Performs a non-disruptive migration for the passed VPLEX virtual volume.
     * The backend volume of the VPLEX volume that is migrated is the backend
     * volume on the passed source storage system. The volume is migrated to the
     * passed target storage system, which must be connected to the same VPLEX
     * cluster as the source storage system.
     * 
     * 
     * @prereq none
     * 
     * @param migrateParam A reference to the migration parameters.
     * @deprecated Use the Change Virtual Pool API instead
     * @brief Perform a non-disruptive migration for a VPLEX volume.
     * @return A TaskResourceRep for the volume being migrated.
     * @throws InternalException
     */
    @Deprecated
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_ADMIN })
    public TaskResourceRep migrateVolume(MigrationParam migrateParam) throws InternalException {

        // Create a unique task id.
        String taskId = UUID.randomUUID().toString();

        s_logger.info(
                "Migrate volume {} from storage system {} to storage system {}",
                new Object[] { migrateParam.getVolume(), migrateParam.getSrcStorageSystem(),
                        migrateParam.getTgtStorageSystem() });

        // Verify the requested volume supports migration.
        Volume vplexVolume = verifyRequestedVolumeSupportsMigration(migrateParam.getVolume());
        s_logger.debug("Verfified requested volume");

        // Make sure that we don't have some pending
        // operation against the volume
        checkForPendingTasks(Arrays.asList(vplexVolume.getTenant().getURI()), Arrays.asList(vplexVolume));

        // Determine the backend volume of the requested VPlex volume that
        // is to be migrated. It is the volume on the passed source storage
        // system.
        Volume migrationSrc = getMigrationSource(vplexVolume,
                migrateParam.getSrcStorageSystem());
        s_logger.debug("Migration source is {}", migrationSrc.getId());

        // The project for the migration target will be the same as that
        // of the source.
        Project migrationTgtProject = _permissionsHelper.getObjectById(migrationSrc
                .getProject().getURI(), Project.class);
        s_logger.debug("Migration target project is {}", migrationTgtProject.getId());

        // The VirtualArray for the migration target will be the same as
        // that of the source.
        VirtualArray migrationTargetVarray = _permissionsHelper.getObjectById(
                migrationSrc.getVirtualArray(), VirtualArray.class);
        s_logger.debug("Migration target VirtualArray is {}", migrationTargetVarray.getId());

        // Verify the requested target storage system exists and
        // is a system to which the migration source volume can
        // be migrated.
        verifyTargetStorageSystemForMigration(migrateParam.getVolume(),
                vplexVolume.getStorageController(), migrateParam.getSrcStorageSystem(),
                migrateParam.getTgtStorageSystem());
        s_logger.debug("Verified target storage system {}",
                migrateParam.getTgtStorageSystem());

        // Get the VirtualPool for the migration target.
        VirtualPool migrationTgtCos = getVirtualPoolForMigrationTarget(migrateParam.getVirtualPool(),
                vplexVolume, migrationSrc);
        s_logger.debug("Migration target VirtualPool is {}", migrationTgtCos.getId());

        // Get the VPlex storage system for the virtual volume.
        URI vplexSystemURI = vplexVolume.getStorageController();
        Set<URI> requestedVPlexSystems = new HashSet<URI>();
        requestedVPlexSystems.add(vplexSystemURI);

        // Get a placement recommendation on the requested target storage
        // system connected to the VPlex storage system of the VPlex volume.
        VPlexScheduler vplexScheduler = _vplexBlockServiceApi.getBlockScheduler();
        VirtualPoolCapabilityValuesWrapper cosWrapper = new VirtualPoolCapabilityValuesWrapper();
        cosWrapper.put(VirtualPoolCapabilityValuesWrapper.SIZE, migrationSrc.getCapacity());
        cosWrapper.put(VirtualPoolCapabilityValuesWrapper.RESOURCE_COUNT, new Integer(1));
        List<Recommendation> recommendations = vplexScheduler.scheduleStorage(
                migrationTargetVarray, requestedVPlexSystems, migrateParam.getTgtStorageSystem(),
                migrationTgtCos, false, null, null, cosWrapper);
        if (recommendations.isEmpty()) {
            throw APIException.badRequests.noStorageFoundForVolumeMigration(migrationTgtCos.getLabel(), migrationTargetVarray.getLabel(),
                    vplexVolume.getId());
        }
        s_logger.debug("Got recommendation for migration target");

        // There should be a single recommendation.
        Recommendation recommendation = recommendations.get(0);
        URI recommendedSystem = recommendation.getSourceStorageSystem();
        URI recommendedPool = recommendation.getSourceStoragePool();
        s_logger.debug("Recommendation storage system is {}", recommendedSystem);
        s_logger.debug("Recommendation storage pool is {}", recommendedPool);

        // Prepare the migration target.
        List<URI> migrationTgts = new ArrayList<URI>();
        Map<URI, URI> poolTgtMap = new HashMap<URI, URI>();
        Long size = _vplexBlockServiceApi.getVolumeCapacity(migrationSrc);
        Volume migrationTgt = VPlexBlockServiceApiImpl.prepareVolumeForRequest(
                size, migrationTgtProject, migrationTargetVarray,
                migrationTgtCos, recommendedSystem, recommendedPool,
                migrationSrc.getLabel(), ResourceOperationTypeEnum.CREATE_BLOCK_VOLUME,
                taskId, _dbClient);
        URI migrationTgtURI = migrationTgt.getId();
        migrationTgts.add(migrationTgtURI);
        poolTgtMap.put(recommendedPool, migrationTgtURI);
        s_logger.debug("Prepared migration target volume {}", migrationTgtURI);

        // Prepare the migration.
        Map<URI, URI> migrationsMap = new HashMap<URI, URI>();
        Migration migration = _vplexBlockServiceApi
                .prepareMigration(migrateParam.getVolume(), migrationSrc.getId(),
                        migrationTgt.getId(), taskId);
        migrationsMap.put(migrationTgtURI, migration.getId());
        s_logger.debug("Prepared migration {}", migration.getId());

        // Create a task for the virtual volume being migrated and set the
        // initial task state to pending.
        Operation op = _dbClient.createTaskOpStatus(Volume.class,
                vplexVolume.getId(), taskId, ResourceOperationTypeEnum.MIGRATE_BLOCK_VOLUME);
        TaskResourceRep task = toTask(vplexVolume, taskId, op);
        s_logger.debug("Created task for volume {}", migrateParam.getVolume());

        try {
            VPlexController controller = _vplexBlockServiceApi.getController();
            String successMsg = String.format("Migration succeeded for volume %s",
                    migrateParam.getVolume());
            String failMsg = String.format("Migration failed for volume %s",
                    migrateParam.getVolume());
            controller.migrateVolumes(vplexSystemURI, migrateParam.getVolume(),
                    migrationTgts, migrationsMap, poolTgtMap,
                    (migrateParam.getVirtualPool() != null ? migrateParam.getVirtualPool() : null), null,
                    successMsg, failMsg, null, taskId, null);
            s_logger.debug("Got VPlex controller and created migration workflow");
        } catch (InternalException e) {
            s_logger.error("Controller Error", e);
            String errMsg = String.format("Controller Error: %s", e.getMessage());
            task.setState(Operation.Status.error.name());
            task.setMessage(errMsg);
            Operation opStatus = new Operation(Operation.Status.error.name(), errMsg);
            _dbClient.updateTaskOpStatus(Volume.class, task.getResource()
                    .getId(), taskId, opStatus);
            migrationTgt.setInactive(true);
            _dbClient.persistObject(migrationTgt);
            migration.setInactive(true);
            _dbClient.persistObject(migration);

            throw e;
        }

        return task;
    }

    /**
     *
     * @param projectURI
     * @param varrayURI
     * @return Get Volume for Virtual Array Change
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/varray-change")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public NamedVolumesList getVolumesForVirtualArrayChange(
            @QueryParam("project") URI projectURI, @QueryParam("targetVarray") URI varrayURI) {
        NamedVolumesList volumeList = new NamedVolumesList();

        // Get the project.
        ArgValidator.checkFieldUriType(projectURI, Project.class, "project");
        Project project = _permissionsHelper.getObjectById(projectURI, Project.class);
        ArgValidator.checkEntity(project, projectURI, false);
        s_logger.info("Found project {}:{}", projectURI);

        // Verify the user is authorized for the project.
        BlockServiceUtils.verifyUserIsAuthorizedForRequest(project, getUserFromContext(), _permissionsHelper);
        s_logger.info("User is authorized for project");

        // Get the target virtual array.
        ArgValidator.checkFieldUriType(varrayURI, VirtualArray.class, "targetVarray");
        VirtualArray tgtVarray = _permissionsHelper.getObjectById(varrayURI, VirtualArray.class);
        ArgValidator.checkEntity(tgtVarray, varrayURI, false);
        s_logger.info("Found target virtual array {}:{}", tgtVarray.getLabel(), varrayURI);

        // Determine all volumes in the project that could potentially
        // be moved to the target virtual array.
        URIQueryResultList volumeIds = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory.getProjectVolumeConstraint(projectURI), volumeIds);
        Iterator<Volume> volumeItr = _dbClient.queryIterativeObjects(Volume.class, volumeIds);
        while (volumeItr.hasNext()) {
            Volume volume = volumeItr.next();
            try {
                // Don't operate on VPLEX backend, RP Journal volumes,
                // or other internal volumes.
                BlockServiceUtils.validateNotAnInternalBlockObject(volume, false);

                // Don't operate on ingested volumes.
                VolumeIngestionUtil.checkOperationSupportedOnIngestedVolume(volume,
                        ResourceOperationTypeEnum.CHANGE_BLOCK_VOLUME_VARRAY, _dbClient);

                // Can't change to the same varray.
                if (volume.getVirtualArray().equals(varrayURI)) {
                    s_logger.info("Virtual array change not supported for volume {} already in the target varray",
                                volume.getId());
                    continue;
                }

                // Get the appropriate migration service implementation.
                MigrationServiceApi migrationServiceAPI = getMigrationServiceImpl(volume);

                // Verify that the virtual array change is allowed for the
                // volume and target virtual array.
                migrationServiceAPI.verifyVarrayChangeSupportedForVolumeAndVarray(volume, tgtVarray);

                // If so, add it to the list.
                volumeList.getVolumes().add(toNamedRelatedResource(volume));
            } catch (Exception e) {
                s_logger.info("Virtual array change not supported for volume {}:{}",
                            volume.getId(), e.getMessage());
            }
        }

        return volumeList;
    }


    /**
     * Lists the id and name for all the hosts that belong to the given tenant organization.
     *
     * @param tid the URI of a CoprHD tenant organization
     * @prereq none
     * @brief List hosts
     * @return a list of hosts that belong to the tenant organization.
     * @throws DatabaseException when a DB error occurs
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public HostList getMigrationHosts(@QueryParam("tenant") final URI tid) throws DatabaseException {
        URI tenantId;
        StorageOSUser user = getUserFromContext();
        if (tid == null || StringUtils.isBlank(tid.toString())) {
            tenantId = URI.create(user.getTenantId());
        } else {
            tenantId = tid;
        }
        // this call validates the tenant id
        TenantOrg tenant = _permissionsHelper.getObjectById(tenantId, TenantOrg.class);
        ArgValidator.checkEntity(tenant, tenantId, isIdEmbeddedInURL(tenantId), true);

        // check the user permissions for this tenant org
        verifyAuthorizedInTenantOrg(tenantId, user);
        // get all host children
        HostList migrationHostList = new HostList();
        migrationHostList.setHosts(map(ResourceTypeEnum.HOST, listChildren(tenantId, Host.class, "label", "tenant")));
        return migrationHostList;
    }

    /**
     * Allows the caller to change the virtual array for the given volumes.
     * The data migration will be performed by a southbound driver if the source
     * volumes' storage controller has implemented migration. Otherwise, a host-
     * based migration will be performed if an external linux host has been
     * configured.
     *
     * @brief Change virtual array for the given volumes.
     *
     * @prereq Volumes must not be exported, snapshotted, copied or mirrored.
     *
     * @return A TaskList representing the varray change for the volumes.
     *
     * @throws InternalException, APIException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskList migrateVolumesVirtualArray(MigrateVolumeVirtualArrayParam param)
            throws InternalException, APIException, DatabaseException {
        s_logger.info("Request to change varray for volumes {}", param.getVolumes());

        List<URI> volumeURIs = param.getVolumes();
        URI tgtVarrayURI = param.getVirtualArray();
        URI migrationHostURI = param.getMigrationHost();
        boolean isHostMigration = param.getIsHostMigration();

        // Create the result.
        TaskList taskList = new TaskList();

        // Create a unique task id.
        String taskId = UUID.randomUUID().toString();

        // Validate that each of the volumes passed in is eligible for the varray change
        VirtualArray tgtVarray = null;
        BlockConsistencyGroup cg = null;
        Host migrationHost = null;
        InitiatorList initiatorList = null;
        MigrationServiceApi migrationServiceApi = null;
        List<Volume> volumes = new ArrayList<Volume>();
        List<Volume> cgVolumes = new ArrayList<Volume>();
        boolean foundVolumeNotInCG = false;
        for (URI volumeURI : volumeURIs) {
            // Get and verify the volume.
            ArgValidator.checkFieldUriType(volumeURI, Volume.class, "volume");
            Volume volume = queryVolumeResource(volumeURI);
            ArgValidator.checkEntity(volume, volumeURI, false);
            s_logger.info("Found volume {}", volumeURI);

            // Don't operate on internal block objects
            BlockServiceUtils.validateNotAnInternalBlockObject(volume, false);

            // Don't operate on ingested volumes
            VolumeIngestionUtil.checkOperationSupportedOnIngestedVolume(volume,
                    ResourceOperationTypeEnum.CHANGE_BLOCK_VOLUME_VARRAY, _dbClient);

            // Get and validate the volume's project
            URI projectURI = volume.getProject().getURI();
            Project project = _permissionsHelper.getObjectById(projectURI, Project.class);
            ArgValidator.checkEntity(project, projectURI, false);
            s_logger.info("Found volume project {}", projectURI);

            // Verify the user is authorized for the volume's project.
            BlockServiceUtils.verifyUserIsAuthorizedForRequest(project, getUserFromContext(), _permissionsHelper);
            s_logger.info("User is authorized for volume's project");

            // Verify the current and requested virtual arrays are not the same.
            if (volume.getVirtualArray().equals(tgtVarrayURI)) {
                throw APIException.badRequests.currentAndRequestedVArrayAreTheSame();
            }

            // Get and validate the target virtual array.
            if (tgtVarray == null) {
                tgtVarray = BlockServiceUtils.verifyVirtualArrayForRequest(project,
                tgtVarrayURI, uriInfo, _permissionsHelper, _dbClient);
                s_logger.info("Found new VirtualArray {}", tgtVarrayURI);
            }

            // Make sure that we don't have some pending
            // operation against the volume
            checkForPendingTasks(Arrays.asList(volume.getTenant().getURI()), Arrays.asList(volume));

            migrationServiceApi = getMigrationServiceImpl(volume);

            // Verify that the virtual array change is allowed for the
            // requested volume and virtual array
            migrationServiceApi.verifyVarrayChangeSupportedForVolumeAndVarray(volume, tgtVarray);
            s_logger.info("Virtual array change is supported for requested volume and varray");

            // All volumes must be a CG or none of the volumes can be
            // in a CG. After processing individual volumes, if the
            // volumes are in a CG, then we make sure all volumes in the
            // CG and only the volumes in the CG are passed.
            URI cgURI = volume.getConsistencyGroup();
            if ((cg == null) && (!foundVolumeNotInCG)) {
                if (!isNullURI(cgURI)) {
                    cg = _permissionsHelper.getObjectById(cgURI, BlockConsistencyGroup.class);
                    s_logger.info("All volumes should be in CG {}:{}", cgURI, cg.getLabel());
                    cgVolumes.addAll(migrationServiceApi.getActiveCGVolumes(cg));
                } else {
                    s_logger.info("No volumes should be in CGs");
                    foundVolumeNotInCG = true;
                }
            } else if (((cg != null) && (isNullURI(cgURI))) ||
                    ((foundVolumeNotInCG) && (!isNullURI(cgURI)))) {
                // A volume was in a CG, so all volumes must be in a CG.
                if (cg != null) {
                    // Volumes should all be in the CG and this one is not.
                    s_logger.error("Volume {}:{} is not in the CG", volumeURI, volume.getLabel());
                } else {
                    s_logger.error("Volume {}:{} is in CG {}", new Object[] { volumeURI,
                                volume.getLabel(), cgURI });
                }
                throw APIException.badRequests.mixedVolumesinCGForVarrayChange();
            }

            // Add the volume to the list
            volumes.add(volume);
        }

        // If the volumes are in a CG verify that they are
        // all in the same CG and all volumes are passed.
        if (cg != null) {
            // all volume in CG must have been passed.
            s_logger.info("Verify all volumes in CG {}:{}", cg.getId(), cg.getLabel());
            URI storageId = cg.getStorageController();
            if (!NullColumnValueGetter.isNullURI(storageId)) {
                verifyVolumesInCG(volumes, cgVolumes);
            } else {
                verifyVolumesInCG(volumes, cgVolumes);
            }
        }

        // Create a task for each volume and set the initial
        // task state to pending.
        for (Volume volume : volumes) {
            Operation op = _dbClient.createTaskOpStatus(Volume.class, volume.getId(), taskId,
                    ResourceOperationTypeEnum.CHANGE_BLOCK_VOLUME_VARRAY);
            TaskResourceRep resourceTask = toTask(volume, taskId, op);
            taskList.addTask(resourceTask);
        }

        if (isHostMigration) {
            initiatorList = getInitiators(migrationHostURI);
        }

        // Now execute the varray change for the volumes.
        if (cg != null) {
            try {
                // When the volumes are part of a CG, executed as a single workflow.
                migrationServiceApi.migrateVolumesVirtualArray(volumes, cg, cgVolumes, tgtVarray,
                            isHostMigration, initiatorList, taskId);
                s_logger.info("Executed virtual array change for volumes");
            } catch (InternalException | APIException e) {
                // Fail all the tasks.
                String errorMsg = String.format("Volume virtual array change error: %s", e.getMessage());
                s_logger.error(errorMsg);
                for (TaskResourceRep resourceTask : taskList.getTaskList()) {
                    resourceTask.setState(Operation.Status.error.name());
                    resourceTask.setMessage(errorMsg);
                    _dbClient.error(Volume.class, resourceTask.getResource().getId(), taskId, e);
                }
            } catch (Exception e) {
                // Fail all the tasks.
                String errorMsg = String.format("Volume virtual array change error: %s", e.getMessage());
                s_logger.error(errorMsg);
                for (TaskResourceRep resourceTask : taskList.getTaskList()) {
                    resourceTask.setState(Operation.Status.error.name());
                    resourceTask.setMessage(errorMsg);
                    _dbClient.error(Volume.class, resourceTask.getResource().getId(), taskId,
                            InternalServerErrorException.internalServerErrors
                                    .unexpectedErrorDuringVarrayChange(e));
                }
            }
        } else {
            // When the volumes are not in a CG, then execute as individual workflows.
            for (Volume volume : volumes) {
                try {
                    migrationServiceApi.migrateVolumesVirtualArray(Arrays.asList(volume), cg, cgVolumes, tgtVarray,
                                isHostMigration, initiatorList, taskId);
                    s_logger.info("Executed virtual array change for volume {}", volume.getId());
                } catch (InternalException | APIException e) {
                    String errorMsg = String.format("Volume virtual array change error: %s", e.getMessage());
                    s_logger.error(errorMsg);
                    for (TaskResourceRep resourceTask : taskList.getTaskList()) {
                        // Fail the correct task.
                        if (resourceTask.getResource().getId().equals(volume.getId())) {
                            resourceTask.setState(Operation.Status.error.name());
                            resourceTask.setMessage(errorMsg);
                            _dbClient.error(Volume.class, resourceTask.getResource().getId(), taskId, e);
                        }
                    }
                } catch (Exception e) {
                    // Fail all the tasks.
                    String errorMsg = String.format("Volume virtual array change error: %s", e.getMessage());
                    s_logger.error(errorMsg);
                    for (TaskResourceRep resourceTask : taskList.getTaskList()) {
                        // Fail the correct task.
                        if (resourceTask.getResource().getId().equals(volume.getId())) {
                            resourceTask.setState(Operation.Status.error.name());
                            resourceTask.setMessage(errorMsg);
                            _dbClient.error(Volume.class, resourceTask.getResource().getId(), taskId,
                                    InternalServerErrorException.internalServerErrors
                                            .unexpectedErrorDuringVarrayChange(e));
                        }
                    }
                }
            }
        }

        return taskList;
    }

    /**
     * Gets the id and name for all the host initiators of a host.
     *
     * @param id The URI of the host.
     * @return List of Host Initiators.
     * @throws DatabaseException when a DB error occurs.
     */
    private InitiatorList getInitiators(URI id) throws DatabaseException {
        Host host = queryObject(Host.class, id, false);
        // check the user permissions
        verifyAuthorizedInTenantOrg(host.getTenant(), getUserFromContext());
        // get the initiators
        InitiatorList initiatorList = new InitiatorList();
        List<NamedElementQueryResultList.NamedElement> dataObjects = listChildren(id, Initiator.class, "iniport", "host");
        for (NamedElementQueryResultList.NamedElement dataObject : dataObjects) {
            initiatorList.getInitiators().add(toNamedRelatedResource(ResourceTypeEnum.INITIATOR,
                        dataObject.getId(), dataObject.getName()));
        }
        return initiatorList;
    }

    /**
     * Verifies that the passed volumes correspond to the passed volumes from
     * a consistency group.
     *
     * @param volumes The volumes to verify.
     * @param cgVolumes The list of active volumes in a CG.
     */
    private void verifyVolumesInCG(List<Volume> volumes, List<Volume> cgVolumes) {
        // The volumes counts must match. If the number of volumes
        // is less, then not all volumes in the CG were passed.
        if (volumes.size() < cgVolumes.size()) {
            throw APIException.badRequests.cantChangeVarrayNotAllCGVolumes();
        }

        // Make sure only the CG volumes are selected.
        for (Volume volume : volumes) {
            boolean found = false;
            for (Volume cgVolume : cgVolumes) {
                if (volume.getId().equals(cgVolume.getId())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                s_logger.error("Volume {}:{} not found in CG", volume.getId(), volume.getLabel());
                throw APIException.badRequests.cantChangeVarrayVolumeIsNotInCG();
            }
        }
    }

    /**
     * Returns a list of the migrations the user is permitted to see or an empty
     * list if the user is not authorized for any migrations.
     * 
     * 
     * @prereq none
     * 
     * @brief List migrations
     * @return A MigrationList specifying the name, id, and self link for each
     *         migration.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN, Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public MigrationList getMigrations() {
        // Return the migrations the user is authorized to see.
        MigrationList migrationList = new MigrationList();
        List<URI> migrationURIs = _dbClient.queryByType(Migration.class, true);
        Iterator<URI> uriIter = migrationURIs.iterator();
        while (uriIter.hasNext()) {
            Migration migration = queryResource(uriIter.next());
            if (BulkList.MigrationFilter.isUserAuthorizedForMigration(migration,
                    getUserFromContext(), _permissionsHelper)) {
                migrationList.getMigrations().add(
                        toNamedRelatedResource(migration, migration.getLabel()));
            }
        }
        return migrationList;
    }

    /**
     * Returns the data for the migration with the id specified in the request.
     * 
     * 
     * @prereq none
     * 
     * @param id the URN of a ViPR migration.
     * 
     * @brief Show data for a migration.
     * @return A MigrationRestRep instance specifying the information about the
     *         migration.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.TENANT_ADMIN, Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public MigrationRestRep getMigration(@PathParam("id") URI id) {
        // Return the migration or throw an exception when the user is
        // not authorized or the migration is not found.
        ArgValidator.checkFieldUriType(id, Migration.class, "id");
        Migration migration = queryResource(id);
        if (!BulkList.MigrationFilter.isUserAuthorizedForMigration(migration,
                getUserFromContext(), _permissionsHelper)) {
            StorageOSUser user = getUserFromContext();
            throw APIException.forbidden.insufficientPermissionsForUser(user.getName());
        } else {
            return map(migration);
        }
    }

    /**
     * Pause a migration that is in progress.
     * 
     * 
     * @prereq The migration is in progress
     * 
     * @param id the URN of a ViPR migration.
     * 
     * @brief Pause a migration.
     * @return A TaskResourceRep
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/pause")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep pauseMigration(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, Migration.class, "id");
        Migration migration = queryResource(id);
        if (!BulkList.MigrationFilter.isUserAuthorizedForMigration(migration,
                getUserFromContext(), _permissionsHelper)) {
            StorageOSUser user = getUserFromContext();
            throw APIException.forbidden.insufficientPermissionsForUser(user.getName());
        }
        String status = migration.getMigrationStatus();
        String migrationName = migration.getLabel();
        if (status == null || status.isEmpty() || migrationName == null || migrationName.isEmpty()) {
            throw APIException.badRequests.migrationHasntStarted(id.toString());
        }
        if (status.equalsIgnoreCase(VPlexMigrationInfo.MigrationStatus.COMPLETE.getStatusValue()) ||
                status.equalsIgnoreCase(VPlexMigrationInfo.MigrationStatus.ERROR.getStatusValue()) ||
                status.equalsIgnoreCase(VPlexMigrationInfo.MigrationStatus.COMMITTED.getStatusValue()) ||
                status.equalsIgnoreCase(VPlexMigrationInfo.MigrationStatus.CANCELLED.getStatusValue())) {
            throw APIException.badRequests.migrationCantBePaused(migrationName, status);
        }
        URI volId = migration.getVolume();
        Volume vplexVol = _dbClient.queryObject(Volume.class, volId);

        // Create a unique task id.
        String taskId = UUID.randomUUID().toString();
        // Create a task for the volume and set the
        // initial task state to pending.
        Operation op = _dbClient.createTaskOpStatus(Volume.class,
                volId, taskId, ResourceOperationTypeEnum.PAUSE_MIGRATION);
        TaskResourceRep task = toTask(vplexVol, taskId, op);
        if (status.equalsIgnoreCase(VPlexMigrationInfo.MigrationStatus.PAUSED.getStatusValue())) {
            // it has been paused.
            s_logger.info("Migration {} has been paused", id);
            op.ready();
            vplexVol.getOpStatus().createTaskStatus(taskId, op);
            _dbClient.persistObject(vplexVol);
            return task;
        }

        try {
            VPlexController controller = _vplexBlockServiceApi.getController();

            controller.pauseMigration(vplexVol.getStorageController(), id, taskId);

        } catch (InternalException e) {
            s_logger.error("Error", e);
            String errMsg = String.format("Error: %s", e.getMessage());
            task.setState(Operation.Status.error.name());
            task.setMessage(errMsg);
            op.error(e);
            vplexVol.getOpStatus().updateTaskStatus(taskId, op);
            _dbClient.persistObject(vplexVol);
        }

        return task;
    }

    /**
     * Resume a migration that was previously paused.
     * 
     * 
     * @prereq The migration is paused
     * 
     * @param id the URN of a ViPR migration.
     * 
     * @brief Resume a paused migration.
     * @return A TaskResourceRep
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/resume")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep resumeMigration(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, Migration.class, "id");
        Migration migration = queryResource(id);
        if (!BulkList.MigrationFilter.isUserAuthorizedForMigration(migration,
                getUserFromContext(), _permissionsHelper)) {
            StorageOSUser user = getUserFromContext();
            throw APIException.forbidden.insufficientPermissionsForUser(user.getName());
        }
        String status = migration.getMigrationStatus();
        String migrationName = migration.getLabel();
        if (status == null || status.isEmpty() || migrationName == null || migrationName.isEmpty()) {
            throw APIException.badRequests.migrationHasntStarted(id.toString());
        }
        if (!status.equalsIgnoreCase(VPlexMigrationInfo.MigrationStatus.PAUSED.getStatusValue())) {
            throw APIException.badRequests.migrationCantBeResumed(migrationName, status);
        }
        URI volId = migration.getVolume();
        Volume vplexVol = _dbClient.queryObject(Volume.class, volId);

        // Create a unique task id.
        String taskId = UUID.randomUUID().toString();
        // Create a task for the virtual volume being migrated and set the
        // initial task state to pending.
        Operation op = _dbClient.createTaskOpStatus(Volume.class,
                volId, taskId, ResourceOperationTypeEnum.RESUME_MIGRATION);
        TaskResourceRep task = toTask(vplexVol, taskId, op);

        try {
            VPlexController controller = _vplexBlockServiceApi.getController();

            controller.resumeMigration(vplexVol.getStorageController(), id, taskId);

        } catch (InternalException e) {
            s_logger.error("Error", e);
            String errMsg = String.format("Error: %s", e.getMessage());
            task.setState(Operation.Status.error.name());
            task.setMessage(errMsg);
            op.error(e);
            vplexVol.getOpStatus().updateTaskStatus(taskId, op);
            _dbClient.persistObject(vplexVol);

        }

        return task;
    }

    /**
     * Cancel a migration that has yet to be committed.
     * 
     * 
     * @prereq none
     * 
     * @param id the URN of a ViPR migration.
     * 
     * @brief Cancel an uncommitted migration.
     * @return A TaskResourceRep
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/cancel")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep cancelMigration(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, Migration.class, "id");
        Migration migration = queryResource(id);
        if (!BulkList.MigrationFilter.isUserAuthorizedForMigration(migration,
                getUserFromContext(), _permissionsHelper)) {
            StorageOSUser user = getUserFromContext();
            throw APIException.forbidden.insufficientPermissionsForUser(user.getName());
        }
        if (migration == null || migration.getInactive()) {
            throw APIException.badRequests.cancelMigrationFailed(id.toString(), "The migration is invalid");
        }
        String status = migration.getMigrationStatus();
        String migrationName = migration.getLabel();
        URI volId = migration.getVolume();
        Volume vplexVol = _dbClient.queryObject(Volume.class, volId);
        if (vplexVol == null || vplexVol.getInactive()) {
            throw APIException.badRequests.cancelMigrationFailed(migrationName, "The migrating volume is not valid");
        }

        // Don't allow cancel operation if the vplex volume is in a CG
        URI cgURI = vplexVol.getConsistencyGroup();
        if (!NullColumnValueGetter.isNullURI(cgURI)) {
            throw APIException.badRequests.cancelMigrationFailed(migrationName,
                    "Migration cancellation is not supported for the volumes in consistency group");
        }

        if (status == null || status.isEmpty() || migrationName == null || migrationName.isEmpty()) {
            throw APIException.badRequests.migrationHasntStarted(id.toString());
        }
        if (status.equalsIgnoreCase(VPlexMigrationInfo.MigrationStatus.COMMITTED.getStatusValue())) {
            throw APIException.badRequests.migrationCantBeCancelled(migrationName, status);
        }

        // Create a unique task id.
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(Volume.class,
                volId, taskId, ResourceOperationTypeEnum.CANCEL_MIGRATION);
        TaskResourceRep task = toTask(vplexVol, taskId, op);

        if (status.equalsIgnoreCase(VPlexMigrationInfo.MigrationStatus.CANCELLED.getStatusValue()) ||
                status.equalsIgnoreCase(VPlexMigrationInfo.MigrationStatus.PARTIALLY_CANCELLED.getStatusValue())) {
            // it has been cancelled
            s_logger.info("Migration {} has been cancelled", id);
            op.ready();
            vplexVol.getOpStatus().createTaskStatus(taskId, op);
            _dbClient.persistObject(vplexVol);
            return task;
        }

        try {
            VPlexController controller = _vplexBlockServiceApi.getController();

            controller.cancelMigration(vplexVol.getStorageController(), id, taskId);

        } catch (InternalException e) {
            s_logger.error("Controller Error", e);
            String errMsg = String.format("Controller Error: %s", e.getMessage());
            task.setState(Operation.Status.error.name());
            task.setMessage(errMsg);
            op.error(e);
            vplexVol.getOpStatus().updateTaskStatus(taskId, op);
            _dbClient.persistObject(vplexVol);
        }

        return task;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Migration queryResource(URI id) {
        ArgValidator.checkUri(id);
        Migration migration = _permissionsHelper.getObjectById(id, Migration.class);
        ArgValidator.checkEntityNotNull(migration, id, isIdEmbeddedInURL(id));
        return migration;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    private Volume queryVolumeResource(URI id) {
        ArgValidator.checkUri(id);
        Class<? extends DataObject> blockClazz = Volume.class;

        if (URIUtil.isType(id, BlockMirror.class)) {
            blockClazz = BlockMirror.class;
        }
        if (URIUtil.isType(id, VplexMirror.class)) {
            blockClazz = VplexMirror.class;
        }
        DataObject dataObject = _permissionsHelper.getObjectById(id, blockClazz);
        ArgValidator.checkEntityNotNull(dataObject, id, isIdEmbeddedInURL(id));
        return (Volume) dataObject;
    }

    /**
     * Determines if the passed volume supports migration.
     * 
     * @param volumeURI The URI of the volume.
     * 
     * @return true if the volume can be migrated, false otherwise.
     */
    private Volume verifyRequestedVolumeSupportsMigration(URI volumeURI) {

        // Verify the VPlex virtual volume exists and is active.
        Volume vplexVolume = _permissionsHelper.getObjectById(volumeURI, Volume.class);
        ArgValidator.checkEntity(vplexVolume, volumeURI, false);

        // Verify this is in fact a volume on a VPlex storage system.
        // If not, then the migration can't be done non-disruptively.
        StorageSystem vplexSystem = null;
        URI vplexSystemURI = vplexVolume.getStorageController();
        if (vplexSystemURI == null) {
            // Must be an RP protected volume, which uses a protection
            // controller.
            throw APIException.badRequests.requestedVolumeIsNotVplexVolume(volumeURI);
        } else {
            vplexSystem = _permissionsHelper.getObjectById(vplexSystemURI,
                    StorageSystem.class);
            if (!DiscoveredDataObject.Type.vplex.name().equals(
                    vplexSystem.getSystemType())) {
                // The volume is not a VPlex volume.
                throw APIException.badRequests.requestedVolumeIsNotVplexVolume(volumeURI);
            }
        }

        return vplexVolume;
    }

    /**
     * Gets the source volume for the migration, which is the backend volume of
     * the virtual volume that resides on the passed source storage system.
     * 
     * @param vplexVolume A reference to the VPlex virtual volume.
     * @param srcStorageSystemURI The URI of the source storage system.
     * 
     * @return A reference to the source Volume for the migration.
     */
    private Volume getMigrationSource(Volume vplexVolume, URI srcStorageSystemURI) {
        // Determine the backend volume of the passed VPlex volume that
        // is to be migrated. It is the volume on the passed source
        // storage system.
        Volume migrationSrc = null;
        StringSet assocVolumeIds = vplexVolume.getAssociatedVolumes();
        for (String assocVolumeId : assocVolumeIds) {
            Volume assocVolume = _permissionsHelper.getObjectById(
                    URI.create(assocVolumeId), Volume.class);
            if (assocVolume.getStorageController().toString()
                    .equals(srcStorageSystemURI.toString())) {
                migrationSrc = assocVolume;
                break;
            }
        }

        // Validate that we found the migration source.
        if (migrationSrc == null) {
            throw APIException.badRequests.invalidParameterVolumeNotOnSystem(vplexVolume.getId(), srcStorageSystemURI);
        }

        return migrationSrc;
    }

    /**
     * Verifies that the passed target storage system is connected to the passed
     * VPlex storage system.
     * 
     * @param vplexVolumeURI The URI of the VPlex virtual volume.
     * @param vplexSystemURI The URI of the VPlex storage system.
     * @param srcStorageSystemURI The URI of the source storage system.
     * @param tgtStorageSystemURI The URI of the target storage system.
     */
    private void verifyTargetStorageSystemForMigration(URI vplexVolumeURI,
            URI vplexSystemURI, URI srcStorageSystemURI, URI tgtStorageSystemURI) {

        // Intention is tech refresh, so the source and target systems should
        // be different.
        if (tgtStorageSystemURI.toString().equals(srcStorageSystemURI.toString())) {
            throw APIException.badRequests.targetAndSourceStorageCannotBeSame();
        }

        // Verify requested target storage system is active.
        StorageSystem tgtStorageSystem = _permissionsHelper.getObjectById(
                tgtStorageSystemURI, StorageSystem.class);
        ArgValidator.checkEntity(tgtStorageSystem, tgtStorageSystemURI, false);

        // Verify the target storage system is at least connected to the
        // VPlex storage system for the VPlex virtual volume. Technically,
        // it must be connected to the same VPlex cluster as the source
        // storage system, i.e., it must be in the same VirtualArray.
        // If it is not it will fail in the placement logic when we
        // try to get recommendations for the migration target.
        boolean isConnectedToVPlex = false;
        Set<URI> associatedVplexes = ConnectivityUtil
                .getVPlexSystemsAssociatedWithArray(_dbClient, tgtStorageSystemURI);
        if (associatedVplexes.contains(vplexSystemURI)) {
            isConnectedToVPlex = true;
        }
        if (!isConnectedToVPlex) {
            throw APIException.badRequests.storageSystemNotConnectedToCorrectVPlex(tgtStorageSystemURI, vplexSystemURI);
        }
    }

    /**
     * Gets the VirtualPool for the migration target.
     * 
     * @param requestedCosURI The VirtualPool specified in the migration request.
     * @param vplexVolume A reference to the VPlex virtual volume.
     * @param migrationSrc A reference to the migration source.
     * 
     * @return A reference to the VirtualPool for the migration target volume.
     */
    private VirtualPool getVirtualPoolForMigrationTarget(URI requestedCosURI, Volume vplexVolume,
            Volume migrationSrc) {
        // Get the VirtualPool for the migration source.
        VirtualPool cosForMigrationSrc = _permissionsHelper.getObjectById(
                migrationSrc.getVirtualPool(), VirtualPool.class);

        // Determine the VirtualPool for the migration target based on
        // the VirtualPool specified in the request, if any. Note that the
        // VirtualPool specified in the request should be the new VirtualPool for
        // the passed VPlex volume after the migration is complete.
        VirtualPool cosForMigrationTgt = null;
        if (requestedCosURI != null) {
            // Get the new VirtualPool for the virtual volume verifying
            // that the VirtualPool is valid for the project's tenant and
            // set it initially as the VirtualPool for the migration
            // target.
            Project vplexVolumeProject = _permissionsHelper.getObjectById(
                    vplexVolume.getProject(), Project.class);
            cosForMigrationTgt = BlockService.getVirtualPoolForRequest(vplexVolumeProject,
                    requestedCosURI, _dbClient, _permissionsHelper);

            // Now get the VirtualArray of the migration source volume.
            // We need to know if this is the primary volume or the HA
            // volume.
            URI migrationNhURI = migrationSrc.getVirtualArray();
            if (!migrationNhURI.toString().equals(
                    vplexVolume.getVirtualArray().toString())) {
                // The HA backend volume is being migrated.
                // The VirtualPool for the HA volume is potentially
                // specified by the HA VirtualPool map in the requested
                // VirtualPool. If not, then the VirtualPool for the HA volume
                // is the same as that of the VPlex volume.
                StringMap haNhCosMap = cosForMigrationTgt.getHaVarrayVpoolMap();
                if ((haNhCosMap != null)
                        && (haNhCosMap.containsKey(migrationNhURI.toString()))) {
                    cosForMigrationTgt = BlockService.getVirtualPoolForRequest(
                            vplexVolumeProject,
                            URI.create(haNhCosMap.get(migrationNhURI.toString())), _dbClient,
                            _permissionsHelper);
                }

                // Now verify the VirtualPool change is legitimate.
                VirtualPoolChangeAnalyzer.verifyVirtualPoolChangeForTechRefresh(cosForMigrationSrc,
                        cosForMigrationTgt);
            } else {
                // The primary or source volume is being migrated.
                // The VirtualPool for the primary volume is the same as
                // that for the VPlex volume. We still need to verify
                // this is a legitimate VirtualPool change.
                VirtualPoolChangeAnalyzer.verifyVirtualPoolChangeForTechRefresh(cosForMigrationSrc,
                        cosForMigrationTgt);
            }
        } else {
            // A new VirtualPool was not specified for the virtual volume, so
            // the VirtualPool for the migration target will be the same as that
            // for the migration source.
            cosForMigrationTgt = cosForMigrationSrc;
        }

        return cosForMigrationTgt;
    }

    /**
     * Migration is not a zone level resource
     */
    @Override
    protected boolean isZoneLevelResource() {
        return false;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.MIGRATION;
    }

    /**
     * Implements function called by bulk API to map migration instances to
     * their corresponding REST responses.
     */
    private class BlockMigrationAdapter implements Function<Migration, MigrationRestRep> {

        /**
         * {@inheritDoc}
         */
        @Override
        public MigrationRestRep apply(final Migration migration) {
            return BlockMigrationMapper.map(migration);
        }
    }

    /**
     * {@inheritDoc}
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public BlockMigrationBulkRep getBulkResources(BulkIdParam param) {
        return (BlockMigrationBulkRep) super.getBulkResources(param);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BlockMigrationBulkRep queryBulkResourceReps(List<URI> ids) {
        Iterator<Migration> _dbIterator = _dbClient.queryIterativeObjects(
                getResourceClass(), ids);
        return new BlockMigrationBulkRep(BulkList.wrapping(_dbIterator,
                new BlockMigrationAdapter()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected BlockMigrationBulkRep queryFilteredBulkResourceReps(List<URI> ids) {
        Iterator<Migration> _dbIterator = _dbClient.queryIterativeObjects(
                getResourceClass(), ids);
        return new BlockMigrationBulkRep(BulkList.wrapping(_dbIterator,
                new BlockMigrationAdapter(), new BulkList.MigrationFilter(
                        getUserFromContext(), _permissionsHelper)));
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public Class<Migration> getResourceClass() {
        return Migration.class;
    }

    /**
     * Delete a migration that has been committed or cancelled
     * 
     * 
     * @param id the URN of a ViPR migration.
     * 
     * @brief Delete a committed or cancelled migration.
     * @return A TaskResourceRep
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/deactivate")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep deleteMigration(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, Migration.class, "id");
        Migration migration = queryResource(id);
        if (!BulkList.MigrationFilter.isUserAuthorizedForMigration(migration,
                getUserFromContext(), _permissionsHelper)) {
            StorageOSUser user = getUserFromContext();
            throw APIException.forbidden.insufficientPermissionsForUser(user.getName());
        }
        String status = migration.getMigrationStatus();
        String migrationName = migration.getLabel();

        if (status == null || status.isEmpty() || migrationName == null || migrationName.isEmpty()) {
            throw APIException.badRequests.migrationHasntStarted(id.toString());
        }
        if (!status.equalsIgnoreCase(VPlexMigrationInfo.MigrationStatus.COMMITTED.getStatusValue()) &&
                !status.equalsIgnoreCase(VPlexMigrationInfo.MigrationStatus.CANCELLED.getStatusValue()) &&
                !status.equalsIgnoreCase(VPlexMigrationInfo.MigrationStatus.ERROR.getStatusValue())) {
            throw VPlexApiException.exceptions.cantRemoveMigrationInvalidState(migrationName);
        }

        URI volId = migration.getVolume();
        Volume vplexVol = _dbClient.queryObject(Volume.class, volId);

        // Create a unique task id.
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(Volume.class,
                volId, taskId, ResourceOperationTypeEnum.DELETE_MIGRATION);
        TaskResourceRep task = toTask(vplexVol, taskId, op);
        if (migration.getInactive()) {
            s_logger.info("Migration {} has been deleted", id);
            op.ready();
            vplexVol.getOpStatus().createTaskStatus(taskId, op);
            _dbClient.persistObject(vplexVol);
            return task;
        }

        try {
            VPlexController controller = _vplexBlockServiceApi.getController();

            controller.deleteMigration(vplexVol.getStorageController(), id, taskId);

        } catch (InternalException e) {
            s_logger.error("Error", e);
            String errMsg = String.format("Error: %s", e.getMessage());
            task.setState(Operation.Status.error.name());
            task.setMessage(errMsg);
            op.error(e);
            vplexVol.getOpStatus().updateTaskStatus(taskId, op);
            _dbClient.persistObject(vplexVol);
        }

        return task;
    }
}
