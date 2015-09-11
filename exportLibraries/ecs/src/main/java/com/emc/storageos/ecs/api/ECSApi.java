/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.ecs.api;

import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.MultivaluedMap;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final URI URI_CREATE_BUCKET = URI.create("/object/bucket.json");
    private final String ECS_BUCKET_UPDATE_BASE = "/object/bucket/";
    private static final String ROLE_SYSTEM_ADMIN = "<role>SYSTEM_ADMIN</role>";
    private static final String URI_UPDATE_BUCKET_RETENTION = "/object/bucket/{0}/retention.json";
    private static final String URI_UPDATE_BUCKET_QUOTA = "/object/bucket/{0}/quota.json";
    private static final String URI_DEACTIVATE_BUCKET = "/object/bucket/{0}/deactivate.json";
    private static final long  DAY_TO_SECONDS = 24*60*60;

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
                String vArrayId = null;
                URI uriEcsVarray = null;
                JSONObject objVarrayCap = null;
                String ecsVarray = null;

                // Get ECS vArray ID(=ECS StoragePool/cluster) and its capacity
                aryVarray = objRG.getJSONArray("varrayMappings");
                for (int j = 0; j < aryVarray.length(); j++) {
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
                }// for each ECS varray

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
            _log.error("discovery of Pools failed");
            String response = String.format("%1$s", (clientResp == null) ? "" : clientResp);
            String response2 = String.format("%1$s", (clientRespVarray == null) ? "" : clientRespVarray);
            response = response + response2;
            throw ECSException.exceptions.getStoragePoolsFailed(response, e);
        } finally {
            _log.error("discovery of Pools success");
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
     * 
     * @param name Name of bucket
     * @param namespace Namespace with this associated
     * @param repGroup ECS storage pool name
     * @param retentionPeriod retained value
     * @param blkSizeHQ blocking limit
     * @param notSizeSQ notification limit
     * @param owner owner of bucket
     * @return id (not used)
     * @throws ECSException
     */
    public String createBucket(String name, String namespace, String repGroup, String retentionPeriod, String blkSizeHQ,
            String notSizeSQ, String owner) throws ECSException {
        _log.info("ECSApi:createBucket enter");
        ClientResponse clientResp = null;
        String id = null;
        String body = " { \"name\": \"" + name + "\", " + "\"vpool\": \"" + repGroup + "\", \"namespace\": \"" + namespace + "\"}  ";

        try {
            _log.info("ECSApi:createBucket Create bucket base");
            clientResp = _client.post_json(_baseUrl.resolve(URI_CREATE_BUCKET), authToken, body);
            if (clientResp.getStatus() != 200) {
                if (clientResp.getStatus() == 401 || clientResp.getStatus() == 302) {
                    getAuthToken();
                    clientResp = _client.post_json(_baseUrl.resolve(URI_CREATE_BUCKET), authToken, body);
                }

                if (clientResp.getStatus() != 200) {
                    throw ECSException.exceptions.storageAccessFailed(_baseUrl.resolve(URI_CREATE_BUCKET), clientResp.getStatus(),
                            "creating base bucket");
                }
            }

            // update retention period
            if (retentionPeriod != null && !retentionPeriod.isEmpty()) {
                _log.info("ECSApi:createBucket update retention");
                ClientResponse clientRespRet = null;

                //Convert retention from days to seconds
                Long lRet = Long.parseLong(retentionPeriod);
                lRet = lRet * DAY_TO_SECONDS;

                String bodyRet = " { \"period\": \"" + lRet.toString() + "\", \"namespace\": \"" + namespace + "\"}  ";               

                // ECS_BUCKET_UPDATE_BASE
                String bucketRetention = ECS_BUCKET_UPDATE_BASE + name + "/retention.json";
                URI uriBucketRetention = URI.create(bucketRetention);

                clientRespRet = _client.put_json(_baseUrl.resolve(uriBucketRetention), authToken, bodyRet);
                if (clientRespRet.getStatus() != 200) {
                    if (clientRespRet.getStatus() == 401 || clientRespRet.getStatus() == 302) {
                        getAuthToken();
                        clientRespRet = _client.put_json(_baseUrl.resolve(uriBucketRetention), authToken, bodyRet);
                    }

                    if (clientRespRet.getStatus() != 200) {
                        throw ECSException.exceptions.storageAccessFailed(_baseUrl.resolve(uriBucketRetention),
                                clientRespRet.getStatus(), "add bucket retention");
                    }
                }

                if (clientRespRet != null) {
                    clientRespRet.close();
                }
            }// end retention period != null

            // update hard=block and soft=notification quota
            if (blkSizeHQ != null && notSizeSQ != null && !blkSizeHQ.isEmpty() && !notSizeSQ.isEmpty()) {
                _log.info("ECSApi:createBucket update hard and soft quota");
                ClientResponse clientRespQt = null;

                String bodyQt = " {  \"blockSize\": \"" + blkSizeHQ + "\", \"notificationSize\": \"" + notSizeSQ +
                        "\", \"namespace\": \"" + namespace + "\"}  ";

                // ECS_BUCKET_UPDATE_BASE
                String bucketQuota = ECS_BUCKET_UPDATE_BASE + name + "/quota.json";
                URI uriBucketQuota = URI.create(bucketQuota);

                clientRespQt = _client.put_json(_baseUrl.resolve(uriBucketQuota), authToken, bodyQt);
                if (clientRespQt.getStatus() != 200) {
                    if (clientRespQt.getStatus() == 401 || clientRespQt.getStatus() == 302) {
                        getAuthToken();
                        clientRespQt = _client.put_json(_baseUrl.resolve(uriBucketQuota), authToken, bodyQt);
                    }

                    if (clientRespQt.getStatus() != 200) {
                        throw ECSException.exceptions.storageAccessFailed(_baseUrl.resolve(uriBucketQuota),
                                clientRespQt.getStatus(), "add hard and soft quota");
                    }
                }

                if (clientRespQt != null) {
                    clientRespQt.close();
                }
            }// end update hard=block and soft=notification quota

            // update owner
            if (owner != null && !owner.isEmpty()) {
                _log.info("ECSApi:createBucket update owner");
                ClientResponse clientRespOnr = null;

                String bodyOnr = " { \"new_owner\": \"" + owner + "\", \"namespace\": \"" + namespace + "\"}  ";

                // ECS_BUCKET_UPDATE_BASE
                String bucketOwner = ECS_BUCKET_UPDATE_BASE + name + "/owner.json";
                URI uriBucketOwner = URI.create(bucketOwner);

                clientRespOnr = _client.post_json(_baseUrl.resolve(uriBucketOwner), authToken, bodyOnr);
                if (clientRespOnr.getStatus() != 200) {
                    if (clientRespOnr.getStatus() == 401 || clientRespOnr.getStatus() == 302) {
                        getAuthToken();
                        clientRespOnr = _client.post_json(_baseUrl.resolve(uriBucketOwner), authToken, bodyOnr);
                    }

                    if (clientRespOnr.getStatus() != 200) {
                        throw ECSException.exceptions.storageAccessFailed(_baseUrl.resolve(uriBucketOwner),
                                clientRespOnr.getStatus(), "add bucket owner");
                    }
                }

                if (clientRespOnr != null) {
                    clientRespOnr.close();
                }
            }// end update owner

            _log.info("ECSApi:createBucket leave");

            //extract bucket id
            JSONObject jObj = clientResp.getEntity(JSONObject.class);
            if (jObj.has("id")) {
                id = jObj.getString("id");
            }
            return id;
        } catch (ECSException ie) {
            _log.info("ECSApi:createBucket ECSException");
            throw ie;
        } catch (Exception e) {
            _log.info("ECSApi:createBucket Exception");
            String response = String.format("%1$s", (clientResp == null) ? "" : clientResp);
            throw ECSException.exceptions.createBucketFailed(response, e);
        } finally {
            _log.info("ECSApi:createBucket success");
            if (clientResp != null) {
                clientResp.close();
            }
        }

    }// end create bucket

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
        ClientResponse clientResp = null;

        if (null != namespace && null != bucketName) {
            if (null != softQuota || null != hardQuota) {
                String quotaUpdate = " { \"blockSize\": \"" + hardQuota + "\", \"notificationSize\": \"" + softQuota
                        + "\", \"namespace\": \"" + namespace + "\" }  ";
                final String path = MessageFormat.format(URI_UPDATE_BUCKET_QUOTA, bucketName);
                try {
                    clientResp = put(path, quotaUpdate);
                } catch (Exception e) {
                    _log.error("Error occured while Quota update for bucket : {}", bucketName, e);
                } finally {
                    if (null == clientResp || clientResp.getStatus() != 200) {
                        throw ECSException.exceptions.bucketUpdateFailed(bucketName, "Quota");
                    }
                    closeResponse(clientResp);
                }
            }
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
        ClientResponse clientResp = null;
        
        if (null != namespace && null != bucketName) {
            if (null != retention) {
                String retentionUpdate = " { \"period\": \"" + (retention*DAY_TO_SECONDS) + "\", \"namespace\": \"" + namespace + "\" }  ";
                final String path = MessageFormat.format(URI_UPDATE_BUCKET_RETENTION, bucketName);
                try {
                    clientResp = put(path, retentionUpdate);
                } catch (Exception e) {
                    _log.error("Error occured while Retention update for bucket : {}", bucketName, e);
                } finally {
                    if (null == clientResp || clientResp.getStatus() != 200) {
                        throw ECSException.exceptions.bucketUpdateFailed(bucketName, "Retention");
                    }
                    closeResponse(clientResp);
                }
            }
        }
    }

    /**
     * Delets a bucket on ECS Storage
     * 
     * @param bucketName Bucket name
     * @throws ECSException If error occurs during delete
     */
    public void deleteBucket(String bucketName) throws ECSException {
        ClientResponse clientResp = null;

        if (null != bucketName) {
            String deleteBody = " {  }  ";
            final String path = MessageFormat.format(URI_DEACTIVATE_BUCKET, bucketName);
            try {
                clientResp = post(path, deleteBody);
            } catch (Exception e) {
                _log.error("Error occured while delete of bucket : {}", bucketName, e);
            } finally {
                if (null == clientResp || clientResp.getStatus() != 200) {
                    throw ECSException.exceptions.bucketDeleteFailed(bucketName);
                }
                closeResponse(clientResp);
            }
        }
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
}