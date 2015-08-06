/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;

/**
 * 
 */
@SuppressWarnings("serial")
public class CreateBlockSnapshotSessionCompleter extends TaskCompleter {

    /**
     * Constructor
     * 
     * @param snapSessionIds The ids of the BlockSnapshotSession instances in the database.
     * @param stepId The id of the WF step in which the session is being created.
     */
    public CreateBlockSnapshotSessionCompleter(List<URI> snapSessionIds, String stepId) {
        super(BlockSnapshotSession.class, snapSessionIds, stepId);
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
