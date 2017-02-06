/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.file;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FilePolicy;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.workflow.WorkflowStepCompleter;

public class FilePolicyWorkflowCompleter extends FileTaskCompleter {

    private static final long serialVersionUID = 1L;
    protected static final Logger _log = LoggerFactory.getLogger(FileWorkflowCompleter.class);

    public FilePolicyWorkflowCompleter(URI policyUri, String task) {
        super(FilePolicy.class, policyUri, task);
        _log.info("Creating completer for OpId: " + getOpId());
    }

    @Override
    protected void setStatus(DbClient dbClient, Operation.Status status, ServiceCoded serviceCoded) {
        switch (status) {
            case error:
                dbClient.error(FilePolicy.class, getId(), getOpId(), serviceCoded);
                if (isNotifyWorkflow()) {
                    WorkflowStepCompleter.stepFailed(getOpId(), serviceCoded);
                }
                break;
            case ready:
                dbClient.ready(FilePolicy.class, getId(), getOpId());
                if (isNotifyWorkflow()) {
                    WorkflowStepCompleter.stepSucceded(getOpId());
                }
                break;
            case suspended_error:
                dbClient.suspended_error(FilePolicy.class, getId(), getOpId(), serviceCoded);
                if (isNotifyWorkflow()) {
                    WorkflowStepCompleter.stepSuspendedError(getOpId(), serviceCoded);
                }
                break;
            case suspended_no_error:
                dbClient.suspended_no_error(FilePolicy.class, getId(), getOpId());
                if (isNotifyWorkflow()) {
                    WorkflowStepCompleter.stepSuspendedNoError(getOpId());
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
