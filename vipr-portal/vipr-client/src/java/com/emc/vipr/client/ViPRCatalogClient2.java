/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client;

import java.net.URI;

import com.emc.storageos.model.tenant.TenantResponse;
import com.emc.storageos.model.user.UserInfo;
import com.emc.vipr.client.catalog.Approvals2;
import com.emc.vipr.client.catalog.AssetOptions;
import com.emc.vipr.client.catalog.CatalogCategories;
import com.emc.vipr.client.catalog.CatalogImages;
import com.emc.vipr.client.catalog.CatalogPreferences;
import com.emc.vipr.client.catalog.CatalogServices;
import com.emc.vipr.client.catalog.ExecutionWindows2;
import com.emc.vipr.client.catalog.Orders2;
import com.emc.vipr.client.catalog.ServiceDescriptors;
import com.emc.vipr.client.catalog.UserPreferences;
import com.emc.vipr.client.catalog.search.CatalogSearchBuilder;
import com.emc.vipr.client.impl.RestClient;

/**
 * New fully feature catalog api client.
 * 
 */
public class ViPRCatalogClient2 {

    protected RestClient client;

    public ViPRCatalogClient2(String host) {
        this(new ClientConfig().withHost(host));
    }

    public ViPRCatalogClient2(ClientConfig config) {
        this.client = config.newClient();
    }

    /**
     * Sets the authentication token to be used for this client.
     * 
     * @param authToken
     *            The authentication token to set.
     */
    public void setAuthToken(String authToken) {
        client.setAuthToken(authToken);
    }

    /**
     * Sets the proxy token to be used for this client.
     * 
     * @param proxyToken
     *            The authentication token to set.
     */
    public void setProxyToken(String proxyToken) {
        client.setProxyToken(proxyToken);
    }

    public AuthClient auth() {
        return new AuthClient(client);
    }

    /**
     * Performs an authentication login and returns the updated client.
     * 
     * @see AuthClient#login(String, String)
     * @param username
     *            The username.
     * @param password
     *            The password.
     * @return The updated client.
     */
    public ViPRCatalogClient2 withLogin(String username, String password) {
        auth().login(username, password);
        return this;
    }

    /**
     * Sets the authentication token and returns the updated client.
     * 
     * @see #setAuthToken(String)
     * @param token
     *            The authentication token to set.
     * @return The updated client.
     */
    public ViPRCatalogClient2 withAuthToken(String token) {
        setAuthToken(token);
        return this;
    }

    /**
     * Sets the proxy token and returns the updated client.
     * 
     * @see #setProxyToken(String)
     * @param token
     *            The proxy token to set.
     * @return The updated client.
     */
    public ViPRCatalogClient2 withProxyToken(String token) {
        setProxyToken(token);
        return this;
    }

    public TenantResponse getUserTenant() {
        TenantResponse tenant = client.get(TenantResponse.class, "/tenant");
        return tenant;
    }

    public URI getUserTenantId() {
        return getUserTenant().getTenant();
    }

    public UserInfo getUserInfo() {
        return client.get(UserInfo.class, "/user/whoami");
    }

    public Orders2 orders() {
        return new Orders2(this, client);
    }

    public CatalogCategories categories() {
        return new CatalogCategories(this, client);
    }

    public CatalogServices services() {
        return new CatalogServices(this, client);
    }

    public Approvals2 approvals() {
        return new Approvals2(this, client);
    }

    public ExecutionWindows2 executionWindows() {
        return new ExecutionWindows2(this, client);
    }

    public AssetOptions assetOptions() {
        return new AssetOptions(this, client);
    }

    public ServiceDescriptors serviceDescriptors() {
        return new ServiceDescriptors(this, client);
    }

    public CatalogImages images() {
        return new CatalogImages(this, client);
    }

    public CatalogPreferences catalogPreferences() {
        return new CatalogPreferences(this, client);
    }

    public UserPreferences userPreferences() {
        return new UserPreferences(this, client);
    }

    public CatalogSearchBuilder browse() {
        return browse(getUserTenantId());
    }

    public CatalogSearchBuilder browse(URI tenantId) {
        return new CatalogSearchBuilder(this, tenantId);
    }
}
