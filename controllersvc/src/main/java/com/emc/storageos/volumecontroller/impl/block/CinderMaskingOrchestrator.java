/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block;

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
import com.emc.storageos.volumecontroller.BlockStorageDevice;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportOrchestrationTask;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportTaskCompleter;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.workflow.Workflow;
import com.google.common.base.Joiner;

public class CinderMaskingOrchestrator extends AbstractMaskingFirstOrchestrator {
    private static final Logger _log = LoggerFactory.getLogger(CinderMaskingOrchestrator.class);

    private static final AtomicReference<BlockStorageDevice> CINDER_BLOCK_DEVICE = new AtomicReference<BlockStorageDevice>();
    public static final String CINDER_STORAGE_DEVICE = "cinderStorageDevice";

    @Override
    public BlockStorageDevice getDevice() {
        BlockStorageDevice device = CINDER_BLOCK_DEVICE.get();
        synchronized (CINDER_BLOCK_DEVICE) {
            if (device == null) {
                device = (BlockStorageDevice) ControllerServiceImpl.getBean(CINDER_STORAGE_DEVICE);
                CINDER_BLOCK_DEVICE.compareAndSet(null, device);
            }
        }
        return device;
    }

    @Override
    public boolean determineExportGroupCreateSteps(Workflow workflow,
            String previousStep, BlockStorageDevice device,
            StorageSystem storage, ExportGroup exportGroup,
            List<URI> initiatorURIs, Map<URI, Integer> volumeMap, boolean zoneStepNeeded, String token)
                    throws Exception {
        Map<String, URI> portNameToInitiatorURI = new HashMap<String, URI>();
        List<URI> hostURIs = new ArrayList<URI>();
        List<String> portNames = new ArrayList<String>();

        /*
         * Populate the port WWN/IQNs (portNames) and the mapping of the
         * WWN/IQNs to Initiator URIs
         */
        processInitiators(exportGroup, initiatorURIs, portNames, portNameToInitiatorURI, hostURIs);

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

        if (matchingExportMaskURIs == null || matchingExportMaskURIs.isEmpty()) {

            _log.info(String.format(
                    "No existing mask found w/ initiators { %s }",
                    Joiner.on(",").join(portNames)));

            createNewExportMaskWorkflowForInitiators(initiatorURIs,
                    exportGroup, workflow, volumeMap, storage, token,
                    previousStep);
        } else {
            _log.info(String.format("Mask(s) found w/ initiators {%s}. "
                    + "MatchingExportMaskURIs {%s}, portNameToInitiators {%s}",
                    Joiner.on(",").join(portNames),
                    Joiner.on(",").join(matchingExportMaskURIs.keySet()),
                    Joiner.on(",").join(portNameToInitiatorURI.entrySet())));

            /*
             * use case not supported for Cinder (brown field). as there is no
             * API to get existing volumes and their export information.
             */
            throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
        }
        return true;
    }

    @Override
    public void createWorkFlowAndSubmitForExportGroupCreate(
            List<URI> initiatorURIs, Map<URI, Integer> volumeMap, String token,
            ExportOrchestrationTask taskCompleter, BlockStorageDevice device,
            ExportGroup exportGroup, StorageSystem storage) throws Exception {
        // Get new work flow to setup steps for export group creation
        Workflow workflow = _workflowService.getNewWorkflow(
                MaskingWorkflowEntryPoints.getInstance(), "exportGroupCreate",
                true, token);

        // Create two steps, one for the ExportGroup actions and one for Zoning.
        boolean createdSteps = determineExportGroupCreateSteps(workflow, null,
                device, storage, exportGroup, initiatorURIs, volumeMap, false, token);

        // zoning map update step
        String zoningMapUpdateStep = generateZoningMapUpdateWorkflow(workflow,
                EXPORT_GROUP_MASKING_TASK, exportGroup, storage);

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
                workflow, EXPORT_GROUP_UPDATE_ZONING_MAP, exportGroup, null,
                volumeMap);

        if (createdSteps && null != zoningStep && null != zoningMapUpdateStep) {
            // Execute the plan and allow the WorkflowExecutor to fire the
            // taskCompleter.
            workflow.executePlan(taskCompleter, String.format(
                    "ExportGroup successfully applied for StorageArray %s",
                    storage.getLabel()));
        }

    }

    @Override
    public void createWorkFlowAndSubmitForAddVolumes(URI storageURI,
            URI exportGroupURI, Map<URI, Integer> volumeMap, String token,
            ExportTaskCompleter taskCompleter, ExportGroup exportGroup,
            StorageSystem storage) throws Exception {
        List<ExportMask> exportMasks = ExportMaskUtils.getExportMasks(_dbClient,
                exportGroup, storageURI);
        if (exportMasks != null && !exportMasks.isEmpty()) {
            // Set up work flow steps.
            Workflow workflow = _workflowService.getNewWorkflow(
                    MaskingWorkflowEntryPoints.getInstance(),
                    "exportGroupAddVolumes - Added volumes to existing mask",
                    true, token);

            // For each export mask in export group, invoke add Volumes if export Mask belongs to the same storage Array
            for (ExportMask exportMask : exportMasks) {
                if (exportMask.getStorageDevice().equals(storageURI)) {
                    _log.info("export_volume_add: adding volume to an existing export");
                    exportMask.addVolumes(volumeMap);
                    _dbClient.persistObject(exportMask);

                    List<URI> volumeURIs = new ArrayList<URI>();
                    volumeURIs.addAll(volumeMap.keySet());

                    List<ExportMask> masks = new ArrayList<ExportMask>();
                    masks.add(exportMask);

                    String maskingStep = generateExportMaskAddVolumesWorkflow(workflow,
                            null, storage, exportGroup, exportMask, volumeMap, null);
                    String zoningMapUpdateStep = generateZoningMapUpdateWorkflow(
                            workflow, maskingStep, exportGroup, storage);
                    generateZoningAddVolumesWorkflow(workflow, zoningMapUpdateStep,
                            exportGroup, masks, volumeURIs);
                }
            }

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
                    Initiator initiator = _dbClient.queryObject(
                            Initiator.class, URI.create(initiatorId));
                    initiatorURIs.add(initiator.getId());
                }

                // Set up work flow steps.
                Workflow workflow = _workflowService.getNewWorkflow(
                        MaskingWorkflowEntryPoints.getInstance(),
                        "exportGroupAddVolumes - Create a new mask", true,
                        token);

                generateExportMaskCreateWorkflow(workflow, null, storage,
                        exportGroup, initiatorURIs, volumeMap, token);
                generateZoningMapUpdateWorkflow(workflow,
                        EXPORT_GROUP_MASKING_TASK, exportGroup, storage);
                generateZoningCreateWorkflow(workflow,
                        EXPORT_GROUP_UPDATE_ZONING_MAP, exportGroup, null,
                        volumeMap);

                String successMessage = String
                        .format("Initiators successfully added to export StorageArray %s",
                                storage.getLabel());

                workflow.executePlan(taskCompleter, successMessage);
            } else {
                _log.info("export_volume_add: adding volume, no initiators yet");
                taskCompleter.ready(_dbClient);
            }
        }
    }

    @Override
    public void findAndUpdateFreeHLUsForClusterExport(StorageSystem storage, ExportGroup exportGroup, List<URI> initiatorURIs,
            Map<URI, Integer> volumeMap) throws Exception {
        // TODO Auto-generated method stub

    }

}
