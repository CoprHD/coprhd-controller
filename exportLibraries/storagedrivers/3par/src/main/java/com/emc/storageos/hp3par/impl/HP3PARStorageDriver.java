
/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.impl;

import java.io.PrintWriter;
import java.io.StringWriter;
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

import com.emc.storageos.hp3par.command.CPGCommandResult;
import com.emc.storageos.hp3par.command.CPGMember;
import com.emc.storageos.hp3par.command.ConsistencyGroupResult;
import com.emc.storageos.hp3par.command.ConsistencyGroupsListResult;
import com.emc.storageos.hp3par.command.FcPath;
import com.emc.storageos.hp3par.command.HostCommandResult;
import com.emc.storageos.hp3par.command.HostMember;
import com.emc.storageos.hp3par.command.HostSetDetailsCommandResult;
import com.emc.storageos.hp3par.command.ISCSIPath;
import com.emc.storageos.hp3par.command.PortCommandResult;
import com.emc.storageos.hp3par.command.PortMembers;
import com.emc.storageos.hp3par.command.PortStatMembers;
import com.emc.storageos.hp3par.command.PortStatisticsCommandResult;
import com.emc.storageos.hp3par.command.Position;
import com.emc.storageos.hp3par.command.SystemCommandResult;
import com.emc.storageos.hp3par.command.VirtualLun;
import com.emc.storageos.hp3par.command.VirtualLunsList;

import com.emc.storageos.hp3par.command.VlunResult;
import com.emc.storageos.hp3par.command.VolumeAncestorInfo;
import com.emc.storageos.hp3par.command.VolumeDetailsCommandResult;
import com.emc.storageos.hp3par.connection.HP3PARApiFactory;
import com.emc.storageos.hp3par.utils.CompleteError;
import com.emc.storageos.hp3par.utils.HP3PARConstants;
import com.emc.storageos.hp3par.utils.HP3PARConstants.copyType;
import com.emc.storageos.hp3par.utils.SanUtils;
import com.emc.storageos.storagedriver.AbstractStorageDriver;
import com.emc.storageos.storagedriver.BlockStorageDriver;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.HostExportInfo;
import com.emc.storageos.storagedriver.RegistrationData;
import com.emc.storageos.storagedriver.model.Initiator;
import com.emc.storageos.storagedriver.model.Initiator.HostOsType;
import com.emc.storageos.storagedriver.model.Initiator.Type;
import com.emc.storageos.storagedriver.model.StorageBlockObject;
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
import com.emc.storageos.storagedriver.model.StoragePort.TransportType;
import com.emc.storageos.storagedriver.model.StorageProvider;
import com.emc.storageos.storagedriver.model.StorageSystem;
import com.emc.storageos.storagedriver.model.StorageSystem.SupportedProvisioningType;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.model.VolumeClone;
import com.emc.storageos.hp3par.command.VVSetCloneList;
import com.emc.storageos.hp3par.command.VVSetCloneList.VVSetVolumeClone;
import com.emc.storageos.storagedriver.model.VolumeConsistencyGroup;
import com.emc.storageos.storagedriver.model.VolumeMirror;
import com.emc.storageos.storagedriver.model.VolumeSnapshot;
import com.emc.storageos.storagedriver.storagecapabilities.CapabilityInstance;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;
import com.emc.storageos.hp3par.command.VolumesCommandResult;
import com.emc.storageos.hp3par.command.VolumeMember;

/**
 * 
 * Implements functions to discover the HP 3PAR storage and provide provisioning
 * You can refer super class for method details
 *
 */
public class HP3PARStorageDriver extends AbstractStorageDriver implements BlockStorageDriver {

	private static final Logger _log = LoggerFactory.getLogger(HP3PARStorageDriver.class);
	private HP3PARApiFactory hp3parApiFactory = null;

	// HashMap of list of storage ports discovered for each storage systems.
	// KEY: storage system id would be the key.
	// VALUE:List of storage ports discovered for the storage system identified
	// by the key.
	private static Map<String, List<StoragePort>> storagePortMap = new HashMap<String, List<StoragePort>>();
	private static HashMap<String, HashMap<String, ArrayList<VolumeMember>>> vvolToVvolsMap = new HashMap<String, HashMap<String, ArrayList<VolumeMember>>>();
	private static HashMap<String, HashMap<Long, VolumeAncestorInfo>> vvolIdTOMetaInfoMap = new HashMap<String, HashMap<Long, VolumeAncestorInfo>>();
	
	//private static HashMap<Integer, String> vvolIdTONameMap = new HashMap<Integer, String>();	
	private static Map<String, List<String>> snapshotsMap = new HashMap<String, List<String>>();
	private static Map<String, List<String>> clonesMap = new HashMap<String, List<String>>();

	public HP3PARStorageDriver() {
		_log.info("3PARDriver:HP3PARStorageDriver enter");
		if (hp3parApiFactory == null) {
			hp3parApiFactory = new HP3PARApiFactory();
			hp3parApiFactory.setConnectionTimeoutMs(30000);
			hp3parApiFactory.setConnManagerTimeout(60000);
			hp3parApiFactory.setSocketConnectionTimeoutMs(7200000);
			hp3parApiFactory.init();
		}
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
		_log.info("3PARDriver: getStorageObject Running ");
		try {
			HP3PARApi hp3parApi = getHP3PARDeviceFromNativeId(storageSystemId);
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
		_log.info("3PARDriver: getStorageObject Running ");
		// TODO Auto-generated method stub
		return null;
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

	        URI deviceURI = new URI("https", null, 
	                storageSystem.getIpAddress(), storageSystem.getPortNumber(), "/", null, null);

	        // remove '/' as lock fails with this name
	        String uniqueId = deviceURI.toString();
	        uniqueId = uniqueId.replace("/", "");

	        HP3PARApi hp3parApi = getHP3PARDevice(storageSystem);
	        String authToken = hp3parApi.getAuthToken(storageSystem.getUsername(),storageSystem.getPassword());
	        if (authToken == null) {
	            throw new HP3PARException("Could not get authentication token");
	        }

	        // Verify user role
	        hp3parApi.verifyUserRole(storageSystem.getUsername());

	        // get storage details
	        SystemCommandResult systemRes = hp3parApi.getSystemDetails();
	        storageSystem.setSerialNumber(systemRes.getSerialNumber());
	        storageSystem.setMajorVersion(systemRes.getSystemVersion());
	        storageSystem.setMinorVersion("0"); //as there is no individual portion in 3par api

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
	        // 3PAR Remote copy will be supported in coming versions
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
	    //For this 3PAR system
	    _log.info("3PARDriver: discoverStoragePools information for storage system {}, nativeId {} - start",
                storageSystem.getIpAddress(), storageSystem.getNativeId());
	    DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_DISCOVER_STORAGE_POOLS);

	    try {
	        // get Api client
	        HP3PARApi hp3parApi = getHP3PARDeviceFromNativeId(storageSystem.getNativeId());

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
                
                pool.setMaximumThinVolumeSize(16 * HP3PARConstants.MEGA_BYTE);
                pool.setMinimumThinVolumeSize(256 * HP3PARConstants.KILO_BYTE);
                pool.setMaximumThickVolumeSize(16 * HP3PARConstants.MEGA_BYTE);
                pool.setMinimumThickVolumeSize(256 * HP3PARConstants.KILO_BYTE);

                pool.setSupportedResourceType(SupportedResourceType.THIN_AND_THICK);
                pool.setPoolServiceType(PoolServiceType.block);
                
                // Storage object properties
                pool.setNativeId(currMember.getName()); //SB SDK is not sending pool name in volume creation
                pool.setDeviceLabel(currMember.getName());
                pool.setDisplayName(currMember.getName());
                storageSystem.setAccessStatus(AccessStatus.READ_WRITE);

                _log.info("3PARDriver: added storage pool {}, native id {}",  pool.getPoolName(), pool.getNativeId());
                storagePools.add(pool);
            } //for each storage pool
	        
            task.setStatus(DriverTask.TaskStatus.READY);
            _log.info("3PARDriver: discoverStoragePools information for storage system {}, nativeId {} - end",
                    storageSystem.getIpAddress(), storageSystem.getNativeId());
	    } catch (Exception e) {
	        String msg = String.format
	                ("3PARDriver: Unable to discover the storage pool information for storage system %s native id %s; Error: %s.\n",
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

		if (token.intValue() == 0) {
			// arrayToVolumeToVolumeExportInfoMap.clear();
		}

		DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_GET_STORAGE_VOLUMES);

		List<StoragePort> ports = new ArrayList<>();
		discoverStoragePorts(storageSystem, ports);
		
		HashMap<String, ArrayList<VolumeMember>> mappings = null;
		HashMap<Long, VolumeAncestorInfo> idToNameMap = null;
		
		if(vvolToVvolsMap.containsKey(storageSystem.getNativeId())){
			mappings = vvolToVvolsMap.get(storageSystem.getNativeId());
		}
		else{
			mappings = new HashMap<String, ArrayList<VolumeMember>>();
			vvolToVvolsMap.put(storageSystem.getNativeId(), mappings);
		}
		if(vvolIdTOMetaInfoMap.containsKey(storageSystem.getNativeId())){
			idToNameMap = vvolIdTOMetaInfoMap.get(storageSystem.getNativeId());
		}
		else{
			idToNameMap = new HashMap<Long, VolumeAncestorInfo>();
			vvolIdTOMetaInfoMap.put(storageSystem.getNativeId(), idToNameMap);
		}
				
		try {
			HashMap<String, ArrayList<String>> volumesToVolSetsMap = generateVolumeSetToVolumeMap(storageSystem);

			// get Api client
			HP3PARApi hp3parApi = getHP3PARDeviceFromNativeId(storageSystem.getNativeId());
			VolumesCommandResult objStorageVolumes = hp3parApi.getStorageVolumes();

			_log.info("BEFORE THE FIRST FOR LOOP");
			_log.info("BEFORE THE FIRST FOR LOOP the TOTAL SIZE IS {}", objStorageVolumes.getTotal());
			
			//first we build HashMap of volume id , volume name
			for (int volIndex = 0; volIndex < objStorageVolumes.getTotal(); volIndex++) {
				VolumeMember objVolMember = objStorageVolumes.getMembers().get(volIndex);
				if(objVolMember.getCopyType() == HP3PARConstants.copyType.VIRTUAL_COPY.getValue()){
					VolumeAncestorInfo objAncestorInfo = new VolumeAncestorInfo();
					objAncestorInfo.setBaseId(objVolMember.getBaseId());
					objAncestorInfo.setCopyType(objVolMember.getCopyType());
					objAncestorInfo.setName(objVolMember.getName());
					idToNameMap.put(new Long(objVolMember.getId()), objAncestorInfo);
				}
				else if(objVolMember.getCopyType() == HP3PARConstants.copyType.PHYSICAL_COPY.getValue()){
					VolumeAncestorInfo objAncestorInfo = new VolumeAncestorInfo();
					objAncestorInfo.setBaseId(objVolMember.getPhysParentId());
					objAncestorInfo.setCopyType(objVolMember.getCopyType());
					objAncestorInfo.setName(objVolMember.getName());
					idToNameMap.put(new Long(objVolMember.getId()), objAncestorInfo);
				}
			}	
			
			_log.info("idToNameMap is {}", idToNameMap);				
			_log.info("objStorageVolumes.getTotal() is {}", objStorageVolumes.getTotal());
			
			for (int volIndex = 0; volIndex < objStorageVolumes.getTotal(); volIndex++) {
				VolumeMember objVolMember = objStorageVolumes.getMembers().get(volIndex);
				_log.info("volindex is {}", volIndex);
				_log.info("objVolMember.getid is {}", objVolMember.getId());
				_log.info("objVolMember.getbaseid is {}", objVolMember.getBaseId());
				_log.info("objVolMember.getname is {}", objVolMember.getName());
				StorageVolume driverVolume = new StorageVolume();
				driverVolume.setStorageSystemId(storageSystem.getNativeId());
				driverVolume.setStoragePoolId(objVolMember.getUserCPG());
				driverVolume.setNativeId(objVolMember.getName());
				// if (VOLUMES_IN_CG) {
				// driverVolume.setConsistencyGroup("driverSimulatorCG-" +
				// token.intValue());
				// }
				//driverVolume.setConsistencyGroup(consistencyGroup);
				driverVolume.setProvisionedCapacity(objVolMember.getSizeMiB() * HP3PARConstants.MEGA_BYTE);
				driverVolume.setAllocatedCapacity(objVolMember.getSizeMiB() * HP3PARConstants.MEGA_BYTE);
				driverVolume.setWwn(objVolMember.getWwn());
				driverVolume.setNativeId(objVolMember.getName()); 
				driverVolume.setDeviceLabel(objVolMember.getName());

				// if the volumesToVolSetsMap contains the volume name entry. It  means 
				//that volume belongs to consistency group(volume set in hp3par terminology)
				if (volumesToVolSetsMap.containsKey(objVolMember.getName())) {
					driverVolume.setConsistencyGroup(volumesToVolSetsMap.get(objVolMember.getName()).get(0));
				} else {
					_log.debug("Unmanaged volume volume {}  not part of any consistency group", driverVolume);
				}

				if (objVolMember.isReadOnly()) {
					driverVolume.setAccessStatus(StorageVolume.AccessStatus.READ_ONLY);
				} else {
					driverVolume.setAccessStatus(StorageVolume.AccessStatus.READ_WRITE);
				}

				if (objVolMember.getProvisioningType() == HP3PARConstants.provisioningType.TPVV.getValue()) {
					driverVolume.setThinlyProvisioned(true);
				} else {
					driverVolume.setThinlyProvisioned(false);
				}

				// TODO: how much should the thin volume preallocation size be.
				driverVolume.setThinVolumePreAllocationSize(3000L);
				
				_log.info("objVolMember.getCopyOf() in getstoragevolumes is {}",objVolMember.getCopyOf());
				if(objVolMember.getCopyOf()!=null){
					
				}
				else{
					_log.info("Adding to storagevolumes array the volume {}", objVolMember.getName());
					storageVolumes.add(driverVolume);
				}				
				
				_log.info("Unmanaged volume info: pool {}, volume {}", driverVolume.getStoragePoolId(), driverVolume);				
				_log.info("objVolMember.getCopyOf() is {}", objVolMember.getCopyOf());
								
				if( objVolMember.getCopyOf() != null){
					_log.info("Came in the the IF CONDITION");
					_log.info("objVolMember.getCopyType() {}", objVolMember.getCopyType());
					
					VolumeAncestorInfo objAncestor = null;					
					//Here we see if the current VVOL entity's copyof value corresponds to a physical
					//volume or another virtual copy. 
					//Example: snapA, is the snapshot of volumeA. Now snapA', is the snapshot of snapA.
					//then when objVolMember represents snapA', then copyOf will point to snapA, but
					//baseid will point to the id of the volume volumeA.					
					if(objVolMember.getCopyType()==copyType.VIRTUAL_COPY.getValue()){						
						_log.info("objVolMember.getBaseId() is {}",objVolMember.getBaseId());
						objAncestor = idToNameMap.get(objVolMember.getBaseId());										
					}
					else if(objVolMember.getCopyType()==copyType.PHYSICAL_COPY.getValue()){						
						_log.info("objVolMember.getBaseId() is {}",objVolMember.getPhysParentId());
						objAncestor = idToNameMap.get(objVolMember.getPhysParentId());						
					}
					
					if(mappings.containsKey(objAncestor.getName())){
						_log.info("IN THE IF COND ADDING TO THE LIST OF CHILDREN {}" ,objVolMember.getName());							
						ArrayList<VolumeMember> listOfChildren = (ArrayList<VolumeMember>)mappings.get(objAncestor.getName());
						listOfChildren.add(objVolMember);
					}
					else{
						_log.info("IN THE ELSE COND ADDING TO THE LIST OF CHILDREN {}" ,objVolMember.getName());
						ArrayList<VolumeMember> listOfChildren = new ArrayList<VolumeMember>();
						listOfChildren.add(objVolMember);
						mappings.put(objAncestor.getName() , listOfChildren);
					}
					_log.info("objAncestor name is {}" , objAncestor.getName());
					_log.info("objVolMember being added is {} ", objVolMember.getName());				
					_log.info("objAncestor is {}" , objAncestor);
				}					
				_log.info("volIndex after the if condition is {}", volIndex);	
				_log.info("AFTER IF objStorageVolumes.getTotal() {}",objStorageVolumes.getTotal() );
				
			}
			_log.info("THE MAPPINGS BEING RETURNED BY THE GETSTORAGEVOLS IS {}" , vvolToVvolsMap);
			task.setStatus(DriverTask.TaskStatus.READY);
		} catch (Exception e) {
			String msg = String.format(
					"3PARDriver: Unable to get storagevolumes for storage system %s native id %s; Error: %s.\n",
					storageSystem.getSystemName(), storageSystem.getNativeId(), e.getMessage());
			task.setMessage(msg);
			task.setStatus(DriverTask.TaskStatus.FAILED);
			e.printStackTrace();
		}
		return task;
	}

	/*
	 * Returns: Hashmap of volume to volumesets mapping The key of this hashmap
	 * will be the name of the volume The value of the hasmap returned will be
	 * an array list of volume sets that the volume belongs to. Example:
	 * {volume1: [volumeset5] , volume2:[volumeset1, volumeset2]}
	 */
	private HashMap<String, ArrayList<String>> generateVolumeSetToVolumeMap(StorageSystem storageSystem)
			throws Exception {
		// get Api client
		HP3PARApi hp3parApi = getHP3PARDeviceFromNativeId(storageSystem.getNativeId());
		ConsistencyGroupsListResult objConsisGroupSets = hp3parApi.getVVsetsList();
		HashMap<String, ArrayList<String>> volumeToVolumeSetMap = new HashMap<String, ArrayList<String>>();

		_log.info("3PARDriver: objConsisGroupSets.getTotal() information is {}", objConsisGroupSets.getTotal());
		for (Integer index = 0; index < objConsisGroupSets.getTotal(); index++) {
			ConsistencyGroupResult objConsisGroupResult = objConsisGroupSets.getMembers().get(index);

			if (objConsisGroupResult.getSetmembers() != null) {
				for (Integer volIndex = 0; volIndex < objConsisGroupResult.getSetmembers().size(); volIndex++) {
					String vVolName = objConsisGroupResult.getSetmembers().get(volIndex);
					if (!volumeToVolumeSetMap.containsKey(vVolName)) {
						ArrayList<String> volSetList = new ArrayList<String>();
						volSetList.add(objConsisGroupResult.getName());
						volumeToVolumeSetMap.put(vVolName, volSetList);
					} else {
						volumeToVolumeSetMap.get(vVolName).add(objConsisGroupResult.getName());
					}
				}
			}
		}

		_log.info("3PARDriver: volumeToVolumeSetMap information is {}", volumeToVolumeSetMap.toString());
		return volumeToVolumeSetMap;
	}

	/**
	 * Get storage port information and its properties
	 */
	@Override
	public DriverTask discoverStoragePorts(StorageSystem storageSystem, List<StoragePort> storagePorts) {
        //For this 3PAR system
        _log.info("3PARDriver: discoverStoragePorts information for storage system {}, nativeId {} - start",
                storageSystem.getIpAddress(), storageSystem.getNativeId());
        DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_DISCOVER_STORAGE_PORTS);

        try {
            // get Api client
            HP3PARApi hp3parApi = getHP3PARDeviceFromNativeId(storageSystem.getNativeId());

            // get storage port details
            PortCommandResult portResult = hp3parApi.getPortDetails();
            PortStatisticsCommandResult portStatResult = hp3parApi.getPortStatisticsDetail();

            // for each ViPR Storage port = 3PAR host port
            for (PortMembers currMember:portResult.getMembers()) {
                StoragePort port = new StoragePort();

                // Consider online target ports 
                if (currMember.getMode() != HP3PARConstants.MODE_TARGET ||
                        currMember.getLinkState() != HP3PARConstants.LINK_READY) {
                    continue;
                }
                
                if (currMember.getLabel() == null) {
                    String label = String.format("port:%s:%s:%s", currMember.getPortPos().getNode(),
                            currMember.getPortPos().getSlot(), currMember.getPortPos().getCardPort());
                    port.setPortName(label);
                } else {
                    port.setPortName(currMember.getLabel());
                    }
                
                port.setStorageSystemId(storageSystem.getNativeId());

                switch(currMember.getProtocol()) {
                    case 1:
                        port.setTransportType(TransportType.FC);
                        break;
                    case 3:
                        port.setTransportType(TransportType.Ethernet);
                        break;
                    case 2:
                        port.setTransportType(TransportType.IP);
                        break;
                    default:
                        _log.warn("3PARDriver: discoverStoragePorts Invalid port {}", port.getPortName());
                        break;
                }
                
                // loop for port speed as specific query is not supported
                for (PortStatMembers currStat:portStatResult.getMembers()) {

                    if (currMember.getPortPos().getNode() == currStat.getNode() && 
                            currMember.getPortPos().getSlot() == currStat.getSlot() && 
                            currMember.getPortPos().getCardPort() == currStat.getCardPort()) {
                        port.setPortSpeed(currStat.getSpeed() * HP3PARConstants.MEGA_BYTE);
                    }
                }

                // grouping with cluster node and slot
                port.setPortGroup(currMember.getPortPos().getNode().toString());
                port.setPortSubGroup(currMember.getPortPos().getSlot().toString());

                // set specific properties based on protocol
                if (port.getTransportType().equals(TransportType.FC.toString()) ||
                        port.getTransportType().equals(TransportType.Ethernet.toString())) {

                    port.setPortNetworkId(SanUtils.formatWWN(currMember.getPortWWN()));
                    // rest of the values
                    port.setEndPointID(port.getPortNetworkId());
                    port.setTcpPortNumber((long)0);
                } else {
                    port.setIpAddress(currMember.getIPAddr());
                    port.setPortNetworkId(currMember.getiSCSINmae());
                    port.setTcpPortNumber(currMember.getiSCSIPortInfo().getiSNSPort());
                    // rest of the values                    
                    port.setEndPointID(port.getPortNetworkId());
                }
               
                port.setAvgBandwidth(port.getPortSpeed());
                port.setPortHAZone(String.format("Group-%s", currMember.getPortPos().getNode()));
                
                String id = String.format("%s:%s:%s", currMember.getPortPos().getNode(),
                        currMember.getPortPos().getSlot(), currMember.getPortPos().getCardPort());
                // Storage object properties
                port.setNativeId(id);
                port.setDeviceLabel(port.getPortName());
                port.setDisplayName(port.getPortName());
                storageSystem.setAccessStatus(AccessStatus.READ_WRITE);
                port.setOperationalStatus(StoragePort.OperationalStatus.OK);  
                _log.info("3PARDriver: added storage port {}, native id {}",  port.getPortName(), port.getNativeId());
                storagePorts.add(port);
            } //for each storage port
                       
            storagePortMap.put(storageSystem.getNativeId() , storagePorts);
            
            task.setStatus(DriverTask.TaskStatus.READY);
            _log.info("3PARDriver: discoverStoragePorts information for storage system {}, nativeId {} - end",
                    storageSystem.getIpAddress(), storageSystem.getNativeId());
        } catch (Exception e) {
            String msg = String.format
                    ("3PARDriver: Unable to discover the storage port information for storage system %s native id %s; Error: %s.\n",
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
		_log.info("3PARDriver: discoverStorageHostComponents Running ");
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
                HP3PARApi hp3parApi = getHP3PARDeviceFromNativeId(volume.getStorageSystemId());

                // Create volume
                VolumeDetailsCommandResult volResult = null;
                hp3parApi.createVolume(volume.getDisplayName(), 
                        volume.getStoragePoolId(), 
                        volume.getThinlyProvisioned(), 
                        volume.getRequestedCapacity() / HP3PARConstants.MEGA_BYTE);
                volResult = hp3parApi.getVolumeDetails(volume.getDisplayName());
                
                // Attributes of the volume in array
                volume.setProvisionedCapacity(volResult.getSizeMiB() * HP3PARConstants.MEGA_BYTE);
                volume.setAllocatedCapacity(volResult.getSizeMiB() * HP3PARConstants.MEGA_BYTE);
                volume.setWwn(volResult.getWwn());
                volume.setNativeId(volume.getDisplayName()); //required for volume delete
                volume.setDeviceLabel(volume.getDisplayName());
                volume.setAccessStatus(AccessStatus.READ_WRITE);
                
                //Update Consistency Group
                String volumeCGName = volume.getConsistencyGroup();
                if (volumeCGName != null && !volumeCGName.isEmpty()) {
                	_log.info("3PARDriver:createVolumes Adding volume {} to consistency group {} ",volume.getDisplayName(),volumeCGName);
                	int addMember = 1;
                hp3parApi.updateVVset(volumeCGName,volume.getNativeId(),addMember);
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

            // get Api client
            HP3PARApi hp3parApi = getHP3PARDeviceFromNativeId(volume.getStorageSystemId());

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
                HP3PARApi hp3parApi = getHP3PARDeviceFromNativeId(volume.getStorageSystemId());

                
                //Remove from Consistency Group
                String volumeCGName = volume.getConsistencyGroup();
                if (volumeCGName != null && !volumeCGName.isEmpty()) {
                	_log.info("3PARDriver:deleteVolumes Removing volume {} from consistency group {} ",volume.getDisplayName(),volumeCGName);
                	int removeMember = 2;
                hp3parApi.updateVVset(volumeCGName,volume.getNativeId(),removeMember);
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
		_log.info("3PARDriver: getVolumeSnapshots Running ");
		List<VolumeSnapshot> snapshots = new ArrayList<>();

		try {
			_log.info("vvolToVvolsMap is {}" , vvolToVvolsMap.toString());
			
			HP3PARApi hp3parApi = getHP3PARDeviceFromNativeId(volume.getStorageSystemId());
			//VolumesCommandResult snapsResult = hp3parApi.getSnapshotsOfVolume(volume.getNativeId());
			
			HashMap<String, ArrayList<VolumeMember>> volMappings = null;
			ArrayList listOfChildVols = null;
			
			if( vvolToVvolsMap.containsKey(volume.getStorageSystemId())){
				volMappings = vvolToVvolsMap.get(volume.getStorageSystemId());
				listOfChildVols = volMappings.get(volume.getNativeId());
			}
			else{
				return snapshots;
			}

			_log.info("listOfChildVols.size()  is {}", listOfChildVols.size() );
			
			//for (int i = 0; i < snapsResult.getTotal(); i++) {
			for (int i = 0; i < listOfChildVols.size() ; i++) {
				//VolumeMember is the data structure used for representation of the HP3PAR virtual volume
				VolumeMember objSnapshot = (VolumeMember)listOfChildVols.get(i);				
				//VolumeSnapshot is the CoprHD southbound freamework's datastructure 
				VolumeSnapshot driverSnapshot = new VolumeSnapshot();
				
				driverSnapshot.setParentId(volume.getNativeId());
				driverSnapshot.setNativeId(objSnapshot.getName());
				driverSnapshot.setDeviceLabel(objSnapshot.getName());
				driverSnapshot.setStorageSystemId(volume.getStorageSystemId());
				driverSnapshot.setAccessStatus(StorageObject.AccessStatus.READ_ONLY);
				
				if(volume.getConsistencyGroup()!=null){
					driverSnapshot.setConsistencyGroup(volume.getConsistencyGroup());
				}
				
				driverSnapshot.setWwn(objSnapshot.getWwn());

				// TODO: We need to have more clarity on provisioned and
				// allocated sizes
				driverSnapshot.setAllocatedCapacity(objSnapshot.getSizeMiB() * HP3PARConstants.MEGA_BYTE);
				driverSnapshot.setProvisionedCapacity(objSnapshot.getSizeMiB() * HP3PARConstants.MEGA_BYTE);
				snapshots.add(driverSnapshot);

				/*
				 * if (GENERATE_EXPORT_DATA) { // generate export data for this
				 * snap --- the same export data as for its parent volume
				 * generateExportDataForVolumeReplica(volume, snapshot); }
				 */
			}
			return snapshots;
		} catch (Exception e) {
			String msg = String.format(
					"3PARDriver: Unable to get snapshot of volume with storage system %s and volume native id %s; Error: %s.\n",
					volume.getStorageSystemId(), volume.getNativeId(), e.getMessage());
			e.printStackTrace();
		}

		return null;
    }

    /**
     * Identifying clones of the given parent base volume.
     * NOTE: Intermediate physical copies of 3PAR generated from other snapshots/clone are shown as clone of base volume itself
     * Need to check this behavior ?
     */
    @Override
	public List<VolumeClone> getVolumeClones(StorageVolume volume) {
		_log.info("3PARDriver: getVolumeClones Running ");
		List<VolumeClone> clones = new ArrayList<>();

		try {
			_log.info("vvolToVvolsMap is {}", vvolToVvolsMap.toString());

			HP3PARApi hp3parApi = getHP3PARDeviceFromNativeId(volume.getStorageSystemId());
			// VolumesCommandResult snapsResult =
			// hp3parApi.getClonesOfVolume(volume.getNativeId());

			HashMap<String, ArrayList<VolumeMember>> volMappings = null;
			ArrayList listOfChildVols = null;

			if (vvolToVvolsMap.containsKey(volume.getStorageSystemId())) {
				volMappings = vvolToVvolsMap.get(volume.getStorageSystemId());
				listOfChildVols = volMappings.get(volume.getNativeId());
			} else {
				return clones;
			}

			_log.info("listOfChildVols.size()  is {}", listOfChildVols.size());

			// for (int i = 0; i < snapsResult.getTotal(); i++) {
			for (int i = 0; i < listOfChildVols.size(); i++) {
				// VolumeMember is the data structure used for representation of
				// the HP3PAR virtual volume
				VolumeMember objClone = (VolumeMember) listOfChildVols.get(i);
				if (objClone.getCopyType() == copyType.PHYSICAL_COPY.getValue()) {
					// VolumeClone is the CoprHD southbound freamework's data
					// structure
					VolumeClone driverClone = new VolumeClone();

					driverClone.setParentId(volume.getNativeId());
					driverClone.setNativeId(objClone.getName());
					driverClone.setDeviceLabel(objClone.getName());
					driverClone.setStorageSystemId(volume.getStorageSystemId());
					driverClone.setStoragePoolId(volume.getStoragePoolId());
					driverClone.setAccessStatus(StorageObject.AccessStatus.READ_ONLY);

					if (volume.getConsistencyGroup() != null) {
						driverClone.setConsistencyGroup(volume.getConsistencyGroup());
					}

					driverClone.setWwn(objClone.getWwn());
					driverClone.setThinlyProvisioned(volume.getThinlyProvisioned());

					// TODO: We need to have more clarity on provisioned and
					// allocated sizes
					driverClone.setAllocatedCapacity(objClone.getSizeMiB() * HP3PARConstants.MEGA_BYTE);
					driverClone.setProvisionedCapacity(objClone.getSizeMiB() * HP3PARConstants.MEGA_BYTE);
					driverClone.setReplicationState(VolumeClone.ReplicationState.SYNCHRONIZED);
					clones.add(driverClone);

					/*
					 * Attached clones in HP3PAR cannot be exported, and
					 * detached clone will not be shown as physical copy, it
					 * becomes a base volume. 
					 * 
					 * if (GENERATE_EXPORT_DATA) { //generate export data for this clone --- the same export
					 * 								data as for its parent volume
					 * generateExportDataForVolumeReplica(volume, clone); }
					 */
				}
			}
			return clones;
		} catch (Exception e) {
			String msg = String.format(
					"3PARDriver: Unable to get clone of volume with storage system %s and volume native id %s; Error: %s.\n",
					volume.getStorageSystemId(), volume.getNativeId(), e.getMessage());
			e.printStackTrace();
		}

		return null;
	}

    @Override
    public List<VolumeMirror> getVolumeMirrors(StorageVolume volume) {
    	_log.info("3PARDriver: getVolumeMirrors Running ");
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

    	for (VolumeSnapshot snap : snapshots) {
            try {
            	//native id = null , 
                _log.info("3PARDriver: createVolumeSnapshot for storage system native id {}, volume name {} - start",
                		snap.toString(), snap.getDisplayName());  
                Boolean readOnly = true;
                
                // get Api client
                HP3PARApi hp3parApi = getHP3PARDeviceFromNativeId(snap.getStorageSystemId());


                VolumeDetailsCommandResult volResult = null;
                if (snap.getAccessStatus() != AccessStatus.READ_ONLY) {
                	readOnly = false;
                }
                // Create volume snapshot
                hp3parApi.createVirtualCopy(snap.getParentId(),snap.getDisplayName(),readOnly);
                volResult = hp3parApi.getVolumeDetails(snap.getDisplayName());
                                
                // Actual size of the volume in array
                //snap.setProvisionedCapacity(volResult.getSizeMiB() * HP3PARConstants.MEGA_BYTE);
                snap.setWwn(volResult.getWwn());
                snap.setNativeId(snap.getDisplayName()); //required for volume delete
                snap.setDeviceLabel(snap.getDisplayName());
                snap.setAccessStatus(snap.getAccessStatus());

                task.setStatus(DriverTask.TaskStatus.READY);
                _log.info("createVolumeSnapshot for storage system native id {}, volume name {} - end",
                		snap.getStorageSystemId(), snap.getDisplayName());            
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

    /**
     * Promote Virtual Copy is HP3PAR term for restore Snapshot.
     * First offline restore then online restore will be tried. 
     */
    @Override
    public DriverTask restoreSnapshot(List<VolumeSnapshot> snapshots) {

	    DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_RESTORE_SNAPSHOT_VOLUMES);

        // Executing restore for each requested volume snapshot (in one or more 3par system)
        for (VolumeSnapshot snap : snapshots) {
            try {
                _log.info("3PARDriver: restoreSnapshot for storage system system id {}, volume name {} , native id {} , all = {} - start",
                		snap.getStorageSystemId(), snap.getDisplayName(), snap.getNativeId(), snap.toString());     

                // get Api client
                HP3PARApi hp3parApi = getHP3PARDeviceFromNativeId(snap.getStorageSystemId());

                // restore virtual copy
                hp3parApi.restoreVirtualCopy(snap.getNativeId());
                
                task.setStatus(DriverTask.TaskStatus.READY);
                _log.info("3PARDriver: restoreSnapshot for storage system  id {}, volume snap display name {} - end",
                		snap.getStorageSystemId(), snap.getDisplayName());            
            } catch (Exception e) {
                String msg = String.format(
                        "3PARDriver: Unable to restore volume display name %s with native id %s for storage system id %s; Error: %s.\n",
                        snap.getDisplayName(), snap.getNativeId(), snap.getStorageSystemId(), e.getMessage());
                _log.error(msg);
                task.setMessage(msg);
                task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
                e.printStackTrace();
            }
        } // end for each restore snapshot
        
        return task;
    }

    @Override
    public DriverTask deleteVolumeSnapshot(List<VolumeSnapshot> snapshots) {

	    DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_DELETE_SNAPSHOT_VOLUMES);

        // For each requested volume snapshot (in one or more 3par system)
        for (VolumeSnapshot snap : snapshots) {
            try {
                _log.info("3PARDriver: deleteVolumeSnapshot for storage system native id {}, volume name {} , native id {} - start",
                		snap.getStorageSystemId(), snap.getDisplayName(), snap.getNativeId());     

                // get Api client
                HP3PARApi hp3parApi = getHP3PARDeviceFromNativeId(snap.getStorageSystemId());

                // Delete virtual copy
                hp3parApi.deleteVirtualCopy(snap.getNativeId());
                
                task.setStatus(DriverTask.TaskStatus.READY);
                _log.info("3PARDriver: deleteVolumeSnapshot for storage system native id {}, volume name {} - end",
                		snap.getStorageSystemId(), snap.getDisplayName());            
            } catch (Exception e) {
                String msg = String.format(
                        "3PARDriver: Unable to delete volume name %s with native id %s for storage system native id %s; Error: %s.\n",
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
     * Physical Copy is a 3PAR term for Volume clone
     * 
     * Vipr UI doesn't provide offline/online options while creating clone, so below logic will be followed
     * First, create clone creation as offline volume [Attached volume]
     *   if error, create a new volume with clone name by using its base volume parameters like TPVV,CPG.
     *        Then create a offline volume clone using newly created volume clone 
     *        Note: A offline clone creates a attached clone, which actually creates a intermediate snapshot 
     *              which can be utilized for restore/update.
     */
    
    @Override
    public DriverTask createVolumeClone(List<VolumeClone> clones, StorageCapabilities capabilities) {

    	DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_CREATE_CLONE_VOLUMES);

    	for (VolumeClone clone : clones) {
            try {
            	//native id = null , 
                _log.info("3PARDriver: createVolumeClone for storage system native id {}, volume name {} , volume clone name {} - start",
                		clone.toString(), clone.getParentId(), clone.getDisplayName() );  
             // get Api client
                HP3PARApi hp3parApi = getHP3PARDeviceFromNativeId(clone.getStorageSystemId());
                VolumeDetailsCommandResult volResult = null;
                
                // Create volume clone
                hp3parApi.createPhysicalCopy(clone.getParentId(),clone.getDisplayName(),clone.getStoragePoolId());
                volResult = hp3parApi.getVolumeDetails(clone.getDisplayName());
                                
                // Actual size of the volume in array
                clone.setProvisionedCapacity(volResult.getSizeMiB() * HP3PARConstants.MEGA_BYTE);
                clone.setWwn(volResult.getWwn());
                clone.setNativeId(clone.getDisplayName()); //required for volume delete
                clone.setDeviceLabel(clone.getDisplayName());
                clone.setAccessStatus(clone.getAccessStatus());
                clone.setReplicationState(VolumeClone.ReplicationState.SYNCHRONIZED);

                task.setStatus(DriverTask.TaskStatus.READY);
                _log.info("createVolumeClone for storage system native id {}, volume name {} - end",
                		clone.getStorageSystemId(), clone.getDisplayName());            
            } catch (Exception e) {
                String msg = String.format(
                        "3PARDriver: createVolumeClone Unable to create volume clone name %s for parent base volume id %s whose storage system native id is %s; Error: %s.\n",
                        clone.getDisplayName(), clone.getParentId(), clone.getStorageSystemId(), e.getMessage());
                _log.info("createVolumeClone exception message {} ",e.getMessage());
                _log.error(msg);
                task.setMessage(msg);
                task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
                e.printStackTrace();
            }
        } // end for each volume clone creation
        
        return task;
    
    }

    @Override
    public DriverTask detachVolumeClone(List<VolumeClone> clones) {
    	// There is no REST API available for detach clone in HP3PAR
    	// This is getting called while delete clone, hence setting this as working by default
    	_log.info("3PARDriver: detachVolumeClone Running ");
    	DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_RESTORE_CLONE_VOLUMES);
    	task.setStatus(DriverTask.TaskStatus.READY);
    	return task;
    }

    /**
     * restore clone or restore physical copy
     * Intermediate snapshot will be used for restore, this got generated during clone creation
     * NOTE: intermediate snapshot cannot be exported hence offline restore will be used
     */
    @Override
    public DriverTask restoreFromClone(List<VolumeClone> clones) {

	    DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_RESTORE_CLONE_VOLUMES);

        // Executing restore for each requested volume clone (in one or more 3par system)
        for (VolumeClone clone : clones) {
            try {
                _log.info("3PARDriver: restoreFromClone for storage system system id {}, volume name {} , native id {} , all = {} - start",
                		clone.getStorageSystemId(), clone.getDisplayName(), clone.getNativeId(), clone.toString());     

                // get Api client
                HP3PARApi hp3parApi = getHP3PARDeviceFromNativeId(clone.getStorageSystemId());

                // restore virtual copy
                hp3parApi.restorePhysicalCopy(clone.getNativeId());
                
                clone.setReplicationState(VolumeClone.ReplicationState.RESTORED);
                
                task.setStatus(DriverTask.TaskStatus.READY);
                _log.info("3PARDriver: restoreFromClone successful for storage system  id {}, volume clone native id {} - end",
                		clone.getStorageSystemId(), clone.getNativeId());            
            } catch (Exception e) {
                String msg = String.format(
                        "3PARDriver:restoreFromClone Unable to restore volume display name %s with native id %s for storage system id %s; Error: %s.\n",
                        clone.getDisplayName(), clone.getNativeId(), clone.getStorageSystemId(), e.getMessage());
                _log.error(msg);
                task.setMessage(msg);
                task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
                e.printStackTrace();
            }
        } // end for each restore clone
        
        return task;
    }

    @Override
    public DriverTask deleteVolumeClone(List<VolumeClone> clones) {

	    DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_DELETE_CLONE_VOLUMES);

        // For each requested volume snapshot (in one or more 3par system)
        for (VolumeClone clone : clones) {
            try {
                _log.info("3PARDriver: deleteVolumeSnapshot for storage system native id {}, volume name {} , native id {} - start",
                		clone.getStorageSystemId(), clone.getDisplayName(), clone.getNativeId());     

                // get Api client
                HP3PARApi hp3parApi = getHP3PARDeviceFromNativeId(clone.getStorageSystemId());

                // Delete physical copy
                hp3parApi.deletePhysicalCopy(clone.getNativeId());
                
                task.setStatus(DriverTask.TaskStatus.READY);
                _log.info("3PARDriver: deleteVolumecloneshot for storage system native id {}, volume name {} - end",
                		clone.getStorageSystemId(), clone.getDisplayName());            
            } catch (Exception e) {
                String msg = String.format(
                        "3PARDriver: Unable to delete volume name %s with native id %s for storage system native id %s; Error: %s.\n",
                        clone.getDisplayName(), clone.getNativeId(), clone.getStorageSystemId(), e.getMessage());
                _log.error(msg);
                task.setMessage(msg);
                task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
                e.printStackTrace();
            }
        } // end for each delete clone
        
        return task;
    }

    @Override
    public DriverTask createVolumeMirror(List<VolumeMirror> mirrors, StorageCapabilities capabilities) {
    	_log.info("3PARDriver: createVolumeMirror Running ");
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask createConsistencyGroupMirror(VolumeConsistencyGroup consistencyGroup, List<VolumeMirror> mirrors,
            List<CapabilityInstance> capabilities) {
    	_log.info("3PARDriver: createConsistencyGroupMirror Running ");
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask deleteVolumeMirror(List<VolumeMirror> mirrors) {
    	_log.info("3PARDriver: deleteVolumeMirror Running ");
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask deleteConsistencyGroupMirror(List<VolumeMirror> mirrors) {
    	_log.info("3PARDriver: deleteConsistencyGroupMirror Running ");
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask splitVolumeMirror(List<VolumeMirror> mirrors) {
    	_log.info("3PARDriver: splitVolumeMirror Running ");
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask resumeVolumeMirror(List<VolumeMirror> mirrors) {
    	_log.info("3PARDriver: resumeVolumeMirror Running ");
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DriverTask restoreVolumeMirror(List<VolumeMirror> mirrors) {
    	_log.info("3PARDriver: restoreVolumeMirror Running ");
        // TODO Auto-generated method stub
        return null;
    }

    /*
     * This function should return a HashMap.
     * Key of HashMap : HostName to which the volume is exported
     * Value of the HashMap : HostExportInfo associated with export to HostName
     */
    @Override
    public Map<String, HostExportInfo> getVolumeExportInfoForHosts(StorageVolume volume) {
    	_log.info("3PARDriver: getVolumeExportInfoForHosts Running ");       
    	_log.info("volume.getdisplay name is {}",volume.getNativeId());
		_log.info("volume.getstoragesysid  is {}",volume.getStorageSystemId());		
		return getBlockObjectExportInfoForHosts(volume.getStorageSystemId(), volume.getWwn() , volume.getNativeId(), volume);
    }
    

    @Override
    public Map<String, HostExportInfo> getSnapshotExportInfoForHosts(VolumeSnapshot snapshot) {
    	_log.info("3PARDriver: getSnapshotExportInfoForHosts Running ");       
    	_log.info("volume.getdisplay name is {}",snapshot.getNativeId());
		_log.info("volume.getstoragesysid  is {}",snapshot.getStorageSystemId());		
		return getBlockObjectExportInfoForHosts(snapshot.getStorageSystemId(), snapshot.getWwn() , snapshot.getNativeId(), snapshot);
    }

    @Override
    public Map<String, HostExportInfo> getCloneExportInfoForHosts(VolumeClone clone) {
    	_log.info("3PARDriver: getCloneExportInfoForHosts Running ");
    	_log.info("volume.getdisplay name is {}",clone.getNativeId());
		_log.info("volume.getstoragesysid  is {}",clone.getStorageSystemId());		
		return getBlockObjectExportInfoForHosts(clone.getStorageSystemId(), clone.getWwn() , clone.getNativeId(), clone);
    }
    
    
    public Map<String, HostExportInfo> getBlockObjectExportInfoForHosts(String storageSystemId , String wwn,  
    																	String objectName , StorageBlockObject object) {
    	try{    		    		    		   
    		Map<String, HostExportInfo> resultMap = new HashMap<String, HostExportInfo>();
    		
    		//get the vlun associated with the volume at consideration.
    		HP3PARApi hp3parApi = getHP3PARDeviceFromNativeId(storageSystemId);
    		VirtualLunsList vlunsOfVolume = hp3parApi.getVLunsOfVolume(wwn);
    		    		
    		//process the vlun information by iterating through the vluns
    		//and then for each vlun, we create the appropriate key:value pair
    		//in the resultMap with hostname:HostExportInfo information.    		
    		for( int index=0; index < vlunsOfVolume.getTotal() ; index++ ){    			    		    	
    			_log.info("after virtual lun init");
				VirtualLun objVirtualLun = vlunsOfVolume.getMembers().get(index);
				_log.info("objVirtualLun.getHostName {}", objVirtualLun.getHostname());
				_log.info("objVirtualLun.getPortPos {}", objVirtualLun.getPortPos());
				_log.info("objVirtualLun.getRemoteName {}", objVirtualLun.getRemoteName());
				_log.info("objVirtualLun.getVolumeWWN {}", objVirtualLun.getVolumeWWN());
				_log.info("objVirtualLun.getVolumeName {}", objVirtualLun.getVolumeName());
				_log.info("objVirtualLun.getType {}", objVirtualLun.getType());
								    		
				if(!objVirtualLun.isActive()){
    				continue;				
    			}
    			
    			List<String> volumeIds = new ArrayList<>();
    			List<Initiator> initiators = new ArrayList<Initiator>();
    			List<StoragePort> storageports = new ArrayList<>();
    			
    			//To volumeIds we need to add the native id of volume 
    			//and for hp3par volume name would be the native id
		        volumeIds.add(objVirtualLun.getVolumeName());
	        
		        Initiator hostInitiator = new Initiator();
	        	//hp3par returns remote name in the format like 10000000C98F5C79. 
    	        //we now convert this to the format 10:00:00:00:C9:8F:5C:79
		        String portId = objVirtualLun.getRemoteName().substring(0, 2) + ":" + 
	        				objVirtualLun.getRemoteName().substring(2, 4) + ":" +
	        				objVirtualLun.getRemoteName().substring(4, 6) + ":" +
	        				objVirtualLun.getRemoteName().substring(6, 8) + ":" +
	        				objVirtualLun.getRemoteName().substring(8, 10) + ":" +
	        				objVirtualLun.getRemoteName().substring(10, 12) + ":" +
	        				objVirtualLun.getRemoteName().substring(12, 14) + ":" +
	        				objVirtualLun.getRemoteName().substring(14, 16);
    	        
		        _log.info("before native id");
		        String nativeId = String.format("%s:%s:%s", objVirtualLun.getPortPos().getNode(),
    	        		objVirtualLun.getPortPos().getSlot(), objVirtualLun.getPortPos().getCardPort());
        
	        	//Check which of the storage ports discovered, matches the node:portpos:cardport 
    	        //combination of the VLUN
		        List<StoragePort> storPortsOfStorage = storagePortMap.get(storageSystemId);    	        
				_log.info("storPortsOfStorage are {}",storPortsOfStorage);
				_log.info("storPortMap are {}",storagePortMap);

	        	for(int portIndex = 0 ; portIndex < storPortsOfStorage.size() ; portIndex++){
	        		StoragePort port = storPortsOfStorage.get(portIndex);
					_log.info("native id is {}" , nativeId);
					_log.info("port.getNativeId() is {} " , port.getNativeId());

	        		if(port.getNativeId().equals(nativeId)){
	        			storageports.add(port);
	        		}    	        	
	        	}
	        
	        	hostInitiator.setHostName(objVirtualLun.getHostname());    	        
	        	hostInitiator.setPort(portId);
	        	initiators.add(hostInitiator);

    	        HostExportInfo exportInfo = null;
    	        
		        if(resultMap.containsKey(objVirtualLun.getHostname())){
    	        	exportInfo = resultMap.get(objVirtualLun.getHostname());	
					for(int i1 = 0; i1 < storageports.size() ; i1++)
					{
						StoragePort ob1 = storageports.get(i1);
						if(!exportInfo.getTargets().contains(ob1)){
							exportInfo.getTargets().add(ob1);
						}
					}
					for(int i1 = 0; i1 < initiators.size() ; i1++)
					{
						Initiator ob1 = initiators.get(i1);
						if(!exportInfo.getInitiators().contains(ob1)){
							exportInfo.getInitiators().add(ob1);
						}
					}    	       
    		    }
	    	    else{
    	        	exportInfo = new HostExportInfo(objVirtualLun.getHostname(), volumeIds, initiators, storageports);
    	        }
    			    	        
    			resultMap.put(objVirtualLun.getHostname(), exportInfo);
    			_log.info("RESULTMAP FROM GETVOLUMEEXPORTINFO {}",resultMap);
    		}    		
    		return resultMap;
    	}
    	catch (Exception e) {
			String msg = String.format(
					"3PARDriver: Unable to get export info of the storage objet %s in storage system native id is %s; Error: %s.\n",
					objectName, storageSystemId, e.getMessage());
			_log.error(msg);			
			e.printStackTrace();
		}
                    	
        return null;
    }

    @Override
    public Map<String, HostExportInfo> getMirrorExportInfoForHosts(VolumeMirror mirror) {
    	_log.info("3PARDriver: getMirrorExportInfoForHosts Running ");
        // TODO Auto-generated method stub
        return null;
    }

    String get3parHostname(List<Initiator> initiators, String storageId) throws Exception{
        //Since query works this implementation can be changed
        String hp3parHost = null;
        _log.info("3PARDriver: get3parHostname enter");
        
        try {
            HP3PARApi hp3parApi = getHP3PARDeviceFromNativeId(storageId);
            HostCommandResult hostRes = hp3parApi.getAllHostDetails();

            Complete:
                // for each host in 3par
                for (int iHst = 0; iHst < hostRes.getTotal(); iHst++) {
                    HostMember hostMemb = hostRes.getMembers().get(iHst);
                    // for each host initiator sent
                    for (Initiator init:initiators) {

                        // Is initiator FC
                        if (init.getProtocol().toString().compareToIgnoreCase(Protocols.FC.toString()) == 0 ) {
                            // verify in all FC ports with host 
                            for (int kFc = 0; kFc < hostMemb.getFCPaths().size(); kFc++) {
                                FcPath fcPath = hostMemb.getFCPaths().get(kFc);
                                if (SanUtils.formatWWN(fcPath.getWwn()).compareToIgnoreCase(init.getPort()) == 0) {
                                    hp3parHost = hostMemb.getName();
                                    _log.info("3PARDriver: get3parHostname initiator {} host {}", init.getPort(), hp3parHost);
                                    break Complete;
                                }
                            }
                        } else {
                            // verify in all iSCSI ports with host 
                            for (int kSc = 0; kSc < hostMemb.getiSCSIPaths().size(); kSc++) {
                                ISCSIPath scsiPath = hostMemb.getiSCSIPaths().get(kSc);
                                if (scsiPath.getName().compareToIgnoreCase(init.getPort()) == 0) {   
                                    hp3parHost = hostMemb.getName();
                                    _log.info("3PARDriver: get3parHostname initiator {} host {}", init.getPort(), hp3parHost);
                                    break Complete;
                                }
                            }

                        } // if FC or iSCSI
                    }//each initiator
                } // each host
            _log.info("3PARDriver: get3parHostname leave");
            return hp3parHost;
        } catch (Exception e) {
            _log.error("3PARDriver:get3parHostname could not get 3par registered host name");
            return null;
        }
    }

    /*      
     *******USE CASES**********
      
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
    
    @Override
    public DriverTask exportVolumesToInitiators(List<Initiator> initiators, List<StorageVolume> volumes, Map<String, String> volumeToHLUMap,
            List<StoragePort> recommendedPorts, List<StoragePort> availablePorts, StorageCapabilities capabilities,
            MutableBoolean usedRecommendedPorts, List<StoragePort> selectedPorts) {
        DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_EXPORT_STORAGE_VOLUMES);
        _log.info("3PARDriver:exportVolumesToInitiators enter");

        String host = null;

        for (StorageVolume vol:volumes) {
            //If required host should get created in all arrays to which volume belongs
            String hostArray = null;
            String clustArray = null;
            try {
                // all initiators belong to same host
                initiators.get(0).setInitiatorType(Type.Host); //TEMP CODE for Cluster unit testing
                if (initiators.get(0).getInitiatorType().equals(Type.Host) == true) {
                    // Exclusive-Host export 
                    // Some code is repeated with cluster for simplicity
                    hostArray = get3parHostname(initiators, vol.getStorageSystemId());
                    if (hostArray == null) {
                        // create a new host or add initiator to existing host
                        HP3PARApi hp3parApi = getHP3PARDeviceFromNativeId(vol.getStorageSystemId());

                        ArrayList<String> portIds = new ArrayList<>();
                        for (Initiator init:initiators) {
                            portIds.add(init.getPort());
                        }

                        Integer persona = 0;
                        //Supporting from lower versions; Windows1, HPUX7, Linux1, Esx11, AIX8, AIXVIO8, SUNVCS1, No_OS6, Other6
                        switch (initiators.get(0).getHostOsType()) {
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

                                //persona 3 is by experimentation, doc is not up-to-date 
                            case No_OS:
                            case Other:
                            default:
                                persona = 3;
                                break;
                        }

                        hp3parApi.createHost(initiators.get(0).getHostName(), portIds, persona);
                        host = initiators.get(0).getHostName();
                    } else {
                        host = hostArray;
                    }
                    // Host available

                    //****TEMP CODE ****************/
                } else if (initiators.get(0).getInitiatorType().equals(Type.RP) == true) {
                    /*else if (initiators.get(0).getInitiatorType().equals(Type.Cluster) == true) {*/
                    // Shared-Cluster export
                    clustArray = initiators.get(0).getClusterName();
                    HP3PARApi hp3parApi = getHP3PARDeviceFromNativeId(vol.getStorageSystemId());

                    //Check if host exists, otherwise create
                    hostArray = get3parHostname(initiators, vol.getStorageSystemId());
                    if (hostArray == null) {
                        // create a new host or add initiator to existing host
                        ArrayList<String> portIds = new ArrayList<>();
                        for (Initiator init:initiators) {
                            portIds.add(init.getPort());
                        }

                        Integer persona = 0;
                        //Supporting from lower versions; Windows1, HPUX7, Linux1, Esx11, AIX8, AIXVIO8, SUNVCS1, No_OS6, Other6
                        switch (initiators.get(0).getHostOsType()) {
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

                                //persona 3 is by experimentation, doc is not up-to-date 
                            case No_OS:
                            case Other:
                            default:
                                persona = 3;
                                break;
                        }

                        hp3parApi.createHost(initiators.get(0).getHostName(), portIds, persona);
                        hostArray = initiators.get(0).getHostName();
                    }
                    
                    // only one thread should create cluster
                    synchronized (this) {
                        //Check if cluster exists, otherwise create
                        HostSetDetailsCommandResult hostsetRes = hp3parApi.getHostSetDetails(clustArray);
                        if (hostsetRes == null) {
                            hp3parApi.createHostSet(clustArray, initiators.get(0).getHostName());
                        } else {
                            //if this host is not part of the cluster add it
                            boolean present = false;
                            for (int index = 0; index < hostsetRes.getSetmembers().size(); index++) {
                                if (hostArray.compareTo(hostsetRes.getSetmembers().get(index)) == 0) {
                                    present = true;
                                    break;
                                }
                            }
                            
                            if (present == false) {
                                // update cluster with this host
                                hp3parApi.updateHostSet(clustArray, hostArray);
                            }
                        }
                        
                        // Cluster available
                        host = "set:" + clustArray;
                    } //end synchronized
                    
                } else {
                    _log.error("3PARDriver:exportVolumesToInitiators error: Host/Cluster type not supported");
                    throw new HP3PARException("3PARDriver:exportVolumesToInitiators error: Host/Cluster type not supported");
                }
            } catch (Exception e) {
                String msg = String.format("3PARDriver: Unable to export, error: %s", e);
                _log.error(msg);
                _log.error(CompleteError.getStackTrace(e));
                task.setMessage(msg);
                task.setStatus(DriverTask.TaskStatus.FAILED);
                e.printStackTrace();
                return task;
            }
        } // for each volume

        
        /* 
         * Export will be done keeping volumes as the starting point
         */
        Integer totalExport = recommendedPorts.size();
        for (StorageVolume vol:volumes) {
            Integer currExport = 0;
            Integer hlu = Integer.parseInt(volumeToHLUMap.get(vol.getNativeId()));

            try {
                // volume could belong to different storage system; get specific api client; 
                HP3PARApi hp3parApi = getHP3PARDeviceFromNativeId(vol.getStorageSystemId());

                /*
                 * export for INDIVIDUAL HOST=exclusive
                 * Some code is repeated with cluster for simplicity
                 */
                if (host.startsWith("set:") == false) {
                    // try with recommended ports
                    for (StoragePort port : recommendedPorts) {
                        // verify volume and port belong to same storage
                        if (vol.getStorageSystemId().equalsIgnoreCase(port.getStorageSystemId()) == false) {
                            continue;
                        }

                        String message = String.format("3PARDriver:exportVolumesToInitiators using recommendedPorts for "
                                + "storage system %s, volume %s host %s hlu %s port %s", 
                                port.getStorageSystemId(), vol.getNativeId(), host, hlu.toString(), port.getNativeId());
                        _log.info(message);

                        VlunResult vlunRes = hp3parApi.createVlun(vol.getNativeId(), hlu, host, port.getNativeId());
                        if (vlunRes != null && vlunRes.getStatus() == true) {
                            currExport++;
                            usedRecommendedPorts.setValue(true);
                            // update hlu obtained as lun from 3apr & add the selected port if required
                            volumeToHLUMap.put(vol.getNativeId(), vlunRes.getAssignedLun());
                            if (selectedPorts.contains(port) == false) {
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
                        if (vol.getStorageSystemId().equalsIgnoreCase(port.getStorageSystemId()) == false) {
                            continue;
                        }

                        String message = String.format("3PARDriver:exportVolumesToInitiators using availablePorts for "
                                + "storage system %s, volume %s host %s hlu %s port %s", 
                                port.getStorageSystemId(), vol.getNativeId(), host, hlu.toString(), port.getNativeId());
                        _log.info(message);

                        VlunResult vlunRes = hp3parApi.createVlun(vol.getNativeId(), hlu, host, port.getNativeId());
                        if (vlunRes != null && vlunRes.getStatus() == true) {
                            currExport++;
                            usedRecommendedPorts.setValue(false);
                            // update hlu obtained as lun from 3apr & add the selected port if required
                            volumeToHLUMap.put(vol.getNativeId(), vlunRes.getAssignedLun());
                            if (selectedPorts.contains(port) == false) {
                                selectedPorts.add(port);
                            }
                        } else {
                            task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
                            _log.warn("3PARDriver: Could not export " + message);
                        }
                    } // for available ports
                } else {
                    /*
                     * export for CLUSTER=shared
                     * Some code is repeated with cluster for simplicity
                     * 
                     * Cluster export will be done as host-set in 3APR for entire cluster in one go
                     * Hence requests coming for rest of the individual host exports should gracefully exit
                     */

                    synchronized (this) {
                        /* 
                         * If this is the first request key gets created with export operation.
                         * other requests will gracefully exit.
                         * key will be removed in unexport.
                         */

                        String message = String.format("3PARDriver:exportVolumesToInitiators "
                                + "storage system %s, volume %s Cluster %s hlu %s ", 
                                vol.getStorageSystemId(), vol.getNativeId(), host, hlu.toString());
                        _log.info(message);

                        String exportPath = vol.getStorageSystemId() + vol.getNativeId() + host;
                        Map<String, List<String>> attributes = new HashMap<>();
                        List<String> expValue = new ArrayList<>();
                        List<String> lunValue = new ArrayList<>();
                        boolean doExport = true;

                        attributes = this.driverRegistry.getDriverAttributesForKey(HP3PARConstants.DRIVER_NAME, exportPath);
                        
                        if (attributes != null) {
                            expValue = attributes.get("EXPORT_PATH");
                            if (expValue != null && expValue.get(0).compareTo(exportPath) == 0) {
                                doExport = false;
                                // Already exported, make hlu, port details; gracefully exit
                                lunValue = attributes.get(vol.getNativeId());
                                volumeToHLUMap.put(vol.getNativeId(), lunValue.get(0));
                                
                                String hstArray = get3parHostname(initiators, vol.getStorageSystemId());
                                HostMember hostRes = hp3parApi.getHostDetails(hstArray);
                                
                                //get storage array ports for this host ports
                                List<StoragePort> clusterStoragePorts = new ArrayList<>();
                                getCluseterStoragePorts(hostRes, availablePorts, vol.getStorageSystemId(), clusterStoragePorts);

                                for (StoragePort sp:clusterStoragePorts) {
                                    // assign all these ports as selected ports
                                    if (selectedPorts.contains(sp) == false) {
                                        selectedPorts.add(sp);
                                    }
                                }
                                
                                // go thru all slectedports. if anyone is not part of the recommendedPorts
                                // set usedRecommendedPorts to false
                                usedRecommendedPorts.setValue(true);

                                for (StoragePort sp:selectedPorts) {
                                    if (recommendedPorts.contains(sp) == false) {
                                        usedRecommendedPorts.setValue(false);
                                        break;
                                    } 
                                }
                                
                                task.setStatus(DriverTask.TaskStatus.READY);
                                _log.info("3PARDriver: Already exported, exiting" + message);
                            }
                        }
                        
                        if (doExport == true) {
                            /*
                             * export volume; for cluster use host set method to export
                             * We cannot specify port; determine the individual host ports used 
                             */
                            VlunResult vlunRes = hp3parApi.createVlun(vol.getNativeId(), hlu, host, null);
                            if (vlunRes != null && vlunRes.getStatus() == true) {

                                // update hlu obtained as lun from 3apr & add the selected port if required
                                volumeToHLUMap.put(vol.getNativeId(), vlunRes.getAssignedLun());

                                String hstArray = get3parHostname(initiators, vol.getStorageSystemId());
                                HostMember hostRes = hp3parApi.getHostDetails(hstArray);
                                
                                //get storage array ports for this host ports
                                List<StoragePort> clusterStoragePorts = new ArrayList<>();
                                getCluseterStoragePorts(hostRes, availablePorts, vol.getStorageSystemId(), clusterStoragePorts);

                                for (StoragePort sp:clusterStoragePorts) {
                                    // assign all these ports as selected ports
                                    if (selectedPorts.contains(sp) == false) {
                                        selectedPorts.add(sp);
                                    }
                                }

                                // go thru all slectedports. if anyone is not part of the recommendedPorts
                                // set usedRecommendedPorts to false
                                usedRecommendedPorts.setValue(true);

                                for (StoragePort sp:selectedPorts) {
                                    if (recommendedPorts.contains(sp) == false) {
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
                                this.driverRegistry.setDriverAttributesForKey(HP3PARConstants.DRIVER_NAME, exportPath, attributes);
                                
                                task.setStatus(DriverTask.TaskStatus.READY);

                            } else { //end createVlun
                                task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
                                _log.warn("3PARDriver: Could not export " + message);
                            }
                        } // doExport == true
                        
                    } //end synchronized
                }//end cluster export                

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
        
    private void getCluseterStoragePorts(HostMember hostRes, List<StoragePort> arrayPorts, 
            String volStorageSystemId, List <StoragePort> clusterPorts) {

        for(StoragePort sp:arrayPorts) {
            if (volStorageSystemId.compareToIgnoreCase(sp.getStorageSystemId()) != 0) {
                continue;
            }
            
            String[] pos = sp.getNativeId().split(":");
            
            for (FcPath fc:hostRes.getFCPaths()) {

                if (fc.getPortPos() != null) {
                    if ((fc.getPortPos().getNode().toString().compareToIgnoreCase(pos[0]) == 0) &&
                            (fc.getPortPos().getSlot().toString().compareToIgnoreCase(pos[1]) == 0) &&
                            (fc.getPortPos().getCardPort().toString().compareToIgnoreCase(pos[2]) == 0) ) {

                        // host connected array port
                        clusterPorts.add(sp);
                    }
                } // porPos != null
            } //for fc
        }
    }
   
    /* 
     * Unexport the volumes from array
     */
    @Override
    public DriverTask unexportVolumesFromInitiators(List<Initiator> initiators, List<StorageVolume> volumes) {
        DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_UNEXPORT_STORAGE_VOLUMES);
        _log.info("3PARDriver:unexportVolumesFromInitiators enter");

        // All initiators belong to same host
        ArrayList<Position> initiatorPaths = new ArrayList<>();
        String host = null;
        int totalUnexport = 0;

        try {
            if (initiators.isEmpty() || volumes.isEmpty()) {
                _log.error("3PARDriver:unexportVolumesFromInitiators error blank initiator or volumes");
                throw new HP3PARException("3PARDriver:unexportVolumesFromInitiators error blank initiator or volumes");
            }
            
            host = get3parHostname(initiators, volumes.get(0).getStorageSystemId());
            if (host == null) {
                _log.error("3PARDriver:unexportVolumesFromInitiators error in processing host name");
                throw new HP3PARException("3PARDriver:unexportVolumesFromInitiators error in processing host name");
            }
        } catch (Exception e) {
            String msg = String.format("3PARDriver:unexportVolumesFromInitiators error : %s", e);
            _log.error(msg);
            _log.error(CompleteError.getStackTrace(e));
            task.setMessage(msg);
            task.setStatus(DriverTask.TaskStatus.FAILED);
            e.printStackTrace();
            return task;
        }

        // unexport each volume
        for (StorageVolume volume:volumes) {
            try {
                // get Api client for volume specific array
                HP3PARApi hp3parApi = getHP3PARDeviceFromNativeId(volume.getStorageSystemId());

                initiators.get(0).setInitiatorType(Type.Host); ////TEMP CODING
                if (initiators.get(0).getInitiatorType().equals(Type.Host) == true) {
                    // get vlun and port details on this export
                    Integer lun = -1;
                    Position pos = null;
                    VirtualLunsList vlunRes = hp3parApi.getAllVlunDetails();

                    for (VirtualLun vLun:vlunRes.getMembers()) {

                        for (Initiator init:initiators) {
                            String portId = init.getPort();
                            portId = portId.replace(":", "");
                            if (volume.getNativeId().compareTo(vLun.getVolumeName()) != 0 || vLun.isActive() == false
                                    || portId.compareToIgnoreCase(vLun.getRemoteName()) != 0) {
                                continue;
                            }

                            lun = vLun.getLun();
                            pos = vLun.getPortPos();

                            String message = String.format("3PARDriver:unexportVolumesFromInitiators for "
                                    + "storage system %s, volume %s host %s hlu %s port %s", 
                                    volume.getStorageSystemId(), volume.getNativeId(), host, lun.toString(), pos.toString());
                            _log.info(message);

                            // Each vlun will have required info
                            String posStr = String.format("%s:%s:%s", pos.getNode(), pos.getSlot(), pos.getCardPort());
                            hp3parApi.deleteVlun(volume.getNativeId(), lun.toString(), host, posStr);
                            totalUnexport++;
                        }// end for init
                    }
                } /*else if (initiators.get(0).getInitiatorType().equals(Type.CLUSTER) == true)*/ //TEMP CODING
                else if (initiators.get(0).getInitiatorType().equals(Type.RP) == true) {

                    String clusterName = "set:" + initiators.get(0).getClusterName();
                    String exportPath = volume.getStorageSystemId() + volume.getNativeId() + clusterName;
                    Map<String, List<String>> attributes = new HashMap<>();
                    List<String> expValue = new ArrayList<>();
                    List<String> lunValue = new ArrayList<>();
                    boolean regPresent = false;

                    String message = String.format("3PARDriver:unexportVolumesFromInitiators for "
                            + "storage system %s, volume %s Cluster %s", 
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
                            
                            // remove the registry content
                            this.driverRegistry.clearDriverAttributesForKey(HP3PARConstants.DRIVER_NAME, exportPath);
                            totalUnexport++;
                        }
                    }
                    
                    if (regPresent == false) {
                        //gracefully exit, nothing to be done
                        _log.info("3PARDriver: Already unexported, exiting gracefully" + message);
                        totalUnexport++;
                    }
                } // if cluster
                    
            } catch (Exception e) {
                String msg = String.format("3PARDriver: Unable to unexport few volumes, error: %s", e);
                _log.error(msg);
                _log.error(CompleteError.getStackTrace(e));
                task.setMessage(msg);
                task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
                e.printStackTrace();
            }
        } //for each volume

        if (totalUnexport == volumes.size()) {
            task.setStatus(DriverTask.TaskStatus.READY);
        }
        
        _log.info("3PARDriver:unexportVolumesFromInitiatorss leave");
        return task;
    }

    @Override
	public DriverTask createConsistencyGroup(VolumeConsistencyGroup consistencyGroup) {
		DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_CREATE_CONSISTENCY_GROUP);

		try {
			_log.info(
					"3PARDriver: createConsistencyGroup for storage system  id {}, display name {} , native id {}, device lable id {} , cosistency group id {}  - start",
					consistencyGroup.getStorageSystemId(), consistencyGroup.getDisplayName(),
					consistencyGroup.getNativeId(), consistencyGroup.getDeviceLabel(),
					consistencyGroup.getConsistencyGroup());

			// get Api client
			HP3PARApi hp3parApi = getHP3PARDeviceFromNativeId(consistencyGroup.getStorageSystemId());

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
			task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
			e.printStackTrace();
		}

		return task;

	}

	/**
	 * Delete VV Set or consistency group
	 * 
	 */
	@Override
	public DriverTask deleteConsistencyGroup(VolumeConsistencyGroup consistencyGroup) {
		_log.info(
				"3PARDriver: deleteConsistencyGroup for storage system  id {}, display name {} , native id {}, device lable id {} , cosistency group id {}  - start",
				consistencyGroup.getStorageSystemId(), consistencyGroup.getDisplayName(),
				consistencyGroup.getNativeId(), consistencyGroup.getDeviceLabel(),
				consistencyGroup.getConsistencyGroup());

		DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_DELETE_CONSISTENCY_GROUP);

		try {

			// get Api client
			HP3PARApi hp3parApi = getHP3PARDeviceFromNativeId(consistencyGroup.getStorageSystemId());

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
			task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
			e.printStackTrace();
		}

		return task;

	}

	@Override
	public DriverTask createConsistencyGroupSnapshot(VolumeConsistencyGroup consistencyGroup,
			List<VolumeSnapshot> snapshots, List<CapabilityInstance> capabilities) {
		_log.info(
				"3PARDriver: createConsistencyGroupSnapshot for storage system  id {}, display name {} , native id {} - start",
				consistencyGroup.getStorageSystemId(), consistencyGroup.getDisplayName(),
				consistencyGroup.getNativeId());
		String VVsetSnapshotName = consistencyGroup.getDisplayName();

		DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_SNAPSHOT_CONSISTENCY_GROUP);
		VolumeDetailsCommandResult volResult = null;

		try {

			Boolean readOnly = true;

			// get Vipr generated Snapshot name
			for (VolumeSnapshot snap : snapshots) {

				// native id = null ,
				_log.info(
						"3PARDriver: createConsistencyGroupSnapshot for volume native id {}, snap shot name generated is {} - start",
						snap.getParentId(), snap.getDisplayName());

				if (snap.getAccessStatus() != AccessStatus.READ_ONLY) {
					readOnly = false;
				}

				String generatedSnapshotName = snap.getDisplayName();
				VVsetSnapshotName = generatedSnapshotName.substring(0, generatedSnapshotName.lastIndexOf("-")) + "-";
				_log.info("3PARDriver: createConsistencyGroupSnapshot VVsetSnapshotName {} ", VVsetSnapshotName);
				break;

			}

			// get Api client
			HP3PARApi hp3parApi = getHP3PARDeviceFromNativeId(consistencyGroup.getStorageSystemId());

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
						"3PARDriver: createConsistencyGroupSnapshot snapshotCreated {}, volumeNumber {} , snapVolumeCount {} - start",
						snapshotCreated, volumeNumber, snapVolumeCount);

				volResult = hp3parApi.getVolumeDetails(VVsetSnapshotName + volumeNumber);
				if (volResult != null) {
					String baseVolume = volResult.getCopyOf();

					if (baseVolume != null) {
						for (VolumeSnapshot snap : snapshots) {
							_log.info(
									"createConsistencyGroupSnapshot Snapshot system native id {}, Parent Volume {}, access status {}, display name {}, native Name {}, DeviceLabel {} - Before",
									snap.getStorageSystemId(), snap.getParentId(), snap.getAccessStatus(),
									snap.getDisplayName(), snap.getNativeId(), snap.getDeviceLabel());

							String parentName = snap.getParentId();
							_log.info("createConsistencyGroupSnapshot +++{}++{}+++ ", parentName, baseVolume);
							if (parentName.equals(baseVolume)) {
								_log.info(
										"createConsistencyGroupSnapshot snap name {} wwn {} deviceLable {} displayname {} ",
										snap.getNativeId(), snap.getWwn(), snap.getDeviceLabel(),
										snap.getDisplayName());
								snap.setWwn(volResult.getWwn());
								snap.setNativeId(volResult.getName());
								snap.setDeviceLabel(volResult.getName());
								snap.setLabel(volResult.getName());
								// snap.setAccessStatus(volResult.getAccessStatus());
								snap.setDisplayName(volResult.getName());

								_log.info("createConsistencyGroupSnapshot volResult name {} wwn {} ",
										volResult.getName(), volResult.getWwn());
							}

							_log.info(
									"createConsistencyGroupSnapshot Snapshot system native id {}, Parent Volume {}, access status {}, display name {}, native Name {}, DeviceLabel {} - After",
									snap.getStorageSystemId(), snap.getParentId(), snap.getAccessStatus(),
									snap.getDisplayName(), snap.getNativeId(), snap.getDeviceLabel());

						}
					} else {
						_log.info("3PARDriver: createConsistencyGroupSnapshot baseVolume is null");

					}

				} else {
					_log.info("3PARDriver: createConsistencyGroupSnapshot volResult is null");

				}
				volumeNumber = volumeNumber + 1;
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

	@Override
	public DriverTask deleteConsistencyGroupSnapshot(List<VolumeSnapshot> snapshots) {

		DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_DELETE_SNAPSHOT_CONSISTENCY_GROUP);

		// For each requested CG volume snapshot
		for (VolumeSnapshot snap : snapshots) {
			try {
				_log.info(
						"3PARDriver: deleteConsistencyGroupSnapshot for storage system native id {}, volume name {} , native id {} - start",
						snap.getStorageSystemId(), snap.getDisplayName(), snap.getNativeId());


				// get Api client
				HP3PARApi hp3parApi = getHP3PARDeviceFromNativeId(snap.getStorageSystemId());

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
     * Creating physical copy for VVset or CG clone 
     * Rest API expects created VVset with its corresponding volumes types for clone destination 
     * So, There are many ways for implementation
     * 1. Customer will provide the VVSet name which already exist in Array with its corresponding similar volumes for cloning
     *  
     * 2. Customer will not provide any existing and matching VV set with corresponding volumes for CG clone 
     * 
     * 3. Customer will provide VVset name which is created but volumes are not of matching for clone creation.
     * 
     * Create new VV Set / CG .
     * Create new volumes similar to parent VVSet volumes
     * Use this newly created VV set for CG clone 
     * option 2 is implemented, need to handle negative / error cases of option 3  
     */

	@Override
	public DriverTask createConsistencyGroupClone(VolumeConsistencyGroup consistencyGroup, List<VolumeClone> clones,
			List<CapabilityInstance> capabilities) {

		DriverTask task = createDriverTask(HP3PARConstants.TASK_TYPE_CLONE_CONSISTENCY_GROUP);
	    	
	    	_log.info("3PARDriver: createConsistencyGroupClone for storage system  id {}, Base CG name {} , Base CG native id {} - start",
	    			consistencyGroup.getStorageSystemId(), consistencyGroup.getDisplayName(), consistencyGroup.getNativeId());
	    	String  VVsetNameForClone = consistencyGroup.getDisplayName();
			
			VolumeDetailsCommandResult volResult = null;
			HashMap<String,VolumeClone> clonesMap = new HashMap<String,VolumeClone>();
			
			try {

				Boolean saveSnapshot = true;

				// get Vipr generated clone name
			   	for (VolumeClone clone : clones) {
		            
		            	//native id = null , 
		                _log.info("3PARDriver: createConsistencyGroupClone generated clone parent id {}, display name {} - start",
		                		clone.getParentId(), clone.getDisplayName());  
		            
		                String generatedCloneName = clone.getDisplayName();
		                VVsetNameForClone = generatedCloneName.substring(0, generatedCloneName.lastIndexOf("-"));
		                _log.info("3PARDriver: createConsistencyGroupClone CG name {} to be used in cloning ",VVsetNameForClone);
		                clonesMap.put(clone.getParentId(), clone);

			   	}
			    _log.info("3PARDriver: createConsistencyGroupClone  clonesMap {}",clonesMap.toString());
			    
				// get Api client
				HP3PARApi hp3parApi = getHP3PARDeviceFromNativeId(consistencyGroup.getStorageSystemId());
			   	
				// Create vvset clone
				VVSetVolumeClone[] result = hp3parApi.createVVsetPhysicalCopy(consistencyGroup.getNativeId(), VVsetNameForClone, clones, saveSnapshot);
				
				_log.info("3PARDriver: createConsistencyGroupClone outPut of CG clone result  {} ",result.toString());
				
				int volumeNumber = 0;
				int cloneVolumeCount  = result.length;
				
				/**
				 * for each volume clone result returned  
				 * find corresponding clone object and set its value and commit it
				 */
				//ArrayList<VVSetVolumeClone> createdClones = result.getClonesInfo();

			//	for (VVSetVolumeClone cloneCreated : createdClones) {
					for (VVSetVolumeClone cloneCreated : result) {	
					VolumeClone clone = clonesMap.get(cloneCreated.getParent());
					
					_log.info("createConsistencyGroupClone cloneCreated {} and local clone obj nativeid = {} , parent id = {}",cloneCreated.getValues(),clone.getNativeId(),clone.getParentId());
					volResult = hp3parApi.getVolumeDetails(cloneCreated.getChild());
					
					_log.info("createConsistencyGroupClone cloneCreated All values {} ",volResult.getAllValues());
					
					clone.setWwn(volResult.getWwn());
					clone.setNativeId(volResult.getName());
					clone.setDeviceLabel(volResult.getName());
					clone.setLabel(volResult.getName());
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
						VVsetNameForClone, consistencyGroup.getNativeId(), consistencyGroup.getStorageSystemId(), e.getMessage());
				_log.error(msg);
				task.setMessage(msg);
				task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
				e.printStackTrace();
			}
	        
	        return task;
	        
	    }
	   

	/*
	 * Internal methods in the driver
	 */
	private HP3PARApi getHP3PARDevice(StorageSystem hp3parSystem) throws HP3PARException {
		URI deviceURI;
		_log.info("3PARDriver:getHP3PARDevice input storage system");

		try {
			deviceURI = new URI("https", null, hp3parSystem.getIpAddress(), hp3parSystem.getPortNumber(), "/", null,
					null);
			return hp3parApiFactory.getRESTClient(deviceURI, hp3parSystem.getUsername(), hp3parSystem.getPassword());
		} catch (Exception e) {
			e.printStackTrace();
			_log.error("3PARDriver:Error in getting 3PAR device, with StorageSystem");
			throw new HP3PARException("Error in getting 3PAR device");
		}
	}

	private HP3PARApi getHP3PARDevice(String ip, String port, String user, String pass) throws HP3PARException {
		URI deviceURI;
		_log.info("3PARDriver:getHP3PARDevice input full details");

		try {
			deviceURI = new URI("https", null, ip, Integer.parseInt(port), "/", null, null);
			return hp3parApiFactory.getRESTClient(deviceURI, user, pass);
		} catch (Exception e) {
			e.printStackTrace();
			_log.error("3PARDriver:Error in getting 3PAR device with details");
			throw new HP3PARException("Error in getting 3PAR device");
		}
	}

	private HP3PARApi getHP3PARDeviceFromNativeId(String nativeId) throws HP3PARException {
		try {
			Map<String, List<String>> connectionInfo = driverRegistry
					.getDriverAttributesForKey(HP3PARConstants.DRIVER_NAME, nativeId);
			List<String> ipAddress = connectionInfo.get(HP3PARConstants.IP_ADDRESS);
			List<String> portNumber = connectionInfo.get(HP3PARConstants.PORT_NUMBER);
			List<String> userName = connectionInfo.get(HP3PARConstants.USER_NAME);
			List<String> password = connectionInfo.get(HP3PARConstants.PASSWORD);
			HP3PARApi hp3parApi = getHP3PARDevice(ipAddress.get(0), portNumber.get(0), userName.get(0),
					password.get(0));
			return hp3parApi;
		} catch (Exception e) {
			e.printStackTrace();
			_log.error("3PARDriver:Error in getting 3PAR device with nativeId");
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
}
