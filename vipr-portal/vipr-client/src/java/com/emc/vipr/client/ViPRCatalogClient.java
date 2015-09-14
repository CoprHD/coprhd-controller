/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client;

import com.emc.vipr.client.catalog.*;
import com.emc.vipr.client.impl.RestClient;

/**
 * The Catalog APIs have been moved from the portal to the a back end ViPR service.
 * 
 * @deprecated Replaced by
 * @see ViPRCatalogClient2
 */
@Deprecated
public class ViPRCatalogClient {
    protected RestClient client;

    /**
     * Convenience method for calling constructor with new ClientConfig().withHost(host)
     * 
     * @param host Hostname or IP address for the Virtual IP of the target environment.
     */
    public ViPRCatalogClient(String host) {
        this(new ClientConfig().withHost(host));
    }

    /**
     * Convenience method for calling constructor with new ClientConfig().withHost(host).withIgnoringCertificates(ignoreCertificates)
     * 
     * @param host Hostname or IP address for the Virtual IP of the target environment.
     * @param ignoreCertificates True if SSL certificates should be ignored.
     */
    public ViPRCatalogClient(String host, boolean ignoreCertificates) {
        this(new ClientConfig().withHost(host).withIgnoringCertificates(ignoreCertificates));
    }

    public ViPRCatalogClient(ClientConfig config) {
        this.client = config.newPortalClient();
    }

    /**
     * Sets the authentication token to be used for this client.
     * 
     * @param authToken The authentication token to set.
     */
    public void setAuthToken(String authToken) {
        client.setAuthToken(authToken);
    }

    /**
     * Sets the authentication token and returns the updated client.
     * 
     * @see #setAuthToken(String)
     * @param token The authentication token to set.
     * @return The updated client.
     */
    public ViPRCatalogClient withAuthToken(String token) {
        setAuthToken(token);
        return this;
    }

    @Deprecated
    public Orders orders() {
        return new Orders(this, client);
    }

    @Deprecated
    public Approvals approvals() {
        return new Approvals(this, client);
    }

    @Deprecated
    public Catalog catalog() {
        return new Catalog(this, client);
    }

    @Deprecated
    public ExecutionWindows executionWindows() {
        return new ExecutionWindows(this, client);
    }

    @Deprecated
    public Setup setup() {
        return new Setup(client);
    }
}
