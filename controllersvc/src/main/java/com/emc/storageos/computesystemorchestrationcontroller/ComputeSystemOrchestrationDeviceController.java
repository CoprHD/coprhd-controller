/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemorchestrationcontroller;

import java.net.URI;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.emc.storageos.computesystemcontroller.exceptions.ComputeSystemControllerException;
import com.emc.storageos.computesystemcontroller.hostmountadapters.HostDeviceInputOutput;
import com.emc.storageos.computesystemcontroller.hostmountadapters.MountCompleter;
import com.emc.storageos.computesystemcontroller.impl.ComputeSystemControllerImpl;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowService;

public class ComputeSystemOrchestrationDeviceController implements ComputeSystemOrchestrationController {

    private static final Log _log = LogFactory.getLog(ComputeSystemOrchestrationDeviceController.class);

    private WorkflowService _workflowService;
    private DbClient _dbClient;
    private ComputeSystemControllerImpl _computeSystemControllerImpl;

    private static final String MOUNT_DEVICE_WF_NAME = "MOUNT_DEVICE_WORKFLOW";
    private static final String UNMOUNT_DEVICE_WF_NAME = "UNMOUNT_DEVICE_WORKFLOW";

    public void setDbClient(DbClient dbc) {
        _dbClient = dbc;
    }

    public void setWorkflowService(WorkflowService workflowService) {
        _workflowService = workflowService;
    }

    public void setComputeSystemControllerImpl(ComputeSystemControllerImpl computeSystemControllerImpl) {
        _computeSystemControllerImpl = computeSystemControllerImpl;
    }

    @Override
    public void mountDevice(URI hostId, URI resId, String subDirectory, String security, String mountPath, String fsType, String opId)
            throws ControllerException {
        HostDeviceInputOutput args = new HostDeviceInputOutput();
        args.setSubDirectory(subDirectory);
        args.setHostId(hostId);
        args.setResId(resId);
        args.setSecurity(security);
        args.setMountPath(mountPath);
        args.setFsType(fsType);
        // Generate the Workflow.
        Workflow workflow = null;
        MountCompleter completer = new MountCompleter(args.getResId(), opId);
        try {
            // Generate the Workflow.
            workflow = _workflowService.getNewWorkflow(this, MOUNT_DEVICE_WF_NAME, false, opId);
            _computeSystemControllerImpl.addStepsForMountDevice(workflow, args);

            // Finish up and execute the plan.
            // The Workflow will handle the TaskCompleter
            String successMessage = "Mount device successful for: " + args.getHostId().toString();
            workflow.executePlan(completer, successMessage);

        } catch (Exception ex) {
            _log.error("Could not mount device: " + args, ex);
            ComputeSystemControllerException exception = ComputeSystemControllerException.exceptions
                    .unableToMount(_dbClient.queryObject(Host.class, args.getHostId()).getType(), ex);
            completer.error(_dbClient, exception);
            _workflowService.releaseAllWorkflowLocks(workflow);
            throw ex;
        }
    }

    @Override
    public void unmountDevice(URI hostId, URI resId, String mountPath, String opId) throws ControllerException {
        HostDeviceInputOutput args = new HostDeviceInputOutput();
        args.setHostId(hostId);
        args.setResId(resId);
        args.setMountPath(mountPath);
        // Generate the Workflow.
        Workflow workflow = null;
        MountCompleter completer = new MountCompleter(args.getResId(), opId);
        try {
            // Generate the Workflow.
            workflow = _workflowService.getNewWorkflow(this, UNMOUNT_DEVICE_WF_NAME, false, opId);
            _computeSystemControllerImpl.addStepsForUnmountDevice(workflow, args);

            // Finish up and execute the plan.
            // The Workflow will handle the TaskCompleter
            String successMessage = "Unmount device successful for: " + args.getHostId().toString();
            workflow.executePlan(completer, successMessage);

        } catch (Exception ex) {
            _log.error("Could not unmount device: " + args, ex);
            ComputeSystemControllerException exception = ComputeSystemControllerException.exceptions
                    .unableToUnmount(_dbClient.queryObject(Host.class, args.getHostId()).getType(), ex);
            completer.error(_dbClient, exception);
            _workflowService.releaseAllWorkflowLocks(workflow);
            throw ex;
        }
    }
}
