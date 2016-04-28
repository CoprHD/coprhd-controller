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
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

/**
 * Task completer invoked when a workflow relinking target volumes to a
 * BlockSnapshotSession completes.
 */
@SuppressWarnings("serial")
public class BlockSnapshotSessionRelinkTargetsWorkflowCompleter extends BlockSnapshotSessionCompleter {

    // Message constants.
    public static final String SNAPSHOT_SESSION_RELINK_TARGETS_SUCCESS_MSG = "Re-linked targets for Block Snapshot Session %s for source %s";
    public static final String SNAPSHOT_SESSION_RELINK_TARGETS_FAIL_MSG = "Failed to re-link targets for Block Snapshot Session %s for source %s";

    // A logger.
    private static final Logger s_logger = LoggerFactory.getLogger(BlockSnapshotSessionRelinkTargetsWorkflowCompleter.class);

    /**
     * Constructor
     * 
     * @param snapSessionURI The URI of the target BlockSnapshotSession instance.
     * @param taskId The unique task identifier.
     */
    public BlockSnapshotSessionRelinkTargetsWorkflowCompleter(URI snapSessionURI, String taskId) {
        super(snapSessionURI, taskId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        URI tgtSnapSessionURI = getId();
        try {
            // Update the status map of the snapshot session.
            BlockSnapshotSession tgtSnapSession = dbClient.queryObject(BlockSnapshotSession.class, tgtSnapSessionURI);
            List<BlockObject> allSources = getAllSources(tgtSnapSession, dbClient);
            BlockObject sourceObj = allSources.get(0);

            // Record the results.
            recordBlockSnapshotSessionOperation(dbClient, OperationTypeEnum.RELINK_SNAPSHOT_SESSION_TARGET,
                    status, tgtSnapSession, sourceObj);

            switch (status) {
                case error:
                    setErrorOnDataObject(dbClient, BlockSnapshotSession.class, tgtSnapSessionURI, coded);
                    break;
                case ready:
                    setReadyOnDataObject(dbClient, BlockSnapshotSession.class, tgtSnapSessionURI);
                    break;
                default:
                    String errMsg = String.format("Unexpected status %s for completer for task %s", status.name(), getOpId());
                    s_logger.info(errMsg);
                    throw DeviceControllerException.exceptions.unexpectedCondition(errMsg);
            }
            s_logger.info("Done re-link target volumes task {} with status: {}", getOpId(), status.name());
        } catch (Exception e) {
            s_logger.error("Failed updating status for re-link target volumes task {}", getOpId(), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getDescriptionOfResults(Operation.Status status, BlockObject sourceObj, BlockSnapshotSession snapSession) {
        return (status == Operation.Status.ready) ?
                String.format(SNAPSHOT_SESSION_RELINK_TARGETS_SUCCESS_MSG, snapSession.getLabel(), sourceObj.getLabel()) :
                String.format(SNAPSHOT_SESSION_RELINK_TARGETS_FAIL_MSG, snapSession.getLabel(), sourceObj.getLabel());
    }
}