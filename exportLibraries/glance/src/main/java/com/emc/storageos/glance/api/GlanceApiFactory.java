/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.glance.api;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.glance.GlanceEndPointInfo;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;

/**
 * Glance API client factory
 */
public class GlanceApiFactory {
    private Logger _log = LoggerFactory.getLogger(GlanceApiFactory.class);
    private static final int DEFAULT_MAX_CONN = 300;
    private static final int DEFAULT_MAX_CONN_PER_HOST = 100;
    private static final int DEFAULT_CONN_TIMEOUT = 1000 * 30;
    private static final int DEFAULT_SOCKET_CONN_TIMEOUT = 1000 * 60 * 60;
    
    private int _maxConn = DEFAULT_MAX_CONN;
    private int _maxConnPerHost = DEFAULT_MAX_CONN_PER_HOST;
    private int _connTimeout = DEFAULT_CONN_TIMEOUT;
    private int _socketConnTimeout = DEFAULT_SOCKET_CONN_TIMEOUT;

    private ConcurrentMap<String, GlanceApi> _clientMap = null;

    /**
     * Initialize 
     */
    public void init() {
        _log.info(" GlanceApi factory initialized");
    	if (_clientMap == null) {
            _clientMap = new ConcurrentHashMap<String, GlanceApi>();
            _log.info(" GlanceApi factory new map created");
    	}
    }

    /**
     * shutdown 
     */
    protected void shutdown()  {
    }

    /**
     * Return Glance API client, create if not present
     *
     * @param provider Storage Provider URI
     * @return
     */
    public GlanceApi getApi(URI provider, GlanceEndPointInfo endPoint) 
    {
    	boolean isNew = false;
        GlanceApi glanceApi = _clientMap.get(provider.toString());
        if (glanceApi == null) {
        	isNew = true;
            ClientConfig config = new DefaultClientConfig();
            Client jerseyClient = Client.create(config);
            glanceApi = new GlanceApi(endPoint, jerseyClient);
            _clientMap.putIfAbsent(provider.toString(), glanceApi);
        }
        
        if(!isNew)
        {   //Token gets expired, if the instance is not new refresh it.
        	glanceApi.getClient().setAuthTokenHeader(endPoint.getGlanceToken());
        }
        
        return glanceApi;
    }

}

