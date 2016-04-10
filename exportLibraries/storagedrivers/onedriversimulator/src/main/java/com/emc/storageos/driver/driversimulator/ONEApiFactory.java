package com.emc.storageos.driver.driversimulator;


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
 * ONE API client factory
 */
public class ONEApiFactory {
    private Logger _log = LoggerFactory.getLogger(ONEApiFactory.class);
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
    private ConcurrentMap<String, ONEApi> _clientMap;
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
     * Initialize
     */
    public void init() {
        _log.info(" ONEApiFactory:init ONEApi factory initialization");
        _clientMap = new ConcurrentHashMap<String, ONEApi>();

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
     * Create ONE API client
     *
     * @param endpoint ONE endpoint
     * @return
     */
    public ONEApi getRESTClient(URI endpoint) {
        ONEApi oneApi = _clientMap.get(endpoint.toString() + ":" + ":");
        if (oneApi == null) {
            Client jerseyClient = new ApacheHttpClient(_clientHandler);
            RESTClient restClient = new RESTClient(jerseyClient);
            oneApi = new ONEApi(endpoint, restClient);
            _clientMap.putIfAbsent(endpoint.toString() + ":" + ":", oneApi);
        }
        return oneApi;
    }

    /**
     * Create ONE API client
     *
     * @param endpoint ONE endpoint
     * @return
     */
    public ONEApi getRESTClient(URI endpoint, String username, String password) {
        ONEApi oneApi = _clientMap.get(endpoint.toString() + ":" + username + ":" + password);
        if (oneApi == null) {
            Client jerseyClient = new ApacheHttpClient(_clientHandler);
            jerseyClient.addFilter(new HTTPBasicAuthFilter(username, password));
            RESTClient restClient = new RESTClient(jerseyClient);
            oneApi = new ONEApi(endpoint, restClient);
            _clientMap.putIfAbsent(endpoint.toString() + ":" + username + ":" + password, oneApi);
        }
        return oneApi;
    }

    
    public static void main(String[] args) {
         System.out.println("starting one main");
         URI uri = URI.create(String.format("https://10.247.143.100:8080/api/v1/credentials"));
         ONEApiFactory factory = new ONEApiFactory();
         factory.init();
         ONEApi oneApi = factory.getRESTClient(uri, "superme", "superme");
         
         String authToken = oneApi.getAuthToken();
         System.out.println(authToken);
    }
    
    /*
     * public static void main(String[] args) {
     * System.out.println("starting one main");
     * URI uri = URI.create(String.format("https://10.*.*.*:4443/login"));
     * ONEApiFactory factory = new ONEApiFactory();
     * factory.init();
     * ONEApi oneApi = factory.getRESTClient(uri, "root", "***");
     * 
     * String authToken = oneApi.getAuthToken();
     * System.out.println(authToken);
     * 
     * if (oneApi.isSystemAdmin())
     * System.out.println("Sys admin");
     * else
     * System.out.println("NOT Sys admin");
     * 
     * //UserSecretKeysGetCommandResult res = oneApi.getUserSecretKeys("prov_user");
     * //System.out.println(res);
     * UserSecretKeysAddCommandResult res2 = oneApi.addUserSecretKey("prov_user", "R6JUtI6hK2rDxY2fKuaQ51OL2tfyoHjPp8xL2y3T");
     * System.out.println(res2);
     * int dummy = 2;
     * 
     * //oneApi.getStoragePools();
     * //oneApi.getStoragePort("");
     * 
     * //oneApi.getNamespaces();
     * //ONENamespaceRepGroup ns = oneApi.getNamespaceDetails("psns");
     * //dummy = ns.getReplicationGroups().size();
     * 
     * //createBucket(String name, String namespace, String repGroup,
     * //String retentionPeriod, String blkSizeHQ, String notSizeSQ) throws ONEException {
     * //oneApi.createBucket("m1", "s3", "urn:storageos:ReplicationGroupInfo:b3bf2d47-d732-457c-bb9b-d260eb53a76a:global",
     * //"4", "99", "55", "testlogin");
     * //oneApi.deleteBucket("esc_myproj_bucket1");
     * }
     */

}
