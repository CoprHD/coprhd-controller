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
    private static final URI URI_WHOAMI = URI.create("/user/whoami");
    private static final URI URI_STORAGE_POOL = URI.create("/vdc/data-service/vpools.json");
    private final String ECS_VARRAY_BASE = "/object/capacity/";

    private static final String ROLE_SYSTEM_ADMIN = "<role>SYSTEM_ADMIN</role>";
    
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
    		throw ECSException.exceptions.unableToConnect(_baseUrl, clientResp.getStatus());
    	}

    	MultivaluedMap<String,String> headers=clientResp.getHeaders();
    	authTokenList = headers.get("X-SDS-AUTH-TOKEN");
    	if (authTokenList.size() != 1) {
    		throw ECSException.exceptions.invalidReturnParameters(_baseUrl);
    	}
    	authToken = authTokenList.get(0);
    	return authToken;
    }
    
    public boolean isSystemAdmin() throws ECSException {
    	ClientResponse clientResp = null;
    	
    	clientResp = _client.get_json(_baseUrl.resolve(URI_WHOAMI), authToken);
    	if (clientResp.getStatus() != 200) {
    		if (clientResp.getStatus() == 401 || clientResp.getStatus() == 302) {
    			getAuthToken();
    			clientResp = _client.get_xml(_baseUrl.resolve(URI_WHOAMI), authToken);
    		}
    		
    		if (clientResp.getStatus() != 200) {
    			throw ECSException.exceptions.isSystemAdminFailed(_baseUrl, clientResp.getStatus());
    		}
    	}
    	
    	String respBody = clientResp.getEntity(String.class);
    	if (respBody.contains(ROLE_SYSTEM_ADMIN)) {
    		return true;
    	} else {
    		return false;
    	}
    }
    
    public List<ECSStoragePool> getStoragePools() throws ECSException {
    	ClientResponse clientResp = null;
    	
    	clientResp = _client.get_json(_baseUrl.resolve(URI_STORAGE_POOL), authToken);
    	if (clientResp.getStatus() != 200) {
    		if (clientResp.getStatus() == 401 || clientResp.getStatus() == 302) {
    			getAuthToken();
    			clientResp = _client.get_json(_baseUrl.resolve(URI_STORAGE_POOL), authToken);
    		}
    		
    		if (clientResp.getStatus() != 200) {
    			throw ECSException.exceptions.getStoragePoolsAccessFailed(_baseUrl, clientResp.getStatus());
    		}
    	}
    	
    	JSONObject objectRepGroup = null;
    	JSONArray arrayRepGroup = null;
		List<ECSStoragePool> ecsPools = new ArrayList<ECSStoragePool>();
		JSONObject objRG = null;
		JSONArray aryVarray = null;
		Long storagepoolTotalCapacity = 0L, storagepoolFreeCapacity = 0L;
		ClientResponse clientRespVarray = null;
		
    	try {
    		objectRepGroup = clientResp.getEntity(JSONObject.class);
    		arrayRepGroup = objectRepGroup.getJSONArray("data_service_vpool");
   		
    		//run thru every replication group
    		for(int i=0; i<arrayRepGroup.length(); i++) {
    			ECSStoragePool pool = new ECSStoragePool();
    			objRG = arrayRepGroup.getJSONObject(i);
    			
    			JSONObject objVarray = null;
    			String vArrayId = null;
    			URI uriEcsVarray = null;
    			JSONObject objVarrayCap = null;
    			String ecsVarray = null;
    			
    			//Get ECS vArray ID(=ECS StoragePool/cluster) and its capacity
    			aryVarray = objRG.getJSONArray("varrayMappings");
    			for(int j=0; j<aryVarray.length(); j++) {
    				objVarray = aryVarray.getJSONObject(j);
    				vArrayId = objVarray.getString("value");
    				
    				//get total and free capacity for this ECS vArray
    				ecsVarray = ECS_VARRAY_BASE + vArrayId + ".json";
    				uriEcsVarray = URI.create(ecsVarray);
    						
    				clientRespVarray = _client.get_json(_baseUrl.resolve(uriEcsVarray), authToken);
    		    	if (clientRespVarray.getStatus() != 200) {
    		    		if (clientRespVarray.getStatus() == 401 || clientRespVarray.getStatus() == 302) {
    		    			getAuthToken();
    		    			clientRespVarray = _client.get_json(_baseUrl.resolve(uriEcsVarray), authToken);
    		    		}
    		    		
    		    		if (clientRespVarray.getStatus() != 200) {
    		    			throw ECSException.exceptions.getStoragePoolsAccessFailed(_baseUrl, clientRespVarray.getStatus());
    		    		}
    		    	}
    		    	
    		    	objVarrayCap = clientRespVarray.getEntity(JSONObject.class);
    		    	storagepoolTotalCapacity += Integer.parseInt(objVarrayCap.getString("totalProvisioned_gb"));
    		    	storagepoolFreeCapacity += Integer.parseInt(objVarrayCap.getString("totalFree_gb"));
    			}//for each ECS varray

    			pool.setName(objRG.getString("name"));
    			pool.setId(objRG.getString("id"));
    			pool.setTotalCapacity(storagepoolTotalCapacity);
    			pool.setFreeCapacity(storagepoolFreeCapacity);
    			ecsPools.add(pool);
    			
    			if (clientRespVarray != null) {
    				clientRespVarray.close();
                }
    		}
    	} catch (Exception e) {
            String response = String.format("%1$s", (clientResp == null) ? "" : clientResp);
            String response2 = String.format("%1$s", (clientRespVarray == null) ? "" : clientRespVarray);
            response = response + response2;
            throw ECSException.exceptions.getStoragePoolsFailed(response, e);
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            if (clientRespVarray != null) {
            	clientRespVarray.close();
            }
        }

        return ecsPools;
    }


    public List<ECSStoragePort> getStoragePort(String name) throws ECSException {
    	List<ECSStoragePort> ecsPort = new ArrayList<ECSStoragePort>();
    	ECSStoragePort port = new ECSStoragePort();
    	port.setName(name);
    	port.setId(name);
    	port.setIpAddress(name);
    	ecsPort.add(port);
    	
    	return ecsPort;
    }
}
