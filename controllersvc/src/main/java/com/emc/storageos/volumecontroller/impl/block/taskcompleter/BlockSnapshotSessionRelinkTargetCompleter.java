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
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

/**
 * Task completer invoked when SMI-S request to re-link a target
 * volume to an array snapshot completes.
 */
@SuppressWarnings("serial")
public class BlockSnapshotSessionRelinkTargetCompleter extends BlockSnapshotSessionCompleter {

    // The URI of the BlockSnapshotSession representing the target array snapshot.
    private final URI _snapshotURI;

    // A logger.
    private static final Logger s_logger = LoggerFactory.getLogger(BlockSnapshotSessionRelinkTargetCompleter.class);

    /**
     * Constructor
     * 
     * @param tgtSnapSessionURI The id of the target BlockSnapshotSession instance in the database.
     * @param snapshotURI The id of the BlockSnapshot instance representing the target.
     * @param stepId The id of the WF step in which the target is being re-linked.
     */
    public BlockSnapshotSessionRelinkTargetCompleter(URI tgtSnapSessionURI, URI snapshotURI, String stepId) {
        super(tgtSnapSessionURI, stepId);
        _snapshotURI = snapshotURI;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            switch (status) {
                case error:
                    break;
                case ready:
                    // Remove the linked targets from the linked targets list for their
                    // current snapshot session and add them to the linked targets for
                    // the target session.
                    BlockSnapshotSession tgtSnapSession = dbClient.queryObject(BlockSnapshotSession.class, getId());
                    StringSet tgtSnapSessionTargets = tgtSnapSession.getLinkedTargets();
                    List<BlockSnapshotSession> snaphotSessionsList = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient,
                            BlockSnapshotSession.class,
                            ContainmentConstraint.Factory.getLinkedTargetSnapshotSessionConstraint(_snapshotURI));
                    if (snaphotSessionsList.isEmpty()) {
                        // The target is not linked to an active snapshot session.
                        throw DeviceControllerException.exceptions.unexpectedCondition(String.format(
                                "Cound not find active snapshot session for linked target %s", _snapshotURI));
                    }

                    // A target can only be linked to a single session.
                    BlockSnapshotSession currentSnapSession = snaphotSessionsList.get(0);

                    // If the target was not re-linked to the same snapshot session
                    // update the linked targets list for both the current and target
                    // snapshot sessions.
                    if (!currentSnapSession.getId().equals(getId())) {
                        String snapshotId = _snapshotURI.toString();
                        // Remove from the current snapshot session.
                        StringSet currentSnapSessionTargets = currentSnapSession.getLinkedTargets();
                        currentSnapSessionTargets.remove(snapshotId);
                        dbClient.updateObject(currentSnapSession);

                        // Add to the target snapshot session.
                        if (tgtSnapSessionTargets == null) {
                            tgtSnapSessionTargets = new StringSet();
                            tgtSnapSession.setLinkedTargets(tgtSnapSessionTargets);
                        }
                        tgtSnapSessionTargets.add(snapshotId);
                        dbClient.updateObject(tgtSnapSession);
                    }
                    break;
                default:
                    String errMsg = String.format("Unexpected status %s for completer for step %s", status.name(), getOpId());
                    s_logger.info(errMsg);
                    throw DeviceControllerException.exceptions.unexpectedCondition(errMsg);
            }
            s_logger.info("Done re-link target volume step {} with status: {}", getOpId(), status.name());
        } catch (Exception e) {
            s_logger.error("Failed updating status for re-link target volume step {}", getOpId(), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }
}
