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

package com.emc.storageos.driver.dellsc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.mutable.MutableBoolean;
import org.apache.commons.lang.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.dellsc.scapi.StorageCenterAPI;
import com.emc.storageos.driver.dellsc.scapi.objects.ScControllerPort;
import com.emc.storageos.driver.dellsc.scapi.objects.ScControllerPortIscsiConfiguration;
import com.emc.storageos.driver.dellsc.scapi.objects.ScFaultDomain;
import com.emc.storageos.driver.dellsc.scapi.objects.ScServer;
import com.emc.storageos.driver.dellsc.scapi.objects.ScServerHba;
import com.emc.storageos.driver.dellsc.scapi.objects.ScStorageType;
import com.emc.storageos.driver.dellsc.scapi.objects.StorageCenter;
import com.emc.storageos.driver.dellsc.scapi.objects.StorageCenterStorageUsage;
import com.emc.storageos.storagedriver.BlockStorageDriver;
import com.emc.storageos.storagedriver.DefaultStorageDriver;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.DriverTask.TaskStatus;
import com.emc.storageos.storagedriver.HostExportInfo;
import com.emc.storageos.storagedriver.RegistrationData;
import com.emc.storageos.storagedriver.model.Initiator;
import com.emc.storageos.storagedriver.model.StorageHostComponent;
import com.emc.storageos.storagedriver.model.StorageObject;
import com.emc.storageos.storagedriver.model.StorageObject.AccessStatus;
import com.emc.storageos.storagedriver.model.StoragePool;
import com.emc.storageos.storagedriver.model.StoragePool.Protocols;
import com.emc.storageos.storagedriver.model.StoragePort;
import com.emc.storageos.storagedriver.model.StorageProvider;
import com.emc.storageos.storagedriver.model.StorageSystem;
import com.emc.storageos.storagedriver.model.StorageSystem.SupportedProvisioningType;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.model.VolumeClone;
import com.emc.storageos.storagedriver.model.VolumeConsistencyGroup;
import com.emc.storageos.storagedriver.model.VolumeMirror;
import com.emc.storageos.storagedriver.model.VolumeSnapshot;
import com.emc.storageos.storagedriver.storagecapabilities.CapabilityInstance;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;

/**
 * Storage Driver for Dell SC Series storage arrays.
 */
public class DellSCStorageDriver extends DefaultStorageDriver implements BlockStorageDriver {

    private static final Logger LOG = LoggerFactory.getLogger(DellSCStorageDriver.class);

    private static final String HOST_KEY = "HOST";
    private static final String PORT_KEY = "KEY";
    private static final String USER_KEY = "USER";
    private static final String PASS_KEY = "PASS";

    private static final String DRIVER_NAME = "dellscsystem";
    private static final String DRIVER_VERSION = "1.0.0";

    /**
     * Create storage volumes with a given set of capabilities.
     * Before completion of the request, set all required data for provisioned
     * volumes in "volumes" parameter.
     *
     * @param volumes Input/output argument for volumes.
     * @param storageCapabilities Input argument for capabilities. Defines
     *            storage capabilities of volumes to create.
     * @return The volume creation task.
     */
    @Override
    public DriverTask createVolumes(List<StorageVolume> list, StorageCapabilities storageCapabilities) {
        LOG.info("Creating %d new volumes", list.size());
        DriverTask task = new DellSCDriverTask("createVolume");
        task.setStatus(TaskStatus.FAILED);
        return task;
    }

    /**
     * Expand volume to a new size.
     * Before completion of the request, set all required data for expanded
     * volume in "volume" parameter.
     *
     * @param storageVolume Volume to expand. Type: Input/Output argument.
     * @param newCapacity Requested capacity. Type: input argument.
     * @return The volume expansion task.
     */
    @Override
    public DriverTask expandVolume(StorageVolume storageVolume, long newCapacity) {
        LOG.info("Expanding volume {} to {}", storageVolume.getNativeId(), newCapacity);
        DriverTask task = new DellSCDriverTask("expandVolume");
        task.setStatus(TaskStatus.FAILED);
        return task;
    }

    /**
     * Delete volume.
     *
     * @param volume The volume to delete.
     * @return The volume deletion task.
     */
    @Override
    public DriverTask deleteVolume(StorageVolume volume) {
        LOG.info("Deleting volume {}", volume);
        DriverTask task = new DellSCDriverTask("deleteVolume");
        task.setStatus(TaskStatus.FAILED);
        return task;
    }

    /**
     * Create volume snapshots.
     *
     * @param list The list of snapshots to create.
     * @param storageCapabilities The requested capabilities of the snapshots.
     * @return The snapshot creation task.
     */
    @Override
    public DriverTask createVolumeSnapshot(List<VolumeSnapshot> list, StorageCapabilities storageCapabilities) {
        LOG.info("Creating snapshots");
        DriverTask task = new DellSCDriverTask("createVolumeSnapshot");
        task.setStatus(TaskStatus.FAILED);
        return task;
    }

    @Override
    public DriverTask restoreSnapshot(List<VolumeSnapshot> list) {
        LOG.info("Restoring snapshots");
        DriverTask task = new DellSCDriverTask("restoreVolumeSnapshot");
        task.setStatus(TaskStatus.FAILED);
        return task;
    }

    /**
     * Delete snapshots.
     *
     * @param list The snapshots to delete.
     * @return The delete task.
     */
    @Override
    public DriverTask deleteVolumeSnapshot(VolumeSnapshot snapshot) {
        LOG.info("Deleting volume snapshot {}.", snapshot);
        DriverTask task = new DellSCDriverTask("deleteVolumeSnapshot");
        task.setStatus(TaskStatus.FAILED);
        return task;
    }

    /**
     * Create a clone of a volume.
     *
     * @param list The clones to create.
     * @param storageCapabilities The requested capabilities for the clones.
     * @return The clone task.
     */
    @Override
    public DriverTask createVolumeClone(List<VolumeClone> list, StorageCapabilities storageCapabilities) {
        LOG.info("Creating volume clone");
        DriverTask task = new DellSCDriverTask("createVolumeClone");
        task.setStatus(TaskStatus.FAILED);
        return task;
    }

    /**
     * Detach volume clones.
     *
     * @param list The clones to detach.
     * @return The detach task.
     */
    @Override
    public DriverTask detachVolumeClone(List<VolumeClone> list) {
        LOG.info("Detaching volume clone");
        DriverTask task = new DellSCDriverTask("detachVolumeClone");
        task.setStatus(TaskStatus.FAILED);
        return task;
    }

    @Override
    public DriverTask restoreFromClone(List<VolumeClone> list) {
        LOG.info("Restoring volume clone");
        DriverTask task = new DellSCDriverTask("restoreVolumeClone");
        task.setStatus(TaskStatus.FAILED);
        return task;
    }

    /**
     * Delete volume clone.
     *
     * @param clone The clone to delete.
     * @return The delete task.
     */
    @Override
    public DriverTask deleteVolumeClone(VolumeClone clone) {
        LOG.info("Deleting volume clone {}", clone);
        DriverTask task = new DellSCDriverTask("deleteVolumeClone");
        task.setStatus(TaskStatus.FAILED);
        return task;
    }

    /**
     * Create volume mirrors.
     *
     * @param list The volume mirrors to create.
     * @param storageCapabilities The requested capabilities.
     * @return The creation task.
     */
    @Override
    public DriverTask createVolumeMirror(List<VolumeMirror> list, StorageCapabilities storageCapabilities) {
        LOG.info("Creating volume mirror");
        DriverTask task = new DellSCDriverTask("createVolumeMirror");
        task.setStatus(TaskStatus.FAILED);
        return task;
    }

    @Override
    public DriverTask createConsistencyGroupMirror(VolumeConsistencyGroup volumeConsistencyGroup, List<VolumeMirror> list,
            List<CapabilityInstance> list1) {
        LOG.info("Creating consistency group mirror");
        DriverTask task = new DellSCDriverTask("createConsistencyGroupMirror");
        task.setStatus(TaskStatus.FAILED);
        return task;
    }

    /**
     * Delete volume mirror.
     *
     * @param mirror The mirror to delete.
     * @return The delete task.
     */
    @Override
    public DriverTask deleteVolumeMirror(VolumeMirror mirror) {
        LOG.info("Deleting volume mirror {}", mirror);
        DriverTask task = new DellSCDriverTask("deleteVolumeMirror");
        task.setStatus(TaskStatus.FAILED);
        return task;
    }

    @Override
    public DriverTask deleteConsistencyGroupMirror(List<VolumeMirror> list) {
        LOG.info("Deleting consistency group mirror");
        DriverTask task = new DellSCDriverTask("deleteConsistencyGroupMirror");
        task.setStatus(TaskStatus.FAILED);
        return task;
    }

    /**
     * Split volume mirrors.
     *
     * @param list The mirrors to split.
     * @return The split task.
     */
    @Override
    public DriverTask splitVolumeMirror(List<VolumeMirror> list) {
        LOG.info("Splitting volume mirror");
        DriverTask task = new DellSCDriverTask("splitVolumeMirror");
        task.setStatus(TaskStatus.FAILED);
        return task;
    }

    /**
     * Resume volume mirrors.
     *
     * @param list The mirrors to resume.
     * @return The mirror task.
     */
    @Override
    public DriverTask resumeVolumeMirror(List<VolumeMirror> list) {
        LOG.info("Resuming volume mirror");
        DriverTask task = new DellSCDriverTask("resumeVolumeMirror");
        task.setStatus(TaskStatus.FAILED);
        return task;
    }

    @Override
    public DriverTask restoreVolumeMirror(List<VolumeMirror> list) {
        LOG.info("Restoring volume mirror");
        DriverTask task = new DellSCDriverTask("restoreVolumeMirror");
        task.setStatus(TaskStatus.FAILED);
        return task;
    }

    @Override
    public Map<String, HostExportInfo> getVolumeExportInfoForHosts(StorageVolume storageVolume) {
        LOG.info("Getting volume export info for host");
        return new HashMap<String, HostExportInfo>(0);
    }

    @Override
    public Map<String, HostExportInfo> getSnapshotExportInfoForHosts(VolumeSnapshot volumeSnapshot) {
        LOG.info("Getting snapshot export info for host");
        return new HashMap<String, HostExportInfo>(0);
    }

    @Override
    public Map<String, HostExportInfo> getCloneExportInfoForHosts(VolumeClone volumeClone) {
        LOG.info("Getting clone export info for host");
        return new HashMap<String, HostExportInfo>(0);
    }

    @Override
    public Map<String, HostExportInfo> getMirrorExportInfoForHosts(VolumeMirror volumeMirror) {
        LOG.info("Getting mirror export infor host");
        return new HashMap<String, HostExportInfo>(0);
    }

    /**
     * Export volumes to initiators through a given set of ports. If ports are
     * not provided, use port requirements from ExportPathsServiceOption
     * storage capability.
     *
     * @param initiators The initiators to export to.
     * @param volumes The volumes to export.
     * @param volumeToHLUMap Map of volume nativeID to requested HLU. HLU
     *            value of -1 means that HLU is not defined and will
     *            be assigned by array.
     * @param recommendedPorts List of storage ports recommended for the export.
     *            Optional.
     * @param availablePorts List of ports available for the export.
     * @param capabilities The storage capabilities.
     * @param usedRecommendedPorts True if driver used recommended and only
     *            recommended ports for the export, false
     *            otherwise.
     * @param selectedPorts Ports selected for the export (if recommended ports
     *            have not been used).
     * @return The export task.
     */
    @Override
    public DriverTask exportVolumesToInitiators(List<Initiator> initiators, List<StorageVolume> volumes,
            Map<String, String> volumeToHLUMap, List<StoragePort> recommendedPorts,
            List<StoragePort> availablePorts, StorageCapabilities capabilities,
            MutableBoolean usedRecommendedPorts, List<StoragePort> selectedPorts) {
        LOG.info("Exporting volumes to inititators");
        DriverTask task = new DellSCDriverTask("exportVolumes");
        task.setStatus(TaskStatus.FAILED);
        return task;
    }

    /**
     * Remove volume exports to initiators.
     *
     * @param initiators The initiators to remove from.
     * @param volumes The volumes to remove.
     * @return The unexport task.
     */
    @Override
    public DriverTask unexportVolumesFromInitiators(List<Initiator> initiators, List<StorageVolume> volumes) {
        LOG.info("Unexporting volumes from initiators");
        DriverTask task = new DellSCDriverTask("unexportVolumes");
        task.setStatus(TaskStatus.FAILED);
        return task;
    }

    /**
     * Create a consistency group.
     *
     * @param volumeConsistencyGroup The group to create.
     * @return The consistency group creation task.
     */
    @Override
    public DriverTask createConsistencyGroup(VolumeConsistencyGroup volumeConsistencyGroup) {
        LOG.info("Creating consistency group");
        DriverTask task = new DellSCDriverTask("createConsistencyGroup");
        task.setStatus(TaskStatus.FAILED);
        return task;
    }

    /**
     * Delete a consistency group.
     *
     * @param volumeConsistencyGroup The group to delete.
     * @return The consistency group delete task.
     */
    @Override
    public DriverTask deleteConsistencyGroup(VolumeConsistencyGroup volumeConsistencyGroup) {
        LOG.info("Deleting consistency group");
        DriverTask task = new DellSCDriverTask("deleteVolume");
        task.setStatus(TaskStatus.FAILED);
        return task;
    }

    /**
     * Create consistency group snapshots.
     *
     * @param volumeConsistencyGroup The consistency group.
     * @param snapshots The snapshots.
     * @param capabilities The requested capabilities.
     * @return The create task.
     */
    @Override
    public DriverTask createConsistencyGroupSnapshot(VolumeConsistencyGroup volumeConsistencyGroup,
            List<VolumeSnapshot> snapshots,
            List<CapabilityInstance> capabilities) {
        LOG.info("Creating consistency group snapshot");
        DriverTask task = new DellSCDriverTask("createCGSnapshot");
        task.setStatus(TaskStatus.FAILED);
        return task;
    }

    /**
     * Delete a consistency group snapshot set.
     *
     * @param snapshots The snapshots to delete.
     * @return The delete task.
     */
    @Override
    public DriverTask deleteConsistencyGroupSnapshot(List<VolumeSnapshot> snapshots) {
        LOG.info("Deleting consistency group snapshot");
        DriverTask task = new DellSCDriverTask("deleteCGSnapshot");
        task.setStatus(TaskStatus.FAILED);
        return task;
    }

    /**
     * Create clone of consistency group.
     *
     * @param volumeConsistencyGroup The consistency group.
     * @param volumes The volumes to clone.
     * @param capabilities The requested capabilities.
     * @return The clone creation task.
     */
    @Override
    public DriverTask createConsistencyGroupClone(VolumeConsistencyGroup volumeConsistencyGroup,
            List<VolumeClone> volumes,
            List<CapabilityInstance> capabilities) {
        LOG.info("Creating consistency group clone");
        DriverTask task = new DellSCDriverTask("createCGClone");
        task.setStatus(TaskStatus.FAILED);
        return task;
    }

    /**
     * Get driver registration data.
     *
     * @return The registration data.
     */
    @Override
    public RegistrationData getRegistrationData() {
        LOG.info("Getting registration data.");
        return new RegistrationData("Dell SC Storage", DRIVER_NAME, null);
    }

    /**
     * Discover storage systems and their capabilities.
     *
     * @param storageSystem Storage system to discover.
     * @return The discovery task.
     */
    @Override
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
            long ssn = Long.parseLong(sn);
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
                StorageCenter sc = api.findStorageCenter(ssn);
                storageSystem.setSerialNumber(sc.scSerialNumber);
                storageSystem.setAccessStatus(AccessStatus.READ_WRITE);
                storageSystem.setModel(sc.modelSeries);
                storageSystem.setProvisioningType(SupportedProvisioningType.THIN);
                storageSystem.setNativeId(sc.scSerialNumber);
                storageSystem.setSystemType(DRIVER_NAME);

                // Parse out version information
                String[] version = sc.version.split("\\.");
                storageSystem.setMajorVersion(version[0]);
                storageSystem.setMinorVersion(version[1]);
                storageSystem.setFirmwareVersion(sc.version);
                storageSystem.setIsSupportedVersion(true);

                // Set display info if needed
                if (storageSystem.getDeviceLabel() == null) {
                    if (storageSystem.getDisplayName() != null) {
                        storageSystem.setDeviceLabel(storageSystem.getDisplayName());
                    } else {
                        storageSystem.setDeviceLabel(sc.scName);
                        storageSystem.setDisplayName(sc.scName);
                    }
                }

                // TODO(smcginnis): Only supporting iSCSI to start, expand for other protocols
                List<String> protocols = new ArrayList<>();
                protocols.add(Protocols.iSCSI.toString());
                storageSystem.setProtocols(protocols);

                saveConnectionInfo(
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

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.storagedriver.BlockStorageDriver#validateStorageProviderConnection(com.emc.storageos.storagedriver.model.
     * StorageProvider)
     */
    @Override
    public boolean validateStorageProviderConnection(StorageProvider storageProvider) {
        LOG.info("Validating storage provider connection.");

        try (StorageCenterAPI api = StorageCenterAPI.openConnection(
                storageProvider.getProviderHost(),
                storageProvider.getPortNumber(),
                storageProvider.getUsername(),
                storageProvider.getPassword())) {
            LOG.info("Connection to {} validated", storageProvider.getProviderHost());
            return true;
        } catch (Exception e) {
            LOG.error("Failed to connect to storage provider {}: {}",
                    storageProvider.getProviderHost(), e);
        }
        return false;
    }

    /**
     * Discover storage pools and their capabilities.
     *
     * @param storageSystem The storage system on which to discover.
     * @param storagePools The storage pools.
     * @return The discovery task.
     */
    @Override
    public DriverTask discoverStoragePools(StorageSystem storageSystem, List<StoragePool> storagePools) {
        LOG.info("Discovering storage pools for [{}] {} {}",
                storageSystem.getSystemName(), storageSystem.getIpAddress(),
                storageSystem.getNativeId());
        DellSCDriverTask task = new DellSCDriverTask("discoverStoragePools");

        try (StorageCenterAPI api = getSavedConnection(storageSystem.getNativeId())) {

            ScStorageType[] storageTypes = api.getStorageTypes(storageSystem.getNativeId());
            StorageCenterStorageUsage su = api.getStorageUsage(storageSystem.getNativeId());
            for (ScStorageType storageType : storageTypes) {
                StoragePool pool = new StoragePool();
                pool.setNativeId(storageType.instanceId);
                pool.setStorageSystemId(storageSystem.getNativeId());
                LOG.info("Discovered Pool {}, storageSystem {}",
                        pool.getNativeId(), pool.getStorageSystemId());

                pool.setDeviceLabel(storageType.name);
                pool.setPoolName(storageType.name);

                Set<StoragePool.Protocols> protocols = new HashSet<>();
                protocols.add(StoragePool.Protocols.iSCSI);
                pool.setProtocols(protocols);

                pool.setPoolServiceType(StoragePool.PoolServiceType.block);
                pool.setMaximumThickVolumeSize(0L);
                pool.setMinimumThickVolumeSize(0L);
                pool.setMaximumThinVolumeSize(549755813888L); // Max 512 TB
                pool.setMinimumThinVolumeSize(1048576L); // Min 1 GB
                pool.setSupportedResourceType(StoragePool.SupportedResourceType.THIN_ONLY);

                LOG.info("Space info: {} {} {}", su.configuredSpace, su.availableSpace, su.allocatedSpace);
                pool.setSubscribedCapacity(5000000L);
                pool.setFreeCapacity(45000000L);
                pool.setTotalCapacity(48000000L);
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
    @Override
    public DriverTask discoverStoragePorts(StorageSystem storageSystem, List<StoragePort> storagePorts) {
        LOG.info("Discovering storage ports for [{}] {} {}",
                storageSystem.getSystemName(), storageSystem.getIpAddress(),
                storageSystem.getNativeId());
        DellSCDriverTask task = new DellSCDriverTask("discoverStoragePorts");

        try (StorageCenterAPI api = getSavedConnection(storageSystem.getNativeId())) {

            ScControllerPort[] iscsiPorts = api.getIscsiTargetPorts(storageSystem.getNativeId());
            for (ScControllerPort scPort : iscsiPorts) {

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

                ScControllerPortIscsiConfiguration portConfig = api.getControllerPortIscsiConfig(
                        scPort.instanceId);

                port.setDeviceLabel(scPort.iscsiName);
                port.setPortName(port.getDeviceLabel());
                port.setPortHAZone(haZone);
                port.setNetworkId(portConfig.ipAddress);
                port.setIpAddress(portConfig.ipAddress);
                port.setPortNetworkId(portConfig.getFormattedMACAddress());
                port.setTransportType(StoragePort.TransportType.IP);
                port.setOperationalStatus(StoragePort.OperationalStatus.OK);
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

    @Override
    public DriverTask discoverStorageHostComponents(StorageSystem storageSystem, List<StorageHostComponent> storageHosts) {
        LOG.info("Discovering storage host components");
        DellSCDriverTask task = new DellSCDriverTask("discoverStorageHostComponents");

        try (StorageCenterAPI api = getSavedConnection(storageSystem.getNativeId())) {

            ScServer[] servers = api.getServerDefinitions(storageSystem.getNativeId());
            for (ScServer server : servers) {

                StorageHostComponent host = new StorageHostComponent();
                host.setNativeId(server.instanceId);
                LOG.info("Discovered storage host {}, storageSystem {}",
                        host.getNativeId(), storageSystem.getNativeId());

                host.setAccessStatus(AccessStatus.READ_WRITE);
                host.setDeviceLabel(server.name);
                host.setDisplayName(host.getDeviceLabel());
                host.setHostName(host.getDeviceLabel());
                host.setIsSupportedVersion(true);

                // Get the HBAs for this server
                Set<Initiator> initiators = new HashSet<>();
                ScServerHba[] hbas = api.getServerHbas(storageSystem.getNativeId(), server.instanceId);

                for (ScServerHba hba : hbas) {
                    // TODO(smcginnis): Determine what all should be set
                    Initiator init = new Initiator();
                    init.setDeviceLabel(hba.name);
                    init.setDisplayName(hba.name);
                    init.setNativeId(hba.instanceId);
                    initiators.add(init);
                }

                host.setInitiators(initiators);
                storageHosts.add(host);
            }
            task.setStatus(DriverTask.TaskStatus.READY);
        } catch (Exception e) {
            String failureMessage = String.format("Error getting server definition information: %s", e);
            task.setFailed(failureMessage);
            LOG.warn(failureMessage);
        }
        return task;
    }

    @Override
    public DriverTask discoverStorageProvider(StorageProvider storageProvider, List<StorageSystem> storageSystems) {
        DriverTask task = new DellSCDriverTask("discover");

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
                storageProvider.setProviderVersion(DRIVER_VERSION);
                storageProvider.setIsSupportedVersion(true);

                // Populate the basic SC information
                StorageCenter[] scs = api.getStorageCenterInfo();
                for (StorageCenter sc : scs) {
                    StorageSystem storageSystem = new StorageSystem();
                    storageSystem.setSerialNumber(sc.scSerialNumber);
                    storageSystem.setNativeId(sc.scSerialNumber);
                    storageSystem.setSystemType(DRIVER_NAME);

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
            task.setMessage(msg);
            task.setStatus(DriverTask.TaskStatus.FAILED);
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
    @Override
    public DriverTask getStorageVolumes(StorageSystem storageSystem,
            List<StorageVolume> storageVolumes,
            MutableInt token) {
        LOG.info("Getting volumes");
        DriverTask task = new DellSCDriverTask("getVolumes");
        task.setStatus(TaskStatus.FAILED);
        return task;
    }

    @Override
    public List<VolumeSnapshot> getVolumeSnapshots(StorageVolume storageVolume) {
        LOG.info("Getting snapshots for {}", storageVolume.getNativeId());
        return new ArrayList<>(0);
    }

    @Override
    public List<VolumeClone> getVolumeClones(StorageVolume storageVolume) {
        LOG.info("Getting clones for volume {}", storageVolume.getNativeId());
        return new ArrayList<>(0);
    }

    @Override
    public List<VolumeMirror> getVolumeMirrors(StorageVolume storageVolume) {
        LOG.info("Getting mirrors for volume {}", storageVolume.getNativeId());
        return new ArrayList<>(0);
    }

    /**
     * Return driver task with a given id.
     *
     * @param taskId The task ID.
     * @return The requested task or Null if it does not exist.
     */
    @Override
    public DriverTask getTask(String taskId) {
        LOG.info("Getting task {}", taskId);
        return null;
    }

    /**
     * Get storage object with a given type with specified native ID which belongs to specified storage system.
     *
     * @param storageSystemId The storage system native ID.
     * @param objectId The object ID.
     * @param type The class instance.
     * @param <T> The storage object type.
     * @return Storage object or Null if it does not exist.
     */
    @Override
    public <T extends StorageObject> T getStorageObject(String storageSystemId, String objectId, Class<T> type) {
        LOG.info("Request for object {} from {}", objectId, storageSystemId);
        return null;
    }

    @Override
    public DriverTask stopManagement(StorageSystem storageSystem) {
        LOG.info("Stop management called for storage system {}", storageSystem.getNativeId());
        DriverTask task = new DellSCDriverTask("stopManagement");
        task.setStatus(TaskStatus.FAILED);
        return task;
    }

    @Override
    public DriverTask addVolumesToConsistencyGroup(List<StorageVolume> volumes, StorageCapabilities capabilities) {
        LOG.info("Adding volumes to consistency group");
        DriverTask task = new DellSCDriverTask("addVolumesToCG");
        task.setStatus(TaskStatus.FAILED);
        return task;
    }

    @Override
    public DriverTask removeVolumesFromConsistencyGroup(List<StorageVolume> volumes, StorageCapabilities capabilities) {
        LOG.info("Removing volumes from consistency group");
        DriverTask task = new DellSCDriverTask("removeVolumeFromCG");
        task.setStatus(TaskStatus.FAILED);
        return task;
    }

    /**
     * Saves connection information to the registry.
     * 
     * @param systemId The identifier for this entry.
     * @param host The host name or IP.
     * @param port The connection port.
     * @param user The connection user name.
     * @param password The connection password.
     */
    private void saveConnectionInfo(String systemId, String host, int port, String user, String password) {
        LOG.info("Saving connection information for {} - {}:{}", systemId, host, port);

        List<String> listHost = new ArrayList<>();
        List<String> listPort = new ArrayList<>();
        List<String> listUser = new ArrayList<>();
        List<String> listPass = new ArrayList<>();
        listHost.add(host);
        listPort.add(Integer.toString(port));
        listUser.add(user);
        listPass.add(password);

        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(HOST_KEY, listHost);
        attributes.put(PORT_KEY, listPort);
        attributes.put(USER_KEY, listUser);
        attributes.put(PASS_KEY, listPass);

        this.driverRegistry.clearDriverAttributesForKey(DRIVER_NAME, systemId);
        this.driverRegistry.setDriverAttributesForKey(DRIVER_NAME, systemId, attributes);
    }

    /**
     * Get a connection from the saved settings.
     * 
     * @param systemId The system ID of the connection.
     * @return The Storage Center API connection.
     * @throws DellSCDriverException on failure.
     */
    private StorageCenterAPI getSavedConnection(String systemId) throws DellSCDriverException {
        LOG.info("Getting saved connection information for {}", systemId);

        try {
            Map<String, List<String>> connectionInfo = this.driverRegistry.getDriverAttributesForKey(
                    DRIVER_NAME, systemId);
            return StorageCenterAPI.openConnection(
                    connectionInfo.get(HOST_KEY).get(0),
                    Integer.parseInt(connectionInfo.get(PORT_KEY).get(0)),
                    connectionInfo.get(USER_KEY).get(0),
                    connectionInfo.get(PASS_KEY).get(0));
        } catch (Exception e) {
            LOG.error(String.format("Error getting saved connection information: %s", e), e);
            throw new DellSCDriverException("Error getting saved connection information.", e);
        }
    }
}
