/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.ecs.api;

import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.MultivaluedMap;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.ObjectNamespace;
import com.emc.storageos.services.util.SecurityUtils;
import com.google.gson.Gson;
import com.sun.jersey.api.client.ClientResponse;

/**
 * implementation of ECS specifics
 * 
 */
public class ECSApi {
    private Logger _log = LoggerFactory.getLogger(ECSApi.class);
    private final URI _baseUrl;
    private final RESTClient _client;
    private String authToken;

    private static final URI URI_LOGIN = URI.create("/login");
    private static final URI URI_WHOAMI = URI.create("/user/whoami");
    private static final URI URI_STORAGE_POOL = URI.create("/vdc/data-service/vpools.json");
    private final String ECS_VARRAY_BASE = "/object/capacity/";
    private static final String URI_CREATE_BUCKET = "/object/bucket.json";
    private static final String ROLE_SYSTEM_ADMIN = "<role>SYSTEM_ADMIN</role>";
    private static final String URI_UPDATE_BUCKET_RETENTION = "/object/bucket/{0}/retention.json";
    private static final String URI_UPDATE_BUCKET_QUOTA = "/object/bucket/{0}/quota.json";
    private static final String URI_UPDATE_BUCKET_OWNER = "/object/bucket/{0}/owner.json";
    private static final String URI_DEACTIVATE_BUCKET = "/object/bucket/{0}/deactivate.json?namespace={1}";
    private static final String URI_BUCKET_INFO = "/object/bucket/{0}/info.json?namespace={1}";
    private static final String URI_UPDATE_BUCKET_ACL = "/object/bucket/{0}/acl.json";    
    private static final String URI_GET_NAMESPACES = "/object/namespaces.json";
    private static final String URI_GET_NAMESPACE_DETAILS = "/object/namespaces/namespace/{0}.json";
    private static final long DAY_TO_SECONDS = 24 * 60 * 60;
    private static final long BYTES_TO_GB = 1024 * 1024 * 1024;

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

    /**
     * 
     * @return authentication token
     * @throws ECSException
     */
    public String getAuthToken() throws ECSException {
        _log.info("ECSApi:getAuthToken enter");
        List<String> authTokenList = null;
        ClientResponse clientResp = null;

        clientResp = _client.get(_baseUrl.resolve(URI_LOGIN));
        if (clientResp.getStatus() != 200) {
            throw ECSException.exceptions.unableToConnect(_baseUrl, clientResp.getStatus());
        }

        MultivaluedMap<String, String> headers = clientResp.getHeaders();
        authTokenList = headers.get("X-SDS-AUTH-TOKEN");
        if (authTokenList.size() != 1) {
            throw ECSException.exceptions.invalidReturnParameters(_baseUrl);
        }
        authToken = authTokenList.get(0);
        _log.info("ECSApi:getAuthToken leave");
        return authToken;
    }

    /**
     * 
     * @return user has sys admin priviledges or not
     * @throws ECSException
     */
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

    /**
     * Get storage pools.
     * 
     * @return storage pools
     * @throws ECSException
     */
    public List<ECSStoragePool> getStoragePools() throws ECSException {
        _log.info("ECSApi:getStoragePools enter");
        ClientResponse clientResp = null;

        clientResp = _client.get_json(_baseUrl.resolve(URI_STORAGE_POOL), authToken);
        if (clientResp.getStatus() != 200) {
            if (clientResp.getStatus() == 401 || clientResp.getStatus() == 302) {
                getAuthToken();
                clientResp = _client.get_json(_baseUrl.resolve(URI_STORAGE_POOL), authToken);
            }

            if (clientResp.getStatus() != 200) {
                throw ECSException.exceptions.storageAccessFailed(_baseUrl.resolve(URI_STORAGE_POOL), clientResp.getStatus(),
                        "getStoragePools");
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

            // run thru every replication group
            for (int i = 0; i < arrayRepGroup.length(); i++) {
                ECSStoragePool pool = new ECSStoragePool();
                objRG = arrayRepGroup.getJSONObject(i);

                JSONObject objVarray = null;
                String vArrayId = null, storagePoolVDC=null;
                URI uriEcsVarray = null;
                JSONObject objVarrayCap = null;
                String ecsVarray = null;

                // Get ECS vArray ID(=ECS StoragePool/cluster) and its capacity
                aryVarray = objRG.getJSONArray("varrayMappings");
                for (int j = 0; j < aryVarray.length(); j++) {
                	//Reset capacity variables to 0
                	storagepoolTotalCapacity = 0L; storagepoolFreeCapacity = 0L;
                    objVarray = aryVarray.getJSONObject(j);
                    vArrayId = objVarray.getString("value");

                    // get total and free capacity for this ECS vArray
                    ecsVarray = ECS_VARRAY_BASE + vArrayId + ".json";
                    uriEcsVarray = URI.create(ecsVarray);

                    clientRespVarray = _client.get_json(_baseUrl.resolve(uriEcsVarray), authToken);
                    if (clientRespVarray.getStatus() != 200) {
                        if (clientRespVarray.getStatus() == 401 || clientRespVarray.getStatus() == 302) {
                            getAuthToken();
                            clientRespVarray = _client.get_json(_baseUrl.resolve(uriEcsVarray), authToken);
                        }

                        if (clientRespVarray.getStatus() != 200) {
                            throw ECSException.exceptions.storageAccessFailed(_baseUrl.resolve(uriEcsVarray),
                                    clientRespVarray.getStatus(), "get ECS vArray");
                        }
                    }

                    objVarrayCap = clientRespVarray.getEntity(JSONObject.class);
                    storagepoolTotalCapacity += Integer.parseInt(objVarrayCap.getString("totalProvisioned_gb"));
                    storagepoolFreeCapacity += Integer.parseInt(objVarrayCap.getString("totalFree_gb"));
                    
                    //get storage pool VDC
                    storagePoolVDC = objVarray.getString("name");
                    pool.setStoragePoolVDC(storagePoolVDC);
                }// for each ECS varray

                pool.setName(objRG.getString("name"));
                pool.setId(objRG.getString("id"));
                pool.setTotalCapacity(storagepoolTotalCapacity);
                pool.setFreeCapacity(storagepoolFreeCapacity);
                pool.setTotalDataCenters();
                ecsPools.add(pool);

                if (clientRespVarray != null) {
                    clientRespVarray.close();
                }
            }
        } catch (Exception e) {
            _log.error("discovery of Pools failed");
            String response = String.format("%1$s", (clientResp == null) ? "" : clientResp);
            String response2 = String.format("%1$s", (clientRespVarray == null) ? "" : clientRespVarray);
            response = response + response2;
            throw ECSException.exceptions.getStoragePoolsFailed(response, e);
        } finally {
            _log.info("discovery of Pools finished");
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

    /**
     * 
     * @param name ipAddress of storage system
     * @return list of ECS ports
     * @throws ECSException
     */
    public List<ECSStoragePort> getStoragePort(String name) throws ECSException {
        List<ECSStoragePort> ecsPort = new ArrayList<ECSStoragePort>();
        ECSStoragePort port = new ECSStoragePort();
        port.setName(name);
        port.setId(name);
        port.setIpAddress(name);
        ecsPort.add(port);

        return ecsPort;
    }

    /**
     * Create a Base Bucket instance
     * 
     * @param bucketName Bucket name
     * @param namespace Namespace where Bucket should reside
     * @param repGroup Volume
     * @return Source ID of the Bucket created
     */
    public String createBucket(String bucketName, String namespace, String repGroup) {
        _log.debug("ECSApi:createBucket Create bucket initiated for : {}", bucketName);
        String id = null;
        ClientResponse clientResp = null;
        String body = " { \"name\": \"" + bucketName + "\", " + "\"vpool\": \"" + repGroup + "\", \"namespace\": \"" + namespace + "\"}  ";
        try {
            clientResp = post(URI_CREATE_BUCKET, body);
        } catch (Exception e) {
            _log.error("Error occured while bucket base creation : {}", bucketName, e);
        } finally {
            if (null == clientResp) {
                throw ECSException.exceptions.storageAccessFailed(_baseUrl.resolve(URI_CREATE_BUCKET), 500,
                        "no response from ECS");
            } else if (clientResp.getStatus() != 200) {
                String response = String.format("%1$s", (clientResp == null) ? "" : clientResp);
                throw ECSException.exceptions.storageAccessFailed(_baseUrl.resolve(URI_CREATE_BUCKET), clientResp.getStatus(), response);
            } else {
                // extract bucket id
                JSONObject jObj = clientResp.getEntity(JSONObject.class);
                if (jObj.has("id")) {
                    try {
                        id = jObj.getString("id");
                    } catch (JSONException e) {
                        throw ECSException.exceptions.storageAccessFailed(_baseUrl.resolve(URI_CREATE_BUCKET), clientResp.getStatus(),
                                "Unable to extract source ID of the bucket");
                    }
                }
            }
            closeResponse(clientResp);
        }
        return id;
    }

    /**
     * Update Bucket Owner information.
     * 
     * @param bucketName Bucket Name
     * @param namespace Namespace where bucket resides
     * @param owner Owner of the Bucket
     */
    public void updateBucketOwner(String bucketName, String namespace, String owner) {
        _log.debug("ECSApi:updateBucketOwner Update bucket initiated for : {}", bucketName);

        ClientResponse clientResp = null;
        String bodyOnr = " { \"new_owner\": \"" + owner + "\", \"namespace\": \"" + namespace + "\"}  ";

        final String path = MessageFormat.format(URI_UPDATE_BUCKET_OWNER, bucketName);
        try {
            clientResp = post(path, bodyOnr);
        } catch (Exception e) {
            _log.error("Error occured while Owner update for bucket : {}", bucketName, e);
        } finally {
            if (null == clientResp) {
                throw ECSException.exceptions.bucketUpdateFailed(bucketName, "Owner", "no response from ECS");
            } else if (clientResp.getStatus() != 200) {
                throw ECSException.exceptions.bucketUpdateFailed(bucketName, "Owner", getResponseDetails(clientResp));
            }
            closeResponse(clientResp);
        }
    }

    /**
     * Updates Bucket Quota
     * 
     * @param bucketName Bucket name
     * @param namespace Namespace of bucket
     * @param softQuota Notification Quota limit in GB
     * @param hardQuota Notification Quota limit in GB
     * @throws ECSException if Error occurs during update
     */
    public void updateBucketQuota(String bucketName, String namespace, Long softQuota, Long hardQuota) throws ECSException {
        _log.debug("ECSApi:updateBucketQuota Update bucket initiated for : {}", bucketName);

        ClientResponse clientResp = null;

        String quotaUpdate = " { \"blockSize\": \"" + hardQuota / BYTES_TO_GB + "\", \"notificationSize\": \"" + softQuota / BYTES_TO_GB
                + "\", \"namespace\": \"" + namespace + "\" }  ";
        final String path = MessageFormat.format(URI_UPDATE_BUCKET_QUOTA, bucketName);
        try {
            clientResp = put(path, quotaUpdate);
        } catch (Exception e) {
            _log.error("Error occured while Quota update for bucket : {}", bucketName, e);
        } finally {
            if (null == clientResp) {
                throw ECSException.exceptions.bucketUpdateFailed(bucketName, "Quota", "no response from ECS");
            } else if (clientResp.getStatus() != 200) {
                throw ECSException.exceptions.bucketUpdateFailed(bucketName, "Quota", getResponseDetails(clientResp));
            }
            closeResponse(clientResp);
        }
    }

    /**
     * Updates Retention value on a bucket
     * 
     * @param bucketName Bucket name
     * @param namespace Namespace
     * @param retention Retention in seconds
     * @throws ECSException If error occurs during update
     */
    public void updateBucketRetention(String bucketName, String namespace, Integer retention) throws ECSException {
        _log.debug("ECSApi:updateBucketRetention Update bucket initiated for : {}", bucketName);
        ClientResponse clientResp = null;

        String retentionUpdate = " { \"period\": \"" + (retention * DAY_TO_SECONDS) + "\", \"namespace\": \"" + namespace
                + "\" }  ";
        final String path = MessageFormat.format(URI_UPDATE_BUCKET_RETENTION, bucketName);
        try {
            clientResp = put(path, retentionUpdate);
        } catch (Exception e) {
            _log.error("Error occured while Retention update for bucket : {}", bucketName, e);
        } finally {
            if (null == clientResp) {
                throw ECSException.exceptions.bucketUpdateFailed(bucketName, "Quota", "no response from ECS");
            } else if (clientResp.getStatus() != 200) {
                throw ECSException.exceptions.bucketUpdateFailed(bucketName, "Retention", getResponseDetails(clientResp));
            }
            closeResponse(clientResp);

        }
    }
    
    /**
     * Updates the bucket ACL
     * 
     * @param bucketName
     * @param payload
     * @throws ECSException
     */
    public void updateBucketACL(String bucketName, String payload) throws ECSException {

        _log.debug("ECSApi:updateBucketACL Update bucket ACL initiated for : {}", bucketName);
        ClientResponse clientResp = null;
        final String path = MessageFormat.format(URI_UPDATE_BUCKET_ACL, bucketName);
        try {
            clientResp = put(path, payload);
            if (null == clientResp) {
                throw ECSException.exceptions.bucketACLUpdateFailed(bucketName, "no response from ECS");
            } else if (clientResp.getStatus() != 200) {
                throw ECSException.exceptions.bucketACLUpdateFailed(bucketName, getResponseDetails(clientResp));
            }
        } catch (Exception e) {
            _log.error("Error occured while ACL update for bucket : {}", bucketName, e);
            throw ECSException.exceptions.bucketACLUpdateFailed(bucketName, e.getMessage());
        } finally {
            if (clientResp != null) {
                closeResponse(clientResp);
            }

        }

    }

    /**
     * Deletes a bucket on ECS Storage
     * 
     * @param bucketName Bucket name
     * @param namespace Namespace where bucket resides
     * @throws ECSException If error occurs during delete
     */
    public void deleteBucket(String bucketName, String namespace) throws ECSException {
        ClientResponse clientResp = null;

        if (null != bucketName) {
            String deleteBody = " {  }  ";
            final String path = MessageFormat.format(URI_DEACTIVATE_BUCKET, bucketName, namespace);
            try {
                clientResp = post(path, deleteBody);
            } catch (Exception e) {
                _log.error("Error occured while delete of bucket : {}", bucketName, e);
            } finally {
                if (null == clientResp) {
                    throw ECSException.exceptions.bucketDeleteFailed(bucketName, "no response");
                } else if (clientResp.getStatus() != 200) {
                    throw ECSException.exceptions.bucketDeleteFailed(bucketName, getResponseDetails(clientResp));
                }
                closeResponse(clientResp);
            }
        }
    }
    
    /**
     * Get current owner of the bucket
     * 
     * @param bucketName 	Name of the bucket
     * @return				Owner of bucket
     * @throws ECSException If error occurs
     */
    public String getBucketOwner(String bucketName, String namespace) throws ECSException {
    	ClientResponse clientResp = null;
    	String owner = null;

    	final String path = MessageFormat.format(URI_BUCKET_INFO, bucketName, namespace);
    	try {
    		clientResp = get(path);
    		if (clientResp != null && clientResp.getStatus() == 200) {
    			owner = getFieldValue(clientResp, "owner");
    		}
    	} catch (Exception e) {
    		_log.error("Error occured while getting owner of bucket : {}", bucketName, e);
    	} finally {
    		if (clientResp == null) {
    			throw ECSException.exceptions.getBucketOwnerFailed(bucketName, "no response");
    		} else if (clientResp.getStatus() != 200) {
    			String resp = getResponseDetails(clientResp);
    			closeResponse(clientResp);
    			throw ECSException.exceptions.getBucketOwnerFailed(bucketName, resp);
    		}
    		closeResponse(clientResp);
    		return owner;
    	}
    }
    
    /**
     * Get the list of ECS namespace IDs
     * 
     * @return List of namespace strings
     * @throws ECSException
     */
    public List<String> getNamespaces() throws ECSException {
        _log.debug("ECSApi:getNamespace enter");
        ClientResponse clientResp = null;
        List<String> namespaceIdList = new ArrayList<String>();
        try {
            String responseString = null;
            clientResp = get(URI_GET_NAMESPACES);
            if (null == clientResp) {
                throw ECSException.exceptions.getNamespacesFailed("no response from ECS");
            } else if (clientResp.getStatus() != 200) {
                throw ECSException.exceptions.getNamespacesFailed(getResponseDetails(clientResp));
            }

            responseString = clientResp.getEntity(String.class);
            _log.info("ECSApi:getNamespace ECS response is {}", responseString);
            NamespaceCommandResult ecsNsResult = new Gson().fromJson(SecurityUtils.sanitizeJsonString(responseString),
                    NamespaceCommandResult.class);
            for (int index = 0; index < ecsNsResult.getNamespace().size(); index++) {
                namespaceIdList.add(ecsNsResult.getNamespace().get(index).getId());
            }
            return namespaceIdList;
        } catch (Exception e) {
            throw ECSException.exceptions.getNamespacesFailed(e);
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.debug("ECSApi:getNamespace exit");
        }
    }
    
    /**
     * Get the Bucket ACL
     * 
     * @return String response 
     * @throws ECSException
     */
    public String getBucketAclFromECS(String bucketName) throws ECSException {
        _log.debug("ECSApi:getBucketAclFromECS");
        ClientResponse clientResp = null;
       
        try {
            String responseString = null;
            clientResp = get(URI_UPDATE_BUCKET_ACL);
            if (null == clientResp) {
                throw ECSException.exceptions.bucketACLUpdateFailed(bucketName, "no response from ECS");
            } else if (clientResp.getStatus() != 200) {
                throw ECSException.exceptions.bucketACLUpdateFailed(bucketName, getResponseDetails(clientResp));
            }

            responseString = clientResp.getEntity(String.class);
            _log.info("ECSApi:getBucketAclFromECS response is {}", responseString);
           
            return responseString;
        } catch (Exception e) {
            throw ECSException.exceptions.getNamespacesFailed(e);
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.debug("ECSApi:getBucketAclFromECS exit");
        }
    }
    
    /**
     * 
     * @param Id of the namespace for which allowed/disallowed pools are required
     * @param replicaiton group type
     * @return List of allowed or disallowd rep groups or none
     * @throws ECSException
     */
    public ECSNamespaceRepGroup getNamespaceDetails(String namespaceId) throws ECSException {
        _log.debug("ECSApi:getNamespaceDetails enter");
        ClientResponse clientResp = null;
        ECSNamespaceRepGroup nsRepGroup = new ECSNamespaceRepGroup();
        try {
            String responseString = null;
            final String path = MessageFormat.format(URI_GET_NAMESPACE_DETAILS, namespaceId);
            clientResp = get(path);
            if (null == clientResp) {
                throw ECSException.exceptions.getNamespaceDetailsFailed(namespaceId, "no response from ECS");
            } else if (clientResp.getStatus() != 200) {
                throw ECSException.exceptions.getNamespaceDetailsFailed(namespaceId, getResponseDetails(clientResp));
            }

            responseString = clientResp.getEntity(String.class);
            _log.info("ECSApi:getNamespaceDetails for {} ECS response is {}", namespaceId, responseString);
            NamespaceDetailsCommandResult ecsNsResult = new Gson().fromJson(SecurityUtils.sanitizeJsonString(responseString),
                    NamespaceDetailsCommandResult.class);
            nsRepGroup.setNamespaceName(ecsNsResult.getName());
            nsRepGroup.setRgType(ObjectNamespace.OBJ_StoragePool_Type.NONE);

            if (!ecsNsResult.getAllowed_vpools_list().isEmpty()) {
                for (int index = 0; index < ecsNsResult.getAllowed_vpools_list().size(); index++) {
                    //Its possible to have replication group list blank
                    if (ecsNsResult.getAllowed_vpools_list().get(index) != null &&
                            !ecsNsResult.getAllowed_vpools_list().get(index).isEmpty())
                        nsRepGroup.addReplicationGroups(ecsNsResult.getAllowed_vpools_list().get(index));
                }
                nsRepGroup.setRgType(ObjectNamespace.OBJ_StoragePool_Type.ALLOWED);
            } else if (!ecsNsResult.getDisallowed_vpools_list().isEmpty()) {
                for (int index = 0; index < ecsNsResult.getDisallowed_vpools_list().size(); index++) {
                    //Its possible to have replication group list blank
                    if (ecsNsResult.getDisallowed_vpools_list().get(index) != null &&
                            !ecsNsResult.getDisallowed_vpools_list().get(index).isEmpty())
                        nsRepGroup.addReplicationGroups(ecsNsResult.getDisallowed_vpools_list().get(index));
                }
                nsRepGroup.setRgType(ObjectNamespace.OBJ_StoragePool_Type.DISALLOWED);
            }
            return nsRepGroup;
        } catch (Exception e) {
            throw ECSException.exceptions.getNamespaceDetailsFailed(namespaceId, e);
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.debug("ECSApi:getNamespaceDetails exit");
        }
    }

    
    
    
    private ClientResponse get(final String uri) {
        ClientResponse clientResp = _client.get_json(_baseUrl.resolve(uri), authToken);
        if (clientResp != null && clientResp.getStatus() == 401) {
            getAuthToken();
            clientResp = _client.get_json(_baseUrl.resolve(uri), authToken);
        }
        return clientResp;
    }
    
    private ClientResponse post(final String uri, final String body) {
        ClientResponse clientResp = _client.post_json(_baseUrl.resolve(uri), authToken, body);
        if (clientResp.getStatus() == 401) {
            getAuthToken();
            clientResp = _client.post_json(_baseUrl.resolve(uri), authToken, body);
        }
        return clientResp;
    }

    private ClientResponse put(final String uri, final String body) {
        ClientResponse clientResp = _client.put_json(_baseUrl.resolve(uri), authToken, body);
        if (clientResp.getStatus() == 401) {
            getAuthToken();
            clientResp = _client.put_json(_baseUrl.resolve(uri), authToken, body);
        }
        return clientResp;
    }

    private void closeResponse(ClientResponse clientResp) {
        if (null != clientResp) {
            clientResp.close();
        }
    }

    private String getResponseDetails(ClientResponse clientResp) {
        String detailedResponse = null;
        try {
            JSONObject jObj = clientResp.getEntity(JSONObject.class);
            detailedResponse = String.format("ECS Description:%s, Details:%s",
                    jObj.getString("description"), jObj.getString("details"));
            _log.error(String.format("HTTP error code: %d, Complete ECS error response: %s", clientResp.getStatus(),
                    jObj.toString()));
        } catch (Exception e) {
            _log.error("Unable to get ECS error details");
            detailedResponse = String.format("%1$s", (clientResp == null) ? "" : clientResp);
        }
        return detailedResponse;
    }
    
    private String getFieldValue(ClientResponse clientResp, String field) {
        String value = null;
        try {
            JSONObject jObj = clientResp.getEntity(JSONObject.class);
            value = jObj.getString(field); 
        } catch (Exception e) {
            _log.error("Unable to get field value: %s", field);
        }
        return value;
    }
}