/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
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
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.BlockStorageDevice;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskDeleteCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskRemoveVolumeCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportOrchestrationTask;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeUpdateCompleter;
import com.emc.storageos.volumecontroller.impl.hds.prov.job.HDSExportMaskDeleteCompleter;
import com.emc.storageos.volumecontroller.impl.hds.prov.job.HDSExportMaskRemoveVolumeCompleter;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.workflow.Workflow;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;

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
        ExportOrchestrationTask taskCompleter = null;
        try {
            BlockStorageDevice device = getDevice();
            taskCompleter = new ExportOrchestrationTask(exportGroupURI, token);
            StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageURI);
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
            boolean anyVolumesAdded = false;
            boolean createdNewMask = false;
            if (exportGroup.getExportMasks() != null) {
                // Set up workflow steps.
                Workflow workflow = _workflowService.getNewWorkflow(
                        MaskingWorkflowEntryPoints.getInstance(), "exportGroupAddVolumes", true,
                        token);
                List<ExportMask> exportMasksToZoneAddVolumes = new ArrayList<ExportMask>();
                List<URI> volumesToZoneAddVolumes = new ArrayList<URI>();
                List<URI> exportMasksToZoneCreate = new ArrayList<URI>();
                Map<URI, Integer> volumesToZoneCreate = new HashMap<URI, Integer>();
                // Add the volume to all the ExportMasks that are contained in the
                // ExportGroup. The volumes should be added only if they don't
                // already exist for the ExportMask.
                Collection<URI> initiatorURIs =
                        Collections2.transform(exportGroup.getInitiators(),
                                CommonTransformerFunctions.FCTN_STRING_TO_URI);
                List<URI> hostURIs = new ArrayList<URI>();
                Map<String, URI> portNameToInitiatorURI = new HashMap<String, URI>();
                List<String> portNames = new ArrayList<String>();
                processInitiators(exportGroup, initiatorURIs, portNames, portNameToInitiatorURI, hostURIs);
                // We always want to have the full list of initiators for the hosts involved in
                // this export. This will allow the export operation to always find any
                // existing exports for a given host.
                queryHostInitiatorsAndAddToList(portNames, portNameToInitiatorURI,
                        initiatorURIs, hostURIs);
                Map<String, Set<URI>> foundMatches = device.findExportMasks(storage, portNames, false);
                Set<String> checkMasks = mergeWithExportGroupMaskURIs(exportGroup, foundMatches.values());
                for (String maskURIStr : checkMasks) {
                    ExportMask exportMask = _dbClient.queryObject(ExportMask.class,
                            URI.create(maskURIStr));
                    _log.info(String.format("Checking mask %s", exportMask.getMaskName()));
                    if (!exportMask.getInactive()
                            && exportMask.getStorageDevice().equals(storageURI)) {
                        exportMask = device.refreshExportMask(storage, exportMask);
                        // BlockStorageDevice level, so that it has up-to-date
                        // info from the array
                        Map<URI, Integer> volumesToAdd = getVolumesToAdd(volumeMap, exportMask, exportGroup, token);
                        // Not able to get VolumesToAdd due to error condition so, return
                        if (null == volumesToAdd) {
                            return;
                        }
                        _log.info(String.format("Mask %s, adding volumes %s",
                                exportMask.getMaskName(),
                                Joiner.on(',').join(volumesToAdd.entrySet())));
                        if (volumesToAdd.size() > 0) {
                            exportMasksToZoneAddVolumes.add(exportMask);
                            volumesToZoneAddVolumes.addAll(volumesToAdd.keySet());

                            // Make sure the zoning map is getting updated for user-created masks
                            updateZoningMap(exportGroup, exportMask, true);
                            generateExportMaskAddVolumesWorkflow(workflow, EXPORT_GROUP_ZONING_TASK, storage,
                                    exportGroup, exportMask, volumesToAdd);
                            anyVolumesAdded = true;
                            // Need to check if the mask is not already associated with
                            // ExportGroup. This is case when we are adding volume to
                            // the export and there is an existing export on the array.
                            // We have to reuse that existing export, but we need also
                            // associated it with the ExportGroup.
                            if (!exportGroup.hasMask(exportMask.getId())) {
                                exportGroup.addExportMask(exportMask.getId());
                                _dbClient.updateAndReindexObject(exportGroup);
                            }
                        }
                    }
                }
                if (!anyVolumesAdded) {
                    // This is the case where we were requested to add volumes to the
                    // export for this storage array, but none were scheduled to be
                    // added. This could be either because there are existing masks,
                    // but the volumes are already in the export mask or there are no
                    // masks for the storage array. We are checking if there are any
                    // masks and if there are initiators for the export.
                    if (!ExportMaskUtils.hasExportMaskForStorage(_dbClient,
                            exportGroup, storageURI) &&
                            exportGroup.hasInitiators()) {
                        _log.info("No existing masks to which the requested volumes can be added. Creating a new mask");
                        List<URI> initiators =
                                StringSetUtil.stringSetToUriList(exportGroup.getInitiators());

                        Map<String, List<URI>> hostInitiatorMap =
                                mapInitiatorsToComputeResource(exportGroup, initiators);

                        if (!hostInitiatorMap.isEmpty()) {
                            for (Map.Entry<String, List<URI>> resourceEntry : hostInitiatorMap
                                    .entrySet()) {
                                String computeKey = resourceEntry.getKey();
                                List<URI> computeInitiatorURIs = resourceEntry.getValue();
                                _log.info(String.format("New export masks for %s",
                                        computeKey));
                                GenExportMaskCreateWorkflowResult result = generateExportMaskCreateWorkflow(workflow,
                                        EXPORT_GROUP_ZONING_TASK, storage, exportGroup,
                                        computeInitiatorURIs, volumeMap, token);
                                exportMasksToZoneCreate.add(result.getMaskURI());
                                volumesToZoneCreate.putAll(volumeMap);
                            }
                            createdNewMask = true;
                        }
                    }
                }

                if (!exportMasksToZoneAddVolumes.isEmpty()) {
                    generateZoningAddVolumesWorkflow(workflow, null,
                            exportGroup, exportMasksToZoneAddVolumes, volumesToZoneAddVolumes);
                }

                if (!exportMasksToZoneCreate.isEmpty()) {
                    generateZoningCreateWorkflow(workflow, null, exportGroup, exportMasksToZoneCreate, volumesToZoneCreate);
                }

                String successMessage = String.format(
                        "Successfully added volumes to export on StorageArray %s",
                        storage.getLabel());
                workflow.executePlan(taskCompleter, successMessage);
            } else {
                if (exportGroup.hasInitiators()) {
                    _log.info("There are no masks for this export. Need to create anew.");
                    List<URI> initiatorURIs = new ArrayList<URI>();
                    for (String initiatorURIStr : exportGroup.getInitiators()) {
                        initiatorURIs.add(URI.create(initiatorURIStr));
                    }
                    // Invoke the export group create operation,
                    // which should in turn create a workflow operations to
                    // create the export for the newly added volume(s).
                    exportGroupCreate(storageURI, exportGroupURI, initiatorURIs, volumeMap, token);
                    anyVolumesAdded = true;
                } else {
                    _log.warn("There are no initiator for export group: " + exportGroup.getLabel());
                }
            }
            if (!anyVolumesAdded && !createdNewMask) {
                taskCompleter.ready(_dbClient);
                _log.info("No volumes pushed to array because either they already exist " +
                        "or there were no initiators added to the export yet.");
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
                    _dbClient.updateAndReindexObject(mask);
                    // TODO: All export group modifications should be moved to completers
                    exportGroup.addExportMask(mask.getId());
                    _dbClient.updateAndReindexObject(exportGroup);
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
    public String generateDeviceSpecificAddInitiatorWorkFlow(Workflow workflow,
            String previousStep, StorageSystem storage, ExportGroup exportGroup,
            ExportMask mask, List<URI> initiatorsURIs,
            Map<URI, List<URI>> maskToInitiatorsMap, String token) throws Exception {

        String maskingStep = generateExportMaskAddInitiatorsWorkflow(workflow, previousStep,
                storage, exportGroup, mask, initiatorsURIs, null, token);

        return generateZoningAddInitiatorsWorkflow(workflow, maskingStep, exportGroup,
                maskToInitiatorsMap);
    }

    @Override
    public String generateDeviceSpecificAddVolumeWorkFlow(Workflow workflow,
            String previousStep, StorageSystem storage, ExportGroup exportGroup,
            ExportMask mask, Map<URI, Integer> volumesToAdd, List<URI> volumeURIs) throws Exception {
        List<ExportMask> masks = new ArrayList<ExportMask>();
        masks.add(mask);
        String exportStepId = generateExportMaskAddVolumesWorkflow(workflow, previousStep, storage, exportGroup,
                mask, volumesToAdd);

        generateZoningAddVolumesWorkflow(workflow, exportStepId, exportGroup, masks, volumeURIs);
        return exportStepId;
    }

    @Override
    public GenExportMaskCreateWorkflowResult generateDeviceSpecificExportMaskCreateWorkFlow(Workflow workflow,
            String zoningGroupId, StorageSystem storage, ExportGroup exportGroup,
            List<URI> hostInitiators, Map<URI, Integer> volumeMap, String token) throws Exception {
        return generateExportMaskCreateWorkflow(workflow, null, storage, exportGroup, hostInitiators, volumeMap, token);
    }

    @Override
    public String generateDeviceSpecificZoningCreateWorkflow(Workflow workflow,
            String previousStepId, ExportGroup exportGroup, List<URI> exportMaskList,
            Map<URI, Integer> overallVolumeMap) {
        return generateZoningCreateWorkflow(workflow, previousStepId, exportGroup,
                exportMaskList, overallVolumeMap);
    }

    @Override
    public String generateDeviceSpecificExportMaskAddInitiatorsWorkflow(Workflow workflow,
            String zoningGroupId, StorageSystem storage, ExportGroup exportGroup,
            ExportMask mask, List<URI> newInitiators, String token) throws Exception {
        return generateExportMaskAddInitiatorsWorkflow(workflow, zoningGroupId, storage,
                exportGroup, mask, newInitiators, null, token);
    }

    @Override
    public String generateDeviceSpecificZoningAddInitiatorsWorkflow(Workflow workflow,
            String previousStep, ExportGroup exportGroup,
            Map<URI, List<URI>> zoneMasksToInitiatorsURIs) {
        return generateZoningAddInitiatorsWorkflow(workflow, previousStep, exportGroup,
                zoneMasksToInitiatorsURIs);
    }

    @Override
    public String generateDeviceSpecificDeleteWorkflow(Workflow workflow,
            String previousStep, ExportGroup exportGroup, ExportMask mask,
            StorageSystem storage) throws Exception {
        List<ExportMask> masks = new ArrayList<ExportMask>();
        masks.add(mask);
        ExportTaskCompleter hdsExportMaskDeleteCompleter = new HDSExportMaskDeleteCompleter(
                exportGroup.getId(), mask.getId(), previousStep);
        String exportMaskDeleteStepId = generateExportMaskDeleteWorkflow(workflow, null,
                storage, exportGroup, mask, hdsExportMaskDeleteCompleter);
        String zoningStepId = generateZoningDeleteWorkflow(workflow, exportMaskDeleteStepId, exportGroup, masks);
        generateWorkflowStepToMarkExportMaskInActive(workflow, zoningStepId, exportGroup,
                mask, null);
        return exportMaskDeleteStepId;
    }

    @Override
    public String generateDeviceSpecificRemoveInitiatorsWorkflow(Workflow workflow,
            String previousStep, ExportGroup exportGroup, ExportMask mask,
            StorageSystem storage, Map<URI, List<URI>> maskToInitiatorsMap,
            List<URI> initiatorsToRemove, boolean removeTargets) throws Exception {

        String exportMaskRemoveInitiatorsStepId = generateExportMaskRemoveInitiatorsWorkflow(
                workflow, previousStep, storage, exportGroup, mask, initiatorsToRemove, removeTargets);

        return generateZoningRemoveInitiatorsWorkflow(workflow,
                exportMaskRemoveInitiatorsStepId, exportGroup, maskToInitiatorsMap);
    }

    @Override
    public String generateDeviceSpecificRemoveVolumesWorkflow(Workflow workflow,
            String previousStep, ExportGroup exportGroup, ExportMask mask,
            StorageSystem storage, List<URI> volumesToRemove,
            ExportTaskCompleter completer) throws Exception {
        String exportMaskRemoveVolumesStepId = generateExportMaskRemoveVolumesWorkflow(
                workflow, previousStep, storage, exportGroup, mask, volumesToRemove,
                completer);
        String zoningStepId = generateZoningRemoveVolumesWorkflow(workflow, exportMaskRemoveVolumesStepId,
                exportGroup, Arrays.asList(mask), volumesToRemove);
        return generateWorkflowStepToToRemoveVolumesFromExportMask(workflow, zoningStepId,
                exportGroup, mask, volumesToRemove,
                null);
    }

    @Override
    public String generateDeviceSpecificExportMaskDeleteWorkflow(Workflow workflow,
            String previousStep, ExportGroup exportGroup, ExportMask exportMask,
            StorageSystem storage) throws Exception {
        ExportTaskCompleter hdsExportMaskDeleteCompleter = new HDSExportMaskDeleteCompleter(
                exportGroup.getId(), exportMask.getId(), previousStep);
        // HDS ExportMask operation should not depend on any other workflow step.
        return generateExportMaskDeleteWorkflow(workflow, null, storage, exportGroup,
                exportMask, hdsExportMaskDeleteCompleter);
    }

    @Override
    public String generateDeviceSpecificExportMaskRemoveVolumesWorkflow(Workflow workflow,
            String previousStep, ExportGroup exportGroup, ExportMask exportMask,
            StorageSystem storage, List<URI> volumesToRemove,
            ExportTaskCompleter completer) throws Exception {
        ExportTaskCompleter taskCompleter = new HDSExportMaskRemoveVolumeCompleter(
                exportGroup.getId(), exportMask.getId(), volumesToRemove, previousStep);
        return generateExportMaskRemoveVolumesWorkflow(workflow, previousStep, storage, exportGroup,
                exportMask, volumesToRemove, taskCompleter);
    }

    @Override
    public String generateDeviceSpecificZoningRemoveVolumesWorkflow(Workflow workflow,
            String previousStep, ExportGroup exportGroup,
            List<ExportMask> exportMasksToZoneRemoveVolumes,
            List<URI> volumesToZoneRemoveVolumes) {
        String zoningStepId = generateZoningRemoveVolumesWorkflow(workflow, previousStep,
                exportGroup, exportMasksToZoneRemoveVolumes, volumesToZoneRemoveVolumes);
        String returnStep = zoningStepId;
        for (ExportMask mask : exportMasksToZoneRemoveVolumes) {
            returnStep = generateWorkflowStepToToRemoveVolumesFromExportMask(workflow, zoningStepId,
                    exportGroup, mask, volumesToZoneRemoveVolumes, null);
        }
        return returnStep;
    }

    @Override
    public String generateDeviceSpecificZoningDeleteWorkflow(Workflow workflow,
            String previousStep, ExportGroup exportGroup,
            List<ExportMask> exportMasksToZoneDelete) {
        String zoningStepId = generateZoningDeleteWorkflow(workflow, EXPORT_GROUP_MASKING_TASK,
                exportGroup, exportMasksToZoneDelete);
        String returnStep = zoningStepId;
        for (ExportMask exportMask : exportMasksToZoneDelete) {
            returnStep = generateWorkflowStepToMarkExportMaskInActive(workflow, zoningStepId,
                    exportGroup, exportMask, null);
        }
        return returnStep;
    }

    /**
     * Generates device specific workflow step to remove volumes from ExportGroup.
     * 
     * @param workflow
     * @param previousStep
     * @param storage
     * @param exportGroup
     * @param volumeURIs
     */
    @Override
    public String generateDeviceSpecificExportGroupRemoveVolumesCleanup(Workflow workflow,
            String previousStep, StorageSystem storage, ExportGroup exportGroup,
            List<URI> volumeURIs) {
        return generateExportGroupRemoveVolumesCleanup(workflow, previousStep, storage,
                exportGroup, volumeURIs);
    }
}
