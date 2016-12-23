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
import com.emc.storageos.storagedriver.model.VolumeClone;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;

public class IBMSVCClones {

    private static final Logger _log = LoggerFactory.getLogger(IBMSVCStorageDriver.class);
    private IBMSVCDriverConfiguration ibmsvcdriverConfiguration = IBMSVCDriverConfiguration.getInstance();
    /*
     * Connection Manager for managing connection pool
     */
    private ConnectionManager connectionManager = null;

    /**
     * Constructor
     */
    public IBMSVCClones() {
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
     * Clone volume clones.
     *
     * @param clones
     *            Type: Input/Output.
     * @param capabilities
     *            capabilities of clones. Type: Input.
     * @return task
     */

    public DriverTask createVolumeClone(List<VolumeClone> clones, StorageCapabilities capabilities) {

        DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_CREATE_CLONE_VOLUMES);

        // TODO: Convert to Async

        _log.info("createVolumeClone() for storage system {} - start", clones.get(0).getStorageSystemId());

        int clonesSuccessfullyCreated = 0;

        for (VolumeClone volumeClone : clones) {


            SSHConnection connection = null;

            try {
                connection = connectionManager.getClientBySystemId(volumeClone.getStorageSystemId());

                IBMSVCFlashCopy.createAndStartFlashCopy(connection, volumeClone.getParentId(), volumeClone, true);

                volumeClone.setReplicationState(VolumeClone.ReplicationState.CREATED);

                clonesSuccessfullyCreated += 1;

            } catch (Exception e) {
                _log.error("Unable to create the clone volume {} on the storage system {} - {}", volumeClone.getParentId(),
                        volumeClone.getStorageSystemId(), e.getMessage());

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

        }

        // TODO: wait for all clone copies to complete

        // Set order status based on number of successful completions
        if (clonesSuccessfullyCreated == clones.size()) {
            task.setStatus(DriverTask.TaskStatus.READY);
        } else if (clonesSuccessfullyCreated == 0) {
            task.setStatus(DriverTask.TaskStatus.FAILED);
        } else {
            task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
        }

        _log.info("createVolumeClone() for storage system {} - end", clones.get(0).getStorageSystemId());

        return task;
    }

    /**
     * Detach volume clones.
     *
     * @param clones
     *            Type: Input/Output.
     * @return task
     */
    public DriverTask detachVolumeClone(List<VolumeClone> clones) {

        DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_DETACH_CLONE_VOLUMES);

        int clonesDetached = 0;

        for (VolumeClone volumeClone : clones) {

            _log.info("detachVolumeClone() for storage system {} - start", volumeClone.getStorageSystemId());

            SSHConnection connection = null;
            try {
                connection = connectionManager.getClientBySystemId(volumeClone.getStorageSystemId());

                _log.info(String.format("Detaching the clone volume Id %s\n", volumeClone.getNativeId()));

                IBMSVCFilterFCMappingResult resultFilterFCMap = IBMSVCCLI.filterVolumeFCMapping(connection, null,
                        volumeClone.getNativeId());

                if (resultFilterFCMap.isSuccess() && !resultFilterFCMap.getFcMapList().isEmpty()
                        && resultFilterFCMap.getFcMapList().size() < 2) {

                    IBMSVCFCMap fcMap = resultFilterFCMap.getFcMapList().get(0);

                    // Delete the Snapshot Volume
                    IBMSVCDeleteFCMappingResult resultDeleteMappingResult = IBMSVCCLI.deleteFCMapping(connection,
                            fcMap.getfCMapID());

                    if (resultDeleteMappingResult.isSuccess()) {
                        _log.info(String.format("Detached the volume Id %s and the new clone volume Id %s.\n",
                                volumeClone.getParentId(), volumeClone.getNativeId()));
                        task.setMessage(String.format("Detached the volume Id %s and the new clone volume Id %s.\n",
                                volumeClone.getParentId(), volumeClone.getNativeId()));
                        // task.setStatus(DriverTask.TaskStatus.READY);
                        clonesDetached += 1;
                    } else {
                        _log.error(
                                String.format("Detaching the clone volume from the volume Id %s failed : %s\n",
                                        volumeClone.getParentId(), resultDeleteMappingResult.getErrorString()),
                                resultDeleteMappingResult.isSuccess());
                        task.setMessage(String.format("Detaching the clone volume from the volume Id %s failed : %s",
                                volumeClone.getParentId(), resultDeleteMappingResult.isSuccess()));
                        task.setStatus(DriverTask.TaskStatus.FAILED);
                        break;
                    }
                }

            } catch (Exception e) {
                _log.error("Unable to detach the clone volume from the volume Id {} on the storage system {}",
                        volumeClone.getParentId(), volumeClone.getStorageSystemId());
                task.setMessage(String.format(
                        "Unable to detach the clone volume from the volume Id %s on the storage system %s",
                        volumeClone.getParentId(), volumeClone.getStorageSystemId()) + e.getMessage());
                task.setStatus(DriverTask.TaskStatus.FAILED);
                e.printStackTrace();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            _log.info("detachVolumeClone() for storage system {} - end", volumeClone.getStorageSystemId());
        }

        if (clonesDetached == clones.size()) {
            task.setStatus(DriverTask.TaskStatus.READY);
        } else if (clonesDetached == 0) {
            task.setStatus(DriverTask.TaskStatus.FAILED);
        } else {
            task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
        }

        return task;
    }

    /**
     * Restore Volume Clone
     *
     * @param clones
     * @return
     */
    public DriverTask restoreFromClone(List<VolumeClone> clones) {

        DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_RESTORE_CLONE_VOLUMES);

        int clonesRestored = 0;

        for (VolumeClone volumeClone : clones) {

            _log.info("restoreFromClone() for storage system {} - start", volumeClone.getStorageSystemId());

            SSHConnection connection = null;
            try {
                connection = connectionManager.getClientBySystemId(volumeClone.getStorageSystemId());

                // 1. Get the Source Volume details like fcMapCount,
                // seCopyCount, copyCount
                // As each Snapshot has an Max of 256 FC Mappings only for each
                // source volume
                IBMSVCGetVolumeResult resultQueryVolume = IBMSVCCLI.queryStorageVolume(connection,
                        volumeClone.getParentId());

                if (resultQueryVolume.isSuccess()) {

                    _log.info(String.format("Processing clone volume Id %s.\n",
                            resultQueryVolume.getProperty("VolumeId")));

                    int fcMapCount = Integer.parseInt(resultQueryVolume.getProperty("FCMapCount"));

                    if (fcMapCount < IBMSVCConstants.MAX_SOURCE_MAPPINGS) {

                        String sourceVolumeName = volumeClone.getNativeId();
                        String targetVolumeName = resultQueryVolume.getProperty("VolumeName");

                        // 2. Create FC Mapping for the source and target volume
                        // Set the fullCopy to true to indicate its Volume Clone
                        IBMSVCCreateFCMappingResult resultFCMapping = IBMSVCCLI.createFCMapping(connection,
                                sourceVolumeName, targetVolumeName, null, true);

                        if (resultFCMapping.isSuccess()) {
                            _log.info(String.format("Created flashCopy mapping %s\n", resultFCMapping.getId()));

                            IBMSVCFlashCopy.waitForFCMapState(5, connection, resultFCMapping.getId(), "idle_or_copied", -1);

                            IBMSVCStartFCMappingResult resultStartFCMapping = IBMSVCFlashCopy.startFCMapping(connection,
                                    resultFCMapping.getId(), true, true);

                            if (resultStartFCMapping.isSuccess()) {

                                // Wait for restore to complete
                                // TODO: Move to Asynchronous
                                // waitForFCMapState(5, connection, resultFCMapping.getId(), "idle_or_copied", 100);

                                _log.info(String.format(
                                        "Started flashCopy mapping Id %s for the source volume %s and the target volume %s.\n",
                                        resultStartFCMapping.getId(), sourceVolumeName, targetVolumeName));
                                task.setMessage(String.format(
                                        "Started flashCopy mapping Id %s for the source volume %s and the target volume %s.",
                                        resultStartFCMapping.getId(), sourceVolumeName, targetVolumeName)
                                        + resultFCMapping.getErrorString());
                                // task.setStatus(DriverTask.TaskStatus.READY);
                                clonesRestored += 1;
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
                        task.setMessage(
                                String.format("FlashCopy mapping has reached the maximum for the source volume %s",
                                        resultQueryVolume.getProperty("VolumeName"))
                                        + resultQueryVolume.getErrorString());
                        task.setStatus(DriverTask.TaskStatus.FAILED);
                    }

                } else {
                    _log.error(String.format("Querying storage volume %s failed : %s\n", volumeClone.getParentId(),
                            resultQueryVolume.getErrorString()), resultQueryVolume.isSuccess());
                    task.setMessage(String.format("Querying storage volume %s failed : %s.", volumeClone.getParentId(),
                            resultQueryVolume.getErrorString()));
                    task.setStatus(DriverTask.TaskStatus.FAILED);
                }

            } catch (Exception e) {
                _log.error("Unable to restore the clone volume {} on the storage system {}", volumeClone.getParentId(),
                        volumeClone.getStorageSystemId());
                task.setMessage(String.format("Unable to restore the clone volume %s on the storage system %s",
                        volumeClone.getDeviceLabel(), volumeClone.getStorageSystemId()) + e.getMessage());
                task.setStatus(DriverTask.TaskStatus.FAILED);
                e.printStackTrace();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            _log.info("restoreFromClone() for storage system {} - end", volumeClone.getStorageSystemId());
        }

        if (clonesRestored == clones.size()) {
            task.setStatus(DriverTask.TaskStatus.READY);
        } else if (clonesRestored == 0) {
            task.setStatus(DriverTask.TaskStatus.FAILED);
        } else {
            task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
        }

        return task;
    }

    /**
     * Delete volume clones.
     *
     * @param volumeClone
     *            clone to delete. Type: Input.
     * @return
     */
    public DriverTask deleteVolumeClone(VolumeClone volumeClone) {

        DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_DELETE_CLONE_VOLUMES);

        _log.info("deleteVolumeClone() for storage system {} - start", volumeClone.getStorageSystemId());

        SSHConnection connection = null;

        try {
            connection = connectionManager.getClientBySystemId(volumeClone.getStorageSystemId());

            _log.info(String.format("Deleting the clone volume Id %s\n", volumeClone.getNativeId()));

            // Delete the Snapshot Volume
            IBMSVCDeleteVolumeResult resultDelVol = IBMSVCCLI.deleteStorageVolumes(connection,
                    volumeClone.getNativeId());

            if (resultDelVol.isSuccess()) {
                _log.info(String.format("Deleted clone volume Id %s.\n", resultDelVol.getId()));
                task.setMessage(String.format("Clone volume Id %s has been deleted.", resultDelVol.getId()));
                task.setStatus(DriverTask.TaskStatus.READY);
            } else {
                _log.error(String.format("Deleting clone volume Id %s failed : %s\n", resultDelVol.getId(),
                        resultDelVol.getErrorString()), resultDelVol.isSuccess());
                task.setMessage(String.format("Deleting clone volume Id %s failed : %s", resultDelVol.getId(),
                        resultDelVol.getErrorString()));
                task.setStatus(DriverTask.TaskStatus.FAILED);
            }

        } catch (Exception e) {
            _log.error("Unable to delete the clone volume Id {} on the storage system {}",
                    volumeClone.getParentId(), volumeClone.getStorageSystemId());
            task.setMessage(String.format("Unable to delete the clone volume Id %s on the storage system %s",
                    volumeClone.getDeviceLabel(), volumeClone.getStorageSystemId()) + e.getMessage());
            task.setStatus(DriverTask.TaskStatus.FAILED);
            e.printStackTrace();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        _log.info("deleteVolumeClone() for storage system {} - end", volumeClone.getStorageSystemId());

        return task;
    }

}
