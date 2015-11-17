/*
 * Copyright 2015 EMC Corporation
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
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportGroup.ExportGroupType;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.ExportPathParams;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.volumecontroller.BlockStorageDevice;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportDeregisterInitiatorCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskAddInitiatorCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportOrchestrationTask;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.SnapshotWorkflowEntryPoints;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeUpdateCompleter;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.volumecontroller.placement.BlockStorageScheduler;
import com.emc.storageos.workflow.Workflow;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Collections2;
import com.google.common.collect.ListMultimap;

/**
 * This class will contain VNX specific masking orchestration implementations.
 * The goal of this implementation would be to flexibly support export
 * operations. Essentially, the export operations need to be amenable to the
 * existence of exports created outside of the system. It should take to make
 * sure that it does what it can to allow the operation to succeed in light of
 * such cases.
 * 
 * TODO: You'll notice several areas of code are very similar.
 * Recommend refactor to consolidate methods:
 * - Create, AddVolumes, AddInitiators should have its main BL in one place
 * - Delete, RemoveVolumes, RemoveInitiators should have its main BL in one place
 * 
 */
public class VnxMaskingOrchestrator extends AbstractBasicMaskingOrchestrator {
    private static final Logger _log = LoggerFactory.getLogger(VnxMaskingOrchestrator.class);

    private static final AtomicReference<BlockStorageDevice> VNX_BLOCK_DEVICE = new AtomicReference<BlockStorageDevice>();
    public static final String VNX_SMIS_DEVICE = "vnxSmisDevice";
    public static final String DEFAULT_LABEL = "Default";

    @Override
    public BlockStorageDevice getDevice() {
        BlockStorageDevice device = VNX_BLOCK_DEVICE.get();
        synchronized (VNX_BLOCK_DEVICE) {
            if (device == null) {
                device = (BlockStorageDevice) ControllerServiceImpl.getBean(VNX_SMIS_DEVICE);
                VNX_BLOCK_DEVICE.compareAndSet(null, device);
            }
        }
        return device;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.volumecontroller.impl.block.AbstractDefaultMaskingOrchestrator#generateExportMaskAddInitiatorsWorkflow(com.emc.
     * storageos.workflow.Workflow, java.lang.String, com.emc.storageos.db.client.model.StorageSystem,
     * com.emc.storageos.db.client.model.ExportGroup, com.emc.storageos.db.client.model.ExportMask, java.util.List, java.util.Set,
     * java.lang.String)
     */
    @Override
    public String generateExportMaskAddInitiatorsWorkflow(Workflow workflow,
            String previousStep,
            StorageSystem storage,
            ExportGroup exportGroup,
            ExportMask exportMask,
            List<URI> initiatorURIs,
            Set<URI> newVolumeURIs,
            String token)
            throws Exception {
        URI exportGroupURI = exportGroup.getId();
        URI exportMaskURI = exportMask.getId();
        URI storageURI = storage.getId();
        List<URI> newTargetURIs = new ArrayList<>();

        // Only update the ports of a mask that we created.
        List<Initiator> initiators =
                _dbClient.queryObject(Initiator.class, initiatorURIs);
        // Allocate any new ports that are required for the initiators
        // and update the zoning map in the exportMask.
        Collection<URI> volumeURIs = (exportMask.getVolumes() == null) ? newVolumeURIs :
                (Collection<URI>) (Collections2.transform(exportMask.getVolumes().keySet(),
                        CommonTransformerFunctions.FCTN_STRING_TO_URI));
        ExportPathParams pathParams = _blockScheduler.calculateExportPathParamForVolumes(
                volumeURIs, exportGroup.getNumPaths(), storage.getId(), exportGroup.getId());
        if (exportGroup.getType() != null) {
            pathParams.setExportGroupType(exportGroup.getType());
        }
        Map<URI, List<URI>> assignments = _blockScheduler.assignStoragePorts(storage, exportGroup, initiators,
                exportMask.getZoningMap(), pathParams, volumeURIs, _networkDeviceController, exportGroup.getVirtualArray(), token);
        newTargetURIs = BlockStorageScheduler.getTargetURIsFromAssignments(assignments);
        exportMask.addZoningMap(BlockStorageScheduler.getZoneMapFromAssignments(assignments));
        _dbClient.persistObject(exportMask);

        String maskingStep = workflow.createStepId();
        ExportTaskCompleter exportTaskCompleter = new ExportMaskAddInitiatorCompleter(
                exportGroupURI, exportMask.getId(), initiatorURIs, newTargetURIs,
                maskingStep);

        Workflow.Method maskingExecuteMethod = new Workflow.Method(
                "doExportGroupAddInitiators", storageURI, exportGroupURI,
                exportMaskURI, initiatorURIs, newTargetURIs, exportTaskCompleter);

        Workflow.Method rollbackMethod = new Workflow.Method(
                "rollbackExportGroupAddInitiators", storageURI, exportGroupURI,
                exportMaskURI, initiatorURIs, maskingStep);

        maskingStep = workflow.createStep(EXPORT_GROUP_MASKING_TASK,
                String.format("Adding initiators to mask %s (%s)",
                        exportMask.getMaskName(), exportMask.getId().toString()),
                previousStep, storageURI, storage.getSystemType(),
                MaskingWorkflowEntryPoints.class, maskingExecuteMethod,
                rollbackMethod, maskingStep);

        return maskingStep;
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
            logExportGroup(exportGroup, storageURI);

            if (initiatorURIs != null && !initiatorURIs.isEmpty()) {
                _log.info("export_create: initiator list non-empty");

                // Set up workflow steps.
                Workflow workflow = _workflowService.getNewWorkflow(
                        MaskingWorkflowEntryPoints.getInstance(), "exportGroupCreate", true, token);

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
                } else {
                    _log.info("export_create: no steps created.");
                    taskCompleter.ready(_dbClient);
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
            logExportGroup(exportGroup, storageURI);
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
                        // info from the array
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

                            // This is the list of export masks where volumes will be added
                            // some may be user-created and being 'accepted' into ViPR for
                            // the first time. Need to update zoning map
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
    protected boolean useComputedMaskName() {
        return true;
    }

    @Override
    protected String getMaskingCustomConfigTypeName(String exportType) {
        return CustomConfigConstants.VNX_HOST_STORAGE_GROUP_MASK_NAME;
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
     * @param zoningStepNeeded - No specific logic required for VNX as zoning is taken care of already.
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
        Map<String, Set<URI>> matchingExportMaskURIs =
                device.findExportMasks(storage, portNames, false);
        if (matchingExportMaskURIs.isEmpty()) {
            previousStep =
                    checkForSnapshotsToCopyToTarget(workflow, storage, previousStep,
                            volumeMap, null);

            _log.info(String.format("No existing mask found w/ initiators { %s }", Joiner.on(",")
                    .join(portNames)));
            previousStep = createNewExportMaskWorkflowForInitiators(initiatorURIs, exportGroup, workflow, volumeMap, storage, token,
                    previousStep);
            flowCreated = true;
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
                    getDevice().refreshExportMask(storage, mask);

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
                        if (bo != null && !mask.hasExistingVolume(bo)) {
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
                            // DO NOT CHECK-IN!  WJEIV  Email out to Ameer.  After latest merge from master, -1 isn't working anymore
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

            previousStep =
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
                    GenExportMaskCreateWorkflowResult result =
                            generateExportMaskCreateWorkflow(workflow, previousStep, storage, exportGroup,
                                    hostInitiatorMap.get(hostID), volumeMap, token);
                    previousStep = result.getStepId();
                    flowCreated = true;
                }
            }

            for (Map.Entry<URI, Map<URI, Integer>> entry : existingMasksToUpdateWithNewVolumes
                    .entrySet()) {
                ExportMask mask = _dbClient.queryObject(ExportMask.class, entry.getKey());
                Map<URI, Integer> volumesToAdd = entry.getValue();
                _log.info(String.format("adding these volumes %s to mask %s",
                        Joiner.on(",").join(volumesToAdd.keySet()), mask.getMaskName()));
                previousStep = generateExportMaskAddVolumesWorkflow(workflow, previousStep, storage, exportGroup, mask,
                        volumesToAdd);
                flowCreated = true;
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
                // To make the right pathing assignments, send down the volumes we are going to add to this mask, if available.
                previousStep = generateExportMaskAddInitiatorsWorkflow(workflow, previousStep, storage, exportGroup, mask,
                        initiatorsURIs,
                        existingMasksToUpdateWithNewVolumes.get(entry.getKey()) != null ?
                                existingMasksToUpdateWithNewVolumes.get(entry.getKey()).keySet() : null,
                        token);
                flowCreated = true;
            }
        }
        return flowCreated;
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

    @Override
    public GenExportMaskCreateWorkflowResult generateDeviceSpecificExportMaskCreateWorkFlow(Workflow workflow,
            String zoningGroupId, StorageSystem storage, ExportGroup exportGroup,
            List<URI> hostInitiators, Map<URI, Integer> volumeMap, String token) throws Exception {
        return generateExportMaskCreateWorkflow(workflow, null, storage, exportGroup, hostInitiators, volumeMap, token);
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

    @Override
    public void increaseMaxPaths(Workflow workflow, StorageSystem storageSystem, ExportGroup exportGroup,
            ExportMask exportMask, List<URI> newInitiators, String token) throws Exception {
        // Increases the MaxPaths for a given ExportMask if it has Initiators
        // that are not currently zoned to ports. The method
        // generateExportMaskAddInitiatorsWorkflow will
        // allocate additional ports for the newInitiators to be processed.
        // These will be zoned and then subsequently added to the MaskingView /
        // ExportMask.
        Map<URI, List<URI>> zoneMasksToInitiatorsURIs = new HashMap<URI, List<URI>>();
        zoneMasksToInitiatorsURIs.put(exportMask.getId(), newInitiators);

        String deregisterInitiatorStep = workflow.createStepId();
        ExportTaskCompleter completer = new ExportDeregisterInitiatorCompleter(exportGroup.getId(),
                exportMask.getId(), newInitiators, deregisterInitiatorStep);
        // These new initiators will be first removed from the storagesystem for
        // the following reason:
        // There seems to be a bug in provider or some other place for VNX only where
        // in a case when initiators are already registered and associated with the
        // storage groups, when these initiators are zoned and registered again
        // they don't get associated with the storage groups and hence these
        // new initiators don't have connectivity to the volumes. Thus it is done
        // this way to ensure newly zoned initiators have connectivity to volumes.
        String removeInitiatorStep = generateExportMaskRemoveInitiatorsWorkflow(workflow, null,
                storageSystem, exportGroup, exportMask, newInitiators, true, completer);
        String zoningStep = generateZoningAddInitiatorsWorkflow(workflow, removeInitiatorStep,
                exportGroup, zoneMasksToInitiatorsURIs);
        generateExportMaskAddInitiatorsWorkflow(workflow, zoningStep, storageSystem, exportGroup, exportMask,
                newInitiators, null, token);
    }

    /**
     * Overridden implementation of createNewExportMaskWorkflowForInitiators for VNX. The difference
     * with this implementation and the superclass' is that here the creates will be run sequentially.
     */
    @Override
    protected String createNewExportMaskWorkflowForInitiators(List<URI> initiatorURIs,
            ExportGroup exportGroup, Workflow workflow, Map<URI, Integer> volumeMap,
            StorageSystem storage, String token, String previousStep) throws Exception {
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

        return previousStep;
    }

    @Override
    public void exportGroupChangePolicyAndLimits(URI storageURI, URI exportMaskURI,
            URI exportGroupURI, List<URI> volumeURIs, URI newVpoolURI,
            boolean rollback, String token) throws Exception {
        // EXportGroup and ExportMask URIs will be null for VNX.
        VolumeUpdateCompleter taskCompleter = new VolumeUpdateCompleter(
                volumeURIs, token);

        StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageURI);
        VirtualPool newVpool = _dbClient.queryObject(VirtualPool.class, newVpoolURI);
        BlockStorageDevice device = getDevice();
        device.updatePolicyAndLimits(storage, null, volumeURIs, newVpool,
                rollback, taskCompleter);
    }

}
