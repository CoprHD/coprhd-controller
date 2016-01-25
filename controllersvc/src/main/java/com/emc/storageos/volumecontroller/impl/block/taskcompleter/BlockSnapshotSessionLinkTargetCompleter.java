/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

/**
 * Task completer invoked when SMI-S request to create and link a new target
 * volume to an array snapshot completes.
 */
@SuppressWarnings("serial")
public class BlockSnapshotSessionLinkTargetCompleter extends TaskLockingCompleter {

    // A logger.
    private static final Logger s_logger = LoggerFactory.getLogger(BlockSnapshotSessionLinkTargetCompleter.class);

    // The URI of the BlockSnapshotSession representing the array snapshot.
    private final URI _snapSessionURI;

    /**
     * Constructor
     * 
     * @param snapSessionURI The id of the BlockSnapshotSession instance in the database.
     * @param snapshotURI The id of the BlockSnapshot instance representing the target.
     * @param stepId The id of the WF step in which the target is being created and linked.
     */
    public BlockSnapshotSessionLinkTargetCompleter(URI snapSessionURI, URI snapshotURI, String stepId) {
        super(BlockSnapshot.class, snapshotURI, stepId);
        _snapSessionURI = snapSessionURI;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            switch (status) {
                case error:
                    break;
                case ready:
                    // On success add the URI of the BlockSnapshot instance representing the linked
                    // target to the linked targets for the session.
                    URI snapshotURI = getId();
                    String snapshotId = snapshotURI.toString();
                    BlockSnapshotSession snapSession = dbClient.queryObject(BlockSnapshotSession.class, _snapSessionURI);
                    StringSet linkedTargets = snapSession.getLinkedTargets();
                    if (linkedTargets == null) {
                        linkedTargets = new StringSet();
                        snapSession.setLinkedTargets(linkedTargets);
                    }
                    linkedTargets.add(snapshotId);
                    dbClient.updateObject(snapSession);
                    break;
                default:
                    String errMsg = String.format("Unexpected status %s for completer for step %s", status.name(), getOpId());
                    s_logger.info(errMsg);
                    throw DeviceControllerException.exceptions.unexpectedCondition(errMsg);
            }

            if (isNotifyWorkflow()) {
                // If there is a workflow, update the step to complete.
                updateWorkflowStatus(status, coded);
            }
            s_logger.info("Done link snapshot session target step {} with status: {}", getOpId(), status.name());
        } catch (Exception e) {
            s_logger.error("Failed updating status for link snapshot session target step {}", getOpId(), e);
        }
    }
}