/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.impl;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.lang.mutable.MutableBoolean;
import org.apache.commons.lang.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.hp3par.command.SystemCommandResult;
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
import com.emc.storageos.storagedriver.model.StoragePool.Protocols;
import com.emc.storageos.storagedriver.model.StoragePort;
import com.emc.storageos.storagedriver.model.StorageSystem;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.model.VolumeClone;
import com.emc.storageos.storagedriver.model.VolumeConsistencyGroup;
import com.emc.storageos.storagedriver.model.VolumeMirror;
import com.emc.storageos.storagedriver.model.VolumeSnapshot;
import com.emc.storageos.storagedriver.model.StorageObject.AccessStatus;
import com.emc.storageos.storagedriver.model.StorageSystem.SupportedProvisioningType;
import com.emc.storageos.storagedriver.model.StorageSystem.SupportedReplication;
import com.emc.storageos.storagedriver.storagecapabilities.CapabilityInstance;
import com.emc.storageos.storagedriver.storagecapabilities.CommonStorageCapabilities;
import com.emc.storageos.storagedriver.storagecapabilities.CustomStorageCapabilities;
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
	
	@Override
	public DriverTask discoverStorageSystem(List<StorageSystem> storageSystems) {
	    DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_DISCOVER_STORAGE_SYSTEM);

	    // For each 3par system
	    for (StorageSystem storageSystem : storageSystems) {
	        try {
	            _log.info("3PAR DiscoverStorageSystem information for storage system {}, name {} - start",
	                    storageSystem.getIpAddress(), storageSystem.getSystemName());            

	            URI deviceURI = new URI("https", null, 
	                    storageSystem.getIpAddress(), storageSystem.getPortNumber(), "/", null, null);
	            String uniqueId = deviceURI.toString();

	            ConnectionInfo connectionInfo = new ConnectionInfo(storageSystem.getIpAddress(),
	                    storageSystem.getPortNumber(),
	                    storageSystem.getUsername(),
	                    storageSystem.getPassword());

	            // Re-enter the connection info always as there could be change in user name/password 
	            connectionMap.put(uniqueId, connectionInfo);

	            HP3PARApi hp3parApi = getHP3PARDevice(storageSystem);
	            String authToken = hp3parApi.getAuthToken(storageSystem.getUsername(),storageSystem.getPassword());
	            if (authToken == null) {
	                break;
	            }
	            //_log.info("3PAR auth key: {}", authToken);
	            
	            // get storage details
	            SystemCommandResult systemRes = hp3parApi.getSystemDetails();
	            storageSystem.setSerialNumber(systemRes.getSerialNumber());
	            storageSystem.setMajorVersion(systemRes.getSystemVersion());
	            storageSystem.setMinorVersion("0"); //as there is no individual portion in 3par api
	            storageSystem.setFirmwareVersion(systemRes.getSystemVersion());
	            storageSystem.setIsSupportedVersion(true); //always supported
	            storageSystem.setModel(systemRes.getModel());
	            storageSystem.setProvisioningType(SupportedProvisioningType.THIN_AND_THICK);
                Set<StorageSystem.SupportedReplication> supportedReplications = new HashSet<>();
                supportedReplications.add(StorageSystem.SupportedReplication.elementReplica);
                supportedReplications.add(StorageSystem.SupportedReplication.groupReplica);
                storageSystem.setSupportedReplications(supportedReplications);
                storageSystem.setNativeId(uniqueId + ":" + systemRes.getSerialNumber());

                if (storageSystem.getDeviceLabel() == null) {
	                if (storageSystem.getDisplayName() != null) {
	                    storageSystem.setDeviceLabel(storageSystem.getDisplayName());
	                } else if (systemRes.getName() != null) {
	                    storageSystem.setDeviceLabel(systemRes.getName());
	                    storageSystem.setDisplayName(systemRes.getName());
	                }
	            }
                
                // protocols supported
                List<String> protocols = new ArrayList<String>();
                protocols.add(Protocols.iSCSI.toString());
                protocols.add(Protocols.FC.toString());
                protocols.add(Protocols.FCoE.toString());
                storageSystem.setProtocols(protocols);

                storageSystem.setAccessStatus(AccessStatus.READ_WRITE);
	            setConnInfoToRegistry(storageSystem.getNativeId(), storageSystem.getIpAddress(), storageSystem.getPortNumber(),
	                    storageSystem.getUsername(), storageSystem.getPassword());

	            task.setStatus(DriverTask.TaskStatus.READY);
	            storageSystem.setNativeId(uniqueId);
	            _log.info("Successfull discovery of 3PAR storage system {}, name {} - end",
	                        storageSystem.getIpAddress(), storageSystem.getSystemName());    
	        } catch (Exception e) {
	            _log.error("Unable to discover the storage system information {}.\n",
	                    storageSystem.getSystemName());
	            task.setMessage(String.format("Unable to query the storage system %s information ",
	                    storageSystem.getSystemName()) + e.getMessage());
	            task.setStatus(DriverTask.TaskStatus.FAILED);
	            e.printStackTrace();
	            // return error task immediately
	            return task;
	        }
	    } // end for each StorageSystem
	    
	    return task;
	}

	@Override
	public DriverTask discoverStoragePools(StorageSystem storageSystem, List<StoragePool> storagePools) {
		// TODO Auto-generated method stub
	    _log.info("3PAR Discover pools for native id {}", storageSystem.getNativeId());
		return null;
	}

	@Override
	public DriverTask discoverStoragePorts(StorageSystem storageSystem, List<StoragePort> storagePorts) {
		// TODO Auto-generated method stub
	    _log.info("3PAR Discover ports");
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
	
	/*
	 * Internal methods in the driver
	 */
    private HP3PARApi getHP3PARDevice(StorageSystem hp3parSystem) throws HP3PARException {
        URI deviceURI;
        try {
            deviceURI = new URI("https", null, hp3parSystem.getIpAddress(), hp3parSystem.getPortNumber(), "/", null, null);
            return hp3parApiFactory
                    .getRESTClient(deviceURI, hp3parSystem.getUsername(), hp3parSystem.getPassword());
        } catch (Exception e) {
            e.printStackTrace();
            _log.error("Error in getting 3PAR device");
            throw new HP3PARException("Error in getting 3PAR device");
        }       
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
    
    private void setConnInfoToRegistry(String systemNativeId, String ipAddress, int port, String username, String password) {
        Map<String, List<String>> attributes = new HashMap<>();
        List<String> listIP = new ArrayList<>();
        List<String> listPort = new ArrayList<>();
        List<String> listUserName = new ArrayList<>();
        List<String> listPwd = new ArrayList<>();

        listIP.add(ipAddress);
        attributes.put("IP_ADDRESS", listIP);
                listPort.add(Integer.toString(port));
        attributes.put("PORT_NUMBER", listPort);
                listUserName.add(username);
        attributes.put("USER_NAME", listUserName);
                listPwd.add(password);
        attributes.put("PASSWORD", listPwd);
        //_log.info(String.format("StorageDriver: setting connection information for %s, attributes: %s ", systemNativeId, attributes));
        this.driverRegistry.setDriverAttributesForKey("StorageDriverSimulator", systemNativeId, attributes);
    }
}
