/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportPathParams;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.export.ExportUpdateParam;
import com.emc.storageos.model.block.export.VolumeParam;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.volumecontroller.BlockExportController;
import com.google.common.base.Joiner;

/**
 * Background thread that runs the placement, scheduling, and controller dispatching of an export group update
 * request. This allows the API to return a Task object quickly.
 */
class CreateExportGroupUpdateSchedulingThread implements Runnable {

    static final Logger _log = LoggerFactory.getLogger(CreateExportGroupUpdateSchedulingThread.class);

    private final DbClient dbClient;
    private final ExportGroupService exportGroupService;
    private final ExportUpdateParam exportUpdateParam;
    private Project project;
    private ExportGroup exportGroup;
    private String task;
    private TaskResourceRep taskRes;

    private CreateExportGroupUpdateSchedulingThread(DbClient dbClient, ExportGroupService exportGroupService, Project project,
            ExportGroup exportGroup, ExportUpdateParam exportUpdateParam, String task, TaskResourceRep taskRes) {
        this.dbClient = dbClient;
        this.exportGroupService = exportGroupService;
        this.project = project;
        this.exportGroup = exportGroup;
        this.exportUpdateParam = exportUpdateParam;
        this.task = task;
        this.taskRes = taskRes;
    }

    @Override
    public void run() {
        _log.info("Starting scheduling for export group update thread...");
        try {
            Map<URI, Integer> newVolumesMap = exportGroupService.getUpdatedVolumesMap(
                                                            exportUpdateParam, exportGroup);
            Map<URI, Map<URI, Integer>> storageMap = exportGroupService.computeAndValidateVolumes(newVolumesMap, exportGroup,
                    exportUpdateParam);
            _log.info("Updated volumes belong to storage systems: {}", Joiner.on(',').join(storageMap.keySet()));
            
            // Convert the storageMap to a list of added and removed Block Objects
            newVolumesMap.clear();
            for (Map.Entry<URI, Map<URI, Integer>> entry : storageMap.entrySet()) {
                newVolumesMap.putAll(entry.getValue());
            }
            Map<URI, Integer> addedBlockObjectsMap = new HashMap<URI, Integer>();
            Map<URI, Integer> removedBlockObjectsMap = new HashMap<URI, Integer>();
            ExportUtils.getAddedAndRemovedBlockObjects(newVolumesMap, exportGroup, addedBlockObjectsMap, removedBlockObjectsMap);
            _log.info("Added volumes: {}", Joiner.on(',').join(addedBlockObjectsMap.keySet()));
            _log.info("Removed volumes: {}", Joiner.on(',').join(removedBlockObjectsMap.keySet()));
            
            // If ExportPathParameter block is present, and volumes are added, capture ExportPathParameters arguments.
            // This looks weird, but isn't. We use the added volumes from ExportCreateParam instead of addedBlockObjectsMap
            // because the user may want to change the parameters for volumes that are already exported. In this way,
            // the same volume can have different parameters to different hosts.
            Map<URI, Integer> addedVolumeParams = exportGroupService.getChangedVolumes(exportUpdateParam, true);
            ExportPathParams exportPathParam = null;
            if (exportUpdateParam.getExportPathParameters() != null && !addedVolumeParams.keySet().isEmpty()) {
                exportPathParam = exportGroupService.validateAndCreateExportPathParam(
                        exportUpdateParam.getExportPathParameters(), exportGroup, addedVolumeParams.keySet());
                exportGroupService.addBlockObjectsToPathParamMap(addedVolumeParams.keySet(), exportPathParam.getId(), exportGroup);
            }
            // Remove the block objects being deleted from any existing path parameters.
            exportGroupService.removeBlockObjectsFromPathParamMap(removedBlockObjectsMap.keySet(), exportGroup);

            // Validate updated entries
            List<URI> newInitiators = StringSetUtil.stringSetToUriList(exportGroup.getInitiators());
            List<URI> newHosts = StringSetUtil.stringSetToUriList(exportGroup.getHosts());
            List<URI> newClusters = StringSetUtil.stringSetToUriList(exportGroup.getClusters());
            exportGroupService.validateClientsAndUpdate(exportGroup, project, storageMap.keySet(), exportUpdateParam, newClusters,
                    newHosts, newInitiators);
            _log.info("All clients were successfully validated");
            dbClient.persistObject(exportGroup);
            if (exportPathParam != null) {
                dbClient.createObject(exportPathParam);
            }

            // push it to storage devices
            BlockExportController exportController = exportGroupService.getExportController();
            _log.info("Submitting export group update request.");
            exportController.exportGroupUpdate(exportGroup.getId(), addedBlockObjectsMap, removedBlockObjectsMap,
                    newClusters, newHosts, newInitiators, task);
        } catch (Exception ex) {
            if (ex instanceof ServiceCoded) {
                dbClient.error(ExportGroup.class, taskRes.getResource().getId(), taskRes.getOpId(), (ServiceCoded) ex);
            } else {
                dbClient.error(ExportGroup.class, taskRes.getResource().getId(), taskRes.getOpId(),
                        InternalServerErrorException.internalServerErrors
                                .unexpectedErrorExportGroupPlacement(ex));
            }
            _log.error(ex.getMessage(), ex);
            taskRes.setMessage(ex.getMessage());
        }
        _log.info("Ending export group update scheduling thread...");
    }

    /**
     * Interface to kick off an ExportGroup update.
     *
     * @param exportGroupService [IN] - ExportGroupService reference for accessing shared methods and references
     * @param executorService [IN] - ExecutorService used for scheduling these requests
     * @param dbClient [IN] - DbClient for DB access
     * @param project [IN] - Project in which this ExportGroup (and volumes) belong
     * @param exportGroup [IN] - ExportGroup that will be updated
     * @param exportUpdateParam [IN] - ExportGroup Update parameters
     * @param task [IN] - Task ID String
     * @param taskRes [IN] - TaskRestRep for updating task messages
     */
    public static void executeApiTask(ExportGroupService exportGroupService, ExecutorService executorService, DbClient dbClient,
            Project project, ExportGroup exportGroup, ExportUpdateParam exportUpdateParam, String task, TaskResourceRep taskRes) {

        CreateExportGroupUpdateSchedulingThread schedulingThread = new CreateExportGroupUpdateSchedulingThread(dbClient, exportGroupService,
                project, exportGroup, exportUpdateParam, task, taskRes);
        try {
            executorService.execute(schedulingThread);
        } catch (Exception e) {
            String message = "Failed to execute export group update API task for resource " + exportGroup.getId();
            _log.error(message);
            taskRes.setMessage(message);
        }
    }

}