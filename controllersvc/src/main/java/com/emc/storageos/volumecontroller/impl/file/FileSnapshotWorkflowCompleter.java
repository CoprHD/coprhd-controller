/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.file;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.workflow.WorkflowStepCompleter;

public class FileSnapshotWorkflowCompleter extends FileTaskCompleter {

    private static final long serialVersionUID = 1L;
    protected static final Logger _log = LoggerFactory.getLogger(FileWorkflowCompleter.class);

    public FileSnapshotWorkflowCompleter(URI snapURI, String task) {
        super(Snapshot.class, snapURI, task);
        _log.info("Creating completer for OpId: " + getOpId());
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded serviceCoded) {
        switch (status) {
            case error:
                for (URI id : getIds()) {
                    dbClient.error(Snapshot.class, id, getOpId(), serviceCoded);
                }
                if (isNotifyWorkflow()) {
                    WorkflowStepCompleter.stepFailed(getOpId(), serviceCoded);
                }
                break;
            case ready:
                for (URI id : getIds()) {
                    dbClient.ready(Snapshot.class, id, getOpId());
                }
                if (isNotifyWorkflow()) {
                    WorkflowStepCompleter.stepSucceded(getOpId());
                }
                break;
            default:
                if (isNotifyWorkflow()) {
                    WorkflowStepCompleter.stepExecuting(getOpId());
                }
                break;
        }
    }
}
