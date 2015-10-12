package com.emc.storageos.storagedriver.impl;

import com.emc.storageos.storagedriver.*;
import com.emc.storageos.storagedriver.model.*;
import org.apache.commons.lang.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


public class NewStorageDriver extends AbstractStorageDriver {

    private static final Logger _log = LoggerFactory.getLogger(NewStorageDriver.class);

    public NewStorageDriver(Registry driverRegistry, LockManager lockManager) {
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
    public List<CapabilityDefinition> getCapabilityDefinitions() {
        return null;
    }

    @Override
    public DriverTask discoverStorageSystem(List<StorageSystem> storageSystems) {
        return null;
    }

    @Override
    public DriverTask discoverStoragePools(StorageSystem storageSystem, List<StoragePool> storagePools) {
        return null;
    }

    @Override
    public DriverTask getStoragePorts(StorageSystem storageSystem, List<StoragePort> storagePorts) {
        return null;
    }

    @Override
    public DriverTask getStorageVolumes(StorageSystem storageSystem, List<StoragePort> storageVolumes, MutableInt token) {
        return null;
    }



    // BlockStorageDriver implementation

    @Override
    public DriverTask createVolumes(List<StorageVolume> volumes, List<CapabilityInstance> capabilities) {
        return null;
    }

    @Override
    public DriverTask expandVolume(StorageVolume volume, long newCapacity, List<CapabilityInstance> capabilities) {
        return null;
    }

    @Override
    public DriverTask deleteVolumes(List<StorageVolume> volumes, List<CapabilityInstance> capabilities) {
        return null;
    }

    @Override
    public DriverTask createVolumeSnapshot(List<StorageVolume> volumes, List<VolumeSnapshot> snapshots, List<CapabilityInstance> capabilities) {
        return null;
    }

    @Override
    public DriverTask restoreFromSnapshot(StorageVolume volume, VolumeSnapshot snapshot, List<CapabilityInstance> capabilities) {
        return null;
    }

    @Override
    public DriverTask deleteVolumeSnapshot(List<VolumeSnapshot> snapshots, List<CapabilityInstance> capabilities) {
        return null;
    }

    @Override
    public DriverTask createVolumeClone(List<StorageVolume> volume, List<VolumeClone> clones, List<CapabilityInstance> capabilities) {
        return null;
    }

    @Override
    public DriverTask detachVolumeClone(List<VolumeClone> clones, List<CapabilityInstance> capabilities) {
        return null;
    }

    @Override
    public DriverTask restoreFromClone(StorageVolume volume, VolumeClone clone, List<CapabilityInstance> capabilities) {
        return null;
    }

    @Override
    public DriverTask deleteVolumeClone(List<VolumeClone> clones, List<CapabilityInstance> capabilities) {
        return null;
    }

    @Override
    public DriverTask createVolumeMirror(List<StorageVolume> volumes, List<VolumeMirror> mirrors, List<CapabilityInstance> capabilities) {
        return null;
    }

    @Override
    public DriverTask deleteVolumeMirror(List<VolumeMirror> mirrors, List<CapabilityInstance> capabilities) {
        return null;
    }

    @Override
    public DriverTask splitVolumeMirror(List<VolumeMirror> mirrors, List<CapabilityInstance> capabilities) {
        return null;
    }

    @Override
    public DriverTask resumeVolumeMirror(List<VolumeMirror> mirrors, List<CapabilityInstance> capabilities) {
        return null;
    }

    @Override
    public DriverTask restoreVolumeMirror(StorageVolume volume, VolumeMirror mirror, List<CapabilityInstance> capabilities) {
        return null;
    }

    @Override
    public List<ITL> getITL(StorageSystem storageSystem, List<Initiator> initiators) {
        return null;
    }

    @Override
    public DriverTask exportVolumesToInitiators(List<Initiator> initiators, List<StorageVolume> volumes, List<StoragePort> recommendedPorts, List<CapabilityInstance> capabilities) {
        return null;
    }

    @Override
    public DriverTask unexportVolumesFromInitiators(List<Initiator> initiators, List<StorageVolume> volumes, List<CapabilityInstance> capabilities) {
        return null;
    }

    public static void main (String[] args) {
        StorageDriver driver = new NewStorageDriver(RegistryImpl.getInstance(), LockManagerImpl.getInstance(null));
        StorageVolume volume = driver.getStorageObject("123", "234", StorageVolume.class);
        System.out.println("This is allocated capacity: " + volume.getAllocatedCapacity());
    }
}
