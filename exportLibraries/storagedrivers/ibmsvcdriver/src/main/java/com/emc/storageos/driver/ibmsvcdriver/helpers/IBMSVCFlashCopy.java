package com.emc.storageos.driver.ibmsvcdriver.helpers;/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

import com.emc.storageos.driver.ibmsvcdriver.api.*;
import com.emc.storageos.driver.ibmsvcdriver.connection.SSHConnection;
import com.emc.storageos.driver.ibmsvcdriver.exceptions.IBMSVCDriverException;
import com.emc.storageos.driver.ibmsvcdriver.impl.IBMSVCStorageDriver;
import com.emc.storageos.driver.ibmsvcdriver.utils.IBMSVCConstants;
import com.emc.storageos.driver.ibmsvcdriver.utils.IBMSVCDriverConfiguration;
import com.emc.storageos.driver.ibmsvcdriver.utils.IBMSVCDriverUtils;
import com.emc.storageos.storagedriver.model.StorageObject;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.model.VolumeClone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * All Flash Copy related operations
 */
public class IBMSVCFlashCopy {
    private static final Logger _log = LoggerFactory.getLogger(IBMSVCStorageDriver.class);


    public static IBMSVCCreateFCMappingResult createFCMapping(SSHConnection connection, String sourceVolumeName, String targetVolumeName, String consistGrpName, boolean fullCopy) throws IBMSVCDriverException{

        IBMSVCCreateFCMappingResult resultFCMapping = IBMSVCCLI.createFCMapping(connection,
                sourceVolumeName, targetVolumeName, consistGrpName, fullCopy);

        if (resultFCMapping.isSuccess()) {
            _log.info(String.format("Created flashCopy mapping %s\n", resultFCMapping.getId()));
            return resultFCMapping;
        }else{
            throw new IBMSVCDriverException(String.format(
                    "Creating flashCopy mapping for the source volume %s and the target volume %s failed : %s.",
                    sourceVolumeName, targetVolumeName, resultFCMapping.getErrorString()));
        }

    }

    /**
     * Prepare for Starting FC Mapping
     *
     * @param connection
     *            SSH Connection to the Storage System
     * @param fcMappingId
     *            FC Mapping ID to be prepared
     */
    public static void preStartFCMapping(SSHConnection connection, String fcMappingId) {
        IBMSVCPreStartFCMappingResult resultPreStartFCMapping = IBMSVCCLI.preStartFCMapping(connection, fcMappingId);
        if (resultPreStartFCMapping.isSuccess()) {
            _log.info(String.format("Prepared to start flashCopy mapping %s\n", resultPreStartFCMapping.getId()));
        } else {
            _log.warn(String.format("Preparing to start flashCopy mapping Id %s failed : %s.\n",
                    resultPreStartFCMapping.getId(), resultPreStartFCMapping.getErrorString()));
        }
    }


    /**
     * Start FC Mapping
     *
     * @param connection
     *            SSH Connection to the Storage System
     * @param fcMappingId
     *            FC Mapping ID to be started
     */
    public static IBMSVCStartFCMappingResult startFCMapping(SSHConnection connection, String fcMappingId, boolean restore, boolean prep) throws IBMSVCDriverException {
        IBMSVCStartFCMappingResult resultStartFCMapping = IBMSVCCLI.startFCMapping(connection, fcMappingId, restore, prep);
        if (resultStartFCMapping.isSuccess()) {
            _log.info(String.format("Started flashCopy mapping %s\n", resultStartFCMapping.getId()));
        } else {
            _log.warn(String.format("Starting flashCopy mapping Id %s failed : %s.\n", resultStartFCMapping.getId(),
                    resultStartFCMapping.getErrorString()));
            throw new IBMSVCDriverException(String.format("Start flashCopy mapping Id %s failed %s\n",
                    resultStartFCMapping.getId(), resultStartFCMapping.getErrorString()));
        }
        return resultStartFCMapping;
    }

    /**
     * Delete the Storage Volumes created during the snapshot creation
     *
     * @param connection
     *            SSH Connection to the Storage System
     * @param volumeId
     *            Volume ID to be deleted
     * @return state
     */
    public static boolean deleteStorageVolumes(SSHConnection connection, String volumeId) {
        IBMSVCDeleteVolumeResult resultDelVol = IBMSVCCLI.deleteStorageVolumes(connection, volumeId);

        if (resultDelVol.isSuccess()) {
            _log.info(String.format("Deleted storage snapshot volume %s.\n", resultDelVol.getId()));
            return true;
        } else {
            _log.error(String.format("Deleting storage snapshot volume failed %s\n", resultDelVol.getErrorString()),
                    resultDelVol.isSuccess());
            return false;
        }
    }


    /**
     * Delete FC Mapping
     * @param connection
     * @param FcMapId
     * @return
     */
    public static boolean deleteFCMapping(SSHConnection connection, String FcMapId) {
        IBMSVCDeleteFCMappingResult resultDelMap = IBMSVCCLI.deleteFCMapping(connection, FcMapId);

        if (resultDelMap.isSuccess()) {
            _log.info(String.format("Deleted Flash Copy mapping %s.\n", resultDelMap.getErrorString()));
            return true;
        } else {
            _log.error(String.format("Failed to Delete Flash Copy mapping %s\n", resultDelMap.getErrorString()),
                    resultDelMap.isSuccess());
            return false;
        }
    }


    /**
     * Wait for a FC Map Status
     * @param maxRetries - Maximum Retries
     * @param connection - SSH Connection
     * @param fcMapID - FC Map ID
     * @param waitForState - The state to wait for
     * @param waitForProgress - The progress percentage to wait for
     * @throws Exception
     */
    public static void waitForFCMapState(int maxRetries, SSHConnection connection, String fcMapID, String waitForState, int waitForProgress) throws Exception{

        String fcMapStatus = "";

        _log.info("Wait for state on FCMap ID - {} ; state - {} - start", fcMapID, waitForState);

        label: for (int i = 1; i <= maxRetries; i++) {

            IBMSVCQueryFCMappingResult resultQueryFCMapping = IBMSVCCLI.queryFCMapping(
                    connection, fcMapID, false, null, null);

            if (resultQueryFCMapping.isSuccess()) {
                _log.info(String.format("Queried flashCopy mapping %s\n",
                        resultQueryFCMapping.getId()));

                fcMapStatus  = resultQueryFCMapping.getProperty("FCMapStatus");
                String fcMapProgress  = resultQueryFCMapping.getProperty("FCMapProgress");

                if(waitForState.equals(fcMapStatus)){

                    if(waitForProgress > -1){
                        try{
                            if(Integer.valueOf(fcMapProgress) >= waitForProgress){
                                _log.info("Wait for state on FCMap ID - {} ; state - {} - end", fcMapID, waitForState);
                                return;
                            }
                        }catch(NumberFormatException numEx){
                            _log.info("Unable to determine progress percentage from value - {}", fcMapProgress);
                        }

                    }else{
                        return;
                    }

                }else if("unknown".equals(fcMapStatus) || "preparing".equals(fcMapStatus)){
                    throw new Exception(String.format(
                            "Unexpected flashCopy mapping Id %s with status %s\n",
                            resultQueryFCMapping.getId(), fcMapStatus));
                }

            } else {
                _log.warn(String.format("Querying flashCopy mapping Id %s failed %s\n",
                        resultQueryFCMapping.getId(),
                        resultQueryFCMapping.getErrorString()));
                throw new Exception(String.format("Querying flashCopy mapping Id %s failed %s\n",
                        resultQueryFCMapping.getId(),
                        resultQueryFCMapping.getErrorString()));
            }

            SECONDS.sleep(5);
        }

        _log.info("Wait for state on FCMap ID - {} ; state - {} - end", fcMapID, waitForState);

        throw new Exception(String.format(
                "Wait for state timed-out. FC Map ID - %s ; FC Map Status - %s %n",
                fcMapID, fcMapStatus));
    }


    /**
     * Cleanup Flash Copy Map and delete volumes
     * @param connection
     *              SSH Connection to the array
     * @param listOfCreatedVolumes
     *              List of volumes created during the main operation
     */
    public static void cleanupMappingsAndVolumes(SSHConnection connection, List<IBMSVCCreateVolumeResult> listOfCreatedVolumes){

        for(IBMSVCCreateVolumeResult createdVolume : listOfCreatedVolumes){
            _log.error(String.format(
                    "Cleaning up the flashCopy mapping Id %s and volume %s.",
                    createdVolume.getId(), createdVolume.getName()));

            IBMSVCFlashCopy.deleteStorageVolumes(connection, createdVolume.getId());

            _log.error(String.format(
                    "Cleaned up the flashCopy mapping Id %s and volume %s.",
                    createdVolume.getId(), createdVolume.getName()));
        }

    }

    /**
     * Cleanup Flash Copy Map and delete volumes
     * @param connection
     *              SSH Connection to the array
     * @param createdVolume
     *              List of volumes created during the main operation
     */
    public static void cleanupMappingsAndVolumes(SSHConnection connection, IBMSVCCreateVolumeResult createdVolume){

            if(createdVolume == null){
                return;
            }

            _log.error(String.format(
                    "Cleaning up the flashCopy mapping Id %s and volume %s.",
                    createdVolume.getId(), createdVolume.getName()));

            IBMSVCFlashCopy.deleteStorageVolumes(connection, createdVolume.getId());

            _log.error(String.format(
                    "Cleaned up the flashCopy mapping Id %s and volume %s.",
                    createdVolume.getId(), createdVolume.getName()));


    }


    /**
     * Cleanup Flash Copy Map and delete volumes
     * @param connection
     *              SSH Connection to the array
     * @param listOfCreatedMappings
     *              List of volumes created during the main operation
     */
    public static void cleanupMappings(SSHConnection connection, List<IBMSVCCreateFCMappingResult> listOfCreatedMappings){

        for(IBMSVCCreateFCMappingResult createdMapping : listOfCreatedMappings){
            _log.error(String.format(
                    "Cleaning up the flashCopy mapping Id %s.",
                    createdMapping.getId()));

            IBMSVCFlashCopy.deleteFCMapping(connection, createdMapping.getId());

            _log.error(String.format(
                    "Cleaned up the flashCopy mapping Id %s",
                    createdMapping.getId()));
        }

    }


    /**
     * Create and Start Flash Copy Clone
     * @param connection
     *              SSH Connection to the array
     * @param volumeClone
     *              Volume Clone Object
     * @return
     *              IBMSVCStartFCMappingResult
     * @throws IBMSVCDriverException
     */
    public static IBMSVCStartFCMappingResult createAndStartFlashCopy(SSHConnection connection, String parentID, StorageVolume volumeClone, boolean fullCopy)throws IBMSVCDriverException{

        IBMSVCCreateVolumeResult createdVolumeToCleanup = null;

        try {
            // 1. Get the Source Volume details like fcMapCount,
            // seCopyCount, copyCount
            // As each Snapshot has an Max of 256 FC Mappings only for each
            // source volume
            IBMSVCGetVolumeResult resultGetVolume = IBMSVCVolumes.queryStorageVolume(connection,
                    parentID);

            boolean createMirrorCopy = false;

            String sourceVolumeName = resultGetVolume.getProperty("VolumeName");

            int seCopyCount = Integer.parseInt(resultGetVolume.getProperty("SECopyCount"));
            int copyCount = Integer.parseInt(resultGetVolume.getProperty("CopyCount"));
            int fcMapCount = Integer.parseInt(resultGetVolume.getProperty("FCMapCount"));

            if (fcMapCount < IBMSVCConstants.MAX_SOURCE_MAPPINGS) {

                // Create the snapshot volume parameters
                StorageVolume targetStorageVolume = new StorageVolume();
                targetStorageVolume.setStorageSystemId(volumeClone.getStorageSystemId());
                targetStorageVolume.setDeviceLabel(volumeClone.getDeviceLabel());
                targetStorageVolume.setDisplayName(volumeClone.getDisplayName());
                targetStorageVolume.setStoragePoolId(resultGetVolume.getProperty("PoolId"));
                targetStorageVolume.setRequestedCapacity(
                        IBMSVCDriverUtils.convertGBtoBytes(resultGetVolume.getProperty("VolumeCapacity")));

                if (seCopyCount > 0) {
                    targetStorageVolume.setThinlyProvisioned(true);
                }
                if (copyCount > 1) {
                    createMirrorCopy = true;
                }
                _log.info(String.format("Processed storage volume Id %s.\n",
                        resultGetVolume.getProperty("VolumeId")));

                // 2. Create a new Clone Volume with details supplied
                IBMSVCCreateVolumeResult resultCreateVol = IBMSVCVolumes.createStorageVolumes(connection,
                        targetStorageVolume, false, createMirrorCopy);

                // Store volume details for cleanup
                createdVolumeToCleanup = resultCreateVol;

                targetStorageVolume.setNativeId(resultCreateVol.getId());

                IBMSVCGetVolumeResult resultGetSnapshotVolume = IBMSVCVolumes.queryStorageVolume(connection,
                        resultCreateVol.getId());

                targetStorageVolume.setWwn(resultGetSnapshotVolume.getProperty("VolumeWWN"));
                volumeClone.setWwn(resultGetSnapshotVolume.getProperty("VolumeWWN"));

                volumeClone.setNativeId(resultCreateVol.getId());
                volumeClone.setDeviceLabel(resultGetSnapshotVolume.getProperty("VolumeName"));
                volumeClone.setDisplayName(resultGetSnapshotVolume.getProperty("VolumeName"));
                volumeClone.setAccessStatus(StorageObject.AccessStatus.READ_WRITE);

                String targetVolumeName = volumeClone.getDeviceLabel();

                // 3. Create FC Mapping for the source and target
                // volume Set the fullCopy to true/false to indicate its Volume Clone/Snapshot
                IBMSVCCreateFCMappingResult resultFCMapping = IBMSVCFlashCopy.createFCMapping(connection,
                        sourceVolumeName, targetVolumeName, null, fullCopy);

                // 4. Start of FC Mapping with prep option
                return  IBMSVCFlashCopy.startFCMapping(connection,resultFCMapping.getId(), false, true);

            }
        } catch (Exception e) {
            _log.error("Unable to create or start the FC Pair {} on the storage system {} - {}", parentID,
                    volumeClone.getStorageSystemId(), e.getMessage());

            // Cleanup volume if created
            IBMSVCFlashCopy.cleanupMappingsAndVolumes(connection, createdVolumeToCleanup);

        }

        return null;
    }


}
