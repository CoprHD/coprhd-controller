
/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.mutable.MutableBoolean;
import org.apache.commons.lang.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.hp3par.command.CPGCommandResult;
import com.emc.storageos.hp3par.command.CPGMember;
import com.emc.storageos.hp3par.command.ConsistencyGroupResult;
import com.emc.storageos.hp3par.command.FcPath;
import com.emc.storageos.hp3par.command.HostCommandResult;
import com.emc.storageos.hp3par.command.HostMember;
import com.emc.storageos.hp3par.command.HostSetDetailsCommandResult;
import com.emc.storageos.hp3par.command.ISCSIPath;
import com.emc.storageos.hp3par.command.Position;
import com.emc.storageos.hp3par.command.SystemCommandResult;
import com.emc.storageos.hp3par.command.VVSetCloneList.VVSetVolumeClone;
import com.emc.storageos.hp3par.command.VirtualLun;
import com.emc.storageos.hp3par.command.VirtualLunsList;
import com.emc.storageos.hp3par.command.VlunResult;
import com.emc.storageos.hp3par.command.VolumeDetailsCommandResult;
import com.emc.storageos.hp3par.connection.HP3PARApiFactory;
import com.emc.storageos.hp3par.utils.CompleteError;
import com.emc.storageos.hp3par.utils.HP3PARConstants;
import com.emc.storageos.hp3par.utils.HP3PARUtil;
import com.emc.storageos.hp3par.utils.SanUtils;
import com.emc.storageos.storagedriver.AbstractStorageDriver;
import com.emc.storageos.storagedriver.BlockStorageDriver;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.HostExportInfo;
import com.emc.storageos.storagedriver.RegistrationData;
import com.emc.storageos.storagedriver.model.Initiator;
import com.emc.storageos.storagedriver.model.Initiator.HostOsType;
import com.emc.storageos.storagedriver.model.Initiator.Type;
import com.emc.storageos.storagedriver.model.StorageHostComponent;
import com.emc.storageos.storagedriver.model.StorageObject;
import com.emc.storageos.storagedriver.model.StorageObject.AccessStatus;
import com.emc.storageos.storagedriver.model.StoragePool;
import com.emc.storageos.storagedriver.model.StoragePool.PoolOperationalStatus;
import com.emc.storageos.storagedriver.model.StoragePool.PoolServiceType;
import com.emc.storageos.storagedriver.model.StoragePool.Protocols;
import com.emc.storageos.storagedriver.model.StoragePool.RaidLevels;
import com.emc.storageos.storagedriver.model.StoragePool.SupportedDriveTypes;
import com.emc.storageos.storagedriver.model.StoragePool.SupportedResourceType;
import com.emc.storageos.storagedriver.model.StoragePort;
import com.emc.storageos.storagedriver.model.StorageProvider;
import com.emc.storageos.storagedriver.model.StorageSystem;
import com.emc.storageos.storagedriver.model.StorageSystem.SupportedProvisioningType;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.model.VolumeClone;
import com.emc.storageos.storagedriver.model.VolumeConsistencyGroup;
import com.emc.storageos.storagedriver.model.VolumeMirror;
import com.emc.storageos.storagedriver.model.VolumeSnapshot;
import com.emc.storageos.storagedriver.storagecapabilities.CapabilityInstance;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;

/**
 * 
 * Implements functions to discover the HP 3PAR storage and provide provisioning
 * You can refer super class for method details
 *
 */
public class HP3PARStorageDriver extends AbstractStorageDriver implements BlockStorageDriver {

	private static final Logger _log = LoggerFactory.getLogger(HP3PARStorageDriver.class);

	// Independent functionalities
	private HP3PARIngestHelper ingestHelper;
	private HP3PARUtil hp3parUtil;
	private HP3PARApiFactory hp3parApiFactory;
	private HP3PARSnapshotHelper snapshotHelper;
	private HP3PARCloneHelper cloneHelper;
	private HP3PARCGHelper cgHelper;
	
	@Override
	public DriverTask getTask(String taskId) {
		_log.info("3PARDriver: getTask Running ");
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * objectid is nothing but the native id of the storage object. For
	 * consistency group it would be the native id of consistency group, which
	 * on the HP3PAR array is nothing but the name of the volume set.
	 */
	@Override
	public <T extends StorageObject> T getStorageObject(String storageSystemId, String objectId, Class<T> type) {
		// TODO Auto-generated method stub
		_log.info("3PARDriver: getStorageObject enter ");
		try {
			HP3PARApi hp3parApi = hp3parUtil.getHP3PARDeviceFromNativeId(storageSystemId, this.driverRegistry);
			ConsistencyGroupResult cgResult = null;
			if (VolumeConsistencyGroup.class.getSimpleName().equals(type.getSimpleName())) {
				cgResult = hp3parApi.getVVsetDetails(objectId);
				VolumeConsistencyGroup cg = new VolumeConsistencyGroup();
				cg.setStorageSystemId(storageSystemId);
				cg.setNativeId(cgResult.getName());
				cg.setDeviceLabel(objectId);
				_log.info("3PARDriver: getStorageObject leaving ");
				return (T) cg;
			}
		} catch (Exception e) {
			String msg = String.format("3PARDriver: Unable to get Stroage Object for id %s; Error: %s.\n", objectId,
					e.getMessage());
			_log.error(msg);
			e.printStackTrace();
			return (T) null;
		}
		return null;
	}

	@Override
	public RegistrationData getRegistrationData() {
		_log.info("3PARDriver: getStorageObject enter");
        RegistrationData registrationData = new RegistrationData(HP3PARConstants.DRIVER_NAME, "driversystem", null);
        _log.info("3PARDriver: getStorageObject leave");
        return registrationData;
	}

	/**
	 * Get storage system information and capabilities
	 */
	@Override
	public DriverTask discoverStorageSystem(StorageSystem storageSystem) {
		DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_DISCOVER_STORAGE_SYSTEM);

		try {
			_log.info("3PARDriver:discoverStorageSystem information for storage system {}, name {} - start",
					storageSystem.getIpAddress(), storageSystem.getSystemName());

			URI deviceURI = new URI("https", null, storageSystem.getIpAddress(), storageSystem.getPortNumber(), "/",
					null, null);

			// remove '/' as lock fails with this name
			String uniqueId = deviceURI.toString();
			uniqueId = uniqueId.replace("/", "");

			HP3PARApi hp3parApi = hp3parUtil.getHP3PARDevice(storageSystem);
			String authToken = hp3parApi.getAuthToken(storageSystem.getUsername(), storageSystem.getPassword());
			if (authToken == null) {
				throw new HP3PARException("Could not get authentication token");
			}

			// Verify user role
			hp3parApi.verifyUserRole(storageSystem.getUsername());

			// get storage details
			SystemCommandResult systemRes = hp3parApi.getSystemDetails();
			storageSystem.setSerialNumber(systemRes.getSerialNumber());
			storageSystem.setMajorVersion(systemRes.getSystemVersion());
			storageSystem.setMinorVersion("0"); // as there is no individual portion in 3par api

			// protocols supported
			List<String> protocols = new ArrayList<String>();
			protocols.add(Protocols.iSCSI.toString());
			protocols.add(Protocols.FC.toString());
			storageSystem.setProtocols(protocols);

			storageSystem.setFirmwareVersion(systemRes.getSystemVersion());
			if (systemRes.getSystemVersion().startsWith("3.1") || systemRes.getSystemVersion().startsWith("3.2.1") ) {
			    // SDK is taking care of unsupported message
			    storageSystem.setIsSupportedVersion(false);
			} else {
			    storageSystem.setIsSupportedVersion(true);
			}
			
			storageSystem.setModel(systemRes.getModel());
			storageSystem.setProvisioningType(SupportedProvisioningType.THIN_AND_THICK);
			Set<StorageSystem.SupportedReplication> supportedReplications = new HashSet<>();
			storageSystem.setSupportedReplications(supportedReplications);

			// Storage object properties
			storageSystem.setNativeId(uniqueId + ":" + systemRes.getSerialNumber());

			if (storageSystem.getDeviceLabel() == null) {
				if (storageSystem.getDisplayName() != null) {
					storageSystem.setDeviceLabel(storageSystem.getDisplayName());
				} else if (systemRes.getName() != null) {
					storageSystem.setDeviceLabel(systemRes.getName());
					storageSystem.setDisplayName(systemRes.getName());
				}
			}

			storageSystem.setAccessStatus(AccessStatus.READ_WRITE);
			setConnInfoToRegistry(storageSystem.getNativeId(), storageSystem.getIpAddress(),
					storageSystem.getPortNumber(), storageSystem.getUsername(), storageSystem.getPassword());

			task.setStatus(DriverTask.TaskStatus.READY);
			_log.info("3PARDriver: Successfull discovery storage system {}, name {} - end",
					storageSystem.getIpAddress(), storageSystem.getSystemName());
		} catch (Exception e) {
			String msg = String.format("3PARDriver: Unable to discover the storage system %s ip %s; Error: %s.\n",
					storageSystem.getSystemName(), storageSystem.getIpAddress(), e);
			_log.error(msg);
			_log.error(CompleteError.getStackTrace(e));
			task.setMessage(msg);
			task.setStatus(DriverTask.TaskStatus.FAILED);
			e.printStackTrace();
		}

		return task;
	}

	/**
	 * Get storage pool information and its capabilities
	 */
	@Override
	public DriverTask discoverStoragePools(StorageSystem storageSystem, List<StoragePool> storagePools) {
		// For this 3PAR system
		_log.info("3PARDriver: discoverStoragePools information for storage system {}, nativeId {} - start",
				storageSystem.getIpAddress(), storageSystem.getNativeId());
		DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_DISCOVER_STORAGE_POOLS);

		try {
			// get Api client
			HP3PARApi hp3parApi = hp3parUtil.getHP3PARDeviceFromNativeId(storageSystem.getNativeId(),
					this.driverRegistry);

			// get storage pool details
			CPGCommandResult cpgResult = hp3parApi.getAllCPGDetails();

			// for each ViPR Storage pool = 3PAR CPG
			for (CPGMember currMember:cpgResult.getMembers()) {
				StoragePool pool = new StoragePool();

				pool.setPoolName(currMember.getName());
				pool.setStorageSystemId(storageSystem.getNativeId());

				Set<Protocols> supportedProtocols = new HashSet<>();
				supportedProtocols.add(Protocols.iSCSI);
				supportedProtocols.add(Protocols.FC);
				pool.setProtocols(supportedProtocols);

				pool.setTotalCapacity((currMember.getUsrUsage().getTotalMiB().longValue()
						+ currMember.getSAUsage().getTotalMiB().longValue()
						+ currMember.getSDUsage().getTotalMiB().longValue()) * HP3PARConstants.KILO_BYTE);
				pool.setSubscribedCapacity((currMember.getUsrUsage().getUsedMiB().longValue()
						+ currMember.getSAUsage().getUsedMiB().longValue()
						+ currMember.getSDUsage().getUsedMiB().longValue()) * HP3PARConstants.KILO_BYTE);
				pool.setFreeCapacity(pool.getTotalCapacity() - pool.getSubscribedCapacity());

				pool.setOperationalStatus(
						currMember.getState() == 1 ? PoolOperationalStatus.READY : PoolOperationalStatus.NOTREADY);

				Set<RaidLevels> supportedRaidLevels = new HashSet<>();
				switch (currMember.getSDGrowth().getLDLayout().getRAIDType()) {
				case 1:
					supportedRaidLevels.add(RaidLevels.RAID0);
					break;
				case 2:
					supportedRaidLevels.add(RaidLevels.RAID1);
					break;
				case 3:
					supportedRaidLevels.add(RaidLevels.RAID5);
					break;
				case 4:
					supportedRaidLevels.add(RaidLevels.RAID6);
					break;
				}
				pool.setSupportedRaidLevels(supportedRaidLevels);

				Set<SupportedDriveTypes> supportedDriveTypes = new HashSet<>();
				for (int j = 0; j < currMember.getSDGrowth().getLDLayout().getDiskPatterns().size(); j++) {
					switch (currMember.getSDGrowth().getLDLayout().getDiskPatterns().get(j).getDiskType()) {
					case 1:
						supportedDriveTypes.add(SupportedDriveTypes.FC);
						break;
					case 2:
						supportedDriveTypes.add(SupportedDriveTypes.NL_SAS);
						break;
					case 3:
						supportedDriveTypes.add(SupportedDriveTypes.SSD);
						break;
					}
				}
				pool.setSupportedDriveTypes(supportedDriveTypes);

				pool.setMaximumThinVolumeSize(16 * HP3PARConstants.MEGA_BYTE);
				pool.setMinimumThinVolumeSize(256 * HP3PARConstants.KILO_BYTE);
				pool.setMaximumThickVolumeSize(16 * HP3PARConstants.MEGA_BYTE);
				pool.setMinimumThickVolumeSize(256 * HP3PARConstants.KILO_BYTE);

				pool.setSupportedResourceType(SupportedResourceType.THIN_AND_THICK);
				pool.setPoolServiceType(PoolServiceType.block);

				// Storage object properties
				pool.setNativeId(currMember.getName()); // SB SDK is not sending
														// pool name in volume
														// creation
				pool.setDeviceLabel(currMember.getName());
				pool.setDisplayName(currMember.getName());
				storageSystem.setAccessStatus(AccessStatus.READ_WRITE);

				_log.info("3PARDriver: added storage pool {}, native id {}", pool.getPoolName(), pool.getNativeId());
				storagePools.add(pool);
			} // for each storage pool

			task.setStatus(DriverTask.TaskStatus.READY);
			_log.info("3PARDriver: discoverStoragePools information for storage system {}, nativeId {} - end",
					storageSystem.getIpAddress(), storageSystem.getNativeId());
		} catch (Exception e) {
			String msg = String.format(
					"3PARDriver: Unable to discover the storage pool information for storage system %s native id %s; Error: %s.\n",
					storageSystem.getSystemName(), storageSystem.getNativeId(), e);
			_log.error(msg);
			_log.error(CompleteError.getStackTrace(e));
			task.setMessage(msg);
			task.setStatus(DriverTask.TaskStatus.FAILED);
			e.printStackTrace();
		}
		return task;
	}

	@Override
	public DriverTask getStorageVolumes(StorageSystem storageSystem, List<StorageVolume> storageVolumes,
			MutableInt token) {
		_log.info("3PARDriver: getStorageVolumes Running ");
		DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_GET_STORAGE_VOLUMES);
		_log.info("3PARDriver: getStorageVolumes Leaving");
		return ingestHelper.getStorageVolumes(storageSystem, storageVolumes, token, task, this.driverRegistry);
	}

	/**
	 * Get storage port information
	 */
	@Override
	public DriverTask discoverStoragePorts(StorageSystem storageSystem, List<StoragePort> storagePorts) {
		// For this 3PAR system
		_log.info("3PARDriver: discoverStoragePorts information for storage system {}, nativeId {} - start",
				storageSystem.getIpAddress(), storageSystem.getNativeId());
		DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_DISCOVER_STORAGE_PORTS);

		try {
			hp3parUtil.discoverStoragePortsById(storageSystem.getNativeId(), storagePorts, this.driverRegistry);

			storageSystem.setAccessStatus(AccessStatus.READ_WRITE);
			task.setStatus(DriverTask.TaskStatus.READY);
			_log.info("3PARDriver: discoverStoragePorts information for storage system {}, nativeId {} - end",
					storageSystem.getIpAddress(), storageSystem.getNativeId());
		} catch (Exception e) {
			String msg = String.format(
					"3PARDriver: Unable to discover the storage port information for storage system %s native id %s; Error: %s.\n",
					storageSystem.getSystemName(), storageSystem.getNativeId(), e);
			_log.error(msg);
			_log.error(CompleteError.getStackTrace(e));
			task.setMessage(msg);
			task.setStatus(DriverTask.TaskStatus.FAILED);
			e.printStackTrace();
		}
		return task;
	}

	@Override
	public DriverTask discoverStorageHostComponents(StorageSystem storageSystem,
			List<StorageHostComponent> embeddedStorageHostComponents) {
		_log.error("3PARDriver: discoverStorageHostComponents not supported");
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * Create requested volumes
	 */
	@Override
	public DriverTask createVolumes(List<StorageVolume> volumes, StorageCapabilities capabilities) {
		DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_CREATE_STORAGE_VOLUMES);

		// For each requested volume
		for (StorageVolume volume : volumes) {
			try {
				_log.info("3PARDriver:createVolumes for storage system native id {}, volume name {} - start",
						volume.getStorageSystemId(), volume.getDisplayName());

				// get Api client
				HP3PARApi hp3parApi = hp3parUtil.getHP3PARDeviceFromNativeId(volume.getStorageSystemId(),
						this.driverRegistry);

				// Create volume
				VolumeDetailsCommandResult volResult = null;
				hp3parApi.createVolume(volume.getDisplayName(), volume.getStoragePoolId(),
						volume.getThinlyProvisioned(), volume.getRequestedCapacity() / HP3PARConstants.MEGA_BYTE);
				volResult = hp3parApi.getVolumeDetails(volume.getDisplayName());

				// Attributes of the volume in array
				volume.setProvisionedCapacity(volResult.getSizeMiB() * HP3PARConstants.MEGA_BYTE);
				volume.setAllocatedCapacity(volResult.getSizeMiB() * HP3PARConstants.MEGA_BYTE);
				volume.setWwn(volResult.getWwn());
				volume.setNativeId(volume.getDisplayName()); // required for volume delete
				volume.setDeviceLabel(volume.getDisplayName());
				volume.setAccessStatus(AccessStatus.READ_WRITE);

				// Update Consistency Group
				String volumeCGName = volume.getConsistencyGroup();
				if (volumeCGName != null && !volumeCGName.isEmpty()) {
					_log.info("3PARDriver:createVolumes Adding volume {} to consistency group {} ",
							volume.getDisplayName(), volumeCGName);
					int addMember = 1;
					hp3parApi.updateVVset(volumeCGName, volume.getNativeId(), addMember);
				}

				task.setStatus(DriverTask.TaskStatus.READY);
				_log.info("3PARDriver:createVolumes for storage system native id {}, volume name {} - end",
						volume.getStorageSystemId(), volume.getDisplayName());
			} catch (Exception e) {
				String msg = String.format(
						"3PARDriver: Unable to create volume name %s with pool id %s for storage system native id %s; Error: %s.\n",
						volume.getDisplayName(), volume.getStoragePoolId(), volume.getStorageSystemId(), e);
				_log.error(msg);
				_log.error(CompleteError.getStackTrace(e));
				task.setMessage(msg);
				task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
				e.printStackTrace();
			}
		} // end for each volume

		return task;
	}

	/**
     * Expand the size of requested volume
     */
	@Override
	public DriverTask expandVolume(StorageVolume volume, long newCapacity) {
		DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_EXPAND_STORAGE_VOLUMES);

		// For this volume
		try {
			_log.info("3PARDriver:expandVolume for storage system native id {}, volume name {} - start",
					volume.getStorageSystemId(), volume.getDisplayName());
			
			if (newCapacity < volume.getProvisionedCapacity()) {
			    throw new HP3PARException("New capacity is less than original capcity");
			}

			// get Api client
			HP3PARApi hp3parApi = hp3parUtil.getHP3PARDeviceFromNativeId(volume.getStorageSystemId(),
					this.driverRegistry);

			// expand volume
			Long additionalSize = newCapacity - volume.getProvisionedCapacity();
			hp3parApi.expandVolume(volume.getDisplayName(), additionalSize / HP3PARConstants.MEGA_BYTE);

			volume.setRequestedCapacity(newCapacity);

			// actual size of the volume in array
			VolumeDetailsCommandResult volResult = hp3parApi.getVolumeDetails(volume.getDisplayName());
			volume.setProvisionedCapacity(volResult.getSizeMiB() * HP3PARConstants.MEGA_BYTE);
			volume.setAllocatedCapacity(volResult.getSizeMiB() * HP3PARConstants.MEGA_BYTE);

			task.setStatus(DriverTask.TaskStatus.READY);
			_log.info("3PARDriver:expandVolumes for storage system native id {}, volume name {} - end",
					volume.getStorageSystemId(), volume.getDisplayName());
		} catch (Exception e) {
			String msg = String.format(
					"3PARDriver: Unable to expand volume name %s with pool id %s for storage system native id %s; Error: %s.\n",
					volume.getDisplayName(), volume.getStoragePoolId(), volume.getStorageSystemId(), e);
			_log.error(msg);
			_log.error(CompleteError.getStackTrace(e));
			task.setMessage(msg);
			task.setStatus(DriverTask.TaskStatus.FAILED);
			e.printStackTrace();
		}

		return task;
	}

	/**
     * Remove the list of volumes from array
     */
	@Override
	public DriverTask deleteVolumes(List<StorageVolume> volumes) {
		DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_DELETE_STORAGE_VOLUMES);

		// For each requested volume (in one or more 3par system)
		for (StorageVolume volume : volumes) {
			try {
				_log.info("3PARDriver:deleteVolumes for storage system native id {}, volume name {} - start",
						volume.getStorageSystemId(), volume.getDisplayName());

				// get Api client
				HP3PARApi hp3parApi = hp3parUtil.getHP3PARDeviceFromNativeId(volume.getStorageSystemId(),
						driverRegistry);

				// Remove from Consistency Group
				String volumeCGName = volume.getConsistencyGroup();
				if (volumeCGName != null && !volumeCGName.isEmpty()) {
					_log.info("3PARDriver:deleteVolumes Removing volume {} from consistency group {} ",
							volume.getDisplayName(), volumeCGName);
					int removeMember = 2;
					hp3parApi.updateVVset(volumeCGName, volume.getNativeId(), removeMember);
				}

				// Delete volume
				hp3parApi.deleteVolume(volume.getNativeId());

				task.setStatus(DriverTask.TaskStatus.READY);
				_log.info("3PARDriver:deleteVolumes for storage system native id {}, volume name {} - end",
						volume.getStorageSystemId(), volume.getDisplayName());
			} catch (Exception e) {
				String msg = String.format(
						"3PARDriver: Unable to delete volume name %s with pool id %s for storage system native id %s; Error: %s.\n",
						volume.getDisplayName(), volume.getStoragePoolId(), volume.getStorageSystemId(), e);
				_log.error(msg);
				_log.error(CompleteError.getStackTrace(e));
				task.setMessage(msg);
				task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
				e.printStackTrace();
			}
		} // end for each volume

		return task;
	}

	@Override
	public List<VolumeSnapshot> getVolumeSnapshots(StorageVolume volume) {
		return ingestHelper.getVolumeSnapshots(volume, this.driverRegistry);
	}

	/**
	 * Identifying clones of the given parent base volume. NOTE: Intermediate
	 * physical copies of 3PAR generated from other snapshots/clone are shown as
	 * clone of base volume itself Need to check this behavior ?
	 */
	@Override
	public List<VolumeClone> getVolumeClones(StorageVolume volume) {
		_log.info("3PARDriver: getVolumeClones Running ");
		return ingestHelper.getVolumeClones(volume, driverRegistry);
	}

	@Override
	public List<VolumeMirror> getVolumeMirrors(StorageVolume volume) {
		_log.error("3PARDriver: getVolumeMirrors not supported ");
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * 
	 * Virtual Copy is HP3PAR term for Snapshot.
	 * 
	 */
	@Override
	public DriverTask createVolumeSnapshot(List<VolumeSnapshot> snapshots, StorageCapabilities capabilities) {

		DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_CREATE_SNAPSHOT_VOLUMES);

		return  snapshotHelper.createVolumeSnapshot(snapshots, capabilities, task, this.driverRegistry);
	}

	/**
	 * Promote Virtual Copy is HP3PAR term for restore Snapshot. First offline
	 * restore then online restore will be tried.
	 */
	@Override
	public DriverTask restoreSnapshot(List<VolumeSnapshot> snapshots) {

		DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_RESTORE_SNAPSHOT_VOLUMES);

		return snapshotHelper.restoreSnapshot(snapshots, task, this.driverRegistry);
	}

	@Override
	public DriverTask deleteVolumeSnapshot(List<VolumeSnapshot> snapshots) {

		DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_DELETE_SNAPSHOT_VOLUMES);

		return snapshotHelper.deleteVolumeSnapshot(snapshots, task, this.driverRegistry);
	}

	/**
	 * Physical Copy is a 3PAR term for Volume clone
	 * 
	 */
	@Override
	public DriverTask createVolumeClone(List<VolumeClone> clones, StorageCapabilities capabilities) {

		DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_CREATE_CLONE_VOLUMES);
		return cloneHelper.createVolumeClone(clones, capabilities, task, this.driverRegistry);

	}

	/**
	 *  There is no REST API available for detach clone in HP3PAR
	 *	This is getting called while delete clone, hence setting this as
	 *	working by default.  
	 */
	@Override
	public DriverTask detachVolumeClone(List<VolumeClone> clones) {
		
		_log.info("3PARDriver: detachVolumeClone no action ");
		DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_DETACH_CLONE_VOLUMES);
		task.setStatus(DriverTask.TaskStatus.READY);
		return task;
	}

	/**
	 * restore clone or restore physical copy Intermediate snapshot will be used
	 * for restore, this got generated during clone creation 
	 * NOTE: intermediate snapshot cannot be exported hence offline restore will be used
	 */
	@Override
	public DriverTask restoreFromClone(List<VolumeClone> clones) {

		DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_RESTORE_CLONE_VOLUMES);

		return cloneHelper.restoreFromClone(clones, this.driverRegistry, task);
		
	}

	@Override
	public DriverTask deleteVolumeClone(List<VolumeClone> clones) {

		DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_DELETE_CLONE_VOLUMES);

		return cloneHelper.deleteVolumeClone(clones, task, this.driverRegistry);
		
	}

	@Override
	public DriverTask createVolumeMirror(List<VolumeMirror> mirrors, StorageCapabilities capabilities) {
		_log.error("3PARDriver: createVolumeMirror not supported ");
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DriverTask createConsistencyGroupMirror(VolumeConsistencyGroup consistencyGroup, List<VolumeMirror> mirrors,
			List<CapabilityInstance> capabilities) {
		_log.error("3PARDriver: createConsistencyGroupMirror not supported ");
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DriverTask deleteVolumeMirror(List<VolumeMirror> mirrors) {
		_log.error("3PARDriver: deleteVolumeMirror not supported ");
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DriverTask deleteConsistencyGroupMirror(List<VolumeMirror> mirrors) {
		_log.error("3PARDriver: deleteConsistencyGroupMirror not supported ");
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public DriverTask splitVolumeMirror(List<VolumeMirror> mirrors) {
		_log.error("3PARDriver: splitVolumeMirror not supported ");
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public DriverTask resumeVolumeMirror(List<VolumeMirror> mirrors) {
		_log.error("3PARDriver: resumeVolumeMirror not supported ");
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DriverTask restoreVolumeMirror(List<VolumeMirror> mirrors) {
		_log.error("3PARDriver: restoreVolumeMirror not supported ");
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<String, HostExportInfo> getVolumeExportInfoForHosts(StorageVolume volume) {
		_log.info("3PARDriver: getVolumeExportInfoForHosts Running ");
		return ingestHelper.getBlockObjectExportInfoForHosts(volume.getStorageSystemId(), volume.getWwn(),
				volume.getNativeId(), volume, driverRegistry);
	}

   /**
     * Expand the size of requested volume
     */
	@Override
	public Map<String, HostExportInfo> getSnapshotExportInfoForHosts(VolumeSnapshot snapshot) {
		_log.info("3PARDriver: getSnapshotExportInfoForHosts Running ");
		return ingestHelper.getBlockObjectExportInfoForHosts(snapshot.getStorageSystemId(), snapshot.getWwn(),
				snapshot.getNativeId(), snapshot, driverRegistry);
	}

	/**
	 * Remove the list of volumes from array
	 */
	@Override
	public Map<String, HostExportInfo> getCloneExportInfoForHosts(VolumeClone clone) {
		_log.info("3PARDriver: getCloneExportInfoForHosts Running ");
		return ingestHelper.getBlockObjectExportInfoForHosts(clone.getStorageSystemId(), clone.getWwn(),
				clone.getNativeId(), clone, driverRegistry);
	}

	@Override
	public Map<String, HostExportInfo> getMirrorExportInfoForHosts(VolumeMirror mirror) {
		_log.error("3PARDriver: getMirrorExportInfoForHosts not supported ");
		// TODO Auto-generated method stub
		return null;
	}
	
	private String search3parHostName(List<Initiator> initiators, HostCommandResult hostRes) {
	    String hp3parHost = null;

	    // for each host in 3par
	    for(HostMember hostMemb:hostRes.getMembers()) {
	        // for each host initiator sent
	        for (Initiator init : initiators) {

	            // Is initiator FC
	            if (init.getProtocol().toString().compareToIgnoreCase(Protocols.FC.toString()) == 0 ) {
	                // verify in all FC ports with host
	                for(FcPath fcPath: hostMemb.getFCPaths()) {                         
	                    if (SanUtils.formatWWN(fcPath.getWwn()).compareToIgnoreCase(init.getPort()) == 0) {
	                        hp3parHost = hostMemb.getName();
	                        _log.info("3PARDriver: get3parHostname initiator {} host {}", init.getPort(),
	                                hp3parHost);
	                        return hp3parHost;
	                    }
	                }
	            } else if (init.getProtocol().toString().compareToIgnoreCase(Protocols.iSCSI.toString()) == 0 ){
	                // verify in all iSCSI ports with host
	                for (ISCSIPath scsiPath:hostMemb.getiSCSIPaths()) {
	                    if (scsiPath.getName().compareToIgnoreCase(init.getPort()) == 0) {
	                        hp3parHost = hostMemb.getName();
	                        _log.info("3PARDriver: get3parHostname initiator {} host {}", init.getPort(),
	                                hp3parHost);
	                        return hp3parHost;
	                    }
	                }

	            } // if FC or iSCSI
	        } // each initiator
	    } // each host

	    return null;
	}

	private String get3parHostname(List<Initiator> initiators, String storageId) throws Exception {
		// Since query works this implementation can be changed
		String hp3parHost = null;
		_log.info("3PARDriver: get3parHostname enter");

		try {
			HP3PARApi hp3parApi = hp3parUtil.getHP3PARDeviceFromNativeId(storageId, driverRegistry);
			HostCommandResult hostRes = hp3parApi.getAllHostDetails();

			hp3parHost = search3parHostName(initiators, hostRes);
			
			_log.info("3PARDriver: get3parHostname leave");
			return hp3parHost;
		} catch (Exception e) {
			_log.error("3PARDriver:get3parHostname could not get 3par registered host name");
			_log.error(CompleteError.getStackTrace(e));
			return null;
		}
	}

	private Integer getPersona(HostOsType hostType) {
	    Integer persona = 0;
	    
        // Supporting from lower OS versions; 
        switch (hostType) {
            case Windows:
            case Linux:
            case SUNVCS:
                persona = 1;
                break;

            case HPUX:
                persona = 7;
                break;

            case Esx:
                persona = 11;
                break;

            case AIX:
            case AIXVIO:
                persona = 8;
                break;

                // persona 3 is by experimentation, doc is not up-to-date
            case No_OS:
            case Other:
            default:
                persona = 3;
                break;
        }
        return persona;
	}
	
    /*********USE CASES**********
      
      EXCLUSIVE EXPORT: Will include port number of host
      
      1 Export volume to existing host  
      2 Export volume to non-existing host  
      3 Add initiator to existing host 
      4 Remove initiator from host 
      5 Unexport volume 
      
      A 1-5 can be done with single/multiple volumes,initiators as applicable
      B Does not depend on host name
      C Adding an initiator in matched-set will not do anything further. 
        All volumes have to be exported to new initiator explicitly. 
        In host-sees 3PAR will automatically export the volumes to newly added initiator.
      -------------------------------------------
      SHARED EXPORT: Will not include port number, exported to all ports, the cluster can see
      
      1 Export volume to existing cluster
      2 Export volume to non-existing cluster 
      3 Add initiator to existing host in cluster 
      4 Remove initiator from host in cluster
      5 Unexport volume from cluster
      6 Export a private volume to a host in a cluster 
      7 Unexport a private volume from a host in a cluster
      8 Add a host to cluster 
      9 Remove a host from a cluster
      10 Add a host having private export 
      11 Remove a host having private export
      12 Move a host from one cluster to another
      
      A 1-12 can be done with single/multiple volumes,initiators,hosts as applicable
      B Cluster name in ViPR and 3PAR has to be identical with case
      C Adding a new host to host-set will automatically export all volumes to the new host(initial export must have been host-set)
     */

    /*
     * All volumes in the list will be exported to all initiators using recommended ports. If a volume can not be exported to 'n' 
     * initiators the same will be tried with available ports  
     */
	
	private String doHostProcessing(List<Initiator> initiators, List<StorageVolume> volumes) {
	    String host = null;

	    for (StorageVolume vol : volumes) {
	        // If required host/cluster should get created in all arrays to which volume belongs
	        String hostArray = null;
	        String clustArray = null;

	        try {
	            // all initiators belong to same host
	            if (initiators.get(0).getInitiatorType().equals(Type.Host)) {
	                // Exclusive-Host export
	                // Some code is repeated with cluster for simplicity
	                hostArray = get3parHostname(initiators, vol.getStorageSystemId());
	                if (hostArray == null) {
	                    // create a new host or add initiator to existing host
	                    HP3PARApi hp3parApi = hp3parUtil.getHP3PARDeviceFromNativeId(vol.getStorageSystemId(),
	                            driverRegistry);

	                    ArrayList<String> portIds = new ArrayList<>();
	                    for (Initiator init : initiators) {
	                        portIds.add(init.getPort());
	                    }

	                    Integer persona = getPersona(initiators.get(0).getHostOsType());
	                    hp3parApi.createHost(initiators.get(0).getHostName(), portIds, persona);
	                    host = initiators.get(0).getHostName();
	                } else {
	                    host = hostArray;
	                }
	                // Host available

	            } else if (initiators.get(0).getInitiatorType().equals(Type.Cluster)) {
	                // Shared-Cluster export
	                clustArray = initiators.get(0).getClusterName();
	                HP3PARApi hp3parApi = hp3parUtil.getHP3PARDeviceFromNativeId(vol.getStorageSystemId(),
	                        driverRegistry);

	                // Check if host exists, otherwise create
	                hostArray = get3parHostname(initiators, vol.getStorageSystemId());
	                if (hostArray == null) {
	                    // create a new host or add initiator to existing host
	                    ArrayList<String> portIds = new ArrayList<>();
	                    for (Initiator init : initiators) {
	                        portIds.add(init.getPort());
	                    }

	                    Integer persona = getPersona(initiators.get(0).getHostOsType());
	                    hp3parApi.createHost(initiators.get(0).getHostName(), portIds, persona);
	                    hostArray = initiators.get(0).getHostName();
	                }

	                // only one thread across all nodes should create cluster; 
	                // TBD: lock acquisition is having issues, synch is used
	                //String lockName = vol.getStorageSystemId() + vol.getNativeId() + hostArray;
	                //if (this.lockManager.acquireLock(lockName, 10, TimeUnit.MINUTES)) {
	                synchronized (this) {
	                    // Check if cluster exists, otherwise create
	                    HostSetDetailsCommandResult hostsetRes = hp3parApi.getHostSetDetails(clustArray);
	                    if (hostsetRes == null) {
	                        hp3parApi.createHostSet(clustArray, initiators.get(0).getHostName());
	                    } else {
	                        // if this host is not part of the cluster add it
	                        boolean present = false;
	                        for (String setMember:hostsetRes.getSetmembers()) {
	                            if (hostArray.compareTo(setMember) == 0) {
	                                present = true;
	                                break;
	                            }
	                        }

	                        if (!present) {
	                            // update cluster with this host
	                            hp3parApi.updateHostSet(clustArray, hostArray);
	                        }
	                    }

	                    // Cluster available
	                    host = "set:" + clustArray;
	                    //this.lockManager.releaseLock(lockName);
	                }

	            } else {
	                _log.error("3PARDriver:exportVolumesToInitiators error: Host/Cluster type not supported");
	                throw new HP3PARException(
	                        "3PARDriver:exportVolumesToInitiators error: Host/Cluster type not supported");
	            }
	        } catch (Exception e) {
	            String msg = String.format("3PARDriver: Unable to export, error: %s", e);
	            _log.error(msg);
	            _log.error(CompleteError.getStackTrace(e));
	            return null;
	        }
	    } // for each volume

	    return host;
	}
	
	@Override
	public DriverTask exportVolumesToInitiators(List<Initiator> initiators, List<StorageVolume> volumes,
			Map<String, String> volumeToHLUMap, List<StoragePort> recommendedPorts, List<StoragePort> availablePorts,
			StorageCapabilities capabilities, MutableBoolean usedRecommendedPorts, List<StoragePort> selectedPorts) {
	    DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_EXPORT_STORAGE_VOLUMES);
	    _log.info("3PARDriver:exportVolumesToInitiators enter");

	    String host = null;
	    host = doHostProcessing(initiators, volumes);
	    if (host == null ) {
	        task.setMessage("exportVolumesToInitiators error: Processing hosts, Unable to export");
	        task.setStatus(DriverTask.TaskStatus.FAILED);
	        return task;
	    }

	    /*
	     Export will be done keeping volumes as the starting point
	     */
	    Integer totalExport = recommendedPorts.size();
	    for (StorageVolume vol : volumes) {
	        Integer currExport = 0;
	        Integer hlu = Integer.parseInt(volumeToHLUMap.get(vol.getNativeId()));

	        try {
	            // volume could belong to different storage system; get specific api client;
	            HP3PARApi hp3parApi = hp3parUtil.getHP3PARDeviceFromNativeId(vol.getStorageSystemId(), driverRegistry);

	            /*
	             export for INDIVIDUAL HOST=exclusive 
	             Some code is repeated with cluster for simplicity
	             */
	            if (!host.startsWith("set:")) {
	                // try with recommended ports
	                for (StoragePort port : recommendedPorts) {
	                    // verify volume and port belong to same storage
	                    if (!vol.getStorageSystemId().equalsIgnoreCase(port.getStorageSystemId())) {
	                        continue;
	                    }

	                    String message = String.format(
	                            "3PARDriver:exportVolumesToInitiators using recommendedPorts for "
	                                    + "storage system %s, volume %s host %s hlu %s port %s",
	                                    port.getStorageSystemId(), vol.getNativeId(), host, hlu.toString(), port.getNativeId());
	                    _log.info(message);

	                    VlunResult vlunRes = hp3parApi.createVlun(vol.getNativeId(), hlu, host, port.getNativeId());
	                    if (vlunRes != null && vlunRes.getStatus()) {
	                        currExport++;
	                        usedRecommendedPorts.setValue(true);
	                        // update hlu obtained as lun from 3apr & add the selected port if required
	                        volumeToHLUMap.put(vol.getNativeId(), vlunRes.getAssignedLun());
	                        if (!selectedPorts.contains(port)) {
	                            selectedPorts.add(port);
	                        }
	                    } else {
	                        task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
	                        _log.warn("3PARDriver: Could not export " + message);
	                    }
	                } // for recommended ports

	                // now try with available ports
	                for (StoragePort port : availablePorts) {
	                    if (currExport == totalExport) {
	                        task.setStatus(DriverTask.TaskStatus.READY);
	                        break;
	                    }
	                    // verify volume and port belong to same storage
	                    if (!vol.getStorageSystemId().equalsIgnoreCase(port.getStorageSystemId())) {
	                        continue;
	                    }

	                    String message = String.format(
	                            "3PARDriver:exportVolumesToInitiators using availablePorts for "
	                                    + "storage system %s, volume %s host %s hlu %s port %s",
	                                    port.getStorageSystemId(), vol.getNativeId(), host, hlu.toString(), port.getNativeId());
	                    _log.info(message);

	                    VlunResult vlunRes = hp3parApi.createVlun(vol.getNativeId(), hlu, host, port.getNativeId());
	                    if (vlunRes != null && vlunRes.getStatus()) {
	                        currExport++;
	                        usedRecommendedPorts.setValue(false);
	                        // update hlu obtained as lun from 3apr & add the selected port if required
	                        volumeToHLUMap.put(vol.getNativeId(), vlunRes.getAssignedLun());
	                        if (!selectedPorts.contains(port)) {
	                            selectedPorts.add(port);
	                        }
	                    } else {
	                        task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
	                        _log.warn("3PARDriver: Could not export " + message);
	                    }
	                } // for available ports
	            } else {
	                /*
	                  export for CLUSTER=shared 
	                  Some code is repeated with cluster for simplicity
	                  
	                  Cluster export will be done as host-set in 3APR for entire cluster in one go.
	                  Hence requests coming for rest of the individual host exports should gracefully exit
	                 */

	                //TBD: lock to be used once issue is resolved
	                synchronized(this) {
	                    /*
	                      If this is the first request key gets created with export operation. 
	                      other requests will gracefully exit. key will be removed in unexport.
	                     */

	                    String message = String.format(
	                            "3PARDriver:exportVolumesToInitiators "
	                                    + "storage system %s, volume %s Cluster %s hlu %s ",
	                                    vol.getStorageSystemId(), vol.getNativeId(), host, hlu.toString());
	                    _log.info(message);

	                    String exportPath = vol.getStorageSystemId() + vol.getNativeId() + host;
	                    Map<String, List<String>> attributes = new HashMap<>();
	                    List<String> expValue = new ArrayList<>();
	                    List<String> lunValue = new ArrayList<>();
	                    boolean doExport = true;

	                    attributes = this.driverRegistry.getDriverAttributesForKey(HP3PARConstants.DRIVER_NAME,
	                            exportPath);

	                    if (attributes != null) {
	                        expValue = attributes.get("EXPORT_PATH");
	                        if (expValue != null && expValue.get(0).compareTo(exportPath) == 0) {
	                            doExport = false;
	                            // Already exported, make hlu, port details; gracefully exit
	                            lunValue = attributes.get(vol.getNativeId());
	                            volumeToHLUMap.put(vol.getNativeId(), lunValue.get(0));

	                            String hstArray = get3parHostname(initiators, vol.getStorageSystemId());
	                            HostMember hostRes = hp3parApi.getHostDetails(hstArray);

	                            // get storage array ports for this host ports
	                            List<StoragePort> clusterStoragePorts = new ArrayList<>();
	                            getClusterStoragePorts(hostRes, availablePorts, vol.getStorageSystemId(),
	                                    clusterStoragePorts);

	                            for (StoragePort sp : clusterStoragePorts) {
	                                // assign all these ports as selected ports
	                                if (!selectedPorts.contains(sp)) {
	                                    selectedPorts.add(sp);
	                                }
	                            }

	                            // go thru all slectedports. 
	                            // if anyone is not part of the recommendedPorts set usedRecommendedPorts to false
	                            usedRecommendedPorts.setValue(true);

	                            for (StoragePort sp : selectedPorts) {
	                                if (!recommendedPorts.contains(sp)) {
	                                    usedRecommendedPorts.setValue(false);
	                                    break;
	                                }
	                            }

	                            task.setStatus(DriverTask.TaskStatus.READY);
	                            _log.info("3PARDriver: Already exported, exiting" + message);
	                        }
	                    }

	                    if (doExport) {
	                        /*
	                         for cluster use host set method, We cannot specify port; 
	                         determine the individual host ports used
	                         */
	                        VlunResult vlunRes = hp3parApi.createVlun(vol.getNativeId(), hlu, host, null);
	                        if (vlunRes != null && vlunRes.getStatus()) {

	                            // update hlu obtained as lun from 3apr & add the selected port if required
	                            volumeToHLUMap.put(vol.getNativeId(), vlunRes.getAssignedLun());

	                            String hstArray = get3parHostname(initiators, vol.getStorageSystemId());
	                            HostMember hostRes = hp3parApi.getHostDetails(hstArray);

	                            // get storage array ports for this host ports
	                            List<StoragePort> clusterStoragePorts = new ArrayList<>();
	                            getClusterStoragePorts(hostRes, availablePorts, vol.getStorageSystemId(),
	                                    clusterStoragePorts);

	                            for (StoragePort sp : clusterStoragePorts) {
	                                // assign all these ports as selected ports
	                                if (!selectedPorts.contains(sp)) {
	                                    selectedPorts.add(sp);
	                                }
	                            }

	                            usedRecommendedPorts.setValue(true);

	                            for (StoragePort sp : selectedPorts) {
	                                if (!recommendedPorts.contains(sp)) {
	                                    usedRecommendedPorts.setValue(false);
	                                    break;
	                                }
	                            }

	                            // Everything is successful, Set as exported in registry
	                            attributes = new HashMap<>();
	                            expValue = new ArrayList<>();
	                            lunValue = new ArrayList<>();

	                            expValue.add(exportPath);
	                            attributes.put("EXPORT_PATH", expValue);
	                            lunValue.add(vlunRes.getAssignedLun());
	                            attributes.put(vol.getNativeId(), lunValue);

	                            attributes.put(vol.getNativeId(), lunValue);
	                            this.driverRegistry.setDriverAttributesForKey(HP3PARConstants.DRIVER_NAME, exportPath,
	                                    attributes);

	                            task.setStatus(DriverTask.TaskStatus.READY);

	                        } else { // end createVlun
	                            task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
	                            _log.warn("3PARDriver: Could not export " + message);
	                        }
	                    } // doExport

	                }
	                
	            } // end cluster export

	        } catch (Exception e) {
	            String msg = String.format("3PARDriver: Unable to export few volumes, error: %s", e);
	            _log.error(CompleteError.getStackTrace(e));
	            _log.error(msg);
	            task.setMessage(msg);
	            task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
	            e.printStackTrace();
	        }
	    } // for each volume

	    _log.info("3PARDriver:exportVolumesToInitiators leave");
	    return task;
	}

	private void getClusterStoragePorts(HostMember hostRes, List<StoragePort> arrayPorts, String volStorageSystemId,
			List<StoragePort> clusterPorts) {

		for (StoragePort sp : arrayPorts) {
			if (volStorageSystemId.compareToIgnoreCase(sp.getStorageSystemId()) != 0) {
				continue;
			}

			String[] pos = sp.getNativeId().split(":");

			for (FcPath fc:hostRes.getFCPaths()) {

				if (fc.getPortPos() != null) {
					if ((fc.getPortPos().getNode().toString().compareToIgnoreCase(pos[0]) == 0)
							&& (fc.getPortPos().getSlot().toString().compareToIgnoreCase(pos[1]) == 0)
							&& (fc.getPortPos().getCardPort().toString().compareToIgnoreCase(pos[2]) == 0)) {

						// host connected array port
						clusterPorts.add(sp);
					}
				} // porPos != null
			} // for fc
		}
	}

    /* 
     * Unexport the volumes from array
     */
	@Override
	public DriverTask unexportVolumesFromInitiators(List<Initiator> initiators, List<StorageVolume> volumes) {
	    DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_UNEXPORT_STORAGE_VOLUMES);
	    _log.info("3PARDriver:unexportVolumesFromInitiators enter");

	    String host = null;
	    Boolean fullSuccess = true;

	    if (initiators.isEmpty() || volumes.isEmpty()) {
	        String msg = "3PARDriver:unexportVolumesFromInitiators error blank initiator and/or volumes";
	        _log.error(msg);
	        task.setMessage(msg);
	        task.setStatus(DriverTask.TaskStatus.FAILED);
	        return task;
	    }

	    // unexport each volume
	    for (StorageVolume volume : volumes) {
	        try {
	            // get Api client for volume specific array
	            HP3PARApi hp3parApi = hp3parUtil.getHP3PARDeviceFromNativeId(volume.getStorageSystemId(),
	                    driverRegistry);
	            // TBD: Efficiency; use query method
	            VirtualLunsList vlunRes = hp3parApi.getAllVlunDetails();

	            for (Initiator init : initiators) {

	                // TBD: Efficiency; Initiator & host name to be stored in hash-map
	                ArrayList<Initiator> initList = new ArrayList<>();
	                initList.add(init);
	                host = get3parHostname(initList, volume.getStorageSystemId());
	                if (host == null) {
	                    fullSuccess = false;
	                    String message = String.format(
                                "3PARDriver:unexportVolumesFromInitiators for " + "storage system %s, volume %s initiator %s",
                                volume.getStorageSystemId(), volume.getNativeId(), init.getPort());
	                    _log.warn(message);
	                    task.setMessage(message);
	                    task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
	                    continue;
	                }

	                if (init.getInitiatorType().equals(Type.Host)) {
	                    // get vlun and port details on this export
	                    Integer lun = -1;
	                    Position pos = null;
	                    String hostPortId = init.getPort();//host port
	                    hostPortId = hostPortId.replace(":", "");

	                    for (VirtualLun vLun:vlunRes.getMembers()) {

	                        if (volume.getNativeId().compareTo(vLun.getVolumeName()) != 0 || (!vLun.isActive())
	                                || hostPortId.compareToIgnoreCase(vLun.getRemoteName()) != 0) {
	                            continue;
	                        }

	                        lun = vLun.getLun();
	                        pos = vLun.getPortPos();

	                        String message = String.format(
	                                "3PARDriver:unexportVolumesFromInitiators for "
	                                        + "storage system %s, volume %s host %s hlu %s port %s",
	                                        volume.getStorageSystemId(), volume.getNativeId(), host, lun.toString(),
	                                        pos.toString());
	                        _log.info(message);

	                        String posStr = String.format("%s:%s:%s", pos.getNode(), pos.getSlot(), pos.getCardPort());
	                        hp3parApi.deleteVlun(volume.getNativeId(), lun.toString(), host, posStr);
	                    }
	                } else if (init.getInitiatorType().equals(Type.Cluster)) {

	                    // cluster unexport
	                    String clusterName = "set:" + initiators.get(0).getClusterName();
	                    String exportPath = volume.getStorageSystemId() + volume.getNativeId() + clusterName;
	                    Map<String, List<String>> attributes = new HashMap<>();
	                    List<String> expValue = new ArrayList<>();
	                    List<String> lunValue = new ArrayList<>();
	                    boolean regPresent = false;

	                    String message = String.format(
	                            "3PARDriver:unexportVolumesFromInitiators for " + "storage system %s, volume %s Cluster %s",
	                            volume.getStorageSystemId(), volume.getNativeId(), clusterName);

	                    attributes = this.driverRegistry.getDriverAttributesForKey(HP3PARConstants.DRIVER_NAME, exportPath);

	                    if (attributes != null) {
	                        expValue = attributes.get("EXPORT_PATH");
	                        if (expValue != null && expValue.get(0).compareTo(exportPath) == 0) {
	                            lunValue = attributes.get(volume.getNativeId());
	                            regPresent = true;

	                            _log.info(message);
	                            /*
	                             * below operations are assumed to autonomic
	                             */
	                            hp3parApi.deleteVlun(volume.getNativeId(), lunValue.get(0), clusterName, null);
	                            this.driverRegistry.clearDriverAttributesForKey(HP3PARConstants.DRIVER_NAME, exportPath);
	                        }
	                    }

	                    if (!regPresent) {
	                        // gracefully exit, nothing to be done
	                        _log.info("3PARDriver: Already unexported, exiting gracefully" + message);
	                    }
	                } // if cluster
	            } // for each initiator

	        } catch (Exception e) {
	            String msg = String.format("3PARDriver: Unable to unexport few volumes, error: %s", e);
	            _log.error(msg);
	            _log.error(CompleteError.getStackTrace(e));
	            task.setMessage(msg);
	            task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
	            e.printStackTrace();
	            fullSuccess = false;
	        }
	    } // for each volume

	    if (fullSuccess) {
	        task.setStatus(DriverTask.TaskStatus.READY);
	    }

	    _log.info("3PARDriver:unexportVolumesFromInitiatorss leave");
	    return task;
	}

	/**
	 * createConsistencyGroup will get called once and subsequent volume addition to the CG will be handled 
	 * in create volume
	 * 
	 */
	@Override
	public DriverTask createConsistencyGroup(VolumeConsistencyGroup consistencyGroup) {
		DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_CREATE_CONSISTENCY_GROUP);

		return cgHelper.createConsistencyGroup(consistencyGroup, task, this.driverRegistry);

	}

	/**
	 * Delete VV Set or consistency group
	 * 
	 */
	@Override
	public DriverTask deleteConsistencyGroup(VolumeConsistencyGroup consistencyGroup) {

		DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_DELETE_CONSISTENCY_GROUP);

		return cgHelper.deleteConsistencyGroup(consistencyGroup, task, driverRegistry);

	}

	@Override
	public DriverTask createConsistencyGroupSnapshot(VolumeConsistencyGroup consistencyGroup,
			List<VolumeSnapshot> snapshots, List<CapabilityInstance> capabilities) {

		DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_SNAPSHOT_CONSISTENCY_GROUP);

		return cgHelper.createConsistencyGroupSnapshot(consistencyGroup, snapshots, capabilities, task, driverRegistry);

	}

	@Override
	public DriverTask deleteConsistencyGroupSnapshot(List<VolumeSnapshot> snapshots) {

		DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_DELETE_SNAPSHOT_CONSISTENCY_GROUP);
		
		return cgHelper.deleteConsistencyGroupSnapshot(snapshots, task, driverRegistry);
		
	}


	@Override
	public DriverTask createConsistencyGroupClone(VolumeConsistencyGroup consistencyGroup, List<VolumeClone> clones,
			List<CapabilityInstance> capabilities) {

		_log.error("3PARDriver: createConsistencyGroupClone not supported ");
		
		return null;
		
		/*
		 * Need to handle some more cases, hence commenting 
		DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_CLONE_CONSISTENCY_GROUP);

		return cgHelper.createConsistencyGroupClone(consistencyGroup, clones, capabilities, task, driverRegistry);
		*/

	}

	/**
	 * Create driver task for task type
	 *
	 * @param taskType
	 */
	private DriverTask createDriverTask(String taskType) {
		String taskID = String.format("%s+%s+%s", HP3PARConstants.DRIVER_NAME, taskType, UUID.randomUUID());
		DriverTask task = new HP3PARDriverTask(taskID);
		return task;
	}

	private void setConnInfoToRegistry(String systemNativeId, String ipAddress, int port, String username,
			String password) {
		_log.info("3PARDriver:Saving connection info in registry enter");
		Map<String, List<String>> attributes = new HashMap<>();
		List<String> listIP = new ArrayList<>();
		List<String> listPort = new ArrayList<>();
		List<String> listUserName = new ArrayList<>();
		List<String> listPwd = new ArrayList<>();

		listIP.add(ipAddress);
		attributes.put(HP3PARConstants.IP_ADDRESS, listIP);
		listPort.add(Integer.toString(port));
		attributes.put(HP3PARConstants.PORT_NUMBER, listPort);
		listUserName.add(username);
		attributes.put(HP3PARConstants.USER_NAME, listUserName);
		listPwd.add(password);
		attributes.put(HP3PARConstants.PASSWORD, listPwd);
		this.driverRegistry.setDriverAttributesForKey(HP3PARConstants.DRIVER_NAME, systemNativeId, attributes);
		_log.info("3PARDriver:Saving connection info in registry leave");
	}

	@Override
	public DriverTask discoverStorageProvider(StorageProvider storageProvider, List<StorageSystem> storageSystems) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean validateStorageProviderConnection(StorageProvider storageProvider) {
		// TODO Auto-generated method stub
		return false;
	}

	public HP3PARIngestHelper getIngestHelper() {
		return ingestHelper;
	}

	public void setIngestHelper(HP3PARIngestHelper ingestHelper) {
		this.ingestHelper = ingestHelper;
	}

	public HP3PARApiFactory getHp3parApiFactory() {
		return hp3parApiFactory;
	}

	public void setHp3parApiFactory(HP3PARApiFactory hp3parApiFactory) {
		this.hp3parApiFactory = hp3parApiFactory;
	}

	public HP3PARSnapshotHelper getSnapshotHelper() {
		return snapshotHelper;
	}

	public void setSnapshotHelper(HP3PARSnapshotHelper snapshotHelper) {
		this.snapshotHelper = snapshotHelper;
	}

	public HP3PARCloneHelper getCloneHelper() {
		return cloneHelper;
	}

	public void setCloneHelper(HP3PARCloneHelper cloneHelper) {
		this.cloneHelper = cloneHelper;
	}

	public HP3PARCGHelper getCgHelper() {
		return cgHelper;
	}

	public void setCgHelper(HP3PARCGHelper cgHelper) {
		this.cgHelper = cgHelper;
	}

	public HP3PARUtil getHp3parUtil() {
		return hp3parUtil;
	}

	public void setHp3parUtil(HP3PARUtil hp3parUtil) {
		this.hp3parUtil = hp3parUtil;
	}

    @Override
    public DriverTask stopManagement(StorageSystem storageSystem) {
    	_log.info("3PARDriver: stopManagement Running");
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask addVolumesToConsistencyGroup(List<StorageVolume> volumes, StorageCapabilities capabilities) {
    	_log.info("3PARDriver: addVolumesToConsistencyGroup Running");
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask removeVolumesFromConsistencyGroup(List<StorageVolume> volumes, StorageCapabilities capabilities) {
    	_log.info("3PARDriver: removeVolumesFromConsistencyGroup Running");
        // TODO Auto-generated method stub
        return null;
    }
}
