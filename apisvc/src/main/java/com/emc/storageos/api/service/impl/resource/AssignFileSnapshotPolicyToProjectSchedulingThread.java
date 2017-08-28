/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FilePolicy;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;

public class AssignFileSnapshotPolicyToProjectSchedulingThread implements Runnable {

    private static final Logger _log = LoggerFactory.getLogger(AssignFileSnapshotPolicyToProjectSchedulingThread.class);

    private final FilePolicyService filePolicyService;
    private Map<URI, List<URI>> vpoolToStorageSystemMap;
    private List<URI> projectURIs;
    private URI filePolicyToAssign;
    private FileServiceApi fileServiceImpl;
    private TaskResourceRep taskObject;
    private String task;

    public AssignFileSnapshotPolicyToProjectSchedulingThread(FilePolicyService fileService, URI filePolicyToAssign,
            Map<URI, List<URI>> vpoolToStorageSystemMap,
            List<URI> projectURIs, FileServiceApi fileServiceImpl, TaskResourceRep taskObject, String task) {

        this.filePolicyService = fileService;
        this.vpoolToStorageSystemMap = vpoolToStorageSystemMap;
        this.projectURIs = projectURIs;
        this.filePolicyToAssign = filePolicyToAssign;
        this.fileServiceImpl = fileServiceImpl;
        this.taskObject = taskObject;
        this.task = task;

    }

    @Override
    public void run() {
        _log.info("Starting scheduling placement thread for task {}", task);
        try {
            fileServiceImpl.assignFilePolicyToProjects(vpoolToStorageSystemMap, projectURIs, filePolicyToAssign, task);
        } catch (Exception ex) {
            if (ex instanceof ServiceCoded) {
                this.filePolicyService._dbClient
                .error(FilePolicy.class, taskObject.getResource().getId(), taskObject.getOpId(), (ServiceCoded) ex);
            } else {
                this.filePolicyService._dbClient.error(FilePolicy.class, taskObject.getResource().getId(), taskObject.getOpId(),
                        InternalServerErrorException.internalServerErrors
                        .unexpectedErrorVolumePlacement(ex));
            }
            _log.error(ex.getMessage(), ex);
            taskObject.setMessage(ex.getMessage());

        }
        _log.info("Ending scheduling/placement thread...");

    }

    public static void executeApiTask(FilePolicyService fileService, ExecutorService executorService,
            DbClient dbClient, URI filePolicyToAssign,
            Map<URI, List<URI>> vpoolToStorageSystemMap, List<URI> projectURIs,
            FileServiceApi fileServiceImpl, TaskResourceRep taskObject, String task) {

        AssignFileSnapshotPolicyToProjectSchedulingThread schedulingThread = new AssignFileSnapshotPolicyToProjectSchedulingThread(
                fileService, filePolicyToAssign,
                vpoolToStorageSystemMap, projectURIs, fileServiceImpl, taskObject, task);
        try {
            executorService.execute(schedulingThread);
        } catch (Exception e) {
            String message = "Failed to execute file assign policy API task for resource " + taskObject.getResource().getId();
            _log.error(message);
            taskObject.setMessage(message);
        }
    }

}
