/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.impl;

import java.util.HashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.hp3par.command.ConsistencyGroupResult;
import com.emc.storageos.hp3par.command.VolumeDetailsCommandResult;
import com.emc.storageos.hp3par.command.VVSetCloneList.VVSetVolumeClone;
import com.emc.storageos.hp3par.utils.HP3PARConstants;
import com.emc.storageos.hp3par.utils.HP3PARUtil;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.Registry;
import com.emc.storageos.storagedriver.model.VolumeClone;
import com.emc.storageos.storagedriver.model.VolumeConsistencyGroup;
import com.emc.storageos.storagedriver.model.VolumeSnapshot;
import com.emc.storageos.storagedriver.model.StorageObject.AccessStatus;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.storagecapabilities.CapabilityInstance;

public class HP3PARCGHelper {

	private static final Logger _log = LoggerFactory.getLogger(HP3PARCGHelper.class);
	private HP3PARUtil hp3parUtil;

	public DriverTask createConsistencyGroup(VolumeConsistencyGroup consistencyGroup, DriverTask task,
			Registry driverRegistry) {

		try {
			_log.info(
					"3PARDriver: createConsistencyGroup for storage system  id {}, display name {} , native id {}, device lable id {} , cosistency group id {}  - start",
					consistencyGroup.getStorageSystemId(), consistencyGroup.getDisplayName(),
					consistencyGroup.getNativeId(), consistencyGroup.getDeviceLabel(),
					consistencyGroup.getConsistencyGroup());

			// get Api client
			HP3PARApi hp3parApi = hp3parUtil.getHP3PARDeviceFromNativeId(consistencyGroup.getStorageSystemId(),
					driverRegistry);

			ConsistencyGroupResult cgResult = null;

			// Create VV Set / Consistency Group
			hp3parApi.createVVset(consistencyGroup.getDisplayName());
			cgResult = hp3parApi.getVVsetDetails(consistencyGroup.getDisplayName());

			_log.info("3PARDriver: createConsistencyGroup getDetails " + cgResult.getDetails());
			consistencyGroup.setNativeId(consistencyGroup.getDisplayName());
			consistencyGroup.setDeviceLabel(consistencyGroup.getDisplayName());

			task.setStatus(DriverTask.TaskStatus.READY);
			_log.info(
					"3PARDriver: createConsistencyGroup for storage system  id {}, display name {} , native id {}, device lable id {} , cosistency group id {}  - end",
					consistencyGroup.getStorageSystemId(), consistencyGroup.getDisplayName(),
					consistencyGroup.getNativeId(), consistencyGroup.getDeviceLabel(),
					consistencyGroup.getConsistencyGroup());
		} catch (Exception e) {
			String msg = String.format(
					"3PARDriver: Unable to create consistency group name %s in storage system native id is %s; Error: %s.\n",
					consistencyGroup.getDisplayName(), consistencyGroup.getStorageSystemId(), e.getMessage());
			_log.error(msg);
			task.setMessage(msg);
			task.setStatus(DriverTask.TaskStatus.FAILED);
			e.printStackTrace();
		}

		return task;

	}

	public DriverTask deleteConsistencyGroup(VolumeConsistencyGroup consistencyGroup, DriverTask task,
			Registry driverRegistry) {
		_log.info(
				"3PARDriver: deleteConsistencyGroup for storage system  id {}, display name {} , native id {}, device lable id {} , cosistency group id {}  - start",
				consistencyGroup.getStorageSystemId(), consistencyGroup.getDisplayName(),
				consistencyGroup.getNativeId(), consistencyGroup.getDeviceLabel(),
				consistencyGroup.getConsistencyGroup());

		try {

			// get Api client
			HP3PARApi hp3parApi = hp3parUtil.getHP3PARDeviceFromNativeId(consistencyGroup.getStorageSystemId(),
					driverRegistry);

			// Delete virtual copies of CG
			hp3parApi.deleteVVset(consistencyGroup.getNativeId());

			task.setStatus(DriverTask.TaskStatus.READY);
			_log.info("3PARDriver: deleteConsistencyGroup for storage system native id {}, volume name {} - end",
					consistencyGroup.getStorageSystemId(), consistencyGroup.getDisplayName());
		} catch (Exception e) {
			String msg = String.format(
					"3PARDriver: deleteConsistencyGroup Unable to delete CG %s with native id %s which is part of storage system native id %s; Error: %s.\n",
					consistencyGroup.getDisplayName(), consistencyGroup.getNativeId(),
					consistencyGroup.getStorageSystemId(), e.getMessage());
			_log.error(msg);
			task.setMessage(msg);
			task.setStatus(DriverTask.TaskStatus.FAILED);
			e.printStackTrace();
		}

		return task;

	}

	public DriverTask createConsistencyGroupSnapshot(VolumeConsistencyGroup consistencyGroup,
			List<VolumeSnapshot> snapshots, List<CapabilityInstance> capabilities, DriverTask task,
			Registry driverRegistry) {
		_log.info(
				"3PARDriver: createConsistencyGroupSnapshot for storage system  id {}, display name {} , native id {} - start",
				consistencyGroup.getStorageSystemId(), consistencyGroup.getDisplayName(),
				consistencyGroup.getNativeId());
		String VVsetSnapshotName = consistencyGroup.getDisplayName();

		VolumeDetailsCommandResult volResult = null;

		try {

			Boolean readOnly = true;
			int noOfSnaps = snapshots.size();

			// get Vipr generated Snapshot name
			for (VolumeSnapshot snap : snapshots) {

				// native id = null ,
				_log.info(
						"3PARDriver: createConsistencyGroupSnapshot for volume native id {}, snap shot name generated is {} ",
						snap.getParentId(), snap.getDisplayName());

				if (snap.getAccessStatus() != AccessStatus.READ_ONLY) {
					readOnly = false;
				}

				String generatedSnapshotName = snap.getDisplayName();
				if (noOfSnaps > 1) {
				VVsetSnapshotName = generatedSnapshotName.substring(0, generatedSnapshotName.lastIndexOf("-")) + "-";
				}
				else {
					VVsetSnapshotName = generatedSnapshotName;
				}
				_log.info("3PARDriver: createConsistencyGroupSnapshot VVsetSnapshotName {} ", VVsetSnapshotName);
				break;

			}

			// get Api client
			HP3PARApi hp3parApi = hp3parUtil.getHP3PARDeviceFromNativeId(consistencyGroup.getStorageSystemId(),
					driverRegistry);

			// Create vvset snapshot
			hp3parApi.createVVsetVirtualCopy(consistencyGroup.getNativeId(), VVsetSnapshotName, readOnly);
			int volumeNumber = 0;
			int snapVolumeCount = snapshots.size();

			/**
			 * for each volume snapshot available find correct snapshot object
			 * and set the values
			 */

			while (volumeNumber < snapVolumeCount) {

				String snapshotCreated = VVsetSnapshotName + volumeNumber;
				_log.info(
						"3PARDriver: createConsistencyGroupSnapshot snapshotCreated {}, volumeNumber {} , snapVolumeCount {} ",
						snapshotCreated, volumeNumber, snapVolumeCount);

				volResult = hp3parApi.getVolumeDetails(VVsetSnapshotName + volumeNumber);
				if (volResult != null) {
					String baseVolume = volResult.getCopyOf();

					if (baseVolume != null) {
						for (VolumeSnapshot snap : snapshots) {

							String parentName = snap.getParentId();

							if (parentName.equals(baseVolume)) {
								_log.info(
										"createConsistencyGroupSnapshot Snapshot system native id {}, Parent id {}, base volume {}, "
												+ "access status {}, display name {}, native Name {}, DeviceLabel {}, wwn {} - Before ",
										snap.getStorageSystemId(), snap.getParentId(), baseVolume,
										snap.getAccessStatus(), snap.getDisplayName(), snap.getNativeId(),
										snap.getDeviceLabel(), snap.getWwn());

								snap.setWwn(volResult.getWwn());
								snap.setNativeId(volResult.getName());
								snap.setDeviceLabel(volResult.getName());

								// snap.setAccessStatus(volResult.getAccessStatus());
								snap.setDisplayName(volResult.getName());

								_log.info(
										"createConsistencyGroupSnapshot Snapshot system native id {}, Parent Volume {}, access status {}, display name {},"
												+ " native Name {}, DeviceLabel {}, wwn {} - After",
										snap.getStorageSystemId(), snap.getParentId(), snap.getAccessStatus(),
										snap.getDisplayName(), snap.getNativeId(), snap.getDeviceLabel(),
										snap.getWwn());
							}

						}
					} else {
						_log.info("3PARDriver: createConsistencyGroupSnapshot baseVolume is null");

					}

				}
				
				volumeNumber++;
			}

			task.setStatus(DriverTask.TaskStatus.READY);
			_log.info(
					"createConsistencyGroupSnapshot for storage system native id {}, CG display Name {}, CG native id {} - end",
					consistencyGroup.getStorageSystemId(), consistencyGroup.getDisplayName(),
					consistencyGroup.getNativeId());
		} catch (Exception e) {
			String msg = String.format(
					"3PARDriver: Unable to create vv set snap name %s and its native id %s whose storage system  id is %s; Error: %s.\n",
					VVsetSnapshotName, consistencyGroup.getNativeId(), consistencyGroup.getStorageSystemId(),
					e.getMessage());
			_log.error(msg);
			task.setMessage(msg);
			task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
			e.printStackTrace();
		}

		return task;

	}

	public DriverTask deleteConsistencyGroupSnapshot(List<VolumeSnapshot> snapshots, DriverTask task,
			Registry driverRegistry) {

		String storageSystemId = null;
		HP3PARApi hp3parApi = null;

		// For each requested CG volume snapshot
		for (VolumeSnapshot snap : snapshots) {
			try {
				_log.info(
						"3PARDriver: deleteConsistencyGroupSnapshot for storage system native id {}, volume name {} , native id {} - start",
						snap.getStorageSystemId(), snap.getDisplayName(), snap.getNativeId());

				String localStorageSystemId = snap.getStorageSystemId();
				// get Api client
				if (storageSystemId == null || storageSystemId != localStorageSystemId) {
					storageSystemId = localStorageSystemId;
					hp3parApi = hp3parUtil.getHP3PARDeviceFromNativeId(localStorageSystemId, driverRegistry);
				}

				// Delete virtual copy
				hp3parApi.deleteVirtualCopy(snap.getNativeId());

				task.setStatus(DriverTask.TaskStatus.READY);
				_log.info(
						"3PARDriver: deleteConsistencyGroupSnapshot for storage system native id {}, volume name {} - end",
						snap.getStorageSystemId(), snap.getDisplayName());
			} catch (Exception e) {
				String msg = String.format(
						"3PARDriver: deleteConsistencyGroupSnapshot Unable to delete cg snapshot name %s with native id %s for storage system native id %s; Error: %s.\n",
						snap.getDisplayName(), snap.getNativeId(), snap.getStorageSystemId(), e.getMessage());
				_log.error(msg);
				task.setMessage(msg);
				task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
				e.printStackTrace();
			}
		} // end for each delete snapshot

		return task;
	}

	/**
	 * Creating physical copy for VVset or CG clone Rest API expects created
	 * VVset with its corresponding volumes types for clone destination So,
	 * There are many ways for implementation
	 * 
	 * 1. Customer will provide the VVSet name which already exist in Array
	 * with its corresponding similar volumes for cloning
	 * 
	 * 2. Customer will not provide any existing and matching VV set with
	 * corresponding volumes for CG clone
	 * 
	 * 3. Customer will provide VVset name which is created but volumes are not
	 * matching for clone creation.
	 * 
	 * Create new VV Set / CG . Create new volumes similar to parent VVSet
	 * volumes Use this newly created VV set for CG clone
	 * 
	 * option 2 is implemented, need to handle negative / error cases of option
	 * 3
	 */

	public DriverTask createConsistencyGroupClone(VolumeConsistencyGroup consistencyGroup, List<VolumeClone> clones,
			List<CapabilityInstance> capabilities, DriverTask task, Registry driverRegistry) {

		_log.info(
				"3PARDriver: createConsistencyGroupClone for storage system  id {}, Base CG name {} , Base CG native id {} - start",
				consistencyGroup.getStorageSystemId(), consistencyGroup.getDisplayName(),
				consistencyGroup.getNativeId());
		String VVsetNameForClone = consistencyGroup.getDisplayName();

		VolumeDetailsCommandResult volResult = null;
		HashMap<String, VolumeClone> clonesMap = new HashMap<String, VolumeClone>();

		try {

			Boolean saveSnapshot = true;
			
			// get Api client
			HP3PARApi hp3parApi = hp3parUtil.getHP3PARDeviceFromNativeId(consistencyGroup.getStorageSystemId(),
					driverRegistry);

			// get Vipr generated clone name
			for (VolumeClone clone : clones) {

				// native id = null ,
				_log.info("3PARDriver: createConsistencyGroupClone generated clone parent id {}, display name {} ",
						clone.getParentId(), clone.getDisplayName());

				String generatedCloneName = clone.getDisplayName();
				VVsetNameForClone = generatedCloneName.substring(0, generatedCloneName.lastIndexOf("-"));
				_log.info("3PARDriver: createConsistencyGroupClone CG name {} to be used in cloning ",
						VVsetNameForClone);
				clonesMap.put(clone.getParentId(), clone);

			}
			_log.info("3PARDriver: createConsistencyGroupClone  clonesMap {}", clonesMap.toString());

			// Create vvset clone
			VVSetVolumeClone[] result = hp3parApi.createVVsetPhysicalCopy(consistencyGroup.getNativeId(),
					VVsetNameForClone, clones, saveSnapshot);

			_log.info("3PARDriver: createConsistencyGroupClone outPut of CG clone result  {} ", result.toString());

			for (VVSetVolumeClone cloneCreated : result) {
				VolumeClone clone = clonesMap.get(cloneCreated.getParent());

				_log.info(
						"createConsistencyGroupClone cloneCreated {} and local clone obj nativeid = {} , parent id = {}",
						cloneCreated.getValues(), clone.getNativeId(), clone.getParentId());
				volResult = hp3parApi.getVolumeDetails(cloneCreated.getChild());

				_log.info("createConsistencyGroupClone cloneCreated All values {} ", volResult.getAllValues());

				clone.setWwn(volResult.getWwn());
				clone.setNativeId(volResult.getId());
				clone.setDeviceLabel(volResult.getName());

				// snap.setAccessStatus(volResult.getAccessStatus());
				clone.setDisplayName(volResult.getName());

				clone.setReplicationState(VolumeClone.ReplicationState.SYNCHRONIZED);

				clone.setProvisionedCapacity(clone.getRequestedCapacity());
				clone.setAllocatedCapacity(clone.getRequestedCapacity());

			}

			task.setStatus(DriverTask.TaskStatus.READY);
			_log.info(
					"createConsistencyGroupClone for storage system native id {}, CG display Name {}, CG native id {} - end",
					consistencyGroup.getStorageSystemId(), consistencyGroup.getDisplayName(),
					consistencyGroup.getNativeId());
		} catch (Exception e) {
			String msg = String.format(
					"3PARDriver: createConsistencyGroupClone Unable to create vv set snap name %s and its native id %s whose storage system  id is %s; Error: %s.\n",
					VVsetNameForClone, consistencyGroup.getNativeId(), consistencyGroup.getStorageSystemId(),
					e.getMessage());
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

	public DriverTask addOrRemoveConsistencyGroupVolume(List<StorageVolume> volumes, DriverTask task,
			Registry driverRegistry, int action) {
		String storageSystemId = null;
		HP3PARApi hp3parApi = null;

		for (StorageVolume volume : volumes) {
			try {
				_log.info(
						"3PARDriver: removeConsistencyGroupVolume for storage system  id {}, display name {} , native id {}, device lable id {} , cosistency group id {}",
						volume.getStorageSystemId(), volume.getDisplayName(), volume.getNativeId(),
						volume.getDeviceLabel(), volume.getConsistencyGroup());

				String localStorageSystemId = volume.getStorageSystemId();
				// get Api client
				if (storageSystemId == null || storageSystemId != localStorageSystemId) {
					storageSystemId = localStorageSystemId;
					hp3parApi = hp3parUtil.getHP3PARDeviceFromNativeId(localStorageSystemId, driverRegistry);
				}

				// update VV Set / Consistency Group
				hp3parApi.updateVVset(volume.getConsistencyGroup(), volume.getDisplayName(), action);

				task.setStatus(DriverTask.TaskStatus.READY);

			} catch (Exception e) {
				String msg = String.format(
						"3PARDriver: Unable to create consistency group name %s in storage system native id is %s; Error: %s.\n",
						volume.getDisplayName(), volume.getStorageSystemId(), e.getMessage());
				_log.error(msg);
				task.setMessage(msg);
				task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
				e.printStackTrace();
			}
		}

		return task;
	}


}
