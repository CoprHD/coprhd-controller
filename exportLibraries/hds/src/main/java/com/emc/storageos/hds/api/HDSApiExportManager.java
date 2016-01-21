/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hds.api;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
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
import com.emc.storageos.hds.model.FreeLun;
import com.emc.storageos.hds.model.Get;
import com.emc.storageos.hds.model.HDSHost;
import com.emc.storageos.hds.model.HostStorageDomain;
import com.emc.storageos.hds.model.ISCSIName;
import com.emc.storageos.hds.model.Path;
import com.emc.storageos.hds.model.StorageArray;
import com.emc.storageos.hds.model.WorldWideName;
import com.emc.storageos.hds.util.SmooksUtil;
import com.emc.storageos.hds.xmlgen.InputXMLGenerationClient;
import com.sun.jersey.api.client.ClientResponse;

/**
 * This volume manager is responsible creating volumes/delete volumes.
 * 
 */
public class HDSApiExportManager {

    /**
     * Logger instance to log messages.
     */
    private static final Logger log = LoggerFactory.getLogger(HDSApiExportManager.class);

    private HDSApiClient hdsApiClient;

    public HDSApiExportManager(HDSApiClient hdsApiClient) {
        this.hdsApiClient = hdsApiClient;
    }

    /**
     * Register the given host with HiCommand Device Manager.
     * 
     * @param hostName
     * @param ipAddress
     * @param portWWN
     * @return
     * @throws Exception
     */
    public HDSHost registerHost(HDSHost hdshost, List<String> portWWNList,
            String initiatorType) throws Exception {
        String addHostQueryWithParams = null;
        InputStream responseStream = null;
        HDSHost registeredhost = null;
        try {
            if (initiatorType.equalsIgnoreCase(HDSConstants.FC)) {
                addHostQueryWithParams = constructAddFCInitiatorHostQuery(hdshost, portWWNList);
            } else if (initiatorType.equalsIgnoreCase(HDSConstants.ISCSI)) {
                addHostQueryWithParams = constructAddiSCSIInitiatorHostQuery(hdshost, portWWNList);
            }
            log.info("Query to Add host: {}", addHostQueryWithParams);
            URI endpointURI = hdsApiClient.getBaseURI();
            ClientResponse response = hdsApiClient.post(endpointURI,
                    addHostQueryWithParams);
            if (HttpStatus.SC_OK == response.getStatus()) {
                responseStream = response.getEntityInputStream();
                JavaResult javaResult = SmooksUtil.getParsedXMLJavaResult(responseStream,
                        HDSConstants.HOST_INFO_SMOOKS_CONFIG_FILE);
                EchoCommand command = javaResult.getBean(EchoCommand.class);
                if (null == command || null == command.getStatus()
                        || HDSConstants.FAILED_STR.equalsIgnoreCase(command.getStatus())) {
                    Error error = javaResult.getBean(Error.class);
                    if (error.getCode() == HDSConstants.HOST_ALREADY_EXISTS) {
                        log.info("The host {} already exists on DeviceManager", hdshost.getName());
                        return registeredhost;
                    }
                    else if (error.getCode() == HDSConstants.HOST_PORT_WWN_ALREADY_EXISTS) {
                        log.info("The WWN is already in use by another host");
                        return registeredhost;
                    } else {
                        log.error("Error response received for messageID", command.getMessageID());
                        log.error("command failed with error code: {} with message {}",
                                error.getCode(), error.getDescription());
                        throw HDSException.exceptions.notAbleToAddHostToDeviceManager(
                                hdshost.getName());
                    }

                }
                registeredhost = javaResult.getBean(HDSHost.class);
                if (null == registeredhost) {
                    throw HDSException.exceptions.notAbleToAddHostToDeviceManager(String
                            .format("Not able to add host:%1$s to Device manager",
                                    hdshost.getName()));
                }
            } else {
                throw HDSException.exceptions
                        .invalidResponseFromHDS(String
                                .format("Add Host to Device Manager failed due to invalid response %1$s from server",
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
        return registeredhost;
    }

    /**
     * Gets host registered with HiCommand Device Manager.
     * 
     * @return
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    public List<HDSHost> getHostsRegisteredWithDM() throws Exception {
        InputStream responseStream = null;
        List<HDSHost> hostList = null;
        try {
            Map<String, Object> attributeMap = new HashMap<String, Object>();
            Get getOp = new Get("Host");
            attributeMap.put("Get", getOp);

            String getHostQuery = InputXMLGenerationClient
                    .getInputXMLString("getHostsInfo", attributeMap,
                            HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                            HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

            log.info("Query to Add host: {}", getHostQuery);
            URI endpointURI = hdsApiClient.getBaseURI();
            ClientResponse response = hdsApiClient.post(endpointURI,
                    getHostQuery);
            if (HttpStatus.SC_OK == response.getStatus()) {
                responseStream = response.getEntityInputStream();
                JavaResult javaResult = SmooksUtil.getParsedXMLJavaResult(responseStream,
                        HDSConstants.HOST_INFO_SMOOKS_CONFIG_FILE);
                verifyErrorPayload(javaResult);
                hostList = (List<HDSHost>) javaResult
                        .getBean(HDSConstants.HOST_LIST_BEAN_NAME);
                if (null == hostList || hostList.isEmpty()) {
                    throw HDSException.exceptions.notAbleToGetHostInfoForHSD();
                }
            } else {
                throw HDSException.exceptions
                        .invalidResponseFromHDS(String
                                .format("Not able to Get registered hosts from Device Manager failed due to invalid response %1$s from server",
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
        return hostList;
    }

    /**
     * Constructs the addHostQuery.
     * 
     * @param hostName
     * @param ipAddress
     * @param portWWNList
     * @return
     */
    private String constructAddFCInitiatorHostQuery(HDSHost hdshost, List<String> portWWNList) {
        Map<String, Object> attributeMap = new HashMap<String, Object>();
        List<WorldWideName> wwnList = new ArrayList<WorldWideName>();
        Add addOp = new Add(HDSConstants.HOST);
        attributeMap.put(HDSConstants.HOST, hdshost);
        attributeMap.put(HDSConstants.ADD, addOp);

        if (null != portWWNList && !portWWNList.isEmpty()) {
            for (String portWWN : portWWNList) {
                WorldWideName wwn = new WorldWideName(portWWN);
                wwnList.add(wwn);
            }
            hdshost.setWwnList(wwnList);
        }
        attributeMap.put(HDSConstants.WWN_LIST, wwnList);
        String addHostWithWorldWideNamesQuery = InputXMLGenerationClient
                .getInputXMLString(HDSConstants.ADD_HOST_WITH_WWN_OP, attributeMap,
                        HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                        HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

        return addHostWithWorldWideNamesQuery;
    }

    /**
     * Constructs the addHostQuery.
     * 
     * @param hostName
     * @param ipAddress
     * @param portWWNList
     * @return
     */
    private String constructAddiSCSIInitiatorHostQuery(HDSHost hdshost, List<String> portWWNList) {
        Map<String, Object> attributeMap = new HashMap<String, Object>();
        List<ISCSIName> wwnList = new ArrayList<ISCSIName>();
        Add addOp = new Add(HDSConstants.HOST);
        attributeMap.put(HDSConstants.HOST, hdshost);
        attributeMap.put(HDSConstants.ADD, addOp);

        if (null != portWWNList && !portWWNList.isEmpty()) {
            for (String portWWN : portWWNList) {
                ISCSIName wwn = new ISCSIName(portWWN, null);
                wwnList.add(wwn);
            }
        }
        attributeMap.put(HDSConstants.ISCSINAME_LIST, wwnList);
        String addHostWithISCSINamesQuery = InputXMLGenerationClient
                .getInputXMLString(HDSConstants.ADD_HOST_WITH_ISCSINAMES_OP, attributeMap,
                        HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                        HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

        return addHostWithISCSINamesQuery;
    }

    /**
     * Add WWN to the HostStorageDomain means enable LUN security by adding WWN.
     * 
     * @param systemId
     * @param hsdId
     * @param wwnList
     * @param model
     * @return
     * @throws Exception
     */
    public HostStorageDomain addWWNToHostStorageDomain(String systemId, String hsdId,
            List<String> wwnList, String model) throws Exception {
        InputStream responseStream = null;
        HostStorageDomain hsd = null;
        try {
            String addWWNToHSDQuery = constructWWNQuery(systemId, hsdId, wwnList, model);
            log.info("Query to add FC initiators to HostStorageDomain: {}", addWWNToHSDQuery);
            URI endpointURI = hdsApiClient.getBaseURI();
            ClientResponse response = hdsApiClient.post(endpointURI, addWWNToHSDQuery);
            if (HttpStatus.SC_OK == response.getStatus()) {
                responseStream = response.getEntityInputStream();
                JavaResult javaResult = SmooksUtil.getParsedXMLJavaResult(responseStream,
                        HDSConstants.SMOOKS_CONFIG_FILE);
                verifyErrorPayload(javaResult);
                hsd = javaResult.getBean(HostStorageDomain.class);
                if (null == hsd) {
                    throw HDSException.exceptions
                            .notAbleToAddInitiatorToHostStorageDomain("FC",
                                    hsdId, systemId);
                }

            } else {
                throw HDSException.exceptions
                        .invalidResponseFromHDS(String
                                .format("Add initiator to HostStorageDomain failed due to invalid response %1$s from server",
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
        return hsd;
    }

    /**
     * Remove given WWN's from HostStorageDomain means disable LUN security by removing these WWN's.
     * 
     * @param systemId
     * @param hsdId
     * @param wwnList
     * @param model
     * @return
     * @throws Exception
     */
    public void deleteWWNsFromHostStorageDomain(String systemId, String hsdId,
            List<String> wwnList, String model) throws Exception {
        InputStream responseStream = null;
        try {
            String removeWWNFromHSDQuery = constructDeleteWWNQuery(systemId, hsdId, wwnList, model);
            log.info("Query to delete FC initiators to HostStorageDomain: {}", removeWWNFromHSDQuery);
            URI endpointURI = hdsApiClient.getBaseURI();
            ClientResponse response = hdsApiClient.post(endpointURI, removeWWNFromHSDQuery);
            if (HttpStatus.SC_OK == response.getStatus()) {
                responseStream = response.getEntityInputStream();
                JavaResult javaResult = SmooksUtil.getParsedXMLJavaResult(responseStream,
                        HDSConstants.SMOOKS_CONFIG_FILE);
                verifyErrorPayload(javaResult);
                log.info("Remove fc initiators: {} from HSD: {}", wwnList, hsdId);
            } else {
                throw HDSException.exceptions
                        .invalidResponseFromHDS(String
                                .format("Deleting initiator from HostStorageDomain failed due to response %1$s from server",
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
     * Add WWN to the HostStorageDomain means enable LUN security by adding WWN.
     * 
     * @param systemId
     * @param hsdId
     * @param scsiNameList
     * @param model
     * @return
     * @throws Exception
     */
    public HostStorageDomain addISCSIInitatorsToHostStorageDomain(String systemId, String hsdId,
            List<String> scsiNameList, String model) throws Exception {
        InputStream responseStream = null;
        HostStorageDomain hsd = null;
        try {
            String addISCSINamesToHSDQuery = constructISCSIQuery(systemId, hsdId, scsiNameList, model);
            log.info("Query to add SCSI Initiators to HostStorageDomain: {}", addISCSINamesToHSDQuery);
            URI endpointURI = hdsApiClient.getBaseURI();
            ClientResponse response = hdsApiClient.post(endpointURI, addISCSINamesToHSDQuery);
            if (HttpStatus.SC_OK == response.getStatus()) {
                responseStream = response.getEntityInputStream();
                JavaResult javaResult = SmooksUtil.getParsedXMLJavaResult(responseStream,
                        HDSConstants.SMOOKS_CONFIG_FILE);
                verifyErrorPayload(javaResult);
                hsd = javaResult.getBean(HostStorageDomain.class);
                if (null == hsd) {
                    throw HDSException.exceptions
                            .notAbleToAddInitiatorToHostStorageDomain("iSCSI",
                                    hsdId, systemId);
                }

            } else {
                throw HDSException.exceptions
                        .invalidResponseFromHDS(String
                                .format("Add iSCSI initiator to HostStorageDomain failed due to invalid response %1$s from server",
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
        return hsd;
    }

    /**
     * Remove ISCSIName from the HostStorageDomain means disable LUN security by to the given iSCSIName.
     * 
     * @param systemId
     * @param hsdId
     * @param scsiNameList
     * @param model
     * @return
     * @throws Exception
     */
    public void deleteISCSIsFromHostStorageDomain(String systemId, String hsdId,
            List<String> scsiNameList, String model) throws Exception {
        InputStream responseStream = null;
        try {
            String addISCSINamesToHSDQuery = constructRemoveISCSIQuery(systemId, hsdId, scsiNameList, model);
            log.info("Query to remove SCSI Initiators from HostStorageDomain: {}", addISCSINamesToHSDQuery);
            URI endpointURI = hdsApiClient.getBaseURI();
            ClientResponse response = hdsApiClient.post(endpointURI, addISCSINamesToHSDQuery);
            if (HttpStatus.SC_OK == response.getStatus()) {
                responseStream = response.getEntityInputStream();
                JavaResult javaResult = SmooksUtil.getParsedXMLJavaResult(responseStream,
                        HDSConstants.SMOOKS_CONFIG_FILE);
                verifyErrorPayload(javaResult);
                log.info("Remove iscsi initiators: {} from HSD: {}", scsiNameList, hsdId);
            } else {
                throw HDSException.exceptions
                        .invalidResponseFromHDS(String
                                .format("Remove iSCSI initiator From HostStorageDomain failed due to invalid response %1$s from server",
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
            log.info("Error response received from Hitachi server for messageID", command.getMessageID());
            log.info("Hitachi command failed with error code:{} with message:{} for request:{}",
                    new Object[] { error.getCode().toString(), error.getDescription(), error.getSource() });
            throw HDSException.exceptions.errorResponseReceived(
                    error.getCode(), error.getDescription());
        }
    }

    /**
     * Return the existing HSD's configured on the storage array.
     * 
     * @param systemId
     * @param type
     * @return
     * @throws Exception
     */
    public List<HostStorageDomain> getHostStorageDomains(String systemId)
            throws Exception {
        InputStream responseStream = null;
        StorageArray storageArray = null;
        List<HostStorageDomain> hsdList = null;
        try {
            Map<String, Object> attributeMap = new HashMap<String, Object>();
            StorageArray array = new StorageArray(systemId);
            attributeMap.put(HDSConstants.STORAGEARRAY, array);
            Get getOp = new Get(HDSConstants.STORAGEARRAY);
            attributeMap.put(HDSConstants.GET, getOp);
            HostStorageDomain hsd = new HostStorageDomain();
            attributeMap.put(HDSConstants.HOST_STORAGE_DOMAIN, hsd);

            String getAllHSDQuery = InputXMLGenerationClient.getInputXMLString(
                    HDSConstants.GET_HSD_INFO_OP, attributeMap,
                    HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                    HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

            log.info("Query to get HostStorageDomain: {}", getAllHSDQuery);
            URI endpointURI = hdsApiClient.getBaseURI();
            ClientResponse response = hdsApiClient.post(endpointURI, getAllHSDQuery);
            if (HttpStatus.SC_OK == response.getStatus()) {
                responseStream = response.getEntityInputStream();
                JavaResult javaResult = SmooksUtil.getParsedXMLJavaResult(responseStream,
                        HDSConstants.SMOOKS_CONFIG_FILE);
                verifyErrorPayload(javaResult);
                storageArray = javaResult.getBean(StorageArray.class);
                if (null != storageArray) {
                    hsdList = storageArray.getHsdList();
                }
            } else {
                throw HDSException.exceptions
                        .invalidResponseFromHDS(String
                                .format("Not able to query HostStorageDomain's due to invalid response %1$s from server",
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
        return hsdList;
    }

    /**
     * Return the existing HSD's configured on the storage array.
     * 
     * @param systemId
     * @param type
     * @return
     * @throws Exception
     */
    public HostStorageDomain getHostStorageDomain(String systemId, String hsdId)
            throws Exception {
        InputStream responseStream = null;
        HostStorageDomain hsd = null;
        try {
            Map<String, Object> attributeMap = new HashMap<String, Object>();
            StorageArray array = new StorageArray(systemId);
            attributeMap.put(HDSConstants.STORAGEARRAY, array);
            Get getOp = new Get(HDSConstants.STORAGEARRAY);
            attributeMap.put(HDSConstants.GET, getOp);
            HostStorageDomain inputHsd = new HostStorageDomain(hsdId);
            attributeMap.put(HDSConstants.HOST_STORAGE_DOMAIN, inputHsd);

            String getHSDQuery = InputXMLGenerationClient.getInputXMLString(
                    HDSConstants.GET_HSD_INFO_OP, attributeMap,
                    HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                    HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

            log.info("Query to get HostStorageDomain: {}", getHSDQuery);
            URI endpointURI = hdsApiClient.getBaseURI();
            ClientResponse response = hdsApiClient.post(endpointURI, getHSDQuery);
            if (HttpStatus.SC_OK == response.getStatus()) {
                responseStream = response.getEntityInputStream();
                JavaResult javaResult = SmooksUtil.getParsedXMLJavaResult(responseStream,
                        HDSConstants.SMOOKS_CONFIG_FILE);
                verifyErrorPayload(javaResult);
                hsd = javaResult.getBean(HostStorageDomain.class);
            } else {
                throw HDSException.exceptions
                        .invalidResponseFromHDS(String
                                .format("Not able to query HostStorageDomain due to invalid response %1$s from server",
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
        return hsd;
    }

    /**
     * Construct the WWN Query by adding multiple WWNs.
     * This query should be used to add FC initiators to the FC HSD.
     * 
     * @param systemId
     * @param hsdId
     * @param wwnList
     * @return
     */
    private String constructWWNQuery(String systemId, String hsdId, List<String> wwnList, String model) {
        Map<String, Object> attributeMap = new HashMap<String, Object>();
        StorageArray array = new StorageArray(systemId);
        Add addOp = new Add(HDSConstants.ADD_WWN_TO_HSD_TARGET);
        attributeMap.put(HDSConstants.STORAGEARRAY, array);
        attributeMap.put(HDSConstants.ADD, addOp);
        attributeMap.put(HDSConstants.MODEL, model);
        HostStorageDomain hsd = new HostStorageDomain(hsdId);
        attributeMap.put(HDSConstants.HOST_STORAGE_DOMAIN, hsd);
        List<WorldWideName> wwnObjList = new ArrayList<WorldWideName>();

        if (null != wwnList && !wwnList.isEmpty()) {
            for (String initiatorWWN : wwnList) {
                WorldWideName wwn = new WorldWideName(initiatorWWN);
                wwnObjList.add(wwn);
            }
        }
        attributeMap.put(HDSConstants.WWN_LIST, wwnObjList);

        String addWWNQuery = InputXMLGenerationClient.getInputXMLString(
                HDSConstants.ADD_WWN_TO_HSD_OP, attributeMap,
                HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

        return addWWNQuery;
    }

    /**
     * Construct the WWN Query by adding multiple WWNs.
     * This query should be used to add FC initiators to the FC HSD.
     * 
     * @param systemId
     * @param hsdId
     * @param wwnList
     * @return
     */
    private String constructDeleteWWNQuery(String systemId, String hsdId, List<String> wwnList, String model) {
        Map<String, Object> attributeMap = new HashMap<String, Object>();
        StorageArray array = new StorageArray(systemId);
        Delete deleteOp = new Delete(HDSConstants.ADD_WWN_TO_HSD_TARGET);
        attributeMap.put(HDSConstants.STORAGEARRAY, array);
        attributeMap.put(HDSConstants.DELETE, deleteOp);
        attributeMap.put(HDSConstants.MODEL, model);
        HostStorageDomain hsd = new HostStorageDomain(hsdId);
        attributeMap.put(HDSConstants.HOST_STORAGE_DOMAIN, hsd);
        List<WorldWideName> wwnObjList = new ArrayList<WorldWideName>();

        if (null != wwnList && !wwnList.isEmpty()) {
            for (String initiatorWWN : wwnList) {
                WorldWideName wwn = new WorldWideName(initiatorWWN);
                wwnObjList.add(wwn);
            }
        }
        attributeMap.put(HDSConstants.WWN_LIST, wwnObjList);

        String deleteWWNFromHSDQuery = InputXMLGenerationClient.getInputXMLString(
                HDSConstants.DELETE_WWN_FROM_HSD_OP, attributeMap,
                HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

        return deleteWWNFromHSDQuery;
    }

    /**
     * Construct the iSCSINames Query by adding multiple WWNs.
     * This query should be used to add the iSCSI initiators to the iSCSI HSD.
     * 
     * @param systemId
     * @param hsdId
     * @param wwnList
     * @return
     */
    private String constructISCSIQuery(String systemId, String hsdId, List<String> scsiNameList, String model) {

        Map<String, Object> attributeMap = new HashMap<String, Object>();
        StorageArray array = new StorageArray(systemId);
        Add addOp = new Add(HDSConstants.ISCSI_NAME_FOR_HSD_TARGET);
        attributeMap.put(HDSConstants.STORAGEARRAY, array);
        attributeMap.put(HDSConstants.ADD, addOp);
        attributeMap.put(HDSConstants.MODEL, model);
        HostStorageDomain hsd = new HostStorageDomain(hsdId);
        attributeMap.put(HDSConstants.HOST_STORAGE_DOMAIN, hsd);
        List<ISCSIName> iSCSIObjList = new ArrayList<ISCSIName>();

        if (null != scsiNameList && !scsiNameList.isEmpty()) {
            for (String iScsiName : scsiNameList) {
                ISCSIName iSCSIName = new ISCSIName(iScsiName, null);
                iSCSIObjList.add(iSCSIName);
            }
        }
        attributeMap.put(HDSConstants.ISCSINAME_LIST, iSCSIObjList);

        String addISCSINamesToHSDQuery = InputXMLGenerationClient.getInputXMLString(
                HDSConstants.ADD_ISCSI_NAME_TO_HSD_OP, attributeMap,
                HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

        return addISCSINamesToHSDQuery;
    }

    /**
     * Construct the iSCSINames Query by adding multiple WWNs.
     * This query should be used to add the iSCSI initiators to the iSCSI HSD.
     * 
     * @param systemId
     * @param hsdId
     * @param wwnList
     * @return
     */
    private String constructRemoveISCSIQuery(String systemId, String hsdId, List<String> scsiNameList, String model) {

        Map<String, Object> attributeMap = new HashMap<String, Object>();
        StorageArray array = new StorageArray(systemId);
        Delete deleteOp = new Delete(HDSConstants.ISCSI_NAME_FOR_HSD_TARGET);
        attributeMap.put(HDSConstants.STORAGEARRAY, array);
        attributeMap.put(HDSConstants.DELETE, deleteOp);
        attributeMap.put(HDSConstants.MODEL, model);
        HostStorageDomain hsd = new HostStorageDomain(hsdId);
        attributeMap.put(HDSConstants.HOST_STORAGE_DOMAIN, hsd);
        List<ISCSIName> iSCSIObjList = new ArrayList<ISCSIName>();

        if (null != scsiNameList && !scsiNameList.isEmpty()) {
            for (String iScsiName : scsiNameList) {
                ISCSIName iSCSIName = new ISCSIName(iScsiName, null);
                iSCSIObjList.add(iSCSIName);
            }
        }
        attributeMap.put(HDSConstants.ISCSINAME_LIST, iSCSIObjList);

        String removeISCSINamesToHSDQuery = InputXMLGenerationClient.getInputXMLString(
                HDSConstants.REMOVE_ISCSI_NAME_FROM_HSD_OP, attributeMap,
                HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

        return removeISCSINamesToHSDQuery;
    }

    /**
     * Add new HostStorageDomain.
     * 
     * @param systemId
     * @param targetPortID
     * @param hsdNickName
     * @param hostMode.
     * @param hostModeOption
     * @param model
     * @return
     * @throws Exception
     */
    public HostStorageDomain addHostStorageDomain(String systemId, String targetPortID,
            String domainType, String hsdName, String hsdNickName, String hostMode,
            String hostModeOption, String model) throws Exception {
        InputStream responseStream = null;
        HostStorageDomain hsd = null;
        try {

            Map<String, Object> attributeMap = new HashMap<String, Object>();
            StorageArray array = new StorageArray(systemId);
            Add addOp = new Add(HDSConstants.HOST_STORAGE_DOMAIN);
            attributeMap.put(HDSConstants.STORAGEARRAY, array);
            attributeMap.put(HDSConstants.ADD, addOp);
            attributeMap.put(HDSConstants.MODEL, model);
            HostStorageDomain inputHsd = new HostStorageDomain(targetPortID, hsdName, domainType, hsdNickName);
            inputHsd.setHostMode(hostMode);
            inputHsd.setHostModeOption(hostModeOption);
            attributeMap.put(HDSConstants.HOST_STORAGE_DOMAIN, inputHsd);

            String addHSDToSystemQuery = InputXMLGenerationClient.getInputXMLString(
                    HDSConstants.ADD_HSD_TO_SYSTEM_OP, attributeMap,
                    HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                    HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

            log.info("Query to create HostStorageDomain: {}", addHSDToSystemQuery);
            URI endpointURI = hdsApiClient.getBaseURI();
            ClientResponse response = hdsApiClient.post(endpointURI, addHSDToSystemQuery);
            if (HttpStatus.SC_OK == response.getStatus()) {
                responseStream = response.getEntityInputStream();
                JavaResult javaResult = SmooksUtil.getParsedXMLJavaResult(responseStream,
                        HDSConstants.SMOOKS_CONFIG_FILE);
                verifyErrorPayload(javaResult);
                hsd = javaResult.getBean(HostStorageDomain.class);
                if (null == hsd) {
                    throw HDSException.exceptions.notAbleToAddHSD(systemId);
                }
            } else {
                throw HDSException.exceptions
                        .invalidResponseFromHDS(String
                                .format("Not able to add HostStorageDomain due to invalid response %1$s from server",
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
        return hsd;
    }

    /**
     * API call to addLun to the storage array.
     * Once the client makes the call, the luns should be visible to the host.
     * 
     * @param systemId
     * @param targetPortId
     * @param domainId
     * @param deviceLunList
     * @param model
     * @throws Exception
     */
    public List<Path> addLUN(String systemId, String targetPortId, String domainId,
            Map<String, String> deviceLunList, String model) throws Exception {
        InputStream responseStream = null;
        List<Path> pathList = new ArrayList<Path>();

        try {
            String addLUNQuery = constructAddLUNQuery(systemId, targetPortId, domainId,
                    deviceLunList, pathList, model);
            log.info("Query to addLUN Query: {}", addLUNQuery);
            URI endpointURI = hdsApiClient.getBaseURI();
            ClientResponse response = hdsApiClient.post(endpointURI, addLUNQuery);
            if (HttpStatus.SC_OK == response.getStatus()) {
                responseStream = response.getEntityInputStream();
                JavaResult javaResult = SmooksUtil.getParsedXMLJavaResult(responseStream,
                        HDSConstants.SMOOKS_CONFIG_FILE);
                verifyErrorPayload(javaResult);
                pathList = (List<Path>) javaResult.getBean(HDSConstants.PATHLIST_RESPONSE_BEANID);
                if (pathList.isEmpty()) {
                    throw HDSException.exceptions.notAbleToAddVolumeToHSD(
                            domainId, systemId);
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
        return pathList;
    }

    /**
     * Constructs the addLun query using multiple path elements. Each path
     * element defines the path from volume to initiators.
     * 
     * @param systemId
     * @param targetPortId
     * @param domainId
     * @param deviceLunList
     * @param pathList
     * @param model
     * @return
     * @throws Exception
     */
    private String constructAddLUNQuery(String systemId, String targetPortId,
            String domainId, Map<String, String> deviceLunList, List<Path> pathList, String model) throws Exception {

        Map<String, Object> attributeMap = new HashMap<String, Object>();
        StorageArray array = new StorageArray(systemId);
        Add addOp = new Add(HDSConstants.LUN_TARGET);
        attributeMap.put(HDSConstants.STORAGEARRAY, array);
        attributeMap.put(HDSConstants.ADD, addOp);
        attributeMap.put(HDSConstants.MODEL, model);

        if (null != deviceLunList && !deviceLunList.isEmpty()) {
            for (String device : deviceLunList.keySet()) {
                String lun = deviceLunList.get(device);
                Path path = new Path(targetPortId, domainId, null, lun, device);
                pathList.add(path);
                log.info("Device :{} lun:{}", device, lun);
            }
        }
        attributeMap.put(HDSConstants.PATH_LIST, pathList);

        String addLunInputXML = InputXMLGenerationClient.getInputXMLString(
                HDSConstants.ADD_PATH_TO_HSD_OP, attributeMap,
                HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);
        return addLunInputXML;
    }

    /**
     * Return the Free Lun's available for a Given HSD in a System.
     * 
     * @throws Exception
     * 
     */
    public List<FreeLun> getFreeLUNInfo(String systemId, String hsdId) throws Exception {
        InputStream responseStream = null;
        HostStorageDomain hostStorageDomain = null;
        List<FreeLun> freeLunList = null;
        try {
            Map<String, Object> attributeMap = new HashMap<String, Object>();
            StorageArray array = new StorageArray(systemId);
            Get getOp = new Get(HDSConstants.STORAGEARRAY);
            attributeMap.put(HDSConstants.STORAGEARRAY, array);
            HostStorageDomain hsd = new HostStorageDomain(hsdId);
            FreeLun freeLun = new FreeLun();
            attributeMap.put(HDSConstants.GET, getOp);
            attributeMap.put(HDSConstants.HOST_STORAGE_DOMAIN, hsd);
            attributeMap.put(HDSConstants.FREELUN, freeLun);

            String getFreeLunQueryInputXML = InputXMLGenerationClient.getInputXMLString(
                    HDSConstants.GET_FREE_LUN_INFO_OP, attributeMap,
                    HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                    HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

            log.info("Query to get FreeLUN's of a HostStorageDomain: {}", getFreeLunQueryInputXML);
            URI endpointURI = hdsApiClient.getBaseURI();
            ClientResponse response = hdsApiClient.post(endpointURI,
                    getFreeLunQueryInputXML);
            if (HttpStatus.SC_OK == response.getStatus()) {
                responseStream = response.getEntityInputStream();
                JavaResult javaResult = SmooksUtil.getParsedXMLJavaResult(responseStream,
                        HDSConstants.SMOOKS_CONFIG_FILE);
                verifyErrorPayload(javaResult);
                hostStorageDomain = javaResult.getBean(HostStorageDomain.class);
                if (null != hostStorageDomain && null != hostStorageDomain.getFreeLunList()) {
                    freeLunList = hostStorageDomain.getFreeLunList();
                } else {
                    throw HDSException.exceptions
                            .notAbleToGetFreeLunInfoForHSD(hsdId, systemId);
                }
            } else {
                throw HDSException.exceptions
                        .invalidResponseFromHDS(String
                                .format("Not able to get FreeLun info for HostStorageDomain due to invalid response %1$s from server",
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
        return freeLunList;
    }

    /**
     * Delete the Host Storage Domain for a given storage array.
     * 
     * @param systemObjectId
     * @param hsdObjectId
     * @param model
     * @throws Exception
     */
    public void deleteHostStorageDomain(String systemObjectId, String hsdObjectId, String model) throws Exception {
        InputStream responseStream = null;
        try {
            Map<String, Object> attributeMap = new HashMap<String, Object>();
            StorageArray array = new StorageArray(systemObjectId);
            Delete deleteOp = new Delete(HDSConstants.HOST_STORAGE_DOMAIN);
            attributeMap.put(HDSConstants.STORAGEARRAY, array);
            attributeMap.put(HDSConstants.DELETE, deleteOp);
            attributeMap.put(HDSConstants.MODEL, model);
            HostStorageDomain inputHsd = new HostStorageDomain(hsdObjectId);
            attributeMap.put(HDSConstants.HOST_STORAGE_DOMAIN, inputHsd);

            String deleteHSDFromSystemQuery = InputXMLGenerationClient.getInputXMLString(
                    HDSConstants.DELETE_HSD_FROM_SYSTEM_OP, attributeMap,
                    HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                    HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);
            log.info("Query to delete HostStorageDomain: {}", deleteHSDFromSystemQuery);
            URI endpointURI = hdsApiClient.getBaseURI();
            ClientResponse response = hdsApiClient.post(endpointURI,
                    deleteHSDFromSystemQuery);
            if (HttpStatus.SC_OK == response.getStatus()) {
                responseStream = response.getEntityInputStream();
                JavaResult javaResult = SmooksUtil.getParsedXMLJavaResult(responseStream,
                        HDSConstants.SMOOKS_CONFIG_FILE);
                verifyErrorPayload(javaResult);
                log.info("Deleted HSD {} from system {}", hsdObjectId, systemObjectId);
            } else {
                throw HDSException.exceptions
                        .invalidResponseFromHDS(String
                                .format("Not able to delete HostStorageDomain due to invalid response %1$s from server",
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
     * Delete the LUN Path from HSD of a given storage array.
     * 
     * @param systemObjectId
     * @param pathObjectIdList
     * @param model
     * 
     * @throws Exception
     */
    public void deleteLunPathsFromSystem(String systemObjectId, List<String> pathObjectIdList, String model) throws Exception {
        InputStream responseStream = null;
        try {
            String deleteLunPathsQuery = constructDeleteLunPathsQuery(systemObjectId, pathObjectIdList, model);
            log.info("Query to delete Lun paths: {}", deleteLunPathsQuery);
            URI endpointURI = hdsApiClient.getBaseURI();
            ClientResponse response = hdsApiClient.post(endpointURI,
                    deleteLunPathsQuery);
            if (HttpStatus.SC_OK == response.getStatus()) {
                responseStream = response.getEntityInputStream();
                JavaResult javaResult = SmooksUtil.getParsedXMLJavaResult(responseStream,
                        HDSConstants.SMOOKS_CONFIG_FILE);
                verifyErrorPayload(javaResult);
                log.info("Deleted LUN paths {} from system {}", pathObjectIdList, systemObjectId);
            } else {
                throw HDSException.exceptions
                        .invalidResponseFromHDS(String
                                .format("Not able to delete LUN paths for system:%1$s due to invalid response %2$s from server",
                                        systemObjectId, response.getStatus()));
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
     * Construct the WWN Query by adding multiple WWNs.
     * This query should be used to add FC initiators to the FC HSD.
     * 
     * @param systemId
     * @param hsdId
     * @param lunPathObjectIdList
     * @return
     */
    private String constructDeleteLunPathsQuery(String systemId, List<String> lunPathObjectIdList, String model) {
        Map<String, Object> attributeMap = new HashMap<String, Object>();
        List<Path> pathList = new ArrayList<Path>();
        StorageArray array = new StorageArray(systemId);
        Delete deleteOp = new Delete(HDSConstants.LUN_TARGET);
        attributeMap.put(HDSConstants.STORAGEARRAY, array);
        attributeMap.put(HDSConstants.MODEL, model);
        attributeMap.put(HDSConstants.DELETE, deleteOp);

        if (null != lunPathObjectIdList && !lunPathObjectIdList.isEmpty()) {
            for (String pathObjectId : lunPathObjectIdList) {
                Path path = new Path(pathObjectId);
                pathList.add(path);
            }
        }
        attributeMap.put(HDSConstants.PATH_LIST, pathList);

        String deleteLunInputXML = InputXMLGenerationClient.getInputXMLString(
                HDSConstants.DELETE_PATH_FROM_HSD_OP, attributeMap,
                HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

        return deleteLunInputXML;
    }

}
