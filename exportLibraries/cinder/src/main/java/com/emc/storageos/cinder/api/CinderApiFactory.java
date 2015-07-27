/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.cinder.api;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cinder.CinderEndPointInfo;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;

/**
 * Cinder API client factory
 */
public class CinderApiFactory {
    private Logger _log = LoggerFactory.getLogger(CinderApiFactory.class);
    private static final int DEFAULT_MAX_CONN = 300;
    private static final int DEFAULT_MAX_CONN_PER_HOST = 100;
    private static final int DEFAULT_CONN_TIMEOUT = 1000 * 30;
    private static final int DEFAULT_SOCKET_CONN_TIMEOUT = 1000 * 60 * 60;
    
    private int _maxConn = DEFAULT_MAX_CONN;
    private int _maxConnPerHost = DEFAULT_MAX_CONN_PER_HOST;
    private int _connTimeout = DEFAULT_CONN_TIMEOUT;
    private int _socketConnTimeout = DEFAULT_SOCKET_CONN_TIMEOUT;

    private ConcurrentMap<String, CinderApi> _clientMap = null;

    /**
     * Initialize 
     */
    public void init() {
        _log.info(" CinderApi factory initialized");
    	if (_clientMap == null) {
            _clientMap = new ConcurrentHashMap<String, CinderApi>();
            _log.info(" CinderApi factory new map created");
    	}
    }

    /**
     * shutdown 
     */
    protected void shutdown()  {
    }

    /**
     * Return Cinder API client, create if not present
     *
     * @param provider Storage Provider URI
     * @return
     */
    public CinderApi getApi(URI provider, CinderEndPointInfo endPoint) 
    {
    	boolean isNew = false;
        CinderApi cinderApi = _clientMap.get(provider.toString());
        if (cinderApi == null) {
        	isNew = true;
            ClientConfig config = new DefaultClientConfig();
            Client jerseyClient = Client.create(config);
            cinderApi = new CinderApi(endPoint, jerseyClient);
            _clientMap.putIfAbsent(provider.toString(), cinderApi);
        }
        
        if(!isNew)
        {   //Token gets expired, if the instance is not new refresh it.
        	cinderApi.getClient().setAuthTokenHeader(endPoint.getCinderToken());
        }
        
        return cinderApi;
    }

}

