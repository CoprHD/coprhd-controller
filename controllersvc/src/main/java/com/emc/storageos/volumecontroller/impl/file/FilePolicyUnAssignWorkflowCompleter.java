/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.file;

import java.net.URI;
import java.util.ArrayList;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FilePolicy;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyApplyLevel;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.fileorchestrationcontroller.FileOrchestrationUtils;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;

public class FilePolicyUnAssignWorkflowCompleter extends FilePolicyWorkflowCompleter {
    private static final long serialVersionUID = 1L;
    protected static final Logger _log = LoggerFactory.getLogger(FilePolicyUnAssignWorkflowCompleter.class);
    private ArrayList<URI> unassignFromResource;
    private URI workFlowId;

    public URI getWorkFlowId() {
        return workFlowId;
    }

    public void setWorkFlowId(URI workFlowId) {
        this.workFlowId = workFlowId;
    }

    public FilePolicyUnAssignWorkflowCompleter(URI policyUri, Set<URI> unassignFromResource, String task) {
        super(policyUri, task);
        this.unassignFromResource = new ArrayList<URI>(unassignFromResource);
        _log.info("Creating completer for OpId: " + getOpId());
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            FilePolicy filePolicy = dbClient.queryObject(FilePolicy.class, getId());
            if (Operation.Status.ready.equals(status)) {
                updatePolicyUnAssignedResources(dbClient, filePolicy, status, coded);
                setStatus(dbClient, status, coded);
            } else {
                StringBuffer strErrorMsg = new StringBuffer();
                Integer totalWFSteps = 0;
                int numFailedSteps = getWorkFlowFailedSteps(dbClient, getWorkFlowId(), strErrorMsg);
                if (numFailedSteps > 0) {
                    totalWFSteps = getWorkFlowSteps(dbClient, getWorkFlowId()).size();
                    int successPolicies = totalWFSteps - numFailedSteps;
                    // In case of partial success, Do not update policy assignment attributes!!
                    // as there are some more storage system policy resources!!
                    _log.error(String.format(" %s number of storage policies assigned successful and %s failed due to %s ", successPolicies,
                            numFailedSteps, strErrorMsg.toString()));
                    ServiceError serviceError = DeviceControllerException.errors.deviceProtectionPolicyOperationFailed(
                            filePolicy.getId().toString(), "unassign", numFailedSteps, successPolicies);
                    setStatus(dbClient, status, serviceError);
                }
            }

        } catch (Exception e) {
            _log.info("Unassign file policy: and its resource DB object failed", getId(), e);
        }
    }

    protected void updatePolicyUnAssignedResources(DbClient dbClient, FilePolicy filePolicy, Status status, ServiceCoded coded) {
        for (URI resourceURI : unassignFromResource) {
            filePolicy.removeAssignedResources(resourceURI);
            FilePolicyApplyLevel applyLevel = FilePolicyApplyLevel.valueOf(filePolicy.getApplyAt());
            switch (applyLevel) {
                case vpool:
                    VirtualPool vpool = dbClient.queryObject(VirtualPool.class, resourceURI);
                    vpool.removeFilePolicy(filePolicy.getId());
                    dbClient.updateObject(vpool);
                    break;
                case project:
                    Project project = dbClient.queryObject(Project.class, resourceURI);
                    project.removeFilePolicy(project, filePolicy.getId());
                    dbClient.updateObject(project);
                    break;
                case file_system:
                    FileShare fs = dbClient.queryObject(FileShare.class, resourceURI);
                    fs.removeFilePolicy(filePolicy.getId());
                    dbClient.updateObject(fs);
                    break;
                default:
                    _log.error("Not a valid policy apply level: " + applyLevel);
            }
        }

        if (filePolicy.getAssignedResources() == null || filePolicy.getAssignedResources().isEmpty()) {
            // if no resources are attached to policy
            // remove the file policy vpool
            if (!NullColumnValueGetter.isNullURI(filePolicy.getFilePolicyVpool())) {
                filePolicy.setFilePolicyVpool(NullColumnValueGetter.getNullURI());
            }

            // If no other resources are assigned to replication policy
            // Remove the replication topology from the policy
            FileOrchestrationUtils.removeTopologyInfo(filePolicy, dbClient);
        }
        dbClient.updateObject(filePolicy);
    }
}
