/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.util.BlockConsistencyGroupUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

/**
 * Task completer invoked when a workflow restoring a BlockSnapshotSession completes.
 */
@SuppressWarnings("serial")
public class BlockSnapshotSessionRestoreWorkflowCompleter extends BlockSnapshotSessionCompleter {

    // Message constants.
    public static final String SNAPSHOT_SESSION_RESTORE_SUCCESS_MSG = "Block Snapshot Session %s restored for source %s";
    public static final String SNAPSHOT_SESSION_RESTORE_FAIL_MSG = "Failed to restore Block Snapshot Session %s for source %s";

    // Flag indicates if the completer should update and record the status when it completes.
    private Boolean _updateOpStatus = Boolean.TRUE;

    // A logger.
    private static final Logger s_logger = LoggerFactory.getLogger(BlockSnapshotSessionRestoreWorkflowCompleter.class);

    /**
     * Constructor
     * 
     * @param snapSessionURI The URI of the BlockSnapshotSession instance.
     * @param taskId The unique task identifier.
     */
    public BlockSnapshotSessionRestoreWorkflowCompleter(URI snapSessionURI, Boolean updateOpStatus, String taskId) {
        super(snapSessionURI, taskId);
        _updateOpStatus = updateOpStatus;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        URI snapSessionURI = getId();
        try {
            if (_updateOpStatus) {
                BlockSnapshotSession snapSession = dbClient.queryObject(BlockSnapshotSession.class, snapSessionURI);
                List<BlockObject> allSources = getAllSources(snapSession, dbClient);
                BlockObject sourceObj = allSources.get(0);

                // Record the results.
                recordBlockSnapshotSessionOperation(dbClient, OperationTypeEnum.RESTORE_SNAPSHOT_SESSION,
                        status, snapSession, sourceObj);

                // Update the status map of the snapshot session.
                switch (status) {
                    case error:
                        setErrorOnDataObject(dbClient, BlockSnapshotSession.class, snapSession.getId(), coded);
                        break;
                    case ready:
                        setReadyOnDataObject(dbClient, BlockSnapshotSession.class, snapSession.getId());
                        break;
                    default:
                        String errMsg = String.format("Unexpected status %s for completer for task %s", status.name(), getOpId());
                        s_logger.info(errMsg);
                        throw DeviceControllerException.exceptions.unexpectedCondition(errMsg);
                }
            }
            s_logger.info("Done restore snapshot session task {} with status: {}", getOpId(), status.name());
        } catch (Exception e) {
            s_logger.error("Failed updating status for restore snapshot session task {}", getOpId(), e);
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
                String.format(SNAPSHOT_SESSION_RESTORE_SUCCESS_MSG, snapSession.getLabel(), sourceObj.getLabel()) :
                String.format(SNAPSHOT_SESSION_RESTORE_FAIL_MSG, snapSession.getLabel(), sourceObj.getLabel());
    }
}