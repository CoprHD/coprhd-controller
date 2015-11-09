package com.emc.storageos.driver.driversimulator;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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


public class StorageDriverSimulator extends AbstractStorageDriver {

    private static final Logger _log = LoggerFactory.getLogger(StorageDriverSimulator.class);
    private static final String DRIVER_NAME = "SimulatorDriver";
    private static final String STORAGE_DEVICE_ID = "PureStorage-x123";

    public StorageDriverSimulator(Registry driverRegistry, LockManager lockManager) {
        super(driverRegistry, lockManager);
    }

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
        _log.info("StorageDriver: discoverStorageSystem information for storage system {} - start",
                storageSystem.getIpAddress());

        storageSystem.setSerialNumber("123456789");
        storageSystem.setNativeId(STORAGE_DEVICE_ID);
        storageSystem.setFirmwareVersion("2.4-3.12");
        storageSystem.setIsSupportedVersion(true);

        String taskType = "discover-storage-system";
        String taskId = String.format("%s+%s+%s", DRIVER_NAME, taskType, UUID.randomUUID().toString());
        DriverTask task = new DriverSimulatorTask(taskId);
        task.setStatus(DriverTask.TaskStatus.READY);
        _log.info("StorageDriver: discoverStorageSystem information for storage system {}, nativeId {} - end",
                storageSystem.getIpAddress(), storageSystem.getNativeId());
        return task;

    }

    @Override
    public DriverTask discoverStoragePools(StorageSystem storageSystem, List<StoragePool> storagePools) {

        _log.info("Discovery of storage pools for storage system {} .", storageSystem.getNativeId());
        for (int i =0; i <= 2; i++ ) {
            StoragePool pool = new StoragePool();
            pool.setNativeId("pool-12345-" + i);
            pool.setStorageSystemId(storageSystem.getNativeId());
            _log.info("Discovered Pool {}, storageSystem {}", pool.getNativeId(), pool.getStorageSystemId());

            pool.setDeviceLabel("er-pool-12345" + i);
            pool.setPoolName(pool.getDeviceLabel());
            Set<StoragePool.Protocols> protocols = new HashSet<>();
            protocols.add(StoragePool.Protocols.FC);
            protocols.add(StoragePool.Protocols.iSCSI);
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

        String taskType = "discover-storage-pools";
        String taskId = String.format("%s+%s+%s", DRIVER_NAME, taskType, UUID.randomUUID().toString());
        DriverTask task = new DriverSimulatorTask(taskId);
        task.setStatus(DriverTask.TaskStatus.READY);
        _log.info("StorageDriver: discoverStoragePools information for storage system {}, nativeId {} - end",
                storageSystem.getIpAddress(), storageSystem.getNativeId());
        return task;
    }

    @Override
    public DriverTask getStoragePorts(StorageSystem storageSystem, List<StoragePort> storagePorts) {
        _log.info("Discovery of storage ports for storage system {} .", storageSystem.getNativeId());

        // Create ports
        for (int i =0; i <= 2; i++ ) {
            StoragePort port = new StoragePort();
            port.setNativeId("pool-12345-" + i);
            port.setStorageSystemId(storageSystem.getNativeId());
            _log.info("Discovered Port {}, storageSystem {}", port.getNativeId(), port.getStorageSystemId());

            port.setDeviceLabel("er-port-12345" + i);
            port.setPortName(port.getDeviceLabel());
            port.setNetworkId("50:FE:FE:FE:FE:FE:FE:0" + i);
            port.setTransportType(StoragePort.TransportType.FC);
            port.setOperationalStatus(StoragePort.OperationalStatus.OK);
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
        return null;
    }

    @Override
    public DriverTask expandVolume(StorageVolume volume, long newCapacity) {
        return null;
    }

    @Override
    public DriverTask deleteVolumes(List<StorageVolume> volumes) {
        return null;
    }

    @Override
    public DriverTask createVolumeSnapshot(List<VolumeSnapshot> snapshots, StorageCapabilities capabilities) {
        return null;
    }

    @Override
    public DriverTask restoreSnapshot(StorageVolume volume, VolumeSnapshot snapshot) {
        return null;
    }

    @Override
    public DriverTask deleteVolumeSnapshot(List<VolumeSnapshot> snapshots) {
        return null;
    }

    @Override
    public DriverTask createVolumeClone(List<VolumeClone> clones, StorageCapabilities capabilities) {
        return null;
    }

    @Override
    public DriverTask detachVolumeClone(List<VolumeClone> clones) {
        return null;
    }

    @Override
    public DriverTask restoreFromClone(StorageVolume volume, VolumeClone clone) {
        return null;
    }

    @Override
    public DriverTask deleteVolumeClone(List<VolumeClone> clones) {
        return null;
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
    public DriverTask exportVolumesToInitiators(List<Initiator> initiators, List<StorageVolume> volumes, List<StoragePort> recommendedPorts, StorageCapabilities capabilities) {
        return null;
    }

    @Override
    public DriverTask unexportVolumesFromInitiators(List<Initiator> initiators, List<StorageVolume> volumes) {
        return null;
    }

    @Override
    public DriverTask createConsistencyGroup(VolumeConsistencyGroup consistencyGroup) {
        return null;
    }

    @Override
    public DriverTask createConsistencyGroupSnapshot(VolumeConsistencyGroup consistencyGroup, List<VolumeSnapshot> snapshots, List<CapabilityInstance> capabilities) {
        return null;
    }

    @Override
    public DriverTask deleteConsistencyGroupSnapshot(List<VolumeSnapshot> snapshots) {
        return null;
    }

    @Override
    public DriverTask createConsistencyGroupClone(VolumeConsistencyGroup consistencyGroup, List<VolumeClone> clones, List<CapabilityInstance> capabilities) {
        return null;
    }

    @Override
    public DriverTask deleteConsistencyGroupClone(List<VolumeClone> clones) {
        return null;
    }

    @Override
    public DriverTask getStorageVolumes(StorageSystem storageSystem, List<StorageVolume> storageVolumes, MutableInt token) {
        return null;
    }
//
//    public static void main (String[] args) {
//        StorageDriver driver = new NewStorageDriver(RegistryImpl.getInstance(), LockManagerImpl.getInstance(null));
//        StorageVolume volume = driver.getStorageObject("123", "234", StorageVolume.class);
//        System.out.println("This is allocated capacity: " + volume.getAllocatedCapacity());
//    }
}
