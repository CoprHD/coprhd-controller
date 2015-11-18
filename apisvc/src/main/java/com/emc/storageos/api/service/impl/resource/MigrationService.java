/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.BlockMapper.map;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.BlockMigrationMapper;
import com.emc.storageos.api.service.impl.placement.VPlexScheduler;
import com.emc.storageos.api.service.impl.resource.utils.VirtualPoolChangeAnalyzer;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.Migration;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.BlockMigrationBulkRep;
import com.emc.storageos.model.block.MigrationList;
import com.emc.storageos.model.block.MigrationParam;
import com.emc.storageos.model.block.MigrationRestRep;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
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

    // A logger reference.
    private static final Logger s_logger = LoggerFactory
            .getLogger(MigrationService.class);

    /**
     * Setter for the VPlex BlockServiceApi called through Spring configuration.
     * 
     * @param vplexBlockServiceApi A reference to the BlockServiceApi for VPlex.
     */
    public void setVplexBlockServiceApi(VPlexBlockServiceApiImpl vplexBlockServiceApi) {
        _vplexBlockServiceApi = vplexBlockServiceApi;
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
     * 
     * @brief Perform a non-disruptive migration for a VPLEX volume.
     * @return A TaskResourceRep for the volume being migrated.
     * @throws InternalException
     */
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
        VirtualArray migrationTgtNh = _permissionsHelper.getObjectById(
                migrationSrc.getVirtualArray(), VirtualArray.class);
        s_logger.debug("Migration target VirtualArray is {}", migrationTgtNh.getId());

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
                migrationTgtNh, requestedVPlexSystems, migrateParam.getTgtStorageSystem(),
                migrationTgtCos, false, null, null, cosWrapper);
        if (recommendations.isEmpty()) {
            throw APIException.badRequests.noStorageFoundForVolumeMigration(migrationTgtCos.getId(), migrationTgtNh.getId(),
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
        Volume migrationTgt = _vplexBlockServiceApi.prepareVolumeForRequest(
                migrationSrc.getCapacity(), migrationTgtProject, migrationTgtNh,
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
        String status = migration.getMigrationStatus();
        String migrationName = migration.getLabel();
        URI volId = migration.getVolume();
        Volume vplexVol = _dbClient.queryObject(Volume.class, volId);

        if (status == null || status.isEmpty() || migrationName == null || migrationName.isEmpty()) {
            throw APIException.badRequests.migrationHasntStarted(id.toString());
        }
        if (status.equalsIgnoreCase(VPlexMigrationInfo.MigrationStatus.COMMITTED.getStatusValue())){
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
