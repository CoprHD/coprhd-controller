/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax;

import com.emc.storageos.driver.univmax.helper.DriverDataUtil;
import com.emc.storageos.driver.univmax.sdkapi.VolumeManager;
import com.emc.storageos.driver.univmax.sdkapi.discover.DiscoverStorageProvider;
import com.emc.storageos.driver.univmax.sdkapi.discover.DiscoverStorageSystem;
import com.emc.storageos.driver.univmax.sdkapi.discover.UnManagedVolumeManager;
import com.emc.storageos.storagedriver.DefaultStorageDriver;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.HostExportInfo;
import com.emc.storageos.storagedriver.model.StorageObject;
import com.emc.storageos.storagedriver.model.StorageProvider;
import com.emc.storageos.storagedriver.model.StorageSystem;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.model.VolumeClone;
import com.emc.storageos.storagedriver.model.VolumeSnapshot;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;
import org.apache.commons.lang.mutable.MutableInt;

import java.util.List;
import java.util.Map;

public class UniVmaxStorageDriver extends DefaultStorageDriver {

    private DriverDataUtil driverDataUtil;

    public UniVmaxStorageDriver() {
        driverDataUtil = new DriverDataUtil(this);
    }

    public DriverDataUtil getDriverDataUtil() {
        return driverDataUtil;
    }

    @Override
    public DriverTask discoverStorageProvider(StorageProvider storageProvider, List<StorageSystem> storageSystems) {
        return new DiscoverStorageProvider().discoverStorageProvider(driverDataUtil, storageProvider, storageSystems);
    }

    @Override
    public DriverTask discoverStorageSystem(StorageSystem storageSystem) {
        return new DiscoverStorageSystem().discoverStorageSystem(driverDataUtil, storageSystem);
    }

    @Override
    public DriverTask getStorageVolumes(StorageSystem storageSystem, List<StorageVolume> storageVolumes,
                                        MutableInt token) {
        return new UnManagedVolumeManager().getStorageVolumes(driverDataUtil, storageSystem, storageVolumes, token);
    }

    @Override
    public Map<String, HostExportInfo> getVolumeExportInfoForHosts(StorageVolume volume) {
        return new UnManagedVolumeManager().getVolumeExportInfoForHosts(driverDataUtil, volume);
    }

    @Override
    public List<VolumeSnapshot> getVolumeSnapshots(StorageVolume volume) {
        return new UnManagedVolumeManager().getVolumeSnapshots(driverDataUtil, volume);
    }

    @Override
    public List<VolumeClone> getVolumeClones(StorageVolume volume) {
        return new UnManagedVolumeManager().getVolumeClones(driverDataUtil, volume);
    }

    @Override
    public <T extends StorageObject> T getStorageObject(String storageSystemId, String objectId, Class<T> type) {
        return new UnManagedVolumeManager().getStorageObject(driverDataUtil, storageSystemId, objectId, type);
    }

    @Override
    public Map<String, HostExportInfo> getSnapshotExportInfoForHosts(VolumeSnapshot snapshot) {
        return new UnManagedVolumeManager().getSnapshotExportInfoForHosts(driverDataUtil, snapshot);
    }

    @Override
    public Map<String, HostExportInfo> getCloneExportInfoForHosts(VolumeClone clone) {
        return new UnManagedVolumeManager().getCloneExportInfoForHosts(driverDataUtil, clone);
    }

    @Override
    public DriverTask createVolumes(List<StorageVolume> volumes, StorageCapabilities capabilities) {
        return new VolumeManager().createVolumes(driverDataUtil, volumes, capabilities);
    }
}
