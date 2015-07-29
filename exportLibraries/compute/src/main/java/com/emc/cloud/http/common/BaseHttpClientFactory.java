/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * 
 */
package com.emc.cloud.http.common;

/**
 * @author prabhj
 *
 */
import com.emc.cloud.http.ssl.SSLHelper;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import java.security.GeneralSecurityException;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BaseHttpClientFactory {

    private static final Logger LOG = LoggerFactory.getLogger(BaseHttpClientFactory.class);

    /** create thread-safe HTTP client instances */
    protected boolean threadSafeClients = false;

    /** for thread-safe clients, the maxiumum concurrent connections */
    protected int maxConnections = 25;

    /** for thread-safe clients, the maximum concurrent conections allows to a specific host */
    protected int maxConnectionsPerHost = 5;

    /** connection timeout in milliseconds used by extractor worker, configurable from command line */
    protected int connectionTimeout = 10000;

    /** connection read timeout in milliseconds used by extractor worker, configurable from command line */
    protected int connectionReadTimeout = 150000;

    /** if true allows for more permissive ssl, shouldn't be used in production */
    protected boolean relaxSSL = false;

    /** if set to true configures the httpClient the ViPR SSL trust manager. */
    protected boolean secureSSL = false;

    private CoordinatorClientImpl coordinator;

    public void setRelaxSSL(boolean relaxSSL) {
        this.relaxSSL = relaxSSL;
    }

    public boolean getRelaxSSL() {
        return relaxSSL;
    }

    public void setThreadSafeClients(boolean threadSafeClients) {
        this.threadSafeClients = threadSafeClients;
    }

    public boolean getThreadSafeClients() {
        return threadSafeClients;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnectionsPerHost(int maxConnectionsPerHost) {
        this.maxConnectionsPerHost = maxConnectionsPerHost;
    }

    public int getMaxConnectionsPerHost() {
        return maxConnectionsPerHost;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionReadTimeout(int connectionReadTimeout) {
        this.connectionReadTimeout = connectionReadTimeout;
    }

    public int getConnectionReadTimeout() {
        return connectionReadTimeout;
    }

    /**
     * create a HTTPClient using the factory's default configuration
     * 
     * @throws AuthenticationException, GeneralSecurityException, RuntimeException
     * 
     */
    public AbstractHttpClient createHTTPClient() throws AuthenticationException, GeneralSecurityException, RuntimeException {
        // use the configured defaults
        return createHTTPClient(connectionTimeout, connectionReadTimeout);
    }

    /**
     * create a HTTPClient overriding the factory's default configuration
     * 
     * @param useConnectionTimeout - allows override of the the default connectionTimeout
     * @param useConnectionReadTimeout - allows override of the default connectionReadTimeout
     * @throws AuthenticationException, GeneralSecurityException, RuntimeException
     * 
     */
    public AbstractHttpClient createHTTPClient(int useConnectionTimeout, int useConnectionReadTimeout) throws AuthenticationException,
            GeneralSecurityException, RuntimeException {

        return createRawHTTPClient(useConnectionTimeout, useConnectionReadTimeout);

    }

    /**
     * Create a HTTPClient using the factories configuration without Credentials
     * 
     * @param useConnectionTimeout - allows override of the the default connectionTimeout
     * @param useConnectionReadTimeout - allows override of the default connectionReadTimeout
     * @throws AuthenticationException, GeneralSecurityException, RuntimeException
     * 
     */
    protected AbstractHttpClient createRawHTTPClient(int useConnectionTimeout, int useConnectionReadTimeout)
            throws AuthenticationException, GeneralSecurityException, RuntimeException {

        // select the appropriate connection manager and set options as appropriate
        ClientConnectionManager cm = null;
        if (threadSafeClients) {
            ThreadSafeClientConnManager tscm = new ThreadSafeClientConnManager();
            tscm.setMaxTotal(maxConnections);
            tscm.setDefaultMaxPerRoute(maxConnectionsPerHost);
            cm = tscm;
        } else {
            cm = new SingleClientConnManager();
        }

        // construct a client instance with the connection manager embedded
        AbstractHttpClient httpClient = new DefaultHttpClient(cm);

        if (relaxSSL) {
            // !!!WARNING: This effectively turns off the authentication component of SSL, leaving only encryption
            // can throw GeneralSecurityException
            SSLHelper.configurePermissiveSSL(httpClient);
        } else if (isSecureSSL()) {
            SSLHelper.configureSSLWithTrustManger(httpClient, coordinator);
        }

        // see org.apache.http.client.params.AllClientPNames for a collected
        // list of the available client parameters
        HttpParams clientParams = httpClient.getParams();
        HttpConnectionParams.setConnectionTimeout(clientParams, useConnectionTimeout);
        HttpConnectionParams.setSoTimeout(clientParams, useConnectionReadTimeout);

        // consider turning off the use of the Expect: 100-Continue response if
        // your posts/puts tend to be relatively small
        HttpProtocolParams.setUseExpectContinue(clientParams, false);

        // TODO: reconsider this setting
        // by default the client auto-retries on failures - turn that off so we can handle it manually
        httpClient.setHttpRequestRetryHandler(new DefaultHttpRequestRetryHandler(0, false));

        return httpClient;
    }

    public void setCoordinator(CoordinatorClientImpl coordinator) {
        this.coordinator = coordinator;
    }

    public CoordinatorClientImpl getCoordinator() {
        return coordinator;
    }

    public boolean isSecureSSL() {
        return secureSSL;
    }

    public void setSecureSSL(boolean secureSSL) {
        this.secureSSL = secureSSL;
    }
}
