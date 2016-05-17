/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.connection;

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
import org.apache.log4j.BasicConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.hp3par.command.CPGCommandResult;
import com.emc.storageos.hp3par.command.PortStatisticsCommandResult;
import com.emc.storageos.hp3par.command.SystemCommandResult;
import com.emc.storageos.hp3par.impl.HP3PARApi;
import com.emc.storageos.hp3par.impl.HP3PARException;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.client.apache.ApacheHttpClient;
import com.sun.jersey.client.apache.ApacheHttpClientHandler;

/*
 * HP 3PAR API client factory
 */
public class HP3PARApiFactory {
    private Logger _log = LoggerFactory.getLogger(HP3PARApiFactory.class);
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
    private ConcurrentMap<String, HP3PARApi> _clientMap;
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
        _clientMap = new ConcurrentHashMap<String, HP3PARApi>();

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

        Protocol.registerProtocol("https", new Protocol("https", new NonValidatingSocketFactory(), 8080));
    }

    /**
     * shutdown http connection manager.
     */
    protected void shutdown() {
        _connectionManager.shutdown();
    }
    
    /**
     * Create HP3PAR API client
     * 
     * @param endpoint
     * @param username
     * @param password
     * @return api client
     * @throws HP3PARException 
     */
    public HP3PARApi getRESTClient(URI endpoint, String username, String password) throws HP3PARException {
        try {
            // key=uri+user+pass to make unique, value=HP3PARApi object
            HP3PARApi hp3parApi = _clientMap.get(endpoint.toString() + ":" + username + ":" + password);
            if (hp3parApi == null) {
                Client jerseyClient = new ApacheHttpClient(_clientHandler);
                jerseyClient.addFilter(new HTTPBasicAuthFilter(username, password));
                RESTClient restClient = new RESTClient(jerseyClient);
                hp3parApi = new HP3PARApi(endpoint, restClient);
                _clientMap.putIfAbsent(endpoint.toString() + ":" + username + ":" + password, hp3parApi);
            }
            return hp3parApi;
        } catch (Exception e) {
            throw new HP3PARException(e.toString());
        }
    }
    
    // Sample direct program
    /*
    public static void main(String[] args) {
        System.out.println("starting HP3PAR main");
        try {
        URI uri = URI.create(String.format("https://10.247.143.100:8080/api/v1/credentials"));
        HP3PARApiFactory factory = new HP3PARApiFactory();
        BasicConfigurator.configure();
        factory.init();
        HP3PARApi hp3parApi = factory.getRESTClient(uri, "superme", "superme");
        
        String authToken = hp3parApi.getAuthToken("superme", "superme");
        System.out.println(authToken);
        
        SystemCommandResult sysRes = hp3parApi.getSystemDetails();
        //System.out.println(sysRes.toString());
        //CPGCommandResult cpgRes = hp3parApi.getCPGDetails();
        //System.out.println(cpgRes.toString());
        //hp3parApi.getPortDetails();
        PortStatisticsCommandResult portStatRes = hp3parApi.getPortStatisticsDetail();

        } catch (Exception e) {
            System.out.println(e.toString());
            e.printStackTrace();
        }
   } //end main
   */
}
