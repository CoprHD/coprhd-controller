/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.file;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.FilePolicy;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.WorkflowStep;
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

    public List<WorkflowStep> getWorkFlowSteps(DbClient dbClient, URI workFlowId) {
        List<WorkflowStep> workFlowSteps = new ArrayList<WorkflowStep>();
        if (workFlowId != null) {
            URIQueryResultList stepURIs = new URIQueryResultList();
            dbClient.queryByConstraint(
                    ContainmentConstraint.Factory.getWorkflowWorkflowStepConstraint(workFlowId),
                    stepURIs);
            Iterator<URI> iter = stepURIs.iterator();
            while (iter.hasNext()) {
                URI workflowStepURI = iter.next();
                WorkflowStep step = dbClient.queryObject(WorkflowStep.class, workflowStepURI);
                workFlowSteps.add(step);
            }
        }
        return workFlowSteps;
    }

    public int getWorkFlowFailedSteps(DbClient dbClient, URI workFlowId, StringBuffer strErrorMsg) {
        int numStepsFailed = 0;
        List<WorkflowStep> workFlowSteps = getWorkFlowSteps(dbClient, workFlowId);
        if (workFlowSteps != null && !workFlowSteps.isEmpty()) {
            strErrorMsg.append("The following workflow step(s) failed :");
            for (WorkflowStep workFlowStep : workFlowSteps) {
                if (workFlowStep.getState() != null && workFlowStep.getState().equalsIgnoreCase("error")) {
                    numStepsFailed++;
                    strErrorMsg.append(workFlowStep.getDescription());
                }
            }
        }
        return numStepsFailed;
    }

}
