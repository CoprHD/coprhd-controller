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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.http.HttpStatus;
import org.milyn.payload.JavaResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.hds.HDSConstants;
import com.emc.storageos.hds.HDSException;
import com.emc.storageos.hds.model.Add;
import com.emc.storageos.hds.model.ArrayGroup;
import com.emc.storageos.hds.model.Delete;
import com.emc.storageos.hds.model.EchoCommand;
import com.emc.storageos.hds.model.Error;
import com.emc.storageos.hds.model.Get;
import com.emc.storageos.hds.model.LDEV;
import com.emc.storageos.hds.model.LogicalUnit;
import com.emc.storageos.hds.model.Modify;
import com.emc.storageos.hds.model.ObjectLabel;
import com.emc.storageos.hds.model.Pool;
import com.emc.storageos.hds.model.StorageArray;
import com.emc.storageos.hds.util.SmooksUtil;
import com.emc.storageos.hds.xmlgen.InputXMLGenerationClient;
import com.sun.jersey.api.client.ClientResponse;

/**
 * This volume manager is responsible creating volumes/delete volumes.
 * 
 */
public class HDSApiVolumeManager {

    /**
     * Logger instance to log messages.
     */
    private static final Logger log = LoggerFactory.getLogger(HDSApiVolumeManager.class);

    private HDSApiClient hdsApiClient;

    public HDSApiVolumeManager(HDSApiClient hdsApiClient) {
        this.hdsApiClient = hdsApiClient;
    }

    /**
     * Creates the Thick volume with the passed information.
     * 
     * @TODO we should add support for multi volume creation by constructing the xml with new attributes. rest will work fine.
     * @param systemId : represents SystemObjectID.
     * @param arrayGroupId : represents StoragePoolObjectID.
     * @param luCapacityInBytes: Logical Unit Capacity in bytes.
     * @param noOfLus : No. of LU's to created
     * @param volumeName : Logical Unit name.
     * @return : asyncMessageId
     * @throws Exception
     */
    public String createThickVolumes(String systemId, String arrayGroupId,
            Long luCapacityInBytes, int noOfLus, String volumeName,
            String formatType, String model, Integer devNum) throws Exception {
        Long luCapacityInKB = luCapacityInBytes / 1024;
        InputStream responseStream = null;
        String asyncTaskMessageId = null;

        try {
            Map<String, Object> attributeMap = new HashMap<String, Object>();
            StorageArray storageArray = new StorageArray(systemId);
            Pool arrayGroup = new Pool(arrayGroupId);
            Add addOp = new Add(HDSConstants.LOGICALUNIT, noOfLus, formatType);
            addOp.setBulk(Boolean.TRUE);
            LogicalUnit logicalUnit = new LogicalUnit(null, String.valueOf(luCapacityInKB),
                    volumeName, null, devNum);
            attributeMap.put(HDSConstants.STORAGEARRAY, storageArray);
            attributeMap.put(HDSConstants.ARRAY_GROUP, arrayGroup);
            attributeMap.put(HDSConstants.ADD, addOp);
            attributeMap.put(HDSConstants.MODEL, model);
            attributeMap.put(HDSConstants.LOGICALUNIT, logicalUnit);
            String createVolumeInputXML = InputXMLGenerationClient.getInputXMLString(
                    HDSConstants.CREATE_THICK_VOLUMES_OP, attributeMap,
                    HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                    HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

            log.info("Query to create thick volume: {}", createVolumeInputXML);
            URI endpointURI = hdsApiClient.getBaseURI();
            ClientResponse response = hdsApiClient.post(endpointURI, createVolumeInputXML);
            if (HttpStatus.SC_OK == response.getStatus()) {
                responseStream = response.getEntityInputStream();
                JavaResult result = SmooksUtil.getParsedXMLJavaResult(responseStream, HDSConstants.SMOOKS_CONFIG_FILE);
                EchoCommand command = result.getBean(EchoCommand.class);
                if (HDSConstants.PROCESSING_STR.equalsIgnoreCase(command.getStatus())) {
                    asyncTaskMessageId = command.getMessageID();
                } else if (HDSConstants.FAILED_STR.equalsIgnoreCase(command.getStatus())) {
                    Error error = result.getBean(Error.class);
                    log.error("Volume creation failed status messageID: {}", command.getMessageID());
                    log.error("Volume creation failed with error code: {} with message: {}", error.getCode(), error.getDescription());
                    throw HDSException.exceptions.notAbleToCreateVolume(
                            error.getCode(), error.getDescription());

                }
            } else {
                log.error("LogicalUnit creation failed with invalid response code {}",
                        response.getStatus());
                throw HDSException.exceptions
                        .invalidResponseFromHDS(String
                                .format("LogicalUnit creation failed due to invalid response %1$s from server for system %2$s",
                                        response.getStatus(), systemId));
            }
        } finally {
            if (null != responseStream) {
                try {
                    responseStream.close();
                } catch (IOException e) {
                    log.warn("Exception occurred while close volume creation response stream");
                }
            }
        }
        return asyncTaskMessageId;
    }

    /**
     * Creates the Thin volume with the passed information.
     * 
     * @param systemId : represents SystemObjectID.
     * @param arrayGroupId : represents StoragePoolObjectID.
     * @param luCapacityInBytes: Logical Unit Capacity in bytes.
     * @param noOfLus : No. of LU's to created
     * @param volumeName : Logical Unit name.
     * @param formatType : formatType.
     * @param model : model.
     * @return : asyncMessageId
     * @throws Exception
     */
    public String createThinVolumes(String systemId, String arrayGroupId,
            Long luCapacityInBytes, int noOfLus, String volumeName,
            String formatType, String model) throws Exception {
        Long luCapacityInKB = luCapacityInBytes / 1024;
        InputStream responseStream = null;
        String asyncTaskMessageId = null;
        try {
            Map<String, Object> attributeMap = new HashMap<String, Object>();
            StorageArray storageArray = new StorageArray(systemId);
            Pool arrayGroup = new Pool(null);
            Add addOp = new Add(HDSConstants.VIRTUALVOLUME, noOfLus, null);
            LogicalUnit logicalUnit = new LogicalUnit(arrayGroupId, String.valueOf(luCapacityInKB),
                    volumeName, HDSConstants.EMULATION_OPENV, null);
            attributeMap.put(HDSConstants.STORAGEARRAY, storageArray);
            attributeMap.put(HDSConstants.ARRAY_GROUP, arrayGroup);
            attributeMap.put(HDSConstants.ADD, addOp);
            attributeMap.put(HDSConstants.LOGICALUNIT, logicalUnit);
            attributeMap.put(HDSConstants.MODEL, model);
            String createVolumeInputXML = InputXMLGenerationClient.getInputXMLString(
                    HDSConstants.CREATE_THIN_VOLUMES_OP, attributeMap,
                    HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                    HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

            log.info("Query to create thin Volume: {}", createVolumeInputXML);
            URI endpointURI = hdsApiClient.getBaseURI();
            ClientResponse response = hdsApiClient.post(endpointURI, createVolumeInputXML);
            if (HttpStatus.SC_OK == response.getStatus()) {
                responseStream = response.getEntityInputStream();
                JavaResult result = SmooksUtil.getParsedXMLJavaResult(responseStream, HDSConstants.SMOOKS_CONFIG_FILE);
                EchoCommand command = result.getBean(EchoCommand.class);
                if (HDSConstants.PROCESSING_STR.equalsIgnoreCase(command.getStatus())) {
                    asyncTaskMessageId = command.getMessageID();
                } else if (HDSConstants.FAILED_STR.equalsIgnoreCase(command.getStatus())) {
                    Error error = result.getBean(Error.class);
                    log.error("Thin Volume creation failed status messageID: {}", command.getMessageID());
                    log.error("Thin Volume creation failed with error code: {} with message: {}", error.getCode(), error.getDescription());
                    throw HDSException.exceptions.notAbleToCreateVolume(
                            error.getCode(), error.getDescription());

                }
            } else {
                log.error("Thin Volume creation failed with invalid response code {}",
                        response.getStatus());
                throw HDSException.exceptions
                        .invalidResponseFromHDS(String
                                .format("Thin Volume creation failed due to invalid response %1$s from server for system %2$s",
                                        response.getStatus(), systemId));
            }
        } finally {
            if (null != responseStream) {
                try {
                    responseStream.close();
                } catch (IOException e) {
                    log.warn("Exception occurred while close thin volume creation response stream");
                }
            }
        }
        return asyncTaskMessageId;
    }

    /**
     * Modify the Virtual Volumes with the passed information.
     * 
     * @param systemId : represents SystemObjectID.
     * @param newLUCapacityInBytes: new VirtualVolume Capacity in bytes.
     * @return : asyncMessageId
     * @throws Exception
     */
    public String modifyVirtualVolume(String systemId, String luObjectId, Long newLUCapacityInBytes, String model) throws Exception {
        Long luCapacityInKB = newLUCapacityInBytes / 1024;
        InputStream responseStream = null;
        String asyncTaskMessageId = null;
        try {
            Map<String, Object> attributeMap = new HashMap<String, Object>();
            StorageArray storageArray = new StorageArray(systemId);
            Modify modifyOp = new Modify(HDSConstants.VIRTUALVOLUME, false);
            LogicalUnit logicalUnit = new LogicalUnit(luObjectId, String.valueOf(luCapacityInKB));
            attributeMap.put(HDSConstants.STORAGEARRAY, storageArray);
            attributeMap.put(HDSConstants.MODEL, model);
            attributeMap.put(HDSConstants.MODIFY, modifyOp);
            attributeMap.put(HDSConstants.LOGICALUNIT, logicalUnit);
            String modifyVolumeInputXML = InputXMLGenerationClient.getInputXMLString(
                    HDSConstants.MODIFY_THIN_VOLUME_OP, attributeMap,
                    HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                    HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

            log.info("Query to modify Thin Volume: {}", modifyVolumeInputXML);
            URI endpointURI = hdsApiClient.getBaseURI();
            ClientResponse response = hdsApiClient.post(endpointURI, modifyVolumeInputXML);
            if (HttpStatus.SC_OK == response.getStatus()) {
                responseStream = response.getEntityInputStream();
                JavaResult result = SmooksUtil.getParsedXMLJavaResult(responseStream, HDSConstants.SMOOKS_CONFIG_FILE);
                EchoCommand command = result.getBean(EchoCommand.class);
                if (HDSConstants.PROCESSING_STR.equalsIgnoreCase(command.getStatus())) {
                    asyncTaskMessageId = command.getMessageID();
                } else if (HDSConstants.FAILED_STR.equalsIgnoreCase(command.getStatus())) {
                    Error error = result.getBean(Error.class);
                    log.error("Thin Volume modification failed status messageID: {}", command.getMessageID());
                    log.error("Thin Volume modification failed with error code: {} with message: {}", error.getCode(),
                            error.getDescription());
                    throw HDSException.exceptions.notAbleToCreateVolume(
                            error.getCode(), error.getDescription());

                }
            } else {
                log.error("Thin Volume modification failed with invalid response code {}",
                        response.getStatus());
                throw HDSException.exceptions
                        .invalidResponseFromHDS(String
                                .format("Thin Volume modification failed due to invalid response %1$s from server for system %2$s",
                                        response.getStatus(), systemId));
            }
        } finally {
            if (null != responseStream) {
                try {
                    responseStream.close();
                } catch (IOException e) {
                    log.warn("Exception occurred while closing Thin volume modification response stream");
                }
            }
        }
        return asyncTaskMessageId;
    }

    /**
     * Formats the LogicalUnit.
     * 
     * @param systemObjectId
     * @param luObjectId
     * @return
     */
    public String formatLogicalUnit(String systemObjectId, String luObjectId) {
        InputStream responseStream = null;
        String asyncTaskMessageId = null;
        try {

            Map<String, Object> attributeMap = new HashMap<String, Object>();
            StorageArray storageArray = new StorageArray(systemObjectId);
            Modify modifyOp = new Modify(HDSConstants.LU_FORMAT_TARGET, true);
            LogicalUnit logicalUnit = new LogicalUnit(luObjectId, null);
            attributeMap.put(HDSConstants.STORAGEARRAY, storageArray);

            attributeMap.put(HDSConstants.MODIFY, modifyOp);
            attributeMap.put(HDSConstants.LOGICALUNIT, logicalUnit);

            String fromatVolumeInputXML = InputXMLGenerationClient.getInputXMLString(
                    HDSConstants.FORMAT_VOLUME_OP, attributeMap,
                    HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                    HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);
            log.info("Query to format LogicalUnit: {}", fromatVolumeInputXML);
            URI endpointURI = hdsApiClient.getBaseURI();
            ClientResponse response = hdsApiClient.post(endpointURI, fromatVolumeInputXML);
            if (HttpStatus.SC_OK == response.getStatus()) {
                responseStream = response.getEntityInputStream();
                JavaResult result = SmooksUtil.getParsedXMLJavaResult(responseStream, HDSConstants.SMOOKS_CONFIG_FILE);
                EchoCommand command = result.getBean(EchoCommand.class);
                if (HDSConstants.PROCESSING_STR.equalsIgnoreCase(command.getStatus())) {
                    asyncTaskMessageId = command.getMessageID();
                } else if (HDSConstants.FAILED_STR.equalsIgnoreCase(command.getStatus())) {
                    Error error = result.getBean(Error.class);
                    log.error("Query to format LogicalUnit: failed status messageID: {}", command.getMessageID());
                    log.error("LogicalUnit formatting failed with error code: {} with message: {}", error.getCode(), error.getDescription());
                    throw HDSException.exceptions.notAbleToCreateVolume(error.getCode(), error.getDescription());

                }
            } else {
                log.error("LogicalUnit format failed with invalid response code {}",
                        response.getStatus());
                throw HDSException.exceptions
                        .invalidResponseFromHDS(String
                                .format("LogicalUnit format failed due to invalid response %1$s from server for system %2$s",
                                        response.getStatus(), systemObjectId));
            }
        } finally {
            if (null != responseStream) {
                try {
                    responseStream.close();
                } catch (IOException e) {
                    log.warn("Exception occurred while closing Formatting LogicalUnit response stream");
                }
            }
        }
        return asyncTaskMessageId;
    }

    public String deleteThickLogicalUnits(String systemObjectID, Set<String> logicalUnitIdList, String model)
            throws Exception {
        InputStream responseStream = null;
        String asyncTaskMessageId = null;

        try {
            // If the LogicalUnits are LUSE, we should release them.
            releaseLUSEVolumesIfPresent(systemObjectID, logicalUnitIdList);
            Map<String, Object> attributeMap = new HashMap<String, Object>();
            StorageArray storageArray = new StorageArray(systemObjectID);
            Delete deleteOp = new Delete(HDSConstants.LOGICALUNIT);
            List<LogicalUnit> luList = new ArrayList<LogicalUnit>();
            for (String logicalUnitId : logicalUnitIdList) {
                LogicalUnit logicalUnit = new LogicalUnit(logicalUnitId, null);
                luList.add(logicalUnit);
            }
            attributeMap.put(HDSConstants.STORAGEARRAY, storageArray);
            attributeMap.put(HDSConstants.DELETE, deleteOp);
            attributeMap.put(HDSConstants.MODEL, model);
            attributeMap.put(HDSConstants.LOGICALUNIT_LIST, luList);
            String deleteVolumesInputXML = InputXMLGenerationClient.getInputXMLString(
                    HDSConstants.DELETE_VOLUMES_OP, attributeMap,
                    HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                    HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

            log.debug("volume delete payload :{}", deleteVolumesInputXML);
            URI endpointURI = hdsApiClient.getBaseURI();
            ClientResponse response = hdsApiClient.post(endpointURI,
                    deleteVolumesInputXML);
            if (HttpStatus.SC_OK == response.getStatus()) {
                responseStream = response.getEntityInputStream();
                JavaResult result = SmooksUtil.getParsedXMLJavaResult(responseStream,
                        HDSConstants.SMOOKS_CONFIG_FILE);
                EchoCommand command = result.getBean(EchoCommand.class);
                if (HDSConstants.PROCESSING_STR.equalsIgnoreCase(command.getStatus())) {
                    asyncTaskMessageId = command.getMessageID();
                } else if (HDSConstants.FAILED_STR.equalsIgnoreCase(command.getStatus())) {
                    Error error = result.getBean(Error.class);
                    log.info("command failed error code: {}", error.getCode());
                    log.info("Command failed: messageID: {} {}", command.getMessageID(),
                            error.getDescription());
                    throw HDSException.exceptions.notAbleToDeleteVolume(error.getCode(), error.getDescription());

                }
            } else {
                log.error("LogicalUnit deletion failed with invalid response code {}",
                        response.getStatus());
                throw HDSException.exceptions
                        .invalidResponseFromHDS(String
                                .format("LogicalUnit creation failed due to invalid response %1$s from server for system %2$s",
                                        response.getStatus(), systemObjectID));
            }
        } finally {
            if (null != responseStream) {
                try {
                    responseStream.close();
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
        return asyncTaskMessageId;
    }

    public String deleteThinLogicalUnits(String systemObjectID, Set<String> logicalUnitIdList, String model)
            throws Exception {
        InputStream responseStream = null;
        String asyncTaskMessageId = null;

        try {
            Map<String, Object> attributeMap = new HashMap<String, Object>();
            StorageArray storageArray = new StorageArray(systemObjectID);
            Delete deleteOp = new Delete(HDSConstants.VIRTUALVOLUME, true);
            List<LogicalUnit> luList = new ArrayList<LogicalUnit>();
            for (String logicalUnitId : logicalUnitIdList) {
                LogicalUnit logicalUnit = new LogicalUnit(logicalUnitId, null);
                luList.add(logicalUnit);
            }
            attributeMap.put(HDSConstants.STORAGEARRAY, storageArray);
            attributeMap.put(HDSConstants.DELETE, deleteOp);
            attributeMap.put(HDSConstants.MODEL, model);
            attributeMap.put(HDSConstants.LOGICALUNIT_LIST, luList);
            String deleteVolumesInputXML = InputXMLGenerationClient.getInputXMLString(
                    HDSConstants.DELETE_VOLUMES_OP, attributeMap,
                    HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                    HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

            log.info("volume delete payload :{}", deleteVolumesInputXML);
            URI endpointURI = hdsApiClient.getBaseURI();
            ClientResponse response = hdsApiClient.post(endpointURI,
                    deleteVolumesInputXML);
            if (HttpStatus.SC_OK == response.getStatus()) {
                responseStream = response.getEntityInputStream();
                JavaResult result = SmooksUtil.getParsedXMLJavaResult(responseStream,
                        HDSConstants.SMOOKS_CONFIG_FILE);
                EchoCommand command = result.getBean(EchoCommand.class);
                if (HDSConstants.PROCESSING_STR.equalsIgnoreCase(command.getStatus())) {
                    asyncTaskMessageId = command.getMessageID();
                } else if (HDSConstants.FAILED_STR.equalsIgnoreCase(command.getStatus())) {
                    Error error = result.getBean(Error.class);
                    log.info("command failed error code: {}", error.getCode());
                    log.info("Command failed: messageID: {} {}", command.getMessageID(),
                            error.getDescription());
                    throw HDSException.exceptions.notAbleToDeleteVolume(error.getCode(), error.getDescription());

                }
            } else {
                log.error("LogicalUnit deletion failed with invalid response code {}",
                        response.getStatus());
                throw HDSException.exceptions
                        .invalidResponseFromHDS(String
                                .format("LogicalUnit creation failed due to invalid response %1$s from server for system %2$s",
                                        response.getStatus(), systemObjectID));
            }
        } finally {
            if (null != responseStream) {
                try {
                    responseStream.close();
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
        return asyncTaskMessageId;
    }

    /**
     * When we delete a logicalunit we should first check whether it is LUSE volume or not.
     * If it is LUSE volume, then we should release LUSE and delete all volumes.
     * 
     * @param systemObjectID
     * @param logicalUnitIdList
     */
    private void releaseLUSEVolumesIfPresent(String systemObjectID,
            Set<String> logicalUnitIdList) {
        if (null != logicalUnitIdList && !logicalUnitIdList.isEmpty()) {
            for (String logicalUnitObjectId : logicalUnitIdList) {
                // Query the Device Manager to get logicalUnit details.
                try {
                    LogicalUnit logicalUnit = getLogicalUnitInfo(systemObjectID, logicalUnitObjectId);
                    if (null == logicalUnit || logicalUnit.getComposite() == 0) {
                        continue;
                    }
                    if (logicalUnit.getComposite() == 1) {
                        // Releasing LUSE don't delete underlying LDEV.
                        // Should we delete them?
                        releaseLUSE(systemObjectID, logicalUnitObjectId);
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }

            }
        }
    }

    /**
     * Return the storagepool information.
     * 
     * @param systemObjectId
     * @param poolObjectId
     * @return
     * @throws Exception
     */
    public Pool getStoragePoolInfo(String systemObjectId, String poolObjectId) throws Exception {
        InputStream responseStream = null;
        Pool storagePool = null;
        String poolMethodType = null;
        Map<String, Object> attributeMap = new HashMap<String, Object>();
        StorageArray storageArray = new StorageArray(systemObjectId);
        attributeMap.put(HDSConstants.STORAGEARRAY, storageArray);
        Get getOp = new Get(HDSConstants.STORAGEARRAY);
        attributeMap.put(HDSConstants.GET, getOp);
        Pool pool = new Pool(poolObjectId);

        if (poolObjectId.contains(HDSConstants.ARRAYGROUP)) {
            attributeMap.put(HDSConstants.ARRAY_GROUP, pool);
            poolMethodType = HDSConstants.GET_ARRAYGROUP_INFO_OP;
        } else if (poolObjectId.contains(HDSConstants.JOURNALPOOL)) {
            attributeMap.put(HDSConstants.JOURNAL_POOL, pool);
            poolMethodType = HDSConstants.GET_JOURNAL_POOL_INFO_OP;
        }
        String getStoragePoolInputXML = InputXMLGenerationClient.getInputXMLString(
                poolMethodType, attributeMap,
                HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

        URI endpointURI = hdsApiClient.getBaseURI();
        log.info("Storagepool info query payload :{}", getStoragePoolInputXML);
        ClientResponse response = hdsApiClient.post(endpointURI, getStoragePoolInputXML);
        if (HttpStatus.SC_OK == response.getStatus()) {
            responseStream = response.getEntityInputStream();
            JavaResult result = SmooksUtil.getParsedXMLJavaResult(responseStream, HDSConstants.SMOOKS_CONFIG_FILE);
            verifyErrorPayload(result);
            storagePool = result.getBean(Pool.class);

        } else {
            log.error("Get StoragePool info failed with invalid response code {}",
                    response.getStatus());
            throw HDSException.exceptions
                    .invalidResponseFromHDS(String
                            .format("Not able to query StoragePool info due to invalid response %1$s from server for system %2$s",
                                    response.getStatus(), systemObjectId));
        }
        return storagePool;
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
            if (command != null) {
                log.info("Error response received for messageID", command.getMessageID());
            }
            log.info("command failed with error code: {} with message {}",
                    error.getCode(), error.getDescription());
            throw HDSException.exceptions.errorResponseReceived(
                    error.getCode(), error.getDescription());
        }
    }

    /**
     * Return the LogicalUnit info for the given logicalUnitObjectId.
     * 
     * @param systemObjectId
     * @param logicalUnitObjectId
     * @return
     * @throws Exception
     */
    public LogicalUnit getLogicalUnitInfo(String systemObjectId, String logicalUnitObjectId) throws Exception {
        InputStream responseStream = null;
        LogicalUnit logicalUnit = null;

        Map<String, Object> attributeMap = new HashMap<String, Object>();
        StorageArray storageArray = new StorageArray(systemObjectId);
        Get getOp = new Get(HDSConstants.STORAGEARRAY);
        attributeMap.put(HDSConstants.STORAGEARRAY, storageArray);
        attributeMap.put(HDSConstants.GET, getOp);
        LogicalUnit lu = new LogicalUnit(logicalUnitObjectId, null);
        attributeMap.put(HDSConstants.LOGICALUNIT, lu);

        String getLogicalUnitsInputXML = InputXMLGenerationClient.getInputXMLString(
                HDSConstants.GET_LOGICALUNITS_OP, attributeMap,
                HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

        URI endpointURI = hdsApiClient.getBaseURI();
        log.info("Volume info query payload :{}", getLogicalUnitsInputXML);
        ClientResponse response = hdsApiClient.post(endpointURI, getLogicalUnitsInputXML);
        if (HttpStatus.SC_OK == response.getStatus()) {
            responseStream = response.getEntityInputStream();
            JavaResult result = SmooksUtil.getParsedXMLJavaResult(responseStream, HDSConstants.SMOOKS_CONFIG_FILE);
            verifyErrorPayload(result);
            logicalUnit = (LogicalUnit) result.getBean(HDSConstants.LOGICALUNIT_BEAN_NAME);
        } else {
            log.error("Get LogicalUnit info failed with invalid response code {}",
                    response.getStatus());
            throw HDSException.exceptions
                    .invalidResponseFromHDS(String
                            .format("Not able to query LogicalUnit info due to invalid response %1$s from server for system %2$s",
                                    response.getStatus(), systemObjectId));
        }
        return logicalUnit;
    }

    /**
     * Form a single meta volume by concatenating multiple volumes.
     * 
     * @param systemObjectId
     * @param ldevIds
     * @return
     * @throws Exception
     */
    public LogicalUnit addLUSE(String systemObjectId, String metaHead, List<String> ldevIds) throws Exception {
        String addLUSEQuery = constructAddLUSEQuery(systemObjectId, metaHead, ldevIds);
        URI endpointURI = hdsApiClient.getBaseURI();
        InputStream responseStream = null;
        LogicalUnit logicalUnit = null;
        try {
            log.info("Add LUSE Query payload :{}", addLUSEQuery);
            ClientResponse response = hdsApiClient.post(endpointURI, addLUSEQuery);
            if (HttpStatus.SC_OK == response.getStatus()) {
                responseStream = response.getEntityInputStream();
                JavaResult result = SmooksUtil.getParsedXMLJavaResult(responseStream, HDSConstants.SMOOKS_CONFIG_FILE);
                verifyErrorPayload(result);
                logicalUnit = (LogicalUnit) result.getBean(HDSConstants.LOGICALUNIT_BEAN_NAME);
            } else {
                log.error("AddLUSE failed with invalid response code {}",
                        response.getStatus());
                throw HDSException.exceptions
                        .invalidResponseFromHDS(String
                                .format("Not able to Add LUSE due to invalid response %1$s from server for system %2$s",
                                        response.getStatus(), systemObjectId));
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
        return logicalUnit;
    }

    /**
     * This client method is responsible to release all the volumes in LUSE volume.
     * 
     * @param systemObjectId
     * @param logicalUnitId
     * @return
     * @throws Exception
     */
    public LogicalUnit releaseLUSE(String systemObjectId, String logicalUnitId) throws Exception {

        Map<String, Object> attributeMap = new HashMap<String, Object>();
        StorageArray storageArray = new StorageArray(systemObjectId);
        attributeMap.put(HDSConstants.STORAGEARRAY, storageArray);
        Add addOp = new Add(HDSConstants.LUSE_TARGET);
        attributeMap.put(HDSConstants.GET, addOp);
        attributeMap.put(HDSConstants.LOGICALUNIT, logicalUnitId);
        String releaseLUSEVolumeInputXML = InputXMLGenerationClient.getInputXMLString(
                HDSConstants.RELEASE_LUSE_VOLUME_OP, attributeMap,
                HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

        URI endpointURI = hdsApiClient.getBaseURI();
        InputStream responseStream = null;
        LogicalUnit logicalUnit = null;
        try {
            log.info("release LUSE Query payload :{}", releaseLUSEVolumeInputXML);
            ClientResponse response = hdsApiClient.post(endpointURI, releaseLUSEVolumeInputXML);
            if (HttpStatus.SC_OK == response.getStatus()) {
                responseStream = response.getEntityInputStream();
                JavaResult result = SmooksUtil.getParsedXMLJavaResult(responseStream, HDSConstants.SMOOKS_CONFIG_FILE);
                verifyErrorPayload(result);
                logicalUnit = (LogicalUnit) result.getBean(HDSConstants.LOGICALUNIT_BEAN_NAME);
            } else {
                log.error("deleteLUSE failed with invalid response code {}",
                        response.getStatus());
                throw HDSException.exceptions
                        .invalidResponseFromHDS(String
                                .format("Not able to delete LUSE due to invalid response %1$s from server for system %2$s",
                                        response.getStatus(), systemObjectId));
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
        return logicalUnit;
    }

    private String constructAddLUSEQuery(String systemId, String metaHead, List<String> ldevIds) {
        Map<String, Object> attributeMap = new HashMap<String, Object>();
        List<LDEV> ldevsList = new LinkedList<LDEV>();
        StorageArray storageArray = new StorageArray(systemId);
        attributeMap.put(HDSConstants.STORAGEARRAY, storageArray);
        Add addOp = new Add(HDSConstants.LUSE_TARGET, true);
        attributeMap.put(HDSConstants.ADD, addOp);
        if (null != ldevIds && !ldevIds.isEmpty()) {
            LDEV metaHeadLDEV = new LDEV(metaHead);
            ldevsList.add(metaHeadLDEV);
            for (String ldevId : ldevIds) {
                LDEV metaMemberLDEV = new LDEV(ldevId);
                ldevsList.add(metaMemberLDEV);
            }
        }
        attributeMap.put("LDEV_List", ldevsList);
        String addLUSEVolumeInputXML = InputXMLGenerationClient.getInputXMLString(
                HDSConstants.ADD_LUSE_VOLUME_OP, attributeMap,
                HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);
        return addLUSEVolumeInputXML;
    }

    /**
     * Returns all LogicalUnits of a given system.
     * 
     * @param systemObjectId
     * @return
     */
    public List<LogicalUnit> getAllLogicalUnits(String systemObjectId) throws Exception {
        InputStream responseStream = null;
        List<LogicalUnit> luList = null;
        Map<String, Object> attributeMap = new HashMap<String, Object>();
        StorageArray storageArray = new StorageArray(systemObjectId);
        attributeMap.put(HDSConstants.STORAGEARRAY, storageArray);
        Get getOp = new Get(HDSConstants.STORAGEARRAY);
        attributeMap.put(HDSConstants.GET, getOp);
        LogicalUnit lu = new LogicalUnit();
        attributeMap.put(HDSConstants.LOGICALUNIT, lu);

        String getLogicalUnitsInputXML = InputXMLGenerationClient.getInputXMLString(
                HDSConstants.GET_LOGICALUNITS_OP, attributeMap,
                HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

        URI endpointURI = hdsApiClient.getBaseURI();
        log.info("Get all LogicalUnits query payload :{}", getLogicalUnitsInputXML);
        ClientResponse response = hdsApiClient.post(endpointURI, getLogicalUnitsInputXML);
        if (HttpStatus.SC_OK == response.getStatus()) {
            responseStream = response.getEntityInputStream();
            JavaResult result = SmooksUtil.getParsedXMLJavaResult(responseStream, HDSConstants.SMOOKS_CONFIG_FILE);
            verifyErrorPayload(result);
            luList = (List<LogicalUnit>) result.getBean(HDSConstants.LOGICALUNIT_LIST_BEAN_NAME);
        } else {
            log.error("Get all LogicalUnits failed with invalid response code {}",
                    response.getStatus());
            throw HDSException.exceptions
                    .invalidResponseFromHDS(String
                            .format("Not able to query all LogicalUnits due to invalid response %1$s from server for system %2$s",
                                    response.getStatus(), systemObjectId));
        }
        return luList;
    }

    /**
     * Adds the label to an Object in DeviceManager.
     * Currently this is supported for labeling LDEV.
     * So, targetID must be a LDEV ID of a LU.
     * 
     * @param targetID
     * @param label
     * @return
     * @throws Exception
     */
    public ObjectLabel addLabelToObject(String targetID, String label) throws Exception {
        InputStream responseStream = null;
        ObjectLabel objectLabel = null;
        Map<String, Object> attributeMap = new HashMap<String, Object>();
        Add addOp = new Add(HDSConstants.OBJECTLABEL);
        addOp.setOverwrite(Boolean.TRUE);
        attributeMap.put(HDSConstants.ADD, addOp);
        ObjectLabel objectLabelReq = new ObjectLabel(targetID, label);
        attributeMap.put(HDSConstants.OBJECTLABEL, objectLabelReq);

        String addLabelToObject = InputXMLGenerationClient.getInputXMLString(
                HDSConstants.ADD_LABEL_TO_OBJECT_OP, attributeMap,
                HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

        URI endpointURI = hdsApiClient.getBaseURI();
        log.info("Add Label to Object payload :{}", addLabelToObject);
        ClientResponse response = hdsApiClient.post(endpointURI, addLabelToObject);
        if (HttpStatus.SC_OK == response.getStatus()) {
            responseStream = response.getEntityInputStream();
            JavaResult result = SmooksUtil.getParsedXMLJavaResult(responseStream, HDSConstants.SMOOKS_CONFIG_FILE);
            verifyErrorPayload(result);
            objectLabel = result.getBean(ObjectLabel.class);
        } else {
            log.error("Add label to Object failed with invalid response code {}",
                    response.getStatus());
            throw HDSException.exceptions
                    .invalidResponseFromHDS(String
                            .format("Not able to Add Label to object due to invalid response %1$s from server",
                                    response.getStatus()));
        }
        return objectLabel;
    }

    public String modifyThinVolumeTieringPolicy(String systemObjectID, String luObjectID,
            String ldevObjectID, String tieringPolicyName, String model) {
        InputStream responseStream = null;
        String asyncTaskMessageId = null;
        Map<String, Object> attributeMap = new HashMap<String, Object>();
        Modify modifyOp = new Modify(HDSConstants.VIRTUALVOLUME);
        StorageArray array = new StorageArray(systemObjectID);
        LogicalUnit logicalUnit = new LogicalUnit();
        logicalUnit.setObjectID(luObjectID);
        LDEV ldev = new LDEV(ldevObjectID);
        ldev.setTierLevel(Integer.parseInt(tieringPolicyName));
        attributeMap.put(HDSConstants.STORAGEARRAY, array);
        attributeMap.put(HDSConstants.MODIFY, modifyOp);
        attributeMap.put(HDSConstants.MODEL, model);
        attributeMap.put(HDSConstants.LOGICALUNIT, logicalUnit);
        attributeMap.put(HDSConstants.LDEV, ldev);

        String modifyThinVolumeTieringPolicyPayload = InputXMLGenerationClient.getInputXMLString(
                HDSConstants.MODIFY_THIN_VOLUME_OP, attributeMap,
                HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

        URI endpointURI = hdsApiClient.getBaseURI();
        log.info("Modify Volume TieringPolicy payload:{}", modifyThinVolumeTieringPolicyPayload);
        ClientResponse response = hdsApiClient.post(endpointURI, modifyThinVolumeTieringPolicyPayload);
        if (HttpStatus.SC_OK == response.getStatus()) {
            responseStream = response.getEntityInputStream();
            JavaResult result = SmooksUtil.getParsedXMLJavaResult(responseStream, HDSConstants.SMOOKS_CONFIG_FILE);
            EchoCommand command = result.getBean(EchoCommand.class);
            if (HDSConstants.PROCESSING_STR.equalsIgnoreCase(command.getStatus())) {
                asyncTaskMessageId = command.getMessageID();
            } else if (HDSConstants.FAILED_STR.equalsIgnoreCase(command.getStatus())) {
                Error error = result.getBean(Error.class);
                log.error("Modify Volume TieringPolicy failed status messageID: {}", command.getMessageID());
                log.error("Modify Volume TieringPolicy failed with error code: {} with message: {}", error.getCode(),
                        error.getDescription());
                throw HDSException.exceptions.notAbleToCreateVolume(
                        error.getCode(), error.getDescription());

            }
        } else {
            log.error("Modify Volume TieringPolicy failed with invalid response code {}",
                    response.getStatus());
            throw HDSException.exceptions
                    .invalidResponseFromHDS(String
                            .format("Modify Volume TieringPolicy failed due to invalid response %1$s from server for system %2$s",
                                    response.getStatus(), systemObjectID));
        }
        return asyncTaskMessageId;
    }

    public String createSnapshotVolume(String systemObjectId, Long luCapacityInBytes, String model) throws Exception {

        Long luCapacityInKB = luCapacityInBytes / 1024;
        InputStream responseStream = null;
        String asyncTaskMessageId = null;

        try {
            log.info("Creating snapshot with {}KB size on Storage System {}", luCapacityInKB, systemObjectId);
            Map<String, Object> attributeMap = new HashMap<String, Object>();

            Add addOp = new Add(HDSConstants.VIRTUALVOLUME);
            StorageArray storageArray = new StorageArray(systemObjectId);
            ArrayGroup arrayGroup = new ArrayGroup();
            arrayGroup.setType("2");
            LogicalUnit logicalUnit = new LogicalUnit();
            logicalUnit.setCapacityInKB(String.valueOf(luCapacityInKB));
            logicalUnit.setEmulation(HDSConstants.EMULATION_OPENV);

            attributeMap.put(HDSConstants.STORAGEARRAY, storageArray);
            attributeMap.put(HDSConstants.MODEL, model);
            attributeMap.put(HDSConstants.ARRAY_GROUP, arrayGroup);
            attributeMap.put(HDSConstants.ADD, addOp);
            attributeMap.put(HDSConstants.LOGICALUNIT, logicalUnit);

            String createSnapshotInputXML = InputXMLGenerationClient.getInputXMLString(
                    HDSConstants.CREATE_SNAPSHOT_VOLUME_OP, attributeMap,
                    HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                    HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

            log.info("Query to create snapshot Volume: {}", createSnapshotInputXML);
            URI endpointURI = hdsApiClient.getBaseURI();
            ClientResponse response = hdsApiClient.post(endpointURI, createSnapshotInputXML);
            if (HttpStatus.SC_OK == response.getStatus()) {
                responseStream = response.getEntityInputStream();
                JavaResult result = SmooksUtil.getParsedXMLJavaResult(responseStream, HDSConstants.SMOOKS_CONFIG_FILE);
                EchoCommand command = result.getBean(EchoCommand.class);
                if (HDSConstants.PROCESSING_STR.equalsIgnoreCase(command.getStatus())) {
                    asyncTaskMessageId = command.getMessageID();
                } else if (HDSConstants.FAILED_STR.equalsIgnoreCase(command.getStatus())) {
                    Error error = result.getBean(Error.class);
                    log.error("Thin snapshot creation failed status messageID: {}", command.getMessageID());
                    log.error("Thin snapshot creation failed with error code: {} with message: {}", error.getCode(), error.getDescription());
                    throw HDSException.exceptions.notAbleToCreateSnapshot(
                            error.getCode(), error.getDescription());
                }
            } else {
                log.error("Thin snapshot creation failed with invalid response code {}",
                        response.getStatus());
                throw HDSException.exceptions
                        .invalidResponseFromHDS(String
                                .format("Thin snapshot creation failed due to invalid response %1$s from server for system %2$s",
                                        response.getStatus(), systemObjectId));
            }
            log.info("Snapshot creation initiated on Storage System {}", systemObjectId);
        } finally {
            if (null != responseStream) {
                try {
                    responseStream.close();
                } catch (IOException e) {
                    log.warn("Exception occurred while closing snapshot creation response stream");
                }
            }
        }
        return asyncTaskMessageId;

    }

    public String deleteSnapshotVolume(String storageSystemObjId, String logicalUnitObjId, String model)
            throws Exception {

        String asyncTaskMessageId = null;
        InputStream responseStream = null;
        try {
            if (null != storageSystemObjId && null != logicalUnitObjId) {
                log.info("Deleting snapshot with id {} from Storage System {}", logicalUnitObjId, storageSystemObjId);
                Map<String, Object> attributeMap = new HashMap<String, Object>();

                Delete deleteOp = new Delete(HDSConstants.VIRTUALVOLUME);
                StorageArray storageArray = new StorageArray(storageSystemObjId);
                LogicalUnit logicalUnit = new LogicalUnit();
                logicalUnit.setObjectID(logicalUnitObjId);

                attributeMap.put(HDSConstants.DELETE, deleteOp);
                attributeMap.put(HDSConstants.MODEL, model);
                attributeMap.put(HDSConstants.STORAGEARRAY, storageArray);
                attributeMap.put(HDSConstants.LOGICALUNIT, logicalUnit);

                String createSnapshotInputXML = InputXMLGenerationClient.getInputXMLString(
                        HDSConstants.DELETE_SNAPSHOT_VOLUME_OP, attributeMap,
                        HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                        HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

                log.info("Query to delete snapshot Volume: {}", createSnapshotInputXML);
                URI endpointURI = hdsApiClient.getBaseURI();
                ClientResponse response = hdsApiClient.post(endpointURI, createSnapshotInputXML);
                if (HttpStatus.SC_OK == response.getStatus()) {
                    responseStream = response.getEntityInputStream();
                    JavaResult result = SmooksUtil.getParsedXMLJavaResult(responseStream, HDSConstants.SMOOKS_CONFIG_FILE);
                    EchoCommand command = result.getBean(EchoCommand.class);
                    if (HDSConstants.PROCESSING_STR.equalsIgnoreCase(command.getStatus())) {
                        asyncTaskMessageId = command.getMessageID();
                    } else if (HDSConstants.FAILED_STR.equalsIgnoreCase(command.getStatus())) {
                        Error error = result.getBean(Error.class);
                        log.error("Snapshot volume deletion failed status messageID: {}", command.getMessageID());
                        log.error("Snapshot volume failed with error code: {} with message: {}", error.getCode(), error.getDescription());
                        throw HDSException.exceptions.notAbleToDeleteSnapshot(
                                error.getCode(), error.getDescription());
                    }
                } else {
                    log.error("Snapshot deletion failed with invalid response code {}",
                            response.getStatus());
                    throw HDSException.exceptions
                            .invalidResponseFromHDS(String
                                    .format("Snapshot deletion failed due to invalid response %1$s from server for system %2$s",
                                            response.getStatus(), storageSystemObjId));
                }
                log.info("Snapshot with id {} deleted from Storage System {}", logicalUnitObjId, storageSystemObjId);
            }
        } finally {
            if (null != responseStream) {
                try {
                    responseStream.close();
                } catch (IOException e) {
                    log.warn("Exception occurred while closing snapshot deletion response stream");
                }
            }
        }
        return asyncTaskMessageId;
    }

}
