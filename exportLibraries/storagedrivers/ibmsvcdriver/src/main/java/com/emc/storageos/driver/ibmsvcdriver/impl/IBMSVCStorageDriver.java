/**
 * Copyright (c) 2016 EMC Corporation
 * 
 * All Rights Reserved
 * 
 */
package com.emc.storageos.driver.ibmsvcdriver.impl;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import com.emc.storageos.driver.ibmsvcdriver.exceptions.IBMSVCDriverException;
import com.emc.storageos.driver.ibmsvcdriver.helpers.IBMSVCDiscovery;
import com.emc.storageos.driver.ibmsvcdriver.helpers.IBMSVCProvisioning;
import com.emc.storageos.storagedriver.DefaultStorageDriver;
import com.emc.storageos.storagedriver.BlockStorageDriver;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.HostExportInfo;
import com.emc.storageos.storagedriver.RegistrationData;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.mutable.MutableBoolean;
import org.apache.commons.lang.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.ibmsvcdriver.api.*;
import com.emc.storageos.driver.ibmsvcdriver.connection.ConnectionInfo;
import com.emc.storageos.driver.ibmsvcdriver.connection.ConnectionManager;
import com.emc.storageos.driver.ibmsvcdriver.connection.SSHConnection;
import com.emc.storageos.driver.ibmsvcdriver.utils.IBMSVCConstants;
import com.emc.storageos.driver.ibmsvcdriver.utils.IBMSVCDriverUtils;
import com.emc.storageos.driver.ibmsvcdriver.utils.IBMSVCHelper;
import com.emc.storageos.storagedriver.impl.InMemoryRegistryImpl;
import com.emc.storageos.storagedriver.model.*;
import com.emc.storageos.storagedriver.model.StoragePool.Protocols;
import com.emc.storageos.storagedriver.model.StoragePool.RaidLevels;
import com.emc.storageos.storagedriver.model.StoragePool.SupportedDriveTypes;
import com.emc.storageos.storagedriver.model.StorageSystem.SupportedProvisioningType;
import com.emc.storageos.storagedriver.storagecapabilities.CapabilityInstance;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;



public class IBMSVCStorageDriver extends DefaultStorageDriver implements BlockStorageDriver {

	private static final Logger _log = LoggerFactory.getLogger(IBMSVCStorageDriver.class);

	/*
	 * Connection Manager for managing connection pool
	 */
	private ConnectionManager connectionManager = null;


	private boolean testFlag = false;

	private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);

    private IBMSVCDiscovery ibmsvcDiscoveryHelper = new IBMSVCDiscovery();
    private IBMSVCProvisioning ibmsvcProvisioningHelper = new IBMSVCProvisioning();

	public IBMSVCStorageDriver() {
		super();

		// Create the connectionManager instance
		if (connectionManager == null) {
			// Initializes the Connection Manager
			connectionManager = new ConnectionManager();
		}

		/**
		 * Setting the InMemory Registry for Unit Testing
		 */
		if (testFlag) {
			InMemoryRegistryImpl inMemRegistry = new InMemoryRegistryImpl();
			setDriverRegistry(inMemRegistry);
		}


	}

    @Override
    public synchronized void setDriverRegistry(com.emc.storageos.storagedriver.Registry driverRegistry) {
        super.setDriverRegistry(driverRegistry);

        // Make sure our helper class gets updated with the right info
        connectionManager.setDriverRegistry(driverRegistry);
    }

	@Override
	public DriverTask getTask(String taskId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public RegistrationData getRegistrationData() {
		// TODO Auto-generated method stub
		return null;
	}

    @Override
    public DriverTask discoverStorageSystem(StorageSystem storageSystem) {
        return ibmsvcDiscoveryHelper.discoverStorageSystem(storageSystem);
    }

	@Override
	public <T extends StorageObject> T getStorageObject(String storageSystemId, String objectId, Class<T> type) {
		/*
		 * if (StorageVolume.class.getSimpleName().equals(type.getSimpleName()))
		 * { } StorageVolume obj = new StorageVolume();
		 * obj.setAllocatedCapacity(200L); return (T) obj;
		 */
		return null;
	}

	@Override
	public DriverTask discoverStoragePools(StorageSystem storageSystem, List<StoragePool> storagePools) {
        return ibmsvcDiscoveryHelper.discoverStoragePools(storageSystem, storagePools);
	}

	@Override
	public DriverTask discoverStoragePorts(StorageSystem storageSystem, List<StoragePort> storagePorts) {
        return ibmsvcDiscoveryHelper.discoverStoragePorts(storageSystem, storagePorts);
	}

	@Override
	public DriverTask discoverStorageHostComponents(StorageSystem storageSystem,
			List<StorageHostComponent> storageHosts) {
        return ibmsvcDiscoveryHelper.discoverStorageHostComponents(storageSystem, storageHosts);
	}

	@Override
	public DriverTask getStorageVolumes(StorageSystem storageSystem, List<StorageVolume> storageVolumes,
			MutableInt token) {
		return ibmsvcDiscoveryHelper.getStorageVolumes(storageSystem, storageVolumes, token);
	}

	/**
	 * Create storage volumes with a given set of capabilities. Before
	 * completion of the request, set all required data for provisioned volumes
	 * in "volumes" parameter.
	 *
	 * @param volumes
	 *            Input/output argument for volumes.
	 * @param capabilities
	 *            Input argument for capabilities. Defines storage capabilities
	 *            of volumes to create.
	 * @return task
	 */
	@Override
	public DriverTask createVolumes(List<StorageVolume> volumes, StorageCapabilities capabilities) {
        return ibmsvcProvisioningHelper.createVolumes(volumes, capabilities);
	}

	/**
	 * Expand volume. Before completion of the request, set all required data
	 * for expanded volume in "volume" parameter.
	 *
	 * @param storageVolume
	 *            Volume to expand. Type: Input/Output argument.
	 * @param newCapacity
	 *            Requested capacity. Type: input argument.
	 * @return task
	 */
	@Override
	public DriverTask expandVolume(StorageVolume storageVolume, long newCapacity) {
		return ibmsvcProvisioningHelper.expandVolume(storageVolume, newCapacity);
	}

	/**
	 * Delete volumes.
	 * 
	 * @param storageVolume
	 *            Volumes to delete.
	 * @return task
	 */
	@Override
	public DriverTask deleteVolume(StorageVolume storageVolume) {
		return ibmsvcProvisioningHelper.deleteVolume(storageVolume);
	}

	/**
	 * Export volumes to initiators through a given set of ports. If ports are
	 * not provided, use port requirements from ExportPathsServiceOption storage
	 * capability
	 *
	 * @param initiators
	 *            Type: Input.
	 * @param volumes
	 *            Type: Input.
	 * @param volumeToHLUMap
	 *            map of volume nativeID to requested HLU. HLU value of -1 means
	 *            that HLU is not defined and will be assigned by array. Type:
	 *            Input/Output.
	 * @param recommendedPorts
	 *            list of storage ports recommended for the export. Optional.
	 *            Type: Input.
	 * @param availablePorts
	 *            list of ports available for the export. Type: Input.
	 * @param storageCapabilities
	 *            storage capabilities. Type: Input.
	 * @param usedRecommendedPorts
	 *            true if driver used recommended and only recommended ports for
	 *            the export, false otherwise. Type: Output.
	 * @param selectedPorts
	 *            ports selected for the export (if recommended ports have not
	 *            been used). Type: Output.
	 * @return task
	 */
	@Override
	public DriverTask exportVolumesToInitiators(List<Initiator> initiators, List<StorageVolume> volumes,
			Map<String, String> volumeToHLUMap, List<StoragePort> recommendedPorts, List<StoragePort> availablePorts,
			StorageCapabilities storageCapabilities, MutableBoolean usedRecommendedPorts,
                                                List<StoragePort> selectedPorts) {

        return ibmsvcProvisioningHelper.exportVolumesToInitiators(initiators, volumes, volumeToHLUMap,
                recommendedPorts, availablePorts, storageCapabilities, usedRecommendedPorts, selectedPorts);
	}


	/**
	 * Unexport volumes from initiators
	 *
	 * @param initiators
	 *            Type: Input.
	 * @param volumes
	 *            Type: Input.
	 * @return task
	 */
	@Override
	public DriverTask unexportVolumesFromInitiators(List<Initiator> initiators, List<StorageVolume> volumes) {
		return ibmsvcProvisioningHelper.unexportVolumesFromInitiators(initiators, volumes);

		DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_UNEXPORT_STORAGE_VOLUMES);

		for (StorageVolume storageVolume : volumes) {

			_log.info("unexportVolumesFromInitiators() for storage system {} - start",
					storageVolume.getStorageSystemId());

			try {
				SSHConnection connection = getClientBySystemId(storageVolume.getStorageSystemId());

				List<Initiator> ibmHostInitiatorList = getIBMSVCHostInitiatorList(connection,
						storageVolume.getStorageSystemId());

				Set<String> uniqueHostSet = new HashSet<>();

				for (Initiator initiator : initiators) {

					for (Initiator hostInitiator : ibmHostInitiatorList) {

						if (initiator.getPort().equals(hostInitiator.getPort())) {
							uniqueHostSet.add(hostInitiator.getHostName());
						}
					}
				}

				if (uniqueHostSet.size() > 0) {

					// create an iterator

					// check values
					for (Object anUniqueHostSet : uniqueHostSet) {

						String hostName = anUniqueHostSet.toString();

						_log.info("UnExporting the volume Id {} to the host {}", storageVolume.getNativeId(), hostName);

						IBMSVCUnExportVolumeResult result = IBMSVCCLI.unexportStorageVolumes(connection,
                                storageVolume.getNativeId(), storageVolume.getDeviceLabel(), hostName);

						if (result.isSuccess()) {

							String logString = String.format("UnExported the storage volume %s to the host %s",
							storageVolume.getDeviceLabel(), result.getHostName());

							// remove host if no volumes remain mapped
							IBMSVCQueryHostVolMapResult vol_result = IBMSVCCLI.queryHostVolMap(connection, hostName);
							if (vol_result.isSuccess() && vol_result.getVolCount() == 0) {
								IBMSVCDeleteHostResult host_result = IBMSVCCLI.deleteHost(connection, hostName);
								logString += " & Host removed from array";
							}

							logString += ".\n";
							_log.info(String.format(logString));
							task.setMessage(logString);
							task.setStatus(DriverTask.TaskStatus.READY);
						} else {
							_log.error(String.format("UnExport the storage volume %s to the host %s failed : %s\n",
									storageVolume.getDeviceLabel(), result.getHostName(), result.getErrorString()),
									result.isSuccess());
							task.setMessage(String.format("UnExport the storage volume to the host %s failed : %s.",
									storageVolume.getDeviceLabel(), result.getHostName()) + result.getErrorString());
							task.setStatus(DriverTask.TaskStatus.FAILED);
                        }
                    }

                } else {
                    _log.info("None of the initiator port hosts are not registered with the storage system {}.",
							storageVolume.getStorageSystemId());
					task.setMessage(String.format(
							"None of the initiator port hosts are not registered with the storage system %s.",
							storageVolume.getStorageSystemId()));
					task.setStatus(DriverTask.TaskStatus.FAILED);
				}

			} catch (Exception e) {
				_log.error("Unable to unexport the storage volume {} on the storage system {}\n",
						storageVolume.getDeviceLabel(), storageVolume.getStorageSystemId());
				task.setMessage(String.format("Unable to unexport the storage volume %s on the storage system %s",
						storageVolume.getDeviceLabel(), storageVolume.getStorageSystemId()) + e.getMessage());
				task.setStatus(DriverTask.TaskStatus.FAILED);
				e.printStackTrace();
			}

			_log.info("unexportVolumesFromInitiators() for storage system {} - end\n",
					storageVolume.getStorageSystemId());
		}
		return task;
	}

	@Override
	public DriverTask resumeVolumeMirror(List<VolumeMirror> mirrors) {
		// TODO Auto-generated method stub
		return null;
	}

	public DriverTask restoreVolumeMirror(StorageVolume volume, VolumeMirror mirror) {
		// TODO Auto-generated method stub
		return null;
	}


	/**
	 * Set task status by checking the number of successful sub-tasks.
	 *
	 * @param sizeOfTasks
	 * @param sizeOfSuccTask
	 * @param task
	 */
	@SuppressWarnings("unused")
	private void setTaskStatus(int sizeOfTasks, int sizeOfSuccTask, DriverTask task) {
		if (sizeOfSuccTask == 0) {
			task.setStatus(DriverTask.TaskStatus.FAILED);
		} else if (sizeOfSuccTask < sizeOfTasks) {
			task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
		} else {
			task.setStatus(DriverTask.TaskStatus.READY);
		}
	}

	/**
	 * Set up Driver Task for NonSupported operations
	 *
	 * @param taskType
	 * @return task
	 */
	@SuppressWarnings("unused")
	private DriverTask setUpNonSupportedTask(IBMSVCConstants.TaskType taskType) {
		DriverTask task = new IBMSVCDriverTask(IBMSVCHelper.getTaskId(taskType));
		task.setStatus(DriverTask.TaskStatus.ABORTED);
		task.setMessage("Operation not supported");
		return task;
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
	 * Compare domain name and system name
	 *
	 * @param domainName
	 * @param systemName
	 */
	public Boolean compare(String domainName, String systemName) {
		return domainName.equalsIgnoreCase(systemName);
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub
	}

	@Override
	public DriverTask discoverStorageProvider(StorageProvider storageProvider, List<StorageSystem> storageSystems) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<VolumeSnapshot> getVolumeSnapshots(StorageVolume volume) {
		// TODO Auto-generated method stub
		return ibmsvcDiscoveryHelper.getVolumeSnapshots(volume);
	}

	@Override
	public List<VolumeClone> getVolumeClones(StorageVolume volume) {
		// TODO Auto-generated method stub
		return ibmsvcDiscoveryHelper.getVolumeClones(volume);
	}

	@Override
	public List<VolumeMirror> getVolumeMirrors(StorageVolume volume) {
		// TODO Auto-generated method stub
		return ibmsvcDiscoveryHelper.getVolumeMirrors(volume);
	}

	@Override
	public DriverTask stopManagement(StorageSystem storageSystem) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DriverTask restoreSnapshot(List<VolumeSnapshot> snapshots) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DriverTask createConsistencyGroupMirror(VolumeConsistencyGroup consistencyGroup, List<VolumeMirror> mirrors,
			List<CapabilityInstance> capabilities) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DriverTask deleteConsistencyGroupMirror(List<VolumeMirror> mirrors) {
		// TODO Auto-generated method stub
		return null;
	}

	/*@Override
	public DriverTask addVolumesToConsistencyGroup(List<StorageVolume> volumes, StorageCapabilities capabilities) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DriverTask removeVolumesFromConsistencyGroup(List<StorageVolume> volumes, StorageCapabilities capabilities) {
		// TODO Auto-generated method stub
		return null;
	}*/


	@Override
	public DriverTask restoreVolumeMirror(List<VolumeMirror> mirrors) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<String, HostExportInfo> getVolumeExportInfoForHosts(StorageVolume volume) {
		// TODO Auto-generated method stub
		return ibmsvcDiscoveryHelper.getVolumeExportInfoForHosts(volume);
	}

	@Override
	public Map<String, HostExportInfo> getSnapshotExportInfoForHosts(VolumeSnapshot snapshot) {
		// TODO Auto-generated method stub
		return ibmsvcDiscoveryHelper.getSnapshotExportInfoForHosts(snapshot);
	}

	@Override
	public Map<String, HostExportInfo> getCloneExportInfoForHosts(VolumeClone clone) {
		// TODO Auto-generated method stub
		return ibmsvcDiscoveryHelper.getCloneExportInfoForHosts(clone);
	}

	@Override
	public Map<String, HostExportInfo> getMirrorExportInfoForHosts(VolumeMirror mirror) {
		return ibmsvcDiscoveryHelper.getMirrorExportInfoForHosts(mirror);
	}

}
