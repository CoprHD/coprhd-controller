package com.emc.storageos.migrationcontroller;

import static com.emc.storageos.migrationcontroller.MigrationControllerUtils.getDataObject;

import java.net.URI;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.computesystemcontroller.impl.ComputeSystemHelper;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Migration;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.migrationorchestrationcontroller.MigrationOrchestrationInterface;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.job.QueueJob;
import com.emc.storageos.vplex.api.clientdata.VolumeInfo;
import com.emc.storageos.vplexcontroller.VPlexControllerUtils;
import com.emc.storageos.vplexcontroller.completers.MigrationTaskCompleter;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowException;
import com.emc.storageos.workflow.WorkflowService;
import com.emc.storageos.workflow.WorkflowStepCompleter;

public class HostMigrationDeviceController extends MigrationControllerImp implements MigrationOrchestrationInterface {
    private static final Logger _log = LoggerFactory.getLogger(HostMigrationDeviceController.class);
    private DbClient _dbClient;
    private URI _hostURI;
    // private final List<Initiator> _initiators = new ArrayList<Initiator>();
    // private List<URI> migrateInitiatorsURIs = new ArrayList<URI>();

    private static volatile HostMigrationDeviceController _instance;
    private WorkflowService _workflowService;
    // Constants used for creating a migration name.
    private static final String MIGRATION_NAME_PREFIX = "M_";
    private static final String MIGRATION_NAME_DATE_FORMAT = "yyMMdd-HHmmss-SSS";

    public HostMigrationDeviceController() {
        _instance = this;
    }

    public static HostMigrationDeviceController getInstance() {
        return _instance;
    }

    public void setMigrateInitiatorsURIs(List<URI> initiatorsUris) {
        // migrateInitiatorsURIs = initiatorsUris;
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

            URI cgURI = null;
            List<URI> localSystemsToRemoveCG = new ArrayList<URI>();

            List<VolumeDescriptor> hostMigrateVolumes = VolumeDescriptor.filterByType(
                    volumes,
                    new VolumeDescriptor.Type[] { VolumeDescriptor.Type.HOST_MIGRATE_VOLUME },
                    new VolumeDescriptor.Type[] {});

            List<Initiator> hostInitiators = ComputeSystemHelper.queryInitiators(_dbClient, _hostURI);

            // export source volumes
            if (hostMigrateVolumes != null && !hostMigrateVolumes.isEmpty()) {
                lastStep = createWorkflowStepsForBlockVolumeExport(workflow, changeVpoolGeneralVolumeURIs,
                        _hostURI, lastStep);
                _log.info("Created workflow steps for volume export.");
            }

            String waitForStep = lastStep;
            if (hostMigrateVolumes != null && !hostMigrateVolumes.isEmpty()) {
                for (URI generalVolumeURI : changeVpoolGeneralVolumeURIs) {
                    _log.info("Adding migration steps for general volume {}", generalVolumeURI);

                    // A list of the volumes satisfying the new VirtualPool to
                    // which the data on the current volumes
                    // will be migrated.
                    List<URI> newVolumes = new ArrayList<URI>();

                    // A Map containing a migration for each new volume
                    Map<URI, URI> migrationMap = new HashMap<URI, URI>();

                    for (VolumeDescriptor desc : hostMigrateVolumes) {
                        // Skip migration targets that are not for the General
                        // volume being processed.
                        Migration migration = getDataObject(Migration.class, desc.getMigrationId(), _dbClient);
                        if (!migration.getVolume().equals(generalVolumeURI)) {
                            continue;
                        }

                        // Set data required to add the migration steps.
                        newVolumes.add(desc.getVolumeURI());
                        migrationMap.put(desc.getVolumeURI(), desc.getMigrationId());

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
                        _log.info("migration controller migrate volume {} by storage system{}",
                                generalVolumeURI, _hostURI);

                        waitForStep = createWorkflowStepsForBlockVolumeExport(workflow,
                                newVolumes, _hostURI, waitFor);
                        _log.info("Created workflow steps for volume export.");

                        waitForStep = createWorkflowStepsForMigrateGeneralVolumes(workflow, _hostURI,
                                generalVolumeURI, newVolumes, newVpoolURI, null, migrationMap, waitForStep);
                        _log.info("Created workflow steps for volume migration.");

                        waitForStep = createWorkflowStepsForCommitMigration(workflow, _hostURI,
                                generalVolumeURI, migrationMap, waitForStep);
                        _log.info("Created workflow steps for commit migration.");

                        lastStep = createWorkflowStepsForDeleteMigrationSource(workflow, _hostURI,
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
                cgURI = getDataObject(Volume.class, changeVpoolGeneralVolumeURIs.get(0), _dbClient).getConsistencyGroup();
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

    public void migrateVolumeExport(URI hostURI, List<URI> volumeURIs, String stepId) throws WorkflowException {

        HostExportManager hostExportMgr = new HostExportManager();
        hostExportMgr.exportOrchestrationSteps(this, volumeURIs, hostURI, stepId);
    }

    public void migrateGeneralVolume(URI storageURI, URI generalVolumeURI,
            URI targetVolumeURI, URI migrationURI, URI newVarrayURI, String stepId) throws WorkflowException {
        _log.info("Migration {} using target {}", migrationURI, targetVolumeURI);

        try {
            // Update step state to executing.
            WorkflowStepCompleter.stepExecuting(stepId);

            // Initialize the step data. The step data indicates if we
            // successfully started the migration and is used in
            // rollback.
            _workflowService.storeStepData(stepId, Boolean.FALSE);

            // Get the general volume.
            Volume generalVolume = getDataObject(Volume.class, generalVolumeURI, _dbClient);
            String generalVolumeName = generalVolume.getDeviceLabel();
            _log.info("general volume name is {}", generalVolumeName);
            StorageSystem srcStorageSystem = getDataObject(StorageSystem.class,
                    generalVolume.getStorageController(), _dbClient);
            _log.info("Storage system for migration source is {}",
                    generalVolume.getStorageController());
            List<String> srcVolumeitls = VPlexControllerUtils.getVolumeITLs(generalVolume);
            VolumeInfo srcVolumeInfo = new VolumeInfo(
                    srcStorageSystem.getNativeGuid(), srcStorageSystem.getSystemType(), generalVolume.getWWN()
                            .toUpperCase().replaceAll(":", ""),
                    generalVolume.getNativeId(),
                    generalVolume.getThinlyProvisioned().booleanValue(), srcVolumeitls);


            // Setup the native volume info for the migration target.
            Volume migrationTarget = getDataObject(Volume.class, targetVolumeURI, _dbClient);
            StorageSystem targetStorageSystem = getDataObject(StorageSystem.class,
                    migrationTarget.getStorageController(), _dbClient);
            _log.info("Storage system for migration target is {}",
                    migrationTarget.getStorageController());
            List<String> tgtVolumeitls = VPlexControllerUtils.getVolumeITLs(migrationTarget);
            VolumeInfo tgtVolumeInfo = new VolumeInfo(
                    targetStorageSystem.getNativeGuid(), targetStorageSystem.getSystemType(), migrationTarget.getWWN()
                            .toUpperCase().replaceAll(":", ""),
                    migrationTarget.getNativeId(),
                    migrationTarget.getThinlyProvisioned().booleanValue(), tgtVolumeitls);

            // Get the migration associated with the target.
            Migration migration = getDataObject(Migration.class, migrationURI, _dbClient);

            // Determine the unique name for the migration. We identifying
            // the migration source and target, using array serial number
            // and volume native id, in the migration name. This was fine
            // for VPlex extent migration, which has a max length of 63
            // for the migration name. However, for remote migrations,
            // which require VPlex device migration, the max length is much
            // more restrictive, like 20 characters. So, we switched over
            // timestamps.
            StringBuilder migrationNameBuilder = new StringBuilder(MIGRATION_NAME_PREFIX);
            DateFormat dateFormatter = new SimpleDateFormat(MIGRATION_NAME_DATE_FORMAT);
            migrationNameBuilder.append(dateFormatter.format(new Date()));
            String migrationName = migrationNameBuilder.toString();
            migration.setLabel(migrationName);
            _dbClient.updateObject(migration);
            _log.info("Migration name is {}", migrationName);

            // Make a call to the VPlex API client to migrate the virtual
            // volume. Note that we need to do a remote migration when a
            // local virtual volume is being migrated to the other VPlex
            // cluster. If the passed new varray is not null, then
            // this is the case.
            // Boolean isRemoteMigration = newVarrayURI != null;

            List<MigrationInfo> migrationInfoList = hostMigrateGeneralVolume(
                    migrationName, Arrays.asList(srcVolumeInfo), Arrays.asList(tgtVolumeInfo));
            _log.info("Started host migration");

            // We store step data indicating that the migration was successfully
            // create and started. We will use this to determine the behavior
            // on rollback. If we never got to the point that the migration
            // was created and started, then there is no rollback to attempt
            // on the VLPEX as the migrate API already tried to clean everything
            // up on the VLPEX.
            _workflowService.storeStepData(stepId, Boolean.TRUE);

            // Initialize the migration info in the database.
            MigrationInfo migrationInfo = migrationInfoList.get(0);
            migration.setMigrationStatus(MigrationInfo.MigrationStatus.READY
                    .getStatusValue());
            migration.setPercentDone("0");
            migration.setStartTime(migrationInfo.getStartTime());
            _dbClient.updateObject(migration);
            _log.info("Update migration info");

            // Create a migration task completer and queue a job to monitor
            // the migration progress. The completer will be invoked by the
            // job when the migration completes.
            MigrationTaskCompleter migrationCompleter = new MigrationTaskCompleter(
                    migrationURI, stepId);
            MigrationJob migrationJob = new MigrationJob(migrationCompleter);
            ControllerServiceImpl.enqueueJob(new QueueJob(migrationJob));
            _log.info("Queued job to monitor migration progress.");
        } catch (MigrationControllerException vae) {
            _log.error("Exception migrating general volume: " + vae.getMessage(), vae);
            WorkflowStepCompleter.stepFailed(stepId, vae);
        } catch (Exception ex) {
            _log.error("Exception migrating general volume: " + ex.getMessage(), ex);
            String opName = ResourceOperationTypeEnum.HOST_MIGRATE_VOLUME.getName();
            ServiceError serviceError = MigrationControllerException.errors.migrateVirtualVolume(opName, ex);
            WorkflowStepCompleter.stepFailed(stepId, serviceError);
        }
    }

    private List<MigrationInfo> hostMigrateGeneralVolume(String migrationName, List<VolumeInfo> srcVolumeInfo,
            List<VolumeInfo> tgtVolumeInfo) {

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
    

        




