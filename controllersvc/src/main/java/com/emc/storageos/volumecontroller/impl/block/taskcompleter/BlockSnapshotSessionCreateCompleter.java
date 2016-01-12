/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;

import com.emc.storageos.db.client.model.BlockObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

/**
 * Task completer invoked when a workflow step creating new BlockSnapshotSession
 * instance completes.
 */
@SuppressWarnings("serial")
public class BlockSnapshotSessionCreateCompleter extends BlockSnapshotSessionCompleter {

    // A logger.
    private static final Logger s_logger = LoggerFactory.getLogger(BlockSnapshotSessionCreateCompleter.class);

    /**
     * Constructor
     * 
     * @param snapSessionURI The ids of the BlockSnapshotSession instances in the database.
     * @param stepId The id of the WF step in which the session is being created.
     */
    public BlockSnapshotSessionCreateCompleter(URI snapSessionURI, String stepId) {
        super(snapSessionURI, stepId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            BlockSnapshotSession snapSession = dbClient.queryObject(BlockSnapshotSession.class, getId());

            // Update the status map of the snapshot session.
            switch (status) {
                case error:
                    // Mark any linked targets inactive. This would not
                    // normally case when failing to create a snapshot
                    // session as targets are not linked when a new
                    // snapshot session is prepared in ViPR. However,
                    // it could be the case when restoring a source volume
                    // form a linked target.
                    StringSet linkedTargets = snapSession.getLinkedTargets();
                    if ((linkedTargets != null) && (!linkedTargets.isEmpty())) {
                        for (String linkedTarget : linkedTargets) {
                            BlockSnapshot target = dbClient.queryObject(BlockSnapshot.class, URI.create(linkedTarget));
                            if (target != null) {
                                target.setInactive(true);
                                dbClient.updateObject(target);
                            }
                        }
                    }

                    // Mark ViPR snapshot session inactive on error.
                    snapSession.setInactive(true);
                    dbClient.updateObject(snapSession);
                    break;
                case ready:
                    break;
                default:
                    String errMsg = String.format("Unexpected status %s for completer for step %s", status.name(), getOpId());
                    s_logger.info(errMsg);
                    throw DeviceControllerException.exceptions.unexpectedCondition(errMsg);
            }
            s_logger.info("Done snapshot session create step {} with status: {}", getOpId(), status.name());
        } catch (Exception e) {
            s_logger.error("Failed updating status for snapshot session create step {}", getOpId(), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }

    @Override
    protected String getDescriptionOfResults(Status status, BlockObject sourceObj, BlockSnapshotSession snapSession) {
        return null;
    }
}
