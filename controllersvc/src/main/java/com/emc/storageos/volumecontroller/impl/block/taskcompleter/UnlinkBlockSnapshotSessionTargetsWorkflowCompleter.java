/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;

/**
 * Task completer invoked when a workflow unlinking target volumes from a
 * BlockSnapshotSession completes.
 */
@SuppressWarnings("serial")
public class UnlinkBlockSnapshotSessionTargetsWorkflowCompleter extends TaskCompleter {

    // A map where the keys are the URIs of the BlockSnapshot instances
    // representing the targets to be unlinked and the values specify
    // whether or not a given snapshot should be deleted.
    private final Map<URI, Boolean> _snapshotDeletionMap;

    // A logger.
    private static final Logger s_logger = LoggerFactory.getLogger(LinkBlockSnapshotSessionTargetsWorkflowCompleter.class);

    /**
     * Constructor
     * 
     * @param snapSessionURI The URI of the BlockSnapshotSession instance.
     * @param snapshotDeletionMap A map where the keys are the URIs of the BlockSnapshot
     *            instances representing the targets to be unlinked and the values specify
     *            whether or not a given snapshot should be deleted.
     * @param taskId The unique task identifier.
     */
    public UnlinkBlockSnapshotSessionTargetsWorkflowCompleter(URI snapSessionURI, Map<URI, Boolean> snapshotDeletionMap, String taskId) {
        super(BlockSnapshotSession.class, snapSessionURI, taskId);
        _snapshotDeletionMap = snapshotDeletionMap;
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
            Set<URI> snapshotURIs = _snapshotDeletionMap.keySet();
            switch (status) {
                case error:
                    setErrorOnDataObject(dbClient, BlockSnapshotSession.class, snapSessionURI, coded);
                    break;
                case ready:
                default:
                    setReadyOnDataObject(dbClient, BlockSnapshotSession.class, snapSessionURI);

                    // Remove these linked targets from the linked targets for the session.
                    boolean updatedSnapSession = false;
                    StringSet linkedTargets = snapSession.getLinkedTargets();
                    for (URI snapshotURI : snapshotURIs) {
                        if (linkedTargets != null) {
                            linkedTargets.remove(snapshotURI.toString());
                            updatedSnapSession = true;
                        }

                        // If a target is deleted, mark the associated BlockSnapshot inactive.
                        Boolean deletedSnapshot = _snapshotDeletionMap.get(snapshotURI);
                        if (deletedSnapshot) {
                            BlockSnapshot snapshot = dbClient.queryObject(BlockSnapshot.class, snapshotURI);
                            snapshot.setInactive(true);
                            dbClient.persistObject(snapshot);
                        }
                    }

                    // Persist the snapshot session if necessary.
                    if (updatedSnapSession) {
                        dbClient.persistObject(snapSession);
                    }
            }

            if (isNotifyWorkflow()) {
                // If there is a workflow, update the step to complete.
                updateWorkflowStatus(status, coded);
            }
            s_logger.info("Done unlink targets from snapshot session task {} with status: {}", getOpId(), status.name());
        } catch (Exception e) {
            s_logger.error("Failed updating status for cunlink targets from snapshot session task {}", getOpId(), e);
        }
    }
}
