/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.security.helpers;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.SecretKey;

import com.sun.jersey.api.client.filter.ClientFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.WebResource.Builder;
import com.emc.storageos.security.authentication.InternalApiSignatureKeyGenerator;
import com.emc.storageos.security.authentication.InternalApiSignatureKeyGenerator.SignatureKeyType;
import com.emc.storageos.security.exceptions.SecurityException;

/**
 * Abstract class for building ViPR REST clients. This class provides help
 * in maintaining Client state, methods for constructing requests, and
 * adding HMAC signatures and tokens.
 */
public abstract class BaseServiceClient implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(BaseServiceClient.class);
    private volatile boolean initialized = false;
    private ClientRequestHelper clientRequestHelper;
    private Client client;

    private int clientMaxRetries = 3;
    private int clientRetryInterval = 5000; // 5s

    private int clientReadTimeout = ClientRequestHelper.DEFAULT_CONNECT_TIMEOUT;
    private int clientConnectTimeout = ClientRequestHelper.DEFAULT_READ_TIMEOUT;

    private URI serviceURI;

    private List<ClientFilter> filters = new ArrayList<>();

    private SignatureKeyType defaultSignatureType = SignatureKeyType.INTERNAL_API;

    // the internal clientRequestHelper needs a InternalApiKeyGenerator to function. If one is not available,
    // the coordinatorClient will be used to create one. Else the coordinator client will be ignored in favor
    // of an InternalApiKeyGenerator bean already instantiated and maintained by the container
    private CoordinatorClient coordinatorClient;
    private InternalApiSignatureKeyGenerator keyGen;

    public URI getServiceURI() {
        return serviceURI;
    }

    public void setServiceURI(URI serviceURI) {
        this.serviceURI = serviceURI;
    }

    public CoordinatorClient getCoordinatorClient() {
        return coordinatorClient;
    }

    public void setCoordinatorClient(CoordinatorClient coordinatorClient) {
        this.coordinatorClient = coordinatorClient;
    }

    public void setKeyGenerator(InternalApiSignatureKeyGenerator keyGen) {
        this.keyGen = keyGen;
    }

    public void setClientReadTimeout(int clientReadTimeout) {
        this.clientReadTimeout = clientReadTimeout;
    }

    public void setClientConnectTimeout(int clientConnectTimeout) {
        this.clientConnectTimeout = clientConnectTimeout;
    }

    public void setClientMaxRetries(int clientMaxRetries) {
        this.clientMaxRetries = clientMaxRetries;
    }

    public int getClientMaxRetries() {
        return this.clientMaxRetries;
    }

    public void setClientRetryInterval(int clientRetryInterval) {
        this.clientRetryInterval = clientRetryInterval;
    }

    public int getClientRetryInterval() {
        return this.clientRetryInterval;
    }

    public SignatureKeyType getDefaultSignatureType() {
        return defaultSignatureType;
    }

    public void setDefaultSignatureType(SignatureKeyType defaultSignatureType) {
        this.defaultSignatureType = defaultSignatureType;
        if (clientRequestHelper != null) {
            clientRequestHelper.setDefaultSignatureType(defaultSignatureType);
        }
    }

    public void addFilter(ClientFilter filter) {
        filters.add(filter);
    }

    /**
     * Shut down this client and release associated resources
     */
    public void shutdown() {
        if (client != null) {
            client.destroy();
            client = null;
        }
    }

    /**
     * Wrapper for setServiceURI that builds out the full URI from a host or IP
     * 
     * @param server the host or IP address for the service we want to call
     */
    public abstract void setServer(String server);

    /**
     * Create a request object for the specified path, resolved against
     * the service base URI and using the appropriate client configuration
     * 
     * @param uriPath the path segment of the request (e.g. /vdc/storage-systems)
     * @return the request object
     */
    protected WebResource createRequest(String uriPath) {
        ensureInitialization();
        return clientRequestHelper.createRequest(client, serviceURI, URI.create(uriPath));
    }

    /**
     * Create a request object for the specified path, resolved against
     * the service base URI and using the appropriate client configuration
     * 
     * @param uriPath the path segment of the request (e.g. /vdc/storage-systems)
     * @return the request object
     */
    protected WebResource createRequest(URI uriPath) {
        ensureInitialization();
        return clientRequestHelper.createRequest(client, serviceURI, uriPath);
    }

    /**
     * Add the HMAC authentication headers to a request and return
     * a builder for additional manipulation
     * 
     * @param webResource the request to sign
     */
    protected Builder addSignature(WebResource webResource) {
        ensureInitialization();
        return clientRequestHelper.addSignature(webResource);
    }

    /**
     * Add the HMAC authentication headers to a request and return
     * a builder for additional manipulation.
     * This version accepts a supplied key to override default behavior
     * (which uses the internal api key)
     * 
     * @param webResource the request to sign
     * @param key secret key to compute the signature with
     */
    protected Builder addSignature(WebResource webResource, SecretKey key) {
        ensureInitialization();
        return clientRequestHelper.addSignature(webResource, key);
    }

    /**
     * Add a user token to the returned request builder
     * 
     * @param requestBuilder the request builder
     * @param token the user token
     * @return the builder with token added
     */
    protected Builder addToken(Builder requestBuilder, String token) {
        ensureInitialization();
        return clientRequestHelper.addTokens(requestBuilder, token, null);
    }

    /**
     * Add user and/or proxy tokens to the returned request builder
     * 
     * @param requestBuilder the request builder
     * @param token the user token (or null)
     * @param proxyToken the proxy token (or null)
     * @return the builder with token(s) added
     */
    protected Builder addTokens(Builder requestBuilder, String token, String proxyToken) {
        ensureInitialization();
        return clientRequestHelper.addTokens(requestBuilder, token, proxyToken);
    }

    private void ensureInitialization() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    if ((serviceURI == null) || (coordinatorClient == null && keyGen == null)) {
                        throw SecurityException.fatals.failedToInitializeClientRequestHelper(serviceURI == null ? "serviceURI was null"
                                : serviceURI.toString(),
                                (coordinatorClient == null && keyGen == null) ? "both coordinatorClient and keyGenerator were null"
                                        : "coordinatorClient or kenGenerator was provided");
                    } else {
                        if (keyGen == null) {
                            clientRequestHelper = new ClientRequestHelper(coordinatorClient, this.clientReadTimeout,
                                    this.clientConnectTimeout);
                        } else {
                            clientRequestHelper = new ClientRequestHelper(keyGen, this.clientReadTimeout, this.clientConnectTimeout);
                        }
                        clientRequestHelper.setDefaultSignatureType(defaultSignatureType);
                        // create and save a client for re-use, since it's an expensive object
                        // and concurrent request creation is thread-safe
                        client = clientRequestHelper.createClient();
                        if (filters.isEmpty()) {
                            client.addFilter(new ServiceClientRetryFilter(clientMaxRetries, clientRetryInterval));
                        }
                        else {
                            for (ClientFilter filter : filters) {
                                client.addFilter(filter);
                            }
                        }
                        initialized = true;
                    }
                }
            }
        }
    }
    
    @Override
    public void close() {
        this.shutdown();
    }
}
