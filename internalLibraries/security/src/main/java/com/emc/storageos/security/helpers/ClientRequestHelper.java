/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.helpers;

import java.net.URI;
import java.security.cert.X509Certificate;
import java.security.SecureRandom;

import javax.crypto.SecretKey;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.HostnameVerifier;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.jaxrs.JacksonJaxbJsonProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.security.SignatureHelper;
import com.emc.storageos.security.authentication.AbstractHMACAuthFilter;
import com.emc.storageos.security.authentication.InternalApiSignatureKeyGenerator;
import com.emc.storageos.security.authentication.InternalApiSignatureKeyGenerator.SignatureKeyType;
import com.emc.storageos.security.authentication.RequestProcessingUtils;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.WebResource.Builder;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.client.urlconnection.HTTPSProperties;

/**
 * Helper class for constructing Jersey Client objects, adding HMAC signatures, etc.
 * 
 * Once configured via its setters, this class is intended to be thread-safe in its
 * operation
 */
public class ClientRequestHelper {
    private static final Logger log = LoggerFactory.getLogger(ClientRequestHelper.class);
    public static final int DEFAULT_READ_TIMEOUT = 30 * 1000;
    public static final int DEFAULT_CONNECT_TIMEOUT = 30 * 1000;

    private InternalApiSignatureKeyGenerator _keyGenerator = null;
    private SignatureKeyType defaultSignatureType = SignatureKeyType.INTERNAL_API;

    private int clientReadTimeout;
    private int clientConnectTimeout;

    // typically the default constructor will be used for clients that are not
    // using signature based requests (coordinator/keygenerator not needed)
    public ClientRequestHelper() {
    }

    /**
     * Construct with the supplied coordinator
     * 
     * @param coordinatorClient
     */
    public ClientRequestHelper(CoordinatorClient coordinatorClient) {
        this(coordinatorClient, DEFAULT_READ_TIMEOUT, DEFAULT_CONNECT_TIMEOUT);
    }

    /**
     * This is the preferred constructor. Pass in an existing keygenerator that has been instantiated
     * by the container.
     * Construct with the supplied keyGenerator and non-default timeout values for clients
     * 
     * @param coordinatorClient
     * @param clientReadTimeout
     * @param clientConnectTimeout
     */
    public ClientRequestHelper(InternalApiSignatureKeyGenerator keyGen, int clientReadTimeout, int clientConnectTimeout) {
        this.clientReadTimeout = clientReadTimeout;
        this.clientConnectTimeout = clientConnectTimeout;
        this._keyGenerator = keyGen;
        this._keyGenerator.loadKeys();
    }

    /**
     * Construct with the supplied coordinator and non-default timeout values for clients
     * Use this constructor if a keygenerator is not already available.
     * 
     * @param coordinatorClient
     * @param clientReadTimeout
     * @param clientConnectTimeout
     */
    public ClientRequestHelper(CoordinatorClient coordinatorClient, int clientReadTimeout, int clientConnectTimeout) {
        this.clientReadTimeout = clientReadTimeout;
        this.clientConnectTimeout = clientConnectTimeout;
        this._keyGenerator = new InternalApiSignatureKeyGenerator();
        this._keyGenerator.setCoordinator(coordinatorClient);
        this._keyGenerator.loadKeys();
    }

    public final int getClientConnectTimeout() {
        return clientConnectTimeout;
    }

    public final void setClientConnectTimeout(int clientConnectTimeout) {
        this.clientConnectTimeout = clientConnectTimeout;
    }

    public final int getClientReadTimeout() {
        return clientReadTimeout;
    }

    public final void setClientReadTimeout(int clientReadTimeout) {
        this.clientReadTimeout = clientReadTimeout;
    }

    public SignatureKeyType getDefaultSignatureType() {
        return defaultSignatureType;
    }

    public void setDefaultSignatureType(SignatureKeyType defaultSignatureType) {
        this.defaultSignatureType = defaultSignatureType;
    }

    /**
     * Create an SSL-trusting Client using the default configurations
     * 
     * This method adds permissive HTTPSProperties settings to the
     * configuration of the client.
     * 
     * Note: Client objects are expensive to create and largely
     * thread-safe so they should be re-used across requests
     * 
     * @return the client
     */
    public Client createClient() {
        return createClient(clientReadTimeout, clientConnectTimeout);
    }

    /**
     * Create an SSL-trusting Client using the specified configurations
     * 
     * This method adds permissive HTTPSProperties settings to the
     * configuration of the client.
     * 
     * Note: Client objects are expensive to create and largely
     * thread-safe so they should be re-used across requests
     * 
     * @param readTimeout the read timeout
     * @param connectTimeout the connect timeout
     * @return the client
     */
    public Client createClient(int readTimeout, int connectTimeout) {
        ClientConfig config = new DefaultClientConfig();
        config.getClasses().add(JacksonJaxbJsonProvider.class);
        config.getProperties().put(ClientConfig.PROPERTY_READ_TIMEOUT, readTimeout);
        config.getProperties().put(ClientConfig.PROPERTY_CONNECT_TIMEOUT, connectTimeout);
        return createClient(config);
    }

    /**
     * Create an SSL-trusting Client using the specified configurations
     * 
     * This method adds permissive HTTPSProperties settings to the
     * configuration of the client.
     * 
     * Note: Client objects are expensive to create and largely
     * thread-safe so they should be re-used across requests
     * 
     * @param config the configuration
     * @return the client
     */
    public Client createClient(ClientConfig config) {
        try {
            config.getProperties().put(HTTPSProperties.PROPERTY_HTTPS_PROPERTIES, createPermissiveHTTPSProperties());
        } catch (Exception ex) {
            log.error("Unexpected failure while configuring trusting HTTPS properties", ex);
        }
        return Client.create(config);
    }

    /**
     * Create a client request using the provided client, base URI, and request URI
     * 
     * @param client
     * @param baseURI
     * @param requestPath
     * @return the request
     */
    public WebResource createRequest(Client client, String baseURI, String requestPath) {
        return createRequest(client, URI.create(baseURI), URI.create(requestPath));
    }

    /**
     * Create a client request using the provided client, base URI, and request URI
     * 
     * @param client
     * @param baseURI
     * @param requestPath
     * @return the request
     */
    public WebResource createRequest(Client client, URI baseURI, URI requestPath) {
        URI fullURI = baseURI.resolve(requestPath);
        return client.resource(fullURI);
    }

    /**
     * Add HMAC signature headers to this request using internal key
     * (Use this version of addSignature for internal api calls)
     * 
     * Note: In order for the signature to be valid, all query parameters
     * should have been added to the request prior to calling this method
     * 
     * @param webResource client request to sign
     * @return a builder object for this request with the signatures added
     */
    public Builder addSignature(WebResource webResource) {
        long timestamp = System.currentTimeMillis();
        return webResource.header(AbstractHMACAuthFilter.INTERNODE_HMAC, getSignature(webResource, timestamp, null))
                .header(AbstractHMACAuthFilter.INTERNODE_TIMESTAMP, timestamp);
    }

    /**
     * Add HMAC signature headers to this request using the provided key
     * (Use this version of addSignature to inter vdc requests, with the key
     * corresponding to the target vdc)
     * 
     * @param webResource client request to sign
     * @param key secret key
     * @return a builder object for this request with the signatures added
     */
    public Builder addSignature(WebResource webResource, SecretKey key) {
        long timestamp = System.currentTimeMillis();
        return webResource.header(AbstractHMACAuthFilter.INTERNODE_HMAC, getSignature(webResource, timestamp, key))
                .header(AbstractHMACAuthFilter.INTERNODE_TIMESTAMP, timestamp);
    }

    /**
     * Add a user token to the returned request builder
     * 
     * @param requestBuilder the request builder
     * @param token the user token
     * @return the builder with token added
     */
    public Builder addToken(Builder requestBuilder, String token) {
        return addTokens(requestBuilder, token, null);
    }

    /**
     * Add user and/or proxy tokens to the returned request builder
     * 
     * @param requestBuilder the request builder
     * @param token the user token (or null)
     * @param proxyToken the proxy token (or null)
     * @return the builder with token(s) added
     */
    public Builder addTokens(Builder requestBuilder, String token, String proxyToken) {
        if (StringUtils.isNotEmpty(token)) {
            requestBuilder = requestBuilder.header(RequestProcessingUtils.AUTH_TOKEN_HEADER, token);
        }
        if (StringUtils.isNotEmpty(proxyToken)) {
            requestBuilder = requestBuilder.header(RequestProcessingUtils.AUTH_PROXY_TOKEN_HEADER, proxyToken);
        }
        return requestBuilder;
    }

    /**
     * Get an HMAC signature for the specified client request
     * 
     * Note: In order for the signature to be valid, all query parameters
     * should have been added to the request prior to calling this method
     * 
     * @param webResource client request to sign
     * @param timestamp timestamp of the request
     * @param key optional, if supplied, signature will be computed on the fly based on
     *            the provided key. If omitted, the signature will be computed based on the internal api
     *            key cached in the keygenerator
     * 
     * @return HMAC signature for this request
     */
    private String getSignature(WebResource webResource, long timestamp, SecretKey key) {
        StringBuilder buf = new StringBuilder(webResource.getURI().toString().toLowerCase());
        buf.append(timestamp);
        String sig = (key == null) ? _keyGenerator.sign(buf.toString(), defaultSignatureType) :
                SignatureHelper.sign2(buf.toString(), key, key.getAlgorithm());
        log.debug("getSignature(): buffer: {} signature: {}", buf.toString(), sig);
        return sig;
    }

    /**
     * Create an HTTPProperties object that will allow communication
     * with self-signed certs
     * 
     * @return the properties
     */
    private HTTPSProperties createPermissiveHTTPSProperties() throws Exception {
        // Create an SSL context that trusts all certs
        SSLContext sc = null;
        sc = SSLContext.getInstance("TLS");
        sc.init(null, trustingTrustManager, new SecureRandom());
        return new HTTPSProperties(trustingHostVerifier, sc);
    }

    /**
     * A HostnameVerifier that trusts everything
     */
    private static final HostnameVerifier trustingHostVerifier = new HostnameVerifier() {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    };

    /**
     * A TrustManager that trusts everything
     */
    private static final TrustManager[] trustingTrustManager = new TrustManager[] { new X509TrustManager() {
        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] certs, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] certs, String authType) {
        }
    } };
}
