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
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Migration;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.migrationorchestrationcontroller.MigrationOrchestrationInterface;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.job.QueueJob;
import com.emc.storageos.vplex.api.VPlexApiException;
import com.emc.storageos.vplexcontroller.completers.MigrationTaskCompleter;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowException;
import com.emc.storageos.workflow.WorkflowService;
import com.emc.storageos.workflow.WorkflowStepCompleter;
import com.iwave.ext.linux.LinuxSystemCLI;
import com.iwave.ext.linux.model.MultiPathEntry;
import com.iwave.ext.linux.model.PowerPathDevice;

public class HostMigrationDeviceController implements MigrationOrchestrationInterface {
    private static final Logger _log = LoggerFactory.getLogger(HostMigrationDeviceController.class);
    private DbClient _dbClient;
    private URI _hostURI;
    private Host _host;
    private HostExportManager _hostExportMgr;
    // private final List<Initiator> _initiators = new ArrayList<Initiator>();
    // private List<URI> migrateInitiatorsURIs = new ArrayList<URI>();
    protected LinuxSystemCLI _linuxSystem;
    private static volatile HostMigrationDeviceController _instance;
    private WorkflowService _workflowService;
    // Constants used for creating a migration name.
    private static final String MIGRATION_NAME_PREFIX = "M_";
    private static final String MIGRATION_NAME_DATE_FORMAT = "yyMMdd-HHmmss-SSS";

    private boolean _usePowerPath;

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
        MigrationControllerWrokFlowUtil migrationControllerWrokFlowUtil = new MigrationControllerWrokFlowUtil();
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
            _hostExportMgr = new HostExportManager();
            // export source volumes
            if (hostMigrateVolumes != null && !hostMigrateVolumes.isEmpty()) {
                lastStep = migrationControllerWrokFlowUtil.createWorkflowStepsForBlockVolumeExport(workflow, lastStep,
                        changeVpoolGeneralVolumeURIs,
                        _hostURI, taskId);
                _log.info("Created workflow steps for volume export.");
            }
            _host = getDataObject(Host.class, _hostURI, _dbClient);
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

                        waitForStep = migrationControllerWrokFlowUtil.createWorkflowStepsForBlockVolumeExport(workflow, lastStep,
                                newVolumes, _hostURI, taskId);
                        _log.info("Created workflow steps for volume export.");

                        waitForStep = migrationControllerWrokFlowUtil.createWorkflowStepsForMigrateGeneralVolumes(workflow, _hostURI,
                                generalVolumeURI, newVolumes, newVpoolURI, null, migrationMap, waitForStep);
                        _log.info("Created workflow steps for volume migration.");

                        waitForStep = migrationControllerWrokFlowUtil.createWorkflowStepsForCommitMigration(workflow, _hostURI,
                                generalVolumeURI, migrationMap, waitForStep);
                        _log.info("Created workflow steps for commit migration.");

                        lastStep = migrationControllerWrokFlowUtil.createWorkflowStepsForDeleteMigrationSource(workflow, _hostURI,
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
                    lastStep = migrationControllerWrokFlowUtil.createWorkflowStepsForDeleteConsistencyGroup(workflow, cgURI,
                            localSystemsToRemoveCG, lastStep);
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

    public void migrateVolumeExport(URI hostURI, List<URI> volumeURIs, String stepId) {

        _hostExportMgr.exportOrchestrationSteps(this, volumeURIs, hostURI, stepId);
    }

    public void rollbackMigrateVolumeExport(URI parentWorkflow, String exportOrchestrationStepId, String token) {
        _hostExportMgr.exportOrchestrationRollbackSteps(parentWorkflow, exportOrchestrationStepId, token);
    }

    public void hostMigrateVolumeExport(String stepId) {

        _hostExportMgr.hostExportOrchestrationSteps(stepId);
    }

    public void rollbackHostMigrateVolumeExport(String stepId) {
        _hostExportMgr.hostExportOrchestrationRollbackSteps(stepId);
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
            /*
             * StorageSystem srcStorageSystem = getDataObject(StorageSystem.class,
             * generalVolume.getStorageController(), _dbClient);
             */
            _log.info("Storage system for migration source is {}",
                    generalVolume.getStorageController());
            /*
             * List<String> srcVolumeitls = VPlexControllerUtils.getVolumeITLs(generalVolume);
             * VolumeInfo srcVolumeInfo = new VolumeInfo(
             * srcStorageSystem.getNativeGuid(), srcStorageSystem.getSystemType(), generalVolume.getWWN()
             * .toUpperCase().replaceAll(":", ""),
             * generalVolume.getNativeId(),
             * generalVolume.getThinlyProvisioned().booleanValue(), srcVolumeitls);
             */


            // Setup the native volume info for the migration target.
            Volume migrationTarget = getDataObject(Volume.class, targetVolumeURI, _dbClient);
            /*
             * StorageSystem targetStorageSystem = getDataObject(StorageSystem.class,
             * migrationTarget.getStorageController(), _dbClient);
             */
            _log.info("Storage system for migration target is {}",
                    migrationTarget.getStorageController());
            /*
             * List<String> tgtVolumeitls = VPlexControllerUtils.getVolumeITLs(migrationTarget);
             * VolumeInfo tgtVolumeInfo = new VolumeInfo(
             * targetStorageSystem.getNativeGuid(), targetStorageSystem.getSystemType(), migrationTarget.getWWN()
             * .toUpperCase().replaceAll(":", ""),
             * migrationTarget.getNativeId(),
             * migrationTarget.getThinlyProvisioned().booleanValue(), tgtVolumeitls);
             */

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
                    migrationName, generalVolume, migrationTarget);
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
            MigrationJob migrationJob = new MigrationJob(migrationCompleter, _host);
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

    private List<MigrationInfo> hostMigrateGeneralVolume(String migrationName, Volume srcVolume,
            Volume tgtVolume) throws Exception {
        // List<Initiator> hostInitiators = ComputeSystemHelper.queryInitiators(_dbClient, _hostURI);
        // todo: precheck mountpoint, multipath, filesystem, refresh storage

        try {
            _usePowerPath = checkForMultipathingSoftware(_host);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        String srcDevice = getDevice(_host, srcVolume, _usePowerPath);
        String tgtDevice = getDevice(_host, tgtVolume, _usePowerPath);
        _log.info("linux.migration.block.volume", srcDevice, tgtDevice);

        String MigrationStatus = HostMigrationCommand.migrationCommand(_host, migrationName, srcDevice, tgtDevice);
        if (MigrationStatus != "SUCCESS_STATUS") {
            if(MigrationStatus == "ASYNC_STATUS"){
                _log.info("host migration is completing asynchronously");
            }else {
                throw new Exception("host migration failed");
            }                       
        }    
        _log.info("Successfully started migration {}", migrationName);
        MigrationInfo migrationInfo = HostMigrationCommand.findMigration(_host, migrationName);
        return Arrays.asList(migrationInfo);

    }

    private String getDevice(Host host, Volume volume, boolean usePowerPath) throws Exception {
        // we will retry this up to 5 times
        int remainingAttempts = 5;
        while (remainingAttempts-- >= 0) {
            try {
                if (usePowerPath) {
                    return findPowerPathEntry(host, volume).getDevice();
                }
                else {
                    return getDeviceForEntry(findMultiPathEntry(host, volume));
                }
            } catch (Exception e) {
                String errorMessage = String.format("Unable to find device for WWN %s. %s more attempts will be made.",
                        volume.getWWN(), remainingAttempts);
                if (remainingAttempts == 0) {
                    throw new Exception(errorMessage);
                }
                _log.warn("linux.support.device.not.found", volume.getWWN(), remainingAttempts);
                // refreshStorage(Collections.singleton(volume), usePowerPath);
            }
        }

        return null;
    }


    private static String getDeviceForEntry(MultiPathEntry entry) {
        return String.format("/dev/mapper/%s", entry.getName());
    }

    public PowerPathDevice findPowerPathEntry(Host host, Volume volume) throws Exception {
        PowerPathDevice entry = HostMigrationCommand.FindPowerPathEntryForVolume(host, volume);
        return entry;
    }

    public MultiPathEntry findMultiPathEntry(Host host, Volume volume) throws Exception {
        MultiPathEntry entry = HostMigrationCommand.FindMultiPathEntryForVolume(host, volume);
        return entry;
    }

    private boolean checkForMultipathingSoftware(Host host) {
        String powerPathError = HostMigrationCommand.checkForPowerPath(host);
        if (powerPathError == null) {
            return true;
        }

        String multipathError = HostMigrationCommand.checkForMultipath(host);
        if (multipathError == null) {
            return false;
        }
        _log.info("failTask.LinuxSupport.noMultipath", new Object[] {}, powerPathError, multipathError);
        return false;

    }

    public void rollbackMigrateGeneralVolume(URI hostURI, URI migrationURI,
            String migrateStepId, String stepId) throws WorkflowException {
        Migration migration = null;
        String migrationVolumeLabel = null;
        try {
            // Update step state to executing.
            WorkflowStepCompleter.stepExecuting(stepId);

            // Was the migration created and started? If so, then
            // we'll try and cancel the migration and clean up.
            // Otherwise, there is nothing to do.
            Boolean migrationStarted = (Boolean) _workflowService.loadStepData(migrateStepId);
            if (!migrationStarted.booleanValue()) {
                // The migration was not successfully started.
                WorkflowStepCompleter.stepSucceded(stepId);
                return;
            }

            // Get the migration.
            migration = _dbClient.queryObject(Migration.class, migrationURI);

            // Get the general volume for the migration.
            Volume migrationVolume = _dbClient.queryObject(Volume.class, migration.getVolume());
            if (migrationVolume != null) {
                migrationVolumeLabel = migrationVolume.getLabel();
            }

            // The migration could have failed due to an error or it may have
            // failed because it was cancelled outside the scope of the
            // workflow. Check the status, and if it's not cancelled, try and
            // cancel it now.
            if (!MigrationInfo.MigrationStatus.CANCELLED.getStatusValue().equals(
                    migration.getMigrationStatus())) {
                _log.info("Cancel migration {}", migrationURI);

                // Try to cancel the migration and cleanup and remove any
                // remnants of the migration.
                cancelMigrations(_host, Arrays.asList(migration.getLabel()), true, true);
                _log.info("Migration cancelled");
            }
            WorkflowStepCompleter.stepSucceded(stepId);
        } catch (MigrationControllerException vae) {
            // Do not allow rollback to go any further COP-21257
            _workflowService.setWorkflowRollbackContOnError(stepId, false);
            _log.error("Error during rollback of start migration: {}", vae.getMessage(), vae);
            if (migration != null) {
                vae = MigrationControllerException.exceptions.migrationRollbackFailure(
                        migration.getVolume().toString(), migrationVolumeLabel, migration.getLabel());
            }
            WorkflowStepCompleter.stepFailed(stepId, vae);
        } catch (Exception e) {
            _log.error("Error during rollback of start migration: {}", e.getMessage());
            // Do not allow rollback to go any further COP-21257
            _workflowService.setWorkflowRollbackContOnError(stepId, false);
            if (migration != null) {
                e = MigrationControllerException.exceptions.migrationRollbackFailure(
                        migration.getVolume().toString(), migrationVolumeLabel, migration.getLabel());
            }
            WorkflowStepCompleter.stepFailed(stepId,
                    MigrationControllerException.exceptions.rollbackMigrateVolume(migrationURI.toString(), e));
        }
    }

    private void cancelMigrations(Host host, List<String> migrationNames, boolean cleanup,
            boolean remove) throws Exception {
        _log.info("Canceling migrations {}", migrationNames);
        List<MigrationInfo> migrationInfoList = HostMigrationCommand.findMigrations(host, migrationNames);
        // Verify that the migrations are in a state in which they can be
        // canceled.
        StringBuilder migrationArgBuilder = new StringBuilder();
        for (MigrationInfo migrationInfo : migrationInfoList) {
            String migrationStatus = migrationInfo.getStatus();
            if (migrationStatus == "cancelled"
                    || migrationStatus == "partially-cancelled") {
                // Skip those already canceled or in the process of being
                // canceled.
                continue;
            } else if ((migrationStatus != "paused")
                    && (migrationStatus != "in-progress")
                    && (migrationStatus != "complete")
                    && (migrationStatus != "error")
                    && (migrationStatus != "queued")) {
                throw MigrationControllerException.exceptions
                        .cantCancelMigrationInvalidState(migrationInfo.getName());
            }
            if (migrationArgBuilder.length() != 0) {
                migrationArgBuilder.append(",");
            }
            migrationArgBuilder.append(migrationInfo.getPath());
        }

        // If the migration paths argument is empty, then all the requested
        // migrations must already be in progress, so just return.
        String migrationPaths = migrationArgBuilder.toString();
        if (migrationPaths.length() == 0) {
            _log.info("All requested migrations are already canceled or " +
                    "in the process of being canceled.");
            return;
        }

        // Cancel the migrations.

        try {
            _log.info("Canceling migrations");
            String cancelMigrationStatus = HostMigrationCommand.cancelMigrationsCommand(host, migrationArgBuilder.toString());
            if (cancelMigrationStatus != "SUCCESS_STATUS") {
                if (cancelMigrationStatus == "ASYNC_STATUS") {
                    _log.info("host migration is cancled asynchronously");
                } else {
                    throw new Exception("host migration cancel failed");
                }
            }
            _log.info("Successfully canceled migrations {}", migrationNames);

            // If specified, cleanup the target device/extents depending on
            // whether this was a device or extent migration.
            if (cleanup) {
                for (MigrationInfo migrationInfo : migrationInfoList) {
                    try {
                        String targetName = migrationInfo.getTarget();
                        if (migrationInfo.getIsHostMigration()) {
                            String deleteDeviceStatus = HostMigrationCommand.deleteHostDevice(host, targetName);
                            if (deleteDeviceStatus != "SUCCESS_STATUS")
                                throw new Exception("host migration delete device failed");
                        }
                    } catch (Exception vae) {
                        _log.error(
                                "Error cleaning target for canceled migration {}:{}",
                                migrationInfo.getName(), vae.getMessage());
                    }
                }
            }

            // If specified, try and remove the migration records.
            if (remove) {
                try {
                    removeCommittedOrCanceledMigrations(host, migrationArgBuilder.toString());
                } catch (VPlexApiException vae) {
                    _log.error(
                            "Error removing migration records after successful cancel: {}",
                            vae.getMessage(), vae);
                }
            }
        } catch (MigrationControllerException e) {
            throw MigrationControllerException.exceptions.failedCancelMigrations(migrationNames, e);
        }
    }

    private void removeCommittedOrCanceledMigrations(Host host, String args) throws Exception {
        String removeMigrationStatus = HostMigrationCommand.removeCommittedOrCanceledMigrations(host, args);
        if (removeMigrationStatus != "SUCCESS_STATUS")
            throw new Exception("host migration remove migration recorder failed");
    }

    public void commitMigration(URI hostURI, URI generalVolumeURI, URI migrationURI,
            Boolean rename, String stepId) throws WorkflowException {
        _log.info("Committing migration {}", migrationURI);
        Migration migration = null;
        try {
            // Update step state to executing.
            WorkflowStepCompleter.stepExecuting(stepId);

            // Get the migration.
            migration = getDataObject(Migration.class, migrationURI, _dbClient);
            if (!MigrationInfo.MigrationStatus.COMMITTED.getStatusValue().equals(
                    migration.getMigrationStatus())) {

                Volume generalVolume = getDataObject(Volume.class, generalVolumeURI, _dbClient);
                try {
                    String commitMigrationStatus = HostMigrationCommand.doCommitMigrationsCommand(_host, generalVolume.getDeviceLabel(),
                            Arrays.asList(migration.getLabel()), true, true, rename.booleanValue());
                    _log.info("Committed migration {}", migration.getLabel());
                } catch (MigrationControllerException vae) {
                    _log.error("Exception committing VPlex migration: " + vae.getMessage(), vae);
                    WorkflowStepCompleter.stepFailed(stepId, vae);
                    return;
                }
                // Initialize the migration info in the database.
                migration.setMigrationStatus(MigrationInfo.MigrationStatus.COMMITTED.getStatusValue());
                _dbClient.updateObject(migration);
                _log.info("Update migration status to committed");

            } else {
                _log.info("The migration is already committed.");
            }
            // Update the workflow step status.
            StringBuilder successMsgBuilder = new StringBuilder();
            successMsgBuilder.append("Host System: ");
            successMsgBuilder.append(_hostURI);
            successMsgBuilder.append(" migration: ");
            successMsgBuilder.append(migrationURI);
            successMsgBuilder.append(" was committed");
            _log.info(successMsgBuilder.toString());
            WorkflowStepCompleter.stepSucceded(stepId);
            _log.info("Updated workflow step state to success");
        } catch (MigrationControllerException vae) {
            _log.error("Exception committing host migration: " + vae.getMessage(), vae);
            WorkflowStepCompleter.stepFailed(stepId, vae);
        } catch (Exception ex) {
            _log.error("Exception committing host migration: " + ex.getMessage(), ex);
            String opName = ResourceOperationTypeEnum.COMMIT_VOLUME_MIGRATION.getName();
            ServiceError serviceError = MigrationControllerException.errors.commitMigrationFailed(opName, ex);
            WorkflowStepCompleter.stepFailed(stepId, serviceError);
        }
        
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
    

        




