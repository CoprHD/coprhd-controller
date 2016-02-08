/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.isilon.restapi;

import java.io.IOException;
import java.net.URI;
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
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.client.apache.ApacheHttpClient;
import com.sun.jersey.client.apache.ApacheHttpClientHandler;

/**
 * Isilon API client factory
 */
public class IsilonApiFactory {
    private Logger _log = LoggerFactory.getLogger(IsilonApiFactory.class);
    private static final int DEFAULT_MAX_CONN = 300;
    private static final int DEFAULT_MAX_CONN_PER_HOST = 100;
    private static final int DEFAULT_CONN_TIMEOUT = 1000 * 30;
    private static final int DEFAULT_CONN_MGR_TIMEOUT = 1000 * 60;
    private static final int DEFAULT_SOCKET_CONN_TIMEOUT = 1000 * 60 * 60;

    private int _maxConn = DEFAULT_MAX_CONN;
    private int _maxConnPerHost = DEFAULT_MAX_CONN_PER_HOST;
    private int _connTimeout = DEFAULT_CONN_TIMEOUT;
    private int _socketConnTimeout = DEFAULT_SOCKET_CONN_TIMEOUT;
    private int connManagerTimeout = DEFAULT_CONN_MGR_TIMEOUT;

    private ApacheHttpClientHandler _clientHandler;
    private ConcurrentMap<String, IsilonApi> _clientMap;
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
     * @param connManagerTimeout the connManagerTimeout to set
     */
    public void setConnManagerTimeout(int connManagerTimeout) {
        this.connManagerTimeout = connManagerTimeout;
    }

    /**
     * Initialize HTTP client
     */
    public void init() {
        _clientMap = new ConcurrentHashMap<String, IsilonApi>();

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
        client.getParams().setConnectionManagerTimeout(connManagerTimeout);
        client.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new HttpMethodRetryHandler() {
            @Override
            public boolean retryMethod(HttpMethod httpMethod, IOException e, int i) {
                return false;
            }
        });
        _clientHandler = new ApacheHttpClientHandler(client);

        Protocol.registerProtocol("https", new Protocol("https", new NonValidatingSocketFactory(), 443));
    }

    /**
     * shutdown http connection manager.
     */
    protected void shutdown() {
        _connectionManager.shutdown();
    }

    /**
     * Create Isilon API client
     * 
     * @param endpoint isilon endpoint
     * @return
     */
    public IsilonApi getRESTClient(URI endpoint) {
        IsilonApi isilonApi = _clientMap.get(endpoint.toString() + ":" + ":");
        if (isilonApi == null) {
            Client jerseyClient = new ApacheHttpClient(_clientHandler);
            RESTClient restClient = new RESTClient(jerseyClient);
            isilonApi = new IsilonApi(endpoint, restClient);
            _clientMap.putIfAbsent(endpoint.toString() + ":" + ":", isilonApi);
        }
        return isilonApi;
    }

    /**
     * Create Isilon API client
     * 
     * @param endpoint isilon endpoint
     * @return
     */
    public IsilonApi getRESTClient(URI endpoint, String username, String password) {
        IsilonApi isilonApi = _clientMap.get(endpoint.toString() + ":" + username + ":" + password);
        if (isilonApi == null) {
            Client jerseyClient = new ApacheHttpClient(_clientHandler);
            jerseyClient.addFilter(new HTTPBasicAuthFilter(username, password));
            RESTClient restClient = new RESTClient(jerseyClient);
            isilonApi = new IsilonApi(endpoint, restClient);
            _clientMap.putIfAbsent(endpoint.toString() + ":" + username + ":" + password, isilonApi);
        }
        return isilonApi;
    }

}
