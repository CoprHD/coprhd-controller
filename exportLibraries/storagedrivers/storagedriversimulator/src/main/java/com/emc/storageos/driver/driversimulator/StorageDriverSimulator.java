/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.driversimulator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang.mutable.MutableBoolean;
import org.apache.commons.lang.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.emc.storageos.driver.driversimulator.operations.CreateGroupCloneSimulatorOperation;
import com.emc.storageos.driver.driversimulator.operations.CreateVolumeCloneSimulatorOperation;
import com.emc.storageos.driver.driversimulator.operations.DriverSimulatorOperation;
import com.emc.storageos.driver.driversimulator.operations.ExpandVolumeSimulatorOperation;
import com.emc.storageos.driver.driversimulator.operations.RestoreFromCloneSimulatorOperation;
import com.emc.storageos.driver.driversimulator.operations.RestoreFromSnapshotSimulatorOperation;
import com.emc.storageos.storagedriver.BlockStorageDriver;
import com.emc.storageos.storagedriver.DefaultStorageDriver;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.HostExportInfo;
import com.emc.storageos.storagedriver.RegistrationData;
import com.emc.storageos.storagedriver.model.Initiator;
import com.emc.storageos.storagedriver.model.StorageBlockObject;
import com.emc.storageos.storagedriver.model.StorageHostComponent;
import com.emc.storageos.storagedriver.model.StorageObject;
import com.emc.storageos.storagedriver.model.StoragePool;
import com.emc.storageos.storagedriver.model.StoragePort;
import com.emc.storageos.storagedriver.model.StorageProvider;
import com.emc.storageos.storagedriver.model.StorageSystem;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.model.VolumeClone;
import com.emc.storageos.storagedriver.model.VolumeConsistencyGroup;
import com.emc.storageos.storagedriver.model.VolumeMirror;
import com.emc.storageos.storagedriver.model.VolumeSnapshot;
import com.emc.storageos.storagedriver.storagecapabilities.AutoTieringPolicyCapabilityDefinition;
import com.emc.storageos.storagedriver.storagecapabilities.CapabilityInstance;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;


public class StorageDriverSimulator extends DefaultStorageDriver implements BlockStorageDriver {

    private static final Logger _log = LoggerFactory.getLogger(StorageDriverSimulator.class);
    public static final String DRIVER_NAME = "SimulatorDriver";
    private static final int NUMBER_OF_VOLUME_PAGES = 3;
    private static final int NUMBER_OF_VOLUMES_ON_PAGE = 2;
    private static final int NUMBER_OF_CLONES_FOR_VOLUME = 2;
    private static final int NUMBER_OF_SNAPS_FOR_VOLUME = 2;
    private static final boolean VOLUMES_IN_CG = true;
    private static final boolean SNAPS_IN_CG = true;
    private static final boolean CLONES_IN_CG = true;
    private static final boolean GENERATE_EXPORT_DATA = true;
    private static final String SIMULATOR_CONF_FILE = "simulator-conf.xml";
    private static final String CONFIG_BEAN_NAME = "simulatorConfig";

    private ApplicationContext parentApplicationContext;
    private SimulatorConfiguration simulatorConfig;
    private Map<String, DriverSimulatorOperation> taskOperationMap = new HashMap<String, DriverSimulatorOperation>();
    private static Integer portIndex = 0;
    private static Map<String, Integer> systemNameToPortIndexName = new HashMap<>();

    // map for storage system to host export info data for a volume;
    // key: array native id
    // value: map where key is volume native id and value is list of volume export info object for this volume for different hosts (one entry for each host)
    private static Map<String, Map<String, List<HostExportInfo>>> arrayToVolumeToVolumeExportInfoMap = new HashMap<>();
    // defines which volume page is exported to which host
    private static Map<Integer, List<String>> pageToHostMap;
    private static Map<String, List<Integer>> hostToPageMap;
    static
    {
        pageToHostMap = new HashMap<>();
        pageToHostMap.put(0, Arrays.asList("10.20.30.40", "10.20.30.50"));
        pageToHostMap.put(1, Arrays.asList("10.20.30.50"));
        pageToHostMap.put(2, Arrays.asList("10.20.30.60"));

        hostToPageMap = new HashMap<>();
        hostToPageMap.put("10.20.30.40",Arrays.asList(0));
        hostToPageMap.put("10.20.30.50", Arrays.asList(0,1));
        hostToPageMap.put("10.20.30.60", Arrays.asList(2));
    }

    private static Map<String, List<String>> hostToInitiatorPortIdMap;
    static
    {
        // each host with two initiators
        hostToInitiatorPortIdMap = new HashMap<>();
        hostToInitiatorPortIdMap.put(pageToHostMap.get(0).get(0), new ArrayList<>(Arrays.asList("50:06:01:61:36:68:08:81", "50:06:01:61:36:68:08:82")));
        hostToInitiatorPortIdMap.put(pageToHostMap.get(1).get(0), new ArrayList<>(Arrays.asList("50:06:01:61:36:68:09:81", "50:06:01:61:36:68:09:82")));
        hostToInitiatorPortIdMap.put(pageToHostMap.get(2).get(0), new ArrayList<>(Arrays.asList("50:06:01:61:36:68:10:81", "50:06:01:61:36:68:10:82")));
    }
    
    //StorageDriver implementation

    public StorageDriverSimulator() {
        ApplicationContext context = new ClassPathXmlApplicationContext(new String[] {SIMULATOR_CONF_FILE}, parentApplicationContext);
        simulatorConfig = (SimulatorConfiguration) context.getBean(CONFIG_BEAN_NAME);
    }
    
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.parentApplicationContext = applicationContext;
    }
    
    @Override
    public RegistrationData getRegistrationData() {
        RegistrationData registrationData = new RegistrationData("driverSimulator", "driversystem", null);
        return registrationData;
    }

    @Override
    public DriverTask getTask(String taskId) {
        if (!taskOperationMap.containsKey(taskId)) {
            _log.error("Invalid task Id {}", taskId);
            return null;
        }
        
        DriverSimulatorOperation taskOperation = taskOperationMap.get(taskId);
        if (taskOperation.getLookupCount() < simulatorConfig.getMaxAsynchronousLookups()) {            
            taskOperation.incrementLookupCount();
            _log.info("This is lookup {} for task {}", taskOperation.getLookupCount(), taskId);
        } else {
            taskOperationMap.remove(taskId);
            if (simulatorConfig.getSimulateFailures()) {
                _log.info("Simulating asynchronous failure for task {} of type {}", taskId, taskOperation.getType());
                String errorMsg = taskOperation.getFailureMessage();
                taskOperation.doFailure(errorMsg);
            } else {
                _log.info("Simulating asynchronous success for task {} of type {}", taskId, taskOperation.getType());
                taskOperation.updateOnAsynchronousSuccess();
                String successMsg = taskOperation.getSuccessMessage();
                taskOperation.doSuccess(successMsg);
            }
        }
        
        return taskOperation.getDriverTask();
    }

    @Override
    public <T extends StorageObject> T getStorageObject(String storageSystemId, String objectId, Class<T> type) {
        if (StorageVolume.class.getSimpleName().equals(type.getSimpleName())) {
            StorageVolume obj = new StorageVolume();
            obj.setAllocatedCapacity(200L);
            _log.info("getStorageObject: storage volume allocated capacity: {}", obj.getAllocatedCapacity());
            return (T) obj;
        } else if (VolumeConsistencyGroup.class.getSimpleName().equals(type.getSimpleName())) {
            VolumeConsistencyGroup cg = new VolumeConsistencyGroup();
            cg.setStorageSystemId(storageSystemId);
            cg.setNativeId(objectId);
            cg.setDeviceLabel(objectId);
            _log.info("Return volume cg {} from array {}", objectId, storageSystemId);
            return (T) cg;
        } else if (StoragePool.class.getSimpleName().equals(type.getSimpleName())) {
            StoragePool pool = new StoragePool();
            pool.setFreeCapacity(40000000L); // 40 GB
            pool.setSubscribedCapacity(10000000L);  // 10 GB
            pool.setNativeId(objectId);
            pool.setStorageSystemId(storageSystemId);
            _log.info("getStorageObject: storage pool free capacity: {}, subscribed capacity: {}",
                    pool.getFreeCapacity(), pool.getSubscribedCapacity());
            return (T) pool;
        } else {
            _log.error("getStorageObject: not supported for type: {}", type.getSimpleName());
            return null;
        }
    }
    // DiscoveryDriver implementation

    @Override
    public DriverTask discoverStorageSystem(StorageSystem storageSystem) {
        _log.info("StorageDriver: discoverStorageSystem information for storage system {}, name {} - start",
                storageSystem.getIpAddress(), storageSystem.getSystemName());
        String taskType = "discover-storage-system";
        String taskId = String.format("%s+%s+%s", DRIVER_NAME, taskType, UUID.randomUUID().toString());
        DriverTask task = new DriverSimulatorTask(taskId);

        try {
            if (storageSystem.getSerialNumber() == null) {
            storageSystem.setSerialNumber(storageSystem.getSystemName());
            }
            if (storageSystem.getNativeId() == null) {
            storageSystem.setNativeId(storageSystem.getSystemName());
            }
            storageSystem.setFirmwareVersion("2.4-3.12");
            storageSystem.setIsSupportedVersion(true);
            setConnInfoToRegistry(storageSystem.getNativeId(), storageSystem.getIpAddress(), storageSystem.getPortNumber(),
                    storageSystem.getUsername(), storageSystem.getPassword());
            // Support both, element and group replicas.
            Set<StorageSystem.SupportedReplication> supportedReplications = new HashSet<>();
            supportedReplications.add(StorageSystem.SupportedReplication.elementReplica);
            supportedReplications.add(StorageSystem.SupportedReplication.groupReplica);
            storageSystem.setSupportedReplications(supportedReplications);


            task.setStatus(DriverTask.TaskStatus.READY);
            _log.info("StorageDriver: discoverStorageSystem information for storage system {}, nativeId {} - end",
                    storageSystem.getIpAddress(), storageSystem.getNativeId());
            return task;
        } catch (Exception e) {
            task.setStatus(DriverTask.TaskStatus.FAILED);
            e.printStackTrace();
        }
        return task;

    }

    @Override
    public DriverTask discoverStoragePools(StorageSystem storageSystem, List<StoragePool> storagePools) {

        _log.info("Discovery of storage pools for storage system {} .", storageSystem.getNativeId());
        String taskType = "discover-storage-pools";
        String taskId = String.format("%s+%s+%s", DRIVER_NAME, taskType, UUID.randomUUID().toString());
        DriverTask task = new DriverSimulatorTask(taskId);
        AutoTieringPolicyCapabilityDefinition capabilityDefinition = new AutoTieringPolicyCapabilityDefinition();

        try {
            // Get connection information.
            Map<String, List<String>> connectionInfo =
                    driverRegistry.getDriverAttributesForKey("StorageDriverSimulator", storageSystem.getNativeId());
            _log.info("Storage system connection info: {} : {}", storageSystem.getNativeId(), connectionInfo);
            for (int i =0; i <= 2; i++ ) {
                StoragePool pool = new StoragePool();
                pool.setNativeId("pool-1234577-" + i + storageSystem.getNativeId());
                pool.setStorageSystemId(storageSystem.getNativeId());
                _log.info("Discovered Pool {}, storageSystem {}", pool.getNativeId(), pool.getStorageSystemId());

                pool.setDeviceLabel("er-pool-1234577" + i + storageSystem.getNativeId());
                pool.setPoolName(pool.getDeviceLabel());
                Set<StoragePool.Protocols> protocols = new HashSet<>();
                protocols.add(StoragePool.Protocols.FC);
                protocols.add(StoragePool.Protocols.iSCSI);
                //protocols.add(StoragePool.Protocols.ScaleIO);
                pool.setProtocols(protocols);
                pool.setPoolServiceType(StoragePool.PoolServiceType.block);
                pool.setMaximumThickVolumeSize(3000000L);
                pool.setMinimumThickVolumeSize(1000L);
                pool.setMaximumThinVolumeSize(5000000L);
                pool.setMinimumThinVolumeSize(1000L);
                if (i%2 == 0) {
                    pool.setSupportedResourceType(StoragePool.SupportedResourceType.THIN_ONLY);
                } else {
                    pool.setSupportedResourceType(StoragePool.SupportedResourceType.THICK_ONLY);
                }

                pool.setSubscribedCapacity(5000000L);
                pool.setFreeCapacity(45000000L);
                pool.setTotalCapacity(48000000L);
                pool.setOperationalStatus(StoragePool.PoolOperationalStatus.READY);
                Set<StoragePool.SupportedDriveTypes> supportedDriveTypes = new HashSet<>();
                supportedDriveTypes.add(StoragePool.SupportedDriveTypes.FC);
                supportedDriveTypes.add(StoragePool.SupportedDriveTypes.SATA);
                pool.setSupportedDriveTypes(supportedDriveTypes);

                //            Set<StoragePool.RaidLevels> raidLevels = new HashSet<>();
                //            raidLevels.add(StoragePool.RaidLevels.RAID5);
                //            raidLevels.add(StoragePool.RaidLevels.RAID6);
                //            pool.setSupportedRaidLevels(raidLevels);
                
                
                List<CapabilityInstance> capabilities = new ArrayList<>();
                for (int j = 1; j <= 2; j++) {
                    String policyId = "Auto-Tier-Policy-" + i + j;
                    Map<String, List<String>> props = new HashMap<>();
                    props.put(AutoTieringPolicyCapabilityDefinition.PROPERTY_NAME.POLICY_ID.name(), Arrays.asList(policyId));
                    String provisioningType;
                    if (i%2 == 0) {
                        provisioningType = StoragePool.AutoTieringPolicyProvisioningType.ThinlyProvisioned.name();
                    } else {
                        provisioningType = StoragePool.AutoTieringPolicyProvisioningType.ThicklyProvisioned.name();
                    }
                    props.put(AutoTieringPolicyCapabilityDefinition.PROPERTY_NAME.PROVISIONING_TYPE.name(), Arrays.asList(provisioningType));
                    CapabilityInstance capabilityInstance = new CapabilityInstance(capabilityDefinition.getId(), policyId, props);
                    capabilities.add(capabilityInstance);
                }
                pool.setCapabilities(capabilities);

                storagePools.add(pool);

            }
            task.setStatus(DriverTask.TaskStatus.READY);
            _log.info("StorageDriver: discoverStoragePools information for storage system {}, nativeId {} - end",
                    storageSystem.getIpAddress(), storageSystem.getNativeId());
        } catch (Exception e) {
            task.setStatus(DriverTask.TaskStatus.FAILED);
            e.printStackTrace();
        }
        return task;
    }

    @Override
    public DriverTask discoverStoragePorts(StorageSystem storageSystem, List<StoragePort> storagePorts) {
        _log.info("Discovery of storage ports for storage system {} .", storageSystem.getNativeId());

        int index = 0;
        // Get "portIndexes" attribute map
        Map<String, List<String>> portIndexes = driverRegistry.getDriverAttributesForKey("simulatordriver", "portIndexes");
        if (portIndexes != null) {
            List<String>  indexes = portIndexes.get(storageSystem.getNativeId());
            if (indexes != null) {
                index = Integer.parseInt(indexes.get(0));
                _log.info("Storage ports index for storage system {} is {} .", storageSystem.getNativeId(), index);
            }
        }

        if (index == 0) {
            // no index for this system in the registry
            // get the last used index and increment by 1 to generate an index
            if (portIndexes != null) {
                List<String> indexes = portIndexes.get("lastIndex");
                if (indexes != null) {
                    index = Integer.parseInt(indexes.get(0)) + 1;
                } else {
                    index ++;
                }
            } else {
                index ++;
            }
            // set this index for the system in registry
            driverRegistry.addDriverAttributeForKey("simulatordriver", "portIndexes", storageSystem.getNativeId(),
                    Collections.singletonList(String.valueOf(index)));
            driverRegistry.addDriverAttributeForKey("simulatordriver", "portIndexes", "lastIndex",
                    Collections.singletonList(String.valueOf(index)));
            _log.info("Storage ports index for storage system {} is {} .", storageSystem.getNativeId(), index);
        }

//        Integer index = systemNameToPortIndexName.get(storageSystem.getNativeId());
//        if(index == null) {
//            // Get "portIndexes" attribute map
//            //Map<String, List<String>> portIndexes = driverRegistry.getDriverAttributesForKey("simulatordriver", "portIndexes");
//
//            index = ++portIndex;
//            systemNameToPortIndexName.put(storageSystem.getNativeId(), index);
//        }

        // Create ports with network
        for (int i =0; i <= 2; i++ ) {
            StoragePort port = new StoragePort();
            port.setNativeId("port-1234577-" + i + storageSystem.getNativeId());
            port.setStorageSystemId(storageSystem.getNativeId());
            _log.info("Discovered Port {}, storageSystem {}", port.getNativeId(), port.getStorageSystemId());

            port.setDeviceLabel("er-port-1234577" + i + storageSystem.getNativeId());
            port.setPortName(port.getDeviceLabel());
            //port.setNetworkId("er-network77"+ storageSystem.getNativeId());
            port.setNetworkId("11");
            port.setTransportType(StoragePort.TransportType.FC);
            //port.setTransportType(StoragePort.TransportType.IP);
            port.setPortNetworkId("6" + Integer.toHexString(index) + ":FE:FE:FE:FE:FE:FE:1" + i);
            port.setOperationalStatus(StoragePort.OperationalStatus.OK);
            port.setPortHAZone("zone-"+i);
            storagePorts.add(port);
        }

        // Create ports without network
        for (int i =3; i <= 6; i++ ) {
            StoragePort port = new StoragePort();
            port.setNativeId("port-1234577-" + i+ storageSystem.getNativeId());
            port.setStorageSystemId(storageSystem.getNativeId());
            _log.info("Discovered Port {}, storageSystem {}", port.getNativeId(), port.getStorageSystemId());

            port.setDeviceLabel("er-port-1234577" + i+ storageSystem.getNativeId());
            port.setPortName(port.getDeviceLabel());
            //port.setNetworkId("er-network77"+ storageSystem.getNativeId());
            port.setTransportType(StoragePort.TransportType.FC);
            port.setPortNetworkId("6" + Integer.toHexString(index) + ":FE:FE:FE:FE:FE:FE:1" + i);
            port.setOperationalStatus(StoragePort.OperationalStatus.OK);
            port.setPortHAZone("zone-with-many-ports");
            storagePorts.add(port);
        }

        String taskType = "discover-storage-ports";
        String taskId = String.format("%s+%s+%s", DRIVER_NAME, taskType, UUID.randomUUID().toString());
        DriverTask task = new DriverSimulatorTask(taskId);
        task.setStatus(DriverTask.TaskStatus.READY);
        _log.info("StorageDriver: discoverStoragePorts information for storage system {}, nativeId {} - end",
                storageSystem.getIpAddress(), storageSystem.getNativeId());
        return task;

    }
    
    @Override
    public DriverTask stopManagement(StorageSystem driverStorageSystem){
    	_log.info("Stopping management for StorageSystem {}", driverStorageSystem.getNativeId());
    	String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, "stopManagement", UUID.randomUUID().toString());
        DriverTask task = new DriverSimulatorTask(taskId);
        task.setStatus(DriverTask.TaskStatus.READY);
        
        String msg = String.format("Driver stopped managing storage system %s.",driverStorageSystem.getNativeId());
        _log.info(msg);
        task.setMessage(msg);
        
        return task;
    }

    @Override
    public DriverTask createVolumes(List<StorageVolume> volumes, StorageCapabilities capabilities) {

        //String newVolumes = "";
        Set<String> newVolumes = new HashSet<>();

        for (StorageVolume volume : volumes) {
            volume.setNativeId("driverSimulatorVolume" + UUID.randomUUID().toString());
            volume.setAccessStatus(StorageVolume.AccessStatus.READ_WRITE);
            volume.setProvisionedCapacity(volume.getRequestedCapacity());
            volume.setAllocatedCapacity(volume.getRequestedCapacity());
            volume.setDeviceLabel(volume.getNativeId());
            volume.setWwn(String.format("%s%s", volume.getStorageSystemId(), volume.getNativeId()));

            // newVolumes = newVolumes + volume.getNativeId() + " ";
            newVolumes.add(volume.getNativeId());
        }
        String taskType = "create-storage-volumes";
        String taskId = String.format("%s+%s+%s", DRIVER_NAME, taskType, UUID.randomUUID().toString());
        DriverTask task = new DriverSimulatorTask(taskId);
        task.setStatus(DriverTask.TaskStatus.READY);

        String msg = String.format("StorageDriver: createVolumes information for storage system %s, volume nativeIds %s - end",
                volumes.get(0).getStorageSystemId(), newVolumes.toString());
        _log.info(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask expandVolume(StorageVolume volume, long newCapacity) {
        ExpandVolumeSimulatorOperation expandVolumeSimulatorOperation = new ExpandVolumeSimulatorOperation(volume, newCapacity);
        if (simulatorConfig.getSimulateAsynchronousResponses()) {
            DriverTask driverTask = expandVolumeSimulatorOperation.getDriverTask();
            taskOperationMap.put(driverTask.getTaskId(), expandVolumeSimulatorOperation);
            return driverTask;
        } else if (simulatorConfig.getSimulateFailures()) {
            String failMsg = expandVolumeSimulatorOperation.getFailureMessage();
            return expandVolumeSimulatorOperation.doFailure(failMsg);
        } else {
            expandVolumeSimulatorOperation.updateVolumeInfo(volume, newCapacity);
            String successMsg = expandVolumeSimulatorOperation.getSuccessMessage(volume);
            return expandVolumeSimulatorOperation.doSuccess(successMsg);
        }
    }

    @Override
    public DriverTask deleteVolume(StorageVolume volume) {

        String taskType = "delete-storage-volumes";
        String taskId = String.format("%s+%s+%s", DRIVER_NAME, taskType, UUID.randomUUID().toString());
        DriverTask task = new DriverSimulatorTask(taskId);
        task.setStatus(DriverTask.TaskStatus.READY);

        _log.info("StorageDriver: deleteVolumes information for storage system {}, volume nativeIds {} - end",
                volume.getStorageSystemId(), volume.toString());
        return task;
    }
    
    @Override
    public DriverTask addVolumesToConsistencyGroup (List<StorageVolume> volumes, StorageCapabilities capabilities){
    	_log.info("Adding {} Volumes to Consistency Group {}", volumes.toString(), volumes.get(0).getConsistencyGroup());
        String taskType = "add-volumes-to-consistency-groupd";
        String taskId = String.format("%s+%s+%s", DRIVER_NAME, taskType, UUID.randomUUID().toString());
        DriverTask task = new DriverSimulatorTask(taskId);
        task.setStatus(DriverTask.TaskStatus.READY);
        
        String msg = String.format("StorageDriver: addVolumesToConsistencyGroup information for storage system %s, volume nativeIds %s, Consistency Group - end",
        		volumes.get(0).getStorageSystemId(), volumes.toString());
        _log.info(msg);
        task.setMessage(msg);
        
        return task;
    }
    
    @Override
    public DriverTask removeVolumesFromConsistencyGroup(List<StorageVolume> volumes, StorageCapabilities capabilities){
    	_log.info("Remove {} Volumes from Consistency Group {}", volumes.toString(), volumes.get(0).getConsistencyGroup());
        String taskType = "remove-volumes-to-consistency-groupd";
        String taskId = String.format("%s+%s+%s", DRIVER_NAME, taskType, UUID.randomUUID().toString());
        DriverTask task = new DriverSimulatorTask(taskId);
        task.setStatus(DriverTask.TaskStatus.READY);
        
        String msg = String.format("StorageDriver: removeVolumesFromConsistencyGroup information for storage system %s, volume nativeIds %s, Consistency Group - end",
                volumes.get(0).getStorageSystemId(), volumes.toString());
        _log.info(msg);
        task.setMessage(msg);
        
        return task;
    }

    @Override
    public DriverTask createVolumeSnapshot(List<VolumeSnapshot> snapshots, StorageCapabilities capabilities) {
        String snapTimestamp = Long.toString(System.currentTimeMillis());
        for (VolumeSnapshot snapshot : snapshots) {
            snapshot.setNativeId("snap-" + snapshot.getParentId() + UUID.randomUUID().toString());
            snapshot.setConsistencyGroup(snapTimestamp);
            snapshot.setAllocatedCapacity(1000L);
            snapshot.setProvisionedCapacity(2000L);
        }
        String taskType = "create-volume-snapshot";
        String taskId = String.format("%s+%s+%s", DRIVER_NAME, taskType, UUID.randomUUID().toString());
        DriverTask task = new DriverSimulatorTask(taskId);
        task.setStatus(DriverTask.TaskStatus.READY);

        _log.info("StorageDriver: createVolumeSnapshot information for storage system {}, snapshots nativeIds {} - end",
                snapshots.get(0).getStorageSystemId(), snapshots.toString());
        return task;
    }

    @Override
    public DriverTask restoreSnapshot(List<VolumeSnapshot> snapshots) {
        RestoreFromSnapshotSimulatorOperation restoreSnapshotSimulatorOperation = new RestoreFromSnapshotSimulatorOperation(snapshots);
        if (simulatorConfig.getSimulateAsynchronousResponses()) {
            DriverTask driverTask = restoreSnapshotSimulatorOperation.getDriverTask();
            taskOperationMap.put(driverTask.getTaskId(), restoreSnapshotSimulatorOperation);
            return driverTask;
        } else if (simulatorConfig.getSimulateFailures()) {
            String failMsg = restoreSnapshotSimulatorOperation.getFailureMessage();
            return restoreSnapshotSimulatorOperation.doFailure(failMsg);
        } else {
            String successMsg = restoreSnapshotSimulatorOperation.getSuccessMessage(snapshots);
            return restoreSnapshotSimulatorOperation.doSuccess(successMsg);
        }        
    }

    @Override
    public DriverTask deleteVolumeSnapshot(VolumeSnapshot snapshot) {
        String taskType = "delete-volume-snapshot";
        String taskId = String.format("%s+%s+%s", DRIVER_NAME, taskType, UUID.randomUUID().toString());
        DriverTask task = new DriverSimulatorTask(taskId);
        task.setStatus(DriverTask.TaskStatus.READY);
        String msg = String.format("StorageDriver: deleteVolumSnapshot for storage system %s, " +
                        "snapshots nativeId %s - end",
                snapshot.getStorageSystemId(), snapshot.toString());
        _log.info(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask createVolumeClone(List<VolumeClone> clones, StorageCapabilities capabilities) {
        CreateVolumeCloneSimulatorOperation createCloneSimulatorOperation = new CreateVolumeCloneSimulatorOperation(clones);
        if (simulatorConfig.getSimulateAsynchronousResponses()) {
            DriverTask driverTask = createCloneSimulatorOperation.getDriverTask();
            taskOperationMap.put(driverTask.getTaskId(), createCloneSimulatorOperation);
            return driverTask;
        } else if (simulatorConfig.getSimulateFailures()) {
            String failMsg = createCloneSimulatorOperation.getFailureMessage();
            return createCloneSimulatorOperation.doFailure(failMsg);
        } else {
            createCloneSimulatorOperation.updateCloneInfo(clones);
            String successMsg = createCloneSimulatorOperation.getSuccessMessage(clones);
            return createCloneSimulatorOperation.doSuccess(successMsg);
        }
    }

    @Override
    public DriverTask detachVolumeClone(List<VolumeClone> clones) {
        String taskType = "detach-volume-clone";
        String taskId = String.format("%s+%s+%s", DRIVER_NAME, taskType, UUID.randomUUID().toString());
        DriverTask task = new DriverSimulatorTask(taskId);
        task.setStatus(DriverTask.TaskStatus.READY);
        String msg = String.format("StorageDriver: detachVolumeClone for storage system %s, " +
                        "clones nativeId %s - end",
                clones.get(0).getStorageSystemId(), clones.toString());
        _log.info(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask restoreFromClone(List<VolumeClone> clones) {
        RestoreFromCloneSimulatorOperation restoreCloneSimulatorOperation = new RestoreFromCloneSimulatorOperation(clones);
        if (simulatorConfig.getSimulateAsynchronousResponses()) {
            DriverTask driverTask = restoreCloneSimulatorOperation.getDriverTask();
            taskOperationMap.put(driverTask.getTaskId(), restoreCloneSimulatorOperation);
            return driverTask;
        } else if (simulatorConfig.getSimulateFailures()) {
            String failMsg = restoreCloneSimulatorOperation.getFailureMessage();
            return restoreCloneSimulatorOperation.doFailure(failMsg);
        } else {
            restoreCloneSimulatorOperation.updateCloneInfo(clones);
            String successMsg = restoreCloneSimulatorOperation.getSuccessMessage(clones);
            return restoreCloneSimulatorOperation.doSuccess(successMsg);
        }        
    }


    @Deprecated
    public DriverTask deleteVolumeClone(VolumeClone clone) {
        String taskType = "delete-volume-clone";
        String taskId = String.format("%s+%s+%s", DRIVER_NAME, taskType, UUID.randomUUID().toString());
        DriverTask task = new DriverSimulatorTask(taskId);
        task.setStatus(DriverTask.TaskStatus.READY);
        String msg = String.format("StorageDriver: deleteVolumeClone for storage system %s, " +
                        "clones nativeId %s - end",
                clone.getStorageSystemId(), clone.toString());
        _log.info(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask createVolumeMirror(List<VolumeMirror> mirrors, StorageCapabilities capabilities) {
        return null;
    }

    @Override
    public DriverTask deleteVolumeMirror(VolumeMirror mirror) {
        return null;
    }

    @Override
    public DriverTask splitVolumeMirror(List<VolumeMirror> mirrors) {
        return null;
    }

    @Override
    public DriverTask resumeVolumeMirror(List<VolumeMirror> mirrors) {
        return null;
    }

    @Override
    public Map<String, HostExportInfo> getVolumeExportInfoForHosts(StorageVolume volume) {

        _log.info("Processing export info for volume: {}", volume);
        Map<String, HostExportInfo> exportInfoMap = getStorageObjectExportInfo(volume.getStorageSystemId(), volume.getNativeId());
        _log.info("Export info data for volume {}: {} .", volume, exportInfoMap);

        return exportInfoMap;
    }

    @Override
    public Map<String, HostExportInfo> getSnapshotExportInfoForHosts(VolumeSnapshot snapshot) {
        _log.info("Processing export info for snapshot: {}", snapshot);
        Map<String, HostExportInfo> exportInfoMap = getStorageObjectExportInfo(snapshot.getStorageSystemId(), snapshot.getNativeId());
        _log.info("Export info data for volume {}: {} .", snapshot, exportInfoMap);

        return exportInfoMap;
    }

    @Override
    public Map<String, HostExportInfo> getCloneExportInfoForHosts(VolumeClone clone) {
        _log.info("Processing export info for volume: {}", clone);
        Map<String, HostExportInfo> exportInfoMap = getStorageObjectExportInfo(clone.getStorageSystemId(), clone.getNativeId());
        _log.info("Export info data for volume {}: {} .", clone, exportInfoMap);

        return exportInfoMap;
    }

    @Override
    public Map<String, HostExportInfo> getMirrorExportInfoForHosts(VolumeMirror mirror) {
        _log.info("Processing export info for volume: {}", mirror);
        Map<String, HostExportInfo> exportInfoMap = getStorageObjectExportInfo(mirror.getStorageSystemId(), mirror.getNativeId());
        _log.info("Export info data for volume {}: {} .", mirror, exportInfoMap);

        return exportInfoMap;
    }

    private Map<String, HostExportInfo> getStorageObjectExportInfo(String systemId, String objectId) {
        Map<String, HostExportInfo> resultMap = new HashMap<>();
        Map<String, List<HostExportInfo>> volumeToHostExportInfoMap = arrayToVolumeToVolumeExportInfoMap.get(systemId);
        // get storage object export data
        if (volumeToHostExportInfoMap != null) {
            List<HostExportInfo> volumeExportInfo = volumeToHostExportInfoMap.get(objectId);
            if (volumeExportInfo != null) {
                for (HostExportInfo exportInfo : volumeExportInfo) {
                    resultMap.put(exportInfo.getHostName(), exportInfo);
                }
            }
        }
        return resultMap;
    }

    @Override
    public DriverTask exportVolumesToInitiators(List<Initiator> initiators, List<StorageVolume> volumes, Map<String, String> volumeToHLUMap,
                                                List<StoragePort> recommendedPorts,
                                                List<StoragePort> availablePorts, StorageCapabilities capabilities, MutableBoolean usedRecommendedPorts,
                                                List<StoragePort> selectedPorts) {

        usedRecommendedPorts.setValue(true);
        selectedPorts.addAll(recommendedPorts);

        String taskType = "export-volumes-to-initiators";
        String taskId = String.format("%s+%s+%s", DRIVER_NAME, taskType, UUID.randomUUID().toString());
        DriverTask task = new DriverSimulatorTask(taskId);
        task.setStatus(DriverTask.TaskStatus.READY);
        String msg = String.format("StorageDriver: exportVolumesToInitiators: export type %s, initiators %s .",
                initiators.get(0).getInitiatorType(), initiators.toString());
        _log.info(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask unexportVolumesFromInitiators(List<Initiator> initiators, List<StorageVolume> volumes) {
        String taskType = "unexport-volumes-from-initiators";
        String taskId = String.format("%s+%s+%s", DRIVER_NAME, taskType, UUID.randomUUID().toString());
        DriverTask task = new DriverSimulatorTask(taskId);
        task.setStatus(DriverTask.TaskStatus.READY);
        String msg = String.format("StorageDriver: unexportVolumesFromInitiators: export type %s, initiators %s .",
                initiators.get(0).getInitiatorType(), initiators.toString());
        _log.info(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask createConsistencyGroup(VolumeConsistencyGroup consistencyGroup) {

        consistencyGroup.setNativeId(consistencyGroup.getDisplayName());
        consistencyGroup.setDeviceLabel(consistencyGroup.getDisplayName());
        String taskType = "create-volume-cg";
        String taskId = String.format("%s+%s+%s", DRIVER_NAME, taskType, UUID.randomUUID().toString());
        DriverTask task = new DriverSimulatorTask(taskId);
        task.setStatus(DriverTask.TaskStatus.READY);

        String msg = String.format("StorageDriver: createConsistencyGroup information for storage system %s, consistencyGroup nativeId %s - end",
                consistencyGroup.getStorageSystemId(), consistencyGroup.getNativeId());
        _log.info(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask deleteConsistencyGroup(VolumeConsistencyGroup consistencyGroup) {

        String taskType = "delete-volume-cg";
        String taskId = String.format("%s+%s+%s", DRIVER_NAME, taskType, UUID.randomUUID().toString());
        DriverTask task = new DriverSimulatorTask(taskId);
        task.setStatus(DriverTask.TaskStatus.READY);
        String msg = String.format("StorageDriver: deleteConsistencyGroup information for storage system %s, consistencyGroup nativeId %s - end",
                consistencyGroup.getStorageSystemId(), consistencyGroup.getNativeId());
        _log.info(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask createConsistencyGroupSnapshot(VolumeConsistencyGroup consistencyGroup, List<VolumeSnapshot> snapshots, List<CapabilityInstance> capabilities) {
        String snapTimestamp = Long.toString(System.currentTimeMillis());
        for (VolumeSnapshot snapshot : snapshots) {
            snapshot.setNativeId("snap-" + snapshot.getParentId() + consistencyGroup.getDisplayName() + UUID.randomUUID().toString());
            snapshot.setConsistencyGroup(snapTimestamp);
            snapshot.setSnapSetId(snapTimestamp);
        }
        String taskType = "create-group-snapshot";
        String taskId = String.format("%s+%s+%s", DRIVER_NAME, taskType, UUID.randomUUID().toString());
        DriverTask task = new DriverSimulatorTask(taskId);
        task.setStatus(DriverTask.TaskStatus.READY);
        task.setMessage("Created snapshots for consistency group " + snapshots.get(0).getConsistencyGroup());

        _log.info("StorageDriver: createGroupSnapshot information for storage system {}, snapshots nativeIds {} - end",
                snapshots.get(0).getStorageSystemId(), snapshots.toString());
        return task;
    }

    @Override
    public DriverTask deleteConsistencyGroupSnapshot(List<VolumeSnapshot> snapshots) {
        String taskType = "delete-volume-cg-snapshot";
        String taskId = String.format("%s+%s+%s", DRIVER_NAME, taskType, UUID.randomUUID().toString());
        DriverTask task = new DriverSimulatorTask(taskId);
        task.setStatus(DriverTask.TaskStatus.READY);
        String msg = String.format("StorageDriver: deleteConsistencyGroupSnapshot for storage system %s, " +
                        "snapshot consistencyGroup nativeId %s, group snapshots %s - end",
                snapshots.get(0).getStorageSystemId(), snapshots.get(0).getConsistencyGroup(), snapshots.toString());
        _log.info(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask createConsistencyGroupClone(VolumeConsistencyGroup consistencyGroup, List<VolumeClone> clones, List<CapabilityInstance> capabilities) {
        CreateGroupCloneSimulatorOperation createCloneSimulatorOperation = new CreateGroupCloneSimulatorOperation(consistencyGroup, clones);
        if (simulatorConfig.getSimulateAsynchronousResponses()) {
            DriverTask driverTask = createCloneSimulatorOperation.getDriverTask();
            taskOperationMap.put(driverTask.getTaskId(), createCloneSimulatorOperation);
            return driverTask;
        } else if (simulatorConfig.getSimulateFailures()) {
            String failMsg = createCloneSimulatorOperation.getFailureMessage();
            return createCloneSimulatorOperation.doFailure(failMsg);
        } else {
            createCloneSimulatorOperation.updateGroupCloneInfo(consistencyGroup, clones);
            String successMsg = createCloneSimulatorOperation.getSuccessMessage(clones);
            return createCloneSimulatorOperation.doSuccess(successMsg);
        }
    }

    @Override
    public DriverTask getStorageVolumes(StorageSystem storageSystem, List<StorageVolume> storageVolumes, MutableInt token) {

        // create set of native volumes for our storage pools
        // all volumes on the same page belong to the same consistency group
        if (token.intValue() == 0) {
            arrayToVolumeToVolumeExportInfoMap.clear();
        }

        List<StoragePort> ports = new ArrayList<>();
        discoverStoragePorts(storageSystem, ports);

        for (int vol = 0; vol < NUMBER_OF_VOLUMES_ON_PAGE; vol ++) {
            StorageVolume driverVolume = new StorageVolume();
            driverVolume.setStorageSystemId(storageSystem.getNativeId());
            driverVolume.setStoragePoolId("pool-1234577-" + token.intValue() + storageSystem.getNativeId());
            driverVolume.setNativeId("driverSimulatorVolume-1234567-" + token.intValue() + "-" + vol);
            if (VOLUMES_IN_CG) {
                driverVolume.setConsistencyGroup("driverSimulatorCG-" + token.intValue());
            }
            driverVolume.setAccessStatus(StorageVolume.AccessStatus.READ_WRITE);
            driverVolume.setThinlyProvisioned(true);
            driverVolume.setThinVolumePreAllocationSize(3000L);
            driverVolume.setProvisionedCapacity(3*1024*1024*1024L);
            driverVolume.setAllocatedCapacity(50000L);
            driverVolume.setDeviceLabel(driverVolume.getNativeId());
            driverVolume.setWwn(String.format("%s%s", driverVolume.getStorageSystemId(), driverVolume.getNativeId()));
            storageVolumes.add(driverVolume);
            _log.info("Unmanaged volume info: pool {}, volume {}", driverVolume.getStoragePoolId(), driverVolume);

            if (GENERATE_EXPORT_DATA) {
                // add entry to arrayToVolumeToVolumeExportInfoMap for this volume
                // get host for this page
                for (String hostName : pageToHostMap.get(token.intValue())) {
                    _log.info("Process host {}", hostName);
                    generateExportDataForVolume(hostName, driverVolume.getStorageSystemId(), driverVolume.getNativeId(),
                            vol, ports, token.intValue());
                }
            }
        }

        String taskType = "create-storage-volumes";

        String taskId = String.format("%s+%s+%s", DRIVER_NAME, taskType, UUID.randomUUID().toString());
        DriverTask task = new DriverSimulatorTask(taskId);
        task.setStatus(DriverTask.TaskStatus.READY);
        task.setMessage("Get storage volumes: page " + token);

        _log.info("StorageDriver: get storage volumes information for storage system {}, token  {} - end",
                storageSystem.getNativeId(), token);
        // set next value
        if (token.intValue() < NUMBER_OF_VOLUME_PAGES-1) { // each page has different consistency group
            token.setValue(token.intValue() + 1);
            //    token.setValue(0); // last page
        } else {
            token.setValue(0); // last page
        }
        return task;
    }

    private void generateExportDataForVolume(String hostName, String storageSystemId, String volumeId, int volumeIndex, List<StoragePort> ports, int page) {
        Map<String, List<HostExportInfo>> volumeToExportInfoMap = arrayToVolumeToVolumeExportInfoMap.get(storageSystemId);
        if (volumeToExportInfoMap == null) {
            volumeToExportInfoMap = new HashMap<>();
            arrayToVolumeToVolumeExportInfoMap.put(storageSystemId, volumeToExportInfoMap);
        }

        List<HostExportInfo> volumeToHostExportInfoList = volumeToExportInfoMap.get(volumeId);
        if (volumeToHostExportInfoList == null) {
            volumeToHostExportInfoList = new ArrayList<>();
            volumeToExportInfoMap.put(volumeId, volumeToHostExportInfoList);
        }

        // build volume export info
        HostExportInfo exportInfo;
        // get volume info
        List<String> volumeIds = new ArrayList<>();
        volumeIds.add(volumeId);
        // for initiators we only know port network id and host name
        List<String> hostInitiatorIds = hostToInitiatorPortIdMap.get(hostName);
        List<Initiator> initiators = new ArrayList<>();
        for (String initiatorId : hostInitiatorIds) {
            Initiator initiator = new Initiator();
            initiator.setHostName(hostName);
            initiator.setPort(initiatorId);
            initiators.add(initiator);
        }
        // decide about ports.
        if (page % 2 == 1) {
            // for odd pages we generate invalid masks for volumes (to test negative scenarios)
            int portIndex = volumeIndex < ports.size() ? volumeIndex : ports.size() - 1;
            List<StoragePort> exportPorts = Collections.singletonList(ports.get(portIndex));
            exportInfo = new HostExportInfo(hostName, volumeIds, initiators, exportPorts);
        } else {
            exportInfo = new HostExportInfo(hostName, volumeIds, initiators, ports);
        }

        volumeToHostExportInfoList.add(exportInfo);
        _log.info("VolumeToHostExportInfo: " + volumeToHostExportInfoList);
    }

    @Override
    public DriverTask discoverStorageHostComponents(StorageSystem storageSystem, List<StorageHostComponent> embeddedHostComponents) {
        return null;
    }

    public void setConnInfoToRegistry(String systemNativeId, String ipAddress, int port, String username, String password) {
        Map<String, List<String>> attributes = new HashMap<>();
        List<String> listIP = new ArrayList<>();
        List<String> listPort = new ArrayList<>();
        List<String> listUserName = new ArrayList<>();
        List<String> listPwd = new ArrayList<>();

        listIP.add(ipAddress);
        attributes.put("IP_ADDRESS", listIP);
        listPort.add(Integer.toString(port));
        attributes.put("PORT_NUMBER", listPort);
        listUserName.add(username);
        attributes.put("USER_NAME", listUserName);
        listPwd.add(password);
        attributes.put("PASSWORD", listPwd);
        _log.info(String.format("StorageDriver: setting connection information for %s, attributes: %s ", systemNativeId, attributes));
        this.driverRegistry.setDriverAttributesForKey("StorageDriverSimulator", systemNativeId, attributes);
    }

    @Override
    public List<VolumeSnapshot> getVolumeSnapshots(StorageVolume volume) {
        List<VolumeSnapshot> snapshots = new ArrayList<>();

        for (int i=0; i<NUMBER_OF_SNAPS_FOR_VOLUME; i++) {
            VolumeSnapshot snapshot = new VolumeSnapshot();
            snapshot.setParentId(volume.getNativeId());
            snapshot.setNativeId(volume.getNativeId() + "snap-" + i);
            snapshot.setDeviceLabel(volume.getNativeId() + "snap-" + i);
            snapshot.setStorageSystemId(volume.getStorageSystemId());
            snapshot.setAccessStatus(StorageObject.AccessStatus.READ_ONLY);
            if (SNAPS_IN_CG) {
                snapshot.setConsistencyGroup(volume.getConsistencyGroup() + "snapSet-" + i);
            }
            snapshot.setAllocatedCapacity(1000L);
            snapshot.setProvisionedCapacity(volume.getProvisionedCapacity());
            snapshot.setWwn(String.format("%s%s", snapshot.getStorageSystemId(), snapshot.getNativeId()));
            snapshots.add(snapshot);

            if (GENERATE_EXPORT_DATA) {
                // generate export data for this snap --- the same export data as for its parent volume
                generateExportDataForVolumeReplica(volume, snapshot);
            }
        }
        return snapshots;
    }

    @Override
    public List<VolumeClone> getVolumeClones(StorageVolume volume) {
        List<VolumeClone> clones = new ArrayList<>();

        for (int i=0; i<NUMBER_OF_CLONES_FOR_VOLUME; i++) {
            VolumeClone clone = new VolumeClone();
            clone.setParentId(volume.getNativeId());
            clone.setNativeId(volume.getNativeId() + "clone-" + i);
            clone.setDeviceLabel(volume.getNativeId() + "clone-" + i);
            clone.setStorageSystemId(volume.getStorageSystemId());
            clone.setStoragePoolId(volume.getStoragePoolId());
            clone.setAccessStatus(StorageObject.AccessStatus.READ_WRITE);
            if (CLONES_IN_CG) {
                clone.setConsistencyGroup(volume.getConsistencyGroup() + "cloneGroup-" + i);
            }
            clone.setAllocatedCapacity(volume.getAllocatedCapacity());
            clone.setProvisionedCapacity(volume.getProvisionedCapacity());
            clone.setThinlyProvisioned(true);
            clone.setWwn(String.format("%s%s", clone.getStorageSystemId(), clone.getNativeId()));
            clone.setReplicationState(VolumeClone.ReplicationState.SYNCHRONIZED);
            clones.add(clone);

            if (GENERATE_EXPORT_DATA) {
                // generate export data for this clone --- the same export data as for its parent volume
                generateExportDataForVolumeReplica(volume, clone);
            }
        }
        return clones;
    }

    @Override
    public List<VolumeMirror> getVolumeMirrors(StorageVolume volume) {
        return null;
    }

    private void generateExportDataForVolumeReplica(StorageVolume volume, StorageBlockObject replica) {
        Map<String, List<HostExportInfo>> volumeToExportInfoMap = arrayToVolumeToVolumeExportInfoMap.get(volume.getStorageSystemId());
        if (volumeToExportInfoMap != null) {
            List<HostExportInfo> volumeExportInfoList = volumeToExportInfoMap.get(volume.getNativeId());
            if (volumeExportInfoList != null && !volumeExportInfoList.isEmpty()) {
                List<HostExportInfo> replicaExportInfoList = new ArrayList<>();
                // build replica export info from info of parent volume
                for (HostExportInfo hostExportInfo : volumeExportInfoList) {
                    List<String> snapIds = new ArrayList<>();
                    snapIds.add(replica.getNativeId());
                    List<Initiator> hostInitiators = hostExportInfo.getInitiators();
                    List<StoragePort> exportPorts = hostExportInfo.getTargets();
                    HostExportInfo exportInfo = new HostExportInfo(hostExportInfo.getHostName(), snapIds, hostInitiators, exportPorts);
                    replicaExportInfoList.add(exportInfo);
                }
                _log.info("Export Info for replica: {} --- {}", replica.getNativeId(), replicaExportInfoList);
                volumeToExportInfoMap.put(replica.getNativeId(), replicaExportInfoList);
            }
        }
    }

    @Override
    public DriverTask createConsistencyGroupMirror(VolumeConsistencyGroup consistencyGroup, List<VolumeMirror> mirrors, List<CapabilityInstance> capabilities) {
        return null;
    }

    @Override
    public DriverTask deleteConsistencyGroupMirror(List<VolumeMirror> mirrors) {
        return null;
    }

    @Override
    public DriverTask restoreVolumeMirror(List<VolumeMirror> mirrors) {
        return null;
    }

    @Override
    public DriverTask discoverStorageProvider(StorageProvider storageProvider, List<StorageSystem> storageSystems) {

        storageProvider.setIsSupportedVersion(true);
        StorageSystem providerSystem = new StorageSystem();
        providerSystem.setSystemType("providersystem");
        providerSystem.setNativeId("providerSystem-1");
        providerSystem.setSerialNumber("1234567-1");
        providerSystem.setFirmwareVersion("1.2.3");
        storageSystems.add(providerSystem);

        providerSystem = new StorageSystem();
        providerSystem.setSystemType("providersystem");
        providerSystem.setNativeId("providerSystem-2");
        providerSystem.setSerialNumber("1234567-2");
        providerSystem.setFirmwareVersion("1.2.3");
        storageSystems.add(providerSystem);

        providerSystem = new StorageSystem();
        providerSystem.setSystemType("providersystem");
        providerSystem.setNativeId("providerSystem-3");
        providerSystem.setSerialNumber("1234567-3");
        providerSystem.setFirmwareVersion("1.2.3");
        storageSystems.add(providerSystem);

        String taskType = "discover-storage-provider";
        String taskId = String.format("%s+%s+%s", DRIVER_NAME, taskType, UUID.randomUUID().toString());
        DriverTask task = new DriverSimulatorTask(taskId);
        task.setStatus(DriverTask.TaskStatus.READY);
        String msg = String.format("Discovered provider: %s, discovered %s storage systems.", storageProvider.getProviderName(),
                storageSystems.size());
        task.setMessage(msg);
        _log.info(msg);

        return task;
    }

    @Override
    public boolean validateStorageProviderConnection(StorageProvider storageProvider) {
        String msg = String.format("Request to validate connection to storage provider with type: %s, host: %s, port: %s ",
                storageProvider.getProviderType(), storageProvider.getProviderHost(), storageProvider.getPortNumber());
        _log.info(msg);
        return true;
}

}
