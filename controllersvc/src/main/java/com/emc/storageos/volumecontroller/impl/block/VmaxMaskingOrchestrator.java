/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block;

import static com.google.common.collect.Lists.newArrayList;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.emc.storageos.computesystemcontroller.impl.ComputeSystemHelper;
import com.emc.storageos.customconfigcontroller.CustomConfigConstants;
import com.emc.storageos.customconfigcontroller.impl.CustomConfigHandler;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportGroup.ExportGroupType;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.ExportPathParams;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePortGroup;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.StringMapUtil;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.locking.LockTimeoutValue;
import com.emc.storageos.locking.LockType;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.volumecontroller.BlockStorageDevice;
import com.emc.storageos.volumecontroller.impl.ControllerLockingUtil;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.HostIOLimitsParam;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportChangePortGroupCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskAddVolumeCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskChangePortGroupAddMaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskDeleteCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskRemoveInitiatorCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportOrchestrationTask;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportRemoveVolumesOnAdoptedMaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeUpdateCompleter;
import com.emc.storageos.volumecontroller.impl.smis.SmisStorageDevice;
import com.emc.storageos.volumecontroller.impl.smis.SmisUtils;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.volumecontroller.impl.utils.ObjectLocalCache;
import com.emc.storageos.volumecontroller.placement.BlockStorageScheduler;
import com.emc.storageos.vplexcontroller.VPlexControllerUtils;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowService;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Sets;

public class VmaxMaskingOrchestrator extends AbstractBasicMaskingOrchestrator {

    private static final Logger _log = LoggerFactory.getLogger(VmaxMaskingOrchestrator
            .class);

    private static final AtomicReference<BlockStorageDevice> VMAX_BLOCK_DEVICE = new
            AtomicReference<BlockStorageDevice>();
    public static final String VMAX_SMIS_DEVICE = "vmaxSmisDevice";
    public static final HashSet<String> INITIATOR_FIELDS = new HashSet<String>();
    public static final String CUSTOM_CONFIG_HANDLER = "customConfigHandler";
    private ExportWorkflowUtils _wfUtils;

    static {
        INITIATOR_FIELDS.add("clustername");
        INITIATOR_FIELDS.add("hostname");
        INITIATOR_FIELDS.add("iniport");
    }
    
    public void setExportWorkflowUtils(ExportWorkflowUtils exportWorkflowUtils) {
        _wfUtils = exportWorkflowUtils;
    }

    @Override
    public BlockStorageDevice getDevice() {
        BlockStorageDevice device = VMAX_BLOCK_DEVICE.get();
        synchronized (VMAX_BLOCK_DEVICE) {
            if (device == null) {
                device = (BlockStorageDevice)
                        ControllerServiceImpl.getBean(VMAX_SMIS_DEVICE);
                VMAX_BLOCK_DEVICE.compareAndSet(null, device);
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
            StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageURI);
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
            logExportGroup(exportGroup, storageURI);
            // When adding volumes to an export group, this is the most common scenario.
            // The export group has a set of export masks already associated with it, and we
            // simply need to determine which of those masks require the volumes and add them.
            // Exceptions to this are documented in the logic.
            if (exportGroup != null && exportGroup.getExportMasks() != null) {
                // Set up workflow steps.
                Workflow workflow = _workflowService.getNewWorkflow(
                        MaskingWorkflowEntryPoints.getInstance(), "exportGroupAddVolumes", true,
                        token);

                Collection<URI> initiatorIds = Collections2.transform(StringSetUtil.get(exportGroup.getInitiators()),
                        CommonTransformerFunctions.FCTN_STRING_TO_URI);

                if (!determineExportGroupCreateSteps(workflow, null, device, storage, exportGroup,
                        new ArrayList<URI>(initiatorIds), volumeMap, true, token)) {
                    throw DeviceControllerException.exceptions.exportGroupCreateFailed(new Exception("Export Group Add Volume Failed"));
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
                } else {
                    _log.warn("There are no initiators for export group: " + exportGroup.getLabel());
                    // Additional logic to ensure the task is closed out in the case where no workflow was really generated.
                    taskCompleter.ready(_dbClient);
                    _log.info("No volumes pushed to array because either they already exist " +
                            "or there were no initiators added to the export yet.");
                }
            }
        } catch (Exception ex) {
            _log.error("ExportGroup Orchestration failed.", ex);
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
        String previousStep = null;
        ExportOrchestrationTask taskCompleter = new ExportOrchestrationTask(exportGroupURI, token);
        StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageURI);
        ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
        logExportGroup(exportGroup, storageURI);
        // Set up workflow steps.
        Workflow workflow = _workflowService.getNewWorkflow(
                MaskingWorkflowEntryPoints.getInstance(), "exportGroupAddInitiators", true, token);
        Map<URI, List<URI>> zoneMasksToInitiatorsURIs = new HashMap<URI, List<URI>>();
        Map<URI, Map<URI, Integer>> zoneNewMasksToVolumeMap = new HashMap<URI, Map<URI, Integer>>();
        Map<URI, ExportMask> refreshedMasks = new HashMap<URI, ExportMask>();

        // Populate a map of volumes on the storage device
        List<BlockObject> blockObjects = new ArrayList<BlockObject>();
        Map<URI, Integer> volumeMap = new HashMap<URI, Integer>();
        if (exportGroup != null && exportGroup.getVolumes() != null) {
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

        InitiatorHelper initiatorHelper = new InitiatorHelper(initiatorURIs).process(exportGroup);

        boolean anyOperationsToDo = false;
        Set<URI> partialMasks = new HashSet<>();
        Map<String, Set<URI>> initiatorToExportMaskPlacementMap =
                determineInitiatorToExportMaskPlacements(exportGroup, storageURI,
                initiatorHelper.getResourceToInitiators(), device.findExportMasks(storage, initiatorHelper.getPortNames(), false),
                        initiatorHelper.getPortNameToInitiatorURI(), null, partialMasks);

        if (!initiatorToExportMaskPlacementMap.isEmpty()) {
            Map<URI, ExportMaskPolicy> policyCache = new HashMap<>();
            // The logic contained here is trying to place the initiators that were passed down in the
            // request. If we are in this path where the initiatorToExportMaskPlacementMap is not empty, then there
            // are several cases why we got here:
            //
            // 1). An ExportMask has been found that is associated with the ExportGroup and it
            // is supposed to be the container for the compute resources that we are attempting
            // to add initiators for.
            // 2). An ExportMask has been found that is on the array. It may not be associated with the
            // ExportGroup, but it is supposed to be the container for the compute resources that
            // we are attempting to add initiators for.
            // 3). An ExportMask has been found that is on the array. It may not be associated with the
            // ExportGroup, but it has the initiators that we are trying to add
            // 4). One of the above possibilities + an initiator that cannot be placed. The use-case here
            // would someone adds a new initiator for an existing host and a new host to a cluster export.
            List<URI> initiatorsToPlace = new ArrayList<URI>();
            initiatorsToPlace.addAll(initiatorURIs);

            // This loop will determine a list of volumes to update per export mask
            Map<URI, Map<URI, Integer>> existingMasksToUpdateWithNewVolumes = new HashMap<URI, Map<URI, Integer>>();
            Map<URI, Set<Initiator>> existingMasksToUpdateWithNewInitiators = new HashMap<URI, Set<Initiator>>();
            for (Map.Entry<String, Set<URI>> entry : initiatorToExportMaskPlacementMap.entrySet()) {
                URI initiatorURI = initiatorHelper.getPortNameToInitiatorURI().get(entry.getKey());
                if (initiatorURI == null || exportGroup == null) {
                    // This initiator does not exist or it is not one of the initiators passed to the function
                    continue;
                }
                Initiator initiator = _dbClient.queryObject(Initiator.class, initiatorURI);
                // Get a list of the ExportMasks that were matched to the initiator
                List<URI> exportMaskURIs = new ArrayList<URI>();
                exportMaskURIs.addAll(entry.getValue());
                List<ExportMask> masks = _dbClient.queryObject(ExportMask.class, exportMaskURIs);
                _log.info(String.format("Trying to place initiator %s", entry.getKey()));
                for (ExportMask mask : masks) {
                    // Check for NO_VIPR. If found, avoid this mask.
                    if (mask.getMaskName() != null && mask.getMaskName().toUpperCase().contains(ExportUtils.NO_VIPR)) {
                        _log.info(String.format(
                                "ExportMask %s disqualified because the name contains %s (in upper or lower case) to exclude it",
                                mask.getMaskName(), ExportUtils.NO_VIPR));
                        continue;
                    }

                    _log.info(String.format("Trying to place initiator %s in mask %s", entry.getKey(), mask.getMaskName()));
                    if (mask.getInactive() && !mask.getStorageDevice().equals(storageURI)) {
                        continue;
                    }
                    // This refresh call should be revisited, it should have been made in
                    // determineInitiatorToExportMaskPlacements or findExportMasks
                    if (!refreshedMasks.containsKey(mask.getId())) {
                        mask = device.refreshExportMask(storage, mask);
                        refreshedMasks.put(mask.getId(), mask);
                    }
                    ExportMaskPolicy policy = getExportMaskPolicy(policyCache, device, storage, mask);
                    // Check if the mask that as was found/selected for the initiator already
                    // has the initiator in it. The only time that this would be untrue is
                    // if we are attempting to add new hosts to a cluster export. In this case,
                    // the determineInitiatorToExportMaskPlacements() would have found the ExportMask for
                    // the cluster to place the initiators, but it would not have them added
                    // yet. The below logic will add the volumes necessary.
                    if (mask.hasInitiator(initiatorURI.toString())
                            && CollectionUtils.isEmpty(ExportUtils.getExportMasksSharingInitiator(_dbClient,
                                    initiatorURI, mask, exportMaskURIs))) {
                        _log.info(String.format("mask %s has initiator %s", mask.getMaskName(),
                                initiator.getInitiatorPort()));
                        // Loop through all the block objects that have been exported
                        // to the storage system and place only those that are not
                        // already in the masks to the placement list
                        for (BlockObject blockObject : blockObjects) {
                            // Determine if the block object belongs in this mask or not, given the mask, mask policy,
                            // blockObject properties, and so on.
                            if (!mask.hasExistingVolume(blockObject.getWWN()) && !mask.hasVolume(blockObject.getId())) {
                                String volumePolicyName = ControllerUtils.getAutoTieringPolicyName(blockObject.getId(), _dbClient);
                                if (((volumePolicyName == null || volumePolicyName.equalsIgnoreCase(Constants.NONE.toString())) && (policy.tierPolicies == null || policy.tierPolicies
                                        .isEmpty()))
                                        ||
                                        ((volumePolicyName != null && policy.tierPolicies != null && policy.tierPolicies.size() == 1 && policy.tierPolicies
                                                .contains(volumePolicyName)))) {
                                    _log.info(String.format("mask doesn't have volume %s yet, need to add it", blockObject.getId()));
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
                            } else {
                                _log.info(String.format("not adding volume %s to mask %s", blockObject.getId(), mask.getMaskName()));
                            }
                        }
                        // The initiator has been placed - it is in an already existing export
                        // for which case, we may just have to add volumes to it
                        initiatorsToPlace.remove(initiatorURI);
                    } else {
                        Set<URI> existingInitiatorIds = ExportMaskUtils.getAllInitiatorsForExportMask(_dbClient, mask);
                        if (existingInitiatorIds.isEmpty()) {
                            _log.info(String.format(
                                    "not adding initiator to %s mask %s because there are no initiators associated with this mask",
                                    initiatorURI, mask.getMaskName()));
                        }

                        // This mask does not contain the initiator, but it may not belong to the same compute resource.
                        for (URI existingInitiatorId : existingInitiatorIds) {
                            Initiator existingInitiator = _dbClient.queryObject(Initiator.class, existingInitiatorId);
                            if (existingInitiator == null) {
                                _log.warn(String.format(
                                        "Initiator %s was found to be associated with ExportMask %s, but no longer exists in the DB",
                                        existingInitiatorId, mask.getId()));
                                continue;
                            }
                            if ((existingInitiator.getHost() != null && existingInitiator.getHost().equals(initiator.getHost()))
                                    ||
                                    (existingInitiator.getClusterName() != null && existingInitiator.getClusterName().equals(
                                            initiator.getClusterName()))) {

                                // We don't want to add this initiator to the mask in the condition where:
                                // 1. The export group type is cluster, and
                                // 2. The export mask is a "partial" mask, meaning it contains a single host, and
                                // 3. The host of this initiator is not the host associated with the mask.
                                // Place the initiator in this ExportMask.
                                if (exportGroup.forCluster() && !policy.isCascadedIG() &&
                                        ((existingInitiator.getHost() == null || !existingInitiator.getHost().equals(initiator.getHost())))) {
                                    _log.info(String.format(
                                            "not adding initiator to %s mask %s because it is likely part of another mask in the cluster",
                                            initiatorURI, mask.getMaskName()));
                                    continue;
                                }

                                Set<Initiator> existingMaskInitiators = existingMasksToUpdateWithNewInitiators.get(mask.getId());
                                if (existingMaskInitiators == null) {
                                    existingMaskInitiators = new HashSet<Initiator>();
                                    existingMasksToUpdateWithNewInitiators.put(mask.getId(), existingMaskInitiators);
                                }
                                _log.info(String.format(
                                        "adding initiator to %s mask %s because it was found to be in the same compute resource",
                                        initiatorURI, mask.getMaskName()));
                                existingMaskInitiators.add(initiator);
                                // The initiator has been placed - it is not in the export, we will have to
                                // add it to the mask
                                initiatorsToPlace.remove(initiatorURI);
                            } else {
                                _log.info(String.format(
                                        "not adding initiator to %s mask %s because it doesn't belong to the same compute resource",
                                        existingInitiator.getId(), mask.getMaskName()));
                            }
                        }
                    }

                    updateZoningMap(exportGroup, mask, true);
                }
            }

            // The initiatorsToPlace was used in the foreach initiator loop to see
            // which initiators already exist in a mask. If it is non-empty,
            // then it means there are initiators that are new,
            // so let's add them to the main tracker
            if (!initiatorsToPlace.isEmpty()) {
                Map<String, List<URI>> computeResourceToInitiators =
                        mapInitiatorsToComputeResource(exportGroup, initiatorsToPlace);
                for (Map.Entry<String, List<URI>> resourceEntry : computeResourceToInitiators.entrySet()) {
                    String computeKey = resourceEntry.getKey();
                    List<URI> computeInitiatorURIs = resourceEntry.getValue();
                    _log.info(String.format("New export masks for %s", computeKey));
                    GenExportMaskCreateWorkflowResult result = generateExportMaskCreateWorkflow(workflow, previousStep, storage,
                            exportGroup, computeInitiatorURIs, volumeMap, token);
                    previousStep = result.getStepId();
                    zoneNewMasksToVolumeMap.put(result.getMaskURI(), volumeMap);
                    anyOperationsToDo = true;
                }
            }

            _log.info(String.format("existingMasksToUpdateWithNewVolumes.size = %d",
                    existingMasksToUpdateWithNewVolumes.size()));

            // At this point we have a mapping of all the masks that we need to update with new volumes
            for (Map.Entry<URI, Map<URI, Integer>> entry : existingMasksToUpdateWithNewVolumes
                    .entrySet()) {
                ExportMask mask = _dbClient.queryObject(ExportMask.class, entry.getKey());
                Map<URI, Integer> volumesToAdd = entry.getValue();
                _log.info(String.format("adding these volumes %s to mask %s",
                        Joiner.on(",").join(volumesToAdd.keySet()), mask.getMaskName()));
                List<URI> volumeURIs = new ArrayList<URI>();
                volumeURIs.addAll(volumesToAdd.keySet());
                List<ExportMask> masks = new ArrayList<ExportMask>();
                masks.add(mask);
                previousStep = generateZoningAddVolumesWorkflow(workflow, previousStep,
                        exportGroup, masks, volumeURIs);
                previousStep = generateExportMaskAddVolumesWorkflow(workflow, previousStep, storage, exportGroup,
                        mask, volumesToAdd, null);
                anyOperationsToDo = true;
            }

            // At this point we have a mapping of all the masks that we need to update with new initiators
            for (Map.Entry<URI, Set<Initiator>> entry : existingMasksToUpdateWithNewInitiators.entrySet()) {
                ExportMask mask = _dbClient.queryObject(ExportMask.class, entry.getKey());
                Set<Initiator> initiatorsToAdd = entry.getValue();
                List<URI> initiatorsURIs = new ArrayList<URI>();
                for (Initiator initiator : initiatorsToAdd) {
                    initiatorsURIs.add(initiator.getId());
                }
                _log.info(String.format("adding these initiators %s to mask %s",
                        Joiner.on(",").join(initiatorsURIs), mask.getMaskName()));
                Map<URI, List<URI>> maskToInitiatorsMap = new HashMap<URI, List<URI>>();
                maskToInitiatorsMap.put(mask.getId(), initiatorsURIs);
                previousStep = generateExportMaskAddInitiatorsWorkflow(workflow, previousStep, storage, exportGroup, mask,
                        initiatorsURIs, null, token);
                previousStep = generateZoningAddInitiatorsWorkflow(workflow, previousStep,
                        exportGroup, maskToInitiatorsMap);
                anyOperationsToDo = true;
            }

        } else {
            _log.info("There are no masks for this export. Need to create anew.");
            // Create two steps, one for Zoning, one for the ExportGroup actions.
            // This step is for zoning. It is not specific to a single NetworkSystem,
            // as it will look at all the initiators and targets and compute the
            // zones required (which might be on multiple NetworkSystems.)
            for (Map.Entry<String, List<URI>> resourceEntry : initiatorHelper.getResourceToInitiators().entrySet()) {
                String computeKey = resourceEntry.getKey();
                List<URI> computeInitiatorURIs = resourceEntry.getValue();
                _log.info(String.format("New export masks for %s", computeKey));
                GenExportMaskCreateWorkflowResult result = generateExportMaskCreateWorkflow(workflow, previousStep, storage,
                        exportGroup, computeInitiatorURIs, volumeMap, token);
                zoneNewMasksToVolumeMap.put(result.getMaskURI(), volumeMap);
                previousStep = result.getStepId();
                anyOperationsToDo = true;
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
                previousStep = generateZoningCreateWorkflow(workflow, previousStep, exportGroup, exportMaskList, overallVolumeMap);
            }
            if (!zoneMasksToInitiatorsURIs.isEmpty()) {
                previousStep = generateZoningAddInitiatorsWorkflow(workflow, previousStep, exportGroup, zoneMasksToInitiatorsURIs);
            }
            String successMessage = String.format(
                    "Successfully exported to initiators on StorageArray %s", storage.getLabel());
            workflow.executePlan(taskCompleter, successMessage);
        } else {
            taskCompleter.ready(_dbClient);
        }
    }


    @Override
    public void exportGroupRemoveInitiators(URI storageURI, URI exportGroupURI,
            List<URI> initiatorURIs, String token) throws Exception {
        BlockStorageDevice device = getDevice();
        ExportOrchestrationTask taskCompleter = new ExportOrchestrationTask(exportGroupURI, token);
        StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageURI);
        ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
        StringBuffer errorMessage = new StringBuffer();
        logExportGroup(exportGroup, storageURI);
        
        try {
            // Set up workflow steps.
            Workflow workflow = _workflowService.getNewWorkflow(
                    MaskingWorkflowEntryPoints.getInstance(), "exportGroupRemoveInitiators", true,
                    token);
            Initiator firstInitiator = _dbClient.queryObject(Initiator.class, initiatorURIs.get(0));
            // No need to validate the orchestrator level validation for vplex/rp. Hence ignoring validation for vplex/rp initiators.
            boolean isValidationNeeded = validatorConfig.isValidationEnabled() && !VPlexControllerUtils.isVplexInitiator(firstInitiator, _dbClient)
                    && !ExportUtils.checkIfInitiatorsForRP(Arrays.asList(firstInitiator));
            _log.info("Orchestration level validation needed : {}", isValidationNeeded);
            InitiatorHelper initiatorHelper = new InitiatorHelper(initiatorURIs).process(exportGroup);

            // Populate a map of volumes on the storage device associated with this ExportGroup
            List<BlockObject> blockObjects = new ArrayList<BlockObject>();
            if (exportGroup != null) {
                for (Map.Entry<String, String> entry : exportGroup.getVolumes().entrySet()) {
                    URI boURI = URI.create(entry.getKey());
                    BlockObject bo = BlockObject.fetch(_dbClient, boURI);
                    if (bo.getStorageController().equals(storageURI)) {
                        blockObjects.add(bo);
                    }
                }
            }

            Map<URI, Boolean> initiatorIsPartOfFullListFlags = flagInitiatorsThatArePartOfAFullList(exportGroup, initiatorURIs);

            List<String> initiatorNames = new ArrayList<String>();

            for (URI initiatorURI : initiatorURIs) {
                Initiator initiator = _dbClient.queryObject(Initiator.class, initiatorURI);
                String normalizedName = Initiator.normalizePort(initiator.getInitiatorPort());
                initiatorNames.add(normalizedName);
            }
            _log.info("Normalized initiator names :{}", initiatorNames);
            device.findExportMasks(storage, initiatorNames, false);
            boolean anyOperationsToDo = false;
            Map<URI, ExportMask> refreshedMasks = new HashMap<URI, ExportMask>();
            if (exportGroup != null && exportGroup.getExportMasks() != null) {
                // There were some exports out there that already have some or all of the
                // initiators that we are attempting to remove. We need to only
                // remove the volumes that the user added to these masks
                Map<String, Set<URI>> matchingExportMaskURIs = getInitiatorToExportMaskMap(exportGroup);

                // This loop will determine a list of volumes to update per export mask
                Map<URI, List<URI>> existingMasksToRemoveInitiator = new HashMap<URI, List<URI>>();
                Map<URI, List<URI>> existingMasksToRemoveVolumes = new HashMap<URI, List<URI>>();
                for (Map.Entry<String, Set<URI>> entry : matchingExportMaskURIs.entrySet()) {
                    URI initiatorURI = initiatorHelper.getPortNameToInitiatorURI().get(entry.getKey());
                    if (initiatorURI == null || !initiatorURIs.contains(initiatorURI)) {
                        // Entry key points to an initiator that was not passed in the remove request
                        continue;
                    }
                    Initiator initiator = _dbClient.queryObject(Initiator.class, initiatorURI);

                    // Get a list of the ExportMasks that were matched to the initiator
                    // go through the initiators and figure out the proper initiator and volume ramifications
                    // to the existing masks.
                    List<URI> exportMaskURIs = new ArrayList<URI>();
                    exportMaskURIs.addAll(entry.getValue());
                    List<ExportMask> masks = _dbClient.queryObject(ExportMask.class, exportMaskURIs);
                    _log.info(String.format("initiator %s masks {%s}", initiator.getInitiatorPort(),
                            Joiner.on(',').join(exportMaskURIs)));
                    for (ExportMask mask : masks) {
                        if (mask == null || mask.getInactive() || !mask.getStorageDevice().equals(storageURI)) {
                            continue;
                        }

                        if (!refreshedMasks.containsKey(mask.getId())) {
                            // refresh the export mask always
                            // COP-34971 Pass the deleted initiator list to underlying method refreshZoningMap and refreshFCZoneReferences()
                            // of NetworkDeviceController
                            if (device instanceof SmisStorageDevice) {
                                mask = ((SmisStorageDevice) device).refreshExportMask(storage, mask, initiatorNames);
                            } else {
                                mask = device.refreshExportMask(storage, mask);
                            }
                            refreshedMasks.put(mask.getId(), mask);
                        }

                        _log.info(String.format("mask %s has initiator %s", mask.getMaskName(),
                                initiator.getInitiatorPort()));

                        
                        /**
                         * If user asked to remove Host from Cluster
                         * 1. Check if the export mask is shared across other export Groups, if not remove the host.
                         * 2. If shared, check whether all the initiators of host is being asked to remove
                         * 3. If yes, check if atleast one of the other shared export Group is EXCLUSIVE
                         * 4. If yes, then remove the shared volumes
                         * 
                         * In all other cases, remove the initiators.
                         */
                        
                        List<ExportGroup> otherExportGroups = ExportUtils.getOtherExportGroups(exportGroup, mask, _dbClient);
                        if (!otherExportGroups.isEmpty() && initiatorIsPartOfFullListFlags.get(initiatorURI) &&
                                ExportUtils.exportMaskHasBothExclusiveAndSharedVolumes(exportGroup, otherExportGroups, mask)) {
                            
                            if (!exportGroup.forInitiator()) {
                                List<URI> removeVolumesList = existingMasksToRemoveVolumes.get(mask.getId());
                                if (removeVolumesList == null) {
                                    removeVolumesList = new ArrayList<URI>();
                                    existingMasksToRemoveVolumes.put(mask.getId(),
                                            removeVolumesList);
                                }
                                for (String volumeIdStr : exportGroup.getVolumes().keySet()) {
                                    URI egVolumeID = URI.create(volumeIdStr);
                                    if (mask.getUserAddedVolumes().containsValue(volumeIdStr) && 
                                            !removeVolumesList.contains(egVolumeID)) {
                                        removeVolumesList.add(egVolumeID);
                                    }
                                }
                               
                            } else {
                                // Just a reminder to the world in the case where Initiator is used in this odd situation.
                                _log.info(
                                        "Removing volumes from an Initiator type export group as part of an initiator removal is not supported.");
                            }
                        } else {
                            _log.info(
                                    String.format("We can remove initiator %s from mask %s", initiator.getInitiatorPort(),
                                            mask.getMaskName()));
                            List<URI> initiators = existingMasksToRemoveInitiator.get(mask.getId());
                            if (initiators == null) {
                                initiators = new ArrayList<URI>();
                                existingMasksToRemoveInitiator.put(mask.getId(), initiators);
                            }
                            if (!initiators.contains(initiator.getId())) {
                                initiators.add(initiator.getId());
                            }
                        }

                       
                    }
                }
                Set<URI> masksGettingRemoved = new HashSet<URI>();

                // In this loop we are trying to remove those initiators that exist
                // on a mask that ViPR created.
                String previousStep = null;
                for (Map.Entry<URI, List<URI>> entry : existingMasksToRemoveInitiator.entrySet()) {
                    ExportMask mask = _dbClient.queryObject(ExportMask.class, entry.getKey());
                    List<URI> initiatorsToRemove = entry.getValue();
                    List<URI> initiatorsToRemoveOnStorage = new ArrayList<URI>();
                    for (URI initiatorURI : initiatorsToRemove) {
                        Initiator initiator = _dbClient.queryObject(Initiator.class, initiatorURI);
                        // COP-28729 - We can allow remove initiator or host if the shared mask doesn't have any existing volumes.
                        // Shared masks will have at least one unmanaged volume.
                        String err = ExportUtils.getExportMasksSharingInitiatorAndHasUnManagedVolumes(_dbClient, initiator, mask,
                                existingMasksToRemoveInitiator.keySet());
                        if (err != null) {
                            errorMessage.append(err);
                        }
                        initiatorsToRemoveOnStorage.add(initiatorURI);
                    }
                    // CTRL-8846 fix : Compare against all the initiators
                    Set<String> allMaskInitiators = ExportUtils.getExportMaskAllInitiatorPorts(mask, _dbClient);
                    List<Initiator> removableInitiatorList = _dbClient.queryObject(Initiator.class, initiatorsToRemove);
                    List<String> portNames = new ArrayList<>(
                            Collections2.transform(removableInitiatorList, CommonTransformerFunctions.fctnInitiatorToPortName()));
                    allMaskInitiators.removeAll(portNames);
                    if (allMaskInitiators.isEmpty()) {
                        masksGettingRemoved.add(mask.getId());
                        // For this case, we are attempting to remove all the
                        // initiators in the mask. This means that we will have to delete the
                        // exportGroup
                        _log.info(String.format("mask %s has removed all "
                                + "initiators, mask will be deleted from the array.. ",
                                mask.getMaskName()));
                        List<ExportMask> exportMasks = new ArrayList<ExportMask>();
                        exportMasks.add(mask);
                        previousStep = generateExportMaskDeleteWorkflow(workflow, previousStep, storage, exportGroup,
                                mask, getExpectedVolumes(mask), getExpectedInitiators(mask), null);
                        previousStep = generateZoningDeleteWorkflow(workflow, previousStep, exportGroup,
                                exportMasks);
                        anyOperationsToDo = true;
                    } else {
                        _log.info(String.format("mask %s - going to remove the "
                                + "following initiators %s. ", mask.getMaskName(),
                                Joiner.on(',').join(initiatorsToRemove)));
                        Map<URI, List<URI>> maskToInitiatorsMap = new HashMap<URI, List<URI>>();
                        maskToInitiatorsMap.put(mask.getId(), initiatorsToRemove);

                        ExportMaskRemoveInitiatorCompleter exportTaskCompleter = new ExportMaskRemoveInitiatorCompleter(exportGroupURI,
                                mask.getId(), initiatorsToRemove, null);
                        previousStep = generateExportMaskRemoveInitiatorsWorkflow(workflow, previousStep, storage,
                                exportGroup, mask, getExpectedVolumes(mask), initiatorsToRemoveOnStorage, true, exportTaskCompleter);
                        previousStep = generateZoningRemoveInitiatorsWorkflow(workflow, previousStep, exportGroup,
                                maskToInitiatorsMap);
                        anyOperationsToDo = true;
                    }
                }

                // In this loop we are trying to remove volumes from masks that
                // ViPR did not create. We have no control over the initiators defined in
                // these masks. We will be removing only those volumes that are applicable
                // for the storage array and ExportGroup.
                for (Map.Entry<URI, List<URI>> entry : existingMasksToRemoveVolumes.entrySet()) {
                    if (masksGettingRemoved.contains(entry.getKey())) {
                        _log.info("Mask {} is getting removed, no need to remove volumes from it",
                                entry.getKey().toString());
                        continue;
                    }

                    ExportMask mask = _dbClient.queryObject(ExportMask.class, entry.getKey());
                    List<URI> volumesToRemove = entry.getValue();
                    List<URI> initiatorsToRemove = existingMasksToRemoveInitiator.get(mask.getId());
                    if (initiatorsToRemove != null) {
                        Set<String> initiatorsInExportMask = ExportUtils.getExportMaskAllInitiatorPorts(mask, _dbClient);
                        List<Initiator> removableInitiatorList = _dbClient.queryObject(Initiator.class, initiatorsToRemove);
                        List<String> portNames = new ArrayList<>(
                                Collections2.transform(removableInitiatorList, CommonTransformerFunctions.fctnInitiatorToPortName()));
                        initiatorsInExportMask.removeAll(portNames);
                        if (!initiatorsInExportMask.isEmpty()) {
                            // There are still some initiators in this ExportMask
                            _log.info(String.format("ExportMask %s would have remaining initiators {%s} that require access to {%s}. " +
                                    "Not going to remove any of the volumes",
                                    mask.getMaskName(), Joiner.on(',').join(initiatorsInExportMask),
                                    Joiner.on(", ").join(volumesToRemove)));
                            continue;
                        }
                    }

                    Collection<String> volumesToRemoveURIStrings = Collections2.transform(volumesToRemove,
                            CommonTransformerFunctions.FCTN_URI_TO_STRING);
                    List<String> exportMaskVolumeURIStrings = new ArrayList<String>(mask.getVolumes().keySet());
                    exportMaskVolumeURIStrings.removeAll(volumesToRemoveURIStrings);
                    
                    boolean hasExistingVolumes = !CollectionUtils.isEmpty(mask.getExistingVolumes()); 
                    List<? extends BlockObject> boList = BlockObject.fetchAll(_dbClient, volumesToRemove);
                    if (!hasExistingVolumes && exportMaskVolumeURIStrings.isEmpty()) {
                        _log.info(
                                String.format("All the volumes (%s) from mask %s will be removed, so will have to remove the whole mask. ",
                                        Joiner.on(", ").join(volumesToRemove), mask.getMaskName()));
                        errorMessage.append(
                                String.format(
                                        "Mask %s would have deleted from array ",
                                        mask.forDisplay()));
                        // Order matters! Above this would be any remove initiators that would impact other masking views.
                        // Be sure to always remove anything inside the mask before removing the mask itself.
                        previousStep = generateExportMaskDeleteWorkflow(workflow, previousStep, storage, exportGroup, mask,
                                getExpectedVolumes(mask), getExpectedInitiators(mask), null);
                        previousStep = generateZoningDeleteWorkflow(workflow, previousStep, exportGroup, Arrays.asList(mask));
                        anyOperationsToDo = true;
                    } else {
                        ExportTaskCompleter completer = new ExportRemoveVolumesOnAdoptedMaskCompleter(
                                exportGroupURI, mask.getId(), volumesToRemove, token);
                        _log.info(String.format("A subset of volumes will be removed from mask %s: %s. ",
                                mask.getMaskName(), Joiner.on(",").join(volumesToRemove)));
                        errorMessage.append(String.format("A subset of volumes will be removed from mask %s: %s. ",
                                mask.forDisplay(), Joiner.on(", ").join(
                                        Collections2.transform(boList, CommonTransformerFunctions.fctnDataObjectToForDisplay()))));
                        List<ExportMask> masks = new ArrayList<ExportMask>();
                        masks.add(mask);

                        previousStep = generateExportMaskRemoveVolumesWorkflow(workflow, previousStep, storage, exportGroup,
                                mask, volumesToRemove, getExpectedInitiators(mask), completer);
                        previousStep = generateZoningRemoveVolumesWorkflow(workflow, previousStep,
                                exportGroup, masks, volumesToRemove);
                        anyOperationsToDo = true;

                    }
                }
                
            }
            _log.warn("Error Message {}", errorMessage);

            if (isValidationNeeded && StringUtils.hasText(errorMessage)) {
                throw DeviceControllerException.exceptions.removeInitiatorValidationError(Joiner.on(", ").join(initiatorNames),
                        storage.getLabel(),
                        errorMessage.toString());
            }

            if (anyOperationsToDo) {
                String successMessage = String.format(
                        "Successfully removed exports for initiators on StorageArray %s",
                        storage.getLabel());
                workflow.executePlan(taskCompleter, successMessage);
            } else {
                taskCompleter.ready(_dbClient);
            }
        } catch (Exception ex) {
            _log.error("ExportGroup remove initiator Orchestration failed.", ex);
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
        if (ExportGroupType.Cluster.name().equals(exportType)) {
            return CustomConfigConstants.VMAX_CLUSTER_MASKING_VIEW_MASK_NAME;
        } else {
            return CustomConfigConstants.VMAX_HOST_MASKING_VIEW_MASK_NAME;
        }
    }

    @Override
    public void findAndUpdateFreeHLUsForClusterExport(StorageSystem storage, ExportGroup exportGroup, List<URI> initiatorURIs,
            Map<URI, Integer> volumeMap) {
        findUpdateFreeHLUsForClusterExport(storage, exportGroup, initiatorURIs, volumeMap);
    }
      
    /**
     * This method find the ultimate usable hlus for HPUX host on VMAX system.
     *      
     *
     * @param minHlu
     * @param freeHLUs
     * @param userHlus
     * @param finalUsableHlus
     * @param errMsg
     */
    private boolean verifyAndGetValidHlus(Integer minHlu, Set<Integer> freeHLUs, Set<Integer> userHlus,
            Set<Integer> finalUsableHlus, StringBuffer errMsg) {
        Set<Integer> sortedFreeHLUs = new TreeSet<Integer>(freeHLUs);
        Iterator<Integer> freeHLUItr = sortedFreeHLUs.iterator();
        // Remove free hlus which are less than user provided least hlu
        while(freeHLUItr.hasNext()) {
            Integer hlu = freeHLUItr.next();
            if(hlu < minHlu) {
                freeHLUItr.remove(); 
            }else {
                break;
            }
        }

        Set<Integer> commonHluSet = Sets.intersection(sortedFreeHLUs, new TreeSet(userHlus));
        finalUsableHlus.addAll(commonHluSet);
        if (commonHluSet.size() == userHlus.size()) {
            _log.info("All user provided hlus are available on system");
            return true;
        } else {
            // Remove common hlus from free list
            Set<Integer> remainingFreeHlus = Sets.difference(sortedFreeHLUs, commonHluSet);
            int stillNeed = userHlus.size() - commonHluSet.size();
            if(remainingFreeHlus.size() < stillNeed) {
                String msg = String.format("No more free HLU available for %d volumes", stillNeed - remainingFreeHlus.size());
                _log.warn(msg);
                errMsg.append(msg);
                return false;
            } else {
                Set<Integer> remSortedFreeHlus = new TreeSet<Integer>(remainingFreeHlus);
                for ( Integer hlu : remSortedFreeHlus) {
                    finalUsableHlus.add(hlu);
                    if(--stillNeed <= 0) {
                        break;
                    }
                }
            }
        }
        return true;
    }


    /**
     * This method is different than findAndUpdateFreeHLUsForClusterExport(). It is used for updating the hlu when there
     * is one hlu being allocated as per user request and others are being added by client but may be invalid. This will
     * fail at device level so we need to update the hlus with appropriate HLU.
     *
     * 
     * Finds the next available HLU for cluster export by querying the cluster hosts
     * used HLUs and updates the volumeHLU map with free HLUs.
     *
     * @param storage
     *            the storage system
     * @param exportGroup
     *            the export group
     * @param initiatorURIs
     *            the initiator uris
     * @param volumeMap
     *            the volume HLU map
     */
    public void validateAndUpdateFreeHLUsForHPUXClusterExport(StorageSystem storage, ExportGroup exportGroup,
            List<URI> initiatorURIs, Map<URI, Integer> volumeMap) {
        if (!exportGroup.checkInternalFlags(Flag.INTERNAL_OBJECT) && exportGroup.forCluster()
                && !volumeMap.values().contains(ExportGroup.LUN_UNASSIGNED)
                && ExportUtils.systemSupportsConsistentHLUGeneration(storage)) {

            _log.info("Validate and update free HLUs for Cluster Export START..");
            /**
             * Group the initiators by Host. For each Host, call device.findHLUsForInitiators() to get used HLUs.
             * Add all hosts HLUs to a Set.
             * Get the maximum allowed HLU for the storage array.
             * Calculate the free lowest available HLUs.
             * Update the new values in the VolumeHLU Map.
             */
            Set<Integer> usedHlus = findHLUsForClusterHosts(storage, exportGroup, initiatorURIs);
            Integer maxHLU = ExportUtils.getMaximumAllowedHLU(storage);
            Set<Integer> freeHLUs = ExportUtils.calculateFreeHLUs(usedHlus, maxHLU);
            freeHLUs = ExportUtils.filterHLUforHPUX(freeHLUs);

            // Get least hlu value from user input!!!
            List<Integer> hlus = new ArrayList<Integer>();
            hlus.addAll(volumeMap.values());
            Collections.sort(hlus);
            Integer minHlu = hlus.get(0);

            // Verify the least hlu number exists in free hlu list
            // otherwise, throw an error
            if(!freeHLUs.contains(minHlu)) {
                String errorMsg = String.format(("HLU: %d provided for HPUX host" + 
                        " does not exist on storage system"), minHlu);
                _log.error(errorMsg);
                throw DeviceControllerException.exceptions.minHluDoesNotAvailableOnSystem(errorMsg);
            }

            StringBuffer errMsg = new StringBuffer();
            Set<Integer>  userInputHlus = new HashSet<Integer>();
            userInputHlus.addAll(volumeMap.values());
            Set<Integer> finalUsableHlus = new HashSet<Integer>();
            if(!verifyAndGetValidHlus(minHlu, freeHLUs, userInputHlus,
                    finalUsableHlus, errMsg)) {
                _log.error(errMsg.toString());
                throw DeviceControllerException.exceptions.minHluDoesNotAvailableOnSystem(errMsg.toString());
            }

            Iterator<Integer> validHLUItr = finalUsableHlus.iterator();
            for (Entry<URI, Integer> entry : volumeMap.entrySet()) {
                Integer hlu = entry.getValue();
                if (hlu != ExportGroup.LUN_UNASSIGNED) {
                    _log.info("HLU {} would update to new avaliable HLU", hlu);
                    if (validHLUItr.hasNext()) {
                        entry.setValue(validHLUItr.next());
                    } else {
                        String detailMsg = String.format("Requested volumes: {%s}, free HLUs available: {%s}",
                                Joiner.on(',').join(volumeMap.keySet()), finalUsableHlus);
                        _log.warn("No more free HLU available on array to assign. {}", detailMsg);
                        throw DeviceControllerException.exceptions.volumeExportReachedMaximumHlu(detailMsg);
                    }

                }
            }

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
     * @param zoningStepNeeded - Determines whether zone step is needed
     * @param token - Identifier for the operation
     * @throws Exception
     */
    @Override
    public boolean determineExportGroupCreateSteps(Workflow workflow, String previousStep,
            BlockStorageDevice device, StorageSystem storage, ExportGroup exportGroup,
            List<URI> initiatorURIs, Map<URI, Integer> volumeMap, boolean zoningStepNeeded, String token) throws Exception {
        // If we didn't create any workflows by the end of this method, we can return an appropriate exception (instead of the Task just
        // hanging)
        boolean flowCreated = false;

        InitiatorHelper initiatorHelper = new InitiatorHelper(initiatorURIs).process(exportGroup);

        // Find the qualifying export masks that are associated with any or all the ports in
        // portNames. We will have to do processing differently based on whether
        // or there is an existing ExportMasks.
        //
        // In the case of clusters, we try to find the export mask that contains a subset of initiators
        // of the cluster, so we can build onto it.
        Set<URI> partialMasks = new HashSet<>();
        /**
         * For Cluster exports, we will not reuse any partial masking views. Masking view will only be reused if all the required cluster
         * initiators are available in the existing masking view. This is to simplify the existing design.
         * - If there are existing masking views already available, then we will not reuse it.
         * - If there are masking views with all required cluster initiators, we will reuse it.
         * - If there are masking views which has more than one Host in the Cluster but not all the hosts are part of it, then don't reuse.
         * - If there are existing masking views with all the hosts in the Cluster, but few of the hosts doesn't contain all ViPR discovered
         * initiators then don't reuse. Always try to create a new masking view for Cluster.
         * 
         * Btw we consider only the host or cluster initiators connected to the network to be part of the given masking view.
         * If ViPR discovered X initiators in CLuster and only X-n are connected to network,
         * then we look for masking view with X-N initiators not X. Later during export the remaining initiators will be added to IG.
         * The existing IG can be one single IG with more than one host or it could be IG per host with missing initiators.
         * If X initiators are already available in the view, then we try to create a new masking view by reusing the IG.
         * During reuse if the masking view creation fails with Initiator-port is already available, then user has to modify the existing
         * initiator Group.
         */
        Map<String, Set<URI>> matchingMasks = device.findExportMasks(storage, initiatorHelper.getPortNames(), exportGroup.forCluster());
        Map<String, List<URI>> initiatorToComputeResourceMap =   initiatorHelper.getResourceToInitiators();
        
        Map<String, Set<URI>> initiatorToExportMaskPlacementMap = determineInitiatorToExportMaskPlacements(exportGroup, storage.getId(),
                initiatorToComputeResourceMap, matchingMasks,
                initiatorHelper.getPortNameToInitiatorURI(), volumeMap.keySet(), partialMasks);

        /**
         * COP-28674: During Vblock boot volume export, if existing masking views are found then check for existing volumes
         * If found throw exception. This condition is valid only for boot volume vblock export.
         */
        if (exportGroup.forHost() && ExportMaskUtils.isVblockHost(initiatorURIs, _dbClient) && ExportMaskUtils.isBootVolume(_dbClient, volumeMap)) {
            _log.info("VBlock boot volume Export: Validating the storage system {} to find existing masking views",
                    storage.getNativeGuid());
            if (CollectionUtils.isEmpty(matchingMasks)) {
                _log.info("No existing masking views found, passed validation..");
            } else {
                List<String> maskNames = new ArrayList<String>();
                for (Entry<String, Set<URI>> maskEntry : matchingMasks.entrySet()) {
                    List<ExportMask> masks = _dbClient.queryObject(ExportMask.class, maskEntry.getValue());
                    if (!CollectionUtils.isEmpty(masks)) {
                        for (ExportMask mask : masks) {
                            maskNames.add(mask.getMaskName());
                        }
                    }
                }
                
                Set<String> computeResourceSet = initiatorToComputeResourceMap.keySet();
                ExportOrchestrationTask completer = new ExportOrchestrationTask(exportGroup.getId(), token);
                ServiceError serviceError = DeviceControllerException.errors.existingMaskFoundDuringBootVolumeExport(
                        Joiner.on(",").join(maskNames), computeResourceSet.iterator().next());
                completer.error(_dbClient, serviceError);
                return false;
            }
        } else {
            _log.info("VBlock Boot volume Export Validation : Skipping");
        }

        /**
         * To support multiple export for VMAX3 volumes with Host IO Limit, same Storage Group and
         * Port Group should be used to create a new masking view. Re-using same storage group will
         * lead to problems as it could have additional volumes. Also reusing a child storage group
         * in a masking view to another masking view is not supported.
         */
        if (storage.checkIfVmax3() && ExportUtils.checkIfvPoolHasHostIOLimitSet(_dbClient, volumeMap)) {
            _log.info("Volumes have Host IO Limit set in virtual pools. Validating for multiple export..");
            Map<String, List<URI>> storageGroupToVolumes =
                    getDevice().groupVolumesByStorageGroupWithHostIOLimit(storage, volumeMap.keySet());
            if (!storageGroupToVolumes.isEmpty()) {
                ExportOrchestrationTask completer = new ExportOrchestrationTask(exportGroup.getId(), token);
                ServiceError serviceError = DeviceControllerException.errors.cannotMultiExportVolumesWithHostIOLimit(
                        Joiner.on(",").join(storageGroupToVolumes.keySet()),
                        Joiner.on(",").join(storageGroupToVolumes.values()));
                completer.error(_dbClient, serviceError);
                return false;
            }
        }

        List<Initiator> initiatorList = _dbClient.queryObject(Initiator.class, Sets.newHashSet(initiatorURIs));

        boolean hasHPUXHost = ExportUtils.hasInitiatorHPUX(initiatorList, _dbClient);

        if(hasHPUXHost && storage.getSystemType().equals(StorageSystem.Type.vmax.toString())) {
            _log.info(
                    "The inititators list is of HPUX host, hence will find and update free HLUs if there is one hlu that is been allocated as per user request.");
            validateAndUpdateFreeHLUsForHPUXClusterExport(storage, exportGroup, initiatorURIs, volumeMap);
        } else {
            _log.info("The inititators list is not of HPUX host, hence will find and update free HLUs");
            findAndUpdateFreeHLUsForClusterExport(storage, exportGroup, initiatorURIs, volumeMap);
        }
        
        /**
         * If export Group cluster. run algorithm to discard the matching masks based on the below
         * 1. If any of the mask has cascaded IG, use the mask and discard others.
         */

        // If we didn't find any export masks for any compute resources, then it's a total loss, and we need to
        // create new masks for each compute resource.
        //
        // TODO: I'm guessing this logic isn't necessary and the "else" statement below will take care of this situation
        // as well. Certainly not as clearly as this will, but regardless.
        if (initiatorToExportMaskPlacementMap.isEmpty()) {
            _log.info(String.format("No existing mask found w/ initiators { %s }", Joiner.on(",")
                    .join(initiatorHelper.getPortNames())));
            if (!initiatorURIs.isEmpty()) {
                Map<String, List<URI>> computeResourceToInitiators =
                        mapInitiatorsToComputeResource(exportGroup, initiatorURIs);
                for (Map.Entry<String, List<URI>> resourceEntry : computeResourceToInitiators.entrySet()) {
                    String computeKey = resourceEntry.getKey();
                    List<URI> computeInitiatorURIs = resourceEntry.getValue();
                    _log.info(String.format("New export masks for %s", computeKey));
                    GenExportMaskCreateWorkflowResult result = generateExportMaskCreateWorkflow(workflow, previousStep, storage,
                            exportGroup, computeInitiatorURIs, volumeMap, token);
                    previousStep = result.getStepId();
                    flowCreated = true;
                }
            }
        } else {
            Map<URI, ExportMaskPolicy> policyCache = new HashMap<>();
            _log.info(String.format("Mask(s) found w/ initiators {%s}. "
                    + "MatchingExportMaskURIs {%s}, portNameToInitiators {%s}", Joiner.on(",")
                    .join(initiatorHelper.getPortNames()), Joiner.on(",").join(initiatorToExportMaskPlacementMap.values()), Joiner
                    .on(",").join(initiatorHelper.getPortNameToInitiatorURI().entrySet())));
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
            Set<URI> initiatorsForNewExport = new HashSet<>();

            // Special case for VMAX with a cluster compute resource:
            //
            // When multiple export masks combine to make a cluster mask, we wish to leverage them.
            //
            // At this point, we have already gotten our qualified masks for the compute resources, however
            // the "default" flow will not consider cases where multiple export masks, when combined, make up
            // a cluster resource. We need to add to the placement Map that the cluster's qualifying masks
            // are those per-node masks. Logic in the orchestrator will ensure these multi-masks will be
            // treated as a single mask, where volumes will be added to both.
            //
            // Logic is as follows: Attempt to discover which ports have not been placed in the map yet (specific to VMAX),
            // and add those ports to the map in the circumstance where we are doing cluster and the
            // existing mask is already handling multiple hosts.
            //
            // In the case of brownfield cluster, some of these port to ExportMask may be missing because the array doesn't
            // have them yet. Find this condition and add the additional ports to the map.
            if (exportGroup.forCluster() || exportGroup.forHost()) {
                updatePlacementMapForCluster(exportGroup, initiatorHelper.getResourceToInitiators(), initiatorToExportMaskPlacementMap);
            }

            // This loop processes all initiators that were found in the existing export masks.
            // It doesn't necessary mean that all initiators requested are in an export mask.
            // In the case where initiators are missing and not represented by other masks, we need
            // to mark that these initiators need to be added to the existing masks.
            for (Map.Entry<String, Set<URI>> entry : initiatorToExportMaskPlacementMap.entrySet()) {
                URI initiatorURI = initiatorHelper.getPortNameToInitiatorURI().get(entry.getKey());
                Initiator initiator = _dbClient.queryObject(Initiator.class, initiatorURI);
                // Keep track of those initiators that have been found to exist already
                // in some export mask on the array
                initiatorURIsCopy.remove(initiatorURI);

                List<URI> exportMaskURIs = new ArrayList<URI>();
                exportMaskURIs.addAll(entry.getValue());
                List<ExportMask> masks = _dbClient.queryObject(ExportMask.class, exportMaskURIs);
                _log.info(String.format("initiator %s masks {%s}", initiator.getInitiatorPort(),
                        Joiner.on(',').join(exportMaskURIs)));

                // This section will look through greenfield or brownfield scenarios and will discover if the initiator
                // is not yet added to the mask. Note the masks were all refreshed by #device.findExportMasks() above
                for (ExportMask mask : masks) {
                    _log.info(String.format("processing mask %s and initiator %s", mask.getMaskName(),
                            initiator.getInitiatorPort()));

                    // Check for NO_VIPR. If found, avoid this mask.
                    if (mask.getMaskName() != null && mask.getMaskName().toUpperCase().contains(ExportUtils.NO_VIPR)) {
                        _log.info(String.format(
                                "ExportMask %s disqualified because the name contains %s (in upper or lower case) to exclude it",
                                mask.getMaskName(), ExportUtils.NO_VIPR));
                        continue;
                    }
                    ExportMaskPolicy exportMaskDetails = getExportMaskPolicy(policyCache, device, storage, mask);

                    // Check if the ExportMask applies to more than one host. Since
                    // ViPR will be creating ExportMask per compute resource
                    // (single host or cluster), the only way that an existing mask
                    // applies to multiple hosts is when it was for a cluster
                    // export. If we find that to be the case,
                    // we should be able to create ExportMasks for it.
                    boolean hasMultipleHosts = maskAppliesToMultipleHosts(mask);
                    boolean createHostExportWhenClusterExportExists =
                            (hasMultipleHosts && exportGroup.forHost());
                    // One node cluster Case - Always create a new MV if existing mask doesn't contain Cascaded IG.
                    boolean createClusterExportWhenHostExportExists =
                            (exportGroup.forCluster() && !exportMaskDetails.isCascadedIG());
                    if (createClusterExportWhenHostExportExists ||
                            createHostExportWhenClusterExportExists) {
                        // It may turn out that we find these initiators already covered by a collection of
                        // masks for cluster purposes. If that's the case, we figure that out below and these
                        // "new" exports will never see the light of day.
                        _log.info("New export mask will be created for initiator {}", initiatorURI);
                        initiatorsForNewExport.add(initiatorURI);
                        // remove this mask from policyCache
                        policyCache.remove(mask.getId());
                        continue;
                    }

                    // We're still OK if the mask contains ONLY initiators that can be found
                    // in our export group, because we would simply add to them.
                    if (mask.getInitiators() != null) {
                        for (String existingMaskInitiatorStr : mask.getInitiators()) {
                            Initiator existingMaskInitiator = _dbClient.queryObject(Initiator.class, URI.create(existingMaskInitiatorStr));
                            // Now look at it from a different angle. Which one of our export group initiators
                            // are NOT in the current mask? And if so, if it belongs to the same host as an existing one,
                            // we should add it to this mask.
                            if ((initiator != null && initiator.getId() != null) &&
                                    // and we don't have an entry already to add this initiator to the mask
                                    (!existingMasksToUpdateWithNewInitiators.containsKey(mask.getId()) || !existingMasksToUpdateWithNewInitiators
                                            .get(mask.getId()).contains(initiator)) &&
                                    // and the initiator exists in the first place
                                    (existingMaskInitiator != null &&
                                    // and this is a host export for this host, or...
                                    (exportGroup.forHost() && initiator.getHost() != null
                                            && initiator.getHost().equals(existingMaskInitiator.getHost()) ||
                                    // this is a cluster export for this cluster
                                    (exportGroup.forCluster() && initiator.getClusterName() != null && initiator.getClusterName().equals(
                                            existingMaskInitiator.getClusterName()))))) {
                                // Add to the list of initiators we need to add to this mask
                                Set<Initiator> existingMaskInitiators = existingMasksToUpdateWithNewInitiators.get(mask.getId());

                                if (existingMaskInitiators == null) {
                                    existingMaskInitiators = new HashSet<Initiator>();
                                    existingMasksToUpdateWithNewInitiators.put(mask.getId(), existingMaskInitiators);
                                }

                                // If this initiator is already in the mask, add a key to mark that we need to add the export mask reference
                                // to the export group later.
                                if (!mask.hasInitiator(initiator.getId().toString())) {
                                    existingMaskInitiators.add(initiator);
                                    _log.info(String.format("initiator %s needs to be added to mask %s", initiator.getInitiatorPort(),
                                            mask.getMaskName()));
                                }
                            }
                        }
                    }
                }
            }

            VmaxVolumeToExportMaskApplicatorContext context = createVmaxNativeApplicatorContext(workflow, exportGroup, storage, policyCache,
                    zoningStepNeeded, token, initiatorHelper, initiatorToExportMaskPlacementMap, initiatorURIsCopy, partialMasks, volumeMap,
                    initiatorsForNewExport, existingMasksToUpdateWithNewVolumes, existingMasksToUpdateWithNewInitiators, previousStep);
            NativeVolumeToExportMaskRuleApplicator ruleApplicator = new NativeVolumeToExportMaskRuleApplicator(_dbClient, context);
            ruleApplicator.run();
            if (context.resultSuccess) {
                // Set the flags that should have been filled in by NativeVolumeToExportMaskRuleApplicator running
                previousStep = context.previousStep;
                flowCreated = context.flowCreated;
            } else {
                _log.info("Failure in volume to ExportMask rules");
                return false;
            }

            _log.info(String.format("existingMasksToUpdateWithNewVolumes.size = %d", existingMasksToUpdateWithNewVolumes.size()));

            // This is the case where we have an existing export for a cluster and we
            // want to create another export against one of the hosts in the cluster,
            // or vice-versa.
            if (!initiatorsForNewExport.isEmpty()) {
                _log.info("Initiators for which new Export Mask will be created: {}", initiatorsForNewExport);
                if (exportGroup.forCluster() && !initiatorURIsCopy.isEmpty()) {
                    // Clustered export group create request and there are essentially
                    // new and existing initiators. We'll take what's not already
                    // exported to and add it to the list of initiators to export
                    initiatorsForNewExport.addAll(initiatorURIsCopy);
                    // Clear the copy list because we're going to be creating exports
                    // for these. (There's code below that uses initiatorURIsCopy to
                    // determine what exports to update)
                    initiatorURIsCopy.clear();
                }
                Map<String, List<URI>> computeResourceToInitiators =
                        mapInitiatorsToComputeResource(exportGroup,
                                initiatorsForNewExport);
                for (Map.Entry<String, List<URI>> resourceEntry : computeResourceToInitiators.entrySet()) {
                    String computeKey = resourceEntry.getKey();
                    List<URI> computeInitiatorURIs = resourceEntry.getValue();
                    _log.info(String.format("New export masks for %s", computeKey));
                    GenExportMaskCreateWorkflowResult result = generateExportMaskCreateWorkflow(workflow, previousStep, storage,
                            exportGroup, computeInitiatorURIs, volumeMap, token);
                    flowCreated = true;
                    previousStep = result.getStepId();
                    if (zoningStepNeeded) {
                        String zoningStep = workflow.createStepId();
                        List<URI> masks = new ArrayList<URI>();
                        masks.add(result.getMaskURI());
                        previousStep = generateZoningCreateWorkflow(workflow, previousStep, exportGroup, masks, volumeMap,
                                zoningStep);
                    }
                }
            }

            // The initiatorURIsCopy was used in the for each initiator loop to see
            // which initiators already exist in a mask. If it is non-empty,
            // then it means there are initiators that are new,
            // so let's add them to the main tracker
            Map<String, List<URI>> newComputeResources =
                    mapInitiatorsToComputeResource(exportGroup, initiatorURIsCopy);

            // At this point we have the necessary data structures populated to
            // determine the workflow steps. We are going to create new masks
            // and/or add volumes to existing masks.
            if (newComputeResources != null && !newComputeResources.isEmpty()) {
                for (Map.Entry<String, List<URI>> entry : newComputeResources.entrySet()) {
                    // We have some brand new initiators, let's add them to new masks
                    _log.info(String.format("New mask needed for compute resource %s",
                            entry.getKey()));
                    GenExportMaskCreateWorkflowResult result = generateExportMaskCreateWorkflow(workflow, previousStep, storage,
                            exportGroup, entry.getValue(), volumeMap, token);
                    flowCreated = true;
                    previousStep = result.getStepId();
                    // Add zoning step
                    if (zoningStepNeeded) {
                        String zoningStep = workflow.createStepId();
                        List<URI> masks = new ArrayList<URI>();
                        masks.add(result.getMaskURI());
                        previousStep = generateZoningCreateWorkflow(workflow, previousStep, exportGroup, masks, volumeMap,
                                zoningStep);
                    }
                }
            }

            // Put volumes in the existing masks that need them.
            for (Map.Entry<URI, Map<URI, Integer>> entry : existingMasksToUpdateWithNewVolumes
                    .entrySet()) {
                ExportMask mask = _dbClient.queryObject(ExportMask.class, entry.getKey());
                updateZoningMap(exportGroup, mask, true);
                Map<URI, Integer> volumesToAdd = entry.getValue();
                _log.info(String.format("adding these volumes %s to mask %s",
                        Joiner.on(",").join(volumesToAdd.keySet()), mask.getMaskName()));
                previousStep = generateZoningAddVolumesWorkflow(workflow, previousStep,
                        exportGroup, Arrays.asList(mask), new ArrayList<URI>(volumesToAdd.keySet()));
                previousStep = generateExportMaskAddVolumesWorkflow
                        (workflow, previousStep, storage, exportGroup, mask, volumesToAdd, null);
                flowCreated = true;
                exportGroup.addExportMask(mask.getId());
                _dbClient.updateObject(exportGroup);
            }

            // Put new initiators in existing masks that are missing them.
            for (Map.Entry<URI, Set<Initiator>> entry : existingMasksToUpdateWithNewInitiators.entrySet()) {
                ExportMask mask = _dbClient.queryObject(ExportMask.class, entry.getKey());
                // Make sure the mask is one that we are going to add volumes to. Otherwise, don't bother
                // modifying it or making it part of our export group.
                if (!existingMasksToUpdateWithNewVolumes.containsKey(mask.getId())) {
                    _log.info(String.format("Not adding initiators to mask: %s because we found we don't need to change the mask",
                            mask.getMaskName()));
                    continue;
                }
                updateZoningMap(exportGroup, mask, true);

                exportGroup.addExportMask(mask.getId());
                _dbClient.updateObject(exportGroup);
                Set<Initiator> initiatorsToAdd = entry.getValue();
                if (!initiatorsToAdd.isEmpty()) {
                    List<URI> initiatorsURIs = new ArrayList<URI>();
                    for (Initiator initiator : initiatorsToAdd) {
                        initiatorsURIs.add(initiator.getId());
                    }
                    _log.info(String.format("adding these initiators %s to mask %s",
                            Joiner.on(",").join(initiatorsURIs), mask.getMaskName()));
                    Map<URI, List<URI>> maskToInitiatorsMap = new HashMap<URI, List<URI>>();
                    maskToInitiatorsMap.put(mask.getId(), initiatorsURIs);
                    previousStep = generateZoningAddInitiatorsWorkflow(workflow, previousStep, exportGroup,
                            maskToInitiatorsMap);
                    previousStep = generateExportMaskAddInitiatorsWorkflow(workflow, previousStep, storage,
                            exportGroup, mask, initiatorsURIs, volumeMap.keySet(), token);

                    flowCreated = true;
                }
            }
        }

        /*
         * // DO NOT CHECK IN UNCOMMENTED (this is convenient for debugging)
         * if (flowCreated) {
         * ExportOrchestrationTask completer = new ExportOrchestrationTask(
         * exportGroup.getId(), token);
         * completer.ready(_dbClient);
         * return false;
         * }
         */

        // Catch if no flows were created; close off the task
        if (!flowCreated) {
            ExportOrchestrationTask completer = new ExportOrchestrationTask(
                    exportGroup.getId(), token);
            completer.ready(_dbClient);
            return true;
        }

        return true;
    }

    /**
     * Special case for VMAX with a cluster compute resource:
     *
     * In the case where a mask may contain a subset of nodes of a cluster, we wish to leverage it.
     *
     * Logic is as follows: Attempt to discover which ports have not been placed in the map yet (specific to VMAX),
     * and add those ports to the map in the circumstance where we are doing cluster and the
     * existing mask is already handling multiple hosts.
     *
     * In the case of brownfield cluster, some of these port to ExportMask may be missing because the array doesn't
     * have them yet. Find this condition and add the additional ports to the map.
     *
     * @param exportGroup export group
     * @param resourceToInitiators resource -> initiator list
     * @param initiatorToExportMaskPlacementMap placement mask map from the default orchestrator
     */
    private void updatePlacementMapForCluster(ExportGroup exportGroup,
            Map<String, List<URI>> resourceToInitiators,
            Map<String, Set<URI>> initiatorToExportMaskPlacementMap) {
        // double check we're dealing with cluster
        if (exportGroup.forCluster() || exportGroup.forHost()) {
            // Safety, ensure the map has been created.
            if (initiatorToExportMaskPlacementMap == null) {
                initiatorToExportMaskPlacementMap = new HashMap<String, Set<URI>>();
            }

            // Check each compute resource's initiator list
            for (Map.Entry<String, List<URI>> entry : resourceToInitiators.entrySet()) {
                List<URI> initiatorSet = entry.getValue();
                for (URI initiatorURI : initiatorSet) {
                    Initiator initiator = _dbClient.queryObject(Initiator.class, initiatorURI);

                    // Is this initiator covered in the map yet?
                    Set<URI> exportMasksToAdd = new HashSet<URI>();
                    if (!initiatorToExportMaskPlacementMap.keySet().contains(Initiator.normalizePort(initiator.getInitiatorPort()))) {
                        // Can we find an existing intiatorToExportMaskURIMap entry that contains the same compute resource?
                        for (String port : initiatorToExportMaskPlacementMap.keySet()) {
                            // Verify it's the same compute resource
                            Initiator existingInitiator = ExportUtils.getInitiator(Initiator.toPortNetworkId(port), _dbClient);
                            if (existingInitiator != null &&
                                    ((exportGroup.forCluster() && existingInitiator.getClusterName().equals(initiator.getClusterName())) ||
                                    (exportGroup.forHost() && existingInitiator.getHostName().equals(initiator.getHostName())))) {
                                // Go through the masks, verify they are all multi-host already
                                for (URI maskId : initiatorToExportMaskPlacementMap.get(port)) {
                                    ExportMask mask = _dbClient.queryObject(ExportMask.class, maskId);
                                    if (exportGroup.forHost() || maskAppliesToMultipleHosts(mask)) {
                                        // Create a new map entry for this initiator.
                                        exportMasksToAdd.add(mask.getId());
                                    }
                                }
                            } else {
                                _log.info("Initiator {} does not have any masks that match its compute resource",
                                        initiator.getInitiatorPort());
                            }
                        }
                    }

                    if (!exportMasksToAdd.isEmpty()) {
                        _log.info("Initiator {} - to be added to export masks: {}", initiator.getInitiatorPort(), exportMasksToAdd);
                        initiatorToExportMaskPlacementMap.put(Initiator.normalizePort(initiator.getInitiatorPort()), exportMasksToAdd);
                    }
                }
            }
        }
    }

    /**
     * This method will call the method to apply a set of business rules to the volumes
     * required to be added to masking views. See the "applyVolumesToMasksUsingRule" method
     * documentation for exact rule logic.
     *
     * @param exportGroup export group
     * @param existingMasksToUpdateWithNewVolumes masks to update with new volumes if criteria is met
     * @param volumesWithNoMask a list that empties as we find homes for volumes
     * @param maskToInitiatorsMap map of export masks to the initiators they need to cover
     * @param partialMasks masks that match only a subset of the initiators that are requested
     * @param token task id
     * @param masks masks associated with the initiator
     * @param rule rule number from above
     * @return true if the task succeeded to search for homes for all the volumes. false if a fatal error occurred.
     */
    private boolean applyVolumesToMasksUsingRules(StorageSystem storage, ExportGroup exportGroup,
            Map<URI, Map<URI, Integer>> existingMasksToUpdateWithNewVolumes,
            Map<URI, Map<URI, Integer>> volumesWithNoMask,
            Map<ExportMask, ExportMaskPolicy> masksMap,
            Map<URI, Set<Initiator>> maskToInitiatorsMap,
            Set<URI> partialMasks, String token) {
        boolean isVMAX3 = storage.checkIfVmax3();
        
        // Apply RP+VMAX best practice rules if the export is to an RP
        if (exportGroup.checkInternalFlags(Flag.RECOVERPOINT) || (exportGroup.checkInternalFlags(Flag.RECOVERPOINT_JOURNAL))) {
            masksMap = applyVolumesToMasksUsingRPVMAXRules(storage, exportGroup, masksMap);
            if (masksMap.isEmpty()) {
                _log.info("No masks were found for RP that aligned with the masks of the compute resource, proceeding to create new masks");
                return true;
            }
        }
                                 
        // Rule 1: See if there is a mask that matches our policy and only our policy
        if (!applyVolumesToMasksUsingRule(exportGroup, token,
                existingMasksToUpdateWithNewVolumes,
                volumesWithNoMask, masksMap, maskToInitiatorsMap, partialMasks, 1, isVMAX3)) {
            return false; // inner method took care of the servicecode/completer
        }

        // Rule 2: See if there is any mask that contains more than 1 policy at all, which would
        // indicate a cascaded storage group, which we can use, OR a non-fast non-cascaded storage group
        // that has a volume in it that is associated with another policy, which is also usable by us.
        if (!applyVolumesToMasksUsingRule(exportGroup, token,
                existingMasksToUpdateWithNewVolumes,
                volumesWithNoMask, masksMap, maskToInitiatorsMap, partialMasks, 2, isVMAX3)) {
            return false;
        }

        // Rule 3: See if there is a non-fast masking view that is pointing to a non-cascading SG.
        // We can create a "phantom" storage group and add this volume to the storage group
        // that's already associated with this mask. This doesn't apply to VMAX3
        if (!applyVolumesToMasksUsingRule(exportGroup, token,
                existingMasksToUpdateWithNewVolumes,
                volumesWithNoMask, masksMap, maskToInitiatorsMap, partialMasks, 3, isVMAX3)) {
            return false;
        }

        return true;
    }
   
    /**
     * This method checks to see if the RP+VMAX best practice rules are followed.
     * 
     * If host information is specified in the ExportGroup (this is the case when "Create Block volume for host" service catalog is chosen):
     * a) Determine all the masking views corresponding to the compute resource.
     * b) Determine, if any, all the RP masking views corresponding to the RP site specified.
     * c) Compare the storage ports from the masking view of the compute resource and the RP masking view and see if there is a match.
     * If a match is found, then return all the matching RP masking views.
     * d) Returns an empty list of masks if there is no RP masking view that matches the given host masking view.
     * 
     * If no compute resource information is specified in the ExportGroup, just returns an empty list of masks.
     * 
     * This method also looks at existing RP masking view to check if those masks are intended for JOURNAL volumes only,
     * If the ExportGroup is for RP_JOURNAL, then return the masking view with that contains the "journal" keyword in the mask name.
     * Returns an empty list if no such masks are found and the ExportGroup specifies RP_JOURNAL.
     * 
     *
     * @param storage Storage system
     * @param exportGroup ExportGroup
     * @param masksMap Map of exportMask to policy
     * @return Map of ExportMask to ExportPolicy, masks matching the above set of rules is returned based on whether ExportGroup specifies
     *         RP or RP_JOURNAL in the ExportGroup flags.
     */
    private Map<ExportMask, ExportMaskPolicy> applyVolumesToMasksUsingRPVMAXRules(StorageSystem storage, 
            ExportGroup exportGroup,
            Map<ExportMask, ExportMaskPolicy> masksMap) {

        Map<ExportMask, ExportMaskPolicy> matchingMaskMap = new HashMap<ExportMask, ExportMaskPolicy>();
        final String RECOVERPOINT_JOURNAL = "journal";

        // If this an RP Export (non-journal) but there is no host information, return the existing maskMap.
        if (exportGroup.checkInternalFlags(Flag.RECOVERPOINT) && !exportGroup.checkInternalFlags(Flag.RECOVERPOINT_JOURNAL)
                && exportGroup.getHosts() == null && exportGroup.getClusters() == null) {
            _log.info("ExportGroup doesnt specify any hosts/clusters to which the volumes are exported, follow normal guidelines");

            // To follow the normal guidelines, make sure we dont accidentally pick a Journal MV for a non-journal volume
            for (Entry<ExportMask, ExportMaskPolicy> maskMap : masksMap.entrySet()) {
                ExportMask rpMaskingView = maskMap.getKey();
                if (rpMaskingView.getMaskName().toLowerCase().contains(RECOVERPOINT_JOURNAL)) {
                    _log.info(String.format("Not considering %s for this RP export", rpMaskingView.getMaskName()));
                    continue;
                }
                matchingMaskMap.put(maskMap.getKey(), maskMap.getValue());
            }
            return matchingMaskMap;
        }

        // If this is a RP Journal export operation, try to find an existing ExportMask that contains "Journal" keyword in the name.
        // If a matching mask is found, use it. (There should not be more than one journal mask on the VMAX)

        // TODO: Although unlikely, is it possible that there is more than one JOURNAL masking view with journal keyword in it?
        // If the answer to the above question is yes, we need a way to handle that.
        if (exportGroup.checkInternalFlags(Flag.RECOVERPOINT_JOURNAL)) {
            _log.info("Looking for masks with JOURNAL keyword since this export group is intended for journal volumes only");
            for (Entry<ExportMask, ExportMaskPolicy> maskMap : masksMap.entrySet()) {
                ExportMask rpMaskingView = maskMap.getKey();
                if (rpMaskingView.getMaskName().toLowerCase().contains(RECOVERPOINT_JOURNAL)) {
                    matchingMaskMap.put(maskMap.getKey(), maskMap.getValue());
                }
            }
   
            return matchingMaskMap;
        }

        List<String> initiators = getComputeResourceInitiators(exportGroup);
        
        //Fetch all the existing masks for the compute resource
        Map<String, Set<URI>> crMaskingViews = getDevice().findExportMasks(storage, initiators, false);
        Map<URI, ExportMask> crMaskingViewMap = new HashMap<URI, ExportMask>();

        for (Entry<String, Set<URI>> crMaskingViewEntry : crMaskingViews.entrySet()) {
            Set<URI> crMaskingView = crMaskingViewEntry.getValue();
            for (URI crMaskingViewUri : crMaskingView) {
                crMaskingViewMap.put(crMaskingViewUri, _dbClient.queryObject(ExportMask.class, crMaskingViewUri));
            }
        }

        //In the case of an RP  volume, the masksMap contains all the masks matching the RP initiators that was passed down. 
        //We need to weed through this list to find only those masking view that is compatible with the list of masking views for the compute resource
        for (Entry<ExportMask, ExportMaskPolicy> maskMap : masksMap.entrySet()) {
            ExportMask rpMaskingView = maskMap.getKey();

            // If we got this far, then we are looking for masks that are not meant for JOURNALs.
            // Ignore RP masks with journal keyword.
            if (rpMaskingView.getMaskName().toLowerCase().contains(RECOVERPOINT_JOURNAL)) {
                _log.info(String.format("%s is a journal mask, not considering it for RP source/target copy volume",
                        rpMaskingView.getMaskName()));
                continue;
            }

            for (Entry<URI, ExportMask> crMaskingViewMapEntry : crMaskingViewMap.entrySet()) {
                ExportMask crMaskingView = crMaskingViewMapEntry.getValue();

                // If the storage ports in the compute resource mask contains all the ports in the RP mask, then we have match.
                if (crMaskingView.getStoragePorts().size() >= rpMaskingView.getStoragePorts().size() &&
                        crMaskingView.getStoragePorts().containsAll(rpMaskingView.getStoragePorts())) {
                    if (!matchingMaskMap.containsKey(rpMaskingView)) {
                        _log.info(String.format(
                                "Found a RP masking view %s that has the same storage ports as the computer resource (host/cluster) mask %s to which we are exporting the volume. "
                                        + "OK to use the RP masking view.",
                                rpMaskingView.getMaskName(), crMaskingView.getMaskName()));
                        matchingMaskMap.put(rpMaskingView, maskMap.getValue());
                    }
                }
            }
        }
        
        if (matchingMaskMap.isEmpty()) {
            _log.info("No RP masks found that align with to the compute resources' masks");
            if (!masksMap.isEmpty()) {
                _log.info(
                        "There are existing RP masks but none align with the masks for the compute resource. Check to see if they can be re-used");
                return masksMap;
            } else {
                _log.info("No existing masks found for the compute resource, proceed as normal");
            }
        }

        return matchingMaskMap;
    }

    /**
     * Fetch all the initiators of the compute resource (host/cluster) in the export group.
     * 
     * @param exportGroup Export Group
     * @return List of initiators
     */
    private List<String> getComputeResourceInitiators(ExportGroup exportGroup) {
        // Get the initiators of the compute resource in the exportGroup
        List<String> initiators = new ArrayList<String>();
        URIQueryResultList uriQueryList = new URIQueryResultList();
      
        if (exportGroup.getClusters() != null && !exportGroup.getClusters().isEmpty()) {
            _log.info("Exporting to Cluster");
            List<URI> clusterHostUris = ComputeSystemHelper.getChildrenUris(_dbClient,
                    URI.create(exportGroup.getClusters().iterator().next()), Host.class, "cluster");
            for (URI clusterHostUri : clusterHostUris) {
                URIQueryResultList list = new URIQueryResultList();
                _dbClient.queryByConstraint(
                        ContainmentConstraint.Factory.getContainedObjectsConstraint(clusterHostUri, Initiator.class, "host"), list);
                Iterator<URI> uriIter = list.iterator();
                while (uriIter.hasNext()) {
                    Initiator initiator = _dbClient.queryObject(Initiator.class, uriIter.next());
                    initiators.add(Initiator.normalizePort(initiator.getInitiatorPort()));
                    _log.info("ComputeResource (Cluster-Host) initiator : " + Initiator.normalizePort(initiator.getInitiatorPort()));
                }
            }
        } else if (exportGroup.getHosts() != null && !exportGroup.getHosts().isEmpty()) {
            _log.info("Exporting to Host");
            _dbClient.queryByConstraint(
                    ContainmentConstraint.Factory.getContainedObjectsConstraint(URI.create(exportGroup.getHosts().iterator().next()),
                            Initiator.class, "host"),
                    uriQueryList);
            Iterator<URI> uriIter = uriQueryList.iterator();
            while(uriIter.hasNext()) {
                Initiator initiator = _dbClient.queryObject(Initiator.class, uriIter.next());
                initiators.add(Initiator.normalizePort(initiator.getInitiatorPort()));   
                _log.info("ComputeResource (Host) initiator : " + Initiator.normalizePort(initiator.getInitiatorPort()));
            }
        }
        return initiators;
    }
    
    /**
     * Apply business rules to "add" volumes to specific export masks.
     *
     * Currently implemented rules:
     * Rule 1. If you find an exact match of your volume with a mask's policy, use it.
     * Rule 2. If you find a mask with multiple policies using cascaded storage groups, use it.
     * Rule 3. If you find a mask with non-cascading storage group and a non-fast SG, use it.
     * (phantom will be searched/created in this case)
     *
     * @param exportGroup export group
     * @param token task id
     * @param existingMasksToUpdateWithNewVolumes masks to update with new volumes if criteria is met
     * @param volumesWithNoMask a list that empties as we find homes for volumes
     * @param masks masks associated with the initiator
     * @param maskToInitiatorsMap map of export masks to the initiators they need to cover
     * @param partialMasks list of masks that contain a subset of initiators for the compute resource requested
     * @param rule rule number from above
     * @return true if the task succeeded to search for homes for all the volumes. false if a fatal error occurred.
     */
    private boolean applyVolumesToMasksUsingRule(ExportGroup exportGroup,
            String token,
            Map<URI, Map<URI, Integer>> existingMasksToUpdateWithNewVolumes,
            Map<URI, Map<URI, Integer>> volumesWithNoMask,
            Map<ExportMask, ExportMaskPolicy> masks,
            Map<URI, Set<Initiator>> maskToInitiatorsMap,
            Set<URI> partialMasks, int rule, boolean isVMAX3) {

        // populate a map of mask to initiator ID for the analysis loop.
        Map<URI, Set<URI>> maskToInitiatorsToAddMap = new HashMap<URI, Set<URI>>();
        if (maskToInitiatorsMap != null) {
            for (Entry<URI, Set<Initiator>> entry : maskToInitiatorsMap.entrySet()) {
                for (Initiator initiator : entry.getValue()) {
                    if (!maskToInitiatorsToAddMap.containsKey(entry.getKey())) {
                        maskToInitiatorsToAddMap.put(entry.getKey(), new HashSet<URI>());
                    }
                    maskToInitiatorsToAddMap.get(entry.getKey()).add(initiator.getId());
                }
            }
        }

        ListMultimap<URI, URI> volumesWithMask = ArrayListMultimap.create();
        for (ExportMask mask : ExportMaskUtils.sortMasksByEligibility(masks, exportGroup)) {
            // We need to see if the volume also exists the mask,
            // if it doesn't then we'll add it to the list of volumes to add.
            ExportMaskPolicy policy = masks.get(mask);
            for (URI initiatorId : volumesWithNoMask.keySet()) {

                // Check to ensure the initiator is in this mask or in the list of initiators we intend to add to this mask.
                if ((mask.getInitiators() == null || !mask.getInitiators().contains(initiatorId.toString()))
                        &&
                        (!maskToInitiatorsToAddMap.containsKey(mask.getId()) || !maskToInitiatorsToAddMap.get(mask.getId()).contains(
                                initiatorId))) {
                    continue;
                }

                Map<URI, VirtualPool> uriVirtualPoolMap = new HashMap<URI, VirtualPool>();
                for (URI boURI : volumesWithNoMask.get(initiatorId).keySet()) {
                    BlockObject bo = Volume.fetchExportMaskBlockObject(_dbClient, boURI);
                    if (bo != null && !mask.hasExistingVolume(bo)) {

                        // Make sure the volume hasn't already been found for this initiator and BO combination.
                        // If that's the case, we can simply move onto the next volume because the volumesWithMask
                        // object is already reflected that the volume is covered.
                        if (volumesWithMask.containsKey(initiatorId) && volumesWithMask.get(initiatorId).contains(boURI)) {
                            continue;
                        }

                        // Make sure the mask matches the fast policy of the volume
                        boolean match = false;

                        // Make sure this volume hasn't already been placed in this masking view.
                        // If it is, we still need to mark that the volume has found a home for this specific initiator, so
                        // set the match flag to true so the volumesWithMask will get marked properly.
                        if (existingMasksToUpdateWithNewVolumes.containsKey(mask.getId())
                                && existingMasksToUpdateWithNewVolumes.get(mask.getId()).containsKey(boURI)) {
                            match = true;
                        } else {
                            List<Initiator> initiators = _dbClient.queryObjectField(Initiator.class, "iniport", Arrays.asList(initiatorId));
                            Initiator initiator = initiators.get(0);
                            _log.info(String.format(
                                    "Pre-existing Mask Rule %d: volume %s is not exposed to initiator %s in mask %s.  Checking rule.",
                                    rule, bo.getLabel(),
                                    initiator.getInitiatorPort(), mask.getMaskName()));
                            // Check if the requested HLU for the volume is
                            // already taken by a pre-existing volume.
                            Integer requestedHLU = volumesWithNoMask.get(initiatorId).get(boURI);
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

                            String volumePolicyName = ControllerUtils.getAutoTieringPolicyName(bo.getId(), _dbClient);
                            if (volumePolicyName.equalsIgnoreCase(Constants.NONE.toString())) {
                                volumePolicyName = null;
                            }

                            VirtualPool virtualPool = null;
                            if (bo instanceof Volume) {
                                Volume volume = (Volume) bo;
                                virtualPool = uriVirtualPoolMap.get(volume.getVirtualPool());
                                if (virtualPool == null) {
                                    virtualPool = _dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());
                                    uriVirtualPoolMap.put(volume.getVirtualPool(), virtualPool);
                                }
                            }

                            // There is no policy in the mask and volume: that's an exact match.
                            // There is a policy in the mask and it's exactly the only policy for this mask.
                            // Exact is either a simple mask with our policy, or a non-simple mask with our policy and only our policy
                            // The mask must contain all the initiators associated with the compute resource.
                            if (rule == 1) {
                                // Spot-check partial masks before continuing: Either...
                                // 1. This mask is a full mask (no partial initiator masks that only make up a portion of the whole compute
                                // resource)
                                // 2. This mask is a a partial mask, but all of the masks point to the same SG (which works for both FAST
                                // and non-FAST)
                                // 3. The volume is NON-FAST
                                if (!partialMasks.contains(mask.getId()) || partialMasksContainSameSG(partialMasks, masks, mask)
                                        || volumePolicyName == null) {
                                    // Exact fit case, no FAST policy (or for case of VMAX3, the MV is associated to the Optimized SLO)
                                    if (volumePolicyName == null && (policy.localTierPolicy == null ||
                                            (isVMAX3 && policy.localTierPolicy.contains(Constants.OPTIMIZED_SLO)))) {
                                        _log.info("Pre-existing Mask Matched rule 1B: volume and mask do not have FAST policy");
                                        match = true;
                                    }

                                    // Volume with no FAST policy and Mask with non-cascading SG and FAST Policy
                                    if (isVMAX3 && volumePolicyName == null && policy.localTierPolicy != null) {
                                        _log.info(
                                                "Pre-existing Mask Matched rule 1B-1 (VMAX3): volume do not have FAST policy but mask has with non-cascading SG");
                                        match = true;
                                    }

                                    // Exact fit case, FAST policy with non-cascading storage group
                                    if (volumePolicyName != null) {
                                        if (policy.localTierPolicy != null) {
                                            if (isVMAX3) {
                                                // COP-34807 In Case of VMAX3 SG is converted to CSG
                                                // irrespective of policy name and MV is reused.
                                                _log.info(
                                                        "Pre-existing Mask Matched rule 1C-1 (VMAX3):volume has FAST policy which is same or different as "
                                                                + "masking view with non-cascading storage group");
                                                match = true;
                                            } else {
                                                match = policy.localTierPolicy.equalsIgnoreCase(volumePolicyName);
                                                if (match) {
                                                _log.info(
                                                        "Pre-existing Mask Matched rule 1C: volume has same FAST policy as masking view with non-cascading storage group");
                                                }
                                            }
                                        }

                                        // Exact fit case, FAST policy with cascading storage group, but there's only one FAST policy, and
                                        // it's ours.
                                        if (policy.localTierPolicy == null && policy.tierPolicies != null
                                                && policy.tierPolicies.size() == 1) {
                                            if (isVMAX3) {
                                                String policyName = policy.tierPolicies.iterator().next();
                                                match = SmisUtils.checkPolicyMatchForVMAX3(policyName, volumePolicyName);
                                            } else {
                                                match = policy.tierPolicies.contains(volumePolicyName);
                                            }

                                            if (match) {
                                                _log.info("Pre-existing Mask Matched rule 1D: volume has same FAST policy as masking view with cascading storage group");
                                            }
                                        }
                                    }

                                    // verify host io limits match if policy name is a match
                                    if (virtualPool != null) {
                                        match &= HostIOLimitsParam.isEqualsLimit(policy.getHostIOLimitBandwidth(),
                                                virtualPool.getHostIOLimitBandwidth())
                                                && HostIOLimitsParam.isEqualsLimit(policy.getHostIOLimitIOPs(),
                                                        virtualPool.getHostIOLimitIOPs());
                                    }
                                } else {
                                    _log.info("Pre-existing Mask did not match rule 1A: volume is FAST, mask comprises only part of the compute resource, and the storage groups in each mask are not the same.  "
                                            +
                                            "Attempting to use this mask would cause a violation on the VMAX since the same volume can not be in more than one storage group with a FAST policy defined.");
                                }
                            }

                            // The mask is associated with at least more than one policy (including non-FAST)
                            // and it's using cascading storage groups.
                            if (rule == 2) {
                                // if it is a cascaded SG, mask need to be selected
                                // VMAX3: Phantom SGs are not created for VMAX3, so ignore the mask
                                if (!policy.simpleMask && checkIfRule2SatisfiesForVMAX3(isVMAX3, policy)) {
                                    _log.info("Pre-existing mask Matched rule 2A: volume has FAST policy and masking view has cascaded storage group");
                                    // VMAX2: Host IO limits cannot be associated to phantom SGs,
                                    // hence verify if IO limit set on the SG within MV if not we need to create a new Masking view.
                                    if (ExportMaskPolicy.EXPORT_TYPE.PHANTOM.name().equalsIgnoreCase(policy.getExportType())) {
                                        if (virtualPool != null) {
                                            if (HostIOLimitsParam.isEqualsLimit(policy.getHostIOLimitBandwidth(),
                                                    virtualPool.getHostIOLimitBandwidth())
                                                    && HostIOLimitsParam.isEqualsLimit(policy.getHostIOLimitIOPs(),
                                                            virtualPool.getHostIOLimitIOPs())) {
                                                _log.info("Pre-existing mask Matched rule 2A-1: Phantom SGs are available to add FAST volumes to this masking view, and expected HostIO limit is set on SG within masking view.");
                                                match = true;
                                            } else {
                                                _log.info("Pre-existing mask did not match rule 2A-1: Phantom SGs are available to add FAST volumes to this masking view, but HostIO limit is not set on SG within masking view.");
                                            }
                                        }
                                    } else {
                                        match = true;
                                    }

                                } else {
                                    if (volumePolicyName == null) {
                                        _log.info("Pre-existing mask did not match rule 2A: volume does not have a FAST policy, and this rules requires the volume to have a FAST policy associated with it");
                                    }
                                    if (policy.simpleMask) {
                                        _log.info("Pre-existing mask did not match rule 2A: mask has a cascaded storage group, and this rule requires the storage group be non-cascaded in the mask");
                                    }
                                }
                            }

                            // If it's a non-cascaded SG, non-FAST masking view with at least 1 fast volume in it, then we can select it
                            // because
                            // we're capable of creating phantom storage groups.
                            if (!isVMAX3 && rule == 3) {
                                if (volumePolicyName != null) {
                                    if ((policy.tierPolicies == null || policy.tierPolicies.isEmpty()) && policy.simpleMask) {
                                        _log.info("Pre-existing mask Matched rule 3A: volume has non-cascaded, non-FAST storage group, allowing VipR to make/use island storage groups for FAST");
                                        match = true;

                                        // verify host io limits match if policy name is a match
                                        if (virtualPool != null) {
                                            match = HostIOLimitsParam.isEqualsLimit(policy.getHostIOLimitBandwidth(),
                                                    virtualPool.getHostIOLimitBandwidth())
                                                    && HostIOLimitsParam.isEqualsLimit(policy.getHostIOLimitIOPs(),
                                                            virtualPool.getHostIOLimitIOPs());
                                        }
                                    } else {
                                        _log.info("Pre-existing mask did not match rule 3A: volume is FAST and mask does not have a non-cascaded, non-FAST storage group.  A non-cascaded, non-FAST storage group in the masking view allows ViPR to "
                                                +
                                                "create or use a separate island storage group for FAST volumes");
                                    }
                                } else {
                                    _log.info("Pre-existing mask did not match rule 3A: volume does not have a FAST policy, and this rule requires the volume to have a FAST policy associated with it");
                                }
                            }

                            if (match) {
                                _log.info(String.format("Found that we can add volume %s to export mask %s", bo.getLabel(),
                                        mask.getMaskName()));
                                // The volume doesn't exist, so we have to add it to
                                // the masking container.
                                Map<URI, Integer> newVolumes = existingMasksToUpdateWithNewVolumes
                                        .get(mask.getId());
                                if (newVolumes == null) {
                                    newVolumes = new HashMap<URI, Integer>();
                                    existingMasksToUpdateWithNewVolumes.put(mask.getId(), newVolumes);
                                }

                                // Check to see if the volume is already in this mask. (Map hashcode not finding existing volume URIs)
                                if (!newVolumes.containsKey(bo.getId())) {
                                    newVolumes.put(bo.getId(), requestedHLU);
                                    mask.addToUserCreatedVolumes(bo);
                                } else {
                                    _log.info(String.format("Found we already have volume %s in the list for mask %s", bo.getLabel(),
                                            mask.getMaskName()));
                                }
                            }
                        }

                        if (match) {
                            // We found a mask for this volume, remove from the no-mask-yet list
                            volumesWithMask.put(initiatorId, boURI);
                        }

                    } else if (mask.hasExistingVolume(bo)) {
                        // We found a mask for this volume, remove from the no-mask-yet list
                        _log.info(String.format("rule %d: according to the database, volume %s is already in the mask: %s", rule,
                                bo.getWWN(),
                                mask.getMaskName()));
                        volumesWithMask.put(initiatorId, boURI);
                    }
                }
            }

            // Update the list of volumes and initiators for the mask
            Map<URI, Integer> volumeMapForExistingMask = existingMasksToUpdateWithNewVolumes
                    .get(mask.getId());
            if (volumeMapForExistingMask != null && !volumeMapForExistingMask.isEmpty()) {
                mask.addVolumes(volumeMapForExistingMask);
            }

            // Remove the entries from the no-mask-yet map
            for (Entry<URI, Collection<URI>> entry : volumesWithMask.asMap().entrySet()) {
                URI initiatorId = entry.getKey();
                if (volumesWithNoMask != null &&
                        volumesWithNoMask.get(initiatorId) != null) {
                    for (URI boId : entry.getValue()) {
                        if (volumesWithNoMask.get(initiatorId) != null) {
                            volumesWithNoMask.get(initiatorId).remove(boId);
                            if (volumesWithNoMask.get(initiatorId).isEmpty()) {
                                volumesWithNoMask.remove(initiatorId);
                            }
                        }
                    }
                }
            }
        }

        return true;
    }

    /**
     * Check if rule-2 satisfies for VMAX3.
     * If it is not simple mask, it could either be cascaded or Phantom SG.
     * Phantom SGs are not created for VMAX3, so rule-2 does not satisfy for this case.
     */
    private boolean checkIfRule2SatisfiesForVMAX3(boolean isVMAX3, ExportMaskPolicy policy) {
        if (isVMAX3 &&
                ExportMaskPolicy.EXPORT_TYPE.PHANTOM.name().equalsIgnoreCase(policy.getExportType())) {
            return false;
        }
        return true;
    }

    /**
     * Determines if the mask has the same SG as the other masks that are partial masks.
     *
     * @param partialMasks list of export masks that are partial masks
     * @param masks
     * @param mask export mask
     * @return true if all masks in partialMasks have the same SG
     */
    private boolean partialMasksContainSameSG(Set<URI> partialMasks, Map<ExportMask, ExportMaskPolicy> masks, ExportMask mask) {
        String sgName = null;
        // Find the mask in the mask mapping, grab the SG name
        for (Map.Entry<ExportMask, ExportMaskPolicy> entry : masks.entrySet()) {
            ExportMaskPolicy policy = entry.getValue();
            sgName = policy.sgName;
        }

        for (URI partialMaskURI : partialMasks) {
            // Find the mask in the mask mapping
            for (Map.Entry<ExportMask, ExportMaskPolicy> entry : masks.entrySet()) {
                ExportMask myMask = entry.getKey();
                if (myMask.getId().equals(partialMaskURI)) {
                    ExportMaskPolicy policy = entry.getValue();
                    if (sgName == null || !sgName.equalsIgnoreCase(policy.sgName)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * This method will search the array for existing exports that match the set of
     * compute to initiator port names map.
     *
     * For those cases where exportGroup.Type = Cluster or Host,
     * this will attempt make sure that existing exports with exactly those initiators
     * a compute are considered hits.
     *
     * @param device [in] - BlockStorageDevice interface for accessing find
     *            function for VMAX
     *
     * @param storage [in] - StorageSystem object representing the physical
     *            array that we are going to search
     *
     * @param exportGroup [in] - ExportGroup object representing the ViPR level export
     * @param computeToPorts [in] - Multi list (basically,
     *            a map of String to Collection of Strings),
     *            representing the list of initiator port names for compute
     *            resources (clusters or hosts)
     * @return Map of compute resources keys to set of ExportMask URIs.
     */
    protected Map<String, Set<URI>> findExistingMasksForComputeResources(BlockStorageDevice device, StorageSystem storage,
            ExportGroup exportGroup,
            ListMultimap<String, String> computeToPorts) {
        Map<String, Set<URI>> matchingExportMaskURIs = new HashMap<String, Set<URI>>();
        // Loop all compute resources and look up existing masks (if any) and to the
        // the result list.
        for (Map.Entry<String, Collection<String>> entry : computeToPorts.asMap().entrySet()) {
            String computeResourceId = entry.getKey();
            List<String> portNames = new ArrayList<String>(entry.getValue());
            _log.info("findExistingMasksForComputeResource - Trying to find " +
                    "existing export for compute resource {} with these ports: {}",
                    computeResourceId, Joiner.on(',').join(portNames));
            Map<String, Set<URI>> exportMaskURIs =
                    device.findExportMasks(storage, portNames, false);
            for (String portName : exportMaskURIs.keySet()) {
                if (exportMaskURIs.get(portName) != null) {
                    for (URI maskURI : exportMaskURIs.get(portName)) {
                        ExportMask mask = _dbClient.queryObject(ExportMask.class, maskURI);
                        boolean addMask = true;
                        if (exportGroup.forHost() && maskAppliesToMultipleHosts(mask)) {
                            addMask = false;
                            _log.info("findExistingMasksForComputeResource - disqualifying mask {} because it contains multiple hosts",
                                    mask.getMaskName());
                        } else if (exportGroup.forCluster() && !maskAppliesToMultipleHosts(mask)) {
                            addMask = false;
                            _log.info("findExistingMasksForComputeResource - (temporarily) disqualifying mask {} because it does not " +
                                    "contain multiple hosts.  Additional check will be made in next phase.", mask.getMaskName());
                        }

                        if (addMask) {
                            if (matchingExportMaskURIs.get(computeResourceId) == null) {
                                matchingExportMaskURIs.put(computeResourceId, new HashSet<URI>());
                            }
                            matchingExportMaskURIs.get(computeResourceId).add(maskURI);
                        }
                    }
                }
            }

            // Did we find any exact matches for this compute resource for cluster? If so, we're done looking.
            // Otherwise we're going to look for an aggregate of multiple masking views that, when combined,
            // equal the whole cluster.
            if (exportGroup.getType() != null && exportGroup.forCluster() && matchingExportMaskURIs.isEmpty()) {
                _log.info("findExistingMasksForComputeResource - Trying to find " +
                        "existing multiple export for compute resource {} with these exact ports: {}",
                        computeResourceId, Joiner.on(',').join(portNames));
                // Refresh the export masks to find subsets of our ports
                // TODO: somehow only call findExportMasks once.
                exportMaskURIs =
                        device.findExportMasks(storage, portNames, false);
                if (exportMaskURIs.size() == portNames.size()) {
                    _log.info("findExistingMasksForComputeResource - Found that returned masks do contain " +
                            "all of the port necessary to consistute the compute resource: " + computeResourceId);
                    for (String portName : exportMaskURIs.keySet()) {
                        if (matchingExportMaskURIs.get(computeResourceId) == null) {
                            matchingExportMaskURIs.put(computeResourceId, new HashSet<URI>());
                        }
                        matchingExportMaskURIs.get(computeResourceId).addAll(exportMaskURIs.get(portName));
                    }
                }
            }
        }
        _log.info("findExistingMasksForComputeResource - {} compute resources were found",
                matchingExportMaskURIs.size());
        return matchingExportMaskURIs;
    }

    /**
     * This function processes the initiatorURIs and return a mapping of String
     * host or cluster resource reference to a list Initiator URIs.
     *
     * This is the default implementation and it will group the
     * initiator's host reference
     *
     * @param exportGroup [in] - ExportGroup object to examine
     * @param initiatorURIs [in] - Initiator URIs
     * @return Map of String:computeResourceName to List of Initiator URIs
     */
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
                if (initiator != null) {
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
    public void exportGroupChangePolicyAndLimits(URI storageURI, URI exportMaskURI,
            URI exportGroupURI, List<URI> volumeURIs, URI newVpoolURI,
            boolean rollback, String token) throws Exception {

        ExportOrchestrationTask taskCompleter = new ExportOrchestrationTask(
                exportGroupURI, token);
        ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
        StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageURI);
        VirtualPool newVpool = _dbClient.queryObject(VirtualPool.class, newVpoolURI);
        BlockStorageDevice device = getDevice();
        device.updatePolicyAndLimits(storage, exportMask, volumeURIs, newVpool,
                rollback, taskCompleter);
    }

    @Override
    public void changeAutoTieringPolicy(URI storageURI, List<URI> volumeURIs,
            URI newVpoolURI, boolean rollback, String token) throws Exception {

        VolumeUpdateCompleter taskCompleter = new VolumeUpdateCompleter(volumeURIs, token);
        StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageURI);
        VirtualPool newVpool = _dbClient.queryObject(VirtualPool.class, newVpoolURI);
        BlockStorageDevice device = getDevice();
        device.updatePolicyAndLimits(storage, null, volumeURIs, newVpool,
                rollback, taskCompleter);
    }

    @Override
    protected void suggestExportMasksForPlacement(StorageSystem storage, BlockStorageDevice device, List<Initiator> initiators,
            ExportMaskPlacementDescriptor descriptor) {
        // VMAX can have multiple ExportMasks (MaskingViews) that contain the same set of initiators.
        // So, it's up to this implementation to determine where to best place the volumes based
        // on volume and ExportMask characteristics. To that end, we will hint that the placement
        // will be separating the volumes per ExportMask
        descriptor.setPlacementHint(ExportMaskPlacementDescriptor.PlacementHint.VOLUMES_TO_SEPARATE_MASKS);

        // Find all the ExportMasks on the array that have the initiators (or a subset of them)
        Map<URI, ExportMask> matchingMasks = readExistingExportMasks(storage, device, initiators);

        // filter out export masks which do not have at least one port from the virtual array
        Set<URI> invalidMasks = new HashSet<URI>();
        VirtualArray virtualArray = _dbClient.queryObject(VirtualArray.class, descriptor.getVirtualArray());
        for (Entry<URI, ExportMask> entry : matchingMasks.entrySet()) {
            ExportMask mask = entry.getValue();
            boolean matched = maskHasStoragePortsInExportVarray(virtualArray, mask);
            if (!matched) {
                invalidMasks.add(entry.getKey());
                _log.info("Mask does not have valid ports from varray: {}", mask.getLabel());
            }
        }
        for (URI maskUri : invalidMasks) {
            matchingMasks.remove(maskUri);
        }
        // set matching masks in the descriptor
        descriptor.setMasks(matchingMasks);
        if (matchingMasks.isEmpty()) {
            return;
        }

        // Dummy/non-essential data
        ExportGroup dummyExportGroup = new ExportGroup();
        dummyExportGroup.setType(ExportGroupType.Host.name());

        // InitiatorHelper for processing the initiators
        InitiatorHelper initiatorHelper = new InitiatorHelper(initiators).process(dummyExportGroup);

        // Create and fill in:
        // -- Mapping of initiators to ExportMask URIs
        // -- Set of ExportMasks that match partially to the initiator set
        // -- Mapping of ExportMask URI to ExportMaskPolicy
        Set<URI> partialMasks = new HashSet<>();
        Map<String, Set<URI>> initiatorToExportMaskMap = new HashMap<>();
        Map<URI, ExportMaskPolicy> policyCache = new HashMap<>();
        Collection<String> portNames = Collections2.transform(initiators, CommonTransformerFunctions.fctnInitiatorToPortName());
        for (Map.Entry<URI, ExportMask> entry : matchingMasks.entrySet()) {
            URI exportMaskURI = entry.getKey();
            ExportMask exportMask = entry.getValue();
            // Get the ExportMaskPolicy, thereby saving it to the policyCache. The policyCache is a mapping of the ExportMask URI to
            // its ExportMaskPolicy object. The ExportMaskPolicy is a transient object that is used to hold meta data about the ExportMask.
            // This meta data is mostly there to describe the AutoTieringPolicy, Host IO parameters, and InitiatorGroup usage.
            // There could be other information, as well. It suffices to understand that this data is relevant for the rules applicator
            // that we're invoking below. The rules applicator will use this as a way to determine which ExportMask is best
            // suited to hold the volumes.
            getExportMaskPolicy(policyCache, device, storage, exportMask);
            // Populate the mapping of Initiator portname (WWN/IQN) to the ExportMask URI
            for (String portName : portNames) {
                Set<URI> masks = initiatorToExportMaskMap.get(portName);
                if (masks == null) {
                    masks = new HashSet<>();
                    initiatorToExportMaskMap.put(portName, masks);
                }
                masks.add(exportMaskURI);
            }
            // If the mask does not contain the exact set of initiators that we're trying to
            // export to, then we need to put it in the set of masks that have a partial match
            if (!ExportMaskUtils.hasExactlyTheseInitiators(exportMask, portNames, _dbClient)) {
                partialMasks.add(exportMaskURI);
            }

            // Determine which ExportMasks are equivalent in terms of attributes, other than
            // the number of volumes that they contain. The preference is for the rules
            // applicator (below) to choose, from equivalent masks, the one with the least
            // volumes. But we'd like to still know which are equivalent in case the mask
            // that is selected in the code here, is invalid in some higher level validation.
            descriptor.addToEquivalentMasks(exportMask, policyCache.get(exportMaskURI));
        }

        // Populate the Volume URI to Volume HLU mapping. We will let the array decide the HLUs (i.e., set it to -1)
        Map<URI, Integer> volumeMap = new HashMap<>();
        for (URI volumeURI : descriptor.getVolumesToPlace().keySet()) {
            volumeMap.put(volumeURI, -1);
        }

        // Mapping of ExportMask URI to Volume-HLU: the basic output that we're expecting to be filled in by the rules applicator
        Map<URI, Map<URI, Integer>> maskToUpdateWithNewVolumes = new HashMap<>();

        // All data structures should have been filled in at this point; create the context and ruleApplicator for it
        VmaxVolumeToExportMaskApplicatorContext context = createVPlexBackendApplicatorContext(dummyExportGroup, storage, policyCache,
                initiatorHelper, initiatorToExportMaskMap, partialMasks, volumeMap, maskToUpdateWithNewVolumes);
        VplexBackendVolumeToExportMaskRuleApplicator rulesApplicator =
                new VplexBackendVolumeToExportMaskRuleApplicator(_dbClient, context);
        try {
            rulesApplicator.run();
            if (context.resultSuccess) {
                // Get configuration value for how many volumes are allowed in MaskingView. If the number
                // of volumes exceeds this amount for a particular ExportMask, then it cannot be a candidate
                // for reuse.
                customConfigHandler = (CustomConfigHandler) ControllerServiceImpl.getBean(CUSTOM_CONFIG_HANDLER);
                int maxVolumesAllowedByConfig = Integer.valueOf(customConfigHandler
                        .getComputedCustomConfigValue(CustomConfigConstants.VPLEX_VMAX_MASKING_VIEW_MAXIMUM_VOLUMES,
                                storage.getSystemType(), null));

                // Use a local cache in case the same volumes are selected
                // to be placed into different ExportMasks
                ObjectLocalCache cache = new ObjectLocalCache(_dbClient);

                // Rules were run without errors, now process the results:
                // Process each entry in the mapping of ExportMask to Volumes ...
                for (Map.Entry<URI, Map<URI, Integer>> entry : maskToUpdateWithNewVolumes.entrySet()) {
                    URI exportMaskURI = entry.getKey();
                    Set<URI> volumeURIs = entry.getValue().keySet();
                    // The ExportMaskPolicy is a transient object that is used to hold meta data about the ExportMask.
                    // This meta data is mostly there to describe the AutoTieringPolicy, Host IO parameters, and InitiatorGroup usage.
                    // There could be other information, as well.
                    ExportMaskPolicy policy = policyCache.get(exportMaskURI);
                    // Translate the Volume URI to Volume HLU map to a Volume URI to Volume object map:
                    Map<URI, Volume> volumes = new HashMap<>();
                    List<Volume> queriedVols = cache.queryObject(Volume.class, volumeURIs);
                    for (Volume volume : queriedVols) {
                        volumes.put(volume.getId(), volume);
                    }

                    // TODO: We need to explore if we should/can make the volume count check (done below) another rule run as
                    // part of the RuleApplicator. The one concern with doing this is what would happen if another ExportMask
                    // is selected. Would we end up selecting an ExportMask that can support the volumes, but is undesirable
                    // through some other considerations. For now, we will let the rules engine decide the appropriate
                    // ExportMasks and then evaluate that selection for the volume count.

                    // Validate the number of volumes that will be in the ExportMask if 'volumes' were added to it.
                    // If there are more the maximum number of volumes allowed, then we should not place these 'volumes'.
                    // The volumes would show up in the descriptor as being unplaced. The VplexBackendManager would
                    // to take care of this case by creating another ExportMask to contain these volumes.
                    ExportMask exportMask = matchingMasks.get(exportMaskURI);
                    int totalVolumesWhenAddedToExportMask = exportMask.returnTotalVolumeCount() + volumes.size();
                    boolean moreVolumesThanAllowedByConfig = totalVolumesWhenAddedToExportMask > maxVolumesAllowedByConfig;
                    boolean moreVolumesThanAllowedByArray = totalVolumesWhenAddedToExportMask > policy.getMaxVolumesAllowed();
                    if (moreVolumesThanAllowedByArray || moreVolumesThanAllowedByConfig) {
                        _log.info(String.format(
                                "ExportMask %s (%s) is matching, but already %d volumes associated with it. " +
                                "Adding %d volumes to it will make it go over its limit, hence it will not be used for placement.%n" +
                                "The configuration allows %d volumes and the array allows %d as the max number of volumes to a MaskingView",
                                exportMask.getMaskName(), exportMask.getId(),
                                exportMask.returnTotalVolumeCount(), volumes.size(), maxVolumesAllowedByConfig,
                                policy.getMaxVolumesAllowed()));
                        continue;
                    }

                    // Fill in the descriptor to be used for VPlex backend placement
                    descriptor.placeVolumes(exportMaskURI, volumes);

                    // https://coprhd.atlassian.net/browse/COP-20497
                    // Fill in ExportMask place alternatives (if they exist). These alternative exports
                    // could be used as a backup in case the ExportMask placed here is determined to
                    // be invalid (by some higher-level validation).
                    for (URI volumeURI : volumes.keySet()) {
                        for (URI equivalentExport : descriptor.getEquivalentExportMasks(exportMaskURI)) {
                            descriptor.addAsAlternativeExportForVolume(volumeURI, exportMaskURI);
                        }
                    }
                }
            }
        } catch (Exception e) {
            _log.error("Exception while trying to get suggestions for ExportMasks to used for volumes", e);
        }
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

    /**
     * Create the context for native VmaxVolumeToExportMaskRuleApplicator
     *
     * @param workflow [IN] - Workflow to use for postApply() steps
     * @param exportGroup [IN] - ExportGroup
     * @param storage [IN] - StorageSystem
     * @param policyCache [IN] - ExportMask URI to ExportMaskPolicy
     * @param zoningStepNeeded [IN] - Indicates if zoning needs to be done in postApply() or not
     * @param token [IN] - Token representing the workflow step
     * @param initiatorHelper [IN] - Initiator helper object that has data related to initiators
     * @param initiatorToExportMaskPlacementMap [IN] - Mapping of port name (WWN/IQN) to set of ExportMask URIs that contain the port name
     * @param initiatorURIsCopy [IN]
     * @param partialMasks [IN] - Set of ExportMask URIs that have a subset of the initiators that are being exported to
     * @param volumeMap [IN] - Mapping of Volume URI to Integer HLU representing the volumes to export
     * @param initiatorsForNewExport [IN] - Initiators that will be added to the new ExportMask(s)
     * @param masksToUpdateWithVolumes [IN] - Mapping of ExportMask URI to Volumes to export
     * @param masksToUpdateWithInitiators [IN] - Mapping of ExportMask URI to set of Initiators to add to them
     * @param previousStep [IN] - Previous workflow steps to be used for postApply()
     * @return new VmaxVolumeToExportMaskApplicatorContext
     */
    private VmaxVolumeToExportMaskApplicatorContext createVmaxNativeApplicatorContext(Workflow workflow, ExportGroup exportGroup,
            StorageSystem storage, Map<URI, ExportMaskPolicy> policyCache, boolean zoningStepNeeded, String token,
            InitiatorHelper initiatorHelper, Map<String, Set<URI>> initiatorToExportMaskPlacementMap, List<URI> initiatorURIsCopy,
            Set<URI> partialMasks, Map<URI, Integer> volumeMap, Set<URI> initiatorsForNewExport,
            Map<URI, Map<URI, Integer>> masksToUpdateWithVolumes, Map<URI, Set<Initiator>> masksToUpdateWithInitiators,
            String previousStep) {
        VmaxVolumeToExportMaskApplicatorContext context = new VmaxVolumeToExportMaskApplicatorContext();
        context.exportGroup = exportGroup;
        context.storage = storage;
        context.workflow = workflow;
        context.zoningStepNeeded = zoningStepNeeded;
        context.initiatorURIsCopy = initiatorURIsCopy;
        context.initiatorsForNewExport = initiatorsForNewExport;
        context.partialMasks = partialMasks;
        context.token = token;
        context.exportMaskURIToPolicy = policyCache;
        context.masksToUpdateWithVolumes = masksToUpdateWithVolumes;
        context.masksToUpdateWithInitiators = masksToUpdateWithInitiators;
        context.initiatorHelper = initiatorHelper;
        context.volumeMap = volumeMap;
        context.initiatorToExportMaskPlacementMap = initiatorToExportMaskPlacementMap;
        context.previousStep = previousStep;
        return context;
    }

    /**
     * Create the context for VPlex backend VmaxVolumeToExportMaskRuleApplicator
     *
     * @param exportGroup [IN] - ExportGroup
     * @param storage [IN] - StorageSystem
     * @param policyCache [IN] - ExportMask URI to ExportMaskPolicy
     * @param initiatorHelper [IN] - Initiator helper object that has data related to initiators
     * @param initiatorToExportMaskPlacementMap [IN] - Mapping of port name (WWN/IQN) to set of ExportMask URIs that contain the port name
     * @param partialMasks [IN] - Set of ExportMask URIs that have a subset of the initiators that are being exported to
     * @param volumeMap [IN] - Mapping of Volume URI to Integer HLU representing the volumes to export
     * @param masksToUpdateWithVolumes [IN] - Mapping of ExportMask URI to Volumes to export
     * @return new VmaxVolumeToExportMaskApplicatorContext
     */
    private VmaxVolumeToExportMaskApplicatorContext createVPlexBackendApplicatorContext(ExportGroup exportGroup, StorageSystem storage,
            Map<URI, ExportMaskPolicy> policyCache, InitiatorHelper initiatorHelper,
            Map<String, Set<URI>> initiatorToExportMaskPlacementMap, Set<URI> partialMasks, Map<URI, Integer> volumeMap,
            Map<URI, Map<URI, Integer>> masksToUpdateWithVolumes) {
        VmaxVolumeToExportMaskApplicatorContext context = new VmaxVolumeToExportMaskApplicatorContext();
        context.storage = storage;
        context.partialMasks = partialMasks;
        context.exportMaskURIToPolicy = policyCache;
        context.masksToUpdateWithVolumes = masksToUpdateWithVolumes;
        context.initiatorHelper = initiatorHelper;
        context.volumeMap = volumeMap;
        context.initiatorToExportMaskPlacementMap = initiatorToExportMaskPlacementMap;
        // Not needed for VPlex
        context.exportGroup = exportGroup;
        context.initiatorURIsCopy = new ArrayList<>();
        context.initiatorsForNewExport = new HashSet<>();
        context.masksToUpdateWithInitiators = new HashMap<>();
        context.token = UUID.randomUUID().toString();
        return context;
    }

    private List<URI> getExpectedVolumes(ExportMask exportMask) {
        return ExportMaskUtils.getUserAddedVolumeURIs(exportMask);
    }

    private List<URI> getExpectedInitiators(ExportMask exportMask) {
        return newArrayList(ExportMaskUtils.getAllInitiatorsForExportMask(_dbClient, exportMask));
    }
    
    @Override
    public void changePortGroup(URI storageURI, URI exportGroupURI, URI portGroupURI, List<URI> exportMaskURIs, boolean waitForApproval,
            String token) {
        ExportChangePortGroupCompleter taskCompleter = null;
        try {
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
            StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageURI);
            StoragePortGroup portGroup = _dbClient.queryObject(StoragePortGroup.class, portGroupURI);
            taskCompleter = new ExportChangePortGroupCompleter(storageURI, exportGroupURI, token, portGroupURI);
            logExportGroup(exportGroup, storageURI);

            String workflowKey = "changePortGroup";
            if (_workflowService.hasWorkflowBeenCreated(token, workflowKey)) {
                return;
            }
            Workflow workflow = _workflowService.getNewWorkflow(
                    MaskingWorkflowEntryPoints.getInstance(), workflowKey, false, token);
            
			if (CollectionUtils.isEmpty(exportMaskURIs)) {
                _log.info("No export masks to change");
                taskCompleter.ready(_dbClient);
                return;
            }
            List<ExportMask> exportMasks = _dbClient.queryObject(ExportMask.class, exportMaskURIs);

            String previousStep = null;
            Set<URI> hostURIs = new HashSet<URI>();

            SmisStorageDevice device = (SmisStorageDevice) getDevice();
            for (ExportMask oldMask : exportMasks) {
                oldMask = device.refreshExportMask(storage, oldMask);
                StringSet existingInits = oldMask.getExistingInitiators();
                StringMap existingVols = oldMask.getExistingVolumes();
				if (!CollectionUtils.isEmpty(existingInits)) {
                    String error = String.format("The export mask %s has unmanaged initiators %s", oldMask.getMaskName(),
                            Joiner.on(',').join(existingInits));
                    _log.error(error);
                    ServiceError serviceError = DeviceControllerException.errors.changePortGroupValidationError(error);
                    taskCompleter.error(_dbClient, serviceError);
                    return;
                }
				if (!CollectionUtils.isEmpty(existingVols)) {
                    String error = String.format("The export mask %s has unmanaged volumes %s", oldMask.getMaskName(),
                            Joiner.on(',').join(existingVols.keySet()));
                    _log.error(error);
                    ServiceError serviceError = DeviceControllerException.errors.changePortGroupValidationError(error);
                    taskCompleter.error(_dbClient, serviceError);
                    return;
                }
                InitiatorHelper initiatorHelper = new InitiatorHelper(StringSetUtil.stringSetToUriList(oldMask.getInitiators())).process(exportGroup);
                List<String> initiatorNames = initiatorHelper.getPortNames();
                
                List<URI> volumes = StringSetUtil.stringSetToUriList(oldMask.getVolumes().keySet());
                ExportPathParams pathParams = _blockScheduler.calculateExportPathParamForVolumes(
                        volumes, 0, storageURI, exportGroupURI);
                pathParams.setStoragePorts(portGroup.getStoragePorts());
                List<Initiator> initiators = ExportUtils.getExportMaskInitiators(oldMask, _dbClient);
                List<URI> initURIs = new ArrayList<URI>();
                for (Initiator init : initiators) {
                    if (!NullColumnValueGetter.isNullURI(init.getHost())) {
                        hostURIs.add(init.getHost());
                    }
                    initURIs.add(init.getId());
                }
                
                // Get impacted export groups
                List<ExportGroup> impactedExportGroups = ExportMaskUtils.getExportGroups(_dbClient, oldMask);
                List<URI> exportGroupURIs = URIUtil.toUris(impactedExportGroups);
                _log.info("changePortGroup: exportMask {}, impacted export groups: {}", oldMask.getMaskName(),
                        Joiner.on(',').join(exportGroupURIs));
                device.refreshPortGroup(portGroupURI);
                
                // Trying to find if there is existing export mask or masking view for the same host and using the new
                // port group. If found one, add the volumes in the current export mask to the new one; otherwise, create
                // a new export mask/masking view, with the same storage group, initiator group and the new port group.
                // then delete the current export mask.
                ExportMask newMask = device.findExportMasksForPortGroupChange(storage, initiatorNames, portGroupURI);
                Map<URI, Integer> volumesToAdd = StringMapUtil.stringMapToVolumeMap(oldMask.getVolumes());
                
                if (newMask != null) {
                    updateZoningMap(exportGroup, newMask, true);
                    _log.info(String.format("adding these volumes %s to mask %s",
                            Joiner.on(",").join(volumesToAdd.keySet()), newMask.getMaskName()));
                    previousStep = generateZoningAddVolumesWorkflow(workflow, previousStep,
                            exportGroup, Arrays.asList(newMask), new ArrayList<URI>(volumesToAdd.keySet()));
                    
                    String addVolumeStep = workflow.createStepId();
                    ExportTaskCompleter exportTaskCompleter = new ExportMaskAddVolumeCompleter(
                            exportGroupURI, newMask.getId(), volumesToAdd, addVolumeStep);
                    exportTaskCompleter.setExportGroups(exportGroupURIs);

                    Workflow.Method maskingExecuteMethod = new Workflow.Method(
                            "doExportGroupAddVolumes", storageURI, exportGroupURI, newMask.getId(),
                            volumesToAdd, null, exportTaskCompleter);

                    Workflow.Method maskingRollbackMethod = new Workflow.Method(
                            "rollbackExportGroupAddVolumes", storageURI, exportGroupURI, exportGroupURIs,
                            newMask.getId(), volumesToAdd, initURIs, addVolumeStep);

                    previousStep = workflow.createStep(EXPORT_GROUP_MASKING_TASK,
                            String.format("Adding volumes to mask %s (%s)",
                                    newMask.getMaskName(), newMask.getId().toString()),
                            previousStep, storageURI, storage.getSystemType(),
                            MaskingWorkflowEntryPoints.class, maskingExecuteMethod,
                            maskingRollbackMethod, addVolumeStep);
                    previousStep = generateExportMaskAddVolumesWorkflow
                            (workflow, previousStep, storage, exportGroup, newMask, volumesToAdd, null);
                } else {
                    // We don't find existing export mask /masking view, we will create a new one.
                    // first, to construct the new export mask name, if the export mask has the original name, then 
                    // append the new port group name to the current export mask name; if the export mask already has the current
                    // port group name appended, then remove the current port group name, and append the new one.
                    Map<URI, List<URI>> assignments = _blockScheduler.assignStoragePorts(storage, exportGroup, initiators,
                            null, pathParams, volumes, _networkDeviceController, exportGroup.getVirtualArray(), token);
                    String oldName = oldMask.getMaskName();
                    URI oldPGURI = oldMask.getPortGroup();
                    if (oldPGURI != null) {
                        StoragePortGroup oldPG = _dbClient.queryObject(StoragePortGroup.class, oldPGURI);
                        if (oldPG != null) {
                            String pgName = oldPG.getLabel();
                            if (oldName.endsWith(pgName)) {
                                oldName = oldName.replaceAll(pgName, "");
                            }
                        }
                    }
                    String maskName = null;
                    if (oldName.endsWith("_")) {
                        maskName = String.format("%s%s", oldName, portGroup.getLabel());
                    } else {
                        maskName = String.format("%s_%s", oldName, portGroup.getLabel());
                    }

                    newMask = ExportMaskUtils.initializeExportMask(storage, exportGroup,
                            initiators, 
                            volumesToAdd, 
                            getStoragePortsInPaths(assignments), assignments, maskName, _dbClient);
        
                    newMask.setPortGroup(portGroupURI);
                
                    List<BlockObject> vols = new ArrayList<BlockObject>();
                    for (URI boURI : volumesToAdd.keySet()) {
                        BlockObject bo = BlockObject.fetch(_dbClient, boURI);
                        vols.add(bo);
                    }
                    newMask.addToUserCreatedVolumes(vols);
                    _dbClient.updateObject(newMask);
                    _log.info(String.format("Creating new exportMask %s", maskName));
                    // Make a new TaskCompleter for the exportStep. It has only one subtask.
                    // This is due to existing requirements in the doExportGroupCreate completion
                    // logic.
                    String maskingStep = workflow.createStepId();
                    ExportTaskCompleter exportTaskCompleter = new ExportMaskChangePortGroupAddMaskCompleter(
                            newMask.getId(), exportGroupURI, maskingStep);
                    exportTaskCompleter.setExportGroups(exportGroupURIs);
                    
                    Workflow.Method maskingExecuteMethod = new Workflow.Method(
                            "doExportChangePortGroupAddPaths", storageURI, exportGroupURI, newMask.getId(),
                            oldMask.getId(), portGroupURI, exportTaskCompleter);
        
                    Workflow.Method maskingRollbackMethod = new Workflow.Method(
                            "rollbackExportGroupCreate",
                            storageURI, exportGroupURI, newMask.getId(), maskingStep);
        
                    maskingStep = workflow.createStep(EXPORT_GROUP_MASKING_TASK,
                            String.format("Create export mask(%s) to use port group %s",
                                    newMask.getMaskName(), portGroup.getNativeGuid()),
                            previousStep, storageURI, storage.getSystemType(),
                            MaskingWorkflowEntryPoints.class, maskingExecuteMethod,
                            maskingRollbackMethod, maskingStep);
                    String zoningStep = workflow.createStepId();
                    List<URI> masks = new ArrayList<URI>();
                    masks.add(newMask.getId());
                    previousStep = generateZoningCreateWorkflow(workflow, maskingStep, exportGroup, masks, volumesToAdd, zoningStep);
                }
            }
            previousStep = _wfUtils.generateHostRescanWorkflowSteps(workflow, hostURIs, previousStep);
            if (waitForApproval) {
                // Insert a step that will be suspended. When it resumes, it will re-acquire the lock keys,
                // which are released when the workflow suspends.
                List<String> lockKeys = ControllerLockingUtil.getHostStorageLockKeys(
                        _dbClient, ExportGroup.ExportGroupType.valueOf(exportGroup.getType()),
                        StringSetUtil.stringSetToUriList(exportGroup.getInitiators()), storageURI);
                String suspendMessage = "Adjust/rescan host/cluster paths. Press \"Resume\" to start removal of unnecessary paths."
                        + "\"Rollback\" will terminate the order and roll back";
                Workflow.Method method = WorkflowService.acquireWorkflowLocksMethod(lockKeys,
                        LockTimeoutValue.get(LockType.EXPORT_GROUP_OPS));
                Workflow.Method rollbackNull = Workflow.NULL_METHOD;
                previousStep = workflow.createStep("AcquireLocks",
                        "Suspending for user verification of host/cluster connectivity.",
                        previousStep, storage.getId(),
                        storage.getSystemType(),
                        WorkflowService.class, method, rollbackNull, waitForApproval, null);
                workflow.setSuspendedStepMessage(previousStep, suspendMessage);
                    
            }

            for (ExportMask exportMask : exportMasks) {
                previousStep = generateChangePortGroupDeleteMaskWorkflowstep(storageURI, exportGroup, exportMask, previousStep, workflow);
            }

            _wfUtils.generateHostRescanWorkflowSteps(workflow, hostURIs, previousStep);

            if (!workflow.getAllStepStatus().isEmpty()) {
                _log.info("The change port group workflow has {} steps. Starting the workflow.",
                        workflow.getAllStepStatus().size());
                // update ExportChangePortGroupCompleter with affected export groups
                Set<URI> affectedExportGroups = new HashSet<URI> ();
                for (ExportMask mask : exportMasks) {
                    List<ExportGroup> assocExportGroups = ExportMaskUtils.getExportGroups(_dbClient, mask);
                    for (ExportGroup eg : assocExportGroups) {
                        affectedExportGroups.add(eg.getId());
                    }
                }
                taskCompleter.setAffectedExportGroups(affectedExportGroups);
                workflow.executePlan(taskCompleter, "Change port group successfully.");
                _workflowService.markWorkflowBeenCreated(token, workflowKey);
            } else {
                taskCompleter.ready(_dbClient);
            }
        } catch (Exception e) {
            _log.error("Export change port group Orchestration failed.", e);
            if (taskCompleter != null) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailedMsg(e.getMessage(), e);
                taskCompleter.error(_dbClient, serviceError);
            }

        }
    }
   
    /**
     * Generate change port group delete old mask steps
     * 
     * @param storageURI - Storage system URI
     * @param exportGroupURI - Export group URI
     * @param oldMaskURI - Old export mask URI
     * @param waitFor - The previous step
     * @param workflow - Workflow
     * @return - The last step it generates
     * @throws Exception
     */
    private String generateChangePortGroupDeleteMaskWorkflowstep(URI storageURI, ExportGroup exportGroup, ExportMask oldMask, String waitFor,
            Workflow workflow) throws Exception {
        StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageURI);
                        
        // Remove the old export mask.
        String maskingStep = workflow.createStepId();
        ExportTaskCompleter exportTaskCompleter = new ExportMaskDeleteCompleter(exportGroup.getId(),
                oldMask.getId(), maskingStep);
        StringMap volMap = oldMask.getVolumes();
        List<URI> volumes = null;
        if (volMap != null) {
            volumes = StringSetUtil.stringSetToUriList(volMap.keySet());
        }
        List<URI> inits = StringSetUtil.stringSetToUriList(oldMask.getInitiators());
        Workflow.Method maskingExecuteMethod = new Workflow.Method(
                "doExportGroupDelete", storageURI, exportGroup.getId(), oldMask.getId(), volumes, inits,
                exportTaskCompleter);
        _log.info(String.format("ExportMask %s is to be deleted:", oldMask.getMaskName()));

        Workflow.Method maskinkRollbackMethod = new Workflow.Method(
                "rollbackChangePortGroupDeleteMask", storageURI, exportGroup.getId(), oldMask.getId());
        maskingStep = workflow.createStep(EXPORT_GROUP_MASKING_TASK,
                String.format("Deleting mask %s (%s)", oldMask.getMaskName(),
                        oldMask.getId().toString()),
                waitFor, storageURI, storage.getSystemType(),
                MaskingWorkflowEntryPoints.class, maskingExecuteMethod, maskinkRollbackMethod,
                maskingStep);
            
        List<ExportMask> masks = new ArrayList<ExportMask>();
        masks.add(oldMask);
        String zoningStep = generateZoningDeleteWorkflow(workflow, maskingStep, exportGroup, masks);
        return zoningStep;
    }
    
    /**
     * Get storage ports in the paths
     * 
     * @param paths - The initiator to storage ports map
     * @return The list of storage ports presented in the paths
     */
    private List<URI> getStoragePortsInPaths(Map<URI, List<URI>> paths) {
        Set<URI> results = new HashSet<URI>();
        for (List<URI> ports : paths.values()) {
            for (URI port : ports) {
                results.add(port);
            }
        }
        return new ArrayList<URI>(results);
    }

    /**
     * Creates an ExportMask Workflow that generates a new ExportMask in an existing ExportGroup.
     *
     * @param workflow
     *            workflow to add steps to
     * @param previousStep
     *            previous step before these steps
     * @param storage
     *            storage system
     * @param exportGroup
     *            export group
     * @param initiatorURIs
     *            initiators impacted by this operation
     * @param volumeMap
     *            volumes
     * @param token
     *            step ID
     * @return URI of the new ExportMask
     * @throws Exception
     */
    @Override
    public GenExportMaskCreateWorkflowResult generateExportMaskCreateWorkflow(Workflow workflow,
            String previousStep,
            StorageSystem storage,
            ExportGroup exportGroup,
            List<URI> initiatorURIs,
            Map<URI, Integer> volumeMap,
            String token) throws Exception {
        URI exportGroupURI = exportGroup.getId();
        URI storageURI = storage.getId();

        List<Initiator> initiators = null;
        if (!CollectionUtils.isEmpty(initiatorURIs)) {
            initiators = _dbClient.queryObject(Initiator.class, initiatorURIs);
        } else {
            _log.error("Internal Error: Need to add the initiatorURIs to the call that assembles this step.");
        }

        // Create and initialize the Export Mask. This involves assigning and
        // allocating the Storage Ports (targets).
        ExportPathParams pathParams = _blockScheduler.calculateExportPathParamForVolumes(
                volumeMap.keySet(), exportGroup.getNumPaths(), storage.getId(), exportGroup.getId());
        if (exportGroup.getType() != null) {
            pathParams.setExportGroupType(exportGroup.getType());
        }
        if (exportGroup.getZoneAllInitiators()) {
            pathParams.setAllowFewerPorts(true);
        }
        URI portGroupURI = null;
        List<URI> pgPorts = null;
        if (pathParams.getPortGroup() != null) {
            portGroupURI = pathParams.getPortGroup();
            getDevice().refreshPortGroup(portGroupURI);
            StoragePortGroup portGroup = _dbClient.queryObject(StoragePortGroup.class, portGroupURI);
            _log.info(String.format("port group is %s", portGroup.getLabel()));
            pgPorts = StringSetUtil.stringSetToUriList(portGroup.getStoragePorts());
            if (!CollectionUtils.isEmpty(pgPorts)) {
            	StringSet pgPortsURISet = StringSetUtil.uriListToStringSet(pgPorts);
            	if (pathParams.getStoragePorts().isEmpty()) {
            		pathParams.setStoragePorts(pgPortsURISet);
            	} else {
            		pathParams.getStoragePorts().replace(pgPortsURISet);
            	}
            } else {
                _log.error(String.format("The port group %s does not have any port members", portGroup));
                throw DeviceControllerException.exceptions.noPortMembersInPortGroupError(portGroup.getLabel());
            }
        }
        Map<URI, List<URI>> assignments = _blockScheduler.assignStoragePorts(storage, exportGroup,
                initiators, null, pathParams, volumeMap.keySet(), _networkDeviceController, exportGroup.getVirtualArray(), token);
        List<URI> targets = (pgPorts != null) ? pgPorts : BlockStorageScheduler.getTargetURIsFromAssignments(assignments);

        String maskName = useComputedMaskName() ? getComputedExportMaskName(storage, exportGroup, initiators) : null;

        // TODO: Bharath - This might NOT be the best way to do this. look into getComputerExportMaskName to see if this
        // can be done differently
        if (exportGroup.checkInternalFlags(Flag.RECOVERPOINT_JOURNAL)) {
            maskName += "_journal";
        }

        ExportMask exportMask = ExportMaskUtils.initializeExportMask(storage, exportGroup,
                initiators, volumeMap, targets, assignments, maskName, _dbClient);
        if (portGroupURI != null) {
            exportMask.setPortGroup(portGroupURI);
        }
        List<BlockObject> vols = new ArrayList<BlockObject>();
        for (URI boURI : volumeMap.keySet()) {
            BlockObject bo = BlockObject.fetch(_dbClient, boURI);
            vols.add(bo);
        }
        exportMask.addToUserCreatedVolumes(vols);
        _dbClient.updateObject(exportMask);
        // Make a new TaskCompleter for the exportStep. It has only one subtask.
        // This is due to existing requirements in the doExportGroupCreate completion
        // logic.
        String maskingStep = workflow.createStepId();
        ExportTaskCompleter exportTaskCompleter = new ExportMaskCreateCompleter(
                exportGroupURI, exportMask.getId(), initiatorURIs, volumeMap,
                maskingStep);

        Workflow.Method maskingExecuteMethod = new Workflow.Method(
                "doExportGroupCreate", storageURI, exportGroupURI, exportMask.getId(),
                volumeMap, initiatorURIs, targets, exportTaskCompleter);

        Workflow.Method maskingRollbackMethod = new Workflow.Method(
                "rollbackExportGroupCreate",
                storageURI, exportGroupURI, exportMask.getId(), maskingStep);

        maskingStep = workflow.createStep(EXPORT_GROUP_MASKING_TASK,
                String.format("Creating mask %s (%s)",
                        exportMask.getMaskName(), exportMask.getId().toString()),
                previousStep, storageURI, storage.getSystemType(),
                MaskingWorkflowEntryPoints.class, maskingExecuteMethod,
                maskingRollbackMethod, maskingStep);

        return new GenExportMaskCreateWorkflowResult(exportMask.getId(), maskingStep);
    }

    /**
     * Native VMAX rules application. This should run the volume to ExportMask matching rules, then do
     * post processing work such that the proper workflow steps are added for new ExportMasks (if any)
     */
    class NativeVolumeToExportMaskRuleApplicator extends VmaxVolumeToExportMaskRuleApplicator {

        public NativeVolumeToExportMaskRuleApplicator(DbClient dbClient,
                VmaxVolumeToExportMaskApplicatorContext context) {
            super(dbClient, context);
        }

        /**
         * After the rules have been applied, the VMAX native implementation requires that we set up
         * workflow steps for any of those volumes that could not be placed. There are additional
         * contextual information that needs to be updated (like the list of initiators that were
         * placed and initiators that need to be added to existing ExportMasks)
         *
         * @param initiatorsForResource [IN] - Initiators that are applicable to a resource
         * @param initiatorsToVolumes [IN] - Mapping of initiators to Volumes
         * @throws Exception
         */
        @Override
        public void postApply(List<URI> initiatorsForResource, Map<URI, Map<URI, Integer>> initiatorsToVolumes) throws Exception {
            String token = context.token;
            ExportGroup exportGroup = context.exportGroup;
            Set<URI> initiatorsForNewExport = context.initiatorsForNewExport;
            List<URI> initiatorURIsCopy = context.initiatorURIsCopy;

            // This is the case where we couldn't find a mask that was appropriate to add the volumes,
            // even though several masks matched the export mask criteria at first.
            if (!initiatorsToVolumes.isEmpty()) {
                List<URI> leftoverInitiatorsForNewExport = new ArrayList<URI>();
                // Figure out the initiators we "missed" for the volumes in this loop
                for (URI initiatorId : initiatorsToVolumes.keySet()) {
                    Initiator initiator = _dbClient.queryObject(Initiator.class, initiatorId);
                    if (initiator != null) {
                        leftoverInitiatorsForNewExport.add(initiator.getId());
                        initiatorsForNewExport.remove(initiator.getId());
                    }
                }
                Map<String, List<URI>> computeResourceToInitiators = mapInitiatorsToComputeResource(exportGroup,
                        leftoverInitiatorsForNewExport);
                for (Entry<String, List<URI>> resourceEntry : computeResourceToInitiators.entrySet()) {
                    String computeKey = resourceEntry.getKey();
                    List<URI> computeInitiatorURIs = resourceEntry.getValue();
                    Initiator initiator = _dbClient.queryObject(Initiator.class, computeInitiatorURIs.get(0));
                    _log.info(String.format("Residual mask needed: New export masks for %s", computeKey));
                    GenExportMaskCreateWorkflowResult result = generateExportMaskCreateWorkflow(context.workflow, context.previousStep,
                            context.storage, exportGroup, computeInitiatorURIs, initiatorsToVolumes.get(initiator.getId()), token);
                    context.flowCreated = true;
                    context.previousStep = result.getStepId();
                    // Add zoning
                    if (context.zoningStepNeeded) {
                        String zoningStep = context.workflow.createStepId();
                        List<URI> masks = new ArrayList<URI>();
                        masks.add(result.getMaskURI());
                        context.previousStep = generateZoningCreateWorkflow(context.workflow, context.previousStep, exportGroup, masks,
                                context.volumeMap,
                                zoningStep);
                    }
                }
            }

            // Now if our efforts to find homes for the volumes in existing masking views yielded success,
            // this is the time to trim down the list of initiators that will be used to create NEW masking
            // views. Go through the initiators for the resource we originally used to populate the "volumesWithNoMask"
            // map and see if any volumes still exist. If not, remove those "new" initiators.
            for (URI initiatorId : initiatorsForResource) {
                if (initiatorsToVolumes.get(initiatorId) == null) {
                    if (initiatorURIsCopy.remove(initiatorId)) {
                        _log.info("Determined that we do not need to create a new mask for initiator [1]: " + initiatorId);
                    }
                    if (initiatorsForNewExport.remove(initiatorId)) {
                        _log.info("Determined that we do not need to create a new mask for initiator [2]: " + initiatorId);
                    }
                }
            }
        }

        /**
         * This is central to the VmaxVolumeToExportMaskRuleApplicator class. We are essentially trying to have a wrapper around this
         * method because it needs to be called in different contexts. One context is when this is a native VMAX export operation.
         * This context requires workflow steps, so this what the postApply() is for. The second context is when we are suggesting
         * ExportMasks to use for a set of initiators for the VPlex backend. The VPlex backend workflow bypasses these
         * MaskingOrchestrator implementation and creates steps that reference the ExportMaskOperations. So, there's no need to
         * have extra workflow steps done after applyRules. We are trying to get only the rules to run so that we have a set of
         * ExportMasks that we can suggest for the VPlex backend workflow.
         *
         * @param initiatorsToVolumes [IN] - Initiators that are applicable to a resource
         *
         * @return true if the operation was successful. In this case, the contextual data would be populated.
         */
        @Override
        public boolean applyRules(Map<URI, Map<URI, Integer>> initiatorsToVolumes) {
            return applyVolumesToMasksUsingRules(context.storage, context.exportGroup, context.masksToUpdateWithVolumes,
                    initiatorsToVolumes, context.exportMaskToPolicy, context.masksToUpdateWithInitiators, context.partialMasks,
                    context.token);
        }
    }

    /**
     * VPlexBackend ExportMask rule selection is the NativeVolume rule selection minus the post processing.
     * It should just run the rules and the results will be used for ExportMask selection interface implementation:
     * VmaxMaskingOrchestrator#suggestExportMasksForPlacement
     */
    class VplexBackendVolumeToExportMaskRuleApplicator extends NativeVolumeToExportMaskRuleApplicator {

        public VplexBackendVolumeToExportMaskRuleApplicator(DbClient dbClient, VmaxVolumeToExportMaskApplicatorContext context) {
            super(dbClient, context);
        }

        /**
         * This is on overridden with no implementation on purpose. We do not need to do the same
         * post processing that the native VMAX does. Effectively, we need to call applyRules() only.
         *
         * @param initiatorsForResource [IN] - Initiators that are applicable to a resource
         * @param initiatorsToVolumes [IN] - Mapping of initiators to Volumes
         * @throws Exception
         */
        @Override
        public void postApply(List<URI> initiatorsForResource, Map<URI, Map<URI, Integer>> initiatorsToVolumes)
                throws Exception {
        }
    }
}
