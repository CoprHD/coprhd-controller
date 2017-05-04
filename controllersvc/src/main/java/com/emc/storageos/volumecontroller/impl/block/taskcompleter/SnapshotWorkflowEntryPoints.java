/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.emc.storageos.Controller;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.BlockStorageDevice;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowStepCompleter;

/**
 * This class will contain entry points and utilities for invoking snapshot operations
 * through the Workflow engine.
 */
public class SnapshotWorkflowEntryPoints implements Controller {
    private Map<String, BlockStorageDevice> _devices;
    private DbClient _dbClient;

    public SnapshotWorkflowEntryPoints() {
    }

    public void setDevices(Map<String, BlockStorageDevice> deviceInterfaces) {
        _devices = deviceInterfaces;
    }

    private BlockStorageDevice getDevice(StorageSystem storage) {
        return _devices.get(storage.getSystemType());
    }

    public void setDbClient(DbClient dbc) {
        _dbClient = dbc;
    }

    /**
     * This method should be called to generate a workflow step entry in the passed in
     * Workflow object for running copy-to-target for the snapshots in a consistency
     * group.
     * 
     * @param workflow [required] - Workflow object to add step
     * @param previousStep [optional] - Id of previous step to wait for before
     *            copy-to-target step is run. If null,
     *            is run in parallel to other steps in the workflow
     * @param storageSystem [required] - StorageSystem object that this operation
     *            applies to.
     * @param snapshotList [required] - List of blocksnapshot URIs that this
     *            operation should be applied against.
     * @return String ID of the step that is created.
     */
    public static String
            generateCopySnapshotsToTargetWorkflow(Workflow workflow, String previousStep,
                    StorageSystem storageSystem,
                    List<URI> snapshotList) {
        String copyToTargetStep = workflow.createStepId();

        BlockSnapshotCopyGroupToTargetsCompleter taskCompleter =
                new BlockSnapshotCopyGroupToTargetsCompleter(BlockSnapshot.class,
                        snapshotList, copyToTargetStep);

        Workflow.Method copy2TargetMethod =
                new Workflow.Method("doCopySnapshotsToTargetStep",
                        storageSystem.getId(),
                        snapshotList, taskCompleter);

        copyToTargetStep = workflow.createStep("CopyGroupSnapshotToTargets",
                "Copying snapshots to targets", previousStep, storageSystem.getId(),
                storageSystem.getSystemType(), SnapshotWorkflowEntryPoints.class,
                copy2TargetMethod, null, copyToTargetStep);

        return copyToTargetStep;
    }

    /**
     * Official Workflow Step
     * Entry method to invoke the copy-to-target operation.
     * 
     * @param storageURI [required] - StorageSystem object URI
     * @param snapshotList [required] - List of blocksnapshot URIs that this
     *            operation should be applied against.
     * @param taskCompleter [required] - TaskCompleter that will be used to signal the
     *            completion of the workflow step
     * @param token [required] - Token used for step identification
     */
    public void doCopySnapshotsToTargetStep(URI storageURI, List<URI> snapshotList,
            TaskCompleter taskCompleter, String token) {
        WorkflowStepCompleter.stepExecuting(token);

        StorageSystem storage = _dbClient
                .queryObject(StorageSystem.class, storageURI);
        getDevice(storage).doCopySnapshotsToTarget(storage, snapshotList,
                taskCompleter);
    }
}
