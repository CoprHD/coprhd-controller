package com.emc.storageos.migrationcontroller;

import static com.emc.storageos.migrationcontroller.MigrationControllerUtils.getDataObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.Serializable;
import java.net.URI;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Host;
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
import com.emc.storageos.volumecontroller.impl.block.AbstractDefaultMaskingOrchestrator;
import com.emc.storageos.volumecontroller.impl.block.BlockDeviceController;
import com.emc.storageos.volumecontroller.impl.job.QueueJob;
import com.emc.storageos.vplexcontroller.completers.MigrationTaskCompleter;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowException;
import com.emc.storageos.workflow.WorkflowService;
import com.emc.storageos.workflow.WorkflowStepCompleter;
import com.iwave.ext.linux.model.MultiPathEntry;
import com.iwave.ext.linux.model.PowerPathDevice;

public class HostMigrationDeviceController implements MigrationOrchestrationInterface {
    private static final Logger _log = LoggerFactory.getLogger(HostMigrationDeviceController.class);
    private DbClient _dbClient;
    private URI _hostURI;
    private HostExportManager _hostExportMgr;
    // private final List<Initiator> _initiators = new ArrayList<Initiator>();
    // private List<URI> migrateInitiatorsURIs = new ArrayList<URI>();
    private static volatile HostMigrationDeviceController _instance;
    private WorkflowService _workflowService;
    private MigrationControllerWorkFlowUtil _migrationControllerWorkFlowUtil;

    // Constants used for creating a migration name.
    private static final String MIGRATION_NAME_PREFIX = "M_";
    private static final String MIGRATION_NAME_DATE_FORMAT = "yyMMdd-HHmmss-SSS";

    private static final String DELETE_MIGRATION_SOURCES_WF_NAME = "deleteMigrationSources";
    private static final String DELETE_VOLUMES_METHOD_NAME = "deleteVolumes";
    private static final String UNEXPORT_STEP = AbstractDefaultMaskingOrchestrator.EXPORT_GROUP_MASKING_TASK;
    private static final String MIGRATION_VOLUME_DELETE_STEP = "delete";
    private static final String DELETE_MIGRATION_SOURCES_STEP = "deleteSources";
    private boolean _usePowerPath;

    public HostMigrationDeviceController() {
        _instance = this;
    }

    public static HostMigrationDeviceController getInstance() {
        return _instance;
    }

    public void setWorkflowService(WorkflowService workflowService) {
        this._workflowService = workflowService;
    }

    public void setMigrationControllerWrokFlowUtil(MigrationControllerWorkFlowUtil migrationControllerWorkFlowUtil) {
        this._migrationControllerWorkFlowUtil = migrationControllerWorkFlowUtil;

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
                                VolumeDescriptor.PARAM_VPOOL_CHANGE_NEW_VPOOL_ID);
                    }
                    URI generalVolumeURI = (URI) generalVolume.getParameters().get(
                            VolumeDescriptor.PARAM_VPOOL_CHANGE_EXISTING_VOLUME_ID);
                    _log.info("Adding steps for change vpool for general volume {}", generalVolumeURI);
                    changeVpoolGeneralVolumeURIs.add(generalVolumeURI);
                }
            }

            // currently not consider RP.

            // todo: get _hostURI from descriptors;

            URI cgURI = null;
            String lastStep = waitFor;
            List<URI> localSystemsToRemoveCG = new ArrayList<URI>();

            List<VolumeDescriptor> hostMigrateVolumes = VolumeDescriptor.filterByType(
                    volumes,
                    new VolumeDescriptor.Type[] { VolumeDescriptor.Type.HOST_MIGRATE_VOLUME },
                    new VolumeDescriptor.Type[] {});

            if (hostMigrateVolumes != null && !hostMigrateVolumes.isEmpty()) {
                _hostURI = null;
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
                        if (_hostURI == null){
                            _hostURI = migration.getMigrationHost();
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
                    // export source volumes
                    waitFor = _migrationControllerWorkFlowUtil.createWorkflowStepsForBlockVolumeExport(workflow, waitFor,
                            Arrays.asList(generalVolumeURI), _hostURI, taskId);
                    _log.info("Created workflow steps for volume export.");
                    // Note that the last step here is a step group associated
                    // with deleting the migration sources after the migrations
                    // have completed and committed. This means that anything
                    // that waits on this, will occur after the migrations have
                    // completed, been committed, and the migration sources deleted.
                    lastStep = addStepsForMigrateVolumes(workflow, _hostURI,
                            generalVolumeURI, newVolumes, migrationMap,
                            newVpoolURI, null, taskId, waitFor);
                    _log.info("Add migration steps for general volume {}", generalVolumeURI);

                }

                // Add step to delete backend CG if necessary. Note that these
                // are done sequentially, else you can have issues updating the
                // systemConsistencyGroup specified for the group.
                cgURI = getDataObject(Volume.class, changeVpoolGeneralVolumeURIs.get(0), _dbClient).getConsistencyGroup();
                if (!NullColumnValueGetter.isNullURI(cgURI)) {
                    _log.info("Vpool change volumes are in CG {}", cgURI);
                    lastStep = _migrationControllerWorkFlowUtil.createWorkflowStepsForDeleteConsistencyGroup(workflow, cgURI,
                            localSystemsToRemoveCG, lastStep);
                }
            }

            // Return the last step
            return lastStep;
        } catch (Exception ex) {
            throw MigrationControllerException.exceptions.addStepsForChangeVirtualPoolFailed(ex);
        }
    }

    private String addStepsForMigrateVolumes(Workflow workflow, URI hostURI, URI generalVolumeURI,
            List<URI> targetVolumeURIs, Map<URI, URI> migrationsMap,
            URI newVpoolURI, URI newVarrayURI, String opId, String waitFor)
            throws InternalException {

        try {
            _log.info("migration controller migrate volume {} by host{}",
                    generalVolumeURI, _hostURI);

            waitFor = _migrationControllerWorkFlowUtil.createWorkflowStepsForBlockVolumeExport(workflow, waitFor,
                    targetVolumeURIs, _hostURI, opId);
            _log.info("Created workflow steps for volume export.");

            waitFor = _migrationControllerWorkFlowUtil.createWorkflowStepsForMigrateGeneralVolumes(workflow, _hostURI,
                    generalVolumeURI, targetVolumeURIs, newVpoolURI, newVarrayURI, migrationsMap, waitFor);
            _log.info("Created workflow steps for volume migration.");

            String waitForStep = waitFor;
            waitForStep = _migrationControllerWorkFlowUtil.createWorkflowStepsForCommitMigration(workflow, _hostURI,
                    generalVolumeURI, migrationsMap, waitForStep);
            _log.info("Created workflow steps for commit migration.");

            _migrationControllerWorkFlowUtil.createWorkflowStepsForDeleteMigrationSource(workflow, _hostURI,
                    generalVolumeURI, newVpoolURI, newVarrayURI, migrationsMap, waitForStep);
            _log.info("Created workflow steps for delete migration source.");
            return DELETE_MIGRATION_SOURCES_STEP;
        } catch (Exception e) {
            throw MigrationControllerException.exceptions.addStepsForChangeVirtualPoolFailed(e);
        }
    }

    @Override
    public String addStepsForChangeVirtualArray(Workflow workflow, String waitFor, List<VolumeDescriptor> volumes, String taskId)
            throws InternalException {
        try {
            URI cgURI = null;
            URI tgtVarrayURI = null;

            // todo: get host URI from descriptors

            List<URI> generalVolumeURIs = new ArrayList<URI>();
            // Get all the general Volumes and the new varray URI.
            List<VolumeDescriptor> generalVolumeDescriptors = VolumeDescriptor.filterByType(
                    volumes,
                    new VolumeDescriptor.Type[] { VolumeDescriptor.Type.GENERAL_VOLUME },
                    new VolumeDescriptor.Type[] {});
            for (VolumeDescriptor generalVolumeDescriptor : generalVolumeDescriptors) {

                URI generalVolumeURI = generalVolumeDescriptor.getVolumeURI();
                _log.info("Add steps to change virtual array for volume {}", generalVolumeURI);
                generalVolumeURIs.add(generalVolumeURI);

                // Set the target virtual array if not already set.
                if (tgtVarrayURI == null) {
                    if ((generalVolumeDescriptor.getParameters() != null) &&
                            (!generalVolumeDescriptor.getParameters().isEmpty())) {
                        tgtVarrayURI = (URI) generalVolumeDescriptor.getParameters().get(
                                VolumeDescriptor.PARAM_VARRAY_CHANGE_NEW_VAARAY_ID);
                        _log.info("Target virtual array for varray change is {}", tgtVarrayURI);
                    }
                }

            }
            String lastStep = waitFor;
            // Create steps to migrate the backend volumes.
            List<URI> localSystemsToRemoveCG = new ArrayList<URI>();
            List<VolumeDescriptor> hostMigrateVolumes = VolumeDescriptor.filterByType(
                    volumes, new VolumeDescriptor.Type[] { VolumeDescriptor.Type.HOST_MIGRATE_VOLUME },
                    new VolumeDescriptor.Type[] {});

            if (hostMigrateVolumes != null && !hostMigrateVolumes.isEmpty()) {
                _hostURI = null;

                for (URI generalVolumeURI : generalVolumeURIs) {
                    _log.info("Adding migration steps for general volume {}", generalVolumeURI);

                    // A list of the volumes to which the data on the current
                    // backend volumes will be migrated.
                    List<URI> newVolumes = new ArrayList<URI>();

                    // A Map containing a migration for each new backend
                    // volume
                    Map<URI, URI> migrationMap = new HashMap<URI, URI>();

                    for (VolumeDescriptor desc : hostMigrateVolumes) {
                        // Skip migration targets that are not for the VPLEX
                        // volume being processed.
                        Migration migration = getDataObject(Migration.class, desc.getMigrationId(), _dbClient);
                        if (!migration.getVolume().equals(generalVolumeURI)) {
                            continue;
                        }
                        if(_hostURI == null){
                            _hostURI = migration.getMigrationHost();
                        }

                        _log.info("Found migration {} for general volume", migration.getId());

                        // Set data required to add the migration steps.
                        newVolumes.add(desc.getVolumeURI());
                        migrationMap.put(desc.getVolumeURI(), desc.getMigrationId());

                        // If the migration is to a different storage system
                        // we may need to remove the backend CG on the source
                        // system after the migration completes.
                        URI migSrcURI = migration.getSource();
                        Volume migSrc = getDataObject(Volume.class, migSrcURI, _dbClient);
                        URI migTgtURI = migration.getTarget();
                        Volume migTgt = getDataObject(Volume.class, migTgtURI, _dbClient);
                        if ((!migTgt.getStorageController().equals(migSrc.getStorageController())) &&
                                (!localSystemsToRemoveCG.contains(migSrc.getStorageController()))) {
                            _log.info("Will remove CG on local system {} if volume is in a CG.", migSrc.getStorageController());
                            localSystemsToRemoveCG.add(migSrc.getStorageController());
                        }
                    }
                   // export source volumes
                   waitFor = _migrationControllerWorkFlowUtil.createWorkflowStepsForBlockVolumeExport(workflow, waitFor,
                            Arrays.asList(generalVolumeURI), _hostURI, taskId);
                    _log.info("Created workflow steps for volume export.");

                    // Note that the migrate step here is a step group associated
                    // with deleting the migration sources after the migrations
                    // have completed and committed. This means that anything
                    // that waits on this, will occur after the migrations have
                    // completed, been committed, and the migration sources deleted.
                    lastStep = addStepsForMigrateVolumes(workflow, _hostURI,
                            generalVolumeURI, newVolumes, migrationMap,
                            null, tgtVarrayURI, taskId, waitFor);
                    _log.info("Add migration steps for general volume {}", generalVolumeURI);

                }
                cgURI = getDataObject(Volume.class, generalVolumeURIs.get(0), _dbClient).getConsistencyGroup();
                if (!NullColumnValueGetter.isNullURI(cgURI)) {
                    _log.info("Vpool change volumes are in CG {}", cgURI);
                    lastStep = _migrationControllerWorkFlowUtil.createWorkflowStepsForDeleteConsistencyGroup(workflow, cgURI,
                            localSystemsToRemoveCG, lastStep);
                }

            }
            // Return the last step
            return lastStep;
        } catch (Exception ex) {
            throw MigrationControllerException.exceptions.addStepsForChangeVirtualArrayFailed(ex);
        }
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

    public void migrateGeneralVolume(URI hostURI, URI generalVolumeURI,
            URI targetVolumeURI, URI migrationURI, URI newVarrayURI, String stepId) throws WorkflowException {
        _log.info("Migration {} using target {}", migrationURI, targetVolumeURI);

        try {
            // Update step state to executing.
            WorkflowStepCompleter.stepExecuting(stepId);

            // Initialize the step data. The step data indicates if we
            // successfully started the migration and is used in
            // rollback.
            _workflowService.storeStepData(stepId, Boolean.FALSE);

            Host host = getDataObject(Host.class, hostURI, _dbClient);

            // Get the general volume.
            Volume generalVolume = getDataObject(Volume.class, generalVolumeURI, _dbClient);
            String generalVolumeName = generalVolume.getDeviceLabel();
            _log.info("general volume name is {}", generalVolumeName);

            // Setup the native volume info for the migration target.
            Volume migrationTarget = getDataObject(Volume.class, targetVolumeURI, _dbClient);
            _log.info("Storage system for migration target is {}",
                    migrationTarget.getStorageController());

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

            // Set up the migration scripts on the migration host
            Map<String, String> migrationScriptMap = new HashMap<String, String>();

            try {
                String migrateVolumeScript = new Scanner(
                        new File("migrationScripts/migrateVolume.sh")).useDelimiter("\\Z").next();
                String pollMigrationScript = new Scanner(
                        new File("migrationScripts/pollMigration.sh")).useDelimiter("\\Z").next();
                migrationScriptMap.put(migrateVolumeScript, "/tmp/coprhdMigration/migrateVolume.sh");
                migrationScriptMap.put(pollMigrationScript, "/tmp/coprhdMigration/pollMigration.sh");
            } catch (FileNotFoundException e) {
                // This should never occur unless the migration scripts were manually
                // deleted from the source code.
                _log.error("Migration scripts not found");
            }

            HostMigrationCommand.copyMigrationScriptsToHost(host, migrationScriptMap);

            // Start the migration
            List<MigrationInfo> migrationInfoList = hostMigrateGeneralVolume(migration,
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
            MigrationJob migrationJob = new MigrationJob(migrationCompleter, host);
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

    private List<MigrationInfo> hostMigrateGeneralVolume(Migration migration, String migrationName, Volume srcVolume,
            Volume tgtVolume) throws Exception {
        // List<Initiator> hostInitiators = ComputeSystemHelper.queryInitiators(_dbClient, _hostURI);
        // todo: precheck mountpoint, multipath, filesystem, refresh storage

        Host host = getDataObject(Host.class, _hostURI, _dbClient);
        try {
            _usePowerPath = checkForMultipathingSoftware(host);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        refreshHostStorage(host, _usePowerPath);

        String srcDevice = getDevice(host, srcVolume, _usePowerPath);
        String tgtDevice = getDevice(host, tgtVolume, _usePowerPath);
        _log.info("linux.migration.block.volume", srcDevice, tgtDevice);
        migration.setSrcDev(srcDevice);
        migration.setTgtDev(tgtDevice);
        _dbClient.updateObject(migration);
        String migrationPid = HostMigrationCommand.migrationCommand(host, srcDevice, tgtDevice);
        migration.setMigrationPid(migrationPid);
        _dbClient.updateObject(migration);
        _log.info("Successfully started migration {}", migrationName);
        MigrationInfo migrationInfo = HostMigrationCommand.pollMigration(host, migrationName, migrationPid);
        return Arrays.asList(migrationInfo);

    }

    private void refreshHostStorage(Host host, boolean usePowerPath) {
        if (_usePowerPath)
            HostMigrationCommand.updatePowerPathEntries(host);
        else
            HostMigrationCommand.updateMultiPathEntries(host);
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
            } catch (IllegalStateException e) {
                String errorMessage = String.format("Unable to find device for WWN %s. %s more attempts will be made.",
                        volume.getWWN(), remainingAttempts);
                if (remainingAttempts == 0) {
                    throw new Exception(errorMessage);
                }
                _log.warn("linux.support.device.not.found", volume.getWWN(), remainingAttempts);
                Thread.sleep(5000);
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
        Host host = getDataObject(Host.class, _hostURI, _dbClient);
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
                cancelMigrations(host, Arrays.asList(migration.getLabel()), Arrays.asList(migration), true, true);
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

    private void cancelMigrations(Host host, List<String> migrationNames, List<Migration> migrations,
            boolean cleanup, boolean remove) throws Exception {
        _log.info("Canceling migrations {}", migrationNames);
        List<MigrationInfo> migrationInfoList = HostMigrationCommand.pollMigrations(host, migrations);
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
                } catch (Exception vae) {
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
        Host host = getDataObject(Host.class, _hostURI, _dbClient);
        try {
            // Update step state to executing.
            WorkflowStepCompleter.stepExecuting(stepId);

            // Get the migration.
            migration = getDataObject(Migration.class, migrationURI, _dbClient);
            if (!MigrationInfo.MigrationStatus.COMMITTED.getStatusValue().equals(
                    migration.getMigrationStatus())) {

                Volume generalVolume = getDataObject(Volume.class, generalVolumeURI, _dbClient);
                try {
                    String commitMigrationStatus = HostMigrationCommand.doCommitMigrationsCommand(host, 
                            generalVolume.getDeviceLabel(), Arrays.asList(migration));
                    if (commitMigrationStatus != "SUCCESS_STATUS") {
                        if (commitMigrationStatus == "ASYNC_STATUS") {
                            _log.info("commitMigration is asynchronously");
                        } else {
                            throw new Exception("host migration cancel failed");
                        }
                    }

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
        // Update step state to executing.
        WorkflowStepCompleter.stepExecuting(stepId);
        Host host = getDataObject(Host.class, _hostURI, _dbClient);
        try {
            // Determine if any migration was successfully committed.
            boolean migrationCommitted = false;
            Iterator<URI> migrationIter = migrationURIs.iterator();
            while (migrationIter.hasNext()) {
                URI migrationURI = migrationIter.next();
                Migration migration = _dbClient.queryObject(Migration.class, migrationURI);
                if (MigrationInfo.MigrationStatus.COMMITTED.getStatusValue().equals(migration.getMigrationStatus())) {
                    migrationCommitted = true;
                    continue;
                }
                String migrationPid = migration.getMigrationPid();
                MigrationInfo migrationInfo = HostMigrationCommand.pollMigration(host, migration.getLabel(), migrationPid);
                if (migrationInfo.getStatus().equalsIgnoreCase(MigrationInfo.MigrationStatus.COMMITTED.name())) {
                    migrationCommitted = true;
                    migration.setMigrationStatus(MigrationInfo.MigrationStatus.COMMITTED.name());
                    _dbClient.updateObject(migration);
                    // Clear the internal flag for the source volume, making it visible so that
                    // it can be deleted if desired by the user.
                    // setOrClearVolumeInternalFlag(migration.getSource(), false);
                    continue;
                }
            }

            // All we want to do is prevent further rollback if any migration
            // has been committed so that we don't end up deleting the migration
            // targets of the committed migrations, which now hold the data.
            // If the migration is not committed, then rollback of the migration
            // creation step will cancel the migration.
            if (migrationCommitted) {
                _log.info("Migration is committed, failing rollback");
                // Don't allow rollback to go further than the first error.
                _workflowService.setWorkflowRollbackContOnError(stepId, false);
                String opName = ResourceOperationTypeEnum.ROLLBACK_COMMIT_VOLUME_MIGRATION.getName();
                ServiceError serviceError = MigrationControllerException.errors.rollbackCommitMigration(opName);
                WorkflowStepCompleter.stepFailed(stepId, serviceError);
            } else {
                _log.info("No Migrations are not committed");
                WorkflowStepCompleter.stepSucceded(stepId);
            }
        } catch (Exception e) {
            _log.info("Exception determining commit rollback state", e);
            // Don't allow rollback to go further than the first error.
            _workflowService.setWorkflowRollbackContOnError(stepId, false);
            String opName = ResourceOperationTypeEnum.ROLLBACK_COMMIT_VOLUME_MIGRATION.getName();
            ServiceError serviceError = MigrationControllerException.errors.rollbackCommitMigration(opName);
            WorkflowStepCompleter.stepFailed(stepId, serviceError);
        }

    }

    public void deleteMigrationSources(URI hostURI, URI generalVolumeURI,
            URI newVpoolURI, URI newVarrayURI, List<URI> migrationSources, String stepId) throws WorkflowException {
        try {
            WorkflowStepCompleter.stepExecuting(stepId);

            if (!migrationSources.isEmpty()) {
                // Now create and execute the sub workflow to delete the
                // migration source volumes if we have any. If the volume
                // migrated was ingested VPLEX volume we will not have
                // the sources.
                Workflow subWorkflow = _workflowService.getNewWorkflow(this,
                        DELETE_MIGRATION_SOURCES_WF_NAME, true, UUID.randomUUID().toString());

                // Creates steps to remove the migration source volumes from all
                // export groups containing them and delete them.
                boolean unexportStepsAdded = _hostExportMgr.addUnexportVolumeWfSteps(subWorkflow,
                        null, migrationSources, null);

                // Only need to wait for unexport if there was a step for it added
                // to the workflow.
                String waitFor = null;
                if (unexportStepsAdded) {
                    waitFor = UNEXPORT_STEP;

                    // If the migration sources are unexported, Add a step to
                    // forget these backend volumes.
                    // addStepToForgetVolumes(subWorkflow, vplexURI, migrationSources, waitFor);
                }

                // Add steps to delete the volumes.
                Iterator<URI> migrationSourcesIter = migrationSources.iterator();
                while (migrationSourcesIter.hasNext()) {
                    URI migrationSourceURI = migrationSourcesIter.next();
                    _log.info("Migration source URI is {}", migrationSourceURI);
                    Volume migrationSource = _dbClient.queryObject(Volume.class,
                            migrationSourceURI);
                    URI sourceSystemURI = migrationSource.getStorageController();
                    _log.info("Source storage system URI is {}", sourceSystemURI);
                    StorageSystem sourceSystem = _dbClient.queryObject(StorageSystem.class,
                            sourceSystemURI);

                    String subWFStepId = subWorkflow.createStepId();
                    Workflow.Method deleteVolumesMethod = new Workflow.Method(
                            DELETE_VOLUMES_METHOD_NAME, sourceSystemURI,
                            Arrays.asList(migrationSourceURI));
                    _log.info("Creating workflow step to delete source");
                    subWorkflow.createStep(MIGRATION_VOLUME_DELETE_STEP, String.format(
                            "Delete volume from storage system: %s", sourceSystemURI),
                            waitFor, sourceSystemURI, sourceSystem.getSystemType(),
                            BlockDeviceController.class, deleteVolumesMethod, null, subWFStepId);
                    _log.info("Created workflow step to delete source");
                }

                // Execute this sub workflow.
                DeleteMigrationSourcesCallback wfCallback = new DeleteMigrationSourcesCallback();
                subWorkflow.executePlan(null, "Deleted migration sources", wfCallback,
                        new Object[] { stepId }, null, null);
            } else {
                // No sources to delete. Must have migrated an ingested volume.
                WorkflowStepCompleter.stepSucceded(stepId);
                _log.info("Updated workflow step to success");
            }
        } catch (Exception ex) {
            // Log the error.
            _log.error("Error deleting migration sources", ex);

            // Always return success. This is a cleanup step after a
            // successfully committed migration. We don't want rollback,
            // so we return success.
            WorkflowStepCompleter.stepSucceded(stepId);
        }

    }

    public void deleteConsistencyGroup(URI hostURI, URI cgURI, String opId)
            throws ControllerException {

    }

    /**
     * Callback handler for the delete migrations source sub workflow. The
     * handler is informed when the workflow completes at which point we simply
     * update the workflow step step state in the main workflow.
     */
    @SuppressWarnings("serial")
    private static class DeleteMigrationSourcesCallback implements
            Workflow.WorkflowCallbackHandler, Serializable {

        /**
         * {@inheritDoc}
         * 
         * @throws WorkflowException
         */
        @Override
        public void workflowComplete(Workflow workflow, Object[] args) throws WorkflowException {
            _log.info("Delete migration workflow completed.");

            // Simply update the workflow step in the main workflow that caused
            // the sub workflow to execute. The delete migration sources sub
            // workflow is a cleanup step after a successfully committed
            // migration. We don't want rollback, so we return success
            // regardless of the result after the sub workflow has completed.
            WorkflowStepCompleter.stepSucceded(args[0].toString());
        }
    }
}
    

        




