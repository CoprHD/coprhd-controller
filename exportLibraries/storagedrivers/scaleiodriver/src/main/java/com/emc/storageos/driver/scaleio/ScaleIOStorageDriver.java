/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.scaleio;

import com.emc.storageos.driver.scaleio.api.ScaleIOConstants;
import com.emc.storageos.driver.scaleio.api.restapi.ScaleIORestClient;
import com.emc.storageos.driver.scaleio.api.restapi.response.*;
import com.emc.storageos.storagedriver.AbstractStorageDriver;
import com.emc.storageos.storagedriver.BlockStorageDriver;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.RegistrationData;
import com.emc.storageos.storagedriver.model.*;
import com.emc.storageos.storagedriver.storagecapabilities.CapabilityInstance;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;
import org.apache.commons.lang.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.*;

public class ScaleIOStorageDriver extends AbstractStorageDriver implements BlockStorageDriver {
    private static final Logger log = LoggerFactory.getLogger(ScaleIOStorageDriver.class);
    String fullyQualifiedXMLConfigName = "/scaleio-driver-prov.xml";
    ApplicationContext context = new ClassPathXmlApplicationContext(fullyQualifiedXMLConfigName);
    ScaleIORestHandleFactory scaleIORestHandleFactory = (ScaleIORestHandleFactory) context.getBean("scaleIORestHandleFactory");
    private ScaleIORestClient client;
    private int countSucc;

    /**
     * Create storage volumes with a given set of capabilities.
     * Before completion of the request, set all required data for provisioned volumes in "volumes" parameter.
     *
     * @param volumes Input/output argument for volumes.
     * @param capabilities Input argument for capabilities. Defines storage capabilities of volumes to create.
     * @return task
     */
    @Override
    public DriverTask createVolumes(List<StorageVolume> volumes, StorageCapabilities capabilities) {
        return null;
    }

    /**
     * Expand volume.
     * Before completion of the request, set all required data for expanded volume in "volume" parameter.
     *
     * @param volume Volume to expand. Type: Input/Output argument.
     * @param newCapacity Requested capacity. Type: input argument.
     * @return task
     */
    @Override
    public DriverTask expandVolume(StorageVolume volume, long newCapacity) {
        return null;
    }

    /**
     * Delete volumes.
     *
     * @param volumes Volumes to delete.
     * @return task
     */
    @Override
    public DriverTask deleteVolumes(List<StorageVolume> volumes) {
        return null;
    }

    /**
     * Create volume snapshots.
     *
     * @param snapshots Type: Input/Output.
     * @param capabilities capabilities required from snapshots. Type: Input.
     * @return task
     */
    @Override
    public DriverTask createVolumeSnapshot(List<VolumeSnapshot> snapshots, StorageCapabilities capabilities) {
        log.info("Request to create Snapshots -- Start :");
        DriverTask task = new DriverTaskImpl(ScaleIOHelper.getTaskId(ScaleIOConstants.TaskType.SNAPSHOT_CREATE));
        countSucc = 0;
        // Assume snapshots could be from different storage system
        if (snapshots != null && snapshots.size() > 0) {
            // Assume snapshots could be from different storage system
            for (VolumeSnapshot snapshot : snapshots) {
                log.info("Start to get Rest client for volume {} of ScaleIO storage system: {}", snapshot.getParentId(),
                        snapshot.getStorageSystemId());
                client = this.getClientBySystemId(snapshot.getStorageSystemId());
                // create snapshot
                if (client != null) {
                    ScaleIOSnapshotVolumeResponse result = null;
                    try {
                        log.info("Client got! Create snapshot for volume {}:{} - start", snapshot.getDisplayName(), snapshot.getParentId());
                        result = client.snapshotVolume(snapshot.getParentId(), snapshot.getDisplayName(),
                                snapshot.getStorageSystemId());
                        // set value to the output
                        if (result != null) {
                            snapshot.setNativeId(result.getVolumeIdList().get(0));
                            snapshot.setTimestamp(ScaleIOHelper.getCurrentTime());
                            snapshot.setAccessStatus(StorageObject.AccessStatus.READ_WRITE);
                            Map<String, String> snapNameIdMap = client.getVolumes(result.getVolumeIdList());
                            snapshot.setDeviceLabel(snapNameIdMap.get(snapshot.getNativeId()));
                            countSucc++;
                            log.info("Successfully create snapshot for volume {}:{} - end", snapshot.getDisplayName(),
                                    snapshot.getParentId());
                        } else {
                            log.info("No snapshot returned for volume {}:{} ", snapshot.getDisplayName(), snapshot.getParentId());
                        }
                    } catch (Exception e) {
                        log.error("Exception while creating snapshot for volume {}", snapshot.getParentId(), e);
                    }
                } else {
                    log.error("Exception while getting client instance for volume {}:{}", snapshot.getDisplayName(), snapshot.getParentId());
                }
            }
            this.setTaskStatus(snapshots.size(), countSucc, task);
        } else {
            log.error("Empty snapshot input List");
            task.setStatus(DriverTask.TaskStatus.FAILED);
        }
        task.setEndTime(Calendar.getInstance());
        log.info("Request to create Snapshots -- End ");
        return task;
    }

    /**
     * Restore volume to snapshot state.
     *
     * @param volume Type: Input/Output.
     * @param snapshot Type: Input.
     * @return task
     */
    @Override
    public DriverTask restoreSnapshot(StorageVolume volume, VolumeSnapshot snapshot) {
        return setUpNonSupportedTask(ScaleIOConstants.TaskType.SNAPSHOT_RESTORE);
    }

    /**
     * Delete snapshots.
     *
     * @param snapshots Type: Input.
     * @return task
     */
    @Override
    public DriverTask deleteVolumeSnapshot(List<VolumeSnapshot> snapshots) {
        log.info("Request to delete Snapshots -- Start :");
        DriverTask task = new DriverTaskImpl(ScaleIOHelper.getTaskId(ScaleIOConstants.TaskType.SNAPSHOT_DELETE));
        countSucc = 0;
        // Assume snapshots could be from different storage system
        if (snapshots != null && snapshots.size() > 0) {
            for (VolumeSnapshot snapshot : snapshots) {
                log.info("Get Rest client for snapshot {}:{} - start", snapshot.getDisplayName(), snapshot.getNativeId());
                client = this.getClientBySystemId(snapshot.getStorageSystemId());
                // delete snapshot
                if (client != null) {
                    try {
                        log.info("Rest client Got! delete snapshot {}:{} - start", snapshot.getDisplayName(), snapshot.getNativeId());
                        client.removeVolume(snapshot.getNativeId());
                        countSucc++;
                        log.info("Successfully delete snapshot {}:{} - end", snapshot.getDisplayName(), snapshot.getNativeId());
                    } catch (Exception e) {
                        log.error("Exception while deleting snapshot {}", snapshot.getNativeId(), e);
                    }
                } else {
                    log.error("Exception while getting client instance for snapshot {}:{}", snapshot.getDisplayName(),
                            snapshot.getNativeId());
                }
            }
            this.setTaskStatus(snapshots.size(), countSucc, task);
        } else {
            log.error("Can't delete empty snapshot list");
            task.setStatus(DriverTask.TaskStatus.FAILED);
        }
        task.setEndTime(Calendar.getInstance());
        log.info("Request to delete Snapshots -- End ");
        return task;
    }

    /**
     * Clone volume clones.
     *
     * @param clones Type: Input/Output.
     * @param capabilities capabilities of clones. Type: Input.
     * @return task
     */
    @Override
    public DriverTask createVolumeClone(List<VolumeClone> clones, StorageCapabilities capabilities) {
        return null;
    }

    /**
     * Detach volume clones.
     *
     * @param clones Type: Input/Output.
     * @return task
     */
    @Override
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
    @Override
    public DriverTask restoreFromClone(StorageVolume volume, VolumeClone clone) {
        return setUpNonSupportedTask(ScaleIOConstants.TaskType.CLONE_RESTORE);
    }

    /**
     * Delete volume clones.
     *
     * @param clones clones to delete. Type: Input.
     * @return task
     */
    @Override
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
    @Override
    public DriverTask createVolumeMirror(List<VolumeMirror> mirrors, StorageCapabilities capabilities) {
        return setUpNonSupportedTask(ScaleIOConstants.TaskType.MIRROR_OPERATIONS);
    }

    /**
     * Delete mirrors.
     *
     * @param mirrors mirrors to delete. Type: Input.
     * @return task
     */
    @Override
    public DriverTask deleteVolumeMirror(List<VolumeMirror> mirrors) {
        return setUpNonSupportedTask(ScaleIOConstants.TaskType.MIRROR_OPERATIONS);
    }

    /**
     * Split mirrors
     *
     * @param mirrors Type: Input/Output.
     * @return task
     */
    @Override
    public DriverTask splitVolumeMirror(List<VolumeMirror> mirrors) {
        return setUpNonSupportedTask(ScaleIOConstants.TaskType.MIRROR_OPERATIONS);
    }

    /**
     * Resume mirrors after split
     *
     * @param mirrors Type: Input/Output.
     * @return task
     */
    @Override
    public DriverTask resumeVolumeMirror(List<VolumeMirror> mirrors) {
        return setUpNonSupportedTask(ScaleIOConstants.TaskType.MIRROR_OPERATIONS);
    }

    /**
     * Restore volume from a mirror
     *
     * @param volume Type: Input/Output.
     * @param mirror Type: Input.
     * @return task
     */
    @Override
    public DriverTask restoreVolumeMirror(StorageVolume volume, VolumeMirror mirror) {
        return setUpNonSupportedTask(ScaleIOConstants.TaskType.MIRROR_OPERATIONS);
    }

    /**
     * Get export masks for a given set of initiators.
     *
     * @param storageSystem Storage system to get ITLs from. Type: Input.
     * @param initiators Type: Input.
     * @return list of export masks
     */
    @Override
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
    @Override
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
    @Override
    public DriverTask unexportVolumesFromInitiators(List<Initiator> initiators, List<StorageVolume> volumes) {
        return null;
    }

    /**
     * Create block consistency group.
     *
     * @param consistencyGroup input/output
     * @return task
     */
    @Override
    public DriverTask createConsistencyGroup(VolumeConsistencyGroup consistencyGroup) {
        return setUpNonSupportedTask(ScaleIOConstants.TaskType.CG_CREATE);
    }

    /**
     * Delete block consistency group.
     *
     * @param consistencyGroup Input
     * @return task
     */
    @Override
    public DriverTask deleteConsistencyGroup(VolumeConsistencyGroup consistencyGroup) {
        return setUpNonSupportedTask(ScaleIOConstants.TaskType.CG_DELETE);
    }

    /**
     * Create snapshot of consistency group.
     *
     * @param consistencyGroup input parameter
     * @param snapshots input/output parameter
     * @param capabilities Capabilities of snapshots. Type: Input.
     * @return task
     */
    @Override
    public DriverTask createConsistencyGroupSnapshot(VolumeConsistencyGroup consistencyGroup, List<VolumeSnapshot> snapshots,
            List<CapabilityInstance> capabilities) {
        log.info("Request to create consistency group snapshot -- Start :");
        DriverTask task = new DriverTaskImpl(ScaleIOHelper.getTaskId(ScaleIOConstants.TaskType.CG_SNAP_CREATE));
        countSucc = 0;
        if (ScaleIOHelper.isFromSameStorageSystem(snapshots)) {
            String systemId = snapshots.get(0).getStorageSystemId();
            log.info("Start to get Rest client for ScaleIO storage system: {}", systemId);
            ScaleIORestClient client = this.getClientBySystemId(systemId);
            if (client != null) {
                try {
                    log.info("Rest Client Got! Create consistency group snapshot - Start:");
                    Map<String, String> parent2snap = new HashMap<>();
                    for (VolumeSnapshot snapshot : snapshots) {
                        parent2snap.put(snapshot.getParentId(), snapshot.getDisplayName());
                    }
                    ScaleIOSnapshotVolumeResponse result = client.snapshotMultiVolume(parent2snap, systemId);

                    // set value to the output
                    if (consistencyGroup == null) {
                        consistencyGroup = new VolumeConsistencyGroup();
                    }
                    consistencyGroup.setNativeId(result.getSnapshotGroupId());
                    consistencyGroup.setStorageSystemId(systemId);

                    // get parentID
                    List<String> nativeIds = result.getVolumeIdList();
                    Map<String, ScaleIOVolume> snapIdInfoMap = client.getVolumeNameMap(nativeIds);
                    String currentTime = ScaleIOHelper.getCurrentTime();
                    for (VolumeSnapshot snapshot : snapshots) {
                        for (ScaleIOVolume snapInfo : snapIdInfoMap.values()) {
                            if (snapshot.getParentId().equalsIgnoreCase(snapInfo.getAncestorVolumeId())) {
                                snapshot.setNativeId(snapInfo.getId());
                                snapshot.setTimestamp(currentTime);
                                snapshot.setAccessStatus(StorageObject.AccessStatus.READ_WRITE);
                                snapshot.setDeviceLabel(snapInfo.getName());
                                snapshot.setConsistencyGroup(result.getSnapshotGroupId());
                                countSucc++;
                            }
                        }
                    }
                    this.setTaskStatus(snapshots.size(), countSucc, task);
                    log.info("Create consistency group snapshot with group ID:{} - End:", consistencyGroup.getNativeId());
                } catch (Exception e) {
                    log.error("Exception while Creating consistency group snapshots in storage system: {}", systemId, e);
                    task.setStatus(DriverTask.TaskStatus.FAILED);
                }
            } else {
                log.error("Exception while getting Rest client instance for storage system {} ", systemId);
                task.setStatus(DriverTask.TaskStatus.FAILED);
            }
        } else {
            log.error("Snapshots are not from same storage system");
            task.setStatus(DriverTask.TaskStatus.FAILED);
        }
        task.setEndTime(Calendar.getInstance());
        log.info("Request to create consistency group snapshot -- End");
        return task;
    }

    /**
     * Delete snapshot.
     *
     * @param snapshots Input.
     * @return task
     */
    @Override
    public DriverTask deleteConsistencyGroupSnapshot(List<VolumeSnapshot> snapshots) {
        log.info("Request to delete consistency group snapshots -- Start :");
        DriverTask task = new DriverTaskImpl(ScaleIOHelper.getTaskId(ScaleIOConstants.TaskType.CG_SNAP_DELETE));
        if (ScaleIOHelper.isFromSameCGgroup(snapshots)) {
            String systemId = snapshots.get(0).getStorageSystemId();
            log.info("Start to get Rest client for ScaleIO storage system: {}", systemId);
            ScaleIORestClient client = this.getClientBySystemId(systemId);
            if (client != null) {
                try {
                    log.info("Rest Client Got! delete consistency group snapshot - Start:");
                    client.removeConsistencyGroupSnapshot(snapshots.get(0).getConsistencyGroup());
                    task.setStatus(DriverTask.TaskStatus.READY);
                    log.info("Successfully delete consistency group snapshot - End:");
                } catch (Exception e) {
                    log.error("Exception while deleting consistency group snapshot", e);
                    task.setStatus(DriverTask.TaskStatus.FAILED);
                }
            } else {
                log.error("Exception while getting client instance for storage system {}", systemId);
                task.setStatus(DriverTask.TaskStatus.FAILED);
            }
        } else {
            log.error("Snapshots are not from same consistency group");
            task.setStatus(DriverTask.TaskStatus.FAILED);
        }
        task.setEndTime(Calendar.getInstance());
        log.info("Request to delete consistency group snapshot -- End");
        return task;
    }

    /**
     * Create clone of consistency group.
     *
     * @param consistencyGroup input/output
     * @param clones output
     * @param capabilities Capabilities of clones. Type: Input.
     * @return task
     */
    @Override
    public DriverTask createConsistencyGroupClone(VolumeConsistencyGroup consistencyGroup, List<VolumeClone> clones,
            List<CapabilityInstance> capabilities) {
        return null;
    }

    /**
     * Delete consistency group clone
     *
     * @param clones output
     * @return task
     */
    @Override
    public DriverTask deleteConsistencyGroupClone(List<VolumeClone> clones) {
        return null;
    }

    /**
     * Get driver registration data.
     */
    @Override
    public RegistrationData getRegistrationData() {
        return null;
    }

    /**
     * Discover storage systems and their capabilities
     *
     * @param storageSystems StorageSystems to discover. Type: Input/Output.
     * @return task
     */
    @Override
    public DriverTask discoverStorageSystem(List<StorageSystem> storageSystems) {
        String taskType = "discover-storage-system";
        String taskID = String.format("%s+%s+%s", ScaleIOConstants.DRIVER_NAME, taskType, UUID.randomUUID());
        DriverTask task = new DriverTaskImpl(taskID);

        for (StorageSystem storageSystem : storageSystems) {
            try {
                setConnInfoToRegistry(storageSystem.getNativeId(), storageSystem.getIpAddress(), storageSystem.getPortNumber(),
                        storageSystem.getUsername(), storageSystem.getPassword());
                ScaleIORestClient scaleIOHandle = scaleIORestHandleFactory.getClientHandle(storageSystem.getNativeId(),
                        storageSystem.getIpAddress(),
                        storageSystem.getPortNumber(), storageSystem.getUsername(), storageSystem.getPassword());
                if (scaleIOHandle != null) {
                    ScaleIOSystem scaleIOSystem = scaleIOHandle.getSystem();
                    storageSystem.setSerialNumber(storageSystem.getSerialNumber());
                    storageSystem.setNativeId(storageSystem.getNativeId());
                    storageSystem.setSystemName(storageSystem.getSystemName());
                    storageSystem.setProtocols(storageSystem.getProtocols());
                    String version = scaleIOSystem.getVersion().replaceAll("_", ".");
                    storageSystem.setFirmwareVersion(version);
                    if (Double.parseDouble(ScaleIOConstants.MINIMUM_SUPPORTED_VERSION) <= Double.parseDouble(version)) {
                        storageSystem.setIsSupportedVersion(ScaleIOConstants.INCOMPATIBLE);
                    } else {
                        storageSystem.setIsSupportedVersion(ScaleIOConstants.COMPATIBLE);
                    }
                    storageSystem.setProtocols(storageSystem.getProtocols());
                    storageSystem.setModel(storageSystem.getModel());

                    task.setStatus(DriverTask.TaskStatus.READY);
                    log.info("StorageDriver: discoverStorageSystem information for storage system {}, name {} - End",
                            storageSystem.getIpAddress(), storageSystem.getSystemName());
                } else {
                    task.setStatus(DriverTask.TaskStatus.FAILED);
                }
            } catch (Exception e) {
                log.error("Exception was encountered when attempting to discover storage system {}, name {}", storageSystem.getIpAddress(),
                        storageSystem.getSystemName());
                task.setStatus(DriverTask.TaskStatus.ABORTED);
            }
        }
        return task;

    }

    /**
     * Discover storage pools and their capabilities.
     *
     * @param storageSystem Type: Input.
     * @param storagePools Type: Output.
     * @return task
     */
    @Override
    public DriverTask discoverStoragePools(StorageSystem storageSystem, List<StoragePool> storagePools) {

        String taskType = "discover-storage-pools";
        String taskID = String.format("%s+%s+%s", ScaleIOConstants.DRIVER_NAME, taskType, UUID.randomUUID());
        DriverTask task = new DriverTaskImpl(taskID);
        try {

            log.info("Discovery of storage pools for storage system {} .", storageSystem.getNativeId());
            ScaleIORestClient scaleIOHandle = scaleIORestHandleFactory.getClientHandle(storageSystem.getNativeId(),
                    storageSystem.getIpAddress(),
                    storageSystem.getPortNumber(), storageSystem.getUsername(), storageSystem.getPassword());
            if (scaleIOHandle != null) {
                List<ScaleIOProtectionDomain> protectionDomains = scaleIOHandle.getProtectionDomains();
                for (ScaleIOProtectionDomain protectionDomain : protectionDomains) {
                    List<ScaleIOStoragePool> scaleIOStoragePoolList = scaleIOHandle.getProtectionDomainStoragePools(protectionDomain
                            .getId());
                    StoragePool pool;
                    for (ScaleIOStoragePool storagePool : scaleIOStoragePoolList) {
                        pool = new StoragePool();
                        pool.setStorageSystemId(storageSystem.getNativeId());
                        log.info("Discovered Pool {}, storageSystem {}", pool.getNativeId(), pool.getStorageSystemId());
                        pool.setDeviceLabel(storageSystem.getDeviceLabel());
                        pool.setPoolName(storagePool.getName());
                        Set<StoragePool.Protocols> protocols = new HashSet<>();
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
                        Set<StoragePool.SupportedDriveTypes> supportedDriveTypes = new HashSet<>();
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
            log.error("Exception was encountered when attempting to discover storage pool for storage system {}",
                    storageSystem.getNativeId());
            task.setStatus(DriverTask.TaskStatus.ABORTED);
        }
        return task;

    }

    /**
     * Discover storage ports and their capabilities
     *
     * @param storageSystem Type: Input.
     * @param storagePorts Type: Output.
     * @return task
     */
    @Override
    public DriverTask discoverStoragePorts(StorageSystem storageSystem, List<StoragePort> storagePorts) {

        String taskType = "discover-storage-ports";
        String taskID = String.format("%s+%s+%s", ScaleIOConstants.DRIVER_NAME, taskType, UUID.randomUUID());
        DriverTask task = new DriverTaskImpl(taskID);
        try {

            log.info("Discovery of storage ports for storage system {} .", storageSystem.getNativeId());
            ScaleIORestClient scaleIOHandle = scaleIORestHandleFactory.getClientHandle(storageSystem.getNativeId(),
                    storageSystem.getIpAddress(),
                    storageSystem.getPortNumber(), storageSystem.getUsername(), storageSystem.getPassword());
            if (scaleIOHandle != null) {
                List<ScaleIOProtectionDomain> protectionDomains = scaleIOHandle.getProtectionDomains();
                List<ScaleIOSDS> allSDSs = scaleIOHandle.queryAllSDS();
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
                                // String nativeId = URIUtil
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
            } else {
                task.setStatus(DriverTask.TaskStatus.FAILED);
            }
        } catch (Exception e) {
            log.error("Exception was encountered when attempting to discover storage ports for storage system {}",
                    storageSystem.getNativeId());
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
     * @return task
     */
    @Override
    public DriverTask getStorageVolumes(StorageSystem storageSystem, List<StorageVolume> storageVolumes, MutableInt token) {
        return null;
    }

    /**
     * Get list of supported storage system types. Ex. vmax, vnxblock, hitachi, etc...
     *
     * @return list of supported storage system types
     */
    @Override
    public List<String> getSystemTypes() {
        return null;
    }

    /**
     * Return driver task with a given id.
     *
     * @param taskId
     * @return
     */
    @Override
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
    @Override
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
        List<String> listPort = new ArrayList<>();
        List<String> listUserName = new ArrayList<>();
        List<String> listPwd = new ArrayList<>();

        listIP.add(ipAddress);
        attributes.put(ScaleIOConstants.IP_ADDRESS, listIP);
        listPort.add(Integer.toString(port));
        attributes.put(ScaleIOConstants.PORT_NUMBER, listPort);
        listUserName.add(username);
        attributes.put(ScaleIOConstants.USER_NAME, listUserName);
        listPwd.add(password);
        attributes.put(ScaleIOConstants.PASSWORD, listPwd);

        this.driverRegistry.setDriverAttributesForKey(ScaleIOConstants.DRIVER_NAME, systemNativeId, attributes);
    }

    /**
     * get Rest Client
     * 
     * @param systemId storage system id
     * @return rest client handler
     */
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
     * set task status by checking the number of successful sub-tasks.
     * 
     * @param sizeOfTasks
     * @param sizeOfSuccTask
     * @param task
     */
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
    private DriverTask setUpNonSupportedTask(ScaleIOConstants.TaskType taskType) {
        DriverTask task = new DriverTaskImpl(ScaleIOHelper.getTaskId(taskType));
        task.setStatus(DriverTask.TaskStatus.ABORTED);
        task.setMessage("Operation not supported");
        return task;
    }
}
