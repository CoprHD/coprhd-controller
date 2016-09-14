
/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang.mutable.MutableBoolean;
import org.apache.commons.lang.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.emc.storageos.hp3par.command.CPGCommandResult;
import com.emc.storageos.hp3par.command.CPGMember;
import com.emc.storageos.hp3par.command.ConsistencyGroupResult;
import com.emc.storageos.hp3par.command.SystemCommandResult;
import com.emc.storageos.hp3par.connection.HP3PARApiFactory;
import com.emc.storageos.hp3par.utils.CompleteError;
import com.emc.storageos.hp3par.utils.HP3PARConstants;
import com.emc.storageos.hp3par.utils.HP3PARUtil;
import com.emc.storageos.storagedriver.AbstractStorageDriver;
import com.emc.storageos.storagedriver.BlockStorageDriver;
import com.emc.storageos.storagedriver.DefaultStorageDriver;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.HostExportInfo;
import com.emc.storageos.storagedriver.RegistrationData;
import com.emc.storageos.storagedriver.model.Initiator;
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
import com.emc.storageos.storagedriver.storagecapabilities.DeduplicationCapabilityDefinition;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;

/**
 * 
 * Implements functions to discover the HP 3PAR storage and provide provisioning
 * You can refer super class for method details
 *
 */
public class HP3PARStorageDriver extends DefaultStorageDriver implements BlockStorageDriver {

    private static final String HP3PAR_CONF_FILE = "hp3par-conf.xml";
	private static final Logger _log = LoggerFactory.getLogger(HP3PARStorageDriver.class);
	private ApplicationContext parentApplicationContext;

	// Independent functionalities
	private HP3PARIngestHelper ingestHelper;
	private HP3PARUtil hp3parUtil;
	private HP3PARApiFactory hp3parApiFactory;
	private HP3PARSnapshotHelper snapshotHelper;
	private HP3PARCloneHelper cloneHelper;
	private HP3PARCGHelper cgHelper;
	private HP3PARProvisioningHelper provHelper;
	private HP3PARExpUnexpHelper expunexpHelper;
	
	public void init() {
	    ApplicationContext context = new ClassPathXmlApplicationContext(new String[] {HP3PAR_CONF_FILE}, parentApplicationContext);
        this.ingestHelper = (HP3PARIngestHelper) context.getBean("3parIngestionHelper");
        this.hp3parUtil = (HP3PARUtil) context.getBean("hp3parUtil");
        this.hp3parApiFactory = (HP3PARApiFactory) context.getBean("hp3parApiFactory");
        this.snapshotHelper = (HP3PARSnapshotHelper) context.getBean("3parSnapshotHelper");
        this.cloneHelper = (HP3PARCloneHelper) context.getBean("3parCloneHelper");
        this.cgHelper = (HP3PARCGHelper) context.getBean("3parCGHelper");
        this.provHelper = (HP3PARProvisioningHelper) context.getBean("3parProvHelper");
        this.expunexpHelper = (HP3PARExpUnexpHelper) context.getBean("3parExpUnexpHelper");
	}

	// Injecting HP3PAR parent application context 
    public void setApplicationContext(ApplicationContext parentApplicationContext) {
        this.parentApplicationContext = parentApplicationContext;
    }

	
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
			} else if (StoragePool.class.getSimpleName().equals(type.getSimpleName())) {
			    CPGMember cpgResult = null;
			    cpgResult = hp3parApi.getCPGDetails(objectId);
			    StoragePool sp = new StoragePool();

			    sp.setNativeId(cpgResult.getName()); 
			    sp.setTotalCapacity((cpgResult.getUsrUsage().getTotalMiB().longValue()
			            + cpgResult.getSAUsage().getTotalMiB().longValue()
			            + cpgResult.getSDUsage().getTotalMiB().longValue()) * HP3PARConstants.KILO_BYTE);
			    sp.setSubscribedCapacity((cpgResult.getUsrUsage().getUsedMiB().longValue()
			            + cpgResult.getSAUsage().getUsedMiB().longValue()
			            + cpgResult.getSDUsage().getUsedMiB().longValue()) * HP3PARConstants.KILO_BYTE);
			    sp.setFreeCapacity(sp.getTotalCapacity() - sp.getSubscribedCapacity());
			    _log.info("3PARDriver: StoragePool getStorageObject leaving ");
			    return (T) sp;
			}
		} catch (Exception e) {
			String msg = String.format("3PARDriver: Unable to get Stroage Object for id %s; Error: %s.\n", objectId,
					e.getMessage());
			_log.error(msg);
			e.printStackTrace();
			_log.error(CompleteError.getStackTrace(e));
			return (T) null;
		}
		return null;
	}

	@Override
	public RegistrationData getRegistrationData() {
		_log.info("3PARDriver: getStorageObject enter");
        RegistrationData registrationData = new RegistrationData(HP3PARConstants.DRIVER_NAME, "hp3par", null);
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
            supportedReplications.add(StorageSystem.SupportedReplication.elementReplica);
            supportedReplications.add(StorageSystem.SupportedReplication.groupReplica);
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
		
		DeduplicationCapabilityDefinition dedupCapabilityDefinition = new DeduplicationCapabilityDefinition();

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

				if (currMember.getSDGrowth().getLDLayout().getDiskPatterns() == null) {
					_log.warn("3PARDriver: Neglecting storage pool {} as there is no disk associated with it", currMember.getName());
					continue;
				}
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
				pool.setNativeId(currMember.getName()); // SB SDK is not sending pool name in volume creation
				pool.setDeviceLabel(currMember.getName());
				pool.setDisplayName(currMember.getName());
				storageSystem.setAccessStatus(AccessStatus.READ_WRITE);
                List<CapabilityInstance> capabilities = new ArrayList<>(); // SDK requires initialization
                
                // setting appropriate capability for dedup supported pool
                if(currMember.isDedupCapable()) {
                    Boolean dedupEnabled = true;
                    Map<String, List<String>> props = new HashMap<>();
                    props.put(DeduplicationCapabilityDefinition.PROPERTY_NAME.ENABLED.name(), Arrays.asList(dedupEnabled.toString()));
                    CapabilityInstance capabilityInstance = new CapabilityInstance(dedupCapabilityDefinition.getId(), dedupCapabilityDefinition.getId(), props);
                    capabilities.add(capabilityInstance);
                }
                pool.setCapabilities(capabilities);
                

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
		DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_GET_STORAGE_VOLUMES);
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

		return provHelper.createVolumes(volumes, capabilities, task, this.driverRegistry);
	}

	/**
     * Expand the size of requested volume
     */
	@Override
	public DriverTask expandVolume(StorageVolume volume, long newCapacity) {
		DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_EXPAND_STORAGE_VOLUMES);

		return provHelper.expandVolume(volume, newCapacity, task, this.driverRegistry);
	}

	/**
     * Remove the list of volumes from array
     */
	@Override
	public DriverTask deleteVolume(StorageVolume volume) {
		DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_DELETE_STORAGE_VOLUMES);

		return provHelper.deleteVolumes(volume, task, this.driverRegistry);
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
	public DriverTask deleteVolumeSnapshot(VolumeSnapshot snapshots) {

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
	public DriverTask deleteVolumeClone(VolumeClone clones) {

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
	public DriverTask deleteVolumeMirror(VolumeMirror mirrors) {
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
		
	/* 
     * Export the volumes to array
     */
	@Override
	public DriverTask exportVolumesToInitiators(List<Initiator> initiators, List<StorageVolume> volumes,
			Map<String, String> volumeToHLUMap, List<StoragePort> recommendedPorts, List<StoragePort> availablePorts,
			StorageCapabilities capabilities, MutableBoolean usedRecommendedPorts, List<StoragePort> selectedPorts) {
	    DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_EXPORT_STORAGE_VOLUMES);

	    return expunexpHelper.exportVolumesToInitiators(initiators, volumes, volumeToHLUMap, recommendedPorts, 
	            availablePorts, capabilities, usedRecommendedPorts, selectedPorts,
	            task, this.driverRegistry, this.lockManager);
	}


    /* 
     * Unexport the volumes from array
     */
	@Override
	public DriverTask unexportVolumesFromInitiators(List<Initiator> initiators, List<StorageVolume> volumes) {
	    DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_UNEXPORT_STORAGE_VOLUMES);
	    
	    return expunexpHelper.unexportVolumesFromInitiators(initiators, volumes,
	            task, this.driverRegistry, this.lockManager);
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

    @Override
    public DriverTask stopManagement(StorageSystem storageSystem) {
        _log.info("3PARDriver: stopManagement Running");
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask addVolumesToConsistencyGroup(List<StorageVolume> volumes, StorageCapabilities capabilities) {
    	int addVolume = 1;
		DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_ADD_VOLUME_TO_CONSISTENCY_GROUP);
		return cgHelper.addOrRemoveConsistencyGroupVolume(volumes, task, driverRegistry,addVolume);

    }

    @Override
	public DriverTask removeVolumesFromConsistencyGroup(List<StorageVolume> volumes, StorageCapabilities capabilities) {
		DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_REMOVE_VOLUME_FROM_CONSISTENCY_GROUP);
		int removeVolume = 2;
		return cgHelper.addOrRemoveConsistencyGroupVolume(volumes, task, driverRegistry, removeVolume);

	}
    
}
