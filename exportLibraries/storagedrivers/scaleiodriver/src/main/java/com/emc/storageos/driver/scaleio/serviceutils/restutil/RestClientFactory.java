/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.scaleio.serviceutils.restutil;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.client.apache.ApacheHttpClient;
import com.sun.jersey.client.apache.ApacheHttpClientHandler;
import com.sun.jersey.client.urlconnection.HTTPSProperties;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodRetryHandler;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

abstract public class RestClientFactory {
    private Logger _log = LoggerFactory.getLogger(RestClientFactory.class);

    private int _maxConn;
    private int _maxConnPerHost;
    private int _connTimeout;
    private int _socketConnTimeout;
    private boolean _needCertificateManager;

    private ApacheHttpClientHandler _clientHandler;
    private ConcurrentMap<String, RestClientItf> _clientMap;
    private MultiThreadedHttpConnectionManager _connectionManager;

    /**
     * Maximum number of outstanding connections
     * 
     * @param maxConn
     */
    public void setMaxConnections(int maxConn) {
        _maxConn = maxConn;
    }

    public int getMaxConnections() {
        return _maxConn;
    }

    /**
     * Maximum number of outstanding connections per host
     * 
     * @param maxConnPerHost
     */
    public void setMaxConnectionsPerHost(int maxConnPerHost) {
        _maxConnPerHost = maxConnPerHost;
    }

    public int getMaxConnectionsPerHost() {
        return _maxConnPerHost;
    }

    /**
     * Connection timeout
     * 
     * @param connectionTimeoutMs
     */
    public void setConnectionTimeoutMs(int connectionTimeoutMs) {
        _connTimeout = connectionTimeoutMs;
    }

    public int getConnectionTimeoutMs() {
        return _connTimeout;
    }

    /**
     * Socket connection timeout
     * 
     * @param connectionTimeoutMs
     */
    public void setSocketConnectionTimeoutMs(int connectionTimeoutMs) {
        _socketConnTimeout = connectionTimeoutMs;
    }

    public int getSocketConnectionTimeoutMs() {
        return _socketConnTimeout;
    }

    /**
     * If Factory should create a ApacheHTTPClient client with Cetificate Manager
     * 
     * @param need
     */
    public void setNeedCertificateManager(boolean need) {
        _needCertificateManager = need;
    }

    public boolean getNeedCertificateManager() {
        return _needCertificateManager;
    }

    /**
     * Initialize HTTP client
     */
    public void init() {
        _clientMap = new ConcurrentHashMap<String, RestClientItf>();

        _log.info("Init");

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
        // client.

        if (_needCertificateManager) {
            // TMP CODE to create dummy security certificate manager
            ClientConfig clientConfig = null;
            try {
                final TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(final X509Certificate[] chain, final String authType) {
                    }

                    @Override
                    public void checkServerTrusted(final X509Certificate[] chain, final String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                } };
                // Install the all-trusting trust manager
                SSLContext sslContext;
                sslContext = SSLContext.getInstance("SSL");
                sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
                javax.net.ssl.HostnameVerifier hostVerifier = new javax.net.ssl.HostnameVerifier() {
                    @Override
                    public boolean verify(String hostname, javax.net.ssl.SSLSession sslSession) {
                        return true;
                    }
                };
                clientConfig = new com.sun.jersey.client.apache.config.DefaultApacheHttpClientConfig();
                clientConfig.getProperties().put(HTTPSProperties.PROPERTY_HTTPS_PROPERTIES,
                        new HTTPSProperties(hostVerifier, sslContext));
            } catch (GeneralSecurityException ex) {
                throw new RuntimeException("Failed to obtain ApacheHTTPClient Config");
            }
            _clientHandler = new ApacheHttpClientHandler(client, clientConfig);
        }
        else {
            _clientHandler = new ApacheHttpClientHandler(client);
        }

        Protocol.registerProtocol("https", new Protocol("https", new NonValidatingSocketFactory(), 443));

    }

    /**
     * shutdown http connection manager.
     */
    protected void shutdown() {
        _connectionManager.shutdown();
    }

    public RestClientItf getRESTClient(URI endpoint, String username, String password, boolean authFilter) {
        RestClientItf clientApi = _clientMap.get(endpoint.toString() + ":" + username + ":" + password);
        if (clientApi == null) {
            Client jerseyClient = new ApacheHttpClient(_clientHandler);
            if (authFilter) {
                jerseyClient.addFilter(new HTTPBasicAuthFilter(username, password));
            }
            clientApi = createNewRestClient(endpoint, username, password, jerseyClient);

            _clientMap.putIfAbsent(endpoint.toString() + ":" + username + ":" + password, clientApi);
        }
        return clientApi;
    }

    /**
     * Remove the connection from the cache.
     * 
     * @param endpoint
     * @param username
     * @param password
     */
    public void removeRESTClient(URI endpoint, String username, String password) {
        String clientKey = endpoint.toString() + ":" + username + ":" + password;
        RestClientItf clientApi = _clientMap.get(clientKey);
        if (null != clientApi) {
            _clientMap.remove(clientKey);
        }
    }

    public RestClientItf getRESTClient(URI endpoint, String username, String password) {
        RestClientItf clientApi = _clientMap.get(endpoint.toString() + ":" + username + ":" + password);
        if (clientApi == null) {
            Client jerseyClient = new ApacheHttpClient(_clientHandler);
            clientApi = createNewRestClient(endpoint, username, password, jerseyClient);
            _clientMap.putIfAbsent(endpoint.toString() + ":" + username + ":" + password, clientApi);
        }
        return clientApi;
    }

    abstract protected RestClientItf createNewRestClient(URI endpoint, String username, String password,
            Client client);

    protected Client getBaseClient(URI endpoint, String username, String password, boolean authFilter) {
        Client jerseyClient = new ApacheHttpClient(_clientHandler);
        if (authFilter) {
            jerseyClient.addFilter(new HTTPBasicAuthFilter(username, password));
        }
        return jerseyClient;
    }

}
