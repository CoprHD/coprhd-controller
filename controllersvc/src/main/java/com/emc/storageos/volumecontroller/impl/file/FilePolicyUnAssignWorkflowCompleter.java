/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.file;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.FilePolicy;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyApplyLevel;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.FileShare.PersonalityTypes;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
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
        try {
            if (Operation.Status.ready.equals(status)) {
                FilePolicy filePolicy = dbClient.queryObject(FilePolicy.class, getId());

                for (URI resourceURI : unassignFromResource) {
                    filePolicy.removeAssignedResources(resourceURI);
                    FilePolicyApplyLevel applyLevel = FilePolicyApplyLevel.valueOf(filePolicy.getApplyAt());
                    switch (applyLevel) {
                        case vpool:
                            updateFileSystemStatus(dbClient, resourceURI);
                            VirtualPool vpool = dbClient.queryObject(VirtualPool.class, resourceURI);
                            vpool.removeFilePolicy(filePolicy.getId());
                            dbClient.updateObject(vpool);
                            break;
                        case project:
                            updateFileSystemStatus(dbClient, filePolicy.getFilePolicyVpool(), resourceURI);
                            Project project = dbClient.queryObject(Project.class, resourceURI);
                            project.removeFilePolicy(project, filePolicy.getId());
                            dbClient.updateObject(project);
                            break;
                        case file_system:
                            FileShare fs = dbClient.queryObject(FileShare.class, resourceURI);
                            removeReplicationInfo(Collections.singletonList(fs), dbClient);
                            fs.removeFilePolicy(filePolicy.getId());
                            dbClient.updateObject(fs);
                            break;
                        default:
                            _log.error("Not a valid policy apply level: " + applyLevel);
                    }
                }
                dbClient.updateObject(filePolicy);
            }
            setStatus(dbClient, status, coded);
        } catch (Exception e) {
            _log.info("updating file policy: and its resource DB object failed", getId(), e);
        }
    }

    protected void updateFileSystemStatus(DbClient dbClient, URI vpoolURI) {

        ContainmentConstraint containmentConstraint = ContainmentConstraint.Factory.getVirtualPoolFileshareConstraint(vpoolURI);
        List<FileShare> fileshares = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient, FileShare.class,
                containmentConstraint);
        if (!fileshares.isEmpty()) {
            removeReplicationInfo(fileshares, dbClient);
        }
    }

    protected void updateFileSystemStatus(DbClient dbClient, URI vpoolURI, URI project) {

        ContainmentConstraint containmentConstraint = ContainmentConstraint.Factory.getVirtualPoolFileshareConstraint(vpoolURI);
        List<FileShare> fileshares = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient, FileShare.class,
                containmentConstraint);
        List<FileShare> projectFileshares = new ArrayList<>();
        for (FileShare fileshare : fileshares) {
            if (fileshare.getProject().getURI().equals(project)) {
                projectFileshares.add(fileshare);
            }
        }
        if (!projectFileshares.isEmpty()) {
            removeReplicationInfo(projectFileshares, dbClient);
        }
    }

    protected void removeReplicationInfo(List<FileShare> fileshares, DbClient dbClient) {
        for (FileShare fileshare : fileshares) {
            fileshare.setMirrorStatus(NullColumnValueGetter.getNullStr());
            fileshare.setAccessState(NullColumnValueGetter.getNullStr());
            if (fileshare.getMirrorfsTargets() != null
                    && fileshare.getPersonality() != null
                    && fileshare.getPersonality().equalsIgnoreCase(PersonalityTypes.SOURCE.toString())) {
                StringSet targets = fileshare.getMirrorfsTargets();
                if (targets != null && !targets.isEmpty()) {
                    targets.clear();
                }
                fileshare.setMirrorfsTargets(targets);
                fileshare.setPersonality(NullColumnValueGetter.getNullStr());
                dbClient.updateObject(fileshare);

            } else if (fileshare.getParentFileShare() != null
                    && fileshare.getPersonality() != null
                    && fileshare.getPersonality().equalsIgnoreCase(PersonalityTypes.TARGET.toString())) {
                fileshare.setPersonality(NullColumnValueGetter.getNullStr());
                fileshare.setParentFileShare(NullColumnValueGetter.getNullNamedURI());
                dbClient.updateObject(fileshare);
            }
        }
        _log.info("Removed the replication information for fileshares: {}", fileshares);
    }
}
