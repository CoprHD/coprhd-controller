/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller;

import java.net.URI;

import com.emc.storageos.model.object.BucketACL;
import com.emc.storageos.db.client.model.Bucket;
import com.emc.storageos.db.client.model.ObjectUserSecretKey;
import com.emc.storageos.model.object.BucketParam;
import com.emc.storageos.model.object.BucketACLUpdateParams;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;

public interface ObjectController extends StorageController {

    /** 
     * Create a bucket
     * 
     * @param storage		URI of storage controller.
     * @param Pool			URI of ECS storage pool
     * @param bkt			URI of bucket
     * @param label			Name of bucket
     * @param namespace		Namespace with this associated
     * @param retention		retained value
     * @param hardQuota		blocking limit
     * @param softQuota		notification limit
     * @param owner			owner of bucket
     * @param opId			taak id
     * @throws InternalException
     */

    public void createBucket(URI storage, URI Pool, URI bkt, String label, String namespace, Integer retention,
            Long hardQuota, Long softQuota, String owner, String opId) throws InternalException;

    /**
     * Deactivates Bucket
     * 
     * @param bucket Bucket ID
     * @param task Task ID
     * @throws InternalException if error occurs during Bucket delete
     */
    public void deleteBucket(URI storage, URI bucket, String deleteType, String task) throws InternalException;

    /**
     * Updates Bucket instance
     * 
     * @param bucket Bucket ID
     * @param softQuota Soft Quota limit of Bucket
     * @param hardQuota Hard Quota limit of Bucket
     * @param retention Data retention period in days
     * @param task Task ID
     * @throws InternalException if error occurs during Bucket update
     */
    public void updateBucket(URI storage, URI bucket, Long softQuota, Long hardQuota, Integer retention, String task) throws ControllerException;
    
    /**
     * Add/Modify/Delete the existing ACL settings.
     * @param storage
     * @param bucket
     * @param param
     * @param opId
     * @throws InternalException
     */
    public void updateBucketACL(URI storage, URI bucket, BucketACLUpdateParams param, String opId) throws InternalException;

    /**
     * Deletes the entire ACL settings for bucket.
     * @param storage
     * @param bucket
     * @param opId
     * @throws InternalException
     */
    public void deleteBucketACL(URI storage, URI bucket, String opId) throws InternalException;
    
    /**
     * Get the ACl for the bucket from Object storage and persist in coprhd DB.
     * @param storage
     * @param bucketId
     * @param opId
     * @throws InternalException
     */
    public void syncBucketACL(URI storage, URI bucketId, String opId ) throws InternalException;
    
    /**
     * Get all object user secret keys
     * @param storage storage URN
     * @param userId object storage user id
     * @return details
     * @throws InternalException
     */
    public ObjectUserSecretKey getUserSecretKeys(URI storage, String userId)  throws InternalException;

    /**
     * Create an object user secret key
     * @param storage URN
     * @param userId object storage user id
     * @param secretKey key value
     * @return success results
     * @throws InternalException
     */
    public ObjectUserSecretKey addUserSecretKey(URI storage, String userId, String secretKey)  throws InternalException;

}
