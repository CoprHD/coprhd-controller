package com.emc.storageos.storagedriver;

import com.emc.storageos.storagedriver.model.*;

import java.util.List;

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
     * @param capabilities Input argument for capabilities.
     * @return task
     */
    public DriverTask createVolumes(List<StorageVolume> volumes, List<CapabilityInstance> capabilities);

    /**
     * Expand volume.
     * Before completion of the request, set all required data for expanded volume in "volume" parameter.
     *
     * @param volume  Volume to expand. Type: Input argument.
     * @param newCapacity  Requested capacity. Type: input argument.
     * @param capabilities Input argument for capabilities.
     * @return task
     */
    public DriverTask expandVolume(StorageVolume volume, long newCapacity, List<CapabilityInstance> capabilities);

    /**
     * Delete volumes.
     * @param volumes Volumes to delete.
     * @param capabilities Input argument for capabilities.
     * @return
     */
    public DriverTask deleteVolumes(List<StorageVolume> volumes, List<CapabilityInstance> capabilities);

    // Block Snapshot operations

    /**
     * Create snapshots for a given list of volumes.
     *
     * @param volumes volumes to snap. Type: Input.
     * @param snapshots Type: Output.
     * @param capabilities
     * @return
     */
    public DriverTask createVolumeSnapshot(List<StorageVolume> volumes, List<VolumeSnapshot> snapshots, List<CapabilityInstance> capabilities);

    /**
     * Restore volume from snapshot.
     *
     * @param volume Type: Input/Output.
     * @param snapshot  Type: Input.
     * @param capabilities
     * @return
     */
    public DriverTask restoreFromSnapshot(StorageVolume volume, VolumeSnapshot snapshot, List<CapabilityInstance> capabilities);

    /**
     * Delete snapshots.
     * @param snapshots Type: Input.
     * @param capabilities
     * @return
     */
    public DriverTask deleteVolumeSnapshot(List<VolumeSnapshot> snapshots, List<CapabilityInstance> capabilities);

    // Block clone operations

    /**
     * Clone volumes.
     * @param volume  Type: Input.
     * @param clones  Type: Output.
     * @param capabilities
     * @return
     */
    public DriverTask createVolumeClone(List<StorageVolume> volume, List<VolumeClone> clones, List<CapabilityInstance> capabilities);

    /**
     * Detach volume clones.
     *
     * @param clones Type: Input/Output.
     * @param capabilities
     * @return
     */
    public DriverTask detachVolumeClone(List<VolumeClone> clones, List<CapabilityInstance> capabilities);

    /**
     * Restore from clone.
     *
     * @param volume  Type: Input.
     * @param clone   Type: Input.
     * @param capabilities
     * @return
     */
    public DriverTask restoreFromClone(StorageVolume volume, VolumeClone clone, List<CapabilityInstance> capabilities);

    /**
     * Delete volume clones.
     *
     * @param clones  Type: Input.
     * @param capabilities
     * @return
     */
    public DriverTask deleteVolumeClone(List<VolumeClone> clones, List<CapabilityInstance> capabilities);

    // Block Mirror operations

    /**
     * Create mirrors for a given list of volumes.
     *
     * @param volumes  Type: Input.
     * @param mirrors  Type: Output.
     * @param capabilities
     * @return
     */
    public DriverTask createVolumeMirror(List<StorageVolume> volumes, List<VolumeMirror> mirrors, List<CapabilityInstance> capabilities);

    /**
     * Delete mirrors.
     *
     * @param mirrors Type: Input.
     * @param capabilities
     * @return
     */
    public DriverTask deleteVolumeMirror(List<VolumeMirror> mirrors, List<CapabilityInstance> capabilities);

    /**
     * Split mirrors
     * @param mirrors  Type: Input/Output.
     * @param capabilities
     * @return
     */
    public DriverTask splitVolumeMirror(List<VolumeMirror> mirrors, List<CapabilityInstance> capabilities);

    /**
     * Resume mirrors after split
     *
     * @param mirrors  Type: Input/Output.
     * @param capabilities
     * @return
     */
    public DriverTask resumeVolumeMirror(List<VolumeMirror> mirrors, List<CapabilityInstance> capabilities);

    /**
     * Restore volume from a mirror
     *
     * @param volume  Type: Input/Output.
     * @param mirror  Type: Input.
     * @param capabilities
     * @return
     */
    public DriverTask restoreVolumeMirror(StorageVolume volume, VolumeMirror mirror, List<CapabilityInstance> capabilities);



    // Block Export operations
    /**
     * Get export masks for a given set of initiators.
     *
     * @param storageSystem Storage system to get ITLs from. Type: Input.
     * @param initiators Type: Input.
     * @return
     */
    public List<ITL> getITL(StorageSystem storageSystem, List<Initiator> initiators);

    /**
     * Export volumes to initiators through a given set of ports
     *
     * @param initiators Type: Input.
     * @param volumes    Type: Input.
     * @param recommendedPorts recommended list of ports.  Type: Input.
     * @param capabilities
     * @return
     */
    public DriverTask exportVolumesToInitiators(List<Initiator> initiators, List<StorageVolume> volumes, List<StoragePort> recommendedPorts,
                                                List<CapabilityInstance> capabilities);

    /**
     * Unexport volumes from initiators
     *
     * @param initiators  Type: Input.
     * @param volumes     Type: Input.
     * @return
     */
    public DriverTask unexportVolumesFromInitiators(List<Initiator> initiators, List<StorageVolume> volumes,
                                                    List<CapabilityInstance> capabilities);




}
