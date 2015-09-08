package com.emc.storageos.volumecontroller;

import java.net.URI;

import com.emc.storageos.db.client.model.Bucket;
import com.emc.storageos.model.object.BucketParam;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;

public interface ObjectController extends StorageController {

	//Create a bucket
	//controller.createBucket(recommendation.getSourceDevice(), recommendation.getSourcePool(), bucket.getId(), param, task);
	public void createBucket(URI storage, URI vPool, URI bkt, BucketParam param, String opId) throws InternalException;
	
	//Delete bucket
	//public void deleteBucket(URI storage, URI pool, URI uri, boolean forceDelete, String opId) throws InternalException;
}
