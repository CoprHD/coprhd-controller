/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax.restapi;

import java.net.URI;
import java.net.URISyntaxException;

import com.emc.storageos.services.restutil.RestClientFactory;
import com.emc.storageos.services.restutil.RestClientItf;
import com.emc.storageos.vmax.VMAXRestUtils;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.client.apache.ApacheHttpClient;

public class VMAXApiClientFactory extends RestClientFactory {

    private static VMAXApiClientFactory singleton = new VMAXApiClientFactory();
    private static boolean isInitiated = false;

    private VMAXApiClientFactory() {

    }

    public static VMAXApiClientFactory getInstance() {
        return singleton;
    }

    @Override
    public void init() {
        if (!isInitiated) {
            synchronized (this) {
                super.init();
                isInitiated = true;
            }
        }
    }
    
    @Override
    public RestClientItf getRESTClient(URI endpoint, String username, String password, boolean authFilter) {
        // removed caching RestClient session as it is not actually a session, just a java RestClient object
        // RestClientItf clientApi = _clientMap.get(endpoint.toString() + ":" + username + ":" + password + ":" + model);

        Client jerseyClient = new ApacheHttpClient(_clientHandler);
        if (authFilter) {
            jerseyClient.addFilter(new HTTPBasicAuthFilter(username, password));
        }
        RestClientItf clientApi = createNewRestClient(endpoint, username, password, jerseyClient);
        return clientApi;
    }

    @Override
    protected RestClientItf createNewRestClient(URI endpoint, String username, String password, Client client) {
        return new VMAXApiClient(endpoint, username, password, client);
    }

    public VMAXApiClient getClient(String ipAddress, int port, boolean useSSL, String username, String password) throws URISyntaxException {
        return (VMAXApiClient) getRESTClient(VMAXRestUtils.getUnisphereRestServerInfo(ipAddress, port, useSSL), username, password, true);
    }
}
