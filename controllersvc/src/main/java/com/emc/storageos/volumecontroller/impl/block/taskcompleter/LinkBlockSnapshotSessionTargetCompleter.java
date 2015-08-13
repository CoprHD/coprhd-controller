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
 *
 */
@SuppressWarnings("serial")
public class LinkBlockSnapshotSessionTargetCompleter extends TaskLockingCompleter {

    //
    @SuppressWarnings("unused")
    private final URI _snapSessionURI;

    /**
     * Constructor
     * 
     * @param snapSessionURI The id of the BlockSnapshotSession instance in the database.
     * @param snapshotURI The id of the BlockSnapshot instance representing the target.
     * @param stepId The id of the WF step in which the targets are being created.
     */
    public LinkBlockSnapshotSessionTargetCompleter(URI snapSessionURI, URI snapshotURI, String stepId) {
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
