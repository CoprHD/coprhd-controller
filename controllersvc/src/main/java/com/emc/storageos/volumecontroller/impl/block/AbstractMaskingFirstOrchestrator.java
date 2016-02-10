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

import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.BlockStorageDevice;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportOrchestrationTask;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportTaskCompleter;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowException;
import com.google.common.base.Joiner;

/**
 * This class will have common code used by the MaskingOrchestrator
 * implementations. It also provides a simple, default implementation of the
 * export operations, which assumes that the ExportMasks on the array will only
 * be created by the system. Any existing exports maybe clobber or the operation
 * may fail in such scenarios.
 * 
 * This class is added from 2.2 release to reverse the zoning and masking
 * operations. Extend from this class if the requirement is to do masking first
 * and then zoning. For E.g : Cinder, VNX and HDS systems require masking to be
 * done first and then zoning
 */
abstract public class AbstractMaskingFirstOrchestrator extends
        AbstractBasicMaskingOrchestrator
{

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
    public void exportGroupCreate(URI storageURI, URI exportGroupURI,
            List<URI> initiatorURIs, Map<URI, Integer> volumeMap, String token)
            throws Exception
    {
        ExportOrchestrationTask taskCompleter = null;
        try
        {
            BlockStorageDevice device = getDevice();
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
            checkForInActiveExportGroup(exportGroup);

            StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageURI);
            taskCompleter = new ExportOrchestrationTask(exportGroupURI, token);

            if (initiatorURIs != null && !initiatorURIs.isEmpty())
            {
                _log.info("export_create: initiator list non-empty");
                createWorkFlowAndSubmitForExportGroupCreate(initiatorURIs,
                        volumeMap, token, taskCompleter, device, exportGroup,
                        storage);
            }
            else
            {
                _log.info("export_create: initiator list is empty");
                taskCompleter.ready(_dbClient);
            }
        } catch (DeviceControllerException dex)
        {
            if (taskCompleter != null)
            {
                taskCompleter.error(_dbClient, DeviceControllerException.errors
                        .vmaxExportGroupCreateError(dex.getMessage()));
            }
        } catch (Exception ex)
        {
            _log.error("ExportGroup Orchestration failed.", ex);
            // TODO add service code here
            if (taskCompleter != null)
            {
                ServiceError serviceError = DeviceControllerException.errors
                        .jobFailedMsg(ex.getMessage(), ex);
                taskCompleter.error(_dbClient, serviceError);
            }
        }
    }

    /**
     * Create device specific steps for work flow execution
     * 
     * @param initiatorURIs
     * @param volumeMap
     * @param token
     * @param taskCompleter
     * @param device
     * @param exportGroup
     * @param storage
     * @throws Exception
     */
    public abstract void createWorkFlowAndSubmitForExportGroupCreate(
            List<URI> initiatorURIs, Map<URI, Integer> volumeMap, String token,
            ExportOrchestrationTask taskCompleter, BlockStorageDevice device,
            ExportGroup exportGroup, StorageSystem storage) throws Exception;

    @Override
    public void exportGroupAddVolumes(URI storageURI, URI exportGroupURI,
            Map<URI, Integer> volumeMap, String token) throws Exception
    {
        ExportTaskCompleter taskCompleter = null;
        try
        {
            _log.info(String
                    .format("exportAddVolume START - Array: %s ExportMask: %s Volume: %s",
                            storageURI.toString(), exportGroupURI.toString(),
                            Joiner.on(',').join(volumeMap.entrySet())));

            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
            checkForInActiveExportGroup(exportGroup);
            StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageURI);
            taskCompleter = new ExportOrchestrationTask(exportGroupURI, token);

            createWorkFlowAndSubmitForAddVolumes(storageURI, exportGroupURI,
                    volumeMap, token, taskCompleter, exportGroup, storage);

            _log.info(String
                    .format("exportAddVolume END - Array: %s ExportMask: %s Volume: %s",
                            storageURI.toString(), exportGroupURI.toString(),
                            volumeMap.toString()));
        } catch (Exception e)
        {
            if (taskCompleter != null)
            {
                ServiceError serviceError = DeviceControllerException.errors
                        .jobFailedMsg(e.getMessage(), e);
                taskCompleter.error(_dbClient, serviceError);
            }
            else
            {
                throw DeviceControllerException.exceptions.exportGroupAddVolumesFailed(e);
            }
        }
    }

    /**
     * Creates device specific work flow steps for the add volumes to export
     * group operation.
     * 
     * @param storageURI
     * @param exportGroupURI
     * @param volumeMap
     * @param token
     * @param taskCompleter
     * @param exportGroup
     * @param storage
     * @throws Exception
     */
    public abstract void createWorkFlowAndSubmitForAddVolumes(URI storageURI,
            URI exportGroupURI, Map<URI, Integer> volumeMap, String token,
            ExportTaskCompleter taskCompleter, ExportGroup exportGroup,
            StorageSystem storage) throws Exception;

    @Override
    public void increaseMaxPaths(Workflow workflow,
            StorageSystem storageSystem, ExportGroup exportGroup,
            ExportMask exportMask, List<URI> newInitiators, String token)
            throws Exception
    {
        /*
         * Increases the MaxPaths for a given ExportMask if it has Initiators
         * that are not currently zoned to ports. The method
         * generateExportMaskAddInitiatorsWorkflow will allocate additional
         * ports for the newInitiators to be processed. These will be zoned and
         * then subsequently added to the MaskingView / ExportMask.
         */
        Map<URI, List<URI>> zoneMasksToInitiatorsURIs = new HashMap<URI, List<URI>>();
        zoneMasksToInitiatorsURIs.put(exportMask.getId(), newInitiators);

        String maskinStep = generateExportMaskAddInitiatorsWorkflow(workflow,
                null, storageSystem, exportGroup, exportMask, newInitiators, null,
                token);
        generateZoningAddInitiatorsWorkflow(workflow, maskinStep, exportGroup,
                zoneMasksToInitiatorsURIs);
    }

    /**
     * Generates the sequence of work flow based on the device type for
     * addInitiators operation in export mask. This is default implementation.
     * If there is any device specific implementation, we should override this
     * method and implement device specific logic.
     * 
     * @param workflow
     * @param previousStep
     * @param storage
     * @param exportGroup
     * @param mask
     * @param initiatorsURIs
     * @param maskToInitiatorsMap
     * @param token
     * @throws
     */
    @Override
    public String generateDeviceSpecificAddInitiatorWorkFlow(Workflow workflow,
            String previousStep,
            StorageSystem storage,
            ExportGroup exportGroup,
            ExportMask mask, List<URI> initiatorsURIs,
            Map<URI, List<URI>> maskToInitiatorsMap,
            String token) throws Exception
    {
        // First masking step is created
        String maskingStep = generateExportMaskAddInitiatorsWorkflow(workflow,
                previousStep, storage, exportGroup, mask, initiatorsURIs, null, token);

        // Zoning step is second - it waits till the completion of masking step
        return generateZoningAddInitiatorsWorkflow(workflow, maskingStep, exportGroup,
                maskToInitiatorsMap);

    }

    /**
     * Generates device specific sequence of work flow to addVolumes in export
     * mask.
     * 
     * @param workflow
     * @param attachGroupSnapshot
     * @param storage
     * @param exportGroup
     * @param mask
     * @param volumesToAdd
     * @param volumeURIs
     * @return workflow stepId: export mask stepId.
     * @throws Exception
     */
    @Override
    public String generateDeviceSpecificAddVolumeWorkFlow(Workflow workflow,
            String previousStep, StorageSystem storage,
            ExportGroup exportGroup, ExportMask mask,
            Map<URI, Integer> volumesToAdd, List<URI> volumeURIs)
            throws Exception
    {
        List<ExportMask> masks = new ArrayList<ExportMask>();
        masks.add(mask);

        // First create the masking step
        String maskingStepId = generateExportMaskAddVolumesWorkflow(workflow,
                previousStep, storage, exportGroup, mask, volumesToAdd);

        // Second create the zoning step - this will wait for the masking
        // completion
        generateZoningAddVolumesWorkflow(workflow, maskingStepId, exportGroup,
                masks, volumeURIs);

        return maskingStepId;
    }

    /**
     * Generates Device specific workflow step to create exportmask.
     * 
     * @param workflow
     * @param previousStepId
     * @param storage
     * @param exportGroup
     * @param hostInitiators
     * @param volumeMap
     * @param token
     * @return
     * @throws Exception
     */
    @Override
    public GenExportMaskCreateWorkflowResult generateDeviceSpecificExportMaskCreateWorkFlow(
            Workflow workflow, String zoningGroupId, StorageSystem storage,
            ExportGroup exportGroup, List<URI> hostInitiators,
            Map<URI, Integer> volumeMap, String token) throws Exception
    {
        // Removed the dependency for zoning, hence masking will be performed first
        return generateExportMaskCreateWorkflow(workflow, null, storage,
                exportGroup, hostInitiators, volumeMap, token);
    }

    /**
     * Generates Device specific workflow step to create zoning in exportmask.
     * 
     * @param workflow
     * @param previousStepId
     * @param exportGroup
     * @param exportMaskList
     * @param overallVolumeMap
     */
    @Override
    public String generateDeviceSpecificZoningCreateWorkflow(Workflow workflow,
            String previousStepId, ExportGroup exportGroup,
            List<URI> exportMaskList, Map<URI, Integer> overallVolumeMap)
    {
        return generateZoningCreateWorkflow(workflow, previousStepId,
                exportGroup, exportMaskList, overallVolumeMap);
    }

    /**
     * Generates Device specific workflow step to addInitiators in exportMask.
     * 
     * @param workflow
     * @param zoningGroupId
     * @param storage
     * @param exportGroup
     * @param mask
     * @param newInitiators
     * @param token
     * @throws Exception
     */
    @Override
    public String generateDeviceSpecificExportMaskAddInitiatorsWorkflow(Workflow workflow,
            String zoningGroupId,
            StorageSystem storage,
            ExportGroup exportGroup,
            ExportMask mask, List<URI> newInitiators,
            String token) throws Exception
    {
        // Removed the dependency for zoning, hence masking will be performed first
        return generateExportMaskAddInitiatorsWorkflow(workflow, null, storage, exportGroup, mask, newInitiators, null, token);
    }

    /**
     * Generates device specific workflow step to do zoning for addInitiators in
     * exportmask.
     * 
     * @param workflow
     * @param object
     * @param exportGroup
     * @param zoneMasksToInitiatorsURIs
     */
    @Override
    public String generateDeviceSpecificZoningAddInitiatorsWorkflow(Workflow workflow,
            String previousStep, ExportGroup exportGroup,
            Map<URI, List<URI>> zoneMasksToInitiatorsURIs)
    {
        return generateZoningAddInitiatorsWorkflow(workflow, previousStep, exportGroup, zoneMasksToInitiatorsURIs);
    }

    /**
     * Generates work flow step for zoning map update in export mask.
     * 
     * @param workflow
     * @param previousStep
     * @param exportMask
     * @param storage
     * @return
     * @throws WorkflowException
     */
    public String generateZoningMapUpdateWorkflow(Workflow workflow,
            String previousStep, ExportGroup exportGroup, StorageSystem storage)
            throws WorkflowException
    {

        String updateZoningMapStep = workflow.createStepId();

        Workflow.Method maskingExecuteMethod = new Workflow.Method(
                "doExportMaskZoningMapUpdate", exportGroup.getId(),
                storage.getId());

        Workflow.Method maskingRollbackMethod = new Workflow.Method(
                "rollbackExportMaskZoningMapUpdate", exportGroup.getId(),
                storage.getId());

        updateZoningMapStep = workflow.createStep(
                EXPORT_GROUP_UPDATE_ZONING_MAP,
                String.format("Updating zoning map in export mask "),
                previousStep, storage.getId(), storage.getSystemType(),
                MaskingWorkflowEntryPoints.class, maskingExecuteMethod,
                maskingRollbackMethod, updateZoningMapStep);

        return updateZoningMapStep;
    }
}
