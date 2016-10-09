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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.mutable.MutableBoolean;
import org.apache.commons.lang.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.dellsc.helpers.DellSCCloning;
import com.emc.storageos.driver.dellsc.helpers.DellSCConnectionManager;
import com.emc.storageos.driver.dellsc.helpers.DellSCConsistencyGroups;
import com.emc.storageos.driver.dellsc.helpers.DellSCDiscovery;
import com.emc.storageos.driver.dellsc.helpers.DellSCMirroring;
import com.emc.storageos.driver.dellsc.helpers.DellSCProvisioning;
import com.emc.storageos.driver.dellsc.helpers.DellSCSnapshots;
import com.emc.storageos.driver.dellsc.scapi.StorageCenterAPI;
import com.emc.storageos.driver.dellsc.scapi.StorageCenterAPIException;
import com.emc.storageos.driver.dellsc.scapi.objects.ScReplayProfile;
import com.emc.storageos.driver.dellsc.scapi.objects.ScVolume;
import com.emc.storageos.storagedriver.BlockStorageDriver;
import com.emc.storageos.storagedriver.DefaultStorageDriver;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.DriverTask.TaskStatus;
import com.emc.storageos.storagedriver.HostExportInfo;
import com.emc.storageos.storagedriver.RegistrationData;
import com.emc.storageos.storagedriver.model.Initiator;
import com.emc.storageos.storagedriver.model.StorageObject;
import com.emc.storageos.storagedriver.model.StoragePool;
import com.emc.storageos.storagedriver.model.StoragePort;
import com.emc.storageos.storagedriver.model.StorageProvider;
import com.emc.storageos.storagedriver.model.StorageSystem;
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

    static final String DRIVER_NAME = "dellscsystem";
    static final String DRIVER_VERSION = "1.0.0";

    private DellSCProvisioning provisioningHelper = new DellSCProvisioning();
    private DellSCSnapshots snapshotHelper = new DellSCSnapshots();
    private DellSCConsistencyGroups cgHelper = new DellSCConsistencyGroups();
    private DellSCDiscovery discoveryHelper = new DellSCDiscovery(DRIVER_NAME, DRIVER_VERSION);
    private DellSCCloning cloneHelper = new DellSCCloning();
    private DellSCMirroring mirrorHelper = new DellSCMirroring();

    @Override
    public synchronized void setDriverRegistry(com.emc.storageos.storagedriver.Registry driverRegistry) {
        super.setDriverRegistry(driverRegistry);

        // Make sure our helper class gets updated with the right info
        DellSCConnectionManager.getInstance().setDriverRegistry(driverRegistry);
    }

    //
    // Driver common functionality
    //

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

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.storagedriver.BlockStorageDriver#validateStorageProviderConnection(com.emc.storageos.storagedriver.model.
     * StorageProvider)
     */
    @Override
    public boolean validateStorageProviderConnection(StorageProvider storageProvider) {
        LOG.info("Validating storage provider connection.");

        try {
            DellSCConnectionManager.getInstance().getConnection(
                    storageProvider.getProviderHost(),
                    storageProvider.getPortNumber(),
                    storageProvider.getUsername(),
                    storageProvider.getPassword(), false);
            LOG.info("Connection to {} validated", storageProvider.getProviderHost());
            return true;
        } catch (StorageCenterAPIException e) {
            LOG.error(String.format("Failed to connect to storage provider %s: %s",
                    storageProvider.getProviderHost(), e));
        }
        return false;
    }

    /**
     * Return driver task with a given id.
     *
     * @param taskId The task ID.
     * @return The requested task or Null if it does not exist.
     */
    @Override
    public DriverTask getTask(String taskId) {
        // Will need to implement if/when we support async tasks
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
    @SuppressWarnings("unchecked")
    @Override
    public <T extends StorageObject> T getStorageObject(String storageSystemId, String objectId, Class<T> type) {
        String requestedType = type.getSimpleName();
        LOG.info("Request for {} object {} from {}", requestedType, objectId, storageSystemId);
        DellSCUtil util = DellSCUtil.getInstance();
        try {
            StorageCenterAPI api = DellSCConnectionManager.getInstance().getConnection(storageSystemId);

            if (requestedType.equals(StorageVolume.class.getSimpleName())) {
                ScVolume volume = api.getVolume(objectId);
                Map<ScReplayProfile, List<String>> cgInfo = util.getGCInfo(api, storageSystemId);
                return (T) util.getStorageVolumeFromScVolume(api, volume, cgInfo);
            } else if (requestedType.equals(VolumeConsistencyGroup.class.getSimpleName())) {
                return (T) util.getVolumeConsistencyGroupFromReplayProfile(
                        api.getConsistencyGroup(objectId), null);
            } else if (requestedType.equals(VolumeSnapshot.class.getSimpleName())) {
                return (T) util.getVolumeSnapshotFromReplay(api.getReplay(objectId), null);
            } else if (requestedType.equals(StoragePool.class.getSimpleName())) {
                return (T) util.getStoragePoolFromStorageType(api, api.getStorageType(objectId), null);
            }
        } catch (StorageCenterAPIException | DellSCDriverException e) {
            String message = String.format("Error getting requested storage object: %s", e);
            LOG.warn(message);
        }

        LOG.warn("Requested object of type {} not found.", requestedType);
        return null;

    }

    @Override
    public DriverTask stopManagement(StorageSystem storageSystem) {
        LOG.info("Stop management called for storage system {}", storageSystem.getNativeId());
        DriverTask task = new DellSCDriverTask("stopManagement");
        task.setStatus(TaskStatus.READY);
        return task;
    }

    //
    // Provisioning operations
    //

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
    public DriverTask createVolumes(List<StorageVolume> volumes, StorageCapabilities storageCapabilities) {
        LOG.info("Creating {} new volumes", volumes.size());
        return provisioningHelper.createVolumes(volumes, storageCapabilities);
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
        return provisioningHelper.expandVolume(storageVolume, newCapacity);
    }

    /**
     * Delete volume.
     *
     * @param volume The volume to delete.
     * @return The volume deletion task.
     */
    @Override
    public DriverTask deleteVolume(StorageVolume volume) {
        LOG.info("Deleting volume {}", volume.getNativeId());
        return provisioningHelper.deleteVolume(volume);
    }

    //
    // Snapshot operations
    //

    /**
     * Create volume snapshots.
     *
     * @param snapshots The list of snapshots to create.
     * @param storageCapabilities The requested capabilities of the snapshots.
     * @return The snapshot creation task.
     */
    @Override
    public DriverTask createVolumeSnapshot(List<VolumeSnapshot> snapshots, StorageCapabilities storageCapabilities) {
        LOG.info("Creating {} snapshots...", snapshots.size());
        return snapshotHelper.createVolumeSnapshot(snapshots, storageCapabilities);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.storagedriver.DefaultStorageDriver#restoreSnapshot(java.util.List)
     */
    @Override
    public DriverTask restoreSnapshot(List<VolumeSnapshot> list) {
        LOG.info("Restoring snapshots");
        return snapshotHelper.restoreSnapshot(list);
    }

    /**
     * Delete a snapshot.
     *
     * @param snapshot The snapshot to delete.
     * @return The delete task.
     */
    @Override
    public DriverTask deleteVolumeSnapshot(VolumeSnapshot snapshot) {
        LOG.info("Deleting volume snapshot {}.", snapshot);
        return snapshotHelper.deleteVolumeSnapshot(snapshot);
    }

    //
    // Clone operations
    //

    /**
     * Create a clone of a volume.
     *
     * @param list The clones to create.
     * @param storageCapabilities The requested capabilities for the clones.
     * @return The clone task.
     */
    @Override
    public DriverTask createVolumeClone(List<VolumeClone> list, StorageCapabilities storageCapabilities) {
        return cloneHelper.createVolumeClone(list);
    }

    /**
     * Detach volume clones.
     *
     * @param list The clones to detach.
     * @return The detach task.
     */
    @Override
    public DriverTask detachVolumeClone(List<VolumeClone> list) {
        return cloneHelper.detachVolumeClone(list);
    }

    /**
     * Restore a cloned volume.
     *
     * @param list The volume clones.
     * @return The driver task.
     */
    @Override
    public DriverTask restoreFromClone(List<VolumeClone> list) {
        return cloneHelper.restoreFromClone(list);
    }

    /**
     * Delete volume clone.
     *
     * @param clone The clone to delete.
     * @return The delete task.
     */
    @Override
    public DriverTask deleteVolumeClone(VolumeClone clone) {
        return cloneHelper.deleteVolumeClone(clone);
    }

    //
    // Mirror operations
    //

    /**
     * Create volume mirrors.
     *
     * @param list The volume mirrors to create.
     * @param storageCapabilities The requested capabilities.
     * @return The creation task.
     */
    @Override
    public DriverTask createVolumeMirror(List<VolumeMirror> list, StorageCapabilities storageCapabilities) {
        return mirrorHelper.createVolumeMirror(list);
    }

    /**
     * Delete volume mirror.
     *
     * @param mirror The mirror to delete.
     * @return The delete task.
     */
    @Override
    public DriverTask deleteVolumeMirror(VolumeMirror mirror) {
        return mirrorHelper.deleteVolumeMirror(mirror);
    }

    /**
     * Split volume mirrors.
     *
     * @param list The mirrors to split.
     * @return The split task.
     */
    @Override
    public DriverTask splitVolumeMirror(List<VolumeMirror> list) {
        return mirrorHelper.splitVolumeMirror(list);
    }

    /**
     * Resume volume mirrors.
     *
     * @param list The mirrors to resume.
     * @return The mirror task.
     */
    @Override
    public DriverTask resumeVolumeMirror(List<VolumeMirror> list) {
        return mirrorHelper.resumeVolumeMirror(list);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.storagedriver.DefaultStorageDriver#restoreVolumeMirror(java.util.List)
     */
    @Override
    public DriverTask restoreVolumeMirror(List<VolumeMirror> list) {
        return mirrorHelper.restoreVolumeMirror(list);
    }

    //
    // Export/unexport operations
    //

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
        return provisioningHelper.exportVolumesToInitiators(
                initiators,
                volumes,
                volumeToHLUMap,
                recommendedPorts,
                availablePorts,
                capabilities,
                usedRecommendedPorts,
                selectedPorts);
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
        return provisioningHelper.unexportVolumesFromInitiators(initiators, volumes);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.storagedriver.DefaultStorageDriver#getVolumeExportInfoForHosts(com.emc.storageos.storagedriver.model.StorageVolume)
     */
    @Override
    public Map<String, HostExportInfo> getVolumeExportInfoForHosts(StorageVolume storageVolume) {
        LOG.info("Getting volume export info for host");
        return provisioningHelper.getVolumeExportInfo(storageVolume.getNativeId(), storageVolume.getStorageSystemId());
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.storagedriver.DefaultStorageDriver#getSnapshotExportInfoForHosts(com.emc.storageos.storagedriver.model.
     * VolumeSnapshot)
     */
    @Override
    public Map<String, HostExportInfo> getSnapshotExportInfoForHosts(VolumeSnapshot volumeSnapshot) {
        LOG.info("Getting snapshot export info for host");
        // We don't export snapshots
        return new HashMap<>(0);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.storagedriver.DefaultStorageDriver#getCloneExportInfoForHosts(com.emc.storageos.storagedriver.model.VolumeClone)
     */
    @Override
    public Map<String, HostExportInfo> getCloneExportInfoForHosts(VolumeClone volumeClone) {
        LOG.info("Getting clone export info for host");
        return provisioningHelper.getVolumeExportInfo(volumeClone.getNativeId(), volumeClone.getStorageSystemId());
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.storagedriver.DefaultStorageDriver#getMirrorExportInfoForHosts(com.emc.storageos.storagedriver.model.VolumeMirror)
     */
    @Override
    public Map<String, HostExportInfo> getMirrorExportInfoForHosts(VolumeMirror volumeMirror) {
        LOG.info("Getting mirror export info for host");
        // We don't export mirrors
        return new HashMap<>(0);
    }

    //
    // Consistency group related operations
    //

    /**
     * Create a consistency group.
     *
     * @param volumeConsistencyGroup The group to create.
     * @return The consistency group creation task.
     */
    @Override
    public DriverTask createConsistencyGroup(VolumeConsistencyGroup volumeConsistencyGroup) {
        LOG.info("Creating consistency group {}", volumeConsistencyGroup.getDisplayName());
        return cgHelper.createConsistencyGroup(volumeConsistencyGroup);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.storagedriver.DefaultStorageDriver#addVolumesToConsistencyGroup(java.util.List,
     * com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities)
     */
    @Override
    public DriverTask addVolumesToConsistencyGroup(List<StorageVolume> volumes, StorageCapabilities capabilities) {
        LOG.info("Adding volumes to consistency group");
        return cgHelper.addVolumesToConsistencyGroup(volumes, capabilities);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.storagedriver.DefaultStorageDriver#removeVolumesFromConsistencyGroup(java.util.List,
     * com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities)
     */
    @Override
    public DriverTask removeVolumesFromConsistencyGroup(List<StorageVolume> volumes, StorageCapabilities capabilities) {
        LOG.info("Removing volumes from consistency group");
        return cgHelper.removeVolumesFromConsistencyGroup(volumes, capabilities);
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
        return cgHelper.deleteConsistencyGroup(volumeConsistencyGroup);
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
        return cgHelper.createConsistencyGroupSnapshot(volumeConsistencyGroup, snapshots, capabilities);
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
        DellSCDriverTask task = new DellSCDriverTask("deleteConsistencyGroupSnapshot");
        StringBuilder errBuffer = new StringBuilder();
        int deletedCount = 0;

        for (VolumeSnapshot snapshot : snapshots) {
            DriverTask subTask = snapshotHelper.deleteVolumeSnapshot(snapshot);
            if (subTask.getStatus() == TaskStatus.FAILED) {
                errBuffer.append(String.format("%s%n", subTask.getMessage()));
            } else {
                deletedCount++;
            }
        }

        task.setMessage(errBuffer.toString());

        if (deletedCount == snapshots.size()) {
            task.setStatus(TaskStatus.READY);
        } else if (deletedCount == 0) {
            task.setStatus(TaskStatus.FAILED);
        } else {
            task.setStatus(TaskStatus.PARTIALLY_FAILED);
        }

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
        LOG.info("Creating consistency group clone for group {}", volumeConsistencyGroup.getDisplayName());
        // No difference for us cloning group or single volumes
        return cloneHelper.createVolumeClone(volumes);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.storagedriver.DefaultStorageDriver#createConsistencyGroupMirror(com.emc.storageos.storagedriver.model.
     * VolumeConsistencyGroup, java.util.List, java.util.List)
     */
    @Override
    public DriverTask createConsistencyGroupMirror(VolumeConsistencyGroup volumeConsistencyGroup, List<VolumeMirror> mirrors,
            List<CapabilityInstance> capabilities) {
        LOG.info("Creating consistency group mirrors for group {}", volumeConsistencyGroup.getDisplayName());
        return mirrorHelper.createVolumeMirror(mirrors);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.storagedriver.DefaultStorageDriver#deleteConsistencyGroupMirror(java.util.List)
     */
    @Override
    public DriverTask deleteConsistencyGroupMirror(List<VolumeMirror> mirrors) {
        DellSCDriverTask task = new DellSCDriverTask("deleteConsistencyGroupMirror");
        StringBuilder errBuffer = new StringBuilder();
        int deletedCount = 0;

        for (VolumeMirror mirror : mirrors) {
            DriverTask subTask = mirrorHelper.deleteVolumeMirror(mirror);
            if (subTask.getStatus() == TaskStatus.FAILED) {
                errBuffer.append(String.format("%s%n", subTask.getMessage()));
            } else {
                deletedCount++;
            }
        }

        task.setMessage(errBuffer.toString());

        if (deletedCount == mirrors.size()) {
            task.setStatus(TaskStatus.READY);
        } else if (deletedCount == 0) {
            task.setStatus(TaskStatus.FAILED);
        } else {
            task.setStatus(TaskStatus.PARTIALLY_FAILED);
        }

        return task;
    }

    //
    // Discovery functions
    //

    /**
     * Discover storage systems and their capabilities.
     *
     * @param storageSystem Storage system to discover.
     * @return The discovery task.
     */
    @Override
    public DriverTask discoverStorageSystem(StorageSystem storageSystem) {
        return discoveryHelper.discoverStorageSystem(storageSystem);
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
        return discoveryHelper.discoverStoragePools(storageSystem, storagePools);
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
        return discoveryHelper.discoverStoragePorts(storageSystem, storagePorts);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.emc.storageos.storagedriver.DefaultStorageDriver#discoverStorageProvider(com.emc.storageos.storagedriver.model.StorageProvider,
     * java.util.List)
     */
    @Override
    public DriverTask discoverStorageProvider(StorageProvider storageProvider, List<StorageSystem> storageSystems) {
        return discoveryHelper.discoverStorageProvider(storageProvider, storageSystems);
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
        return discoveryHelper.getStorageVolumes(storageSystem, storageVolumes, token);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.storagedriver.DefaultStorageDriver#getVolumeSnapshots(com.emc.storageos.storagedriver.model.StorageVolume)
     */
    @Override
    public List<VolumeSnapshot> getVolumeSnapshots(StorageVolume storageVolume) {
        return discoveryHelper.getVolumeSnapshots(storageVolume);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.storagedriver.DefaultStorageDriver#getVolumeClones(com.emc.storageos.storagedriver.model.StorageVolume)
     */
    @Override
    public List<VolumeClone> getVolumeClones(StorageVolume storageVolume) {
        return discoveryHelper.getVolumeClones(storageVolume);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.storagedriver.DefaultStorageDriver#getVolumeMirrors(com.emc.storageos.storagedriver.model.StorageVolume)
     */
    @Override
    public List<VolumeMirror> getVolumeMirrors(StorageVolume storageVolume) {
        return discoveryHelper.getVolumeMirrors(storageVolume);
    }
}
