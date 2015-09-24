/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.ecs;

import java.net.URI;
import java.net.URISyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Bucket;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.ecs.api.ECSApi;
import com.emc.storageos.ecs.api.ECSApiFactory;
import com.emc.storageos.ecs.api.ECSException;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.ObjectDeviceInputOutput;
import com.emc.storageos.volumecontroller.ObjectStorageDevice;
import com.emc.storageos.volumecontroller.impl.BiosCommandResult;

/**
 * ECS specific object controller implementation.
 */
public class ECSObjectStorageDevice implements ObjectStorageDevice {
    private Logger _log = LoggerFactory.getLogger(ECSObjectStorageDevice.class);
    private ECSApiFactory ecsApiFactory;
    private DbClient _dbClient;

    /**
     * Set ECS API factory
     * 
     * @param factory
     */
    public void setECSApiFactory(ECSApiFactory factory) {
        _log.info("ECSObjectStorageDevice setECSApiFactory");
        ecsApiFactory = factory;
    }

    public void setDbClient(DbClient dbc) {
        _dbClient = dbc;
    }

    /**
     * Initialize HTTP client
     */
    public void init() {
        _log.info("From ECSObjectStorageDevice:init");

    }

    @Override
    public BiosCommandResult doCreateBucket(StorageSystem storageObj, Bucket bucket, ObjectDeviceInputOutput args, String taskId)
            throws ControllerException {
        ECSApi ecsApi = getAPI(storageObj);
        BiosCommandResult result = null;
        String bktNativeId = null;
        try {
            _log.info("Initiated for Bucket createion. Name : {} Namespace : {}", args.getName(), args.getNamespace());
            bktNativeId = ecsApi.createBucket(args.getName(), args.getNamespace(), args.getDevStoragePool());
            ecsApi.updateBucketRetention(args.getName(), args.getNamespace(), args.getRetentionPeriod());
            ecsApi.updateBucketQuota(args.getName(), args.getNamespace(), args.getNotSizeSQ(), args.getBlkSizeHQ());
            ecsApi.updateBucketOwner(args.getName(), args.getNamespace(), args.getOwner());
            _log.info("Successfully created Bucket. Name : {} Namespace : {}", args.getName(), args.getNamespace());
            bucket.setNativeId(bktNativeId);
            completeTask(bucket.getId(), taskId, "Successfully created Bucket.");
            result = BiosCommandResult.createSuccessfulResult();
        } catch (ECSException e) {
            _log.error("ECSObjectStorageDevice:doCreateBucket failed. Trying to cleanup at source as well.", e);
            bucket.setInactive(true);
            if (null != bktNativeId) {
                try {
                    ecsApi.deleteBucket(bucket.getLabel(), bucket.getNamespace());
                } catch (Exception del) {
                    _log.error("Unable to delete the Bucket at source. Name : {} Storage : {}", bucket.getLabel(),
                            bucket.getStorageDevice());
                }
            }
            completeTask(bucket.getId(), taskId, e);
            result = BiosCommandResult.createErrorResult(e);
        }
        _dbClient.persistObject(bucket);
        return result;
    }

    @Override
    public BiosCommandResult doUpdateBucket(StorageSystem storageObj, Bucket bucket, Long softQuota, Long hardQuota, Integer retention,
            String taskId) {
        // Update Quota
        ECSApi objectAPI = getAPI(storageObj);
        try {
            objectAPI.updateBucketQuota(bucket.getLabel(), bucket.getNamespace(), softQuota, hardQuota);
            bucket.setHardQuota(hardQuota);
            bucket.setSoftQuota(softQuota);
        } catch (ECSException e) {
            _log.error("Quota Update for Bucket : {} failed.", bucket.getLabel(), e);
            completeTask(bucket.getId(), taskId, e);
            return BiosCommandResult.createErrorResult(e);
        }

        // Update Retention
        try {
            objectAPI.updateBucketRetention(bucket.getLabel(), bucket.getNamespace(), retention);
            bucket.setRetention(retention);
        } catch (ECSException e) {
            _log.error("Retention Update for Bucket : {} failed.", bucket.getLabel(), e);
            completeTask(bucket.getId(), taskId, e);
            return BiosCommandResult.createErrorResult(e);
        }

        _dbClient.persistObject(bucket);
        completeTask(bucket.getId(), taskId, "Successfully updated Bucket.");
        return BiosCommandResult.createSuccessfulResult();
    }

    @Override
    public BiosCommandResult doDeleteBucket(StorageSystem storageObj, Bucket bucket, final String taskId) {
        BiosCommandResult result;
        try {
            ECSApi objectAPI = getAPI(storageObj);
            objectAPI.deleteBucket(bucket.getLabel(), bucket.getNamespace());
            bucket.setInactive(true);
            _dbClient.persistObject(bucket);
            result = BiosCommandResult.createSuccessfulResult();
            completeTask(bucket.getId(), taskId, "Bucket deleted successfully!");
        } catch (ECSException e) {
            _log.error("Delete Bucket : {} failed.", bucket.getLabel(), e);
            result = BiosCommandResult.createErrorResult(e);
            completeTask(bucket.getId(), taskId, e);
        }
        return result;
    }

    private ECSApi getAPI(StorageSystem storageObj) throws ControllerException {
        ECSApi objectAPI = null;
        URI deviceURI = null;
        try {
            deviceURI = new URI("https", null, storageObj.getIpAddress(), storageObj.getPortNumber(), "/", null, null);
        } catch (URISyntaxException e) {
            throw ECSException.exceptions.invalidReturnParameters(storageObj.getId());
        }
        if (storageObj.getUsername() != null && !storageObj.getUsername().isEmpty()) {
            objectAPI = ecsApiFactory.getRESTClient(deviceURI, storageObj.getUsername(), storageObj.getPassword());
        } else {
            objectAPI = ecsApiFactory.getRESTClient(deviceURI);
        }
        return objectAPI;
    }

    private void completeTask(final URI bucketID, final String taskID, ECSException error) {
        BucketOperationTaskCompleter completer = new BucketOperationTaskCompleter(Bucket.class, bucketID, taskID);
        completer.error(_dbClient, error);
    }

    private void completeTask(final URI bucketID, final String taskID, final String message) {
        BucketOperationTaskCompleter completer = new BucketOperationTaskCompleter(Bucket.class, bucketID, taskID);
        completer.statusReady(_dbClient, message);
    }
}