/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplexcontroller.completers;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Migration;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowException;
import com.emc.storageos.workflow.WorkflowStepCompleter;

public class MigrationWorkflowCompleter extends TaskCompleter {

    String _wfStepId;
    List<URI> _migrationURIs = new ArrayList<URI>();

    public MigrationWorkflowCompleter(List<URI> volumeURIs, List<URI> migrationURIs, String opId, String wfStepId) {
        super(Volume.class, volumeURIs, opId);
        _wfStepId = wfStepId;
        _migrationURIs.addAll(migrationURIs);
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        // Don't update the volume status if a WF step is passed.
        // If the migration is executing as a sub workflow in a step
        // in another workflow, that other WF will update the volume
        // status when that workflow completes all its steps.
        if (_wfStepId == null) {
            setStatus(dbClient, status, coded);
        }
        updateMigrationStatus(dbClient, status, coded);
        updateWorkflowStatus(status, coded);
    }

    @Override
    protected void updateWorkflowStatus(Status status, ServiceCoded coded) throws WorkflowException {
        String id = (_wfStepId != null ? _wfStepId : getOpId());
        switch (status) {
            case error:
                WorkflowStepCompleter.stepFailed(id, coded);
                break;
            case pending:
                WorkflowStepCompleter.stepExecuting(id);
                break;
            case suspended_error:
                WorkflowStepCompleter.stepSuspendedError(id);
                break;
            case suspended_no_error:
                WorkflowStepCompleter.stepSuspendedNoError(id);
                break;
            default:
                WorkflowStepCompleter.stepSucceded(id);
        }
    }

    @Override
    protected void updateWorkflowState(Workflow.StepState state, ServiceCoded coded) throws WorkflowException {
        String id = (_wfStepId != null ? _wfStepId : getOpId());
        switch (state) {
            case ERROR:
                WorkflowStepCompleter.stepFailed(id, coded);
                break;
            case EXECUTING:
                WorkflowStepCompleter.stepExecuting(id);
                break;
            case SUSPENDED_ERROR:
                WorkflowStepCompleter.stepSuspendedError(id);
                break;
            case SUSPENDED_NO_ERROR:
                WorkflowStepCompleter.stepSuspendedNoError(id);
                break;
            case SUCCESS:
            default:
                WorkflowStepCompleter.stepSucceded(id);
        }
    }

    /**
     * Update the status of the migration tasks.
     * 
     * @param dbClient Reference to a database client
     * @param status Operation status
     * @param coded The error on error status.
     */
    private void updateMigrationStatus(DbClient dbClient, Status status, ServiceCoded coded) {
        switch (status) {
            case error:
                for (URI migrationURI : _migrationURIs) {
                    dbClient.error(Migration.class, migrationURI, getOpId(), coded);
                }
                break;
            case ready:
                for (URI migrationURI : _migrationURIs) {
                    dbClient.ready(Migration.class, migrationURI, getOpId());
                }
                break;
            case suspended_error:
                for (URI migrationURI : _migrationURIs) {
                    dbClient.suspended_error(Migration.class, migrationURI, getOpId());
                }
                break;
            case suspended_no_error:
                for (URI migrationURI : _migrationURIs) {
                    dbClient.suspended_no_error(Migration.class, migrationURI, getOpId());
                }
                break;
            default:
        }
    }
}
