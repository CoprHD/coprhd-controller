/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.model.BlockObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

import static com.emc.storageos.db.client.util.CommonTransformerFunctions.FCTN_URI_TO_STRING;
import static com.google.common.collect.Collections2.transform;
import static com.google.common.collect.Lists.newArrayList;

/**
 * Task completer invoked when SMI-S request to create and link a new target
 * volume to an array snapshot completes.
 */
@SuppressWarnings("serial")
public class BlockSnapshotSessionLinkTargetCompleter extends BlockSnapshotSessionCompleter {

    // A logger.
    private static final Logger s_logger = LoggerFactory.getLogger(BlockSnapshotSessionLinkTargetCompleter.class);

    private URI _snapshotSessionURI;
    private List<URI> _snapshotURIs;

    /**
     * Constructor
     * 
     * @param snapSessionURI The id of the BlockSnapshotSession instance in the database.
     * @param snapshotURIs The id of the BlockSnapshot instance representing the target.
     * @param stepId The id of the WF step in which the target is being created and linked.
     */
    public BlockSnapshotSessionLinkTargetCompleter(URI snapSessionURI, List<URI> snapshotURIs, String stepId) {
        super(snapSessionURI, stepId);
        _snapshotSessionURI = snapSessionURI;
        _snapshotURIs = snapshotURIs;
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
                    List<BlockSnapshotSession> sessionsToUpdate = newArrayList();
                    BlockSnapshotSession snapSession = dbClient.queryObject(BlockSnapshotSession.class, _snapshotSessionURI);
                    StringSet linkedTargets = snapSession.getLinkedTargets();

                    if (linkedTargets == null) {
                        linkedTargets = new StringSet();
                        snapSession.setLinkedTargets(linkedTargets);
                    }
                    linkedTargets.addAll(transform(_snapshotURIs, FCTN_URI_TO_STRING));
                    sessionsToUpdate.add(snapSession);
                    dbClient.updateObject(sessionsToUpdate);
                    break;
                default:
                    String errMsg = String.format("Unexpected status %s for completer for step %s", status.name(), getOpId());
                    s_logger.info(errMsg);
                    throw DeviceControllerException.exceptions.unexpectedCondition(errMsg);
            }
            s_logger.info("Done link snapshot session target step {} with status: {}", getOpId(), status.name());
        } catch (Exception e) {
            s_logger.error("Failed updating status for link snapshot session target step {}", getOpId(), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }

    @Override
    protected String getDescriptionOfResults(Operation.Status status, BlockObject sourceObj, BlockSnapshotSession snapSession) {
        return null;
    }
}