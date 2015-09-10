/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.ecs;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodRetryHandler;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.protocol.Protocol;
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
import com.sun.jersey.client.apache.ApacheHttpClientHandler;

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
		public BiosCommandResult doCreateBucket(StorageSystem storageObj, ObjectDeviceInputOutput args) 
				throws ControllerException {

			_log.info("ECSObjectStorageDevice:doCreateBucket start");

			try {
				ECSApi ecsApi = getAPI(storageObj);
				ecsApi.createBucket(args.getName(), args.getNamespace(), args.getRepGroup(), 
						args.getRetentionPeriod(), args.getBlkSizeHQ(), args.getNotSizeSQ(), args.getOwner());
				_log.info("ECSObjectStorageDevice:doCreateBucket end");
				return BiosCommandResult.createSuccessfulResult();
			} catch (ECSException e) {
				_log.error("ECSObjectStorageDevice:doCreateBucket failed. ECSException", e);
				return BiosCommandResult.createErrorResult(e);
			}
		}
		
    @Override
    public BiosCommandResult doUpdateBucket(StorageSystem storageObj, Bucket bucket, Long softQuota, Long hardQuota, Integer retention) {
        BiosCommandResult result = BiosCommandResult.createSuccessfulResult();
        boolean persistBucket = false;
        // Update Quota
        try {
            ECSApi objectAPI = getAPI(storageObj);
            objectAPI.updateBucketQuota(bucket.getLabel(), bucket.getNamespace(), softQuota, hardQuota);
            bucket.setHardQuota(hardQuota);
            bucket.setSoftQuota(softQuota);
            persistBucket = true;
        } catch (ECSException e) {
            _log.error("Quota Update for Bucket : {} failed.", bucket.getLabel(), e);
            result = BiosCommandResult.createErrorResult(e);
        }

        // Update Retention
        try {
            ECSApi objectAPI = getAPI(storageObj);
            objectAPI.updateBucketRetention(bucket.getLabel(), bucket.getNamespace(), retention);
            bucket.setRetention(retention);
            persistBucket = true;
        } catch (ECSException e) {
            _log.error("Retention Update for Bucket : {} failed.", bucket.getLabel(), e);
            result = BiosCommandResult.createErrorResult(e);
        }

        if (persistBucket) {
            _dbClient.persistObject(bucket);
        }
        return result;
    }

    @Override
    public BiosCommandResult doDeleteBucket(StorageSystem storageObj, Bucket bucket) {
        BiosCommandResult result;
        try {
            ECSApi objectAPI = getAPI(storageObj);
            objectAPI.deleteBucket(bucket.getLabel());
            bucket.setInactive(true);
            _dbClient.persistObject(bucket);
            result = BiosCommandResult.createSuccessfulResult();
        } catch (ECSException e) {
            _log.error("Delete Bucket : {} failed.", bucket.getLabel(), e);
            result = BiosCommandResult.createErrorResult(e);
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
}