/*
 * Copyright (c) 2017 DELL EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax.restapi;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.emc.storageos.services.restutil.RestClientFactory;
import com.emc.storageos.services.restutil.RestClientItf;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodRetryHandler;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.protocol.Protocol;

import com.emc.storageos.vmax.VMAXConstants;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.client.apache.ApacheHttpClient;
import com.sun.jersey.client.apache.ApacheHttpClientHandler;

public class VMAXApiRestClientFactory extends RestClientFactory {

    // Default maximum number of outstanding connections.
    private static final int DEFAULT_MAX_CONN = 300;

    // Default maximum number of outstanding connections per host.
    private static final int DEFAULT_MAX_CONN_PER_HOST = 100;

    // Default connection timeout in milliseconds.
    private static final int DEFAULT_CONN_TIMEOUT = 1000 * 30;

    // Default connection manager timeout in milliseconds.
    private static final int DEFAULT_CONN_MGR_TIMEOUT = 1000 * 60;

    // The maximum number of outstanding connections.
    private int _maxConn = DEFAULT_MAX_CONN;

    // The maximum number of outstanding connections per host.
    private int _maxConnPerHost = DEFAULT_MAX_CONN_PER_HOST;

    // The connection timeout in milliseconds.
    private int _connTimeoutMs = DEFAULT_CONN_TIMEOUT;

    // Timeout to retrieve the connection from connectionManager.
    private int connManagerTimeout = DEFAULT_CONN_MGR_TIMEOUT;

    // Socket connection timeout in milliseconds.
    private int socketConnectionTimeoutMs = DEFAULT_CONN_TIMEOUT;

    // A map of client connections to Unisphere REST interface 
    private ConcurrentMap<String, VMAXApiClient> _clientMap;

    // The root HTTP client handler.
    private ApacheHttpClientHandler _clientHandler;

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
     * @param socketConnectionTimeoutMs the socket connection timeout ms to set
     */
    public void setSocketConnectionTimeoutMs(int socketConnectionTimeoutMs) {
        this.socketConnectionTimeoutMs = socketConnectionTimeoutMs;
    }

    /**
     * Initialize HTTP client
     */
    public void init() {
        // Create the VPlex API client map.
        _clientMap = new ConcurrentHashMap<String, VMAXApiClient>();

        // Setup the connection parameters.
        HttpConnectionManagerParams params = new HttpConnectionManagerParams();
        params.setMaxTotalConnections(_maxConn);
        params.setDefaultMaxConnectionsPerHost(_maxConnPerHost);
        params.setConnectionTimeout(_connTimeoutMs);
        params.setSoTimeout(socketConnectionTimeoutMs);
        params.setTcpNoDelay(true);

        // Create the HTTP connection manager for managing the set of HTTP
        // connections.
        MultiThreadedHttpConnectionManager mgr = new MultiThreadedHttpConnectionManager();
        mgr.setParams(params);
        mgr.closeIdleConnections(0);  // close idle connections immediately

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
     * Get the HDS API client for the HDS Management Station identified
     * by the passed endpoint using the passed username and password.
     * 
     * @param endpoint HDS Management Station endpoint URI.
     * @param username The user name to authenticate.
     * @param password The password to authenticate.
     * 
     * @return Reference to a VPlexApiClient.
     */
    public VMAXApiClient getClient(URI endpoint, String username, String password) {

        // Make the key dependent on user and password in case they
        // change for a client endpoint.
        StringBuilder clientKeyBuilder = new StringBuilder();
        clientKeyBuilder.append(endpoint.toString());
        clientKeyBuilder.append("_");
        clientKeyBuilder.append(username);
        clientKeyBuilder.append("_");
        clientKeyBuilder.append(password);
        String clientKey = clientKeyBuilder.toString();
        VMAXApiClient hdsApiClient = _clientMap.get(clientKey);
        if (hdsApiClient == null) {
            Client jerseyClient = new ApacheHttpClient(_clientHandler);
            RESTClient restClient = new RESTClient(jerseyClient, username, password);
            hdsApiClient = new VMAXApiClient(endpoint, restClient);
            _clientMap.putIfAbsent(clientKey, hdsApiClient);
        }
        return hdsApiClient;
    }

    public VMAXApiClient getClient(String ipAddress, int port, boolean isSSL, String username, String password) {
        return getClient(URI.create(VMAXConstants.getBaseURI(ipAddress, port, isSSL)), username, password);
    }

    @Override
    protected RestClientItf createNewRestClient(URI endpoint, String username, String password, Client client) {
        // TODO Auto-generated method stub
        return null;
    }
}
