/*
 * Copyright (c) 2009 EMC Corporation
 * All Rights Reserved
 */
package com.emc.cloud.http.ssl;

import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.security.ssl.ViPRX509TrustManager;
import java.security.GeneralSecurityException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

/**
 * Helper for configuring SSL connections
 * 
 * @author perkib
 */
public class SSLHelper {

    private final static String SSL = "SSL";
    private final static String HTTP = "http";
    private final static String HTTPS = "https";

    /**
     * static only, no instances!
     */
    private SSLHelper() {
    }

    /**
     * Set the necessary options on a HttpClient instance to enable
     * SSL connections using vipr trust manager.
     *
     * @param threadSafeClients boolean if true a thread-safe HTTP client instance is created.
     * @param maxConnections for thread-safe clients, the maximum concurrent connections
     * @param maxConnectionsPerHost for thread-safe clients, the maximum concurrent connections allows to a specific host
     * @param useConnectionTimeout socket connection timeout in milliseconds
     * @param useConnectionReadTimeout connection read timeout in milliseconds
     * @param coordinatorClient {@link CoordinatorClientImpl} instance
     * @return {@link CloseableHttpClient}
     * @throws {@link GeneralSecurityException}
     */
    public static CloseableHttpClient getSSLEnabledHttpClient(boolean threadSafeClients, int maxConnections,
            int maxConnectionsPerHost, int useConnectionTimeout, int useConnectionReadTimeout,
            CoordinatorClientImpl coordinatorClient) throws GeneralSecurityException {
        CloseableHttpClient httpClient = null;
        SSLContext sslContext;

        try {
            sslContext = SSLContext.getInstance(SSL);
            sslContext.init(null, new TrustManager[] { new ViPRX509TrustManager(coordinatorClient) }, null);
            httpClient = getHttpClient(threadSafeClients, maxConnections, maxConnectionsPerHost, useConnectionTimeout,
                    useConnectionReadTimeout, sslContext);

        } catch (GeneralSecurityException ex) {
            throw new GeneralSecurityException("Error updating https scheme with trust manager", ex);
        }
        return httpClient;
    }

    /**
     * Set the necessary options on a HttpClient instance to enable permissive
     * SSL connections. This implies disabling of trust validation, hostname
     * verification, and expiration checks.
     * <p>
     * WARNING: This is very handy for development and testing, but is NOT recommended for production code.
     * <p>
     * @param threadSafeClients boolean if true a thread-safe HTTP client instance is created.
     * @param maxConnections for thread-safe clients, the maximum concurrent connections
     * @param maxConnectionsPerHost for thread-safe clients, the maximum concurrent connections allows to a specific host
     * @param useConnectionTimeout socket connection timeout in milliseconds
     * @param useConnectionReadTimeout connection read timeout in milliseconds
     * @return {@link CloseableHttpClient}
     * @throws {@link GeneralSecurityException}
     */
    public static CloseableHttpClient getPermissiveSSLHttpClient(boolean threadSafeClients, int maxConnections,
            int maxConnectionsPerHost, int useConnectionTimeout, int useConnectionReadTimeout)
            throws GeneralSecurityException {
        CloseableHttpClient httpClient = null;
        SSLContext sslContext;

        try {
            sslContext = SSLContext.getInstance(SSL);
            sslContext.init(null, new TrustManager[] { new PermissiveX509TrustManager(null) }, null);
            httpClient = getHttpClient(threadSafeClients, maxConnections, maxConnectionsPerHost, useConnectionTimeout,
                    useConnectionReadTimeout, sslContext);

        } catch (GeneralSecurityException ex) {
            throw new GeneralSecurityException("Error updating https scheme with permissive settings", ex);
        }
        return httpClient;
    }

    /**
     * Set the necessary options on a HttpClientbuilder instance to enable permissive
     * SSL connections. This implies disabling of trust validation, hostname
     * verification, and expiration checks.
     * <p>
     * WARNING: This is very handy for development and testing, but is NOT recommended for production code.
     * <p>
     * @param threadSafeClients boolean if true a thread-safe HTTP client builder instance is created.
     * @param maxConnections for thread-safe clients, the maximum concurrent connections
     * @param maxConnectionsPerHost for thread-safe clients, the maximum concurrent connections allows to a specific host
     * @param useConnectionTimeout socket connection timeout in milliseconds
     * @param useConnectionReadTimeout connection read timeout in milliseconds
     * @return {@link HttpClientBuilder}
     * @throws {@link GeneralSecurityException}
     */
    public static HttpClientBuilder getPermissiveSSLHttpClientBuilder(boolean threadSafeClients, int maxConnections,
            int maxConnectionsPerHost, int useConnectionTimeout, int useConnectionReadTimeout)
            throws GeneralSecurityException {
        HttpClientBuilder httpClientBuilder = null;
        SSLContext sslContext;

        try {
            sslContext = SSLContext.getInstance(SSL);
            sslContext.init(null, new TrustManager[] { new PermissiveX509TrustManager(null) }, null);
            httpClientBuilder = getHttpClientBuilder(threadSafeClients, maxConnections, maxConnectionsPerHost,
                    useConnectionTimeout, useConnectionReadTimeout, sslContext);

        } catch (GeneralSecurityException ex) {
            throw new GeneralSecurityException("Error updating https scheme with permissive settings", ex);
        }
        return httpClientBuilder;
    }

    /**
     * Get an HttpClient instance
     * @param threadSafeClients boolean if true a thread-safe HTTP client instance is created.
     * @param maxConnections for thread-safe clients, the maximum concurrent connections
     * @param maxConnectionsPerHost for thread-safe clients, the maximum concurrent connections allows to a specific host
     * @param useConnectionTimeout socket connection timeout in milliseconds
     * @param useConnectionReadTimeout connection read timeout in milliseconds
     * @param sslContext {@link SSLContext} initialized SSLContext instance
     * @return {@link CloseableHttpClient} instance
     */
    private static CloseableHttpClient getHttpClient(boolean threadSafeClients, int maxConnections,
            int maxConnectionsPerHost, int useConnectionTimeout, int useConnectionReadTimeout, SSLContext sslContext) {
        return getHttpClientBuilder(threadSafeClients, maxConnections, maxConnectionsPerHost, useConnectionTimeout,
                useConnectionReadTimeout, sslContext).build();
    }

    /**
     * Generate a {@link HttpClientBuilder} instance to be used to create a httpclient instance.
     * @param threadSafeClients boolean if true a thread-safe HTTP client instance is created.
     * @param maxConnections for thread-safe clients, the maximum concurrent connections
     * @param maxConnectionsPerHost for thread-safe clients, the maximum concurrent connections allows to a specific host
     * @param useConnectionTimeout socket connection timeout in milliseconds
     * @param useConnectionReadTimeout connection read timeout in milliseconds
     * @param sslContext {@link SSLContext} initialized SSLContext instance
     * @return {@link HttpClientBuilder} instance
     */
    private static HttpClientBuilder getHttpClientBuilder(boolean threadSafeClients, int maxConnections,
            int maxConnectionsPerHost, int useConnectionTimeout, int useConnectionReadTimeout, SSLContext sslContext) {
        SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(sslContext,
                SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory> create()
                .register(HTTP, PlainConnectionSocketFactory.getSocketFactory())
                .register(HTTPS, socketFactory).build();

        HttpClientConnectionManager cm = null;
        if (threadSafeClients) {
            cm = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
            if (maxConnections > 0) {
                ((PoolingHttpClientConnectionManager) cm).setMaxTotal(maxConnections);
            }
            if (maxConnectionsPerHost > 0) {
                ((PoolingHttpClientConnectionManager) cm).setDefaultMaxPerRoute(maxConnectionsPerHost);
            }
        } else {
            cm = new BasicHttpClientConnectionManager(socketFactoryRegistry);
        }

        //Build the request config identifying the socket connection parameters.
        RequestConfig.Builder requestConfigBulder = RequestConfig.custom();
        if (useConnectionReadTimeout > 0) {
            requestConfigBulder.setSocketTimeout(useConnectionReadTimeout);
        }
        if (useConnectionTimeout > 0) {
            requestConfigBulder.setConnectTimeout(useConnectionTimeout);
        }
        RequestConfig requestConfig = requestConfigBulder.setExpectContinueEnabled(true).build();

        // construct a client instance with the connection manager embedded
        HttpClientBuilder httpClientBuilder = HttpClients.custom();
        httpClientBuilder.setConnectionManager(cm);
        httpClientBuilder.setDefaultRequestConfig(requestConfig);
        return httpClientBuilder;
    }
}