/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.glance.api;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//import com.emc.storageos.glance.model.AccessWrapper;
import com.emc.storageos.glance.model.AuthTokenRequest;
import com.emc.storageos.glance.GlanceConstants;
import com.emc.storageos.glance.GlanceEndPointInfo;
import com.emc.storageos.glance.model.AuthTokenRequest;
import com.emc.storageos.glance.model.AccessWrapper;
/*import com.emc.storageos.cinder.errorhandling.CinderException;


import com.emc.storageos.cinder.model.SnapshotCreateRequest;
import com.emc.storageos.cinder.model.SnapshotCreateResponse;
import com.emc.storageos.cinder.model.SnapshotListResponse;
import com.emc.storageos.cinder.model.VolumeAttachRequest;
import com.emc.storageos.cinder.model.VolumeAttachResponse;
import com.emc.storageos.cinder.model.VolumeAttachResponseAlt;
import com.emc.storageos.cinder.model.VolumeCreateRequest;
import com.emc.storageos.cinder.model.VolumeCreateResponse;
import com.emc.storageos.cinder.model.VolumeDetachRequest;
import com.emc.storageos.cinder.model.VolumeExpandRequest;
import com.emc.storageos.cinder.model.VolumeShowResponse;
import com.emc.storageos.cinder.model.VolumeTypes; */
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.emc.storageos.glance.rest.client.GlanceRESTClient;
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
     * Gets token from Keystone. It is a synchronous operation.
     * 
     * @param uri String
     * @return token String
     */
    public  String getGlanceImageOld(String uri) {
    	
        String token="";
        String tenantId = "";
        Gson gson = new Gson();
        
        AuthTokenRequest w = new AuthTokenRequest();
        w.auth.passwordCredentials.username = endPoint.getGlanceRESTuserName();
        w.auth.passwordCredentials.password = endPoint.getGlanceRESTPassword();
        w.auth.tenantName = endPoint.getGlanceTenantName();
        String json = gson.toJson(w);

        try{
            ClientResponse js_response = client.post(URI.create(uri), json);
            String s = js_response.getEntity(String.class);
            AccessWrapper wrapper = gson.fromJson(s, AccessWrapper.class);
            token = wrapper.access.token.id;
            tenantId = wrapper.access.token.tenant.id;
        }
        catch(Exception e){
        	_log.error("Exception!! \n Now trying String processing....\n");
            ClientResponse js_response = client.post(URI.create(uri), json);
            String s = js_response.getEntity(String.class);

            token = getTokenId(s);
            tenantId = getTenantId(s);
            
        } /* catch */
        
        //Update it in endPoint instance for every fetch/re-fetch
       endPoint.setGlanceToken(token);
       getClient().setAuthTokenHeader(token);
       endPoint.setGlanceTenantId(tenantId);
       
        return token;
    }
    
   public  ClientResponse getGlanceImage(String uri, String token) {
        Gson gson = new Gson();
        ClientResponse js_response=null;
        
        try{
        	client.setAuthTokenHeader(token);
            js_response = client.get(URI.create(uri));
           
            //response = js_response.getEntity(String.class);
            //AccessWrapper wrapper = gson.fromJson(s, AccessWrapper.class);
            
        }
        catch(Exception e){
        	_log.error("Exception!! \n Now trying String processing....\n");
            
        } /* catch */
             
        return js_response;
    }

    
    
    private String getTokenId(String data) {
    	return getId("token", data);
    }
    
    private String getTenantId(String data) {
    	return getId("tenant", data);
    }
    
    private String getId(String queryStr, String data) {
    	
    	String id = "";
    	 int index = data.indexOf(queryStr);

         if (index > -1) {
             // contains the token
             String token_str = data.substring(index);
             int id_index = token_str.indexOf("id");

             if (id_index > -1) {
                 // contains the id
                 String id_str = token_str.substring(id_index);

                 String[] splits = id_str.split("\"");
                 if (splits.length >= 2) {
                	 id = splits[2].trim();
                 }
             }
         }   
         
         return id;
    }

	/*public static void main(String args[]) throws Exception {
		GlanceEndPointInfo ep = new GlanceEndPointInfo("10.247.39.185",
				"glance", "password", "service");
		String restBaseUri = "http://10.247.39.185:5000/v2.0";

		if (restBaseUri.startsWith(GlanceConstants.HTTP_URL)) {
			ep.setGlanceBaseUriHttp(restBaseUri);
		} else {
			ep.setGlanceBaseUriHttps(restBaseUri);
		}

		ClientConfig config = new DefaultClientConfig();
		Client jerseyClient = Client.create(config);
		GlanceApi glanceApi = new GlanceApi(ep, jerseyClient);

		String authToken = glanceApi.getGlanceImage("http://l10.247.39.185:5000/v2.0/tokens");
		System.out.println("Auth token is " + authToken);


	} */ 
    
}

