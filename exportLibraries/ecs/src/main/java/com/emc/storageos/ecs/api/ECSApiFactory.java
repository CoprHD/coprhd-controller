/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.ecs.api;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.httpclient.protocol.Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.client.apache.ApacheHttpClient;
import com.sun.jersey.client.apache.ApacheHttpClientHandler;

/**
 * ECS API client factory
 */
public class ECSApiFactory {
    private Logger _log = LoggerFactory.getLogger(ECSApiFactory.class);
    
    private ApacheHttpClientHandler _clientHandler;
    
    private ConcurrentMap<String, ECSApi> _clientMap = new ConcurrentHashMap<String, ECSApi>();

    /**
     * Initialize
     */
    public void init() {
        _log.info(" ECSApiFactory:init ECSApi factory initialization");
        Protocol.registerProtocol("https", new Protocol("https", new NonValidatingSocketFactory(), 4443));
    }

    /**
     * Create ECS API client
     *
     * @param endpoint ECS endpoint
     * @return
     */
    public ECSApi getRESTClient(URI endpoint) {
        ECSApi ecsApi = _clientMap.get(endpoint.toString() + ":" + ":");
        if (ecsApi == null) {
            Client jerseyClient = new ApacheHttpClient(_clientHandler);
            RESTClient restClient = new RESTClient(jerseyClient);
            ecsApi = new ECSApi(endpoint, restClient);
            _clientMap.putIfAbsent(endpoint.toString() + ":" + ":", ecsApi);
        }
        return ecsApi;
    }

    /**
     * Create ECS API client
     *
     * @param endpoint ECS endpoint
     * @return
     */
    public ECSApi getRESTClient(URI endpoint, String username, String password) {
        ECSApi ecsApi = _clientMap.get(endpoint.toString() + ":" + username + ":" + password);
        if (ecsApi == null) {
            Client jerseyClient = new ApacheHttpClient(_clientHandler);
            jerseyClient.addFilter(new HTTPBasicAuthFilter(username, password));
            RESTClient restClient = new RESTClient(jerseyClient);
            ecsApi = new ECSApi(endpoint, restClient);
            _clientMap.putIfAbsent(endpoint.toString() + ":" + username + ":" + password, ecsApi);
        }
        return ecsApi;
    }

    public void setClientHandler(ApacheHttpClientHandler clientHandler) {
        this._clientHandler = clientHandler;
    }
    
    /*
     * public static void main(String[] args) {
     * System.out.println("starting ecs main");
     * URI uri = URI.create(String.format("https://xxxxxx:4443/login"));
     * ECSApiFactory factory = new ECSApiFactory();
     * factory.init();
     * ECSApi ecsApi = factory.getRESTClient(uri, "root", "****");
     * 
     * String authToken = ecsApi.getAuthToken();
     * System.out.println(authToken);
     * 
     * if (ecsApi.isSystemAdmin())
     * System.out.println("Sys admin");
     * else
     * System.out.println("NOT Sys admin");
     * 
     * //ecsApi.getStoragePools();
     * //ecsApi.getStoragePort("10.32.4.98");
     * 
     * //createBucket(String name, String namespace, String repGroup,
     * //String retentionPeriod, String blkSizeHQ, String notSizeSQ) throws ECSException {
     * ecsApi.createBucket("m1", "s3", "urn:storageos:ReplicationGroupInfo:b3bf2d47-d732-457c-bb9b-d260eb53a76a:global",
     * "4", "99", "55", "testlogin");
     *  ecsApi.deleteBucket("esc_myproj_bucket1");
     * }
     */

}