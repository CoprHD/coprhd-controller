/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;

/**
 * Task completer invoked when a workflow deleting a BlockSnapshotSession completes.
 */
@SuppressWarnings("serial")
public class DeleteBlockSnapshotSessionWorkflowCompleter extends TaskCompleter {

    // A logger.
    private static final Logger s_logger = LoggerFactory.getLogger(DeleteBlockSnapshotSessionWorkflowCompleter.class);

    /**
     * Constructor
     * 
     * @param snapSessionURI The URI of the BlockSnapshotSession instance.
     * @param taskId The unique task identifier.
     */
    public DeleteBlockSnapshotSessionWorkflowCompleter(URI snapSessionURI, String taskId) {
        super(BlockSnapshotSession.class, snapSessionURI, taskId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        URI snapSessionURI = getId();
        try {
            // Update the status map of the snapshot session.
            BlockSnapshotSession snapSession = dbClient.queryObject(BlockSnapshotSession.class, snapSessionURI);
            switch (status) {
                case error:
                    setErrorOnDataObject(dbClient, BlockSnapshotSession.class, snapSessionURI, coded);
                    break;
                case ready:
                default:
                    setReadyOnDataObject(dbClient, BlockSnapshotSession.class, snapSessionURI);

                    // Mark snapshot session inactive.
                    snapSession.setInactive(true);
                    dbClient.persistObject(snapSession);
            }

            if (isNotifyWorkflow()) {
                // If there is a workflow, update the step to complete.
                updateWorkflowStatus(status, coded);
            }
            s_logger.info("Done delete snapshot session task {} with status: {}", getOpId(), status.name());
        } catch (Exception e) {
            s_logger.error("Failed updating status for delete snapshot session task {}", getOpId(), e);
        }
    }
}