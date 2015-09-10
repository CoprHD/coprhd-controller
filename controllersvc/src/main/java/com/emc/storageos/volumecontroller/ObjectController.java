/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller;

import java.net.URI;

import com.emc.storageos.db.client.model.Bucket;
import com.emc.storageos.model.object.BucketParam;
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

    public void createBucket(URI storage, URI Pool, URI bkt, String label, String namespace, String retention,
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
