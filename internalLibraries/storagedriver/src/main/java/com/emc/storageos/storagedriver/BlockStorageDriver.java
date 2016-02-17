/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver;

import java.util.List;
import java.util.Map;

import com.emc.storageos.storagedriver.model.ITL;
import com.emc.storageos.storagedriver.model.Initiator;
import com.emc.storageos.storagedriver.model.StoragePort;
import com.emc.storageos.storagedriver.model.StorageSystem;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.model.VolumeClone;
import com.emc.storageos.storagedriver.model.VolumeConsistencyGroup;
import com.emc.storageos.storagedriver.model.VolumeMirror;
import com.emc.storageos.storagedriver.model.VolumeSnapshot;
import com.emc.storageos.storagedriver.storagecapabilities.CapabilityInstance;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;
import org.apache.commons.lang.mutable.MutableBoolean;

/**
 * BlockStorageDriver interface.
 * When method return DriverTask, there are two options for implementation --- blocking and non-blocking.
 * For non-blocking implementation, return task in intermediate state (QUEUED/PROVISIONING). Client will poll task until task
 * is set to one of terminal states or until polling timeout was reached.
 * For blocking implementation return task in one of terminal states.
 * When request execution is completed, set task to terminal state.
 * When client sees task in terminal state, client assumes that driver completed request and all required data is set
 * in Output arguments.
 *
 * When method is not supported, return DriverTask in FAILED state with message indicating that operation is not supported.
 *
 */
public interface BlockStorageDriver extends StorageDriver {

    // Block Volume operations

    /**
     * Create storage volumes with a given set of capabilities.
     * Before completion of the request, set all required data for provisioned volumes in "volumes" parameter.
     *
     * @param volumes Input/output argument for volumes.
     * @param capabilities Input argument for capabilities. Defines storage capabilities of volumes to create.
     * @return task
     */
    public DriverTask createVolumes(List<StorageVolume> volumes, StorageCapabilities capabilities);

    /**
     * Expand volume.
     * Before completion of the request, set all required data for expanded volume in "volume" parameter.
     *
     * @param volume  Volume to expand. Type: Input/Output argument.
     * @param newCapacity  Requested capacity. Type: input argument.
     * @return task
     */
    public DriverTask expandVolume(StorageVolume volume, long newCapacity);

    /**
     * Delete volumes.
     * @param volumes Volumes to delete.
     * @return task
     */
    public DriverTask deleteVolumes(List<StorageVolume> volumes);

    // Block Snapshot operations

    /**
     * Create volume snapshots.
     *
     * @param snapshots Type: Input/Output.
     * @param capabilities capabilities required from snapshots. Type: Input.
     * @return task
     */
    public DriverTask createVolumeSnapshot(List<VolumeSnapshot> snapshots, StorageCapabilities capabilities);

    /**
     * Restore volume to snapshot state.
     * Implementation should check if the volume is part of consistency group and restore
     * all volumes in the consistency group to the same consistency group snapshot (as defined
     * by the snapshot parameter).
     * If the volume is not part of consistency group, restore this volume to the snapshot.
     *
     * @param volume Type: Input/Output.
     * @param snapshot  Type: Input.
     * @return task
     */
    public DriverTask restoreSnapshot(StorageVolume volume, VolumeSnapshot snapshot);

    /**
     * Delete snapshots.
     * @param snapshots Type: Input.

     * @return task
     */
    public DriverTask  deleteVolumeSnapshot(List<VolumeSnapshot> snapshots);

    // Block clone operations

    /**
     * Clone volume clones.
     * @param clones  Type: Input/Output.
     * @param capabilities capabilities of clones. Type: Input.
     * @return task
     */
    public DriverTask createVolumeClone(List<VolumeClone> clones, StorageCapabilities capabilities);

    /**
     * Detach volume clones.
     *
     * @param clones Type: Input/Output.
     * @return task
     */
    public DriverTask detachVolumeClone(List<VolumeClone> clones);

    /**
     * Restore from clone.
     *
     * @param volume  Type: Input/Output.
     * @param clone   Type: Input.
     * @return task
     */
    public DriverTask restoreFromClone(StorageVolume volume, VolumeClone clone);

    /**
     * Delete volume clones.
     *
     * @param clones clones to delete. Type: Input.
     * @return
     */
    public DriverTask deleteVolumeClone(List<VolumeClone> clones);

    // Block Mirror operations

    /**
     * Create volume mirrors.
     *
     * @param mirrors  Type: Input/Output.
     * @param capabilities capabilities of mirrors. Type: Input.
     * @return task
     */
    public DriverTask createVolumeMirror(List<VolumeMirror> mirrors, StorageCapabilities capabilities);

    /**
     * Delete mirrors.
     *
     * @param mirrors mirrors to delete. Type: Input.
     * @return task
     */
    public DriverTask deleteVolumeMirror(List<VolumeMirror> mirrors);

    /**
     * Split mirrors
     * @param mirrors  Type: Input/Output.
     * @return task
     */
    public DriverTask splitVolumeMirror(List<VolumeMirror> mirrors);

    /**
     * Resume mirrors after split
     *
     * @param mirrors  Type: Input/Output.
     * @return task
     */
    public DriverTask resumeVolumeMirror(List<VolumeMirror> mirrors);

    /**
     * Restore volume from a mirror
     *
     * @param volume  Type: Input/Output.
     * @param mirror  Type: Input.
     * @return task
     */
    public DriverTask restoreVolumeMirror(StorageVolume volume, VolumeMirror mirror);



    // Block Export operations
    /**
     * Get export masks for a given set of initiators.
     *
     * @param storageSystem Storage system to get ITLs from. Type: Input.
     * @param initiators Type: Input.
     * @return list of export masks
     */
    public List<ITL> getITL(StorageSystem storageSystem, List<Initiator> initiators);

    /**
     * Export volumes to initiators through a given set of ports. If ports are not provided,
     * use port requirements from ExportPathsServiceOption storage capability
     *
     * @param initiators Type: Input.
     * @param volumes    Type: Input.
     * @param volumeToHLUMap map of volume nativeID to requested HLU. HLU value of -1 means that HLU is not defined and will be assigned by array.
     *                       Type: Input/Output.
     * @param recommendedPorts list of storage ports recommended for the export. Optional. Type: Input.
     * @param availablePorts list of ports available for the export. Type: Input.
     * @param capabilities storage capabilities. Type: Input.
     * @param usedRecommendedPorts true if driver used recommended and only recommended ports for the export, false otherwise. Type: Output.
     * @param selectedPorts ports selected for the export (if recommended ports have not been used). Type: Output.
     * @return task
     */
    public DriverTask exportVolumesToInitiators(List<Initiator> initiators, List<StorageVolume> volumes, Map<String, String> volumeToHLUMap, List<StoragePort> recommendedPorts,
                                                List<StoragePort> availablePorts, StorageCapabilities capabilities, MutableBoolean usedRecommendedPorts,
                                                List<StoragePort> selectedPorts);


    /**
     * Unexport volumes from initiators
     *
     * @param initiators  Type: Input.
     * @param volumes     Type: Input.
     * @return task
     */
    public DriverTask unexportVolumesFromInitiators(List<Initiator> initiators, List<StorageVolume> volumes);

    // Consistency group operations.
    /**
     * Create block consistency group.
     * @param consistencyGroup input/output
     * @return
     */
    public DriverTask createConsistencyGroup(VolumeConsistencyGroup consistencyGroup);

    /**
     * Delete block consistency group.
     * @param consistencyGroup Input
     * @return
     */
    public DriverTask deleteConsistencyGroup(VolumeConsistencyGroup consistencyGroup);

    /**
     * Create snapshot of consistency group.
     * @param consistencyGroup input parameter
     * @param snapshots   input/output parameter
     * @param capabilities Capabilities of snapshots. Type: Input.
     * @return
     */
    public DriverTask createConsistencyGroupSnapshot(VolumeConsistencyGroup consistencyGroup, List<VolumeSnapshot> snapshots,
                                                     List<CapabilityInstance> capabilities);

    /**
     * Delete snapshot.
     * @param snapshots  Input.
     * @return
     */
    public DriverTask deleteConsistencyGroupSnapshot(List<VolumeSnapshot> snapshots);

    /**
     * Create clone of consistency group.
     * @param consistencyGroup input/output
     * @param clones output
     * @param capabilities Capabilities of clones. Type: Input.
     * @return
     */
    public DriverTask createConsistencyGroupClone(VolumeConsistencyGroup consistencyGroup, List<VolumeClone> clones,
                                                     List<CapabilityInstance> capabilities);

    /**
     * Delete consistency group clone
     * @param clones  output
     * @return
     */
    public DriverTask deleteConsistencyGroupClone(List<VolumeClone> clones);


}
