/*
 * Copyright (c) 2014 EMC Corporation
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
import com.emc.storageos.hds.model.ConfigFile;
import com.emc.storageos.hds.model.Delete;
import com.emc.storageos.hds.model.EchoCommand;
import com.emc.storageos.hds.model.Error;
import com.emc.storageos.hds.model.Get;
import com.emc.storageos.hds.model.HDSHost;
import com.emc.storageos.hds.model.Modify;
import com.emc.storageos.hds.model.ReplicationGroup;
import com.emc.storageos.hds.model.ReplicationInfo;
import com.emc.storageos.hds.model.SnapshotGroup;
import com.emc.storageos.hds.util.SmooksUtil;
import com.emc.storageos.hds.xmlgen.InputXMLGenerationClient;
import com.google.gson.internal.Pair;
import com.sun.jersey.api.client.ClientResponse;

public class HDSApiProtectionManager {
    /**
     * Logger instance to log messages.
     */
    private static final Logger log = LoggerFactory.getLogger(HDSApiProtectionManager.class);

    public static enum ShadowImageOperationType {
        split,
        resync,
        restore
    }

    private HDSApiClient hdsApiClient;

    public HDSApiProtectionManager(HDSApiClient hdsApiClient) {
        this.hdsApiClient = hdsApiClient;
    }

    /**
     * Returns ViPR-Replication-Group objectID collected from Device manager to create ShadowImage pair on
     * Hitachi StorageSystem.
     * 
     * @return replicationGroup's objectID
     * @throws Exception
     */
    public String getReplicationGroupObjectId() throws Exception {

        List<HDSHost> hostList = getHDSHostList();
        String replicationGroupObjectId = null;
        HDSHost pairMgmtServer = null;
        if (hostList != null) {
            outerloop: for (HDSHost hdsHost : hostList) {
                log.info("HDSHost :{}", hdsHost.toXMLString());
                if (hdsHost != null && hdsHost.getConfigFileList() != null
                        && !hdsHost.getConfigFileList().isEmpty()) {
                    for (ConfigFile configFile : hdsHost.getConfigFileList()) {
                        if (configFile != null) {
                            ReplicationGroup replicationGroup = configFile.getReplicationGroup();
                            if (replicationGroup != null
                                    && HDSConstants.VIPR_REPLICATION_GROUP_NAME.equalsIgnoreCase(replicationGroup.getGroupName())) {
                                pairMgmtServer = hdsHost;
                                log.info("Pair management server {} found", pairMgmtServer.getName());
                                replicationGroupObjectId = replicationGroup.getObjectID();
                                log.info("ViPR Replication Group {} found", replicationGroup.toXMLString());
                                break outerloop;
                            }
                        }
                    }
                }
            }
        }
        return replicationGroupObjectId;

    }

    /**
     * Return's replicationGroup's objectID and replicationInfo's objectID for the given P-VOl and S-VOL
     * 
     * @param primaryVolumeNativeId
     * @param secondaryVolumeNativeId
     * @return {@link Map} replicationGroup's objectID and replicationInfo's objectID
     * @throws Exception
     */
    public Map<String, String> getReplicationRelatedObjectIds(String primaryVolumeNativeId,
            String secondaryVolumeNativeId) throws Exception {
        Map<String, String> objectIds = new HashMap<>();
        log.info("primaryVolumeNativeId :{}", primaryVolumeNativeId);
        log.info("secondaryVolumeNativeId :{}", secondaryVolumeNativeId);
        List<HDSHost> hostList = getHDSHostList();
        if (hostList != null) {
            for (HDSHost hdsHost : hostList) {
                log.info("HDSHost :{}", hdsHost.toXMLString());
                if (hdsHost != null && hdsHost.getConfigFileList() != null
                        && !hdsHost.getConfigFileList().isEmpty()) {
                    for (ConfigFile configFile : hdsHost.getConfigFileList()) {
                        if (configFile != null) {
                            ReplicationGroup replicationGroup = configFile.getReplicationGroup();
                            if (replicationGroup != null && replicationGroup.getReplicationInfoList() != null) {
                                log.debug("replicationGroup :{}", replicationGroup.toXMLString());
                                List<ReplicationInfo> replicationInfoList = replicationGroup.
                                        getReplicationInfoList();
                                if (replicationInfoList != null) {
                                    for (ReplicationInfo replicationInfo : replicationInfoList) {
                                        log.debug("replicationInfo :{}", replicationInfo.toXMLString());
                                        if (primaryVolumeNativeId.equalsIgnoreCase(replicationInfo.getPvolDevNum())
                                                && secondaryVolumeNativeId.equalsIgnoreCase(replicationInfo.getSvolDevNum())) {
                                            objectIds.put(HDSConstants.REPLICATION_INFO_OBJ_ID, replicationInfo.getObjectID());
                                            objectIds.put(HDSConstants.REPLICATION_GROUP_OBJ_ID, replicationGroup.getObjectID());
                                            log.info("objectIds :{}", objectIds);
                                            return objectIds;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        log.error("Unable to get replication information from pair management server");
        return objectIds;
    }

    public Pair<ReplicationInfo, String> getReplicationInfoFromSystem(String sourceNativeId, String targetNativeId) throws Exception {

        log.info("sourceNativeId :{}", sourceNativeId);
        log.info("targetNativeId :{}", targetNativeId);

        List<HDSHost> hostList = getHDSHostList();
        if (hostList != null) {
            for (HDSHost hdsHost : hostList) {
                log.info("HDSHost :{}", hdsHost.toXMLString());
                if (hdsHost != null && hdsHost.getConfigFileList() != null
                        && !hdsHost.getConfigFileList().isEmpty()) {
                    for (ConfigFile configFile : hdsHost.getConfigFileList()) {
                        if (configFile != null) {
                            ReplicationGroup replicationGroup = configFile.getReplicationGroup();
                            if (replicationGroup != null && replicationGroup.getReplicationInfoList() != null) {
                                log.info("replicationGroup :{}", replicationGroup.toXMLString());
                                List<ReplicationInfo> replicationInfoList = replicationGroup.
                                        getReplicationInfoList();
                                if (replicationInfoList != null) {
                                    for (ReplicationInfo replicationInfo : replicationInfoList) {
                                        log.debug("replicationInfo :{}", replicationInfo.toXMLString());
                                        if (sourceNativeId.equals(replicationInfo.getPvolDevNum())
                                                && targetNativeId.equals(replicationInfo.getSvolDevNum())) {
                                            log.info("Found replicationInfo object from system:{}", replicationInfo.toXMLString());
                                            log.info("Host Object Id :{}", hdsHost.getObjectID());
                                            return (new Pair<ReplicationInfo, String>(replicationInfo, hdsHost.getObjectID()));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // If we are here there is no matching replication info available on pair management server.
        log.error("Unable to find replication info details from device manager for the pvol {} svol {}", sourceNativeId, targetNativeId);
        return null;
    }

    /**
     * Returns Host List collected from Device Manager.
     * 
     * @return
     * @throws Exception
     */
    private List<HDSHost> getHDSHostList() throws Exception {
        List<HDSHost> hostList = null;

        InputStream responseStream = null;
        try {
            Map<String, Object> attributeMap = new HashMap<String, Object>();
            Get getOp = new Get(HDSConstants.HOST);
            attributeMap.put(HDSConstants.GET, getOp);
            HDSHost host = new HDSHost();
            host.setName("*");
            attributeMap.put(HDSConstants.HOST, host);

            String getAllHSDQuery = InputXMLGenerationClient.getInputXMLString(
                    HDSConstants.GET_ALL_HOST_INFO_OP, attributeMap,
                    HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                    HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

            log.info("Query to get All Host: {}", getAllHSDQuery);
            URI endpointURI = hdsApiClient.getBaseURI();
            ClientResponse response = hdsApiClient.post(endpointURI, getAllHSDQuery);
            if (HttpStatus.SC_OK == response.getStatus()) {
                responseStream = response.getEntityInputStream();
                JavaResult javaResult = SmooksUtil.getParsedXMLJavaResult(responseStream,
                        HDSConstants.HOST_INFO_SMOOKS_CONFIG_FILE);
                hdsApiClient.verifyErrorPayload(javaResult);
                hostList = (List<HDSHost>) javaResult.getBean(HDSConstants.HOST_LIST_BEAN_NAME);
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
        return hostList;
    }

    /**
     * Creates ShadowImage Pair
     * 
     * @param replicationGroupObjId
     * @param pairName
     * @param arrayType
     * @param arraySerialNumber
     * @param pvolDevNum
     * @param svolDevNum
     * @param model
     * @return {@link ReplicationInfo}
     */
    public ReplicationInfo createShadowImagePair(String replicationGroupObjId, String pairName, String arrayType,
            String arraySerialNumber, String pvolDevNum, String svolDevNum, String model) throws Exception {
        log.info("Shadow Image pair creation started");
        InputStream responseStream = null;
        ReplicationInfo replicationInfoResponse = null;
        String syncTaskMessageId = null;
        try {
            log.info("replicationGroupObjId {} "
                    , replicationGroupObjId);
            log.info("arrayType {} arraySerialNumber {}", arrayType, arraySerialNumber);
            log.info(" pvolDevNum {} svolDevNum {}", pvolDevNum, svolDevNum);
            Map<String, Object> attributeMap = new HashMap<String, Object>();
            Add addOp = new Add(HDSConstants.REPLICATION);
            ReplicationGroup replicationGroup = new ReplicationGroup();
            replicationGroup.setObjectID(replicationGroupObjId);
            replicationGroup.setReplicationFunction(HDSConstants.SHADOW_IMAGE);
            ReplicationInfo replicationInfo = new ReplicationInfo();
            replicationInfo.setPairName(pairName);
            replicationInfo.setPvolArrayType(arrayType);
            replicationInfo.setPvolSerialNumber(arraySerialNumber);
            replicationInfo.setPvolDevNum(pvolDevNum);
            replicationInfo.setSvolArrayType(arrayType);
            replicationInfo.setSvolSerialNumber(arraySerialNumber);
            replicationInfo.setSvolDevNum(svolDevNum);
            replicationInfo.setReplicationFunction(HDSConstants.SHADOW_IMAGE);

            attributeMap.put(HDSConstants.ADD, addOp);
            attributeMap.put(HDSConstants.MODEL, model);
            attributeMap.put(HDSConstants.REPLICATION_GROUP, replicationGroup);
            attributeMap.put(HDSConstants.REPLICATION_INFO, replicationInfo);

            String createShadowImagePairInputXML = InputXMLGenerationClient.getInputXMLString(
                    HDSConstants.CREATE_SHADOW_IMAGE_PAIR_OP, attributeMap,
                    HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                    HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

            log.info("Query to create shadow image pair volume: {}", createShadowImagePairInputXML);
            URI endpointURI = hdsApiClient.getBaseURI();
            ClientResponse response = hdsApiClient.post(endpointURI, createShadowImagePairInputXML);
            if (HttpStatus.SC_OK == response.getStatus()) {
                responseStream = response.getEntityInputStream();
                JavaResult result = SmooksUtil.getParsedXMLJavaResult(responseStream, HDSConstants.HITACHI_SMOOKS_REPLICATION_CONFIG_FILE);
                EchoCommand command = result.getBean(EchoCommand.class);
                if (HDSConstants.COMPLETED_STR.equalsIgnoreCase(command.getStatus())) {
                    log.info("ShadowImage Pair has been created successfully");
                    replicationInfoResponse = result.getBean(ReplicationInfo.class);
                    if (null == replicationInfoResponse) {
                        throw HDSException.exceptions.notAbleToCreateShadowImagePair();
                    }
                } else if (HDSConstants.PROCESSING_STR.equalsIgnoreCase(command.getStatus())) {
                    syncTaskMessageId = command.getMessageID();
                } else if (HDSConstants.FAILED_STR.equalsIgnoreCase(command.getStatus())) {
                    Error error = result.getBean(Error.class);
                    log.error("Shadow Image pair creation failed status messageID: {}", command.getMessageID());
                    log.error("Shadow Image pair creation failed with error code: {} with message: {}", error.getCode(),
                            error.getDescription());
                    throw HDSException.exceptions.notAbleToCreateVolume(
                            error.getCode(), error.getDescription());

                }
            } else {
                log.error("Shadow Image pair creation failed with invalid response code {}",
                        response.getStatus());
                throw HDSException.exceptions
                        .invalidResponseFromHDS(String
                                .format("Shadow Image pair creation failed due to invalid response %1$s from server for system %2$s",
                                        response.getStatus(), arraySerialNumber));
            }
        } finally {
            if (null != responseStream) {
                try {
                    responseStream.close();
                } catch (IOException e) {
                    log.warn("Exception occurred while close Shadow Image Pair creation response stream");
                }
            }
        }
        log.info("Shadow Image pair creation completed");
        return replicationInfoResponse;
    }

    /**
     * Creates ThinImage pair for HDS Snapshot
     * 
     * @param snapshotGroupObjId
     * @param hostObjId
     * @param sourNativeId
     * @param snapNativeId
     * @param thinImagePoolId
     * @return
     * @throws Exception
     */
    public boolean createThinImagePair(String snapshotGroupObjId, String hostObjId,
            String sourNativeId, String snapNativeId, String thinImagePoolId, String model) throws Exception {
        log.info("Thin Image pair creation started");
        boolean status = false;
        InputStream responseStream = null;
        String syncTaskMessageId = null;
        try {
            log.info("snapshotGroupObjId {} "
                    , snapshotGroupObjId);

            Map<String, Object> attributeMap = new HashMap<String, Object>();
            Add addOp = new Add(HDSConstants.REPLICATION);
            addOp.setOption(HDSConstants.INBAND2);

            HDSHost host = new HDSHost();
            host.setObjectID(hostObjId);
            SnapshotGroup snapshotGroup = new SnapshotGroup();
            snapshotGroup.setObjectID(snapshotGroupObjId);
            snapshotGroup.setReplicationFunction(HDSConstants.THIN_IMAGE);

            ReplicationInfo replicationInfo = new ReplicationInfo();
            replicationInfo.setPvolDevNum(sourNativeId);
            replicationInfo.setSvolDevNum(snapNativeId);
            replicationInfo.setPvolPoolID(thinImagePoolId);

            attributeMap.put(HDSConstants.ADD, addOp);
            attributeMap.put(HDSConstants.MODEL, model);
            attributeMap.put(HDSConstants.HOST, host);
            attributeMap.put(HDSConstants.SNAPSHOTGROUP, snapshotGroup);
            attributeMap.put(HDSConstants.REPLICATION_INFO, replicationInfo);

            String createThinImagePairInputXML = InputXMLGenerationClient.getInputXMLString(
                    HDSConstants.CREATE_THIN_IMAGE_PAIR_OP, attributeMap,
                    HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                    HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

            log.info("Query to create thin image pair : {}", createThinImagePairInputXML);
            URI endpointURI = hdsApiClient.getBaseURI();
            ClientResponse response = hdsApiClient.post(endpointURI, createThinImagePairInputXML);
            if (HttpStatus.SC_OK == response.getStatus()) {
                responseStream = response.getEntityInputStream();
                JavaResult result = SmooksUtil.getParsedXMLJavaResult(responseStream, HDSConstants.HITACHI_SMOOKS_THINIMAGE_CONFIG_FILE);
                EchoCommand command = result.getBean(EchoCommand.class);
                if (HDSConstants.COMPLETED_STR.equalsIgnoreCase(command.getStatus())) {
                    log.info("ThinImage Pair has been created successfully");
                    status = true;
                    SnapshotGroup snapshotGrpResponse = result.getBean(SnapshotGroup.class);
                    if (null == snapshotGrpResponse) {
                        throw HDSException.exceptions.notAbleToCreateThinImagePair();
                    }
                } else if (HDSConstants.PROCESSING_STR.equalsIgnoreCase(command.getStatus())) {
                    syncTaskMessageId = command.getMessageID();
                } else if (HDSConstants.FAILED_STR.equalsIgnoreCase(command.getStatus())) {
                    Error error = result.getBean(Error.class);
                    log.error("Thin Image pair creation failed status messageID: {}", command.getMessageID());
                    log.error("Thin Image pair creation failed with error code: {} with message: {}", error.getCode(),
                            error.getDescription());
                    throw HDSException.exceptions.notAbleToCreateThinImagePairError(
                            error.getCode(), error.getDescription());

                }
            } else {
                log.error("Thin Image pair creation failed with invalid response code {}",
                        response.getStatus());
                throw HDSException.exceptions
                        .invalidResponseFromHDS(String
                                .format("Thin Image pair creation failed due to invalid response %1$s from server",
                                        response.getStatus()));
            }
        } finally {
            if (null != responseStream) {
                try {
                    responseStream.close();
                } catch (IOException e) {
                    log.warn("Exception occurred while close Thin Image Pair creation response stream");
                }
            }
        }
        log.info("Thin Image pair creation completed");
        return status;
    }

    /**
     * Modify ShadowImage pair operation .
     * 
     * @param replicationGroupId
     * @param replicationInfoId
     * @param operationType
     * @return {@link ReplicationInfo}
     * @throws Exception
     */
    public ReplicationInfo modifyShadowImagePair(String replicationGroupId, String replicationInfoId,
            ShadowImageOperationType operationType, String model) throws Exception {

        InputStream responseStream = null;
        ReplicationInfo replicationInfo = null;
        try {
            if (replicationGroupId != null && replicationInfoId != null) {
                Map<String, Object> attributeMap = new HashMap<String, Object>();
                Modify modifyOp = new Modify();
                modifyOp.setTarget(HDSConstants.REPLICATION);
                modifyOp.setOption(operationType.name());
                ReplicationGroup replicationGroup = new ReplicationGroup();
                replicationGroup.setObjectID(replicationGroupId);

                replicationInfo = new ReplicationInfo();
                replicationInfo.setObjectID(replicationInfoId);
                attributeMap.put(HDSConstants.MODIFY, modifyOp);
                attributeMap.put(HDSConstants.MODEL, model);
                attributeMap.put(HDSConstants.REPLICATION_GROUP, replicationGroup);
                attributeMap.put(HDSConstants.REPLICATION_INFO, replicationInfo);

                String modifyPairQuery = InputXMLGenerationClient.getInputXMLString(
                        HDSConstants.MODIFY_SHADOW_IMAGE_PAIR_OP, attributeMap,
                        HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                        HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);
                log.info("Query to {} shadow image pair  Query: {}", operationType.name(), modifyPairQuery);
                URI endpointURI = hdsApiClient.getBaseURI();
                ClientResponse response = hdsApiClient.post(endpointURI, modifyPairQuery);
                if (HttpStatus.SC_OK == response.getStatus()) {
                    responseStream = response.getEntityInputStream();
                    JavaResult javaResult = SmooksUtil.getParsedXMLJavaResult(responseStream,
                            HDSConstants.HITACHI_SMOOKS_REPLICATION_CONFIG_FILE);
                    verifyErrorPayload(javaResult);
                    log.info("Successfully {}ed pair", operationType.name());
                    replicationInfo = javaResult.getBean(ReplicationInfo.class);
                    log.info("replicationInfo :{}", replicationInfo);
                    /*
                     * if (null == replicationInfo) {
                     * throw HDSException.exceptions.notAbleToCreateShadowImagePair();
                     * }
                     */
                } else {
                    throw HDSException.exceptions
                            .invalidResponseFromHDS(String
                                    .format("Not able to modify replication info due to invalid response %1$s from server",
                                            response.getStatus()));
                }
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
        return replicationInfo;
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
     * Deletes SI replicationInfo instance from replication group
     * 
     * @param replicationGroupObjId
     * @param replicationInfoObjId
     * @return {@link ReplicationInfo}
     * @throws Exception
     */
    public ReplicationInfo deleteShadowImagePair(String replicationGroupObjId, String replicationInfoObjId, String model) throws Exception {

        InputStream responseStream = null;
        ReplicationInfo replicationInfo = null;
        try {
            if (replicationGroupObjId != null && replicationInfoObjId != null) {
                Map<String, Object> attributeMap = new HashMap<String, Object>();
                Delete deleteOp = new Delete(HDSConstants.REPLICATION);
                ReplicationGroup replicationGroup = new ReplicationGroup();
                replicationGroup.setObjectID(replicationGroupObjId);

                replicationInfo = new ReplicationInfo();
                replicationInfo.setObjectID(replicationInfoObjId);
                attributeMap.put(HDSConstants.DELETE, deleteOp);
                attributeMap.put(HDSConstants.MODEL, model);
                attributeMap.put(HDSConstants.REPLICATION_GROUP, replicationGroup);
                attributeMap.put(HDSConstants.REPLICATION_INFO, replicationInfo);

                String deletePairQuery = InputXMLGenerationClient.getInputXMLString(
                        HDSConstants.DELETE_PAIR_OP, attributeMap,
                        HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                        HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);
                log.info("Query to delete shadow image pair  Query: {}", deletePairQuery);
                URI endpointURI = hdsApiClient.getBaseURI();
                ClientResponse response = hdsApiClient.post(endpointURI, deletePairQuery);
                if (HttpStatus.SC_OK == response.getStatus()) {
                    responseStream = response.getEntityInputStream();
                    JavaResult javaResult = SmooksUtil.getParsedXMLJavaResult(responseStream,
                            HDSConstants.HITACHI_SMOOKS_REPLICATION_CONFIG_FILE);
                    verifyErrorPayload(javaResult);
                    log.info("Successfully Deleted pair");
                    replicationInfo = javaResult.getBean(ReplicationInfo.class);
                    log.info("replicationInfo :{}", replicationInfo);
                    /*
                     * if (null == replicationInfo) {
                     * throw HDSException.exceptions.notAbleToCreateShadowImagePair();
                     * }
                     */
                } else {
                    throw HDSException.exceptions
                            .invalidResponseFromHDS(String
                                    .format("Not able to delete shadow image pair due to invalid response %1$s from server",
                                            response.getStatus()));
                }
            } else {
                log.info("Replication info is not available on pair management server");
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

        return replicationInfo;
    }

    /**
     * Deletes ReplicationInfo instance from SnapshotGroup
     * 
     * @param snapshotGroupObjId
     * @param replicationInfoObjId
     * @throws Exception
     */
    public void deleteThinImagePair(String hostObjId, String snapshotGroupObjId,
            String replicationInfoObjId, String model) throws Exception {

        InputStream responseStream = null;
        ReplicationInfo replicationInfo = null;
        try {
            if (hostObjId != null && snapshotGroupObjId != null && replicationInfoObjId != null) {
                Map<String, Object> attributeMap = new HashMap<String, Object>();
                Delete deleteOp = new Delete(HDSConstants.REPLICATION, HDSConstants.INBAND2);

                HDSHost host = new HDSHost();
                host.setObjectID(hostObjId);
                SnapshotGroup snapshotGroup = new SnapshotGroup();
                snapshotGroup.setObjectID(snapshotGroupObjId);
                replicationInfo = new ReplicationInfo();
                replicationInfo.setObjectID(replicationInfoObjId);

                attributeMap.put(HDSConstants.DELETE, deleteOp);
                attributeMap.put(HDSConstants.HOST, host);
                attributeMap.put(HDSConstants.MODEL, model);
                attributeMap.put(HDSConstants.SNAPSHOTGROUP, snapshotGroup);
                attributeMap.put(HDSConstants.REPLICATION_INFO, replicationInfo);

                String deleteThinImagePairInputXML = InputXMLGenerationClient.getInputXMLString(
                        HDSConstants.DELETE_THIN_IMAGE_PAIR_OP, attributeMap,
                        HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                        HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

                log.info("Query to delete thin image pair : {}", deleteThinImagePairInputXML);
                URI endpointURI = hdsApiClient.getBaseURI();
                ClientResponse response = hdsApiClient.post(endpointURI, deleteThinImagePairInputXML);

                if (HttpStatus.SC_OK == response.getStatus()) {
                    responseStream = response.getEntityInputStream();
                    JavaResult result = SmooksUtil.getParsedXMLJavaResult(responseStream,
                            HDSConstants.HITACHI_SMOOKS_THINIMAGE_CONFIG_FILE);
                    verifyErrorPayload(result);
                    log.info("Thin Image pair deleted successfully.");
                } else {
                    log.error("Thin Image pair deletion failed with invalid response code {}",
                            response.getStatus());
                    throw HDSException.exceptions
                            .invalidResponseFromHDS(String
                                    .format("Thin Image pair deletion failed due to invalid response %1$s from server",
                                            response.getStatus()));
                }
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
     * Get PairManagement Server for SnapshotGroup
     * 
     * @param serialNumber
     * @return
     * @throws Exception
     */
    public HDSHost getSnapshotGroupPairManagementServer(String serialNumber) throws Exception {

        InputStream responseStream = null;
        try {
            log.info("Started to collect Pair Mgmt Server details");
            Map<String, Object> attributeMap = new HashMap<String, Object>();
            Get getOp = new Get(HDSConstants.HOST);
            attributeMap.put(HDSConstants.GET, getOp);
            HDSHost host = new HDSHost();
            host.setName("*");
            attributeMap.put(HDSConstants.HOST, host);
            SnapshotGroup snapshotGroup = new SnapshotGroup();
            // snapshotGroup.setArrayType(arrayType);
            snapshotGroup.setSerialNumber(serialNumber);
            snapshotGroup.setGroupName(HDSConstants.VIPR_SNAPSHOT_GROUP_NAME);
            attributeMap.put(HDSConstants.SNAPSHOTGROUP, snapshotGroup);

            String getSnapshotGroupQuery = InputXMLGenerationClient.getInputXMLString(
                    HDSConstants.GET_SNAPSHOT_GROUP_INFO_OP, attributeMap,
                    HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                    HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);

            log.info("Query to pair management server Host: {}", getSnapshotGroupQuery);
            URI endpointURI = hdsApiClient.getBaseURI();
            ClientResponse response = hdsApiClient.post(endpointURI, getSnapshotGroupQuery);
            if (HttpStatus.SC_OK == response.getStatus()) {
                responseStream = response.getEntityInputStream();
                JavaResult javaResult = SmooksUtil.getParsedXMLJavaResult(responseStream,
                        HDSConstants.HITACHI_SMOOKS_THINIMAGE_CONFIG_FILE);
                hdsApiClient.verifyErrorPayload(javaResult);
                List<HDSHost> hostList = (List<HDSHost>) javaResult.getBean(HDSConstants.HOST_LIST_BEAN_NAME);
                log.info("Host List size :{}", hostList.size());
                for (HDSHost hdsHost : hostList) {
                    if (hdsHost != null && hdsHost.getSnapshotGroupList() != null) {
                        log.info("Host Name :{}", hdsHost.getName());

                        for (SnapshotGroup snapGroup : hdsHost.getSnapshotGroupList()) {
                            log.info("SnapshotGroup groupName :{}", snapGroup.getGroupName());
                            if (snapGroup != null &&
                                    HDSConstants.VIPR_SNAPSHOT_GROUP_NAME.equalsIgnoreCase(snapGroup.getGroupName())
                                    && serialNumber.equalsIgnoreCase(snapGroup.getSerialNumber())) {
                                log.info("Found ViPR snaphot group on pair mgmt server {}", hdsHost.getName());
                                return hdsHost;
                            }
                        }
                    }
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
        // If we are here there is no pair mgmt server available on storage system.
        return null;
    }

    /**
     * Restore's snapshot to source volume.
     * 
     * @param pairMgmtServerHostObjId
     * @param snapshotGroupObjId
     * @param replicationInfoObjId
     * @return
     * @throws Exception
     */
    public boolean restoreThinImagePair(String pairMgmtServerHostObjId, String snapshotGroupObjId,
            String replicationInfoObjId, String model) throws Exception {

        InputStream responseStream = null;
        ReplicationInfo replicationInfo = null;
        boolean status = false;
        try {
            if (pairMgmtServerHostObjId != null && snapshotGroupObjId != null && replicationInfoObjId != null) {
                log.info("Restore thin image pair started");
                Map<String, Object> attributeMap = new HashMap<String, Object>();
                Modify modifyOp = new Modify(HDSConstants.REPLICATION);
                modifyOp.setOption(HDSConstants.RESTORE_INBAND2);

                HDSHost host = new HDSHost();
                host.setObjectID(pairMgmtServerHostObjId);

                SnapshotGroup snapshotGroup = new SnapshotGroup();
                snapshotGroup.setObjectID(snapshotGroupObjId);

                replicationInfo = new ReplicationInfo();
                replicationInfo.setObjectID(replicationInfoObjId);

                attributeMap.put(HDSConstants.MODIFY, modifyOp);
                attributeMap.put(HDSConstants.MODEL, model);
                attributeMap.put(HDSConstants.HOST, host);
                attributeMap.put(HDSConstants.SNAPSHOTGROUP, snapshotGroup);
                attributeMap.put(HDSConstants.REPLICATION_INFO, replicationInfo);

                String restoreThinImagePairQuery = InputXMLGenerationClient.getInputXMLString(
                        HDSConstants.RESTORE_THIN_IMAGE_PAIR_OP, attributeMap,
                        HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                        HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);
                log.info("Query to restore thin image pair  Query: {}", restoreThinImagePairQuery);
                URI endpointURI = hdsApiClient.getBaseURI();
                ClientResponse response = hdsApiClient.post(endpointURI, restoreThinImagePairQuery);
                if (HttpStatus.SC_OK == response.getStatus()) {
                    responseStream = response.getEntityInputStream();
                    JavaResult javaResult = SmooksUtil.getParsedXMLJavaResult(responseStream,
                            HDSConstants.HITACHI_SMOOKS_THINIMAGE_CONFIG_FILE);
                    verifyErrorPayload(javaResult);
                    log.info("Successfully restored thin image pair");
                    status = true;
                    replicationInfo = javaResult.getBean(ReplicationInfo.class);
                    log.info("replicationInfo :{}", replicationInfo);
                    /*
                     * if (null == replicationInfo) {
                     * throw HDSException.exceptions.notAbleToCreateShadowImagePair();
                     * }
                     */
                } else {
                    throw HDSException.exceptions
                            .invalidResponseFromHDS(String
                                    .format("Not able to delete shadow image pair due to invalid response %1$s from server",
                                            response.getStatus()));
                }
            } else {
                log.info("Replication info is not available on pair management server");
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

        return status;
    }

    public void refreshPairManagementServer(String pairMgmtServerHostObjId) throws Exception {

        InputStream responseStream = null;
        try {
            if (pairMgmtServerHostObjId != null) {
                log.info("Refreshing Pair Mgmt Server started");
                Map<String, Object> attributeMap = new HashMap<String, Object>();
                Add addOp = new Add(HDSConstants.HOST_REFRESH);

                HDSHost host = new HDSHost();
                host.setObjectID(pairMgmtServerHostObjId);

                attributeMap.put(HDSConstants.ADD, addOp);
                attributeMap.put(HDSConstants.HOST, host);

                String refreshHostQuery = InputXMLGenerationClient.getInputXMLString(
                        HDSConstants.REFRESH_HOST_OP, attributeMap,
                        HDSConstants.HITACHI_INPUT_XML_CONTEXT_FILE,
                        HDSConstants.HITACHI_SMOOKS_CONFIG_FILE);
                log.info("Query to refresh pair mgmt server: {}", refreshHostQuery);
                URI endpointURI = hdsApiClient.getBaseURI();
                ClientResponse response = hdsApiClient.post(endpointURI, refreshHostQuery);
                if (HttpStatus.SC_OK == response.getStatus()) {
                    responseStream = response.getEntityInputStream();
                    JavaResult javaResult = SmooksUtil.getParsedXMLJavaResult(responseStream,
                            HDSConstants.HITACHI_SMOOKS_THINIMAGE_CONFIG_FILE);
                    verifyErrorPayload(javaResult);
                    log.info("Successfully refreshed pair mgmt server");
                } else {
                    throw HDSException.exceptions
                            .invalidResponseFromHDS(String
                                    .format("Not able to refresh pair mgmt server due to invalid response %1$s from server",
                                            response.getStatus()));
                }
            } else {
                log.info("Pair Mgmt Server Object id is null");
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

}
