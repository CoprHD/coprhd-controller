/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver;

import java.util.List;
import java.util.Map;

import com.emc.storageos.storagedriver.model.StorageProvider;
import org.apache.commons.lang.mutable.MutableBoolean;
import org.apache.commons.lang.mutable.MutableInt;

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
     * Discover storage volumes
     * @param storageSystem  Type: Input.
     * @param storageVolumes Type: Output.
     * @param token used for paging. Input 0 indicates that the first page should be returned. Output 0 indicates
     *              that last page was returned. Type: Input/Output.
     * @return
     */
    public DriverTask getStorageVolumes(StorageSystem storageSystem, List<StorageVolume> storageVolumes, MutableInt token);

    /**
     * Get snapshots of the specified storage volume.
     * @param volume storage volume. Type: Input
     * @return snapshots list of snapshots of the storage volume.
     */
    public  List<VolumeSnapshot> getVolumeSnapshots(StorageVolume volume);

    /**
     * Get clones (full copies) of the specified volume.
     * @param volume storage volume. Type: Input
     * @return clones list of clones of the volume.
     */
    public  List<VolumeClone> getVolumeClones(StorageVolume volume);

    /**
     * Get mirrors (continuous copies) of the specified volume.
     * @param volume storage volume. Type: Input
     * @return mirrors list of mirrors of the volume.
     */
    public  List<VolumeMirror> getVolumeMirrors(StorageVolume volume);

    /**
     * Expand volume.
     * Before completion of the request, set all required data for expanded volume in "volume" parameter.
     * This includes update for capacity properties based on the new volume size:
     *                                         requestedCapacity, provisionedCapacity, allocatedCapacity.
     *
     * @param volume  Volume to expand. Type: Input/Output argument.
     * @param newCapacity  Requested capacity in bytes. Type: input argument.
     * @return task
     */
    public DriverTask expandVolume(StorageVolume volume, long newCapacity);
    
    /**
     * Stop Management of the storage system
     * 
     * @param Storage System to be detached.
     * @return task
     */
    public DriverTask stopManagement(StorageSystem storageSystem);

    /**
     * Delete volumes.
     * @param volume Volume to delete. Type: Input.
     * @return task
     */
    public DriverTask deleteVolume(StorageVolume volume);

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
     * Implementation should validate consistency of this operation for snapshots of volumes
     * in consistency group.
     * Implementation should check if parent volumes are part of consistency group and restore
     * all volumes in the consistency group to the same consistency group snapshot (as defined
     * in the snapshot consistency group property).
     * If parent volumes are not part of consistency group, restore only snapshots provided in the method.
     *
     * @param snapshots  Type: Input/Output.
     * @return task
     */
    public DriverTask restoreSnapshot(List<VolumeSnapshot> snapshots);

    /**
     * Delete snapshot.
     * @param snapshot snapshot to delete. Type: Input.

     * @return task
     */
    public DriverTask  deleteVolumeSnapshot(VolumeSnapshot snapshot);

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
     * This operation should transform clone(group of clones) to regular volume in regard to all following
     * api operations.
     *
     * It is implementation responsibility to validate consistency of this operation
     * when clones belong to consistency groups.
     *
     * @param clones Type: Input/Output.
     * @return task
     */
    public DriverTask detachVolumeClone(List<VolumeClone> clones);

    /**
     * Restore from clone.
     *
     * It is implementation responsibility to validate consistency of this operation
     * when clones belong to consistency groups.
     *
     * @param clones   Clones to restore from. Type: Input/Output.
     * @return task
     */
    public DriverTask restoreFromClone(List<VolumeClone> clones);

    /**
     * Delete volume clones.
     * Deprecated:
     * CoprHD uses detach clone followed delete volume requests to deleted volume clone.
     *
     * @param clone clone to delete. Type: Input.
     * @return
     */
    @Deprecated
    public DriverTask deleteVolumeClone(VolumeClone clone);

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
     * Creates consistency group mirror.
     * @param consistencyGroup consistency group to mirror. Type: Input.
     * @param mirrors volume mirrors to create. Type: Input/Output.
     * @param capabilities capabilities required for mirrors.
     * @return task
     */
    public DriverTask createConsistencyGroupMirror(VolumeConsistencyGroup consistencyGroup, List<VolumeMirror> mirrors,
                                                   List<CapabilityInstance> capabilities);

    /**
     * Delete mirrors.
     *
     * @param mirror mirror to delete. Type: Input.
     * @return task
     */
    public DriverTask deleteVolumeMirror(VolumeMirror mirror);

    /**
     * Delete mirrors of entire volume consistency group.
     * Implementation should validate consistency of this operation: all mirrors from the same consistency group
     * mirror set have to be specified in this call.
     * @param mirrors mirrors to delete. Type: Input/Output
     * @return task
     */
    public DriverTask deleteConsistencyGroupMirror(List<VolumeMirror> mirrors);
    
    /**
     * Add multiple volumes to a consistency group.
     * @param Volumes to be added to a consistency group
     * @param capabilities required for consitency groups.
     * @return task
     */
    public DriverTask addVolumesToConsistencyGroup( List<StorageVolume> volumes, StorageCapabilities capabilities);
    
    /**
     * Removes multiple volumes from a consistency group.
     * @param volumes to be delete from the consistency group.
     * @param capabilities for consistency group.
     * @return task
     */
    public DriverTask removeVolumesFromConsistencyGroup( List<StorageVolume> volumes, StorageCapabilities capabilities);

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
     * @param mirrors  Type: Input/Output
     * @return task
     */
    public DriverTask restoreVolumeMirror(List<VolumeMirror> mirrors);



    // Block Export operations

    /**
     * This method returns a map of Initiator-Target access information for a given volume.
     *
     * Key in the returned map is host FQDN, value: instance of HostExportInfo.
     * Each entry in the map represents access information from a host (key) to a given volume.
     * The entry value contains volume native id, list of host initiators with list of storage array ports which are
     * mapped and masked to access this volume on array.
     *
     * @param volume Storage volume. Type: Input.
     * @return Map of a host FQDN to initiator-target mapping info for the host (key) and the volume. Type: Output.
     */
    public Map<String, HostExportInfo> getVolumeExportInfoForHosts(StorageVolume volume);

    /**
     * This method returns a map of Initiator-Target access information for a given snapshot.
     *
     * Key in the returned map is host FQDN, value: instance of HostExportInfo.
     * Each entry in the map represents access information from a host (key) to a given snapshot.
     * The entry value contains snapshot native id, list of host initiators with list of storage array ports which are
     * mapped and masked to access this snapshot on array.
     *
     * @param snapshot Snapshot. Type: Input.
     * @return Map of a host FQDN to initiator-target mapping info for the host (key) and the snapshot. Type: Output.
     */
    public Map<String, HostExportInfo> getSnapshotExportInfoForHosts(VolumeSnapshot snapshot);

    /**
     * This method returns a map of Initiator-Target access information for a given clone.
     *
     * Key in the returned map is host FQDN, value: instance of HostExportInfo.
     * Each entry in the map represents access information from a host (key) to a given clone.
     * The entry value contains clone native id, list of host initiators with list of storage array ports which are
     * mapped and masked to access this clone on array.
     *
     * @param clone Snapshot. Type: Input.
     * @return Map of a host FQDN to initiator-target mapping info for the host (key) and the clone. Type: Output.
     */
    public Map<String, HostExportInfo> getCloneExportInfoForHosts(VolumeClone clone);

    /**
     * This method returns a map of Initiator-Target access information for a given mirror.
     *
     * Key in the returned map is host FQDN, value: instance of HostExportInfo.
     * Each entry in the map represents access information from a host (key) to a given mirror.
     * The entry value contains mirror native id, list of host initiators with list of storage array ports which are
     * mapped and masked to access this mirror on array.
     *
     * @param mirror Snapshot. Type: Input.
     * @return Map of a host FQDN to initiator-target mapping info for the host (key) and the mirror. Type: Output.
     */
    public Map<String, HostExportInfo> getMirrorExportInfoForHosts(VolumeMirror mirror);


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
     * @param consistencyGroup Type: input/output
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
     * Create mirror of consistency group.
     * @param consistencyGroup consistency group of parent volume. Type: Input.
     * @param snapshots   input/output parameter
     * @param capabilities Capabilities of snapshots. Type: Input.
     * @return
     */
    public DriverTask createConsistencyGroupSnapshot(VolumeConsistencyGroup consistencyGroup, List<VolumeSnapshot> snapshots,
                                                     List<CapabilityInstance> capabilities);

    /**
     * Delete consistency group snapshot.
     * @param snapshots  Input/Output.
     * @return
     */
    public DriverTask deleteConsistencyGroupSnapshot(List<VolumeSnapshot> snapshots);

    /**
     * Create clone of consistency group.
     * It is implementation responsibility to validate consistency of this group operation.
     *
     * @param consistencyGroup consistency group of parent volume. Type: Input.
     * @param clones input/output
     * @param capabilities Capabilities of clones. Type: Input.
     * @return
     */
    public DriverTask createConsistencyGroupClone(VolumeConsistencyGroup consistencyGroup, List<VolumeClone> clones,
                                                  List<CapabilityInstance> capabilities);

    /**
     * Validate connection to storage provider.
     *
     * @param storageProvider provider to validate connection to.
     * @return true/false
     */
    public boolean validateStorageProviderConnection(StorageProvider storageProvider);

}
