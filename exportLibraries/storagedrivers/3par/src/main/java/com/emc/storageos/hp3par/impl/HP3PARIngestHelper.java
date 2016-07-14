
/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.hp3par.command.ConsistencyGroupResult;
import com.emc.storageos.hp3par.command.ConsistencyGroupsListResult;
import com.emc.storageos.hp3par.command.VirtualLun;
import com.emc.storageos.hp3par.command.VirtualLunsList;
import com.emc.storageos.hp3par.command.VolumeDetailsCommandResult;
import com.emc.storageos.hp3par.command.VolumeMember;
import com.emc.storageos.hp3par.command.VolumesCommandResult;
import com.emc.storageos.hp3par.utils.HP3PARConstants;
import com.emc.storageos.hp3par.utils.HP3PARConstants.copyType;
import com.emc.storageos.hp3par.utils.HP3PARUtil;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.HostExportInfo;
import com.emc.storageos.storagedriver.Registry;
import com.emc.storageos.storagedriver.model.Initiator;
import com.emc.storageos.storagedriver.model.StorageBlockObject;
import com.emc.storageos.storagedriver.model.StorageObject;
import com.emc.storageos.storagedriver.model.StoragePort;
import com.emc.storageos.storagedriver.model.StorageSystem;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.model.VolumeClone;
import com.emc.storageos.storagedriver.model.VolumeSnapshot;

/**
 * 
 * Implements functions to discover the HP 3PAR storage and provide provisioning
 * You can refer super class for method details
 *
 */
public class HP3PARIngestHelper {

	private static final Logger _log = LoggerFactory.getLogger(HP3PARIngestHelper.class);

	private HP3PARUtil hp3parUtil;

	public DriverTask getStorageVolumes(StorageSystem storageSystem, List<StorageVolume> storageVolumes,
			MutableInt token, DriverTask task, Registry driverRegistry) {

		Map<String, List<String>> vvolAssociations = new HashMap<String, List<String>>();
		Map<String, List<String>> vvolAncestryMap = new HashMap<String, List<String>>();
		HashMap<Long, String> vvolNamesMap = new HashMap<Long, String>();

		try {
			HashMap<String, ArrayList<String>> volumesToVolSetsMap = generateVolumeSetToVolumeMap(storageSystem,
					driverRegistry);

			// get Api client
			HP3PARApi hp3parApi = hp3parUtil.getHP3PARDeviceFromNativeId(storageSystem.getNativeId(), driverRegistry);
			VolumesCommandResult objStorageVolumes = hp3parApi.getStorageVolumes();

			// first we build HashMap of volume id , volume name
			for (VolumeMember objVolMember : objStorageVolumes.getMembers()) {				
				vvolNamesMap.put(new Long(objVolMember.getId()), objVolMember.getName());
			}

			_log.info("vvolNamesMap is {}", vvolNamesMap);

			// first we build HashMap of volume id , volume name
			for (VolumeMember objVolMember : objStorageVolumes.getMembers()) {				
				if (objVolMember.getCopyType() == HP3PARConstants.copyType.VIRTUAL_COPY.getValue()) {
					ArrayList<String> arrLst = new ArrayList<String>();
					arrLst.add(vvolNamesMap.get(objVolMember.getBaseId()));
					vvolAncestryMap.put(new String(objVolMember.getId()), arrLst);
				} else if (objVolMember.getCopyType() == HP3PARConstants.copyType.PHYSICAL_COPY.getValue()) {
					ArrayList<String> arrLst = new ArrayList<String>();
					arrLst.add(vvolNamesMap.get(objVolMember.getPhysParentId()));
					vvolAncestryMap.put(new String(objVolMember.getId()), arrLst);
				}
			}

			_log.info("vvolAncestryMap is {}", vvolAncestryMap);
			_log.info("objStorageVolumes.getTotal() is {}", objStorageVolumes.getTotal());

			for (VolumeMember objVolMember: objStorageVolumes.getMembers()) {							
				_log.info("objVolMember.getid is {}", objVolMember.getId());
				_log.info("objVolMember.getbaseid is {}", objVolMember.getBaseId());
				_log.info("objVolMember.getname is {}", objVolMember.getName());
				StorageVolume driverVolume = new StorageVolume();
				driverVolume.setStorageSystemId(storageSystem.getNativeId());
				driverVolume.setStoragePoolId(objVolMember.getUserCPG());
				driverVolume.setNativeId(objVolMember.getName());
				driverVolume.setProvisionedCapacity(objVolMember.getSizeMiB() * HP3PARConstants.MEGA_BYTE);
				driverVolume.setAllocatedCapacity(objVolMember.getSizeMiB() * HP3PARConstants.MEGA_BYTE);
				driverVolume.setWwn(objVolMember.getWwn());
				driverVolume.setNativeId(objVolMember.getName());
				driverVolume.setDeviceLabel(objVolMember.getName());

				// if the volumesToVolSetsMap contains the volume name entry. It
				// means
				// that volume belongs to consistencygroup(volume set in hp3par
				// teminology)
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

				_log.info("objVolMember.getCopyOf() in getstoragevolumes is {}", objVolMember.getCopyOf());
				if (objVolMember.getCopyOf() != null) {

				} else {
					_log.info("Adding to storagevolumes array the volume {}", objVolMember.getName());
					storageVolumes.add(driverVolume);
				}

				_log.info("Unmanaged volume info: pool {}, volume {}", driverVolume.getStoragePoolId(), driverVolume);
				_log.info("objVolMember.getCopyOf() is {}", objVolMember.getCopyOf());

				if (objVolMember.getCopyOf() != null) {					
					_log.info("objVolMember.getCopyType() {}", objVolMember.getCopyType());

					String ancestorId = null;
					String ancestorName = null;
					// Here we see if the current VVOL entity's copyof value
					// corresponds to a physical
					// volume or another virtual copy.
					// Example: snapA, is the snapshot of volumeA. Now snapA',
					// is the snapshot of snapA.
					// then when objVolMember represents snapA', then copyOf
					// will point to snapA, but
					// baseid will point to the id of the volume volumeA.

					if (objVolMember.getCopyType() == copyType.VIRTUAL_COPY.getValue()) {
						_log.info("objVolMember.getBaseId() is {}", objVolMember.getBaseId());
						ancestorName = vvolAncestryMap.get(objVolMember.getId()).get(0);
					} else if (objVolMember.getCopyType() == copyType.PHYSICAL_COPY.getValue()) {
						_log.info("objVolMember.getPhysParentId() is {}", objVolMember.getPhysParentId());
						ancestorName = vvolAncestryMap.get(objVolMember.getId()).get(0);
					}
					//ancestorName = vvolNamesMap.get(ancestorId);
					if (vvolAssociations.containsKey(ancestorName)) {						
						ArrayList<String> listOfChildren = (ArrayList<String>) vvolAssociations.get(ancestorName);
						listOfChildren.add(objVolMember.getName());
					} else {						
						ArrayList<String> listOfChildren = new ArrayList<String>();
						listOfChildren.add(objVolMember.getName());
						vvolAssociations.put(ancestorName, listOfChildren);
					}
					_log.info("objAncestor name is {}", ancestorName);
					_log.info("objVolMember being added is {} ", objVolMember.getName());
				}
			}
			_log.info("THE vvolAssociations BEING RETURNED BY THE GETSTORAGEVOLS IS {}", vvolAssociations);
			task.setStatus(DriverTask.TaskStatus.READY);
			driverRegistry.setDriverAttributesForKey(HP3PARConstants.DRIVER_NAME,
					storageSystem.getNativeId() + "____VVOL_ASSOCIATIONS", vvolAssociations);
			driverRegistry.setDriverAttributesForKey(HP3PARConstants.DRIVER_NAME,
					storageSystem.getNativeId() + "____VVOL_ANCESTORS", vvolAncestryMap);

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
	private HashMap<String, ArrayList<String>> generateVolumeSetToVolumeMap(StorageSystem storageSystem,
			Registry registry) throws Exception {
		// get Api client
		HP3PARApi hp3parApi = hp3parUtil.getHP3PARDeviceFromNativeId(storageSystem.getNativeId(), registry);
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
	 * Identifying snapshots of the given parent base volume. NOTE: Intermediate
	 * physical copies of 3PAR generated from other snapshots/clone are shown as
	 * clone of base volume itself Need to check this behavior ?
	 */
	public List<VolumeSnapshot> getVolumeSnapshots(StorageVolume volume, Registry registry) {
		_log.info("3PARDriver: getVolumeSnapshots Running ");
		List<VolumeSnapshot> snapshots = new ArrayList<>();

		try {
			Map<String, List<String>> vvolAssociations = registry.getDriverAttributesForKey(HP3PARConstants.DRIVER_NAME,
					volume.getStorageSystemId() + "____VVOL_ASSOCIATIONS");
			
			_log.info("vvolAssociations is {}", vvolAssociations.toString());

			HP3PARApi hp3parApi = hp3parUtil.getHP3PARDeviceFromNativeId(volume.getStorageSystemId(), registry);

			ArrayList<String> listOfChildVols = null;
			listOfChildVols = (ArrayList<String>) vvolAssociations.get(volume.getNativeId());
			
			_log.info("listOfChildVols.size()  is {}", listOfChildVols.size());
			for (String childName:listOfChildVols) {
				// VolumeMember is the data structure used for representation of
				// the HP3PAR virtual volume				
				// VolumeSnapshot is the CoprHD southbound freamework's
				// datastructure
				VolumeSnapshot driverSnapshot = new VolumeSnapshot();

				VolumeDetailsCommandResult resultSnap = hp3parApi.getVolumeDetails(childName);
				if (resultSnap.getCopyType() == copyType.VIRTUAL_COPY.getValue()) {
					driverSnapshot.setParentId(volume.getNativeId());
					driverSnapshot.setNativeId(resultSnap.getName());
					driverSnapshot.setDeviceLabel(resultSnap.getName());
					driverSnapshot.setStorageSystemId(volume.getStorageSystemId());
					driverSnapshot.setAccessStatus(StorageObject.AccessStatus.READ_ONLY);
	
					if (volume.getConsistencyGroup() != null) {
						driverSnapshot.setConsistencyGroup(volume.getConsistencyGroup());
					}
	
					driverSnapshot.setWwn(resultSnap.getWwn());
	
					// TODO: We need to have more clarity on provisioned and
					// allocated sizes
					driverSnapshot.setAllocatedCapacity(resultSnap.getSizeMiB() * HP3PARConstants.MEGA_BYTE);
					driverSnapshot.setProvisionedCapacity(resultSnap.getSizeMiB() * HP3PARConstants.MEGA_BYTE);
					snapshots.add(driverSnapshot);
				}
			}
			_log.info("3PARDriver: getVolumeSnapshots Leaving");
			return snapshots;
		} catch (Exception e) {
			String msg = String.format(
					"3PARDriver: Unable to get snapshot of volume with storage system %s and volume native id %s; Error: %s.\n",
					volume.getStorageSystemId(), volume.getNativeId(), e.getMessage());
			e.printStackTrace();
		}
		_log.info("3PARDriver: getVolumeSnapshots Leaving");
		return null;
	}

	/**
	 * Identifying clones of the given parent base volume. NOTE: Intermediate
	 * physical copies of 3PAR generated from other snapshots/clone are shown as
	 * clone of base volume itself Need to check this behavior ?
	 */
	public List<VolumeClone> getVolumeClones(StorageVolume volume, Registry registry) {
		_log.info("3PARDriver: getVolumeClones Running ");
		List<VolumeClone> clones = new ArrayList<>();

		try {
			Map<String, List<String>> vvolAssociations = registry.getDriverAttributesForKey(HP3PARConstants.DRIVER_NAME,
					volume.getStorageSystemId() + "____VVOL_ASSOCIATIONS");
			;

			_log.info("vvolAssociations is {}", vvolAssociations.toString());

			HP3PARApi hp3parApi = hp3parUtil.getHP3PARDeviceFromNativeId(volume.getStorageSystemId(), registry);
			// VolumesCommandResult snapsResult =
			// hp3parApi.getClonesOfVolume(volume.getNativeId());

			HashMap<String, ArrayList<VolumeMember>> volMappings = null;
			ArrayList<String> listOfChildVols = null;

			listOfChildVols = (ArrayList<String>) vvolAssociations.get(volume.getNativeId());
			
			_log.info("listOfChildVols.size()  is {}", listOfChildVols.size());

			for (String childName:listOfChildVols) {
				// VolumeMember is the data structure used for representation of
				// the HP3PAR virtual volume				
				VolumeDetailsCommandResult objClone = hp3parApi.getVolumeDetails(childName);
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
				}
			}
			_log.info("3PARDriver: getVolumeClones Leaving");
			return clones;
		} catch (Exception e) {
			String msg = String.format(
					"3PARDriver: Unable to get clone of volume with storage system %s and volume native id %s; Error: %s.\n",
					volume.getStorageSystemId(), volume.getNativeId(), e.getMessage());
			e.printStackTrace();
		}

		return null;
	}

	/*
	 * This function should return a HashMap. Key of HashMap : HostName to which
	 * the volume is exported Value of the HashMap : HostExportInfo associated
	 * with export to HostName
	 */
	public Map<String, HostExportInfo> getVolumeExportInfoForHosts(StorageVolume volume, Registry registry) {
		return getBlockObjectExportInfoForHosts(volume.getStorageSystemId(), volume.getWwn(), volume.getNativeId(),
				volume, registry);
	}

	/*
	 * This function should return a HashMap. Key of HashMap : HostName to which
	 * the snapshot is exported Value of the HashMap : HostExportInfo associated
	 * with export to HostName
	 */
	public Map<String, HostExportInfo> getSnapshotExportInfoForHosts(VolumeSnapshot snapshot, Registry registry) {
		return getBlockObjectExportInfoForHosts(snapshot.getStorageSystemId(), snapshot.getWwn(),
				snapshot.getNativeId(), snapshot, registry);
	}

	/*
	 * This function should return a HashMap. Key of HashMap : HostName to which
	 * the clone is exported Value of the HashMap : HostExportInfo associated
	 * with export to HostName
	 */
	public Map<String, HostExportInfo> getCloneExportInfoForHosts(VolumeClone clone, Registry registry) {
		return getBlockObjectExportInfoForHosts(clone.getStorageSystemId(), clone.getWwn(), clone.getNativeId(), clone,
				registry);
	}

	public Map<String, HostExportInfo> getBlockObjectExportInfoForHosts(String storageSystemId, String wwn,
			String objectName, StorageBlockObject object, Registry registry) {
		try {
			_log.info("3PARDriver: getBlockObjectExportInfoForHosts Running");
			Map<String, HostExportInfo> resultMap = new HashMap<String, HostExportInfo>();

			// get the vlun associated with the volume at consideration.
			HP3PARApi hp3parApi = hp3parUtil.getHP3PARDeviceFromNativeId(storageSystemId, registry);
			VirtualLunsList vlunsOfVolume = hp3parApi.getVLunsOfVolume(wwn);

			// Check which of the storage ports discovered, matches the
			// node:portpos:cardport
			// combination of the VLUN
			List<StoragePort> storPortsOfStorage = new ArrayList<>();
			hp3parUtil.discoverStoragePortsById(storageSystemId, storPortsOfStorage, registry);
			_log.info("storPortsOfStorage are {}", storPortsOfStorage);

			
			// process the vlun information by iterating through the vluns
			// and then for each vlun, we create the appropriate key:value pair
			// in the resultMap with hostname:HostExportInfo information.
			//for (int index = 0; index < vlunsOfVolume.getTotal(); index++) {
			for (VirtualLun objVirtualLun : vlunsOfVolume.getMembers()){
				if (!objVirtualLun.isActive()) {
					continue;
				}

				_log.info("objVirtualLun.toString() {}",objVirtualLun.toString());

				List<String> volumeIds = new ArrayList<>();
				List<Initiator> initiators = new ArrayList<Initiator>();
				List<StoragePort> storageports = new ArrayList<>();

				// To volumeIds we need to add the native id of volume
				// and for hp3par volume name would be the native id
				volumeIds.add(objVirtualLun.getVolumeName());

				Initiator hostInitiator = new Initiator();
				// hp3par returns remote name in the format like
				// 10000000C98F5C79.
				// we now convert this to the format 10:00:00:00:C9:8F:5C:79
				String portId = objVirtualLun.getRemoteName().substring(0, 2) + ":"
						+ objVirtualLun.getRemoteName().substring(2, 4) + ":"
						+ objVirtualLun.getRemoteName().substring(4, 6) + ":"
						+ objVirtualLun.getRemoteName().substring(6, 8) + ":"
						+ objVirtualLun.getRemoteName().substring(8, 10) + ":"
						+ objVirtualLun.getRemoteName().substring(10, 12) + ":"
						+ objVirtualLun.getRemoteName().substring(12, 14) + ":"
						+ objVirtualLun.getRemoteName().substring(14, 16);
				
				String nativeId = String.format("%s:%s:%s", objVirtualLun.getPortPos().getNode(),
						objVirtualLun.getPortPos().getSlot(), objVirtualLun.getPortPos().getCardPort());

				for (StoragePort port:storPortsOfStorage) {															
					if (port.getNativeId().equals(nativeId)) {
						storageports.add(port);
						break;
					}
				}

				hostInitiator.setHostName(objVirtualLun.getHostname());
				hostInitiator.setPort(portId);
				initiators.add(hostInitiator);

				HostExportInfo exportInfo = null;
				if (resultMap.containsKey(objVirtualLun.getHostname())) {
					exportInfo = resultMap.get(objVirtualLun.getHostname());
					for (int i1 = 0; i1 < storageports.size(); i1++) {
						StoragePort ob1 = storageports.get(i1);
						if (!exportInfo.getTargets().contains(ob1)) {
							exportInfo.getTargets().add(ob1);
						}
					}
					for (int i1 = 0; i1 < initiators.size(); i1++) {
						Initiator ob1 = initiators.get(i1);
						if (!exportInfo.getInitiators().contains(ob1)) {
							exportInfo.getInitiators().add(ob1);
						}
					}
				} else {
					exportInfo = new HostExportInfo(objVirtualLun.getHostname(), volumeIds, initiators, storageports);
				}

				resultMap.put(objVirtualLun.getHostname(), exportInfo);
			}
			_log.info("RESULTMAP FROM GETVOLUMEEXPORTINFO {}", resultMap);
			_log.info("3PARDriver: Leaving getBlockObjectExportInfoForHosts");
			return resultMap;
		} catch (Exception e) {
			String msg = String.format(
					"3PARDriver: Unable to get export info of the storage objet %s in storage system native id is %s; Error: %s.\n",
					objectName, storageSystemId, e.getMessage());
			_log.error(msg);
			e.printStackTrace();
		}

		return null;
	}

	public HP3PARUtil getHp3parUtil() {
		return hp3parUtil;
	}

	public void setHp3parUtil(HP3PARUtil hp3parUtil) {
		this.hp3parUtil = hp3parUtil;
	}

}
