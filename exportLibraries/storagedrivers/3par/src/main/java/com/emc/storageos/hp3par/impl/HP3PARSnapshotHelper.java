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
import com.emc.storageos.storagedriver.model.VolumeSnapshot;
import com.emc.storageos.storagedriver.model.StorageObject.AccessStatus;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;

public class HP3PARSnapshotHelper {

	private static final Logger _log = LoggerFactory.getLogger(HP3PARSnapshotHelper.class);
	private HP3PARUtil hp3parUtil;

	public DriverTask createVolumeSnapshot(List<VolumeSnapshot> snapshots, StorageCapabilities capabilities,
			DriverTask task, Registry driverRegistry) {

		for (VolumeSnapshot snap : snapshots) {
			try {
				// native id = null ,
				_log.info(
						"3PARDriver: createVolumeSnapshot for storage system native id {}, snapshot name {}, parent id {}- start",
						snap.getNativeId(), snap.getDisplayName(), snap.getParentId());
				Boolean readOnly = true;

				// get Api client
				HP3PARApi hp3parApi = hp3parUtil.getHP3PARDeviceFromNativeId(snap.getStorageSystemId(), driverRegistry);

				VolumeDetailsCommandResult volResult = null;
				if (snap.getAccessStatus() != AccessStatus.READ_ONLY) {
					readOnly = false;
				}
				// Create volume snapshot
				hp3parApi.createVirtualCopy(snap.getParentId(), snap.getDisplayName(), readOnly);
				volResult = hp3parApi.getVolumeDetails(snap.getDisplayName());

				// Actual size of the volume in array
				snap.setProvisionedCapacity(volResult.getSizeMiB() * HP3PARConstants.MEGA_BYTE);
				snap.setWwn(volResult.getWwn());
				snap.setNativeId(snap.getDisplayName()); // required for volume
															// delete
				snap.setDeviceLabel(snap.getDisplayName());
				snap.setAccessStatus(snap.getAccessStatus());

				task.setStatus(DriverTask.TaskStatus.READY);
				_log.info("createVolumeSnapshot for storage system native id {}, snapshot name {}, parent id {} - end",
						snap.getStorageSystemId(), snap.getDisplayName(), snap.getParentId());
			} catch (Exception e) {
				String msg = String.format(
						"3PARDriver: Unable to create volume snap name %s for parent base volume id %s whose storage system native id is %s; Error: %s.\n",
						snap.getDisplayName(), snap.getParentId(), snap.getStorageSystemId(), e.getMessage());
				_log.error(msg);
				task.setMessage(msg);
				task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
				e.printStackTrace();
			}
		} // end for each volume snapshot creation

		return task;
	}

	public DriverTask restoreSnapshot(List<VolumeSnapshot> snapshots, DriverTask task, Registry driverRegistry) {

		// Executing restore for each requested volume snapshot (in one or more
		// 3par system)
		for (VolumeSnapshot snap : snapshots) {
			try {
				_log.info(
						"3PARDriver: restoreSnapshot for storage system system id {}, snapshot name {} , native id {} , all = {} - start",
						snap.getStorageSystemId(), snap.getDisplayName(), snap.getNativeId(), snap.toString());

				// get Api client
				HP3PARApi hp3parApi = hp3parUtil.getHP3PARDeviceFromNativeId(snap.getStorageSystemId(), driverRegistry);

				// restore virtual copy
				hp3parApi.restoreVirtualCopy(snap.getNativeId());

				task.setStatus(DriverTask.TaskStatus.READY);
				_log.info("3PARDriver: restoreSnapshot for storage system  id {}, snapshot display name {} - end",
						snap.getStorageSystemId(), snap.getDisplayName());
			} catch (Exception e) {
				String msg = String.format(
						"3PARDriver: Unable to restore snapshot display name %s with native id %s for storage system id %s; Error: %s.\n",
						snap.getDisplayName(), snap.getNativeId(), snap.getStorageSystemId(), e.getMessage());
				_log.error(msg);
				task.setMessage(msg);
				task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
				e.printStackTrace();
			}
		} // end for each restore snapshot

		return task;
	}

	public DriverTask deleteVolumeSnapshot(VolumeSnapshot snap, DriverTask task, Registry driverRegistry) {

			try {
				_log.info(
						"3PARDriver: deleteVolumeSnapshot for storage system native id {}, snapshot name {} , native id {} - start",
						snap.getStorageSystemId(), snap.getDisplayName(), snap.getNativeId());

				// get Api client
				HP3PARApi hp3parApi = hp3parUtil.getHP3PARDeviceFromNativeId(snap.getStorageSystemId(), driverRegistry);

				// Delete virtual copy
				hp3parApi.deleteVirtualCopy(snap.getNativeId());

				task.setStatus(DriverTask.TaskStatus.READY);
				_log.info("3PARDriver: deleteVolumeSnapshot for storage system native id {}, snapshot name {} - end",
						snap.getStorageSystemId(), snap.getDisplayName());
			} catch (Exception e) {
				String msg = String.format(
						"3PARDriver: Unable to delete snapshot name %s with native id %s for storage system native id %s; Error: %s.\n",
						snap.getDisplayName(), snap.getNativeId(), snap.getStorageSystemId(), e.getMessage());
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
