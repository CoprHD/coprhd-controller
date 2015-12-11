/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

import static com.emc.storageos.db.client.util.CommonTransformerFunctions.FCTN_URI_TO_STRING;
import static com.google.common.collect.Collections2.transform;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Lists.newArrayList;

/**
 * Task completer invoked when SMI-S request to create and link a new target
 * volume to an array snapshot completes.
 */
@SuppressWarnings("serial")
public class BlockSnapshotSessionLinkTargetCompleter extends TaskLockingCompleter {

    // A logger.
    private static final Logger s_logger = LoggerFactory.getLogger(BlockSnapshotSessionLinkTargetCompleter.class);

    private Map<URI, List<URI>> _snapSessionSnapshotMap;

    /**
     * Constructor
     * 
     * @param snapSessionURI The id of the BlockSnapshotSession instance in the database.
     * @param snapshotURI The id of the BlockSnapshot instance representing the target.
     * @param stepId The id of the WF step in which the target is being created and linked.
     */
    public BlockSnapshotSessionLinkTargetCompleter(URI snapSessionURI, URI snapshotURI, String stepId) {
        super(BlockSnapshot.class, snapshotURI, stepId);
        _snapSessionSnapshotMap = new HashMap<>();
        _snapSessionSnapshotMap.put(snapSessionURI, newArrayList(snapshotURI));
    }

    public BlockSnapshotSessionLinkTargetCompleter(Map<URI, List<URI>> snapSessionSnapshotMap, String stepId) {
        super(BlockSnapshot.class, newArrayList(concat(snapSessionSnapshotMap.values())), stepId);
        _snapSessionSnapshotMap = snapSessionSnapshotMap;
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
                    for (Map.Entry<URI, List<URI>> entry : _snapSessionSnapshotMap.entrySet()) {
                        URI snapSessionURI = entry.getKey();
                        List<URI> snapshotTargets = entry.getValue();

                        BlockSnapshotSession snapSession = dbClient.queryObject(BlockSnapshotSession.class, snapSessionURI);
                        StringSet linkedTargets = snapSession.getLinkedTargets();

                        // TODO Remove this START
                        for (URI snapURI : snapshotTargets) {
                            s_logger.info("Linking target {} to session {}", snapURI, snapSessionURI);
                        }
                        if (true) { continue; }
                        // TODO Remove this END


                        if (linkedTargets == null) {
                            linkedTargets = new StringSet();
                            snapSession.setLinkedTargets(linkedTargets);
                        }
                        linkedTargets.addAll(transform(snapshotTargets, FCTN_URI_TO_STRING));
                        sessionsToUpdate.add(snapSession);
                    }
                    dbClient.updateObject(sessionsToUpdate);
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