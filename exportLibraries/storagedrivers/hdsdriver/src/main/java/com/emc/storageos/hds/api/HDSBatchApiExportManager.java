/*
 * Copyright (c) 2015 EMC Corporation
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
import com.emc.storageos.hds.model.Add;
import com.emc.storageos.hds.model.Delete;
import com.emc.storageos.hds.model.EchoCommand;
import com.emc.storageos.hds.model.Error;
import com.emc.storageos.hds.model.HostStorageDomain;
import com.emc.storageos.hds.model.Path;
import com.emc.storageos.hds.model.StorageArray;
import com.emc.storageos.hds.util.SmooksUtil;
import com.emc.storageos.hds.xmlgen.InputXMLGenerationClient;
import com.sun.jersey.api.client.ClientResponse;

/**
 * This class is responsible to perform the Batch API operations using REST
 * calls like POST to HiCommand DM server.
 * 
 */
public class HDSBatchApiExportManager {

    /**
     * Logger instance to log messages.
     */
    private static final Logger log = LoggerFactory
            .getLogger(HDSBatchApiExportManager.class);

    private HDSApiClient hdsApiClient;

    public HDSBatchApiExportManager(HDSApiClient hdsApiClient) {
        this.hdsApiClient = hdsApiClient;
    }

    /**
     * This method makes http POST call with a payload of bulk
     * HostStorageDomains.
     * 
     * @param systemId
     *            - SystemObjectID.
     * @param hostGroups
     *            - List of HostStorageDomain objects.
     * @return - Returns a List of created HostStorageDomain on Array.
     * @throws Exception
     *             - In case processing errors.
     */
    public List<HostStorageDomain> addHostStorageDomains(String systemId,
            List<HostStorageDomain> hostGroups, String model) throws Exception {
        InputStream responseStream = null;
        List<HostStorageDomain> hsdList = null;
        try {

            Map<String, Object> attributeMap = new HashMap<String, Object>();
            StorageArray array = new StorageArray(systemId);
            Add addOp = new Add(HDSConstants.HOST_STORAGE_DOMAIN);
            attributeMap.put(HDSConstants.STORAGEARRAY, array);
            attributeMap.put(HDSConstants.ADD, addOp);
            attributeMap.put(HDSConstants.MODEL, model);
            attributeMap.put(HDSConstants.HOSTGROUP_LIST, hostGroups);

            String addHSDToSystemQuery = InputXMLGenerationClient
                    .getInputXMLString(
                            HDSConstants.BATCH_ADD_HSDS_TO_SYSTEM_OP,
                            attributeMap,
                            HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                            HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

            log.info("Batch query to create HostStorageDomains: {}",
                    addHSDToSystemQuery);
            URI endpointURI = hdsApiClient.getBaseURI();
            ClientResponse response = hdsApiClient.post(endpointURI,
                    addHSDToSystemQuery);
            if (HttpStatus.SC_OK == response.getStatus()) {
                responseStream = response.getEntityInputStream();
                JavaResult javaResult = SmooksUtil.getParsedXMLJavaResult(
                        responseStream, HDSConstants.SMOOKS_CONFIG_FILE);
                verifyErrorPayload(javaResult);
                hsdList = (List<HostStorageDomain>) javaResult
                        .getBean(HDSConstants.HSD_RESPONSE_BEAN_ID);
                if (null == hsdList || hsdList.isEmpty()) {
                    throw HDSException.exceptions.notAbleToAddHSD(systemId);
                }
            } else {
                throw HDSException.exceptions
                        .invalidResponseFromHDS(String
                                .format("Not able to add HostStorageDomains due to invalid response %1$s from server",
                                        response.getStatus()));
            }
        } finally {
            if (null != responseStream) {
                try {
                    responseStream.close();
                } catch (IOException e) {
                    log.warn("IOException occurred while closing the response stream addHostStorageDomains");
                }
            }
        }
        return hsdList;
    }

    /**
     * This method makes a HTTP POST call to HiCommand with a payload of muliple
     * HSD's and each with a set of WWNs. This method can only be used for FC
     * HSD's.
     * 
     * @param systemId
     *            - represents Storage System ObjectID.
     * @param hsdList
     *            - List of HostStorageDomain objects.
     * @return - List of HostStorageDomain objects with WWN's added.
     * @throws Exception
     *             - In case of processing error.
     */
    public List<HostStorageDomain> addWWNsToHostStorageDomain(String systemId,
            List<HostStorageDomain> hsdList, String model) throws Exception {
        InputStream responseStream = null;
        List<HostStorageDomain> hsdResponseList = null;
        try {
            String addWWNToHSDsQuery = constructWWNQuery(systemId, hsdList, model);
            log.info(
                    "batch query to add FC initiators to HostStorageDomains: {}",
                    addWWNToHSDsQuery);
            URI endpointURI = hdsApiClient.getBaseURI();
            ClientResponse response = hdsApiClient.post(endpointURI,
                    addWWNToHSDsQuery);
            if (HttpStatus.SC_OK == response.getStatus()) {
                responseStream = response.getEntityInputStream();
                JavaResult javaResult = SmooksUtil.getParsedXMLJavaResult(
                        responseStream, HDSConstants.SMOOKS_CONFIG_FILE);
                verifyErrorPayload(javaResult);
                hsdResponseList = (List<HostStorageDomain>) javaResult
                        .getBean(HDSConstants.HSD_RESPONSE_BEAN_ID);
                if (null == hsdResponseList || hsdResponseList.isEmpty()) {
                    throw HDSException.exceptions
                            .notAbleToAddInitiatorsToHostStorageDomain(systemId);
                }

            } else {
                throw HDSException.exceptions
                        .invalidResponseFromHDS(String
                                .format("Batch query to add FC initiators to HSDs failed due to invalid response %1$s from server",
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
        return hsdResponseList;
    }

    /**
     * This method makes a HTTP POST call to HiCommand with a payload of muliple
     * HSD's and each with a set of ISCSINames. This method can only be used for
     * ISCSI HSD's.
     * 
     * @param systemId
     *            - represents Storage System ObjectID.
     * @param hsdList
     *            - List of HostStorageDomain objects.
     * @return - List of HostStorageDomain objects with ISCSINames's added.
     * @throws Exception
     *             - In case of processing error.
     */
    public List<HostStorageDomain> addISCSINamesToHostStorageDomain(
            String systemId, List<HostStorageDomain> hsdList, String model) throws Exception {
        InputStream responseStream = null;
        List<HostStorageDomain> hsdResponseList = null;
        try {
            String addISCSINamesToHSDsQuery = constructISCSINamesQuery(
                    systemId, hsdList, model);
            log.info(
                    "batch query to add ISCSI initiators to HostStorageDomains: {}",
                    addISCSINamesToHSDsQuery);
            URI endpointURI = hdsApiClient.getBaseURI();
            ClientResponse response = hdsApiClient.post(endpointURI,
                    addISCSINamesToHSDsQuery);
            if (HttpStatus.SC_OK == response.getStatus()) {
                responseStream = response.getEntityInputStream();
                JavaResult javaResult = SmooksUtil.getParsedXMLJavaResult(
                        responseStream, HDSConstants.SMOOKS_CONFIG_FILE);
                verifyErrorPayload(javaResult);
                hsdResponseList = (List<HostStorageDomain>) javaResult
                        .getBean(HDSConstants.HSD_RESPONSE_BEAN_ID);
                if (null == hsdResponseList || hsdResponseList.isEmpty()) {
                    throw HDSException.exceptions
                            .notAbleToAddInitiatorsToHostStorageDomain(systemId);
                }

            } else {
                throw HDSException.exceptions
                        .invalidResponseFromHDS(String
                                .format("Batch query to add ISCSI initiators to HSDs failed due to invalid response %1$s from server",
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
        return hsdResponseList;
    }

    /**
     * This method makes a HTTP POST call to add multiple LUN Paths using a
     * batch query.
     * 
     * @param systemId
     *            - Represents the storage system objectID.
     * @param pathList
     *            - List of Path objects.
     * @param model - model of the system
     * @return - List of Path objects after successful creation.
     * @throws Exception
     *             - Incase of processing Error.
     */
    public List<Path> addLUNPathsToHSDs(String systemId, List<Path> pathList, String model)
            throws Exception {
        InputStream responseStream = null;
        List<Path> pathResponseList = null;

        try {
            String addLUNQuery = constructAddLUNQuery(systemId, pathList, model);
            log.info("Query to addLUN Query: {}", addLUNQuery);
            URI endpointURI = hdsApiClient.getBaseURI();
            ClientResponse response = hdsApiClient.post(endpointURI,
                    addLUNQuery);
            if (HttpStatus.SC_OK == response.getStatus()) {
                responseStream = response.getEntityInputStream();
                JavaResult javaResult = SmooksUtil.getParsedXMLJavaResult(
                        responseStream, HDSConstants.SMOOKS_CONFIG_FILE);
                verifyErrorPayload(javaResult);
                pathResponseList = (List<Path>) javaResult
                        .getBean(HDSConstants.PATHLIST_RESPONSE_BEANID);
                if (null == pathResponseList || pathResponseList.isEmpty()) {
                    throw HDSException.exceptions.notAbleToAddVolumeToHSD(null,
                            systemId);
                }
            } else {
                throw HDSException.exceptions
                        .invalidResponseFromHDS(String
                                .format("Not able to add Volume to HostStorageDomain due to invalid response %1$s from server",
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
        return pathResponseList;
    }

    /**
     * This method makes a HTTP POST call to delete all HostStorageDomain's
     * using a batch query.
     * 
     * @param systemId
     *            - Represents storage system ObjectID.
     * @param hsdList
     *            - List of HostStorageDomain objects to delete.
     * @param model - Model of the system
     * 
     * @throws Exception
     */
    public void deleteBatchHostStorageDomains(String systemId,
            List<HostStorageDomain> hsdList, String model) throws Exception {
        InputStream responseStream = null;
        try {
            String deleteHSDsQuery = constructDeleteHSDsQuery(systemId, hsdList, model);
            log.info("Batch Query to delete HSD's: {}", deleteHSDsQuery);
            URI endpointURI = hdsApiClient.getBaseURI();
            ClientResponse response = hdsApiClient.post(endpointURI,
                    deleteHSDsQuery);
            if (HttpStatus.SC_OK == response.getStatus()) {
                responseStream = response.getEntityInputStream();
                JavaResult javaResult = SmooksUtil.getParsedXMLJavaResult(
                        responseStream, HDSConstants.SMOOKS_CONFIG_FILE);
                verifyErrorPayload(javaResult);
            } else {
                throw HDSException.exceptions
                        .invalidResponseFromHDS(String
                                .format("Not able to delete HostStorageDomains due to invalid response %1$s from server",
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
        log.info("Batch Query to delete HSD's completed.");
    }

    /**
     * This method makes HTTP POST call to delete LUN Paths's using a batch
     * query from the storage system.
     * 
     * @param systemId
     *            - represents the storage system objectID.
     * @param pathList
     *            - List of Path objects to delete.
     * @param model - Model of the system
     * @throws Exception
     *             - Incase of processing error.
     */
    public void deleteLUNPathsFromStorageSystem(String systemId,
            List<Path> pathList, String model) throws Exception {
        InputStream responseStream = null;
        try {
            String deleteLUNsQuery = constructRemoveLUNsQuery(systemId,
                    pathList, model);
            log.info("Batch query to deleteLUNs Query: {}", deleteLUNsQuery);
            URI endpointURI = hdsApiClient.getBaseURI();
            ClientResponse response = hdsApiClient.post(endpointURI,
                    deleteLUNsQuery);
            if (HttpStatus.SC_OK == response.getStatus()) {
                responseStream = response.getEntityInputStream();
                JavaResult javaResult = SmooksUtil.getParsedXMLJavaResult(
                        responseStream, HDSConstants.SMOOKS_CONFIG_FILE);
                verifyErrorPayload(javaResult);
                log.info("Deleted {} LUN paths from system:{}",
                        pathList.size(), systemId);
            } else {
                throw HDSException.exceptions
                        .invalidResponseFromHDS(String
                                .format("Not able to delete Volume from HostGroups due to invalid response %1$s from server",
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
    }

    /**
     * Constructs a batch query for given HSD's and each with a set of
     * WorldWideName's to add. This query should be used to add FC initiators to
     * the FC HSD.
     * 
     * @param systemId
     *            - Represents the storage system objectID.
     * @param hsdList
     *            - List of HostStorageDomain objects.
     * @return
     */
    private String constructISCSINamesQuery(String systemId,
            List<HostStorageDomain> hsdList, String model) {
        Map<String, Object> attributeMap = new HashMap<String, Object>();
        StorageArray array = new StorageArray(systemId);
        Add addOp = new Add(HDSConstants.ISCSI_NAME_FOR_HSD_TARGET);
        attributeMap.put(HDSConstants.STORAGEARRAY, array);
        attributeMap.put(HDSConstants.ADD, addOp);
        attributeMap.put(HDSConstants.MODEL, model);
        attributeMap.put(HDSConstants.HOSTGROUP_LIST, hsdList);

        String addWWNQuery = InputXMLGenerationClient.getInputXMLString(
                HDSConstants.BATCH_ADD_WWN_TO_HSD_OP, attributeMap,
                HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

        return addWWNQuery;
    }

    /**
     * Constructs a batch query for given HSD's and the WorldWideName's to add.
     * This query should be used to add FC initiators to the FC HSD. This
     * constructs the xml input string for multiple HSD's and each HSD with
     * multiple WWN's.
     * 
     * @param systemId
     *            - StorageSystem ObjectID.
     * @param hsdList
     *            - List of HSD objects.
     * @return - XML String to add HSD's with WWN's.
     */
    private String constructWWNQuery(String systemId,
            List<HostStorageDomain> hsdList, String model) {
        Map<String, Object> attributeMap = new HashMap<String, Object>();
        StorageArray array = new StorageArray(systemId);
        Add addOp = new Add(HDSConstants.ADD_WWN_TO_HSD_TARGET);
        attributeMap.put(HDSConstants.STORAGEARRAY, array);
        attributeMap.put(HDSConstants.ADD, addOp);
        attributeMap.put(HDSConstants.MODEL, model);
        attributeMap.put(HDSConstants.HOSTGROUP_LIST, hsdList);

        return InputXMLGenerationClient.getInputXMLString(
                HDSConstants.BATCH_ADD_WWN_TO_HSD_OP, attributeMap,
                HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);
    }

    /**
     * Constructs a batch query for the given Path objects to remove LUN's from
     * storage system.
     * 
     * @param systemId
     *            - represents storage system objectID.
     * @param pathList
     *            - List of Path objects.
     * @return - XML String to remove the Paths from storage system
     */
    private String constructRemoveLUNsQuery(String systemId, List<Path> pathList, String model) {
        Map<String, Object> attributeMap = new HashMap<String, Object>();
        StorageArray array = new StorageArray(systemId);
        Delete deleteOp = new Delete(HDSConstants.LUN_TARGET);
        attributeMap.put(HDSConstants.STORAGEARRAY, array);
        attributeMap.put(HDSConstants.DELETE, deleteOp);
        attributeMap.put(HDSConstants.MODEL, model);
        attributeMap.put(HDSConstants.PATH_LIST, pathList);

        return InputXMLGenerationClient.getInputXMLString(
                HDSConstants.DELETE_PATH_FROM_HSD_OP, attributeMap,
                HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);
    }

    /**
     * Constructs a batch query for the given Path objects to add LUN's to
     * storage system.
     * 
     * @param systemId
     *            - represents storagesystem objectID.
     * @param pathList
     *            - List of Path objects.
     * @return - XML String to add LUN's to storage system.
     */
    private String constructAddLUNQuery(String systemId, List<Path> pathList, String model) {
        Map<String, Object> attributeMap = new HashMap<String, Object>();
        StorageArray array = new StorageArray(systemId);
        Add addOp = new Add(HDSConstants.LUN_TARGET);
        attributeMap.put(HDSConstants.STORAGEARRAY, array);
        attributeMap.put(HDSConstants.ADD, addOp);
        attributeMap.put(HDSConstants.MODEL, model);
        attributeMap.put(HDSConstants.PATH_LIST, pathList);

        return InputXMLGenerationClient.getInputXMLString(
                HDSConstants.ADD_PATH_TO_HSD_OP, attributeMap,
                HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);
    }

    /**
     * Constructs a batch query to delete given HSD's from the storage system.
     * 
     * @param systemId
     *            - represents the storage system ObjectID.
     * @param hsdList
     *            - List of HostStorageDomain objects.
     * @return - XML string to delete the HostStorageDomain's
     */
    private String constructDeleteHSDsQuery(String systemId,
            List<HostStorageDomain> hsdList, String model) {

        Map<String, Object> attributeMap = new HashMap<String, Object>();
        StorageArray array = new StorageArray(systemId);
        Delete deleteOp = new Delete(HDSConstants.HOST_STORAGE_DOMAIN);
        attributeMap.put(HDSConstants.STORAGEARRAY, array);
        attributeMap.put(HDSConstants.DELETE, deleteOp);
        attributeMap.put(HDSConstants.MODEL, model);
        attributeMap.put(HDSConstants.HOSTGROUP_LIST, hsdList);

        return InputXMLGenerationClient.getInputXMLString(
                HDSConstants.BATCH_DELETE_HSDS_FROM_SYSTEM, attributeMap,
                HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);
    }

    /**
     * Utility method to check if there are any errors or not.
     * 
     * @param javaResult
     *            - java Result of the Parsed XML response.
     * @throws Exception
     *             - In case of processing error.
     */
    private void verifyErrorPayload(JavaResult javaResult) throws Exception {
        EchoCommand command = javaResult.getBean(EchoCommand.class);
        if (null == command
                || null == command.getStatus()
                || HDSConstants.FAILED_STR
                        .equalsIgnoreCase(command.getStatus())) {
            Error error = javaResult.getBean(Error.class);
            log.info(
                    "Error response received from Hitachi server for messageID",
                    command.getMessageID());
            log.info(
                    "Hitachi command failed with error code:{} with message:{} for request:{}",
                    new Object[] { error.getCode().toString(),
                            error.getDescription(), error.getSource() });
            throw HDSException.exceptions.errorResponseReceived(
                    error.getCode(), error.getDescription());
        }
    }

}