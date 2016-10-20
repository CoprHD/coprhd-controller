/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.impl;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.hp3par.command.VolumeDetailsCommandResult;
import com.emc.storageos.hp3par.utils.HP3PARConstants;
import com.emc.storageos.hp3par.utils.HP3PARUtil;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.Registry;
import com.emc.storageos.storagedriver.model.VolumeClone;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;

public class HP3PARCloneHelper {

	private static final Logger _log = LoggerFactory.getLogger(HP3PARCloneHelper.class);
	private HP3PARUtil hp3parUtil;

	
	public DriverTask createVolumeClone(List<VolumeClone> clones, StorageCapabilities capabilities, DriverTask task, Registry driverRegistry) {

		String storageSystemId = null;
		HP3PARApi hp3parApi = null;
		for (VolumeClone clone : clones) {
			try {
				// native id = null ,
				_log.info(
						"3PARDriver: createVolumeClone for storage system native id {}, clone parent name {} , clone name {} - start",
						clone.toString(), clone.getParentId(), clone.getDisplayName());

				String localStorageSystemId = clone.getStorageSystemId();
				// get Api client
				if (storageSystemId == null || storageSystemId != localStorageSystemId) {
					storageSystemId = localStorageSystemId;
					hp3parApi = hp3parUtil.getHP3PARDeviceFromNativeId(localStorageSystemId,
							driverRegistry);
				}
				VolumeDetailsCommandResult volResult = null;

				// Create volume clone
				hp3parApi.createPhysicalCopy(clone.getParentId(), clone.getDisplayName(), clone.getStoragePoolId());
				volResult = hp3parApi.getVolumeDetails(clone.getDisplayName());

				// Actual size of the volume in array
				clone.setProvisionedCapacity(volResult.getSizeMiB() * HP3PARConstants.MEGA_BYTE);
				clone.setWwn(volResult.getWwn());
				clone.setNativeId(volResult.getName()); // required for volume
														// delete
				clone.setDeviceLabel(clone.getDisplayName());
				clone.setAccessStatus(clone.getAccessStatus());
				clone.setReplicationState(VolumeClone.ReplicationState.SYNCHRONIZED);

				task.setStatus(DriverTask.TaskStatus.READY);
				_log.info("createVolumeClone for storage system native id {}, volume clone name {} - end",
						clone.getStorageSystemId(), clone.getDisplayName());
			} catch (Exception e) {
				String msg = String.format(
						"3PARDriver: createVolumeClone Unable to create volume clone name %s for parent base volume id %s whose storage system native id is %s; Error: %s.\n",
						clone.getDisplayName(), clone.getParentId(), clone.getStorageSystemId(), e.getMessage());
				_log.info("createVolumeClone exception message {} ", e.getMessage());
				_log.error(msg);
				task.setMessage(msg);
				task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
				e.printStackTrace();
			}
		} // end for each volume clone creation

		return task;

	}

	public DriverTask restoreFromClone(List<VolumeClone> clones, Registry driverRegistry, DriverTask task) {

		String storageSystemId = null;
		HP3PARApi hp3parApi = null;

		// Executing restore for each requested volume clone (in one or more
		// 3par system)
		for (VolumeClone clone : clones) {
			try {
				_log.info(
						"3PARDriver: restoreFromClone for storage system system id {}, clone name {} , native id {}  - start",
						clone.getStorageSystemId(), clone.getDisplayName(), clone.getNativeId());

				String localStorageSystemId = clone.getStorageSystemId();
				// get Api client
				if (storageSystemId == null || storageSystemId != localStorageSystemId) {
					storageSystemId = localStorageSystemId;
					hp3parApi = hp3parUtil.getHP3PARDeviceFromNativeId(localStorageSystemId,
							driverRegistry);
				}

				// restore virtual copy
				hp3parApi.restorePhysicalCopy(clone.getNativeId());

			//	clone.setReplicationState(VolumeClone.ReplicationState.RESTORED);

				task.setStatus(DriverTask.TaskStatus.READY);
				_log.info(
						"3PARDriver: restoreFromClone successful for storage system  id {}, volume clone native id {} - end",
						clone.getStorageSystemId(), clone.getNativeId());
			} catch (Exception e) {
				String msg = String.format(
						"3PARDriver:restoreFromClone Unable to restore volume clone display name %s with native id %s for storage system id %s; Error: %s.\n",
						clone.getDisplayName(), clone.getNativeId(), clone.getStorageSystemId(), e.getMessage());
				_log.error(msg);
				task.setMessage(msg);
				task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
				e.printStackTrace();
			}
		} // end for each restore clone

		return task;
	}

	public DriverTask deleteVolumeClone(VolumeClone clone, DriverTask task, Registry driverRegistry) {

		String storageSystemId = null;
		HP3PARApi hp3parApi = null;

			try {
				_log.info(
						"3PARDriver: deleteVolumeClone for storage system native id {}, volume clone name {} , native id {} - start",
						clone.getStorageSystemId(), clone.getDisplayName(), clone.getNativeId());

				String localStorageSystemId = clone.getStorageSystemId();
				// get Api client
				if (storageSystemId == null || storageSystemId != localStorageSystemId) {
					storageSystemId = localStorageSystemId;
					hp3parApi = hp3parUtil.getHP3PARDeviceFromNativeId(localStorageSystemId,
							driverRegistry);
				}

				// Delete physical copy
				hp3parApi.deletePhysicalCopy(clone.getDeviceLabel());

				task.setStatus(DriverTask.TaskStatus.READY);
				_log.info("3PARDriver: deleteVolumeClone for storage system native id {}, volume clone name {} - end",
						clone.getStorageSystemId(), clone.getDisplayName());
			} catch (Exception e) {
				String msg = String.format(
						"3PARDriver: deleteVolumeClone Unable to delete volume clone name %s with native id %s for storage system native id %s; Error: %s.\n",
						clone.getDisplayName(), clone.getNativeId(), clone.getStorageSystemId(), e.getMessage());
				_log.error(msg);
				task.setMessage(msg);
				task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
				e.printStackTrace();
			}

		return task;
	}

	public HP3PARUtil getHp3parUtil() {
		return hp3parUtil;
	}

	public void setHp3parUtil(HP3PARUtil hp3parUtil) {
		this.hp3parUtil = hp3parUtil;
	}

}
