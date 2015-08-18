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
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;

/**
 * 
 */
@SuppressWarnings("serial")
public class LinkBlockSnapshotSessionTargetsWorkflowCompleter extends TaskCompleter {

    private final List<URI> _snapshotURIs;

    // A logger.
    private static final Logger s_logger = LoggerFactory.getLogger(LinkBlockSnapshotSessionTargetsWorkflowCompleter.class);

    /**
     * Constructor
     * 
     * @param snapSessionURI The URI of the BlockSnapshotSession instance.
     * @param snapshotURIs The URIs of the BlockSnapshot instances representing the
     *            targets volume to be linked to the session.
     * @param taskId The unique task identifier.
     */
    public LinkBlockSnapshotSessionTargetsWorkflowCompleter(URI snapSessionURI, List<URI> snapshotURIs, String taskId) {
        super(BlockSnapshotSession.class, snapSessionURI, taskId);
        _snapshotURIs = snapshotURIs;
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

                    // Mark ViPR BlockSnapshot instances inactive on error.
                    for (URI snapshotURI : _snapshotURIs) {
                        BlockSnapshot snapshot = dbClient.queryObject(BlockSnapshot.class, snapshotURI);
                        snapshot.setInactive(true);
                        dbClient.persistObject(snapshot);
                    }
                    break;
                default:
                    setReadyOnDataObject(dbClient, BlockSnapshotSession.class, snapSessionURI);

                    // Adds these linked targets to the linked targets for the session.
                    StringSet linkedTargets = snapSession.getLinkedTargets();
                    if (linkedTargets == null) {
                        linkedTargets = new StringSet();
                        snapSession.setLinkedTargets(linkedTargets);
                    }
                    for (URI snapshotURI : _snapshotURIs) {
                        linkedTargets.add(snapshotURI.toString());
                    }
                    dbClient.persistObject(snapSession);
            }

            if (isNotifyWorkflow()) {
                // If there is a workflow, update the step to complete.
                updateWorkflowStatus(status, coded);
            }
            s_logger.info("Done create and link new target volumes task {} with status: {}", getOpId(), status.name());
        } catch (Exception e) {
            s_logger.error("Failed updating status for create and link new target volumes task {}", getOpId(), e);
        }
    }
}
