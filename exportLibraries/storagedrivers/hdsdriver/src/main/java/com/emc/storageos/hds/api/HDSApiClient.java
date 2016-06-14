/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hds.api;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Set;

import org.apache.http.HttpStatus;
import org.milyn.payload.JavaResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.hds.HDSConstants;
import com.emc.storageos.hds.HDSException;
import com.emc.storageos.hds.model.EchoCommand;
import com.emc.storageos.hds.model.Error;
import com.emc.storageos.hds.model.HDSHost;
import com.emc.storageos.hds.model.LogicalUnit;
import com.emc.storageos.hds.model.ObjectLabel;
import com.emc.storageos.hds.model.Pool;
import com.emc.storageos.hds.model.StorageArray;
import com.emc.storageos.hds.util.SmooksUtil;
import com.sun.jersey.api.client.ClientResponse;

/**
 * The HDS API Client is used to get information from the HighCommand Device Manager and to execute
 * configuration commands on a Hitachi Devices. A HDSApiClient instance represents a
 * connection to a single HighCommand Device Manager server. Use the {@link HDSApiFactory} to get a HdsApiClient instance for a given
 * HighCommand
 * management server.
 */
public class HDSApiClient {

    // Logger reference.
    private static Logger log = LoggerFactory.getLogger(HDSApiClient.class);

    private static final String STATUS_QUERY = "<?xml version=\"1.0\"?><HiCommandServerMessage><APIInfo version=\"7.4.1\"/><Request><SessionManager><Get target=\"RequestStatus\"><RequestStatus messageID=\"%1$s\"/></Get></SessionManager></Request></HiCommandServerMessage>";

    // The base URI of the HDS Management Station.
    private URI baseURI;

    // The REST client for executing requests to the HDS Management Station.
    private RESTClient client;

    private HDSApiVolumeManager hdsApiVolumeManager;

    private HDSApiExportManager hdsApiExportManager;

    private HDSApiDiscoveryManager hdsApiDiscoveryManager;

    private HDSApiProtectionManager hdsApiProtectionManager;

    private HDSBatchApiExportManager hdsBatchApiExportManager;

    /**
     * Constructor
     * 
     * @param endpoint The URI of the HighCommand Device Manager.
     * @param client A reference to the REST client for making requests.
     */
    HDSApiClient(URI endpoint, RESTClient client) {
        this.baseURI = endpoint;
        this.client = client;
        this.hdsApiVolumeManager = new HDSApiVolumeManager(this);
        this.hdsApiExportManager = new HDSApiExportManager(this);
        this.hdsBatchApiExportManager = new HDSBatchApiExportManager(this);
        this.hdsApiDiscoveryManager = new HDSApiDiscoveryManager(this);
        this.hdsApiProtectionManager = new HDSApiProtectionManager(this);
    }

    /**
     * Client call to create Thick volumes.
     * 
     * @param systemObjectId
     * @param arrayGroupId
     * @param luCapacity
     * @param noOfLus
     * @param label
     * @return
     * @throws Exception
     */
    public String createThickVolumes(String systemObjectId, String arrayGroupId,
            Long luCapacity, int noOfLus, String label, String formatType, String model, Integer devNum) throws Exception {
        return hdsApiVolumeManager.createThickVolumes(systemObjectId, arrayGroupId, luCapacity, noOfLus, label, formatType, model, devNum);
    }

    /**
     * Client call to create Thick volumes.
     * 
     * @param systemObjectId
     * @param arrayGroupId
     * @param luCapacity
     * @param noOfLus
     * @param label
     * @return
     * @throws Exception
     */
    public String createThinVolumes(String systemObjectId, String arrayGroupId,
            Long luCapacity, int noOfLus, String label, String formatType,
            String model) throws Exception {
        return hdsApiVolumeManager.createThinVolumes(systemObjectId,
                arrayGroupId, luCapacity, noOfLus, label, formatType, model);
    }

    public String createSnapshotVolume(String systemObjectId,
            Long luCapacity, String model) throws Exception {
        return hdsApiVolumeManager.createSnapshotVolume(systemObjectId,
                luCapacity, model);
    }

    /**
     * Client call to delete snapshot volume
     * 
     * @param storageSystemObjId
     * @param logicalUnitObjId
     * @param model
     * @return
     * @throws Exception
     */
    public String deleteSnapshotVolume(String storageSystemObjId, String logicalUnitObjId, String model)
            throws Exception {
        return hdsApiVolumeManager.deleteSnapshotVolume(storageSystemObjId, logicalUnitObjId, model);
    }

    /**
     * Client API call to add label to
     * 
     * @param luObjectId
     * @param name
     * @throws Exception
     */
    public ObjectLabel addVolumeLabel(String luObjectId, String name) throws Exception {
        return hdsApiVolumeManager.addLabelToObject(luObjectId, name);
    }

    /**
     * Formats the given logicalunit.
     * 
     * @param systemObjectId
     * @param luObjectId
     * @return
     * @throws Exception
     */
    public String formatLogicalUnit(String systemObjectId, String luObjectId) throws Exception {
        return hdsApiVolumeManager.formatLogicalUnit(systemObjectId, luObjectId);
    }

    /**
     * Client call to create Logical units.
     * 
     * @param systemObjectId
     * @param arrayGroupId
     * @param luCapacity
     * @param noOfLus
     * @param label
     * @param isThinVolume
     * @return
     * @throws Exception
     */
    public LogicalUnit createLUSEVolume(String systemObjectId, String metaHead, List<String> ldevIds) throws Exception {
        return hdsApiVolumeManager.addLUSE(systemObjectId, metaHead, ldevIds);
    }

    /**
     * Client call to create Logical units.
     * 
     * @param systemObjectId
     * @param arrayGroupId
     * @param luCapacity
     * @param noOfLus
     * @param label
     * @param isThinVolume
     * @return
     * @throws Exception
     */
    public String modifyThinVolume(String systemObjectId, String luObjectId, Long newLUCapacityInBytes, String model) throws Exception {
        return hdsApiVolumeManager.modifyVirtualVolume(systemObjectId, luObjectId, newLUCapacityInBytes, model);
    }

    /**
     * 
     * @param systemObjectId
     * @param logicalUnitObjectId
     * @return
     */
    public LogicalUnit getLogicalUnitInfo(String systemObjectId, String logicalUnitObjectId) throws Exception {
        return hdsApiVolumeManager.getLogicalUnitInfo(systemObjectId, logicalUnitObjectId);
    }

    /**
     * Client call to delete logicalunits.
     * 
     * @param systemObjectId
     * @param logicalUnitIdList
     * @throws Exception
     */
    public String deleteThickLogicalUnits(String systemObjectId, Set<String> logicalUnitIdList, String model) throws Exception {
        return hdsApiVolumeManager.deleteThickLogicalUnits(systemObjectId, logicalUnitIdList, model);
    }

    /**
     * Client call to delete logicalunits.
     * 
     * @param systemObjectId
     * @param logicalUnitIdList
     * @throws Exception
     */
    public String deleteThinLogicalUnits(String systemObjectId, Set<String> logicalUnitIdList, String model) throws Exception {
        return hdsApiVolumeManager.deleteThinLogicalUnits(systemObjectId, logicalUnitIdList, model);
    }

    /**
     * Client call to get StoragePool Information
     * 
     * @param systemObjectId
     * @param poolObjectId
     * @return
     * @throws Exception
     */
    public Pool getStoragePoolInfo(String systemObjectId, String poolObjectId) throws Exception {
        return hdsApiVolumeManager.getStoragePoolInfo(systemObjectId, poolObjectId);
    }

    /**
     * Client call to get all Storage systems info managed by the HiCommand Device Manager.
     * 
     * @return
     * @throws Exception
     */
    public List<StorageArray> getStorageSystemsInfo() throws Exception {
        return hdsApiDiscoveryManager.getStorageSystemsInfo();
    }

    /**
     * Returns HiCommand Device Manager's API version.
     * 
     * @return apiVersion
     * @throws Exception
     */
    public String getProviderAPIVersion() throws Exception {
        return hdsApiDiscoveryManager.getProviderAPIVersion();
    }

    /**
     * Client call to get all Storage systems info managed by the HiCommand Device Manager.
     * 
     * @return
     * @throws Exception
     */
    public StorageArray getStorageSystemDetails(String systemObjectID) throws Exception {
        return hdsApiDiscoveryManager.getStorageSystemDetails(systemObjectID);
    }

    /**
     * Returns tiering policies of a given storage system.
     * 
     * @param systemObjectID
     * @return
     * @throws Exception
     */
    public StorageArray getStorageSystemTieringPolicies(String systemObjectID) throws Exception {
        return hdsApiDiscoveryManager.getStorageSystemTieringPolicyDetails(systemObjectID);
    }

    /**
     * Returns pool tier info of a given storage system.
     * 
     * @param systemObjectID
     * @return
     * @throws Exception
     */
    public Pool getStoragePoolTierInfo(String systemObjectID, String poolObjectID) throws Exception {
        return hdsApiDiscoveryManager.getStoragePoolTierInfo(systemObjectID, poolObjectID);
    }

    /**
     * Returns all pools tier info of a given storage system.
     * 
     * @param systemObjectID
     * @return
     * @throws Exception
     */
    public List<Pool> getStoragePoolsTierInfo(String systemObjectID) throws Exception {
        return hdsApiDiscoveryManager.getStoragePoolsTierInfo(systemObjectID);
    }

    /**
     * 
     * @return
     */
    public HDSApiVolumeManager getHDSApiVolumeManager() {
        return hdsApiVolumeManager;
    }

    /**
     * 
     * @return
     */
    public HDSApiExportManager getHDSApiExportManager() {
        return hdsApiExportManager;
    }

    /**
     * 
     * @return
     */
    public HDSBatchApiExportManager getHDSBatchApiExportManager() {
        return hdsBatchApiExportManager;
    }

    /**
     * 
     * @return
     */
    public HDSApiDiscoveryManager getHDSApiDiscoveryManager() {
        return hdsApiDiscoveryManager;
    }

    /**
     * Package protected getter for the base URI for the client.
     * 
     * @return The base URI for the client.
     */
    public URI getBaseURI() {
        return baseURI;
    }

    /**
     * Package protected method for executing a GET request.
     * 
     * @param resourceURI The resource URI.
     * 
     * @return The client response.
     */
    public ClientResponse get(URI resourceURI) {
        return client.get(resourceURI);
    }

    /**
     * Package protected method for executing a POST request.
     * 
     * @param resourceURI The resource URI.
     * @param postData The POST data.
     * 
     * @return The client response.
     */
    public ClientResponse post(URI resourceURI, String postData) {
        return client.post(resourceURI, postData);
    }

    /**
     * Package protected method for executing a PUT request.
     * 
     * @param resourceURI The resource URI.
     * 
     * @return The client response.
     */
    public ClientResponse put(URI resourceURI) {
        return client.put(resourceURI);
    }

    /**
     * This method is responsible to check the async task status for every 3 seconds
     * and if we don't get status in 60 retries, throw exception.
     * 
     * @param messageID : Task status for messageID
     * @return Parsed java result of response stream.
     */
    public JavaResult waitForCompletion(String messageID) throws Exception {
        log.info("Verifying the Async task status for message {}", messageID);
        InputStream responseStream = null;
        EchoCommand command = null;
        JavaResult result = null;
        String statusQueryWithParams = String.format(STATUS_QUERY, messageID);
        int retries = 0;
        do {
            try {
                log.info("retrying {}th time", retries);
                ClientResponse response = client.post(getBaseURI(), statusQueryWithParams);
                if (HttpStatus.SC_OK == response.getStatus()) {
                    responseStream = response.getEntityInputStream();
                    result = SmooksUtil.getParsedXMLJavaResult(responseStream,
                            HDSConstants.SMOOKS_CONFIG_FILE);
                    command = result.getBean(EchoCommand.class);
                    // Sleep for some time if the Async task is still
                    // processing state.
                    if (HDSConstants.PROCESSING_STR.equalsIgnoreCase(command
                            .getStatus())) {
                        log.info("Async task is still in processing state. Hence sleeping...");
                        Thread.sleep(HDSConstants.TASK_PENDING_WAIT_TIME);
                    }
                } else {
                    throw HDSException.exceptions
                            .asyncTaskInvalidResponse(response.getStatus());
                }
            } finally {
                try {
                    if (null != responseStream) {
                        responseStream.close();
                    }
                } catch (IOException ioEx) {
                    log.warn(
                            "Ignoring io exception that occurred during stream closing for async status check for messageID {}",
                            messageID);
                }
            }
        } while (HDSConstants.PROCESSING_STR.equalsIgnoreCase(command.getStatus()) && retries++ < HDSConstants.MAX_RETRIES);
        if (retries >= HDSConstants.MAX_RETRIES) {
            log.error("Async task exceeded the maximum number of retries");
            throw HDSException.exceptions
                    .asyncTaskMaximumRetriesExceed(messageID);
            // handle carefully for the generated task. is it possible to cancel the task?
        }
        if (HDSConstants.FAILED_STR.equalsIgnoreCase(command.getStatus())) {
            Error error = result.getBean(Error.class);
            String errorMessage = String
                    .format("Async task failed for messageID %s due to %s with error code: %d",
                            messageID, error.getDescription(),
                            error.getCode());
            log.error(errorMessage);
            HDSException.exceptions.asyncTaskFailedWithErrorResponse(messageID, error.getDescription(), error.getCode());
            throw new Exception(errorMessage);
        }
        log.info("Async task completed for messageID {}", messageID);
        return result;
    }

    /**
     * This method is responsible to check the async task status.
     * 
     * @param messageID : Task status for messageID
     * @return Parsed java result of response stream.
     */
    public JavaResult checkAsyncTaskStatus(String messageID) throws Exception {
        InputStream responseStream = null;
        JavaResult result = null;
        try {
            String statusQueryWithParams = String.format(STATUS_QUERY, messageID);
            ClientResponse response = client.post(getBaseURI(), statusQueryWithParams);
            if (HttpStatus.SC_OK == response.getStatus()) {
                responseStream = response.getEntityInputStream();
                result = SmooksUtil.getParsedXMLJavaResult(responseStream,
                        HDSConstants.SMOOKS_CONFIG_FILE);
            } else {
                throw HDSException.exceptions
                        .asyncTaskInvalidResponse(response.getStatus());
            }
        } finally {
            try {
                if (null != responseStream) {
                    responseStream.close();
                }
            } catch (IOException ioEx) {
                log.warn(
                        "Ignoring io exception that occurred during stream closing for async status check for messageID {}",
                        messageID);
            }
        }
        return result;
    }

    /**
     * Utility method to check if there are any errors or not.
     * 
     * @param javaResult
     * @throws Exception
     */
    public void verifyErrorPayload(JavaResult javaResult) throws Exception {
        EchoCommand command = javaResult.getBean(EchoCommand.class);
        if (null == command || null == command.getStatus()
                || HDSConstants.FAILED_STR.equalsIgnoreCase(command.getStatus())) {
            Error error = javaResult.getBean(Error.class);
            // log.info("Error response received from Hitachi server for messageID :{}", command.getMessageID());
            log.info("Hitachi command failed with error code:{} with message:{} for request:{}",
                    new Object[] { error.getCode().toString(), error.getDescription(), error.getSource() });
            throw HDSException.exceptions.errorResponseReceived(
                    error.getCode(), error.getDescription());
        }
    }

    public HDSApiProtectionManager getHdsApiProtectionManager() {
        return hdsApiProtectionManager;
    }

    public String modifyThinVolumeTieringPolicy(String systemObjectID, String luObjectID,
            String ldevObjectID, String tieringPolicyName, String model) {
        return hdsApiVolumeManager.modifyThinVolumeTieringPolicy(systemObjectID, luObjectID, ldevObjectID, tieringPolicyName, model);
    }

    public HDSHost getSnapshotGroupPairManagementServer(String serialNumber) throws Exception {
        return hdsApiProtectionManager.getSnapshotGroupPairManagementServer(serialNumber);
    }

    public List<Pool> getThinImagePoolList(String systemObjectId) throws Exception {
        return hdsApiDiscoveryManager.getThinImagePoolList(systemObjectId);
    }

    public boolean createThinImagePair(String snapshotGroupObjId, String hostObjId,
            String sourNativeId, String sanpNativeId, String thinImagePoolId, String model) throws Exception {
        return hdsApiProtectionManager.createThinImagePair(snapshotGroupObjId, hostObjId, sourNativeId, sanpNativeId, thinImagePoolId,
                model);
    }

    public boolean restoreThinImagePair(String pairMgmtServerHostObjId, String snapshotGroupObjId,
            String replicationInfoObjId, String model) throws Exception {
        return hdsApiProtectionManager.restoreThinImagePair(pairMgmtServerHostObjId, snapshotGroupObjId, replicationInfoObjId, model);
    }

    public void deleteThinImagePair(String pairMgmtServerHostObjId, String snapShotGrpId,
            String replicationInfoObjId, String model) throws Exception {
        hdsApiProtectionManager.deleteThinImagePair(pairMgmtServerHostObjId, snapShotGrpId, replicationInfoObjId, model);
    }

}
