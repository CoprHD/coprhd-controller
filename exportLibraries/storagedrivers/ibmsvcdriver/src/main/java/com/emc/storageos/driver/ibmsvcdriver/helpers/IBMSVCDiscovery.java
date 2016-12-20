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
import com.emc.storageos.driver.ibmsvcdriver.exceptions.IBMSVCDriverException;
import com.emc.storageos.driver.ibmsvcdriver.impl.IBMSVCClusterNode;
import com.emc.storageos.driver.ibmsvcdriver.impl.IBMSVCDriverTask;
import com.emc.storageos.driver.ibmsvcdriver.impl.IBMSVCStorageDriver;
import com.emc.storageos.driver.ibmsvcdriver.utils.IBMSVCConstants;
import com.emc.storageos.driver.ibmsvcdriver.utils.IBMSVCDriverConfiguration;
import com.emc.storageos.driver.ibmsvcdriver.utils.IBMSVCDriverUtils;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.HostExportInfo;
import com.emc.storageos.storagedriver.Registry;
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
    private Registry driverRegistry;
        private IBMSVCDriverConfiguration ibmsvcConfiguration = IBMSVCDriverConfiguration.getInstance();

    Map<String, HostExportInfo> hostExportCache = new HashMap<>();
    Map<String, List<StoragePort>> storagePortsCache = new HashMap<>();

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
     * Sets the persistence store.
     *
     * @param driverRegistry The driver persistence registry.
     */
    public void setDriverRegistry(Registry driverRegistry) {
        this.driverRegistry = driverRegistry;
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

                connectionManager.setConnInfoToRegistry(result.getProperty("SerialNumber"), storageSystem.getIpAddress(), storageSystem.getPortNumber(), storageSystem.getUsername(), storageSystem.getPassword());

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
        DriverTask.TaskStatus overallTaskState = DriverTask.TaskStatus.READY;

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
                        //supportedProtocols.add(StoragePool.Protocols.iSCSI);
                        supportedProtocols.add(StoragePool.Protocols.FC);
                        storagePool.setProtocols(supportedProtocols);

                        //TODO: Not discovering RAID Levels from Storage pools.
                        // RAID is configured at MDisk level for disks of type Array (Internal SVC Storage)
                        // should we look at RAID Group of each MDisk and consider that as a supported RAID Level
                        // We don't necessarily see RAID type in Managed disks (External Disks)

                        // https://www.ibm.com/support/knowledgecenter/STPVGU_7.3.0/com.ibm.storage.svc.console.730.doc/svc_mdisksovr_1bchav.html
                        // https://www.ibm.com/support/knowledgecenter/STPVGU_7.3.0/com.ibm.storage.svc.console.730.doc/svc_raid_07121736.html
                        // https://www.ibm.com/support/knowledgecenter/STPVGU_7.3.0/com.ibm.storage.svc.console.730.doc/svc_mdiskgroupovr_1bchdu.html

                        /*Set<StoragePool.RaidLevels> supportedRaidLevels = new HashSet<StoragePool.RaidLevels>();
                        supportedRaidLevels.add(StoragePool.RaidLevels.RAID0);
                        supportedRaidLevels.add(StoragePool.RaidLevels.RAID1);
                        supportedRaidLevels.add(StoragePool.RaidLevels.RAID5);
                        supportedRaidLevels.add(StoragePool.RaidLevels.RAID6);
                        supportedRaidLevels.add(StoragePool.RaidLevels.RAID10);
                        storagePool.setSupportedRaidLevels(supportedRaidLevels);*/

                        storagePool.setPoolServiceType(StoragePool.PoolServiceType.block);
                        storagePool.setSupportedResourceType(StoragePool.SupportedResourceType.THIN_AND_THICK);
                        storagePool.setAccessStatus(StorageObject.AccessStatus.READ_WRITE);

                        storagePool.setMinimumThickVolumeSize(IBMSVCDriverUtils.convertGBtoBytes(ibmsvcConfiguration.getDefaultStoragePoolMinThickVolSizeGB()));
                        storagePool.setMaximumThickVolumeSize(IBMSVCDriverUtils.convertGBtoBytes(ibmsvcConfiguration.getDefaultStoragePoolMaxThickVolSizeGB()));
                        storagePool.setMinimumThinVolumeSize(IBMSVCDriverUtils.convertGBtoBytes(ibmsvcConfiguration.getDefaultStoragePoolMinThinVolSizeGB()));
                        storagePool.setMaximumThinVolumeSize(IBMSVCDriverUtils.convertGBtoBytes(ibmsvcConfiguration.getDefaultStoragePoolMaxThinVolSizeGB()));
                        storagePools.add(storagePool);

                        _log.info(String.format("Processed storage pool %s.\n", storagePool.getPoolName()));

                    } else {
                        _log.warn(String.format("Processing storage pool failed %s\n",
                                resultStoragePool.getErrorString()), resultStoragePool.isSuccess());
                        task.setMessage(String.format("Processing storage pool %s failed : ", storagePool.getPoolName())
                                + resultStoragePool.getErrorString());
                        overallTaskState = DriverTask.TaskStatus.FAILED;
                    }
                }

                task.setMessage(String.format("All storage pool discovery for the storage system %s completed.",
                        storageSystem.getIpAddress()));
            } else {
                _log.error(String.format("All storage pool discovery for the storage system failed %s\n",
                        resultAllStoragePool.getErrorString()), resultAllStoragePool.isSuccess());
                task.setMessage(String.format("All storage pool discovery for the storage system %s failed : ",
                        storageSystem.getIpAddress()) + resultAllStoragePool.getErrorString());
                overallTaskState = DriverTask.TaskStatus.FAILED;
            }

        } catch (Exception e) {
            _log.error("Unable to query the storage pools information for the host {}\n",
                    storageSystem.getSystemName());
            task.setMessage(String.format("Unable to query the Storage Pools information for the host %s",
                    storageSystem.getSystemName()) + e.getMessage());
            overallTaskState = DriverTask.TaskStatus.FAILED;
            e.printStackTrace();
        } finally {
            if(connection != null){
                connection.disconnect();
            }
        }

        _log.info("discoverStoragePools() information for storage system {}, nativeId {} - end\n",
                storageSystem.getIpAddress(), storageSystem.getNativeId());
        task.setStatus(overallTaskState);
        return task;
    }

    /**
     * Discover Storage Ports
     * @param storageSystem Storage System to discover
     * @param storagePorts StoragePorts Discovered Type: Output
     * @return DriverTask
     */
    public DriverTask discoverStoragePorts(StorageSystem storageSystem, List<StoragePort> storagePorts) {

        DriverTask task = createDriverTask(IBMSVCConstants.TASK_TYPE_DISCOVER_STORAGE_PORTS);
        DriverTask.TaskStatus overallTaskState = DriverTask.TaskStatus.READY;

        _log.info("discoverStoragePorts() information for storage system {}, name {} - start",
                storageSystem.getIpAddress(), storageSystem.getSystemName());

        SSHConnection connection = null;

        try {

            connection = connectionManager.getClientBySystemId(storageSystem.getNativeId());

            Map<String, Object> result = new HashMap<>();

            discoverStoragePorts(connection, storageSystem.getNativeId(), storagePorts, result, null);

            task.setMessage(result.get("message").toString());
            overallTaskState = (DriverTask.TaskStatus) result.get("overallTaskState");

        } catch (Exception e) {
            _log.error("Unable to query the storage ports information for the Storge System {}\n",
                    storageSystem.getSystemName());
            task.setMessage(String.format("Unable to query the Storage Ports information for the Storge System %s",
                    storageSystem.getSystemName()) + e.getMessage());
            //task.setStatus(DriverTask.TaskStatus.FAILED);
            overallTaskState = DriverTask.TaskStatus.FAILED;
            e.printStackTrace();
        }finally {
            if(connection != null){
                connection.disconnect();
            }
        }

        _log.info("discoverStoragePorts() information for storage system {}, nativeId {} - end\n",
                storageSystem.getIpAddress(), storageSystem.getNativeId());
        task.setStatus(overallTaskState);
        return task;
    }


    private void discoverStoragePorts(SSHConnection connection, String storageSystemId, List<StoragePort> storagePorts, Map<String, Object> result, Map<String, List<StoragePort>> storagePortsCache){

        if(storagePortsCache != null && storagePortsCache.containsKey(storageSystemId)){
            List<StoragePort> cachedStoragePorts = storagePortsCache.get(storageSystemId);
            storagePorts.addAll(cachedStoragePorts);
            return;
        }

        IBMSVCQueryAllClusterNodeResult resultClusterNodes = IBMSVCCLI.queryAllClusterNodes(connection);

        result.put("overallTaskState", DriverTask.TaskStatus.READY);

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
                        storagePort.setStorageSystemId(storageSystemId);
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

                    result.put("message", String.format("Storage port discovery for the cluster node %s completed.",
                            clusterNode.getNodeName()));
                    //task.setStatus(DriverTask.TaskStatus.READY);

                } else {
                    _log.warn(String.format("Processing storage port for the cluster node failed %s\n",
                            resultStoragePort.getErrorString()), resultClusterNodes.isSuccess());
                    result.put("message", String.format("Processing storage port for the cluster node %s failed : ",
                            clusterNode.getNodeName()) + resultStoragePort.getErrorString());
                    //task.setStatus(DriverTask.TaskStatus.FAILED);
                    result.put("overallTaskState", DriverTask.TaskStatus.FAILED);
                    continue;
                }

                _log.info(String.format("Processed all storage port on node %s.\n", clusterNode.getNodeName()));
            }

        } else {
            _log.error(String.format("All storage port discovery for the storage system failed %s\n",
                    resultClusterNodes.getErrorString()), resultClusterNodes.isSuccess());
            result.put("message", String.format("All storage port discovery for the storage system failed : %s", resultClusterNodes.getErrorString()));
            //task.setStatus(DriverTask.TaskStatus.FAILED);
            result.put("overallTaskState", DriverTask.TaskStatus.FAILED);
        }

        // Cache the result
        if(storagePortsCache != null){
            storagePortsCache.put(storageSystemId, storagePorts);
        }


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
        DriverTask.TaskStatus overallTaskState = DriverTask.TaskStatus.READY;
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
                        //task.setStatus(DriverTask.TaskStatus.READY);

                    } else {
                        _log.error(
                                String.format("Querying host initiator for host Id %s failed %s\n",
                                        resultHostInitiator.getHostId(), resultHostInitiator.getErrorString()),
                                resultHostInitiator.isSuccess());
                        task.setMessage(String.format("Querying host initiator for host Id %s failed : ",
                                resultHostInitiator.getHostId()) + resultHostInitiator.getErrorString());
                        //task.setStatus(DriverTask.TaskStatus.FAILED);
                        overallTaskState = DriverTask.TaskStatus.FAILED;
                    }
                }

            } else {
                _log.error(String.format("Querying all host failed %s\n", resultAllHost.getErrorString()),
                        resultAllHost.isSuccess());
                task.setMessage(String.format("Querying all host failed : %s", resultAllHost.getErrorString()));
                //task.setStatus(DriverTask.TaskStatus.FAILED);
                overallTaskState = DriverTask.TaskStatus.FAILED;
            }

        } catch (Exception e) {
            _log.error("Unable to query the hosts information on the storage system {}", storageSystem.getNativeId());
            task.setMessage(String.format("Unable to query the hosts information on the storage system %s",
                    storageSystem.getNativeId()) + e.getMessage());
            //task.setStatus(DriverTask.TaskStatus.FAILED);
            overallTaskState = DriverTask.TaskStatus.FAILED;
            e.printStackTrace();
        } finally {
            if(connection != null){
                    connection.disconnect();
            }

        }

        _log.info("discoverStorageHostComponents() for storage system {} - end", storageSystem.getNativeId());
        task.setStatus(overallTaskState);
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

        // Reset Cache
        this.hostExportCache = new HashMap<>();
        this.storagePortsCache = new HashMap<>();

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

                    storageVolume.setStorageSystemId(storageSystem.getNativeId());

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

        String storageSystemID = volume.getStorageSystemId();
        Map<String, HostExportInfo> volumeExportInfo = new HashMap<>();



        _log.info("getVolumeExportInfoForHosts() information for storage system {}, volume id {} - start",
                volume.getStorageSystemId(), volume.getNativeId());

        SSHConnection connection = null;

        try {
            connection = connectionManager.getClientBySystemId(volume.getStorageSystemId());

            _log.info(String.format("Query volume to Host Mapping %s.%n", volume.getNativeId()));
            IBMSVCQueryVolumeHostMappingResult resultVolumeHostMapping = IBMSVCCLI.queryVolumeHostMapping(connection, volume.getNativeId());

            if (resultVolumeHostMapping.isSuccess()) {

                _log.info(String.format("Getting all storage ports for array %s.%n", volume.getStorageSystemId()));
                // Get all storage array ports
                List<StoragePort> allStoragePorts = new ArrayList<>();
                Map<String, Object> result = new HashMap<>();

                discoverStoragePorts(connection, volume.getStorageSystemId(), allStoragePorts, result, storagePortsCache);

                //For each host the volume is mapped to get Host Export Info
                for (IBMSVCHost host : resultVolumeHostMapping.getHostList()) {
                    HostExportInfo hostExportInfo = getHostExportInfo(connection, host,  allStoragePorts, hostExportCache);
                    volumeExportInfo.put(hostExportInfo.getHostName(), hostExportInfo);
                }


            } else {
                _log.error(String.format("Processing volume host mapping failed for volume %s - %s\n",
                        volume.getNativeId(), resultVolumeHostMapping.getErrorString()), resultVolumeHostMapping.isSuccess());

            }

        } catch (Exception e) {
            _log.error("Unable to query the storage volumes to host mapping information for the volume {}",
                    volume.getNativeId(), e.getLocalizedMessage());
            e.printStackTrace();
        } finally {
            if(connection != null){
                connection.disconnect();
            }
        }

        _log.info("getVolumeExportInfoForHosts() information for storage system {}, volume id {} - end",
                volume.getStorageSystemId(), volume.getNativeId());

        return volumeExportInfo;
        //return null;
    }

    /**
     * Get Host Export Info
     * @param connection SSH Connection
     * @param host IBM SVC Host
     * @param allStoragePorts List of all storage ports
     * @return hostExportInfo
     * @throws IBMSVCDriverException
     */
    private HostExportInfo getHostExportInfo(SSHConnection connection, IBMSVCHost host,  List<StoragePort> allStoragePorts, Map<String, HostExportInfo> hostExportCache )throws IBMSVCDriverException{

        String hostName = host.getHostName();
        //Remove underscore if it is present. IBM SVC does not accept hostname that starts with a digit. So hostname as IP addresses are prefixed with an underscore.
        // TODO: Look into this. What about host names with neither FQDN or ip address? Does ViPR accept those?
        if(hostName.charAt(0) == '_'){
            hostName = hostName.replaceFirst("_", "");
        }

        // Get hostExportInfo from Cache if possible
        if(hostExportCache.containsKey(hostName)){
            return hostExportCache.get(hostName);
        }

        _log.info(String.format("Processing host export info %s.\n", host.getHostName()));

        List<String> storageObjectNativeIds;
        List<Initiator> initiators;
        List<StoragePort> targets;

        IBMSVCQueryHostVolMapResult queryHostVolMapResult = IBMSVCCLI.queryHostVolMap(connection, host.getHostId());
        if(queryHostVolMapResult.isSuccess()){
            storageObjectNativeIds = new ArrayList<>(queryHostVolMapResult.getVolHluMap().keySet());
        }else{
            throw new IBMSVCDriverException(String.format("Unable to query host volume map for host %s - %s" , host.getHostName(), queryHostVolMapResult.getErrorString()));
        }

        _log.info(String.format("Volumes for host %s - %s .%n", host.getHostName(), storageObjectNativeIds));

        IBMSVCQueryHostInitiatorResult queryHostInitiatorResult = IBMSVCCLI.queryHostInitiator(connection, host.getHostId());

        if(queryHostInitiatorResult.isSuccess()){
            initiators = queryHostInitiatorResult.getHostInitiatorList();
        }else{
            throw new IBMSVCDriverException(String.format("Unable to query initiators for host %s - %s" , host.getHostName(), queryHostInitiatorResult.getErrorString()));
        }

        _log.info(String.format("Initiators for host %s - %s .%n", host.getHostName(), initiators));

        // Attempt to get target list from fabric connectivity. If host is already connected to array via fabric
        IBMSVCQueryFabricResult queryFabricResult = IBMSVCCLI.queryFabricConnectivity(connection, host.getHostName());

        if(queryFabricResult.isSuccess() && queryFabricResult.getTargetList() != null && queryFabricResult.getTargetList().size() > 0){
            List<String> targetList = queryFabricResult.getTargetList();
            targets = filterStoragePorts(allStoragePorts, targetList);
        }else{
            //Get target ports using customer specific logic
            IBMSVCProvisioning ibmsvcProvisioningHelper = new IBMSVCProvisioning();
            List<StoragePort> storagePorts = ibmsvcProvisioningHelper.identifyStoragePortListForHost(connection, host.getHostName());
            targets = matchStoragePorts(allStoragePorts, storagePorts);
        }

        _log.info(String.format("Targets for host %s - %s .%n", host.getHostName(), targets));


        HostExportInfo hostExportInfo = new HostExportInfo(hostName, storageObjectNativeIds, initiators, targets);

        // Store host info in cache
        hostExportCache.put(hostName, hostExportInfo);

        _log.info(String.format("Processed host export info %s.\n", host.getHostName()));
        return hostExportInfo;
    }

    /**
     * Filter storage ports from a string list
     * @param allStoragePorts All Storage Ports
     * @param targetPortList Target Storage Ports
     * @return
     */
    private List<StoragePort> filterStoragePorts(List<StoragePort> allStoragePorts, List<String> targetPortList){

        List<StoragePort> filteredStoragePort = new ArrayList<>();

        for(String targetPort : targetPortList){
            for(StoragePort storagePort: allStoragePorts){
                if(targetPort.equalsIgnoreCase(storagePort.getNativeId())){
                    filteredStoragePort.add(storagePort);
                }
            }
        }

        return filteredStoragePort;

    }

    /**
     * Filter storage ports from another port list
     * @param allStoragePorts
     * @param targetPortList
     * @return
     */
    private List<StoragePort> matchStoragePorts(List<StoragePort> allStoragePorts, List<StoragePort> targetPortList){

        List<StoragePort> filteredStoragePort = new ArrayList<>();

        for(StoragePort targetPort : targetPortList){
            for(StoragePort storagePort: allStoragePorts){
                if(targetPort.getNativeId().equalsIgnoreCase(storagePort.getNativeId())){
                    filteredStoragePort.add(storagePort);
                }
            }
        }

        return filteredStoragePort;

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
