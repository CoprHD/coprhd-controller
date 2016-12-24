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

            _log.info("createVolumeSnapshot() for storage system {} - start", snapshots.get(0).getStorageSystemId());

            int snapshotsSuccessfullyCreated = 0;

            for (VolumeSnapshot volumeSnapshot : snapshots) {

                SSHConnection connection = null;

                try {
                    connection = connectionManager.getClientBySystemId(volumeSnapshot.getStorageSystemId());

                    IBMSVCFlashCopy.createAndStartFlashCopy(connection, volumeSnapshot.getStorageSystemId(), volumeSnapshot.getParentId(), volumeSnapshot, false, false);

                    snapshotsSuccessfullyCreated += 1;

                } catch (Exception e) {
                    _log.error("Unable to create the snapshot volume {} on the storage system {} - {}", volumeSnapshot.getParentId(),
                            volumeSnapshot.getStorageSystemId(), e.getMessage());

                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }

            }

            // Set order status based on number of successful completions
            if (snapshotsSuccessfullyCreated == snapshots.size()) {
                task.setStatus(DriverTask.TaskStatus.READY);
            } else if (snapshotsSuccessfullyCreated == 0) {
                task.setStatus(DriverTask.TaskStatus.FAILED);
            } else {
                task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
            }

            _log.info("createVolumeSnapshot() for storage system {} - end", snapshots.get(0).getStorageSystemId());

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

        _log.info("restoreSnapshot() for storage system {} - start", snapshots.get(0).getStorageSystemId());

        int snapshotsSuccessfullyRestored = 0;

        for (VolumeSnapshot volumeSnapshot : snapshots) {

            SSHConnection connection = null;

            try {
                connection = connectionManager.getClientBySystemId(volumeSnapshot.getStorageSystemId());

                IBMSVCFlashCopy.createAndStartFlashCopy(connection, volumeSnapshot.getStorageSystemId(),volumeSnapshot.getParentId(), volumeSnapshot, false, true);

                snapshotsSuccessfullyRestored += 1;

            } catch (Exception e) {
                _log.error("Unable to create the snapshot volume {} on the storage system {} - {}", volumeSnapshot.getParentId(),
                        volumeSnapshot.getStorageSystemId(), e.getMessage());

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

        }

        //TODO: Wait for Snapshot restore operation to complete

        // Set order status based on number of successful completions
        if (snapshotsSuccessfullyRestored == snapshots.size()) {
            task.setStatus(DriverTask.TaskStatus.READY);
        } else if (snapshotsSuccessfullyRestored == 0) {
            task.setStatus(DriverTask.TaskStatus.FAILED);
        } else {
            task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
        }

        _log.info("restoreSnapshot() for storage system {} - end", snapshots.get(0).getStorageSystemId());


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
     * NOTE: This is a customer specific logic. Customer required CG groups to be created with the hostname for all snapshots.
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

            // TODO: Is this required or shall we simply force delete snapshots
            // checkVolumeFCMappings(task, volumeSnapshot, connection);

            // Wait for the checkVolumeFCMapping thread to notify
            // This wait isn't working
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
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        _log.info("deleteVolumeSnapshot() for storage system {} - end", volumeSnapshot.getStorageSystemId());

        return task;
    }

    /**
     * Check Volume FC mappings
     * Starts an independent thread to wait for Volume FC Mappings to finish
     * @param task
     * @param volumeSnapshot
     * @param connection
     * @throws Exception
     */
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
