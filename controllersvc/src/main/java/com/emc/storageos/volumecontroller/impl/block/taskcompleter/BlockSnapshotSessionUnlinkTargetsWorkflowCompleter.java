/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.util.BlockConsistencyGroupUtils;
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
 * Task completer invoked when a workflow unlinking target volumes from a
 * BlockSnapshotSession completes.
 */
@SuppressWarnings("serial")
public class BlockSnapshotSessionUnlinkTargetsWorkflowCompleter extends BlockSnapshotSessionCompleter {

    // Message constants.
    public static final String SNAPSHOT_SESSION_UNLINK_TARGETS_SUCCESS_MSG = "Unlinked targets for Block Snapshot Session %s for source %s";
    public static final String SNAPSHOT_SESSION_UNLINK_TARGETS_FAIL_MSG = "Failed to unlink targets for Block Snapshot Session %s for source %s";

    // A logger.
    private static final Logger s_logger = LoggerFactory.getLogger(BlockSnapshotSessionUnlinkTargetsWorkflowCompleter.class);

    /**
     * Constructor
     * 
     * @param snapSessionURI The URI of the BlockSnapshotSession instance.
     * @param taskId The unique task identifier.
     */
    public BlockSnapshotSessionUnlinkTargetsWorkflowCompleter(URI snapSessionURI, String taskId) {
        super(snapSessionURI, taskId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        URI snapSessionURI = getId();
        try {
            BlockSnapshotSession snapSession = dbClient.queryObject(BlockSnapshotSession.class, snapSessionURI);

            // Get the snapshot session source object.
            BlockObject sourceObj = null;
            if (snapSession.hasConsistencyGroup()) {
                BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, snapSession.getId());
                List<Volume> nativeVolumes = BlockConsistencyGroupUtils.getActiveNativeVolumesInCG(cg, dbClient);
                sourceObj = nativeVolumes.get(0);
            } else {
                sourceObj = BlockObject.fetch(dbClient, snapSession.getParent().getURI());
            }

            // Record the results.
            recordBlockSnapshotSessionOperation(dbClient, OperationTypeEnum.UNLINK_SNAPSHOT_SESSION_TARGET,
                    status, snapSession, sourceObj);

            // Update the status map of the snapshot session.
            switch (status) {
                case error:
                    setErrorOnDataObject(dbClient, BlockSnapshotSession.class, snapSessionURI, coded);
                    break;
                case ready:
                    setReadyOnDataObject(dbClient, BlockSnapshotSession.class, snapSessionURI);
                    break;
                default:
                    String errMsg = String.format("Unexpected status %s for completer for task %s", status.name(), getOpId());
                    s_logger.info(errMsg);
                    throw DeviceControllerException.exceptions.unexpectedCondition(errMsg);
            }

            if (isNotifyWorkflow()) {
                // If there is a workflow, update the task to complete.
                updateWorkflowStatus(status, coded);
            }
            s_logger.info("Done unlink targets from snapshot session task {} with status: {}", getOpId(), status.name());
        } catch (Exception e) {
            s_logger.error("Failed updating status for unlink targets from snapshot session task {}", getOpId(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getDescriptionOfResults(Operation.Status status, BlockObject sourceObj, BlockSnapshotSession snapSession) {
        return (status == Operation.Status.ready) ?
                String.format(SNAPSHOT_SESSION_UNLINK_TARGETS_SUCCESS_MSG, snapSession.getLabel(), sourceObj.getLabel()) :
                String.format(SNAPSHOT_SESSION_UNLINK_TARGETS_FAIL_MSG, snapSession.getLabel(), sourceObj.getLabel());
    }
}
