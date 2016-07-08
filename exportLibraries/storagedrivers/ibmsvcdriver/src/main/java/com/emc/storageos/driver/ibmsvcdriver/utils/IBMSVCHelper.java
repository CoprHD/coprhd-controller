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
package com.emc.storageos.driver.ibmsvcdriver.utils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.storagedriver.model.VolumeSnapshot;

public class IBMSVCHelper {
    private static final Logger _log = LoggerFactory.getLogger(IBMSVCHelper.class);

    /**
     * Generate Task ID for a task type
     * 
     * @param taskType
     * @return task id
     */
    public static String getTaskId(IBMSVCConstants.TaskType taskType) {
        _log.info("%s+%s+%s", IBMSVCConstants.DRIVER_NAME, taskType.name(), UUID.randomUUID());
        return String.format("%s+%s+%s", IBMSVCConstants.DRIVER_NAME, taskType.name(), UUID.randomUUID());
    }

    /**
     * Generate timestamp
     * 
     * @return current time string
     */
    public static String getCurrentTime() {
        DateFormat datafomate = new SimpleDateFormat("yyyy.MM.dd G 'at' HH:mm:ss z");
        Date date = new Date();
        return datafomate.format(date);
    }

    /**
     * Check if all snapshots are from same storage system
     * 
     * @param snapshots
     * @return true if all volumes are from same storage system, false otherwise
     */
    public static boolean isFromSameStorageSystem(List<VolumeSnapshot> snapshots) {
        boolean isSameSys = false;
        if (snapshots != null && snapshots.size() > 0) {
            String storageSystemId = snapshots.get(0).getStorageSystemId();
            isSameSys = true;
            for (VolumeSnapshot snapshot : snapshots) {
                if (snapshot.getStorageSystemId() != storageSystemId) {
                    isSameSys = false;
                    break;
                }
            }
        }
        return isSameSys;
    }

    /**
     * Check if all snapshots are from same consistency group
     * 
     * @param snapshots
     * @return true if all volumes are from same consistency group, false otherwise
     */
    public static boolean isFromSameCGgroup(List<VolumeSnapshot> snapshots) {
        boolean isSameCG = false;
        if (snapshots != null && snapshots.size() > 0) {
            String groupId = snapshots.get(0).getConsistencyGroup();
            isSameCG = true;
            for (VolumeSnapshot snapshot : snapshots) {
                if (snapshot.getConsistencyGroup() != groupId) {
                    isSameCG = false;
                    break;
                }
            }
        }
        return isSameCG;
    }

}