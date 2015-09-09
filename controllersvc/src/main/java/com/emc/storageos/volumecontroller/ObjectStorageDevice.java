package com.emc.storageos.volumecontroller;

import com.emc.storageos.db.client.model.Bucket;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.impl.BiosCommandResult;

public interface ObjectStorageDevice {

    BiosCommandResult doCreateBucket(StorageSystem storageObj, ObjectDeviceInputOutput ob)
            throws ControllerException;

    /**
     * Update Bucket information to the Object Storage (Storage System)
     * 
     * @param storageObj Storage system instance
     * @param bucket Bucket instance
     * @param softQuota Soft Quota for a bucket
     * @param hardQuota Hard Quota for a bucket
     * @param retention Retention period on a bucket
     * @return Result of operation
     * @throws ControllerException if Update fails
     */
    BiosCommandResult doUpdateBucket(StorageSystem storageObj, Bucket bucket, Long softQuota, Long hardQuota,
            Integer retention) throws ControllerException;

    /**
     * 
     * @param storageObj Storage system instance
     * @param bucket Bucket instance
     * @return Result of operation
     * @throws ControllerException if Delete fails
     */
    BiosCommandResult doDeleteBucket(StorageSystem storageObj, Bucket bucket) throws ControllerException;

}
