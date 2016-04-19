/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.file;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.workflow.WorkflowStepCompleter;

public class FileWorkflowCompleter extends FileTaskCompleter{
	
	protected static final Logger _log = LoggerFactory.getLogger(FileWorkflowCompleter.class);

    public FileWorkflowCompleter(List<URI> fsUris, String task) {
        super(FileShare.class, fsUris, task);
        _log.info("Creating completer for OpId: " + getOpId());
    }

    public FileWorkflowCompleter(URI fsUri, String task) {
        super(FileShare.class, fsUri, task);
        _log.info("Creating completer for OpId: " + getOpId());
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded serviceCoded) {
        switch (status) {
            case error:
                for (URI id : getIds()) {
                    dbClient.error(FileShare.class, id, getOpId(), serviceCoded);
                }
                if (isNotifyWorkflow()) {
                    WorkflowStepCompleter.stepFailed(getOpId(), serviceCoded);
                }
                break;
            case ready:
                for (URI id : getIds()) {
                    dbClient.ready(FileShare.class, id, getOpId());
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
