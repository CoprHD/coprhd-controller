package com.emc.storageos.driver.scaleio;

import java.util.*;

import com.emc.storageos.driver.scaleio.api.restapi.ScaleIORestClientFactory;
import com.emc.storageos.driver.scaleio.api.restapi.response.*;
import org.apache.commons.lang.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.emc.storageos.driver.scaleio.api.ScaleIOConstants;
import com.emc.storageos.driver.scaleio.api.restapi.ScaleIORestClient;
import com.emc.storageos.storagedriver.AbstractStorageDriver;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.RegistrationData;
import com.emc.storageos.storagedriver.model.*;
import com.emc.storageos.storagedriver.storagecapabilities.CapabilityInstance;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;

public class ScaleIOStorageDriver extends AbstractStorageDriver {

    private static final Logger log = LoggerFactory.getLogger(ScaleIOStorageDriver.class);
    String fullyQualifiedXMLConfigName = "/scaleio-driver-prov.xml";
    ApplicationContext context = new ClassPathXmlApplicationContext(fullyQualifiedXMLConfigName);
    ScaleIORestHandleFactory scaleIORestHandleFactory = (ScaleIORestHandleFactory) context.getBean("scaleIORestHandleFactory");
    private ScaleIORestClient client;



    /**
     * Create storage volumes with a given set of capabilities.
     * Before completion of the request, set all required data for provisioned volumes in "volumes" parameter.
     *
     * @param volumes Input/output argument for volumes.
     * @param capabilities Input argument for capabilities. Defines storage capabilities of volumes to create.
     * @return task
     */
    public DriverTask createVolumes(List<StorageVolume> volumes, StorageCapabilities capabilities) {
        String taskType = "create-volume";
        String taskID = String.format("%s+%s+%s", ScaleIOConstants.DRIVER_NAME, taskType, UUID.randomUUID());
        DriverTaskImpl task = new DriverTaskImpl(taskID);

        if (volumes != null && volumes.size() > 0) {
            int successful = 0;

            // Assume volumes can be created for different storage systems
            for (StorageVolume volume : volumes) {
                String capacity = volume.getRequestedCapacity().toString();

                 try {
                     client = this.getClientBySystemId(volume.getStorageSystemId());

                     if (client != null) {
                         ScaleIOVolume result;

                         try {
                             result = client.addVolume(volume.getStorageSystemId(), volume.getStoragePoolId(),
                                     volume.getDisplayName(), capacity);

                             if (result != null) {
                                 volume.setNativeId(result.getId());
                                 long sizeInBytes = Long.parseLong(result.getSizeInKb()) * 1000;
                                 volume.setAllocatedCapacity(sizeInBytes);

                                 successful++;
                             } else {
                                 log.error("Exception while creating volume");
                             }

                         } catch (Exception e) {
                             log.error("Exception while creating volume", e);
                         }
                     }

                     } catch(Exception e) {
                         log.error("Exception while getting client instance", e);
                     }
            }

            this.setTaskStatus(volumes.size(), successful, task);

        } else {
            log.error("Empty volume input list");
            task.setStatus(DriverTask.TaskStatus.FAILED);
        }

        return task;
    }

    /**
     * Expand volume.
     * Before completion of the request, set all required data for expanded volume in "volume" parameter.
     *
     * @param volume Volume to expand. Type: Input/Output argument.
     * @param newCapacity Requested capacity in GB. Type: input argument.
     * @return task
     */
    public DriverTask expandVolume(StorageVolume volume, long newCapacity) {
        String taskType = "expand-volume";
        String taskID = String.format("%s+%s+%s", ScaleIOConstants.DRIVER_NAME, taskType, UUID.randomUUID());
        DriverTaskImpl task = new DriverTaskImpl(taskID);

        if (newCapacity > 0) {
            ScaleIORestClient client = this.getClientBySystemId(volume.getStorageSystemId());

            if (client != null) {
                ScaleIOVolume result;

                try {

                    result = client.modifyVolumeCapacity(volume.getNativeId(), String.valueOf(newCapacity));

                    if (result != null) {
                        task.setStatus(DriverTask.TaskStatus.READY);
                        return task;
                    }

                } catch (Exception e) {
                    log.error("Exception while expanding volume", e);
                }

            } else {
                log.error("Exception while getting client instance");
            }

        } else {
            log.error("Invalid new capacity");
        }

        task.setStatus(DriverTask.TaskStatus.FAILED);
        return task;
    }

    /**
     * Delete volumes.
     *
     * @param volumes Volumes to delete.
     * @return task
     */
    public DriverTask deleteVolumes(List<StorageVolume> volumes) {
        String taskType = "delete-volume";
        String taskID = String.format("%s+%s+%s", ScaleIOConstants.DRIVER_NAME, taskType, UUID.randomUUID());
        DriverTaskImpl task = new DriverTaskImpl(taskID);

        if (volumes.size() != 0) {
            int successful = 0;

            for (StorageVolume volume : volumes) {
                ScaleIORestClient client = this.getClientBySystemId(volume.getStorageSystemId());

                if (client != null) {
                    try {
                        client.removeVolume(volume.getNativeId());
                        successful++;

                    } catch (Exception e) {
                        log.error("Exception while deleting volume", e);
                    }

                } else {
                    log.error("Exception while getting client instance");
                }
            }

            this.setTaskStatus(volumes.size(), successful, task);

        } else {
            task.setStatus(DriverTask.TaskStatus.FAILED);
            log.error("Empty volume input list");
        }

        return task;
    }

    /**
     * Create volume snapshots.
     *
     * @param snapshots Type: Input/Output.
     * @param capabilities capabilities required from snapshots. Type: Input.
     * @return task
     */
    public DriverTask createVolumeSnapshot(List<VolumeSnapshot> snapshots, StorageCapabilities capabilities) {
        return null;
    }

    /**
     * Restore volume to snapshot state.
     *
     * @param volume Type: Input/Output.
     * @param snapshot Type: Input.
     * @return task
     */
    public DriverTask restoreSnapshot(StorageVolume volume, VolumeSnapshot snapshot) {
        return null;
    }

    /**
     * Delete snapshots.
     *
     * @param snapshots Type: Input.
     * @return task
     */
    public DriverTask deleteVolumeSnapshot(List<VolumeSnapshot> snapshots) {
        return null;
    }

    /**
     * Clone volume clones.
     *
     * @param clones Type: Input/Output.
     * @param capabilities capabilities of clones. Type: Input.
     * @return task
     */
    public DriverTask createVolumeClone(List<VolumeClone> clones, StorageCapabilities capabilities) {
        return null;
    }

    /**
     * Detach volume clones.
     *
     * @param clones Type: Input/Output.
     * @return task
     */
    public DriverTask detachVolumeClone(List<VolumeClone> clones) {
        return null;
    }

    /**
     * Restore from clone.
     *
     * @param volume Type: Input/Output.
     * @param clone Type: Input.
     * @return task
     */
    public DriverTask restoreFromClone(StorageVolume volume, VolumeClone clone) {
        return null;
    }

    /**
     * Delete volume clones.
     *
     * @param clones clones to delete. Type: Input.
     * @return
     */
    public DriverTask deleteVolumeClone(List<VolumeClone> clones) {
        return null;
    }

    /**
     * Create volume mirrors.
     *
     * @param mirrors Type: Input/Output.
     * @param capabilities capabilities of mirrors. Type: Input.
     * @return task
     */
    public DriverTask createVolumeMirror(List<VolumeMirror> mirrors, StorageCapabilities capabilities) {
        return null;
    }

    /**
     * Delete mirrors.
     *
     * @param mirrors mirrors to delete. Type: Input.
     * @return task
     */
    public DriverTask deleteVolumeMirror(List<VolumeMirror> mirrors) {
        return null;
    }

    /**
     * Split mirrors
     *
     * @param mirrors Type: Input/Output.
     * @return task
     */
    public DriverTask splitVolumeMirror(List<VolumeMirror> mirrors) {
        return null;
    }

    /**
     * Resume mirrors after split
     *
     * @param mirrors Type: Input/Output.
     * @return task
     */
    public DriverTask resumeVolumeMirror(List<VolumeMirror> mirrors) {
        return null;
    }

    /**
     * Restore volume from a mirror
     *
     * @param volume Type: Input/Output.
     * @param mirror Type: Input.
     * @return task
     */
    public DriverTask restoreVolumeMirror(StorageVolume volume, VolumeMirror mirror) {
        return null;
    }

    /**
     * Get export masks for a given set of initiators.
     *
     * @param storageSystem Storage system to get ITLs from. Type: Input.
     * @param initiators Type: Input.
     * @return list of export masks
     */
    public List<ITL> getITL(StorageSystem storageSystem, List<Initiator> initiators) {
        return null;
    }

    /**
     * Export volumes to initiators through a given set of ports. If ports are not provided,
     * use port requirements from ExportPathsServiceOption storage capability
     *
     * @param initiators Type: Input.
     * @param volumes Type: Input.
     * @param recommendedPorts recommended list of ports. Optional. Type: Input.
     * @param capabilities storage capabilities. Type: Input.
     * @return task
     */
    public DriverTask exportVolumesToInitiators(List<Initiator> initiators, List<StorageVolume> volumes,
            List<StoragePort> recommendedPorts, StorageCapabilities capabilities) {
        return null;
    }

    /**
     * Unexport volumes from initiators
     *
     * @param initiators Type: Input.
     * @param volumes Type: Input.
     * @return task
     */
    public DriverTask unexportVolumesFromInitiators(List<Initiator> initiators, List<StorageVolume> volumes) {
        return null;
    }

    /**
     * Create block consistency group.
     *
     * @param consistencyGroup input/output
     * @return
     */
    public DriverTask createConsistencyGroup(VolumeConsistencyGroup consistencyGroup) {
        return null;
    }

    /**
     *
     * Delete block consistency group.
     *
     * @param consistencyGroup Input
     * @return
     */
    public DriverTask deleteConsistencyGroup(VolumeConsistencyGroup consistencyGroup) {
        return null;
    }

    /**
     * Create snapshot of consistency group.
     *
     * @param consistencyGroup input parameter
     * @param snapshots input/output parameter
     * @param capabilities Capabilities of snapshots. Type: Input.
     * @return
     */
    public DriverTask createConsistencyGroupSnapshot(VolumeConsistencyGroup consistencyGroup, List<VolumeSnapshot> snapshots,
            List<CapabilityInstance> capabilities) {
        return null;
    }

    /**
     * Delete snapshot.
     *
     * @param snapshots Input.
     * @return
     */
    public DriverTask deleteConsistencyGroupSnapshot(List<VolumeSnapshot> snapshots) {
        return null;
    }

    /**
     * Create clone of consistency group.
     *
     * @param consistencyGroup input/output
     * @param clones output
     * @param capabilities Capabilities of clones. Type: Input.
     * @return
     */
    public DriverTask createConsistencyGroupClone(VolumeConsistencyGroup consistencyGroup, List<VolumeClone> clones,
            List<CapabilityInstance> capabilities) {
        return null;
    }

    /**
     * Delete consistency group clone
     *
     * @param clones output
     * @return
     */
    public DriverTask deleteConsistencyGroupClone(List<VolumeClone> clones) {
        return null;
    }

    /**
     * Get driver registration data.
     */
    public RegistrationData getRegistrationData() {
        return null;
    }

    /**
     * Discover storage systems and their capabilities
     *
     * @param storageSystems StorageSystems to discover. Type: Input/Output.
     * @return
     */
    @Override
    public DriverTask discoverStorageSystem(List<StorageSystem> storageSystems) {
        DriverTask task = createDriverTask(ScaleIOConstants.TASK_TYPE_DISCOVER_STORAGE_SYSTEM);
        for (StorageSystem storageSystem : storageSystems) {
            try {
                log.info("StorageDriver: Discovery information for storage system {}, name {} - Start", storageSystem.getIpAddress(), storageSystem.getSystemName());
                ScaleIORestClient scaleIOHandle = scaleIORestHandleFactory.getClientHandle(storageSystem.getSystemName(), storageSystem.getIpAddress(), storageSystem.getPortNumber(), storageSystem.getUsername(), storageSystem.getPassword());
                if (scaleIOHandle != null) {
                    ScaleIOSystem scaleIOSystem = scaleIOHandle.getSystem();
                    List<ScaleIOProtectionDomain> protectionDomains = scaleIOHandle.getProtectionDomains();
                    for (ScaleIOProtectionDomain protectionDomain : protectionDomains) {
                        String domainName = protectionDomain.getName();
                        if (compare(domainName, storageSystem.getSystemName())) {
                            storageSystem.setNativeId(protectionDomain.getId());
                            storageSystem.setSystemName(domainName);
                            storageSystem.setSerialNumber(protectionDomain.getSystemId());
                            storageSystem.setSystemType(protectionDomain.getProtectionDomainState());
                            String version = scaleIOSystem.getVersion().replaceAll("_", ".").substring(ScaleIOConstants.START_POS, ScaleIOConstants.END_POS);
                            storageSystem.setFirmwareVersion(version);
                            if ((ScaleIOConstants.MINIMUM_SUPPORTED_VERSION) < Double.valueOf(version)) {
                                storageSystem.setIsSupportedVersion(ScaleIOConstants.INCOMPATIBLE);
                            } else {
                                storageSystem.setIsSupportedVersion(ScaleIOConstants.COMPATIBLE);
                            }
                            task.setStatus(DriverTask.TaskStatus.READY);
                            setConnInfoToRegistry(storageSystem.getNativeId(), storageSystem.getIpAddress(), storageSystem.getPortNumber(), storageSystem.getUsername(), storageSystem.getPassword());
                            log.info("StorageDriver: Discovery information for storage system {}, name {} - End", storageSystem.getIpAddress(), storageSystem.getSystemName());
                        }
                    }
                } else {
                    log.info("StorageDriver: Discovery failed to get an handle for the storage system {}, name {}", storageSystem.getIpAddress(), storageSystem.getSystemName());
                    task.setStatus(DriverTask.TaskStatus.FAILED);
                }
            } catch (Exception e) {
                log.error("StorageDriver: Discovery failed for the storage system {}, name {}", storageSystem.getIpAddress(), storageSystem.getSystemName());
                task.setStatus(DriverTask.TaskStatus.ABORTED);
            }
        }
        return task;
    }

    /**
     * Discover storage pools and their capabilities.
     *
     * @param storageSystem Type: Input.
     * @param storagePools  Type: Output.
     * @return
     */
    @Override
    public DriverTask discoverStoragePools(StorageSystem storageSystem, List<StoragePool> storagePools) {
        DriverTask task = createDriverTask(ScaleIOConstants.TASK_TYPE_DISCOVER_STORAGE_POOLS);
        try {
            log.info("StorageDriver: Discovery of storage pools for storage system {}, name {} - Start", storageSystem.getIpAddress(), storageSystem.getSystemName());
            ScaleIORestClient scaleIOHandle = scaleIORestHandleFactory.getClientHandle(storageSystem.getNativeId(), storageSystem.getIpAddress(), storageSystem.getPortNumber(), storageSystem.getUsername(), storageSystem.getPassword());
            if (scaleIOHandle != null) {
                List<ScaleIOProtectionDomain> protectionDomains = scaleIOHandle.getProtectionDomains();
                for (ScaleIOProtectionDomain protectionDomain : protectionDomains) {
                    String domainID = protectionDomain.getSystemId();
                    if (compare(domainID, storageSystem.getNativeId())) {
                        List<ScaleIOStoragePool> scaleIOStoragePoolList = scaleIOHandle.getProtectionDomainStoragePools(protectionDomain.getId());
                        StoragePool pool;
                        for (ScaleIOStoragePool storagePool : scaleIOStoragePoolList) {
                            pool = new StoragePool();
                            pool.setNativeId(storagePool.getId());
                            log.info("StorageDriver: Discovered Pool {}, storageSystem {}", pool.getNativeId(), pool.getStorageSystemId());
                            pool.setStorageSystemId(protectionDomain.getId());
                            pool.setPoolName(storagePool.getName());
                            Set<StoragePool.Protocols> protocols = new HashSet<>();
                            protocols.add(StoragePool.Protocols.FC);
                            protocols.add(StoragePool.Protocols.iSCSI);
                            pool.setProtocols(protocols);
                            pool.setPoolServiceType(StoragePool.PoolServiceType.block);
                            pool.setTotalCapacity(Long.valueOf(storagePool.getMaxCapacityInKb()));
                            pool.setFreeCapacity(Long.valueOf(storagePool.getCapacityAvailableForVolumeAllocationInKb()));
                            pool.setSupportedResourceType(StoragePool.SupportedResourceType.THIN_AND_THICK);
                            pool.setOperationalStatus(StoragePool.PoolOperationalStatus.READY);
                            Set<StoragePool.SupportedDriveTypes> supportedDriveTypes = new HashSet<>();
                            supportedDriveTypes.add(StoragePool.SupportedDriveTypes.FC);
                            supportedDriveTypes.add(StoragePool.SupportedDriveTypes.SATA);
                            pool.setSupportedDriveTypes(supportedDriveTypes);
                            storagePools.add(pool);
                        }
                    }
                }
                task.setStatus(DriverTask.TaskStatus.READY);
                log.info("StorageDriver: Discovery of storage pools for storage system {}, name {} - End", storageSystem.getIpAddress(), storageSystem.getSystemName());
            } else {
                log.info("StorageDriver: Failed to get an handle for the storage system {}, name {}", storageSystem.getIpAddress(), storageSystem.getSystemName());
                task.setStatus(DriverTask.TaskStatus.FAILED);
            }
        } catch (Exception e) {
            log.error("StorageDriver: Discovery of storage pools failed for the storage system {}, name {}", storageSystem.getIpAddress(), storageSystem.getSystemName());
            task.setStatus(DriverTask.TaskStatus.ABORTED);
        }
        return task;
    }

    /**
     * Discover storage ports and their capabilities
     *
     * @param storageSystem Type: Input.
     * @param storagePorts  Type: Output.
     * @return
     */
    @Override
    public DriverTask discoverStoragePorts(StorageSystem storageSystem, List<StoragePort> storagePorts) {
        DriverTask task = createDriverTask(ScaleIOConstants.TASK_TYPE_DISCOVER_STORAGE_PORTS);
        try {
            log.info("StorageDriver: Discovery of storage ports for storage system {}, name {} - Start", storageSystem.getNativeId(), storageSystem.getSystemName());
            ScaleIORestClient scaleIOHandle = scaleIORestHandleFactory.getClientHandle(storageSystem.getNativeId(), storageSystem.getIpAddress(), storageSystem.getPortNumber(), storageSystem.getUsername(), storageSystem.getPassword());
            if (scaleIOHandle != null) {
                List<ScaleIOSDS> allSDSs = scaleIOHandle.queryAllSDS();
                for (ScaleIOSDS sds : allSDSs) {
                    StoragePort port;
                    String pdId = sds.getProtectionDomainId();
                    if (compare(pdId, storageSystem.getNativeId())) {
                        String sdsId = sds.getId();
                        List<ScaleIOSDS.IP> ips = sds.getIpList();
                        String sdsIP = null;
                        if (ips != null && !ips.isEmpty()) {
                            sdsIP = ips.get(0).getIp();
                        }

                        if (sdsId != null && compare(sds.getSdsState(), ScaleIOConstants.OPERATIONAL_STATUS_CONNECTED)) {
                            port = new StoragePort();
                            port.setNativeId(sdsId);
                            log.info("StorageDriver: Discovered port {}, storageSystem {}", port.getNativeId(), port.getStorageSystemId());
                            port.setDeviceLabel(String.format("%s-%s-StoragePort", sds.getName(), sdsId));
                            port.setPortName(sds.getName());
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
                task.setStatus(DriverTask.TaskStatus.READY);
                log.info("StorageDriver: Discovery of storage ports for storage system {}, name {} - End", storageSystem.getIpAddress(), storageSystem.getSystemName());
            } else {
                log.info("StorageDriver: Failed to get an handle for the storage system {}, name {}", storageSystem.getIpAddress(), storageSystem.getSystemName());
                task.setStatus(DriverTask.TaskStatus.FAILED);
            }
        } catch (Exception e) {
            log.error("StorageDriver: Discovery of storage ports failed for the storage system {}, name {}", storageSystem.getIpAddress(), storageSystem.getSystemName());
            task.setStatus(DriverTask.TaskStatus.ABORTED);
        }
        return task;
    }

    /**
     * Discover storage volumes
     *
     * @param storageSystem Type: Input.
     * @param storageVolumes Type: Output.
     * @param token used for paging. Input 0 indicates that the first page should be returned. Output 0 indicates
     *            that last page was returned. Type: Input/Output.
     * @return
     */
    public DriverTask getStorageVolumes(StorageSystem storageSystem, List<StorageVolume> storageVolumes, MutableInt token) {
        return null;
    }

    /**
     * Get list of supported storage system types. Ex. vmax, vnxblock, hitachi, etc...
     *
     * @return list of supported storage system types
     */
    public List<String> getSystemTypes() {
        return null;
    }

    /**
     * Return driver task with a given id.
     *
     * @param taskId
     * @return
     */
    public DriverTask getTask(String taskId) {
        return null;
    }

    /**
     * Get storage object with a given type with specified native ID which belongs to specified storage system
     *
     * @param storageSystemId storage system native id
     * @param objectId object native id
     * @param type class instance
     * @return storage object or null if does not exist
     *         <p/>
     *         Example of usage: StorageVolume volume = StorageDriver.getStorageObject("vmax-12345", "volume-1234", StorageVolume.class);
     */
    public <T extends StorageObject> T getStorageObject(String storageSystemId, String objectId, Class<T> type) {
        return null;
    }

    /**
     * Get connection info from registry
     *
     * @param systemNativeId
     * @param attrName use string constants in the scaleioConstants.java. e.g. ScaleIOConstants.IP_ADDRESS
     * @return Ip_address, port, username or password for given systemId and attribute name
     */
    public String getConnInfoFromRegistry(String systemNativeId, String attrName) {
        Map<String, List<String>> attributes = this.driverRegistry.getDriverAttributesForKey(ScaleIOConstants.DRIVER_NAME, systemNativeId);
        if (attributes == null) {
            log.info("Connection info for " + systemNativeId + " is not set up in the registry");
            return null;
        } else if (attributes.get(attrName) == null) {
            log.info(attrName + "is not found in the registry");
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
    public void setConnInfoToRegistry(String systemNativeId, String ipAddress, int port, String username, String password) {
        Map<String, List<String>> attributes = new HashMap<>();
        List<String> listIP = new ArrayList<>();
        listIP.add(ipAddress);
        attributes.put(ScaleIOConstants.IP_ADDRESS, listIP);
        List<String> listPort = new ArrayList<>();
        listPort.add(Integer.toString(port));
        attributes.put(ScaleIOConstants.PORT_NUMBER, listPort);
        List<String> listUserName = new ArrayList<>();
        listUserName.add(username);
        attributes.put(ScaleIOConstants.USER_NAME, listUserName);
        List<String> listPwd = new ArrayList<>();
        listPwd.add(password);
        attributes.put(ScaleIOConstants.PASSWORD, listPwd);

        this.driverRegistry.setDriverAttributesForKey(ScaleIOConstants.DRIVER_NAME, systemNativeId, attributes);
    }

    public void setTaskStatus(int operations, int successful, DriverTask task) {
        if (successful == 0) {
            task.setStatus(DriverTask.TaskStatus.FAILED);
        } else if (successful > 0 && successful < operations) {
            task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
        } else if (operations == successful) {
            task.setStatus(DriverTask.TaskStatus.READY);
        }
    }

    private ScaleIORestClient getClientBySystemId(String systemId) {
        String ip_address, port, username, password;
        ScaleIORestClient client;
        ip_address = this.getConnInfoFromRegistry(systemId, ScaleIOConstants.IP_ADDRESS);
        port = this.getConnInfoFromRegistry(systemId, ScaleIOConstants.PORT_NUMBER);
        username = this.getConnInfoFromRegistry(systemId, ScaleIOConstants.USER_NAME);
        password = this.getConnInfoFromRegistry(systemId, ScaleIOConstants.PASSWORD);
        if (ip_address != null && port != null && username != null && password != null) {
            try {
                client = scaleIORestHandleFactory.getClientHandle(systemId, ip_address, Integer.parseInt(port), username, password);
                return client;
            } catch (Exception e) {
                log.error("Exception when creating rest client instance.", e);
                return null;
            }
        } else {
            log.info("Exception when retrieving connection information found.");
            return null;
        }
    }

    /**
     * Compare domain name and system name
     *
     * @param domainName
     * @param systemName
     */
    public Boolean compare(String domainName, String systemName) {
        if (domainName.equalsIgnoreCase(systemName)) {
            return true;
        }
        return false;
    }

    /**
     * Create driver task for task type
     *
     * @param taskType
     */
    public DriverTask createDriverTask(String taskType) {
        String taskID = String.format("%s+%s+%s", ScaleIOConstants.DRIVER_NAME, taskType, UUID.randomUUID());
        DriverTask task = new DriverTaskImpl(taskID);
        return task;
    }

}
