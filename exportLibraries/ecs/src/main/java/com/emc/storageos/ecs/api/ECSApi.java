package com.emc.storageos.ecs.api;

import java.net.URI;

import org.codehaus.jettison.json.JSONObject;

import com.sun.jersey.api.client.ClientResponse;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.*;


public class ECSApi {
    private final URI _baseUrl;
    private final RESTClient _client;

    private static final URI URI_LOGIN = URI.create("login");
    
    /**
     * Constructor for using http connections
     * 
     * @throws IsilonException
     */
    public ECSApi(URI endpoint, RESTClient client) {
        _baseUrl = endpoint;
        _client = client;
    }

    /**
     * Close client resources
     */
    public void close() {
        _client.close();
    }
    
    public String getAuthToken(StorageSystem storageSystem) throws ECSException {
    	String authToken = null;
 
    	ClientResponse clientResp = null;
    	clientResp = _client.get(_baseUrl.resolve(URI_LOGIN));
    	if (clientResp.getStatus() != 200) {
    		throw ECSException.exceptions.unableToConnect(_baseUrl);
    	}
    	
    	try {
    		JSONObject resp = clientResp.getEntity(JSONObject.class);
    	}finally {
    		if (clientResp != null) {
    			clientResp.close();
    		}

    		return authToken;
    	}
    }
}
