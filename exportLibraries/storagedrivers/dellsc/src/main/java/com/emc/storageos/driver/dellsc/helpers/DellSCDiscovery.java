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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.dellsc.DellSCDriverException;
import com.emc.storageos.driver.dellsc.DellSCDriverTask;
import com.emc.storageos.driver.dellsc.DellSCUtil;
import com.emc.storageos.driver.dellsc.scapi.SizeUtil;
import com.emc.storageos.driver.dellsc.scapi.StorageCenterAPI;
import com.emc.storageos.driver.dellsc.scapi.StorageCenterAPIException;
import com.emc.storageos.driver.dellsc.scapi.objects.ScControllerPort;
import com.emc.storageos.driver.dellsc.scapi.objects.ScControllerPortFibreChannelConfiguration;
import com.emc.storageos.driver.dellsc.scapi.objects.ScControllerPortIscsiConfiguration;
import com.emc.storageos.driver.dellsc.scapi.objects.ScCopyMirrorMigrate;
import com.emc.storageos.driver.dellsc.scapi.objects.ScFaultDomain;
import com.emc.storageos.driver.dellsc.scapi.objects.ScReplay;
import com.emc.storageos.driver.dellsc.scapi.objects.ScReplayProfile;
import com.emc.storageos.driver.dellsc.scapi.objects.ScStorageType;
import com.emc.storageos.driver.dellsc.scapi.objects.ScStorageTypeStorageUsage;
import com.emc.storageos.driver.dellsc.scapi.objects.ScVolume;
import com.emc.storageos.driver.dellsc.scapi.objects.StorageCenter;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.DriverTask.TaskStatus;
import com.emc.storageos.storagedriver.model.Initiator.Protocol;
import com.emc.storageos.storagedriver.model.StorageObject.AccessStatus;
import com.emc.storageos.storagedriver.model.StoragePool;
import com.emc.storageos.storagedriver.model.StoragePool.Protocols;
import com.emc.storageos.storagedriver.model.StoragePort;
import com.emc.storageos.storagedriver.model.StorageProvider;
import com.emc.storageos.storagedriver.model.StorageSystem;
import com.emc.storageos.storagedriver.model.StorageSystem.SupportedProvisioningType;
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

    private DellSCPersistence persistence;
    private String driverName;
    private String driverVersion;
    private DellSCUtil util;

    /**
     * Initialize the instance.
     * 
     * @param driverName The driver name.
     * @param driverVersion The driver version.
     * @param persistence The persistence interface.
     */
    public DellSCDiscovery(String driverName, String driverVersion, DellSCPersistence persistence) {
        this.driverName = driverName;
        this.driverVersion = driverVersion;
        this.persistence = persistence;
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

            try (StorageCenterAPI api = StorageCenterAPI.openConnection(
                    storageSystem.getIpAddress(),
                    port,
                    storageSystem.getUsername(),
                    storageSystem.getPassword())) {
                // Populate the SC information
                StorageCenter sc = api.findStorageCenter(sn);
                storageSystem.setSerialNumber(sc.scSerialNumber);
                storageSystem.setAccessStatus(AccessStatus.READ_WRITE);
                storageSystem.setModel(sc.modelSeries);
                storageSystem.setProvisioningType(SupportedProvisioningType.THIN);
                storageSystem.setNativeId(sc.scSerialNumber);
                storageSystem.setSystemType(driverName);

                // Parse out version information
                String[] version = sc.version.split("\\.");
                storageSystem.setMajorVersion(version[0]);
                storageSystem.setMinorVersion(version[1]);
                storageSystem.setFirmwareVersion(sc.version);
                storageSystem.setIsSupportedVersion(true);

                storageSystem.setDeviceLabel(sc.scName);
                storageSystem.setDisplayName(sc.scName);

                // Check the supported protocols for this array
                List<String> protocols = getSupportedProtocols(api, sc.scSerialNumber);
                storageSystem.setProtocols(protocols);

                persistence.saveConnectionInfo(
                        storageSystem.getNativeId(),
                        storageSystem.getIpAddress(),
                        port,
                        storageSystem.getUsername(),
                        storageSystem.getPassword());
            }

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

        try (StorageCenterAPI api = persistence.getSavedConnection(storageSystem.getNativeId())) {

            ScStorageType[] storageTypes = api.getStorageTypes(storageSystem.getNativeId());
            for (ScStorageType storageType : storageTypes) {
                StoragePool pool = new StoragePool();
                pool.setNativeId(storageType.instanceId);
                pool.setStorageSystemId(storageSystem.getNativeId());
                LOG.info("Discovered Pool {}, storageSystem {}",
                        pool.getNativeId(), pool.getStorageSystemId());

                pool.setDeviceLabel(storageType.name);
                pool.setDisplayName(storageType.name);
                pool.setPoolName(storageType.name);
                pool.setCapabilities(new ArrayList<>(0));

                // Get the supported transport protocols
                Set<StoragePool.Protocols> protocols = new HashSet<>();
                List<String> transportProtocols = getSupportedProtocols(api, storageSystem.getNativeId());
                if (transportProtocols.contains(Protocol.FC.toString())) {
                    protocols.add(StoragePool.Protocols.FC);
                }
                if (transportProtocols.contains(Protocol.iSCSI.toString())) {
                    protocols.add(StoragePool.Protocols.iSCSI);
                }
                pool.setProtocols(protocols);

                pool.setPoolServiceType(StoragePool.PoolServiceType.block);
                pool.setMaximumThickVolumeSize(0L);
                pool.setMinimumThickVolumeSize(0L);
                pool.setMaximumThinVolumeSize(549755813888L); // Max 512 TB
                pool.setMinimumThinVolumeSize(1048576L); // Min 1 GB
                pool.setSupportedResourceType(StoragePool.SupportedResourceType.THIN_ONLY);

                ScStorageTypeStorageUsage su = api.getStorageTypeStorageUsage(storageType.instanceId);
                LOG.info("Space info: {} {} {}", su.allocatedSpace, su.freeSpace, su.usedSpace);
                pool.setSubscribedCapacity(SizeUtil.sizeStrToBytes(su.usedSpace));
                pool.setFreeCapacity(SizeUtil.sizeStrToBytes(su.freeSpace));
                pool.setTotalCapacity(SizeUtil.sizeStrToBytes(su.allocatedSpace));
                pool.setOperationalStatus(StoragePool.PoolOperationalStatus.READY);

                storagePools.add(pool);

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

        try (StorageCenterAPI api = persistence.getSavedConnection(storageSystem.getNativeId())) {

            ScControllerPort[] scPorts = api.getTargetPorts(storageSystem.getNativeId(), null);
            for (ScControllerPort scPort : scPorts) {

                StoragePort port = new StoragePort();
                port.setNativeId(scPort.instanceId);
                port.setStorageSystemId(storageSystem.getNativeId());
                LOG.info("Discovered Port {}, storageSystem {}",
                        port.getNativeId(), port.getStorageSystemId());

                // Get the port configuration
                String haZone = "";
                ScFaultDomain[] faultDomains = api.getControllerPortFaultDomains(scPort.instanceId);
                if (faultDomains.length > 0) {
                    // API returns list, but currently only one fault domain per port allowed
                    haZone = faultDomains[0].name;
                }
                port.setPortHAZone(haZone);

                if (ScControllerPort.FC_TRANSPORT_TYPE.equals(scPort.transportType)) {
                    setFCPortInfo(api, scPort, port);
                } else if (ScControllerPort.ISCSI_TRANSPORT_TYPE.equals(scPort.transportType)) {
                    setISCSIPortInfo(api, scPort, port);
                } else {
                    // Unsupported transport type
                    LOG.warn("Skipping unsupported {} transport type port.", scPort.transportType);
                    continue;
                }

                StoragePort.OperationalStatus status = StoragePort.OperationalStatus.OK;
                if (!ScControllerPort.PORT_STATUS_UP.equals(scPort.status)) {
                    status = StoragePort.OperationalStatus.NOT_OK;
                }
                port.setOperationalStatus(status);
                port.setPortName(port.getDeviceLabel());
                port.setEndPointID(port.getPortNetworkId());
                port.setAccessStatus(AccessStatus.READ_WRITE);

                storagePorts.add(port);
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

            try (StorageCenterAPI api = StorageCenterAPI.openConnection(
                    storageProvider.getProviderHost(),
                    storageProvider.getPortNumber(),
                    storageProvider.getUsername(),
                    storageProvider.getPassword())) {

                // Populate the provider information
                storageProvider.setAccessStatus(AccessStatus.READ_WRITE);
                storageProvider.setManufacturer("Dell");
                storageProvider.setProviderVersion(driverVersion);
                storageProvider.setIsSupportedVersion(true);

                // Populate the basic SC information
                StorageCenter[] scs = api.getStorageCenterInfo();
                for (StorageCenter sc : scs) {
                    StorageSystem storageSystem = new StorageSystem();
                    storageSystem.setSerialNumber(sc.scSerialNumber);
                    storageSystem.setNativeId(sc.scSerialNumber);
                    storageSystem.setSystemType(driverName);

                    storageSystem.setIpAddress(storageProvider.getProviderHost());
                    storageSystem.setPortNumber(storageProvider.getPortNumber());

                    // Set display info
                    storageSystem.setDeviceLabel(sc.scName);
                    storageSystem.setDisplayName(sc.scName);
                    storageSystem.setSystemName(sc.scName);

                    // Parse out version information
                    String[] version = sc.version.split("\\.");
                    storageSystem.setMajorVersion(version[0]);
                    storageSystem.setMinorVersion(version[1]);
                    storageSystem.setFirmwareVersion(sc.version);
                    storageSystem.setIsSupportedVersion(true);

                    storageSystems.add(storageSystem);
                }
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
        LOG.info("Getting volumes");
        DellSCDriverTask task = new DellSCDriverTask("getVolumes");

        try (StorageCenterAPI api = persistence.getSavedConnection(storageSystem.getNativeId())) {
            Map<ScReplayProfile, List<String>> cgInfo = util.getGCInfo(api, storageSystem.getNativeId());
            ScVolume[] volumes = api.getAllVolumes(storageSystem.getNativeId());
            for (ScVolume volume : volumes) {
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

        try (StorageCenterAPI api = persistence.getSavedConnection(storageVolume.getStorageSystemId())) {
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

        try (StorageCenterAPI api = persistence.getSavedConnection(storageVolume.getStorageSystemId())) {
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

    /**
     * Sets FC specific info for a port.
     *
     * @param api The API connection.
     * @param scPort The Storage Center port.
     * @param port The storage port object to populate.
     */
    private void setFCPortInfo(StorageCenterAPI api, ScControllerPort scPort, StoragePort port) {
        port.setDeviceLabel(scPort.wwn);
        port.setTransportType(StoragePort.TransportType.FC);

        ScControllerPortFibreChannelConfiguration portConfig = api.getControllerPortFCConfig(
                scPort.instanceId);
        port.setPortNetworkId(scPort.wwn);
        port.setPortSpeed(SizeUtil.speedStrToGigabits(portConfig.speed));
        port.setPortGroup(String.format("%s", portConfig.homeControllerIndex));
        port.setPortSubGroup(String.format("%s", portConfig.slot));
        port.setTcpPortNumber(0L);
    }

    /**
     * Sets iSCSI specific info for a port.
     *
     * @param api The API connection.
     * @param scPort The Storage Center port.
     * @param port The storage port object to populate.
     */
    private void setISCSIPortInfo(StorageCenterAPI api, ScControllerPort scPort, StoragePort port) {
        port.setDeviceLabel(scPort.iscsiName);
        port.setTransportType(StoragePort.TransportType.IP);

        ScControllerPortIscsiConfiguration portConfig = api.getControllerPortIscsiConfig(
                scPort.instanceId);
        port.setNetworkId(portConfig.getNetwork());
        port.setIpAddress(portConfig.ipAddress);
        port.setPortNetworkId(portConfig.getFormattedMACAddress());
        port.setPortSpeed(SizeUtil.speedStrToGigabits(portConfig.speed));
        port.setPortGroup(String.format("%s", portConfig.homeControllerIndex));
        port.setPortSubGroup(String.format("%s", portConfig.slot));
        port.setTcpPortNumber(portConfig.portNumber);
    }

    /**
     * Gets the transport protocols supported by an array.
     *
     * @param api The SC API connection.
     * @param scSerialNumber The Storage Center serial number to check.
     * @return The supported protocols.
     */
    private List<String> getSupportedProtocols(StorageCenterAPI api, String scSerialNumber) {

        List<String> protocols = new ArrayList<>();
        boolean hasIScsi = false;
        boolean hasFC = false;
        ScControllerPort[] controllerPorts = api.getTargetPorts(scSerialNumber, null);
        for (ScControllerPort scPort : controllerPorts) {
            if (ScControllerPort.FC_TRANSPORT_TYPE.equals(scPort.transportType)) {
                hasFC = true;
            } else if (ScControllerPort.ISCSI_TRANSPORT_TYPE.equals(scPort.transportType)) {
                hasIScsi = true;
            }
        }

        if (hasIScsi) {
            protocols.add(Protocols.iSCSI.toString());
        }
        if (hasFC) {
            protocols.add(Protocols.FC.toString());
        }

        return protocols;
    }
}
