package com.emc.storageos.migrationorchestrationcontroller;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.Controller;
import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.fileorchestrationcontroller.FileOrchestrationDeviceController.WorkflowCallback;
import com.emc.storageos.migrationcontroller.HostMigrationDeviceController;
import com.emc.storageos.migrationcontroller.NativeMigrationDeviceController;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.ControllerLockingService;
import com.emc.storageos.volumecontroller.impl.block.BlockDeviceController;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeVarrayChangeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeVpoolChangeTaskCompleter;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowService;

public class MigrationOrchestrationDeviceController implements MigrationOrchestrationController, Controller {
    private static final Logger s_logger = LoggerFactory.getLogger(MigrationOrchestrationDeviceController.class);
    private WorkflowService _workflowService;
    private static DbClient s_dbClient;
    private static BlockDeviceController _blockDeviceController;
    private static HostMigrationDeviceController _hostMigrationDeviceController;
    private static NativeMigrationDeviceController _nativeMigrationDeviceController;
    private ControllerLockingService _locker;

    static final String CREATE_VOLUMES_WF_NAME = "CREATE_VOLUMES_WORKFLOW";
    static final String DELETE_VOLUMES_WF_NAME = "DELETE_VOLUMES_WORKFLOW";
    static final String CHANGE_VPOOL_WF_NAME = "CHANGE_VPOOL_WORKFLOW";
    static final String CHANGE_VARRAY_WF_NAME = "CHANGE_VARRAY_WORKFLOW";

    @Override
    public void changeVirtualPool(List<VolumeDescriptor> volumes, String taskId) throws ControllerException {
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

            // change vpool may require new volumes for migration.
            waitFor = _blockDeviceController.addStepsForCreateVolumes(
                    workflow, waitFor, volumes, taskId);

            // Call the HostMigrationDeviceController to add change virtual pool steps.
            waitFor = _hostMigrationDeviceController.addStepsForChangeVirtualPool(
                    workflow, waitFor, volumes, taskId);

            // call the nativeMigrationDeviceController to add change virtual pool steps.
            waitFor = _nativeMigrationDeviceController.addStepsForChangeVirtualPool(
                    workflow, waitFor, volumes, taskId);
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
    public void changeVirtualArray(List<VolumeDescriptor> volumeDescriptors, String taskId) throws ControllerException {
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

            // Then call the HostMigrationDeviceController to add change virtual array steps.
            waitFor = _hostMigrationDeviceController.addStepsForChangeVirtualArray(workflow,
                    waitFor, volumeDescriptors, taskId);

            // Then call the NativeMigrationDeviceController to add change virtual array steps.
            waitFor = _nativeMigrationDeviceController.addStepsForChangeVirtualArray(workflow,
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

    public static BlockDeviceController getBlockDeviceController() {
        return _blockDeviceController;
    }

}
