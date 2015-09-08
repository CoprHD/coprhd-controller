package com.emc.storageos.volumecontroller.impl.ecs;

import java.io.IOException;
import java.net.URI;
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
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.ecs.api.ECSApi;
import com.emc.storageos.ecs.api.ECSApiFactory;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.ObjectStorageDevice;
import com.emc.storageos.volumecontroller.impl.BiosCommandResult;
import com.sun.jersey.client.apache.ApacheHttpClientHandler;

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
		public BiosCommandResult doCreateBucket(StorageSystem storageObj,
				String name) throws ControllerException {
			// TODO Auto-generated method stub
			_log.info("ECSObjectStorageDevice:doCreateBucket");
			/*
			try {
	            //_log.info("IsilonFileStorageDevice doCreateFS {} with name {} - start", args.getFsId(), args.getFsName());
				URI deviceURI = new URI("https", null, storageObj.getIpAddress(), storageObj.getPortNumber(), "/", null, null);
		        ECSApi ecsApi = ecsApiFactory.getRESTClient(deviceURI, storageObj.getUsername(), storageObj.getPassword());
		        
		        ecsApi.createBucket(name, namespace, repGroup);

			}*/
			
			return null;
		}
}
