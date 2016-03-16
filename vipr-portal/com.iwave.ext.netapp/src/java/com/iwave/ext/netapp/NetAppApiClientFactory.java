/*
 * Copyright (c) 2014-2015 EMC Corporation
 * All Rights Reserved
 */

package com.iwave.ext.netapp;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodRetryHandler;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.protocol.Protocol;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.client.apache.ApacheHttpClient;
import com.sun.jersey.client.apache.ApacheHttpClientHandler;

/*
 *  VNXe (KittyHawk) API client factory
 */
public class NetAppApiClientFactory {
    // client map

    private static final int DEFAULT_MAX_CONN = 300;
    private static final int DEFAULT_MAX_CONN_PER_HOST = 100;
    private static final int DEFAULT_CONN_TIMEOUT = 1000 * 30;
    private static final int DEFAULT_CONN_MGR_TIMEOUT = 1000 * 60;
    private static final int DEFAULT_SOCKET_CONN_TIMEOUT = 1000 * 60 * 60;

    private final int _maxConn = DEFAULT_MAX_CONN;
    private final int _maxConnPerHost = DEFAULT_MAX_CONN_PER_HOST;
    private final int _connTimeout = DEFAULT_CONN_TIMEOUT;
    private final int _socketConnTimeout = DEFAULT_SOCKET_CONN_TIMEOUT;
    private final int connManagerTimeout = DEFAULT_CONN_MGR_TIMEOUT;

    private ConcurrentMap<String, NetAppClient> clientMap;
    private ApacheHttpClientHandler _clientHandler;
    private MultiThreadedHttpConnectionManager _connectionManager;

    public void init() {
        clientMap = new ConcurrentHashMap<String, NetAppClient>();

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

    /*
     * get NetAppClient based on the netapp system details!!
     */
    public NetAppClient getClient(String host, int port, String user, String password) {
        StringBuilder builder = new StringBuilder();
        builder.append(host);
        builder.append("_");
        builder.append("port");
        builder.append("_");
        builder.append("user");
        String key = builder.toString();
        NetAppClient apiClient = null;
        if (clientMap.get(key) != null) {
            apiClient = clientMap.get(key);
        } else {

            Client jerseyClient = new ApacheHttpClient(_clientHandler);
            RestClient restClient = new RestClient(jerseyClient);
            apiClient = new NetAppClient(restClient);
            clientMap.putIfAbsent(key, apiClient);
        }
        return apiClient;
    }

}