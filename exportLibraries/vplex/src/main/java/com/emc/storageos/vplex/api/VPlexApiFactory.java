/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplex.api;

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
import com.sun.jersey.client.apache.ApacheHttpClient;
import com.sun.jersey.client.apache.ApacheHttpClientHandler;

/**
 * VPlex API client factory
 */
public class VPlexApiFactory {

    // Default maximum number of outstanding connections.
    private static final int DEFAULT_MAX_CONN = 300;

    // Default maximum number of outstanding connections per host.
    private static final int DEFAULT_MAX_CONN_PER_HOST = 100;

    // Default connection timeout in milliseconds.
    private static final int DEFAULT_CONN_TIMEOUT = 1000 * 30;

    // The default socket time in milliseconds.
    private static final int DEFAULT_SOCKET_TIMEOUT = 1000 * 60 * 60;

    // The default connection mgr timeout.
    private static final int DEFAULT_CONN_MGR_TIMEOUT = 1000 * 60 * 60;

    // The maximum number of outstanding connections.
    private int _maxConn = DEFAULT_MAX_CONN;

    // The maximum number of outstanding connections per host.
    private int _maxConnPerHost = DEFAULT_MAX_CONN_PER_HOST;

    // The connection timeout in milliseconds.
    private int _connTimeoutMs = DEFAULT_CONN_TIMEOUT;

    // The connection timeout in milliseconds.
    private int _socketTimeoutMs = DEFAULT_SOCKET_TIMEOUT;

    // Timeout to retrieve the connection from ConnectionManager.
    private int connManagerTimeout = DEFAULT_CONN_MGR_TIMEOUT;

    // A map of client connections to VPlex Management Stations keyed
    // by the URI of the Management Station.
    private ConcurrentMap<String, VPlexApiClient> _clientMap;

    // The root HTTP client handler.
    private ApacheHttpClientHandler _clientHandler;

    // The singleton VPLEX client factory.
    private static VPlexApiFactory _instance = null;

    // Logger reference.
    private static Logger s_logger = LoggerFactory.getLogger(VPlexApiFactory.class);

    /**
     * Default constructor is private.
     */
    private VPlexApiFactory() {
    }

    /**
     * Public static member to create or get a reference to the
     * singleton client factory.
     * 
     * @return The VPLEX client factory.
     */
    public static synchronized VPlexApiFactory getInstance() {
        if (_instance == null) {
            s_logger.info("Creating VPLEX client factory.");
            _instance = new VPlexApiFactory();
            _instance.init();
        }

        return _instance;
    }

    /**
     * Setter for the maximum number of outstanding connections.
     * 
     * @param maxConn The maximum number of outstanding connections.
     */
    public void setMaxConnections(int maxConn) {
        _maxConn = maxConn;
    }

    /**
     * Setter for the maximum number of outstanding connections per host.
     * 
     * @param maxConnPerHost
     */
    public void setMaxConnectionsPerHost(int maxConnPerHost) {
        _maxConnPerHost = maxConnPerHost;
    }

    /**
     * Setter for the connection timeout.
     * 
     * @param connTimeoutMs The connection timeout in ms.
     */
    public void setConnectionTimeoutMs(int connTimeoutMs) {
        _connTimeoutMs = connTimeoutMs;
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
    private void init() {
        // Create the VPlex API client map.
        _clientMap = new ConcurrentHashMap<String, VPlexApiClient>();

        // Setup the connection parameters.
        HttpConnectionManagerParams params = new HttpConnectionManagerParams();
        params.setMaxTotalConnections(_maxConn);
        params.setDefaultMaxConnectionsPerHost(_maxConnPerHost);
        params.setConnectionTimeout(_connTimeoutMs);
        params.setSoTimeout(_socketTimeoutMs);
        params.setTcpNoDelay(true);

        // Create the HTTP connection manager for managing the set of HTTP
        // connections and set the configuration parameters. Also, make sure
        // idle connections are closed immediately to prevent a buildup of
        // connections in the CLOSE_WAIT state.
        MultiThreadedHttpConnectionManager mgr = new MultiThreadedHttpConnectionManager();
        mgr.setParams(params);
        mgr.closeIdleConnections(0);

        // Create the HTTP client and set the handler for determining if an
        // HttpMethod should be retried after a recoverable exception during
        // execution.
        HttpClient client = new HttpClient(mgr);
        client.getParams().setConnectionManagerTimeout(connManagerTimeout);
        client.getParams().setParameter(HttpMethodParams.RETRY_HANDLER,
                new HttpMethodRetryHandler() {
                    @Override
                    public boolean retryMethod(HttpMethod httpMethod, IOException e, int i) {
                        return false;
                    }
                });

        // Create the client handler.
        _clientHandler = new ApacheHttpClientHandler(client);

        // Register the specific for the HTTPS protocol.
        Protocol.registerProtocol("https", new Protocol("https",
                new NonValidatingSocketFactory(), 443));
    }

    /**
     * Get the VPlex API client for the VPlex Management Station identified
     * by the passed endpoint suing the passed username and password.
     * 
     * @param endpoint VPlex Management Station endpoint URI.
     * @param username The user name to authenticate.
     * @param password The password to authenticate.
     * 
     * @return Reference to a VPlexApiClient.
     */
    public synchronized VPlexApiClient getClient(URI endpoint, String username, String password) {

        // Make the key dependent on user and password in case they
        // change for a client endpoint.
        StringBuilder clientKeyBuilder = new StringBuilder();
        clientKeyBuilder.append(endpoint.toString());
        clientKeyBuilder.append("_");
        clientKeyBuilder.append(username);
        clientKeyBuilder.append("_");
        clientKeyBuilder.append(password);
        String clientKey = clientKeyBuilder.toString();
        VPlexApiClient vplexApiClient = _clientMap.get(clientKey);
        if (vplexApiClient == null) {
            s_logger.info("Creating new VPLEX client for the management server {}", endpoint);
            Client jerseyClient = new ApacheHttpClient(_clientHandler);
            RESTClient restClient = new RESTClient(jerseyClient, username, password);
            vplexApiClient = new VPlexApiClient(endpoint, restClient);
            _clientMap.putIfAbsent(clientKey, vplexApiClient);
        }
        return vplexApiClient;
    }
}
