/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.xtremio.prov.utils;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.xtremio.restapi.XtremIOClient;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOSystem;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOVolume;

public class XtremIOProvUtils {

    private static final Logger _log = LoggerFactory.getLogger(XtremIOProvUtils.class);

    public static void updateStoragePoolCapacity(XtremIOClient client, DbClient dbClient, StoragePool storagePool) {
        try {
            StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, storagePool.getStorageDevice());
            _log.info(String.format("Old storage pool capacity data for %n  pool %s/%s --- %n  free capacity: %s; subscribed capacity: %s",
                    storageSystem.getId(), storagePool.getId(),
                    storagePool.calculateFreeCapacityWithoutReservations(),
                    storagePool.getSubscribedCapacity()));
            List<XtremIOSystem> systems = client.getXtremIOSystemInfo();
            for (XtremIOSystem system : systems) {
                if (system.getSerialNumber().equalsIgnoreCase(storageSystem.getSerialNumber())) {
                    storagePool.setFreeCapacity(system.getTotalCapacity() - system.getUsedCapacity());
                    dbClient.persistObject(storagePool);
                    break;
                }
            }
            _log.info(String.format("New storage pool capacity data for %n  pool %s/%s --- %n  free capacity: %s; subscribed capacity: %s",
                    storageSystem.getId(), storagePool.getId(),
                    storagePool.calculateFreeCapacityWithoutReservations(),
                    storagePool.getSubscribedCapacity()));
        } catch (Exception e) {
            _log.warn("Problem when updating pool capacity for pool {}", storagePool.getNativeGuid());
        }
    }

    public static XtremIOVolume isVolumeAvailableInArray(XtremIOClient client, String label) {
        XtremIOVolume volume = null;
        try {
            volume = client.getVolumeDetails(label);
        } catch (Exception e) {
            _log.info("Volume {} already deleted.", label);
        }
        return volume;
    }

    public static XtremIOVolume isSnapAvailableInArray(XtremIOClient client, String label) {
        XtremIOVolume volume = null;
        try {
            volume = client.getSnapShotDetails(label);
        } catch (Exception e) {
            _log.info("Snapshot {} not available in Array.", label);
        }
        return volume;
    }
}
