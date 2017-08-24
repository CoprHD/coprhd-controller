/*
 * Copyright (c) 2017 EMC Corporation
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
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.WorkflowStep;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;

public class FileProtectionPolicyUpdateCompleter extends FilePolicyWorkflowCompleter {

    private static final long serialVersionUID = 1L;
    protected static final Logger _log = LoggerFactory.getLogger(FileProtectionPolicyUpdateCompleter.class);
    private URI workFlowId;

    public FileProtectionPolicyUpdateCompleter(URI policyUri, String task) {
        super(policyUri, task);
        _log.info("Creating update policy completer for OpId: " + getOpId());
    }

    public URI getWorkFlowId() {
        return workFlowId;
    }

    public void setWorkFlowId(URI workFlowId) {
        this.workFlowId = workFlowId;
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        FilePolicy filePolicy = dbClient.queryObject(FilePolicy.class, getId());

        int numStepsFailed = 0;
        int numPolicies = 0;
        List<WorkflowStep> workFlowSteps = getWorkFlowSteps(dbClient);
        if (workFlowSteps != null && !workFlowSteps.isEmpty()) {
            StringBuffer strErrorMsg = new StringBuffer();
            strErrorMsg.append("The following workflow step(s) failed :");
            numPolicies = workFlowSteps.size();
            for (WorkflowStep workFlowStep : workFlowSteps) {
                if (workFlowStep.getState() != null && workFlowStep.getState().equalsIgnoreCase("error")) {
                    numStepsFailed++;
                    strErrorMsg.append(workFlowStep.getDescription());
                }
            }
            if (numStepsFailed > 0) {
                _log.error("Update file protection policy failed  with {}" + strErrorMsg.toString());
            }

        }

        dbClient.updateObject(filePolicy);
        // Update the task error, if the task failed!!!
        if (numStepsFailed > 0) {
            int successPolicies = numPolicies - numStepsFailed;
            _log.error(String.format("Failed to Update %s storage policies and successfully updated %s policies", numStepsFailed,
                    successPolicies));
            ServiceError serviceError = DeviceControllerException.errors.deviceProtectionPolicyOperationFailed(
                    filePolicy.getId().toString(), "update", numStepsFailed, successPolicies);
            setStatus(dbClient, status, serviceError);
        } else {
            setStatus(dbClient, status, coded);
        }

    }

    private List<WorkflowStep> getWorkFlowSteps(DbClient dbClient) {
        URI workFlowId = getWorkFlowId();
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
}