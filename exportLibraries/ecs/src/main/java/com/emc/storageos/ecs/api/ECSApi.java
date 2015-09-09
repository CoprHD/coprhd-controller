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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.sun.jersey.api.client.ClientResponse;

public class ECSApi {
	private Logger _log = LoggerFactory.getLogger(ECSApi.class);
    private final URI _baseUrl;
    private final RESTClient _client;
    private String authToken;

    private static final URI URI_LOGIN = URI.create("/login");
    private static final URI URI_WHOAMI = URI.create("/user/whoami");
    private static final URI URI_STORAGE_POOL = URI.create("/vdc/data-service/vpools.json");
    private final String ECS_VARRAY_BASE = "/object/capacity/";
    private static final URI URI_CREATE_BUCKET = URI.create("/object/bucket.json");
    private final String ECS_BUCKET_UPDATE_BASE = "/object/bucket/";
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
    	_log.info("ECSApi:getAuthToken enter");
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
    	_log.info("ECSApi:getAuthToken leave");
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
    	_log.info("ECSApi:getStoragePools enter--");
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

    	_log.info("ECSApi:getStoragePools leave");
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
    
    
    public String createBucket(String name, String namespace, String repGroup, String retentionPeriod, String blkSizeHQ, String notSizeSQ, String owner) throws ECSException {
    	_log.info("ECSApi:createBucket enter");
    	ClientResponse clientResp = null;
    	String id = null;
    	String body = " { \"name\": \""+ name + "\", " + "\"vpool\": \"" + repGroup +  "\", \"namespace\": \"" + namespace + "\"}  ";

    	try {
    		_log.info("ECSApi:createBucket URI_CREATE_BUCKET");
    		clientResp = _client.post_json(_baseUrl.resolve(URI_CREATE_BUCKET), authToken, body);
    		if (clientResp.getStatus() != 200) {
    			//if (clientResp.getStatus() == 401 || clientResp.getStatus() == 302) {
    				_log.info("getting new auth token " + clientResp.getStatus());
    				getAuthToken();
    				clientResp = _client.post_json(_baseUrl.resolve(URI_CREATE_BUCKET), authToken, body);
    			//}

    			if (clientResp.getStatus() != 200) {
    				JSONObject jObj = clientResp.getEntity(JSONObject.class);
    				_log.info("ECSApi:createBucket resp error " + jObj.toString() + " " + _baseUrl.resolve(URI_CREATE_BUCKET).toString() + " " + body 
    						+ "  " + authToken);
    				throw ECSException.exceptions.getStoragePoolsAccessFailed(_baseUrl, clientResp.getStatus());
    			}
    		}
    		
    		//working
			JSONObject jObj = clientResp.getEntity(JSONObject.class);
			_log.info("ECSApi:createBucket working " + jObj.toString() + " " + _baseUrl.resolve(URI_CREATE_BUCKET).toString() + " " + body 
					+ "  " + authToken);


    		//extract bucket id
//    		JSONObject jObj = clientResp.getEntity(JSONObject.class);
//    		if (jObj.has("id")) {
//    			id = jObj.getString("id");
//    		}

    		//update retention period
    		if (retentionPeriod != null) {
    			_log.info("ECSApi:createBucket update retention");
    			ClientResponse clientResp2 = null;

    			String body2 = " { \"period\": \""+ retentionPeriod + "\", \"namespace\": \"" + namespace + "\"}  ";

    			//ECS_BUCKET_UPDATE_BASE
    			String bucketRetention = ECS_BUCKET_UPDATE_BASE + name + "/retention.json";
    			URI uriBucketRetention = URI.create(bucketRetention);

    			clientResp2 = _client.put_json(_baseUrl.resolve(uriBucketRetention), authToken, body2);
    			if (clientResp2.getStatus() != 200) {
    				//if (clientResp2.getStatus() == 401 || clientResp2.getStatus() == 302) {
    				_log.info("getting new auth token " + clientResp2.getStatus());
    					_log.info("getting new auth token");
    					getAuthToken();
    					clientResp2 = _client.put_json(_baseUrl.resolve(uriBucketRetention), authToken, body2);
    				//}

    				if (clientResp2.getStatus() != 200) {
    					JSONObject jObj2 = clientResp2.getEntity(JSONObject.class);
    					_log.info("ECSApi:createBucket resp error " + jObj2.toString());
    					throw ECSException.exceptions.getStoragePoolsAccessFailed(_baseUrl, clientResp2.getStatus());
    				}
    			}

    			if (clientResp2 != null) {
    				clientResp2.close();
    			}
    		}//end retention period != null


    		//update hard=block and soft=notification quota
    		if (blkSizeHQ != null && notSizeSQ != null) {
    			_log.info("ECSApi:createBucket update hard and soft quota");
    			ClientResponse clientResp3 = null;

    			String body3 = " {  \"blockSize\": \""+ blkSizeHQ + "\", \"notificationSize\": \""+ notSizeSQ +
    					  "\", \"namespace\": \"" + namespace + "\"}  ";
    			
    			//ECS_BUCKET_UPDATE_BASE
    			String bucketQuota = ECS_BUCKET_UPDATE_BASE + name + "/quota.json";
    			URI uriBucketQuota = URI.create(bucketQuota);

    			clientResp3 = _client.put_json(_baseUrl.resolve(uriBucketQuota), authToken, body3);
    			if (clientResp3.getStatus() != 200) {
    				//if (clientResp3.getStatus() == 401 || clientResp3.getStatus() == 302) {
    				_log.info("getting new auth token " + clientResp3.getStatus());
    					getAuthToken();
    					clientResp3 = _client.put_json(_baseUrl.resolve(uriBucketQuota), authToken, body3);
    				//}

    				if (clientResp3.getStatus() != 200) {
    					JSONObject jObj3 = clientResp3.getEntity(JSONObject.class);
    					_log.info("ECSApi:createBucket resp error " + jObj3.toString());
    					throw ECSException.exceptions.getStoragePoolsAccessFailed(_baseUrl, clientResp3.getStatus());
    				}
    			}

    			if (clientResp3 != null) {
    				clientResp3.close();
    			}
    		}//end update hard=block and soft=notification quota

    		//update owner
    		if (owner != null) {
    			_log.info("ECSApi:createBucket update owner");
    			ClientResponse clientResp4 = null;

    			String body4 = " { \"new_owner\": \""+ owner + "\", \"namespace\": \"" + namespace + "\"}  ";
    			
    			//ECS_BUCKET_UPDATE_BASE
    			String bucketOwner = ECS_BUCKET_UPDATE_BASE + name + "/owner.json";
    			URI uriBucketOwner = URI.create(bucketOwner);

    			clientResp4 = _client.post_json(_baseUrl.resolve(uriBucketOwner), authToken, body4);
    			if (clientResp4.getStatus() != 200) {
    				//if (clientResp4.getStatus() == 401 || clientResp4.getStatus() == 302) {
    				_log.info("getting new auth token " + clientResp4.getStatus());
    					getAuthToken();
    					clientResp4 = _client.post_json(_baseUrl.resolve(uriBucketOwner), authToken, body4);
    				//}

    				if (clientResp4.getStatus() != 200) {
    					JSONObject jObj4 = clientResp4.getEntity(JSONObject.class);
    					_log.info("ECSApi:createBucket resp error " + jObj4.toString());
    					throw ECSException.exceptions.getStoragePoolsAccessFailed(_baseUrl, clientResp4.getStatus());
    				}
    			}

    			if (clientResp4 != null) {
    				clientResp4.close();
    			}
    		}//end update owner
    	
    		_log.info("ECSApi:createBucket leave");
    		return id;
    	} catch (ECSException ie) {
    		_log.info("ECSApi:createBucket ECSException");
    		throw ie;
    	} catch (Exception e) {
    		_log.info("ECSApi:createBucket Exception");
    		String response = String.format("%1$s", (clientResp == null) ? "" : clientResp);
    		throw ECSException.exceptions.createBucketFailed(response, e);
    	} finally {
    		_log.info("ECSApi:createBucket leave2");
    		if (clientResp != null) {
    			clientResp.close();
    		}
    	}

    }//end create bucket
}
