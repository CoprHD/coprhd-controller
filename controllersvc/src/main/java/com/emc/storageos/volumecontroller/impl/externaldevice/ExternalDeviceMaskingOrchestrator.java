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
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
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

    public static final String EXTERNAL_STORAGE_DEVICE = "externalBlockStorageDevice";
    public static final String STORAGE_DRIVER_MANAGER = "storageDriverManager";
    private static final Logger _log = LoggerFactory.getLogger(ExternalDeviceMaskingOrchestrator.class);
    private static final AtomicReference<StorageDriverManager> DRIVER_MANAGER = new AtomicReference<>();
    private static final AtomicReference<BlockStorageDevice> EXTERNAL_BLOCK_DEVICE = new AtomicReference<>();

    @Override
    public BlockStorageDevice getDevice()
    {
        BlockStorageDevice device = EXTERNAL_BLOCK_DEVICE.get();
        synchronized (EXTERNAL_BLOCK_DEVICE)
        {
            if (device == null)
            {
                device = (BlockStorageDevice) ControllerServiceImpl.getBean(EXTERNAL_STORAGE_DEVICE);
                EXTERNAL_BLOCK_DEVICE.compareAndSet(null, device);
            }
        }
        return device;
    }

    public StorageDriverManager getDriverManager()
    {
        StorageDriverManager driverManager = DRIVER_MANAGER.get();
        synchronized (DRIVER_MANAGER)
        {
            if (driverManager == null)
            {
                driverManager = (StorageDriverManager) ControllerServiceImpl.getBean(STORAGE_DRIVER_MANAGER);
                DRIVER_MANAGER.compareAndSet(null, driverManager);
            }
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
        String maskingStepId = generateExportGroupCreateSteps(workflow, null,
                device, storage, exportGroup, initiatorURIs, volumeMap, false, token);

        // Have to store export group id to be available at device level/
        // WorkflowService.getInstance().storeStepData(maskingStepId, exportGroup.getId());
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

        if (maskingStepId != null && null != zoningStep)
        {
            // Execute the plan and allow the WorkflowExecutor to fire the
            // taskCompleter.
            workflow.executePlan(taskCompleter, String.format(
                    "ExportGroup successfully applied for StorageArray %s",
                    storage.getLabel()));
        }
    }

    public String generateExportGroupCreateSteps(Workflow workflow,
                                                 String previousStep, BlockStorageDevice device,
                                                 StorageSystem storage, ExportGroup exportGroup,
                                                 List<URI> initiatorURIs, Map<URI, Integer> volumeMap, boolean zoneStepNeeded, String token)
            throws Exception {
        Map<String, URI> portNameToInitiatorURI = new HashMap<>();
        List<URI> hostURIs = new ArrayList<>();
        List<String> portNames = new ArrayList<>();
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

        if (matchingExportMaskURIs == null || matchingExportMaskURIs.isEmpty()) {

            _log.info(String.format(
                    "No existing mask found w/ initiators { %s }",
                    Joiner.on(",").join(portNames)));

            stepId = createNewExportMaskWorkflowForInitiators(initiatorURIs,
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
        return stepId;
    }

    @Override
    public void createWorkFlowAndSubmitForAddVolumes(URI storageURI,
                                                     URI exportGroupURI, Map<URI, Integer> volumeMap, String token,
                                                     ExportTaskCompleter taskCompleter, ExportGroup exportGroup,
                                                     StorageSystem storage) throws Exception
    {
        // Note: We support only case when add volumes to export group does not change storage ports in
        // existing export masks where volumes are added.
        // Based on this, we only execute masking step on device for existing masks --- no zoning change is required.
        List<ExportMask> exportMasks = ExportMaskUtils.getExportMasks(_dbClient,
                exportGroup, storageURI);
        if (exportMasks != null && !exportMasks.isEmpty())
        {
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
                    _dbClient.updateObject(exportMask);

                    List<URI> volumeURIs = new ArrayList<>();
                    volumeURIs.addAll(volumeMap.keySet());

                    List<ExportMask> masks = new ArrayList<>();
                    masks.add(exportMask);

                    String maskingStep = generateExportMaskAddVolumesWorkflow(workflow,
                            null, storage, exportGroup, exportMask, volumeMap);

                    // We do not need zoning step, since storage ports should not change.
                    // Have to store export group id to be available at device level.
                    // Todo: remove commented code.
                    // WorkflowService.getInstance().storeStepData(maskingStep, exportGroup.getId());
                   // String zoningMapUpdateStep = generateZoningMapUpdateWorkflow(
                   //         workflow, maskingStep, exportGroup, storage);
                   // generateZoningAddVolumesWorkflow(workflow, maskingStep,
                   //         exportGroup, masks, volumeURIs);
                }
            }

            String successMessage = String.format(
                    "Volumes successfully added to export on StorageArray %s",
                    storage.getLabel());
            workflow.executePlan(taskCompleter, successMessage);
        }
        else
        { // Todo: complete this .....
            // This is the case when export group does not have export mask for storage array the volumes belongs.
            // In this case we will create new export masks for storage array and each compute resource in the export group.
            if (exportGroup.getInitiators() != null
                    && !exportGroup.getInitiators().isEmpty())
            {
                _log.info("export_volume_add: adding volume, creating a new export mask");

                List<URI> initiatorURIs = new ArrayList<>();
                for (String initiatorId : exportGroup.getInitiators())
                {
                    Initiator initiator = _dbClient.queryObject(
                            Initiator.class, URI.create(initiatorId));
                    initiatorURIs.add(initiator.getId());
                }

                // Get new work flow to setup steps for export masks creation
                Workflow workflow = _workflowService.getNewWorkflow(
                        MaskingWorkflowEntryPoints.getInstance(), "exportGroupCreate",
                        true, token);
                String stepId;

                stepId = createNewExportMaskWorkflowForInitiators(initiatorURIs,
                        exportGroup, workflow, volumeMap, storage, token,
                        null);

                // Set up work flow steps.
//                Workflow workflow = _workflowService.getNewWorkflow(
//                        MaskingWorkflowEntryPoints.getInstance(),
//                        "exportGroupAddVolumes - Create a new mask", true,
//                        token);
//
//                generateExportMaskCreateWorkflow(workflow, null, storage,
//                        exportGroup, initiatorURIs, volumeMap, token);
                //generateZoningMapUpdateWorkflow(workflow,
                //        EXPORT_GROUP_MASKING_TASK, exportGroup, storage);
                generateZoningCreateWorkflow(workflow,
                        EXPORT_GROUP_MASKING_TASK, exportGroup, null,
                        volumeMap);

                String successMessage = String
                        .format("Initiators successfully added to export StorageArray %s",
                                storage.getLabel());

                workflow.executePlan(taskCompleter, successMessage);
            }
            else
            {
                _log.info("Export group doesn't have initiators.");
                taskCompleter.ready(_dbClient);
            }
        }
    }
}
