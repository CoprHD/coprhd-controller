/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.customconfigcontroller.CustomConfigConstants;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.BlockStorageDevice;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskDeleteCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskRemoveVolumeCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportOrchestrationTask;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeUpdateCompleter;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.workflow.Workflow;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.Sets;

/**
 * This class will contain HDS specific masking orchestration implementations.
 * The goal of this implementation would be to flexibly support export
 * operations. Essentially, the export operations need to be amenable to the
 * existence of exports created outside of the system. It should take to make
 * sure that it does what it can to allow the operation to succeed in light of
 * such cases.
 * 
 */
public class HDSMaskingOrchestrator extends AbstractBasicMaskingOrchestrator {
    private static final Logger _log = LoggerFactory.getLogger(HDSMaskingOrchestrator.class);

    private static final AtomicReference<BlockStorageDevice> HDS_BLOCK_DEVICE = new AtomicReference<BlockStorageDevice>();
    public static final String HDS_STORAGE_DEVICE = "hdsStorageDevice";
    public static final String DEFAULT_LABEL = "Default";

    @Override
    public BlockStorageDevice getDevice() {
        BlockStorageDevice device = HDS_BLOCK_DEVICE.get();
        synchronized (HDS_BLOCK_DEVICE) {
            if (device == null) {
                device = (BlockStorageDevice) ControllerServiceImpl.getBean(HDS_STORAGE_DEVICE);
                HDS_BLOCK_DEVICE.compareAndSet(null, device);
            }
        }
        return device;
    }

    /**
     * Create storage level masking components to support the requested
     * ExportGroup object. This operation will be flexible enough to take into
     * account initiators that are in some already existent in some
     * StorageGroup. In such a case, the underlying masking component will be
     * "adopted" by the ExportGroup. Further operations against the "adopted"
     * mask will only allow for addition and removal of those initiators/volumes
     * that were added by a Bourne request. Existing initiators/volumes will be
     * maintained.
     * 
     * 
     * @param storageURI - URI referencing underlying storage array
     * @param exportGroupURI - URI referencing Bourne-level masking, ExportGroup
     * @param initiatorURIs - List of Initiator URIs
     * @param volumeMap - Map of Volume URIs to requested Integer URI
     * @param token - Identifier for operation
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

                boolean createdSteps = determineExportGroupCreateSteps(workflow, null,
                        device, storage, exportGroup, initiatorURIs, volumeMap, false, token);

                String zoningStep = generateDeviceSpecificZoningCreateWorkflow(workflow,
                        EXPORT_GROUP_MASKING_TASK, exportGroup, null, volumeMap);

                if (createdSteps && null != zoningStep) {
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
        } catch (DeviceControllerException dex) {
            if (taskCompleter != null) {
                taskCompleter.error(_dbClient, DeviceControllerException.errors
                        .vmaxExportGroupCreateError(dex.getMessage()));
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
    public void exportGroupAddVolumes(URI storageURI, URI exportGroupURI,
            Map<URI, Integer> volumeMap, String token) throws Exception {

        ExportTaskCompleter taskCompleter = null;
        try {
            _log.info(String.format(
                    "exportAddVolume start - Array: %s ExportMask: %s Volume: %s",
                    storageURI.toString(), exportGroupURI.toString(), Joiner.on(',')
                            .join(volumeMap.entrySet())));
            String maskStep = null;
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class,
                    exportGroupURI);
            StorageSystem storage = _dbClient
                    .queryObject(StorageSystem.class, storageURI);
            taskCompleter = new ExportOrchestrationTask(exportGroupURI, token);

            List<ExportMask> exportMasks = ExportMaskUtils.getExportMasks(_dbClient,
                    exportGroup, storageURI);
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
                        _log.info("export_volume_add: adding volume to an existing export");
                        exportMask.addVolumes(volumeMap);
                        _dbClient.updateObject(exportMask);
                        masks.add(exportMask);
                    }
                }
                List<URI> volumeURIs = new ArrayList<URI>();
                volumeURIs.addAll(volumeMap.keySet());
                for (ExportMask mask : masks) {
                    maskStep = generateExportMaskAddVolumesWorkflow(workflow, null,
                            storage, exportGroup, mask, volumeMap);
                }
                generateZoningAddVolumesWorkflow(workflow,
                        maskStep, exportGroup, masks, volumeURIs);

                String successMessage = String.format(
                        "Volumes successfully added to export on StorageArray %s",
                        storage.getLabel());
                workflow.executePlan(taskCompleter, successMessage);
            } else {
                if (exportGroup.getInitiators() != null
                        && !exportGroup.getInitiators().isEmpty()) {
                    _log.info("export_volume_add: adding volume, creating a new export");

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
                        _log.info(String.format("New export masks for %s", computeKey));
                        GenExportMaskCreateWorkflowResult result = generateExportMaskCreateWorkflow(
                                workflow, null, storage, exportGroup,
                                computeInitiatorURIs, volumeMap, token);
                        exportMasksToZoneCreate.add(result.getMaskURI());
                        volumesToZoneCreate.putAll(volumeMap);
                    }

                    if (!exportMasksToZoneCreate.isEmpty()) {
                        generateZoningCreateWorkflow(workflow, EXPORT_GROUP_MASKING_TASK, exportGroup,
                                exportMasksToZoneCreate, volumesToZoneCreate);
                    }

                    String successMessage = String.format(
                            "Initiators successfully added to export StorageArray %s",
                            storage.getLabel());
                    workflow.executePlan(taskCompleter, successMessage);
                } else {
                    _log.info("export_volume_add: adding volume, no initiators yet");
                    taskCompleter.ready(_dbClient);
                }
            }

            _log.info(String.format(
                    "exportAddVolume end - Array: %s ExportMask: %s Volume: %s",
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
    protected boolean useComputedMaskName() {
        return true;
    }

    @Override
    protected String getMaskingCustomConfigTypeName(String exportType) {
        return CustomConfigConstants.HDS_HOST_STORAGE_DOMAIN_NAME_MASK_NAME;
    }

    /**
     * Routine contains logic to create an export mask on the array
     * 
     * @param workflow - Workflow object to create steps against
     * @param previousStep - [optional] Identifier of workflow step to wait for
     * @param device - BlockStorageDevice implementation
     * @param storage - StorageSystem object representing the underlying array
     * @param exportGroup - ExportGroup object representing Bourne-level masking
     * @param initiatorURIs - List of Initiator URIs
     * @param volumeMap - Map of Volume URIs to requested Integer HLUs
     * @param zoningStepNeeded - Not required ofr HDS
     * @param token - Identifier for the operation
     * 
     * @throws Exception
     */
    @Override
    public boolean determineExportGroupCreateSteps(Workflow workflow, String previousStep,
            BlockStorageDevice device, StorageSystem storage, ExportGroup exportGroup,
            List<URI> initiatorURIs, Map<URI, Integer> volumeMap, boolean zoningStepNeeded, String token) throws Exception {

        Map<String, URI> portNameToInitiatorURI = new HashMap<String, URI>();
        List<URI> volumeURIs = new ArrayList<URI>();
        volumeURIs.addAll(volumeMap.keySet());
        Map<URI, URI> hostToExistingExportMaskMap = new HashMap<URI, URI>();
        List<URI> hostURIs = new ArrayList<URI>();
        List<String> portNames = new ArrayList<String>();
        List<Initiator> initiators = _dbClient.queryObject(Initiator.class, initiatorURIs);
        // Populate the port WWN/IQNs (portNames) and the
        // mapping of the WWN/IQNs to Initiator URIs
        processInitiators(exportGroup, initiatorURIs, portNames, portNameToInitiatorURI, hostURIs);

        // We always want to have the full list of initiators for the hosts involved in
        // this export. This will allow the export operation to always find any
        // existing exports for a given host.
        queryHostInitiatorsAndAddToList(portNames, portNameToInitiatorURI,
                initiatorURIs, hostURIs);

        // Find the export masks that are associated with any or all the ports in
        // portNames. We will have to do processing differently based on whether
        // or there is an existing ExportMasks.
        Map<String, Set<URI>> matchingExportMaskURIs =
                device.findExportMasks(storage, portNames, false);
        if (matchingExportMaskURIs.isEmpty()) {
            _log.info(String.format("No existing mask found w/ initiators { %s }", Joiner.on(",")
                    .join(portNames)));
            createNewExportMaskWorkflowForInitiators(initiatorURIs, exportGroup, workflow, volumeMap, storage, token, previousStep);
        } else {
            _log.info(String.format("Mask(s) found w/ initiators {%s}. "
                    + "MatchingExportMaskURIs {%s}, portNameToInitiators {%s}", Joiner.on(",")
                    .join(portNames), Joiner.on(",").join(matchingExportMaskURIs.values()), Joiner
                    .on(",").join(portNameToInitiatorURI.entrySet())));
            // There are some initiators that already exist. We need to create a
            // workflow that create new masking containers or updates masking
            // containers as necessary.

            // These data structures will be used to track new initiators - ones
            // that don't already exist on the array
            List<URI> initiatorURIsCopy = new ArrayList<URI>();
            initiatorURIsCopy.addAll(initiatorURIs);

            // This loop will determine a list of volumes to update per export mask
            Map<URI, Map<URI, Integer>> existingMasksToUpdateWithNewVolumes = new HashMap<URI, Map<URI, Integer>>();
            Map<URI, Set<Initiator>> existingMasksToUpdateWithNewInitiators = new HashMap<URI, Set<Initiator>>();
            for (Map.Entry<String, Set<URI>> entry : matchingExportMaskURIs.entrySet()) {
                URI initiatorURI = portNameToInitiatorURI.get(entry.getKey());
                Initiator initiator = _dbClient.queryObject(Initiator.class, initiatorURI);
                // Keep track of those initiators that have been found to exist already
                // in some export mask on the array
                initiatorURIsCopy.remove(initiatorURI);
                // Get a list of the ExportMasks that were matched to the initiator
                List<URI> exportMaskURIs = new ArrayList<URI>();
                exportMaskURIs.addAll(entry.getValue());
                List<ExportMask> masks = _dbClient.queryObject(ExportMask.class, exportMaskURIs);
                _log.info(String.format("initiator %s masks {%s}", initiator.getInitiatorPort(),
                        Joiner.on(',').join(exportMaskURIs)));
                for (ExportMask mask : masks) {
                    if (null == mask.getMaskName()) {
                        String maskName = ExportMaskUtils.getMaskName(_dbClient, initiators, exportGroup, storage);
                        _log.info("Generated mask name: {}", maskName);
                        mask.setMaskName(maskName);
                    }

                    _log.info(String.format("mask %s has initiator %s", mask.getMaskName(),
                            initiator.getInitiatorPort()));
                    if (mask.getCreatedBySystem()) {
                        _log.info(String.format("initiator %s is in persisted mask %s",
                                initiator.getInitiatorPort(),
                                mask.getMaskName()));

                        // We're still OK if the mask contains ONLY initiators that can be found
                        // in our export group, because we would simply add to them.
                        if (mask.getInitiators() != null) {
                            for (String existingMaskInitiatorStr : mask.getInitiators()) {

                                // Now look at it from a different angle. Which one of our export group initiators
                                // are NOT in the current mask? And if so, if it belongs to the same host as an existing one,
                                // we should add it to this mask.
                                Iterator<URI> initiatorIter = initiatorURIsCopy.iterator();
                                while (initiatorIter.hasNext()) {
                                    Initiator initiatorCopy = _dbClient.queryObject(Initiator.class, initiatorIter.next());

                                    if (initiatorCopy != null && initiatorCopy.getId() != null
                                            && !mask.hasInitiator(initiatorCopy.getId().toString())) {
                                        Initiator existingMaskInitiator = _dbClient.queryObject(Initiator.class,
                                                URI.create(existingMaskInitiatorStr));
                                        if (existingMaskInitiator != null && initiatorCopy.getHost() != null &&
                                                initiatorCopy.getHost().equals(existingMaskInitiator.getHost())) {
                                            // Add to the list of initiators we need to add to this mask
                                            Set<Initiator> existingMaskInitiators = existingMasksToUpdateWithNewInitiators
                                                    .get(mask.getId());
                                            if (existingMaskInitiators == null) {
                                                existingMaskInitiators = new HashSet<Initiator>();
                                                existingMasksToUpdateWithNewInitiators.put(mask.getId(), existingMaskInitiators);
                                            }
                                            existingMaskInitiators.add(initiatorCopy);
                                            initiatorIter.remove(); // remove this from the list of initiators we'll make a new mask from
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Insert this initiator into the mask's list of initiators managed by the system.
                        // This will get persisted below.
                        mask.addInitiator(initiator);
                        if (!NullColumnValueGetter.isNullURI(initiator.getHost())) {
                            hostToExistingExportMaskMap.put(initiator.getHost(),
                                    mask.getId());
                        }
                    }

                    // We need to see if the volume also exists the mask,
                    // if it doesn't then we'll add it to the list of volumes to add.
                    for (URI boURI : volumeURIs) {
                        BlockObject bo = BlockObject.fetch(_dbClient, boURI);
                        if (!mask.hasExistingVolume(bo)) {
                            _log.info(String.format("volume %s is not in mask %s", bo.getNativeGuid(),
                                    mask.getMaskName()));
                            // The volume doesn't exist, so we have to add it to
                            // the masking container.
                            Map<URI, Integer> newVolumes = existingMasksToUpdateWithNewVolumes
                                    .get(mask.getId());
                            if (newVolumes == null) {
                                newVolumes = new HashMap<URI, Integer>();
                                existingMasksToUpdateWithNewVolumes.put(mask.getId(), newVolumes);
                            }
                            // Check if the requested HLU for the volume is
                            // already taken by a pre-existing volume.
                            Integer requestedHLU = volumeMap.get(bo.getId());
                            StringMap existingVolumesInMask = mask.getExistingVolumes();
                            if (existingVolumesInMask != null && requestedHLU.intValue() != ExportGroup.LUN_UNASSIGNED &&
                                    !ExportGroup.LUN_UNASSIGNED_DECIMAL_STR.equals(requestedHLU.toString()) &&
                                    existingVolumesInMask.containsValue(requestedHLU.toString())) {
                                ExportOrchestrationTask completer = new ExportOrchestrationTask(
                                        exportGroup.getId(), token);
                                ServiceError serviceError =
                                        DeviceControllerException.errors.
                                                exportHasExistingVolumeWithRequestedHLU(boURI.toString(), requestedHLU.toString());
                                completer.error(_dbClient, serviceError);
                                return false;
                            }
                            newVolumes.put(bo.getId(), requestedHLU);
                            mask.addToUserCreatedVolumes(bo);
                        }
                    }

                    // Update the list of volumes and initiators for the mask
                    Map<URI, Integer> volumeMapForExistingMask = existingMasksToUpdateWithNewVolumes
                            .get(mask.getId());
                    if (volumeMapForExistingMask != null && !volumeMapForExistingMask.isEmpty()) {
                        mask.addVolumes(volumeMapForExistingMask);
                    }

                    Set<Initiator> initiatorSetForExistingMask = existingMasksToUpdateWithNewInitiators
                            .get(mask.getId());
                    if (initiatorSetForExistingMask != null && initiatorSetForExistingMask.isEmpty()) {
                        mask.addInitiators(initiatorSetForExistingMask);
                    }

                    updateZoningMap(exportGroup, mask);
                    _dbClient.updateObject(mask);
                    // TODO: All export group modifications should be moved to completers
                    exportGroup.addExportMask(mask.getId());
                    _dbClient.updateObject(exportGroup);
                }

            }

            // The initiatorURIsCopy was used in the foreach initiator loop to see
            // which initiators already exist in a mask. If it is non-empty,
            // then it means there are initiators that are new,
            // so let's add them to the main tracker
            Map<URI, List<URI>> hostInitiatorMap = new HashMap<URI, List<URI>>();
            if (!initiatorURIsCopy.isEmpty()) {
                for (URI newExportMaskInitiator : initiatorURIsCopy) {

                    Initiator initiator = _dbClient.queryObject(Initiator.class, newExportMaskInitiator);
                    List<URI> initiatorSet = hostInitiatorMap.get(initiator.getHost());
                    if (initiatorSet == null) {
                        initiatorSet = new ArrayList<URI>();
                        hostInitiatorMap.put(initiator.getHost(), initiatorSet);
                    }
                    initiatorSet.add(initiator.getId());

                    _log.info(String.format("host = %s, "
                            + "initiators to add: %d, "
                            + "existingMasksToUpdateWithNewVolumes.size = %d",
                            initiator.getHost(),
                            hostInitiatorMap.get(initiator.getHost()).size(),
                            existingMasksToUpdateWithNewVolumes.size()));
                }
            }

            _log.info(String.format("existingMasksToUpdateWithNewVolumes.size = %d",
                    existingMasksToUpdateWithNewVolumes.size()));

            // At this point we have the necessary data structures populated to
            // determine the workflow steps. We are going to create new masks
            // and/or add volumes to existing masks.
            if (!hostInitiatorMap.isEmpty()) {
                for (URI hostID : hostInitiatorMap.keySet()) {
                    // Check if there is an existing mask (created outside of ViPR) for
                    // the host. If there is we will need to add these intiators
                    // associated with that host to the list
                    if (hostToExistingExportMaskMap.containsKey(hostID)) {
                        URI existingExportMaskURI =
                                hostToExistingExportMaskMap.get(hostID);
                        Set<Initiator> toAddInits = new HashSet<Initiator>();
                        List<URI> hostInitaitorList = hostInitiatorMap.get(hostID);
                        for (URI initURI : hostInitaitorList) {
                            Initiator initiator = _dbClient.queryObject(Initiator.class, initURI);
                            if (!initiator.getInactive()) {
                                toAddInits.add(initiator);
                            }
                        }
                        _log.info(String.format("Need to add new initiators to existing mask %s, %s",
                                existingExportMaskURI.toString(),
                                Joiner.on(',').join(hostInitaitorList)));
                        existingMasksToUpdateWithNewInitiators.put(existingExportMaskURI, toAddInits);
                        continue;
                    }
                    // We have some brand new initiators, let's add them to new masks
                    _log.info(String.format("new export masks %s",
                            Joiner.on(",").join(hostInitiatorMap.get(hostID))));

                    generateExportMaskCreateWorkflow(workflow, previousStep, storage, exportGroup,
                            hostInitiatorMap.get(hostID), volumeMap, token);
                }
            }

            Map<URI, String> stepMap = new HashMap<URI, String>();
            for (Map.Entry<URI, Map<URI, Integer>> entry : existingMasksToUpdateWithNewVolumes
                    .entrySet()) {
                ExportMask mask = _dbClient.queryObject(ExportMask.class, entry.getKey());
                Map<URI, Integer> volumesToAdd = entry.getValue();
                _log.info(String.format("adding these volumes %s to mask %s",
                        Joiner.on(",").join(volumesToAdd.keySet()), mask.getMaskName()));
                stepMap.put(entry.getKey(), generateExportMaskAddVolumesWorkflow(workflow, null, storage, exportGroup, mask,
                        volumesToAdd));
            }

            for (Entry<URI, Set<Initiator>> entry : existingMasksToUpdateWithNewInitiators.entrySet()) {
                ExportMask mask = _dbClient.queryObject(ExportMask.class, entry.getKey());
                Set<Initiator> initiatorsToAdd = entry.getValue();
                List<URI> initiatorsURIs = new ArrayList<URI>();
                for (Initiator initiator : initiatorsToAdd) {
                    initiatorsURIs.add(initiator.getId());
                }
                _log.info(String.format("adding these initiators %s to mask %s",
                        Joiner.on(",").join(initiatorsURIs), mask.getMaskName()));
                previousStep = stepMap.get(entry.getKey()) == null ? previousStep : stepMap.get(entry.getKey());
                generateExportMaskAddInitiatorsWorkflow(workflow, previousStep, storage, exportGroup, mask,
                        initiatorsURIs, null, token);
            }
        }
        return true;
    }

    @Override
    public void exportGroupChangePolicyAndLimits(URI storageURI, URI exportMaskURI,
            URI exportGroupURI, List<URI> volumeURIs, URI newVpoolURI,
            boolean rollback, String token) throws Exception {
        // ExportGroup and ExportMask URIs will be null for HDS.
        VolumeUpdateCompleter taskCompleter = new VolumeUpdateCompleter(
                volumeURIs, token);

        StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageURI);
        VirtualPool newVpool = _dbClient.queryObject(VirtualPool.class, newVpoolURI);
        BlockStorageDevice device = getDevice();
        device.updatePolicyAndLimits(storage, null, volumeURIs, newVpool,
                rollback, taskCompleter);
    }

    /**
     * Generates workflow step to Mark ExportMask inActive.
     * 
     * @param workflow
     * @param previousStep
     * @param exportGroup
     * @param exportMask
     * @param completer
     * @return
     */
    public String generateWorkflowStepToMarkExportMaskInActive(Workflow workflow,
            String previousStep, ExportGroup exportGroup, ExportMask exportMask,
            ExportTaskCompleter completer) {
        URI exportGroupURI = exportGroup.getId();

        String stepId = workflow.createStepId();
        ExportTaskCompleter exportTaskCompleter;
        if (completer != null) {
            exportTaskCompleter = completer;
            exportTaskCompleter.setOpId(stepId);
        } else {
            exportTaskCompleter = new ExportMaskDeleteCompleter(exportGroupURI,
                    exportMask.getId(), stepId);
        }

        Workflow.Method markExportMaskInActiveExecuteMethod = new Workflow.Method(
                "doExportGroupToCleanExportMask", exportGroupURI, exportMask.getId(),
                exportTaskCompleter);

        stepId = workflow.createStep(
                EXPORT_MASK_CLEANUP_TASK,
                String.format("Marking exportmasks to inactive %s (%s)",
                        exportMask.getMaskName(), exportMask.getId().toString()),
                previousStep, NullColumnValueGetter.getNullURI(), "storage-system",
                MaskingWorkflowEntryPoints.class, markExportMaskInActiveExecuteMethod,
                null, stepId);

        return stepId;
    }

    /**
     * Generates workflow step to remove volumes from ExportMask.
     * 
     * @param workflow
     * @param previousStep
     * @param exportGroup
     * @param exportMask
     * @param completer
     * @return
     */
    public String generateWorkflowStepToToRemoveVolumesFromExportMask(Workflow workflow,
            String previousStep, ExportGroup exportGroup, ExportMask exportMask,
            List<URI> volumesToRemove, ExportTaskCompleter completer) {
        URI exportGroupURI = exportGroup.getId();

        String stepId = workflow.createStepId();
        ExportTaskCompleter exportTaskCompleter;
        if (completer != null) {
            exportTaskCompleter = completer;
            exportTaskCompleter.setOpId(stepId);
        } else {
            exportTaskCompleter = new ExportMaskRemoveVolumeCompleter(exportGroupURI,
                    exportMask.getId(), volumesToRemove, stepId);
        }

        Workflow.Method removeVolumesFromExportMaskExecuteMethod = new Workflow.Method(
                "doExportGroupToCleanVolumesInExportMask", exportGroupURI, exportMask.getId(),
                volumesToRemove, exportTaskCompleter);

        stepId = workflow.createStep(
                EXPORT_MASK_CLEANUP_TASK,
                String.format("ExportMask to removeVolumes %s (%s)",
                        exportMask.getMaskName(), exportMask.getId().toString()),
                previousStep, NullColumnValueGetter.getNullURI(), "storage-system",
                MaskingWorkflowEntryPoints.class, removeVolumesFromExportMaskExecuteMethod,
                null, stepId);

        return stepId;
    }

    @Override
    protected Map<String, List<URI>> mapInitiatorsToComputeResource(
            ExportGroup exportGroup, Collection<URI> initiatorURIs) {
        Map<String, List<URI>> result = new HashMap<String, List<URI>>();
        if (exportGroup.forCluster()) {
            Cluster singleCluster = null;
            if (exportGroup.getClusters() != null && exportGroup.getClusters().size() == 1) {
                String clusterUriString = exportGroup.getClusters().iterator().next();
                singleCluster = _dbClient.queryObject(Cluster.class, URI.create(clusterUriString));
            }
            for (URI newExportMaskInitiator : initiatorURIs) {
                Initiator initiator =
                        _dbClient.queryObject(Initiator.class,
                                newExportMaskInitiator);
                String clusterName = getClusterName(singleCluster, initiator);
                List<URI> initiatorSet = result.get(clusterName);
                if (initiatorSet == null) {
                    initiatorSet = new ArrayList<URI>();
                    result.put(clusterName, initiatorSet);
                }
                initiatorSet.add(newExportMaskInitiator);
                _log.info(String.format("cluster = %s, initiators to add to map: %s, ",
                        clusterName,
                        newExportMaskInitiator.toString()));
            }
        } else {
            // Bogus URI for those initiators without a host object, helps maintain a good map.
            // We want to put bunch up the non-host initiators together.
            URI fillerHostURI = NullColumnValueGetter.getNullURI();
            for (URI newExportMaskInitiator : initiatorURIs) {
                Initiator initiator = _dbClient.queryObject(Initiator.class,
                        newExportMaskInitiator);

                // Not all initiators have hosts, be sure to handle either case.
                URI hostURI = initiator.getHost();
                if (hostURI == null) {
                    hostURI = fillerHostURI;
                }

                List<URI> initiatorSet = result.get(hostURI.toString());
                if (initiatorSet == null) {
                    initiatorSet = new ArrayList<URI>();
                    result.put(hostURI.toString(), initiatorSet);
                }
                initiatorSet.add(initiator.getId());

                _log.info(String.format("host = %s, initiators to add to map: %d, ",
                        hostURI,
                        result.get(hostURI.toString()).size()));
            }
        }
        return result;
    }

    @Override
    public void exportGroupRemoveVolumes(URI storageURI, URI exportGroupURI, List<URI> volumes,
            String token) throws Exception {
        ExportTaskCompleter taskCompleter = null;
        try {
            _log.info(String.format("exportRemoveVolume start - Array: %s ExportMask: %s "
                    + "Volume: %s", storageURI.toString(), exportGroupURI.toString(), Joiner
                    .on(',').join(volumes)));

            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
            StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageURI);
            taskCompleter = new ExportOrchestrationTask(exportGroupURI, token);

            List<ExportMask> exportMasks = ExportMaskUtils.getExportMasks(_dbClient, exportGroup,
                    storageURI);
            if (exportMasks != null && !exportMasks.isEmpty()) {
                Workflow workflow = _workflowService.getNewWorkflow(
                        MaskingWorkflowEntryPoints.getInstance(), "exportGroupRemoveVolumes", true,
                        token);
                List<ExportMask> exportMaskstoDelete = new ArrayList<ExportMask>();
                List<ExportMask> exportMaskstoRemoveVolume = new ArrayList<ExportMask>();

                for (ExportMask exportMask : exportMasks) {
                    // Delete mask only if there are no existing volumes & no shared mask across EG's.
                    if (isRemoveAllVolumes(exportMask, volumes) && exportMask.getExistingVolumes().isEmpty()
                            && !checkIfTheMaskIsSharedAcrossEGs(exportMask)) {
                        exportMaskstoDelete.add(exportMask);
                    } else {
                        exportMaskstoRemoveVolume.add(exportMask);
                    }
                }
                if (!exportMaskstoRemoveVolume.isEmpty()) {
                    String removeVolumeStep = null;
                    for (ExportMask exportMask : exportMaskstoRemoveVolume) {
                        removeVolumeStep = generateExportMaskRemoveVolumesWorkflow(workflow, null, storage,
                                exportGroup, exportMask, volumes, null);
                    }
                    generateZoningRemoveVolumesWorkflow(workflow, removeVolumeStep,
                            exportGroup, exportMaskstoRemoveVolume, volumes);

                }
                if (!exportMaskstoDelete.isEmpty()) {
                    String maskDeleteStep = null;
                    for (ExportMask exportMask : exportMaskstoDelete) {
                        maskDeleteStep = generateExportMaskDeleteWorkflow(workflow, null, storage,
                                exportGroup, exportMask, null);
                    }
                    generateZoningDeleteWorkflow(workflow, maskDeleteStep,
                            exportGroup, exportMaskstoDelete);
                }
                // volumes
                generateExportGroupRemoveVolumesCleanup(workflow, EXPORT_GROUP_MASKING_TASK, storage,
                        exportGroup, volumes);

                String successMessage = String.format(
                        "Volumes successfully unexported from StorageArray %s", storage.getLabel());
                workflow.executePlan(taskCompleter, successMessage);
            } else {
                _log.info("export_volume_remove: no export (initiator should be empty)");
                exportGroup.removeVolumes(volumes);
                _dbClient.updateObject(exportGroup);
                taskCompleter.ready(_dbClient);
            }

            _log.info(String.format("exportRemoveVolume end - Array: %s ExportMask: %s "
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
            _log.info(String.format("exportAddInitiator start - Array: %s ExportMask: "
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
            _log.info("initiators  : {}", Joiner.on(",").join(computeResourceToInitiators.entrySet()));

            taskCompleter = new ExportOrchestrationTask(exportGroupURI, token);

            Map<URI, Integer> volumes = selectExportMaskVolumes(exportGroup, storageURI);
            _log.info("Volumes  : {}", Joiner.on(",").join(volumes.keySet()));
            if (exportMasks != null && !exportMasks.isEmpty()) {
                // find the export mask which has the same Host name as the initiator
                // Add the initiator to that export mask
                // Set up workflow steps.
                _log.info("Creating AddInitiators workFlow");
                Workflow workflow = _workflowService.getNewWorkflow(
                        MaskingWorkflowEntryPoints.getInstance(),
                        "exportGroupAddInitiators", true, token);

                // irrespective of cluster name, host will be always present
                Map<String, URI> hostToEMaskGroup = ExportMaskUtils
                        .mapHostToExportMask(_dbClient, exportGroup,
                                storage.getId());
                _log.info("InitiatorsToHost  : {}", Joiner.on(",").join(hostToEMaskGroup.entrySet()));
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
                                workflow, null, storage, exportGroup,
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
                        generateZoningCreateWorkflow(workflow, EXPORT_GROUP_MASKING_TASK, exportGroup,
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
                        true, token);

                List<URI> exportMasksToZoneCreate = new ArrayList<URI>();
                Map<URI, Integer> volumesToZoneCreate = new HashMap<URI, Integer>();

                for (Map.Entry<String, List<URI>> resourceEntry : computeResourceToInitiators
                        .entrySet()) {
                    String computeKey = resourceEntry.getKey();
                    List<URI> computeInitiatorURIs = resourceEntry.getValue();
                    _log.info(String.format("New export masks for %s", computeKey));
                    GenExportMaskCreateWorkflowResult result = generateExportMaskCreateWorkflow(
                            workflow, null, storage, exportGroup,
                            computeInitiatorURIs, volumes, token);
                    exportMasksToZoneCreate.add(result.getMaskURI());
                    volumesToZoneCreate.putAll(volumes);
                }

                if (!exportMasksToZoneCreate.isEmpty()) {
                    generateZoningCreateWorkflow(workflow, EXPORT_GROUP_MASKING_TASK, exportGroup,
                            exportMasksToZoneCreate, volumesToZoneCreate);
                }

                String successMessage = String.format(
                        "Initiators successfully added to export StorageArray %s",
                        storage.getLabel());
                workflow.executePlan(taskCompleter, successMessage);
            }

            _log.info(String.format("exportAddInitiator end - Array: %s ExportMask: %s "
                    + "Initiator: %s", storageURI.toString(), exportGroupURI.toString(),
                    Joiner.on(',').join(initiatorURIs)));
        } catch (Exception e) {
            _log.info("Error", e);
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

            _log.info(String.format("exportRemoveInitiator start - Array: %s " +
                    "ExportMask: %s Initiator: %s",
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

            _log.info("Host to initiators  : {}", Joiner.on(",").join(computeResourceToInitiators.entrySet()));

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
                _log.info("Host to ExportMask  : {}", Joiner.on(",").join(hostToEMaskGroup.entrySet()));
                // if export masks are found for the Host, then remove initiators from the export mask
                // Export Masks are not shared between export Groups

                // list of export masks from which initiators need to be removed
                List<ExportMask> exportMaskRemoveInitiator = new ArrayList<ExportMask>();

                // list of export masks to delete as all initiators are removed
                List<ExportMask> exportMaskDelete = new ArrayList<ExportMask>();

                // list of export masks to delete volumes for shared export masks.
                List<ExportMask> exportMaskRemoveVolumes = new ArrayList<ExportMask>();

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
                            _log.info("Processing export mask  {} with initiators {}", storageURI, Joiner.on(",").join(initiators));
                            maskToInitiatorsMap.put(exportMask.getId(), computeResourceToInitiators.get(computeKey));
                            if (checkIfTheMaskIsSharedAcrossEGs(exportMask)) {
                                _log.info("Mask is shared across Export Groups. Hence removing volumes alone.", exportMask.getId());
                                // If the mask is shared then remove only the shared volumes.
                                exportMaskRemoveVolumes.add(exportMask);
                            } else {
                                // check if there are no existing initiators before delete the complete mask.
                                if (isRemoveAllInitiators(exportMask, initiators) && checkIfNoExistingInitiators(exportMask, initiators)) {
                                    exportMaskDelete.add(exportMask);
                                } else {
                                    exportMaskRemoveInitiator.add(exportMask);
                                }
                            }
                        }
                    }
                }
                String previousStep = null;
                if (!exportMaskRemoveInitiator.isEmpty()) {
                    for (ExportMask exportMask : exportMaskRemoveInitiator) {
                        previousStep = generateExportMaskRemoveInitiatorsWorkflow(workflow, zoningStep,
                                storage, exportGroup, exportMask, initiatorURIs, true);
                    }
                }
                if (!exportMaskDelete.isEmpty()) {
                    for (ExportMask exportMask : exportMaskDelete) {
                        previousStep = generateExportMaskDeleteWorkflow(workflow, null, storage,
                                exportGroup, exportMask, null);
                    }
                }
                // If the mask is shared between export groups, then just remove only the export group volumes.
                if (!exportMaskRemoveVolumes.isEmpty()) {
                    List<URI> volumes = getVolumesToRemove(exportGroup);
                    String removeVolumeStep = null;
                    for (ExportMask exportMask : exportMaskRemoveVolumes) {
                        removeVolumeStep = generateExportMaskRemoveVolumesWorkflow(workflow, null, storage,
                                exportGroup, exportMask, volumes, null);
                    }
                    if (!volumes.isEmpty()) {
                        generateZoningRemoveVolumesWorkflow(workflow, removeVolumeStep,
                                exportGroup, exportMaskRemoveVolumes, volumes);
                    }
                }

                if (!maskToInitiatorsMap.isEmpty() && exportMaskRemoveVolumes.isEmpty()) {
                    generateZoningRemoveInitiatorsWorkflow(workflow, previousStep,
                            exportGroup, maskToInitiatorsMap);
                }
                String successMessage = String.format(
                        "Initiators successfully removed from export StorageArray %s",
                        storage.getLabel());
                workflow.executePlan(taskCompleter, successMessage);

                _log.info(String.format("exportRemoveInitiator end - Array: %s ExportMask: " +
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

    private List<URI> getVolumesToRemove(ExportGroup exportGroup) {
        List<URI> egVolumes = new ArrayList<URI>();
        if (!exportGroup.getVolumes().isEmpty()) {
            egVolumes = (List<URI>) Collections2
                    .transform(exportGroup.getVolumes().keySet(), CommonTransformerFunctions.FCTN_STRING_TO_URI);
        }
        return egVolumes;
    }

    /**
     * Check if the ExportMask is shared by other ExportGroups.
     * 
     * @param exportMask
     * @return
     */
    private boolean checkIfTheMaskIsSharedAcrossEGs(ExportMask exportMask) {
        List<ExportGroup> groups = ExportMaskUtils.getExportGroups(_dbClient, exportMask);
        return groups.size() > 1;
    }

    /**
     * Checks if there are any existing initiators in the ExportMask.
     * 
     * @param exportMask
     * @return
     */
    private boolean checkIfNoExistingInitiators(ExportMask exportMask, List<Initiator> initiatorsToRemove) {
        StringSet existingInitiators = exportMask.getExistingInitiators();
        Collection<String> initiatorWwns = Collections2.transform(initiatorsToRemove,
                CommonTransformerFunctions.fctnInitiatorToPortName());
        existingInitiators.removeAll(initiatorWwns);
        return existingInitiators.isEmpty();
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
            _log.info(String.format("exportGroupDelete start - Array: %s ExportMask: %s",
                    storageURI.toString(), exportGroupURI.toString()));

            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class,
                    exportGroupURI);
            StorageSystem storage = _dbClient
                    .queryObject(StorageSystem.class, storageURI);
            TaskCompleter taskCompleter = new ExportOrchestrationTask(exportGroupURI,
                    token);
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
            String previousStep = null;

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
                if (checkIfTheMaskIsSharedAcrossEGs(exportMask)) {
                    Set<String> masksToDelete = getSharedMasksAcrossExportGroups(exportMask, exportGroup);
                    // If the exportMask is shared then remove volumes & initiators.
                    // check if all initiators are shared.
                } else {
                    previousStep = generateExportMaskDeleteWorkflow(workflow, null, storage, exportGroup,
                            exportMask, null);
                }
            }

            generateZoningDeleteWorkflow(workflow, previousStep, exportGroup, exportMasks);
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

    private Set<String> getSharedMasksAcrossExportGroups(ExportMask exportMask, ExportGroup currentExportGroup) {
        List<ExportGroup> exportGroups = ExportMaskUtils.getExportGroups(_dbClient, exportMask);
        StringSet initiatorsToDelete = currentExportGroup.getInitiators();
        StringSet maskToDelete = exportMask.getInitiators();
        Set<String> masksDelete = new HashSet<String>();
        for (ExportGroup exportGroup : exportGroups) {
            // leave the current ExportGroup.
            if (URIUtil.identical(exportGroup.getId(), currentExportGroup.getId())) {
                continue;
            }
            StringSet egInitiators = exportGroup.getInitiators();
            Set<String> diff = Sets.difference(initiatorsToDelete, egInitiators);
            if (diff.isEmpty()) {
                masksDelete.addAll(exportGroup.getExportMasks());
            }
        }
        return maskToDelete;
    }

    /**
     * Determine the name of the cluster that the initiator belongs to or belonged to. It is possible that
     * the cluster to host relationship is altered prior to the export operation. Hence, the initiator.clusterName
     * may be null or empty. So, we need to account for this case. We can determine the cluster name by
     * other means only when the ExportGroup contains a single cluster.
     *
     * @param singleCluster [in] - Cluster object. Can be null if ExportGroup does not have a single cluster in it
     * @param initiator [in] - Initiator object.
     * @return Cluster name that the initiator belongs (or belonged to)
     */
    private String getClusterName(Cluster singleCluster, Initiator initiator) {
        String initiatorClusterName = initiator.getClusterName();
        if (Strings.isNullOrEmpty(initiatorClusterName) && singleCluster != null) {
            // clusterName is unknown and singleCluster is non-null meaning that the
            // initiator should be associated with that cluster, so use that as the
            // / cluster name
            initiatorClusterName = singleCluster.getLabel();
        }
        return initiatorClusterName;
    }

}
