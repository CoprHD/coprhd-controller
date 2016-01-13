/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;

/**
 * Task completer invoked when SMI-S request to delete a snapshot session completes.
 */
@SuppressWarnings("serial")
public class BlockSnapshotSessionDeleteCompleter extends TaskCompleter {

    // Flag indicates if the completer should mark the snapshot session inactive.
    private Boolean _markInactive = Boolean.FALSE;

    // A logger.
    private static final Logger s_logger = LoggerFactory.getLogger(BlockSnapshotSessionDeleteCompleter.class);

    /**
     * Constructor
     * 
     * @param snapSessionURI The id of the BlockSnapshotSession instance in the database.
     * @param markInactive true if the step should mark the session inactive, false otherwise.
     * @param stepId The id of the WF step in which the session is being deleted.
     */
    public BlockSnapshotSessionDeleteCompleter(URI snapSessionURI, Boolean markInactive, String stepId) {
        super(BlockSnapshotSession.class, snapSessionURI, stepId);
        _markInactive = markInactive;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {

        URI snapSessionURI = getId();
        try {
            BlockSnapshotSession snapSession = dbClient.queryObject(BlockSnapshotSession.class, snapSessionURI);
            switch (status) {
                case ready:
                    // Mark snapshot session inactive if the flag so indicates.
                    // Note that in most cases the snapshot session is marked
                    // inactive in the workflow completer rather than this workflow
                    // step completer. In this way we able better able to prevent
                    // access to snapshot session after it has been marked inactive.
                    if ((_markInactive) && (snapSession != null) && (!snapSession.getInactive())) {
                        snapSession.setInactive(true);
                        dbClient.updateObject(snapSession);
                    }
                    break;
                default:
                    break;
            }

            if (isNotifyWorkflow()) {
                // If there is a workflow, update the step to complete.
                updateWorkflowStatus(status, coded);
            }
            s_logger.info("Done delete snapshot session step {} with status: {}", getOpId(), status.name());
        } catch (Exception e) {
            s_logger.error("Failed updating status for delete snapshot session step {}", getOpId(), e);
        }
    }
}