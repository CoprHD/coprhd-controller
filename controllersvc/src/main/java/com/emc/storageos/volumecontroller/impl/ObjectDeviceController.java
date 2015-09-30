/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
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
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.ObjectController;
import com.emc.storageos.volumecontroller.ObjectDeviceInputOutput;
import com.emc.storageos.volumecontroller.ObjectStorageDevice;

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
    public void createBucket(URI storage, URI uriPool, URI bkt, String label, String namespace, Integer retention,
            Long hardQuota, Long softQuota, String owner, String task) throws ControllerException {

        _log.info("ObjectDeviceController:createBucket Bucket URI : {} ", bkt);
        StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
        Bucket bucketObj = _dbClient.queryObject(Bucket.class, bkt);
        StoragePool stPool = _dbClient.queryObject(StoragePool.class, uriPool);
        ObjectDeviceInputOutput args = new ObjectDeviceInputOutput();
        args.setName(label);
        args.setNamespace(namespace);
        args.setDevStoragePool(stPool.getNativeId()); // recommended storage pool
        args.setRetentionPeriod(retention);
        args.setBlkSizeHQ(hardQuota);
        args.setNotSizeSQ(softQuota);
        args.setOwner(owner);

        _log.info("ObjectDeviceController:createBucket URI and Type: " + storage.toString() + "   " +
                storageObj.getSystemType());
        BiosCommandResult result = getDevice(storageObj.getSystemType()).doCreateBucket(storageObj, bucketObj, args, task);
        if (result.getCommandPending()) {
            return;
        }
        bucketObj.getOpStatus().updateTaskStatus(task, result.toOperation());
    }

    @Override
    public void deleteBucket(URI storage, URI bucket, String task) throws ControllerException {
        _log.info("ObjectDeviceController:deleteBucket Bucket URI : {} ", bucket);
        Bucket bucketObj = _dbClient.queryObject(Bucket.class, bucket);
        StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
        BiosCommandResult result = getDevice(storageObj.getSystemType()).doDeleteBucket(storageObj, bucketObj, task);

        if (result.getCommandPending()) {
            return;
        }
        bucketObj.getOpStatus().updateTaskStatus(task, result.toOperation());
    }

    @Override
    public void updateBucket(URI storage, URI bucket, Long softQuota, Long hardQuota, Integer retention,
            String task) throws ControllerException {
        _log.info("ObjectDeviceController:updateBucket Bucket URI : {} ", bucket);

        Bucket bucketObj = _dbClient.queryObject(Bucket.class, bucket);
        StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
        BiosCommandResult result = getDevice(storageObj.getSystemType()).doUpdateBucket(storageObj, bucketObj, softQuota, hardQuota,
                retention, task);

        if (result.getCommandPending()) {
            return;
        }
        bucketObj.getOpStatus().updateTaskStatus(task, result.toOperation());
    }

}
