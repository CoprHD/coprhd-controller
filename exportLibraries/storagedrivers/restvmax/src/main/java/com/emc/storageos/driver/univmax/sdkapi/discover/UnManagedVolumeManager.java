/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.sdkapi.discover;

import com.emc.storageos.driver.univmax.helper.DriverDataUtil;
import com.emc.storageos.driver.univmax.helper.DriverUtil;
import com.emc.storageos.driver.univmax.rest.EndPoint;
import com.emc.storageos.driver.univmax.rest.RestClient;
import com.emc.storageos.driver.univmax.rest.type.common.IteratorType;
import com.emc.storageos.driver.univmax.rest.type.common.VolumeIdType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning84.VolumeType;
import com.emc.storageos.storagedriver.DefaultDriverTask;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.HostExportInfo;
import com.emc.storageos.storagedriver.model.StorageObject;
import com.emc.storageos.storagedriver.model.StorageSystem;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.model.VolumeClone;
import com.emc.storageos.storagedriver.model.VolumeSnapshot;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class UnManagedVolumeManager {

    private static Logger log = LoggerFactory.getLogger(UnManagedVolumeManager.class);
    private RestClient client;

    // todo: in progress
    public DriverTask getStorageVolumes(DriverDataUtil driverDataUtil, StorageSystem storageSystem,
                                        List<StorageVolume> storageVolumes, MutableInt token) {

        String taskType = "get-storage-volumes";
        String driverName = driverDataUtil.getDriverName();
        String taskId = String.format("%s+%s+%s", driverName, taskType, UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);
        String msg = "Get storage volumes: ";

        try {
            client = driverDataUtil.getRestClientByStorageSystemId(storageSystem.getNativeId());

            // get volume list
            IteratorType<VolumeIdType> it = client.get(
                    new TypeToken<IteratorType<VolumeIdType>>() {}.getType(),
                    String.format(EndPoint.SLOPROVISIONING84_SYMMETRIX_ID_VOLUME,
                            storageSystem.getNativeId()));

            VolumeIdType[] vidlist = it.getResultList().getResult();

            for (VolumeIdType vid : vidlist) {

                // get volume information
                VolumeType vol = client.get(
                        VolumeType.class,
                        String.format(EndPoint.SLOPROVISIONING84_SYMMETRIX_ID_VOLUME_ID,
                                storageSystem.getNativeId(),
                                vid.getVolumeId()));

                StorageVolume sv = new StorageVolume();
                sv.setNativeId(vol.getVolumeId());
                // todo
                sv.setStoragePoolId(null);
                sv.setDeviceLabel(null);
                sv.setAccessStatus(null);
                sv.setProvisionedCapacity(null);
                sv.setAllocatedCapacity(null);
                sv.setThinlyProvisioned(true);
                sv.setConsistencyGroup(null);

                storageVolumes.add(sv);
            }

            token.setValue(it.getResultList().getTo());

            msg += "success.";
            log.info(msg);
            task.setMessage(msg);
            task.setStatus(DriverTask.TaskStatus.READY);
        } catch (Exception e) {
            msg += "fail.\n" + DriverUtil.getStackTrace(e);
            log.error(msg);
            task.setMessage(msg);
        }

        return task;
    }

    public Map<String, HostExportInfo> getVolumeExportInfoForHosts(DriverDataUtil driverDataUtil,
                                                                   StorageVolume volume) {
        throw new UnsupportedOperationException("TODO");
    }

    public List<VolumeSnapshot> getVolumeSnapshots(DriverDataUtil driverDataUtil,
                                                   StorageVolume volume) {
        throw new UnsupportedOperationException("TODO");
    }

    public List<VolumeClone> getVolumeClones(DriverDataUtil driverDataUtil,
                                             StorageVolume volume) {
        throw new UnsupportedOperationException("TODO");
    }

    public <T extends StorageObject> T getStorageObject(
            DriverDataUtil driverDataUtil, String storageSystemId, String objectId, Class<T> type) {
        throw new UnsupportedOperationException("TODO");
    }

    public Map<String, HostExportInfo> getSnapshotExportInfoForHosts(DriverDataUtil driverDataUtil,
                                                                     VolumeSnapshot snapshot) {
        throw new UnsupportedOperationException("TODO");
    }

    public Map<String, HostExportInfo> getCloneExportInfoForHosts(DriverDataUtil driverDataUtil, VolumeClone clone) {
        throw new UnsupportedOperationException("TODO");
    }
}
