/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.ecs.api;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodRetryHandler;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.client.apache.ApacheHttpClient;
import com.sun.jersey.client.apache.ApacheHttpClientHandler;

/**
 * ECS API client factory
 */
public class ECSApiFactory {
    private Logger _log = LoggerFactory.getLogger(ECSApiFactory.class);
    private static final int DEFAULT_MAX_CONN = 300;
    private static final int DEFAULT_MAX_CONN_PER_HOST = 100;
    private static final int DEFAULT_CONN_TIMEOUT = 1000 * 30;
    private static final int DEFAULT_SOCKET_CONN_TIMEOUT = 1000 * 60 * 60;

    private int _maxConn = DEFAULT_MAX_CONN;
    private int _maxConnPerHost = DEFAULT_MAX_CONN_PER_HOST;
    private int _connTimeout = DEFAULT_CONN_TIMEOUT;
    private int _socketConnTimeout = DEFAULT_SOCKET_CONN_TIMEOUT;

    private ApacheHttpClientHandler _clientHandler;
    private ConcurrentMap<String, ECSApi> _clientMap;
    private MultiThreadedHttpConnectionManager _connectionManager;

    /**
     * Maximum number of outstanding connections
     *
     * @param maxConn
     */
    public void setMaxConnections(int maxConn) {
        _maxConn = maxConn;
    }

    /**
     * Maximum number of outstanding connections per host
     *
     * @param maxConnPerHost
     */
    public void setMaxConnectionsPerHost(int maxConnPerHost) {
        _maxConnPerHost = maxConnPerHost;
    }

    /**
     * Connection timeout
     *
     * @param connectionTimeoutMs
     */
    public void setConnectionTimeoutMs(int connectionTimeoutMs) {
        _connTimeout = connectionTimeoutMs;
    }

    /**
     * Socket connection timeout
     *
     * @param connectionTimeoutMs
     */
    public void setSocketConnectionTimeoutMs(int connectionTimeoutMs) {
        _socketConnTimeout = connectionTimeoutMs;
    }

    /**
     * Initialize
     */
    public void init() {
        _log.info(" ECSApiFactory:init ECSApi factory initialization");
        _clientMap = new ConcurrentHashMap<String, ECSApi>();

        HttpConnectionManagerParams params = new HttpConnectionManagerParams();
        params.setDefaultMaxConnectionsPerHost(_maxConnPerHost);
        params.setMaxTotalConnections(_maxConn);
        params.setTcpNoDelay(true);
        params.setConnectionTimeout(_connTimeout);
        params.setSoTimeout(_socketConnTimeout);

        _connectionManager = new MultiThreadedHttpConnectionManager();
        _connectionManager.setParams(params);
        _connectionManager.closeIdleConnections(0);  // close idle connections immediately

        HttpClient client = new HttpClient(_connectionManager);
        client.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new HttpMethodRetryHandler() {
            @Override
            public boolean retryMethod(HttpMethod httpMethod, IOException e, int i) {
                return false;
            }
        });
        _clientHandler = new ApacheHttpClientHandler(client);

        Protocol.registerProtocol("https", new Protocol("https", new NonValidatingSocketFactory(), 4443));
    }

    /**
     * shutdown http connection manager.
     */
    protected void shutdown() {
        _connectionManager.shutdown();
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



    public static void main(String[] args) {          
    System.out.println("starting ecs main");
    URI uri = URI.create(String.format("https://10.247.142.65:4443/login"));
    ECSApiFactory factory = new ECSApiFactory();
    factory.init();
    ECSApi ecsApi = factory.getRESTClient(uri, "root", "ChangeMe");
    
    String authToken = ecsApi.getAuthToken();
    System.out.println(authToken);
    
    if (ecsApi.isSystemAdmin())
        System.out.println("Sys admin");
    else
        System.out.println("NOT Sys admin");
    
    UserSecretKeysGetCommandResult res = ecsApi.getUserSecretKeys("prov_user");
    System.out.println(res);
//    UserSecretKeysAddCommandResult res2 = ecsApi.addUserSecretKeys("prov_user", "R6JUtI6hK2rDxY2fKuaQ51OL2tfyoHjPp8xL2y3T");
//    System.out.println(res2);
    int dummy = 2;
    
    //ecsApi.getStoragePools();
    //ecsApi.getStoragePort("");
    
    //ecsApi.getNamespaces();
    //ECSNamespaceRepGroup ns = ecsApi.getNamespaceDetails("psns");
    //dummy = ns.getReplicationGroups().size();
    
    //createBucket(String name, String namespace, String repGroup,
    //String retentionPeriod, String blkSizeHQ, String notSizeSQ) throws ECSException {
    //ecsApi.createBucket("m1", "s3", "urn:storageos:ReplicationGroupInfo:b3bf2d47-d732-457c-bb9b-d260eb53a76a:global",
    //"4", "99", "55", "testlogin");
    //ecsApi.deleteBucket("esc_myproj_bucket1");
    }

}