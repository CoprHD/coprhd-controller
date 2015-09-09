package com.emc.storageos.volumecontroller;

import java.net.URI;

import com.emc.storageos.db.client.model.Bucket;
import com.emc.storageos.model.object.BucketParam;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;

public interface ObjectController extends StorageController {

    // Create a bucket
    // controller.createBucket(recommendation.getSourceDevice(), recommendation.getSourcePool(), bucket.getId(), param, task);
    public void createBucket(URI storage, URI vPool, URI bkt, String label, String namespace, String retention,
            String hardQuota, String softQuota, String owner, String opId) throws InternalException;

    /**
     * Deactivates Bucket
     * 
     * @param bucket Bucket ID
     * @param task Task ID
     * @throws InternalException if error occurs during Bucket delete
     */
    public void deleteBucket(URI storage, URI bucket, String task) throws InternalException;

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
}
