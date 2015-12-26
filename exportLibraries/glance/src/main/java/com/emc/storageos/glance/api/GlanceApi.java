/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.glance.api;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.glance.GlanceConstants;
import com.emc.storageos.glance.GlanceEndPointInfo;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.emc.storageos.glance.rest.client.GlanceRESTClient;
import com.emc.storageos.glance.errorhandling.GlanceApiException;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;

/**
 * Glance API client
 */
public class GlanceApi {
    private Logger _log = LoggerFactory.getLogger(GlanceApi.class);
    private GlanceEndPointInfo endPoint;
    private GlanceRESTClient client;

    /**
     * Create Glance API client
     *
     * @param provider Storage Provider URI
     * @param client Jersey Client
     * @return
     */
    public GlanceApi (GlanceEndPointInfo endPointInfo, Client client) {
        endPoint = endPointInfo;
        this.client = new GlanceRESTClient(client, endPointInfo.getGlanceToken());
    }

	public GlanceRESTClient getClient() {
        return client;
    }    
 
	
	 /**
     * Gets Glance image by using passed token. 
     * 
     * @param String Token
     * @return image ClientResponse
     */
    
   public  ClientResponse getGlanceImage(String uri, String token) {
        Gson gson = new Gson();
        ClientResponse js_response=null;
        
        try{
        	client.setAuthTokenHeader(token);
            js_response = client.get(URI.create(uri));
            _log.debug("Inside getGlanceImage Response status {}",  String.valueOf(js_response.getStatus()));            
        }
        catch(Exception e){
        	_log.debug("Failed to get the glance image response");
        	throw GlanceApiException.exceptions.clientResponseGetFailure(js_response.toString());
        } /* catch */
             
        return js_response;
    }
    
}

//How To Use Glance API

/*
		//GlanceEndPointInfo(String hostName, String userName,String password, String tenantName)
		GlanceEndPointInfo ep = new GlanceEndPointInfo("10.247.39.185","admin", "ChangeMe", "tenant1");
		
		//initialise Glance Api
		ClientConfig config = new DefaultClientConfig();
		Client jerseyClient = Client.create(config);
		GlanceApi glanceApi = new GlanceApi(ep, jerseyClient);

        //initialise Glance Base URI
		String restBaseUri = "http://10.247.39.185:9292/v1/images/bac0e68b-0736-4b18-b4dc-198b8077520f";

		
		if(restBaseUri.startsWith(GlanceConstants.HTTP_URL)) 
        {
        	ep.setGlanceBaseUriHttp(restBaseUri);
        }
        else 
        {
        	ep.setGlanceBaseUriHttps(restBaseUri); // for HTTPS
        }
        
        //Pass Authentication Token
		ep.setGlanceToken(Token); 
		
	   //Get glance response
       ClientResponse js_response =  glanceApi.getGlanceImage(restBaseUri, Token);
*/