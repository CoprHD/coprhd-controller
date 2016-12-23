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
import com.emc.storageos.driver.ibmsvcdriver.connection.Connection;
import com.emc.storageos.driver.ibmsvcdriver.connection.SSHConnection;
import com.emc.storageos.driver.ibmsvcdriver.impl.IBMSVCStorageDriver;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.model.VolumeSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;

public class IBMSVCVolFCMapChecker implements Runnable {

    private static final Logger _log = LoggerFactory.getLogger(IBMSVCStorageDriver.class);

    public  volatile boolean isRunning = true;

    private VolumeSnapshot volumeSnapshot;
    private SSHConnection connection;
    private DriverTask task;
    private ScheduledExecutorService scheduledExecutorService;

    public IBMSVCVolFCMapChecker(VolumeSnapshot volumeSnapshot, SSHConnection connection, DriverTask task, ScheduledExecutorService scheduledExecutorService) {

        this.volumeSnapshot = volumeSnapshot;
        this.connection = connection;
        this.task = task;
        this.scheduledExecutorService = scheduledExecutorService;
    }

    @Override
    public void run() {
        _log.info(String.format("Checking flashCopy mapping volume Id %s.\n", volumeSnapshot.getNativeId()));

        try{


        // 1. Query a Snapshot Volume for FC Mappings
        IBMSVCQueryVolumeFCMappingResult resultVolumeFCMap = IBMSVCCLI.queryVolumeFCMapping(connection,
                volumeSnapshot.getNativeId());

        _log.info(String.format("Result of query volume %s - %s.\n", volumeSnapshot.getNativeId(), resultVolumeFCMap.isSuccess()));

        if (resultVolumeFCMap.isSuccess()) {

            _log.info(String.format("Processing snapshot volume %s.\n", volumeSnapshot.getNativeId()));

            boolean wait_for_copy = false;

            for (Integer fcMappingInt : resultVolumeFCMap.getFcMappingIds()) {

                String fcMappingId = fcMappingInt.toString();

                IBMSVCQueryFCMappingResult resultQueryFCMapping = IBMSVCCLI.queryFCMapping(connection, fcMappingId,
                        false, null, null);

                if (resultQueryFCMapping.isSuccess()) {

                    _log.info(String.format("Queried flashCopy mapping %s\n", resultQueryFCMapping.getId()));

                    String srcVolId = resultQueryFCMapping.getProperty("SourceVolId");
                    String tgtVolId = resultQueryFCMapping.getProperty("TargetVolId");
                    String status = resultQueryFCMapping.getProperty("FCMapStatus");
                    //String copyRate = "50";
                    String copyRate = resultQueryFCMapping.getProperty("CopyRate");
                    String autoDelete = "on";

                    // If Snapshot
                    if (copyRate.equals("0")) {

                        if (srcVolId.equals(volumeSnapshot.getNativeId())) {
                            // Volume with snapshots. Return False if
                            // snapshot not allowed
                            IBMSVCChangeFCMappingResult resultChangeFCMapping = IBMSVCCLI
                                    .changeFCMapping(connection, fcMappingId, copyRate, autoDelete, null, null);

                            if (resultChangeFCMapping.isSuccess()) {
                                _log.info(String.format("Changed flashCopy mapping Id %s\n",
                                        resultChangeFCMapping.getFcMappingId()));
                                wait_for_copy = true;
                            } else {
                                _log.warn(String.format("Changing flashCopy mapping Id %s failed %s\n",
                                        resultChangeFCMapping.getFcMappingId(),
                                        resultChangeFCMapping.getErrorString()));
                            }

                        } else {
                            if (!tgtVolId.equals(volumeSnapshot.getNativeId())) {
                                _log.info(String.format(
                                        "Snapshot volume Id %s not involved in mapping (%s)s -> (%s)s.\n ",
                                        volumeSnapshot.getNativeId(), srcVolId, tgtVolId));
                                continue;
                            }

                            switch (status) {
                                case "copying":
                                case "prepared":
                                    stopFCMapping(connection, fcMappingId);
                                    // Need to wait for the fcmap to change to
                                    // stopped state before remove fcmap
                                    wait_for_copy = true;
                                    break;

                                case "stopping":
                                case "preparing":
                                    wait_for_copy = true;
                                    break;

                                default:
                                    deleteFCMapping(connection, fcMappingId);
                                    break;
                            }
                        }
                    } else {
                        // Copy in progress - wait and will autodelete
                        switch (status) {
                            case "prepared":
                                stopFCMapping(connection, fcMappingId);
                                deleteFCMapping(connection, fcMappingId);
                                break;
                            case "idle_or_copied":
                                deleteFCMapping(connection, fcMappingId);
                                break;
                            default:
                                wait_for_copy = true;
                                break;
                        }
                    }

                } else {
                    _log.warn(String.format("Querying flashCopy mapping failed : %s.\n",
                            resultQueryFCMapping.getId()));
                    task.setMessage(String.format("Querying the snapshot volume Id %s failed : %s.",
                            volumeSnapshot.getNativeId(), resultVolumeFCMap.getErrorString()));
                    task.setStatus(DriverTask.TaskStatus.FAILED);
                    break;
                }
            }
            if (!wait_for_copy || (resultVolumeFCMap.getFcMappingIds().size() == 0)) {
                //notifyAll();
                isRunning = false;
            }

        } else {
            _log.error(String.format("Querying the volume Id %s flashCopy mappings failed : %s.\n",
                    volumeSnapshot.getNativeId(), resultVolumeFCMap.getErrorString()));

            task.setMessage(String.format("Querying the volume Id %s flashCopy mappings failed : %s.",
                    volumeSnapshot.getNativeId(), resultVolumeFCMap.getErrorString()));

            task.setStatus(DriverTask.TaskStatus.FAILED);
            scheduledExecutorService.shutdown();
        }

        }catch(Exception e){
            _log.error(String.format("Querying the volume Id %s flashCopy mappings failed : %s.\n",
                    volumeSnapshot.getNativeId(), e.getMessage()));

            task.setMessage(String.format("Querying the volume Id %s flashCopy mappings failed : %s.",
                    volumeSnapshot.getNativeId(), e.getMessage()));

            task.setStatus(DriverTask.TaskStatus.FAILED);
            isRunning = false;
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
    public static void stopFCMapping(SSHConnection connection, String fcMappingId) {
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
    public static void deleteFCMapping(SSHConnection connection, String fcMappingId) {
        // remove from consistency group
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
