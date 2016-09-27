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

	private Map<String, SSHConnection> IBMSVCSSHClientMap = null;

	private Object syncObject = new Object();

	private boolean testFlag = false;

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
    public DriverTask discoverStorageSystem(StorageSystem storageSystem) {

        DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_DISCOVER_STORAGE_SYSTEM);

        //for (StorageSystem storageSystem : storageSystems) {

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

                    _log.info(String.format("Setting conn info in registry %s - %s - %s - %s - %s", storageSystem.getNativeId(), storageSystem.getIpAddress(),
                            storageSystem.getPortNumber(), storageSystem.getUsername(), storageSystem.getPassword()));
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


        //}

        return task;
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

					if (resultStoragePool.isSuccess()) {

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

				_log.info(String.format("Queried all host information.%n"));

				for (IBMSVCHost host : resultAllHost.getHostList()) {

					IBMSVCQueryHostInitiatorResult resultHostInitiator = IBMSVCCLI.queryHostInitiator(connection,
                            host.getHostId());

					if (resultHostInitiator.isSuccess()) {

						_log.info(String.format("Queried host initiator for host Id %s.%n",
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

                //Identify the

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
	 * @param storageVolume
	 *            Volumes to delete.
	 * @return task
	 */
	@Override
	public DriverTask deleteVolume(StorageVolume storageVolume) {

		DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_DELETE_STORAGE_VOLUMES);

		//for (StorageVolume storageVolume : volumes) {

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
		//}
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

                Set<String> uniqueHostSet = createHostsFromInitiators(connection, storageVolume.getStorageSystemId(), initiators);

                if (!uniqueHostSet.isEmpty()) {
					// create an iterator

					// check values
					for (Object anUniqueHostSet : uniqueHostSet) {

						String hostName = anUniqueHostSet.toString();

                        addVdiskAccess(connection, hostName, storageVolume.getNativeId());

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

                    // Create host on the array if it does not exist

                    _log.info("None of the initiator port hosts are registered with the storage system {}.",
                            storageVolume.getStorageSystemId());
					task.setMessage(String.format(
							"None of the initiator port hosts are registered with the storage system %s.",
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

    /**
     * Add VDisk Access to volumes based on Host IO Groups
     * Get list of IO Groups for host
     * Add IOGrp Access to VDisk
     * @param connection - SSHConnection
     * @param hostName - hostName on array
     * @param volumeID - vDisk ID or Name
     * @throws IBMSVCDriverException
     */
    private void addVdiskAccess(SSHConnection connection, String hostName, String volumeID) throws IBMSVCDriverException{
        IBMSVCQueryHostIOGrpResult histIoGrps = IBMSVCCLI.queryHostIOGrps(connection, hostName);
        List<String> ioGrps = histIoGrps.getIOGroupIDList();
        if(ioGrps.isEmpty()){
            _log.warn("Host {} has no IO Grps ", hostName);
            return;
        }

        String iogrps = String.join(":",ioGrps);

        IBMSVCAddVdiskAccessResult addVdiskAccessResult = IBMSVCCLI.addVdiskAccess(connection, volumeID, iogrps);

        if(!addVdiskAccessResult.isSuccess()){
            //throw new IBMSVCDriverException("Failed to add vDisk Access - " + addVdiskAccessResult.getErrorString());
            // TODO: Check what to do when vDisk access fails. This fails if IO_GRP has nodeCount 0.
            // Host may be associated with that IO Group
            _log.warn("Failed to add vDisk Access - " + addVdiskAccessResult.getErrorString());
        }

    }

    /**
     * Check and create hosts on array using initiator information.
     * 1. Get list of hosts and initiators from the array
     * 2. Compare it against list of input initiators and get unique host names
     * 3. Get list of initiators not registered on the array
     * 4. For the list of initiators not available on the array:
     *         - Check if host is registered. If host is registered, add initiator to host (TODO)
     *         - If host is not registered, add host to array with initiators. Then restart checks

     * @param connection
     * @param storageSystemID - Storage System ID
     * @param initiators - List of initiators from the UI
     * @return - List of unique host names on IBM SVC Array
     * @throws IBMSVCDriverException
     */
    private Set<String> createHostsFromInitiators(SSHConnection connection, String storageSystemID, List<Initiator> initiators) throws IBMSVCDriverException{

        // Get Host Initiator List from IBM Array
        List<Initiator> ibmHostInitiatorList = getIBMSVCHostInitiatorList(connection,
                storageSystemID);

        Set<String> uniqueHostSet = new HashSet<>();
        ArrayList<Initiator> unassignedInitiators = new ArrayList<>();

        // Get list of hosts on array with input initiators
        for (Initiator initiator : initiators) {
            String initiatorPort = initiator.getPort();
            String hostName = initiator.getHostName();

            Boolean initiatorMatched = false;
            for (Initiator hostInitiator : ibmHostInitiatorList) {
                if (initiatorPort.equals(hostInitiator.getPort())) {
                    //uniqueHostSet.add(hostName);
                    uniqueHostSet.add(hostInitiator.getHostName());
                    initiatorMatched = true;
                }
            }

            // If initiator is in input and not on array
            if(!initiatorMatched){
                unassignedInitiators.add(initiator);
            }

        }

        // Get Host to Initiator Map from input
        Map<String, List<String>> hostInitiatorMap = IBMSVCHelper.getHostInitiatorMap(initiators);

        // Check unassigned initiators to see if they need to be added or host need to be created
        for(Initiator unAssignedInitiator : unassignedInitiators){
            String hostName = unAssignedInitiator.getHostName();
            String unAssignedInitiatorPort = unAssignedInitiator.getPort();
            if(Character.isDigit(hostName.charAt(0))){
                hostName = "_" + hostName;
            }

            // TODO: Assuming input hostname is equal to hostname defined on the array.
            // Handle case were this is different
            if(uniqueHostSet.contains(hostName)){
                _log.warn(String.format("Initiator Port %s not registered with host on array - %s %n",
                        unAssignedInitiatorPort, hostName));
                // TODO: Host exists but initiator not assigned. Add initiator to Host here
            }else{
                // Host does not exist on array. Add host.
                List<String> hostInitiatorList = hostInitiatorMap.get(hostName);

                IBMSVCIOgrp ioGrp = getLeastUsedIOGrp(connection, storageSystemID);

                IBMSVCCreateHostResult hostCreateResult = IBMSVCCLI.createHost(connection, hostName, StringUtils.join(hostInitiatorList, ":"), ioGrp.getIogrpName());
                if(hostCreateResult.isSuccess()){
                    // Restart checks
                    return createHostsFromInitiators(connection, storageSystemID, initiators);
                }
                else{
                    throw new IBMSVCDriverException(String.format("Host not found on array and could not create - %s \n",
                            hostCreateResult.getName()));

                }
            }

        }

        return uniqueHostSet;
    }

    /**
     * Get Least Used IOGrp
     * 1. Get list of all IOGrps
     * 2. Filter out IO Groups with node count > 0
     * 3. Identify IO Group with least host count and vdisk count
     * @param connection - SSHConnection to the array
     * @param storageSystemId - Storage System ID
     * @return - IBMSVCIOgrp
     * @throws IBMSVCDriverException
     */
    private IBMSVCIOgrp getLeastUsedIOGrp(SSHConnection connection, String storageSystemId) throws IBMSVCDriverException{

        _log.info("identifyLeastUsedIOGrp() for storage system {} - start", storageSystemId);

        IBMSVCQueryIOGrpResult ioGrps = IBMSVCCLI.queryAllIOGrps(connection);

        IBMSVCIOgrp leastUsedIOgrp = null;

        for(IBMSVCIOgrp iogrp : ioGrps.getIogrpList()){
            int nodeCount = iogrp.getNodeCount();
            int hostCount = iogrp.getHostCount();
            int vdiskCount = iogrp.getVdiskCount();

            if(nodeCount > 0){

                if(leastUsedIOgrp == null){
                    leastUsedIOgrp = iogrp;
                }

                if(leastUsedIOgrp.getHostCount() > hostCount){
                    leastUsedIOgrp = iogrp;
                }else if(leastUsedIOgrp.getHostCount() == hostCount){
                    if(leastUsedIOgrp.getVdiskCount() > vdiskCount){
                        leastUsedIOgrp = iogrp;
                    }
                }
            }

        }

        if(leastUsedIOgrp == null){
            _log.info("identifyLeastUsedIOGrp() for storage system {} -No IO Group Identified - end", storageSystemId);
        }else{
            _log.info("identifyLeastUsedIOGrp() for storage system {} - {} - end", storageSystemId, leastUsedIOgrp.getIogrpName());
        }


        return leastUsedIOgrp;
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

        _log.info(String.format(" DriverRegistry %s.%n", this.driverRegistry.getDriverAttributes(IBMSVCConstants.DRIVER_NAME)));

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

        _log.info(String.format("Setting client connection for the Storage System %s - %s.%n", IBMSVCConstants.DRIVER_NAME, systemNativeId));

		this.driverRegistry.setDriverAttributesForKey(IBMSVCConstants.DRIVER_NAME, systemNativeId, attributes);
        _log.info(String.format(" DriverRegistry %s.%n", this.driverRegistry.getDriverAttributes(IBMSVCConstants.DRIVER_NAME)));
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

        _log.info(String.format(" IBMSVCSSHClientMap %s.\n", IBMSVCSSHClientMap.toString()));

		synchronized (syncObject) {

			if (!IBMSVCSSHClientMap.containsKey(systemId)) {

				_log.info(String.format("Before getting the connection details from the Registry %s.%n", systemId));

				ip_address = getConnInfoFromRegistry(systemId, IBMSVCConstants.IP_ADDRESS);
				port = getConnInfoFromRegistry(systemId, IBMSVCConstants.PORT_NUMBER);
				username = getConnInfoFromRegistry(systemId, IBMSVCConstants.USER_NAME);
				password = getConnInfoFromRegistry(systemId, IBMSVCConstants.PASSWORD);

				_log.info(String.format("After getting the connection details from the Registry %s.%n", systemId));

                _log.info(String.format("Get conn info in registry %s - %s - %s - %s", ip_address, port, username, password));

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
