/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.externaldevice;


import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.db.client.model.BlockObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.util.StorageDriverManager;
import com.emc.storageos.volumecontroller.BlockStorageDevice;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.block.AbstractMaskingFirstOrchestrator;
import com.emc.storageos.volumecontroller.impl.block.MaskingWorkflowEntryPoints;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportOrchestrationTask;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportTaskCompleter;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowService;
import com.google.common.base.Joiner;

public class ExternalDeviceMaskingOrchestrator extends AbstractMaskingFirstOrchestrator {

    private static final Logger _log = LoggerFactory.getLogger(ExternalDeviceMaskingOrchestrator.class);
    private StorageDriverManager driverManager = null;
    private BlockStorageDevice device = null;

    @Override
    public synchronized BlockStorageDevice getDevice() {
        if (device == null) {
            device = (BlockStorageDevice) ControllerServiceImpl.getBean(StorageDriverManager.EXTERNAL_STORAGE_DEVICE);
        }
        return device;
    }

    public synchronized StorageDriverManager getDriverManager() {
        if (driverManager == null) {
            driverManager = (StorageDriverManager) ControllerServiceImpl.getBean(StorageDriverManager.STORAGE_DRIVER_MANAGER);
        }
        return driverManager;
    }

    @Override
    public void createWorkFlowAndSubmitForExportGroupCreate(List<URI> initiatorURIs, Map<URI, Integer> volumeMap, String token,
            ExportOrchestrationTask taskCompleter, BlockStorageDevice device,
            ExportGroup exportGroup, StorageSystem storage) throws Exception {
        // Check that storage system is driver managed.
        StorageDriverManager storageDriverManager = getDriverManager();
        if (!storageDriverManager.isDriverManaged(storage.getSystemType())) {
            throw DeviceControllerException.exceptions.invalidSystemType(storage.getSystemType());
        }

        _log.info("Started export group processing.");
        // Get new work flow to setup steps for export group creation
        Workflow workflow = _workflowService.getNewWorkflow(
                MaskingWorkflowEntryPoints.getInstance(), "exportGroupCreate",
                true, token);

        // Create two steps, one for the ExportGroup actions and one for Zoning.
        List<String> maskingSteps = generateExportGroupCreateSteps(workflow, null,
                device, storage, exportGroup, initiatorURIs, volumeMap, false, token);

        // Have to store export group id to be available at device level for each masking step.
        for (String stepId : maskingSteps) {
            WorkflowService.getInstance().storeStepData(stepId, exportGroup.getId());
        }

        /*
         * This step is for zoning. It is not specific to a single
         * NetworkSystem, as it will look at all the initiators and targets and
         * compute the zones required (which might be on multiple
         * NetworkSystems.)
         *
         * Dependency task for the zoning execution is
         * EXPORT_GROUP_MASKING_TASK, hence enforcing the masking to be executed
         * before zoning is attempted
         */
        String zoningStep = generateDeviceSpecificZoningCreateWorkflow(
                workflow, EXPORT_GROUP_MASKING_TASK, exportGroup, null,
                volumeMap);

        if (!maskingSteps.isEmpty() && null != zoningStep) {
            // Execute the plan and allow the WorkflowExecutor to fire the
            // taskCompleter.
            workflow.executePlan(taskCompleter, String.format(
                    "ExportGroup successfully applied for StorageArray %s",
                    storage.getLabel()));
        }
    }

    /**
     * Generates export group create steps for a given set of initiators and volumes.
     * Only "greenfield" case is supported --- if export masks with one or more of given
     * initiators exist, we will fail the request.
     *
     * @param workflow
     * @param previousStep
     * @param device
     * @param storage
     * @param exportGroup
     * @param initiatorURIs
     * @param volumeMap volume URI to HLU map
     * @param zoneStepNeeded
     * @param token
     * @return list of step Ids
     * @throws Exception
     */
    private List<String> generateExportGroupCreateSteps(Workflow workflow,
                                                 String previousStep, BlockStorageDevice device,
                                                 StorageSystem storage, ExportGroup exportGroup,
                                                 List<URI> initiatorURIs, Map<URI, Integer> volumeMap, boolean zoneStepNeeded, String token)
            throws Exception {
        Map<String, URI> portNameToInitiatorURI = new HashMap<>();
        List<URI> hostURIs = new ArrayList<>();
        List<String> portNames = new ArrayList<>(); // host initiator names
        String stepId;
        _log.info("Started export mask steps generation.");
        /*
         * Populate the port WWN/IQNs (portNames) and the mapping of the
         * WWN/IQNs to Initiator URIs
         */
        processInitiators(exportGroup, initiatorURIs, portNames, portNameToInitiatorURI, hostURIs);
        _log.info("Done with initiator processing.");
        /*
         * We always want to have the full list of initiators for the hosts
         * involved in this export. This will allow the export operation to
         * always find any existing exports for a given host.
         */
        queryHostInitiatorsAndAddToList(portNames, portNameToInitiatorURI, initiatorURIs, hostURIs);

        /*
         * Find the export masks that are associated with any or all the ports
         * in portNames. We will have to do processing differently based on
         * whether or there is an existing ExportMasks.
         */
        Map<String, Set<URI>> matchingExportMaskURIs = device.findExportMasks(storage, portNames, false);
        _log.info("Done with matching export masks.");

        List<String> newSteps;
        if (matchingExportMaskURIs == null || matchingExportMaskURIs.isEmpty()) {

            _log.info(String.format(
                    "No existing mask found w/ initiators { %s }",
                    Joiner.on(",").join(portNames)));

            newSteps = createNewExportMaskWorkflowForInitiators(initiatorURIs,
                    exportGroup, workflow, volumeMap, storage, token,
                    previousStep);
        } else {
            _log.info(String.format("Mask(s) found w/ initiators {%s}. "
                            + "MatchingExportMaskURIs {%s}, portNameToInitiators {%s}",
                    Joiner.on(",").join(portNames),
                    Joiner.on(",").join(matchingExportMaskURIs.keySet()),
                    Joiner.on(",").join(portNameToInitiatorURI.entrySet())));

            /*
             * TODO: TBD. use case not supported for External device (brown field).
             */
            throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
        }
        return newSteps;
    }

    @Override
    public void createWorkFlowAndSubmitForAddVolumes(URI storageURI,
            URI exportGroupURI, Map<URI, Integer> volumeMap, String token,
            ExportTaskCompleter taskCompleter, ExportGroup exportGroup,
            StorageSystem storage) throws Exception {
        // Note: We support only case when add volumes to export group does not change storage ports in
        // existing export masks where volumes are added.
        // Since we only execute masking step on device for existing masks --- no zoning change is required.
        List<ExportMask> exportMasks = ExportMaskUtils.getExportMasks(_dbClient,
                exportGroup, storageURI);
        if (exportMasks != null && !exportMasks.isEmpty()) {
            // Set up work flow steps.
            Workflow workflow = _workflowService.getNewWorkflow(
                    MaskingWorkflowEntryPoints.getInstance(),
                    "exportGroupAddVolumes - Added volumes to existing mask",
                    true, token);

            // For each export mask in export group, invoke add Volumes if export Mask belongs to the storage array
            // of added volumes. Export group may have export masks for the same array but for different compute resources
            // (hosts or clusters).
            for (ExportMask exportMask : exportMasks) {
                if (exportMask.getStorageDevice().equals(storageURI)) {
                    _log.info("export_volume_add: adding volume to an existing export");
                    exportMask.addVolumes(volumeMap);
                    // Have to add volumes to user created volumes set in the mask since
                    // generateExportMaskAddVolumesWorkflow() call below does not do this.
                    for (URI volumeUri : volumeMap.keySet()) {
                        BlockObject volume = (BlockObject) _dbClient.queryObject(volumeUri);
                        exportMask.addToUserCreatedVolumes(volume);
                    }
                    _dbClient.updateObject(exportMask);

                    List<URI> volumeURIs = new ArrayList<>();
                    volumeURIs.addAll(volumeMap.keySet());

                    String maskingStep = generateExportMaskAddVolumesWorkflow(workflow,
                            null, storage, exportGroup, exportMask, volumeMap);

                    // We do not need zoning step, since storage ports should not change.
                    // Have to store export group id to be available at device level.
                    WorkflowService.getInstance().storeStepData(maskingStep, exportGroup.getId());
                }
            }

            String successMessage = String.format(
                    "Volumes successfully added to export on StorageArray %s",
                    storage.getLabel());
            workflow.executePlan(taskCompleter, successMessage);
        } else {
            // This is the case when export group does not have export mask for storage array where the volumes belongs.
            // In this case we will create new export masks for the storage array and each compute resource in the export group.
            // Essentially for every existing mask we will add a new mask for the array and initiators in the existing mask.
            if (exportGroup.getInitiators() != null
                    && !exportGroup.getInitiators().isEmpty()) {
                _log.info("export_volume_add: adding volume, creating a new export mask");

                List<URI> initiatorURIs = new ArrayList<>();
                for (String initiatorId : exportGroup.getInitiators()) {
                    Initiator initiator = _dbClient.queryObject(
                            Initiator.class, URI.create(initiatorId));
                    initiatorURIs.add(initiator.getId());
                }

                // Get new workflow to setup steps for export masks creation
                Workflow workflow = _workflowService.getNewWorkflow(
                        MaskingWorkflowEntryPoints.getInstance(), "exportGroupCreate",
                        true, token);

                // This call will create steps for a new mask for each compute resource
                // and new volumes. For example, if there are 3 compute resources in the group,
                // the step will create 3 new masks for these resources and new volumes.
                List<String> maskingSteps = createNewExportMaskWorkflowForInitiators(initiatorURIs,
                        exportGroup, workflow, volumeMap, storage, token,
                        null);

                // Have to store export group id to be available at device level for each masking step.
                for (String stepId : maskingSteps) {
                    WorkflowService.getInstance().storeStepData(stepId, exportGroup.getId());
                }

                generateZoningCreateWorkflow(workflow,
                        EXPORT_GROUP_MASKING_TASK, exportGroup, null,
                        volumeMap);

                String successMessage = String
                        .format("Volumes successfully added to export StorageArray %s",
                                storage.getLabel());

                workflow.executePlan(taskCompleter, successMessage);
            } else {
                _log.info("Export group doesn't have initiators.");
                taskCompleter.ready(_dbClient);
            }
        }
    }

    @Override
    public void exportGroupAddInitiators(URI storageURI, URI exportGroupURI, List<URI> initiatorURIs, String token)
            throws Exception {
        /* Map new initiators to existing export masks in the export group based on common compute resource (each mask belongs to
         * specific compute resource and there can be only one mask for storage array and compute resource).
         * Add initiators to the corresponding export masks found in the mapping.
         * For initiators which do not have corresponding export mask we will create new export mask for compute resource and
         * each storage array in the group.
         *
         */
        // Todo: tbd.
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }

    @Override
    public void exportGroupRemoveInitiators(URI storageURI, URI exportGroupURI,
                                            List<URI> initiatorURIs, String token) throws Exception {
        // Todo: tbd.
        throw DeviceControllerException.exceptions
                .blockDeviceOperationNotSupported();
    }
}
