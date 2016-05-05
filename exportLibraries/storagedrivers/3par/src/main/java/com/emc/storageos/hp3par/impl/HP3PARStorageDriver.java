/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.impl;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.lang.mutable.MutableBoolean;
import org.apache.commons.lang.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.hp3par.connection.ConnectionInfo;
import com.emc.storageos.hp3par.connection.HP3PARApiFactory;
import com.emc.storageos.storagedriver.AbstractStorageDriver;
import com.emc.storageos.storagedriver.BlockStorageDriver;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.RegistrationData;
import com.emc.storageos.storagedriver.model.ITL;
import com.emc.storageos.storagedriver.model.Initiator;
import com.emc.storageos.storagedriver.model.StorageHostComponent;
import com.emc.storageos.storagedriver.model.StorageObject;
import com.emc.storageos.storagedriver.model.StoragePool;
import com.emc.storageos.storagedriver.model.StoragePort;
import com.emc.storageos.storagedriver.model.StorageSystem;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.model.VolumeClone;
import com.emc.storageos.storagedriver.model.VolumeConsistencyGroup;
import com.emc.storageos.storagedriver.model.VolumeMirror;
import com.emc.storageos.storagedriver.model.VolumeSnapshot;
import com.emc.storageos.storagedriver.storagecapabilities.CapabilityInstance;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;

public class HP3PARStorageDriver extends AbstractStorageDriver implements BlockStorageDriver {

	private static final Logger _log = LoggerFactory.getLogger(HP3PARStorageDriver.class);
	private ConcurrentMap<String, ConnectionInfo> connectionMap;
	private HP3PARApiFactory hp3parApiFactory;
	
	public HP3PARStorageDriver () {
	    connectionMap = new ConcurrentHashMap<String, ConnectionInfo>();
	    hp3parApiFactory = new HP3PARApiFactory();
	    hp3parApiFactory.setConnectionTimeoutMs(30000);
	    hp3parApiFactory.setConnManagerTimeout(60000);
	    hp3parApiFactory.setSocketConnectionTimeoutMs(7200000);
	    hp3parApiFactory.init();
	}
	
	@Override
	public List<String> getSystemTypes() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DriverTask getTask(String taskId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <T extends StorageObject> T getStorageObject(String storageSystemId, String objectId, Class<T> type) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public RegistrationData getRegistrationData() {
		// TODO Auto-generated method stub
		return null;
	}
	
	///////////
	private HP3PARApi getHP3PARDevice(StorageSystem hp3parSystem) {
        URI deviceURI;
        try {
            deviceURI = new URI("https", null, hp3parSystem.getIpAddress(), hp3parSystem.getPortNumber(), "/", null, null);
            return hp3parApiFactory
                    .getRESTClient(deviceURI, hp3parSystem.getUsername(), hp3parSystem.getPassword());
        } catch (URISyntaxException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return null;
        }       
    }
	
    /**
     * Create driver task for task type
     *
     * @param taskType
     */
    public DriverTask createDriverTask(String taskType) {
        String taskID = String.format("%s+%s+%s", HP3PARConstants.DRIVER_NAME, taskType, UUID.randomUUID());
        DriverTask task = new HP3PARDriverTask(taskID);
        return task;
    }
	
	////////////////

	@Override
	public DriverTask discoverStorageSystem(List<StorageSystem> storageSystems) {
        String taskType = "discover-storage-system";
        String taskId = String.format("%s+%s+%s", HP3PARConstants.DRIVER_NAME, taskType, UUID.randomUUID().toString());
        DriverTask task = new HP3PARDriverTask(taskId);

	    // For each 3par system
	    for (StorageSystem storageSystem : storageSystems) {
	        try {
	            _log.info("StorageDriver: discoverStorageSystem information for storage system {}, name {} - start",
	                    storageSystem.getIpAddress(), storageSystem.getSystemName());            

	            URI deviceURI = new URI("https", null, 
	                    storageSystem.getIpAddress(), storageSystem.getPortNumber(), "/", null, null);

	            ConnectionInfo connectionInfo = connectionMap.get(deviceURI +
	                    ":" + storageSystem.getUsername() + ":" + storageSystem.getPassword());
	            
	            if (connectionInfo == null) {
	                connectionInfo = new ConnectionInfo(storageSystem.getIpAddress(),
                            storageSystem.getPortNumber(),
                            storageSystem.getUsername(),
                            storageSystem.getPassword());

	                // key=uri+user+pass to make unique, value=all details
	                connectionMap.putIfAbsent(deviceURI.toString() +
	                        ":" + storageSystem.getUsername() + ":" + storageSystem.getPassword(),
	                        connectionInfo);
	            }

	            HP3PARApi hp3parApi = getHP3PARDevice(storageSystem);
	            String authToken = hp3parApi.getAuthToken();
	            _log.info("3PAR auth key {} ",authToken);

	            ////
	            ///get serial number and other details
	            ////
	            storageSystem.setNativeId(deviceURI.toString() + 
	                    ":" + storageSystem.getUsername() + ":" + storageSystem.getPassword());
	            _log.info("3PAR discovery successsful---");    
	        } catch (Exception e) {
	            _log.error("Unable to discover the storage system information {}.\n",
	                    storageSystem.getSystemName());
	            task.setMessage(String.format("Unable to query the storage system %s information ",
	                    storageSystem.getSystemName()) + e.getMessage());
	            task.setStatus(DriverTask.TaskStatus.FAILED);
	            e.printStackTrace();
	        }
	    }

	    // return driver task (indicates 
	    return task;
	}

	@Override
	public DriverTask discoverStoragePools(StorageSystem storageSystem, List<StoragePool> storagePools) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DriverTask discoverStoragePorts(StorageSystem storageSystem, List<StoragePort> storagePorts) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DriverTask discoverStorageHostComponents(StorageSystem storageSystem,
			List<StorageHostComponent> embeddedStorageHostComponents) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DriverTask getStorageVolumes(StorageSystem storageSystem, List<StorageVolume> storageVolumes,
			MutableInt token) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DriverTask createVolumes(List<StorageVolume> volumes, StorageCapabilities capabilities) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DriverTask expandVolume(StorageVolume volume, long newCapacity) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DriverTask deleteVolumes(List<StorageVolume> volumes) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DriverTask createVolumeSnapshot(List<VolumeSnapshot> snapshots, StorageCapabilities capabilities) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DriverTask restoreSnapshot(StorageVolume volume, VolumeSnapshot snapshot) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DriverTask deleteVolumeSnapshot(List<VolumeSnapshot> snapshots) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DriverTask createVolumeClone(List<VolumeClone> clones, StorageCapabilities capabilities) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DriverTask detachVolumeClone(List<VolumeClone> clones) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DriverTask restoreFromClone(List<VolumeClone> clones) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DriverTask deleteVolumeClone(List<VolumeClone> clones) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DriverTask createVolumeMirror(List<VolumeMirror> mirrors, StorageCapabilities capabilities) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DriverTask deleteVolumeMirror(List<VolumeMirror> mirrors) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DriverTask splitVolumeMirror(List<VolumeMirror> mirrors) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DriverTask resumeVolumeMirror(List<VolumeMirror> mirrors) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DriverTask restoreVolumeMirror(StorageVolume volume, VolumeMirror mirror) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<ITL> getITL(StorageSystem storageSystem, List<Initiator> initiators) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DriverTask exportVolumesToInitiators(List<Initiator> initiators, List<StorageVolume> volumes,
			Map<String, String> volumeToHLUMap, List<StoragePort> recommendedPorts, List<StoragePort> availablePorts,
			StorageCapabilities capabilities, MutableBoolean usedRecommendedPorts, List<StoragePort> selectedPorts) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DriverTask unexportVolumesFromInitiators(List<Initiator> initiators, List<StorageVolume> volumes) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DriverTask createConsistencyGroup(VolumeConsistencyGroup consistencyGroup) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DriverTask deleteConsistencyGroup(VolumeConsistencyGroup consistencyGroup) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DriverTask createConsistencyGroupSnapshot(VolumeConsistencyGroup consistencyGroup,
			List<VolumeSnapshot> snapshots, List<CapabilityInstance> capabilities) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DriverTask deleteConsistencyGroupSnapshot(List<VolumeSnapshot> snapshots) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DriverTask createConsistencyGroupClone(VolumeConsistencyGroup consistencyGroup, List<VolumeClone> clones,
			List<CapabilityInstance> capabilities) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DriverTask deleteConsistencyGroupClone(List<VolumeClone> clones) {
		// TODO Auto-generated method stub
		return null;
	}

}
