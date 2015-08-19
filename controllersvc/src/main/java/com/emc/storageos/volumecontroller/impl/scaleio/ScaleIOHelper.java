/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.scaleio;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.scaleio.api.restapi.ScaleIORestClient;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOStoragePool;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOVolume;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ScaleIOHelper {

    public static final long BYTES_IN_GB = 1073741824L;

    private static Logger log = LoggerFactory.getLogger(ScaleIOHelper.class);

    public static void updateVolumeWithAddVolumeInfo(DbClient dbClient, Volume volume, String systemId,
            Long requestedCapacity,
            ScaleIOVolume addVolumeResult) throws IOException {
        volume.setNativeId(addVolumeResult.getId());
        volume.setWWN(generateWWN(systemId, addVolumeResult.getId()));
        volume.setAllocatedCapacity(Long.parseLong(addVolumeResult.getSizeInKb()) * 1024L);
        volume.setProvisionedCapacity(volume.getAllocatedCapacity());
        volume.setCapacity(requestedCapacity * BYTES_IN_GB);
        volume.setDeviceLabel(addVolumeResult.getName());
        volume.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(dbClient, volume));
        volume.setThinlyProvisioned(addVolumeResult.isThinProvisioned());
    }

    public static void updateSnapshotWithSnapshotVolumeResult(DbClient dbClient, BlockObject snapshot, String systemId,
            String nativeId) throws IOException {
        snapshot.setNativeId(nativeId);
        snapshot.setWWN(generateWWN(systemId, nativeId));
        snapshot.setDeviceLabel(snapshot.getLabel());
        if (snapshot instanceof BlockSnapshot) {
            ((BlockSnapshot) snapshot).setIsSyncActive(true);
        }
        if (snapshot instanceof Volume) {
            // This BlockObject is a full copy volume, so we need to set
            // the native GUID for this volume.
            Volume clone = (Volume) snapshot;
            snapshot.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(dbClient, clone));
        }
    }

    public static void updateSnapshotsWithSnapshotMultiVolumeResult(DbClient dbClient,
            List<BlockSnapshot> blockSnapshots, String systemId,
            Map<String, String> snapNameIdMap, String groupId) throws IOException {
        for (BlockSnapshot snapshot : blockSnapshots) {
            String nativeId = snapNameIdMap.get(snapshot.getLabel());
            updateSnapshotWithSnapshotVolumeResult(dbClient, snapshot, systemId, nativeId);
            snapshot.setSnapsetLabel(groupId);
        }
    }

    public static void updateStoragePoolCapacity(DbClient dbClient, ScaleIORestClient scaleIOCLI, BlockSnapshot snapshot) {
        Volume parent = dbClient.queryObject(Volume.class, snapshot.getParent().getURI());
        updateStoragePoolCapacity(dbClient, scaleIOCLI, parent);
    }

    public static void updateStoragePoolCapacity(DbClient dbClient, ScaleIORestClient scaleIOHandle, Volume volume) {
        StorageSystem system = dbClient.queryObject(StorageSystem.class, volume.getStorageController());
        StoragePool pool = dbClient.queryObject(StoragePool.class, volume.getPool());
        updateStoragePoolCapacity(dbClient, scaleIOHandle, pool, system);
    }

    public static void updateStoragePoolCapacity(DbClient dbClient, ScaleIORestClient scaleIOHandle, StoragePool storagePool,
            StorageSystem storage) {
        try {
            log.info(String.format("Old storage pool capacity data for %n  pool %s/%s --- %n  free capacity: %s; subscribed capacity: %s",
                    storage.getId(), storagePool.getId(),
                    storagePool.calculateFreeCapacityWithoutReservations(),
                    storagePool.getSubscribedCapacity()));

            ScaleIOStoragePool storagePoolResult = scaleIOHandle.queryStoragePool(storagePool.getNativeId());
            storagePool.setFreeCapacity(Long.parseLong(storagePoolResult.getCapacityAvailableForVolumeAllocationInKb()));
            storagePool.setTotalCapacity(Long.parseLong(storagePoolResult.getMaxCapacityInKb()));

            log.info(String.format("New storage pool capacity data for pool %n  %s/%s --- %n  free capacity: %s; subscribed capacity: %s",
                    storage.getId(), storagePool.getId(),
                    storagePool.getFreeCapacity(),
                    storagePool.getSubscribedCapacity()));

            dbClient.persistObject(storagePool);
        } catch (Exception e) {
            StringBuilder sb = new StringBuilder();
            sb.append("Failed to update capacity of storage pool after volume provisioning operation.\n");
            sb.append("Storage system: %s, storage pool %s.");
            log.error(String.format(sb.toString(), storage.getId(), storagePool.getId()), e);
        }
    }

    public static String generateWWN(String systemId, String nativeId) {
        return String.format("%s%s", systemId, nativeId);
    }

}
