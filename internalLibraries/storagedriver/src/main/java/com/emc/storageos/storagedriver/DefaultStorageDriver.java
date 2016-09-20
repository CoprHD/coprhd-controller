/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver;

import com.emc.storageos.storagedriver.model.Initiator;
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
import com.emc.storageos.storagedriver.storagecapabilities.CapabilityInstance;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;
import org.apache.commons.lang.mutable.MutableBoolean;
import org.apache.commons.lang.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Default, not-supported, implementation of SDK driver methods.
 * Can be use as a base class for SDK storage drivers.
 */
public class DefaultStorageDriver extends AbstractStorageDriver implements BlockStorageDriver {

    private static final Logger _log = LoggerFactory.getLogger(DefaultStorageDriver.class);

    @Override
    public DriverTask createVolumes(List<StorageVolume> volumes, StorageCapabilities capabilities) {
        String taskType = "create-storage-volumes";
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, taskType, UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);

        String msg = String.format("%s: %s --- operation is not supported.", driverName, "createVolumes");
        _log.warn(msg);
        task.setMessage(msg);
        return task;
    }


    @Override
    public DriverTask getStorageVolumes(StorageSystem storageSystem, List<StorageVolume> storageVolumes, MutableInt token) {
        String taskType = "get-storage-volumes";
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, taskType, UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);

        String msg = String.format("%s: %s --- operation is not supported.", driverName, "getStorageVolumes");
        _log.warn(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public List<VolumeSnapshot> getVolumeSnapshots(StorageVolume volume) {
        String driverName = this.getClass().getSimpleName();
        String msg = String.format("%s: %s --- operation is not supported.", driverName, "getVolumeSnapshots");
        _log.warn(msg);
        throw new UnsupportedOperationException(msg);
    }

    @Override
    public List<VolumeClone> getVolumeClones(StorageVolume volume) {
        String driverName = this.getClass().getSimpleName();
        String msg = String.format("%s: %s --- operation is not supported.", driverName, "getVolumeClones");
        _log.warn(msg);
        throw new UnsupportedOperationException(msg);
    }

    @Override
    public List<VolumeMirror> getVolumeMirrors(StorageVolume volume) {
        String driverName = this.getClass().getSimpleName();
        String msg = String.format("%s: %s --- operation is not supported.", driverName, "getVolumeMirrors");
        _log.warn(msg);
        throw new UnsupportedOperationException(msg);
    }

    @Override
    public DriverTask expandVolume(StorageVolume volume, long newCapacity) {
        String taskType = "expandVolume";
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, taskType, UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);

        String msg = String.format("%s: %s --- operation is not supported.", driverName, "expandVolume");
        _log.warn(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask deleteVolume(StorageVolume volume) {
        String taskType = "delete-storage-volume";
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, taskType, UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);

        String msg = String.format("%s: %s --- operation is not supported.", driverName, "deleteVolume");
        _log.warn(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask createVolumeSnapshot(List<VolumeSnapshot> snapshots, StorageCapabilities capabilities) {
        String taskType = "create-volume-snapshot";
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, taskType, UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);

        String msg = String.format("%s: %s --- operation is not supported.", driverName, "createVolumeSnapshot");
        _log.warn(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask restoreSnapshot(List<VolumeSnapshot> snapshots) {
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, "restoreSnapshot", UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);

        String msg = String.format("%s: %s --- operation is not supported.", driverName, "restoreSnapshot");
        _log.warn(msg);
        task.setMessage(msg);
        return task;
    }
    
    @Override
    public DriverTask stopManagement(StorageSystem driverStorageSystem){
    	_log.info("Stopping management for StorageSystem {}", driverStorageSystem.getNativeId());
    	String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, "stopManagement", UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);
        
        String msg = String.format("%s: %s --- operation is not supported.", driverName, "stopManagement");
        _log.warn(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask deleteVolumeSnapshot(VolumeSnapshot snapshot) {
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, "deleteVolumeSnapshot", UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);

        String msg = String.format("%s: %s --- operation is not supported.", driverName, "deleteVolumeSnapshot");
        _log.warn(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask createVolumeClone(List<VolumeClone> clones, StorageCapabilities capabilities) {
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, "createVolumeClone", UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);

        String msg = String.format("%s: %s --- operation is not supported.", driverName, "createVolumeClone");
        _log.warn(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask detachVolumeClone(List<VolumeClone> clones) {
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, "detachVolumeClone", UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);

        String msg = String.format("%s: %s --- operation is not supported.", driverName, "detachVolumeClone");
        _log.warn(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask restoreFromClone(List<VolumeClone> clones) {
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, "restoreFromClone", UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);

        String msg = String.format("%s: %s --- operation is not supported.", driverName, "restoreFromClone");
        _log.warn(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask deleteVolumeClone(VolumeClone clone) {
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, "deleteVolumeClone", UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);

        String msg = String.format("%s: %s --- operation is not supported.", driverName, "deleteVolumeClone");
        _log.warn(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask createVolumeMirror(List<VolumeMirror> mirrors, StorageCapabilities capabilities) {
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, "createVolumeMirror", UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);

        String msg = String.format("%s: %s --- operation is not supported.", driverName, "createVolumeMirror");
        _log.warn(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask createConsistencyGroupMirror(VolumeConsistencyGroup consistencyGroup, List<VolumeMirror> mirrors, List<CapabilityInstance> capabilities) {
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, "createConsistencyGroupMirror", UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);

        String msg = String.format("%s: %s --- operation is not supported.", driverName, "createConsistencyGroupMirror");
        _log.warn(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask deleteVolumeMirror(VolumeMirror mirror) {
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, "deleteVolumeMirror", UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);

        String msg = String.format("%s: %s --- operation is not supported.", driverName, "deleteVolumeMirror");
        _log.warn(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask deleteConsistencyGroupMirror(List<VolumeMirror> mirrors) {
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, "deleteConsistencyGroupMirror", UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);

        String msg = String.format("%s: %s --- operation is not supported.", driverName, "deleteConsistencyGroupMirror");
        _log.warn(msg);
        task.setMessage(msg);
        return task;
    }
    
    @Override
    public DriverTask addVolumesToConsistencyGroup (List<StorageVolume> volumes, StorageCapabilities capabilities){
    	_log.info("addVolumesToConsistencyGroup : unsupported operation.");
    	String driverName = this.getClass().getSimpleName();
        String taskType = "add-volumes-to-consistency-groupd";
        String taskId = String.format("%s+%s+%s", driverName, taskType, UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);
        
        String msg = String.format("addVolumesToConsistencyGroup: unsupported operation");
        _log.info(msg);
        task.setMessage(msg);
        
        return task;
    }
    
    @Override
    public DriverTask removeVolumesFromConsistencyGroup(List<StorageVolume> volumes,  StorageCapabilities capabilities){
    	_log.info("removeVolumesFromConsistencyGroup : unsupported operation.");
        String taskType = "remove-volumes-to-consistency-groupd";
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, taskType, UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);
        
        String msg = String.format("removeVolumesFromConsistencyGroup: unsupported operation");
        _log.info(msg);
        task.setMessage(msg);
        
        return task;
    }

    @Override
    public DriverTask splitVolumeMirror(List<VolumeMirror> mirrors) {
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, "splitVolumeMirror", UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);

        String msg = String.format("%s: %s --- operation is not supported.", driverName, "splitVolumeMirror");
        _log.warn(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask resumeVolumeMirror(List<VolumeMirror> mirrors) {
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, "resumeVolumeMirror", UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);

        String msg = String.format("%s: %s --- operation is not supported.", driverName, "resumeVolumeMirror");
        _log.warn(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask restoreVolumeMirror(List<VolumeMirror> mirrors) {
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, "restoreVolumeMirror", UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);

        String msg = String.format("%s: %s --- operation is not supported.", driverName, "restoreVolumeMirror");
        _log.warn(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public Map<String, HostExportInfo> getVolumeExportInfoForHosts(StorageVolume volume) {
        String driverName = this.getClass().getSimpleName();
        String msg = String.format("%s: %s --- operation is not supported.", driverName, "getVolumeExportInfoForHosts");
        _log.warn(msg);
        throw new UnsupportedOperationException(msg);
    }

    @Override
    public Map<String, HostExportInfo> getSnapshotExportInfoForHosts(VolumeSnapshot snapshot) {
        String driverName = this.getClass().getSimpleName();
        String msg = String.format("%s: %s --- operation is not supported.", driverName, "getSnapshotExportInfoForHosts");
        _log.warn(msg);
        throw new UnsupportedOperationException(msg);
    }

    @Override
    public Map<String, HostExportInfo> getCloneExportInfoForHosts(VolumeClone clone) {
        String driverName = this.getClass().getSimpleName();
        String msg = String.format("%s: %s --- operation is not supported.", driverName, "getCloneExportInfoForHosts");
        _log.warn(msg);
        throw new UnsupportedOperationException(msg);
    }

    @Override
    public Map<String, HostExportInfo> getMirrorExportInfoForHosts(VolumeMirror mirror) {
        String driverName = this.getClass().getSimpleName();
        String msg = String.format("%s: %s --- operation is not supported.", driverName, "getMirrorExportInfoForHosts");
        _log.warn(msg);
        throw new UnsupportedOperationException(msg);
    }

    @Override
    public DriverTask exportVolumesToInitiators(List<Initiator> initiators, List<StorageVolume> volumes, Map<String, String> volumeToHLUMap, List<StoragePort> recommendedPorts, List<StoragePort> availablePorts, StorageCapabilities capabilities, MutableBoolean usedRecommendedPorts, List<StoragePort> selectedPorts) {
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, "exportVolumesToInitiators", UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);

        String msg = String.format("%s: %s --- operation is not supported.", driverName, "exportVolumesToInitiators");
        _log.warn(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask unexportVolumesFromInitiators(List<Initiator> initiators, List<StorageVolume> volumes) {
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, "unexportVolumesFromInitiators", UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);

        String msg = String.format("%s: %s --- operation is not supported.", driverName, "unexportVolumesFromInitiators");
        _log.warn(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask createConsistencyGroup(VolumeConsistencyGroup consistencyGroup) {
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, "createConsistencyGroup", UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);

        String msg = String.format("%s: %s --- operation is not supported.", driverName, "createConsistencyGroup");
        _log.warn(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask deleteConsistencyGroup(VolumeConsistencyGroup consistencyGroup) {
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, "deleteConsistencyGroup", UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);

        String msg = String.format("%s: %s --- operation is not supported.", driverName, "deleteConsistencyGroup");
        _log.warn(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask createConsistencyGroupSnapshot(VolumeConsistencyGroup consistencyGroup, List<VolumeSnapshot> snapshots, List<CapabilityInstance> capabilities) {
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, "createConsistencyGroupSnapshot", UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);

        String msg = String.format("%s: %s --- operation is not supported.", driverName, "createConsistencyGroupSnapshot");
        _log.warn(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask deleteConsistencyGroupSnapshot(List<VolumeSnapshot> snapshots) {
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, "deleteConsistencyGroupSnapshot", UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);

        String msg = String.format("%s: %s --- operation is not supported.", driverName, "deleteConsistencyGroupSnapshot");
        _log.warn(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask createConsistencyGroupClone(VolumeConsistencyGroup consistencyGroup, List<VolumeClone> clones, List<CapabilityInstance> capabilities) {
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, "createConsistencyGroupClone", UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);

        String msg = String.format("%s: %s --- operation is not supported.", driverName, "createConsistencyGroupClone");
        _log.warn(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask discoverStorageSystem(StorageSystem storageSystem) {
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, "discoverStorageSystem", UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);

        String msg = String.format("%s: %s --- operation is not supported.", driverName, "discoverStorageSystem");
        _log.warn(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask discoverStoragePools(StorageSystem storageSystem, List<StoragePool> storagePools) {
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, "discoverStoragePools", UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);

        String msg = String.format("%s: %s --- operation is not supported.", driverName, "discoverStoragePools");
        _log.warn(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask discoverStoragePorts(StorageSystem storageSystem, List<StoragePort> storagePorts) {
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, "discoverStoragePorts", UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);

        String msg = String.format("%s: %s --- operation is not supported.", driverName, "discoverStoragePorts");
        _log.warn(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask discoverStorageHostComponents(StorageSystem storageSystem, List<StorageHostComponent> embeddedStorageHostComponents) {
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, "discoverStorageHostComponents", UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);

        String msg = String.format("%s: %s --- operation is not supported.", driverName, "discoverStorageHostComponents");
        _log.warn(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public DriverTask discoverStorageProvider(StorageProvider storageProvider, List<StorageSystem> storageSystems) {
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, "discover-storage-provider", UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);

        String msg = String.format("%s: %s --- operation is not supported.", driverName, "discoverStorageProvider");
        _log.warn(msg);
        task.setMessage(msg);
        return task;
    }

    @Override
    public RegistrationData getRegistrationData() {
        String driverName = this.getClass().getSimpleName();
        String msg = String.format("%s: %s --- operation is not supported.", driverName, "getRegistrationData");
        _log.warn(msg);
        throw new UnsupportedOperationException(msg);
    }

    @Override
    public DriverTask getTask(String taskId) {
        String driverName = this.getClass().getSimpleName();
        String msg = String.format("%s: %s --- operation is not supported.", driverName, "getTask");
        _log.warn(msg);
        throw new UnsupportedOperationException(msg);
    }

    @Override
    public <T extends StorageObject> T getStorageObject(String storageSystemId, String objectId, Class<T> type) {
        String driverName = this.getClass().getSimpleName();
        String msg = String.format("%s: %s --- operation is not supported.", driverName, "getStorageObject");
        _log.warn(msg);
        throw new UnsupportedOperationException(msg);
    }

    @Override
    public boolean validateStorageProviderConnection(StorageProvider storageProvider) {
        String driverName = this.getClass().getSimpleName();
        String msg = String.format("%s: %s --- operation is not supported.", driverName, "validateStorageProviderConnection");
        _log.warn(msg);
        throw new UnsupportedOperationException(msg);
    }
}
