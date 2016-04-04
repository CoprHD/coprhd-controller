package com.emc.storageos.driver.par3driver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.client.apache.ApacheHttpClient;
import com.sun.jersey.client.apache.ApacheHttpClientHandler;

public class Par3ApiFactory {

	   private Logger _log = LoggerFactory.getLogger(Par3ApiFactory.class);
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
	    private ConcurrentMap<String, Par3Api> _clientMap;
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
	        _log.info(" Par3ApiFactory:init Par3Api factory initialization");
	        _clientMap = new ConcurrentHashMap<String, Par3Api>();

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

	        //Protocol.registerProtocol("https", new Protocol("https", new NonValidatingSocketFactory(), 4443));
	        Protocol.registerProtocol("http", new Protocol("http", new NonValidatingSocketFactory(), 8008));
	    }

	    /**
	     * shutdown http connection manager.
	     */
	    protected void shutdown() {
	        _connectionManager.shutdown();
	    }

	    /**
	     * Create Par3 API client
	     *
	     * @param endpoint Par3 endpoint
	     * @return
	     */
	    public Par3Api getRESTClient(URI endpoint) {
	        Par3Api par3Api = _clientMap.get(endpoint.toString() + ":" + ":");
	        if (par3Api == null) {
	            Client jerseyClient = new ApacheHttpClient(_clientHandler);
	            RESTClient restClient = new RESTClient(jerseyClient);
	            par3Api = new Par3Api(endpoint, restClient);
	            _clientMap.putIfAbsent(endpoint.toString() + ":" + ":", par3Api);
	        }
	        return par3Api;
	    }

	    /**
	     * Create Par3 API client
	     *
	     * @param endpoint Par3 endpoint
	     * @return
	     */
	    public Par3Api getRESTClient(URI endpoint, String username, String password) {
	        Par3Api par3Api = _clientMap.get(endpoint.toString() + ":" + username + ":" + password);
	        if (par3Api == null) {
	            Client jerseyClient = new ApacheHttpClient(_clientHandler);
	            jerseyClient.addFilter(new HTTPBasicAuthFilter(username, password));
	            RESTClient restClient = new RESTClient(jerseyClient);
	            par3Api = new Par3Api(endpoint, restClient);
	            _clientMap.putIfAbsent(endpoint.toString() + ":" + username + ":" + password, par3Api);
	        }
	        return par3Api;
	    }

	    public static void main(String[] args) {
		      System.out.println("starting par3 main");
		      BasicConfigurator.configure();
		      Par3ApiFactory factory = new Par3ApiFactory();
		      factory.init();
		      URI uri = URI.create(String.format("http://10.247.143.100:8008/api/v1/credentials"));
		      Par3Api par3Api = factory.getRESTClient(uri, "superme", "superme");
		      
		      String authToken = par3Api.getAuthToken();
		      System.out.println(authToken);
	    }
	    
	    /*
	     * public static void main(String[] args) {
	     * System.out.println("starting par3 main");
	     * URI uri = URI.create(String.format("https://10.*.*.*:4443/login"));
	     * Par3ApiFactory factory = new Par3ApiFactory();
	     * factory.init();
	     * Par3Api par3Api = factory.getRESTClient(uri, "root", "***");
	     * 
	     * String authToken = par3Api.getAuthToken();
	     * System.out.println(authToken);
	     * 
	     * if (par3Api.isSystemAdmin())
	     * System.out.println("Sys admin");
	     * else
	     * System.out.println("NOT Sys admin");
	     * 
	     * //UserSecretKeysGetCommandResult res = par3Api.getUserSecretKeys("prov_user");
	     * //System.out.println(res);
	     * UserSecretKeysAddCommandResult res2 = par3Api.addUserSecretKey("prov_user", "R6JUtI6hK2rDxY2fKuaQ51OL2tfyoHjPp8xL2y3T");
	     * System.out.println(res2);
	     * int dummy = 2;
	     * 
	     * //par3Api.getStoragePools();
	     * //par3Api.getStoragePort("");
	     * 
	     * //par3Api.getNamespaces();
	     * //Par3NamespaceRepGroup ns = par3Api.getNamespaceDetails("psns");
	     * //dummy = ns.getReplicationGroups().size();
	     * 
	     * //createBucket(String name, String namespace, String repGroup,
	     * //String retentionPeriod, String blkSizeHQ, String notSizeSQ) throws Par3Exception {
	     * //par3Api.createBucket("m1", "s3", "urn:storageos:ReplicationGroupInfo:b3bf2d47-d732-457c-bb9b-d260eb53a76a:global",
	     * //"4", "99", "55", "testlogin");
	     * //par3Api.deleteBucket("esc_myproj_bucket1");
	     * }
	     */
}
