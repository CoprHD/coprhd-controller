/*
  * Copyright (c) 2016 EMC Corporation
  * All Rights Reserved
  *
  * This software contains the intellectual property of EMC Corporation
  * or is licensed to EMC Corporation from third parties.  Use of this
  * software and the intellectual property contained therein is expressly
  * limited to the terms and conditions of the License Agreement under which
  * it is provided by or on behalf of EMC.
  */
package com.emc.storageos.driver.ibmsvcdriver.helpers;
import com.emc.storageos.driver.ibmsvcdriver.api.*;
import com.emc.storageos.driver.ibmsvcdriver.connection.ConnectionManager;
import com.emc.storageos.driver.ibmsvcdriver.connection.SSHConnection;
import com.emc.storageos.driver.ibmsvcdriver.impl.IBMSVCDriverTask;
import com.emc.storageos.driver.ibmsvcdriver.impl.IBMSVCStorageDriver;
import com.emc.storageos.driver.ibmsvcdriver.utils.IBMSVCConstants;
import com.emc.storageos.driver.ibmsvcdriver.utils.IBMSVCDriverConfiguration;
import com.emc.storageos.driver.ibmsvcdriver.utils.IBMSVCDriverUtils;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.model.VolumeClone;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


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

        List<IBMSVCStartFCMappingResult> listOfCreatedFCMaps = new ArrayList<>();
        List<VolumeClone> listOfCreatedClones = new ArrayList<>();

        for (VolumeClone volumeClone : clones) {


            SSHConnection connection = null;

            try {
                connection = connectionManager.getClientBySystemId(volumeClone.getStorageSystemId());

                IBMSVCStartFCMappingResult startFCMappingResult = IBMSVCFlashCopy.createAndStartFlashCopy(connection, volumeClone.getStorageSystemId(), volumeClone.getParentId(), volumeClone, true, false);

                listOfCreatedFCMaps.add(startFCMappingResult);
                listOfCreatedClones.add(volumeClone);

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

        try{
            IBMSVCFlashCopy.waitForFCMapState(listOfCreatedClones, listOfCreatedFCMaps);

            // Set task status to READY, FAILED, PARTIALLY_FAILED based on complete count
            IBMSVCDriverUtils.setTaskStatusBasedOnCount(clones.size(), listOfCreatedClones.size(), "Clones created and copied successfully", task);

        }catch(Exception ex){
            task.setMessage(String.format("Failed to wait for Clone Copy to complete %s", ex.getMessage()));
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

        // Set task status to READY, FAILED, PARTIALLY_FAILED based on complete count
        IBMSVCDriverUtils.setTaskStatusBasedOnCount(clones.size(), clonesDetached, "Clones detached successfully", task);

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

        // TODO: Convert to Async

        int clonesRestored = 0;

        List<IBMSVCStartFCMappingResult> listOfRestoredFCMaps = new ArrayList<>();
        List<VolumeClone> listOfRestoredClones = new ArrayList<>();

        _log.info("restoreFromClone() for storage system {} - start", clones.get(0).getStorageSystemId());

        for (VolumeClone volumeClone : clones) {

            SSHConnection connection = null;

            try {
                connection = connectionManager.getClientBySystemId(volumeClone.getStorageSystemId());

                IBMSVCStartFCMappingResult startFCMappingResult = IBMSVCFlashCopy.createAndStartFlashCopy(connection, volumeClone.getStorageSystemId(), volumeClone.getParentId(), volumeClone, true, true);

                volumeClone.setReplicationState(VolumeClone.ReplicationState.CREATED);

                listOfRestoredFCMaps.add(startFCMappingResult);
                listOfRestoredClones.add(volumeClone);

                clonesRestored += 1;

            } catch (Exception e) {
                _log.error("Unable to create the clone volume {} on the storage system {} - {}", volumeClone.getParentId(),
                        volumeClone.getStorageSystemId(), e.getMessage());

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

        }

        // TODO: wait for all clone copies to complete here and not inside loop above

        // Set order status based on number of successful completions
        if (clonesRestored == clones.size()) {
            task.setStatus(DriverTask.TaskStatus.READY);
        } else if (clonesRestored == 0) {
            task.setStatus(DriverTask.TaskStatus.FAILED);
        } else {
            task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
        }

        try{
            IBMSVCFlashCopy.waitForFCMapState(listOfRestoredClones, listOfRestoredFCMaps);

            // Set task status to READY, FAILED, PARTIALLY_FAILED based on complete count
            IBMSVCDriverUtils.setTaskStatusBasedOnCount(clones.size(), clonesRestored, "Restored from Clones successfully", task);

        }catch(Exception ex){
            task.setMessage(String.format("Failed to wait for restore from clone to complete copying - %s", ex.getMessage()));
            task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
        }

        _log.info("restoreFromClone() for storage system {} - end", clones.get(0).getStorageSystemId());

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
