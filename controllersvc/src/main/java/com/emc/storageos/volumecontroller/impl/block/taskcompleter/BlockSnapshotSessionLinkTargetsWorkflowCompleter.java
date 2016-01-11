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
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

/**
 * Task completer invoked when a workflow linking target volumes to a
 * BlockSnapshotSession completes.
 */
@SuppressWarnings("serial")
public class BlockSnapshotSessionLinkTargetsWorkflowCompleter extends BlockSnapshotSessionCompleter {

    // Message constants.
    public static final String SNAPSHOT_SESSION_LINK_TARGETS_SUCCESS_MSG = "Linked targets for Block Snapshot Session %s for source %s";
    public static final String SNAPSHOT_SESSION_LINK_TARGETS_FAIL_MSG = "Failed to link targets for Block Snapshot Session %s for source %s";

    // The URIs of the BlockSnapshot instances representing the target volumes
    // to be linked to the session
    private final List<List<URI>> _snapshotURIs;

    // A logger.
    private static final Logger s_logger = LoggerFactory.getLogger(BlockSnapshotSessionLinkTargetsWorkflowCompleter.class);

    /**
     * Constructor
     * 
     * @param snapSessionURI The URI of the BlockSnapshotSession instance.
     * @param snapshotURIs The URIs of the BlockSnapshot instances representing the
     *            targets volume to be linked to the session.
     * @param taskId The unique task identifier.
     */
    public BlockSnapshotSessionLinkTargetsWorkflowCompleter(URI snapSessionURI, List<List<URI>> snapshotURIs, String taskId) {
        super(snapSessionURI, taskId);
        _snapshotURIs = snapshotURIs;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        URI snapSessionURI = getId();
        try {
            BlockSnapshotSession snapSession = dbClient.queryObject(BlockSnapshotSession.class, snapSessionURI);

            BlockObject sourceObj = null;
            if (snapSession.hasConsistencyGroup()) {
                BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, snapSession.getConsistencyGroup());
                List<BlockObject> allSources = BlockConsistencyGroupUtils.getAllSources(cg, dbClient);
                sourceObj = allSources.get(0);
            } else {
                sourceObj = BlockObject.fetch(dbClient, snapSession.getParent().getURI());
            }

            // Record the results.
            recordBlockSnapshotSessionOperation(dbClient, OperationTypeEnum.LINK_SNAPSHOT_SESSION_TARGET,
                    status, snapSession, sourceObj);

            // Update the status map of the snapshot session.
            switch (status) {
                case error:
                    // For those BlockSnapshot instances representing linked targets that
                    // were not successfully created and linked to the array snapshot
                    // represented by the BlockSnapshotSession instance, mark them inactive.
                    for (List<URI> snapshotURIs : _snapshotURIs) {
                        for (URI snapshotURI : snapshotURIs) {
                            // Successfully linked targets will be in the list of linked
                            // targets for the session.
                            StringSet linkedTargets = snapSession.getLinkedTargets();
                            if ((linkedTargets == null) || (!linkedTargets.contains(snapshotURI.toString()))) {
                                BlockSnapshot snapshot = dbClient.queryObject(BlockSnapshot.class, snapshotURI);
                                // If rollback was successful the snapshot would already have been
                                // marked inactive, so be sure to check for null.
                                if ((snapshot != null) && (!snapshot.getInactive())) {
                                    snapshot.setInactive(true);
                                    dbClient.updateObject(snapshot);
                                }
                            }
                        }
                    }

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
            s_logger.info("Done create and link new target volumes task {} with status: {}", getOpId(), status.name());
        } catch (Exception e) {
            s_logger.error("Failed updating status for create and link new target volumes task {}", getOpId(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getDescriptionOfResults(Operation.Status status, BlockObject sourceObj, BlockSnapshotSession snapSession) {
        return (status == Operation.Status.ready) ?
                String.format(SNAPSHOT_SESSION_LINK_TARGETS_SUCCESS_MSG, snapSession.getLabel(), sourceObj.getLabel()) :
                String.format(SNAPSHOT_SESSION_LINK_TARGETS_FAIL_MSG, snapSession.getLabel(), sourceObj.getLabel());
    }
}
