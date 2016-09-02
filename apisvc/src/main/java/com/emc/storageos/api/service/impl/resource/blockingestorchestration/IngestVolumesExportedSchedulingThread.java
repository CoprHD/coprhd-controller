/*
 * Copyright (c) 2008-2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration;

import java.net.URI;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.UnManagedVolumeService;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.VolumeIngestionContext;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl.BaseIngestionRequestContext;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportGroup.ExportGroupType;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.util.ExceptionUtils;
import com.emc.storageos.db.client.util.ResourceAndUUIDNameGenerator;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;

public class IngestVolumesExportedSchedulingThread implements Runnable {

    private static final Logger _logger = LoggerFactory.getLogger(IngestVolumesExportedSchedulingThread.class);
    private BaseIngestionRequestContext _requestContext;
    private IngestStrategyFactory _ingestStrategyFactory;
    private UnManagedVolumeService _unManagedVolumeService;
    private DbClient _dbClient;
    private Map<String, TaskResourceRep> _taskMap;

    private static final String INGESTION_SUCCESSFUL_MSG = "Successfully ingested exported volume and its masks.";

    /**
     * Constructor.
     * 
     * @param requestContext the BaseIngestionRequestContext
     * @param ingestStrategyFactory the IngestStrategyFactory
     * @param unManagedVolumeService the UnManagedVolumeService
     * @param dbClient the database client
     * @param taskMap a Map of UnManagedVolume ids to TaskResourceReps
     */
    public IngestVolumesExportedSchedulingThread(BaseIngestionRequestContext requestContext,
            IngestStrategyFactory ingestStrategyFactory, UnManagedVolumeService unManagedVolumeService, DbClient dbClient,
            Map<String, TaskResourceRep> taskMap) {
        this._requestContext = requestContext;
        this._ingestStrategyFactory = ingestStrategyFactory;
        this._unManagedVolumeService = unManagedVolumeService;
        this._dbClient = dbClient;
        this._taskMap = taskMap;
    }

    @Override
    public void run() {

        try {
            _requestContext.reset();
            URI varrayId = null;
            while (_requestContext.hasNext()) {
                UnManagedVolume unManagedVolume = _requestContext.next();
                _logger.info("Ingestion starting for exported unmanaged volume {}", unManagedVolume.getNativeGuid());

                if (null == varrayId) {
                    varrayId = _requestContext.getVarray(unManagedVolume).getId();
                }

                TaskResourceRep resourceRep = _taskMap.get(unManagedVolume.getId().toString());
                String taskId = resourceRep != null ? resourceRep.getOpId() : null;

                try {

                    URI storageSystemUri = unManagedVolume.getStorageSystemUri();
                    StorageSystem system = _requestContext.getStorageSystemCache().get(storageSystemUri.toString());
                    if (null == system) {
                        system = _dbClient.queryObject(StorageSystem.class, storageSystemUri);
                        _requestContext.getStorageSystemCache().put(storageSystemUri.toString(), system);
                    }

                    // Build the Strategy , which contains reference to Block object & export orchestrators
                    IngestStrategy ingestStrategy = _ingestStrategyFactory.buildIngestStrategy(unManagedVolume,
                            !IngestStrategyFactory.DISREGARD_PROTECTION);

                    @SuppressWarnings("unchecked")
                    BlockObject blockObject = ingestStrategy.ingestBlockObjects(_requestContext,
                            VolumeIngestionUtil.getBlockObjectClass(unManagedVolume));

                    if (null == blockObject) {
                        throw IngestionException.exceptions.generalVolumeException(
                                unManagedVolume.getLabel(), "check the logs for more details");
                    }

                    _requestContext.getBlockObjectsToBeCreatedMap().put(blockObject.getNativeGuid(), blockObject);
                    _requestContext.getProcessedUnManagedVolumeMap().put(
                            unManagedVolume.getNativeGuid(), _requestContext.getVolumeContext());

                    _logger.info("Volume ingestion completed successfully for exported unmanaged volume {} (export ingestion will follow)",
                            unManagedVolume.getNativeGuid());

                } catch (APIException ex) {
                    _logger.error("error: " + ex.getLocalizedMessage(), ex);
                    _dbClient.error(UnManagedVolume.class,
                            _requestContext.getCurrentUnManagedVolumeUri(), taskId, ex);
                    _requestContext.getVolumeContext().rollback();
                } catch (Exception ex) {
                    _logger.error("error: " + ex.getLocalizedMessage(), ex);
                    _dbClient.error(UnManagedVolume.class,
                            _requestContext.getCurrentUnManagedVolumeUri(),
                            taskId, IngestionException.exceptions.generalVolumeException(
                                    unManagedVolume.getLabel(), ex.getLocalizedMessage()));
                    _requestContext.getVolumeContext().rollback();
                }
            }

            _logger.info("Ingestion of all the unmanaged volumes has completed.");

            // next ingest the export masks for the unmanaged volumes which have been fully ingested
            _logger.info("Ingestion of unmanaged export masks for all requested volumes starting.");
            ingestBlockExportMasks(_requestContext, _taskMap);

            for (VolumeIngestionContext volumeContext : _requestContext.getProcessedUnManagedVolumeMap().values()) {
                // If there is a CG involved in the ingestion, organize, pollenate, and commit.
                _unManagedVolumeService.commitIngestedCG(_requestContext, volumeContext.getUnmanagedVolume());

                // commit the volume itself
                volumeContext.commit();
            }

            for (BlockObject bo : _requestContext.getObjectsIngestedByExportProcessing()) {
                _logger.info("Ingestion Wrap Up: Creating BlockObject {} (hash {})", bo.forDisplay(), bo.hashCode());
                _dbClient.createObject(bo);
            }

            for (UnManagedVolume umv : _requestContext.getUnManagedVolumesToBeDeleted()) {
                _logger.info("Ingestion Wrap Up: Deleting UnManagedVolume {} (hash {})", umv.forDisplay(), umv.hashCode());
                _dbClient.updateObject(umv);
            }

            // Update the related objects if any after successful export mask ingestion
            for (Entry<String, Set<DataObject>> updatedObjectsEntry : _requestContext.getDataObjectsToBeUpdatedMap().entrySet()) {
                if (updatedObjectsEntry != null) {
                    _logger.info("Ingestion Wrap Up: Updating objects for UnManagedVolume URI " + updatedObjectsEntry.getKey());
                    for (DataObject dob : updatedObjectsEntry.getValue()) {
                        if (dob.getInactive()) {
                            _logger.info("Ingestion Wrap Up: Deleting DataObject {} (hash {})", dob.forDisplay(), dob.hashCode());
                        } else {
                            _logger.info("Ingestion Wrap Up: Updating DataObject {} (hash {})", dob.forDisplay(), dob.hashCode());
                        }
                        _dbClient.updateObject(dob);
                    }
                }
            }

            // Create the related objects if any after successful export mask ingestion
            for (Set<DataObject> createdObjects : _requestContext.getDataObjectsToBeCreatedMap().values()) {
                if (createdObjects != null && !createdObjects.isEmpty()) {
                    for (DataObject dob : createdObjects) {
                        _logger.info("Ingestion Wrap Up: Creating DataObject {} (hash {})", dob.forDisplay(), dob.hashCode());
                        _dbClient.createObject(dob);
                    }
                }
            }

            ExportGroup exportGroup = _requestContext.getExportGroup();
            if (_requestContext.isExportGroupCreated()) {
                _logger.info("Ingestion Wrap Up: Creating ExportGroup {} (hash {})", exportGroup.forDisplay(), exportGroup.hashCode());
                _dbClient.createObject(exportGroup);
            } else {
                _logger.info("Ingestion Wrap Up: Updating ExportGroup {} (hash {})", exportGroup.forDisplay(), exportGroup.hashCode());
                _dbClient.updateObject(exportGroup);
            }

            // record the events after they have been persisted
            for (BlockObject volume : _requestContext.getObjectsIngestedByExportProcessing()) {
                _unManagedVolumeService.recordVolumeOperation(_dbClient, _unManagedVolumeService.getOpByBlockObjectType(volume),
                        Status.ready, volume.getId());
            }

        } catch (InternalException e) {
            _logger.error("InternalException occurred due to: {}", e);
            throw e;
        } catch (Exception e) {
            _logger.error("Unexpected exception occurred due to: {}", e);
            throw APIException.internalServerErrors.genericApisvcError(ExceptionUtils.getExceptionMessage(e), e);
        } finally {
            // if we created an ExportGroup, but no volumes were ingested into
            // it, then we should clean it up in the database (CTRL-8520)
            if ((null != _requestContext)
                    && _requestContext.isExportGroupCreated()
                    && _requestContext.getObjectsIngestedByExportProcessing().isEmpty()) {
                _logger.info("Ingestion Wrap Up: an export group was created, but no volumes were ingested into it");
                if (_requestContext.getExportGroup().getVolumes() == null ||
                        _requestContext.getExportGroup().getVolumes().isEmpty()) {
                    _logger.info("Ingestion Wrap Up: since no volumes are present, marking {} for deletion",
                            _requestContext.getExportGroup().getLabel());
                    _dbClient.markForDeletion(_requestContext.getExportGroup());
                }
            }
        }
    }

    /**
     * Ingest block export masks for the already-ingested Volumes.
     *
     * @param requestContext the IngestionRequestContext
     * @param taskMap a Map of UnManagedVolume ids to TaskResourceReps
     */
    private void ingestBlockExportMasks(IngestionRequestContext requestContext, Map<String, TaskResourceRep> taskMap) {
        for (String unManagedVolumeGUID : requestContext.getProcessedUnManagedVolumeMap().keySet()) {
            BlockObject processedBlockObject = requestContext.getProcessedBlockObject(unManagedVolumeGUID);
            VolumeIngestionContext volumeContext = requestContext.getVolumeContext(unManagedVolumeGUID);
            UnManagedVolume processedUnManagedVolume = volumeContext.getUnmanagedVolume();
            URI unManagedVolumeUri = processedUnManagedVolume.getId();
            TaskResourceRep resourceRep = taskMap.get(processedUnManagedVolume.getId().toString());
            String taskId = resourceRep != null ? resourceRep.getOpId() : null;
            try {
                if (processedBlockObject == null) {
                    _logger.warn("The ingested block object is null. Skipping ingestion of export masks for unmanaged volume {}",
                            unManagedVolumeGUID);
                    throw IngestionException.exceptions.generalVolumeException(
                            processedUnManagedVolume.getLabel(), "check the logs for more details");
                }

                // Build the Strategy , which contains reference to Block object & export orchestrators
                IngestExportStrategy ingestStrategy = _ingestStrategyFactory.buildIngestExportStrategy(processedUnManagedVolume);

                BlockObject blockObject = ingestStrategy.ingestExportMasks(processedUnManagedVolume,
                        processedBlockObject, requestContext);
                if (null == blockObject) {
                    throw IngestionException.exceptions.generalVolumeException(
                            processedUnManagedVolume.getLabel(), "check the logs for more details");
                }

                if (null == blockObject.getCreationTime()) {
                    // only add objects to create if they were created this round of ingestion,
                    // creationTime will be null unless the object has already been saved to the db
                    requestContext.getObjectsIngestedByExportProcessing().add(blockObject);
                }

                // If the ingested object is internal, flag an error. If it's an RP volume, it's exempt from this check.
                if (blockObject.checkInternalFlags(Flag.PARTIALLY_INGESTED) &&
                        !(blockObject instanceof Volume && ((Volume) blockObject).getRpCopyName() != null)) {
                    StringBuffer taskStatus = requestContext.getTaskStatusMap().get(processedUnManagedVolume.getNativeGuid());
                    String taskMessage = "";
                    if (taskStatus == null) {
                        // No task status found. Put in a default message.
                        taskMessage = String.format("Not all the parent/replicas of unmanaged volume %s have been ingested",
                                processedUnManagedVolume.getLabel());
                    } else {
                        taskMessage = taskStatus.toString();
                    }
                    _dbClient
                            .error(UnManagedVolume.class, processedUnManagedVolume.getId(), taskId,
                                    IngestionException.exceptions.unmanagedVolumeIsNotVisible(processedUnManagedVolume.getLabel(),
                                            taskMessage));
                } else {
                    _dbClient.ready(UnManagedVolume.class,
                            processedUnManagedVolume.getId(), taskId, INGESTION_SUCCESSFUL_MSG);
                }

            } catch (APIException ex) {
                _logger.warn(ex.getLocalizedMessage(), ex);
                _dbClient.error(UnManagedVolume.class, unManagedVolumeUri, taskId, ex);
                volumeContext.rollback();
            } catch (Exception ex) {
                _logger.warn(ex.getLocalizedMessage(), ex);
                _dbClient.error(UnManagedVolume.class, unManagedVolumeUri,
                        taskId, IngestionException.exceptions.generalVolumeException(
                                processedUnManagedVolume.getLabel(), ex.getLocalizedMessage()));
                volumeContext.rollback();
            }
        }
    }

    /**
     * Executes API Tasks on a separate thread by instantiating a IngestVolumesExportedSchedulingThread.
     * 
     * @param executorService the ExecutorService
     * @param requestContext the BaseIngestionRequestContext
     * @param ingestStrategyFactory the IngestStrategyFactory
     * @param unManagedVolumeService the UnManagedVolumeService
     * @param dbClient the database client
     * @param taskMap a Map of UnManagedVolume ids to TaskResourceReps
     * @param taskList a list of Tasks
     */
    public static void executeApiTask(ExecutorService executorService, BaseIngestionRequestContext requestContext,
            IngestStrategyFactory ingestStrategyFactory, UnManagedVolumeService unManagedVolumeService, DbClient dbClient,
            Map<String, TaskResourceRep> taskMap, TaskList taskList) {

        IngestVolumesExportedSchedulingThread schedulingThread = new IngestVolumesExportedSchedulingThread(requestContext,
                ingestStrategyFactory, unManagedVolumeService, dbClient, taskMap);

        try {
            executorService.execute(schedulingThread);
        } catch (Exception e) {
            String message = "Failed to start unmanaged volume ingestion tasks...";
            _logger.error(message, e);
            for (TaskResourceRep taskRep : taskList.getTaskList()) {
                taskRep.setMessage(message);
            }
        }
    }
}
