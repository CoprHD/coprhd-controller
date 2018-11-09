/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * 
 */
package com.emc.cloud.http.common;

import java.security.GeneralSecurityException;

import org.apache.http.auth.AuthenticationException;
import org.apache.http.impl.client.CloseableHttpClient;

/**
 * @author prabhj
 *
 */
import com.emc.cloud.http.ssl.SSLHelper;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;

public class BaseHttpClientFactory {

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

    /**
     * create a HTTPClient using the factory's default configuration
     * @return {@link CloseableHttpClient} instance
     * @throws AuthenticationException, GeneralSecurityException, RuntimeException
     *
     */
    public CloseableHttpClient createHTTPClient() throws AuthenticationException, GeneralSecurityException, RuntimeException {
        // use the configured defaults
        return createHTTPClient(connectionTimeout, connectionReadTimeout);
    }

    /**
     * create a HTTPClient overriding the factory's default configuration
     *
     * @param useConnectionTimeout - allows override of the the default connectionTimeout
     * @param useConnectionReadTimeout - allows override of the default connectionReadTimeout
     * @return {@link CloseableHttpClient} instance
     * @throws AuthenticationException, GeneralSecurityException, RuntimeException
     *
     */
    public CloseableHttpClient createHTTPClient(int useConnectionTimeout, int useConnectionReadTimeout) throws AuthenticationException,
            GeneralSecurityException, RuntimeException {

        return createRawHTTPClient(useConnectionTimeout, useConnectionReadTimeout);

    }

    /**
     * Create a HTTPClient using the factories configuration without Credentials
     *
     * @param useConnectionTimeout - allows override of the the default connectionTimeout
     * @param useConnectionReadTimeout - allows override of the default connectionReadTimeout
     * @return {@link CloseableHttpClient} instance
     * @throws AuthenticationException, GeneralSecurityException, RuntimeException
     *
     */
    protected CloseableHttpClient createRawHTTPClient(int useConnectionTimeout, int useConnectionReadTimeout)
            throws AuthenticationException, GeneralSecurityException, RuntimeException {

        CloseableHttpClient closeableHttpClient = null;
        if (relaxSSL) {
            // !!!WARNING: This effectively turns off the authentication
            // component of SSL, leaving only encryption
            // can throw GeneralSecurityException
            closeableHttpClient = SSLHelper.getPermissiveSSLHttpClient(threadSafeClients, maxConnections,
                    maxConnectionsPerHost, useConnectionTimeout, useConnectionReadTimeout);
        } else if (isSecureSSL()) {
            closeableHttpClient = SSLHelper.getSSLEnabledHttpClient(threadSafeClients, maxConnections,
                    maxConnectionsPerHost, useConnectionTimeout, useConnectionReadTimeout, coordinator);
        }

        return closeableHttpClient;
    }
}
