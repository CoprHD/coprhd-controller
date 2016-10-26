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
import com.emc.storageos.driver.ibmsvcdriver.connection.ConnectionManager;
import com.emc.storageos.driver.ibmsvcdriver.connection.SSHConnection;
import com.emc.storageos.driver.ibmsvcdriver.impl.IBMSVCDriverTask;
import com.emc.storageos.driver.ibmsvcdriver.impl.IBMSVCStorageDriver;
import com.emc.storageos.driver.ibmsvcdriver.utils.IBMSVCConstants;
import com.emc.storageos.driver.ibmsvcdriver.utils.IBMSVCDriverUtils;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.model.StorageObject;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.model.VolumeSnapshot;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;

public class IBMSVCConsistencyGroups {

    private static final Logger _log = LoggerFactory.getLogger(IBMSVCStorageDriver.class);

    /*
     * Connection Manager for managing connection pool
     */
    private ConnectionManager connectionManager = null;

    /**
     * Constructor
     */
    public IBMSVCConsistencyGroups() {
        this.connectionManager = ConnectionManager.getInstance();
    }

    /**
     * Create driver task for task type
     *
     * @param taskType
     */
    public DriverTask createDriverTask(String taskType) {
        String taskID = String.format("%s+%s+%s", IBMSVCConstants.DRIVER_NAME, taskType, UUID.randomUUID());
        DriverTask task = new IBMSVCDriverTask(taskID);
        return task;
    }


	public DriverTask addVolumesToConsistencyGroup(List<StorageVolume> volumes, StorageCapabilities capabilities) {
		// TODO Auto-generated method stub
		return null;
	}


	public DriverTask removeVolumesFromConsistencyGroup(List<StorageVolume> volumes, StorageCapabilities capabilities) {
		// TODO Auto-generated method stub
		return null;
	}

    // get source volumes for FC,
    // check for space on host for new copies?
    // create copies of volumes at the required size,
    // create consistency group for FC,
    // map the volumes to the consistency group.
    //
    public DriverTask createVolumeSnapshot(List<VolumeSnapshot> snapshots, StorageCapabilities capabilities) {

        DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_CREATE_SNAPSHOT_VOLUMES);
        String consistGrpName = "";
        try {
            SSHConnection connectioncg = connectionManager.getClientBySystemId(snapshots.get(0).getStorageSystemId());
            consistGrpName = snapshots.get(0).getDisplayName();
            // check if consistency group exists
            IBMSVCQueryFCConsistGrpResult resultConsistquery = IBMSVCCLI.queryFCConsistGrp(connectioncg,
                    consistGrpName,consistGrpName);

            if (!resultConsistquery.isSuccess()) {
                IBMSVCCreateFCConsistGrpResult resultConsistGrp = IBMSVCCLI.createFCConsistGrp(
                        connectioncg, consistGrpName);
                consistGrpName = resultConsistGrp.getConsistGrpName();
            }
        } catch (Exception e) {
            _log.error("Unable to determine the required consistency group {}",consistGrpName);
            task.setMessage(String.format("Unable to determine the required consistency group {}",consistGrpName));
            task.setStatus(DriverTask.TaskStatus.FAILED);
            e.printStackTrace();
        }

        for (VolumeSnapshot volumeSnapshot : snapshots) {

            _log.info("createVolumeSnapshot() for storage system {} - start", volumeSnapshot.getStorageSystemId());

            try {
                SSHConnection connection = connectionManager.getClientBySystemId(volumeSnapshot.getStorageSystemId());

                // 1. Get the Source Volume details like fc_map_count,
                // se_copy_count, copy_count
                // As each Snapshot has an Max of 256 FC Mappings only for each
                // source volume
                IBMSVCGetVolumeResult resultGetVolume = IBMSVCCLI.queryStorageVolume(connection,
                        volumeSnapshot.getParentId());

                if (resultGetVolume.isSuccess()) {

                    _log.info(String.format("Processing storage volume Id %s.\n",
                            resultGetVolume.getProperty("VolumeId")));

                    boolean createMirrorCopy = false;

                    String sourceVolumeName = resultGetVolume.getProperty("VolumeName");

                    int se_copy_count = Integer.parseInt(resultGetVolume.getProperty("SECopyCount"));
                    int copy_count = Integer.parseInt(resultGetVolume.getProperty("CopyCount"));
                    int fc_map_count = Integer.parseInt(resultGetVolume.getProperty("FCMapCount"));

                    if (fc_map_count < IBMSVCConstants.MAX_SOURCE_MAPPINGS) {
                        // Create the snapshot volume parameters
                        StorageVolume targetStorageVolume = new StorageVolume();
                        targetStorageVolume.setStorageSystemId(volumeSnapshot.getStorageSystemId());
                        targetStorageVolume.setDeviceLabel(volumeSnapshot.getDeviceLabel());
                        targetStorageVolume.setDisplayName(volumeSnapshot.getDisplayName());
                        String timeStamp = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
                        // targetStorageVolume.setDeviceLabel(resultGetVolume.getProperty("VolumeName")
                        // + "_Snapshot_" + timeStamp);
                        targetStorageVolume.setStoragePoolId(resultGetVolume.getProperty("PoolId"));
                        targetStorageVolume.setRequestedCapacity(
                                IBMSVCDriverUtils.convertGBtoBytes(resultGetVolume.getProperty("VolumeCapacity")));

                        if (se_copy_count > 0) {
                            targetStorageVolume.setThinlyProvisioned(true);
                        }
                        if (copy_count > 1) {
                            createMirrorCopy = true;
                        }
                        _log.info(String.format("Processed storage volume Id %s.\n",
                                resultGetVolume.getProperty("VolumeId")));

                        // 2. Create a new Snapshot Volume with details supplied
                        IBMSVCCreateVolumeResult resultCreateVol = IBMSVCCLI.createStorageVolumes(connection,
                                targetStorageVolume, false, createMirrorCopy);

                        if (resultCreateVol.isSuccess()) {
                            _log.info(String.format("Created storage snapshot volume %s (%s) size %s\n",
                                    resultCreateVol.getName(), resultCreateVol.getId(),
                                    resultCreateVol.getRequestedCapacity()));

                            targetStorageVolume.setNativeId(resultCreateVol.getId());

                            IBMSVCGetVolumeResult resultGetSnapshotVolume = IBMSVCCLI.queryStorageVolume(connection,
                                    resultCreateVol.getId());

                            if (resultGetSnapshotVolume.isSuccess()) {
                                _log.info(String.format("Snapshot volume %s has been retrieved.\n",
                                        resultGetSnapshotVolume.getProperty("VolumeId")));
                                targetStorageVolume.setWwn(resultGetSnapshotVolume.getProperty("VolumeWWN"));
                                volumeSnapshot.setWwn(resultGetSnapshotVolume.getProperty("VolumeWWN"));
                            } else {
                                _log.warn(String.format("Snapshot volume %s retrieval failed %s\n",
                                        resultGetSnapshotVolume.getProperty("VolumeId"),
                                        resultGetSnapshotVolume.getErrorString()));
                            }

                            volumeSnapshot.setNativeId(resultCreateVol.getId());
                            volumeSnapshot.setDeviceLabel(resultCreateVol.getName());
                            volumeSnapshot.setDisplayName(resultCreateVol.getName());
                            volumeSnapshot.setAccessStatus(StorageObject.AccessStatus.READ_WRITE);
                            //volumeSnapshot.setTimestamp(timeStamp);

                            String targetVolumeName = volumeSnapshot.getDeviceLabel();

                            // 3. Create FC Mapping for the source and target
                            // volume
                            // Set the fullCopy to false to indicate its Volume
                            // Snapshot
                            //todo consistency group

                            IBMSVCCreateFCMappingResult resultFCMapping = IBMSVCCLI.createFCMapping(connection,
                                    sourceVolumeName, targetVolumeName, consistGrpName, false);

                            if (resultFCMapping.isSuccess()) {
                                _log.info(String.format("Created flashCopy mapping %s\n", resultFCMapping.getId()));

                                // 4. Prepare the Start of FC Mapping
                                IBMSVCPreStartFCMappingResult resultPreStartFCMapping = IBMSVCCLI
                                        .preStartFCMapping(connection, resultFCMapping.getId());

                                if (resultPreStartFCMapping.isSuccess()) {
                                    _log.info(String.format("Prepared to start flashCopy mapping %s\n",
                                            resultPreStartFCMapping.getId()));

                                    boolean mapping_ready = false;

                                    int wait_time = 5;
                                    int max_retries = (IBMSVCConstants.FC_MAPPING_QUERY_TIMEOUT / wait_time) + 1;

                                    // 5. Retry the Query of FC Mapping status
                                    // till Maximum Tries reaches
                                    label: for (int i = 1; i <= max_retries; i++) {

                                        IBMSVCQueryFCMappingResult resultQueryFCMapping = IBMSVCCLI.queryFCMapping(
                                                connection, resultPreStartFCMapping.getId(), false, null, null);

                                        if (resultQueryFCMapping.isSuccess()) {
                                            _log.info(String.format("Queried flashCopy mapping %s\n",
                                                    resultQueryFCMapping.getId()));

                                            String fcMapStatus = resultQueryFCMapping.getProperty("FCMapStatus");

                                            // 6. If the FC Mapping status is
                                            // "unknown" then set task as Failed
                                            // and return
                                            switch (fcMapStatus) {
                                                case "unknown":
                                                case "preparing":
                                                    _log.warn(String.format(
                                                            "Unexpected flashCopy mapping Id %s with status %s\n",
                                                            resultQueryFCMapping.getId(), fcMapStatus));
                                                    task.setMessage(String.format(
                                                            "Unexpected flashCopy mapping Id %s with status %s.",
                                                            resultQueryFCMapping.getId(), fcMapStatus));
                                                    task.setStatus(DriverTask.TaskStatus.FAILED);
                                                    break label;
                                                case "stopped": // 7. If the FC
                                                    // Mapping status is
                                                    // "stopped" then
                                                    // again Prepare the
                                                    // Start of FC
                                                    // Mapping. Repeat
                                                    // Step 4
                                                    preStartFCMapping(connection, resultFCMapping.getId());

                                                    break;
                                                case "prepared":
                                                    mapping_ready = true;
                                                    // 8. If the FC Mapping status
                                                    // is "prepared" then Start the
                                                    // FC Mapping
                                                    startFCMapping(connection, resultFCMapping.getId());
                                                    task.setMessage(String.format(
                                                            "Created flashCopy mapping for the source volume %s and the target volume %s.",
                                                            sourceVolumeName, targetVolumeName));
                                                    task.setStatus(DriverTask.TaskStatus.READY);
                                                    break label;
                                            }

                                        } else {
                                            _log.warn(String.format("Querying flashCopy mapping Id %s failed %s\n",
                                                    resultQueryFCMapping.getId(),
                                                    resultQueryFCMapping.getErrorString()));
                                        }

                                        SECONDS.sleep(5);
                                    }

                                    if (!mapping_ready) {
                                        _log.warn(String.format(
                                                "Preparing for flashCopy mapping Id %s failed to complete within the allocated %s seconds timeout. Terminating. %s\n",
                                                resultPreStartFCMapping.getId(),
                                                IBMSVCConstants.FC_MAPPING_QUERY_TIMEOUT,
                                                resultPreStartFCMapping.getErrorString()));

                                        _log.error(String.format(
                                                "Cleaning up the snapshot volume %s and FC Mapping Id %s.",
                                                resultCreateVol.getName(), resultPreStartFCMapping.getId()));

                                        // deleteFCMapping(connection,
                                        // resultPreStartFCMapping.getId());
                                        /**
                                         * Deleting volume stops and deletes all
                                         * the related FC mappings to that
                                         * volume And finally deletes the volume
                                         */
                                        IBMSVCDeleteVolumeResult delresult = IBMSVCCLI.deleteStorageVolumes(connection, resultCreateVol.getId());

                                        _log.error(String.format(
                                                "Cleaned up the snapshot volume %s and flashCopy mapping Id %s.",
                                                resultCreateVol.getName(), resultPreStartFCMapping.getId()));

                                        task.setMessage(String.format(
                                                "Preparing for flashCopy mapping Id %s failed to complete within the allocated %d seconds timeout. Terminating. %s\n",
                                                resultPreStartFCMapping.getId(),
                                                IBMSVCConstants.FC_MAPPING_QUERY_TIMEOUT,
                                                resultPreStartFCMapping.getErrorString()));

                                        task.setStatus(DriverTask.TaskStatus.FAILED);
                                    }

                                } else {
                                    _log.warn(String.format("Preparing for flashCopy mapping Id %s failed %s\n",
                                            resultPreStartFCMapping.getId(), resultPreStartFCMapping.getErrorString()));

                                    _log.error(String.format("Cleaning up the snapshot volume %s and FC Mapping Id %s.",
                                            resultCreateVol.getName(), resultPreStartFCMapping.getId()));

                                    // stopFCMapping(connection,
                                    // resultPreStartFCMapping.getId());
                                    // deleteFCMapping(connection,
                                    // resultPreStartFCMapping.getId());
                                    /**
                                     * Deleting volume stops and deletes all the
                                     * related FC mappings to that volume And
                                     * finally deletes the volume
                                     */
                                    IBMSVCDeleteVolumeResult delresult = IBMSVCCLI.deleteStorageVolumes(connection, resultCreateVol.getId());

                                    _log.error(String.format(
                                            "Cleaned up the snapshot volume %s and flashCopy mapping Id %s.",
                                            resultCreateVol.getName(), resultPreStartFCMapping.getId()));

                                    task.setMessage(String.format("Preparing for flashCopy mapping Id %s failed : %s",
                                            resultPreStartFCMapping.getId(), resultPreStartFCMapping.getErrorString()));

                                    task.setStatus(DriverTask.TaskStatus.FAILED);
                                }

                            } else {
                                _log.error(String.format("Creating flashCopy mapping failed %s",
                                        resultFCMapping.getErrorString()), resultFCMapping.isSuccess());

                                _log.error(String.format("Cleaning up the snapshot volume %s.",
                                        resultCreateVol.getName()));

                                /**
                                 * Deleting volume stops and deletes all the
                                 * related FC mappings to that volume And
                                 * finally deletes the volume
                                 */
                                IBMSVCDeleteVolumeResult delresult = IBMSVCCLI.deleteStorageVolumes(connection, resultCreateVol.getId());

                                _log.error(
                                        String.format("Cleaned up the snapshot volume %s.", resultCreateVol.getName()));

                                task.setMessage(String.format(
                                        "Creating flashCopy mapping for the source volume %s and the target volume %s failed : %s.",
                                        sourceVolumeName, targetVolumeName, resultFCMapping.getErrorString()));
                                task.setStatus(DriverTask.TaskStatus.FAILED);
                            }

                        } else {
                            _log.error(String.format("Creating storage snapshot volume failed %s\n",
                                    resultCreateVol.getErrorString()), resultCreateVol.isSuccess());
                            task.setMessage(
                                    String.format("Unable to create the snapshot volume %s on the storage system %s",
                                            volumeSnapshot.getDeviceLabel(), volumeSnapshot.getStorageSystemId())
                                            + resultCreateVol.getErrorString());
                            task.setStatus(DriverTask.TaskStatus.FAILED);
                        }

                    } else {
                        _log.error(String.format("FlashCopy mapping has reached the maximum for the source volume %s\n",
                                resultGetVolume.getProperty("VolumeName")));
                        task.setMessage(
                                String.format("FlashCopy mapping has reached the maximum for the source volume %s",
                                        resultGetVolume.getProperty("VolumeName")) + resultGetVolume.getErrorString());
                        task.setStatus(DriverTask.TaskStatus.FAILED);
                    }

                } else {
                    _log.error(String.format("Processing get storage volume Id %s failed %s\n",
                            resultGetVolume.getProperty("VolumeId"), resultGetVolume.getErrorString()));
                    task.setMessage(String.format("Processing get storage volume failed : %s",
                            resultGetVolume.getProperty("VolumeId")) + resultGetVolume.getErrorString());
                    task.setStatus(DriverTask.TaskStatus.FAILED);
                }
            } catch (Exception e) {
                _log.error("Unable to create the snapshot volume {} on the storage system {}",
                        volumeSnapshot.getParentId(), volumeSnapshot.getStorageSystemId());
                task.setMessage(String.format("Unable to create the snapshot volume %s on the storage system %s",
                        volumeSnapshot.getDeviceLabel(), volumeSnapshot.getStorageSystemId()) + e.getMessage());
                task.setStatus(DriverTask.TaskStatus.FAILED);
                e.printStackTrace();
            }

            _log.info("createVolumeSnapshot() for storage system {} - end", volumeSnapshot.getStorageSystemId());
        }

        return task;
    }

    /**
     * Prepare for Starting FC Mapping
     *
     * @param connection
     *            SSH Connection to the Storage System
     * @param fcMappingId
     *            FC Mapping ID to be prepared
     */
    private void preStartFCMapping(SSHConnection connection, String fcMappingId) {
        IBMSVCPreStartFCMappingResult resultPreStartFCMapping = IBMSVCCLI.preStartFCMapping(connection, fcMappingId);
        if (resultPreStartFCMapping.isSuccess()) {
            _log.info(String.format("Prepared to start flashCopy mapping %s\n", resultPreStartFCMapping.getId()));
        } else {
            _log.warn(String.format("Preparing to start flashCopy mapping Id %s failed : %s.\n",
                    resultPreStartFCMapping.getId(), resultPreStartFCMapping.getErrorString()));
        }
    }

    private DriverTask createFCConsistGrp(SSHConnection connection, String ConsistGrpName){

        DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_CREATE_FC_CONSISTGROUP);

        IBMSVCCreateFCConsistGrpResult resultConsistGrp = IBMSVCCLI.createFCConsistGrp(connection, ConsistGrpName);
        if (resultConsistGrp.isSuccess()){
            _log.info(String.format("FC consistency group created %s\n", resultConsistGrp.getConsistGrpId()));
            task.setMessage(String.format("FC consistency group created %s\n", resultConsistGrp.getConsistGrpId()));
            task.setStatus(DriverTask.TaskStatus.READY);
        } else {
            _log.warn(String.format("FC consistency group creation %s failed : %s.\n",
                    resultConsistGrp.getConsistGrpId(), resultConsistGrp.getErrorString()));
            task.setMessage(String.format("FC consistency group creation %s failed : %s.\n",
                    resultConsistGrp.getConsistGrpId(),resultConsistGrp.getErrorString()));
            task.setStatus(DriverTask.TaskStatus.FAILED);
        }
        return task;
    }

    private DriverTask deleteFCConsistGrp(SSHConnection connection, String consistGrpId, String consistGrpName){

        DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_DELETE_FC_CONSISTGROUP);

        IBMSVCDeleteFCConsistGrpResult resultConsistGrp = IBMSVCCLI.deleteFCConsistGrp(
                connection,consistGrpId,consistGrpName);

        if (resultConsistGrp.isSuccess()){
            _log.info(String.format("FC consistency group deleted %s\n",consistGrpId));
            task.setMessage(String.format("FC consistency group deleted %s\n",consistGrpId));
            task.setStatus(DriverTask.TaskStatus.READY);
        } else{
            _log.warn(String.format("FC consistency group deletion failed %s error: %s\n",
                    consistGrpId,resultConsistGrp.getErrorString()));
            task.setMessage(String.format("FC consistency group deletion failed %s error: %s\n",
                    consistGrpId,resultConsistGrp.getErrorString()));
            task.setStatus((DriverTask.TaskStatus.FAILED));
        }

        return task;
    }

    /**
     * Start FC Mapping
     *
     * @param connection
     *            SSH Connection to the Storage System
     * @param fcMappingId
     *            FC Mapping ID to be started
     */
    private void startFCMapping(SSHConnection connection, String fcMappingId) {
        IBMSVCStartFCMappingResult resultStartFCMapping = IBMSVCCLI.startFCMapping(connection, fcMappingId);
        if (resultStartFCMapping.isSuccess()) {
            _log.info(String.format("Started flashCopy mapping %s\n", resultStartFCMapping.getId()));
        } else {
            _log.warn(String.format("Starting flashCopy mapping Id %s failed : %s.\n", resultStartFCMapping.getId(),
                    resultStartFCMapping.getErrorString()));
        }
    }

    /**
     * Stopping FC Mapping
     *
     * @param connection
     *            SSH Connection to the Storage System
     * @param fcMappingId
     *            FC Mapping ID to be stopped
     */
    private void stopFCMapping(SSHConnection connection, String fcMappingId) {
        // Remove the FC Mapping and delete the snapshot volume
        IBMSVCStopFCMappingResult resultStopFCMapping = IBMSVCCLI.stopFCMapping(connection, fcMappingId);
        if (resultStopFCMapping.isSuccess()) {
            _log.info(String.format("Stopped flashCopy mapping %s\n", resultStopFCMapping.getId()));
        } else {
            _log.warn(String.format("Stopping flashCopy mapping Id %s failed : %s.\n", resultStopFCMapping.getId(),
                    resultStopFCMapping.getErrorString()));
        }
    }

    /**
     * Deleting the FC Mapping
     *
     * @param connection
     *            SSH Connection to the Storage System
     * @param fcMappingId
     *            FC Mapping ID to be deleted
     */
    private void deleteFCMapping(SSHConnection connection, String fcMappingId) {
        // Remove the FC Mapping and delete the snapshot volume
        IBMSVCDeleteFCMappingResult resultDeleteFCMapping = IBMSVCCLI.deleteFCMapping(connection, fcMappingId);
        if (resultDeleteFCMapping.isSuccess()) {
            _log.info(String.format("Deleted flashCopy mapping Id %s\n", resultDeleteFCMapping.getId()));
        } else {
            _log.warn(String.format("Deleting flashCopy mapping Id %s failed : %s.\n", resultDeleteFCMapping.getId(),
                    resultDeleteFCMapping.getErrorString()));
        }
    }



}
