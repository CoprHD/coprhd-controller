package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.block.BulkDeleteParam;
import com.emc.storageos.model.object.BucketBulkRep;
import com.emc.storageos.model.object.BucketDeleteParam;
import com.emc.storageos.model.object.BucketRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;
/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

/**
 * ObjectBuckets resources.
 * <p>
 * Base URL: <tt>/object/buckets</tt>
 */
public class ObjectBuckets extends ProjectResources<BucketRestRep> implements TaskResources<BucketRestRep> {

	public ObjectBuckets(ViPRCoreClient parent, RestClient client) {
        super(parent, client, BucketRestRep.class, PathConstants.OBJECT_BUCKET_URL);
    }

	@Override
	public Tasks<BucketRestRep> getTasks(URI id) {
		return doGetTasks(id);
	}

	@Override
	public Task<BucketRestRep> getTask(URI id, URI taskId) {
		return doGetTask(id, taskId);
	}

	@Override
	protected List<BucketRestRep> getBulkResources(BulkIdParam input) {
		BucketBulkRep response = client.post(BucketBulkRep.class, input, getBulkUrl());
        return defaultList(response.getBuckets());
	}
	
	/**
     * Begins deactivating the given file system by ID.
     * <p>
     * API Call: <tt>POST /object/buckets/{id}/deactivate</tt>
     * 
     * @param id
     *            the ID of the file system to deactivate.
     * @param input
     *            the delete configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<BucketRestRep> deactivate(URI id, BucketDeleteParam input) {
        return postTask(input, getDeactivateUrl(), id);
    }
    

}
