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

public class AssignFileSnapshotPolicyToVpoolSchedulingThread implements Runnable {

    private static final Logger _log = LoggerFactory.getLogger(AssignFileSnapshotPolicyToVpoolSchedulingThread.class);

    private final FilePolicyService filePolicyService;
    private Map<URI, List<URI>> vpoolToStorageSystemMap;
    private URI filePolicyToAssign;
    private FileServiceApi fileServiceImpl;
    private TaskResourceRep taskObject;
    private String task;

    public AssignFileSnapshotPolicyToVpoolSchedulingThread(FilePolicyService fileService, URI filePolicyToAssign,
            Map<URI, List<URI>> vpoolToStorageSystemMap,
            FileServiceApi fileServiceImpl, TaskResourceRep taskObject, String task) {

        this.filePolicyService = fileService;
        this.vpoolToStorageSystemMap = vpoolToStorageSystemMap;
        this.filePolicyToAssign = filePolicyToAssign;
        this.fileServiceImpl = fileServiceImpl;
        this.taskObject = taskObject;
        this.task = task;

    }

    @Override
    public void run() {
        _log.info("Starting scheduling placement thread for task {}", task);
        try {
            fileServiceImpl.assignFilePolicyToVirtualPools(vpoolToStorageSystemMap, filePolicyToAssign, task);
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
            Map<URI, List<URI>> vpoolToStorageSystemMap,
            FileServiceApi fileServiceImpl, TaskResourceRep taskObject, String task) {

        AssignFileSnapshotPolicyToVpoolSchedulingThread schedulingThread = new AssignFileSnapshotPolicyToVpoolSchedulingThread(fileService,
                filePolicyToAssign,
                vpoolToStorageSystemMap, fileServiceImpl, taskObject, task);
        try {
            executorService.execute(schedulingThread);
        } catch (Exception e) {
            String message = "Failed to execute file assign policy API task for resource " + taskObject.getResource().getId();
            _log.error(message);
            taskObject.setMessage(message);
        }
    }

}
