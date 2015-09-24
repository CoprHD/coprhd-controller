/* Copyright 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.block;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.Controller;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportGroup.ExportGroupType;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.exceptions.DeviceControllerExceptions;
import com.emc.storageos.locking.LockTimeoutValue;
import com.emc.storageos.locking.LockType;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.util.NetworkLite;
import com.emc.storageos.volumecontroller.BlockStorageDevice;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerLockingUtil;
import com.emc.storageos.volumecontroller.placement.StoragePortsAllocator;
import com.emc.storageos.volumecontroller.placement.StoragePortsAssigner;
import com.emc.storageos.vplex.api.VPlexApiException;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.Workflow.Method;
import com.emc.storageos.workflow.WorkflowService;
import com.emc.storageos.workflow.WorkflowStepCompleter;

/**
 * This implementation is used for export operations that are only intended to be
 * used for VPlex multiple masking. These interfaces pass lower level operations
 * reading the Export Masks to the VPlexBackendManager.
 * 
 * There are also operations to generate the port groups, initiator groups, etc.
 * automatically for multiple mask support.
 * 
 * @author hallup
 * 
 */
public class VplexCinderMaskingOrchestrator extends CinderMaskingOrchestrator
        implements VplexBackEndMaskingOrchestrator,
        Controller {
    private static final Logger _log = LoggerFactory.getLogger(VplexCinderMaskingOrchestrator.class);
    private static final int CINDER_NUM_PORT_GROUP = 1;
    private boolean simulation = false;
    BlockDeviceController _blockController = null;
    WorkflowService _workflowService = null;

    public VplexCinderMaskingOrchestrator() {

    }

    public VplexCinderMaskingOrchestrator(DbClient dbClient, BlockDeviceController controller) {
        this._dbClient = dbClient;
        this._blockController = controller;
    }

    public void setBlockDeviceController(BlockDeviceController blockController) {
        this._blockController = blockController;
    }

    public void setSimulation(boolean simulation) {
        this.simulation = simulation;
    }

    public WorkflowService getWorkflowService() {
        return _workflowService;
    }

    @Override
    public void setWorkflowService(WorkflowService _workflowService) {
        this._workflowService = _workflowService;
    }

    @Override
    public Map<URI, ExportMask> readExistingExportMasks(StorageSystem storage,
            BlockStorageDevice device,
            List<Initiator> initiators) {
        /*
         * Returning Empty.Map as Cinder does not have capability return masks
         * on the storage system.
         * 
         * This will cause the VPlexBackendManager to generate an ExportMask
         * for the first volume and then reuse it by finding it from the database
         * for subsequent volumes.
         */
        return new HashMap<URI, ExportMask>();
    }

    @Override
    public ExportMask refreshExportMask(StorageSystem storage,
            BlockStorageDevice device,
            ExportMask mask) {
        /*
         * Cinder has no capability to read the masks on the storage systems,
         * hence return the mask as it is.
         */
        return mask;
    }

    @Override
    public Set<Map<URI, List<List<StoragePort>>>> getPortGroups(Map<URI, List<StoragePort>> allocatablePorts,
            Map<URI, NetworkLite> networkMap,
            URI varrayURI,
            int nInitiatorGroups) {
        _log.debug("START - getPortGroups");
        Set<Map<URI, List<List<StoragePort>>>> portGroups = new HashSet<Map<URI, List<List<StoragePort>>>>();
        Map<URI, Integer> portsAllocatedPerNetwork = new HashMap<URI, Integer>();

        // Port Group is always 1 for Cinder as of now.
        for (URI netURI : allocatablePorts.keySet()) {
            Integer nports = allocatablePorts.get(netURI).size() / CINDER_NUM_PORT_GROUP;
            portsAllocatedPerNetwork.put(netURI, nports);
        }

        StoragePortsAllocator allocator = new StoragePortsAllocator();

        for (int i = 0; i < CINDER_NUM_PORT_GROUP; i++) {
            Map<URI, List<List<StoragePort>>> portGroup = new HashMap<URI, List<List<StoragePort>>>();
            StringSet portNames = new StringSet();

            for (URI netURI : allocatablePorts.keySet()) {
                NetworkLite net = networkMap.get(netURI);
                List<StoragePort> allocatedPorts = allocatePorts(allocator,
                        allocatablePorts.get(netURI),
                        portsAllocatedPerNetwork.get(netURI),
                        net,
                        varrayURI);
                if (portGroup.get(netURI) == null) {
                    portGroup.put(netURI, new ArrayList<List<StoragePort>>());
                }
                portGroup.get(netURI).add(allocatedPorts);
                allocatablePorts.get(netURI).removeAll(allocatedPorts);
                for (StoragePort port : allocatedPorts) {
                    portNames.add(port.getPortName());
                }
            }

            portGroups.add(portGroup);
            _log.info(String.format("Port Group %d: port names in the port group {%s}", i, portNames.toString()));

        }

        _log.debug("END - getPortGroups");
        return portGroups;
    }

    @Override
    public StringSetMap configureZoning(Map<URI, List<List<StoragePort>>> portGroup,
            Map<String, Map<URI, Set<Initiator>>> initiatorGroup,
            Map<URI, NetworkLite> networkMap, StoragePortsAssigner assigner) {
        return VPlexBackEndOrchestratorUtil.configureZoning(portGroup, initiatorGroup, networkMap, assigner);
    }

    /**
     * Method to re-validate the Export Mask
     * 
     * @param varrayURI
     * @param initiatorPortMap
     * @param mask
     * @param invalidMasks
     * @param directorToInitiatorIds
     * @param idToInitiatorMap
     * @param dbClient
     * @param portWwnToClusterMap
     * @return
     */
    public Method updateZoningMapAndValidateExportMaskMethod(URI varrayURI,
            Map<URI, List<StoragePort>> initiatorPortMap, URI exportMaskURI,
            Map<String, Set<String>> directorToInitiatorIds, Map<String, Initiator> idToInitiatorMap,
            Map<String, String> portWwnToClusterMap, StorageSystem vplex, StorageSystem array, String clusterId) {
        return new Workflow.Method("updateZoningMapAndvalidateExportMask",
                varrayURI,
                initiatorPortMap,
                exportMaskURI,
                directorToInitiatorIds,
                idToInitiatorMap,
                portWwnToClusterMap, vplex, array, clusterId);
    }

    /**
     * Re-validate the ExportMask
     * 
     * This is required to be done as the ExportMask
     * gets updated by reading the cinder export volume
     * response.
     *
     * 
     * @param varrayURI
     * @param initiatorPortMap
     * @param mask
     * @param invalidMasks
     * @param directorToInitiatorIds
     * @param idToInitiatorMap
     * @param dbClient
     * @param portWwnToClusterMap
     */
    public void updateZoningMapAndvalidateExportMask(URI varrayURI,
            Map<URI, List<StoragePort>> initiatorPortMap, URI exportMaskURI,
            Map<String, Set<String>> directorToInitiatorIds, Map<String, Initiator> idToInitiatorMap,
            Map<String, String> portWwnToClusterMap, StorageSystem vplex,
            StorageSystem array, String clusterId, String stepId) {
        
        try {
            WorkflowStepCompleter.stepExecuting(stepId);
            // Export Mask is updated, read it from DB
            ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
            
            // First step would be to update the zoning map based on the connectivity
            updateZoningMap(initiatorPortMap, directorToInitiatorIds,exportMask);
            
            boolean passed = VPlexBackEndOrchestratorUtil.validateExportMask(varrayURI, initiatorPortMap, exportMask, null, directorToInitiatorIds,
                    idToInitiatorMap, _dbClient, portWwnToClusterMap);
            
            if(!passed) {
                // Mark this mask as inactive, so that we dont pick it in the next iteration
                exportMask.setInactive(Boolean.TRUE);
                _dbClient.persistObject(exportMask);
                
                _log.error("Export Mask is not suitable for VPLEX to backend storage system");
                WorkflowStepCompleter.stepFailed(stepId, VPlexApiException.exceptions.couldNotFindValidArrayExportMask(
                        vplex.getNativeGuid(), array.getNativeGuid(), clusterId));                
                throw VPlexApiException.exceptions.couldNotFindValidArrayExportMask(
                        vplex.getNativeGuid(), array.getNativeGuid(), clusterId);
            }
            
            WorkflowStepCompleter.stepSucceded(stepId);

        } catch (Exception ex) {
            _log.error("Failed to validate export mask for cinder: ", ex);
            VPlexApiException vplexex = DeviceControllerExceptions.vplex.failedToValidateExportMask(exportMaskURI.toString(), ex);
            WorkflowStepCompleter.stepFailed(stepId, vplexex);
        }

    }

    /**
     * Update the zoning map entries from the updated target list in the mask
     * 
     * 1. Clean existing zoning map entries
     * 2. From the target storage ports in the mask, generate a map of networkURI string vs list of target storage ports
     * 3. From the initiator ports in the mask, generate a map of its URI vs InitiatorWWN
     * 4. From the initiatorPortMap, generate map of its WWN vs networkURI string 
     * 5. Based on the networkURI matching, generate zoning map entries adhering to vplex best practices
     * 6. Persist the updated mask.
     * 
     * @param initiatorPortMap
     * @param exportMask
     */
    private void updateZoningMap(Map<URI, List<StoragePort>> initiatorPortMap,
            Map<String, Set<String>> directorToInitiatorIds, ExportMask exportMask) {
        
        //STEP 1 - Clean the existing zoning map
        for (String initiatorURIStr : exportMask.getZoningMap().keySet()) {
            exportMask.removeZoningMapEntry(initiatorURIStr);
        }
        exportMask.setZoningMap(null);
        
        // STEP 2- From Back-end storage system ports, which are used as target storage ports for VPLEX
        // generate a map of networkURI string vs list of target storage ports.
        Map<String, List<StoragePort>> nwUriVsTargetPortsFromMask = new HashMap<>();
        StringSet targetPorts = exportMask.getStoragePorts();
        for(String targetPortUri : targetPorts) {
            StoragePort targetPort = _dbClient.queryObject(StoragePort.class, URI.create(targetPortUri));
            String networkUri = targetPort.getNetwork().toString();
            if(nwUriVsTargetPortsFromMask.containsKey(networkUri)) {
                nwUriVsTargetPortsFromMask.get(networkUri).add(targetPort);
            } else {
                nwUriVsTargetPortsFromMask.put(networkUri, new ArrayList<StoragePort>());
                nwUriVsTargetPortsFromMask.get(networkUri).add(targetPort);
            }           
        }
        
        // STEP 3 - From the initiator ports in the mask, generate a map of its URI vs InitiatorWWN 
        //Map<String, URI> initiatorWWNvsUriFromMask = new HashMap<>();
        Map<String, String> initiatorUrivsWWNFromMask = new HashMap<>();
        StringSet initiatorPorts = exportMask.getInitiators();
        for(String initiatorUri : initiatorPorts) {
            Initiator initiator = _dbClient.queryObject(Initiator.class, URI.create(initiatorUri));
            String initiatorWWN = initiator.getInitiatorPort();
            initiatorUrivsWWNFromMask.put(initiator.getId().toString(), initiatorWWN);
        }
        
        // STEP 4 - Convert networkURIvsStoragePort to Initiator Port WWN vs NetworkURI
        Map<String, String> initiatorWWNvsNetworkURI = new HashMap<>();
        Set<URI> networkURIs = initiatorPortMap.keySet();
        for(URI networkURI : networkURIs) {
            List<StoragePort> initiatorPortList = initiatorPortMap.get(networkURI);
            List<String> initiatorWWNList = new ArrayList<>(initiatorPortList.size());
            for(StoragePort initPort : initiatorPortList) {
                initiatorWWNList.add(initPort.getPortNetworkId());
                initiatorWWNvsNetworkURI.put(initPort.getPortNetworkId(), networkURI.toString());
            }
            
        }        
        
        // STEP 5 - Consider directors to restrict paths not more than 4 for each director
        // And add the zoning map entries to adhere to the VPLEX best practices.
        Map<StoragePort, Integer> portUsage = new HashMap<>();
        Set<String> directorKeySet = directorToInitiatorIds.keySet();
        for(String director : directorKeySet) {
            Set<String> initiatorIds = directorToInitiatorIds.get(director);
            int directorPaths = 0;
            for(String initiatorId : initiatorIds) {
                if(4 == directorPaths) {
                    break;
                }
                
                String initWWN = initiatorUrivsWWNFromMask.get(initiatorId);
                String initiatorNetworkURI = initiatorWWNvsNetworkURI.get(initWWN);
                List<StoragePort> matchingTargetPorts = nwUriVsTargetPortsFromMask.get(initiatorNetworkURI);
                
                if(null!=matchingTargetPorts && !matchingTargetPorts.isEmpty()) {
                    StoragePort assignedPort = assignPortBasedOnUsage(matchingTargetPorts, portUsage);
                    StringSet targetPortURIs = new StringSet();
                    targetPortURIs.add(assignedPort.getId().toString());
                    _log.info(String.format("Adding zoning map entry - Initiator is %s and its targetPorts %s",
                            initiatorId, targetPortURIs.toString()));
                    exportMask.addZoningMapEntry(initiatorId, targetPortURIs);
                    directorPaths++;
                }
                
            }
            
        }
                
        // STEP 6 - persist the mask
        _dbClient.updateAndReindexObject(exportMask);
        
    }

    /**
     * Doing the best possible assignment. Matched target ports should
     * be assigned at least to one initiator.
     * 
     * @param matchingTargetPorts
     * @param portUsage
     * @return
     */
    private StoragePort assignPortBasedOnUsage(List<StoragePort> matchingTargetPorts,
            Map<StoragePort, Integer> portUsage) {
        
        StoragePort foundPort= null;
        for(StoragePort matchedPort : matchingTargetPorts) {
            
            if (portUsage.get(matchedPort) == null) {
                portUsage.put(matchedPort, 0);
            }
            
            if (foundPort == null) {
                foundPort = matchedPort;
            } else {
                if (portUsage.get(matchedPort) < portUsage.get(foundPort)) {
                    foundPort = matchedPort;
                }
            }
            
        }
        
        if (foundPort != null) {
            portUsage.put(foundPort, portUsage.get(foundPort) + 1);
        }
        
        return foundPort;
    }

    @Override
    public Method createOrAddVolumesToExportMaskMethod(URI arrayURI,
            URI exportGroupURI,
            URI exportMaskURI,
            Map<URI, Integer> volumeMap,
            TaskCompleter completer) {
        return new Workflow.Method("createOrAddVolumesToExportMask",
                arrayURI,
                exportGroupURI,
                exportMaskURI,
                volumeMap,
                completer);
    }

    @Override
    public void createOrAddVolumesToExportMask(URI arrayURI,
            URI exportGroupURI,
            URI exportMaskURI,
            Map<URI, Integer> volumeMap,
            TaskCompleter completer,
            String stepId) {
        _log.debug("START - createOrAddVolumesToExportMask");
        try {
            WorkflowStepCompleter.stepExecuting(stepId);
            StorageSystem array = _dbClient.queryObject(StorageSystem.class, arrayURI);
            ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskURI);

            // If the exportMask isn't found, or has been deleted, fail, ask user to retry.
            if (exportMask == null || exportMask.getInactive()) {
                _log.info(String.format("ExportMask %s deleted or inactive, failing", exportMaskURI));
                ServiceError svcerr = VPlexApiException.errors
                        .createBackendExportMaskDeleted(exportMaskURI.toString(),
                                arrayURI.toString());
                WorkflowStepCompleter.stepFailed(stepId, svcerr);
                return;
            }

            // Protect concurrent operations by locking {host, array} dupple.
            // Lock will be released when work flow step completes.
            List<String> lockKeys = ControllerLockingUtil.getHostStorageLockKeys(_dbClient,
                    ExportGroupType.Host,
                    StringSetUtil.stringSetToUriList(
                            exportMask.getInitiators()),
                    arrayURI);
            getWorkflowService().acquireWorkflowStepLocks(stepId, lockKeys,
                    LockTimeoutValue.get(LockType.VPLEX_BACKEND_EXPORT));

            // Refresh the ExportMask
            BlockStorageDevice device = _blockController.getDevice(array.getSystemType());

            if (!exportMask.hasAnyVolumes()) {
                /*
                 * We are creating this ExportMask on the hardware! (Maybe not
                 * the first time though...)
                 * 
                 * Fetch the Initiators
                 */
                List<Initiator> initiators = new ArrayList<Initiator>();
                for (String initiatorId : exportMask.getInitiators()) {
                    Initiator initiator = _dbClient.queryObject(Initiator.class,
                            URI.create(initiatorId));
                    if (initiator != null) {
                        initiators.add(initiator);
                    }
                }

                // Fetch the targets
                List<URI> targets = new ArrayList<URI>();
                for (String targetId : exportMask.getStoragePorts()) {
                    targets.add(URI.create(targetId));
                }

                if (volumeMap != null) {
                    for (URI volume : volumeMap.keySet()) {
                        exportMask.addVolume(volume, volumeMap.get(volume));
                    }
                }

                _dbClient.persistObject(exportMask);

                _log.debug(String.format("Calling doExportGroupCreate on the device %s",
                        array.getId().toString()));
                device.doExportGroupCreate(array, exportMask, volumeMap,
                        initiators, targets, completer);
            }
            else {
                _log.debug(String.format("Calling doExportAddVolumes on the device %s",
                        array.getId().toString()));
                device.doExportAddVolumes(array, exportMask, volumeMap, completer);
            }

        } catch (Exception ex) {
            _log.error("Failed to create or add volumes to export mask for cinder: ", ex);
            VPlexApiException vplexex = DeviceControllerExceptions.vplex.addStepsForCreateVolumesFailed(ex);
            WorkflowStepCompleter.stepFailed(stepId, vplexex);
        }

        _log.debug("END - createOrAddVolumesToExportMask");

    }

    @Override
    public void suggestExportMasksForPlacement(
            StorageSystem storage, BlockStorageDevice device, List<Initiator> initiators,
            ExportMaskPlacementDescriptor placementDescriptor) {
        super.suggestExportMasksForPlacement(storage, device, initiators, placementDescriptor);
    }

    @Override
    public Method deleteOrRemoveVolumesFromExportMaskMethod(URI arrayURI,
            URI exportGroupURI,
            URI exportMaskURI,
            List<URI> volumes,
            TaskCompleter completer) {
        return new Workflow.Method("deleteOrRemoveVolumesFromExportMask",
                arrayURI,
                exportGroupURI,
                exportMaskURI,
                volumes,
                completer);
    }

    @Override
    public void deleteOrRemoveVolumesFromExportMask(URI arrayURI,
            URI exportGroupURI,
            URI exportMaskURI,
            List<URI> volumes,
            TaskCompleter completer,
            String stepId) {
        try {
            WorkflowStepCompleter.stepExecuting(stepId);
            StorageSystem array = _dbClient.queryObject(StorageSystem.class, arrayURI);
            BlockStorageDevice device = _blockController.getDevice(array.getSystemType());
            ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskURI);

            // If the exportMask isn't found, or has been deleted, nothing to do.
            if (exportMask == null || exportMask.getInactive()) {
                _log.info(String.format("ExportMask %s inactive, returning success", exportMaskURI));
                WorkflowStepCompleter.stepSucceded(stepId);
                return;
            }

            // Protect concurrent operations by locking {host, array} dupple.
            // Lock will be released when work flow step completes.
            List<String> lockKeys = ControllerLockingUtil.getHostStorageLockKeys(_dbClient,
                    ExportGroupType.Host,
                    StringSetUtil.stringSetToUriList(
                            exportMask.getInitiators()),
                    arrayURI);
            getWorkflowService().acquireWorkflowStepLocks(stepId, lockKeys, LockTimeoutValue.get(LockType.VPLEX_BACKEND_EXPORT));

            // Make sure the completer will complete the work flow. This happens on roll back case.
            if (!completer.getOpId().equals(stepId)) {
                completer.setOpId(stepId);
            }

            // Refresh the ExportMask
            exportMask = refreshExportMask(array, device, exportMask);

            // Determine if we're deleting the last volume.
            Set<String> remainingVolumes = new HashSet<String>();
            if (exportMask.getVolumes() != null) {
                remainingVolumes.addAll(exportMask.getVolumes().keySet());
            }

            for (URI volume : volumes) {
                remainingVolumes.remove(volume.toString());
            }

            // If it is last volume, delete the ExportMask.
            if (remainingVolumes.isEmpty()
                    && (exportMask.getExistingVolumes() == null
                    || exportMask.getExistingVolumes().isEmpty())) {
                _log.debug(String.format("Calling doExportGroupDelete on the device %s",
                        array.getId().toString()));
                device.doExportGroupDelete(array, exportMask, completer);
            }
            else {
                _log.debug(String.format("Calling doExportRemoveVolumes on the device %s",
                        array.getId().toString()));
                device.doExportRemoveVolumes(array, exportMask, volumes, completer);
            }
        } catch (Exception ex) {
            _log.error("Failed to delete or remove volumes to export mask for cinder: ", ex);
            VPlexApiException vplexex = DeviceControllerExceptions.vplex.addStepsForDeleteVolumesFailed(ex);
            WorkflowStepCompleter.stepFailed(stepId, vplexex);
        }

    }

    /**
     * 
     * @param allocator
     * @param candidatePorts
     * @param portsRequested
     * @param nwLite
     * @param varrayURI
     * @return
     */
    private List<StoragePort> allocatePorts(StoragePortsAllocator allocator,
            List<StoragePort> candidatePorts,
            int portsRequested,
            NetworkLite nwLite,
            URI varrayURI) {
        return VPlexBackEndOrchestratorUtil.allocatePorts(allocator,
                candidatePorts,
                portsRequested,
                nwLite,
                varrayURI,
                simulation,
                _blockScheduler,
                _dbClient);
    }

}
