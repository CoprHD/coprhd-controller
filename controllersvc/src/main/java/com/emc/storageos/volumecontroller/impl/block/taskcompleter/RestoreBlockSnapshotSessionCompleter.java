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
 * Task completer invoked when SMI-S request to restore a snapshot session completes.
 */
@SuppressWarnings("serial")
public class RestoreBlockSnapshotSessionCompleter extends TaskLockingCompleter {

    /**
     * Constructor
     * 
     * @param snapSessionURI The id of the BlockSnapshotSession instance in the database.
     * @param stepId The id of the WF step in which the target is being unlinked.
     */
    public RestoreBlockSnapshotSessionCompleter(URI snapSessionURI, String stepId) {
        super(BlockSnapshot.class, snapSessionURI, stepId);
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