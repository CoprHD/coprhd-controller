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
import com.emc.storageos.storagedriver.AbstractStorageDriver;
import com.emc.storageos.storagedriver.BlockStorageDriver;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.HostExportInfo;
import com.emc.storageos.storagedriver.RegistrationData;
import com.emc.storageos.storagedriver.impl.InMemoryRegistryImpl;
import com.emc.storageos.storagedriver.model.*;
import com.emc.storageos.storagedriver.model.StoragePool.Protocols;
import com.emc.storageos.storagedriver.model.StoragePool.RaidLevels;
import com.emc.storageos.storagedriver.model.StoragePool.SupportedDriveTypes;
import com.emc.storageos.storagedriver.model.StorageSystem.SupportedProvisioningType;
import com.emc.storageos.storagedriver.storagecapabilities.CapabilityInstance;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;

public class IBMSVCStorageDriver extends AbstractStorageDriver implements BlockStorageDriver {

	private static final Logger _log = LoggerFactory.getLogger(IBMSVCStorageDriver.class);

	/*
	 * Connection Manager for managing connection pool
	 */
	private ConnectionManager connectionManager = null;

	private Map<String, SSHConnection> IBMSVCSSHClientMap = null;

	private Object syncObject = new Object();

	private boolean testFlag = true;

	private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);

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

		IBMSVCSSHClientMap = new ConcurrentHashMap<String, SSHConnection>();
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
	public <T extends StorageObject> T getStorageObject(String storageSystemId, String objectId, Class<T> type) {
		/*
		 * if (StorageVolume.class.getSimpleName().equals(type.getSimpleName()))
		 * { } StorageVolume obj = new StorageVolume();
		 * obj.setAllocatedCapacity(200L); return (T) obj;
		 */
		return null;
	}

	@Override
	public DriverTask discoverStorageSystem(List<StorageSystem> storageSystems) {

		DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_DISCOVER_STORAGE_SYSTEM);

		for (StorageSystem storageSystem : storageSystems) {

			try {

				_log.info("discoverStorageSystem() information for storage system {}, name {} - start",
						storageSystem.getIpAddress(), storageSystem.getSystemName());

				ConnectionInfo connectionInfo = new ConnectionInfo(storageSystem.getIpAddress(),
						storageSystem.getPortNumber(), storageSystem.getUsername(), storageSystem.getPassword());

				SSHConnection connection = (SSHConnection) connectionManager.getConnection(connectionInfo);

				IBMSVCQueryStorageSystemResult result = IBMSVCCLI.queryStorageSystem(connection);

				if (result.isSuccess()) {

					_log.info(String.format("Processing storage system %s.", storageSystem.getIpAddress()));

					storageSystem.setSerialNumber(result.getProperty("SerialNumber"));
					storageSystem.setFirmwareVersion(result.getProperty("FirmwareVersion"));
					storageSystem.setIpAddress(result.getProperty("IpAddress"));
					storageSystem.setModel(result.getProperty("Model"));
					storageSystem.setNativeId(result.getProperty("SerialNumber"));
					storageSystem.setProvisioningType(SupportedProvisioningType.THIN_AND_THICK);
					storageSystem.setAccessStatus(StorageObject.AccessStatus.READ_WRITE);
					storageSystem.setMajorVersion(result.getProperty("MajorVersion"));
					storageSystem.setMinorVersion(result.getProperty("MinorVersion"));
					storageSystem.setIsSupportedVersion(true);

					IBMSVCSSHClientMap.put(storageSystem.getNativeId(), connection);

					setConnInfoToRegistry(storageSystem.getNativeId(), storageSystem.getIpAddress(),
							storageSystem.getPortNumber(), storageSystem.getUsername(), storageSystem.getPassword());

					_log.info("Processed storage system %s.", storageSystem.getIpAddress());
					task.setMessage(
							String.format("Storage system %s discovery completed.", storageSystem.getIpAddress()));
					task.setStatus(DriverTask.TaskStatus.READY);

				} else {
					_log.error(String.format("Storage system discovery failed %s\n", result.getErrorString()),
							result.isSuccess());
					task.setMessage(String.format("Storage System %s discovery failed : ", storageSystem.getIpAddress())
							+ result.getErrorString());
					task.setStatus(DriverTask.TaskStatus.FAILED);
				}

			} catch (Exception e) {
				_log.error("Unable to query the storage system information for the host {}.\n",
						storageSystem.getSystemName());
				task.setMessage(String.format("Unable to query the storage system %s information for the host : ",
						storageSystem.getSystemName()) + e.getMessage());
				task.setStatus(DriverTask.TaskStatus.FAILED);
				e.printStackTrace();
			}
			_log.info("discoverStorageSystem() information for storage system {}, nativeId {} - end\n",
					storageSystem.getIpAddress(), storageSystem.getNativeId());
		}

		return task;
	}

	@Override
	public DriverTask discoverStoragePools(StorageSystem storageSystem, List<StoragePool> storagePools) {

		DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_DISCOVER_STORAGE_POOLS);

		_log.info("discoverStoragePools() information for storage system {}, name {} - start",
				storageSystem.getIpAddress(), storageSystem.getSystemName());

		try {
			SSHConnection connection = getClientBySystemId(storageSystem.getNativeId());

			IBMSVCQueryAllStoragePoolResult resultAllStoragePool = IBMSVCCLI.queryAllStoragePool(connection);

			if (resultAllStoragePool.isSuccess()) {

				for (StoragePool storagePool : resultAllStoragePool.getStoragePools()) {

					_log.info(String.format("Processing storage pool %s.", storagePool.getPoolName()));

					IBMSVCQueryStoragePoolResult resultStoragePool = IBMSVCCLI.queryStoragePool(connection,
							storagePool.getPoolName());

					if (resultAllStoragePool.isSuccess()) {

						storagePool.setNativeId(resultStoragePool.getProperty("PoolId"));
						storagePool.setStorageSystemId(storageSystem.getSerialNumber());

						Set<SupportedDriveTypes> supportedDriveTypes = new HashSet<SupportedDriveTypes>();

						for (String driveType : resultStoragePool.getSupportedDriveTypes()) {
							switch (driveType) {
							case "ssd":
								supportedDriveTypes.add(SupportedDriveTypes.SSD);
								break;
							case "enterprise":
								supportedDriveTypes.add(SupportedDriveTypes.FC);
								break;
							case "nearline":
								supportedDriveTypes.add(SupportedDriveTypes.SAS);
								supportedDriveTypes.add(SupportedDriveTypes.NL_SAS);
								break;
							}
						}
						storagePool.setSupportedDriveTypes(supportedDriveTypes);

						Set<Protocols> supportedProtocols = new HashSet<>();
						supportedProtocols.add(Protocols.iSCSI);
						supportedProtocols.add(Protocols.FC);
						storagePool.setProtocols(supportedProtocols);

						Set<RaidLevels> supportedRaidLevels = new HashSet<RaidLevels>();
						supportedRaidLevels.add(RaidLevels.RAID0);
						supportedRaidLevels.add(RaidLevels.RAID1);
						supportedRaidLevels.add(RaidLevels.RAID5);
						supportedRaidLevels.add(RaidLevels.RAID6);
						supportedRaidLevels.add(RaidLevels.RAID10);
						storagePool.setSupportedRaidLevels(supportedRaidLevels);

						storagePool.setPoolServiceType(StoragePool.PoolServiceType.block);
						storagePool.setSupportedResourceType(StoragePool.SupportedResourceType.THIN_AND_THICK);
						storagePool.setAccessStatus(StorageObject.AccessStatus.READ_WRITE);

						storagePool.setMinimumThickVolumeSize(IBMSVCDriverUtils.convertGBtoBytes("1.00GB"));
						storagePool.setMaximumThickVolumeSize(IBMSVCDriverUtils.convertGBtoBytes("100.00GB"));
						storagePool.setMinimumThinVolumeSize(IBMSVCDriverUtils.convertGBtoBytes("1.00GB"));
						storagePool.setMaximumThinVolumeSize(IBMSVCDriverUtils.convertGBtoBytes("100.00GB"));
						storagePools.add(storagePool);

						_log.info(String.format("Processed storage pool %s.\n", storagePool.getPoolName()));

					} else {
						_log.warn(String.format("Processing storage pool failed %s\n",
								resultStoragePool.getErrorString()), resultStoragePool.isSuccess());
						task.setMessage(String.format("Processing storage pool %s failed : ", storagePool.getPoolName())
								+ resultStoragePool.getErrorString());
						task.setStatus(DriverTask.TaskStatus.FAILED);
					}
				}

				task.setMessage(String.format("All storage pool discovery for the storage system %s completed.",
						storageSystem.getIpAddress()));
				task.setStatus(DriverTask.TaskStatus.READY);

			} else {
				_log.error(String.format("All storage pool discovery for the storage system failed %s\n",
						resultAllStoragePool.getErrorString()), resultAllStoragePool.isSuccess());
				task.setMessage(String.format("All storage pool discovery for the storage system %s failed : ",
						storageSystem.getIpAddress()) + resultAllStoragePool.getErrorString());
				task.setStatus(DriverTask.TaskStatus.FAILED);
			}

		} catch (Exception e) {
			_log.error("Unable to query the storage pools information for the host {}\n",
					storageSystem.getSystemName());
			task.setMessage(String.format("Unable to query the Storage Pools information for the host %s",
					storageSystem.getSystemName()) + e.getMessage());
			task.setStatus(DriverTask.TaskStatus.FAILED);
			e.printStackTrace();
		}

		_log.info("discoverStoragePools() information for storage system {}, nativeId {} - end\n",
				storageSystem.getIpAddress(), storageSystem.getNativeId());

		return task;
	}

	@Override
	public DriverTask discoverStoragePorts(StorageSystem storageSystem, List<StoragePort> storagePorts) {

		DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_DISCOVER_STORAGE_PORTS);

		_log.info("discoverStoragePorts() information for storage system {}, name {} - start",
				storageSystem.getIpAddress(), storageSystem.getSystemName());

		try {

			SSHConnection connection = getClientBySystemId(storageSystem.getNativeId());

			IBMSVCQueryAllClusterNodeResult resultClusterNodes = IBMSVCCLI.queryAllClusterNodes(connection);

			if (resultClusterNodes.isSuccess()) {

				for (IBMSVCClusterNode clusterNode : resultClusterNodes.getClusterNodes()) {

					_log.info(String.format("Processing all storage ports on node %s.", clusterNode.getNodeName()));

					IBMSVCQueryStoragePortResult resultStoragePort = IBMSVCCLI.queryStoragePort(connection,
							clusterNode.getNodeName());

					if (resultStoragePort.isSuccess()) {

						int i = 1;
						for (StoragePort storagePort : resultStoragePort.getStoragePorts()) {

							_log.info(String.format("Processing storage port %s.", storagePort.getPortNetworkId()));

							String portName = clusterNode.getNodeName() + ":" + i++;
							storagePort.setPortName(portName);
							storagePort.setPortGroup(clusterNode.getNodeName());
							storagePort.setPortType(StoragePort.PortType.backend);
							storagePort.setDeviceLabel(portName);
							storagePort.setDisplayName(portName);
							if (storagePort.getPortNetworkId().contains("iqn")) {
								storagePort.setTransportType(StoragePort.TransportType.IP);
							} else {
								storagePort.setTransportType(StoragePort.TransportType.FC);
							}
							storagePort.setAccessStatus(StorageObject.AccessStatus.READ_WRITE);

							storagePorts.add(storagePort);

							_log.info(String.format("Processed storage port %s.\n", storagePort.getPortNetworkId()));
						}

						task.setMessage(String.format("All storage pool discovery for the storage system %s completed.",
								storageSystem.getIpAddress()));
						task.setStatus(DriverTask.TaskStatus.READY);

					} else {
						_log.warn(String.format("Processing storage port for the cluster node failed %s\n",
								resultStoragePort.getErrorString()), resultClusterNodes.isSuccess());
						task.setMessage(String.format("Processing storage pool for the cluster node %s failed : ",
								clusterNode.getNodeName()) + resultStoragePort.getErrorString());
						task.setStatus(DriverTask.TaskStatus.FAILED);
						continue;
					}

					_log.info(String.format("Processed all storage port on node %s.\n", clusterNode.getNodeName()));
				}

			} else {
				_log.error(String.format("All storage port discovery for the storage system failed %s\n",
						resultClusterNodes.getErrorString()), resultClusterNodes.isSuccess());
				task.setMessage(String.format("All storage port discovery for the storage system %s failed : ",
						storageSystem.getIpAddress()) + resultClusterNodes.getErrorString());
				task.setStatus(DriverTask.TaskStatus.FAILED);
			}
		} catch (Exception e) {
			_log.error("Unable to query the storage ports information for the host {}\n",
					storageSystem.getSystemName());
			task.setMessage(String.format("Unable to query the Storage Ports information for the host %s",
					storageSystem.getSystemName()) + e.getMessage());
			task.setStatus(DriverTask.TaskStatus.FAILED);
			e.printStackTrace();
		}

		_log.info("discoverStoragePorts() information for storage system {}, nativeId {} - end\n",
				storageSystem.getIpAddress(), storageSystem.getNativeId());

		return task;
	}

	@Override
	public DriverTask discoverStorageHostComponents(StorageSystem storageSystem,
			List<StorageHostComponent> storageHosts) {

		DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_DISCOVER_STORAGE_HOSTS);

		_log.info("discoverStorageHostComponents() for storage system {} - start", storageSystem.getNativeId());

		try {

			SSHConnection connection = getClientBySystemId(storageSystem.getNativeId());

			IBMSVCQueryAllHostResult resultAllHost = IBMSVCCLI.queryAllHosts(connection);

			if (resultAllHost.isSuccess()) {

				_log.info(String.format("Queried all host information.\n"));

				for (IBMSVCHost host : resultAllHost.getHostList()) {

					IBMSVCQueryHostInitiatorResult resultHostInitiator = IBMSVCCLI.queryHostInitiator(connection,
							host.getHostId());

					if (resultHostInitiator.isSuccess()) {

						_log.info(String.format("Queried host initiator for host Id %s.\n",
								resultHostInitiator.getHostId()));

						Set<Initiator> hostInitiatorSet = new HashSet<>();

						for (Initiator initiator : resultHostInitiator.getHostInitiatorList())
							hostInitiatorSet.add(initiator);

						StorageHostComponent hostComponent = new StorageHostComponent();
						hostComponent.setNativeId(host.getHostId());
						hostComponent.setType("Host");
						hostComponent.setDisplayName(host.getHostName());
						hostComponent.setDeviceLabel(host.getHostName());
						hostComponent.setHostName(host.getHostName());
						hostComponent.setIsSupportedVersion(true);
						hostComponent.setInitiators(hostInitiatorSet);

						storageHosts.add(hostComponent);

						_log.info(String.format("Processed host Id (%s).\n", resultHostInitiator.getHostId()));
						task.setMessage(String.format("Processed the host Id (%s).", resultHostInitiator.getHostId()));
						task.setStatus(DriverTask.TaskStatus.READY);

					} else {
						_log.error(
								String.format("Querying host initiator for host Id %s failed %s\n",
										resultHostInitiator.getHostId(), resultHostInitiator.getErrorString()),
								resultHostInitiator.isSuccess());
						task.setMessage(String.format("Querying host initiator for host Id %s failed : ",
								resultHostInitiator.getHostId()) + resultHostInitiator.getErrorString());
						task.setStatus(DriverTask.TaskStatus.FAILED);
					}
				}

			} else {
				_log.error(String.format("Querying all host failed %s\n", resultAllHost.getErrorString()),
						resultAllHost.isSuccess());
				task.setMessage(String.format("Querying all host failed : %s", resultAllHost.getErrorString()));
				task.setStatus(DriverTask.TaskStatus.FAILED);
			}

		} catch (Exception e) {
			_log.error("Unable to query the hosts information on the storage system {}", storageSystem.getNativeId());
			task.setMessage(String.format("Unable to query the hosts information on the storage system %s",
					storageSystem.getNativeId()) + e.getMessage());
			task.setStatus(DriverTask.TaskStatus.FAILED);
			e.printStackTrace();
		}

		_log.info("discoverStorageHostComponents() for storage system {} - end", storageSystem.getNativeId());
		return task;
	}

	@Override
	public DriverTask getStorageVolumes(StorageSystem storageSystem, List<StorageVolume> storageVolumes,
			MutableInt token) {

		DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_GET_STORAGE_VOLUMES);

		_log.info("getStorageVolumes() information for storage system {}, name {} - start",
				storageSystem.getIpAddress(), storageSystem.getSystemName());

		try {
			SSHConnection connection = getClientBySystemId(storageSystem.getNativeId());

			IBMSVCQueryAllStorageVolumeResult resultAllStorageVolume = IBMSVCCLI.queryAllStorageVolumes(connection);

			if (resultAllStorageVolume.isSuccess()) {
				for (StorageVolume storageVolume : resultAllStorageVolume.getStorageVolumes()) {
					_log.info(String.format("Processing all storage volume %s.\n", storageVolume.getDeviceLabel()));

					// get each storage volume info
					storageVolumes.add(storageVolume);
					_log.info(String.format("Processed all storage volume %s.\n", storageVolume.getDeviceLabel()));
				}
				task.setMessage(String.format("All storage volumes discovery for the storage system %s completed.",
						storageSystem.getIpAddress()));
				task.setStatus(DriverTask.TaskStatus.READY);

			} else {
				_log.error(String.format("Processing all storage volume for the storage system failed %s\n",
						resultAllStorageVolume.getErrorString()), resultAllStorageVolume.isSuccess());
				task.setMessage(String.format("Processing all storage volume for the storage system %s failed : ",
						storageSystem.getIpAddress()) + resultAllStorageVolume.getErrorString());
				task.setStatus(DriverTask.TaskStatus.FAILED);
			}

		} catch (Exception e) {
			_log.error("Unable to query the storage volumes information for the host {}",
					storageSystem.getSystemName());
			task.setMessage(String.format("Unable to query the storage volumes information for the host %s",
					storageSystem.getSystemName()) + e.getMessage());
			task.setStatus(DriverTask.TaskStatus.FAILED);
			e.printStackTrace();
		}

		_log.info("getStorageVolumes() information for storage system {}, nativeId {} - end",
				storageSystem.getIpAddress(), storageSystem.getNativeId());

		return task;
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

		DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_CREATE_STORAGE_VOLUMES);

		for (StorageVolume storageVolume : volumes) {

			_log.info("createVolumes() for storage system {} - start", storageVolume.getStorageSystemId());

			try {
				SSHConnection connection = getClientBySystemId(storageVolume.getStorageSystemId());

				IBMSVCCreateVolumeResult result = IBMSVCCLI.createStorageVolumes(connection, storageVolume, false,
						false);

				if (result.isSuccess()) {
					_log.info(String.format("Processing create storage volume %s (%s) size %s.\n", result.getName(),
							result.getId(), result.getProvisionedCapacity()));

					IBMSVCGetVolumeResult resultGetVolume = IBMSVCCLI.queryStorageVolume(connection, result.getId());

					if (resultGetVolume.isSuccess()) {

						_log.info(String.format("Processing storage volume %s.\n",
								resultGetVolume.getProperty("VolumeId")));

						storageVolume.setWwn(resultGetVolume.getProperty("VolumeWWN"));
						storageVolume.setDeviceLabel(resultGetVolume.getProperty("VolumeName"));
						storageVolume.setDisplayName(resultGetVolume.getProperty("VolumeName"));
						Long capacity = IBMSVCDriverUtils
								.convertGBtoBytes(resultGetVolume.getProperty("VolumeCapacity"));
						storageVolume.setProvisionedCapacity(capacity);
						storageVolume.setAllocatedCapacity(capacity);

						_log.info(String.format("Processed storage volume %s \n",
								resultGetVolume.getProperty("VolumeId")));

					} else {
						_log.warn(String.format("Processing storage volume failed %s\n",
								resultGetVolume.getErrorString()), resultGetVolume.isSuccess());
					}
					storageVolume.setNativeId(result.getId());
					storageVolume.setAccessStatus(StorageObject.AccessStatus.READ_WRITE);
					result.setName(resultGetVolume.getProperty("VolumeName"));

					task.setMessage(String.format("Created storage volume %s (%s) size %s\n", result.getName(),
							result.getId(), result.getRequestedCapacity()));
					task.setStatus(DriverTask.TaskStatus.READY);

					_log.info(String.format("Created storage volume %s (%s) size %s.\n", result.getName(),
							result.getId(), result.getRequestedCapacity()));

				} else {
					_log.error(String.format("Creating storage volume for the storage system failed %s\n",
							result.getErrorString()), result.isSuccess());
					task.setMessage(String.format("Creating storage volume for the storage system %s failed : ",
							storageVolume.getStorageSystemId()) + result.getErrorString());
					task.setStatus(DriverTask.TaskStatus.FAILED);
				}
			} catch (Exception e) {
				_log.error("Unable to create the storage volume {} on the storage system {}",
						storageVolume.getDeviceLabel(), storageVolume.getStorageSystemId());
				task.setMessage(String.format("Unable to create the storage volume %s on the storage system %s",
						storageVolume.getDeviceLabel(), storageVolume.getStorageSystemId()) + e.getMessage());
				task.setStatus(DriverTask.TaskStatus.FAILED);
				e.printStackTrace();
			}
			_log.info("createVolumes() for storage system {} - end", storageVolume.getStorageSystemId());
		}

		return task;
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

		DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_EXPAND_STORAGE_VOLUMES);

		_log.info("expandVolume() for storage system {} - start", storageVolume.getStorageSystemId());

		try {
			SSHConnection connection = getClientBySystemId(storageVolume.getStorageSystemId());

			String newVolumeCapacity = String.valueOf(newCapacity);

			if (newCapacity > storageVolume.getProvisionedCapacity()) {

				IBMSVCExpandVolumeResult result = IBMSVCCLI.expandStorageVolumes(connection,
						storageVolume.getNativeId(), newVolumeCapacity);

				if (result.isSuccess()) {
					_log.info(String.format("Expanded storage volume Id (%s) size %s.\n", result.getId(),
							result.getRequestedNewSize()));
					task.setMessage(String.format("Expanded storage volume Id (%s) size %s.", result.getId(),
							result.getRequestedNewSize()));
					task.setStatus(DriverTask.TaskStatus.READY);
				} else {
					_log.error(String.format("Expanding storage volume Id (%s) failed %s\n",
							storageVolume.getNativeId(), result.getErrorString()), result.isSuccess());
					task.setMessage(
							String.format("Expanding storage volume Id (%s) failed : ", storageVolume.getNativeId())
									+ result.getErrorString());
					task.setStatus(DriverTask.TaskStatus.FAILED);
				}

			} else {
				_log.info(String.format("Expansion size is less than the existing volume size %s for the Volume %s.\n",
						newVolumeCapacity, storageVolume.getNativeId()));
				task.setMessage(
						String.format("Expansion size is less than the existing volume size %s for the Volume %s",
								newVolumeCapacity, storageVolume.getNativeId()));
				task.setStatus(DriverTask.TaskStatus.FAILED);
			}
		} catch (Exception e) {
			_log.error("Unable to expand the storage volume {} on the storage system {}",
					storageVolume.getDeviceLabel(), storageVolume.getStorageSystemId());
			task.setMessage(String.format("Unable to expand the storage volume %s on the storage system %s",
					storageVolume.getDeviceLabel(), storageVolume.getStorageSystemId()) + e.getMessage());
			task.setStatus(DriverTask.TaskStatus.FAILED);
			e.printStackTrace();
		}

		_log.info("expandVolume() for storage system {} - end", storageVolume.getStorageSystemId());

		return task;
	}

	/**
	 * Delete volumes.
	 * 
	 * @param volumes
	 *            Volumes to delete.
	 * @return task
	 */
	@Override
	public DriverTask deleteVolumes(List<StorageVolume> volumes) {

		DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_DELETE_STORAGE_VOLUMES);

		for (StorageVolume storageVolume : volumes) {

			_log.info("deleteVolumes() for storage system {} - start", storageVolume.getStorageSystemId());

			try {
				SSHConnection connection = getClientBySystemId(storageVolume.getStorageSystemId());

				IBMSVCDeleteVolumeResult result = IBMSVCCLI.deleteStorageVolumes(connection,
						storageVolume.getNativeId());

				if (result.isSuccess()) {
					_log.info(String.format("Deleted storage volume Id %s.\n", result.getId()));
					task.setMessage(String.format("Deleted storage volume Id %s.", result.getId()));
					task.setStatus(DriverTask.TaskStatus.READY);

				} else {
					_log.error(String.format("Deleting storage volume failed %s\n", result.getErrorString()),
							result.isSuccess());
					task.setMessage(String.format("Deleting storage volume Id %s failed : ", result.getId())
							+ result.getErrorString());
					task.setStatus(DriverTask.TaskStatus.FAILED);
				}
			} catch (Exception e) {
				_log.error("Unable to delete the storage volume {} on the storage system {}",
						storageVolume.getDeviceLabel(), storageVolume.getStorageSystemId());
				task.setMessage(String.format("Unable to delete the storage volume %s on the storage system %s",
						storageVolume.getDeviceLabel(), storageVolume.getStorageSystemId()) + e.getMessage());
				task.setStatus(DriverTask.TaskStatus.FAILED);
				e.printStackTrace();
			}

			_log.info("deleteVolumes() for storage system {} - end", storageVolume.getStorageSystemId());
		}
		return task;
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

		DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_EXPORT_STORAGE_VOLUMES);

		for (StorageVolume storageVolume : volumes) {

			_log.info("exportVolumesToInitiators() for storage system {} - start", storageVolume.getStorageSystemId());

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

						_log.info("Exporting the volume Id {} to the host {}", storageVolume.getNativeId(), hostName);

						IBMSVCExportVolumeResult result = IBMSVCCLI.exportStorageVolumes(connection,
								storageVolume.getNativeId(), storageVolume.getDeviceLabel(), hostName);

						if (result.isSuccess()) {
							_log.info(String.format("Exported the storage volume %s to the host %s.\n",
									storageVolume.getDeviceLabel(), result.getHostName()));
							task.setMessage(String.format("Exported the storage volume %s to the host %s.",
									storageVolume.getDeviceLabel(), result.getHostName()));
							task.setStatus(DriverTask.TaskStatus.READY);
						} else {
							_log.error(String.format("Export storage volume %s to the host %s failed %s\n",
									storageVolume.getDeviceLabel(), result.getHostName(), result.getErrorString()),
									result.isSuccess());
							task.setMessage(String.format("Export storage volume %s to the host %s failed : ",
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
				_log.error("Unable to export the storage volume {} on the storage system {}",
						storageVolume.getDeviceLabel(), storageVolume.getStorageSystemId());
				task.setMessage(String.format("Unable to export the storage volume %s on the storage system %s",
						storageVolume.getDeviceLabel(), storageVolume.getStorageSystemId()) + e.getMessage());
				task.setStatus(DriverTask.TaskStatus.FAILED);
				e.printStackTrace();
			}

			_log.info("exportVolumesToInitiators() for storage system {} - end", storageVolume.getStorageSystemId());
		}
		return task;
	}

	private List<Initiator> getIBMSVCHostInitiatorList(SSHConnection connection, String storageSystemId) {

		_log.info("getIBMSVCHostInitiatorList() for storage system {} - start", storageSystemId);

		List<Initiator> hostInitiatorList = new ArrayList<>();

		IBMSVCQueryAllHostResult resultAllHost = IBMSVCCLI.queryAllHosts(connection);

		if (resultAllHost.isSuccess()) {
			_log.info(String.format("Queried all host information.\n"));

			for (IBMSVCHost host : resultAllHost.getHostList()) {

				IBMSVCQueryHostInitiatorResult resultHostInitiator = IBMSVCCLI.queryHostInitiator(connection,
						host.getHostId());

				if (resultHostInitiator.isSuccess()) {

					_log.info(
							String.format("Queried host initiator for host Id %s.\n", resultHostInitiator.getHostId()));

					for (Initiator initiator : resultHostInitiator.getHostInitiatorList()) {
						hostInitiatorList.add(initiator);
					}
				} else {
					_log.error(String.format("Querying host initiator for host failed %s\n",
							resultHostInitiator.getErrorString()), resultHostInitiator.isSuccess());
				}
			}
		} else {
			_log.error(String.format("Querying all host failed %s\n", resultAllHost.getErrorString()),
					resultAllHost.isSuccess());
		}
		_log.info("getIBMSVCHostInitiatorList() for storage system {} - end", storageSystemId);

		return hostInitiatorList;
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
							_log.info(String.format("UnExported the storage volume %s to the host %s.\n",
									storageVolume.getDeviceLabel(), result.getHostName()));
							task.setMessage(String.format("UnExported the storage volume %s to the host %s.",
									storageVolume.getDeviceLabel(), result.getHostName()));
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

	/**
	 * Create volume snapshots.
	 *
	 * Algorithm for the Snapshot Volume Creation
	 *
	 * 1. Get the Source Volume details like fc_map_count, se_copy_count,
	 * copy_count As each Snapshot has an Max of 256 FC Mappings only for each
	 * source volume 2. Create a new Snapshot Volume with details supplied 3.
	 * Create FC Mapping for the source and target volume 4. Prepare the Start
	 * of FC Mapping 5. Retry the Query of FC Mapping status till Maximum Tries
	 * reaches 6. If the FC Mapping status is "unknown" then set task as Failed
	 * and return 7. If the FC Mapping status is "stopped" then again Prepare
	 * the Start of FC Mapping. Repeat Step 4 8. If the FC Mapping status is
	 * "prepared" then Start the FC Mapping
	 *
	 * @param snapshots
	 *            Type: Input/Output.
	 * @param capabilities
	 *            capabilities required from snapshots. Type: Input.
	 * @return task
	 */
	@Override
	public DriverTask createVolumeSnapshot(List<VolumeSnapshot> snapshots, StorageCapabilities capabilities) {

		DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_CREATE_SNAPSHOT_VOLUMES);

		for (VolumeSnapshot volumeSnapshot : snapshots) {

			_log.info("createVolumeSnapshot() for storage system {} - start", volumeSnapshot.getStorageSystemId());

			try {
				SSHConnection connection = getClientBySystemId(volumeSnapshot.getStorageSystemId());

				// 1. Get the Source Volume details like fc_map_count,
				// se_copy_count, copy_count
				// As each Snapshot has an Max of 256 FC Mappings only for each
				// source volume
				IBMSVCGetVolumeResult resultGetVolume = IBMSVCCLI.queryStorageVolume(connection,
						volumeSnapshot.getParentId());

				if (resultGetVolume.isSuccess()) {

					_log.info(String.format("Processing storage volume Id %s.\n",
							resultGetVolume.getProperty("VolumeId")));

					boolean createMirrorCopy = false;

					String sourceVolumeName = resultGetVolume.getProperty("VolumeName");

					int se_copy_count = Integer.parseInt(resultGetVolume.getProperty("SECopyCount"));
					int copy_count = Integer.parseInt(resultGetVolume.getProperty("CopyCount"));
					int fc_map_count = Integer.parseInt(resultGetVolume.getProperty("FCMapCount"));

					if (fc_map_count < IBMSVCConstants.MAX_SOURCE_MAPPINGS) {

						// Create the snapshot volume parameters
						StorageVolume targetStorageVolume = new StorageVolume();
						targetStorageVolume.setStorageSystemId(volumeSnapshot.getStorageSystemId());
						targetStorageVolume.setDeviceLabel(volumeSnapshot.getDeviceLabel());
						targetStorageVolume.setDisplayName(volumeSnapshot.getDisplayName());
						String timeStamp = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
						// targetStorageVolume.setDeviceLabel(resultGetVolume.getProperty("VolumeName")
						// + "_Snapshot_" + timeStamp);
						targetStorageVolume.setStoragePoolId(resultGetVolume.getProperty("PoolId"));
						targetStorageVolume.setRequestedCapacity(
								IBMSVCDriverUtils.convertGBtoBytes(resultGetVolume.getProperty("VolumeCapacity")));

						if (se_copy_count > 0) {
							targetStorageVolume.setThinlyProvisioned(true);
						}
						if (copy_count > 1) {
							createMirrorCopy = true;
						}
						_log.info(String.format("Processed storage volume Id %s.\n",
								resultGetVolume.getProperty("VolumeId")));

						// 2. Create a new Snapshot Volume with details supplied
						IBMSVCCreateVolumeResult resultCreateVol = IBMSVCCLI.createStorageVolumes(connection,
								targetStorageVolume, false, createMirrorCopy);

						if (resultCreateVol.isSuccess()) {
							_log.info(String.format("Created storage snapshot volume %s (%s) size %s\n",
									resultCreateVol.getName(), resultCreateVol.getId(),
									resultCreateVol.getRequestedCapacity()));

							targetStorageVolume.setNativeId(resultCreateVol.getId());

							IBMSVCGetVolumeResult resultGetSnapshotVolume = IBMSVCCLI.queryStorageVolume(connection,
									resultCreateVol.getId());

							if (resultGetSnapshotVolume.isSuccess()) {
								_log.info(String.format("Snapshot volume %s has been retrieved.\n",
										resultGetSnapshotVolume.getProperty("VolumeId")));
								targetStorageVolume.setWwn(resultGetSnapshotVolume.getProperty("VolumeWWN"));
								volumeSnapshot.setWwn(resultGetSnapshotVolume.getProperty("VolumeWWN"));
							} else {
								_log.warn(String.format("Snapshot volume %s retrieval failed %s\n",
										resultGetSnapshotVolume.getProperty("VolumeId"),
										resultGetSnapshotVolume.getErrorString()));
							}

							volumeSnapshot.setNativeId(resultCreateVol.getId());
							volumeSnapshot.setDeviceLabel(resultCreateVol.getName());
							volumeSnapshot.setDisplayName(resultCreateVol.getName());
							volumeSnapshot.setAccessStatus(StorageObject.AccessStatus.READ_WRITE);
							//volumeSnapshot.setTimestamp(timeStamp);

							String targetVolumeName = volumeSnapshot.getDeviceLabel();

							// 3. Create FC Mapping for the source and target
							// volume
							// Set the fullCopy to false to indicate its Volume
							// Snapshot
							IBMSVCCreateFCMappingResult resultFCMapping = IBMSVCCLI.createFCMapping(connection,
									sourceVolumeName, targetVolumeName, null, false);

							if (resultFCMapping.isSuccess()) {
								_log.info(String.format("Created flashCopy mapping %s\n", resultFCMapping.getId()));

								// 4. Prepare the Start of FC Mapping
								IBMSVCPreStartFCMappingResult resultPreStartFCMapping = IBMSVCCLI
										.preStartFCMapping(connection, resultFCMapping.getId());

								if (resultPreStartFCMapping.isSuccess()) {
									_log.info(String.format("Prepared to start flashCopy mapping %s\n",
											resultPreStartFCMapping.getId()));

									boolean mapping_ready = false;

									int wait_time = 5;
									int max_retries = (IBMSVCConstants.FC_MAPPING_QUERY_TIMEOUT / wait_time) + 1;

									// 5. Retry the Query of FC Mapping status
									// till Maximum Tries reaches
									label: for (int i = 1; i <= max_retries; i++) {

										IBMSVCQueryFCMappingResult resultQueryFCMapping = IBMSVCCLI.queryFCMapping(
												connection, resultPreStartFCMapping.getId(), false, null, null);

										if (resultQueryFCMapping.isSuccess()) {
											_log.info(String.format("Queried flashCopy mapping %s\n",
													resultQueryFCMapping.getId()));

											String fcMapStatus = resultQueryFCMapping.getProperty("FCMapStatus");

											// 6. If the FC Mapping status is
											// "unknown" then set task as Failed
											// and return
											switch (fcMapStatus) {
											case "unknown":
											case "preparing":
												_log.warn(String.format(
														"Unexpected flashCopy mapping Id %s with status %s\n",
														resultQueryFCMapping.getId(), fcMapStatus));
												task.setMessage(String.format(
														"Unexpected flashCopy mapping Id %s with status %s.",
														resultQueryFCMapping.getId(), fcMapStatus));
												task.setStatus(DriverTask.TaskStatus.FAILED);
												break label;
											case "stopped": // 7. If the FC
															// Mapping status is
															// "stopped" then
															// again Prepare the
															// Start of FC
															// Mapping. Repeat
															// Step 4
												preStartFCMapping(connection, resultFCMapping.getId());

												break;
											case "prepared":
												mapping_ready = true;
												// 8. If the FC Mapping status
												// is "prepared" then Start the
												// FC Mapping
												startFCMapping(connection, resultFCMapping.getId());
												task.setMessage(String.format(
														"Created flashCopy mapping for the source volume %s and the target volume %s.",
														sourceVolumeName, targetVolumeName));
												task.setStatus(DriverTask.TaskStatus.READY);
												break label;
											}

										} else {
											_log.warn(String.format("Querying flashCopy mapping Id %s failed %s\n",
													resultQueryFCMapping.getId(),
													resultQueryFCMapping.getErrorString()));
										}

										SECONDS.sleep(5);
									}

									if (!mapping_ready) {
										_log.warn(String.format(
												"Preparing for flashCopy mapping Id %s failed to complete within the allocated %s seconds timeout. Terminating. %s\n",
												resultPreStartFCMapping.getId(),
												IBMSVCConstants.FC_MAPPING_QUERY_TIMEOUT,
												resultPreStartFCMapping.getErrorString()));

										_log.error(String.format(
												"Cleaning up the snapshot volume %s and FC Mapping Id %s.",
												resultCreateVol.getName(), resultPreStartFCMapping.getId()));

										// deleteFCMapping(connection,
										// resultPreStartFCMapping.getId());
										/**
										 * Deleting volume stops and deletes all
										 * the related FC mappings to that
										 * volume And finally deletes the volume
										 */
										deleteStorageVolumes(connection, resultCreateVol.getId());

										_log.error(String.format(
												"Cleaned up the snapshot volume %s and flashCopy mapping Id %s.",
												resultCreateVol.getName(), resultPreStartFCMapping.getId()));

										task.setMessage(String.format(
												"Preparing for flashCopy mapping Id %s failed to complete within the allocated %d seconds timeout. Terminating. %s\n",
												resultPreStartFCMapping.getId(),
												IBMSVCConstants.FC_MAPPING_QUERY_TIMEOUT,
												resultPreStartFCMapping.getErrorString()));

										task.setStatus(DriverTask.TaskStatus.FAILED);

									}

								} else {
									_log.warn(String.format("Preparing for flashCopy mapping Id %s failed %s\n",
											resultPreStartFCMapping.getId(), resultPreStartFCMapping.getErrorString()));

									_log.error(String.format("Cleaning up the snapshot volume %s and FC Mapping Id %s.",
											resultCreateVol.getName(), resultPreStartFCMapping.getId()));

									// stopFCMapping(connection,
									// resultPreStartFCMapping.getId());
									// deleteFCMapping(connection,
									// resultPreStartFCMapping.getId());
									/**
									 * Deleting volume stops and deletes all the
									 * related FC mappings to that volume And
									 * finally deletes the volume
									 */
									deleteStorageVolumes(connection, resultCreateVol.getId());

									_log.error(String.format(
											"Cleaned up the snapshot volume %s and flashCopy mapping Id %s.",
											resultCreateVol.getName(), resultPreStartFCMapping.getId()));

									task.setMessage(String.format("Preparing for flashCopy mapping Id %s failed : %s",
											resultPreStartFCMapping.getId(), resultPreStartFCMapping.getErrorString()));

									task.setStatus(DriverTask.TaskStatus.FAILED);
								}

							} else {
								_log.error(String.format("Creating flashCopy mapping failed %s",
										resultFCMapping.getErrorString()), resultFCMapping.isSuccess());

								_log.error(String.format("Cleaning up the snapshot volume %s.",
										resultCreateVol.getName()));

								/**
								 * Deleting volume stops and deletes all the
								 * related FC mappings to that volume And
								 * finally deletes the volume
								 */
								deleteStorageVolumes(connection, resultCreateVol.getId());

								_log.error(
										String.format("Cleaned up the snapshot volume %s.", resultCreateVol.getName()));

								task.setMessage(String.format(
										"Creating flashCopy mapping for the source volume %s and the target volume %s failed : %s.",
										sourceVolumeName, targetVolumeName, resultFCMapping.getErrorString()));
								task.setStatus(DriverTask.TaskStatus.FAILED);
							}

						} else {
							_log.error(String.format("Creating storage snapshot volume failed %s\n",
									resultCreateVol.getErrorString()), resultCreateVol.isSuccess());
							task.setMessage(
									String.format("Unable to create the snapshot volume %s on the storage system %s",
											volumeSnapshot.getDeviceLabel(), volumeSnapshot.getStorageSystemId())
									+ resultCreateVol.getErrorString());
							task.setStatus(DriverTask.TaskStatus.FAILED);
						}

					} else {
						_log.error(String.format("FlashCopy mapping has reached the maximum for the source volume %s\n",
								resultGetVolume.getProperty("VolumeName")));
						task.setMessage(
								String.format("FlashCopy mapping has reached the maximum for the source volume %s",
										resultGetVolume.getProperty("VolumeName")) + resultGetVolume.getErrorString());
						task.setStatus(DriverTask.TaskStatus.FAILED);
					}

				} else {
					_log.error(String.format("Processing get storage volume Id %s failed %s\n",
							resultGetVolume.getProperty("VolumeId"), resultGetVolume.getErrorString()));
					task.setMessage(String.format("Processing get storage volume failed : %s",
							resultGetVolume.getProperty("VolumeId")) + resultGetVolume.getErrorString());
					task.setStatus(DriverTask.TaskStatus.FAILED);
				}
			} catch (Exception e) {
				_log.error("Unable to create the snapshot volume {} on the storage system {}",
						volumeSnapshot.getParentId(), volumeSnapshot.getStorageSystemId());
				task.setMessage(String.format("Unable to create the snapshot volume %s on the storage system %s",
						volumeSnapshot.getDeviceLabel(), volumeSnapshot.getStorageSystemId()) + e.getMessage());
				task.setStatus(DriverTask.TaskStatus.FAILED);
				e.printStackTrace();
			}

			_log.info("createVolumeSnapshot() for storage system {} - end", volumeSnapshot.getStorageSystemId());
		}

		return task;
	}

	/**
	 * Prepare for Starting FC Mapping
	 *
	 * @param connection
	 *            SSH Connection to the Storage System
	 * @param fcMappingId
	 *            FC Mapping ID to be prepared
	 */
	private void preStartFCMapping(SSHConnection connection, String fcMappingId) {
		IBMSVCPreStartFCMappingResult resultPreStartFCMapping = IBMSVCCLI.preStartFCMapping(connection, fcMappingId);
		if (resultPreStartFCMapping.isSuccess()) {
			_log.info(String.format("Prepared to start flashCopy mapping %s\n", resultPreStartFCMapping.getId()));
		} else {
			_log.warn(String.format("Preparing to start flashCopy mapping Id %s failed : %s.\n",
					resultPreStartFCMapping.getId(), resultPreStartFCMapping.getErrorString()));
		}
	}

	/**
	 * Start FC Mapping
	 *
	 * @param connection
	 *            SSH Connection to the Storage System
	 * @param fcMappingId
	 *            FC Mapping ID to be started
	 */
	private void startFCMapping(SSHConnection connection, String fcMappingId) {
		IBMSVCStartFCMappingResult resultStartFCMapping = IBMSVCCLI.startFCMapping(connection, fcMappingId);
		if (resultStartFCMapping.isSuccess()) {
			_log.info(String.format("Started flashCopy mapping %s\n", resultStartFCMapping.getId()));
		} else {
			_log.warn(String.format("Starting flashCopy mapping Id %s failed : %s.\n", resultStartFCMapping.getId(),
					resultStartFCMapping.getErrorString()));
		}
	}

	/**
	 * Stopping FC Mapping
	 *
	 * @param connection
	 *            SSH Connection to the Storage System
	 * @param fcMappingId
	 *            FC Mapping ID to be stopped
	 */
	private void stopFCMapping(SSHConnection connection, String fcMappingId) {
		// Remove the FC Mapping and delete the snapshot volume
		IBMSVCStopFCMappingResult resultStopFCMapping = IBMSVCCLI.stopFCMapping(connection, fcMappingId);
		if (resultStopFCMapping.isSuccess()) {
			_log.info(String.format("Stopped flashCopy mapping %s\n", resultStopFCMapping.getId()));
		} else {
			_log.warn(String.format("Stopping flashCopy mapping Id %s failed : %s.\n", resultStopFCMapping.getId(),
					resultStopFCMapping.getErrorString()));
		}
	}

	/**
	 * Deleting the FC Mapping
	 *
	 * @param connection
	 *            SSH Connection to the Storage System
	 * @param fcMappingId
	 *            FC Mapping ID to be deleted
	 */
	private void deleteFCMapping(SSHConnection connection, String fcMappingId) {
		// Remove the FC Mapping and delete the snapshot volume
		IBMSVCDeleteFCMappingResult resultDeleteFCMapping = IBMSVCCLI.deleteFCMapping(connection, fcMappingId);
		if (resultDeleteFCMapping.isSuccess()) {
			_log.info(String.format("Deleted flashCopy mapping Id %s\n", resultDeleteFCMapping.getId()));
		} else {
			_log.warn(String.format("Deleting flashCopy mapping Id %s failed : %s.\n", resultDeleteFCMapping.getId(),
					resultDeleteFCMapping.getErrorString()));
		}
	}

	/**
	 * Delete the Storage Volumes created during the snapshot creation
	 *
	 * @param connection
	 *            SSH Connection to the Storage System
	 * @param volumeId
	 *            Volume ID to be deleted
	 */
	private boolean deleteStorageVolumes(SSHConnection connection, String volumeId) {
		IBMSVCDeleteVolumeResult resultDelVol = IBMSVCCLI.deleteStorageVolumes(connection, volumeId);

		if (resultDelVol.isSuccess()) {
			_log.info(String.format("Deleted storage snapshot volume %s.\n", resultDelVol.getId()));
			return true;
		} else {
			_log.error(String.format("Deleting storage snapshot volume failed %s\n", resultDelVol.getErrorString()),
					resultDelVol.isSuccess());
			return false;
		}
	}

	/**
	 * Restore volume to snapshot state.
	 *
	 * @param storageVolume
	 *            Type: Input/Output.
	 * @param volumeSnapshot
	 *            Type: Input.
	 * @return task
	 */
	public DriverTask restoreSnapshot(StorageVolume storageVolume, VolumeSnapshot volumeSnapshot) {

		DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_RESTORE_SNAPSHOT_VOLUMES);

		_log.info("restoreSnapshot() for storage system {} - start", storageVolume.getStorageSystemId());

		try {
			SSHConnection connection = getClientBySystemId(storageVolume.getStorageSystemId());

			// 1. Get the Source Volume details like fc_map_count,
			// se_copy_count, copy_count
			// As each Snapshot has an Max of 256 FC Mappings only for each
			// source volume
			IBMSVCGetVolumeResult resultQueryVolume = IBMSVCCLI.queryStorageVolume(connection,
					volumeSnapshot.getParentId());

			if (resultQueryVolume.isSuccess()) {

				_log.info(String.format("Processing snapshot volume Id %s.\n",
						resultQueryVolume.getProperty("VolumeId")));

				int fc_map_count = Integer.parseInt(resultQueryVolume.getProperty("FCMapCount"));

				if (fc_map_count < IBMSVCConstants.MAX_SOURCE_MAPPINGS) {

					String sourceVolumeName = volumeSnapshot.getDeviceLabel();
					String targetVolumeName = storageVolume.getDeviceLabel();

					// 2. Create FC Mapping for the source and target volume
					// Set the fullCopy to true to indicate its Restore Volume
					// Snapshot
					IBMSVCCreateFCMappingResult resultFCMapping = IBMSVCCLI.createFCMapping(connection,
							sourceVolumeName, targetVolumeName, null, true);

					if (resultFCMapping.isSuccess()) {
						_log.info(String.format("Created flashCopy mapping %s\n", resultFCMapping.getId()));

						IBMSVCStartFCMappingResult resultStartFCMapping = IBMSVCCLI.startFCMapping(connection,
								resultFCMapping.getId());

						if (resultStartFCMapping.isSuccess()) {
							_log.info(String.format(
									"Started flashCopy mapping Id %s for the source volume %s and the target volume %s.\n",
									resultStartFCMapping.getId(), sourceVolumeName, targetVolumeName));
							task.setMessage(String.format(
									"Started flashCopy mapping Id %s for the source volume %s and the target volume %s.",
									resultStartFCMapping.getId(), sourceVolumeName, targetVolumeName)
									+ resultFCMapping.getErrorString());
							task.setStatus(DriverTask.TaskStatus.READY);
						} else {
							_log.error(
									String.format(
											"Starting flashCopy mapping for the source volume %s and the target volume %s failed : %s",
											sourceVolumeName, targetVolumeName, resultFCMapping.getErrorString()),
									resultFCMapping.isSuccess());
							task.setMessage(String.format(
									"Starting flashCopy mapping for the source volume %s and the target volume %s failed : %s.",
									sourceVolumeName, targetVolumeName, resultFCMapping.getErrorString()));
							task.setStatus(DriverTask.TaskStatus.FAILED);
						}

					} else {
						_log.error(
								String.format(
										"Creating flashCopy mapping for the source volume %s and the target volume %s failed : %s",
										sourceVolumeName, targetVolumeName, resultFCMapping.getErrorString()),
								resultFCMapping.isSuccess());
						task.setMessage(String.format(
								"Creating flashCopy mapping for the source volume %s and the target volume %s failed : %s.",
								sourceVolumeName, targetVolumeName, resultFCMapping.getErrorString()));
						task.setStatus(DriverTask.TaskStatus.FAILED);
					}

				} else {
					_log.error(String.format("FlashCopy mapping has reached the maximum for the source volume %s\n",
							resultQueryVolume.getProperty("VolumeName")));
					task.setMessage(String.format("FlashCopy mapping has reached the maximum for the source volume %s",
							resultQueryVolume.getProperty("VolumeName")) + resultQueryVolume.getErrorString());
					task.setStatus(DriverTask.TaskStatus.FAILED);
				}

			} else {
				_log.error(String.format("Querying storage volume %s failed : %s\n", volumeSnapshot.getParentId(),
						resultQueryVolume.getErrorString()), resultQueryVolume.isSuccess());
				task.setMessage(String.format("Querying storage volume %s failed : %s.", volumeSnapshot.getParentId(),
						resultQueryVolume.getErrorString()));
				task.setStatus(DriverTask.TaskStatus.FAILED);
			}

		} catch (Exception e) {
			_log.error("Unable to restore the snapshot volume {} on the storage system {}",
					volumeSnapshot.getParentId(), volumeSnapshot.getStorageSystemId());
			task.setMessage(String.format("Unable to restore the snapshot volume %s on the storage system %s",
					volumeSnapshot.getDeviceLabel(), volumeSnapshot.getStorageSystemId()) + e.getMessage());
			task.setStatus(DriverTask.TaskStatus.FAILED);
			e.printStackTrace();
		}

		_log.info("restoreSnapshot() for storage system {} - end", storageVolume.getStorageSystemId());

		return task;
	}

	/**
	 * Delete snapshots.
	 *
	 * Algorithm for the Snapshot Volume Creation
	 *
	 * 1. Ensures that volume is not part of FC mapping and deletes it. 2.
	 * Ensure volume has no FC mappings.
	 *
	 * @param snapshots
	 *            Type: Input.
	 * @return task
	 */
	@Override
	public DriverTask deleteVolumeSnapshot(List<VolumeSnapshot> snapshots) {

		DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_DELETE_SNAPSHOT_VOLUMES);

		for (VolumeSnapshot volumeSnapshot : snapshots) {

			_log.info("deleteVolumeSnapshot() for storage system {} - start", volumeSnapshot.getStorageSystemId());

			try {
				SSHConnection connection = getClientBySystemId(volumeSnapshot.getStorageSystemId());

				/**
				 * Create a timer thread. The default volume service heart beat
				 * is every 10 seconds. The flashCopy usually takes hours before
				 * it finishes. Don't set the sleep interval shorter than the
				 * heartbeat. Otherwise volume service heartbeat will not be
				 * serviced.
				 */
				checkVolumeFCMappings(task, volumeSnapshot, connection);

				// Wait for the checkVolumeFCMapping thread to notify
				scheduledExecutorService.wait();

				// Shutting down the executor service
				scheduledExecutorService.shutdown();

				_log.info(String.format("Deleting the snapshot volume Id %s\n", volumeSnapshot.getNativeId()));

				// Delete the Snapshot Volume
				IBMSVCDeleteVolumeResult resultDelVol = IBMSVCCLI.deleteStorageVolumes(connection,
						volumeSnapshot.getNativeId());

				if (resultDelVol.isSuccess()) {
					_log.info(String.format("Deleted snapshot volume Id %s.\n", resultDelVol.getId()));
					task.setMessage(String.format("Snapshot volume Id %s has been deleted.", resultDelVol.getId()));
					task.setStatus(DriverTask.TaskStatus.READY);
				} else {
					_log.error(String.format("Deleting snapshot volume Id %s failed : %s\n", resultDelVol.getId(),
							resultDelVol.getErrorString()), resultDelVol.isSuccess());
					task.setMessage(String.format("Deleting snapshot volume Id %s failed : %s", resultDelVol.getId(),
							resultDelVol.getErrorString()));
					task.setStatus(DriverTask.TaskStatus.FAILED);
					break;
				}

			} catch (Exception e) {
				_log.error("Unable to delete the snapshot volume {} on the storage system {}",
						volumeSnapshot.getParentId(), volumeSnapshot.getStorageSystemId());
				task.setMessage(String.format("Unable to delete the snapshot volume %s on the storage system %s",
						volumeSnapshot.getDeviceLabel(), volumeSnapshot.getStorageSystemId()) + e.getMessage());
				task.setStatus(DriverTask.TaskStatus.FAILED);
				e.printStackTrace();
			}

			_log.info("deleteVolumeSnapshot() for storage system {} - end", volumeSnapshot.getStorageSystemId());
		}
		return task;
	}

	private void checkVolumeFCMappings(DriverTask task, VolumeSnapshot volumeSnapshot, SSHConnection connection) {

		final Runnable chkVolFCMap = () -> {

			_log.info(String.format("Checking flashCopy mapping volume Id %s.\n", volumeSnapshot.getNativeId()));

			// 1. Query a Snapshot Volume for FC Mappings
			IBMSVCQueryVolumeFCMappingResult resultVolumeFCMap = IBMSVCCLI.queryVolumeFCMapping(connection,
					volumeSnapshot.getNativeId());

			if (resultVolumeFCMap.isSuccess()) {

				_log.info(String.format("Processing snapshot volume %s.\n", volumeSnapshot.getNativeId()));

				boolean wait_for_copy = false;

				for (Integer fcMappingInt : resultVolumeFCMap.getFcMappingIds()) {

					String fcMappingId = fcMappingInt.toString();

					IBMSVCQueryFCMappingResult resultQueryFCMapping = IBMSVCCLI.queryFCMapping(connection, fcMappingId,
							false, null, null);

					if (resultQueryFCMapping.isSuccess()) {

						_log.info(String.format("Queried flashCopy mapping %s\n", resultQueryFCMapping.getId()));

						String srcVolId = resultQueryFCMapping.getProperty("SourceVolId");
						String tgtVolId = resultQueryFCMapping.getProperty("TargetVolId");
						String status = resultQueryFCMapping.getProperty("FCMapStatus");
						String copyRate = "50";
						String autoDelete = "on";

						if (copyRate.equals("0")) {

							if (srcVolId.equals(volumeSnapshot.getNativeId())) {
								// Volume with snapshots. Return False if
								// snapshot not allowed
								IBMSVCChangeFCMappingResult resultChangeFCMapping = IBMSVCCLI
										.changeFCMapping(connection, fcMappingId, copyRate, autoDelete, null, null);

								if (resultChangeFCMapping.isSuccess()) {
									_log.info(String.format("Changed flashCopy mapping Id %s\n",
											resultChangeFCMapping.getFcMappingId()));
									wait_for_copy = true;
								} else {
									_log.warn(String.format("Changing flashCopy mapping Id %s failed %s\n",
											resultChangeFCMapping.getFcMappingId(),
											resultChangeFCMapping.getErrorString()));
								}

							} else {
								if (!tgtVolId.equals(volumeSnapshot.getNativeId())) {
									_log.info(String.format(
											"Snapshot volume Id %s not involved in mapping (%s)s -> (%s)s.\n ",
											volumeSnapshot.getNativeId(), srcVolId, tgtVolId));
									continue;
								}

								switch (status) {
								case "copying":
								case "prepared":
									stopFCMapping(connection, fcMappingId);
									// Need to wait for the fcmap to change to
									// stopped state before remove fcmap
									wait_for_copy = true;
									break;

								case "stopping":
								case "preparing":
									wait_for_copy = true;
									break;

								default:
									deleteFCMapping(connection, fcMappingId);
									break;
								}
							}
						} else {
							// Copy in progress - wait and will autodelete
							switch (status) {
							case "prepared":
								stopFCMapping(connection, fcMappingId);
								deleteFCMapping(connection, fcMappingId);
								break;
							case "idle_or_copied":
								deleteFCMapping(connection, fcMappingId);
								break;
							default:
								wait_for_copy = true;
								break;
							}
						}

					} else {
						_log.warn(String.format("Querying flashCopy mapping failed : %s.\n",
								resultQueryFCMapping.getId()));
						task.setMessage(String.format("Querying the snapshot volume Id %s failed : %s.",
								volumeSnapshot.getNativeId(), resultVolumeFCMap.getErrorString()));
						task.setStatus(DriverTask.TaskStatus.FAILED);
						break;
					}
				}
				if (!wait_for_copy || (resultVolumeFCMap.getFcMappingIds().size() == 0)) {
					notifyAll();
				}

			} else {
				_log.error(String.format("Querying the volume Id %s flashCopy mappings failed : %s.\n",
						volumeSnapshot.getNativeId(), resultVolumeFCMap.getErrorString()));

				task.setMessage(String.format("Querying the volume Id %s flashCopy mappings failed : %s.",
						volumeSnapshot.getNativeId(), resultVolumeFCMap.getErrorString()));

				task.setStatus(DriverTask.TaskStatus.FAILED);
				scheduledExecutorService.shutdown();
			}
		};

		final ScheduledFuture<?> chkVolFCMapHandle = scheduledExecutorService.scheduleWithFixedDelay(chkVolFCMap, 10,
				10, SECONDS);

		scheduledExecutorService.schedule(new Runnable() {
			public void run() {
				chkVolFCMapHandle.cancel(true);
			}
		}, 3 * 60, SECONDS);

	}

	@Override
	public DriverTask createConsistencyGroup(VolumeConsistencyGroup consistencyGroup) {

		DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_CREATE_FC_CONSISTGROUP);

		_log.info("createConsistencyGroup() for storage system {} - start", consistencyGroup.getStorageSystemId());

		try {

			SSHConnection connection = getClientBySystemId(consistencyGroup.getStorageSystemId());

			IBMSVCCreateFCConsistGrpResult result = IBMSVCCLI.createFCConsistGrp(connection,
					consistencyGroup.getDisplayName());

			if (result.isSuccess()) {
				_log.info(String.format("Created flashCopy consistency group %s with Id %s.\n",
						result.getConsistGrpName(), result.getConsistGrpId()));
				consistencyGroup.setNativeId(result.getConsistGrpId());
				consistencyGroup.setDeviceLabel(result.getConsistGrpName());
				task.setMessage(String.format("Created flashCopy consistency group %s with Id %s.",
						result.getConsistGrpName(), result.getConsistGrpId()));
				task.setStatus(DriverTask.TaskStatus.READY);

			} else {
				_log.error(String.format("Creating flashCopy consistency group %s failed %s\n",
						result.getConsistGrpName(), result.getErrorString()), result.isSuccess());
				task.setMessage(
						String.format("Creating flashCopy consistency group %s failed : ", result.getConsistGrpName())
								+ result.getErrorString());
				task.setStatus(DriverTask.TaskStatus.FAILED);
			}
		} catch (Exception e) {
			_log.error("Unable to create the flashCopy consistency group {} on the storage system {}",
					consistencyGroup.getDisplayName(), consistencyGroup.getStorageSystemId());
			task.setMessage(
					String.format("Unable to create the flashCopy consistency group %s on the storage system %s",
							consistencyGroup.getDisplayName(), consistencyGroup.getStorageSystemId()) + e.getMessage());
			task.setStatus(DriverTask.TaskStatus.FAILED);
			e.printStackTrace();
		}

		_log.info("createConsistencyGroup() for storage system {} - end", consistencyGroup.getStorageSystemId());
		return task;
	}

	/**
	 * Deleting the FC Consistency Group
	 *
	 * @param consistencyGroup
	 *            Consistency Group to be deleted
	 * @return task
	 */
	@Override
	public DriverTask deleteConsistencyGroup(VolumeConsistencyGroup consistencyGroup) {

		DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_DELETE_FC_CONSISTGROUP);

		_log.info("deleteConsistencyGroup() for storage system {} - start", consistencyGroup.getStorageSystemId());

		try {
			SSHConnection connection = getClientBySystemId(consistencyGroup.getStorageSystemId());

			IBMSVCDeleteFCConsistGrpResult result = IBMSVCCLI.deleteFCConsistGrp(connection,
					consistencyGroup.getNativeId(), consistencyGroup.getDeviceLabel());

			if (result.isSuccess()) {
				_log.info(String.format("Deleted flashCopy consistency group %s with Id %s.\n",
						result.getConsistGrpName(), result.getConsistGrpId()));
				task.setMessage(String.format("Deleted flashCopy consistency group %s with Id %s.",
						result.getConsistGrpName(), result.getConsistGrpId()));
				task.setStatus(DriverTask.TaskStatus.READY);

			} else {
				_log.error(String.format("Deleting flashCopy consistency group %s failed %s\n",
						result.getConsistGrpName(), result.getErrorString()), result.isSuccess());
				task.setMessage(
						String.format("Deleting flashCopy consistency group %s failed : ", result.getConsistGrpName())
								+ result.getErrorString());
				task.setStatus(DriverTask.TaskStatus.FAILED);
			}
		} catch (Exception e) {
			_log.error("Unable to create the flashCopy consistency group {} on the storage system {}",
					consistencyGroup.getDeviceLabel(), consistencyGroup.getStorageSystemId());
			task.setMessage(
					String.format("Unable to create the flashCopy consistency group %s on the storage system %s",
							consistencyGroup.getDeviceLabel(), consistencyGroup.getStorageSystemId()) + e.getMessage());
			task.setStatus(DriverTask.TaskStatus.FAILED);
			e.printStackTrace();
		}

		_log.info("deleteConsistencyGroup() for storage system {} - end", consistencyGroup.getStorageSystemId());
		return task;
	}

	/**
	 * Create the consistency group snapshot volume
	 *
	 * @param consistencyGroup
	 * @param snapshots
	 * @param capabilities
	 * @return
	 */
	@Override
	public DriverTask createConsistencyGroupSnapshot(VolumeConsistencyGroup consistencyGroup,
			List<VolumeSnapshot> snapshots, List<CapabilityInstance> capabilities) {

		DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_CREATE_FC_CONSISTGROUP_SNAPSHOT);

		for (VolumeSnapshot volumeSnapshot : snapshots) {

			try {
				_log.info("createConsistencyGroupSnapshot() for storage system {} - start",
						volumeSnapshot.getStorageSystemId());

				SSHConnection connection = getClientBySystemId(volumeSnapshot.getStorageSystemId());

				// 1. Get the Source Volume details like fc_map_count,
				// se_copy_count, copy_count
				// As each Snapshot has an Max of 256 FC Mappings only for each
				// source volume
				IBMSVCGetVolumeResult resultGetVolume = IBMSVCCLI.queryStorageVolume(connection,
						volumeSnapshot.getParentId());

				if (resultGetVolume.isSuccess()) {

					_log.info(String.format("Processing storage volume Id %s.\n",
							resultGetVolume.getProperty("VolumeId")));

					boolean createMirrorCopy = false;

					String sourceVolumeName = resultGetVolume.getProperty("VolumeName");

					int se_copy_count = Integer.parseInt(resultGetVolume.getProperty("SECopyCount"));
					int copy_count = Integer.parseInt(resultGetVolume.getProperty("CopyCount"));
					int fc_map_count = Integer.parseInt(resultGetVolume.getProperty("FCMapCount"));

					if (fc_map_count < IBMSVCConstants.MAX_SOURCE_MAPPINGS) {

						// Create the snapshot volume parameters
						StorageVolume targetStorageVolume = new StorageVolume();
						targetStorageVolume.setStorageSystemId(volumeSnapshot.getStorageSystemId());
						targetStorageVolume.setDeviceLabel(volumeSnapshot.getDeviceLabel());
						targetStorageVolume.setDisplayName(volumeSnapshot.getDisplayName());
						targetStorageVolume.setStoragePoolId(resultGetVolume.getProperty("PoolId"));
						targetStorageVolume.setRequestedCapacity(
								IBMSVCDriverUtils.convertGBtoBytes(resultGetVolume.getProperty("VolumeCapacity")));

						if (se_copy_count > 0) {
							targetStorageVolume.setThinlyProvisioned(true);
						}
						if (copy_count > 1) {
							createMirrorCopy = true;
						}
						_log.info(String.format("Processed storage volume Id %s.\n",
								resultGetVolume.getProperty("VolumeId")));

						// 2. Create a new Snapshot Volume with details supplied
						IBMSVCCreateVolumeResult resultCreateVol = IBMSVCCLI.createStorageVolumes(connection,
								targetStorageVolume, false, createMirrorCopy);

						if (resultCreateVol.isSuccess()) {
							_log.info(String.format("Created storage snapshot volume %s (%s) size %s\n",
									resultCreateVol.getName(), resultCreateVol.getId(),
									resultCreateVol.getRequestedCapacity()));

							targetStorageVolume.setNativeId(resultCreateVol.getId());

							IBMSVCGetVolumeResult resultGetSnapshotVolume = IBMSVCCLI.queryStorageVolume(connection,
									resultCreateVol.getId());

							if (resultGetSnapshotVolume.isSuccess()) {
								_log.info(String.format("Snapshot volume %s has been retrieved.\n",
										resultGetSnapshotVolume.getProperty("VolumeId")));
								targetStorageVolume.setWwn(resultGetSnapshotVolume.getProperty("VolumeWWN"));
								volumeSnapshot.setWwn(resultGetSnapshotVolume.getProperty("VolumeWWN"));
							} else {
								_log.warn(String.format("Snapshot volume %s retrieval failed %s\n",
										resultGetSnapshotVolume.getProperty("VolumeId"),
										resultGetSnapshotVolume.getErrorString()));
							}

							volumeSnapshot.setNativeId(resultCreateVol.getId());
							volumeSnapshot.setDeviceLabel(resultCreateVol.getName());
							volumeSnapshot.setDisplayName(resultCreateVol.getName());
							volumeSnapshot.setAccessStatus(StorageObject.AccessStatus.READ_WRITE);
							String timeStamp = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
							//volumeSnapshot.setTimestamp(timeStamp);

							String targetVolumeName = volumeSnapshot.getDeviceLabel();
							String consistencyGrpId = consistencyGroup.getNativeId();
							String consistencyGrpName = consistencyGroup.getDeviceLabel();

							// 3. Create FC Mapping for the source and target
							// volume
							// Set the fullCopy to false to indicate its Volume
							// Snapshot
							IBMSVCCreateFCMappingResult resultFCMapping = IBMSVCCLI.createFCMapping(connection,
									sourceVolumeName, targetVolumeName, consistencyGrpName, false);

							if (resultFCMapping.isSuccess()) {
								_log.info(String.format("Created flashCopy mapping %s\n", resultFCMapping.getId()));

								// 4. Prepare the Start of FC Mapping
								IBMSVCPreStartFCConsistGrpResult resultPreStartFCConsistGrp = IBMSVCCLI
										.preStartFCConsistGrp(connection, consistencyGrpId, consistencyGrpName);

								if (resultPreStartFCConsistGrp.isSuccess()) {
									_log.info(String.format("Prepared to start flashCopy consistency group %s\n",
											consistencyGrpName));

									boolean mapping_ready = false;

									int wait_time = 5;
									int max_retries = (IBMSVCConstants.FC_MAPPING_QUERY_TIMEOUT / wait_time) + 1;

									// 5. Retry the Query of FC Mapping status
									// till Maximum Tries reaches
									label: for (int i = 1; i <= max_retries; i++) {

										IBMSVCQueryFCConsistGrpResult resultQueryFCConsistGrp = IBMSVCCLI
												.queryFCConsistGrp(connection, consistencyGrpId, consistencyGrpName);

										if (resultQueryFCConsistGrp.isSuccess()) {
											_log.info(String.format("Queried flashCopy consistency group %s\n",
													consistencyGrpName));

											String fcMapStatus = resultQueryFCConsistGrp.getConsistGrpStatus();

											// 6. If the FC Mapping status is
											// "unknown" then set task as Failed
											// and return
											switch (fcMapStatus) {
											case "unknown":
											case "preparing":
												_log.warn(String.format(
														"Unexpected flashCopy consistency group %s with status %s\n",
														resultQueryFCConsistGrp.getConsistGrpName(), fcMapStatus));
												task.setMessage(String.format(
														"Unexpected flashCopy consistency group %s with status %s.",
														resultQueryFCConsistGrp.getConsistGrpName(), fcMapStatus));
												task.setStatus(DriverTask.TaskStatus.FAILED);

												break label;
											case "stopped": // 7. If the FC
															// Mapping status is
															// "stopped" then
															// again Prepare the
															// Start of FC
															// Mapping. Repeat
															// Step 4
												preStartFCMConsistGrp(connection, consistencyGrpId, consistencyGrpName);

												break;
											case "prepared":
												mapping_ready = true;
												// 8. If the FC Mapping status
												// is "prepared" then Start the
												// FC Mapping
												startFCConsistGrp(connection, consistencyGrpId, consistencyGrpName);

												task.setMessage(String.format(
														"Created flashCopy consistency group %s and added flashCopy mapping Id %s.",
														consistencyGrpName, resultFCMapping.getId()));
												task.setStatus(DriverTask.TaskStatus.READY);
												break label;
											}

										} else {
											_log.warn(String.format(
													"Querying flashCopy consistency group %s failed : %s.\n",
													resultQueryFCConsistGrp.getConsistGrpName(),
													resultQueryFCConsistGrp.getErrorString()));
										}

										SECONDS.sleep(5);
									}

									if (!mapping_ready) {
										_log.warn(String.format(
												"Preparing for flashCopy consistency group %s failed to complete within the allocated %s seconds timeout. Terminating. %s\n",
												consistencyGrpName, IBMSVCConstants.FC_MAPPING_QUERY_TIMEOUT,
												resultPreStartFCConsistGrp.getErrorString()));

										_log.error(String.format(
												"Cleaning up the flashCopy mapping Id %s and snapshot volume %s.",
												resultFCMapping.getId(), resultCreateVol.getName()));

										// deleteFCMapping(connection,
										// resultFCMapping.getId());
										/**
										 * Deleting volume stops and deletes all
										 * the related FC mappings to that
										 * volume And finally deletes the volume
										 */
										deleteStorageVolumes(connection, resultCreateVol.getId());

										_log.error(String.format(
												"Cleaned up the flashCopy mapping Id %s and snapshot volume %s.",
												resultFCMapping.getId(), resultCreateVol.getName()));

										task.setMessage(String.format(
												"Preparing for flashCopy consistency group %s failed to complete within the allocated %s seconds timeout. Terminating. %s\n",
												consistencyGrpName, IBMSVCConstants.FC_MAPPING_QUERY_TIMEOUT)
												+ resultPreStartFCConsistGrp.getErrorString());

										task.setStatus(DriverTask.TaskStatus.FAILED);

									}

								} else {
									_log.warn(
											String.format("Preparing for flashCopy consistency group %s failed : %s.\n",
													consistencyGrpName, resultPreStartFCConsistGrp.getErrorString()));

									_log.error(String.format(
											"Cleaned up the flashCopy mapping Id %s and snapshot volume %s.",
											resultFCMapping.getId(), resultCreateVol.getName()));

									// stopFCMapping(connection,
									// resultFCMapping.getId());
									// deleteFCMapping(connection,
									// resultFCMapping.getId());
									/**
									 * Deleting volume stops and deletes all the
									 * related FC mappings to that volume And
									 * finally deletes the volume
									 */
									deleteStorageVolumes(connection, resultCreateVol.getId());

									_log.error(String.format(
											"Cleaned up the flashCopy mapping Id %s and snapshot volume %s.",
											resultFCMapping.getId(), resultCreateVol.getName()));

									task.setMessage(
											String.format("Preparing for flashCopy consistency group %s failed : %s",
													consistencyGrpName, resultPreStartFCConsistGrp.getErrorString()));

									task.setStatus(DriverTask.TaskStatus.FAILED);
								}

							} else {
								_log.error(String.format("Creating flashCopy mapping failed %s",
										resultFCMapping.getErrorString()), resultFCMapping.isSuccess());

								_log.error(String.format("Cleaning up the snapshot volume %s.",
										resultCreateVol.getName()));

								/**
								 * Deleting volume stops and deletes all the
								 * related FC mappings to that volume And
								 * finally deletes the volume
								 */
								deleteStorageVolumes(connection, resultCreateVol.getId());

								_log.error(
										String.format("Cleaned up the snapshot volume %s.", resultCreateVol.getName()));

								task.setMessage(String.format(
										"Creating flashCopy mapping for the source volume %s and the target volume %s failed : %s.",
										sourceVolumeName, targetVolumeName) + resultFCMapping.getErrorString());
								task.setStatus(DriverTask.TaskStatus.FAILED);
							}

						} else {
							_log.error(String.format("Creating storage snapshot volume failed %s\n",
									resultCreateVol.getErrorString()), resultCreateVol.isSuccess());
							task.setMessage(
									String.format("Unable to create the snapshot volume %s on the storage system %s",
											volumeSnapshot.getDeviceLabel(), volumeSnapshot.getStorageSystemId())
									+ resultCreateVol.getErrorString());
							task.setStatus(DriverTask.TaskStatus.FAILED);
						}

					} else {
						_log.error(String.format("FlashCopy mapping has reached the maximum for the source volume %s\n",
								resultGetVolume.getProperty("VolumeName")));
						task.setMessage(
								String.format("FlashCopy mapping has reached the maximum for the source volume %s",
										resultGetVolume.getProperty("VolumeName")) + resultGetVolume.getErrorString());
						task.setStatus(DriverTask.TaskStatus.FAILED);
					}

				} else {
					_log.error(String.format("Processing get storage volume Id %s failed %s\n",
							resultGetVolume.getProperty("VolumeId"), resultGetVolume.getErrorString()));
					task.setMessage(String.format("Processing get storage volume failed : %s",
							resultGetVolume.getProperty("VolumeId")) + resultGetVolume.getErrorString());
					task.setStatus(DriverTask.TaskStatus.FAILED);
				}
			} catch (Exception e) {
				_log.error("Unable to create consistency group snapshot volumes on the storage system {}",
						consistencyGroup.getStorageSystemId());
				task.setMessage(
						String.format("Unable to create consistency group snapshot volumes on the storage system %s",
								consistencyGroup.getStorageSystemId()) + e.getMessage());
				task.setStatus(DriverTask.TaskStatus.FAILED);
				e.printStackTrace();
			}
			_log.info("createConsistencyGroupSnapshot() for storage system {} - end",
					volumeSnapshot.getStorageSystemId());
		}
		return task;
	}

	/**
	 * Prepare FC Consistency Group of Mappings
	 *
	 * @param connection
	 * @param fcConsistGrpId
	 * @param fcConsistGrpName
	 */
	private void preStartFCMConsistGrp(SSHConnection connection, String fcConsistGrpId, String fcConsistGrpName) {
		IBMSVCPreStartFCConsistGrpResult resultPreStartFCConsistGrp = IBMSVCCLI.preStartFCConsistGrp(connection,
				fcConsistGrpId, fcConsistGrpName);
		if (resultPreStartFCConsistGrp.isSuccess()) {
			_log.info(String.format("Prepared to start flashCopy consistency group %s of mappings.\n",
					resultPreStartFCConsistGrp.getConsistGrpName()));
		} else {
			_log.warn(String.format("Preparing to start flashCopy consistency group %s of mappings failed : %s.\n",
					resultPreStartFCConsistGrp.getConsistGrpName(), resultPreStartFCConsistGrp.getErrorString()));
		}
	}

	/**
	 * Start FC Consistency Group of Mappings
	 *
	 * @param connection
	 * @param fcConsistGrpId
	 * @param fcConsistGrpName
	 */
	private void startFCConsistGrp(SSHConnection connection, String fcConsistGrpId, String fcConsistGrpName) {
		IBMSVCStartFCConsistGrpResult resultStartFCConsistGrp = IBMSVCCLI.startFCConsistGrp(connection, fcConsistGrpId,
				fcConsistGrpName);
		if (resultStartFCConsistGrp.isSuccess()) {
			_log.info(String.format("Started flashCopy consistency group %s of mappings.\n",
					resultStartFCConsistGrp.getConsistGrpName()));
		} else {
			_log.warn(String.format("Starting flashCopy consistency group %s of mappings failed : %s.\n",
					resultStartFCConsistGrp.getConsistGrpName(), resultStartFCConsistGrp.getErrorString()));
		}
	}

	/**
	 * Stopping FC Consistency Group of Mappings
	 *
	 * @param connection
	 * @param fcConsistGrpId
	 * @param fcConsistGrpName
	 */
	private void stopFCConsistGrp(SSHConnection connection, String fcConsistGrpId, String fcConsistGrpName) {
		// Remove the FC Mapping and delete the snapshot volume
		IBMSVCStopFCConsistGrpResult resultStopFCConsistGrp = IBMSVCCLI.stopFCConsistGrp(connection, fcConsistGrpId,
				fcConsistGrpName);
		if (resultStopFCConsistGrp.isSuccess()) {
			_log.info(String.format("Stopped flashCopy consistency group %s of mappings.\n",
					resultStopFCConsistGrp.getConsistGrpName()));
		} else {
			_log.warn(String.format("Stopping flashCopy consistency group %s of mappings failed : %s.\n",
					resultStopFCConsistGrp.getConsistGrpName(), resultStopFCConsistGrp.getErrorString()));
		}
	}

	/**
	 * Delete the consistency group snapshot volume
	 *
	 * @param snapshots
	 * @return
	 */
	@Override
	public DriverTask deleteConsistencyGroupSnapshot(List<VolumeSnapshot> snapshots) {

		DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_DELETE_FC_CONSISTGROUP_SNAPSHOT);

		for (VolumeSnapshot volumeSnapshot : snapshots) {

			_log.info("deleteConsistencyGroupSnapshot() for storage system {} - start",
					volumeSnapshot.getStorageSystemId());

			try {
				SSHConnection connection = getClientBySystemId(volumeSnapshot.getStorageSystemId());

				_log.info(String.format("Deleting the snapshot volume Id %s\n", volumeSnapshot.getNativeId()));

				// Delete the Snapshot Volume
				IBMSVCDeleteVolumeResult resultDelVol = IBMSVCCLI.deleteStorageVolumes(connection,
						volumeSnapshot.getNativeId());

				if (resultDelVol.isSuccess()) {
					_log.info(String.format("Deleted snapshot volume Id %s.\n", resultDelVol.getId()));
					task.setMessage(String.format("Snapshot volume Id %s has been deleted.", resultDelVol.getId()));
					task.setStatus(DriverTask.TaskStatus.READY);
				} else {
					_log.error(String.format("Deleting snapshot volume Id %s failed : %s\n", resultDelVol.getId(),
							resultDelVol.getErrorString()), resultDelVol.isSuccess());
					task.setMessage(String.format("Deleting snapshot volume Id %s failed : %s", resultDelVol.getId(),
							resultDelVol.getErrorString()));
					task.setStatus(DriverTask.TaskStatus.FAILED);
					break;
				}

			} catch (Exception e) {
				_log.error("Unable to delete the snapshot volume {} on the storage system {}",
						volumeSnapshot.getParentId(), volumeSnapshot.getStorageSystemId());
				task.setMessage(String.format("Unable to delete the snapshot volume %s on the storage system %s",
						volumeSnapshot.getDeviceLabel(), volumeSnapshot.getStorageSystemId()) + e.getMessage());
				task.setStatus(DriverTask.TaskStatus.FAILED);
				e.printStackTrace();
			}

			_log.info("deleteConsistencyGroupSnapshot() for storage system {} - end",
					volumeSnapshot.getStorageSystemId());
		}
		return task;
	}

	/**
	 * Create the consistency group clone volume
	 *
	 * @param consistencyGroup
	 * @param clones
	 * @param capabilities
	 * @return
	 */
	@Override
	public DriverTask createConsistencyGroupClone(VolumeConsistencyGroup consistencyGroup, List<VolumeClone> clones,
			List<CapabilityInstance> capabilities) {
		DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_CREATE_FC_CONSISTGROUP_CLONE);

		for (VolumeClone volumeClone : clones) {

			try {
				_log.info("createConsistencyGroupClone() for storage system {} - start",
						volumeClone.getStorageSystemId());

				SSHConnection connection = getClientBySystemId(volumeClone.getStorageSystemId());

				// 1. Get the Source Volume details like fc_map_count,
				// se_copy_count, copy_count
				// As each Snapshot has an Max of 256 FC Mappings only for each
				// source volume
				IBMSVCGetVolumeResult resultGetVolume = IBMSVCCLI.queryStorageVolume(connection,
						volumeClone.getParentId());

				if (resultGetVolume.isSuccess()) {

					_log.info(String.format("Processing storage volume Id %s.\n",
							resultGetVolume.getProperty("VolumeId")));

					boolean createMirrorCopy = false;

					String sourceVolumeName = resultGetVolume.getProperty("VolumeName");

					int se_copy_count = Integer.parseInt(resultGetVolume.getProperty("SECopyCount"));
					int copy_count = Integer.parseInt(resultGetVolume.getProperty("CopyCount"));
					int fc_map_count = Integer.parseInt(resultGetVolume.getProperty("FCMapCount"));

					if (fc_map_count < IBMSVCConstants.MAX_SOURCE_MAPPINGS) {

						// Create the snapshot volume parameters
						StorageVolume targetStorageVolume = new StorageVolume();
						targetStorageVolume.setStorageSystemId(volumeClone.getStorageSystemId());
						targetStorageVolume.setDeviceLabel(volumeClone.getDeviceLabel());
						targetStorageVolume.setDisplayName(volumeClone.getDisplayName());
						targetStorageVolume.setStoragePoolId(resultGetVolume.getProperty("PoolId"));
						targetStorageVolume.setRequestedCapacity(
								IBMSVCDriverUtils.convertGBtoBytes(resultGetVolume.getProperty("VolumeCapacity")));

						if (se_copy_count > 0) {
							targetStorageVolume.setThinlyProvisioned(true);
						}
						if (copy_count > 1) {
							createMirrorCopy = true;
						}
						_log.info(String.format("Processed storage volume Id %s.\n",
								resultGetVolume.getProperty("VolumeId")));

						// 2. Create a new Snapshot Volume with details supplied
						IBMSVCCreateVolumeResult resultCreateVol = IBMSVCCLI.createStorageVolumes(connection,
								targetStorageVolume, false, createMirrorCopy);

						if (resultCreateVol.isSuccess()) {
							_log.info(String.format("Created storage clone volume %s (%s) size %s\n",
									resultCreateVol.getName(), resultCreateVol.getId(),
									resultCreateVol.getRequestedCapacity()));

							targetStorageVolume.setNativeId(resultCreateVol.getId());

							IBMSVCGetVolumeResult resultGetSnapshotVolume = IBMSVCCLI.queryStorageVolume(connection,
									resultCreateVol.getId());

							if (resultGetSnapshotVolume.isSuccess()) {
								_log.info(String.format("Clone volume %s has been retrieved.\n",
										resultGetSnapshotVolume.getProperty("VolumeId")));
								targetStorageVolume.setWwn(resultGetSnapshotVolume.getProperty("VolumeWWN"));
								volumeClone.setWwn(resultGetSnapshotVolume.getProperty("VolumeWWN"));
							} else {
								_log.warn(String.format("Clone volume %s retrieval failed %s\n",
										resultGetSnapshotVolume.getProperty("VolumeId"),
										resultGetSnapshotVolume.getErrorString()));
							}

							volumeClone.setNativeId(resultCreateVol.getId());
							volumeClone.setDeviceLabel(resultCreateVol.getName());
							volumeClone.setDisplayName(resultCreateVol.getName());
							volumeClone.setAccessStatus(StorageObject.AccessStatus.READ_WRITE);

							String targetVolumeName = volumeClone.getDeviceLabel();
							String consistencyGrpId = consistencyGroup.getNativeId();
							String consistencyGrpName = consistencyGroup.getDeviceLabel();

							// 3. Create FC Mapping for the source and target
							// volume
							// Set the fullCopy to true to indicate its Volume
							// Clone
							IBMSVCCreateFCMappingResult resultFCMapping = IBMSVCCLI.createFCMapping(connection,
									sourceVolumeName, targetVolumeName, consistencyGrpName, true);

							if (resultFCMapping.isSuccess()) {
								_log.info(String.format("Created flashCopy mapping %s\n", resultFCMapping.getId()));

								volumeClone.setReplicationState(VolumeClone.ReplicationState.CREATED);

								// 4. Prepare the Start of FC Mapping
								IBMSVCPreStartFCConsistGrpResult resultPreStartFCConsistGrp = IBMSVCCLI
										.preStartFCConsistGrp(connection, consistencyGrpId, consistencyGrpName);

								if (resultPreStartFCConsistGrp.isSuccess()) {
									_log.info(String.format("Prepared to start flashCopy consistency group %s\n",
											consistencyGrpName));

									boolean mapping_ready = false;

									int wait_time = 5;
									int max_retries = (IBMSVCConstants.FC_MAPPING_QUERY_TIMEOUT / wait_time) + 1;

									// 5. Retry the Query of FC Mapping status
									// till Maximum Tries reaches
									label: for (int i = 1; i <= max_retries; i++) {

										IBMSVCQueryFCConsistGrpResult resultQueryFCConsistGrp = IBMSVCCLI
												.queryFCConsistGrp(connection, consistencyGrpId, consistencyGrpName);

										if (resultQueryFCConsistGrp.isSuccess()) {
											_log.info(String.format("Queried flashCopy consistency group %s\n",
													consistencyGrpName));

											String fcMapStatus = resultQueryFCConsistGrp.getConsistGrpStatus();

											// 6. If the FC Mapping status is
											// "unknown" then set task as Failed
											// and return
											switch (fcMapStatus) {
											case "unknown":
											case "preparing":
												_log.warn(String.format(
														"Unexpected flashCopy consistency group %s with status %s\n",
														resultQueryFCConsistGrp.getConsistGrpName(), fcMapStatus));
												task.setMessage(String.format(
														"Unexpected flashCopy consistency group %s with status %s.",
														resultQueryFCConsistGrp.getConsistGrpName(), fcMapStatus));
												task.setStatus(DriverTask.TaskStatus.FAILED);

												break label;

											case "stopped":
												// 7. If the FC Mapping status
												// is "stopped" then again
												// Prepare the Start of FC
												// Mapping.
												preStartFCMConsistGrp(connection, consistencyGrpId, consistencyGrpName);

												break;

											case "prepared":
												mapping_ready = true;
												// 8. If the FC Mapping status
												// is "prepared" then Start the
												// FC Mapping
												startFCConsistGrp(connection, consistencyGrpId, consistencyGrpName);

												task.setMessage(String.format(
														"Created flashCopy consistency group %s and added flashCopy mapping Id %s.",
														consistencyGrpName, resultFCMapping.getId()));
												task.setStatus(DriverTask.TaskStatus.READY);
												break label;
											}

										} else {
											_log.warn(String.format(
													"Querying flashCopy consistency group %s failed : %s.\n",
													resultQueryFCConsistGrp.getConsistGrpName(),
													resultQueryFCConsistGrp.getErrorString()));
										}

										SECONDS.sleep(5);
									}

									if (!mapping_ready) {
										_log.warn(String.format(
												"Preparing for flashCopy consistency group %s failed to complete within the allocated %s seconds timeout. Terminating. %s\n",
												consistencyGrpName, IBMSVCConstants.FC_MAPPING_QUERY_TIMEOUT,
												resultPreStartFCConsistGrp.getErrorString()));

										_log.error(String.format(
												"Cleaning up the flashCopy mapping Id %s and clone volume %s.",
												resultFCMapping.getId(), resultCreateVol.getName()));

										// deleteFCMapping(connection,
										// resultFCMapping.getId());
										/**
										 * Deleting volume stops and deletes all
										 * the related FC mappings to that
										 * volume And finally deletes the volume
										 */
										deleteStorageVolumes(connection, resultCreateVol.getId());

										_log.error(String.format(
												"Cleaned up the flashCopy mapping Id %s and clone volume %s.",
												resultFCMapping.getId(), resultCreateVol.getName()));

										task.setMessage(String.format(
												"Preparing for flashCopy consistency group %s failed to complete within the allocated %s seconds timeout. Terminating. %s\n",
												consistencyGrpName, IBMSVCConstants.FC_MAPPING_QUERY_TIMEOUT)
												+ resultPreStartFCConsistGrp.getErrorString());

										task.setStatus(DriverTask.TaskStatus.FAILED);

									}

								} else {
									_log.warn(
											String.format("Preparing for flashCopy consistency group %s failed : %s.\n",
													consistencyGrpName, resultPreStartFCConsistGrp.getErrorString()));

									_log.error(
											String.format("Cleaned up the flashCopy mapping Id %s and clone volume %s.",
													resultFCMapping.getId(), resultCreateVol.getName()));

									// stopFCMapping(connection,
									// resultFCMapping.getId());
									// deleteFCMapping(connection,
									// resultFCMapping.getId());
									/**
									 * Deleting volume stops and deletes all the
									 * related FC mappings to that volume And
									 * finally deletes the volume
									 */
									deleteStorageVolumes(connection, resultCreateVol.getId());

									_log.error(
											String.format("Cleaned up the flashCopy mapping Id %s and clone volume %s.",
													resultFCMapping.getId(), resultCreateVol.getName()));

									task.setMessage(
											String.format("Preparing for flashCopy consistency group %s failed : %s",
													consistencyGrpName, resultPreStartFCConsistGrp.getErrorString()));

									task.setStatus(DriverTask.TaskStatus.FAILED);
								}

							} else {
								_log.error(String.format("Creating flashCopy mapping failed %s",
										resultFCMapping.getErrorString()), resultFCMapping.isSuccess());

								_log.error(
										String.format("Cleaning up the clone volume %s.", resultCreateVol.getName()));

								/**
								 * Deleting volume stops and deletes all the
								 * related FC mappings to that volume And
								 * finally deletes the volume
								 */
								deleteStorageVolumes(connection, resultCreateVol.getId());

								_log.error(String.format("Cleaned up the clone volume %s.", resultCreateVol.getName()));

								task.setMessage(String.format(
										"Creating flashCopy mapping for the source volume %s and the target volume %s failed : %s.",
										sourceVolumeName, targetVolumeName) + resultFCMapping.getErrorString());
								task.setStatus(DriverTask.TaskStatus.FAILED);
							}

						} else {
							_log.error(String.format("Creating storage clone volume failed %s\n",
									resultCreateVol.getErrorString()), resultCreateVol.isSuccess());
							task.setMessage(
									String.format("Unable to create the clone volume %s on the storage system %s",
											volumeClone.getDeviceLabel(), volumeClone.getStorageSystemId())
									+ resultCreateVol.getErrorString());
							task.setStatus(DriverTask.TaskStatus.FAILED);
						}

					} else {
						_log.error(String.format("FlashCopy mapping has reached the maximum for the source volume %s\n",
								resultGetVolume.getProperty("VolumeName")));
						task.setMessage(
								String.format("FlashCopy mapping has reached the maximum for the source volume %s",
										resultGetVolume.getProperty("VolumeName")) + resultGetVolume.getErrorString());
						task.setStatus(DriverTask.TaskStatus.FAILED);
					}

				} else {
					_log.error(String.format("Processing get storage volume Id %s failed %s\n",
							resultGetVolume.getProperty("VolumeId"), resultGetVolume.getErrorString()));
					task.setMessage(String.format("Processing get storage volume failed : %s",
							resultGetVolume.getProperty("VolumeId")) + resultGetVolume.getErrorString());
					task.setStatus(DriverTask.TaskStatus.FAILED);
				}
			} catch (Exception e) {
				_log.error("Unable to create consistency group clone volumes on the storage system {}",
						consistencyGroup.getStorageSystemId());
				task.setMessage(
						String.format("Unable to create consistency group clone volumes on the storage system %s",
								consistencyGroup.getStorageSystemId()) + e.getMessage());
				task.setStatus(DriverTask.TaskStatus.FAILED);
				e.printStackTrace();
			}
			_log.info("createConsistencyGroupClone() for storage system {} - end", volumeClone.getStorageSystemId());
		}
		return task;
	}

	/**
	 * Delete the consistency group clone volume
	 *
	 * @param clones
	 * @return
	 */
	public DriverTask deleteConsistencyGroupClone(List<VolumeClone> clones) {

		DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_DELETE_FC_CONSISTGROUP_CLONE);

		for (VolumeClone volumeClone : clones) {

			_log.info("deleteConsistencyGroupSnapshot() for storage system {} - start",
					volumeClone.getStorageSystemId());

			try {
				SSHConnection connection = getClientBySystemId(volumeClone.getStorageSystemId());

				_log.info(String.format("Deleting the clone volume Id %s\n", volumeClone.getNativeId()));

				// Delete the Snapshot Volume
				IBMSVCDeleteVolumeResult resultDelVol = IBMSVCCLI.deleteStorageVolumes(connection,
						volumeClone.getNativeId());

				if (resultDelVol.isSuccess()) {
					_log.info(String.format("Deleted clone volume Id %s.\n", resultDelVol.getId()));
					task.setMessage(String.format("Clone volume Id %s has been deleted.", resultDelVol.getId()));
					task.setStatus(DriverTask.TaskStatus.READY);
				} else {
					_log.error(String.format("Deleting clone volume Id %s failed : %s\n", resultDelVol.getId(),
							resultDelVol.getErrorString()), resultDelVol.isSuccess());
					task.setMessage(String.format("Deleting clone volume Id %s failed : %s", resultDelVol.getId(),
							resultDelVol.getErrorString()));
					task.setStatus(DriverTask.TaskStatus.FAILED);
					break;
				}

			} catch (Exception e) {
				_log.error("Unable to delete the clone volume {} on the storage system {}", volumeClone.getParentId(),
						volumeClone.getStorageSystemId());
				task.setMessage(String.format("Unable to delete the clone volume %s on the storage system %s",
						volumeClone.getDeviceLabel(), volumeClone.getStorageSystemId()) + e.getMessage());
				task.setStatus(DriverTask.TaskStatus.FAILED);
				e.printStackTrace();
			}

			_log.info("deleteConsistencyGroupSnapshot() for storage system {} - end", volumeClone.getStorageSystemId());
		}
		return task;
	}

	/**
	 * Clone volume clones.
	 *
	 * @param clones
	 *            Type: Input/Output.
	 * @param capabilities
	 *            capabilities of clones. Type: Input.
	 * @return task
	 */
	@Override
	public DriverTask createVolumeClone(List<VolumeClone> clones, StorageCapabilities capabilities) {

		DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_CREATE_CLONE_VOLUMES);

		for (VolumeClone volumeClone : clones) {

			_log.info("createVolumeClone() for storage system {} - start", volumeClone.getStorageSystemId());

			try {
				SSHConnection connection = getClientBySystemId(volumeClone.getStorageSystemId());

				// 1. Get the Source Volume details like fc_map_count,
				// se_copy_count, copy_count
				// As each Snapshot has an Max of 256 FC Mappings only for each
				// source volume
				IBMSVCGetVolumeResult resultGetVolume = IBMSVCCLI.queryStorageVolume(connection,
						volumeClone.getParentId());

				if (resultGetVolume.isSuccess()) {

					_log.info(String.format("Processing storage volume Id %s.\n",
							resultGetVolume.getProperty("VolumeId")));

					boolean createMirrorCopy = false;

					String sourceVolumeName = resultGetVolume.getProperty("VolumeName");

					int se_copy_count = Integer.parseInt(resultGetVolume.getProperty("SECopyCount"));
					int copy_count = Integer.parseInt(resultGetVolume.getProperty("CopyCount"));
					int fc_map_count = Integer.parseInt(resultGetVolume.getProperty("FCMapCount"));

					if (fc_map_count < IBMSVCConstants.MAX_SOURCE_MAPPINGS) {

						// Create the snapshot volume parameters
						StorageVolume targetStorageVolume = new StorageVolume();
						targetStorageVolume.setStorageSystemId(volumeClone.getStorageSystemId());
						targetStorageVolume.setDeviceLabel(volumeClone.getDeviceLabel());
						targetStorageVolume.setDisplayName(volumeClone.getDisplayName());
						targetStorageVolume.setStoragePoolId(resultGetVolume.getProperty("PoolId"));
						targetStorageVolume.setRequestedCapacity(
								IBMSVCDriverUtils.convertGBtoBytes(resultGetVolume.getProperty("VolumeCapacity")));

						if (se_copy_count > 0) {
							targetStorageVolume.setThinlyProvisioned(true);
						}
						if (copy_count > 1) {
							createMirrorCopy = true;
						}
						_log.info(String.format("Processed storage volume Id %s.\n",
								resultGetVolume.getProperty("VolumeId")));

						// 2. Create a new Snapshot Volume with details supplied
						IBMSVCCreateVolumeResult resultCreateVol = IBMSVCCLI.createStorageVolumes(connection,
								targetStorageVolume, false, createMirrorCopy);

						if (resultCreateVol.isSuccess()) {
							_log.info(String.format("Created storage clone volume %s (%s) size %s\n",
									resultCreateVol.getName(), resultCreateVol.getId(),
									resultCreateVol.getRequestedCapacity()));

							targetStorageVolume.setNativeId(resultCreateVol.getId());

							IBMSVCGetVolumeResult resultGetSnapshotVolume = IBMSVCCLI.queryStorageVolume(connection,
									resultCreateVol.getId());

							if (resultGetSnapshotVolume.isSuccess()) {
								_log.info(String.format("Clone volume %s has been retrieved.\n",
										resultGetSnapshotVolume.getProperty("VolumeId")));
								targetStorageVolume.setWwn(resultGetSnapshotVolume.getProperty("VolumeWWN"));
								volumeClone.setWwn(resultGetSnapshotVolume.getProperty("VolumeWWN"));
							} else {
								_log.warn(String.format("Clone volume %s retrieval failed %s\n",
										resultGetSnapshotVolume.getProperty("VolumeId"),
										resultGetSnapshotVolume.getErrorString()));
							}

							volumeClone.setNativeId(resultCreateVol.getId());
							volumeClone.setDeviceLabel(resultCreateVol.getName());
							volumeClone.setDisplayName(resultCreateVol.getName());
							volumeClone.setAccessStatus(StorageObject.AccessStatus.READ_WRITE);
							volumeClone.setReplicationState(VolumeClone.ReplicationState.CREATED);

							String targetVolumeName = volumeClone.getDeviceLabel();

							// 3. Create FC Mapping for the source and target
							// volume
							// Set the fullCopy to true to indicate its Volume
							// Clone
							IBMSVCCreateFCMappingResult resultFCMapping = IBMSVCCLI.createFCMapping(connection,
									sourceVolumeName, targetVolumeName, null, true);

							if (resultFCMapping.isSuccess()) {
								_log.info(String.format("Created flashCopy mapping %s\n", resultFCMapping.getId()));

								// 4. Prepare the Start of FC Mapping
								IBMSVCPreStartFCMappingResult resultPreStartFCMapping = IBMSVCCLI
										.preStartFCMapping(connection, resultFCMapping.getId());

								if (resultPreStartFCMapping.isSuccess()) {
									_log.info(String.format("Prepared to start flashCopy mapping %s\n",
											resultPreStartFCMapping.getId()));

									boolean mapping_ready = false;

									int wait_time = 5;
									int max_retries = (IBMSVCConstants.FC_MAPPING_QUERY_TIMEOUT / wait_time) + 1;

									// 5. Retry the Query of FC Mapping status
									// till Maximum Tries reaches
									label: for (int i = 1; i <= max_retries; i++) {

										IBMSVCQueryFCMappingResult resultQueryFCMapping = IBMSVCCLI.queryFCMapping(
												connection, resultPreStartFCMapping.getId(), false, null, null);

										if (resultQueryFCMapping.isSuccess()) {
											_log.info(String.format("Queried flashCopy mapping %s\n",
													resultQueryFCMapping.getId()));

											String fcMapStatus = resultQueryFCMapping.getProperty("FCMapStatus");

											// 6. If the FC Mapping status is
											// "unknown" then set task as Failed
											// and return
											switch (fcMapStatus) {
											case "unknown":
											case "preparing":
												_log.warn(String.format(
														"Unexpected flashCopy mapping Id %s with status %s\n",
														resultQueryFCMapping.getId(), fcMapStatus));
												task.setMessage(String.format(
														"Unexpected flashCopy mapping Id %s with status %s.",
														resultQueryFCMapping.getId(), fcMapStatus));
												task.setStatus(DriverTask.TaskStatus.FAILED);
												break label;
											case "stopped": // 7. If the FC
															// Mapping status is
															// "stopped" then
															// again Prepare the
															// Start of FC
															// Mapping. Repeat
															// Step 4
												preStartFCMapping(connection, resultFCMapping.getId());

												break;
											case "prepared":
												mapping_ready = true;
												// 8. If the FC Mapping status
												// is "prepared" then Start the
												// FC Mapping
												startFCMapping(connection, resultFCMapping.getId());
												task.setMessage(String.format(
														"Created flashCopy mapping for the source volume %s and the target volume %s.",
														sourceVolumeName, targetVolumeName));
												task.setStatus(DriverTask.TaskStatus.READY);
												break label;
											}

										} else {
											_log.warn(String.format("Querying flashCopy mapping Id %s failed %s\n",
													resultQueryFCMapping.getId(),
													resultQueryFCMapping.getErrorString()));
										}

										SECONDS.sleep(5);
									}

									if (!mapping_ready) {
										_log.warn(String.format(
												"Preparing for flashCopy mapping Id %s failed to complete within the allocated %s seconds timeout. Terminating. %s\n",
												resultPreStartFCMapping.getId(),
												IBMSVCConstants.FC_MAPPING_QUERY_TIMEOUT,
												resultPreStartFCMapping.getErrorString()));

										_log.error(String.format(
												"Cleaning up the snapshot volume %s and FC Mapping Id %s.",
												resultCreateVol.getName(), resultPreStartFCMapping.getId()));

										// deleteFCMapping(connection,
										// resultPreStartFCMapping.getId());
										/**
										 * Deleting volume stops and deletes all
										 * the related FC mappings to that
										 * volume And finally deletes the volume
										 */
										deleteStorageVolumes(connection, resultCreateVol.getId());

										_log.error(String.format(
												"Cleaned up the clone volume %s and flashCopy mapping Id %s.",
												resultCreateVol.getName(), resultPreStartFCMapping.getId()));

										task.setMessage(String.format(
												"Preparing for flashCopy mapping Id %s failed to complete within the allocated %d seconds timeout. Terminating. %s\n",
												resultPreStartFCMapping.getId(),
												IBMSVCConstants.FC_MAPPING_QUERY_TIMEOUT,
												resultPreStartFCMapping.getErrorString()));

										task.setStatus(DriverTask.TaskStatus.FAILED);

									}

								} else {
									_log.warn(String.format("Preparing for flashCopy mapping Id %s failed %s\n",
											resultPreStartFCMapping.getId(), resultPreStartFCMapping.getErrorString()));

									_log.error(String.format("Cleaning up the snapshot volume %s and FC Mapping Id %s.",
											resultCreateVol.getName(), resultPreStartFCMapping.getId()));

									// stopFCMapping(connection,
									// resultPreStartFCMapping.getId());
									// deleteFCMapping(connection,
									// resultPreStartFCMapping.getId());
									/**
									 * Deleting volume stops and deletes all the
									 * related FC mappings to that volume And
									 * finally deletes the volume
									 */
									deleteStorageVolumes(connection, resultCreateVol.getId());

									_log.error(
											String.format("Cleaned up the clone volume %s and flashCopy mapping Id %s.",
													resultCreateVol.getName(), resultPreStartFCMapping.getId()));

									task.setMessage(String.format("Preparing for flashCopy mapping Id %s failed : %s",
											resultPreStartFCMapping.getId(), resultPreStartFCMapping.getErrorString()));

									task.setStatus(DriverTask.TaskStatus.FAILED);
								}

							} else {
								_log.error(String.format("Creating flashCopy mapping failed %s",
										resultFCMapping.getErrorString()), resultFCMapping.isSuccess());

								_log.error(
										String.format("Cleaning up the clone volume %s.", resultCreateVol.getName()));

								/**
								 * Deleting volume stops and deletes all the
								 * related FC mappings to that volume And
								 * finally deletes the volume
								 */
								deleteStorageVolumes(connection, resultCreateVol.getId());

								_log.error(String.format("Cleaned up the clone volume %s.", resultCreateVol.getName()));

								task.setMessage(String.format(
										"Creating flashCopy mapping for the source volume %s and the target volume %s failed : %s.",
										sourceVolumeName, targetVolumeName, resultFCMapping.getErrorString()));
								task.setStatus(DriverTask.TaskStatus.FAILED);
							}

						} else {
							_log.error(String.format("Creating storage clone volume failed %s\n",
									resultCreateVol.getErrorString()), resultCreateVol.isSuccess());
							task.setMessage(
									String.format("Unable to create the clone volume %s on the storage system %s",
											volumeClone.getDeviceLabel(), volumeClone.getStorageSystemId())
									+ resultCreateVol.getErrorString());
							task.setStatus(DriverTask.TaskStatus.FAILED);
						}

					} else {
						_log.error(String.format("FlashCopy mapping has reached the maximum for the source volume %s\n",
								resultGetVolume.getProperty("VolumeName")));
						task.setMessage(
								String.format("FlashCopy mapping has reached the maximum for the source volume %s",
										resultGetVolume.getProperty("VolumeName")) + resultGetVolume.getErrorString());
						task.setStatus(DriverTask.TaskStatus.FAILED);
					}

				} else {
					_log.error(String.format("Processing get storage volume Id %s failed %s\n",
							resultGetVolume.getProperty("VolumeId"), resultGetVolume.getErrorString()));
					task.setMessage(String.format("Processing get storage volume failed : %s",
							resultGetVolume.getProperty("VolumeId")) + resultGetVolume.getErrorString());
					task.setStatus(DriverTask.TaskStatus.FAILED);
				}
			} catch (Exception e) {
				_log.error("Unable to create the clone volume {} on the storage system {}", volumeClone.getParentId(),
						volumeClone.getStorageSystemId());
				task.setMessage(String.format("Unable to create the clone volume %s on the storage system %s",
						volumeClone.getDeviceLabel(), volumeClone.getStorageSystemId()) + e.getMessage());
				task.setStatus(DriverTask.TaskStatus.FAILED);
				e.printStackTrace();
			}

			_log.info("createVolumeClone() for storage system {} - end", volumeClone.getStorageSystemId());
		}

		return task;
	}

	/**
	 * Detach volume clones.
	 *
	 * @param clones
	 *            Type: Input/Output.
	 * @return task
	 */
	@Override
	public DriverTask detachVolumeClone(List<VolumeClone> clones) {

		DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_DETACH_CLONE_VOLUMES);

		for (VolumeClone volumeClone : clones) {

			_log.info("deleteVolumeClone() for storage system {} - start", volumeClone.getStorageSystemId());

			try {
				SSHConnection connection = getClientBySystemId(volumeClone.getStorageSystemId());

				_log.info(String.format("Detaching the clone volume Id %s\n", volumeClone.getNativeId()));

				// Delete the Snapshot Volume
				IBMSVCDetachCloneMirrorResult resultDetachClone = IBMSVCCLI.detachVolumeClones(connection,
						volumeClone.getParentId(), volumeClone.getDisplayName());

				if (resultDetachClone.isSuccess()) {
					_log.info(String.format("Detached the volume Id %s and the new clone volume Id %s.\n",
							resultDetachClone.getVolumeId(), resultDetachClone.getCloneVolumeId()));
					task.setMessage(String.format("Detached the volume Id %s and the new clone volume Id %s.",
							resultDetachClone.getVolumeId(), resultDetachClone.getCloneVolumeId()));
					task.setStatus(DriverTask.TaskStatus.READY);
				} else {
					_log.error(
							String.format("Detaching the clone volume from the volume Id %s failed : %s\n",
									resultDetachClone.getVolumeId(), resultDetachClone.getErrorString()),
							resultDetachClone.isSuccess());
					task.setMessage(String.format("Detaching the clone volume from the volume Id %s failed : %s",
							resultDetachClone.getVolumeId(), resultDetachClone.getErrorString()));
					task.setStatus(DriverTask.TaskStatus.FAILED);
					break;
				}

			} catch (Exception e) {
				_log.error("Unable to detach the clone volume from the volume Id {} on the storage system {}",
						volumeClone.getParentId(), volumeClone.getStorageSystemId());
				task.setMessage(String.format(
						"Unable to detach the clone volume from the volume Id %s on the storage system %s",
						volumeClone.getParentId(), volumeClone.getStorageSystemId()) + e.getMessage());
				task.setStatus(DriverTask.TaskStatus.FAILED);
				e.printStackTrace();
			}

			_log.info("deleteVolumeClone() for storage system {} - end", volumeClone.getStorageSystemId());
		}
		return task;
	}

	/**
	 * Restore Volume Clone
	 *
	 * @param clones
	 * @return
	 */
	@Override
	public DriverTask restoreFromClone(List<VolumeClone> clones) {

		DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_RESTORE_CLONE_VOLUMES);

		for (VolumeClone volumeClone : clones) {

			_log.info("restoreFromClone() for storage system {} - start", volumeClone.getStorageSystemId());

			try {
				SSHConnection connection = getClientBySystemId(volumeClone.getStorageSystemId());

				// 1. Get the Source Volume details like fc_map_count,
				// se_copy_count, copy_count
				// As each Snapshot has an Max of 256 FC Mappings only for each
				// source volume
				IBMSVCGetVolumeResult resultQueryVolume = IBMSVCCLI.queryStorageVolume(connection,
						volumeClone.getParentId());

				if (resultQueryVolume.isSuccess()) {

					_log.info(String.format("Processing clone volume Id %s.\n",
							resultQueryVolume.getProperty("VolumeId")));

					int fc_map_count = Integer.parseInt(resultQueryVolume.getProperty("FCMapCount"));

					if (fc_map_count < IBMSVCConstants.MAX_SOURCE_MAPPINGS) {

						String sourceVolumeName = volumeClone.getDeviceLabel();
						String targetVolumeName = resultQueryVolume.getProperty("VolumeName");

						// 2. Create FC Mapping for the source and target volume
						// Set the fullCopy to true to indicate its Volume Clone
						IBMSVCCreateFCMappingResult resultFCMapping = IBMSVCCLI.createFCMapping(connection,
								sourceVolumeName, targetVolumeName, null, true);

						if (resultFCMapping.isSuccess()) {
							_log.info(String.format("Created flashCopy mapping %s\n", resultFCMapping.getId()));

							IBMSVCStartFCMappingResult resultStartFCMapping = IBMSVCCLI.startFCMapping(connection,
									resultFCMapping.getId());

							if (resultStartFCMapping.isSuccess()) {
								_log.info(String.format(
										"Started flashCopy mapping Id %s for the source volume %s and the target volume %s.\n",
										resultStartFCMapping.getId(), sourceVolumeName, targetVolumeName));
								task.setMessage(String.format(
										"Started flashCopy mapping Id %s for the source volume %s and the target volume %s.",
										resultStartFCMapping.getId(), sourceVolumeName, targetVolumeName)
										+ resultFCMapping.getErrorString());
								task.setStatus(DriverTask.TaskStatus.READY);
							} else {
								_log.error(
										String.format(
												"Starting flashCopy mapping for the source volume %s and the target volume %s failed : %s",
												sourceVolumeName, targetVolumeName, resultFCMapping.getErrorString()),
										resultFCMapping.isSuccess());
								task.setMessage(String.format(
										"Starting flashCopy mapping for the source volume %s and the target volume %s failed : %s.",
										sourceVolumeName, targetVolumeName, resultFCMapping.getErrorString()));
								task.setStatus(DriverTask.TaskStatus.FAILED);
							}

						} else {
							_log.error(
									String.format(
											"Creating flashCopy mapping for the source volume %s and the target volume %s failed : %s",
											sourceVolumeName, targetVolumeName, resultFCMapping.getErrorString()),
									resultFCMapping.isSuccess());
							task.setMessage(String.format(
									"Creating flashCopy mapping for the source volume %s and the target volume %s failed : %s.",
									sourceVolumeName, targetVolumeName, resultFCMapping.getErrorString()));
							task.setStatus(DriverTask.TaskStatus.FAILED);
						}

					} else {
						_log.error(String.format("FlashCopy mapping has reached the maximum for the source volume %s\n",
								resultQueryVolume.getProperty("VolumeName")));
						task.setMessage(
								String.format("FlashCopy mapping has reached the maximum for the source volume %s",
										resultQueryVolume.getProperty("VolumeName"))
								+ resultQueryVolume.getErrorString());
						task.setStatus(DriverTask.TaskStatus.FAILED);
					}

				} else {
					_log.error(String.format("Querying storage volume %s failed : %s\n", volumeClone.getParentId(),
							resultQueryVolume.getErrorString()), resultQueryVolume.isSuccess());
					task.setMessage(String.format("Querying storage volume %s failed : %s.", volumeClone.getParentId(),
							resultQueryVolume.getErrorString()));
					task.setStatus(DriverTask.TaskStatus.FAILED);
				}

			} catch (Exception e) {
				_log.error("Unable to restore the clone volume {} on the storage system {}", volumeClone.getParentId(),
						volumeClone.getStorageSystemId());
				task.setMessage(String.format("Unable to restore the clone volume %s on the storage system %s",
						volumeClone.getDeviceLabel(), volumeClone.getStorageSystemId()) + e.getMessage());
				task.setStatus(DriverTask.TaskStatus.FAILED);
				e.printStackTrace();
			}

			_log.info("restoreFromClone() for storage system {} - end", volumeClone.getStorageSystemId());
		}
		return task;
	}

	/**
	 * Delete volume clones.
	 *
	 * @param clones
	 *            clones to delete. Type: Input.
	 * @return
	 */
	@Override
	public DriverTask deleteVolumeClone(List<VolumeClone> clones) {

		DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_DELETE_CLONE_VOLUMES);

		for (VolumeClone volumeClone : clones) {

			_log.info("deleteVolumeClone() for storage system {} - start", volumeClone.getStorageSystemId());

			try {
				SSHConnection connection = getClientBySystemId(volumeClone.getStorageSystemId());

				_log.info(String.format("Deleting the clone volume Id %s\n", volumeClone.getNativeId()));

				// Delete the Snapshot Volume
				IBMSVCDeleteVolumeResult resultDelVol = IBMSVCCLI.deleteStorageVolumes(connection,
						volumeClone.getNativeId());

				if (resultDelVol.isSuccess()) {
					_log.info(String.format("Deleted clone volume Id %s.\n", resultDelVol.getId()));
					task.setMessage(String.format("Clone volume Id %s has been deleted.", resultDelVol.getId()));
					task.setStatus(DriverTask.TaskStatus.READY);
				} else {
					_log.error(String.format("Deleting clone volume Id %s failed : %s\n", resultDelVol.getId(),
							resultDelVol.getErrorString()), resultDelVol.isSuccess());
					task.setMessage(String.format("Deleting clone volume Id %s failed : %s", resultDelVol.getId(),
							resultDelVol.getErrorString()));
					task.setStatus(DriverTask.TaskStatus.FAILED);
					break;
				}

			} catch (Exception e) {
				_log.error("Unable to delete the clone volume Id {} on the storage system {}",
						volumeClone.getParentId(), volumeClone.getStorageSystemId());
				task.setMessage(String.format("Unable to delete the clone volume Id %s on the storage system %s",
						volumeClone.getDeviceLabel(), volumeClone.getStorageSystemId()) + e.getMessage());
				task.setStatus(DriverTask.TaskStatus.FAILED);
				e.printStackTrace();
			}

			_log.info("deleteVolumeClone() for storage system {} - end", volumeClone.getStorageSystemId());
		}
		return task;
	}

	@Override
	public DriverTask createVolumeMirror(List<VolumeMirror> mirrors, StorageCapabilities capabilities) {

		DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_CREATE_MIRROR_VOLUMES);

		for (VolumeMirror volumeMirror : mirrors) {

			_log.info("createVolumeMirror() for storage system {} - start", volumeMirror.getStorageSystemId());

			try {
				SSHConnection connection = getClientBySystemId(volumeMirror.getStorageSystemId());

				// 1. Get the Source Volume details like volumeName, poolId,
				// poolName, copy_count
				// As each Source Volume has an Max of 2 mirror copies
				IBMSVCGetVolumeResult resultQueryVolume = IBMSVCCLI.queryStorageVolume(connection,
						volumeMirror.getParentId());

				if (resultQueryVolume.isSuccess()) {

					_log.info(String.format("Processing mirror source volume Id %s.\n",
							resultQueryVolume.getProperty("VolumeId")));

					int copy_count = Integer.parseInt(resultQueryVolume.getProperty("CopyCount"));

					if (copy_count < IBMSVCConstants.MAX_MIRROR_COUNT) {

						String sourceVolumeId = resultQueryVolume.getProperty("VolumeId");
						String sourceVolumeName = resultQueryVolume.getProperty("VolumeName");
						String targetPoolId = resultQueryVolume.getProperty("PoolId");
						String targetPoolName = resultQueryVolume.getProperty("PoolName");

						// 2. Create mirror volume for the source volume
						IBMSVCCreateMirrorVolumeResult resultCreateMirrorVol = IBMSVCCLI.createMirrorVolume(connection,
								sourceVolumeId, targetPoolId);

						if (resultCreateMirrorVol.isSuccess()) {
							_log.info(String.format("Created mirror volume for the source volume Id %s\n",
									resultCreateMirrorVol.getSrcVolumeId()));

							/**
							 * Iterate through till the Sync Progress has
							 * reached 100%
							 */
							while (true) {

								IBMSVCQueryMirrorSyncProgressResult resultQueryMirrorSync = IBMSVCCLI
										.queryMirrorSyncProgress(connection, resultCreateMirrorVol.getSrcVolumeId());

								if (resultQueryMirrorSync.isSuccess()) {
									_log.info(String.format(
											"Queried the mirrored source volume Id %s for sync progress %s.\n",
											resultQueryMirrorSync.getProperty("VolumeId"),
											resultQueryMirrorSync.getProperty("SyncProgress")));
									task.setMessage(String.format(
											"Queried the mirrored source volume Id %s for sync progress %s.",
											resultQueryMirrorSync.getProperty("VolumeName"),
											resultQueryMirrorSync.getProperty("SyncProgress")));
									task.setStatus(DriverTask.TaskStatus.READY);

									// Break the loop if the sync progress
									// reached 100%
									if (resultQueryMirrorSync.getProperty("SyncProgress").equals("100")) {
										break;
									}

								} else {
									_log.warn(
											String.format(
													"Querying the sync progress for the mirrored source volume Id %s failed : %s.",
													resultQueryMirrorSync.getProperty("VolumeId"),
													resultCreateMirrorVol.getErrorString()),
											resultCreateMirrorVol.isSuccess());
									task.setMessage(String.format(
											"Querying the sync progress for the mirrored source volume Id %s failed : %s.",
											resultQueryMirrorSync.getProperty("VolumeId"),
											resultCreateMirrorVol.getErrorString()));
									task.setStatus(DriverTask.TaskStatus.FAILED);
								}

								SECONDS.sleep(10000);
							}

						} else {
							_log.error(String.format("Creating  mirror volume for the source volume Id %s failed : %s",
									resultCreateMirrorVol.getSrcVolumeId(), resultCreateMirrorVol.getErrorString()),
									resultCreateMirrorVol.isSuccess());
							task.setMessage(
									String.format("Creating mirror volume for the source volume Id %s failed : %s.",
											sourceVolumeName, resultCreateMirrorVol.getErrorString()));
							task.setStatus(DriverTask.TaskStatus.FAILED);
						}

					} else {
						_log.error(String.format("Mirror count has reached the maximum for the source volume %s\n",
								resultQueryVolume.getProperty("VolumeName")));
						task.setMessage(String.format("Mirror count has reached the maximum for the source volume %s",
								resultQueryVolume.getProperty("VolumeName")) + resultQueryVolume.getErrorString());
						task.setStatus(DriverTask.TaskStatus.FAILED);
					}

				} else {
					_log.error(
							String.format("Querying mirrored source volume Id %s failed : %s\n",
									volumeMirror.getParentId(), resultQueryVolume.getErrorString()),
							resultQueryVolume.isSuccess());
					task.setMessage(String.format("Querying mirrored source volume %s failed : %s.",
							volumeMirror.getParentId(), resultQueryVolume.getErrorString()));
					task.setStatus(DriverTask.TaskStatus.FAILED);
				}

			} catch (Exception e) {
				_log.error("Unable to query the mirrored source volume Id {} on the storage system {}",
						volumeMirror.getParentId(), volumeMirror.getStorageSystemId());
				task.setMessage(
						String.format("Unable to query the mirrored source volume Id %s on the storage system %s",
								volumeMirror.getDeviceLabel(), volumeMirror.getStorageSystemId()) + e.getMessage());
				task.setStatus(DriverTask.TaskStatus.FAILED);
				e.printStackTrace();
			}

			_log.info("createVolumeMirror() for storage system {} - end", volumeMirror.getStorageSystemId());
		}
		return task;
	}

	@Override
	public DriverTask deleteVolumeMirror(List<VolumeMirror> mirrors) {

		DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_DELETE_MIRROR_VOLUMES);

		for (VolumeMirror volumeMirror : mirrors) {

			_log.info("deleteVolumeMirror() for storage system {} - start", volumeMirror.getStorageSystemId());

			try {
				SSHConnection connection = getClientBySystemId(volumeMirror.getStorageSystemId());

				IBMSVCDeleteMirrorVolumeResult resultDeleteMirrorVol = IBMSVCCLI.deleteMirrorVolume(connection,
						volumeMirror.getParentId());

				if (resultDeleteMirrorVol.isSuccess()) {

					_log.info(String.format("Deleted mirror volume for the source volume Id %s\n",
							resultDeleteMirrorVol.getVolumeId()));
					task.setMessage(String.format("Deleted mirror volume for the source volume Id %s.",
							resultDeleteMirrorVol.getVolumeId()));
					task.setStatus(DriverTask.TaskStatus.READY);

				} else {
					_log.error(
							String.format("Deleting mirror volume for the source volume Id %s failed : %s\n",
									volumeMirror.getParentId(), resultDeleteMirrorVol.getErrorString()),
							resultDeleteMirrorVol.isSuccess());
					task.setMessage(String.format("Deleting mirror volume for the source volume Id %s failed : %s\\",
							volumeMirror.getParentId(), resultDeleteMirrorVol.getErrorString()));
					task.setStatus(DriverTask.TaskStatus.FAILED);
				}

			} catch (Exception e) {
				_log.error("Unable to delete the mirror volume for the source volume Id {} on the storage system {}",
						volumeMirror.getParentId(), volumeMirror.getStorageSystemId());
				task.setMessage(String.format(
						"Unable to delete the mirror volume for the source volume Id %s on the storage system %s",
						volumeMirror.getDeviceLabel(), volumeMirror.getStorageSystemId()) + e.getMessage());
				task.setStatus(DriverTask.TaskStatus.FAILED);
				e.printStackTrace();
			}

			_log.info("deleteVolumeMirror() for storage system {} - end", volumeMirror.getStorageSystemId());
		}
		return task;
	}

	@Override
	public DriverTask splitVolumeMirror(List<VolumeMirror> mirrors) {

		DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_DETACH_MIRROR_VOLUMES);

		for (VolumeMirror volumeMirror : mirrors) {

			_log.info("splitVolumeMirror() for storage system {} - start", volumeMirror.getStorageSystemId());

			try {
				SSHConnection connection = getClientBySystemId(volumeMirror.getStorageSystemId());

				_log.info(String.format("Detaching the mirror volume for the source volume Id %s\n",
						volumeMirror.getNativeId()));

				// Delete the Mirror Volume
				IBMSVCDetachCloneMirrorResult resultDetachClone = IBMSVCCLI.detachVolumeClones(connection,
						volumeMirror.getParentId(), volumeMirror.getDisplayName());

				if (resultDetachClone.isSuccess()) {
					_log.info(String.format(
							"Detached the mirror volume from the source volume Id %s and the new clone volume Id %s.\n",
							resultDetachClone.getVolumeId(), resultDetachClone.getCloneVolumeId()));
					task.setMessage(String.format(
							"Detached the mirror volume from the source volume Id %s and the new clone volume Id %s.",
							resultDetachClone.getVolumeId(), resultDetachClone.getCloneVolumeId()));
					task.setStatus(DriverTask.TaskStatus.READY);
				} else {
					_log.error(
							String.format("Detaching the mirror volume from the source volume Id %s failed : %s\n",
									resultDetachClone.getVolumeId(), resultDetachClone.getErrorString()),
							resultDetachClone.isSuccess());
					task.setMessage(
							String.format("Detaching the mirror volume from the source volume Id %s failed : %s",
									resultDetachClone.getVolumeId(), resultDetachClone.getErrorString()));
					task.setStatus(DriverTask.TaskStatus.FAILED);
					break;
				}

			} catch (Exception e) {
				_log.error("Unable to detach the clone volume from the volume Id {} on the storage system {}",
						volumeMirror.getParentId(), volumeMirror.getStorageSystemId());
				task.setMessage(String.format(
						"Unable to detach the clone volume from the volume Id %s on the storage system %s",
						volumeMirror.getParentId(), volumeMirror.getStorageSystemId()) + e.getMessage());
				task.setStatus(DriverTask.TaskStatus.FAILED);
				e.printStackTrace();
			}

			_log.info("splitVolumeMirror() for storage system {} - end", volumeMirror.getStorageSystemId());
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
	 * Get connection info from registry
	 *
	 * @param systemNativeId
	 * @param attrName
	 *            use string constants in the IBMSVCConstants.java. e.g.
	 *            IBMSVCConstants.IP_ADDRESS
	 * @return Ip_address, port, username or password for given systemId and
	 *         attribute name
	 */
	public String getConnInfoFromRegistry(String systemNativeId, String attrName) {
		Map<String, List<String>> attributes = this.driverRegistry
				.getDriverAttributesForKey(IBMSVCConstants.DRIVER_NAME, systemNativeId);
		if (attributes == null) {
			_log.info("Connection info for " + systemNativeId + " is not set up in the registry");
			return null;
		} else if (attributes.get(attrName) == null) {
			_log.info(attrName + "is not found in the registry");
			return null;
		} else {
			return attributes.get(attrName).get(0);
		}
	}

	/**
	 * Set connection information to registry
	 *
	 * @param systemNativeId
	 * @param ipAddress
	 * @param port
	 * @param username
	 * @param password
	 */
	public void setConnInfoToRegistry(String systemNativeId, String ipAddress, int port, String username,
			String password) {
		Map<String, List<String>> attributes = new HashMap<>();
		List<String> listIP = new ArrayList<>();
		List<String> listPort = new ArrayList<>();
		List<String> listUserName = new ArrayList<>();
		List<String> listPwd = new ArrayList<>();

		listIP.add(ipAddress);
		attributes.put(IBMSVCConstants.IP_ADDRESS, listIP);
		listPort.add(Integer.toString(port));
		attributes.put(IBMSVCConstants.PORT_NUMBER, listPort);
		listUserName.add(username);
		attributes.put(IBMSVCConstants.USER_NAME, listUserName);
		listPwd.add(password);
		attributes.put(IBMSVCConstants.PASSWORD, listPwd);

		this.driverRegistry.setDriverAttributesForKey(IBMSVCConstants.DRIVER_NAME, systemNativeId, attributes);
	}

	/**
	 * Get SSH Client
	 *
	 * @param systemId
	 *            storage system id
	 * @return ssh client handler
	 */
	private SSHConnection getClientBySystemId(String systemId) {
		String ip_address, port, username, password;
		SSHConnection client;

		if (systemId != null) {
			systemId = systemId.trim();
		}

		_log.info(String.format("Getting client connection for the Storage System %s.\n", systemId));

		synchronized (syncObject) {

			if (!IBMSVCSSHClientMap.containsKey(systemId)) {

				_log.info(String.format("Before getting the connection details from the Registry %s.\n", systemId));

				ip_address = getConnInfoFromRegistry(systemId, IBMSVCConstants.IP_ADDRESS);
				port = getConnInfoFromRegistry(systemId, IBMSVCConstants.PORT_NUMBER);
				username = getConnInfoFromRegistry(systemId, IBMSVCConstants.USER_NAME);
				password = getConnInfoFromRegistry(systemId, IBMSVCConstants.PASSWORD);

				_log.info(String.format("After getting the connection details from the Registry %s.\n", systemId));

				if (ip_address != null && username != null && password != null) {

					ConnectionInfo connectionInfo = new ConnectionInfo(ip_address, Integer.parseInt(port), username,
							password);
					try {

						client = (SSHConnection) connectionManager.getConnection(connectionInfo);

						IBMSVCSSHClientMap.put(systemId, client);

						_log.info(String.format("Connection has been established for the host {}", ip_address));

					} catch (Exception e) {
						_log.error("Exception when creating ssh client instance for storage system {} ", systemId, e);
						e.printStackTrace();
					}
				} else {
					_log.error(
							"Some of the following for storage system {} are Missing: IP Address, username, password.",
							systemId);
				}
			}
		}

		return IBMSVCSSHClientMap.get(systemId);

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
		return null;
	}

	@Override
	public List<VolumeClone> getVolumeClones(StorageVolume volume) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<VolumeMirror> getVolumeMirrors(StorageVolume volume) {
		// TODO Auto-generated method stub
		return null;
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
	public DriverTask restoreVolumeMirror(List<VolumeMirror> mirrors) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<String, HostExportInfo> getVolumeExportInfoForHosts(StorageVolume volume) {
		// TODO Auto-generated method stub
		return null;
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
		// TODO Auto-generated method stub
		return null;
	}

}
