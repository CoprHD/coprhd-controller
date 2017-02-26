/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.file;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FilePolicy;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyApplyLevel;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;

public class FilePolicyAssignWorkflowCompleter extends FilePolicyWorkflowCompleter {

    private static final long serialVersionUID = 1L;
    protected static final Logger _log = LoggerFactory.getLogger(FilePolicyAssignWorkflowCompleter.class);
    private ArrayList<URI> assignToResource;
    private URI projectVPool;
    private URI workFlowId;

    public URI getWorkFlowId() {
        return workFlowId;
    }

    public void setWorkFlowId(URI workFlowId) {
        this.workFlowId = workFlowId;
    }

    public FilePolicyAssignWorkflowCompleter(URI policyUri, Set<URI> assignToResource, URI projectVPool, String task) {
        super(policyUri, task);
        this.assignToResource = new ArrayList<URI>(assignToResource);
        this.projectVPool = projectVPool;
        _log.info("Creating completer for OpId: " + getOpId());
    }

    public FilePolicyAssignWorkflowCompleter(URI policyUri, List<URI> assignToResource, URI projectVPool, String taskId) {
        super(policyUri, taskId);
        this.assignToResource = new ArrayList<URI>(assignToResource);
        this.projectVPool = projectVPool;
        _log.info("Creating completer for OpId: " + getOpId());
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        FilePolicy filePolicy = dbClient.queryObject(FilePolicy.class, getId());
        if (Operation.Status.ready.equals(status)) {
            updatePolicyAssignedResources(dbClient, filePolicy, status, coded);
            setStatus(dbClient, status, coded);
        } else {
            StringBuffer strErrorMsg = new StringBuffer();
            Integer totalWFSteps = 0;
            int numFailedSteps = getWorkFlowFailedSteps(dbClient, getWorkFlowId(), strErrorMsg);
            if (numFailedSteps > 0) {
                totalWFSteps = getWorkFlowSteps(dbClient, getWorkFlowId()).size();
                int successPolicies = totalWFSteps - numFailedSteps;
                // In case of partial success, Update the policy assignment attributes!!
                if (totalWFSteps != numFailedSteps) {
                    updatePolicyAssignedResources(dbClient, filePolicy, status, coded);
                }
                _log.error(String.format(" %s number of storage policies assigned successful and %s failed due to %s ", successPolicies,
                        numFailedSteps, strErrorMsg.toString()));
                ServiceError serviceError = DeviceControllerException.errors.deviceProtectionPolicyOperationFailed(
                        filePolicy.getId().toString(), "assign", numFailedSteps, successPolicies);
                setStatus(dbClient, status, serviceError);
            }
        }
    }

    private void updatePolicyAssignedResources(DbClient dbClient, FilePolicy filePolicy, Status status, ServiceCoded coded) {

        for (URI resourceURI : assignToResource) {
            filePolicy.addAssignedResources(resourceURI);
            FilePolicyApplyLevel applyAt = FilePolicyApplyLevel.valueOf(filePolicy.getApplyAt());
            switch (applyAt) {
                case project:
                    Project project = dbClient.queryObject(Project.class, resourceURI);
                    project.addFilePolicy(filePolicy.getId());
                    dbClient.updateObject(project);
                    if (projectVPool != null) {
                        filePolicy.setFilePolicyVpool(projectVPool);
                    }
                    break;
                case vpool:
                    VirtualPool vpool = dbClient.queryObject(VirtualPool.class, resourceURI);
                    vpool.addFilePolicy(filePolicy.getId());
                    dbClient.updateObject(vpool);
                    break;
                default:
                    break;
            }
        }
        dbClient.updateObject(filePolicy);
    }
}