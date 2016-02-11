/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import com.emc.storageos.api.service.impl.placement.FileRecommendation;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.file.FileSystemParam;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Background thread that runs the placement, scheduling, and controller dispatching of a create file
 * request. This allows the API to return a Task object quickly.
 */
public class CreateFileSystemSchedulingThread implements Runnable {
    static final Logger _log = LoggerFactory.getLogger(CreateFileSystemSchedulingThread.class);

    private final FileService fileService;
    private VirtualArray varray;
    private Project project;
    private VirtualPool vpool;
    private VirtualPoolCapabilityValuesWrapper capabilities;
    private TaskList taskList;
    private String task;
    private ArrayList<String> requestedTypes;
    private FileSystemParam param;
    private FileServiceApi fileServiceImpl;
    private String SuggestedNativeFsId;
    private TenantOrg tenantOrg;
    DataObject.Flag[] flags;

    public CreateFileSystemSchedulingThread(FileService fileService, VirtualArray varray, Project project,
            VirtualPool vpool,
            TenantOrg tenantOrg, DataObject.Flag[] flags,
            VirtualPoolCapabilityValuesWrapper capabilities,
            TaskList taskList, String task, ArrayList<String> requestedTypes,
            FileSystemParam param,
            FileServiceApi fileServiceImpl,
            String suggestedNativeFsId) {

        this.fileService = fileService;
        this.varray = varray;
        this.project = project;
        this.vpool = vpool;
        this.tenantOrg = tenantOrg;
        this.flags = flags;
        this.capabilities = capabilities;
        this.taskList = taskList;
        this.task = task;
        this.requestedTypes = requestedTypes;
        this.param = param;
        this.fileServiceImpl = fileServiceImpl;
        this.SuggestedNativeFsId = suggestedNativeFsId;
    }

    @Override
    public void run() {
        _log.info("Starting scheduling placement thread for task {}", task);
        try {
            // Call out placementManager to get the recommendation for placement.
            List recommendations = this.fileService._filePlacementManager.getRecommendationsForFileCreateRequest(
                    varray, project, vpool, capabilities);
            if (recommendations.isEmpty()) {
                throw APIException.badRequests.noMatchingStoragePoolsForVpoolAndVarray(vpool.getLabel(), varray.getLabel());
            } else {
                // Call out to the respective file service implementation to prepare
                // and create the fileshares based on the recommendations.
                fileServiceImpl.createFileSystems(param, project, varray, vpool, tenantOrg,
                        flags, recommendations, taskList, task, capabilities);
            }
        } catch (Exception ex) {
            for (TaskResourceRep taskObj : taskList.getTaskList()) {
                if (ex instanceof ServiceCoded) {
                    this.fileService._dbClient.error(FileShare.class, taskObj.getResource().getId(), taskObj.getOpId(), (ServiceCoded) ex);
                } else {
                    this.fileService._dbClient.error(FileShare.class, taskObj.getResource().getId(), taskObj.getOpId(),
                            InternalServerErrorException.internalServerErrors
                                    .unexpectedErrorVolumePlacement(ex));
                }
                _log.error(ex.getMessage(), ex);
                taskObj.setMessage(ex.getMessage());
                // Set the fileshare to inactive
                FileShare file = this.fileService._dbClient.queryObject(FileShare.class, taskObj.getResource().getId());
                file.setInactive(true);
                this.fileService._dbClient.updateObject(file);
            }
        }
        _log.info("Ending scheduling/placement thread...");

    }

    /**
     * Static method to execute the API task in the background
     *
     * @param fileService file service ("this" from caller)
     * @param executorService executor service that manages the thread pool
     * @param dbClient db client
     * @param varray virtual array
     * @param project project
     * @param vpool virtual pool
     * @param capabilities capabilities object
     * @param taskList list of tasks
     * @param task task ID
     * @param requestedTypes requested types
     * @param param file creation request params
     * @param fileServiceImpl file service impl to call
     */

    public static void executeApiTask(FileService fileService, ExecutorService executorService,
    		DbClient dbClient, VirtualArray varray,
            Project project,
            VirtualPool vpool,
            TenantOrg tenantOrg, DataObject.Flag[] flags,
            VirtualPoolCapabilityValuesWrapper capabilities,
            TaskList taskList, String task, ArrayList<String> requestedTypes,
            FileSystemParam param,
            FileServiceApi fileServiceImpl, String suggestedNativeFsId) {
        CreateFileSystemSchedulingThread schedulingThread = new CreateFileSystemSchedulingThread(
                fileService, varray, project, vpool, tenantOrg, flags, capabilities, taskList, task,
                requestedTypes, param, fileServiceImpl, suggestedNativeFsId);
        try {
            executorService.execute(schedulingThread);
        } catch (Exception e) {
            for (TaskResourceRep taskObj : taskList.getTaskList()) {
                String message = "Failed to execute file creation API task for resource " + taskObj.getResource().getId();
                _log.error(message);
                taskObj.setMessage(message);
                // Set the fileshare to inactive
                FileShare fileShare = dbClient.queryObject(FileShare.class, taskObj.getResource().getId());
                fileShare.setInactive(true);
                dbClient.updateObject(fileShare);
            }
        }
    }

}
