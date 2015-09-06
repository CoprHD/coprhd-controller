package com.emc.storageos.volumecontroller;

import java.net.URI;

import com.emc.storageos.svcs.errorhandling.resources.InternalException;

public interface ObjectController extends StorageController {

	//Create a bucket
	//public void createBucket(URI storage, URI pool, URI fs, String suggestedNativeFsId, String opId) throws InternalException;
	public void createBucket(URI storage, String name) throws InternalException;
	
	//Delete bucket
	//public void deleteBucket(URI storage, URI pool, URI uri, boolean forceDelete, String opId) throws InternalException;
}
