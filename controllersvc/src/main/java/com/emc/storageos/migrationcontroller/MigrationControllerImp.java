package com.emc.storageos.migrationcontroller;

import static com.emc.storageos.migrationcontroller.MigrationControllerUtils.getDataObject;
import static com.emc.storageos.migrationcontroller.MigrationControllerUtils.getVolumesVarray;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.model.Migration;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.util.BlockConsistencyGroupUtils;
import com.emc.storageos.networkcontroller.impl.NetworkDeviceController;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.impl.block.BlockDeviceController;
import com.emc.storageos.volumecontroller.impl.block.ExportMaskPlacementDescriptor;
import com.emc.storageos.volumecontroller.placement.BlockStorageScheduler;
import com.emc.storageos.workflow.Workflow;

public class MigrationControllerImp implements MigrationController {
    private static final Logger _log = LoggerFactory.getLogger(HostMigrationDeviceController.class);
    private DbClient _dbClient;
    private static final String MIGRATE_GENERAL_VOLUME_METHOD_NAME = "migrateGeneralVolume";
    private static final String RB_MIGRATE_GENERAL_VOLUME_METHOD_NAME = "rollbackMigrateGeneralVolume";
    private static final String COMMIT_MIGRATION_METHOD_NAME = "commitMigration";
    private static final String RB_COMMIT_MIGRATION_METHOD_NAME = "rollbackCommitMigration";
    private static final String DELETE_MIGRATION_SOURCES_METHOD = "deleteMigrationSources";
    private static final String MIGRATE_VOLUME_EXPORT_METHOD_NAME = "migrateVolumeExport";
    private static final String RB_MIGRATE_VOLUME_EXPORT_METHOD_NAME = "rollbackMigrateVolumeExport";

    private static final String DELETE_MIGRATION_SOURCES_STEP = "deleteSources";
    private static final String MIGRATION_CREATE_STEP = "migrate";
    private static final String MIGRATION_COMMIT_STEP = "commit";
    private static final String MIGRATION_VOLUME_EXPORT_STEP = "exportVolume";

    private BlockDeviceController _blockDeviceController;
    private BlockStorageScheduler _blockScheduler;
    private NetworkDeviceController _networkDeviceController;
    @Override
    public String createWorkflowStepsForBlockVolumeExport(Workflow workflow, URI storageURI,
            List<URI> targetVolumeURIs, String waitFor)
            throws InternalException {
        try {
            String lastStep = waitFor;
            StorageSystem storageSystem = getDataObject(StorageSystem.class, storageURI, _dbClient);
            _log.info("Got storage system");

            Map<URI, Volume> volumeMap = new HashMap<URI, Volume>();
            Map<URI, StorageSystem> storageSystemMap = new HashMap<URI, StorageSystem>();
            for (URI tgtvolumeURI : targetVolumeURIs) {
                Volume tgtvolume = getDataObject(Volume.class, tgtvolumeURI, _dbClient);
                volumeMap.put(tgtvolumeURI, tgtvolume);
                StorageSystem targetStorageSystem = getDataObject(StorageSystem.class, tgtvolume.getStorageController(), _dbClient);
                storageSystemMap.put(tgtvolume.getStorageController(), targetStorageSystem);
            }

            // to do .........
            // Set the project and tenant.
            Volume firstVolume = volumeMap.values().iterator().next();
            URI projectURI = firstVolume.getProject().getURI();
            URI tenantURI = firstVolume.getTenant().getURI();
            _log.info("Project is {}, Tenant is {}", projectURI, tenantURI);

            // Main processing containers. ExportGroup --> StorageSystem --> Volumes
            // Populate the container for the export workflow step generation
            for (Map.Entry<URI, StorageSystem> storageEntry : storageSystemMap.entrySet()) {
                URI tgtstorageSystemURI = storageEntry.getKey();
                StorageSystem tgtstorageSystem = storageEntry.getValue();
                URI varray = getVolumesVarray(tgtstorageSystem, volumeMap.values());
                _log.info(String.format("Creating ExportGroup for storage system %s (%s) in Virtual Aarray[(%s)]",
                        tgtstorageSystem.getLabel(), tgtstorageSystemURI, varray));

                if (varray == null) {
                    // For whatever reason, there were no Volumes for this Storage System found, so we
                    // definitely do not want to create anything. Log a warning and continue.
                    _log.warn(String.format("No Volumes for storage system %s (%s), no need to create an ExportGroup.",
                            tgtstorageSystem.getLabel(), tgtstorageSystemURI));
                    continue;
                }

                // todo: return the storage ports on the host machine that should be used
                // for a particular storage array. this is down by finding ports in host machine
                // and array that have common network. (verify network connection between host port and array)

                HostExportManager hostExportMgr = new HostExportManager(_dbClient, this, _blockDeviceController,
                        _blockScheduler, _networkDeviceController, projectURI, tenantURI);

                ExportMaskPlacementDescriptor descriptor = hostExportMgr.chooseBackendExportMask(storageSystem,
                        tgtstorageSystem, varray, volumeMap, lastStep);

                // todo: If there are no networks that can be zoned, error.
                String stepId = workflow.createStepId();
                _log.info("export opId is {}", stepId);
                Workflow.Method exportMigrationExecuteMethod = new Workflow.Method(
                        MIGRATE_VOLUME_EXPORT_METHOD_NAME, storageSystem,
                        tgtstorageSystem, varray);
                Workflow.Method exportMigrationRollbackMethod = new Workflow.Method(
                        RB_MIGRATE_VOLUME_EXPORT_METHOD_NAME, storageSystem, tgtstorageSystem, stepId);
                _log.info("Creating workflow export step");
                workflow.createStep(MIGRATION_VOLUME_EXPORT_STEP, String.format(
                        "storagesystem %s migrating volume", storageSystem.getId().toString()),
                        waitFor, storageSystem.getId(), storageSystem.getSystemType(),
                        getClass(), exportMigrationExecuteMethod, exportMigrationRollbackMethod, stepId);
                _log.info("Created workflow migration step");

            }

            return MIGRATION_VOLUME_EXPORT_STEP;
        } catch (Exception e) {
            throw MigrationControllerException.exceptions.addStepsForChangeVirtualPoolFailed(e);
        }

    }


    @Override
    public String createWorkflowStepsForMigrateGeneralVolumes(Workflow workflow, URI storageURI,
            URI generalVolumeURI, List<URI> targetVolumeURIs, URI newVpoolURI, URI newVarrayURI,
            Map<URI, URI> migrationsMap, String waitFor) throws InternalException {
        try {
            StorageSystem storageSystem = getDataObject(StorageSystem.class, storageURI, _dbClient);
            // Now make a migration Step for each passed target to which data
            // for the passed virtual volume will be migrated. The migrations
            // will be done from this controller.
            Iterator<URI> targetVolumeIter = targetVolumeURIs.iterator();
            while (targetVolumeIter.hasNext()) {
                URI targetVolumeURI = targetVolumeIter.next();
                _log.info("Target volume is {}", targetVolumeURI);
                URI migrationURI = migrationsMap.get(targetVolumeURI);
                _log.info("Migration is {}", migrationURI);
                String stepId = workflow.createStepId();
                _log.info("Migration opId is {}", stepId);
                Workflow.Method generalMigrationExecuteMethod = new Workflow.Method(
                        MIGRATE_GENERAL_VOLUME_METHOD_NAME, storageURI, generalVolumeURI,
                        targetVolumeURI, migrationURI, newVarrayURI);
                Workflow.Method generalMigrationRollbackMethod = new Workflow.Method(
                        RB_MIGRATE_GENERAL_VOLUME_METHOD_NAME, storageURI, migrationURI, stepId);
                _log.info("Creating workflow migration step");
                workflow.createStep(MIGRATION_CREATE_STEP, String.format(
                        "storagesystem %s migrating volume", storageSystem.getId().toString()),
                        waitFor, storageSystem.getId(), storageSystem.getSystemType(),
                        getClass(), generalMigrationExecuteMethod, generalMigrationRollbackMethod, stepId);
                _log.info("Created workflow migration step");
            }
            return MIGRATION_CREATE_STEP;
        } catch (Exception e) {
            throw MigrationControllerException.exceptions.addStepsForChangeVirtualPoolFailed(e);
        }

    }

    @Override
    public String createWorkflowStepsForCommitMigration(Workflow workflow, URI storageURI,
            URI generalVolumeURI, Map<URI, URI> migrationsMap, String waitFor)
            throws InternalException {
        try {
            StorageSystem storageSystem = getDataObject(StorageSystem.class, storageURI, _dbClient);
            // Once the migrations complete, we will commit the migrations.
            // So, now we create the steps to commit the migrations.
            List<URI> migrationURIs = new ArrayList<URI>(migrationsMap.values());
            Iterator<URI> migrationsIter = migrationsMap.values().iterator();
            while (migrationsIter.hasNext()) {
                URI migrationURI = migrationsIter.next();
                _log.info("Migration is {}", migrationURI);
                Migration migration = getDataObject(Migration.class, migrationURI, _dbClient);
                Boolean rename = Boolean.TRUE;
                if (migration.getSource() == null) {
                    rename = Boolean.FALSE;
                }
                _log.info("Added migration source {}", migration.getSource());
                String stepId = workflow.createStepId();
                _log.info("Commit operation id is {}", stepId);
                Workflow.Method commitMigrationExecuteMethod = new Workflow.Method(
                        COMMIT_MIGRATION_METHOD_NAME, storageURI, generalVolumeURI,
                        migrationURI, rename);
                Workflow.Method commitMigrationRollbackMethod = new Workflow.Method(
                        RB_COMMIT_MIGRATION_METHOD_NAME, migrationURIs, stepId);
                _log.info("Creating workflow step to commit migration");
                waitFor = workflow.createStep(MIGRATION_COMMIT_STEP, String.format(
                        "storage sysmtem %s committing volume migration",
                        storageSystem.getId().toString()),
                        waitFor, storageSystem.getId(),
                        storageSystem.getSystemType(), getClass(), commitMigrationExecuteMethod,
                        commitMigrationRollbackMethod, stepId);
                _log.info("Created workflow step to commit migration");
            }
            return waitFor;
        } catch (Exception e) {
            throw MigrationControllerException.exceptions.addStepsForChangeVirtualPoolFailed(e);
        }
    }

    @Override
    public String createWorkflowStepsForDeleteMigrationSource(Workflow workflow, URI storageURI,
            URI generalVolumeURI, URI newVpoolURI, URI newVarrayURI, Map<URI, URI> migrationsMap,
            String waitFor) throws InternalException {
        try {
            StorageSystem storageSystem = getDataObject(StorageSystem.class, storageURI, _dbClient);
            List<URI> migrationSources = new ArrayList<URI>();
            Iterator<URI> migrationsIter = migrationsMap.values().iterator();
            while (migrationsIter.hasNext()) {
                URI migrationURI = migrationsIter.next();
                _log.info("Migration is {}", migrationURI);
                Migration migration = getDataObject(Migration.class, migrationURI, _dbClient);
                if (migration.getSource() != null) {
                    migrationSources.add(migration.getSource());
                }
            }
            String stepId = workflow.createStepId();
            Workflow.Method deleteMigrationExecuteMethod = new Workflow.Method(
                    DELETE_MIGRATION_SOURCES_METHOD, storageURI, generalVolumeURI,
                    newVpoolURI, newVarrayURI, migrationSources);
            workflow.createStep(DELETE_MIGRATION_SOURCES_STEP,
                    String.format("Creating workflow to delete migration sources"),
                    waitFor, storageSystem.getId(), storageSystem.getSystemType(),
                    getClass(), deleteMigrationExecuteMethod, null, stepId);
            _log.info("Created workflow step to create sub workflow for source deletion");

            return DELETE_MIGRATION_SOURCES_STEP;
        } catch (Exception e) {
            throw MigrationControllerException.exceptions.addStepsForChangeVirtualPoolFailed(e);
        }

    }

    @Override
    public String createWorkflowStepsForDeleteConsistencyGroup(Workflow workflow, URI cgURI,
            List<URI> localSystemsToRemoveCG, String lastStep) throws InternalException {

        BlockConsistencyGroup cg = getDataObject(BlockConsistencyGroup.class, cgURI, _dbClient);
        if (cg.checkForType(Types.LOCAL)) {
            _log.info("CG {} has local type", cgURI);
            // If any of the VPLEX volumes involved in the vpool change
            // is in a VPLEX CG with corresponding local CGs for the backend
            // volumes, then it is required that all VPLEX volumes in the
            // CG are part of the vpool change. If the backend volumes are being
            // migrated to a new storage system, then we need to add a step
            // to delete the local CG.
            boolean localCGDeleted = false;
            List<URI> localSystemURIs = BlockConsistencyGroupUtils.getLocalSystems(cg, _dbClient);
            for (URI localSystemURI : localSystemURIs) {
                _log.info("CG exists on local system {}", localSystemURI);
                if (localSystemsToRemoveCG.contains(localSystemURI)) {
                    localCGDeleted = true;
                    _log.info("Adding step to remove CG on local system {}", localSystemURI);
                    StorageSystem localSystem = getDataObject(StorageSystem.class, localSystemURI, _dbClient);
                    Workflow.Method deleteCGMethod = new Workflow.Method(
                            "deleteConsistencyGroup", localSystemURI, cgURI, Boolean.FALSE);
                    workflow.createStep("deleteLocalCG", String.format(
                            "Delete consistency group from storage system: %s", localSystemURI),
                            lastStep, localSystemURI, localSystem.getSystemType(),
                            BlockDeviceController.class, deleteCGMethod, null,
                            null);
                }
            }
            if (localCGDeleted) {
                lastStep = "deleteLocalCG";
            }
        }
        return lastStep;
    }

}
