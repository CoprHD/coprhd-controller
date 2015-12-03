package com.emc.storageos.driver.scaleio;

import com.emc.storageos.driver.scaleio.api.ScaleIOConstants;
import com.emc.storageos.driver.scaleio.api.restapi.ScaleIORestClient;
import com.emc.storageos.driver.scaleio.api.restapi.response.ScaleIOProtectionDomain;
import com.emc.storageos.driver.scaleio.api.restapi.response.ScaleIOSDS;
import com.emc.storageos.driver.scaleio.api.restapi.response.ScaleIOStoragePool;
import com.emc.storageos.driver.scaleio.api.restapi.response.ScaleIOSystem;
import com.emc.storageos.storagedriver.AbstractStorageDriver;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.RegistrationData;
import com.emc.storageos.storagedriver.impl.InMemoryRegistryImpl;
import com.emc.storageos.storagedriver.model.*;
import com.emc.storageos.storagedriver.storagecapabilities.CapabilityInstance;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;
import org.apache.commons.lang.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ScaleIOStorageDriver extends AbstractStorageDriver {
	private ScaleIORestHandleFactory handleFactory;
	private static Logger _log = LoggerFactory.getLogger(ScaleIORestClient.class);

	DriverTask task;

	public void setHandleFactory(ScaleIORestHandleFactory handleFactory) {
		this.handleFactory = handleFactory;

	}
	@Override
	public DriverTask createVolumes(List<StorageVolume> volumes, StorageCapabilities capabilities) {
		return null;
	}

	@Override
	public DriverTask expandVolume(StorageVolume volume, long newCapacity) {
		return null;
	}

	@Override
	public DriverTask deleteVolumes(List<StorageVolume> volumes) {
		return null;
	}

	@Override
	public DriverTask createVolumeSnapshot(List<VolumeSnapshot> snapshots, StorageCapabilities capabilities) {
		return null;
	}

	@Override
	public DriverTask restoreSnapshot(StorageVolume volume, VolumeSnapshot snapshot) {
		return null;
	}

	@Override
	public DriverTask deleteVolumeSnapshot(List<VolumeSnapshot> snapshots) {
		return null;
	}

	@Override
	public DriverTask createVolumeClone(List<VolumeClone> clones, StorageCapabilities capabilities) {
		return null;
	}

	@Override
	public DriverTask detachVolumeClone(List<VolumeClone> clones) {
		return null;
	}

	@Override
	public DriverTask restoreFromClone(StorageVolume volume, VolumeClone clone) {
		return null;
	}

	@Override
	public DriverTask deleteVolumeClone(List<VolumeClone> clones) {
		return null;
	}

	@Override
	public DriverTask createVolumeMirror(List<VolumeMirror> mirrors, StorageCapabilities capabilities) {
		return null;
	}

	@Override
	public DriverTask deleteVolumeMirror(List<VolumeMirror> mirrors) {
		return null;
	}

	@Override
	public DriverTask splitVolumeMirror(List<VolumeMirror> mirrors) {
		return null;
	}

	@Override
	public DriverTask resumeVolumeMirror(List<VolumeMirror> mirrors) {
		return null;
	}

	@Override
	public DriverTask restoreVolumeMirror(StorageVolume volume, VolumeMirror mirror) {
		return null;
	}

	@Override
	public List<ITL> getITL(StorageSystem storageSystem, List<Initiator> initiators) {
		return null;
	}

	@Override
	public DriverTask exportVolumesToInitiators(List<Initiator> initiators, List<StorageVolume> volumes, List<StoragePort> recommendedPorts, StorageCapabilities capabilities) {
		return null;
	}

	@Override
	public DriverTask unexportVolumesFromInitiators(List<Initiator> initiators, List<StorageVolume> volumes) {
		return null;
	}

	@Override
	public DriverTask createConsistencyGroup(VolumeConsistencyGroup consistencyGroup) {
		return null;
	}

	@Override
	public DriverTask createConsistencyGroupSnapshot(VolumeConsistencyGroup consistencyGroup, List<VolumeSnapshot> snapshots, List<CapabilityInstance> capabilities) {
		return null;
	}

	@Override
	public DriverTask deleteConsistencyGroupSnapshot(List<VolumeSnapshot> snapshots) {
		return null;
	}

	@Override
	public DriverTask createConsistencyGroupClone(VolumeConsistencyGroup consistencyGroup, List<VolumeClone> clones, List<CapabilityInstance> capabilities) {
		return null;
	}

	@Override
	public DriverTask deleteConsistencyGroupClone(List<VolumeClone> clones) {
		return null;
	}

	@Override
	public RegistrationData getRegistrationData() {
		return null;
	}

	@Override
	public DriverTask discoverStorageSystem(List<StorageSystem> storageSystems) {
		String taskType = "discover-storage-system";
		String taskID = String.format("%s+%s+%s",ScaleIOConstants.DRIVER_NAME,taskType, UUID.randomUUID());
		task = new DriverTaskImpl(taskID);
		driverRegistry = new InMemoryRegistryImpl();
		for(StorageSystem storageSystem : storageSystems) {
				try
				{
					_log.info("StorageDriver: discoverStorageSystem information for storage system {}, name {} - Start", storageSystem.getIpAddress(), storageSystem.getSystemName());
					List<String> list = new ArrayList<>();
					list.add(storageSystem.getIpAddress());
					driverRegistry.addDriverAttributeForKey(ScaleIOConstants.DRIVER_NAME,storageSystem.getNativeId(),ScaleIOConstants.IP_ADDRESS,list);
					list=new ArrayList<>();
					list.add(String.valueOf(storageSystem.getPortNumber()));
                    driverRegistry.addDriverAttributeForKey(ScaleIOConstants.DRIVER_NAME,storageSystem.getNativeId(),ScaleIOConstants.PORT_NUMBER,list);
					list=new ArrayList<>();
					list.add(storageSystem.getUsername());
                    driverRegistry.addDriverAttributeForKey(ScaleIOConstants.DRIVER_NAME,storageSystem.getNativeId(),ScaleIOConstants.USER_NAME,list);
					list=new ArrayList<>();
					list.add(storageSystem.getPassword());
                    driverRegistry.addDriverAttributeForKey(ScaleIOConstants.DRIVER_NAME,storageSystem.getNativeId(),ScaleIOConstants.PASSWORD,list);
					ScaleIORestClient scaleIOHandle = handleFactory.getClientHandle(storageSystem.getNativeId(),driverRegistry);
					if(scaleIOHandle != null) {
						ScaleIOSystem scaleIOSystem = scaleIOHandle.getSystem();
						storageSystem.setSerialNumber(storageSystem.getSerialNumber());
						storageSystem.setNativeId(storageSystem.getNativeId());
						storageSystem.setSystemName(storageSystem.getSystemName());
						storageSystem.setProtocols(storageSystem.getProtocols());
						String version = scaleIOSystem.getVersion().replaceAll("_",".");
						storageSystem.setFirmwareVersion(version);
						if (Double.parseDouble(ScaleIOConstants.MINIMUM_SUPPORTED_VERSION) <= Double.parseDouble(version)) {
							storageSystem.setIsSupportedVersion(ScaleIOConstants.INCOMPATIBLE);
						}
						else {
							storageSystem.setIsSupportedVersion(ScaleIOConstants.COMPATIBLE);
						}
						storageSystem.setProtocols(storageSystem.getProtocols());
						storageSystem.setModel(storageSystem.getModel());

						task.setStatus(DriverTask.TaskStatus.READY);
						_log.info("StorageDriver: discoverStorageSystem information for storage system {}, name {} - End", storageSystem.getIpAddress(), storageSystem.getSystemName());
					} else {
						task.setStatus(DriverTask.TaskStatus.FAILED);
					}
				} catch (Exception e) {
					_log.error("Exception was encountered when attempting to discover storage system {}, name {}",storageSystem.getIpAddress(),storageSystem.getSystemName());
					task.setStatus(DriverTask.TaskStatus.ABORTED);
				}
		}
		return task;
	}

	@Override
	public DriverTask discoverStoragePools(StorageSystem storageSystem, List<StoragePool> storagePools) {
		try{
				String taskType = "discover-storage-pools";
				String taskID = String.format("%s+%s+%s",ScaleIOConstants.DRIVER_NAME,taskType, UUID.randomUUID());
				task = new DriverTaskImpl(taskID);
				_log.info("Discovery of storage pools for storage system {} .", storageSystem.getNativeId());
				ScaleIORestClient scaleIOHandle = handleFactory.getClientHandle(storageSystem.getNativeId(),driverRegistry);
				if(scaleIOHandle != null) {
					List <ScaleIOProtectionDomain> protectionDomains = scaleIOHandle.getProtectionDomains();
					for (ScaleIOProtectionDomain protectionDomain : protectionDomains) {
						List <ScaleIOStoragePool> scaleIOStoragePoolList = scaleIOHandle.getProtectionDomainStoragePools(protectionDomain.getId());
						StoragePool pool;
						for(ScaleIOStoragePool storagePool : scaleIOStoragePoolList) {
							pool = new StoragePool();
							pool.setStorageSystemId(storageSystem.getNativeId());
							_log.info("Discovered Pool {}, storageSystem {}", pool.getNativeId(),pool.getStorageSystemId());
							pool.setDeviceLabel(storageSystem.getDeviceLabel());
							pool.setPoolName(storagePool.getName());
							Set <StoragePool.Protocols> protocols = new HashSet<>();
							protocols.add(StoragePool.Protocols.FC);
							protocols.add(StoragePool.Protocols.iSCSI);
							pool.setProtocols(protocols);
							pool.setPoolServiceType(StoragePool.PoolServiceType.block);
							pool.setMaximumThickVolumeSize(3000000L);
							pool.setMinimumThickVolumeSize(1000L);
							pool.setMaximumThinVolumeSize(5000000L);
							pool.setMinimumThinVolumeSize(1000L);
							pool.setSupportedResourceType(StoragePool.SupportedResourceType.THIN_AND_THICK);
							String availableCapacity = storagePool.getCapacityAvailableForVolumeAllocationInKb();
							pool.setFreeCapacity(Long.parseLong(availableCapacity));
							String totalCapacity = storagePool.getMaxCapacityInKb();
							pool.setTotalCapacity(Long.parseLong(totalCapacity));
							pool.setOperationalStatus(StoragePool.PoolOperationalStatus.READY);
							Set <StoragePool.SupportedDriveTypes> supportedDriveTypes = new HashSet<>();
							supportedDriveTypes.add(StoragePool.SupportedDriveTypes.FC);
							supportedDriveTypes.add(StoragePool.SupportedDriveTypes.SATA);
							pool.setSupportedDriveTypes(supportedDriveTypes);

							storagePools.add(pool);
							task.setStatus(DriverTask.TaskStatus.READY);
					}
				}
			} else {
				task.setStatus(DriverTask.TaskStatus.FAILED);
			}
		} catch (Exception e) {
			_log.error("Exception was encountered when attempting to discover storage pool for storage system {}",storageSystem.getNativeId());
			task.setStatus(DriverTask.TaskStatus.ABORTED);
		}
		return null;
	}

	@Override
	public DriverTask discoverStoragePorts(StorageSystem storageSystem, List<StoragePort> storagePorts) {
		try {
				String taskType = "discover-storage-ports";
				String taskID = String.format("%s+%s+%s",ScaleIOConstants.DRIVER_NAME,taskType, UUID.randomUUID());
				task = new DriverTaskImpl(taskID);
				_log.info("Discovery of storage ports for storage system {} .", storageSystem.getNativeId());
				ScaleIORestClient scaleIOHandle = handleFactory.getClientHandle(storageSystem.getNativeId(),driverRegistry);
				if(scaleIOHandle != null) {
					List <ScaleIOProtectionDomain> protectionDomains = scaleIOHandle.getProtectionDomains();
					List <ScaleIOSDS> allSDSs = scaleIOHandle.queryAllSDS();
					for (ScaleIOProtectionDomain protectionDomain : protectionDomains) {
						String protectionDomainId = protectionDomain.getId();
						String protectionDomainName = protectionDomain.getName();
						StoragePort port;
						for (ScaleIOSDS sds : allSDSs) {
							String pdId = sds.getProtectionDomainId();
							if (pdId.equals(protectionDomainId)) {
								String sdsId = sds.getId();
								List<ScaleIOSDS.IP> ips = sds.getIpList();
								String sdsIP = null;
								if (ips != null && !ips.isEmpty()) {
									sdsIP = ips.get(0).getIp();
								}

								if (sdsId != null) {
									port = new StoragePort();
									//String nativeId = URIUtil
									port.setDeviceLabel(String.format("%s-%s-StoragePort", protectionDomainName, sdsId));
									port.setPortName(sdsId);
									port.setPortNetworkId(sdsId);
									port.setStorageSystemId(storageSystem.getNativeId());
									port.setTransportType(StoragePort.TransportType.ScaleIO);
									port.setOperationalStatus(StoragePort.OperationalStatus.OK);
									port.setIpAddress(sdsIP);
									port.setPortGroup(sdsId);
									port.setPortType(StoragePort.PortType.frontend);

									storagePorts.add(port);
								}
							}
						}
					}
					task.setStatus(DriverTask.TaskStatus.READY);
				}  else {
					task.setStatus(DriverTask.TaskStatus.FAILED);
				}
		} catch ( Exception e) {
			_log.error("Exception was encountered when attempting to discover storage ports for storage system {}",storageSystem.getNativeId());
			task.setStatus(DriverTask.TaskStatus.ABORTED);
		}
		return task;
	}

	@Override
	public DriverTask getStorageVolumes(StorageSystem storageSystem, List<StorageVolume> storageVolumes, MutableInt token) {
		return null;
	}

	@Override
	public List<String> getSystemTypes() {
		return null;
	}

	@Override
	public DriverTask getTask(String taskId) {
		return null;
	}

	@Override
	public <T extends StorageObject> T getStorageObject(String storageSystemId, String objectId, Class<T> type) {
		return null;
	}
}