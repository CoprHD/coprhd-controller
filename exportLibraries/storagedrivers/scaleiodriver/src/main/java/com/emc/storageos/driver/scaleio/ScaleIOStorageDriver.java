/*
 * Copyright 2016 Oregon State University
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
import org.apache.commons.lang.mutable.MutableBoolean;
import org.apache.commons.lang.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.*;

public class ScaleIOStorageDriver extends AbstractStorageDriver implements BlockStorageDriver {
    private static final Logger log = LoggerFactory.getLogger(ScaleIOStorageDriver.class);
    private String fullyQualifiedXMLConfigName = "/scaleio-driver-prov.xml";
    private ApplicationContext context = new ClassPathXmlApplicationContext(fullyQualifiedXMLConfigName);
    private ScaleIORestHandleFactory scaleIORestHandleFactory = (ScaleIORestHandleFactory) context.getBean("scaleIORestHandleFactory");
    private ScaleIORestClient client;

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
        DriverTask task = new DriverTaskImpl(ScaleIOHelper.getTaskId(ScaleIOConstants.TaskType.VOLUME_CREATE));

        if (volumes != null && !volumes.isEmpty()) {
            int successful = 0;

            // Assume volumes can be created for different storage systems
            for (StorageVolume volume : volumes) {
                String capacity = volume.getRequestedCapacity().toString();

                ScaleIORestClient restClient = getClientBySystemId(volume.getStorageSystemId());

                if (restClient != null) {
                    ScaleIOVolume result;

                    try {

                        result = restClient.addVolume(volume.getStorageSystemId(), volume.getStoragePoolId(),
                                volume.getDisplayName(), capacity, volume.getThinlyProvisioned());

                        if (result != null) {
                            volume.setNativeId(result.getId());
                            volume.setAccessStatus(StorageObject.AccessStatus.READ_WRITE);
                            volume.setDeviceLabel(volume.getNativeId());
                            long sizeInBytes = Long.parseLong(result.getSizeInKb()) * 1024;
                            volume.setAllocatedCapacity(sizeInBytes);
                            String wwn = restClient.getSystemId() + result.getId();
                            volume.setWwn(wwn);
                            volume.setProvisionedCapacity(sizeInBytes);
                            successful++;
                        } else {
                            log.error("Error while creating volume " + volume.getDisplayName() +
                                    " in system " + volume.getStorageSystemId());
                        }
                    } catch (Exception e) {
                        log.error("Error while processing volume " + volume.getDisplayName(), e);
                    }

                } else {
                    log.error("Could not get client for storage system: " + volume.getStorageSystemId());
                }
            }

            setTaskStatus(volumes.size(), successful, task);
            task.setMessage("Task succeeded for " + successful + " of " + volumes.size() + " volumes");

        } else {
            log.error("Empty volume input list");
            task.setMessage("Task failed because volume list was empty");
            task.setStatus(DriverTask.TaskStatus.FAILED);
        }

        return task;
    }

    @Override
    public List<VolumeSnapshot> getVolumeSnapshots(StorageVolume storageVolume) {
        return null;
    }

    @Override
    public List<VolumeClone> getVolumeClones(StorageVolume storageVolume) {
        return null;
    }

    @Override
    public List<VolumeMirror> getVolumeMirrors(StorageVolume storageVolume) {
        return null;
    }

    /**
     * Expand volume.
     * Before completion of the request, set all required data for expanded volume in "volume" parameter.
     *
     * @param volume Volume to expand. Type: Input/Output argument.
     * @param newCapacity Requested capacity in GB. Type: input argument.
     * @return task
     */
    @Override
    public DriverTask expandVolume(StorageVolume volume, long newCapacity) {
        DriverTask task = new DriverTaskImpl(ScaleIOHelper.getTaskId(ScaleIOConstants.TaskType.VOLUME_EXPAND));
        long actualCapacity;
        if (newCapacity > 0) {
            ScaleIORestClient restClient = getClientBySystemId(volume.getStorageSystemId());

            if (restClient != null) {
                ScaleIOVolume result;
                try {
                    actualCapacity = ScaleIOHelper.calculateActualCapacityInGB(newCapacity);
                    result = restClient.modifyVolumeCapacity(volume.getNativeId(), String.valueOf(actualCapacity));

                    if (result != null) {
                        volume.setRequestedCapacity(newCapacity);
                        Long sizeInBytes = Long.parseLong(result.getSizeInKb()) * 1024;
                        volume.setProvisionedCapacity(sizeInBytes);
                        volume.setAllocatedCapacity(sizeInBytes);
                        task.setStatus(DriverTask.TaskStatus.READY);
                        task.setMessage("Volume " + volume.getNativeId() + " expanded to " + newCapacity + " GB");
                        return task;
                    } else {
                        log.error("Error while expanding volume" + volume.getDisplayName());
                    }

                } catch (Exception e) {
                    log.error("Error while processing volume" + volume.getDisplayName(), e);
                }

            } else {
                log.error("Could not get client for storage system: " + volume.getStorageSystemId());

            }

        } else {
            log.error("Invalid new capacity: " + newCapacity);
        }

        task.setStatus(DriverTask.TaskStatus.FAILED);
        task.setMessage("Expand operation failed for volume " + volume.getNativeId());
        return task;
    }

    /**
     * Delete volumes.
     *
     * @param volumes Volumes to delete.
     * @return task
     */
    @Override
    public DriverTask deleteVolumes(List<StorageVolume> volumes) {
        DriverTask task = new DriverTaskImpl(ScaleIOHelper.getTaskId(ScaleIOConstants.TaskType.VOLUME_DELETE));

        if (!volumes.isEmpty()) {
            int successful = 0;

            for (StorageVolume volume : volumes) {
                ScaleIORestClient restClient = getClientBySystemId(volume.getStorageSystemId());

                if (restClient != null) {
                    try {
                        restClient.removeVolume(volume.getNativeId());
                        successful++;

                    } catch (Exception e) {
                        log.error("Error while deleting volume " + volume.getNativeId() +
                                "in storage system " + volume.getStorageSystemId(), e);
                    }

                } else {
                    log.error("Could not get client for storage system: " + volume.getStorageSystemId());

                }
            }

            setTaskStatus(volumes.size(), successful, task);
            task.setMessage("Task succeeded for " + successful + " of " + volumes.size() + " volumes");

        } else {
            task.setStatus(DriverTask.TaskStatus.FAILED);
            task.setMessage("Task failed because volume list was empty");
            log.error("Empty volume input list");
        }

        return task;
    }

    /**
     * Create volume snapshots.
     * snapshots can be from different storage system
     * 
     * @param snapshots Type: Input/Output.
     * @param capabilities capabilities required from snapshots. Type: Input.
     * @return task
     */
    @Override
    public DriverTask createVolumeSnapshot(List<VolumeSnapshot> snapshots, StorageCapabilities capabilities) {
        log.info("createVolumeSnapshot -- Start :");
        DriverTask task = new DriverTaskImpl(ScaleIOHelper.getTaskId(ScaleIOConstants.TaskType.SNAPSHOT_CREATE));
        int countSucc = 0;
        if (!isTaskFailedOnEmptySnapList(snapshots, task)) {
            for (VolumeSnapshot snapshot : snapshots) {
                client = getClientBySystemId(snapshot.getStorageSystemId());
                try {
                    createSnapshotForSingleVolume(client, snapshot);
                    countSucc++;
                } catch (Exception e) {
                    log.info("createVolumeSnapshot -- Failed to create snapshot for volume {} ", snapshot.getNativeId(), e);
                    continue;
                }
            }
            setTaskStatus(snapshots.size(), countSucc, task);
        }
        task.setEndTime(Calendar.getInstance());
        log.info("createVolumeSnapshot -- End ");
        return task;
    }

    /**
     * Check if the input snapshot is empty list
     * 
     * @param snapshots
     * @param task
     * @return
     */
    private boolean isTaskFailedOnEmptySnapList(List<VolumeSnapshot> snapshots, DriverTask task) {
        if (snapshots != null && !snapshots.isEmpty()) {
            return false;
        } else {
            log.error("[ERROR] Empty snapshot input List");
            task.setStatus(DriverTask.TaskStatus.FAILED);
            return true;
        }
    }

    /**
     * Create snapshot for a single volume
     * 
     * @param client ScaleIO Rest Client
     * @param snapshot given snapshot object
     * @return True for a successful creation
     */
    private void createSnapshotForSingleVolume(ScaleIORestClient client, VolumeSnapshot snapshot) throws Exception {
        log.info("Create snapshot for volume {}:{} - start", snapshot.getDisplayName(), snapshot.getParentId());
        ScaleIOSnapshotVolumeResponse result = client.snapshotVolume(snapshot.getParentId(), snapshot.getDisplayName(),
                snapshot.getStorageSystemId());
        // set value to the output
        snapshot.setNativeId(result.getVolumeIdList().get(0));
        snapshot.setAccessStatus(StorageObject.AccessStatus.READ_WRITE);
        Map<String, String> snapNameIdMap = client.getVolumes(result.getVolumeIdList());
        snapshot.setDeviceLabel(snapNameIdMap.get(snapshot.getNativeId()));
        log.info("Successfully create snapshot for volume {}:{} - end", snapshot.getDisplayName(),
                snapshot.getParentId());
    }

    @Override
    public DriverTask restoreSnapshot(List<VolumeSnapshot> list) {
        return setUpNonSupportedTask(ScaleIOConstants.TaskType.SNAPSHOT_RESTORE);
    }

    /**
     * Delete snapshots.
     * Assume snapshots could be from different storage system
     * 
     * @param snapshots Type: Input.
     * @return task
     */
    @Override
    public DriverTask deleteVolumeSnapshot(List<VolumeSnapshot> snapshots) {
        log.info("deleteVolumeSnapshot -- Start :");
        DriverTask task = new DriverTaskImpl(ScaleIOHelper.getTaskId(ScaleIOConstants.TaskType.SNAPSHOT_DELETE));
        int countSucc = 0;
        if (!isTaskFailedOnEmptySnapList(snapshots, task)) {
            for (VolumeSnapshot snapshot : snapshots) {
                client = getClientBySystemId(snapshot.getStorageSystemId());
                try {
                    deleteSingleSnapshot(client, snapshot);
                    countSucc++;
                } catch (Exception e) {
                    log.error("deleteVolumeSnapshot - Failed to delete snapshot {}", snapshot.getNativeId(), e);
                    continue;
                }
            }
            setTaskStatus(snapshots.size(), countSucc, task);
        }
        task.setEndTime(Calendar.getInstance());
        log.info("deleteVolumeSnapshot -- End ");
        return task;
    }

    /**
     * Delete single snapshot
     * 
     * @param client ScaleIO rest client
     * @param snapshot given snapshot
     * @return true if successful, false otherwise
     */
    private void deleteSingleSnapshot(ScaleIORestClient client, VolumeSnapshot snapshot) throws Exception {
        log.info("deleteSingleSnapshot {}:{} - start", snapshot.getDisplayName(), snapshot.getNativeId());
        client.removeVolume(snapshot.getNativeId());
        log.info("deleteSingleSnapshot {}:{} - end", snapshot.getDisplayName(), snapshot.getNativeId());
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
        log.info("Request to create Clone -- Start :");
        DriverTask task = new DriverTaskImpl(ScaleIOHelper.getTaskId(ScaleIOConstants.TaskType.CLONE_CREATE));
        int countSucc = 0;
        if (clones != null && clones.size() > 0)
        {
            for (VolumeClone clone : clones) {
                log.info("Start to get Rest client for volume {} of ScaleIO storage system: {}", clone.getParentId(),
                        clone.getStorageSystemId());
                client = this.getClientBySystemId(clone.getStorageSystemId());
                if (client != null) {// Note: ScaleIO snapshots can be treated as full copies, hence re-use of #snapshotVolume
                    ScaleIOSnapshotVolumeResponse result = null;
                    try {
                        result = client.snapshotVolume(clone.getParentId(), clone.getDisplayName(), clone.getStorageSystemId());
                        log.info("Client got! Create clone for volume {}:{} - start", clone.getDisplayName(), clone.getParentId());
                        // Set O/P Value
                        clone.setNativeId(result.getVolumeIdList().get(0));
                        clone.setAccessStatus(StorageObject.AccessStatus.READ_WRITE);
                        clone.setReplicationState(VolumeClone.ReplicationState.CREATED);
                        String wwn = client.getSystemId() + clone.getNativeId();
                        clone.setWwn(wwn);
                        // Set Device Label
                        Map<String, ScaleIOVolume> cloneVolIdMap = client.getVolumeNameMap(result.getVolumeIdList());
                        ScaleIOVolume cloneInfo = cloneVolIdMap.get(clone.getNativeId());
                        long sizeInBytes = Long.parseLong(cloneInfo.getSizeInKb()) * 1024;
                        clone.setProvisionedCapacity(sizeInBytes);
                        clone.setDeviceLabel(cloneInfo.getName());
                        countSucc++;
                        log.info("Successfully create clone for volume {}:{} - end", clone.getDisplayName(),
                                clone.getParentId());
                    } catch (Exception e) {
                        log.error("Exception while creating clone for volume {}", clone.getParentId(), e);
                    }
                } else {
                    log.error("Exception while getting client instance for volume {}:{}", clone.getDisplayName(), clone.getParentId());
                    log.error("Exception while getting client instance for volume {}", clone.getStorageSystemId());
                }
            }
            setTaskStatus(clones.size(), countSucc, task);
        } else {
            log.error("Empty clone input List");
            task.setStatus(DriverTask.TaskStatus.FAILED);
        }
        task.setEndTime(Calendar.getInstance());
        log.info("Request to clone -- End ");
        return task;
    }

    /**
     * Detach volume clones.
     *
     * @param clones Type: Input/Output.
     * @return task
     */
    @Override
    public DriverTask detachVolumeClone(List<VolumeClone> clones) {
        log.info("Request to Detach Clone -- Start :");
        DriverTask task = new DriverTaskImpl(ScaleIOHelper.getTaskId(ScaleIOConstants.TaskType.CLONE_DETACH));
        int countSucc = 0;

        if (clones != null && clones.size() > 0) {
            for (VolumeClone clone : clones) {
                client = this.getClientBySystemId(clone.getStorageSystemId());
                try {
                    clone.setReplicationState(VolumeClone.ReplicationState.DETACHED);
                    countSucc++;
                    log.info("Successfully detached clone {}:{} - end", clone.getDisplayName(), clone.getNativeId());
                } catch (Exception e) {
                    log.error("Exception while detaching clone {}", clone.getNativeId(), e);
                }
            }
            setTaskStatus(clones.size(), countSucc, task);
        } else {
            log.error("Can't detach empty Clone list");
            task.setStatus(DriverTask.TaskStatus.FAILED);
        }
        task.setEndTime(Calendar.getInstance());
        log.info("Request to detach Clone -- End ");
        return task;
    }

    /**
     * Restore from clone.
     * <p/>
     * It is implementation responsibility to validate consistency of this operation when clones belong to consistency groups.
     *
     * @param clones Clones to restore from. Type: Input/Output.
     * @return task
     */
    @Override
    public DriverTask restoreFromClone(List<VolumeClone> clones) {
        return setUpNonSupportedTask(ScaleIOConstants.TaskType.CLONE_RESTORE);
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

    @Override
    public DriverTask createConsistencyGroupMirror(VolumeConsistencyGroup volumeConsistencyGroup, List<VolumeMirror> list,
            List<CapabilityInstance> list1) {
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

    @Override
    public DriverTask deleteConsistencyGroupMirror(List<VolumeMirror> list) {
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

    @Override
    public DriverTask restoreVolumeMirror(List<VolumeMirror> list) {
        return setUpNonSupportedTask(ScaleIOConstants.TaskType.MIRROR_OPERATIONS);
    }

    @Override
    public List<ITL> getITL(StorageSystem storageSystem, List<Initiator> list, MutableInt mutableInt) {
        return null;
    }

    /**
     * Export volumes to initiators through a given set of ports. If ports are not provided,
     * use port requirements from ExportPathsServiceOption storage capability
     *
     * @param initiators Type: Input.
     * @param volumes Type: Input.
     * @param volumeToHLUMap map of volume nativeID to requested HLU. HLU value of -1 means that HLU is not defined and will be assigned by
     *            array.
     *            Type: Input/Output.
     * @param recommendedPorts list of storage ports recommended for the export. Optional. Type: Input.
     * @param availablePorts list of ports available for the export. Type: Input.
     * @param capabilities storage capabilities. Type: Input.
     * @param usedRecommendedPorts true if driver used recommended and only recommended ports for the export, false otherwise. Type: Output.
     * @param selectedPorts ports selected for the export (if recommended ports have not been used). Type: Output.
     * @return task
     */
    @Override
    public DriverTask exportVolumesToInitiators(List<Initiator> initiators, List<StorageVolume> volumes,
            Map<String, String> volumeToHLUMap, List<StoragePort> recommendedPorts, List<StoragePort> availablePorts,
            StorageCapabilities capabilities, MutableBoolean usedRecommendedPorts, List<StoragePort> selectedPorts) {
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
        log.info("Create consistency group {}: ", consistencyGroup.getDisplayName());
        DriverTask task = new DriverTaskImpl(ScaleIOHelper.getTaskId(ScaleIOConstants.TaskType.CG_CREATE));
        consistencyGroup.setNativeId(consistencyGroup.getDisplayName());
        consistencyGroup.setDeviceLabel(consistencyGroup.getDisplayName());
        task.setStatus(DriverTask.TaskStatus.READY);
        task.setMessage("Set Fake CG native ID");
        return task;
    }

    /**
     * Delete block consistency group.
     *
     * @param consistencyGroup Input
     * @return task
     */
    @Override
    public DriverTask deleteConsistencyGroup(VolumeConsistencyGroup consistencyGroup) {
        log.info("Delete consistency group: {}", consistencyGroup.getNativeId());
        DriverTask task = new DriverTaskImpl(ScaleIOHelper.getTaskId(ScaleIOConstants.TaskType.CG_DELETE));
        task.setStatus(DriverTask.TaskStatus.READY);
        return task;

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
        if (ScaleIOHelper.isFromSameStorageSystem(snapshots)) {
            String systemId = snapshots.get(0).getStorageSystemId();
            ScaleIORestClient client = getClientBySystemId(systemId);
            DriverTask.TaskStatus taskStatus = createGroupSnapshot(client, snapshots);
            task.setStatus(taskStatus);
        } else {
            log.error("Snapshots are not from same storage system");
            task.setStatus(DriverTask.TaskStatus.FAILED);
        }
        task.setEndTime(Calendar.getInstance());
        log.info("Request to create consistency group snapshot -- End");
        return task;
    }

    private DriverTask.TaskStatus createGroupSnapshot(ScaleIORestClient client, List<VolumeSnapshot> snapshots) {
        String systemId = snapshots.get(0).getStorageSystemId();
        DriverTask.TaskStatus taskStatus = DriverTask.TaskStatus.FAILED;

        log.info("Create consistency group snapshot in storage system {} - Start:", systemId);
        try {
            Map<String, String> parent2snap = createSnapParentMap(snapshots);
            ScaleIOSnapshotVolumeResponse result = client.snapshotMultiVolume(parent2snap, systemId);
            taskStatus = populateSnapshotInfo(result, client, snapshots);
        } catch (Exception e) {
            log.error("createGroupSnapshot --  Failed to create CG snapshot for ScaleIO Storage System {}", systemId, e);
            taskStatus = DriverTask.TaskStatus.FAILED;
        } finally {
            log.info("Create consistency group snapshot in storage system {} - End:", systemId);
            return taskStatus;
        }
    }

    /**
     * Create a map that maps snapshot display name with its parent volume id
     * 
     * @param snapshots
     * @return return the map
     */
    private Map<String, String> createSnapParentMap(List<VolumeSnapshot> snapshots) {
        Map<String, String> parent2snap = new HashMap<>();
        for (VolumeSnapshot snapshot : snapshots) {
            parent2snap.put(snapshot.getParentId(), snapshot.getDisplayName());
        }
        return parent2snap;
    }

    /**
     * Populate snapshot info based on Rest request response
     * 
     * @param client ScaleIO Rest Client
     * @param result response for create group snapshot Rest call
     * @param snapshots snapshot list with only parent volume id info
     * @return task status
     */
    private DriverTask.TaskStatus populateSnapshotInfo(ScaleIOSnapshotVolumeResponse result, ScaleIORestClient client,
            List<VolumeSnapshot> snapshots) throws Exception {
        int countSucc = 0;
        List<String> SnapNativeIds = result.getVolumeIdList();
        Map<String, ScaleIOVolume> snapIdInfoMap = client.getSnapshotParentIdMap(SnapNativeIds);

        for (VolumeSnapshot snapshot : snapshots) {
            ScaleIOVolume snapInfo = snapIdInfoMap.get(snapshot.getParentId());
            if (snapInfo != null) {
                snapshot.setNativeId(snapInfo.getId());
                snapshot.setAccessStatus(StorageObject.AccessStatus.READ_WRITE);
                snapshot.setDeviceLabel(snapInfo.getName());
                snapshot.setConsistencyGroup(result.getSnapshotGroupId());
                snapshot.setSnapSetId(result.getSnapshotGroupId());
                countSucc++;
            }
        }
        return getTaskStatus(countSucc, snapshots.size());
    }

    /**
     * Get task status based on the number of successful tasks
     * 
     * @param countSucc the number of successful tasks
     * @param taskSize the number of total tasks
     * @return Task status
     */
    private DriverTask.TaskStatus getTaskStatus(int countSucc, int taskSize) {
        if (countSucc == taskSize) {
            return DriverTask.TaskStatus.READY;
        } else if (countSucc == 0) {
            return DriverTask.TaskStatus.FAILED;
        } else {
            return DriverTask.TaskStatus.PARTIALLY_FAILED;
        }
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
            ScaleIORestClient client = getClientBySystemId(systemId);
            String consistencyGroup = snapshots.get(0).getConsistencyGroup();
            DriverTask.TaskStatus removeTaskStatus = removeGroupSnapshot(client, consistencyGroup);
            task.setStatus(removeTaskStatus);
        } else {
            log.error("Snapshots are not from same consistency group");
            task.setStatus(DriverTask.TaskStatus.FAILED);
        }
        task.setEndTime(Calendar.getInstance());
        log.info("Request to delete consistency group snapshot -- End");
        return task;
    }

    /**
     * Rest call to remove consistency group snapshot
     * 
     * @param client ScaleIO Rest Client
     * @param consistencyGroup Consistency group id
     * @return Task status
     */
    private DriverTask.TaskStatus removeGroupSnapshot(ScaleIORestClient client, String consistencyGroup) {
        try {
            log.info("Rest Client Got! delete consistency group snapshot - Start:");
            client.removeConsistencyGroupSnapshot(consistencyGroup);
            log.info("Successfully delete consistency group snapshot - End:");
            return DriverTask.TaskStatus.READY;
        } catch (Exception e) {
            log.error("Exception while deleting consistency group snapshot", e);
            return DriverTask.TaskStatus.FAILED;
        }
    }

    /**
     * Create clone of consistency group.
     *
     * @param consistencyGroup input
     * @param clones output
     * @param capabilities Capabilities of clones. Type: Input.
     * @return task
     */
    @Override
    public DriverTask createConsistencyGroupClone(VolumeConsistencyGroup consistencyGroup, List<VolumeClone> clones,
            List<CapabilityInstance> capabilities) {
        log.info("Request to create consistency group clone -- Start :");
        DriverTask task = new DriverTaskImpl(ScaleIOHelper.getTaskId(ScaleIOConstants.TaskType.CG_CLONE_CREATE));
        int countSucc = 0;
        if (ScaleIOHelper.isFromSameStorageSystemClone(clones)) {
            if (consistencyGroup != null) {
                String systemId = clones.get(0).getStorageSystemId();
                log.info("Start to get Rest client for ScaleIO storage system: {}", systemId);
                ScaleIORestClient client = this.getClientBySystemId(systemId);
                if (client != null) {
                    try {
                        log.info("Rest Client Got! Create consistency group clone - Start:");
                        Map<String, String> parent2snap = new HashMap<>();
                        for (VolumeClone clone : clones) {
                            parent2snap.put(clone.getParentId(), clone.getDisplayName());
                        }
                        ScaleIOSnapshotVolumeResponse result = client.snapshotMultiVolume(parent2snap, systemId);
                        // get parentID
                        List<String> nativeIds = result.getVolumeIdList();
                        Map<String, ScaleIOVolume> cloneIdInfoMap = client.getSnapshotParentIdMap(nativeIds);
                        for (VolumeClone clone : clones) {
                            ScaleIOVolume cloneInfo = cloneIdInfoMap.get(clone.getParentId());
                            if (cloneInfo != null) {
                                clone.setNativeId(cloneInfo.getId());
                                clone.setAccessStatus(StorageObject.AccessStatus.READ_WRITE);
                                clone.setDeviceLabel(cloneInfo.getName());
                                String wwn = client.getSystemId() + clone.getNativeId();
                                clone.setWwn(wwn);
                                long sizeInBytes = Long.parseLong(cloneInfo.getSizeInKb()) * 1024;
                                clone.setProvisionedCapacity(sizeInBytes);
                                clone.setReplicationState(VolumeClone.ReplicationState.CREATED);
                                clone.setConsistencyGroup(result.getSnapshotGroupId());
                                countSucc++;
                            }
                        }
                        setTaskStatus(clones.size(), countSucc, task);
                        log.info("Create consistency group clone with group ID:{} - End:", consistencyGroup.getDisplayName());
                    } catch (Exception e) {
                        log.error("Exception while Creating consistency group clone in storage system: {}", systemId, e);
                        task.setStatus(DriverTask.TaskStatus.FAILED);
                    }
                } else {
                    log.error("Exception while getting Rest client instance for storage system {} ", systemId);
                    task.setStatus(DriverTask.TaskStatus.FAILED);
                }
            } else {
                log.error("Consistency group value is null");
            }
        } else {
            log.error("Clones are not from same storage system");
            task.setStatus(DriverTask.TaskStatus.FAILED);
        }
        task.setEndTime(Calendar.getInstance());
        log.info("Request to create consistency group clone -- End");
        return task;
    }

    /**
     *
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
     * @return
     */
    @Override
    public DriverTask discoverStorageSystem(List<StorageSystem> storageSystems) {
        DriverTask task = new DriverTaskImpl(ScaleIOHelper.getTaskId(ScaleIOConstants.TaskType.DISCOVER_STORAGE_SYSTEM));
        Set<StorageSystem.SupportedReplication> supportedReplications = new HashSet<>();
        supportedReplications.add(StorageSystem.SupportedReplication.elementReplica);
        supportedReplications.add(StorageSystem.SupportedReplication.groupReplica);
        for (StorageSystem storageSystem : storageSystems) {
            try {
                log.info("StorageDriver: Discovery information for storage system {}, name {} - Start", storageSystem.getIpAddress(),
                        storageSystem.getSystemName());
                ScaleIORestClient scaleIOHandle = scaleIORestHandleFactory.getClientHandle(storageSystem.getSystemName(),
                        storageSystem.getIpAddress(), storageSystem.getPortNumber(), storageSystem.getUsername(),
                        storageSystem.getPassword());
                if (scaleIOHandle != null) {
                    ScaleIOSystem scaleIOSystem = scaleIOHandle.getSystem();
                    List<ScaleIOProtectionDomain> protectionDomains = scaleIOHandle.getProtectionDomains();
                    for (ScaleIOProtectionDomain protectionDomain : protectionDomains) {
                        String domainName = protectionDomain.getName();
                        if (ScaleIOHelper.compare(domainName, storageSystem.getSystemName())) {
                            storageSystem.setNativeId(protectionDomain.getId());
                            storageSystem.setSystemName(domainName);
                            storageSystem.setSerialNumber(protectionDomain.getSystemId());
                            storageSystem.setSystemType(protectionDomain.getProtectionDomainState());
                            String version = scaleIOSystem.getVersion().replaceAll("_", ".")
                                    .substring(ScaleIOConstants.START_POS, ScaleIOConstants.END_POS);
                            storageSystem.setFirmwareVersion(version);
                            if (Double.valueOf(version) < (ScaleIOConstants.MINIMUM_SUPPORTED_VERSION)) {
                                storageSystem.setIsSupportedVersion(ScaleIOConstants.INCOMPATIBLE);
                            } else {
                                storageSystem.setIsSupportedVersion(ScaleIOConstants.COMPATIBLE);
                            }
                            storageSystem.setSupportedReplications(supportedReplications);
                            task.setStatus(DriverTask.TaskStatus.READY);
                            setConnInfoToRegistry(storageSystem.getNativeId(), storageSystem.getIpAddress(), storageSystem.getPortNumber(),
                                    storageSystem.getUsername(), storageSystem.getPassword());
                            log.info("StorageDriver: Discovery information for storage system {}, name {} - End",
                                    storageSystem.getIpAddress(), storageSystem.getSystemName());
                        }
                    }
                } else {
                    log.info("StorageDriver: Discovery failed to get an handle for the storage system {}, name {}",
                            storageSystem.getIpAddress(), storageSystem.getSystemName());
                    task.setStatus(DriverTask.TaskStatus.FAILED);
                }
            } catch (Exception e) {
                log.error("StorageDriver: Discovery failed for the storage system {}, name {}", storageSystem.getIpAddress(),
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
     * @return
     */
    @Override
    public DriverTask discoverStoragePools(StorageSystem storageSystem, List<StoragePool> storagePools) {
        DriverTask task = new DriverTaskImpl(ScaleIOHelper.getTaskId(ScaleIOConstants.TaskType.DISCOVER_STORAGE_POOLS));
        try {
            log.info("StorageDriver: Discovery of storage pools for storage system {}, name {} - Start", storageSystem.getIpAddress(),
                    storageSystem.getSystemName());
            ScaleIORestClient scaleIOHandle = getConnInfoFromRegistry(storageSystem);
            if (scaleIOHandle != null) {
                List<ScaleIOProtectionDomain> protectionDomains = scaleIOHandle.getProtectionDomains();
                for (ScaleIOProtectionDomain protectionDomain : protectionDomains) {
                    String domainID = protectionDomain.getId();
                    if (ScaleIOHelper.compare(domainID, storageSystem.getNativeId())) {
                        List<ScaleIOStoragePool> scaleIOStoragePoolList = scaleIOHandle
                                .getProtectionDomainStoragePools(protectionDomain
                                        .getId());
                        for (ScaleIOStoragePool storagePool : scaleIOStoragePoolList) {
                            StoragePool pool = new StoragePool();
                            pool.setNativeId(storagePool.getId());
                            log.info("StorageDriver: Discovered Pool {}, storageSystem {}", pool.getNativeId(),
                                    pool.getStorageSystemId());
                            pool.setStorageSystemId(protectionDomain.getId());
                            pool.setPoolName(storagePool.getName());
                            Set<StoragePool.Protocols> protocols = new HashSet<>();
                            protocols.add(StoragePool.Protocols.FC);
                            protocols.add(StoragePool.Protocols.iSCSI);
                            protocols.add(StoragePool.Protocols.ScaleIO);
                            pool.setProtocols(protocols);
                            pool.setPoolServiceType(StoragePool.PoolServiceType.block);
                            pool.setTotalCapacity(Long.valueOf(storagePool.getMaxCapacityInKb()));
                            pool.setFreeCapacity(Long.valueOf(storagePool.getCapacityAvailableForVolumeAllocationInKb()));
                            pool.setSupportedResourceType(StoragePool.SupportedResourceType.THIN_AND_THICK);
                            pool.setOperationalStatus(StoragePool.PoolOperationalStatus.READY);
                            pool.setMaximumThickVolumeSize(ScaleIOConstants.maxVolumeSize);
                            pool.setMaximumThinVolumeSize(ScaleIOConstants.maxVolumeSize);
                            pool.setMinimumThickVolumeSize(ScaleIOConstants.minVolumeSize);
                            pool.setMinimumThinVolumeSize(ScaleIOConstants.minVolumeSize);
                            Set<StoragePool.SupportedDriveTypes> supportedDriveTypes = new HashSet<>();
                            supportedDriveTypes.add(StoragePool.SupportedDriveTypes.FC);
                            supportedDriveTypes.add(StoragePool.SupportedDriveTypes.SATA);
                            pool.setSupportedDriveTypes(supportedDriveTypes);
                            storagePools.add(pool);
                        }
                    }
                }

                log.info("StorageDriver: Discovery of storage pools for storage system {}, name {} - End",
                        storageSystem.getIpAddress(),
                        storageSystem.getSystemName());
                task.setStatus(DriverTask.TaskStatus.READY);

            } else {
                log.info("StorageDriver: Failed to get an handle for the storage system {}, name {}", storageSystem.getIpAddress(),
                        storageSystem.getSystemName());
                task.setStatus(DriverTask.TaskStatus.FAILED);
            }
        } catch (Exception e) {
            log.error("StorageDriver: Discovery of storage pools failed for the storage system {}, name {}", storageSystem.getIpAddress(),
                    storageSystem.getSystemName());
            task.setStatus(DriverTask.TaskStatus.ABORTED);
        }
        return task;
    }

    /**
     * Discover storage ports and their capabilities
     *
     * @param storageSystem Type: Input.
     * @param storagePorts Type: Output.
     * @return
     */
    @Override
    public DriverTask discoverStoragePorts(StorageSystem storageSystem, List<StoragePort> storagePorts) {
        DriverTask task = new DriverTaskImpl(ScaleIOHelper.getTaskId(ScaleIOConstants.TaskType.DISCOVER_STORAGE_PORTS));
        try {
            log.info("StorageDriver: Discovery of storage ports for storage system {}, name {} - Start", storageSystem.getNativeId(),
                    storageSystem.getSystemName());
            ScaleIORestClient scaleIOHandle = getConnInfoFromRegistry(storageSystem);
            if (scaleIOHandle != null) {
                ScaleIOSystem sioSystem = scaleIOHandle.getSystem();
                String installationID = sioSystem.getInstallId();
                List<ScaleIOSDS> allSDSs = scaleIOHandle.queryAllSDS();
                for (ScaleIOSDS sds : allSDSs) {
                    StoragePort port;
                    String pdId = sds.getProtectionDomainId();
                    if (ScaleIOHelper.compare(pdId, storageSystem.getNativeId())) {
                        String sdsId = sds.getId();
                        List<ScaleIOSDS.IP> ips = sds.getIpList();
                        String sdsIP = null;
                        if (ips != null && !ips.isEmpty()) {
                            sdsIP = ips.get(0).getIp();
                        }

                        if (sdsId != null && ScaleIOHelper.compare(sds.getSdsState(), ScaleIOConstants.OPERATIONAL_STATUS_CONNECTED)) {
                            port = new StoragePort();
                            port.setNativeId(sdsId);
                            log.info("StorageDriver: Discovered port {}, storageSystem {}", port.getNativeId(), port.getStorageSystemId());
                            port.setDeviceLabel(String.format("%s-%s-StoragePort", sds.getName(), sdsId));
                            port.setPortName(sdsId);
                            port.setPortNetworkId(sdsId);
                            port.setNetworkId(installationID);
                            port.setStorageSystemId(storageSystem.getNativeId());
                            port.setTransportType(StoragePort.TransportType.ScaleIO);
                            port.setOperationalStatus(StoragePort.OperationalStatus.OK);
                            port.setIpAddress(sdsIP);
                            port.setPortGroup(sds.getProtectionDomainId());
                            port.setPortType(StoragePort.PortType.frontend);
                            storagePorts.add(port);
                        }
                    }
                }
                task.setStatus(DriverTask.TaskStatus.READY);
                log.info("StorageDriver: Discovery of storage ports for storage system {}, name {} - End", storageSystem.getIpAddress(),
                        storageSystem.getSystemName());
            } else {
                log.info("StorageDriver: Failed to get an handle for the storage system {}, name {}", storageSystem.getIpAddress(),
                        storageSystem.getSystemName());
                task.setStatus(DriverTask.TaskStatus.FAILED);
            }
        } catch (Exception e) {
            log.error("StorageDriver: Discovery of storage ports failed for the storage system {}, name {}", storageSystem.getIpAddress(),
                    storageSystem.getSystemName());
            task.setStatus(DriverTask.TaskStatus.ABORTED);
        }
        return task;
    }

    /**
     * Discover host components which are part of storage system
     *
     * @param storageSystem Type: Input.
     * @param embeddedStorageHostComponents Type: Output.
     * @return
     */
    @Override
    public DriverTask discoverStorageHostComponents(StorageSystem storageSystem, List<StorageHostComponent> embeddedStorageHostComponents) {
        DriverTask task = new DriverTaskImpl(ScaleIOHelper.getTaskId(ScaleIOConstants.TaskType.DISCOVER_STORAGE_HOSTS));
        try {
            log.info("StorageDriver: Discovery of storage hosts for storage system {}, name {} - Start", storageSystem.getNativeId(),
                    storageSystem.getSystemName());
            ScaleIORestClient scaleIOHandle = getConnInfoFromRegistry(storageSystem);
            if (scaleIOHandle != null) {
                List<ScaleIOSDC> allSDCs = scaleIOHandle.queryAllSDC();
                for (ScaleIOSDC sdc : allSDCs) {
                    StorageHostComponent host;
                    if (sdc != null) {
                        host = new StorageHostComponent();
                        host.setNativeId(sdc.getSdcIp());
                        host.setHostName(sdc.getSdcIp());
                        host.setDisplayName(sdc.getMdmConnectionState());
                        Initiator initiator = new Initiator();
                        initiator.setPort(sdc.getId());
                        initiator.setProtocol(Initiator.Protocol.ScaleIO);
                        Set<Initiator> iniSet = new HashSet<>();
                        iniSet.add(initiator);
                        host.setInitiators(iniSet);
                        embeddedStorageHostComponents.add(host);
                    }
                }
                task.setStatus(DriverTask.TaskStatus.READY);
                log.info("StorageDriver: Discovery of storage hosts for storage system {}, name {} - End", storageSystem.getIpAddress(),
                        storageSystem.getSystemName());
            } else {
                log.info("StorageDriver: Failed to get an handle for the storage system {}, name {}", storageSystem.getIpAddress(),
                        storageSystem.getSystemName());
                task.setStatus(DriverTask.TaskStatus.FAILED);
            }
        } catch (Exception e) {
            log.error("StorageDriver: Discovery of storage hosts failed for the storage system {}, name {}", storageSystem.getIpAddress(),
                    storageSystem.getSystemName());
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
     * Get info from registry
     *
     * @param systemNativeId system Native Id
     * @param attrName attribute name
     * @return List of values
     */
    public List<String> getInfoFromRegistry(String systemNativeId, String attrName) {
        Map<String, List<String>> attributes = this.driverRegistry.getDriverAttributesForKey(ScaleIOConstants.DRIVER_NAME, systemNativeId);
        if (attributes == null) {
            log.info("There is no attribute set up for " + systemNativeId + " in the registry");
            return null;
        } else if (attributes.get(attrName) == null) {
            log.info(attrName + "is not found in the registry");
            return null;
        } else {
            return attributes.get(attrName);
        }
    }

    /**
     * Get connection information from Registry
     *
     * @param systemNativeId
     * @param attrName
     * @return
     */
    public String getConnInfoFromRegistry(String systemNativeId, String attrName) {
        List<String> values = getInfoFromRegistry(systemNativeId, attrName);
        if (values != null) {
            return values.get(0);
        }
        return null;
    }

    /**
     * Store information into Registry
     *
     * @param systemNativeId
     * @param attributeKey
     * @param attributeValue
     */
    public void setInfoToRegistry(String systemNativeId, String attributeKey, String attributeValue) {
        Map<String, List<String>> attributes = this.driverRegistry.getDriverAttributesForKey(ScaleIOConstants.DRIVER_NAME, systemNativeId);
        List<String> values = new ArrayList<>();
        if (attributes != null) {
            if (attributes.get(attributeKey) != null) {
                values.addAll(attributes.get(attributeKey));
                if (!values.contains(attributeValue)) {
                    values.add(attributeValue);
                }
            } else {
                values.add(attributeValue);
            }
        } else {
            values.add(attributeValue);
        }

        this.driverRegistry.addDriverAttributeForKey(ScaleIOConstants.DRIVER_NAME, systemNativeId, attributeKey, values);
    }

    /**
     * Create attribute to store in Registry
     *
     * @param key key for attribute
     * @param value value for attribute
     * @return attribute
     */
    private Map<String, List<String>> createAttributesForRegistry(String key, String value) {
        Map<String, List<String>> attribute = new HashMap<>();
        List<String> valueList = new ArrayList<>();
        valueList.add(value);
        attribute.put(key, valueList);
        return attribute;
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
        attributes.putAll(createAttributesForRegistry(ScaleIOConstants.IP_ADDRESS, ipAddress));
        attributes.putAll(createAttributesForRegistry(ScaleIOConstants.PORT_NUMBER, Integer.toString(port)));
        attributes.putAll(createAttributesForRegistry(ScaleIOConstants.USER_NAME, username));
        attributes.putAll(createAttributesForRegistry(ScaleIOConstants.PASSWORD, password));

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
        log.info("get Rest client for ScaleIO storage system: {} -- Start", systemId);
        ip_address = getConnInfoFromRegistry(systemId, ScaleIOConstants.IP_ADDRESS);
        try {
            port = getConnInfoFromRegistry(systemId, ScaleIOConstants.PORT_NUMBER);
            username = getConnInfoFromRegistry(systemId, ScaleIOConstants.USER_NAME);
            password = getConnInfoFromRegistry(systemId, ScaleIOConstants.PASSWORD);
            client = scaleIORestHandleFactory.getClientHandle(systemId, ip_address, Integer.parseInt(port), username, password);
            log.info("get Rest client for ScaleIO storage system: {} -- End", systemId);
            return client;
        } catch (Exception e) {
            log.error("[Error] Failed to get Rest client for ScaleIO storage System: {}", systemId);
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

    /**
     * Get connection info from registry
     *
     * @param storageSystem
     * @return ScaleIO rest client handle
     */
    private ScaleIORestClient getConnInfoFromRegistry(StorageSystem storageSystem) {
        Map<String, List<String>> attributes = this.driverRegistry.getDriverAttributesForKey(ScaleIOConstants.DRIVER_NAME,
                storageSystem.getNativeId());
        String ipAddress, userName, password;
        int portNumber;
        ipAddress = getConnInfoFromRegistry(storageSystem.getNativeId(), ScaleIOConstants.IP_ADDRESS);
        portNumber = Integer.valueOf(getConnInfoFromRegistry(storageSystem.getNativeId(), ScaleIOConstants.PORT_NUMBER));
        userName = getConnInfoFromRegistry(storageSystem.getNativeId(), ScaleIOConstants.USER_NAME);
        password = getConnInfoFromRegistry(storageSystem.getNativeId(), ScaleIOConstants.PASSWORD);
        try {
            if (attributes == null || ipAddress.isEmpty() || userName.isEmpty() || password.isEmpty()) {
                log.info("StorageDriver: Connection info for " + storageSystem.getNativeId() + " is not set up in the registry");
                return null;
            } else {
                ScaleIORestClient scaleIOHandle = scaleIORestHandleFactory.getClientHandle(storageSystem.getNativeId(), ipAddress,
                        portNumber, userName, password);
                return scaleIOHandle;
            }
        } catch (Exception e) {
            log.error("StorageDriver: Failed to get client handle for the storage system {}, name {}", storageSystem.getIpAddress(),
                    storageSystem.getSystemName());
            return null;
        }
    }

}
