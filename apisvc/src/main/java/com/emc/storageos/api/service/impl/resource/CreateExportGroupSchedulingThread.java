/*
 * Copyright (c) 2015 EMC Corporation
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
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportPathParams;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.export.ExportPathParameters;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.emc.storageos.volumecontroller.BlockExportController;

/**
 * Background thread that runs the placement, scheduling, and controller dispatching of an export group creation
 * request. This allows the API to return a Task object quickly.
 */
class CreateExportGroupSchedulingThread implements Runnable {

    static final Logger _log = LoggerFactory.getLogger(CreateExportGroupSchedulingThread.class);

    private final ExportGroupService exportGroupService;
    private VirtualArray virtualArray;
    private Project project;
    private ExportGroup exportGroup;
    // Map of <storagesystem-id, Map<volume-id, HLU>>
    private Map<URI, Map<URI, Integer>> storageMap;
    private List<URI> hosts;
    private List<URI> clusters;
    private List<URI> initiators;
    private Map<URI, Integer> volumeMap;
    private String task;
    private TaskResourceRep taskRes;
    private ExportPathParameters pathParam;

    public CreateExportGroupSchedulingThread(ExportGroupService exportGroupService, VirtualArray virtualArray, Project project, ExportGroup exportGroup,
            Map<URI, Map<URI, Integer>> storageMap, List<URI> clusters, List<URI> hosts, List<URI> initiators, Map<URI, Integer> volumeMap,
            ExportPathParameters pathParam, String task, TaskResourceRep taskRes) {
        this.exportGroupService = exportGroupService;
        this.virtualArray = virtualArray;
        this.project = project;
        this.exportGroup = exportGroup;
        this.storageMap = storageMap;
        this.clusters = clusters;
        this.hosts = hosts;
        this.initiators = initiators;
        this.volumeMap = volumeMap;
        this.task = task;
        this.taskRes = taskRes;
        this.pathParam = pathParam;
    }

    @Override
    public void run() {
        _log.info("Starting scheduling for export group create thread...");
        // Call out placementManager to get the recommendation for placement.
        try {
            // validate clients (initiators, hosts clusters) input and package them
            // This call may take a long time.
            List<URI> affectedInitiators = this.exportGroupService.validateClientsAndPopulate(exportGroup,
                    project, virtualArray, storageMap.keySet(),
                    clusters, hosts, initiators,
                    volumeMap.keySet());
            _log.info("Initiators {} will be used.", affectedInitiators);
            
            // If ExportPathParameter block is present, and volumes are present, capture those arguments.
            if (pathParam!= null && !volumeMap.keySet().isEmpty()) {
                ExportPathParams exportPathParam = exportGroupService.validateAndCreateExportPathParam(pathParam, 
                                    exportGroup, volumeMap.keySet());
                exportGroupService.addBlockObjectsToPathParamMap(volumeMap.keySet(), exportPathParam.getId(), exportGroup);
                exportGroupService._dbClient.createObject(exportPathParam);
            }
            this.exportGroupService._dbClient.persistObject(exportGroup);

            // If initiators list is empty or storage map is empty, there's no work to do (yet).
            if (storageMap.isEmpty() || affectedInitiators.isEmpty()) {
                this.exportGroupService._dbClient.ready(ExportGroup.class, taskRes.getResource().getId(), taskRes.getOpId());
                return;
            }

            // push it to storage devices
            BlockExportController exportController = this.exportGroupService.getExportController();
            _log.info("createExportGroup request is submitted.");
            exportController.exportGroupCreate(exportGroup.getId(), volumeMap, affectedInitiators, task);
        } catch (Exception ex) {
            if (ex instanceof ServiceCoded) {
                this.exportGroupService._dbClient.error(ExportGroup.class, taskRes.getResource().getId(), taskRes.getOpId(),
                        (ServiceCoded) ex);
            } else {
                this.exportGroupService._dbClient.error(ExportGroup.class, taskRes.getResource().getId(), taskRes.getOpId(),
                        InternalServerErrorException.internalServerErrors
                                .unexpectedErrorExportGroupPlacement(ex));
            }
            _log.error(ex.getMessage(), ex);
            taskRes.setMessage(ex.getMessage());
            // Mark the export group to be deleted
            this.exportGroupService._dbClient.markForDeletion(exportGroup);
        }
        _log.info("Ending export group create scheduling thread...");
    }

    /**
     * Static method to kick off the execution of the API level task to create an export group.
     * 
     * @param exportGroupService export group service ("this" from caller)
     * @param executorService executor service for bounded thread pooling management
     * @param dbClient dbclient
     * @param virtualArray virtual array
     * @param project project
     * @param exportGroup export group
     * @param storageMap storage map
     * @param clusters clusters
     * @param hosts hosts
     * @param initiators initiators
     * @param volumeMap volume map
     * @param pathParam ExportPathParameters from 
     * @param task task
     * @param taskRes task resource object
     */
    public static void executeApiTask(ExportGroupService exportGroupService, ExecutorService executorService, DbClient dbClient,
            VirtualArray virtualArray, Project project,
            ExportGroup exportGroup,
            Map<URI, Map<URI, Integer>> storageMap, List<URI> clusters, List<URI> hosts, List<URI> initiators, Map<URI, Integer> volumeMap,
            ExportPathParameters pathParam, String task, TaskResourceRep taskRes) {

        CreateExportGroupSchedulingThread schedulingThread = new CreateExportGroupSchedulingThread(exportGroupService, virtualArray,
                project, exportGroup, storageMap, clusters, hosts, initiators, volumeMap, pathParam, task, taskRes);
        try {
            executorService.execute(schedulingThread);
        } catch (Exception e) {
            String message = "Failed to execute export group creation API task for resource " + exportGroup.getId();
            _log.error(message);
            taskRes.setMessage(message);
            // Set the export group to inactive
            exportGroup.setInactive(true);
            dbClient.updateAndReindexObject(exportGroup);
        }
    }
}