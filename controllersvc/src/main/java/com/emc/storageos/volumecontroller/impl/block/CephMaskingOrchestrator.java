package com.emc.storageos.volumecontroller.impl.block;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.volumecontroller.BlockStorageDevice;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportOrchestrationTask;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.workflow.Workflow;
import com.google.common.base.Joiner;

public class CephMaskingOrchestrator extends AbstractBasicMaskingOrchestrator {
    private static final Logger _log = LoggerFactory.getLogger(CephMaskingOrchestrator.class);

    @Override
    public BlockStorageDevice getDevice() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void exportGroupCreate(URI storageURI, URI exportGroupURI, List<URI> initiatorURIs, Map<URI, Integer> volumeMap, String token)
            throws Exception {
        ExportOrchestrationTask taskCompleter = new ExportOrchestrationTask(exportGroupURI, token);
        try {
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
            StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageURI);

            if (initiatorURIs != null && !initiatorURIs.isEmpty()) {
                // Set up working flow steps.
                Workflow workflow = _workflowService.getNewWorkflow(
                        MaskingWorkflowEntryPoints.getInstance(), "exportGroupCreate", true, token);

                // Create a mapping of ExportMasks to Add Volumes to or
                // add to a list of new Exports to create
                Map<URI, Map<URI, Integer>> exportMaskToVolumesToAdd = new HashMap<>();
                List<URI> newInitiators = new ArrayList<>();
                List<Initiator> initiators = _dbClient.queryObject(Initiator.class, initiatorURIs);
                for (Initiator initiator : initiators) {
                    List<ExportMask> exportMasks = ExportUtils.getInitiatorExportMasks(initiator, _dbClient);
                    if (exportMasks == null || exportMasks.isEmpty()) {
                        newInitiators.add(initiator.getId());
                    } else {
                        for (ExportMask exportMask : exportMasks) {
                            exportMaskToVolumesToAdd.put(exportMask.getId(), volumeMap);
                        }
                    }
                }

                Map<String, List<URI>> computeResourceToInitiators =
                        mapInitiatorsToComputeResource(exportGroup, newInitiators);
                _log.info(String.format("Need to create ExportMasks for these compute resources %s",
                        Joiner.on(',').join(computeResourceToInitiators.entrySet())));
                // ExportMask that need to be newly create. That is, the initiators in
                // this ExportGroup create do not already exist on the system, hence
                // there aren't any already existing ExportMask for them
                for (Map.Entry<String, List<URI>> toCreate : computeResourceToInitiators.entrySet()) {
                    generateExportMaskCreateWorkflow(workflow, null, storage, exportGroup, toCreate.getValue(), volumeMap, token);
                }

                _log.info(String.format("Need to add volumes for these ExportMasks %s",
                        exportMaskToVolumesToAdd.entrySet()));
                // There are some existing ExportMasks for the initiators in the request.
                // For these, we want to reuse the ExportMask and add volumes to them.
                // These ExportMasks would be created by the system. Ceph has no
                // concept ExportMasks.
                for (Map.Entry<URI, Map<URI, Integer>> toAddVolumes : exportMaskToVolumesToAdd.entrySet()) {
                    ExportMask exportMask = _dbClient.queryObject(ExportMask.class, toAddVolumes.getKey());
                    generateExportMaskAddVolumesWorkflow(workflow, null, storage, exportGroup, exportMask, toAddVolumes.getValue());
                }

                String successMessage = String.format(
                        "ExportGroup successfully applied for StorageArray %s", storage.getLabel());
                workflow.executePlan(taskCompleter, successMessage);
            } else {
                taskCompleter.ready(_dbClient);
            }
        } catch (DeviceControllerException dex) {
            taskCompleter.error(_dbClient, DeviceControllerErrors.ceph.operationFailed("exportGroupCreate", dex.getMessage()));
        } catch (Exception ex) {
            _log.error("ExportGroup Orchestration failed.", ex);
            taskCompleter.error(_dbClient, DeviceControllerErrors.ceph.operationFailed("exportGroupCreate", ex.getMessage()));
        }
    }

    @Override
    public void exportGroupDelete(URI storageURI, URI exportGroupURI, String token) throws Exception {
        ExportOrchestrationTask taskCompleter = new ExportOrchestrationTask(exportGroupURI, token);
        try {
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
            StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageURI);

            List<ExportMask> masks = ExportMaskUtils.getExportMasks(_dbClient, exportGroup, storageURI);
            if (masks != null && !masks.isEmpty()) {
                Workflow workflow = _workflowService.getNewWorkflow(
                        MaskingWorkflowEntryPoints.getInstance(), "exportGroupDelete", true, token);

                Map<URI, Integer> volumeMap = ExportUtils.getExportGroupVolumeMap(_dbClient, storage, exportGroup);
                List<URI> volumeURIs = new ArrayList<>(volumeMap.keySet());
                List<URI> initiatorURIs = StringSetUtil.stringSetToUriList(exportGroup.getInitiators());
                Map<URI, Map<URI, Integer>> exportMaskToVolumeCount =
                        ExportMaskUtils.mapExportMaskToVolumeShareCount(_dbClient, volumeURIs, initiatorURIs);

                for (ExportMask exportMask : masks) {
                    List<URI> exportGroupURIs = new ArrayList<>();
                    if (!ExportUtils.isExportMaskShared(_dbClient, exportMask.getId(), exportGroupURIs)) {
                        _log.info(String.format("Adding step to delete ExportMask %s", exportMask.getMaskName()));
                        generateExportMaskDeleteWorkflow(workflow, null, storage, exportGroup, exportMask, null);
                    } else {
                        Map<URI, Integer> volumeToExportGroupCount = exportMaskToVolumeCount.get(exportMask.getId());
                        List<URI> volumesToRemove = new ArrayList<>();
                        for (URI uri : volumeMap.keySet()) {
                            if (volumeToExportGroupCount == null) {
                                continue;
                            }
                            // Remove the volume only if it is not shared with
                            // more than 1 ExportGroup
                            Integer numExportGroupsVolumeIsIn = volumeToExportGroupCount.get(uri);
                            if (numExportGroupsVolumeIsIn != null && numExportGroupsVolumeIsIn == 1) {
                                volumesToRemove.add(uri);
                            }
                        }
                        if (!volumesToRemove.isEmpty()) {
                            _log.info(String.format("Adding step to remove volumes %s from ExportMask %s",
                                    Joiner.on(',').join(volumesToRemove), exportMask.getMaskName()));
                            generateExportMaskRemoveVolumesWorkflow(workflow, null, storage, exportGroup, exportMask, volumesToRemove, null);
                        }
                    }
                }

                String successMessage = String.format(
                        "ExportGroup delete successfully completed for StorageArray %s", storage.getLabel());
                workflow.executePlan(taskCompleter, successMessage);
            } else {
                taskCompleter.ready(_dbClient);
            }
        } catch (DeviceControllerException dex) {
            taskCompleter.error(_dbClient, DeviceControllerErrors.ceph.operationFailed("exportGroupDelete", dex.getMessage()));
        } catch (Exception ex) {
            _log.error("ExportGroup Orchestration failed.", ex);
            taskCompleter.error(_dbClient, DeviceControllerErrors.ceph.operationFailed("exportGroupDelete", ex.getMessage()));
        }
    }

    @Override
    public void exportGroupAddVolumes(URI storageURI, URI exportGroupURI, Map<URI, Integer> volumeMap, String token) throws Exception {
        ExportOrchestrationTask taskCompleter = new ExportOrchestrationTask(exportGroupURI, token);
        try {
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
            StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageURI);

            List<URI> initiatorURIs = StringSetUtil.stringSetToUriList(exportGroup.getInitiators());
            if (initiatorURIs != null && !initiatorURIs.isEmpty()) {
                // Set up workflow steps.
                Workflow workflow = _workflowService.getNewWorkflow(
                        MaskingWorkflowEntryPoints.getInstance(), "exportGroupAddVolumes", true, token);

                // Create a mapping of ExportMasks to Add Volumes to or
                // add to a list of new Exports to create
                Map<URI, Map<URI, Integer>> exportMaskToVolumesToAdd = new HashMap<>();
                List<URI> initiatorsToPlace = new ArrayList<>(initiatorURIs);

                // Need to figure out which ExportMasks to add volumes to.
                for (ExportMask exportMask : ExportMaskUtils.getExportMasks(_dbClient, exportGroup, storageURI)) {
                    if (exportMask.hasAnyInitiators()) {
                        exportMaskToVolumesToAdd.put(exportMask.getId(), volumeMap);
                        for (String uriString : exportMask.getInitiators()) {
                            URI initiatorURI = URI.create(uriString);
                            initiatorsToPlace.remove(initiatorURI);
                        }
                    }
                }

                // ExportMask that need to be newly create because we just added
                // volumes from 'storage' StorageSystem to this ExportGroup
                Map<String, List<URI>> computeResourceToInitiators = mapInitiatorsToComputeResource(exportGroup, initiatorsToPlace);
                if (!computeResourceToInitiators.isEmpty()) {
                    _log.info(String.format("Need to create ExportMasks for these compute resources %s",
                            Joiner.on(',').join(computeResourceToInitiators.entrySet())));
                    for (Map.Entry<String, List<URI>> toCreate : computeResourceToInitiators.entrySet()) {
                        generateExportMaskCreateWorkflow(workflow, null, storage, exportGroup, toCreate.getValue(), volumeMap, token);
                    }
                }

                // We already know about the ExportMask, so we just add volumes to it
                if (!exportMaskToVolumesToAdd.isEmpty()) {
                    _log.info(String.format("Need to add volumes for these ExportMasks %s",
                            exportMaskToVolumesToAdd.entrySet()));
                    for (Map.Entry<URI, Map<URI, Integer>> toAddVolumes : exportMaskToVolumesToAdd.entrySet()) {
                        ExportMask exportMask = _dbClient.queryObject(ExportMask.class, toAddVolumes.getKey());
                        generateExportMaskAddVolumesWorkflow(workflow, null, storage, exportGroup, exportMask, toAddVolumes.getValue());
                    }
                }

                String successMsgTemplate = "ExportGroup add volumes successfully applied for StorageArray %s";
                workflow.executePlan(taskCompleter, String.format(successMsgTemplate, storage.getLabel()));
            } else {
                taskCompleter.ready(_dbClient);
            }
        } catch (DeviceControllerException dex) {
            taskCompleter.error(_dbClient, DeviceControllerErrors.ceph.
            		operationFailed("exportGroupAddVolumes", dex.getMessage()));
        } catch (Exception ex) {
            _log.error("ExportGroup Orchestration failed.", ex);
            taskCompleter.error(_dbClient, DeviceControllerErrors.ceph.
            		operationFailed("exportGroupAddVolumes", ex.getMessage()));
        }
    }

    @Override
    public void exportGroupRemoveVolumes(URI storageURI, URI exportGroupURI, List<URI> volumeURIs, String token) throws Exception {
        ExportOrchestrationTask taskCompleter = new ExportOrchestrationTask(exportGroupURI, token);
        try {
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
            StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageURI);

            List<ExportMask> masks = ExportMaskUtils.getExportMasks(_dbClient, exportGroup, storageURI);
            if (masks != null && !masks.isEmpty()) {
                // Set up workflow steps.
                Workflow workflow = _workflowService.getNewWorkflow(
                        MaskingWorkflowEntryPoints.getInstance(), "exportGroupRemoveVolumes", true, token);

                // Generate a mapping of volume URIs to the # of
                // ExportGroups that it is associated with
                List<URI> initiatorURIs = StringSetUtil.stringSetToUriList(exportGroup.getInitiators());
                Map<URI, Map<URI, Integer>> exportMaskToVolumeCount =
                        ExportMaskUtils.mapExportMaskToVolumeShareCount(_dbClient, volumeURIs, initiatorURIs);

                // Generate a mapping of the ExportMask URI to a list volumes to
                // remove from that ExportMask
                Map<URI, List<URI>> exportToRemoveVolumesList = new HashMap<>();
                for (ExportMask exportMask : masks) {
                    Map<URI, Integer> volumeToCountMap = exportMaskToVolumeCount.get(exportMask.getId());
                    if (volumeToCountMap == null) {
                        continue;
                    }
                    for (Map.Entry<URI, Integer> it : volumeToCountMap.entrySet()) {
                        URI volumeURI = it.getKey();
                        Integer numberOfExportGroupsVolumesIsIn = it.getValue();
                        if (numberOfExportGroupsVolumesIsIn == 1) {
                            List<URI> volumesToRemove = exportToRemoveVolumesList.get(exportMask.getId());
                            if (volumesToRemove == null) {
                                volumesToRemove = new ArrayList<>();
                                exportToRemoveVolumesList.put(exportMask.getId(), volumesToRemove);
                            }
                            volumesToRemove.add(volumeURI);
                        }
                    }
                }

                // With the mapping of ExportMask to list of volume URIs,
                // generate a step to remove the volumes from the ExportMask
                for (Map.Entry<URI, List<URI>> entry : exportToRemoveVolumesList.entrySet()) {
                    ExportMask exportMask = _dbClient.queryObject(ExportMask.class, entry.getKey());
                    _log.info(String.format("Adding step to remove volumes %s from ExportMask %s",
                            Joiner.on(',').join(entry.getValue()), exportMask.getMaskName()));
                    generateExportMaskRemoveVolumesWorkflow(workflow, null, storage, exportGroup, exportMask, entry.getValue(), null);
                }

                String successMsgTemplate = "ExportGroup remove volumes successfully applied for StorageArray %s";
                workflow.executePlan(taskCompleter, String.format(successMsgTemplate, storage.getLabel()));
            } else {
                taskCompleter.ready(_dbClient);
            }
        } catch (DeviceControllerException dex) {
            taskCompleter.error(_dbClient, DeviceControllerErrors.ceph.operationFailed("exportGroupRemoveVolumes", dex.getMessage()));
        } catch (Exception ex) {
            _log.error("ExportGroup Orchestration failed.", ex);
            taskCompleter.error(_dbClient, DeviceControllerErrors.ceph.operationFailed("exportGroupRemoveVolumes", ex.getMessage()));
        }
    }

    @Override
    public void exportGroupAddInitiators(URI storageURI, URI exportGroupURI, List<URI> initiatorURIs, String token) throws Exception {
        ExportOrchestrationTask taskCompleter = new ExportOrchestrationTask(exportGroupURI, token);
        try {
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
            StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageURI);

            if (initiatorURIs != null && !initiatorURIs.isEmpty()) {
                // Set up workflow steps.
                Workflow workflow = _workflowService.getNewWorkflow(MaskingWorkflowEntryPoints.getInstance(), "exportGroupAddInitiators", true, token);

                // Populate the portNames and the mapping of the portNames to Initiator URIs
                Map<String, URI> portNameToInitiatorURI = new HashMap<>();
                List<URI> hostURIs = new ArrayList<>();
                List<String> portNames = new ArrayList<>();
                processInitiators(exportGroup, initiatorURIs, portNames, portNameToInitiatorURI, hostURIs);

                List<URI> initiatorURIsToPlace = new ArrayList<>(initiatorURIs);
                Map<String, List<URI>> computeResourceToInitiators = mapInitiatorsToComputeResource(exportGroup, initiatorURIs);
                Set<URI> partialMasks = new HashSet<>();
                Map<String, Set<URI>> initiatorToExport =
                        determineInitiatorToExportMaskPlacements(exportGroup, storageURI, computeResourceToInitiators,
                                Collections.EMPTY_MAP, portNameToInitiatorURI, partialMasks);

                Map<URI, List<URI>> exportToInitiators = toExportMaskToInitiatorURIs(initiatorToExport, portNameToInitiatorURI);
                Map<URI, Integer> volumesToAdd = ExportUtils.getExportGroupVolumeMap(_dbClient, storage, exportGroup);
                for (Map.Entry<URI, List<URI>> toAddInitiators : exportToInitiators.entrySet()) {
                    ExportMask exportMask = _dbClient.queryObject(ExportMask.class, toAddInitiators.getKey());
                    if (exportMask == null || exportMask.getInactive()) {
                        continue;
                    }
                    for (URI toAddInitiator : toAddInitiators.getValue()) {
                        if (!exportMask.hasInitiator(toAddInitiator.toString())) {
                            _log.info(String.format("Add step to add initiator %s to ExportMask %s", toAddInitiator.toString(), exportMask.getMaskName()));
                            generateExportMaskAddInitiatorsWorkflow(workflow, null, storage, exportGroup, exportMask, toAddInitiators.getValue(), null, null);
                        } else if (volumesToAdd != null && volumesToAdd.size() > 0) {
                            _log.info(String.format("Add step to add volumes %s to ExportMask %s", Joiner.on(',').join(volumesToAdd.entrySet()), exportMask.getMaskName()));
                            generateExportMaskAddVolumesWorkflow(workflow, null, storage, exportGroup, exportMask, volumesToAdd);
                        }
                        initiatorURIsToPlace.remove(toAddInitiator);
                    }
                }

                // If there are any new initiators that weren't already known to the system previously, add them now.
                if (!initiatorURIsToPlace.isEmpty() && volumesToAdd != null) {
                    Map<String, List<URI>> newComputeResources = mapInitiatorsToComputeResource(exportGroup, initiatorURIsToPlace);
                    _log.info(String.format("Need to create ExportMasks for these compute resources %s", Joiner.on(',').join(newComputeResources.entrySet())));
                    for (Map.Entry<String, List<URI>> toCreate : newComputeResources.entrySet()) {
                        generateExportMaskCreateWorkflow(workflow, null, storage, exportGroup, toCreate.getValue(), volumesToAdd, null);
                    }
                }

                String successMessage = String.format("ExportGroup add initiators successfully applied for StorageArray %s", storage.getLabel());
                workflow.executePlan(taskCompleter, successMessage);
            } else {
                taskCompleter.ready(_dbClient);
            }
        } catch (DeviceControllerException dex) {
            taskCompleter.error(_dbClient, DeviceControllerErrors.ceph.operationFailed("exportGroupAddInitiators", dex.getMessage()));
        } catch (Exception ex) {
            _log.error("ExportGroup Orchestration failed.", ex);
            taskCompleter.error(_dbClient, DeviceControllerErrors.ceph.operationFailed("exportGroupAddInitiators", ex.getMessage()));
        }
    }

    @Override
    public void exportGroupRemoveInitiators(URI storageURI, URI exportGroupURI, List<URI> initiatorURIs, String token) throws Exception {
        ExportOrchestrationTask taskCompleter = new ExportOrchestrationTask(exportGroupURI, token);
        try {
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
            StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageURI);

            if (initiatorURIs != null && !initiatorURIs.isEmpty() && exportGroup.getExportMasks() != null) {
                // Set up workflow steps.
                Workflow workflow = _workflowService.getNewWorkflow(
                        MaskingWorkflowEntryPoints.getInstance(), "exportGroupRemoveInitiators", true, token);

                // Create a mapping of ExportMask URI to initiators to remove
                Map<URI, List<URI>> exportToInitiatorsToRemove = new HashMap<>();
                Map<URI, List<URI>> exportToVolumesToRemove = new HashMap<>();
                Map<URI, Integer> volumeMap = null;
                for (String exportMaskURIStr : exportGroup.getExportMasks()) {
                    URI exportMaskURI = URI.create(exportMaskURIStr);
                    ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
                    if (exportMask == null) {
                        continue;
                    }
                    for (URI initiatorURI : initiatorURIs) {
                        Initiator initiator = _dbClient.queryObject(Initiator.class, initiatorURI);
                        if (initiator == null || !exportMask.hasInitiator(initiatorURI.toString())) {
                            continue;
                        }
                        if (ExportUtils.getInitiatorExportGroups(initiator, _dbClient).size() == 1) {
                            List<URI> initiators = exportToInitiatorsToRemove.get(exportGroupURI);
                            if (initiators == null) {
                                initiators = new ArrayList<>();
                                exportToInitiatorsToRemove.put(exportMaskURI, initiators);
                            }
                            initiators.add(initiatorURI);
                        } else {
                            if (volumeMap == null) {
                                volumeMap = ExportUtils.getExportGroupVolumeMap(_dbClient, storage, exportGroup);
                            }
                            List<URI> volumeURIs = exportToVolumesToRemove.get(exportGroupURI);
                            if (volumeURIs == null) {
                                volumeURIs = new ArrayList<>();
                                exportToVolumesToRemove.put(exportMaskURI, volumeURIs);
                            }
                            for (URI volumeURI : volumeMap.keySet()) {
                                // Only add to the remove list for the ExportMask if
                                // the EM is not being shared with another ExportGroup
                                Integer count = ExportUtils.getNumberOfExportGroupsWithVolume(initiator, volumeURI, _dbClient);
                                if (count == 1) {
                                    volumeURIs.add(volumeURI);
                                }
                            }
                        }
                    }
                }

                // Generate the remove initiators steps for the entries that were determined above
                for (Map.Entry<URI, List<URI>> toRemoveInits : exportToInitiatorsToRemove.entrySet()) {
                    ExportMask exportMask = _dbClient.queryObject(ExportMask.class, toRemoveInits.getKey());
                    if (exportMask != null) {
                        List<URI> removeInitURIs = toRemoveInits.getValue();
                        List<String> exportMaskInitiatorURIs = new ArrayList<>(exportMask.getInitiators());
                        for (URI uri : removeInitURIs) {
                            exportMaskInitiatorURIs.remove(uri.toString());
                        }
                        if (exportMaskInitiatorURIs.isEmpty()) {
                            _log.info(String.format("Adding step to delete ExportMask %s", exportMask.getMaskName()));
                            generateExportMaskDeleteWorkflow(workflow, null, storage, exportGroup, exportMask, null);
                        } else {
                            _log.info(String.format("Adding step to remove initiators %s from ExportMask %s",Joiner.on(',').join(removeInitURIs), exportMask.getMaskName()));
                            generateExportMaskRemoveInitiatorsWorkflow(workflow, null, storage, exportGroup, exportMask, removeInitURIs, true);
                        }
                    }
                }

                // Generate the remove volume for those cases where we remove initiators
                // from an ExportGroup that contains more than one host/initiator
                for (Map.Entry<URI, List<URI>> toRemoveVols : exportToVolumesToRemove.entrySet()) {
                    ExportMask exportMask = _dbClient.queryObject(ExportMask.class, toRemoveVols.getKey());
                    List<URI> removeVolumeURIs = toRemoveVols.getValue();
                    if (exportMask != null && !removeVolumeURIs.isEmpty()) {
                        List<String> exportMaskVolumeURIs = new ArrayList<>(exportMask.getVolumes().keySet());
                        for (URI uri : removeVolumeURIs) {
                            exportMaskVolumeURIs.remove(uri.toString());
                        }
                        if (exportMaskVolumeURIs.isEmpty()) {
                            _log.info(String.format("Adding step to delete ExportMask %s", exportMask.getMaskName()));
                            generateExportMaskDeleteWorkflow(workflow, null, storage, exportGroup, exportMask, null);
                        } else {
                            _log.info(String.format("Adding step to remove volumes %s from ExportMask %s",Joiner.on(',').join(removeVolumeURIs), exportMask.getMaskName()));
                            generateExportMaskRemoveVolumesWorkflow(workflow, null, storage, exportGroup, exportMask, removeVolumeURIs, null);
                        }
                    }
                }

                String successMessage = String.format("ExportGroup remove initiators successfully applied for StorageArray %s", storage.getLabel());
                workflow.executePlan(taskCompleter, successMessage);
            } else {
                taskCompleter.ready(_dbClient);
            }
        } catch (DeviceControllerException dex) {
            taskCompleter.error(_dbClient, DeviceControllerErrors.ceph.operationFailed("exportGroupRemoveInitiators", dex.getMessage()));
        } catch (Exception ex) {
            _log.error("ExportGroup Orchestration failed.", ex);
            taskCompleter.error(_dbClient, DeviceControllerErrors.ceph.operationFailed("exportGroupRemoveInitiators", ex.getMessage()));
        }
    }

}
