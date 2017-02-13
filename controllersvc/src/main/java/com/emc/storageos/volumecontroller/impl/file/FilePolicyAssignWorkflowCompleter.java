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
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class FilePolicyAssignWorkflowCompleter extends FilePolicyWorkflowCompleter {

    private static final long serialVersionUID = 1L;
    protected static final Logger _log = LoggerFactory.getLogger(FilePolicyAssignWorkflowCompleter.class);
    private ArrayList<URI> assignToResource;

    public FilePolicyAssignWorkflowCompleter(URI policyUri, Set<URI> assignToResource, String task) {
        super(policyUri, task);
        this.assignToResource = new ArrayList<URI>(assignToResource);
        _log.info("Creating completer for OpId: " + getOpId());
    }

    public FilePolicyAssignWorkflowCompleter(URI policyUri, List<URI> assignToResource, String taskId) {
        super(policyUri, taskId);
        this.assignToResource = new ArrayList<URI>(assignToResource);
        _log.info("Creating completer for OpId: " + getOpId());
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        FilePolicy filePolicy = dbClient.queryObject(FilePolicy.class, getId());

        for (URI resourceURI : assignToResource) {
            filePolicy.addAssignedResources(resourceURI);
            FilePolicyApplyLevel applyAt = FilePolicyApplyLevel.valueOf(filePolicy.getApplyAt());
            switch (applyAt) {
                case project:
                    Project project = dbClient.queryObject(Project.class, resourceURI);
                    project.addFilePolicy(filePolicy.getId());
                    dbClient.updateObject(project);
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
        setStatus(dbClient, status, coded);
    }
}