/*
 * Copyright (c) 2017 Dell-EMC Corporation
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
import com.emc.storageos.db.client.model.WorkflowStep;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;

/**
 * @author sanjes
 *
 */
public class FileReplicationConfigFailoverCompleter extends FileWorkflowCompleter {
    protected static final Logger _log = LoggerFactory.getLogger(FileReplicationConfigFailoverCompleter.class);
    private URI workFlowId;

    public FileReplicationConfigFailoverCompleter(URI fsUri, String task) {
        super(fsUri, task);
        _log.info("Creating file replication configuration failover completer for OpId: " + getOpId());
    }

    public URI getWorkFlowId() {
        return workFlowId;
    }

    public void setWorkFlowId(URI workFlowId) {
        this.workFlowId = workFlowId;
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        FileShare fileshare = dbClient.queryObject(FileShare.class, getId());

        int numStepsFailed = 0;
        int numSteps = 0;
        int successSteps = 0;
        List<WorkflowStep> workFlowSteps = getWorkFlowSteps(dbClient);
        if (workFlowSteps != null && !workFlowSteps.isEmpty()) {
            StringBuffer strErrorMsg = new StringBuffer();
            strErrorMsg.append("The following workflow step(s) failed :");
            numSteps = workFlowSteps.size();
            for (WorkflowStep workFlowStep : workFlowSteps) {
                if (workFlowStep.getState() != null && workFlowStep.getState().equalsIgnoreCase("error")) {
                    numStepsFailed++;
                    strErrorMsg.append(workFlowStep.getDescription());
                } else if (workFlowStep.getState() != null && workFlowStep.getState().equalsIgnoreCase("success")){
                    successSteps++;
                }
            }
            if (numStepsFailed > 0) {
                _log.error("Replication Configuration on Failover failed with {}" + strErrorMsg.toString());
            }

        }

        dbClient.updateObject(fileshare);
        // Update the task error, if the task failed!!!
        if (numStepsFailed > 0) {
            _log.error(String.format("failed updating  %s configurations and succeeded %s replication configrations on failover",
                    numStepsFailed,
                    successSteps));
            ServiceError serviceError = DeviceControllerException.errors.fileReplicationConfFailoverOperationFailed(
                    fileshare.getName(), numStepsFailed, numSteps);
            setStatus(dbClient, status, serviceError);
        } else {
            setStatus(dbClient, status, coded);
        }
        
        if(numSteps == (successSteps + numStepsFailed)){
            super.complete(dbClient, Operation.Status.ready, coded);
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
