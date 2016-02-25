package com.emc.storageos.driver.driversimulator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.emc.storageos.storagedriver.BlockStorageDriver;
import com.emc.storageos.storagedriver.model.HostComponent;
import org.apache.commons.lang.mutable.MutableBoolean;
import org.apache.commons.lang.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.storagedriver.AbstractStorageDriver;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.LockManager;
import com.emc.storageos.storagedriver.RegistrationData;
import com.emc.storageos.storagedriver.Registry;
import com.emc.storageos.storagedriver.model.ITL;
import com.emc.storageos.storagedriver.model.Initiator;
import com.emc.storageos.storagedriver.model.StorageObject;
import com.emc.storageos.storagedriver.model.StoragePool;
import com.emc.storageos.storagedriver.model.StoragePort;
import com.emc.storageos.storagedriver.model.StorageSystem;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.model.VolumeClone;
import com.emc.storageos.storagedriver.model.VolumeConsistencyGroup;
import com.emc.storageos.storagedriver.model.VolumeMirror;
import com.emc.storageos.storagedriver.model.VolumeSnapshot;
import com.emc.storageos.storagedriver.storagecapabilities.CapabilityInstance;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;


public class StorageDriverSimulator extends AbstractStorageDriver implements BlockStorageDriver {

    private static final Logger _log = LoggerFactory.getLogger(StorageDriverSimulator.class);
    private static final String DRIVER_NAME = "SimulatorDriver";
    private static final String STORAGE_DEVICE_ID = "PureStorage-x123";
    private static Integer portIndex = 0;
    private static Map<String, Integer> systemNameToPortIndexName = new HashMap<>();


//    public StorageDriverSimulator(Registry driverRegistry, LockManager lockManager) {
//        super(driverRegistry, lockManager);
//    }

    //StorageDriver implementation

    @Override
    public List<String> getSystemTypes() {
        return null;
    }

    @Override
    public DriverTask getTask(String taskId) {
        return null;
    }

    @Override
    public <T extends StorageObject> T getStorageObject(String storageSystemId, String objectId, Class<T> type) {
        if (StorageVolume.class.getSimpleName().equals(type.getSimpleName())) {

        }
        StorageVolume obj = new StorageVolume();
        obj.setAllocatedCapacity(200L);
        return (T) obj;
    }
    // DiscoveryDriver implementation

    @Override
    public RegistrationData getRegistrationData() {
        return null;
    }

    @Override
    public DriverTask discoverStorageSystem(List<StorageSystem> storageSystems) {
         StorageSystem storageSystem = storageSystems.get(0);
        _log.info("StorageDriver: discoverStorageSystem information for storage system {}, name {} - start",
                storageSystem.getIpAddress(), storageSystem.getSystemName());
        String taskType = "discover-storage-system";
        String taskId = String.format("%s+%s+%s", DRIVER_NAME, taskType, UUID.randomUUID().toString());
        DriverTask task = new DriverSimulatorTask(taskId);

        try {
            storageSystem.setSerialNumber(storageSystem.getSystemName());
            storageSystem.setNativeId(storageSystem.getSystemName());
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
                pool.setSupportedResourceType(StoragePool.SupportedResourceType.THIN_AND_THICK);

                pool.setSubscribedCapacity(5000000L);
                pool.setFreeCapacity(50000000L);
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

        // get port index
      //  Map<String, List<String>> portIndexes = driverRegistry.getDriverAttributesForKey("simulatordriver","portIndexes");
        // get index
      //  List<String> indexList = portIndexes.get(storageSystem.getNativeId());

        Integer index = systemNameToPortIndexName.get(storageSystem.getNativeId());
        if(index == null) {
            index = ++portIndex;
            systemNameToPortIndexName.put(storageSystem.getNativeId(), index);
        }

        // Create ports with network
        for (int i =0; i <= 2; i++ ) {
            StoragePort port = new StoragePort();
            port.setNativeId("port-1234577-" + i + storageSystem.getNativeId());
            port.setStorageSystemId(storageSystem.getNativeId());
            _log.info("Discovered Port {}, storageSystem {}", port.getNativeId(), port.getStorageSystemId());

            port.setDeviceLabel("er-port-1234577" + i + storageSystem.getNativeId());
            port.setPortName(port.getDeviceLabel());
            port.setNetworkId("er-network77"+ storageSystem.getNativeId());
            //port.setNetworkId("er-networkScaleIO");
            port.setTransportType(StoragePort.TransportType.FC);
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
        String taskType = "expand-storage-volumes";
        String taskId = String.format("%s+%s+%s", DRIVER_NAME, taskType, UUID.randomUUID().toString());
        volume.setRequestedCapacity(newCapacity);
        volume.setProvisionedCapacity(newCapacity);
        volume.setAllocatedCapacity(newCapacity);
        DriverTask task = new DriverSimulatorTask(taskId);
        task.setStatus(DriverTask.TaskStatus.READY);

        _log.info("StorageDriver: expandVolume information for storage system {}, volume nativeId {}," +
                        " new capacity {} - end",
                volume.getStorageSystemId(), volume.toString(), volume.getRequestedCapacity());
        return task;
    }

    @Override
    public DriverTask deleteVolumes(List<StorageVolume> volumes) {

        String taskType = "delete-storage-volumes";
        String taskId = String.format("%s+%s+%s", DRIVER_NAME, taskType, UUID.randomUUID().toString());
        DriverTask task = new DriverSimulatorTask(taskId);
        task.setStatus(DriverTask.TaskStatus.READY);

        _log.info("StorageDriver: deleteVolumes information for storage system {}, volume nativeIds {} - end",
                volumes.get(0).getStorageSystemId(), volumes.toString());
        return task;
    }

    @Override
    public DriverTask createVolumeSnapshot(List<VolumeSnapshot> snapshots, StorageCapabilities capabilities) {
        String snapTimestamp = Long.toString(System.currentTimeMillis());
        for (VolumeSnapshot snapshot : snapshots) {
            snapshot.setNativeId("snap-" + snapshot.getParentId() + UUID.randomUUID().toString());
            snapshot.setTimestamp(snapTimestamp);
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
    public DriverTask restoreSnapshot(StorageVolume volume, VolumeSnapshot snapshot) {
        String taskType = "restore-snapshot";
        String taskId = String.format("%s+%s+%s", DRIVER_NAME, taskType, UUID.randomUUID().toString());
        DriverTask task = new DriverSimulatorTask(taskId);
        task.setStatus(DriverTask.TaskStatus.READY);
        String msg = String.format("StorageDriver: restoreSnapshot for storage system %s, " +
                        "snapshots nativeId %s, group %s - end",
                snapshot.getStorageSystemId(), snapshot.toString(), snapshot.getConsistencyGroup());
        _log.info(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask deleteVolumeSnapshot(List<VolumeSnapshot> snapshots) {
        String taskType = "delete-volume-snapshot";
        String taskId = String.format("%s+%s+%s", DRIVER_NAME, taskType, UUID.randomUUID().toString());
        DriverTask task = new DriverSimulatorTask(taskId);
        task.setStatus(DriverTask.TaskStatus.READY);
        String msg = String.format("StorageDriver: deleteVolumSnapshot for storage system %s, " +
                        "snapshots nativeId %s - end",
                snapshots.get(0).getStorageSystemId(), snapshots.toString());
        _log.info(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask createVolumeClone(List<VolumeClone> clones, StorageCapabilities capabilities) {
        for (VolumeClone clone : clones) {
            clone.setNativeId("clone-" + clone.getParentId() + clone.getDisplayName());
            clone.setWwn(String.format("%s%s", clone.getStorageSystemId(), clone.getNativeId()));
            clone.setReplicationState(VolumeClone.ReplicationState.SYNCHRONIZED);
            clone.setDeviceLabel(clone.getNativeId());
        }
        String taskType = "create-volume-clone";
        String taskId = String.format("%s+%s+%s", DRIVER_NAME, taskType, UUID.randomUUID().toString());
        DriverTask task = new DriverSimulatorTask(taskId);
        task.setStatus(DriverTask.TaskStatus.READY);

        String msg = String.format("StorageDriver: createVolumeClone information for storage system %s, clone nativeIds %s - end",
                clones.get(0).getStorageSystemId(), clones.toString());
        _log.info(msg);
        task.setMessage(msg);
        return task;
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
        String taskType = "restore-volume-clones";
        String taskId = String.format("%s+%s+%s", DRIVER_NAME, taskType, UUID.randomUUID().toString());
        DriverTask task = new DriverSimulatorTask(taskId);
        task.setStatus(DriverTask.TaskStatus.READY);
        String msg = String.format("StorageDriver: restoreFromClone : clones %s ", clones);
        for (VolumeClone clone : clones) {
            clone.setReplicationState(VolumeClone.ReplicationState.RESTORED);
        }
        _log.info(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask deleteVolumeClone(List<VolumeClone> clones) {
        String taskType = "delete-volume-clone";
        String taskId = String.format("%s+%s+%s", DRIVER_NAME, taskType, UUID.randomUUID().toString());
        DriverTask task = new DriverSimulatorTask(taskId);
        task.setStatus(DriverTask.TaskStatus.READY);
        String msg = String.format("StorageDriver: deleteVolumeClone for storage system %s, " +
                        "clones nativeId %s - end",
                clones.get(0).getStorageSystemId(), clones.toString());
        _log.info(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask createVolumeMirror(List<VolumeMirror> mirrors, StorageCapabilities capabilities) {
        return null;
    }

    @Override
    public DriverTask deleteVolumeMirror(List<VolumeMirror> mirrors) {
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
    public DriverTask restoreVolumeMirror(StorageVolume volume, VolumeMirror mirror) {
        return null;
    }

    @Override
    public List<ITL> getITL(StorageSystem storageSystem, List<Initiator> initiators) {
        return null;
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
        String msg = String.format("StorageDriver: exportVolumesToInitiators - end");
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
        String msg = String.format("StorageDriver: unexportVolumesFromInitiators - end");
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

        _log.info("StorageDriver: createConsistencyGroup information for storage system {}, consistencyGroup nativeId {} - end",
                consistencyGroup.getStorageSystemId(), consistencyGroup.getNativeId());
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
            snapshot.setTimestamp(snapTimestamp);
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
                        "consistencyGroup nativeId %s, group snapshots %s - end",
                snapshots.get(0).getStorageSystemId(), snapshots.get(0).getConsistencyGroup(), snapshots.toString());
        _log.info(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask createConsistencyGroupClone(VolumeConsistencyGroup consistencyGroup, List<VolumeClone> clones, List<CapabilityInstance> capabilities) {
        String cloneTimestamp = Long.toString(System.currentTimeMillis());
        for (VolumeClone clone : clones) {
            clone.setNativeId("clone-" + clone.getParentId() + clone.getDisplayName());
            clone.setWwn(String.format("%s%s", clone.getStorageSystemId(), clone.getNativeId()));
            clone.setReplicationState(VolumeClone.ReplicationState.SYNCHRONIZED);
            clone.setDeviceLabel(clone.getNativeId());
            clone.setConsistencyGroup(consistencyGroup.getNativeId()+"_clone-"+cloneTimestamp);
        }
        String taskType = "create-group-clone";
        String taskId = String.format("%s+%s+%s", DRIVER_NAME, taskType, UUID.randomUUID().toString());
        DriverTask task = new DriverSimulatorTask(taskId);
        task.setStatus(DriverTask.TaskStatus.READY);
        task.setMessage("Created clones for consistency group " + clones.get(0).getConsistencyGroup());

        _log.info("StorageDriver: createGroupClone information for storage system {}, clones nativeIds {} - end",
                clones.get(0).getStorageSystemId(), clones.toString());
        return task;
    }

    @Override
    public DriverTask deleteConsistencyGroupClone(List<VolumeClone> clones) {
        return null;
    }

    @Override
    public DriverTask getStorageVolumes(StorageSystem storageSystem, List<StorageVolume> storageVolumes, MutableInt token) {
        return null;
    }

    @Override
    public DriverTask discoverHostComponents(StorageSystem storageSystem, List<HostComponent> embeddedHostComponents) {
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

//
//    public static void main (String[] args) {
//        StorageDriver driver = new NewStorageDriver(RegistryImpl.getInstance(), LockManagerImpl.getInstance(null));
//        StorageVolume volume = driver.getStorageObject("123", "234", StorageVolume.class);
//        System.out.println("This is allocated capacity: " + volume.getAllocatedCapacity());
//    }
}
