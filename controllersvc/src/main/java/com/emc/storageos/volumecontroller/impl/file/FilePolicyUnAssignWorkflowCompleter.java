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
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class FilePolicyUnAssignWorkflowCompleter extends FilePolicyWorkflowCompleter {
    private static final long serialVersionUID = 1L;
    protected static final Logger _log = LoggerFactory.getLogger(FilePolicyUnAssignWorkflowCompleter.class);
    private ArrayList<URI> unassignFromResource;

    public FilePolicyUnAssignWorkflowCompleter(URI policyUri, Set<URI> unassignFromResource, String task) {
        super(policyUri, task);
        this.unassignFromResource = new ArrayList<URI>(unassignFromResource);
        _log.info("Creating completer for OpId: " + getOpId());
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        FilePolicy filePolicy = dbClient.queryObject(FilePolicy.class, getId());

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
        dbClient.updateObject(filePolicy);
        setStatus(dbClient, status, coded);
    }
}
