/*
 * Copyright 2016 Oregon State University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.emc.storageos.driver.scaleio;

import com.emc.storageos.driver.scaleio.api.ScaleIOConstants;
import com.emc.storageos.storagedriver.model.VolumeClone;
import com.emc.storageos.storagedriver.model.VolumeSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class ScaleIOHelper {
    private static final Logger log = LoggerFactory.getLogger(ScaleIOHelper.class);

    /**
     * Generate Task ID for a task type
     *
     * @param taskType
     * @return task id
     */
    public static String getTaskId(ScaleIOConstants.TaskType taskType) {
        return String.format("%s+%s+%s", ScaleIOConstants.DRIVER_NAME, taskType.name(), UUID.randomUUID());
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
        if (snapshots != null && !snapshots.isEmpty()) {
            String storageSystemId = snapshots.get(0).getStorageSystemId();
            isSameSys = true;
            for (VolumeSnapshot snapshot : snapshots) {
                if (!snapshot.getStorageSystemId().equals(storageSystemId)) {
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
        if (snapshots != null && !snapshots.isEmpty()) {
            String groupId = snapshots.get(0).getConsistencyGroup();
            isSameCG = true;
            for (VolumeSnapshot snapshot : snapshots) {
                if (!snapshot.getConsistencyGroup().equals(groupId)) {
                    isSameCG = false;
                    break;
                }
            }
        }
        return isSameCG;
    }

    /**
     * Compare domain name and system name
     *
     * @param domainName
     * @param systemName
     */
    public static Boolean compare(String domainName, String systemName) {
        return domainName.equalsIgnoreCase(systemName);
    }

    /**
     * Check if all clones are from same consistency group
     *
     * @param clones
     * @return true if all volumes are from same consistency group, false otherwise
     */
    public static boolean isFromSameStorageSystemClone(List<VolumeClone> clones) {
        boolean isSameCG = false;
        if (clones != null && !clones.isEmpty()) {
            String storageSystemId = clones.get(0).getStorageSystemId();
            isSameCG = true;
            for (VolumeClone clone : clones) {
                if (!clone.getStorageSystemId().equals(storageSystemId)) {
                    isSameCG = false;
                    break;
                }
            }
        }
        return isSameCG;
    }

    /**
     * Check if all clones are from same consistency group
     *
     * @param clones
     * @return true if all volumes are from same consistency group, false otherwise
     */
    public static boolean isFromSameCGgroupClone(List<VolumeClone> clones) {
        boolean isSameCG = false;
        if (clones != null && !clones.isEmpty()) {
            String groupId = clones.get(0).getConsistencyGroup();
            isSameCG = true;
            for (VolumeClone clone : clones) {
                if (!clone.getConsistencyGroup().equals(groupId)) {
                    isSameCG = false;
                    break;
                }
            }
        }
        return isSameCG;
    }

    /**
     * Calculate actual volume size in GB based on given request capacity: ScaleIO requires the size to be in granularity of 8 GB
     * @param requestedCapacity Request capacity in Byte
     * @return Actual capacity in GB
     */
    public static long calculateActualCapacityInGB(long requestedCapacity){
        requestedCapacity = requestedCapacity / ScaleIOConstants.GB_BYTE;
        long actualCapacity = requestedCapacity;
        // size must be a positive number in granularity of 8 GB
        if (requestedCapacity == 0)
            actualCapacity = 8;
        if (requestedCapacity % 8 != 0) {
            long tmp = requestedCapacity / 8 * 8;
            if (tmp < requestedCapacity) {
                actualCapacity = tmp + 8;
            } else {
                actualCapacity = tmp;
            }
        }
        return actualCapacity;
    }

}
