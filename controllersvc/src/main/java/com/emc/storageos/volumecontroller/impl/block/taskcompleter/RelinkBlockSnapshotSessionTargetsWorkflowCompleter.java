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
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;

/**
 * Task completer invoked when a workflow relinking target volumes to a
 * BlockSnapshotSession completes.
 */
@SuppressWarnings("serial")
public class RelinkBlockSnapshotSessionTargetsWorkflowCompleter extends TaskCompleter {

    // The URIs of the BlockSnapshot instances representing the target volumes
    // to be re-linked to the session
    private final List<URI> _snapshotURIs;

    // A logger.
    private static final Logger s_logger = LoggerFactory.getLogger(RelinkBlockSnapshotSessionTargetsWorkflowCompleter.class);

    /**
     * Constructor
     * 
     * @param snapSessionURI The URI of the target BlockSnapshotSession instance.
     * @param snapshotURIs The URIs of the BlockSnapshot instances representing the
     *            targets volume to be re-linked to the target session.
     * @param taskId The unique task identifier.
     */
    public RelinkBlockSnapshotSessionTargetsWorkflowCompleter(URI snapSessionURI, List<URI> snapshotURIs, String taskId) {
        super(BlockSnapshotSession.class, snapSessionURI, taskId);
        _snapshotURIs = snapshotURIs;
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
            switch (status) {
                case error:
                    setErrorOnDataObject(dbClient, BlockSnapshotSession.class, tgtSnapSessionURI, coded);
                    break;
                case ready:
                default:
                    setReadyOnDataObject(dbClient, BlockSnapshotSession.class, tgtSnapSessionURI);

                    // Remove the linked targets from the linked targets list for their
                    // current snapshot session and add them to the linked targets for
                    // the target session.
                    boolean tgtSnapSessionModified = false;
                    StringSet tgtSnapSessionTargets = tgtSnapSession.getLinkedTargets();
                    for (URI snapshotURI : _snapshotURIs) {
                        List<BlockSnapshotSession> snaphotSessionsList = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient,
                                BlockSnapshotSession.class,
                                ContainmentConstraint.Factory.getLinkedTargetSnapshotSessionConstraint(snapshotURI));
                        if (snaphotSessionsList.isEmpty()) {
                            // The target is not linked to an active snapshot session.
                            throw DeviceControllerException.exceptions.unexpectedCondition(String.format(
                                    "Cound not find active snapshot session for linked target %s", snapshotURI));
                        }

                        // A target can only be linked to a single session.
                        BlockSnapshotSession currentSnapSession = snaphotSessionsList.get(0);

                        // If the target was not re-linked to the same snapshot session
                        // update the linked targets list for both the current and target
                        // snapshot sessions.
                        if (!currentSnapSession.getId().equals(tgtSnapSessionURI)) {
                            String snapshotId = snapshotURI.toString();
                            // Remove from the current snapshot session.
                            StringSet currentSnapSessionTargets = currentSnapSession.getLinkedTargets();
                            currentSnapSessionTargets.remove(snapshotId);
                            dbClient.persistObject(currentSnapSession);

                            // Add to the target snapshot session.
                            if (tgtSnapSessionTargets == null) {
                                tgtSnapSessionTargets = new StringSet();
                                tgtSnapSession.setLinkedTargets(tgtSnapSessionTargets);
                            }
                            tgtSnapSessionTargets.add(snapshotId);
                            tgtSnapSessionModified = true;
                        }
                    }

                    if (tgtSnapSessionModified) {
                        dbClient.persistObject(tgtSnapSession);
                    }
            }

            if (isNotifyWorkflow()) {
                // If there is a workflow, update the step to complete.
                updateWorkflowStatus(status, coded);
            }
            s_logger.info("Done re-link target volumes task {} with status: {}", getOpId(), status.name());
        } catch (Exception e) {
            s_logger.error("Failed updating status for re-link target volumes task {}", getOpId(), e);
        }
    }
}