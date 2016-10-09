/*
 * Copyright 2016 Dell Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.emc.storageos.driver.dellsc.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.dellsc.DellSCDriverException;
import com.emc.storageos.driver.dellsc.DellSCDriverTask;
import com.emc.storageos.driver.dellsc.DellSCUtil;
import com.emc.storageos.driver.dellsc.scapi.StorageCenterAPI;
import com.emc.storageos.driver.dellsc.scapi.StorageCenterAPIException;
import com.emc.storageos.driver.dellsc.scapi.objects.EmDataCollector;
import com.emc.storageos.driver.dellsc.scapi.objects.ScConfiguration;
import com.emc.storageos.driver.dellsc.scapi.objects.ScControllerPort;
import com.emc.storageos.driver.dellsc.scapi.objects.ScCopyMirrorMigrate;
import com.emc.storageos.driver.dellsc.scapi.objects.ScFaultDomain;
import com.emc.storageos.driver.dellsc.scapi.objects.ScReplay;
import com.emc.storageos.driver.dellsc.scapi.objects.ScReplayProfile;
import com.emc.storageos.driver.dellsc.scapi.objects.ScStorageType;
import com.emc.storageos.driver.dellsc.scapi.objects.ScVolume;
import com.emc.storageos.driver.dellsc.scapi.objects.StorageCenter;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.DriverTask.TaskStatus;
import com.emc.storageos.storagedriver.model.StorageObject.AccessStatus;
import com.emc.storageos.storagedriver.model.StoragePool;
import com.emc.storageos.storagedriver.model.StoragePort;
import com.emc.storageos.storagedriver.model.StorageProvider;
import com.emc.storageos.storagedriver.model.StorageSystem;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.model.VolumeClone;
import com.emc.storageos.storagedriver.model.VolumeMirror;
import com.emc.storageos.storagedriver.model.VolumeMirror.SynchronizationState;
import com.emc.storageos.storagedriver.model.VolumeSnapshot;

/**
 * Discovery related operations.
 */
public class DellSCDiscovery {

    private static final Logger LOG = LoggerFactory.getLogger(DellSCDiscovery.class);

    private static final String EMPTY_IPADDR = "0.0.0.0";

    private DellSCConnectionManager connectionManager;
    private String driverName;
    private String driverVersion;
    private DellSCUtil util;

    /**
     * Initialize the instance.
     * 
     * @param driverName The driver name.
     * @param driverVersion The driver version.
     */
    public DellSCDiscovery(String driverName, String driverVersion) {
        this.driverName = driverName;
        this.driverVersion = driverVersion;
        this.connectionManager = DellSCConnectionManager.getInstance();
        this.util = DellSCUtil.getInstance();
    }

    /**
     * Perform discovery for a storage provider.
     * 
     * @param storageProvider The provider.
     * @param storageSystems The storage systems collection to populate.
     * @return The driver task.
     */
    public DriverTask discoverStorageProvider(StorageProvider storageProvider, List<StorageSystem> storageSystems) {
        DellSCDriverTask task = new DellSCDriverTask("discover");

        try {
            LOG.info("Getting information for storage provider [{}:{}] as user {}",
                    storageProvider.getProviderHost(),
                    storageProvider.getPortNumber(),
                    storageProvider.getUsername());

            StorageCenterAPI api = connectionManager.getConnection(
                    storageProvider.getProviderHost(),
                    storageProvider.getPortNumber(),
                    storageProvider.getUsername(),
                    storageProvider.getPassword(), true);

            LOG.info("Connected to DSM {} as user {}",
                    storageProvider.getProviderHost(), storageProvider.getUsername());

            // Populate the provider information
            storageProvider.setAccessStatus(AccessStatus.READ_WRITE);
            storageProvider.setManufacturer("Dell");
            storageProvider.setProviderVersion(driverVersion);
            storageProvider.setIsSupportedVersion(true);

            // Get some info about the DSM for debugging purposes
            EmDataCollector em = api.getDSMInfo();
            if (em != null) {
                LOG.info("Connected to {} DSM version {}, Java version {}",
                        em.type, em.version, em.javaVersion);
                storageProvider.setProviderVersion(em.version);
            }

            // Populate the basic SC information
            StorageCenter[] scs = api.getStorageCenterInfo();
            for (StorageCenter sc : scs) {
                StorageSystem storageSystem = util.getStorageSystemFromStorageCenter(api, sc, null);
                storageSystem.setSystemType(driverName);
                storageSystems.add(storageSystem);
            }

            task.setStatus(DriverTask.TaskStatus.READY);
        } catch (Exception e) {
            String msg = String.format("Exception encountered getting storage provider information: %s", e);
            LOG.error(msg);
            task.setFailed(msg);
        }

        return task;
    }

    /**
     * Discover storage systems and their capabilities.
     *
     * @param storageSystem Storage system to discover.
     * @return The discovery task.
     */
    public DriverTask discoverStorageSystem(StorageSystem storageSystem) {

        DriverTask task = new DellSCDriverTask("discover");

        try {
            LOG.info("Getting information for storage system [{}] - {}",
                    storageSystem.getIpAddress(),
                    storageSystem.getSystemName());

            String sn = storageSystem.getSerialNumber();
            if (sn == null || sn.length() == 0) {
                // Directly added system, no SSN yet so we use the name field
                sn = storageSystem.getSystemName();

                // Through the storage provider CoprHD overrides the system
                // name with provider_name+serial_number
                if (sn.contains("+")) {
                    String[] parts = sn.split("\\+");
                    sn = parts[1];
                }
            }
            int port = storageSystem.getPortNumber();
            if (port == 0) {
                port = 3033;
            }

            StorageCenterAPI api = connectionManager.getConnection(
                    storageSystem.getIpAddress(),
                    port,
                    storageSystem.getUsername(),
                    storageSystem.getPassword(), false);

            // Populate the SC information
            StorageCenter sc = api.findStorageCenter(sn);
            util.getStorageSystemFromStorageCenter(api, sc, storageSystem);
            storageSystem.setSystemType(driverName);

            task.setStatus(DriverTask.TaskStatus.READY);
        } catch (Exception e) {
            String msg = String.format("Exception encountered getting storage system information: %s", e);
            LOG.error(msg);
            task.setMessage(msg);
            task.setStatus(DriverTask.TaskStatus.FAILED);
        }

        return task;
    }

    /**
     * Discover storage pools and their capabilities.
     *
     * @param storageSystem The storage system on which to discover.
     * @param storagePools The storage pools.
     * @return The discovery task.
     */
    public DriverTask discoverStoragePools(StorageSystem storageSystem, List<StoragePool> storagePools) {
        LOG.info("Discovering storage pools for [{}] {} {}",
                storageSystem.getSystemName(), storageSystem.getIpAddress(),
                storageSystem.getNativeId());
        DellSCDriverTask task = new DellSCDriverTask("discoverStoragePools");

        try {
            StorageCenterAPI api = connectionManager.getConnection(storageSystem.getNativeId());

            ScStorageType[] storageTypes = api.getStorageTypes(storageSystem.getNativeId());
            for (ScStorageType storageType : storageTypes) {
                storagePools.add(util.getStoragePoolFromStorageType(api, storageType, null));
            }
            task.setStatus(DriverTask.TaskStatus.READY);
        } catch (Exception e) {
            String failureMessage = String.format("Error getting pool information: %s", e);
            task.setFailed(failureMessage);
            LOG.warn(failureMessage);
        }

        return task;
    }

    /**
     * Discover storage ports and their capabilities.
     *
     * @param storageSystem The storage system on which to discover.
     * @param storagePorts The storage ports.
     * @return The discovery task.
     */
    public DriverTask discoverStoragePorts(StorageSystem storageSystem, List<StoragePort> storagePorts) {
        LOG.info("Discovering storage ports for [{}] {} {}",
                storageSystem.getSystemName(), storageSystem.getIpAddress(),
                storageSystem.getNativeId());
        DellSCDriverTask task = new DellSCDriverTask("discoverStoragePorts");

        try {
            String ssn = storageSystem.getNativeId();
            StorageCenterAPI api = connectionManager.getConnection(ssn);

            Map<String, List<ScControllerPort>> ports = getPortList(api, ssn);
            for (Entry<String, List<ScControllerPort>> entry : ports.entrySet()) {
                for (ScControllerPort scPort : entry.getValue()) {
                    StoragePort port = util.getStoragePortForControllerPort(api, scPort, entry.getKey());
                    LOG.info("Discovered Port {}, storageSystem {}",
                            scPort.instanceId, scPort.scSerialNumber);
                    storagePorts.add(port);
                }
            }

            task.setStatus(DriverTask.TaskStatus.READY);
        } catch (Exception e) {
            String failureMessage = String.format("Error getting port information: %s", e);
            task.setFailed(failureMessage);
            LOG.warn(failureMessage);
        }
        return task;

    }

    /**
     * Gets the ScControllerPorts for a system.
     *
     * @param api The API connection.
     * @param ssn The system serial number.
     * @return The ports.
     */
    private Map<String, List<ScControllerPort>> getPortList(StorageCenterAPI api, String ssn) {
        Map<String, List<ScControllerPort>> ports = new HashMap<>();

        try {
            ScConfiguration scConfig = api.getScConfig(ssn);
            ScFaultDomain[] faultDomains = api.getFaultDomains(ssn);

            for (ScFaultDomain fd : faultDomains) {
                // See what kind of fault domain this is
                boolean virtualMode;
                if (ScControllerPort.FC_TRANSPORT_TYPE.equals(fd.transportType)) {
                    virtualMode = "VirtualPort".equals(scConfig.fibreChannelTransportMode);
                } else if (ScControllerPort.ISCSI_TRANSPORT_TYPE.equals(fd.transportType)) {
                    virtualMode = "VirtualPort".equals(scConfig.iscsiTransportMode);
                } else {
                    // Not a currently supported transport type
                    continue;
                }

                // Now get the actual ports
                if (!ports.containsKey(fd.name)) {
                    ports.put(fd.name, new ArrayList<>());
                }

                ScControllerPort[] fdPorts = api.getFaultDomainPorts(fd.instanceId, virtualMode);
                for (ScControllerPort fdPort : fdPorts) {
                    // See if we need to inherit the IP settings from the fault domain
                    if (EMPTY_IPADDR.equals(fdPort.iscsiSubnetMask)) {
                        fdPort.iscsiSubnetMask = fd.subnetMask;
                    }
                    if (EMPTY_IPADDR.equals(fdPort.iscsiIpAddress)) {
                        fdPort.iscsiIpAddress = fd.targetIpv4Address;
                    }
                    ports.get(fd.name).add(fdPort);
                }
            }
        } catch (Exception e) {
            LOG.error(String.format("Error getting system controller ports: %s", e));
        }

        return ports;
    }

    /**
     * Discover storage volumes.
     *
     * @param storageSystem The storage system on which to discover.
     * @param storageVolumes The discovered storage volumes.
     * @param token Used for paging. Input 0 indicates that the first page should be returned. Output 0 indicates
     *            that last page was returned
     * @return The discovery task.
     */
    public DriverTask getStorageVolumes(StorageSystem storageSystem,
            List<StorageVolume> storageVolumes,
            MutableInt token) {
        LOG.info("Getting volumes from {}", storageSystem.getNativeId());
        DellSCDriverTask task = new DellSCDriverTask("getVolumes");

        try {
            StorageCenterAPI api = connectionManager.getConnection(storageSystem.getNativeId());

            Map<ScReplayProfile, List<String>> cgInfo = util.getGCInfo(api, storageSystem.getNativeId());
            ScVolume[] volumes = api.getAllVolumes(storageSystem.getNativeId());
            for (ScVolume volume : volumes) {
                if (volume.inRecycleBin || volume.liveVolume || volume.cmmDestination || volume.replicationDestination) {
                    continue;
                }
                StorageVolume driverVol = util.getStorageVolumeFromScVolume(api, volume, cgInfo);
                storageVolumes.add(driverVol);
            }

            task.setStatus(TaskStatus.READY);
        } catch (DellSCDriverException | StorageCenterAPIException e) {
            String msg = String.format("Error getting volume info: %s", e);
            LOG.warn(msg);
            task.setFailed(msg);
        }

        return task;
    }

    /**
     * Gets all snapshots for a volume.
     * 
     * @param storageVolume The volume.
     * @return The snapshots.
     */
    public List<VolumeSnapshot> getVolumeSnapshots(StorageVolume storageVolume) {
        LOG.info("Getting snapshots for {}", storageVolume.getNativeId());
        List<VolumeSnapshot> result = new ArrayList<>();

        try {
            StorageCenterAPI api = connectionManager.getConnection(storageVolume.getStorageSystemId());

            ScReplay[] replays = api.getVolumeSnapshots(storageVolume.getNativeId());
            for (ScReplay replay : replays) {
                VolumeSnapshot snap = util.getVolumeSnapshotFromReplay(replay, null);
                result.add(snap);
            }

        } catch (DellSCDriverException e) {
            String msg = String.format("Error getting volume info: %s", e);
            LOG.warn(msg);
        }

        return result;
    }

    /**
     * Gets all clones of a volume.
     * 
     * @param storageVolume The volume.
     * @return The clones.
     */
    public List<VolumeClone> getVolumeClones(StorageVolume storageVolume) {
        LOG.info("Getting clones for volume {}", storageVolume.getNativeId());
        // Clones are independent once created
        return new ArrayList<>(0);
    }

    /**
     * Gets all mirrors of a volume.
     *
     * @param storageVolume The volume.
     * @return The mirrors.
     */
    public List<VolumeMirror> getVolumeMirrors(StorageVolume storageVolume) {
        LOG.info("Getting mirrors for volume {}", storageVolume.getNativeId());
        List<VolumeMirror> result = new ArrayList<>();

        try {
            StorageCenterAPI api = connectionManager.getConnection(storageVolume.getStorageSystemId());

            ScVolume scVolume = api.getVolume(storageVolume.getNativeId());
            if (scVolume != null && scVolume.cmmSource) {
                ScCopyMirrorMigrate[] cmms = api.getVolumeCopyMirrorMigrate(scVolume.instanceId);
                for (ScCopyMirrorMigrate cmm : cmms) {
                    if ("Mirror".equals(cmm.type)) {
                        ScVolume targetVol = api.getVolume(cmm.destinationVolume.instanceId);
                        VolumeMirror mirror = new VolumeMirror();
                        mirror.setAccessStatus(AccessStatus.READ_WRITE);
                        mirror.setDeviceLabel(targetVol.name);
                        mirror.setDisplayName(targetVol.name);
                        mirror.setNativeId(targetVol.instanceId);
                        mirror.setParentId(cmm.sourceVolume.instanceId);
                        mirror.setStorageSystemId(storageVolume.getStorageSystemId());
                        SynchronizationState syncState = SynchronizationState.SYNCHRONIZED;
                        if (cmm.percentComplete != 100) {
                            syncState = SynchronizationState.COPYINPROGRESS;
                        }
                        mirror.setSyncState(syncState);
                        mirror.setWwn(targetVol.deviceId);

                        result.add(mirror);
                    }
                }
            }
        } catch (DellSCDriverException e) {
            String msg = String.format("Error getting mirrors for volume %s", storageVolume.getNativeId(), e);
            LOG.warn(msg);
        }
        return result;
    }
}
