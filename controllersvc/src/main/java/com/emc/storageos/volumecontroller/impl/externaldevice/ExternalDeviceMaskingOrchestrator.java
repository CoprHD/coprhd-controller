/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.externaldevice;


import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.util.StorageDriverManager;
import com.emc.storageos.volumecontroller.BlockStorageDevice;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.block.AbstractMaskingFirstOrchestrator;
import com.emc.storageos.volumecontroller.impl.block.MaskingWorkflowEntryPoints;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportOrchestrationTask;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportTaskCompleter;
import com.emc.storageos.workflow.Workflow;
import com.google.common.base.Joiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class ExternalDeviceMaskingOrchestrator extends AbstractMaskingFirstOrchestrator {

    private static final Logger _log = LoggerFactory.getLogger(ExternalDeviceMaskingOrchestrator.class);
    private static final AtomicReference<StorageDriverManager> DRIVER_MANAGER = new AtomicReference<>();
    private static final AtomicReference<BlockStorageDevice> EXTERNAL_BLOCK_DEVICE = new AtomicReference<>();

    public static final String EXTERNAL_STORAGE_DEVICE = "externalBlockStorageDevice";
    public static final String STORAGE_DRIVER_MANAGER = "storageDriverManager";

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

        if (createdSteps && null != zoningStep && null != zoningMapUpdateStep)
        {
            // Execute the plan and allow the WorkflowExecutor to fire the
            // taskCompleter.
            workflow.executePlan(taskCompleter, String.format(
                    "ExportGroup successfully applied for StorageArray %s",
                    storage.getLabel()));
        }
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
             * TODO: TBD. use case not supported for External device (brown field).
             */
            throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
        }
        return true;
    }

    @Override
    public void createWorkFlowAndSubmitForAddVolumes(URI storageURI, URI exportGroupURI, Map<URI, Integer> volumeMap, String token, ExportTaskCompleter taskCompleter, ExportGroup exportGroup, StorageSystem storage) throws Exception {

    }
}
