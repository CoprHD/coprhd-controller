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
import com.emc.storageos.driver.ibmsvcdriver.helpers.IBMSVCConsistencyGroups;
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
    private IBMSVCConsistencyGroups ibmsvcConsistencyGroups = new IBMSVCConsistencyGroups();

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
        ConnectionManager.getInstance().setDriverRegistry(driverRegistry);

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

	@Override
	public DriverTask createVolumeSnapshot(List<VolumeSnapshot> snapshots, StorageCapabilities capabilities){
		return ibmsvcConsistencyGroups.createVolumeSnapshot(snapshots,capabilities);
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
