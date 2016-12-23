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
import com.emc.storageos.driver.ibmsvcdriver.exceptions.IBMSVCDriverException;
import com.emc.storageos.driver.ibmsvcdriver.impl.IBMSVCDriverTask;
import com.emc.storageos.driver.ibmsvcdriver.impl.IBMSVCStorageDriver;
import com.emc.storageos.driver.ibmsvcdriver.utils.IBMSVCConstants;
import com.emc.storageos.driver.ibmsvcdriver.utils.IBMSVCDriverConfiguration;
import com.emc.storageos.driver.ibmsvcdriver.utils.IBMSVCDriverUtils;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.model.StorageObject;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.model.VolumeSnapshot;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import static java.util.concurrent.TimeUnit.SECONDS;

public class IBMSVCSnapshots {

    private static final Logger _log = LoggerFactory.getLogger(IBMSVCStorageDriver.class);
    private IBMSVCDriverConfiguration ibmsvcdriverConfiguration = IBMSVCDriverConfiguration.getInstance();
    /*
     * Connection Manager for managing connection pool
     */
    private ConnectionManager connectionManager = null;

    /**
     * Constructor
     */
    public IBMSVCSnapshots() {
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


    /**
     * Create Volume Snapshot
     * @param snapshots
     * @param capabilities
     * @return
     */
    public DriverTask createVolumeSnapshot(List<VolumeSnapshot> snapshots, StorageCapabilities capabilities) {
        boolean enforceCGForSnapshots = ibmsvcdriverConfiguration.isEnforceCGForSnapshots();
        if (enforceCGForSnapshots) {
            return createVolumeSnapshotCustom(snapshots, capabilities);
        } else {
            DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_CREATE_SNAPSHOT_VOLUMES);

            for (VolumeSnapshot volumeSnapshot : snapshots) {

                _log.info("createVolumeSnapshot() for storage system {} - start", volumeSnapshot.getStorageSystemId());

                SSHConnection connection = null;

                try {
                    connection = connectionManager.getClientBySystemId(volumeSnapshot.getStorageSystemId());

                    // 1. Get the Source Volume details like fcMapCount,
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
                        int fcMapCount = Integer.parseInt(resultGetVolume.getProperty("FCMapCount"));

                        if (fcMapCount < IBMSVCConstants.MAX_SOURCE_MAPPINGS) {

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
                                volumeSnapshot.setDeviceLabel(resultGetSnapshotVolume.getProperty("VolumeName"));
                                volumeSnapshot.setDisplayName(resultGetSnapshotVolume.getProperty("VolumeName"));
                                volumeSnapshot.setAccessStatus(StorageObject.AccessStatus.READ_WRITE);
                                // volumeSnapshot.setTimestamp(timeStamp);

                                String targetVolumeName = volumeSnapshot.getDeviceLabel();

                                // 3. Create FC Mapping for the source and target
                                // volume
                                // Set the fullCopy to false to indicate its Volume
                                // Snapshot
                                IBMSVCCreateFCMappingResult resultFCMapping = IBMSVCCLI.createFCMapping(connection,
                                        sourceVolumeName, targetVolumeName, null, false);

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
                                                        IBMSVCFlashCopy.preStartFCMapping(connection, resultFCMapping.getId());

                                                        break;
                                                    case "prepared":
                                                        mapping_ready = true;
                                                        // 8. If the FC Mapping status
                                                        // is "prepared" then Start the
                                                        // FC Mapping
                                                        IBMSVCFlashCopy.startFCMapping(connection, resultFCMapping.getId(), false, false);
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
                                            IBMSVCFlashCopy.deleteStorageVolumes(connection, resultCreateVol.getId());

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
                                        IBMSVCFlashCopy.deleteStorageVolumes(connection, resultCreateVol.getId());

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
                                    IBMSVCFlashCopy.deleteStorageVolumes(connection, resultCreateVol.getId());

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
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }

                _log.info("createVolumeSnapshot() for storage system {} - end", volumeSnapshot.getStorageSystemId());
            }

            return task;
        }
    }


    /**
     * Restore volume to snapshot state.
     *
     * @param snapshots
     *            Type: Input/Output.
     * @return task
     */
    public DriverTask restoreSnapshot(List<VolumeSnapshot> snapshots) {

        DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_RESTORE_SNAPSHOT_VOLUMES);

        for (VolumeSnapshot volumeSnapshot : snapshots) {

            SSHConnection connection = null;
            //TODO: Handle restore for consistency group
            // Check for consistency group from snapshot list and compare with CG group on array
            // If different fail
            // If same proceed with restore of Consistency group
            try {

                _log.info("restoreSnapshot() for storage system {} - start", volumeSnapshot.getStorageSystemId());

                connection = connectionManager.getClientBySystemId(volumeSnapshot.getStorageSystemId());

                // 0. Get the FCMap information from Snapshot

                IBMSVCFilterFCMappingResult resultFilterFCMap = IBMSVCCLI.filterVolumeFCMapping(connection, null,
                        volumeSnapshot.getNativeId());

                if (resultFilterFCMap.isSuccess() && !resultFilterFCMap.getFcMapList().isEmpty()
                        && resultFilterFCMap.getFcMapList().size() < 2) {

                    IBMSVCFCMap fcMap = resultFilterFCMap.getFcMapList().get(0);

                    IBMSVCGetVolumeResult resultQueryVolume = IBMSVCCLI.queryStorageVolume(connection,
                            fcMap.getSourceVdiskId());

                    // 1. Get the Source Volume details like fcMapCount,
                    // se_copy_count, copy_count
                    // As each Snapshot has an Max of 256 FC Mappings only for each
                    // source volume

                    if (resultQueryVolume.isSuccess()) {

                        _log.info(String.format("Processing snapshot volume Id %s.\n",
                                fcMap.getTargetVdiskId()));

                        int fcMapCount = Integer.parseInt(resultQueryVolume.getProperty("FCMapCount"));

                        if (fcMapCount < IBMSVCConstants.MAX_SOURCE_MAPPINGS) {

                            String sourceVolumeName = volumeSnapshot.getNativeId();
                            String targetVolumeName = volumeSnapshot.getParentId();

                            // 2. Create FC Mapping for the source and target volume
                            // Set the fullCopy to true to indicate its Restore Volume
                            // Snapshot
                            IBMSVCCreateFCMappingResult resultFCMapping = IBMSVCCLI.createFCMapping(connection,
                                    sourceVolumeName, targetVolumeName, null, true);

                            if (resultFCMapping.isSuccess()) {
                                _log.info(String.format("Created flashCopy mapping %s\n", resultFCMapping.getId()));

                                IBMSVCStartFCMappingResult resultStartFCMapping = IBMSVCCLI.startFCMapping(connection,
                                        resultFCMapping.getId(), true, true);

                                if (resultStartFCMapping.isSuccess()) {
                                    _log.info(String.format(
                                            "Started flashCopy mapping Id %s for the source volume %s and the target volume %s.\n",
                                            resultStartFCMapping.getId(), sourceVolumeName, targetVolumeName));
                                    task.setMessage(String.format(
                                            "Started flashCopy mapping Id %s for the source volume %s and the target volume %s.",
                                            resultStartFCMapping.getId(), sourceVolumeName, targetVolumeName)
                                            + resultFCMapping.getErrorString());
                                    task.setStatus(DriverTask.TaskStatus.READY);
                                } else {
                                    _log.error(
                                            String.format(
                                                    "Starting flashCopy mapping for the source volume %s and the target volume %s failed : %s",
                                                    sourceVolumeName, targetVolumeName, resultStartFCMapping.getErrorString()),
                                            resultStartFCMapping.isSuccess());
                                    task.setMessage(String.format(
                                            "Starting flashCopy mapping for the source volume %s and the target volume %s failed : %s.",
                                            sourceVolumeName, targetVolumeName, resultStartFCMapping.getErrorString()));
                                    task.setStatus(DriverTask.TaskStatus.FAILED);
                                }

                            } else {
                                _log.error(
                                        String.format(
                                                "Creating flashCopy mapping for the source volume %s and the target volume %s failed : %s",
                                                sourceVolumeName, targetVolumeName, resultFCMapping.getErrorString()),
                                        resultFCMapping.isSuccess());
                                task.setMessage(String.format(
                                        "Creating flashCopy mapping for the source volume %s and the target volume %s failed : %s.",
                                        sourceVolumeName, targetVolumeName, resultFCMapping.getErrorString()));
                                task.setStatus(DriverTask.TaskStatus.FAILED);
                            }

                        } else {
                            _log.error(String.format("FlashCopy mapping has reached the maximum for the source volume %s\n",
                                    resultQueryVolume.getProperty("VolumeName")));
                            task.setMessage(String.format("FlashCopy mapping has reached the maximum for the source volume %s",
                                    resultQueryVolume.getProperty("VolumeName")) + resultQueryVolume.getErrorString());
                            task.setStatus(DriverTask.TaskStatus.FAILED);
                        }
                    } else {
                        _log.error(String.format("Querying storage volume %s failed : %s\n", volumeSnapshot.getParentId(),
                                resultQueryVolume.getErrorString()), resultQueryVolume.isSuccess());
                        task.setMessage(String.format("Querying storage volume %s failed : %s.", volumeSnapshot.getParentId(),
                                resultQueryVolume.getErrorString()));
                        task.setStatus(DriverTask.TaskStatus.FAILED);
                    }

                } else {
                    _log.error(String.format(
                            "Querying FC Map List for target volume %s failed or listed more than one results : %s - FCMap List %s\n",
                            volumeSnapshot.getNativeId(),
                            resultFilterFCMap.getErrorString(), resultFilterFCMap.getFcMapList().size()), resultFilterFCMap.isSuccess());
                    task.setMessage(String.format("Querying storage volume %s failed : %s.", volumeSnapshot.getParentId(),
                            resultFilterFCMap.getErrorString()));
                    task.setStatus(DriverTask.TaskStatus.FAILED);
                }

            } catch (Exception e) {
                _log.error("Unable to restore the snapshot volume {} on the storage system {}",
                        volumeSnapshot.getParentId(), volumeSnapshot.getStorageSystemId());
                task.setMessage(String.format("Unable to restore the snapshot volume %s on the storage system %s",
                        volumeSnapshot.getDeviceLabel(), volumeSnapshot.getStorageSystemId()) + e.getMessage());
                task.setStatus(DriverTask.TaskStatus.FAILED);
                e.printStackTrace();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            _log.info("restoreSnapshot() for storage system {} - end", volumeSnapshot.getStorageSystemId());
        }

        return task;
    }

    /**
     * Create Volume Snapshot
     * get source volumes for FC,
     * check for space on host for new copies?
     * create copies of volumes at the required size,
     * create consistency group for FC,
     * map the volumes to the consistency group.
     *
     * @param snapshots
     *          List of snapshots
     * @param capabilities
     *          List of capabilities
     * @return
     */
    public DriverTask createVolumeSnapshotCustom(List<VolumeSnapshot> snapshots, StorageCapabilities capabilities) {
        // 0. create svc consistency group for the host if it doesn't exist.
        DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_CREATE_SNAPSHOT_VOLUMES);

        String consistGrpName = null;
        try {
            if (!snapshots.isEmpty()) {

                SSHConnection connectioncg = connectionManager.getClientBySystemId(snapshots.get(0).getStorageSystemId());
                // find the host for the snapshot to use as consistency group name.
                IBMSVCQueryVolumeHostMappingResult volHostResult = IBMSVCCLI.queryVolumeHostMapping(
                        connectioncg, snapshots.get(0).getParentId());
                if (volHostResult.isSuccess()) {
                    consistGrpName = volHostResult.getHostList().get(0).getHostName();
                } else {
                    throw new Exception(volHostResult.getErrorString());
                }
                // check if consistency group exists
                IBMSVCQueryFCConsistGrpResult resultConsistquery = IBMSVCCLI.queryFCConsistGrp(connectioncg,
                        consistGrpName, consistGrpName);
                if (!resultConsistquery.isSuccess()) {
                    if (resultConsistquery.getErrorString().contains(
                            "The action failed because an object that was specified in the command does not exist.")) {
                        IBMSVCCreateFCConsistGrpResult resultConsistGrp = IBMSVCCLI.createFCConsistGrp(
                                connectioncg, consistGrpName);
                        consistGrpName = resultConsistGrp.getConsistGrpName();
                    } else {
                        throw new Exception(resultConsistquery.getErrorString());
                    }
                } else {
                    consistGrpName = resultConsistquery.getConsistGrpName();
                }

            }
        } catch (Exception e) {
            _log.error("Unable to determine the required consistency group {}", consistGrpName);
            task.setMessage(String.format("Unable to determine the required consistency group {}", consistGrpName));
            task.setStatus(DriverTask.TaskStatus.FAILED);
            e.printStackTrace();
            return task;
        }

        // snapshot iteration
        // 1. Get the Source Volume details for the, target volume creation
        // 2. Create the target volume
        // 3. Use the volumes to create the flash copy to the consistency group.
        for (VolumeSnapshot volumeSnapshot : snapshots) {
            _log.info("createVolumeSnapshot() for storage system {} - start", volumeSnapshot.getStorageSystemId());
            try {
                SSHConnection connection = connectionManager.getClientBySystemId(volumeSnapshot.getStorageSystemId());

                IBMSVCGetVolumeResult resultGetVolume = IBMSVCCLI.queryStorageVolume(connection,
                        volumeSnapshot.getParentId());

                if (resultGetVolume.isSuccess()) {

                    _log.info(String.format("Processing storage volume Id %s.\n",
                            resultGetVolume.getProperty("VolumeId")));

                    boolean createMirrorCopy = false;

                    String sourceVolumeName = resultGetVolume.getProperty("VolumeName");

                    int seCopyCount = Integer.parseInt(resultGetVolume.getProperty("SECopyCount"));
                    int copyCount = Integer.parseInt(resultGetVolume.getProperty("CopyCount"));
                    int fcMapCount = Integer.parseInt(resultGetVolume.getProperty("FCMapCount"));

                    if (fcMapCount < IBMSVCConstants.MAX_SOURCE_MAPPINGS) {
                        // Create the snapshot volume parameters
                        StorageVolume targetStorageVolume = new StorageVolume();
                        targetStorageVolume.setStorageSystemId(volumeSnapshot.getStorageSystemId());
                        targetStorageVolume.setDeviceLabel(volumeSnapshot.getDeviceLabel());
                        targetStorageVolume.setDisplayName(volumeSnapshot.getDisplayName());
                        // String timeStamp = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
                        // targetStorageVolume.setDeviceLabel(resultGetVolume.getProperty("VolumeName")
                        // + "_Snapshot_" + timeStamp);
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
                            // volumeSnapshot.setTimestamp(timeStamp);

                            String targetVolumeName = volumeSnapshot.getDeviceLabel();

                            // 3. Create FC Mapping for the source and target
                            // volume
                            // Set the fullCopy to false to indicate its Volume
                            // add to the hosts consistency group
                            // Snapshot
                            IBMSVCCreateFCMappingResult resultFCMapping = IBMSVCCLI.createFCMapping(connection,
                                    sourceVolumeName, targetVolumeName, consistGrpName, false);

                            if (resultFCMapping.isSuccess()) {
                                _log.info(String.format("Created flashCopy mapping %s\n", resultFCMapping.getId()));

                            } else {
                                IBMSVCDeleteVolumeResult delVolREsult = IBMSVCCLI.deleteStorageVolumes(
                                        connection, targetVolumeName);
                                if (delVolREsult.isSuccess()) {
                                    throw new IBMSVCDriverException("Failed to create FCMapping, reverted");
                                } else {
                                    throw new IBMSVCDriverException("Failed to create FCMapping, not reverted");
                                }

                            }

                        } else {
                            throw new IBMSVCDriverException(
                                    String.format("Failed to create FCMapping %s", resultCreateVol.getErrorString()));
                        }
                    }
                }

            } catch (IBMSVCDriverException e) {
                _log.error("Unable to create the snapshot volume {} on the storage system {}",
                        volumeSnapshot.getParentId(), volumeSnapshot.getStorageSystemId());
                task.setMessage(String.format("Unable to create the snapshot volume %s on the storage system %s",
                        volumeSnapshot.getDeviceLabel(), volumeSnapshot.getStorageSystemId()) + e.getMessage());
                task.setStatus(DriverTask.TaskStatus.FAILED);
            }

            _log.info("createVolumeSnapshot() for storage system {} - end", volumeSnapshot.getStorageSystemId());
        }

        // 4 Run consistency group start, this will start the copy.
        try {
            SSHConnection connection = connectionManager.getClientBySystemId(snapshots.get(0).getStorageSystemId());
            IBMSVCQueryFCConsistGrpResult resultQueryConsistGrp = IBMSVCCLI.queryFCConsistGrp(
                    connection, consistGrpName, consistGrpName);
            // if the consistancy group is not empty then start it
            if (resultQueryConsistGrp.isSuccess()) {
                switch (resultQueryConsistGrp.getConsistGrpStatus()) {
                    case "empty":
                        task.setStatus(DriverTask.TaskStatus.FAILED);
                        break;
                    case "idle_or_copied":
                        IBMSVCStartFCConsistGrpResult ResultStartGrp = IBMSVCCLI.startFCConsistGrp(
                                connection, consistGrpName, consistGrpName, false, true);
                        if (ResultStartGrp.isSuccess()) {
                            task.setStatus(DriverTask.TaskStatus.READY);
                        }
                        break;
                    // TODO : add remaining cases with a wait /empty, idle_or_copied, preparing, prepared, copying, stopped,
                    // suspended, stopping
                }
            } else {
                throw new IBMSVCDriverException(
                        String.format("Unable to start the consistency group %s", resultQueryConsistGrp.getErrorString()));
            }
        } catch (IBMSVCDriverException e) {
            _log.error("Unable to start the consistency group {} ",
                    consistGrpName);
            task.setMessage(String.format("Unable to start the consistency group %s ",
                    consistGrpName));
            task.setStatus(DriverTask.TaskStatus.FAILED);
        }

        return task;
    }





    /**
     * Delete snapshots.
     *
     * Algorithm for the Snapshot Volume Creation
     *
     * 1. Ensures that volume is not part of FC mapping and deletes it.
     * 2. Ensure volume has no FC mappings.
     *
     * @param volumeSnapshot
     *            Type: Input.
     * @return task
     */
    public DriverTask deleteVolumeSnapshot(VolumeSnapshot volumeSnapshot) {

        DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_DELETE_SNAPSHOT_VOLUMES);

        _log.info("deleteVolumeSnapshot() for storage system {} - start", volumeSnapshot.getStorageSystemId());
        SSHConnection connection = null;

        try {
            connection = connectionManager.getClientBySystemId(volumeSnapshot.getStorageSystemId());

            /**
             * Create a timer thread. The default volume service heart beat
             * is every 10 seconds. The flashCopy usually takes hours before
             * it finishes. Don't set the sleep interval shorter than the
             * heartbeat. Otherwise volume service heartbeat will not be
             * serviced.
             */

            checkVolumeFCMappings(task, volumeSnapshot, connection);

            // Wait for the checkVolumeFCMapping thread to notify
            // scheduledExecutorService.wait();


            _log.info(String.format("Deleting the snapshot volume Id %s\n", volumeSnapshot.getNativeId()));

            // Delete the Snapshot Volume
            IBMSVCDeleteVolumeResult resultDelVol = IBMSVCCLI.deleteStorageVolumes(connection,
                    volumeSnapshot.getNativeId());

            if (resultDelVol.isSuccess()) {
                _log.info(String.format("Deleted snapshot volume Id %s.\n", resultDelVol.getId()));
                task.setMessage(String.format("Snapshot volume Id %s has been deleted.", resultDelVol.getId()));
                task.setStatus(DriverTask.TaskStatus.READY);
            } else {
                _log.error(String.format("Deleting snapshot volume Id %s failed : %s\n", resultDelVol.getId(),
                        resultDelVol.getErrorString()), resultDelVol.isSuccess());
                task.setMessage(String.format("Deleting snapshot volume Id %s failed : %s", resultDelVol.getId(),
                        resultDelVol.getErrorString()));
                task.setStatus(DriverTask.TaskStatus.FAILED);
            }

        } catch (Exception e) {
            _log.error("Unable to delete the snapshot volume {} on the storage system {}",
                    volumeSnapshot.getNativeId(), volumeSnapshot.getStorageSystemId());
            task.setMessage(String.format("Unable to delete the snapshot volume %s on the storage system %s",
                    volumeSnapshot.getDeviceLabel(), volumeSnapshot.getStorageSystemId()) + e.getMessage());
            task.setStatus(DriverTask.TaskStatus.FAILED);
            e.printStackTrace();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        _log.info("deleteVolumeSnapshot() for storage system {} - end", volumeSnapshot.getStorageSystemId());

        return task;
    }

    private void checkVolumeFCMappings(DriverTask task, VolumeSnapshot volumeSnapshot, SSHConnection connection) throws Exception{

        final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);

        IBMSVCVolFCMapChecker chkVolFCMap = new IBMSVCVolFCMapChecker(volumeSnapshot, connection, task, scheduledExecutorService);

        final ScheduledFuture<?> chkVolFCMapHandle = scheduledExecutorService.scheduleWithFixedDelay(chkVolFCMap, 10,
                10, SECONDS);

        scheduledExecutorService.schedule(new Runnable() {
            public void run() {
                chkVolFCMapHandle.cancel(true);
            }
        }, 3 * 60, SECONDS);

        //Wait for Volume FC Map to complete running
        while(chkVolFCMap.isRunning){
            _log.info("Waiting for Volume FC Mapping to complete \n");
            SECONDS.sleep(5);
        }

        chkVolFCMapHandle.cancel(true);

        // Shutting down the executor service
        scheduledExecutorService.shutdown();

    }

}
