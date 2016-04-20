package com.emc.storageos.migrationcontroller;

import static com.emc.storageos.migrationcontroller.MigrationControllerUtils.getDataObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Migration;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.migrationorchestrationcontroller.MigrationOrchestrationInterface;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowException;

public class NativeMigrationDeviceController extends MigrationControllerImp implements MigrationOrchestrationInterface {
    private static final Logger _log = LoggerFactory.getLogger(NativeMigrationDeviceController.class);
    private DbClient _dbClient;

    private static volatile NativeMigrationDeviceController _instance;

    public NativeMigrationDeviceController() {
        _instance = this;
    }

    public static NativeMigrationDeviceController getInstance() {
        return _instance;
    }

    @Override
    public String addStepsForChangeVirtualPool(Workflow workflow, String waitFor, List<VolumeDescriptor> volumes, String taskId)
            throws InternalException {
        try {
            // Get all the General Volumes.
            List<VolumeDescriptor> generalVolumes = VolumeDescriptor.filterByType(
                    volumes,
                    new VolumeDescriptor.Type[] { VolumeDescriptor.Type.GENERAL_VOLUME },
                    new VolumeDescriptor.Type[] {});

            if (generalVolumes == null || generalVolumes.isEmpty()) {
                return waitFor;
            }

            // Find the original change vpool volumes from the descriptors.
            URI newVpoolURI = null;
            List<URI> changeVpoolGeneralVolumeURIs = new ArrayList<URI>();
            for (VolumeDescriptor generalVolume : generalVolumes) {
                if (generalVolume.getParameters() != null
                        && !generalVolume.getParameters().isEmpty()) {
                    if (newVpoolURI == null) {
                        newVpoolURI = (URI) generalVolume.getParameters().get(
                                VolumeDescriptor.PARAM_VPOOL_CHANGE_VPOOL_ID);
                    }
                    URI generalVolumeURI = (URI) generalVolume.getParameters().get(
                            VolumeDescriptor.PARAM_VPOOL_CHANGE_VOLUME_ID);
                    _log.info("Adding steps for change vpool for general volume {}", generalVolumeURI);
                    changeVpoolGeneralVolumeURIs.add(generalVolumeURI);
                }
            }

            String lastStep = waitFor;
            String waitForStep = waitFor;
            URI cgURI = null;
            List<URI> localSystemsToRemoveCG = new ArrayList<URI>();
            List<VolumeDescriptor> nativeMigrateVolumes = VolumeDescriptor.filterByType(
                    volumes,
                    new VolumeDescriptor.Type[] { VolumeDescriptor.Type.DRIVER_MIGRATE_VOLUME },
                    new VolumeDescriptor.Type[] {});
            if (nativeMigrateVolumes != null && !nativeMigrateVolumes.isEmpty()) {
                for (URI generalVolumeURI : changeVpoolGeneralVolumeURIs) {
                    _log.info("Adding migration steps for general volume {}", generalVolumeURI);

                    // A list of the volumes satisfying the new VirtualPool to
                    // which the data on the current volumes
                    // will be migrated.
                    List<URI> newVolumes = new ArrayList<URI>();

                    // A Map containing a migration for each new volume
                    Map<URI, URI> migrationMap = new HashMap<URI, URI>();

                    // A map that specifies the storage pool in which
                    // each new volume should be created.
                    Map<URI, URI> poolVolumeMap = new HashMap<URI, URI>();

                    // The URI of the storage system
                    URI storageURI = null;

                    for (VolumeDescriptor desc : nativeMigrateVolumes) {
                        // Skip migration targets that are not for the General
                        // volume being processed.
                        Migration migration = getDataObject(Migration.class, desc.getMigrationId(), _dbClient);
                        if (!migration.getVolume().equals(generalVolumeURI)) {
                            continue;
                        }

                        // We need the storage system and consistency group,
                        // which should be the same for all volumes being
                        // migrated when multiple volumes are passed.
                        if (storageURI == null) {
                            Volume generalVolume = getDataObject(Volume.class, generalVolumeURI, _dbClient);
                            storageURI = generalVolume.getStorageController();
                            cgURI = generalVolume.getConsistencyGroup();
                        }

                        // Set data required to add the migration steps.
                        newVolumes.add(desc.getVolumeURI());
                        migrationMap.put(desc.getVolumeURI(), desc.getMigrationId());
                        poolVolumeMap.put(desc.getVolumeURI(), desc.getPoolURI());

                        // If the migration is to a different storage system
                        // we may need to remove the backend CG on the source
                        // system after the migration completes. Note that the
                        // migration source is null for an ingested volume
                        // being migrated to known storage.
                        Volume migSrc = null;
                        URI migSrcURI = migration.getSource();
                        if (!NullColumnValueGetter.isNullURI(migSrcURI)) {
                            migSrc = getDataObject(Volume.class, migSrcURI, _dbClient);
                        }
                        URI migTgtURI = migration.getTarget();
                        Volume migTgt = getDataObject(Volume.class, migTgtURI, _dbClient);
                        if ((migSrc != null) && (!migTgt.getStorageController().equals(migSrc.getStorageController())) &&
                                (!localSystemsToRemoveCG.contains(migSrc.getStorageController()))) {
                            _log.info("Will remove CG on local system {}", migSrc.getStorageController());
                            localSystemsToRemoveCG.add(migSrc.getStorageController());
                        }
                    }

                    // Note that the last step here is a step group associated
                    // with deleting the migration sources after the migrations
                    // have completed and committed. This means that anything
                    // that waits on this, will occur after the migrations have
                    // completed, been committed, and the migration sources deleted.
                    try {
                        _log.info("migration controller migrate volume {} on storage system{}",
                                generalVolumeURI, storageURI);

                        waitForStep = createWorkflowStepsForMigrateGeneralVolumes(workflow, storageURI,
                                generalVolumeURI, newVolumes, newVpoolURI, null, migrationMap, waitFor);
                        _log.info("Created workflow steps for volume migration.");

                        waitForStep = createWorkflowStepsForCommitMigration(workflow, storageURI,
                                generalVolumeURI, migrationMap, waitForStep);
                        _log.info("Created workflow steps for commit migration.");

                        lastStep = createWorkflowStepsForDeleteMigrationSource(workflow, storageURI,
                                generalVolumeURI, newVpoolURI, null, migrationMap, waitForStep);
                        _log.info("Created workflow steps for commit migration.");
                    } catch (Exception e) {
                        throw MigrationControllerException.exceptions.addStepsForChangeVirtualPoolFailed(e);
                    }
                    _log.info("Add migration steps for general volume {}", generalVolumeURI);
                }

                // Add step to delete backend CG if necessary. Note that these
                // are done sequentially, else you can have issues updating the
                // systemConsistencyGroup specified for the group.
                if (!NullColumnValueGetter.isNullURI(cgURI)) {
                    _log.info("Vpool change volumes are in CG {}", cgURI);
                    lastStep = createWorkflowStepsForDeleteConsistencyGroup(workflow, cgURI, localSystemsToRemoveCG, lastStep);
                }
            }

            // Return the last step
            return lastStep;
        } catch (Exception ex) {
            throw MigrationControllerException.exceptions.addStepsForChangeVirtualPoolFailed(ex);
        }
    }

    @Override
    public String addStepsForChangeVirtualArray(Workflow workflow, String waitFor, List<VolumeDescriptor> volumes, String taskId)
            throws InternalException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String addStepsForMigrateVolumes(Workflow workflow, String waitFor, List<VolumeDescriptor> volumes, String taskId)
            throws InternalException {
        // TODO Auto-generated method stub
        return null;
    }

    public void migrateGeneralVolume(URI vplexURI, URI virtualVolumeURI,
            URI targetVolumeURI, URI migrationURI, URI newNhURI, String stepId) throws WorkflowException {

    }

    public void rollbackMigrateGeneralVolume(URI vplexURI, URI migrationURI,
            String migrateStepId, String stepId) throws WorkflowException {

    }

    public void commitMigration(URI vplexURI, URI virtualVolumeURI, URI migrationURI,
            Boolean rename, String stepId) throws WorkflowException {

    }

    public void rollbackCommitMigration(List<URI> migrationURIs, String commitStepId,
            String stepId) throws WorkflowException {

    }

    public void deleteMigrationSources(URI vplexURI, URI virtualVolumeURI,
            URI newVpoolURI, URI newVarrayURI, List<URI> migrationSources, String stepId) throws WorkflowException {

    }

    public void deleteConsistencyGroup(URI vplexURI, URI cgURI, String opId)
            throws ControllerException {

    }

}
