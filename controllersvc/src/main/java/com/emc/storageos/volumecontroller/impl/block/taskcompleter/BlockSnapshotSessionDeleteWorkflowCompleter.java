/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

/**
 * Task completer invoked when a workflow deleting a BlockSnapshotSession completes.
 */
@SuppressWarnings("serial")
public class BlockSnapshotSessionDeleteWorkflowCompleter extends BlockSnapshotSessionCompleter {

    // Message constants.
    public static final String SNAPSHOT_SESSION_DELETE_SUCCESS_MSG = "Block Snapshot Session %s deleted for source %s";
    public static final String SNAPSHOT_SESSION_DELETE_FAIL_MSG = "Failed to delete Block Snapshot Session %s for source %s";

    // A logger.
    private static final Logger s_logger = LoggerFactory.getLogger(BlockSnapshotSessionDeleteWorkflowCompleter.class);

    /**
     * Constructor
     * 
     * @param snapSessionURI The URI of the BlockSnapshotSession instance.
     * @param taskId The unique task identifier.
     */
    public BlockSnapshotSessionDeleteWorkflowCompleter(URI snapSessionURI, String taskId) {
        super(snapSessionURI, taskId);
    }

    /**
     * Constructor for snap sessions in a consistency group.
     * 
     * @param snapSessionURIs
     * @param taskId
     */
    public BlockSnapshotSessionDeleteWorkflowCompleter(List<URI> snapSessionURIs, String taskId) {
        super(snapSessionURIs, taskId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        List<URI> snapshotSessionURIs = getIds();
        try {
            Iterator<BlockSnapshotSession> iterator = dbClient.queryIterativeObjects(BlockSnapshotSession.class,
                    snapshotSessionURIs);
            while (iterator.hasNext()) {
                BlockSnapshotSession snapshotSession = iterator.next();
                BlockObject sourceObj = BlockObject.fetch(dbClient, snapshotSession.getParent().getURI());

                // Record the results.
                recordBlockSnapshotSessionOperation(dbClient, OperationTypeEnum.DELETE_SNAPSHOT_SESSION,
                        status, snapshotSession, sourceObj);

                // Update the status map of the snapshot session.
                switch (status) {
                    case error:
                        setErrorOnDataObject(dbClient, BlockSnapshotSession.class, snapshotSession.getId(), coded);
                        break;
                    case ready:
                        setReadyOnDataObject(dbClient, BlockSnapshotSession.class, snapshotSession.getId());

                        // Mark snapshot session inactive.
                        snapshotSession.setInactive(true);
                        dbClient.updateObject(snapshotSession);
                        break;
                    default:
                        String errMsg = String.format("Unexpected status %s for completer for task %s", status.name(), getOpId());
                        s_logger.info(errMsg);
                        throw DeviceControllerException.exceptions.unexpectedCondition(errMsg);
                }
            }

            if (isNotifyWorkflow()) {
                // If there is a workflow, update the task to complete.
                updateWorkflowStatus(status, coded);
            }
            s_logger.info("Done delete snapshot session task {} with status: {}", getOpId(), status.name());
        } catch (Exception e) {
            s_logger.error("Failed updating status for delete snapshot session task {}", getOpId(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getDescriptionOfResults(Operation.Status status, BlockObject sourceObj, BlockSnapshotSession snapSession) {
        return (status == Operation.Status.ready) ?
                String.format(SNAPSHOT_SESSION_DELETE_SUCCESS_MSG, snapSession.getLabel(), sourceObj.getLabel()) :
                String.format(SNAPSHOT_SESSION_DELETE_FAIL_MSG, snapSession.getLabel(), sourceObj.getLabel());
    }
}