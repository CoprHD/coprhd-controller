/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.driver.ibmsvcdriver.helpers;

import com.emc.storageos.driver.ibmsvcdriver.api.*;
import com.emc.storageos.driver.ibmsvcdriver.connection.ConnectionInfo;
import com.emc.storageos.driver.ibmsvcdriver.connection.ConnectionManager;
import com.emc.storageos.driver.ibmsvcdriver.connection.SSHConnection;
import com.emc.storageos.driver.ibmsvcdriver.impl.IBMSVCClusterNode;
import com.emc.storageos.driver.ibmsvcdriver.impl.IBMSVCDriverTask;
import com.emc.storageos.driver.ibmsvcdriver.impl.IBMSVCStorageDriver;
import com.emc.storageos.driver.ibmsvcdriver.utils.IBMSVCConstants;
import com.emc.storageos.driver.ibmsvcdriver.utils.IBMSVCDriverUtils;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.HostExportInfo;
import com.emc.storageos.storagedriver.model.*;
import org.apache.commons.lang.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class IBMSVCDiscovery {

    private static final Logger _log = LoggerFactory.getLogger(IBMSVCStorageDriver.class);

    /*
     * Connection Manager for managing connection pool
     */
    private ConnectionManager connectionManager = null;

    /**
     * Constructor
     */
    public IBMSVCDiscovery() {
        this.connectionManager = ConnectionManager.getInstance();
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
     * Discover Storage System
     * @param storageSystem
     * @return
     */
    public DriverTask discoverStorageSystem(StorageSystem storageSystem) {

        DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_DISCOVER_STORAGE_SYSTEM);

        SSHConnection connection = null;

        try {

            _log.info("discoverStorageSystem() information for storage system {}, name {} - start",
                    storageSystem.getIpAddress(), storageSystem.getSystemName());

            ConnectionInfo connectionInfo = new ConnectionInfo(storageSystem.getIpAddress(),
                    storageSystem.getPortNumber(), storageSystem.getUsername(), storageSystem.getPassword(), storageSystem.getNativeId());

            connection = (SSHConnection) connectionManager.getConnection(connectionInfo);

            IBMSVCQueryStorageSystemResult result = IBMSVCCLI.queryStorageSystem(connection);

            if (result.isSuccess()) {

                _log.info(String.format("Processing storage system %s.", storageSystem.getIpAddress()));

                storageSystem.setSerialNumber(result.getProperty("SerialNumber"));
                storageSystem.setFirmwareVersion(result.getProperty("FirmwareVersion"));
                storageSystem.setIpAddress(result.getProperty("IpAddress"));
                storageSystem.setModel(result.getProperty("Model"));
                storageSystem.setNativeId(result.getProperty("SerialNumber"));
                storageSystem.setProvisioningType(StorageSystem.SupportedProvisioningType.THIN_AND_THICK);
                storageSystem.setAccessStatus(StorageObject.AccessStatus.READ_WRITE);
                storageSystem.setMajorVersion(result.getProperty("MajorVersion"));
                storageSystem.setMinorVersion(result.getProperty("MinorVersion"));
                storageSystem.setIsSupportedVersion(true);

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
        } finally {
            if(connection != null){
                connection.disconnect();
            }
        }

        _log.info("discoverStorageSystem() information for storage system {}, nativeId {} - end\n",
                storageSystem.getIpAddress(), storageSystem.getNativeId());

        return task;
    }

    public <T extends StorageObject> T getStorageObject(String storageSystemId, String objectId, Class<T> type) {
		/*
		 * if (StorageVolume.class.getSimpleName().equals(type.getSimpleName()))
		 * { } StorageVolume obj = new StorageVolume();
		 * obj.setAllocatedCapacity(200L); return (T) obj;
		 */
        return null;
    }

    /**
     * Discover Storage pools
     * @param storageSystem
     * @param storagePools
     * @return
     */
    public DriverTask discoverStoragePools(StorageSystem storageSystem, List<StoragePool> storagePools) {

        DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_DISCOVER_STORAGE_POOLS);

        _log.info("discoverStoragePools() information for storage system {}, name {} - start",
                storageSystem.getIpAddress(), storageSystem.getSystemName());

        SSHConnection connection = null;

        try {
            connection = connectionManager.getClientBySystemId(storageSystem.getNativeId());

            IBMSVCQueryAllStoragePoolResult resultAllStoragePool = IBMSVCCLI.queryAllStoragePool(connection);

            if (resultAllStoragePool.isSuccess()) {

                for (StoragePool storagePool : resultAllStoragePool.getStoragePools()) {

                    _log.info(String.format("Processing storage pool %s.", storagePool.getPoolName()));

                    IBMSVCQueryStoragePoolResult resultStoragePool = IBMSVCCLI.queryStoragePool(connection,
                            storagePool.getPoolName());

                    if (resultStoragePool.isSuccess()) {

                        storagePool.setNativeId(resultStoragePool.getProperty("PoolId"));
                        storagePool.setStorageSystemId(storageSystem.getSerialNumber());

                        Set<StoragePool.SupportedDriveTypes> supportedDriveTypes = new HashSet<StoragePool.SupportedDriveTypes>();

                        for (String driveType : resultStoragePool.getSupportedDriveTypes()) {
                            switch (driveType) {
                                case "ssd":
                                    supportedDriveTypes.add(StoragePool.SupportedDriveTypes.SSD);
                                    break;
                                case "enterprise":
                                    supportedDriveTypes.add(StoragePool.SupportedDriveTypes.FC);
                                    break;
                                case "nearline":
                                    supportedDriveTypes.add(StoragePool.SupportedDriveTypes.SAS);
                                    supportedDriveTypes.add(StoragePool.SupportedDriveTypes.NL_SAS);
                                    break;
                            }
                        }
                        storagePool.setSupportedDriveTypes(supportedDriveTypes);

                        Set<StoragePool.Protocols> supportedProtocols = new HashSet<>();
                        supportedProtocols.add(StoragePool.Protocols.iSCSI);
                        supportedProtocols.add(StoragePool.Protocols.FC);
                        storagePool.setProtocols(supportedProtocols);

                        Set<StoragePool.RaidLevels> supportedRaidLevels = new HashSet<StoragePool.RaidLevels>();
                        supportedRaidLevels.add(StoragePool.RaidLevels.RAID0);
                        supportedRaidLevels.add(StoragePool.RaidLevels.RAID1);
                        supportedRaidLevels.add(StoragePool.RaidLevels.RAID5);
                        supportedRaidLevels.add(StoragePool.RaidLevels.RAID6);
                        supportedRaidLevels.add(StoragePool.RaidLevels.RAID10);
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
        } finally {
            if(connection != null){
                connection.disconnect();
            }
        }

        _log.info("discoverStoragePools() information for storage system {}, nativeId {} - end\n",
                storageSystem.getIpAddress(), storageSystem.getNativeId());

        return task;
    }

    /**
     * Discover Storage Ports
     * @param storageSystem
     * @param storagePorts
     * @return
     */
    public DriverTask discoverStoragePorts(StorageSystem storageSystem, List<StoragePort> storagePorts) {

        DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_DISCOVER_STORAGE_PORTS);

        _log.info("discoverStoragePorts() information for storage system {}, name {} - start",
                storageSystem.getIpAddress(), storageSystem.getSystemName());

        SSHConnection connection = null;

        try {

            connection = connectionManager.getClientBySystemId(storageSystem.getNativeId());

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
        }finally {
            if(connection != null){
                connection.disconnect();
            }
        }

        _log.info("discoverStoragePorts() information for storage system {}, nativeId {} - end\n",
                storageSystem.getIpAddress(), storageSystem.getNativeId());

        return task;
    }

    /**
     * Discover Storage Host Components
     * @param storageSystem
     * @param storageHosts
     * @return
     */
    public DriverTask discoverStorageHostComponents(StorageSystem storageSystem,
                                                    List<StorageHostComponent> storageHosts) {

        DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_DISCOVER_STORAGE_HOSTS);

        _log.info("discoverStorageHostComponents() for storage system {} - start", storageSystem.getNativeId());

        SSHConnection connection = null;

        try {

            connection = connectionManager.getClientBySystemId(storageSystem.getNativeId());

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
        } finally {
            if(connection != null){
                    connection.disconnect();
            }

        }

        _log.info("discoverStorageHostComponents() for storage system {} - end", storageSystem.getNativeId());

        return task;
    }

    /**
     * Get Storage Volumes
     * @param storageSystem
     * @param storageVolumes
     * @param token
     * @return task
     */
    public DriverTask getStorageVolumes(StorageSystem storageSystem, List<StorageVolume> storageVolumes,
                                        MutableInt token) {

        DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_GET_STORAGE_VOLUMES);

        _log.info("getStorageVolumes() information for storage system {}, name {} - start",
                storageSystem.getIpAddress(), storageSystem.getSystemName());

        SSHConnection connection = null;

        try {
            connection = connectionManager.getClientBySystemId(storageSystem.getNativeId());

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
        } finally {
            if(connection != null){
                connection.disconnect();
            }
        }

        _log.info("getStorageVolumes() information for storage system {}, nativeId {} - end",
                storageSystem.getIpAddress(), storageSystem.getNativeId());

        return task;
    }


    public List<VolumeSnapshot> getVolumeSnapshots(StorageVolume volume) {
        // TODO Auto-generated method stub
        return null;
    }


    public List<VolumeClone> getVolumeClones(StorageVolume volume) {
        // TODO Auto-generated method stub
        return null;
    }


    public List<VolumeMirror> getVolumeMirrors(StorageVolume volume) {
        // TODO Auto-generated method stub
        return null;
    }

    public Map<String, HostExportInfo> getVolumeExportInfoForHosts(StorageVolume volume) {
        // TODO Auto-generated method stub
        return null;
    }


    public Map<String, HostExportInfo> getSnapshotExportInfoForHosts(VolumeSnapshot snapshot) {
        // TODO Auto-generated method stub
        return null;
    }


    public Map<String, HostExportInfo> getCloneExportInfoForHosts(VolumeClone clone) {
        // TODO Auto-generated method stub
        return null;
    }


    public Map<String, HostExportInfo> getMirrorExportInfoForHosts(VolumeMirror mirror) {
        // TODO Auto-generated method stub
        return null;
    }

}
