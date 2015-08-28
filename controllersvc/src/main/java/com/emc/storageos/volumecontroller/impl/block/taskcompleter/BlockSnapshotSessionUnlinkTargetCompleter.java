/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

/**
 * Task completer invoked when SMI-S request to unlink a target from
 * an array snapshot completes.
 */
@SuppressWarnings("serial")
public class BlockSnapshotSessionUnlinkTargetCompleter extends TaskLockingCompleter {

    // The URI of the BlockSnapshotSession representing the array snapshot.
    @SuppressWarnings("unused")
    private final URI _snapSessionURI;

    /**
     * Constructor
     * 
     * @param snapSessionURI The id of the BlockSnapshotSession instance in the database.
     * @param snapshotURI The id of the BlockSnapshot instance representing the target.
     * @param stepId The id of the WF step in which the target is being unlinked.
     */
    public BlockSnapshotSessionUnlinkTargetCompleter(URI snapSessionURI, URI snapshotURI, String stepId) {
        super(BlockSnapshot.class, snapshotURI, stepId);
        _snapSessionURI = snapSessionURI;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        if (isNotifyWorkflow()) {
            // If there is a workflow, update the step to complete.
            updateWorkflowStatus(status, coded);
        }
    }
}
