/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.externaldevice;

import java.io.IOException;
import java.net.URI;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.storagedriver.model.VolumeClone;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;

/**
 * Utility class to capture common code and functionality
 */
public class ExternalDeviceUtils {
    
    /**
     * Updates the ViPR volume from the passed external device clone.
     * 
     * @param volume A reference to the Volume.
     * @param clone A reference to the external device clone.
     * @param dbClient A reference to a database client.
     * 
     * @throws IOException When there is an issue generating the native GUID for the volume.
     */
    public static void updateVolumeFromClone(Volume volume, VolumeClone clone, DbClient dbClient) throws IOException {
        volume.setNativeId(clone.getNativeId());
        volume.setWWN(clone.getWwn());
        volume.setDeviceLabel(clone.getDeviceLabel());
        volume.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(dbClient, volume));
        volume.setReplicaState(clone.getReplicationState().name());
        volume.setProvisionedCapacity(clone.getProvisionedCapacity());
        volume.setAllocatedCapacity(clone.getAllocatedCapacity());
        volume.setInactive(false);
        dbClient.updateObject(volume);
    }
    
    /**
     * Updates the ViPR consistency group volume from the passed external device clone.
     * 
     * @param volume A reference to the Volume.
     * @param clone A reference to the external device clone.
     * @param cgURI The URI of the consistency group.
     * @param dbClient A reference to a database client.
     * 
     * @throws IOException When there is an issue generating the native GUID for the volume.
     */
    public static void updateGroupVolumeFromClone(Volume volume, VolumeClone clone, URI cgURI, DbClient dbClient) throws IOException {
        volume.setNativeId(clone.getNativeId());
        volume.setWWN(clone.getWwn());
        volume.setDeviceLabel(clone.getDeviceLabel());
        volume.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(dbClient, volume));
        volume.setReplicaState(clone.getReplicationState().name());
        volume.setReplicationGroupInstance(clone.getConsistencyGroup());
        volume.setProvisionedCapacity(clone.getProvisionedCapacity());
        volume.setAllocatedCapacity(clone.getAllocatedCapacity());
        volume.setInactive(false);
        volume.setConsistencyGroup(cgURI);
    }    
}
