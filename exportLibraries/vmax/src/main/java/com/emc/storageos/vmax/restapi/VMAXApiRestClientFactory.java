/*
 * Copyright (c) 2017 DELL EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax.restapi;

import java.net.URI;

import com.emc.storageos.services.restutil.RestClientFactory;
import com.emc.storageos.services.restutil.RestClientItf;
import com.emc.storageos.vmax.VMAXConstants;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.client.apache.ApacheHttpClient;
import com.sun.jersey.client.apache.ApacheHttpClientHandler;

public class VMAXApiRestClientFactory extends RestClientFactory{
	
	private static VMAXApiRestClientFactory singleton = new VMAXApiRestClientFactory();
	private static boolean isInitiated = false;
	
	private VMAXApiRestClientFactory(){
		
	}
	
	public static VMAXApiRestClientFactory getInstance(){
		return singleton;
	}
	
	@Override
    public void init() {
        if (!isInitiated) {
            synchronized(this) {
                super.init();
                isInitiated = true;
            }
        }
    }
    
    
    public RestClientItf getRESTClient(URI endpoint, String username, String password, String version, boolean authFilter) {
        // removed caching RestClient session as it is not actually a session, just a java RestClient object
        // RestClientItf clientApi = _clientMap.get(endpoint.toString() + ":" + username + ":" + password + ":" + model);

        Client jerseyClient = new ApacheHttpClient(_clientHandler);
        if (authFilter) {
            jerseyClient.addFilter(new HTTPBasicAuthFilter(username, password));
        }
        RestClientItf clientApi = createNewRestClient(endpoint, username, password, version, jerseyClient);
        return clientApi;
    }
    
    protected RestClientItf createNewRestClient(URI endpoint, String username,
            String password, String version, Client client) {
    	VMAXApiClient apiClient = new VMAXApiClient(endpoint, username, password, client);
    	return apiClient;
    }

    
    
    public RestClientItf getClient(String ipAddress, int port, boolean isSSL, String username, String password) {
        return getRESTClient(URI.create(VMAXConstants.getBaseURI(ipAddress, port, isSSL)), username, password, true);
    }

	@Override
	protected RestClientItf createNewRestClient(URI endpoint, String username, String password, Client client) {
		return new VMAXApiClient(endpoint, username, password, client);
	}

}
