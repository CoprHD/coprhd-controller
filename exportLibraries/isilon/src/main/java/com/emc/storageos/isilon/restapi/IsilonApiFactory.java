/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.isilon.restapi;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodRetryHandler;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
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

    // Class for initial Isilon Session creation
    private class IsilonIdentity {
    	private String username;
    	private String password;
    	private ArrayList<String> services;
    	
		public IsilonIdentity(String username, String password) {
			super();
			this.username = username;
			this.password = password;
			this.services = new ArrayList<String>();
			services.add("platform");
			services.add("namespace");
		}
    }
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
    	// Handling the multiple threads accessing the clientMap
    	synchronized(this){
	        IsilonApi isilonApi = _clientMap.get(endpoint.toString() + ":" + username + ":" + password);
	        if (isilonApi == null) {
	            isilonApi = createRESTClient(endpoint, username, password);
	        } else {
	            boolean validSession = isilonApi.validateSession();
	            if (!validSession) {
	            	_log.info("Invalidating the Isilon Rest client with CSRF Auth for the management server {}", endpoint);
	            	isilonApi.close();
	            	_clientMap.remove(endpoint.toString() + ":" + username + ":" + password);
	            	isilonApi = createRESTClient(endpoint, username, password);
	            }
	        }
	        return isilonApi;
    	}
    }

    /**
     * Creating a new Isilon REST client with the provided credentials
     * 
     * @param endpoint
     * @param username
     * @param password
     * @return
     */
	private IsilonApi createRESTClient(URI endpoint, String username, String password) {
		Client jerseyClient = new ApacheHttpClient(_clientHandler);
		Map<String, String> isilonAuthInfo = getAuthType(jerseyClient, endpoint, username, password);

		String authType = isilonAuthInfo.get(IsilonApiConstants.AUTH_TYPE);

		RESTClient restClient = null;
		if (authType.equals(IsilonApiConstants.AuthType.BASIC.name())) {
			_log.info("Creating new Isilon Rest client with Basic Auth for the management server {}", endpoint);
		    jerseyClient.addFilter(new HTTPBasicAuthFilter(username, password));
		    restClient = new RESTClient(jerseyClient, authType);
		} else {
			_log.info("Creating new Isilon Rest client with CSRF Auth for the management server {}", endpoint);
			String isisessId = isilonAuthInfo.get(IsilonApiConstants.SESSION_COOKIE);
			String isicsrfId = isilonAuthInfo.get(IsilonApiConstants.CSRF_COOKIE);
			restClient = new RESTClient(jerseyClient, authType, endpoint, isisessId, isicsrfId);
		}

		IsilonApi isilonApi = new IsilonApi(endpoint, restClient);
		_clientMap.putIfAbsent(endpoint.toString() + ":" + username + ":" + password, isilonApi);
		return isilonApi;
	}

	/**
     * Method gets the supported Authentication type from the Isilon
     * 
     * @param _client reference to the Jersey Apache HTTP client.
     * @param baseUrl reference to the device URI
     * @param username Isilon credentials
     * @param password Isilon credentials
     * @return
     */
	private Map<String, String> getAuthType(Client _client, URI baseUrl, String username, String password) {
		Map<String, String> isilonAuthInfo = new HashMap<String,String>();
		isilonAuthInfo.put(IsilonApiConstants.AUTH_TYPE, IsilonApiConstants.AuthType.BASIC.name());

		IsilonIdentity isilonObj = new IsilonIdentity(username, password);
		String body = new Gson().toJson(isilonObj);
		URI sessionURL = baseUrl.resolve(IsilonApiConstants.URI_SESSION);
		ClientResponse clientResp = _client.resource(sessionURL).type(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON).post(ClientResponse.class, body);
		if (clientResp.getStatus() != 201) {
			String errMsg = String.format("Unable to find the supported authentication for the given endpoint %s", baseUrl.toString());
            _log.error(errMsg, baseUrl);
            throw IsilonException.exceptions.unableToConnect(baseUrl);
        }
		String strObj = clientResp.getEntity(String.class);

		Map<String, String> isilonCookieMap = getIsilonCookies(clientResp);

        if (isilonCookieMap != null && isilonCookieMap.size() > 1) {
            String isisessId = isilonCookieMap.get(IsilonApiConstants.SESSION_COOKIE);
            isilonAuthInfo.put(IsilonApiConstants.SESSION_COOKIE, isisessId);
            String isicsrfId = isilonCookieMap.get(IsilonApiConstants.CSRF_COOKIE);
            isilonAuthInfo.put(IsilonApiConstants.CSRF_COOKIE, isicsrfId);
            if (!isisessId.isEmpty() && !isicsrfId.isEmpty()) {
            	isilonAuthInfo.put(IsilonApiConstants.AUTH_TYPE, IsilonApiConstants.AuthType.CSRF.name());
            	return isilonAuthInfo;
            }
        }
        isilonAuthInfo.put(IsilonApiConstants.AUTH_TYPE, IsilonApiConstants.AuthType.BASIC.name());
        return isilonAuthInfo;
	}

	/**
	 * Method retrieve the Isilon cookies
	 * 
	 * @param clientResp HTTP client response
	 * @return
	 */
	private Map<String,String> getIsilonCookies(ClientResponse clientResp) {
		HashMap<String,String> isilonCookieMap = null;

		List<NewCookie> cookies = clientResp.getCookies();

		if (cookies != null && cookies.size() > 1) {
			isilonCookieMap = new HashMap<String,String>();
			for(NewCookie cookie : cookies)
		    {
				if (cookie.getName().equals(IsilonApiConstants.SESSION_COOKIE))
					isilonCookieMap.put(IsilonApiConstants.SESSION_COOKIE, cookie.getValue());
				if (cookie.getName().equals(IsilonApiConstants.CSRF_COOKIE))
					isilonCookieMap.put(IsilonApiConstants.CSRF_COOKIE, cookie.getValue());
		    }
		}

		return isilonCookieMap;
	}

}
