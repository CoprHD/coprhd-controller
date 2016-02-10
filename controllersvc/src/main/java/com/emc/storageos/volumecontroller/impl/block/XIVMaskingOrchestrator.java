/*
 * Copyright (c) 2008-2014 EMC Corporation
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

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.volumecontroller.BlockStorageDevice;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportOrchestrationTask;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.SnapshotWorkflowEntryPoints;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.workflow.Workflow;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Collections2;
import com.google.common.collect.ListMultimap;

/**
 * IBM XIV specific masking orchestration.
 * 
 * The goal of this implementation would be to flexibly support export
 * operations. Essentially, the export operations need to be amenable to the
 * existence of exports created outside of the system. It should take to make
 * sure that it does what it can to allow the operation to succeed in light of
 * such cases.
 */
public class XIVMaskingOrchestrator extends AbstractBasicMaskingOrchestrator {
    private static final Logger _log = LoggerFactory.getLogger(XIVMaskingOrchestrator.class);
    private static final AtomicReference<BlockStorageDevice> XIV_BLOCK_DEVICE = new AtomicReference<BlockStorageDevice>();
    public static final String XIV_SMIS_DEVICE = "xivSmisDevice";
    public static final String DEFAULT_LABEL = "Default";

    @Override
    public BlockStorageDevice getDevice() {
        BlockStorageDevice device = XIV_BLOCK_DEVICE.get();
        synchronized (XIV_BLOCK_DEVICE) {
            if (device == null) {
                device = (BlockStorageDevice) ControllerServiceImpl.getBean(XIV_SMIS_DEVICE);
                XIV_BLOCK_DEVICE.compareAndSet(null, device);
            }
        }

        return device;
    }

    @Override
    public void exportGroupAddVolumes(URI storageURI, URI exportGroupURI,
            Map<URI, Integer> volumeMap, String token) throws Exception {
        ExportOrchestrationTask taskCompleter = null;
        try {
            BlockStorageDevice device = getDevice();
            taskCompleter = new ExportOrchestrationTask(exportGroupURI, token);
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
            checkForInActiveExportGroup(exportGroup);
            StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageURI);
            boolean anyVolumesAdded = false;
            boolean createdNewMask = false;
            if (exportGroup.getExportMasks() != null) {
                // Set up workflow steps.
                Workflow workflow = _workflowService.getNewWorkflow(
                        MaskingWorkflowEntryPoints.getInstance(), "exportGroupAddVolumes", true,
                        token);
                List<ExportMask> exportMasksToZoneAddVolumes = new ArrayList<ExportMask>();
                List<URI> volumesToZoneAddVolumes = new ArrayList<URI>();
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
                    // Check for NO_VIPR. If found, avoid this mask.
                    if (exportMask.getMaskName() != null && exportMask.getMaskName().toUpperCase().contains(ExportUtils.NO_VIPR)) {
                        _log.info(String.format(
                                "ExportMask %s disqualified because the name contains %s (in upper or lower case) to exclude it",
                                exportMask.getMaskName(), ExportUtils.NO_VIPR));
                        continue;
                    }
                    if (!exportMask.getInactive()
                            && exportMask.getStorageDevice().equals(storageURI)) {
                        exportMask = device.refreshExportMask(storage, exportMask);
                        // BlockStorageDevice level, so that it has up-to-date
                        // info from
                        // the array
                        Map<URI, Integer> volumesToAdd = new HashMap<URI, Integer>();
                        for (URI boURI : volumeMap.keySet()) {
                            BlockObject bo = Volume.fetchExportMaskBlockObject(_dbClient, boURI);
                            if (bo != null && !exportMask.hasExistingVolume(bo.getWWN()) &&
                                    !exportMask.hasUserAddedVolume(bo.getWWN())) {
                                URI thisVol = bo.getId();
                                Integer hlu = volumeMap.get(boURI);
                                volumesToAdd.put(thisVol, hlu);
                            }
                            // Check if the requested HLU for the volume is
                            // already taken by a pre-existing volume.
                            Integer requestedHLU = volumeMap.get(boURI);
                            StringMap existingVolumesInMask = exportMask.getExistingVolumes();
                            if (existingVolumesInMask != null && requestedHLU.intValue() != ExportGroup.LUN_UNASSIGNED &&
                                    !ExportGroup.LUN_UNASSIGNED_DECIMAL_STR.equals(requestedHLU.toString()) &&
                                    existingVolumesInMask.containsValue(requestedHLU.toString())) {
                                ExportOrchestrationTask completer = new ExportOrchestrationTask(
                                        exportGroup.getId(), token);
                                ServiceError serviceError =
                                        DeviceControllerException.errors.
                                                exportHasExistingVolumeWithRequestedHLU(boURI.toString(), requestedHLU.toString());
                                completer.error(_dbClient, serviceError);
                                return;
                            }
                        }
                        _log.info(String.format("Mask %s, adding volumes %s",
                                exportMask.getMaskName(),
                                Joiner.on(',').join(volumesToAdd.entrySet())));
                        if (volumesToAdd.size() > 0) {
                            List<URI> volumeURIs = new ArrayList<URI>();
                            volumeURIs.addAll(volumesToAdd.keySet());
                            exportMasksToZoneAddVolumes.add(exportMask);
                            volumesToZoneAddVolumes.addAll(volumeURIs);

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
                    String attachGroupSnapshot;
                    // This is the case where we were requested to add volumes to the
                    // export for this storage array, but none were scheduled to be
                    // added. This could be either because there are existing masks,
                    // but the volumes are already in the export mask or there are no
                    // masks for the storage array. We are checking if there are any
                    // masks and if there are initiators for the export.
                    if (!ExportMaskUtils.hasExportMaskForStorage(_dbClient,
                            exportGroup, storageURI) && exportGroup.hasInitiators()) {
                        _log.info("No existing masks to which the requested volumes can be added. Creating a new mask");
                        List<URI> initiators =
                                StringSetUtil.stringSetToUriList(exportGroup.getInitiators());

                        attachGroupSnapshot =
                                checkForSnapshotsToCopyToTarget(workflow, storage,
                                        null, volumeMap, null);

                        Map<URI, List<URI>> hostInitiatorMap = new HashMap<URI, List<URI>>();
                        for (URI newExportMaskInitiator : initiators) {

                            Initiator initiator = _dbClient.queryObject(Initiator.class, newExportMaskInitiator);
                            // Not all initiators have hosts, be sure to handle either case.
                            URI hostURI = initiator.getHost();
                            if (hostURI == null) {
                                hostURI = NullColumnValueGetter.getNullURI();
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
                            for (URI hostID : hostInitiatorMap.keySet()) {
                                _log.info(String.format("new export masks %s",
                                        Joiner.on(",").join(hostInitiatorMap.get(hostID))));
                                String zoningStep = workflow.createStepId();

                                GenExportMaskCreateWorkflowResult result = generateExportMaskCreateWorkflow(workflow, zoningStep,
                                        storage, exportGroup,
                                        hostInitiatorMap.get(hostID), volumeMap, token);

                                List<URI> masks = new ArrayList<URI>();
                                masks.add(result.getMaskURI());
                                generateZoningCreateWorkflow(workflow,
                                        attachGroupSnapshot, exportGroup, masks, volumeMap, zoningStep);
                            }
                            createdNewMask = true;
                        }
                    }
                }
                if (!exportMasksToZoneAddVolumes.isEmpty()) {
                    generateZoningAddVolumesWorkflow(workflow, null,
                            exportGroup, exportMasksToZoneAddVolumes, volumesToZoneAddVolumes);
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
    public void exportGroupAddInitiators(URI storageURI, URI exportGroupURI,
            List<URI> initiatorURIs, String token) throws Exception {
        BlockStorageDevice device = getDevice();
        ExportOrchestrationTask taskCompleter = new ExportOrchestrationTask(exportGroupURI, token);
        ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
        checkForInActiveExportGroup(exportGroup);
        StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageURI);
        // Set up workflow steps.
        Workflow workflow = _workflowService.getNewWorkflow(
                MaskingWorkflowEntryPoints.getInstance(), "exportGroupAddInitiators", true, token);
        Map<URI, List<URI>> zoneMasksToInitiatorsURIs = new HashMap<URI, List<URI>>();
        Map<URI, Map<URI, Integer>> zoneNewMasksToVolumeMap = new HashMap<URI, Map<URI, Integer>>();

        List<URI> hostURIs = new ArrayList<URI>();
        Map<String, URI> portNameToInitiatorURI = new HashMap<String, URI>();
        List<String> portNames = new ArrayList<String>();
        // Populate the port WWN/IQNs (portNames) and the
        // mapping of the WWN/IQNs to Initiator URIs
        processInitiators(exportGroup, initiatorURIs, portNames, portNameToInitiatorURI, hostURIs);

        // Populate a map of volumes on the storage device
        List<BlockObject> blockObjects = new ArrayList<BlockObject>();
        Map<URI, Integer> volumeMap = new HashMap<URI, Integer>();
        if (exportGroup.getVolumes() != null) {
            for (Map.Entry<String, String> entry : exportGroup.getVolumes().entrySet()) {
                URI boURI = URI.create(entry.getKey());
                Integer hlu = Integer.valueOf(entry.getValue());
                BlockObject bo = BlockObject.fetch(_dbClient, boURI);
                if (bo.getStorageController().equals(storageURI)) {
                    volumeMap.put(boURI, hlu);
                    blockObjects.add(bo);
                }
            }
        }

        // We always want to have the full list of initiators for the hosts involved in
        // this export. This will allow the export operation to always find any
        // existing exports for a given host.
        queryHostInitiatorsAndAddToList(portNames, portNameToInitiatorURI,
                initiatorURIs, hostURIs);

        boolean anyOperationsToDo = false;
        Map<String, Set<URI>> matchingExportMaskURIs =
                device.findExportMasks(storage, portNames, false);
        if (!matchingExportMaskURIs.isEmpty()) {
            // There were some exports out there that already have some or all of the
            // initiators that we are attempting to add. We need to only add
            // volumes to those existing exports.
            List<URI> initiatorURIsCopy = new ArrayList<URI>();
            initiatorURIsCopy.addAll(initiatorURIs);

            // This loop will determine a list of volumes to update per export mask
            Map<URI, Map<URI, Integer>> existingMasksToUpdateWithNewVolumes = new HashMap<URI, Map<URI, Integer>>();
            Map<URI, Set<Initiator>> existingMasksToUpdateWithNewInitiators = new HashMap<URI, Set<Initiator>>();
            for (Map.Entry<String, Set<URI>> entry : matchingExportMaskURIs.entrySet()) {
                URI initiatorURI = portNameToInitiatorURI.get(entry.getKey());
                Initiator initiator = _dbClient.queryObject(Initiator.class, initiatorURI);
                initiatorURIsCopy.remove(initiatorURI);
                // Get a list of the ExportMasks that were matched to the initiator
                List<URI> exportMaskURIs = new ArrayList<URI>();
                exportMaskURIs.addAll(entry.getValue());
                List<ExportMask> masks = _dbClient.queryObject(ExportMask.class, exportMaskURIs);
                _log.info(String.format("initiator %s is in these masks {%s}",
                        initiator.getInitiatorPort(), Joiner.on(',').join(exportMaskURIs)));
                for (ExportMask mask : masks) {
                    // Check for NO_VIPR. If found, avoid this mask.
                    if (mask.getMaskName() != null && mask.getMaskName().toUpperCase().contains(ExportUtils.NO_VIPR)) {
                        _log.info(String.format(
                                "ExportMask %s disqualified because the name contains %s (in upper or lower case) to exclude it",
                                mask.getMaskName(), ExportUtils.NO_VIPR));
                        continue;
                    }

                    _log.info(String.format("mask %s has initiator %s", mask.getMaskName(),
                            initiator.getInitiatorPort()));
                    if (!mask.getInactive() && mask.getStorageDevice().equals(storageURI)) {
                        // Loop through all the block objects that have been exported
                        // to the storage system and place only those that are not
                        // already in the masks to the placement list
                        for (BlockObject blockObject : blockObjects) {
                            if (!mask.hasExistingVolume(blockObject.getWWN()) && !mask.hasUserAddedVolume(blockObject.getWWN())) {
                                Map<URI, Integer> newVolumesMap = existingMasksToUpdateWithNewVolumes
                                        .get(mask.getId());
                                if (newVolumesMap == null) {
                                    newVolumesMap = new HashMap<URI, Integer>();
                                    existingMasksToUpdateWithNewVolumes.put(mask.getId(),
                                            newVolumesMap);
                                }
                                newVolumesMap.put(blockObject.getId(),
                                        volumeMap.get(blockObject.getId()));
                            }
                        }

                        if (mask.getCreatedBySystem()) {
                            // Let's try to hunt down any additional initiators in this update that need to be added to
                            // existing masks because they belong to the same hosts.
                            //
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

                                        if (!mask.hasInitiator(initiatorCopy.getId().toString())) {
                                            Initiator existingMaskInitiator = _dbClient.queryObject(Initiator.class,
                                                    URI.create(existingMaskInitiatorStr));
                                            if (initiatorCopy.getHost().equals(existingMaskInitiator.getHost())) {
                                                // Add to the list of initiators we need to add to this mask
                                                Set<Initiator> existingMaskInitiators = existingMasksToUpdateWithNewInitiators.get(mask
                                                        .getId());
                                                if (existingMaskInitiators == null) {
                                                    existingMaskInitiators = new HashSet<Initiator>();
                                                    existingMasksToUpdateWithNewInitiators.put(mask.getId(), existingMaskInitiators);
                                                }
                                                if (!existingMaskInitiators.contains(initiatorCopy)) {
                                                    existingMaskInitiators.add(initiatorCopy);
                                                }
                                                initiatorIter.remove(); // remove this from the list of initiators we'll make a new mask
                                                                        // from
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Insert this initiator into the mask's list of initiators managed by the system.
                            // This will get persisted below.
                            mask.addInitiator(initiator);
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

            for (URI host : hostInitiatorMap.keySet()) {
                // Create two steps, one for Zoning, one for the ExportGroup actions.
                // This step is for zoning. It is not specific to a single NetworkSystem,
                // as it will look at all the initiators and targets and compute the
                // zones required (which might be on multiple NetworkSystems.)
                GenExportMaskCreateWorkflowResult result = generateExportMaskCreateWorkflow(workflow, EXPORT_GROUP_ZONING_TASK, storage,
                        exportGroup,
                        hostInitiatorMap.get(host), volumeMap, token);
                zoneNewMasksToVolumeMap.put(result.getMaskURI(), volumeMap);
                anyOperationsToDo = true;
            }

            _log.info(String.format("existingMasksToUpdateWithNewVolumes.size = %d",
                    existingMasksToUpdateWithNewVolumes.size()));

            String attachGroupSnapshot =
                    checkForSnapshotsToCopyToTarget(workflow, storage, null,
                            volumeMap, existingMasksToUpdateWithNewVolumes.values());

            // At this point we have a mapping of all the masks that we need to update with new volumes
            // stepMap [URI, String] => [Export Mask URI, StepId of previous task i.e. Add volumes work flow.]
            Map<URI, String> stepMap = new HashMap<URI, String>();
            for (Map.Entry<URI, Map<URI, Integer>> entry : existingMasksToUpdateWithNewVolumes
                    .entrySet()) {
                ExportMask mask = _dbClient.queryObject(ExportMask.class, entry.getKey());
                Map<URI, Integer> volumesToAdd = entry.getValue();
                _log.info(String.format("adding these volumes %s to mask %s",
                        Joiner.on(",").join(volumesToAdd.keySet()), mask.getMaskName()));
                List<URI> volumeURIs = new ArrayList<URI>();
                volumeURIs.addAll(volumesToAdd.keySet());
                stepMap.put(
                        entry.getKey(),
                        generateDeviceSpecificAddVolumeWorkFlow(workflow,
                                attachGroupSnapshot, storage, exportGroup, mask,
                                volumesToAdd, volumeURIs));
                anyOperationsToDo = true;
            }

            // At this point we have a mapping of all the masks that we need to update with new initiators
            for (Entry<URI, Set<Initiator>> entry : existingMasksToUpdateWithNewInitiators
                    .entrySet()) {
                ExportMask mask = _dbClient.queryObject(ExportMask.class, entry.getKey());
                Set<Initiator> initiatorsToAdd = entry.getValue();
                List<URI> initiatorsURIs = new ArrayList<URI>();
                for (Initiator initiator : initiatorsToAdd) {
                    initiatorsURIs.add(initiator.getId());
                }
                _log.info(String.format("adding these initiators %s to mask %s", Joiner
                        .on(",").join(initiatorsURIs), mask.getMaskName()));
                String previousStep = attachGroupSnapshot;
                if (stepMap.get(entry.getKey()) != null) {
                    previousStep = stepMap.get(entry.getKey());
                }
                Map<URI, List<URI>> maskToInitiatorsMap = new HashMap<URI, List<URI>>();
                maskToInitiatorsMap.put(mask.getId(), initiatorURIs);

                generateDeviceSpecificAddInitiatorWorkFlow(workflow, previousStep,
                        storage, exportGroup, mask, initiatorsURIs, maskToInitiatorsMap,
                        token);

                anyOperationsToDo = true;
            }

        } else {
            // None of the initiators that we're trying to add exist on the
            // array in some export. We need to find the ExportMask that was created by
            // the system and add the new initiator(s) to it.
            boolean foundASystemCreatedMask = false;
            Map<String, List<URI>> hostInitiatorMap = new HashMap<String, List<URI>>();
            if (!initiatorURIs.isEmpty()) {
                for (URI newExportMaskInitiator : initiatorURIs) {
                    Initiator initiator = _dbClient.queryObject(Initiator.class, newExportMaskInitiator);
                    if (initiator != null) {
                        String hostURIString = initiator.getHost().toString();
                        List<URI> initiatorSet = hostInitiatorMap.get(hostURIString);
                        if (initiatorSet == null) {
                            hostInitiatorMap.put(initiator.getHost().toString(),
                                    new ArrayList<URI>());
                            initiatorSet = hostInitiatorMap.get(hostURIString);
                        }
                        initiatorSet.add(initiator.getId());

                        _log.info(String.format("host = %s, "
                                + "initiators to add: %d, ",
                                initiator.getHost(),
                                hostInitiatorMap.get(hostURIString).size()));
                    }
                }
            }

            if (exportGroup.getExportMasks() != null) {
                _log.info("There are export masks for this group. Adding initiators.");
                // Loop through all the exports and add the initiators to those masks on
                // the storage system that were created by Bourne and are still active.
                for (String maskURIStr : exportGroup.getExportMasks()) {
                    URI maskURI = URI.create(maskURIStr);
                    ExportMask mask = _dbClient.queryObject(ExportMask.class, maskURI);
                    if (mask != null && !mask.getInactive()
                            && mask.getStorageDevice().equals(storageURI)
                            && mask.getCreatedBySystem()) {
                        List<URI> newInitiators =
                                hostInitiatorMap.get(mask.getResource());
                        if (newInitiators != null && !newInitiators.isEmpty()) {
                            zoneMasksToInitiatorsURIs.put(maskURI, newInitiators);

                            generateDeviceSpecificExportMaskAddInitiatorsWorkflow(workflow, EXPORT_GROUP_ZONING_TASK, storage,
                                    exportGroup, mask, newInitiators, token);
                            foundASystemCreatedMask = true;
                            anyOperationsToDo = true;
                        }
                    }
                }
            }

            if (!foundASystemCreatedMask) {
                _log.info("There are no masks for this export. Need to create anew.");

                for (String host : hostInitiatorMap.keySet()) {
                    // Zoning is done for the new masks identified i.e. zoneNewMasksToVolumeMap.
                    GenExportMaskCreateWorkflowResult result =
                            generateDeviceSpecificExportMaskCreateWorkFlow(workflow, EXPORT_GROUP_ZONING_TASK, storage, exportGroup,
                                    hostInitiatorMap.get(host), volumeMap, token);
                    zoneNewMasksToVolumeMap.put(result.getMaskURI(), volumeMap);
                    anyOperationsToDo = true;
                }
            }
        }

        if (anyOperationsToDo) {
            if (!zoneNewMasksToVolumeMap.isEmpty()) {
                List<URI> exportMaskList = new ArrayList<URI>();
                exportMaskList.addAll(zoneNewMasksToVolumeMap.keySet());
                Map<URI, Integer> overallVolumeMap = new HashMap<URI, Integer>();
                for (Map<URI, Integer> oneVolumeMap : zoneNewMasksToVolumeMap.values()) {
                    overallVolumeMap.putAll(oneVolumeMap);
                }
                generateDeviceSpecificZoningCreateWorkflow(workflow, null, exportGroup, exportMaskList, overallVolumeMap);
            }
            if (!zoneMasksToInitiatorsURIs.isEmpty()) {
                generateDeviceSpecificZoningAddInitiatorsWorkflow(workflow, null,
                        exportGroup, zoneMasksToInitiatorsURIs);
            }
            String successMessage = String.format(
                    "Successfully exported to initiators on StorageArray %s", storage.getLabel());
            workflow.executePlan(taskCompleter, successMessage);
        } else {
            taskCompleter.ready(_dbClient);
        }
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
     * @param zoningStepNeeded - Not required for XIV
     * @param token - Identifier for the operation
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
        Map<String, Set<URI>> matchingExportMaskURIs = device.findExportMasks(storage, portNames, false);
        if (matchingExportMaskURIs.isEmpty()) {
            String attachGroupSnapshot =
                    checkForSnapshotsToCopyToTarget(workflow, storage, previousStep,
                            volumeMap, null);

            _log.info(String.format("No existing mask found w/ initiators { %s }", Joiner.on(",")
                    .join(portNames)));
            createNewExportMaskWorkflowForInitiators(initiatorURIs, exportGroup, workflow, volumeMap, storage, token, attachGroupSnapshot);
        } else {
            _log.info(String.format("Mask(s) found w/ initiators {%s}. "
                    + "MatchingExportMaskURIs {%s}, portNameToInitiators {%s}", Joiner.on(",")
                    .join(portNames), Joiner.on(",").join(matchingExportMaskURIs.keySet()), Joiner
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
                    _log.info(String.format("mask %s has initiator %s", mask.getMaskName(),
                            initiator.getInitiatorPort()));

                    // Check for NO_VIPR. If found, avoid this mask.
                    if (mask.getMaskName() != null && mask.getMaskName().toUpperCase().contains(ExportUtils.NO_VIPR)) {
                        _log.info(String.format(
                                "ExportMask %s disqualified because the name contains %s (in upper or lower case) to exclude it",
                                mask.getMaskName(), ExportUtils.NO_VIPR));
                        continue;
                    }

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
                        BlockObject bo = Volume.fetchExportMaskBlockObject(_dbClient, boURI);
                        if (bo != null && !mask.hasExistingVolume(bo) && !mask.hasUserAddedVolume(bo.getWWN())) {
                            _log.info(String.format("volume %s is not in mask %s", bo.getWWN(),
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
                            Integer requestedHLU = volumeMap.get(boURI);
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

            String attachGroupSnapshot =
                    checkForSnapshotsToCopyToTarget(workflow, storage, previousStep,
                            volumeMap, existingMasksToUpdateWithNewVolumes.values());

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

                    generateExportMaskCreateWorkflow(workflow, attachGroupSnapshot, storage, exportGroup,
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
                stepMap.put(entry.getKey(), generateExportMaskAddVolumesWorkflow(workflow, attachGroupSnapshot, storage, exportGroup, mask,
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
                previousStep = stepMap.get(entry.getKey()) == null ? attachGroupSnapshot : stepMap.get(entry.getKey());
                generateExportMaskAddInitiatorsWorkflow(workflow, previousStep, storage, exportGroup, mask,
                        initiatorsURIs, null, token);
            }
        }
        return true;
    }

    /**
     * Method creates a workflow step for copying snapshots to the target devices,
     * so that they can be exported.
     * 
     * @param workflow - Workflow object to create steps against
     * @param previousStep - [optional] Identifier of workflow step to wait for
     * @param storageSystem - StorageSystem object representing the underlying array
     * @param volumeMap - Map of Volume URIs to requested Integer HLUs
     * @param volumesToAdd - Map of Volumes that need to be added to the export
     * 
     * @return String workflow step ID. If no workflow is added,
     *         the passed in previousStep id is returned.
     * 
     */
    @Override
    public String
            checkForSnapshotsToCopyToTarget(Workflow workflow, StorageSystem storageSystem,
                    String previousStep,
                    Map<URI, Integer> volumeMap,
                    Collection<Map<URI, Integer>> volumesToAdd) {
        String step = previousStep;
        ListMultimap<String, URI> snaps =
                getBlockSnapshotsRequiringCopyToTarget(volumeMap, volumesToAdd);
        if (snaps != null && !snaps.isEmpty()) {
            for (Map.Entry<String, Collection<URI>> entries : snaps.asMap().entrySet()) {
                List<URI> snapshots = new ArrayList<URI>();
                snapshots.addAll(entries.getValue());
                _log.info(String.format("Need to run copy-to-target snapshots in snap set %s:%n%s",
                        entries.getKey(), Joiner.on(',').join(snapshots)));
                step = SnapshotWorkflowEntryPoints.
                        generateCopySnapshotsToTargetWorkflow(workflow, step,
                                storageSystem, snapshots);
            }
        } else {
            _log.info("There are no block snapshots that require copy-to-target.");
        }
        return step;
    }

    /**
     * Method will return a ListMultimap of String snapsetLabel to snapshots that need
     * to have copyToTarget setup for them.
     * 
     * @param volumeMap - Map of Volume URIs to requested Integer HLUs
     * @param volumesToAdd - Map of Volumes that need to be added to the export
     * 
     * @return
     */
    private ListMultimap<String, URI>
            getBlockSnapshotsRequiringCopyToTarget(Map<URI, Integer> volumeMap,
                    Collection<Map<URI, Integer>> volumesToAdd) {
        ListMultimap<String, URI> snapLabelToSnapURIs = ArrayListMultimap.create();
        List<URI> list = new ArrayList<URI>();

        if (volumeMap != null) {
            list.addAll(volumeMap.keySet());
        }

        if (volumesToAdd != null) {
            for (Map<URI, Integer> map : volumesToAdd) {
                list.addAll(map.keySet());
            }
        }

        for (URI uri : list) {
            if (!URIUtil.isType(uri, BlockSnapshot.class)) {
                continue;
            }

            BlockSnapshot snapshot = _dbClient.queryObject(BlockSnapshot.class, uri);
            if (snapshot != null && snapshot.getNeedsCopyToTarget()) {
                String label = (Strings.isNullOrEmpty(snapshot.getSnapsetLabel())) ?
                        DEFAULT_LABEL : snapshot.getSnapsetLabel();
                snapLabelToSnapURIs.put(label, uri);
            }
        }
        return snapLabelToSnapURIs;
    }

    /**
     * Overridden implementation of createNewExportMaskWorkflowForInitiators for XIV. The difference
     * with this implementation and the superclass' is that here the creates will be run sequentially.
     */
    @Override
    protected List<String> createNewExportMaskWorkflowForInitiators(List<URI> initiatorURIs,
            ExportGroup exportGroup, Workflow workflow, Map<URI, Integer> volumeMap,
            StorageSystem storage, String token, String previousStep) throws Exception {

        List<String> newSteps = new ArrayList<>();
        if (!initiatorURIs.isEmpty()) {
            Map<String, List<URI>> computeResourceToInitiators = mapInitiatorsToComputeResource(
                    exportGroup, initiatorURIs);
            for (Map.Entry<String, List<URI>> resourceEntry : computeResourceToInitiators
                    .entrySet()) {
                String computeKey = resourceEntry.getKey();
                List<URI> computeInitiatorURIs = resourceEntry.getValue();
                _log.info(String.format("New export masks for %s", computeKey));
                GenExportMaskCreateWorkflowResult result =
                        generateDeviceSpecificExportMaskCreateWorkFlow(workflow, previousStep, storage, exportGroup,
                                computeInitiatorURIs, volumeMap, token);
                // Run the creates sequentially. There could be some issues with database consistency if
                // masking operations are run in parallel as calls get interleaved against the provider.
                previousStep = result.getStepId();
            }
        }
        newSteps.add(previousStep);
        return newSteps;
    }
}
