/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.BlockStorageDevice;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportOrchestrationTask;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportTaskCompleter;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.workflow.Workflow;
import com.google.common.base.Joiner;

/**
 * XtremIO Masking Orchestrator
 * 
 * Steps Export Group Create :
 * **************************
 * There is no equivalent entity in XtremIO Array which can contribute to an export mask in Array.
 * As Group of LunMaps contribute to a Export Mask, there is really no need to determine create
 * mask steps based on existing Masking information on Array.Always create an export mask irrespective of what's
 * available on Array.
 * 
 * Export Group Add Volume
 * ************************
 * 
 * If there are no export masks within export Group , then generateExportGroupCreateWorkFlow
 * else for each export Masks in export Group
 * if exportMask belongs to the same Storage Array
 * GenerateAddVolumeWorkflow
 * GenerateZoneAdd
 * if initiators are missing in export mask, then
 * addInitiatorWorkFlow else
 * Else if there are no export masks belonging to the Storage System
 * Group Initiators by compute resource
 * For each compute resource
 * GenerateExportMaskWorkFlow
 * GenerateZoneNew
 * 
 * Add Volume to Host 1 alone in existing cluster support [Exclusive]
 * *****************************************************************
 * 
 * Find the Export Mask corresponds to the list of given Hosts's initiator
 * If all initiators found
 * GenerateAddVolumeWorkFlow
 * Else if only a partial set of initiators found
 * GenerateAddInitiatorWorkFlow
 * 
 * Export Group Add Initiators
 * ***************************
 * 
 * If there are no export masks , then generateExportGrouPCreateWorkFlow
 * else For each export Masks in export Group
 * if exportMask belongs to the same Storage Array
 * if initiators are present
 * addVolumesWorkFlow
 * if volumes needs to be added AddZoningWokflow
 * else
 * Group ExportMasks by Host, if initiator's Host is found
 * then AddInitiatorToExportMask
 * AddZoningInitiatorWorkFlow
 * AddVolumesWorkFlow
 * 
 * For the remaining list of initiators Host is not part of any e.masks
 * Group Initiators by Compute
 * For each Compute resource
 * GenerateExportMaskWorkFlow GenerateZoneNew
 * 
 * Export Group remove Initiators
 * *******************************
 * 
 * Find the right Export Mask,
 * If the # initiators in Export Mask is 0 after removing,
 * then deleteExportMask
 * Else
 * RemoveInitiatorsfromExportmask
 * 
 * 
 */
public class XtremIOMaskingOrchestrator extends AbstractBasicMaskingOrchestrator {
    private static final Logger log = LoggerFactory
            .getLogger(XtremIOMaskingOrchestrator.class);

    private static final AtomicReference<BlockStorageDevice> XTREMIO_BLOCK_DEVICE = new AtomicReference<BlockStorageDevice>();
    public static final String XTREMIO_STORAGE_DEVICE = "xtremioStorageDevice";

    @Override
    public BlockStorageDevice getDevice() {
        BlockStorageDevice device = XTREMIO_BLOCK_DEVICE.get();
        synchronized (XTREMIO_BLOCK_DEVICE) {
            if (device == null) {
                device = (BlockStorageDevice) ControllerServiceImpl
                        .getBean(XTREMIO_STORAGE_DEVICE);
                XTREMIO_BLOCK_DEVICE.compareAndSet(null, device);
            }
        }
        return device;
    }

    @Override
    public boolean determineExportGroupCreateSteps(Workflow workflow, String zoningStep,
            BlockStorageDevice device, StorageSystem storage, ExportGroup exportGroup,
            List<URI> initiatorURIs, Map<URI, Integer> volumeMap, boolean zoningStepNeeded, String token)
            throws Exception {

        /**
         * TODO
         * Right now, we decided not to share export masks across Export Groups.
         * But this rule is breaking an existing export Test case.
         * 1. If export mask is shared across export groups ,deleting an export mask means identifying the
         * right set of initiators and volumes to be removed from both the export Groups.
         */
        Map<String, URI> portNameToInitiatorURI = new HashMap<String, URI>();
        List<URI> hostURIs = new ArrayList<URI>();
        List<String> portNames = new ArrayList<String>();

        processInitiators(exportGroup, initiatorURIs, portNames, portNameToInitiatorURI,
                hostURIs);

        queryHostInitiatorsAndAddToList(portNames, portNameToInitiatorURI, initiatorURIs,
                hostURIs);

        // CTRL-13080 fix
        refreshExportMask(storage, device, null);
        // Export Mask cannot be grouped to an individual construct in XtremIO.
        // Group of LunMaps contribute to a Export Mask, hence there is really no need to determine
        // create mask steps based on existing Masking information on Array.
        // Always create a new export mask with the given initiators, irrespective of whether this
        // initiator is already part of existing export masks in export group..
        createNewExportMaskWorkflowForInitiators(initiatorURIs, exportGroup, workflow,
                volumeMap, storage, token, zoningStep);
        return true;

    }

    @Override
    public void exportGroupAddVolumes(URI storageURI, URI exportGroupURI,
            Map<URI, Integer> volumeMap, String token) throws Exception {
        ExportTaskCompleter taskCompleter = null;
        try {
            log.info(String.format(
                    "exportAddVolume start - Array: %s ExportGroup: %s Volume: %s",
                    storageURI.toString(), exportGroupURI.toString(), Joiner.on(',')
                            .join(volumeMap.entrySet())));

            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class,
                    exportGroupURI);
            StorageSystem storage = _dbClient
                    .queryObject(StorageSystem.class, storageURI);
            taskCompleter = new ExportOrchestrationTask(exportGroupURI, token);

            List<ExportMask> exportMasks = ExportMaskUtils.getExportMasks(_dbClient,
                    exportGroup, storageURI);
            // CTRL-13080 fix - Mask really not needed, this method has to get called on every export operation once.
            refreshExportMask(storage, getDevice(), null);
            if (exportMasks != null && !exportMasks.isEmpty()) {
                // Set up workflow steps.
                Workflow workflow = _workflowService.getNewWorkflow(
                        MaskingWorkflowEntryPoints.getInstance(),
                        "exportGroupAddVolumes - Added volumes to existing mask", true,
                        token);
                // For each export mask in export group, invoke add Volumes if export Mask belongs to the same storage Array
                List<ExportMask> masks = new ArrayList<ExportMask>();
                for (ExportMask exportMask : exportMasks) {
                    if (exportMask.getStorageDevice().equals(storageURI)) {
                        log.info("export_volume_add: adding volume to an existing export");
                        exportMask.addVolumes(volumeMap);
                        _dbClient.persistObject(exportMask);
                        masks.add(exportMask);
                    }
                }
                List<URI> volumeURIs = new ArrayList<URI>();
                volumeURIs.addAll(volumeMap.keySet());
                String zoningStep = generateZoningAddVolumesWorkflow(workflow,
                        null, exportGroup, masks, volumeURIs);
                for (ExportMask mask : masks) {
                    generateExportMaskAddVolumesWorkflow(workflow, zoningStep,
                            storage, exportGroup, mask, volumeMap);
                }

                String successMessage = String.format(
                        "Volumes successfully added to export on StorageArray %s",
                        storage.getLabel());
                workflow.executePlan(taskCompleter, successMessage);
            } else {
                if (exportGroup.getInitiators() != null
                        && !exportGroup.getInitiators().isEmpty()) {
                    log.info("export_volume_add: adding volume, creating a new export");

                    List<URI> initiatorURIs = new ArrayList<URI>();
                    for (String initiatorId : exportGroup.getInitiators()) {
                        Initiator initiator = _dbClient.queryObject(Initiator.class,
                                URI.create(initiatorId));
                        initiatorURIs.add(initiator.getId());
                    }

                    // Group Initiators by compute and invoke create Mask
                    Workflow workflow = _workflowService.getNewWorkflow(
                            MaskingWorkflowEntryPoints.getInstance(),
                            "exportGroupAddVolumes - Create a new mask", true, token);
                    List<URI> exportMasksToZoneCreate = new ArrayList<URI>();
                    Map<URI, Integer> volumesToZoneCreate = new HashMap<URI, Integer>();
                    Map<String, List<URI>> computeResourceToInitiators = mapInitiatorsToComputeResource(
                            exportGroup, initiatorURIs);
                    for (Map.Entry<String, List<URI>> resourceEntry : computeResourceToInitiators.entrySet()) {
                        String computeKey = resourceEntry.getKey();
                        List<URI> computeInitiatorURIs = resourceEntry.getValue();
                        log.info(String.format("New export masks for %s", computeKey));
                        GenExportMaskCreateWorkflowResult result = generateExportMaskCreateWorkflow(
                                workflow, EXPORT_GROUP_ZONING_TASK, storage, exportGroup,
                                computeInitiatorURIs, volumeMap, token);
                        exportMasksToZoneCreate.add(result.getMaskURI());
                        volumesToZoneCreate.putAll(volumeMap);
                    }

                    if (!exportMasksToZoneCreate.isEmpty()) {
                        generateZoningCreateWorkflow(workflow, null, exportGroup,
                                exportMasksToZoneCreate, volumesToZoneCreate);
                    }

                    String successMessage = String.format(
                            "Initiators successfully added to export StorageArray %s",
                            storage.getLabel());
                    workflow.executePlan(taskCompleter, successMessage);
                } else {
                    log.info("export_volume_add: adding volume, no initiators yet");
                    taskCompleter.ready(_dbClient);
                }
            }

            log.info(String.format(
                    "exportAddVolume end - Array: %s ExportGroup: %s Volume: %s",
                    storageURI.toString(), exportGroupURI.toString(),
                    volumeMap.toString()));
        } catch (Exception e) {
            if (taskCompleter != null) {
                ServiceError serviceError = DeviceControllerException.errors
                        .jobFailedMsg(e.getMessage(), e);
                taskCompleter.error(_dbClient, serviceError);
            } else {
                throw DeviceControllerException.exceptions.exportGroupAddVolumesFailed(e);
            }
        }
    }

    @Override
    public void exportGroupRemoveVolumes(URI storageURI, URI exportGroupURI, List<URI> volumes,
            String token) throws Exception {
        ExportTaskCompleter taskCompleter = null;
        try {
            log.info(String.format("exportRemoveVolume start - Array: %s ExportGroup: %s "
                    + "Volume: %s", storageURI.toString(), exportGroupURI.toString(), Joiner
                    .on(',').join(volumes)));

            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
            StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageURI);
            taskCompleter = new ExportOrchestrationTask(exportGroupURI, token);

            List<ExportMask> exportMasks = ExportMaskUtils.getExportMasks(_dbClient, exportGroup,
                    storageURI);
            // CTRL-13080 fix - Mask really not needed, this method has to get called on every export operation once.
            refreshExportMask(storage, getDevice(), null);
            if (exportMasks != null && !exportMasks.isEmpty()) {
                Workflow workflow = _workflowService.getNewWorkflow(
                        MaskingWorkflowEntryPoints.getInstance(), "exportGroupRemoveVolumes", true,
                        token);
                List<ExportMask> exportMaskstoDelete = new ArrayList<ExportMask>();
                List<ExportMask> exportMaskstoRemoveVolume = new ArrayList<ExportMask>();
                String previousStep = null;
                for (ExportMask exportMask : exportMasks) {
                    if (isRemoveAllVolumes(exportMask, volumes)) {
                        exportMaskstoDelete.add(exportMask);
                    } else {
                        exportMaskstoRemoveVolume.add(exportMask);
                    }
                }
                if (!exportMaskstoRemoveVolume.isEmpty()) {
                    previousStep = generateZoningRemoveVolumesWorkflow(workflow, null,
                            exportGroup, exportMaskstoRemoveVolume, volumes);
                    for (ExportMask exportMask : exportMaskstoRemoveVolume) {
                        generateExportMaskRemoveVolumesWorkflow(workflow, previousStep, storage,
                                exportGroup, exportMask, volumes, null);
                    }
                }
                if (!exportMaskstoDelete.isEmpty()) {
                    previousStep = generateZoningDeleteWorkflow(workflow, previousStep,
                            exportGroup, exportMaskstoDelete);
                    for (ExportMask exportMask : exportMaskstoDelete) {
                        generateExportMaskDeleteWorkflow(workflow, previousStep, storage,
                                exportGroup, exportMask, null);
                    }
                }
                // volumes
                generateExportGroupRemoveVolumesCleanup(workflow, EXPORT_GROUP_MASKING_TASK, storage,
                        exportGroup, volumes);

                String successMessage = String.format(
                        "Volumes successfully unexported from StorageArray %s", storage.getLabel());
                workflow.executePlan(taskCompleter, successMessage);
            } else {
                log.info("export_volume_remove: no export (initiator should be empty)");
                exportGroup.removeVolumes(volumes);
                _dbClient.persistObject(exportGroup);
                taskCompleter.ready(_dbClient);
            }

            log.info(String.format("exportRemoveVolume end - Array: %s ExportGroup: %s "
                    + "Volume: %s", storageURI.toString(), exportGroupURI.toString(), Joiner
                    .on(',').join(volumes)));
        } catch (Exception e) {
            if (taskCompleter != null) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailedMsg(
                        e.getMessage(), e);
                taskCompleter.error(_dbClient, serviceError);
            } else {
                throw DeviceControllerException.exceptions.exportRemoveVolumes(e);
            }
        }
    }

    @Override
    public void exportGroupAddInitiators(URI storageURI, URI exportGroupURI,
            List<URI> initiatorURIs, String token) throws Exception {
        TaskCompleter taskCompleter = null;
        try {
            log.info(String.format("exportAddInitiator start - Array: %s ExportGroup: "
                    + "%s Initiator: %s", storageURI.toString(),
                    exportGroupURI.toString(), Joiner.on(',').join(initiatorURIs)));
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class,
                    exportGroupURI);
            StorageSystem storage = _dbClient
                    .queryObject(StorageSystem.class, storageURI);

            List<ExportMask> exportMasks = ExportMaskUtils.getExportMasks(_dbClient,
                    exportGroup, storageURI);

            Map<String, List<URI>> computeResourceToInitiators = mapInitiatorsToComputeResource(
                    exportGroup, initiatorURIs);
            // CTRL-13080 fix - Mask really not needed, this method has to get called on every export operation once.
            refreshExportMask(storage, getDevice(), null);
            log.info("initiators  : {}", Joiner.on(",").join(computeResourceToInitiators.entrySet()));

            taskCompleter = new ExportOrchestrationTask(exportGroupURI, token);

            Map<URI, Integer> volumes = selectExportMaskVolumes(exportGroup, storageURI);
            log.info("Volumes  : {}", Joiner.on(",").join(volumes.keySet()));
            if (exportMasks != null && !exportMasks.isEmpty()) {
                // find the export mask which has the same Host name as the initiator
                // Add the initiator to that export mask
                // Set up workflow steps.
                log.info("Creating AddInitiators workFlow");
                Workflow workflow = _workflowService.getNewWorkflow(
                        MaskingWorkflowEntryPoints.getInstance(),
                        "exportGroupAddInitiators", true, token);

                // irrespective of cluster name, host will be always present
                Map<String, URI> hostToEMaskGroup = ExportMaskUtils
                        .mapHostToExportMask(_dbClient, exportGroup,
                                storage.getId());
                log.info("InitiatorsToHost  : {}", Joiner.on(",").join(hostToEMaskGroup.entrySet()));
                // if export masks are found for the Host, then add initiators to the export mask
                Map<URI, List<URI>> masksToInitiators = new HashMap<URI, List<URI>>();
                String addIniStep = null;
                for (String computeKey : computeResourceToInitiators.keySet()) {
                    URI exportMaskUri = hostToEMaskGroup.get(computeKey);
                    if (null != exportMaskUri) {
                        log.info("Processing export mask {}", exportMaskUri);
                        ExportMask exportMask = _dbClient.queryObject(ExportMask.class,
                                exportMaskUri);
                        if (exportMask.getStorageDevice().equals(storageURI)) {
                            log.info("Processing export mask {} with expected storage {}", exportMaskUri, storageURI);
                            // AddInitiatorWorkFlow
                            masksToInitiators.put(exportMaskUri,
                                    computeResourceToInitiators.get(computeKey));
                            // all masks will be always created by system = true, hence port allocation will happen
                            addIniStep = generateExportMaskAddInitiatorsWorkflow(workflow, null,
                                    storage, exportGroup, exportMask, initiatorURIs, null,
                                    token);
                            computeResourceToInitiators.remove(computeKey);
                        }
                        if (!masksToInitiators.isEmpty()) {
                            generateZoningAddInitiatorsWorkflow(
                                    workflow, addIniStep, exportGroup, masksToInitiators);
                        }
                    }
                }

                log.info("Left out initiators  : {}", Joiner.on(",").join(computeResourceToInitiators.entrySet()));
                // left out initiator's Host which doesn't have any export mask.
                Map<URI, Map<URI, Integer>> zoneNewMasksToVolumeMap = new HashMap<URI, Map<URI, Integer>>();
                if (!computeResourceToInitiators.isEmpty()) {
                    for (Map.Entry<String, List<URI>> resourceEntry : computeResourceToInitiators
                            .entrySet()) {
                        String computeKey = resourceEntry.getKey();
                        List<URI> computeInitiatorURIs = resourceEntry.getValue();
                        log.info(String.format("New export masks for %s", computeKey));
                        GenExportMaskCreateWorkflowResult result = generateExportMaskCreateWorkflow(
                                workflow, EXPORT_GROUP_ZONING_TASK, storage, exportGroup,
                                computeInitiatorURIs, volumes, token);
                        zoneNewMasksToVolumeMap.put(result.getMaskURI(), volumes);

                    }

                    if (!zoneNewMasksToVolumeMap.isEmpty()) {
                        List<URI> exportMaskList = new ArrayList<URI>();
                        exportMaskList.addAll(zoneNewMasksToVolumeMap.keySet());
                        Map<URI, Integer> overallVolumeMap = new HashMap<URI, Integer>();
                        for (Map<URI, Integer> oneVolumeMap : zoneNewMasksToVolumeMap
                                .values()) {
                            overallVolumeMap.putAll(oneVolumeMap);
                        }
                        generateZoningCreateWorkflow(workflow, null, exportGroup,
                                exportMaskList, overallVolumeMap);
                    }
                }

                String successMessage = String.format(
                        "Initiators successfully added to export StorageArray %s",
                        storage.getLabel());
                workflow.executePlan(taskCompleter, successMessage);
            } else {
                log.info("export_initiator_add: first initiator, creating a new export");

                // No existing export masks available inexport Group
                Workflow workflow = _workflowService.getNewWorkflow(
                        MaskingWorkflowEntryPoints.getInstance(), "exportGroupCreate",
                        true, token);

                List<URI> exportMasksToZoneCreate = new ArrayList<URI>();
                Map<URI, Integer> volumesToZoneCreate = new HashMap<URI, Integer>();

                for (Map.Entry<String, List<URI>> resourceEntry : computeResourceToInitiators
                        .entrySet()) {
                    String computeKey = resourceEntry.getKey();
                    List<URI> computeInitiatorURIs = resourceEntry.getValue();
                    log.info(String.format("New export masks for %s", computeKey));
                    GenExportMaskCreateWorkflowResult result = generateExportMaskCreateWorkflow(
                            workflow, EXPORT_GROUP_ZONING_TASK, storage, exportGroup,
                            computeInitiatorURIs, volumes, token);
                    exportMasksToZoneCreate.add(result.getMaskURI());
                    volumesToZoneCreate.putAll(volumes);
                }

                if (!exportMasksToZoneCreate.isEmpty()) {
                    generateZoningCreateWorkflow(workflow, null, exportGroup,
                            exportMasksToZoneCreate, volumesToZoneCreate);
                }

                String successMessage = String.format(
                        "Initiators successfully added to export StorageArray %s",
                        storage.getLabel());
                workflow.executePlan(taskCompleter, successMessage);
            }

            log.info(String.format("exportAddInitiator end - Array: %s ExportGroup: %s "
                    + "Initiator: %s", storageURI.toString(), exportGroupURI.toString(),
                    Joiner.on(',').join(initiatorURIs)));
        } catch (Exception e) {
            log.info("Error", e);
            if (taskCompleter != null) {
                ServiceError serviceError = DeviceControllerException.errors
                        .jobFailedMsg(e.getMessage(), e);
                taskCompleter.error(_dbClient, serviceError);
            } else {
                throw DeviceControllerException.exceptions
                        .exportGroupAddInitiatorsFailed(e);
            }
        }
    }

    @Override
    public void exportGroupRemoveInitiators(URI storageURI,
            URI exportGroupURI,
            List<URI> initiatorURIs,
            String token) throws Exception {
        ExportTaskCompleter taskCompleter = null;
        try {

            log.info(String.format("exportRemoveInitiator start - Array: %s " +
                    "ExportGroup: %s Initiator: %s",
                    storageURI.toString(), exportGroupURI.toString(),
                    Joiner.on(',').join(initiatorURIs)));
            taskCompleter = new ExportOrchestrationTask(exportGroupURI, token);

            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class,
                    exportGroupURI);
            StorageSystem storage = _dbClient.queryObject(StorageSystem.class,
                    storageURI);
            List<ExportMask> exportMasks = ExportMaskUtils.getExportMasks(_dbClient,
                    exportGroup, storageURI);
            Map<String, List<URI>> computeResourceToInitiators = mapInitiatorsToComputeResource(
                    exportGroup, initiatorURIs);

            // CTRL-13080 fix - Mask really not needed, this method has to get called on every export operation once.
            refreshExportMask(storage, getDevice(), null);

            log.info("Host to initiators  : {}", Joiner.on(",").join(computeResourceToInitiators.entrySet()));

            if (exportMasks != null && !exportMasks.isEmpty()) {
                // find the export mask which has the same Host name as the initiator
                // Add the initiator to that export mask
                // Set up workflow steps.
                Workflow workflow = _workflowService.getNewWorkflow(
                        MaskingWorkflowEntryPoints.getInstance(),
                        "exportGroupAddInitiators", true, token);

                // irrespective of cluster name, host will be always present
                Map<String, URI> hostToEMaskGroup = ExportMaskUtils
                        .mapHostToExportMask(_dbClient, exportGroup,
                                storage.getId());
                log.info("Host to ExportMask  : {}", Joiner.on(",").join(hostToEMaskGroup.entrySet()));
                // if export masks are found for the Host, then remove initiators from the export mask
                // Export Masks are not shared between export Groups

                // list of export masks from which initiators need to be removed
                List<ExportMask> exportMaskRemoveInitiator = new ArrayList<ExportMask>();

                // list of export masks to delete as all initiators are removed
                List<ExportMask> exportMaskDelete = new ArrayList<ExportMask>();

                // map of masks to initiators being removed needed to remove zones
                Map<URI, List<URI>> maskToInitiatorsMap = new HashMap<URI, List<URI>>();
                String zoningStep = null;

                for (String computeKey : computeResourceToInitiators.keySet()) {
                    URI exportMaskUri = hostToEMaskGroup.get(computeKey);
                    if (null != exportMaskUri) {
                        ExportMask exportMask = _dbClient.queryObject(ExportMask.class,
                                exportMaskUri);
                        if (exportMask.getStorageDevice().equals(storageURI)) {
                            List<Initiator> initiators = _dbClient.queryObject(Initiator.class,
                                    computeResourceToInitiators.get(computeKey));
                            log.info("Processing export mask  {} with initiators {}", storageURI, Joiner.on(",").join(initiators));
                            maskToInitiatorsMap.put(exportMask.getId(), computeResourceToInitiators.get(computeKey));
                            if (isRemoveAllInitiators(exportMask, initiators)) {
                                exportMaskDelete.add(exportMask);
                            } else {
                                exportMaskRemoveInitiator.add(exportMask);
                            }
                        }
                    }
                }
                if (!maskToInitiatorsMap.isEmpty()) {
                    zoningStep = generateZoningRemoveInitiatorsWorkflow(workflow, null,
                            exportGroup, maskToInitiatorsMap);
                }
                if (!exportMaskRemoveInitiator.isEmpty()) {
                    for (ExportMask exportMask : exportMaskRemoveInitiator) {
                        generateExportMaskRemoveInitiatorsWorkflow(workflow, zoningStep,
                                storage, exportGroup, exportMask, initiatorURIs, true);
                    }
                }
                if (!exportMaskDelete.isEmpty()) {
                    for (ExportMask exportMask : exportMaskDelete) {
                        generateExportMaskDeleteWorkflow(workflow, zoningStep, storage,
                                exportGroup, exportMask, null);
                    }
                }
                String successMessage = String.format(
                        "Initiators successfully removed from export StorageArray %s",
                        storage.getLabel());
                workflow.executePlan(taskCompleter, successMessage);

                log.info(String.format("exportRemoveInitiator end - Array: %s ExportGroup: " +
                        "%s Initiator: %s",
                        storageURI.toString(), exportGroupURI.toString(),
                        Joiner.on(',').join(initiatorURIs)));
            } else {
                taskCompleter.ready(_dbClient);
            }

        } catch (Exception e) {
            if (taskCompleter != null) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailedMsg(e.getMessage(), e);
                taskCompleter.error(_dbClient, serviceError);
            } else {
                throw DeviceControllerException.exceptions.exportGroupRemoveInitiatorsFailed(e);
            }
        }
    }

    /**
     * Returns true if the request is to remove all the initiators in the ExportMask.
     */
    private boolean isRemoveAllInitiators(ExportMask exportMask, List<Initiator> initiators) {
        StringSet initiatorsInMask = exportMask.getInitiators();
        StringSet initiatorsToRemove = StringSetUtil.objCollectionToStringSet(initiators);
        return initiatorsInMask == null ||
                (initiatorsInMask.containsAll(initiatorsToRemove) && (initiatorsInMask.size() == initiatorsToRemove.size()));
    }

    /**
     * Returns true if the request is to remove all the volumes in the ExportMask.
     */
    private boolean isRemoveAllVolumes(ExportMask exportMask, List<URI> volumesToRemove) {
        List<URI> volumesInMask = ExportMaskUtils.getVolumeURIs(exportMask);
        return volumesInMask.isEmpty() ||
                (volumesInMask.containsAll(volumesToRemove) && (volumesInMask.size() == volumesToRemove.size()));
    }

    @Override
    public void exportGroupDelete(URI storageURI, URI exportGroupURI, String token)
            throws Exception {
        try {
            log.info(String.format("exportGroupDelete start - Array: %s ExportGroup: %s",
                    storageURI.toString(), exportGroupURI.toString()));

            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class,
                    exportGroupURI);
            StorageSystem storage = _dbClient
                    .queryObject(StorageSystem.class, storageURI);
            TaskCompleter taskCompleter = new ExportOrchestrationTask(exportGroupURI,
                    token);
            // CTRL-13080 fix - Mask really not needed, this method has to get called on every export operation once.
            refreshExportMask(storage, getDevice(), null);
            if (exportGroup == null || exportGroup.getInactive()) {
                exportGroup.getVolumes().clear();
                taskCompleter.ready(_dbClient);
                return;
            }

            List<ExportMask> exportMasks = ExportMaskUtils.getExportMasks(_dbClient,
                    exportGroup, storageURI);

            // Set up workflow steps.
            Workflow workflow = _workflowService.getNewWorkflow(
                    MaskingWorkflowEntryPoints.getInstance(), "exportGroupDelete", true,
                    token);

            String previousStep = generateZoningDeleteWorkflow(workflow, null, exportGroup, exportMasks);

            if (null == exportMasks || exportMasks.isEmpty()) {
                exportGroup.getVolumes().clear();
                taskCompleter.ready(_dbClient);
                return;
            }

            /**
             * TODO
             * Right now,to make orchestration simple , we decided not to share export masks across Export Groups.
             * But this rule is breaking an existing export Test case.
             * 1. If export mask is shared across export groups ,deleting an export mask means identifying the
             * right set of initiators and volumes to be removed from both the export Groups.
             */
            for (ExportMask exportMask : exportMasks) {
                previousStep = generateExportMaskDeleteWorkflow(workflow, previousStep, storage, exportGroup,
                        exportMask, null);
            }

            String successMessage = String.format(
                    "Export was successfully removed from StorageArray %s",
                    storage.getLabel());
            workflow.executePlan(taskCompleter, successMessage);

            log.info(String.format("exportGroupDelete end - Array: %s ExportGroup: %s",
                    storageURI.toString(), exportGroupURI.toString()));
        } catch (Exception e) {
            throw DeviceControllerException.exceptions.exportGroupDeleteFailed(e);
        }
    }

    @Override
    public String checkForSnapshotsToCopyToTarget(Workflow workflow,
            StorageSystem storage, String previousStep, Map<URI, Integer> volumeMap,
            Collection<Map<URI, Integer>> values) {
        throw new DeviceControllerException(
                "Coding error. This code path is not supported for XtremIO.");

    }

}
