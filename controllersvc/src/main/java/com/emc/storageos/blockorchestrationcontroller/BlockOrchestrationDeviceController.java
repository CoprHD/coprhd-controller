/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.blockorchestrationcontroller;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.Controller;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Migration;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPDeviceController;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPHelper;
import com.emc.storageos.srdfcontroller.SRDFDeviceController;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.ControllerLockingService;
import com.emc.storageos.volumecontroller.impl.block.BlockDeviceController;
import com.emc.storageos.volumecontroller.impl.block.ReplicaDeviceController;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotRestoreCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeCreateWorkflowCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeVarrayChangeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeVpoolChangeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeWorkflowCompleter;
import com.emc.storageos.vplexcontroller.VPlexDeviceController;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowException;
import com.emc.storageos.workflow.WorkflowService;

public class BlockOrchestrationDeviceController implements BlockOrchestrationController, Controller {
    private static final Logger s_logger = LoggerFactory.getLogger(BlockOrchestrationDeviceController.class);
    private WorkflowService _workflowService;
    private static DbClient s_dbClient;
    private static BlockDeviceController _blockDeviceController;
    private static VPlexDeviceController _vplexDeviceController;
    private static RPDeviceController _rpDeviceController;
    private static SRDFDeviceController _srdfDeviceController;
    private static ReplicaDeviceController _replicaDeviceController;
    private ControllerLockingService _locker;

    static final String CREATE_VOLUMES_WF_NAME = "CREATE_VOLUMES_WORKFLOW";
    static final String DELETE_VOLUMES_WF_NAME = "DELETE_VOLUMES_WORKFLOW";
    static final String EXPAND_VOLUMES_WF_NAME = "EXPAND_VOLUMES_WORKFLOW";
    static final String RESTORE_VOLUME_FROM_SNAPSHOT_WF_NAME = "RESTORE_VOLUME_FROM_SNAPSHOT_WORKFLOW";
    static final String CHANGE_VPOOL_WF_NAME = "CHANGE_VPOOL_WORKFLOW";
    static final String CHANGE_VARRAY_WF_NAME = "CHANGE_VARRAY_WORKFLOW";

    /*
     * (non-Javadoc)
     *
     * @see com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationController#createVolumes(java.util.List, java.lang.String)
     */
    @Override
    public void createVolumes(List<VolumeDescriptor> volumes, String taskId) throws ControllerException {
        List<URI> volUris = VolumeDescriptor.getVolumeURIs(volumes);
        VolumeCreateWorkflowCompleter completer = new VolumeCreateWorkflowCompleter(volUris, taskId, volumes);
        Workflow workflow = null;
        try {
            // Generate the Workflow.
            workflow = _workflowService.getNewWorkflow(this,
                    CREATE_VOLUMES_WF_NAME, false, taskId);
            String waitFor = null;    // the wait for key returned by previous call

            s_logger.info("Generating steps for create Volume");
            // First, call the BlockDeviceController to add its methods.
            waitFor = _blockDeviceController.addStepsForCreateVolumes(
                    workflow, waitFor, volumes, taskId);

            s_logger.info("Checking for SRDF steps");
            // Call the SRDFDeviceController to add its methods if there are SRDF volumes.
            waitFor = _srdfDeviceController.addStepsForCreateVolumes(
                    workflow, waitFor, volumes, taskId);

            s_logger.info("Checking for VPLEX steps");
            // Call the VPlexDeviceController to add its methods if there are VPLEX volumes.
            waitFor = _vplexDeviceController.addStepsForCreateVolumes(
                    workflow, waitFor, volumes, taskId);

            s_logger.info("Checking for RP steps");
            // Call the RPDeviceController to add its methods if there are RP protections
            waitFor = _rpDeviceController.addStepsForCreateVolumes(
                    workflow, waitFor, volumes, taskId);

            s_logger.info("Checking for Replica steps");
            // Call the ReplicaDeviceController to add its methods if volumes are added to CG, and the CG associated with replication group(s)
            waitFor = _replicaDeviceController.addStepsForCreateVolumes(
                    workflow, waitFor, volumes, taskId);

            // Finish up and execute the plan.
            // The Workflow will handle the TaskCompleter
            String successMessage = "Create volumes successful for: " + volUris.toString();
            Object[] callbackArgs = new Object[] { volUris };
            workflow.executePlan(completer, successMessage, new WorkflowCallback(), callbackArgs, null, null);
        } catch (Exception ex) {
            s_logger.error("Could not create volumes: " + volUris, ex);
            releaseWorkflowLocks(workflow);
            String opName = ResourceOperationTypeEnum.CREATE_BLOCK_VOLUME.getName();
            ServiceError serviceError = DeviceControllerException.errors.createVolumesFailed(
                    volUris.toString(), opName, ex);
            completer.error(s_dbClient, _locker, serviceError);
        }
    }

    @SuppressWarnings("serial")
    private static class WorkflowCallback implements Workflow.WorkflowCallbackHandler, Serializable {
        @SuppressWarnings("unchecked")
        @Override
        public void workflowComplete(Workflow workflow, Object[] args)
                throws WorkflowException {
            List<URI> volumes = (List<URI>) args[0];
            String msg = BlockDeviceController.getVolumesMsg(s_dbClient, volumes);
            s_logger.info("Processed volumes:\n" + msg);
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationController#deleteVolumes(java.util.List, java.lang.String)
     */
    @Override
    public void deleteVolumes(List<VolumeDescriptor> volumes, String taskId) throws ControllerException {
        List<URI> volUris = VolumeDescriptor.getVolumeURIs(volumes);
        VolumeWorkflowCompleter completer = new VolumeWorkflowCompleter(volUris, taskId);
        Workflow workflow = null;

        try {
            // Generate the Workflow.
            workflow = _workflowService.getNewWorkflow(this,
                    DELETE_VOLUMES_WF_NAME, true, taskId);
            String waitFor = null;    // the wait for key returned by previous call

            // Call the ReplicaDeviceController to add its methods if volumes are removed from, and the CG associated with replication group(s)
            waitFor = _replicaDeviceController.addStepsForDeleteVolumes(
                    workflow, waitFor, volumes, taskId);

            // Call the RPDeviceController to add its methods if there are RP protections.
            waitFor = _rpDeviceController.addStepsForDeleteVolumes(
                    workflow, waitFor, volumes, taskId);

            // Call the VPlexDeviceController to add its methods if there are VPLEX volumes.
            waitFor = _vplexDeviceController.addStepsForDeleteVolumes(
                    workflow, waitFor, volumes, taskId);

            // Call the SRDFDeviceController to add its methods if there are SRDF volumes.
            waitFor = _srdfDeviceController.addStepsForDeleteVolumes(
                    workflow, waitFor, volumes, taskId);

            // Next, call the BlockDeviceController to add its methods.
            waitFor = _blockDeviceController.addStepsForDeleteVolumes(
                    workflow, waitFor, volumes, taskId);
          
            // Call the VPlexDeviceController to add its post-delete methods.
            waitFor = _vplexDeviceController.addStepsForPostDeleteVolumes(
                    workflow, waitFor, volumes, taskId, completer);

            // Last, call the RPDeviceController to add its post-delete methods.
            waitFor = _rpDeviceController.addStepsForPostDeleteVolumes(
                    workflow, waitFor, volumes, taskId, completer, _blockDeviceController);

            // Finish up and execute the plan.
            // The Workflow will handle the TaskCompleter
            String successMessage = "Delete volumes successful for: " + volUris.toString();
            Object[] callbackArgs = new Object[] { volUris };
            workflow.executePlan(completer, successMessage, new WorkflowCallback(), callbackArgs, null, null);
        } catch (Exception ex) {
            s_logger.error("Could not delete volumes: " + volUris, ex);
            releaseWorkflowLocks(workflow);
            String opName = ResourceOperationTypeEnum.DELETE_BLOCK_VOLUME.getName();
            ServiceError serviceError = DeviceControllerException.errors.deleteVolumesFailed(
                    volUris.toString(), opName, ex);
            completer.error(s_dbClient, _locker, serviceError);
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationController#expandVolume(java.net.URI, long, java.lang.String)
     */
    @Override
    public void expandVolume(List<VolumeDescriptor> volumes, String taskId) throws ControllerException {
        List<URI> volUris = VolumeDescriptor.getVolumeURIs(volumes);
        VolumeWorkflowCompleter completer = new VolumeWorkflowCompleter(volUris, taskId);
        try {
            // Generate the Workflow.
            Workflow workflow = _workflowService.getNewWorkflow(this,
                    EXPAND_VOLUMES_WF_NAME, true, taskId);
            String waitFor = null;    // the wait for key returned by previous call

            // First, call the RP controller to add methods for RP CG delete
            waitFor = _rpDeviceController.addPreVolumeExpandSteps(
                    workflow, volumes, taskId);

            // Call the BlockDeviceController to add its methods if there are block or VPLEX backend volumes.
            waitFor = _blockDeviceController.addStepsForExpandVolume(
                    workflow, waitFor, volumes, taskId);

            // Call the VPlexDeviceController to add its methods if there are VPLEX volumes.
            waitFor = _vplexDeviceController.addStepsForExpandVolume(
                    workflow, waitFor, volumes, taskId);

            // Call the RPDeviceController to add its methods for post volume expand ie. recreate RPCG
            waitFor = _rpDeviceController.addPostVolumeExpandSteps(
                    workflow, waitFor, volumes, taskId);

            // Finish up and execute the plan.
            // The Workflow will handle the TaskCompleter
            String successMessage = "Expand volume successful for: " + volUris.toString();
            Object[] callbackArgs = new Object[] { new ArrayList<URI>(volUris) };
            workflow.executePlan(completer, successMessage, new WorkflowCallback(), callbackArgs, null, null);
        } catch (Exception ex) {
            s_logger.error("Could not expand volume: " + volUris, toString(), ex);
            String opName = ResourceOperationTypeEnum.EXPAND_BLOCK_VOLUME.getName();
            ServiceError serviceError = DeviceControllerException.errors.expandVolumeFailed(volUris.toString(), opName, ex);
            completer.error(s_dbClient, _locker, serviceError);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationController#restoreVolume(java.net.URI, java.net.URI,
     * java.net.URI, java.net.URI, java.lang.String)
     */
    @Override
    public void restoreVolume(URI storage, URI pool, URI volume, URI snapshot, String taskId) throws ControllerException {
        List<URI> volUris = Arrays.asList(volume);
        BlockSnapshotRestoreCompleter completer = new BlockSnapshotRestoreCompleter(snapshot, taskId);
        try {
            // Generate the Workflow.
            Workflow workflow = _workflowService.getNewWorkflow(this,
                    RESTORE_VOLUME_FROM_SNAPSHOT_WF_NAME, true, taskId);
            String waitFor = null;    // the wait for key returned by previous call

            // First, call the RP controller to add RP steps for volume restore from snapshot
            waitFor = _rpDeviceController.addPreRestoreVolumeSteps(
                    workflow, storage, volume, snapshot, taskId);

            // Call the VplexDeviceController to add its steps for restore volume from snapshot
            waitFor = _vplexDeviceController.addStepsForRestoreVolume(
                    workflow, waitFor, storage, pool, volume, snapshot, null, taskId, completer);

            // Call the BlockDeviceController to add its steps for restore volume from snapshot
            waitFor = _blockDeviceController.addStepsForRestoreVolume(
                    workflow, waitFor, storage, pool, volume, snapshot, Boolean.TRUE, taskId, completer);

            // Call the RPDeviceController to add its steps for post restore volume from snapshot
            waitFor = _rpDeviceController.addStepsForRestoreVolume(
                    workflow, waitFor, storage, pool, volume, snapshot, null, taskId, completer);

            // Call the RP controller to add RP post restore steps
            waitFor = _rpDeviceController.addPostRestoreVolumeSteps(
                    workflow, waitFor, storage, volume, snapshot, taskId);

            // Finish up and execute the plan.
            // The Workflow will handle the TaskCompleter
            String successMessage = String.format("Restore of volume %s from %s completed successfully", volume, snapshot);
            Object[] callbackArgs = new Object[] { new ArrayList<URI>(volUris) };
            workflow.executePlan(completer, successMessage, new WorkflowCallback(), callbackArgs, null, null);
        } catch (Exception ex) {
            s_logger.error("Could not restore volume: " + volUris.toString(), ex);
            String opName = ResourceOperationTypeEnum.RESTORE_VOLUME_SNAPSHOT.getName();
            ServiceError serviceError = DeviceControllerException.errors.restoreVolumeFromSnapshotFailed(volUris.toString(),
                    snapshot.toString(), opName, ex);
            completer.error(s_dbClient, _locker, serviceError);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.blockorchestrationcontroller.BlockOrchestrationController#changeVirtualPool(java.util.List, java.lang.String)
     */
    @Override
    public void changeVirtualPool(List<VolumeDescriptor> volumes, String taskId)
            throws ControllerException {
        Map<URI, URI> changeVpoolVolsMap = VolumeDescriptor.getAllVirtualPoolChangeSourceVolumes(volumes);
        List<URI> volURIs = VolumeDescriptor.getVolumeURIs(volumes);
        List<URI> migrationURIs = new ArrayList<URI>();
        for (VolumeDescriptor desc : volumes) {
            URI migrationURI = desc.getMigrationId();
            if (!NullColumnValueGetter.isNullURI(migrationURI)) {
                migrationURIs.add(migrationURI);
            }
        }

        VolumeVpoolChangeTaskCompleter completer = new VolumeVpoolChangeTaskCompleter(
                volURIs, migrationURIs, changeVpoolVolsMap, taskId);

        try {
            // Generate the Workflow.
            Workflow workflow = _workflowService.getNewWorkflow(this,
                    CHANGE_VPOOL_WF_NAME, true, taskId);
            String waitFor = null;    // the wait for key returned by previous call

            // Mainly for RP+VPLEX as a change vpool would require new volumes (source-journal, target(s),
            // target-journal) to be created.
            waitFor = _blockDeviceController.addStepsForCreateVolumes(
                    workflow, waitFor, volumes, taskId);

            // Call the VPlexDeviceController to add change virtual pool steps.
            waitFor = _vplexDeviceController.addStepsForChangeVirtualPool(
                    workflow, waitFor, volumes, taskId);

            // Last, call the RPDeviceController to add change virtual pool steps.
            waitFor = _rpDeviceController.addStepsForChangeVirtualPool(
                    workflow, waitFor, volumes, taskId);

            // This step is currently used to ensure that any existing resources get added to native
            // CGs. Mainly used for VPLEX->RP+VPLEX change vpool. The existing VPLEX volume would not be
            // in any CG and we now need it's backing volume(s) to be added to their local array CG.
            waitFor = postRPChangeVpoolSteps(workflow, waitFor, volumes, taskId);

            // Finish up and execute the plan.
            // The Workflow will handle the TaskCompleter
            String successMessage = "Change Virtual Pool suceeded for volumes: " + volURIs.toString();
            Object[] callbackArgs = new Object[] { volURIs };
            workflow.executePlan(completer, successMessage, new WorkflowCallback(), callbackArgs, null, null);
        } catch (Exception ex) {
            s_logger.error("Could not change Virtual Pool for volumes: " + volURIs, ex);
            String opName = ResourceOperationTypeEnum.CHANGE_BLOCK_VOLUME_VPOOL.getName();
            ServiceError serviceError = DeviceControllerException.errors.changeVirtualPoolFailed(
                    volURIs.toString(), opName, ex);
            completer.error(s_dbClient, _locker, serviceError);
        }
    }

    @Override
    public void changeVirtualArray(List<VolumeDescriptor> volumeDescriptors, String taskId)
            throws ControllerException {

        // The descriptors that contain descriptor parameters
        // specifying the new target varray are the volumes being
        // moved to the new virtual array.
        List<URI> changeVArrayVolURIList = new ArrayList<URI>();
        List<URI> migrationURIs = new ArrayList<URI>();
        for (VolumeDescriptor volumeDescriptor : volumeDescriptors) {
            Map<String, Object> descrParams = volumeDescriptor.getParameters();
            if ((descrParams != null) && (!descrParams.isEmpty())) {
                changeVArrayVolURIList.add(volumeDescriptor.getVolumeURI());
            }
            URI migrationURI = volumeDescriptor.getMigrationId();
            if (!NullColumnValueGetter.isNullURI(migrationURI)) {
                migrationURIs.add(migrationURI);
            }
        }

        // Create a completer that will update the task status for these
        // volumes and associated migrations when the workflow completes.
        VolumeVarrayChangeTaskCompleter completer = new VolumeVarrayChangeTaskCompleter(
                VolumeDescriptor.getVolumeURIs(volumeDescriptors), migrationURIs, taskId);

        try {
            // Generate the Workflow.
            String waitFor = null;
            Workflow workflow = _workflowService.getNewWorkflow(this,
                    CHANGE_VARRAY_WF_NAME, true, taskId);

            // First, call the BlockDeviceController to add its steps.
            // This will create the migration target volumes.
            waitFor = _blockDeviceController.addStepsForCreateVolumes(workflow, waitFor,
                    volumeDescriptors, taskId);

            // Then call the VPlexDeviceController to add change virtual array steps.
            waitFor = _vplexDeviceController.addStepsForChangeVirtualArray(workflow,
                    waitFor, volumeDescriptors, taskId);

            // Finish up and execute the plan.
            // The Workflow will handle the TaskCompleter
            String successMessage = String.format(
                    "Change virtual array suceeded for volumes: %s", changeVArrayVolURIList);
            Object[] callbackArgs = new Object[] { changeVArrayVolURIList };
            workflow.executePlan(completer, successMessage, new WorkflowCallback(), callbackArgs, null, null);
        } catch (Exception ex) {
            s_logger.error("Could not change virtual array for volumes: " + changeVArrayVolURIList, ex);
            String opName = ResourceOperationTypeEnum.CHANGE_BLOCK_VOLUME_VARRAY.getName();
            ServiceError serviceError = DeviceControllerException.errors
                    .changeVirtualArrayFailed(changeVArrayVolURIList.toString(), opName, ex);
            completer.error(s_dbClient, _locker, serviceError);
        }
    }

    /**
     * Needed to perform post change vpool operations on RP volumes.
     *
     * @param workflow The current workflow
     * @param waitFor The previous operation to wait for
     * @param volumeDescriptors All the volume descriptors
     * @param taskId The current task id
     * @return The previous operation id
     */
    private String postRPChangeVpoolSteps(Workflow workflow, String waitFor,
            List<VolumeDescriptor> volumeDescriptors, String taskId) {
        // Get the list of descriptors needed for post change virtual pool operations on RP.
        List<VolumeDescriptor> rpVolumeDescriptors = VolumeDescriptor.filterByType(volumeDescriptors,
                new VolumeDescriptor.Type[] {
                        VolumeDescriptor.Type.RP_EXISTING_SOURCE,
                }, null);

        // If no volume descriptors match, just return
        if (rpVolumeDescriptors.isEmpty()) {
            return waitFor;
        }

        // We could be performing a change vpool for RP+VPLEX / MetroPoint. This means
        // we could potentially have migrations that need to be done on the backend
        // volumes. If migration info exists we need to collect that ahead of time.
        List<URI> volumesWithMigration = new ArrayList<URI>();
        if (volumeDescriptors != null) {
            List<VolumeDescriptor> migrateDescriptors = VolumeDescriptor.filterByType(volumeDescriptors,
                    new VolumeDescriptor.Type[] { VolumeDescriptor.Type.VPLEX_MIGRATE_VOLUME }, null);

            if (migrateDescriptors != null && !migrateDescriptors.isEmpty()) {
                s_logger.info("Data Migration detected, this is due to a change virtual pool operation on RP+VPLEX or MetroPoint.");
                // Load the migration objects for use later
                Iterator<VolumeDescriptor> migrationIter = migrateDescriptors.iterator();
                while (migrationIter.hasNext()) {
                    Migration migration = s_dbClient.queryObject(Migration.class, migrationIter.next().getMigrationId());
                    volumesWithMigration.add(migration.getSource());
                }
            }
        }

        List<VolumeDescriptor> blockDataDescriptors = new ArrayList<VolumeDescriptor>();

        for (VolumeDescriptor descr : rpVolumeDescriptors) {
            // If there are RP_EXISTING_SOURCE volume descriptors, we need to ensure the
            // existing volumes are added to their native CGs for the change vpool request.
            // Before any existing resource can be protected by RP they have to be removed
            // from their existing CGs but now will need to be added to the new CG needed
            // for RecoverPoint protection.
            // NOTE: Only relevant for RP+VPLEX and MetroPoint. Regular RP does not enforce local
            // array CGs.
            Volume rpExistingSource = s_dbClient.queryObject(Volume.class, descr.getVolumeURI());

            // Check to see if the existing is not already protected by RP and that
            // there are associated volumes (meaning it's a VPLEX volume)
            if (RPHelper.isVPlexVolume(rpExistingSource)) {
                s_logger.info(String.format("Adding post RP Change Vpool steps for existing VPLEX source volume [%s].",
                        rpExistingSource.getLabel()));
                // VPLEX, use associated backing volumes
                // NOTE: If migrations exist for this volume the VPLEX Device Controller will clean these up
                // newly added CGs because we won't need them as the migration volumes will create their own CGs.
                // This is OK.
                for (String assocVolumeId : rpExistingSource.getAssociatedVolumes()) {
                    Volume assocVolume = s_dbClient.queryObject(Volume.class, URI.create(assocVolumeId));

                    // If there is a migration for this backing volume, we don't have to
                    // do any extra steps for ensuring that this volume gets gets added to the backing array CG
                    // because the migration volume will trump this volume. This volume will eventually be
                    // deleted so let's skip it.
                    if (volumesWithMigration.contains(assocVolume.getId())) {
                        s_logger.info(String.format("Migration exists for [%s] so no need to add this volume to a backing array CG.",
                                assocVolume.getLabel()));
                        continue;
                    }

                    // Create the BLOCK_DATA descriptor with the correct info
                    // for creating the CG and adding the backing volume to it.
                    VolumeDescriptor blockDataDesc = new VolumeDescriptor(VolumeDescriptor.Type.BLOCK_DATA,
                            assocVolume.getStorageController(), assocVolume.getId(), null,
                            rpExistingSource.getConsistencyGroup(), descr.getCapabilitiesValues());
                    blockDataDescriptors.add(blockDataDesc);

                    // Good time to update the backing volume with it's new CG
                    assocVolume.setConsistencyGroup(rpExistingSource.getConsistencyGroup());
                    s_dbClient.persistObject(assocVolume);

                    s_logger.info(
                            String.format("Backing volume [%s] needs to be added to CG [%s] on storage system [%s].",
                                    assocVolume.getLabel(), rpExistingSource.getConsistencyGroup(),
                                    assocVolume.getStorageController()));
                }
            }
        }

        if (!blockDataDescriptors.isEmpty()) {
            // Add a step to create the local array consistency group
            waitFor = _blockDeviceController.addStepsForCreateConsistencyGroup(workflow, waitFor, blockDataDescriptors,
                    "postRPChangeVpoolCreateCG");

            // Add a step to update the local array consistency group with the volumes to add
            waitFor = _blockDeviceController.addStepsForAddToConsistencyGroup(workflow, waitFor, blockDataDescriptors);
        }

        return waitFor;
    }

    public WorkflowService getWorkflowService() {
        return _workflowService;
    }

    public void setWorkflowService(WorkflowService workflowService) {
        this._workflowService = workflowService;
    }

    public DbClient getDbClient() {
        return s_dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this.s_dbClient = dbClient;
    }

    public void setLocker(ControllerLockingService locker) {
        this._locker = locker;
    }

    public BlockDeviceController getBlockDeviceController() {
        return _blockDeviceController;
    }

    public void setBlockDeviceController(BlockDeviceController blockDeviceController) {
        this._blockDeviceController = blockDeviceController;
    }

    public static VPlexDeviceController getVplexDeviceController() {
        return _vplexDeviceController;
    }

    public static void setVplexDeviceController(VPlexDeviceController vplexDeviceController) {
        BlockOrchestrationDeviceController._vplexDeviceController = vplexDeviceController;
    }

    public static RPDeviceController getRpDeviceController() {
        return _rpDeviceController;
    }

    public static void setRpDeviceController(RPDeviceController rpDeviceController) {
        BlockOrchestrationDeviceController._rpDeviceController = rpDeviceController;
    }

    public static SRDFDeviceController getSrdfDeviceController() {
        return _srdfDeviceController;
    }

    public static void setSrdfDeviceController(SRDFDeviceController srdfDeviceController) {
        BlockOrchestrationDeviceController._srdfDeviceController = srdfDeviceController;
    }

    public static ReplicaDeviceController getReplicaDeviceController() {
        return BlockOrchestrationDeviceController._replicaDeviceController;
    }

    public static void setReplicaDeviceController(ReplicaDeviceController replicaDeviceController) {
        BlockOrchestrationDeviceController._replicaDeviceController = replicaDeviceController;
    }

    private void releaseWorkflowLocks(Workflow workflow) {
        if (workflow == null) {
            return;
        }
        s_logger.info("Releasing all workflow locks with owner: {}", workflow.getWorkflowURI());
        _workflowService.releaseAllWorkflowLocks(workflow);
    }
}
