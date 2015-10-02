/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;

/**
 * Task completer invoked when a workflow step creating new BlockSnapshotSession
 * instance completes.
 */
@SuppressWarnings("serial")
public class BlockSnapshotSessionCreateCompleter extends TaskCompleter {

    // A logger.
    private static final Logger s_logger = LoggerFactory.getLogger(BlockSnapshotSessionCreateCompleter.class);

    /**
     * Constructor
     * 
     * @param snapSessionURIs The ids of the BlockSnapshotSession instances in the database.
     * @param stepId The id of the WF step in which the session is being created.
     */
    public BlockSnapshotSessionCreateCompleter(List<URI> snapSessionURIs, String stepId) {
        super(BlockSnapshotSession.class, snapSessionURIs, stepId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            List<URI> snapSessionURIs = getIds();
            for (URI snapSessionURI : snapSessionURIs) {
                BlockSnapshotSession snapSession = dbClient.queryObject(BlockSnapshotSession.class, snapSessionURI);

                // Update the status map of the snapshot session.
                switch (status) {
                    case error:
                        // Mark ViPR snapshot session inactive on error.
                        snapSession.setInactive(true);
                        dbClient.persistObject(snapSession);
                        break;
                    case ready:
                        break;
                    default:
                        String errMsg = String.format("Unexpected status %s for completer for step %s", status.name(), getOpId());
                        s_logger.info(errMsg);
                        throw DeviceControllerException.exceptions.unexpectedCondition(errMsg);
                }
            }

            if (isNotifyWorkflow()) {
                // If there is a workflow, update the step to complete.
                updateWorkflowStatus(status, coded);
            }
            s_logger.info("Done snapshot session create step {} with status: {}", getOpId(), status.name());
        } catch (Exception e) {
            s_logger.error("Failed updating status for snapshot session create step {}", getOpId(), e);
        }
    }
}
