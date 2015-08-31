package com.emc.storageos.ecs.api;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MultivaluedMap;

import org.apache.commons.io.IOUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.json.simple.parser.JSONParser;

import com.google.gson.Gson;
import com.sun.jersey.api.client.ClientResponse;

public class ECSApi {
    private final URI _baseUrl;
    private final RESTClient _client;
    private String authToken;

    private static final URI URI_LOGIN = URI.create("/login");
    private static final URI URI_STORAGE_POOL = URI.create("/vdc/data-service/vpools.json");
       
    /**
     * Constructor for using http connections
     * 
     * @throws ECSException
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
    
    public String getAuthToken() throws ECSException {
    	List<String> authTokenList = null;

    	ClientResponse clientResp = null;
    	clientResp = _client.get(_baseUrl.resolve(URI_LOGIN));
    	if (clientResp.getStatus() != 200) {
    		throw ECSException.exceptions.unableToConnect(_baseUrl);
    	}

    	MultivaluedMap<String,String> headers=clientResp.getHeaders();
    	authTokenList = headers.get("X-SDS-AUTH-TOKEN");

    	authToken = authTokenList.get(0);
    	return authToken;
    }
    
    public List<ECSStoragePool> getStoragePools() throws ECSException {
    	ClientResponse clientResp = null;
    	clientResp = _client.get(_baseUrl.resolve(URI_STORAGE_POOL), authToken);
    	if (clientResp.getStatus() != 200) {
    		throw ECSException.exceptions.unableToConnect(_baseUrl);
    	}
    	
    	JSONObject object = null;
    	JSONArray array = null;
		List<ECSStoragePool> pools = new ArrayList<ECSStoragePool>();
    	
    	try {
    		object = clientResp.getEntity(JSONObject.class);
    		array = object.getJSONArray("data_service_vpool");

    		int total = array.length();
    		JSONObject ob = null;
    		
    		for(int i=0; i<total; i++) {
    			ECSStoragePool ecsPool = new ECSStoragePool();
    			ob = array.getJSONObject(i);
    			ecsPool.setName(ob.getString("name"));
    			pools.add(ecsPool);
    		}

    	} catch(Exception e) {
    		int a = 0;
    		//throws JSONException;
    	} 	

        return pools;
    }
}
