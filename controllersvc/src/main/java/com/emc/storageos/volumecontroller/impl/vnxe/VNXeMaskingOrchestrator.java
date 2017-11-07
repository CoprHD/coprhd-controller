/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.vnxe;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.BlockStorageDevice;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.block.AbstractBasicMaskingOrchestrator;
import com.emc.storageos.volumecontroller.impl.block.MaskingWorkflowEntryPoints;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportOrchestrationTask;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportTaskCompleter;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.workflow.Workflow;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;

public class VNXeMaskingOrchestrator extends AbstractBasicMaskingOrchestrator {
    private static final Logger _log = LoggerFactory.getLogger(VNXeMaskingOrchestrator.class);

    private static final AtomicReference<BlockStorageDevice> VNXE_BLOCK_DEVICE = new AtomicReference<BlockStorageDevice>();
    public static final String VNXE_DEVICE = "vnxeDevice";
    public static final String DEFAULT_LABEL = "Default";

    @Override
    public BlockStorageDevice getDevice() {
        BlockStorageDevice device = VNXE_BLOCK_DEVICE.get();
        synchronized (VNXE_BLOCK_DEVICE) {
            if (device == null) {
                device = (BlockStorageDevice) ControllerServiceImpl.getBean(VNXE_DEVICE);
                VNXE_BLOCK_DEVICE.compareAndSet(null, device);
            }
        }
        return device;
    }

    /**
     * Create storage level masking components to support the requested
     * ExportGroup object. ExportMask will be created for each host.
     * 
     * 
     * @param storageURI
     *            - URI referencing underlying storage array
     * @param exportGroupURI
     *            - URI referencing Bourne-level masking, ExportGroup
     * @param initiatorURIs
     *            - List of Initiator URIs
     * @param volumeMap
     *            - Map of Volume URIs to requested Integer URI
     * @param token
     *            - Identifier for operation
     * @throws Exception
     */
    @Override
    public void exportGroupCreate(URI storageURI, URI exportGroupURI, List<URI> initiatorURIs,
            Map<URI, Integer> volumeMap, String token) throws Exception {
        ExportOrchestrationTask taskCompleter = null;
        try {
            BlockStorageDevice device = getDevice();
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
            StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageURI);
            taskCompleter = new ExportOrchestrationTask(exportGroupURI, token);

            if (initiatorURIs != null && !initiatorURIs.isEmpty()) {
                _log.info("export_create: initiator list non-empty");

                // Set up workflow steps.
                Workflow workflow = _workflowService.getNewWorkflow(
                        MaskingWorkflowEntryPoints.getInstance(), "exportGroupCreate", true, token);

                // Create two steps, one for Zoning, one for the ExportGroup actions.
                // This step is for zoning. It is not specific to a single
                // NetworkSystem, as it will look at all the initiators and targets and compute
                // the zones required (which might be on multiple NetworkSystems.)
                
                boolean createdSteps = determineExportGroupCreateSteps(workflow, null, device, storage, exportGroup,
                        initiatorURIs, volumeMap, token);
                
                String zoningStep = generateZoningCreateWorkflow(workflow, EXPORT_GROUP_MASKING_TASK, exportGroup,
                        null, volumeMap);                

                if (createdSteps) {
                    // Execute the plan and allow the WorkflowExecutor to fire the
                    // taskCompleter.
                    String successMessage = String.format(
                            "ExportGroup successfully applied for StorageArray %s", storage.getLabel());
                    workflow.executePlan(taskCompleter, successMessage);
                }
            } else {
                _log.info("export_create: initiator list");
                taskCompleter.ready(_dbClient);
            }
        } catch (Exception ex) {
            _log.error("ExportGroup Orchestration failed.", ex);
            // TODO add service code here
            if (taskCompleter != null) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailedMsg(ex.getMessage(), ex);
                taskCompleter.error(_dbClient, serviceError);
            }
        }
    }

    @Override
    public void exportGroupDelete(URI storageURI,
            URI exportGroupURI,
            String token) throws Exception {
        try {
            _log.info(String.format("exportGroupDelete start - Array: %s ExportMask: %s",
                    storageURI.toString(), exportGroupURI.toString()));

            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class,
                    exportGroupURI);
            StorageSystem storage = _dbClient.queryObject(StorageSystem.class,
                    storageURI);
            TaskCompleter taskCompleter = new ExportOrchestrationTask(exportGroupURI,
                    token);

            if (exportGroup == null || exportGroup.getInactive() || ExportMaskUtils.getExportMasks(_dbClient, exportGroup, storageURI).isEmpty()) {
                taskCompleter.ready(_dbClient);
                return;
            }

            /**
             * If no export mask is found, nothing to be done. Task will be marked
             * complete by the last real export mask delete completion.
             */
            List<ExportMask> exportMasks = ExportMaskUtils.getExportMasks(_dbClient,
                    exportGroup, storageURI);

            Workflow workflow = _workflowService.getNewWorkflow(
                    MaskingWorkflowEntryPoints.getInstance(),
                    "exportGroupDelete", true, token);
            String deleteStep = null;
            for (ExportMask exportMask : exportMasks) {
                refreshExportMask(storage, getDevice(), exportMask);
                deleteStep = generateExportMaskDeleteWorkflow(workflow, deleteStep,
                        storage, exportGroup, exportMask, null, null, null);
            }
            generateZoningDeleteWorkflow(workflow, deleteStep,
                    exportGroup, exportMasks);

            String successMessage = String.format(
                    "Export was successfully removed from StorageArray %s",
                    storage.getLabel());
            workflow.executePlan(taskCompleter, successMessage);

            _log.info(String.format("exportGroupDelete end - Array: %s ExportMask: %s",
                    storageURI.toString(), exportGroupURI.toString()));
        } catch (Exception e) {
            throw DeviceControllerException.exceptions.exportGroupDeleteFailed(e);
        }
    }

    @Override
    public void exportGroupAddInitiators(URI storageURI, URI exportGroupURI,
            List<URI> initiatorURIs,
            String token) throws Exception {
        TaskCompleter taskCompleter = null;
        try {
            _log.info(String.format("exportAddInitiator start - Array: %s ExportMask: " +
                    "%s Initiator: %s",
                    storageURI.toString(), exportGroupURI.toString(),
                    Joiner.on(',').join(initiatorURIs)));

            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class,
                    exportGroupURI);
            StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageURI);

            List<ExportMask> exportMasks = ExportMaskUtils.getExportMasks(_dbClient,
                    exportGroup, storageURI);

            Map<String, List<URI>> computeResourceToInitiators = mapInitiatorsToComputeResource(
                    exportGroup, initiatorURIs);

            _log.info("initiators  : {}", Joiner.on(",").join(computeResourceToInitiators.entrySet()));

            taskCompleter = new ExportOrchestrationTask(exportGroupURI, token);


            Map<URI, Integer> volumes = selectExportMaskVolumes(exportGroup, storageURI);
            _log.info("Volumes  : {}", Joiner.on(",").join(volumes.keySet()));
            if (!CollectionUtils.isEmpty(exportMasks)) {

                // Refresh all export masks
                for (ExportMask exportMask : exportMasks) {
                    refreshExportMask(storage, getDevice(), exportMask);
                }

                // find the export mask which has the same Host name as the initiator
                // Add the initiator to that export mask
                // Set up workflow steps.
                _log.info("Creating AddInitiators workFlow");
                Workflow workflow = _workflowService.getNewWorkflow(
                        MaskingWorkflowEntryPoints.getInstance(),
                        "exportGroupAddInitiators", true, token, taskCompleter);

                // irrespective of cluster name, host will be always present
                Map<String, URI> hostToEMaskGroup = ExportMaskUtils.mapHostToExportMask(
                        _dbClient, exportGroup, storage.getId());
                _log.info("hostsToExportMask  : {}", Joiner.on(",").join(hostToEMaskGroup.entrySet()));
                // if export masks are found for the Host, then add initiators to the export mask
                Map<URI, List<URI>> masksToInitiators = new HashMap<URI, List<URI>>();
                String addIniStep = null;
                for (String computeKey : computeResourceToInitiators.keySet()) {
                    URI exportMaskUri = hostToEMaskGroup.get(computeKey);
                    if (null != exportMaskUri) {
                        _log.info("Processing export mask {}", exportMaskUri);
                        ExportMask exportMask = _dbClient.queryObject(ExportMask.class,
                                exportMaskUri);
                        if (exportMask.getStorageDevice().equals(storageURI)) {
                            _log.info("Processing export mask {} with expected storage {}", exportMaskUri, storageURI);
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

                _log.info("Left out initiators  : {}", Joiner.on(",").join(computeResourceToInitiators.entrySet()));
                // left out initiator's Host which doesn't have any export mask.
                Map<URI, Map<URI, Integer>> zoneNewMasksToVolumeMap = new HashMap<URI, Map<URI, Integer>>();
                if (!computeResourceToInitiators.isEmpty()) {
                    for (Map.Entry<String, List<URI>> resourceEntry : computeResourceToInitiators
                            .entrySet()) {
                        String computeKey = resourceEntry.getKey();
                        List<URI> computeInitiatorURIs = resourceEntry.getValue();
                        _log.info(String.format("New export masks for %s", computeKey));
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
                _log.info("export_initiator_add: first initiator, creating a new export");
                // No existing export masks available inexport Group
                Workflow workflow = _workflowService.getNewWorkflow(
                        MaskingWorkflowEntryPoints.getInstance(), "exportGroupCreate",
                        true, token, taskCompleter);

                List<URI> exportMasksToZoneCreate = new ArrayList<URI>();
                Map<URI, Integer> volumesToZoneCreate = new HashMap<URI, Integer>();

                for (Map.Entry<String, List<URI>> resourceEntry : computeResourceToInitiators
                        .entrySet()) {
                    String computeKey = resourceEntry.getKey();
                    List<URI> computeInitiatorURIs = resourceEntry.getValue();
                    _log.info(String.format("New export masks for %s", computeKey));
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

            _log.info(String.format("exportAddInitiator end - Array: %s ExportMask: %s " +
                    "Initiator: %s",
                    storageURI.toString(), exportGroupURI.toString(),
                    Joiner.on(',').join(initiatorURIs)));
        } catch (Exception e) {
            if (taskCompleter != null) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailedMsg(e.getMessage(), e);
                taskCompleter.error(_dbClient, serviceError);
            } else {
                throw DeviceControllerException.exceptions.exportGroupAddInitiatorsFailed(e);
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
            List<Initiator> initiators = _dbClient.queryObject(Initiator.class,
                    initiatorURIs);
            _log.info(String.format("exportRemoveInitiator start - Array: %s " +
                    "ExportMask: %s Initiator: %s",
                    storageURI.toString(), exportGroupURI.toString(),
                    Joiner.on(',').join(initiatorURIs)));

            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class,
                    exportGroupURI);
            StorageSystem storage = _dbClient.queryObject(StorageSystem.class,
                    storageURI);
            taskCompleter = new ExportOrchestrationTask(exportGroupURI, token);
            // Set up workflow steps.
            Workflow workflow = _workflowService.getNewWorkflow(
                    MaskingWorkflowEntryPoints.getInstance(),
                    "exportGroupRemoveInitiators", true, token);
            /**
             * export mask must exist since both volume & initiator exist
             */
            Map<ExportMask, List<Initiator>> exportMasksMap = getInitiatorExportMasks(initiators, _dbClient, exportGroup, storageURI);
            Map<URI, List<URI>> maskToInitiatorsMap = new HashMap<URI, List<URI>>();
            for (Entry<ExportMask, List<Initiator>> entry : exportMasksMap.entrySet()) {
                ExportMask mask = entry.getKey();
                List<Initiator> inits = entry.getValue();
                List<URI> initURIList = new ArrayList<URI>();
                for (Initiator init : inits) {
                    initURIList.add(init.getId());
                }
                maskToInitiatorsMap.put(mask.getId(), initURIList);
            }
            String deleteStep = null;
            for (ExportMask exportMask : exportMasksMap.keySet()) {
                refreshExportMask(storage, getDevice(), exportMask);
                List<Initiator> inits = exportMasksMap.get(exportMask);

                if (exportMask.getInitiators().size() == inits.size() &&
                        exportMask.getVolumes() != null) {
                    _log.info(String.format("deleting the exportMask: %s",
                            exportMask.getId().toString()));
                    // Initiator list (initiatorURIs) need to be provided when deleting export mask as a result of removing last initiators
                    deleteStep = generateExportMaskDeleteWorkflow(workflow, deleteStep, storage,
                            exportGroup, exportMask, null, initiatorURIs, null);

                } else {
                    Collection<URI> volumeURIs = (Collections2.transform(exportMask.getVolumes().keySet(),
                            CommonTransformerFunctions.FCTN_STRING_TO_URI));
                    generateExportMaskRemoveInitiatorsWorkflow(workflow, deleteStep,
                                storage, exportGroup, exportMask, new ArrayList<URI>(volumeURIs), initiatorURIs, true);
                    
                }
                _log.info(String.format("exportRemoveInitiator end - Array: %s ExportMask: %s",
                        storageURI.toString(), exportGroupURI.toString()));
            }
            generateZoningRemoveInitiatorsWorkflow(workflow, deleteStep,
                    exportGroup, maskToInitiatorsMap);

            String successMessage = String.format(
                    "Initiators successfully removed from export StorageArray %s",
                    storage.getLabel());
            workflow.executePlan(taskCompleter, successMessage);

        } catch (Exception e) {
            if (taskCompleter != null) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailedMsg(e.getMessage(), e);
                taskCompleter.error(_dbClient, serviceError);
            } else {
                throw DeviceControllerException.exceptions.exportGroupRemoveInitiatorsFailed(e);
            }
        }
    }

    @Override
    public void exportGroupAddVolumes(URI storageURI, URI exportGroupURI,
            Map<URI, Integer> volumeMap,
            String token) throws Exception {
        ExportTaskCompleter taskCompleter = null;
        try {
            _log.info(
                    String.format("exportAddVolume start - Array: %s ExportMask: %s Volume: %s",
                            storageURI.toString(), exportGroupURI.toString(),
                            Joiner.on(',').join(volumeMap.entrySet())));

            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
            StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageURI);
            taskCompleter = new ExportOrchestrationTask(exportGroupURI, token);

            List<ExportMask> exportMasks = ExportMaskUtils.getExportMasks(_dbClient, exportGroup, storageURI);
            if (!CollectionUtils.isEmpty(exportMasks)) {
                // refresh all export masks
                for (ExportMask exportMask : exportMasks) {
                    refreshExportMask(storage, getDevice(), exportMask);
                }
                // Set up workflow steps.
                Workflow workflow = _workflowService.getNewWorkflow(
                        MaskingWorkflowEntryPoints.getInstance(),
                        "exportGroupAddVolumes - Added volumes to existing mask", true,
                        token);

                List<URI> volumeURIs = new ArrayList<URI>();
                volumeURIs.addAll(volumeMap.keySet());

                Collection<URI> initiatorURIs = Collections2.transform(exportGroup.getInitiators(),
                        CommonTransformerFunctions.FCTN_STRING_TO_URI);
                findAndUpdateFreeHLUsForClusterExport(storage, exportGroup, new ArrayList<URI>(initiatorURIs), volumeMap);

                String zoningStep = generateZoningAddVolumesWorkflow(workflow, null,
                        exportGroup, exportMasks, volumeURIs);
                String exportStep = null;
                for (ExportMask exportMask : exportMasks) {
                    if (exportStep == null) {
                        exportStep = generateExportMaskAddVolumesWorkflow(workflow, zoningStep, storage,
                                exportGroup, exportMask, volumeMap, null);
                    } else {
                        exportStep = generateExportMaskAddVolumesWorkflow(workflow, exportStep, storage,
                                exportGroup, exportMask, volumeMap, null);
                    }
                }
                String successMessage = String.format(
                        "Volumes successfully added to export on StorageArray %s",
                        storage.getLabel());
                workflow.executePlan(taskCompleter, successMessage);
            } else {
                // This is the case when exportGroup exists, but no volume is added before.
                if (exportGroup.getInitiators() != null && !exportGroup.getInitiators().isEmpty()) {
                    _log.info("export_volume_add: adding volume, creating a new export");

                    List<URI> initiatorURIs = new ArrayList<URI>();
                    for (String initiatorId : exportGroup.getInitiators()) {
                        initiatorURIs.add(URI.create(initiatorId));
                    }

                    exportGroupCreate(storageURI, exportGroupURI, initiatorURIs, volumeMap, token);

                } else {
                    _log.info("export_volume_add: adding volume, no initiators yet");
                    taskCompleter.ready(_dbClient);
                }
            }

            _log.info(String.format("exportAddVolume end - Array: %s ExportMask: %s Volume: %s",
                    storageURI.toString(), exportGroupURI.toString(),
                    volumeMap.toString()));
        } catch (Exception e) {
            if (taskCompleter != null) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailedMsg(e.getMessage(), e);
                taskCompleter.error(_dbClient, serviceError);
            } else {
                throw DeviceControllerException.exceptions.exportGroupAddVolumesFailed(e);
            }
        }
    }

    @Override
    public void exportGroupRemoveVolumes(URI storageURI, URI exportGroupURI,
            List<URI> volumes,
            String token) throws Exception {
        ExportTaskCompleter taskCompleter = null;
        try {
            _log.info(
                    String.format("exportRemoveVolume start - Array: %s ExportMask: %s " +
                            "Volume: %s",
                            storageURI.toString(), exportGroupURI.toString(),
                            Joiner.on(',').join(volumes)));

            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class,
                    exportGroupURI);
            StorageSystem storage = _dbClient.queryObject(StorageSystem.class,
                    storageURI);
            taskCompleter = new ExportOrchestrationTask(exportGroupURI, token);

            List<ExportMask> exportMasks = ExportMaskUtils.getExportMasks(_dbClient,
                    exportGroup, storageURI);
            if (exportMasks != null && !exportMasks.isEmpty()) {
                Workflow workflow = _workflowService.getNewWorkflow(
                        MaskingWorkflowEntryPoints.getInstance(),
                        "exportGroupRemoveVolumes", true, token);

                List<ExportMask> deleteMasks = new ArrayList<ExportMask>();
                List<ExportMask> updateMasks = new ArrayList<ExportMask>();
                for (ExportMask mask : exportMasks) {
                    refreshExportMask(storage, getDevice(), mask);

                    // Determine if we're deleting the last volume.
                    Set<String> remainingVolumes = new HashSet<String>();
                    if (mask.getVolumes() != null) {
                        remainingVolumes.addAll(mask.getVolumes().keySet());
                    }
                    for (URI volume : volumes) {
                        remainingVolumes.remove(volume.toString());
                    }
                    // If so, delete the ExportMask.
                    if (remainingVolumes.isEmpty()) {
                        deleteMasks.add(mask);
                    } else {
                        updateMasks.add(mask);
                    }
                }
                if (!deleteMasks.isEmpty()) {
                    String deleteStep = null;
                    for (ExportMask exportMask : deleteMasks) {
                        deleteStep = generateExportMaskDeleteWorkflow(workflow, deleteStep, storage,
                                exportGroup, exportMask, null, null, null);
                    }
                    generateZoningDeleteWorkflow(workflow, deleteStep,
                            exportGroup, deleteMasks);
                }
                if (!updateMasks.isEmpty()) {
                    String unexportStep = null;
                    for (ExportMask exportMask : updateMasks) {
                        unexportStep = generateExportMaskRemoveVolumesWorkflow(workflow, unexportStep,
                                storage, exportGroup, exportMask, volumes, null, null);
                    }
                    generateZoningRemoveVolumesWorkflow(workflow,
                            null, exportGroup, updateMasks, volumes);
                }
                String successMessage = String.format(
                        "Volumes successfully unexported from StorageArray %s",
                        storage.getLabel());
                workflow.executePlan(taskCompleter, successMessage);
            } else {
                _log.info("export_volume_remove: no export (initiator should be empty)");
                exportGroup.removeVolumes(volumes);
                _dbClient.updateObject(exportGroup);
                taskCompleter.ready(_dbClient);
            }

            _log.info(String.format("exportRemoveVolume end - Array: %s ExportMask: %s " +
                    "Volume: %s",
                    storageURI.toString(), exportGroupURI.toString(),
                    Joiner.on(',').join(volumes)));
        } catch (Exception e) {
            if (taskCompleter != null) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailedMsg(e.getMessage(), e);
                taskCompleter.error(_dbClient, serviceError);
            } else {
                throw DeviceControllerException.exceptions.exportRemoveVolumes(e);
            }
        }
    }

    @Override
    public void findAndUpdateFreeHLUsForClusterExport(StorageSystem storage, ExportGroup exportGroup, List<URI> initiatorURIs,
            Map<URI, Integer> volumeMap) {
        findUpdateFreeHLUsForClusterExport(storage, exportGroup, initiatorURIs, volumeMap);
    }

    /**
     * Routine contains logic to create an export mask on the array
     * 
     * @param workflow
     *            - Workflow object to create steps against
     * @param previousStep
     *            - [optional] Identifier of workflow step to wait for
     * @param device
     *            - BlockStorageDevice implementation
     * @param storage
     *            - StorageSystem object representing the underlying array
     * @param exportGroup
     *            - ExportGroup object representing Bourne-level masking
     * @param initiatorURIs
     *            - List of Initiator URIs
     * @param volumeMap
     *            - Map of Volume URIs to requested Integer HLUs
     * @param token
     *            - Identifier for the operation
     * @throws Exception
     */
    private boolean determineExportGroupCreateSteps(Workflow workflow, String previousStep,
            BlockStorageDevice device, StorageSystem storage, ExportGroup exportGroup,
            List<URI> initiatorURIs, Map<URI, Integer> volumeMap, String token) throws Exception {
        Map<String, URI> portNameToInitiatorURI = new HashMap<String, URI>();
        List<URI> volumeURIs = new ArrayList<URI>();
        volumeURIs.addAll(volumeMap.keySet());

        List<URI> hostURIs = new ArrayList<URI>();
        List<String> portNames = new ArrayList<String>();
        // Populate the port WWN/IQNs (portNames) and the
        // mapping of the WWN/IQNs to Initiator URIs
        processInitiators(exportGroup, initiatorURIs, portNames, portNameToInitiatorURI, hostURIs);

        // We always want to have the full list of initiators for the hosts involved in
        // this export. This will allow the export operation to always find any
        // existing exports for a given host.
        queryHostInitiatorsAndAddToList(portNames, portNameToInitiatorURI,
                initiatorURIs, hostURIs);

        findAndUpdateFreeHLUsForClusterExport(storage, exportGroup, initiatorURIs, volumeMap);

        // Bogus URI for those initiators without a host object, helps maintain a good map.
        // We want to put bunch up the non-host initiators together.
        URI fillerHostURI = URIUtil.createId(Host.class); // could just be NullColumnValueGetter.getNullURI()
        if (!initiatorURIs.isEmpty()) {
            Map<URI, List<URI>> hostInitiatorMap = new HashMap<URI, List<URI>>();
            for (URI newExportMaskInitiator : initiatorURIs) {
                Initiator initiator = _dbClient.queryObject(Initiator.class, newExportMaskInitiator);
                // Not all initiators have hosts, be sure to handle either case.
                URI hostURI = initiator.getHost();
                if (hostURI == null) {
                    hostURI = fillerHostURI;
                }

                List<URI> initiatorSet = hostInitiatorMap.get(hostURI);
                if (initiatorSet == null) {
                    initiatorSet = new ArrayList<URI>();
                    hostInitiatorMap.put(hostURI, initiatorSet);
                }
                initiatorSet.add(initiator.getId());

                _log.info(String.format("host = %s, "
                        + "initiators to add: %d, ",
                        hostURI,
                        hostInitiatorMap.get(hostURI).size()));
            }
            if (!hostInitiatorMap.isEmpty()) {
                String exportStep = null;
                for (URI hostID : hostInitiatorMap.keySet()) {
                    _log.info(String.format("new export masks %s",
                            Joiner.on(",").join(hostInitiatorMap.get(hostID))));
                    if (exportStep == null) {
                        GenExportMaskCreateWorkflowResult result = generateExportMaskCreateWorkflow(workflow, previousStep, storage,
                                exportGroup,
                                hostInitiatorMap.get(hostID), volumeMap, token);
                        exportStep = result.getStepId();
                    } else {
                        GenExportMaskCreateWorkflowResult result = generateExportMaskCreateWorkflow(workflow, exportStep, storage,
                                exportGroup,
                                hostInitiatorMap.get(hostID), volumeMap, token);
                        exportStep = result.getStepId();
                    }
                }
            }
        }

        return true;
    }

    private Map<ExportMask, List<Initiator>> getInitiatorExportMasks(
            List<Initiator> initiators, DbClient dbClient, ExportGroup exportGroup, URI ssysURI) {
        Map<ExportMask, List<Initiator>> exportMasksMap = new HashMap<ExportMask, List<Initiator>>();

        List<ExportMask> exportMasks = ExportMaskUtils.getExportMasks(dbClient, exportGroup, ssysURI);
        for (ExportMask exportMask : exportMasks) {
            List<Initiator> maskInitiators = new ArrayList<Initiator>();
            for (Initiator initiator : initiators) {
                _log.info("initiator to be removed: {}", initiator.getId().toString());
                if (exportMask != null &&
                        !exportMask.getInactive() &&
                        exportMask.hasInitiator(initiator.getId().toString())) {
                    _log.info("initiator is in the mask: {}", exportMask.getId().toString());
                    maskInitiators.add(initiator);

                }
            }
            if (!maskInitiators.isEmpty()) {
                exportMasksMap.put(exportMask, maskInitiators);
            }
        }

        return exportMasksMap;
    }
}
