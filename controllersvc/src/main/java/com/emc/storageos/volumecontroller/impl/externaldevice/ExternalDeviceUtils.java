/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.externaldevice;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.model.VolumeClone;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;

/**
 * Utility class to capture common code and functionality
 */
public class ExternalDeviceUtils {
    
    /**
     * Updates the ViPR volume from the passed, newly created external device clone.
     * 
     * @param volume A reference to the volume representing the controller clone.
     * @param deviceClone A reference to the external device clone.
     * @param dbClient A reference to a database client.
     * 
     * @throws IOException When there is an issue generating the native GUID for the volume.
     */
    public static void updateNewlyCreatedClone(Volume volume, VolumeClone deviceClone, DbClient dbClient) throws IOException {
        volume.setNativeId(deviceClone.getNativeId());
        volume.setWWN(deviceClone.getWwn());
        volume.setDeviceLabel(deviceClone.getDeviceLabel());
        volume.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(dbClient, volume));
        volume.setReplicaState(deviceClone.getReplicationState().name());
        volume.setProvisionedCapacity(deviceClone.getProvisionedCapacity());
        volume.setAllocatedCapacity(deviceClone.getAllocatedCapacity());
        volume.setInactive(false);
        dbClient.updateObject(volume);
    }

    /**
     * Updates the ViPR consistency group volume from the passed, newly created external
     * device group clone.
     * 
     * @param volume A reference to the volume representing the controller clone.
     * @param deviceClone A reference to the external device clone.
     * @param cgURI The URI of the consistency group.
     * @param dbClient A reference to a database client.
     * 
     * @throws IOException When there is an issue generating the native GUID for the volume.
     */
    public static void updateNewlyCreatedGroupClone(Volume volume, VolumeClone deviceClone, URI cgURI, DbClient dbClient) throws IOException {
        volume.setNativeId(deviceClone.getNativeId());
        volume.setWWN(deviceClone.getWwn());
        volume.setDeviceLabel(deviceClone.getDeviceLabel());
        volume.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(dbClient, volume));
        volume.setReplicaState(deviceClone.getReplicationState().name());
        volume.setReplicationGroupInstance(deviceClone.getConsistencyGroup());
        volume.setProvisionedCapacity(deviceClone.getProvisionedCapacity());
        volume.setAllocatedCapacity(deviceClone.getAllocatedCapacity());
        volume.setInactive(false);
        volume.setConsistencyGroup(cgURI);
    }

    /**
     * Updates the ViPR volume from the passed, newly expanded external device volume to reflect
     * the expanded capacity.
     * 
     * @param volume A reference to the controller volume.
     * @param deviceVolume A reference to the external device volume.
     * @param dbClient A reference to a database client.
     */
    public static void updateExpandedVolume(Volume volume, StorageVolume deviceVolume, DbClient dbClient) {
        volume.setCapacity(deviceVolume.getRequestedCapacity());
        volume.setProvisionedCapacity(deviceVolume.getProvisionedCapacity());
        volume.setAllocatedCapacity(deviceVolume.getAllocatedCapacity());
        dbClient.updateObject(volume);
    }
    
    /**
     * Updates the ViPR volume from the passed external device clone after the clone is
     * successfully restored.
     * 
     * @param volume A reference to the Volume representing the controller clone..
     * @param deviceClone A reference to the external device clone.
     * @param dbClient A reference to a database client.
     * @param updateDb true to update the object in the DB, false otherwise.
     */
    public static void updateRestoredClone(Volume volume, VolumeClone deviceClone, DbClient dbClient, boolean updateDb) {
        volume.setReplicaState(deviceClone.getReplicationState().name());
        if (updateDb) {
            dbClient.updateObject(volume);
        }
    }
    
    /**
     * Determines if the passed volume (representing a controller clone) represents the
     * passed external device clone.
     *  
     * @param volume A reference o a controller clone.
     * @param deviceClone A reference to an external device clone.
     * @param dbClient A reference to a database client.
     * 
     * @return true if the controller volume represents the passed external device clone, false otherwise.
     */
    public static boolean isVolumeExternalDeviceClone(Volume volume, VolumeClone deviceClone, DbClient dbClient) {
        // Get the native id of the associated source volume for the controller clone.
        URI assocSourceVolumeURI = volume.getAssociatedSourceVolume();
        Volume assocSourceVolume = dbClient.queryObject(Volume.class, assocSourceVolumeURI);
        String assocSourceVolumeNativeId = assocSourceVolume.getNativeId();
        
        // The passed controller clone represents the passed external device clone if 
        // the native id of the associated source volume for the controller clone is
        // the parent id of the passed device clone.
        return deviceClone.getParentId().equals(assocSourceVolumeNativeId);
    }

    public static void updateStoragePoolCapacityAfterOperationComplete(URI storagePoolURI, URI storageSystemURI,
                                                                 List<URI> volumeURIs,  DbClient dbClient) {
        StoragePool dbPool = dbClient.queryObject(StoragePool.class, storagePoolURI);
        StorageSystem dbSystem = dbClient.queryObject(StorageSystem.class, storageSystemURI);
        ExternalBlockStorageDevice.updateStoragePoolCapacity(dbPool, dbSystem,
                volumeURIs, dbClient);
    }
}
