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
import com.emc.storageos.driver.ibmsvcdriver.helpers.*;
import com.emc.storageos.driver.ibmsvcdriver.utils.IBMSVCDriverConfiguration;
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
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

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

    private static final String IBMSVCSYSTEM_CONF_FILE = "ibmsvcdriver-conf.xml";
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
    private IBMSVCSnapshots ibmsvcSnapshots = new IBMSVCSnapshots();
    private IBMSVCClones ibmsvcClones = new IBMSVCClones();
    private ApplicationContext parentApplicationContext;
    private IBMSVCDriverConfiguration ibmsvcdriverConfiguration;
    private static final String CONFIG_BEAN_NAME = "ibmsvcsystemConfig";

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

        ApplicationContext context = new ClassPathXmlApplicationContext(new String[] {IBMSVCSYSTEM_CONF_FILE}, parentApplicationContext);
        ibmsvcdriverConfiguration = (IBMSVCDriverConfiguration) context.getBean(CONFIG_BEAN_NAME);
        IBMSVCDriverConfiguration.setInstance(ibmsvcdriverConfiguration);

	}


    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.parentApplicationContext = applicationContext;
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


	@Override
	public DriverTask createVolumes(List<StorageVolume> volumes, StorageCapabilities capabilities) {
        return ibmsvcProvisioningHelper.createVolumes(volumes, capabilities);
	}

	@Override
	public DriverTask expandVolume(StorageVolume storageVolume, long newCapacity) {
		return ibmsvcProvisioningHelper.expandVolume(storageVolume, newCapacity);
	}

	@Override
	public DriverTask deleteVolume(StorageVolume storageVolume) {
		return ibmsvcProvisioningHelper.deleteVolume(storageVolume);
	}

	@Override
	public DriverTask exportVolumesToInitiators(List<Initiator> initiators, List<StorageVolume> volumes,
			Map<String, String> volumeToHLUMap, List<StoragePort> recommendedPorts, List<StoragePort> availablePorts,
			StorageCapabilities storageCapabilities, MutableBoolean usedRecommendedPorts,
                                                List<StoragePort> selectedPorts) {

        return ibmsvcProvisioningHelper.exportVolumesToInitiators(initiators, volumes, volumeToHLUMap,
                recommendedPorts, availablePorts, storageCapabilities, usedRecommendedPorts, selectedPorts);
	}


	@Override
	public DriverTask unexportVolumesFromInitiators(List<Initiator> initiators, List<StorageVolume> volumes) {
		return ibmsvcProvisioningHelper.unexportVolumesFromInitiators(initiators, volumes);
	}



	@Override
	public DriverTask createVolumeSnapshot(List<VolumeSnapshot> snapshots, StorageCapabilities capabilities){
		return ibmsvcSnapshots.createVolumeSnapshot(snapshots, capabilities);
	}

    @Override
    public DriverTask restoreSnapshot(List<VolumeSnapshot> snapshots) {

        HashSet<String> consistencyGroupSet =new HashSet<String>();

        for (VolumeSnapshot volumeSnapshot : snapshots) {
            if(volumeSnapshot.getConsistencyGroup() != null){
                consistencyGroupSet.add(volumeSnapshot.getConsistencyGroup());
            }
        }

        //All snapshots must belong to same consistency group. If groups is more than 1, Flag error
        if(consistencyGroupSet.size() > 1){
            DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_RESTORE_SNAPSHOT_VOLUMES);
            task.setMessage(String.format("Restore of snapshots failed : %s.", "Snapshots belong to more than one Consistency Groups - " + consistencyGroupSet.toString()));
            task.setStatus(DriverTask.TaskStatus.FAILED);
            return task;

        }else if(consistencyGroupSet.size() == 1){
            return ibmsvcConsistencyGroups.restoreConsistencyGroupSnapshot(snapshots);
        }else{
            return ibmsvcSnapshots.restoreSnapshot(snapshots);
        }

    }

    @Override
    public DriverTask deleteVolumeSnapshot(VolumeSnapshot snapshot){
        return ibmsvcSnapshots.deleteVolumeSnapshot(snapshot);
    }


    @Override
    public DriverTask createVolumeClone(List<VolumeClone> clones, StorageCapabilities capabilities) {
        return ibmsvcClones.createVolumeClone(clones, capabilities);
    }

    @Override
    public DriverTask detachVolumeClone(List<VolumeClone> clones) {
        return ibmsvcClones.detachVolumeClone(clones);
    }

    @Override
    public DriverTask restoreFromClone(List<VolumeClone> clones) {
        HashSet<String> consistencyGroupSet =new HashSet<String>();

        for (VolumeClone volumeClone : clones) {
            if(volumeClone.getConsistencyGroup() != null){
                consistencyGroupSet.add(volumeClone.getConsistencyGroup());
            }
        }

        //All snapshots must belong to same consistency group. If groups is more than 1, Flag error
        if(consistencyGroupSet.size() > 1){
            DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_RESTORE_SNAPSHOT_VOLUMES);
            task.setMessage(String.format("Restore of snapshots failed : %s.", "Snapshots belong to more than one Consistency Groups - " + consistencyGroupSet.toString()));
            task.setStatus(DriverTask.TaskStatus.FAILED);
            return task;

        }else if(consistencyGroupSet.size() == 1){
            return ibmsvcConsistencyGroups.restoreConsistencyGroupClone(clones);
        }else{
            return ibmsvcClones.restoreFromClone(clones);
        }

    }

    @Override
    public DriverTask deleteVolumeClone(VolumeClone clone) {
        return ibmsvcClones.deleteVolumeClone(clone);
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
    public DriverTask createConsistencyGroup(VolumeConsistencyGroup consistencyGroup) {
        return ibmsvcConsistencyGroups.createConsistencyGroup(consistencyGroup);
    }

    @Override
    public DriverTask deleteConsistencyGroup(VolumeConsistencyGroup consistencyGroup) {
        return ibmsvcConsistencyGroups.deleteConsistencyGroup(consistencyGroup);
    }

    @Override
    public DriverTask addVolumesToConsistencyGroup(List<StorageVolume> volumes, StorageCapabilities capabilities) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask removeVolumesFromConsistencyGroup(List<StorageVolume> volumes, StorageCapabilities capabilities) {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public DriverTask createConsistencyGroupSnapshot(VolumeConsistencyGroup consistencyGroup,
                                                     List<VolumeSnapshot> snapshots, List<CapabilityInstance> capabilities) {
        return ibmsvcConsistencyGroups.createConsistencyGroupSnapshot(consistencyGroup, snapshots, capabilities);
    }

    @Override
    public DriverTask deleteConsistencyGroupSnapshot(List<VolumeSnapshot> snapshots) {
        return ibmsvcConsistencyGroups.deleteConsistencyGroupSnapshot(snapshots);
    }

    @Override
    public DriverTask createConsistencyGroupClone(VolumeConsistencyGroup consistencyGroup, List<VolumeClone> clones,
                                                  List<CapabilityInstance> capabilities) {
        return ibmsvcConsistencyGroups.createConsistencyGroupClone(consistencyGroup, clones, capabilities);
    }

	@Override
	public Map<String, HostExportInfo> getVolumeExportInfoForHosts(StorageVolume volume) {
		// TODO Auto-generated method stub
		return ibmsvcDiscoveryHelper.getVolumeExportInfoForHosts(volume);
	}

	@Override
	public Map<String, HostExportInfo> getSnapshotExportInfoForHosts(VolumeSnapshot snapshot) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<String, HostExportInfo> getCloneExportInfoForHosts(VolumeClone clone) {
		// TODO Auto-generated method stub
        return null;
	}

	@Override
	public Map<String, HostExportInfo> getMirrorExportInfoForHosts(VolumeMirror mirror) {
        return null;
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

}
