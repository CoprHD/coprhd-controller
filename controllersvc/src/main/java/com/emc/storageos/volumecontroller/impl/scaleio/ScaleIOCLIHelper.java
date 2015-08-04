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
import com.emc.storageos.scaleio.api.ScaleIOAddVolumeResult;
import com.emc.storageos.scaleio.api.ScaleIOCLI;
import com.emc.storageos.scaleio.api.ScaleIOQueryStoragePoolResult;
import com.emc.storageos.scaleio.api.ScaleIOSnapshotMultiVolumeResult;
import com.emc.storageos.scaleio.api.ScaleIOSnapshotVolumeResult;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class ScaleIOCLIHelper {

    public static final long BYTES_IN_GB = 1073741824L;

    private static Logger log = LoggerFactory.getLogger(ScaleIOCLIHelper.class);

    public static void updateVolumeWithAddVolumeInfo(DbClient dbClient, Volume volume, String systemId,
            Long requestedCapacity,
            ScaleIOAddVolumeResult addVolumeResult) throws IOException {
        volume.setNativeId(addVolumeResult.getId());
        volume.setWWN(generateWWN(systemId, addVolumeResult.getId()));
        volume.setAllocatedCapacity(Long.parseLong(addVolumeResult.getActualSize()) * BYTES_IN_GB);
        volume.setProvisionedCapacity(volume.getAllocatedCapacity());
        volume.setCapacity(requestedCapacity * BYTES_IN_GB);
        volume.setDeviceLabel(addVolumeResult.getName());
        volume.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(dbClient, volume));
        volume.setThinlyProvisioned(addVolumeResult.isThinlyProvisioned());
    }

    public static void updateSnapshotWithSnapshotVolumeResult(DbClient dbClient, BlockObject snapshot, String systemId,
            ScaleIOSnapshotVolumeResult result) throws IOException {
        snapshot.setNativeId(result.getId());
        snapshot.setWWN(generateWWN(systemId, result.getId()));
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
            ScaleIOSnapshotMultiVolumeResult multiResult) throws IOException {
        for (BlockSnapshot snapshot : blockSnapshots) {
            ScaleIOSnapshotVolumeResult result = multiResult.findResult(snapshot.getLabel());
            updateSnapshotWithSnapshotVolumeResult(dbClient, snapshot, systemId, result);
            snapshot.setSnapsetLabel(multiResult.getConsistencyGroupId());
        }
    }

    public static void updateStoragePoolCapacity(DbClient dbClient, ScaleIOCLI scaleIOCLI, BlockSnapshot snapshot) {
        Volume parent = dbClient.queryObject(Volume.class, snapshot.getParent().getURI());
        updateStoragePoolCapacity(dbClient, scaleIOCLI, parent);
    }

    public static void updateStoragePoolCapacity(DbClient dbClient, ScaleIOCLI scaleIOCLI, Volume volume) {
        StorageSystem system = dbClient.queryObject(StorageSystem.class, volume.getStorageController());
        StoragePool pool = dbClient.queryObject(StoragePool.class, volume.getPool());
        updateStoragePoolCapacity(dbClient, scaleIOCLI, pool, system);
    }

    public static void updateStoragePoolCapacity(DbClient dbClient, ScaleIOCLI scaleIOCLI, StoragePool storagePool,
            StorageSystem storage) {
        try {
            log.info(String.format("Old storage pool capacity data for %n  pool %s/%s --- %n  free capacity: %s; subscribed capacity: %s",
                    storage.getId(), storagePool.getId(),
                    storagePool.calculateFreeCapacityWithoutReservations(),
                    storagePool.getSubscribedCapacity()));

            ScaleIOQueryStoragePoolResult storagePoolResult =
                    scaleIOCLI.queryStoragePool(storage.getSerialNumber(), storagePool.getPoolName());
            String freeCapacityString = storagePoolResult.getAvailableCapacity();
            Long freeCapacityKBytes = ControllerUtils.convertBytesToKBytes(freeCapacityString);
            storagePool.setFreeCapacity(freeCapacityKBytes);
            String totalCapacityString = storagePoolResult.getTotalCapacity();
            Long totalCapacityKBytes = ControllerUtils.convertBytesToKBytes(totalCapacityString);
            storagePool.setTotalCapacity(totalCapacityKBytes);

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
