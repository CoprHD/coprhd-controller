/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.externaldevice;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.mutable.MutableBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.ExportPathParams;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.storagedriver.BlockStorageDriver;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.model.Initiator;
import com.emc.storageos.storagedriver.model.StoragePort;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.VolumeURIHLU;
import com.emc.storageos.volumecontroller.impl.smis.ExportMaskOperations;
import com.emc.storageos.volumecontroller.placement.BlockStorageScheduler;
import com.emc.storageos.workflow.WorkflowService;

public class ExternalDeviceExportOperations implements ExportMaskOperations {

    private static Logger log = LoggerFactory.getLogger(ExternalDeviceExportOperations.class);

    private DbClient dbClient;

    // Need this reference to get driver for device type.
    private ExternalBlockStorageDevice _externalDevice;
    private BlockStorageScheduler _blockScheduler;
    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }
    public void setExternalDevice(ExternalBlockStorageDevice externalDevice) {
        this._externalDevice = externalDevice;
    }
    public void setBlockScheduler(BlockStorageScheduler blockScheduler) {
        this._blockScheduler = blockScheduler;
    }
    @Override
    public void createExportMask(StorageSystem storage, URI exportMaskUri, VolumeURIHLU[] volumeURIHLUs, List<URI> targetURIList,
                                 List<com.emc.storageos.db.client.model.Initiator> initiatorList, TaskCompleter taskCompleter) throws DeviceControllerException {
        log.info("{} createExportMask START...", storage.getSerialNumber());
        log.info("Export mask id: {}", exportMaskUri);
        log.info("createExportMask: assignments: {}", targetURIList);
        log.info("createExportMask: initiators: {}", initiatorList);
        log.info("createExportMask: volume-HLU pairs: {}", volumeURIHLUs);

        try {
            BlockStorageDriver driver = _externalDevice.getDriver(storage.getSystemType());
            ExportMask exportMask = (ExportMask)dbClient.queryObject(exportMaskUri);

            // Load export group from context
            String stepId = taskCompleter.getOpId();
            URI exportGroupUri = (URI)WorkflowService.getInstance().loadStepData(stepId);
            ExportGroup exportGroup = (ExportGroup)dbClient.queryObject(exportGroupUri);
            Set<URI> volumeUris = new HashSet<>();
            for (VolumeURIHLU volumeURIHLU : volumeURIHLUs) {
                URI volumeURI = volumeURIHLU.getVolumeURI();
                volumeUris.add(volumeURI);
            }
            // Prepare volumes
            List<StorageVolume> driverVolumes = new ArrayList<>();
            Map<String, String> volumeToHLUMap = new HashMap<>();
            prepareVolumes(volumeURIHLUs, driverVolumes, volumeToHLUMap);

            // Prepare initiators
            List<Initiator> driverInitiators = new ArrayList<>();
            prepareInitiators(initiatorList, driverInitiators);

            // Prepare target storage ports
            List<StoragePort> recommendedPorts = new ArrayList<>();
            List<StoragePort> availablePorts = new ArrayList<>();
            List<StoragePort> selectedPorts = new ArrayList<>();
            // Prepare ports for driver call. Populate lists of recommended and available ports.
            Map<String, com.emc.storageos.db.client.model.StoragePort> nativeIdToAvailablePortMap = new HashMap<>();
            preparePorts(storage, exportMaskUri, targetURIList, recommendedPorts, availablePorts, nativeIdToAvailablePortMap);

            ExportPathParams pathParams = _blockScheduler.calculateExportPathParamForVolumes(
                    volumeUris, exportGroup.getNumPaths(), storage.getId(), exportGroupUri);
            StorageCapabilities capabilities = new StorageCapabilities();
            // Prepare num paths to send to driver
            prepareCapabilities(pathParams, capabilities);
            MutableBoolean usedRecommendedPorts = new MutableBoolean(true);
            // Ready to call driver
            DriverTask task = driver.exportVolumesToInitiators(driverInitiators, driverVolumes, volumeToHLUMap,
                    recommendedPorts, availablePorts, capabilities, usedRecommendedPorts,
                    selectedPorts);

            // TODO: this is short cut for now, assuming synchronous driver implementation
            // TODO: Support for async case TBD.
            if (task.getStatus() == DriverTask.TaskStatus.READY) {
                // If driver used recommended ports, we are done.
                // Otherwise, if driver did not used recommended ports, we have to get ports selected by driver
                // and use them in export mask and zones.
                // We will verify driver selected ports against available ports list.
                String msg = String.format("createExportMask -- Created export: %s . Used recommended ports: %s .",
                        task.getMessage(), usedRecommendedPorts);
                log.info(msg);
                if (usedRecommendedPorts.isFalse()) {
                    // process driver selected ports
                    if (validSelectedPorts(availablePorts, selectedPorts, pathParams)) {
                        List<com.emc.storageos.db.client.model.StoragePort> selectedPortsForMask = new ArrayList<>();
                        //List<com.emc.storageos.db.client.model.StoragePort> maskStoragePorts = ExportUtils.getStoragePorts(exportMask, dbClient);
                        for (StoragePort driverPort : selectedPorts) {
                            com.emc.storageos.db.client.model.StoragePort port = nativeIdToAvailablePortMap.get(driverPort.getNativeId());
                            selectedPortsForMask.add(port);
                        }
                        updateStoragePortsInExportMask(exportMask, initiatorList, exportGroup, selectedPortsForMask);
                    } else {
                        //  selected ports are not valid. failure

                    }
                }
                // Update volumes Lun Ids in export mask based on driver selection
                for (String volumeUriString : volumeToHLUMap.keySet()) {
                    String targetLunId = volumeToHLUMap.get(volumeUriString);
                    exportMask.getVolumes().put(volumeUriString, targetLunId);
                }

                dbClient.updateObject(exportMask);
                taskCompleter.ready(dbClient);
            } else {
                // failed
                // TODO support async
                // Set volumes to inactive state
                String errorMsg = String.format("createExportMask -- Failed to create export: %s .", task.getMessage());
                log.error(errorMsg);
                ServiceError serviceError = ExternalDeviceException.errors.createVolumesFailed("createExportMask", errorMsg);
                taskCompleter.error(dbClient, serviceError);
            }
        }    catch (Exception ex) {
            log.error("Problem in createExportMask: ", ex);
            log.error("createExportMask -- Failed to create export mask. ", ex);
            ServiceError serviceError = ExternalDeviceException.errors.createVolumesFailed("createExportMask", ex.getMessage());
            taskCompleter.error(dbClient, serviceError);
        }

        log.info("{} createExportMask END...", storage.getSerialNumber() );
    }

    @Override
    public void deleteExportMask(StorageSystem storage, URI exportMask, List<URI> volumeURIList, List<URI> targetURIList, List<com.emc.storageos.db.client.model.Initiator> initiatorList, TaskCompleter taskCompleter) throws DeviceControllerException {

    }

    @Override
    public void addInitiator(StorageSystem storage, URI exportMask, List<com.emc.storageos.db.client.model.Initiator> initiators, List<URI> targets, TaskCompleter taskCompleter) throws DeviceControllerException {

    }

    @Override
    public void removeInitiator(StorageSystem storage, URI exportMask, List<com.emc.storageos.db.client.model.Initiator> initiators, List<URI> targets, TaskCompleter taskCompleter) throws DeviceControllerException {

    }

    @Override
    public void addVolume(StorageSystem storage, URI exportMaskUri, VolumeURIHLU[] volumeURIHLUs, TaskCompleter taskCompleter) throws DeviceControllerException {
        log.info("{} addVolume START...", storage.getSerialNumber());
        log.info("Export mask id: {}", exportMaskUri);
        log.info("addVolume: volume-HLU pairs: {}", volumeURIHLUs);
        try {
            BlockStorageDriver driver = _externalDevice.getDriver(storage.getSystemType());

//        DriverTask task = driver.exportVolumesToInitiators(List < com.emc.storageos.storagedriver.model.Initiator > initiators, List < StorageVolume > volumes, Map < String, Integer > volumeToHLUMap,
//                List < StoragePort > recommendedPorts,
//                List < StoragePort > availablePorts, StorageCapabilities capabilities, MutableBoolean usedRecommendedPorts,
//                List < StoragePort > selectedPorts);
            DriverTask task = driver.exportVolumesToInitiators(null, null, null,
                    null,
                    null, null, null,
                    null);
            // TODO: this is short cut for now, assuming synchronous driver implementation
            // We will implement support for async case later.
            if (task.getStatus() == DriverTask.TaskStatus.READY) {
                String msg = String.format("createExportMask -- Created export: %s .", task.getMessage());
                log.info(msg);
                taskCompleter.ready(dbClient);
            } else {
                // failed
                // TODO support async
                // Set volumes to inactive state
                String errorMsg = String.format("createExportMask -- Failed to create export: %s .", task.getMessage());
                log.error(errorMsg);
                ServiceError serviceError = ExternalDeviceException.errors.createVolumesFailed("createExportMask", errorMsg);
                taskCompleter.error(dbClient, serviceError);
            }
        }  catch (Exception ex) {
            log.error("Problem in addVolume: ", ex);
            log.error("addVolume -- Failed to add volumes. ", ex);
            ServiceError serviceError = ExternalDeviceException.errors.createVolumesFailed("addVolumes", ex.getMessage());
            taskCompleter.error(dbClient, serviceError);
        }

        taskCompleter.ready(dbClient);

        log.info("{} addVolume END...", storage.getSerialNumber());
    }

    @Override
    public void removeVolume(StorageSystem storage, URI exportMask, List<URI> volume, TaskCompleter taskCompleter) throws DeviceControllerException {

    }


    @Override
    public Map<String, Set<URI>> findExportMasks(StorageSystem storage,
                                                 List<String> initiatorNames, boolean mustHaveAllPorts) {
        // not supported. There are no common masking concepts. So, return null.
        return null;
    }

    @Override
    public ExportMask refreshExportMask(StorageSystem storage, ExportMask mask) {
        return null;
    }

    @Override
    public void updateStorageGroupPolicyAndLimits(StorageSystem storage, ExportMask exportMask, List<URI> volumeURIs, VirtualPool newVirtualPool, boolean rollback, TaskCompleter taskCompleter) throws Exception {

    }

    @Override
    public Map<URI, Integer> getExportMaskHLUs(StorageSystem storage, ExportMask exportMask) {
        return null;
    }

    private void prepareVolumes(VolumeURIHLU[] volumeURIHLUs, List<StorageVolume> driverVolumes,
                                Map<String, String> volumeToHLUMap) {

        for (VolumeURIHLU volumeURIHLU : volumeURIHLUs) {
            URI volumeURI = volumeURIHLU.getVolumeURI();
            Volume volume = dbClient.queryObject(Volume.class, volumeURI);
            StorageVolume driverVolume = createDriverVolume(volume);
            driverVolumes.add(driverVolume);
            volumeToHLUMap.put(driverVolume.getNativeId(), volumeURIHLU.getHLU());
        }
    }

    private void prepareInitiators(List<com.emc.storageos.db.client.model.Initiator> initiatorList, List<Initiator> driverInitiators) {
        for (com.emc.storageos.db.client.model.Initiator initiator : initiatorList) {
            Initiator driverInitiator = createDriverInitiator(initiator);
            driverInitiators.add(driverInitiator);
        }
    }


    private void preparePorts(StorageSystem storage, URI exportMaskUri, List<URI> targetURIList,
                              List<StoragePort> recommendedPorts, List<StoragePort> availablePorts,
                              Map<String, com.emc.storageos.db.client.model.StoragePort> nativeIdToAvailablePortMap) {

        Iterator<com.emc.storageos.db.client.model.StoragePort> ports =
                dbClient.queryIterativeObjects(com.emc.storageos.db.client.model.StoragePort.class, targetURIList);

        while (ports.hasNext()) {
            StoragePort driverPort = createDriverPort(ports.next());
            recommendedPorts.add(driverPort);
        }

        List<ExportGroup> exportGroups = ExportUtils.getExportGroupsForMask(exportMaskUri, dbClient);
        URI varrayUri = exportGroups.get(0).getVirtualArray();
        List<com.emc.storageos.db.client.model.StoragePort> varrayPorts =
                getStorageSystemVarrayStoragePorts(storage, varrayUri);

        for(com.emc.storageos.db.client.model.StoragePort port : varrayPorts) {
            if (port.getNativeId() != null) {
                nativeIdToAvailablePortMap.put(port.getNativeId(), port);
            } else {
                // This is error condition. Each port for driver managed systems should have nativeId set at
                // discovery time.
                // TODO process error.
            }
            StoragePort driverPort = createDriverPort(port);
            availablePorts.add(driverPort);
        }
    }

    private void prepareCapabilities(ExportPathParams pathParams, StorageCapabilities capabilities) {

    }

    /**
     * Gets the list of storage ports of a storage system which belong to a virtual array.
     *
     * @param storage
     *            StorageSystem from which ports needs to be queried.
     * @param varrayURI
     *            URI of the virtual array
     * @return list of storage ports of storage system which belong to virtual array
     */
    private List<com.emc.storageos.db.client.model.StoragePort> getStorageSystemVarrayStoragePorts(
            StorageSystem storage, URI varrayURI) {
        log.debug("START - getStorageSystemVarrayStoragePorts");
        // Get the list of storage ports of a storage system which are in a given varray
        Map<URI, List<com.emc.storageos.db.client.model.StoragePort>> networkUriVsStoragePorts = ConnectivityUtil
                .getStoragePortsOfTypeAndVArray(dbClient, storage.getId(),
                        com.emc.storageos.db.client.model.StoragePort.PortType.frontend, varrayURI);

        List<com.emc.storageos.db.client.model.StoragePort> varrayTaggedStoragePorts = new ArrayList<>();

        Set<URI> networkUriSet = networkUriVsStoragePorts.keySet();
        for (URI nwUri : networkUriSet) {
            List<com.emc.storageos.db.client.model.StoragePort> ports = networkUriVsStoragePorts.get(nwUri);
            varrayTaggedStoragePorts.addAll(ports);
            log.info("Varray Tagged Ports for network {} are {}", nwUri, ports);
        }

        log.debug("END - getStorageSystemVarrayStoragePorts");

        return varrayTaggedStoragePorts;
    }


    private Boolean validSelectedPorts(List<StoragePort> availablePorts, List<StoragePort> selectedPorts, ExportPathParams pathParams) {
        boolean containmentCheck = availablePorts.containsAll(selectedPorts);
        boolean numPathCheck = (selectedPorts.size() < pathParams.getMinPaths());
        log.info(String.format("Validation check for selected ports: containmentCheck: %s, numPathCheck: %s .", containmentCheck, numPathCheck));
        return containmentCheck && numPathCheck;
    }

    private void updateStoragePortsInExportMask(ExportMask exportMask, List<com.emc.storageos.db.client.model.Initiator> initiatorList,
                                                ExportGroup exportGroup, List<com.emc.storageos.db.client.model.StoragePort> storagePorts) {

        // This method will update storage ports and zoning map in the export mask based on the storagePorts list.
        // Clean all existing targets in the export mask and add new targets
        List<URI> storagePortListFromMask = StringSetUtil.stringSetToUriList(exportMask.getStoragePorts());
        for (URI removeUri : storagePortListFromMask) {
            exportMask.removeTarget(removeUri);
        }
        exportMask.setStoragePorts(null);

        // Add new target ports
        for (com.emc.storageos.db.client.model.StoragePort port : storagePorts) {
            exportMask.addTarget(port.getId());
        }

        // Update zoning map based on provided storage ports
        _blockScheduler.updateZoningMap(exportMask, exportGroup.getVirtualArray(), exportGroup.getId());

    }

    private StorageVolume createDriverVolume(Volume volume) {
        return null;
    }

    private Initiator createDriverInitiator(com.emc.storageos.db.client.model.Initiator initiator) {
        return null;
    }

    private StoragePort createDriverPort(com.emc.storageos.db.client.model.StoragePort port) {
        return null;
    }

}
