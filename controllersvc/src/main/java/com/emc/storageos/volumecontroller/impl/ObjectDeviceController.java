/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.Bucket;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.ObjectBucketACL;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.model.object.BucketACE;
import com.emc.storageos.model.object.BucketACLUpdateParams;
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
    public void getUserSecretKey(URI storage,  String userId) throws ControllerException {
        StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
        getDevice(storageObj.getSystemType()).doGetUserSecretKey(storageObj, userId);
    }

    @Override
    public void addUserSecretKey(URI storage, String userId, String secretKey) throws InternalException {
        StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
        getDevice(storageObj.getSystemType()).doAddUserSecretKey(storageObj, userId, secretKey);
    }

    @Override
    public String getString(URI storage) {
        // TODO Auto-generated method stub
        _log.info("ObjectDevCtrl start");
        StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
        String s = getDevice(storageObj.getSystemType()).doGetString(storageObj);
        _log.info("ObjectDevCtrl {}", s);
        return s;
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

    @Override
    public void updateBucketACL(URI storage, URI bucket, BucketACLUpdateParams param, String opId) throws InternalException {

        ControllerUtils.setThreadLocalLogData(bucket, opId);
        _log.info("ObjectDeviceController:updateBucketACL Bucket URI : {} ", bucket);

        Bucket bucketObj = _dbClient.queryObject(Bucket.class, bucket);
        StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
        ObjectDeviceInputOutput objectArgs = new ObjectDeviceInputOutput();
        objectArgs.setAllBuckectAcl(param);
        objectArgs.setName(bucketObj.getName());
        objectArgs.setNamespace(bucketObj.getNamespace());
        // Query for existing ACL and setting it.
        objectArgs.setExistingBucketAcl(queryExistingBucketAcl(objectArgs,bucket));

        BiosCommandResult result = getDevice(storageObj.getSystemType()).doUpdateBucketACL(storageObj, bucketObj, objectArgs, param, opId);

        if (result.getCommandPending()) {
            return;
        }
        bucketObj.getOpStatus().updateTaskStatus(opId, result.toOperation());

    }

    @Override
    public void deleteBucketACL(URI storage, URI bucket, String opId) throws InternalException {
        ControllerUtils.setThreadLocalLogData(bucket, opId);
        _log.info("ObjectDeviceController:updateBucketACL Bucket URI : {} ", bucket);
        Bucket bucketObj = _dbClient.queryObject(Bucket.class, bucket);
        StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
        ObjectDeviceInputOutput objectArgs = new ObjectDeviceInputOutput();
        objectArgs.setName(bucketObj.getName());
        objectArgs.setNamespace(bucketObj.getNamespace());
        
        // Query for existing ACL and setting it for deletion.
        objectArgs.setBucketAclToDelete(queryExistingBucketAcl(objectArgs,bucket));
        BiosCommandResult result = getDevice(storageObj.getSystemType()).doDeleteBucketACL(storageObj, bucketObj, objectArgs, opId);
        if (result.getCommandPending()) {
            return;
        }
        bucketObj.getOpStatus().updateTaskStatus(opId, result.toOperation());
        
    }
    
    private List<BucketACE> queryExistingBucketAcl(ObjectDeviceInputOutput args, URI buckeId) {

        _log.info("Querying  ACL of Bucket {}", args.getName());
        List<BucketACE> acl = new ArrayList<BucketACE>();

        try {
            List<ObjectBucketACL> dbBucketAclList = queryDbBucketAcl(args,buckeId);
            Iterator<ObjectBucketACL> dbAclIter = dbBucketAclList.iterator();
            while (dbAclIter.hasNext()) {

                ObjectBucketACL dbBucketAcl = dbAclIter.next();
                BucketACE ace = new BucketACE();
                ace.setDomain(dbBucketAcl.getDomain());
                ace.setBucketName(dbBucketAcl.getBucketName());
                ace.setGroup(dbBucketAcl.getGroup());
                ace.setPermissions(dbBucketAcl.getPermissions());
                ace.setNamespace(dbBucketAcl.getNamespace());
                ace.setUser(dbBucketAcl.getUser());
                ace.setCustomGroup(dbBucketAcl.getCustomGroup());

                acl.add(ace);
            }

        } catch (Exception e) {
            _log.error("Error while querying ACL(s) of a share {}", e);
        }

        return acl;
    }
    
    private List<ObjectBucketACL> queryDbBucketAcl(ObjectDeviceInputOutput args, URI bucketId) {
        List<ObjectBucketACL> acls = new ArrayList<ObjectBucketACL>();
        try {

            ContainmentConstraint containmentConstraint = null;

            _log.info("Querying DB for ACL of Bucket {} ",
                        args.getName());
                containmentConstraint = ContainmentConstraint.Factory.getBucketAclsConstraint(bucketId);

           List<ObjectBucketACL> bucketAclList = CustomQueryUtility.queryActiveResourcesByConstraint(
                    _dbClient, ObjectBucketACL.class, containmentConstraint);

            Iterator<ObjectBucketACL> bucketAclIter = bucketAclList.iterator();
            while (bucketAclIter.hasNext()) {

                ObjectBucketACL bucketAce = bucketAclIter.next();
                if (args.getName().equals(bucketAce.getBucketName())) {
                    acls.add(bucketAce);
                }
            }
        } catch (Exception e) {
            _log.error("Error while querying DB for ACL(s) of a share {}", e);
        }

        return acls;
    }
    
}
