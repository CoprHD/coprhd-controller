/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hds.api;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpStatus;
import org.milyn.payload.JavaResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.hds.HDSConstants;
import com.emc.storageos.hds.HDSException;
import com.emc.storageos.hds.model.Condition;
import com.emc.storageos.hds.model.EchoCommand;
import com.emc.storageos.hds.model.Error;
import com.emc.storageos.hds.model.Get;
import com.emc.storageos.hds.model.Pool;
import com.emc.storageos.hds.model.ServerInfo;
import com.emc.storageos.hds.model.StorageArray;
import com.emc.storageos.hds.util.SmooksUtil;
import com.emc.storageos.hds.xmlgen.InputXMLGenerationClient;
import com.sun.jersey.api.client.ClientResponse;

/**
 * This volume manager is responsible creating volumes/delete volumes.
 * 
 */
public class HDSApiDiscoveryManager {

    /**
     * Logger instance to log messages.
     */
    private static final Logger log = LoggerFactory.getLogger(HDSApiDiscoveryManager.class);

    private HDSApiClient hdsApiClient;

    public HDSApiDiscoveryManager(HDSApiClient hdsApiClient) {
        this.hdsApiClient = hdsApiClient;
    }

    /**
     * Returns all storage system information.
     * 
     * @return
     * @throws Exception
     */
    public List<StorageArray> getStorageSystemsInfo() throws Exception {
        InputStream responseStream = null;
        List<StorageArray> arrayList = null;
        try {
            URI endpointURI = hdsApiClient.getBaseURI();
            Map<String, Object> attributeMap = new HashMap<String, Object>();
            StorageArray storageArray = new StorageArray(HDSConstants.STAR);
            Get getOp = new Get(HDSConstants.STORAGEARRAY);
            attributeMap.put(HDSConstants.STORAGEARRAY, storageArray);
            attributeMap.put(HDSConstants.GET, getOp);

            String getSystemsInputXML = InputXMLGenerationClient.getInputXMLString(
                    HDSConstants.GET_SYSTEMS_INFO_OP, attributeMap,
                    HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                    HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

            log.info("Get all systems query payload :{}", getSystemsInputXML);
            ClientResponse response = hdsApiClient.post(endpointURI, getSystemsInputXML);
            if (HttpStatus.SC_OK == response.getStatus()) {
                responseStream = response.getEntityInputStream();
                JavaResult result = SmooksUtil.getParsedXMLJavaResult(responseStream, HDSConstants.SMOOKS_CONFIG_FILE);
                verifyErrorPayload(result);
                arrayList = (List<StorageArray>) result.getBean(HDSConstants.SYSTEMLIST_BEAN_ID);
            } else {
                log.error("Get all systems query failed with invalid response code {}",
                        response.getStatus());
                throw HDSException.exceptions
                        .invalidResponseFromHDS(String
                                .format("Not able to query all systems due to invalid response %1$s from server",
                                        response.getStatus()));
            }
        } finally {
            if (null != responseStream) {
                try {
                    responseStream.close();
                } catch (IOException e) {
                    log.warn("IOException occurred while closing the response stream");
                }
            }
        }
        return arrayList;
    }

    /**
     * Returns HiCommand Device Manager's API version
     * 
     * @return api version
     * @throws Exception
     */
    public String getProviderAPIVersion() throws Exception {
        String apiVersion = null;
        InputStream responseStream = null;
        URI endpointURI = hdsApiClient.getBaseURI();
        Map<String, Object> attributeMap = new HashMap<String, Object>();
        Get getOp = new Get(HDSConstants.SERVER_INFO);
        attributeMap.put(HDSConstants.GET, getOp);

        String getAPIVersionInputXML = InputXMLGenerationClient.getInputXMLString(
                HDSConstants.GET_API_VERSION_INFO_OP, attributeMap,
                HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

        log.info("Get api version query payload :{}", getAPIVersionInputXML);

        ClientResponse response = hdsApiClient.post(endpointURI, getAPIVersionInputXML);
        if (HttpStatus.SC_OK == response.getStatus()) {
            responseStream = response.getEntityInputStream();
            JavaResult result = SmooksUtil.getParsedXMLJavaResult(responseStream, HDSConstants.SMOOKS_CONFIG_FILE);
            verifyErrorPayload(result);
            apiVersion = result.getBean(ServerInfo.class).getApiVersion();
            log.info("HiCommand Device Manager's API Version :{}", apiVersion);
        } else {
            log.error("Get api version query failed with invalid response code {}",
                    response.getStatus());
            throw HDSException.exceptions
                    .invalidResponseFromHDS(String
                            .format("Not able to query api version due to invalid response %1$s from server",
                                    response.getStatus()));
        }
        return apiVersion;
    }

    /**
     * Returns all storage system information.
     * 
     * @return
     * @throws Exception
     */
    public StorageArray getStorageSystemDetails(String systemObjectID) throws Exception {
        InputStream responseStream = null;
        StorageArray storageArray = null;
        URI endpointURI = hdsApiClient.getBaseURI();
        Map<String, Object> attributeMap = new HashMap<String, Object>();
        StorageArray inputStorageArray = new StorageArray(systemObjectID);
        Get getOp = new Get(HDSConstants.STORAGEARRAY);
        attributeMap.put(HDSConstants.STORAGEARRAY, inputStorageArray);
        attributeMap.put(HDSConstants.GET, getOp);

        String getSystemDetailsInputXML = InputXMLGenerationClient.getInputXMLString(
                HDSConstants.GET_SYSTEM_DETAILS_OP, attributeMap,
                HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

        log.info("Get system details query payload :{}", getSystemDetailsInputXML);
        ClientResponse response = hdsApiClient.post(endpointURI, getSystemDetailsInputXML);
        if (HttpStatus.SC_OK == response.getStatus()) {
            responseStream = response.getEntityInputStream();
            JavaResult result = SmooksUtil.getParsedXMLJavaResult(responseStream, HDSConstants.SMOOKS_CONFIG_FILE);
            verifyErrorPayload(result);
            storageArray = result.getBean(StorageArray.class);
        } else {
            log.error("Get system details failed with invalid response code {}",
                    response.getStatus());
            throw HDSException.exceptions
                    .invalidResponseFromHDS(String
                            .format("Not able to query system details due to invalid response %1$s from server",
                                    response.getStatus()));
        }
        return storageArray;
    }

    /**
     * Returns all storage system information.
     * 
     * @return
     * @throws Exception
     */
    public StorageArray getStorageSystemTieringPolicyDetails(String systemObjectID) throws Exception {
        InputStream responseStream = null;
        StorageArray storageArray = null;
        URI endpointURI = hdsApiClient.getBaseURI();
        Map<String, Object> attributeMap = new HashMap<String, Object>();
        StorageArray inputStorageArray = new StorageArray(systemObjectID);
        Get getOp = new Get(HDSConstants.STORAGEARRAY);
        attributeMap.put(HDSConstants.STORAGEARRAY, inputStorageArray);
        attributeMap.put(HDSConstants.GET, getOp);

        String getSystemDetailsInputXML = InputXMLGenerationClient.getInputXMLString(
                HDSConstants.GET_SYSTEM_TIERING_DETAILS_OP, attributeMap,
                HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

        log.info("Get system Tiering details query payload :{}", getSystemDetailsInputXML);
        ClientResponse response = hdsApiClient.post(endpointURI, getSystemDetailsInputXML);
        if (HttpStatus.SC_OK == response.getStatus()) {
            responseStream = response.getEntityInputStream();
            JavaResult result = SmooksUtil.getParsedXMLJavaResult(responseStream, HDSConstants.SMOOKS_CONFIG_FILE);
            verifyErrorPayload(result);
            storageArray = result.getBean(StorageArray.class);
        } else {
            log.error("Get system details failed with invalid response code {}",
                    response.getStatus());
            throw HDSException.exceptions
                    .invalidResponseFromHDS(String
                            .format("Not able to query system details due to invalid response %1$s from server",
                                    response.getStatus()));
        }
        return storageArray;
    }

    /**
     * Returns all storage system information.
     * 
     * @return
     * @throws Exception
     */
    public Pool getStoragePoolTierInfo(String systemObjectID, String poolObjectID) throws Exception {
        InputStream responseStream = null;
        Pool pool = null;
        URI endpointURI = hdsApiClient.getBaseURI();
        Map<String, Object> attributeMap = new HashMap<String, Object>();
        StorageArray inputStorageArray = new StorageArray(systemObjectID);
        Get getOp = new Get(HDSConstants.STORAGEARRAY);
        attributeMap.put(HDSConstants.STORAGEARRAY, inputStorageArray);
        Pool inputPool = new Pool(poolObjectID);
        attributeMap.put(HDSConstants.JOURNAL_POOL, inputPool);
        attributeMap.put(HDSConstants.GET, getOp);

        String getPoolTieringInfoInputXML = InputXMLGenerationClient.getInputXMLString(
                HDSConstants.GET_STORAGE_POOL_TIERING_INFO_OP, attributeMap,
                HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

        log.info("Get StoragePool Tiering info query payload :{}", getPoolTieringInfoInputXML);
        ClientResponse response = hdsApiClient.post(endpointURI, getPoolTieringInfoInputXML);
        if (HttpStatus.SC_OK == response.getStatus()) {
            responseStream = response.getEntityInputStream();
            JavaResult result = SmooksUtil.getParsedXMLJavaResult(responseStream, HDSConstants.SMOOKS_CONFIG_FILE);
            verifyErrorPayload(result);
            pool = result.getBean(Pool.class);
        } else {
            log.error("Get pool tiering info failed with invalid response code {}",
                    response.getStatus());
            throw HDSException.exceptions
                    .invalidResponseFromHDS(String
                            .format("Not able to query pool tiering info due to invalid response %1$s from server",
                                    response.getStatus()));
        }
        return pool;
    }

    /**
     * Returns all storage system information.
     * 
     * @return
     * @throws Exception
     */
    public List<Pool> getStoragePoolsTierInfo(String systemObjectID) throws Exception {
        InputStream responseStream = null;
        List<Pool> poolList = null;
        URI endpointURI = hdsApiClient.getBaseURI();
        Map<String, Object> attributeMap = new HashMap<String, Object>();
        StorageArray inputStorageArray = new StorageArray(systemObjectID);
        Get getOp = new Get(HDSConstants.STORAGEARRAY);
        attributeMap.put(HDSConstants.STORAGEARRAY, inputStorageArray);
        attributeMap.put(HDSConstants.GET, getOp);

        String getPoolTieringInfoInputXML = InputXMLGenerationClient.getInputXMLString(
                HDSConstants.GET_STORAGE_POOL_TIERING_INFO_OP, attributeMap,
                HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

        log.info("Get StoragePools Tiering info query payload :{}", getPoolTieringInfoInputXML);
        ClientResponse response = hdsApiClient.post(endpointURI, getPoolTieringInfoInputXML);
        if (HttpStatus.SC_OK == response.getStatus()) {
            responseStream = response.getEntityInputStream();
            JavaResult result = SmooksUtil.getParsedXMLJavaResult(responseStream, HDSConstants.SMOOKS_CONFIG_FILE);
            verifyErrorPayload(result);
            poolList = (List<Pool>) result.getBean("thinPoolList");
        } else {
            log.error("Get pools tiering info failed with invalid response code {}",
                    response.getStatus());
            throw HDSException.exceptions
                    .invalidResponseFromHDS(String
                            .format("Not able to query pools tiering info due to invalid response %1$s from server",
                                    response.getStatus()));
        }
        return poolList;
    }

    /**
     * Utility method to check if there are any errors or not.
     * 
     * @param javaResult
     * @throws Exception
     */
    private void verifyErrorPayload(JavaResult javaResult) throws Exception {
        EchoCommand command = javaResult.getBean(EchoCommand.class);
        if (null == command || null == command.getStatus()
                || HDSConstants.FAILED_STR.equalsIgnoreCase(command.getStatus())) {
            Error error = javaResult.getBean(Error.class);
            log.info("Error response received for messageID", command.getMessageID());
            log.info("command failed with error code: {} with message {}",
                    error.getCode(), error.getDescription());
            throw HDSException.exceptions.errorResponseReceived(
                    error.getCode(), error.getDescription());
        }
    }

    public List<Pool> getThinImagePoolList(String systemObjectId) throws Exception {
        InputStream responseStream = null;
        StorageArray storageArray = null;
        try {
            URI endpointURI = hdsApiClient.getBaseURI();
            Map<String, Object> attributeMap = new HashMap<String, Object>();
            StorageArray inputStorageArray = new StorageArray(systemObjectId);
            Get getOp = new Get(HDSConstants.STORAGEARRAY);
            Condition condition = new Condition("6");

            attributeMap.put(HDSConstants.GET, getOp);
            attributeMap.put(HDSConstants.STORAGEARRAY, inputStorageArray);
            attributeMap.put(HDSConstants.CONDITION, condition);

            String getThinImagePoolInputXML = InputXMLGenerationClient.getInputXMLString(
                    HDSConstants.GET_THINIMAGE_POOL_INFO_OP, attributeMap,
                    HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                    HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

            log.info("Query to get ThinImagePool :{}", getThinImagePoolInputXML);
            ClientResponse response = hdsApiClient.post(endpointURI, getThinImagePoolInputXML);
            if (HttpStatus.SC_OK == response.getStatus()) {
                responseStream = response.getEntityInputStream();
                JavaResult result = SmooksUtil.getParsedXMLJavaResult(responseStream, HDSConstants.SMOOKS_CONFIG_FILE);
                verifyErrorPayload(result);
                storageArray = result.getBean(StorageArray.class);
            } else {
                log.error("Get system details failed with invalid response code {}",
                        response.getStatus());
                throw HDSException.exceptions
                        .invalidResponseFromHDS(String
                                .format("Not able to query system details due to invalid response %1$s from server",
                                        response.getStatus()));
            }
        } finally {
            if (null != responseStream) {
                try {
                    responseStream.close();
                } catch (IOException e) {
                    log.warn("IOException occurred while closing the response stream");
                }
            }
        }

        return storageArray.getThinPoolList();
    }

}
