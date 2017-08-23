/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang.mutable.MutableBoolean;
import org.apache.commons.lang.mutable.MutableInt;

import com.emc.storageos.driver.univmax.helper.DriverDataUtil;
import com.emc.storageos.driver.univmax.sdkapi.ExportManager;
import com.emc.storageos.driver.univmax.sdkapi.VolumeManager;
import com.emc.storageos.driver.univmax.sdkapi.discover.DiscoverStorageProvider;
import com.emc.storageos.driver.univmax.sdkapi.discover.DiscoverStorageSystem;
import com.emc.storageos.driver.univmax.sdkapi.discover.UnManagedVolumeManager;
import com.emc.storageos.storagedriver.DefaultStorageDriver;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.HostExportInfo;
import com.emc.storageos.storagedriver.model.Initiator;
import com.emc.storageos.storagedriver.model.StorageObject;
import com.emc.storageos.storagedriver.model.StoragePort;
import com.emc.storageos.storagedriver.model.StorageProvider;
import com.emc.storageos.storagedriver.model.StorageSystem;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.model.VolumeClone;
import com.emc.storageos.storagedriver.model.VolumeSnapshot;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;

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

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.storagedriver.DefaultStorageDriver#exportVolumesToInitiators(java.util.List, java.util.List, java.util.Map,
     * java.util.List, java.util.List, com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities,
     * org.apache.commons.lang.mutable.MutableBoolean, java.util.List)
     */
    @Override
    public DriverTask exportVolumesToInitiators(List<Initiator> initiators, List<StorageVolume> volumes,
            Map<String, String> volumeToHLUMap, List<StoragePort> recommendedPorts, List<StoragePort> availablePorts,
            StorageCapabilities capabilities, MutableBoolean usedRecommendedPorts, List<StoragePort> selectedPorts) {
        return new ExportManager(this.driverRegistry, this.lockManager).exportVolumesToInitiators(initiators, volumes, volumeToHLUMap,
                recommendedPorts, availablePorts, capabilities, usedRecommendedPorts, selectedPorts);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.storagedriver.DefaultStorageDriver#unexportVolumesFromInitiators(java.util.List, java.util.List)
     */
    @Override
    public DriverTask unexportVolumesFromInitiators(List<Initiator> initiators, List<StorageVolume> volumes) {
        return new ExportManager(this.driverRegistry, this.lockManager).unexportVolumesFromInitiators(initiators, volumes);
    }

}
