/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller.impl;

import java.net.URI;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Bucket;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.model.object.BucketParam;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.FileStorageDevice;
import com.emc.storageos.volumecontroller.ObjectController;
import com.emc.storageos.volumecontroller.ObjectDeviceInputOutput;
import com.emc.storageos.volumecontroller.ObjectStorageDevice;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;

/**
 * Generic Object Controller Implementation that does all of the database
 * operations and calls methods on the array specific implementations
 */
public class ObjectDeviceController implements ObjectController {

	private DbClient _dbClient;
	private Map<String, ObjectStorageDevice> _devices;
	private static final Logger _log = LoggerFactory.getLogger(ObjectDeviceController.class);
	
    public void setDbClient(DbClient dbc) {
        _dbClient = dbc;
    }

    public void setDevices(Map<String, ObjectStorageDevice> deviceInterfaces) {
        _devices = deviceInterfaces;
    }

    private ObjectStorageDevice getDevice(String deviceType) {
        return _devices.get(deviceType);
    }
    
	@Override
	public void connectStorage(URI storage) throws InternalException {
		// TODO Auto-generated method stub
		_log.info("ObjectDeviceController:connectStorage");

	}

	@Override
	public void disconnectStorage(URI storage) throws InternalException {
		// TODO Auto-generated method stub
		_log.info("ObjectDeviceController:disconnectStorage");

	}

	@Override
	public void discoverStorageSystem(AsyncTask[] tasks)
			throws InternalException {
		// TODO Auto-generated method stub
		_log.info("ObjectDeviceController:discoverStorageSystem");

	}

	@Override
	public void scanStorageProviders(AsyncTask[] tasks)
			throws InternalException {
		// TODO Auto-generated method stub
		_log.info("ObjectDeviceController:scanStorageProviders");

	}

	@Override
	public void startMonitoring(AsyncTask task, Type deviceType)
			throws InternalException {
		// TODO Auto-generated method stub
		_log.info("ObjectDeviceController:startMonitoring");

	}

	@Override
	public void createBucket(URI storage, URI uriPool, URI bkt, String label, String namespace, String retention,
			String hardQuota, String softQuota, String owner, String opId) throws ControllerException {

		_log.info("ObjectDeviceController:createBucket start");
		StorageSystem storageObj = null;
		Bucket bucketObj = _dbClient.queryObject(Bucket.class, bkt);
		BiosCommandResult result = null;
		
		try {
			StoragePool stPool = _dbClient.queryObject(StoragePool.class, uriPool);
			ObjectDeviceInputOutput args = new ObjectDeviceInputOutput();
			storageObj = _dbClient.queryObject(StorageSystem.class, storage);

			args.setName(label);
			args.setNamespace(namespace);
			args.setRepGroup(stPool.getNativeId()); //recommended storage pool
			args.setRetentionPeriod(retention);
			args.setBlkSizeHQ(hardQuota);
			args.setNotSizeSQ(softQuota);
			args.setOwner(owner);

			_log.info("ObjectDeviceController:createBucket URI and Type: " + storage.toString() + "   " +
					storageObj.getSystemType());
			result = getDevice(storageObj.getSystemType()).doCreateBucket(storageObj, args);
			if (!result.getCommandPending()) {
				bucketObj.getOpStatus().updateTaskStatus(opId, result.toOperation());
			}

			_dbClient.persistObject(bucketObj);
			_log.info("ObjectDeviceController:createBucket end");
		} catch (Exception e) {
			bucketObj.getOpStatus().updateTaskStatus(opId, result.toOperation());
			_log.error("ObjectDeviceController:createBucket Unable to create Bucket storage");
		}
	}
	
    @Override
    public void deleteBucket(URI storage, URI bucket, String task) throws ControllerException {
        _log.info("ObjectDeviceController:deleteBucket");
        Bucket bucketObj = _dbClient.queryObject(Bucket.class, bucket);
        StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
        BiosCommandResult result = getDevice(storageObj.getSystemType()).doDeleteBucket(storageObj, bucketObj);

        if (result.getCommandPending()) {
            return;
        }
        bucketObj.getOpStatus().updateTaskStatus(task, result.toOperation());
    }

    @Override
    public void updateBucket(URI storage, URI bucket, Long softQuota, Long hardQuota, Integer retention,
            String task) throws ControllerException {
        _log.info("ObjectDeviceController:updateBucket");

        Bucket bucketObj = _dbClient.queryObject(Bucket.class, bucket);
        StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
        BiosCommandResult result = getDevice(storageObj.getSystemType()).doUpdateBucket(storageObj, bucketObj, softQuota, hardQuota,
                retention);

        if (result.getCommandPending()) {
            return;
        }
        bucketObj.getOpStatus().updateTaskStatus(task, result.toOperation());
    }
	
}
