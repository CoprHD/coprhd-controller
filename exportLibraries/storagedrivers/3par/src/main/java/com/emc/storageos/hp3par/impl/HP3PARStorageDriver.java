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

import org.apache.commons.lang.mutable.MutableBoolean;
import org.apache.commons.lang.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.hp3par.command.CPGCommandResult;
import com.emc.storageos.hp3par.command.CPGMembers;
import com.emc.storageos.hp3par.command.PortCommandResult;
import com.emc.storageos.hp3par.command.PortMembers;
import com.emc.storageos.hp3par.command.PortStatMembers;
import com.emc.storageos.hp3par.command.PortStatisticsCommandResult;
import com.emc.storageos.hp3par.command.SystemCommandResult;
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
import com.emc.storageos.storagedriver.model.StoragePool.PoolOperationalStatus;
import com.emc.storageos.storagedriver.model.StoragePool.PoolServiceType;
import com.emc.storageos.storagedriver.model.StoragePool.Protocols;
import com.emc.storageos.storagedriver.model.StoragePool.RaidLevels;
import com.emc.storageos.storagedriver.model.StoragePool.SupportedDriveTypes;
import com.emc.storageos.storagedriver.model.StoragePool.SupportedResourceType;
import com.emc.storageos.storagedriver.model.StoragePort;
import com.emc.storageos.storagedriver.model.StoragePort.PortType;
import com.emc.storageos.storagedriver.model.StoragePort.TransportType;
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
	private HP3PARApiFactory hp3parApiFactory;
	
	public HP3PARStorageDriver () {
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
	            _log.info("3PAR: DiscoverStorageSystem information for storage system {}, name {} - start",
	                    storageSystem.getIpAddress(), storageSystem.getSystemName());            

	            URI deviceURI = new URI("https", null, 
                        storageSystem.getIpAddress(), storageSystem.getPortNumber(), "/", null, null);
                String uniqueId = deviceURI.toString();

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
	            
                // protocols supported
                List<String> protocols = new ArrayList<String>();
                protocols.add(Protocols.iSCSI.toString());
                protocols.add(Protocols.FC.toString());
                protocols.add(Protocols.FCoE.toString());
                storageSystem.setProtocols(protocols);
	            
	            storageSystem.setFirmwareVersion(systemRes.getSystemVersion());
	            storageSystem.setIsSupportedVersion(true); //always supported
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
	            setConnInfoToRegistry(storageSystem.getNativeId(), storageSystem.getIpAddress(), storageSystem.getPortNumber(),
	                    storageSystem.getUsername(), storageSystem.getPassword());

	            task.setStatus(DriverTask.TaskStatus.READY);
	            _log.info("3PAR: Successfull discovery storage system {}, name {} - end",
	                        storageSystem.getIpAddress(), storageSystem.getSystemName());    
	        } catch (Exception e) {
	            _log.error("3PAR: Unable to discover the storage system information {}.\n",
	                    storageSystem.getSystemName());
	            task.setMessage(String.format("Unable to query the storage system %s information ",
	                    storageSystem.getSystemName()) + e.getMessage());
	            task.setStatus(DriverTask.TaskStatus.FAILED);
	            e.printStackTrace();
	            // return error task immediately
	            break;
	        }
	    } // end for each StorageSystem
	    
	    return task;
	}

	@Override
	public DriverTask discoverStoragePools(StorageSystem storageSystem, List<StoragePool> storagePools) {
	    //For this 3PAR system
	    _log.info("3PAR: discoverStoragePools information for storage system {}, nativeId {} - start",
                storageSystem.getIpAddress(), storageSystem.getNativeId());
	    DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_DISCOVER_STORAGE_POOLS);

	    try {
	        // get Api client
	        HP3PARApi hp3parApi = getHP3PARDeviceFromNativeId(storageSystem.getNativeId());

            // get storage pool details
            CPGCommandResult cpgResult = hp3parApi.getCPGDetails();
            
            // for each ViPR Storage pool = 3PAR CPG
            for (int index = 0; index < cpgResult.getTotal(); index++) {
                StoragePool pool = new StoragePool();
                CPGMembers currMember =  cpgResult.getMembers().get(index);
                
                pool.setPoolName(currMember.getName());
                pool.setStorageSystemId(storageSystem.getNativeId());
                
                Set<Protocols> supportedProtocols = new HashSet<>();
                supportedProtocols.add(Protocols.iSCSI);
                supportedProtocols.add(Protocols.FC);
                supportedProtocols.add(Protocols.FCoE);
                pool.setProtocols(supportedProtocols);
                
                pool.setTotalCapacity((currMember.getUsrUsage().getTotalMiB().longValue() +
                        currMember.getSAUsage().getTotalMiB().longValue() +
                        currMember.getSDUsage().getTotalMiB().longValue()) *
                        HP3PARConstants.KILO_BYTE); 
                pool.setSubscribedCapacity((currMember.getUsrUsage().getUsedMiB().longValue() +
                        currMember.getSAUsage().getUsedMiB().longValue() +
                        currMember.getSDUsage().getUsedMiB().longValue()) *
                        HP3PARConstants.KILO_BYTE);
                pool.setFreeCapacity(pool.getTotalCapacity() - pool.getSubscribedCapacity());
                
                pool.setOperationalStatus(currMember.getState() == 1 ? 
                        PoolOperationalStatus.READY :  PoolOperationalStatus.NOTREADY);
                
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
                for (int j = 0; j < currMember.getSDGrowth().getLDLayout().getDiskPatterns().size(); j ++) {
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
                
                pool.setMaximumThinVolumeSize(16 * HP3PARConstants.KILO_BYTE * HP3PARConstants.KILO_BYTE);
                pool.setMinimumThinVolumeSize(256 * HP3PARConstants.KILO_BYTE);
                pool.setMaximumThickVolumeSize(16 * HP3PARConstants.KILO_BYTE * HP3PARConstants.KILO_BYTE);
                pool.setMinimumThickVolumeSize(256 * HP3PARConstants.KILO_BYTE);

                pool.setSupportedResourceType(SupportedResourceType.THIN_AND_THICK);
                pool.setPoolServiceType(PoolServiceType.block);
                
                // Storage object properties
                pool.setNativeId(currMember.getUuid());
                pool.setDeviceLabel(currMember.getName());
                pool.setDisplayName(currMember.getName());
                storageSystem.setAccessStatus(AccessStatus.READ_WRITE);

                _log.info("3PAR: added storage pool {}, native id {}",  pool.getPoolName(), pool.getNativeId());
                storagePools.add(pool);
            } //for each storage pool
	        
            task.setStatus(DriverTask.TaskStatus.READY);
            _log.info("3PAR: discoverStoragePools information for storage system {}, nativeId {} - end",
                    storageSystem.getIpAddress(), storageSystem.getNativeId());
	    } catch (Exception e) {
            _log.error("3PAR: Unable to discover the storage pool information {}.\n",
                    storageSystem.getSystemName());
            task.setMessage(String.format("Unable to query the storage system %s information ",
                    storageSystem.getSystemName()) + e.getMessage());
            task.setStatus(DriverTask.TaskStatus.FAILED);
            e.printStackTrace();
        }
        return task;
	}

	@Override
	public DriverTask discoverStoragePorts(StorageSystem storageSystem, List<StoragePort> storagePorts) {
        //For this 3PAR system
        _log.info("3PAR: discoverStoragePorts information for storage system {}, nativeId {} - start",
                storageSystem.getIpAddress(), storageSystem.getNativeId());
        DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_DISCOVER_STORAGE_PORTS);

        try {
            // get Api client
            HP3PARApi hp3parApi = getHP3PARDeviceFromNativeId(storageSystem.getNativeId());

            // get storage port details
            PortCommandResult portResult = hp3parApi.getPortDetails();
            PortStatisticsCommandResult portStatResult = hp3parApi.getPortStatisticsDetail();
            
            // for each ViPR Storage port = 3PAR host port
            for (int index = 0; index < portResult.getTotal(); index++) {
                StoragePort port = new StoragePort();
                PortMembers currMember =  portResult.getMembers().get(index);
                
                // Avoid suspended and free ports
                if (currMember.getMode() == HP3PARConstants.MODE_SUSPENDED || 
                        currMember.getType() == HP3PARConstants.TYPE_FREE) {
                    continue;
                }
                
                if (currMember.getLabel() == null) {
                    String label = String.format("port:%s:%s:%s", currMember.getPortPos().getNode(),
                            currMember.getPortPos().getSlot(), currMember.getPortPos().getCardPort());
                    port.setPortName(label);
                }
                
                port.setStorageSystemId(storageSystem.getNativeId());
                
                // set protocol and port number(WWWN/iqn)
                switch(currMember.getProtocol()) {
                    case 1:
                    case 3:
                        port.setTransportType(TransportType.FC);
                        break;
                    case 2:
                    case 4:
                        port.setTransportType(TransportType.IP);
                        break;
                }
                
                // loop for port speed as specific query is not supported
                for (int stat = 0; stat < portStatResult.getTotal(); stat++) {
                    PortStatMembers currStat = portStatResult.getMembers().get(stat);

                    if (currMember.getPortPos().getNode() == currStat.getNode() && 
                            currMember.getPortPos().getSlot() == currStat.getSlot() && 
                            currMember.getPortPos().getCardPort() == currStat.getCardPort()) {
                        port.setPortSpeed(currStat.getSpeed() * HP3PARConstants.KILO_BYTE * HP3PARConstants.KILO_BYTE);
                    }
                }

                // grouping with cluster node and slot
                port.setPortGroup(currMember.getPortPos().getNode().toString());
                port.setPortSubGroup(currMember.getPortPos().getSlot().toString());
                
                // set protocol specific properties
                if (port.getTransportType().equals(TransportType.FC)) {
                    port.setPortNetworkId(currMember.getPortWWN());                    
                } else {
                    port.setIpAddress(currMember.getIPAddr());
                    port.setPortNetworkId(currMember.getiSCSINmae());                    
                }

                // connected to disk or host(switch)
                if (currMember.getType() == HP3PARConstants.TYPE_DISK) {
                    port.setPortType(PortType.backend);
                } else {
                    port.setPortType(PortType.frontend);
                }
                
                String id = String.format("port:%s:%s:%s", currMember.getPortPos().getNode(),
                        currMember.getPortPos().getSlot(), currMember.getPortPos().getCardPort());
                port.setNativeId(id);
                port.setOperationalStatus(StoragePort.OperationalStatus.OK);  
                
                _log.info("3PAR: added storage port {}, native id {}",  port.getPortName(), port.getNativeId());
                storagePorts.add(port);
            } //for each storage pool
            
            task.setStatus(DriverTask.TaskStatus.READY);
            _log.info("3PAR: discoverStoragePorts information for storage system {}, nativeId {} - end",
                    storageSystem.getIpAddress(), storageSystem.getNativeId());
        } catch (Exception e) {
            _log.error("3PAR: Unable to discover the storage port information {}.\n",
                    storageSystem.getSystemName());
            task.setMessage(String.format("Unable to query the storage system %s information ",
                    storageSystem.getSystemName()) + e.getMessage());
            task.setStatus(DriverTask.TaskStatus.FAILED);
            e.printStackTrace();
        }
        return task;
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
            _log.error("Error in getting 3PAR device: input StorageSystem");
            throw new HP3PARException("Error in getting 3PAR device");
        }       
    }

    private HP3PARApi getHP3PARDevice(String ip, String port, String user, String pass) throws HP3PARException {
        URI deviceURI;
        try {
            deviceURI = new URI("https", null, ip, Integer.parseInt(port), "/", null, null);
            return hp3parApiFactory
                    .getRESTClient(deviceURI, user, pass);
        } catch (Exception e) {
            e.printStackTrace();
            _log.error("Error in getting 3PAR device: input StorageSystem");
            throw new HP3PARException("Error in getting 3PAR device");
        }       
    }
    
    private HP3PARApi getHP3PARDeviceFromNativeId(String nativeId) throws HP3PARException {
        try {
            Map<String, List<String>> connectionInfo =
                    driverRegistry.getDriverAttributesForKey(HP3PARConstants.DRIVER_NAME, nativeId);
            List<String> ipAddress = connectionInfo.get(HP3PARConstants.IP_ADDRESS);
            List<String> portNumber = connectionInfo.get(HP3PARConstants.PORT_NUMBER);
            List<String> userName = connectionInfo.get(HP3PARConstants.USER_NAME);
            List<String> password = connectionInfo.get(HP3PARConstants.PASSWORD);
            HP3PARApi hp3parApi = getHP3PARDevice(ipAddress.get(0), portNumber.get(0),
                    userName.get(0),password.get(0));
            return hp3parApi;
        } catch (Exception e) {
            e.printStackTrace();
            _log.error("Error in getting 3PAR device: input nativeId");
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
        attributes.put(HP3PARConstants.IP_ADDRESS, listIP);
                listPort.add(Integer.toString(port));
        attributes.put(HP3PARConstants.PORT_NUMBER, listPort);
                listUserName.add(username);
        attributes.put(HP3PARConstants.USER_NAME, listUserName);
                listPwd.add(password);
        attributes.put(HP3PARConstants.PASSWORD, listPwd);
        //_log.info(String.format("StorageDriver: setting connection information for %s, attributes: %s ", systemNativeId, attributes));
        this.driverRegistry.setDriverAttributesForKey(HP3PARConstants.DRIVER_NAME, systemNativeId, attributes);
    }
}
