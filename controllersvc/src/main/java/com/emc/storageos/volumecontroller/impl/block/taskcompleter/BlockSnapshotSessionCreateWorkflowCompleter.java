/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

/**
 * Task completer invoked when a workflow creating new BlockSnapshotSession
 * instances completes.
 */
@SuppressWarnings("serial")
public class BlockSnapshotSessionCreateWorkflowCompleter extends BlockSnapshotSessionCompleter {

    // Message constants.
    public static final String SNAPSHOT_SESSION_CREATE_SUCCESS_MSG = "Block Snapshot Session %s created for source %s";
    public static final String SNAPSHOT_SESSION_CREATE_FAIL_MSG = "Failed to create Block Snapshot Session %s for source %s";

    // A map of the BlockSnapshot instances linked to the session for the request.
    private final Map<URI, List<URI>> _sessionSnapshotMap;

    // A logger.
    private static final Logger s_logger = LoggerFactory.getLogger(BlockSnapshotSessionCreateWorkflowCompleter.class);

    /**
     * Constructor
     * 
     * @param snapSessionURIs The URIs of the BlockSnapshotSession instances created in the request.
     * @param sessionSnapshotsMap A map of the BlockSnapshot instances linked to the session for the request.
     * @param taskId The unique task identifier.
     */
    public BlockSnapshotSessionCreateWorkflowCompleter(List<URI> snapSessionURIs, Map<URI, List<URI>> sessionSnapshotsMap, String taskId) {
        super(snapSessionURIs, taskId);
        _sessionSnapshotMap = sessionSnapshotsMap;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            List<URI> sessionURIs = getIds();
            for (URI sessionURI : sessionURIs) {
                BlockSnapshotSession snapSession = dbClient.queryObject(BlockSnapshotSession.class, sessionURI);
                List<URI> sessionSnapshotURIs = _sessionSnapshotMap.get(sessionURI);
                BlockObject sourceObj = BlockObject.fetch(dbClient, snapSession.getParent().getURI());

                // Record the results.
                recordBlockSnapshotSessionOperation(dbClient, OperationTypeEnum.CREATE_SNAPSHOT_SESSION,
                        status, snapSession, sourceObj);

                // Update the status map of the snapshot session.
                switch (status) {
                    case error:
                        setErrorOnDataObject(dbClient, BlockSnapshotSession.class, sessionURI, coded);

                        // Mark ViPR object inactive on error.
                        snapSession.setInactive(true);
                        dbClient.persistObject(snapSession);
                        for (URI sessionSnapshotURI : sessionSnapshotURIs) {
                            BlockSnapshot snapshot = dbClient.queryObject(BlockSnapshot.class, sessionSnapshotURI);
                            snapshot.setInactive(true);
                            dbClient.persistObject(snapshot);
                        }
                        break;
                    default:
                        setReadyOnDataObject(dbClient, BlockSnapshotSession.class, sessionURI);
                }

            }

            if (isNotifyWorkflow()) {
                // If there is a workflow, update the step to complete.
                updateWorkflowStatus(status, coded);
            }
            s_logger.info("Done snapshot session create task {} with status: {}", getOpId(), status.name());
        } catch (Exception e) {
            s_logger.error("Failed updating status for snapshot session create task {}", getOpId(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getDescriptionOfResults(Operation.Status status, BlockObject sourceObj, BlockSnapshotSession snapSession) {
        return (status == Operation.Status.ready) ?
                String.format(SNAPSHOT_SESSION_CREATE_SUCCESS_MSG, snapSession.getLabel(), sourceObj.getLabel()) :
                String.format(SNAPSHOT_SESSION_CREATE_FAIL_MSG, snapSession.getLabel(), sourceObj.getLabel());
    }
}
