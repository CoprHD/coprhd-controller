/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import java.net.URI;
import java.util.List;

import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyManager;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.NativeContinuousCopyCreate;
import com.emc.storageos.model.block.VirtualPoolChangeParam;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.model.systems.StorageSystemConnectivityList;
import com.emc.storageos.model.vpool.VirtualPoolChangeList;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

/**
 * Interface for block service calls that service larger requests, such as
 * creating a volume.
 */
public interface BlockServiceApi {

    /**
     * Define the default BlockServiceApi implementation.
     */
    public static final String DEFAULT = "default";

    public static final String CONTROLLER_SVC = "controllersvc";
    public static final String CONTROLLER_SVC_VER = "1";
    public static final String EVENT_SERVICE_TYPE = "block";

    /**
     * Create volumes
     * 
     * @param param The volume creation post parameter
     * @param project project requested
     * @param varray source VirtualArray
     * @param vpool VirtualPool requested
     * @param recommendations Placement recommendation object
     * @param taskList list of tasks for source volumes
     * @param task task ID
     * @param vpoolCapabilities wrapper for vpool params
     * @return TaskList
     * 
     * @throws InternalException
     */
    public TaskList createVolumes(VolumeCreate param, Project project,
            VirtualArray varray, VirtualPool vpool, List<Recommendation> recommendations,
            TaskList taskList, String task, VirtualPoolCapabilityValuesWrapper vpoolCapabilities)
            throws InternalException;

    /**
     * Delete the passed volumes for the passed system.
     * 
     * @param systemURI URI of the system owing the volumes.
     * @param volumeURIs The URIs of the volumes to be deleted.
     * @param deletionType The type of deletion to perform.
     * @param task The task identifier.
     * 
     * @throws InternalException
     */
    public void deleteVolumes(URI systemURI, List<URI> volumeURIs, String deletionType,
            String task) throws InternalException;

    /**
     * Check if a resource can be deactivated safely.
     * 
     * @return detail type of the dependency if exist, null otherwise
     * 
     * @throws InternalException
     */
    public <T extends DataObject> String checkForDelete(T object) throws InternalException;

    /**
     * Attaches and starts new continuous copies for the given volume.
     * 
     * @param storageSystem
     * @param sourceVolume
     * @param sourceVpool
     * @param capabilities
     * @param param
     * @param task
     * 
     * @return
     * 
     * @throws ControllerException
     */
    public TaskList startNativeContinuousCopies(StorageSystem storageSystem,
            Volume sourceVolume, VirtualPool sourceVpool,
            VirtualPoolCapabilityValuesWrapper capabilities,
            NativeContinuousCopyCreate param, String task) throws ControllerException;

    /**
     * For each continuous copy on the given volume, if any, detach them and
     * promote them to regular block volumes. If mirrors is null, assume all
     * copies are to be detached.
     * 
     * @param storageSystem
     * @param sourceVolume
     * @param mirrors
     * @param taskId
     * 
     * @return
     * 
     * @throws ControllerException
     */
    public TaskList stopNativeContinuousCopies(StorageSystem storageSystem,
            Volume sourceVolume, List<URI> mirrors, String taskId) throws ControllerException;

    /**
     * Pause a volume mirror
     * 
     * @param storageSystem StorageSystem requested
     * @param blockMirrors BlockMirror instance to be paused
     * @param sync Boolean flag for pause operation; true=split, false=fracture
     * @param taskId Task ID
     * 
     * @return TaskList resource
     * 
     * @throws ControllerException
     */
    public TaskList pauseNativeContinuousCopies(StorageSystem storageSystem,
            Volume sourceVolume, List<BlockMirror> blockMirrors, Boolean sync, String taskId)
            throws ControllerException;

    /**
     * For each continuous copy on the given volume, resume them.
     * 
     * @param storageSystem
     * @param sourceVolume
     * @param blockMirrors
     * @param taskId
     * 
     * @return TaskList
     * 
     * @throws ControllerException
     */
    public TaskList resumeNativeContinuousCopies(StorageSystem storageSystem,
            Volume sourceVolume, List<BlockMirror> blockMirrors, String taskId)
            throws ControllerException;

    /**
     * Establish group relation between volume group and native continuous copy group.
     *
     * @param storageSystem the storage system
     * @param sourceVolume the source volume
     * @param blockMirror the block mirror
     * @param taskId the task id
     * @return the task resource rep
     * @throws ControllerException the controller exception
     */
    public TaskResourceRep establishVolumeAndNativeContinuousCopyGroupRelation(
            StorageSystem storageSystem, Volume sourceVolume,
            BlockMirror blockMirror, String taskId) throws ControllerException;

    /**
     * Establish group relation between volume group and snapshot group.
     *
     * @param storageSystem the storage system
     * @param sourceVolume the source volume
     * @param snapshot the block snapshot
     * @param taskId the task id
     * @return the task resource rep
     * @throws ControllerException the controller exception
     */
    public TaskResourceRep establishVolumeAndSnapshotGroupRelation(
            StorageSystem storageSystem, Volume sourceVolume,
            BlockSnapshot snapshot, String taskId) throws ControllerException;

    /**
     * Deactivate a volume mirror This operation will attempt to both detach and
     * delete the desired volume mirror, which should already be in a fractured
     * state.
     * 
     * @param device StorageSystem requested
     * @param mirrorURI URI of mirror to be deactivated
     * @param task Task ID
     * 
     * @return TaskList
     */
    public TaskList deactivateMirror(StorageSystem device, URI mirrorURI,
            String task) throws ControllerException;

    /**
     * Gets the list of potential vpools for a vpool change for the passed volume.
     * 
     * @param volume The volume for which a vpool change is proposed.
     */
    public VirtualPoolChangeList getVirtualPoolForVirtualPoolChange(Volume volume);

    /**
     * Defines the API to change the vpool for the passed volume to the passed
     * vpool.
     * 
     * @param systemURI URI of the storage system on which the volume resides.
     * @param volume A reference to the volume.
     * @param targetVpool A reference to the new vpool.
     * @param vpoolChangeParam vpool change request
     * @param taskId The task identifier.
     * 
     * @throws InternalException
     */
    public void changeVolumeVirtualPool(URI systemURI, Volume volume,
            VirtualPool targetVpool, VirtualPoolChangeParam vpoolChangeParam, String taskId)
            throws InternalException;

    /**
     * Defines the API to change the vpool for the passed volumes to the passed
     * vpool.
     * 
     * @param volumes List of volumes.
     * @param targetVpool A reference to the new vpool.
     * @param vpoolChangeParam vpool change request
     * @param taskId The task identifier.
     * @throws InternalException the internal exception
     */
    public void changeVolumeVirtualPool(List<Volume> volumes,
            VirtualPool targetVpool, VirtualPoolChangeParam vpoolChangeParam, String taskId)
            throws InternalException;

    /**
     * Defines the API to change the varray for the passed volumes to the passed
     * varray.
     * 
     * @param volume A list of volume.
     * @param cg A reference to the volume's consistency group, or null.
     * @param cgVolumes List of volume in the CG when not null.
     * @param varray A reference to the new varray.
     * @param taskId The task identifier.
     * 
     * @throws InternalException
     */
    public void changeVirtualArrayForVolumes(List<Volume> volume,
            BlockConsistencyGroup cg, List<Volume> cgVolumes, VirtualArray varray,
            String taskId) throws InternalException;

    /**
     * Determines whether or not the virtual array for the passed volume can be
     * changed to the passed virtual array. Throws a APIException when the
     * varray change is not supported.
     * 
     * @param volume A reference to the volume.
     * @param newVarray A reference to the new varray.
     * 
     * @throws APIException
     */
    public void verifyVarrayChangeSupportedForVolumeAndVarray(Volume volume,
            VirtualArray newVarray) throws APIException;

    /**
     * Returns the connectivity for protection, vplex, etc. for various StorageSystems.
     * 
     * @param storageSystem The StorageSystem object
     * 
     * @return
     */
    public StorageSystemConnectivityList getStorageSystemConnectivity(
            StorageSystem storageSystem);

    /**
     * Delete an existing consistency group
     * 
     * @param device StorageSystem requested
     * @param consistencyGroup BlockConsistencyGroup instance to be deleted
     * @param task Operation
     * 
     * @return
     */
    public TaskResourceRep deleteConsistencyGroup(StorageSystem device,
            BlockConsistencyGroup consistencyGroup, String task) throws ControllerException;

    /**
     * Updates the consistency to add/remove the passed volumes to/from the
     * consistency group.
     * 
     * @param cgStorageSystem A reference to the CG storage system.
     * @param cgVolumes The active consistency group volumes
     * @param consistencyGroup The consistency group to be modified.
     * @param addVolumesList The list of volumes to be added.
     * @param removeVolumesList The list of volumes to be removed.
     * @param taskId The task identifier.
     * 
     * @return A reference to the task response.
     * 
     * @throws ControllerException When an error occurs in the controller
     *             configuring the workflow to update the consistency group.
     */
    public TaskResourceRep updateConsistencyGroup(StorageSystem cgStorageSystem,
            List<Volume> cgVolumes, BlockConsistencyGroup consistencyGroup,
            List<URI> addVolumesList, List<URI> removeVolumesList, String taskId)
            throws ControllerException;

    /**
     * Verify the passed volume is capable of being expanded to the passed size.
     * 
     * @param volume The volume whose capacity to expand.
     * @param newSize The desired new volume size.
     */
    public void verifyVolumeExpansionRequest(Volume volume, long newSize);

    /**
     * Expand the capacity of the passed volume to the passed size.
     * 
     * @param volume The volume whose capacity to expand.
     * @param newSize The desired new volume size.
     * @param taskId The id of the volume expansion task.
     * 
     * @throws ControllerException
     */
    public void expandVolume(Volume volume, long newSize, String taskId)
            throws InternalException;

    /**
     * Validates a snapshot request.
     * 
     * @param reqVolume The volume from the snapshot request.
     * @param volumesToSnap The volumes for which snapshots will be created.
     *            Could be more than the requested due to CGs.
     * @param snapshotType The snapshot technology type.
     * @param snapshotName The snapshot name.
     * @param A reference to the block full copy manager.
     */
    public void validateCreateSnapshot(Volume reqVolume, List<Volume> volumesToSnap,
            String snapshotType, String snapshotName, BlockFullCopyManager fcManager);

    /**
     * When a request is made to create a snapshot for a specific volume, this
     * function should determine all volumes that should be snapped as a result
     * of that request. For example, if a request is made to create a snapshot
     * for a VMAX/VNX volume, and that volume is in a consistency group, all
     * volumes will be snapped.
     * 
     * @param reqVolume The volume for which the snapshot is requested.
     * 
     * @return A list of all the volumes that should be snapped as a result of a
     *         snapshot request for a specific volume.
     */
    public List<Volume> getVolumesToSnap(Volume reqVolume, String snapshotType);

    /**
     * Gets the active volumes for the consistency group.
     * 
     * @param cg The consistency group.
     * 
     * @return A list of all the active volumes for the passed consistency group.
     */
    public List<Volume> getActiveCGVolumes(BlockConsistencyGroup cg);

    /**
     * Prepares the snapshots for a snapshot request.
     * 
     * @param volumes The volumes for which snapshots are to be created.
     * @param snapShotType The snapshot technology type.
     * @param snapshotName The snapshot name.
     * @param snapshotURIs [OUT] The URIs for the prepared snapshots.
     * @param taskId The unique task identifier
     * 
     * @return The list of snapshots
     */
    public List<BlockSnapshot> prepareSnapshots(List<Volume> volumes, String snapShotType,
            String snapshotName, List<URI> snapshotURIs, String taskId);

    /**
     * Uses the appropriate controller to create the snapshots.
     * 
     * @param reqVolume The volume from the snapshot request.
     * @param snapshotURIs The URIs of the prepared snapshots
     * @param snapshotType The snapshot technology type.
     * @param createInactive true if the snapshots should be created but not
     *            activated, false otherwise.
     * @param readOnly true if the snapshot should be read only, false otherwise
     * @param taskId The unique task identifier.
     */
    public void createSnapshot(Volume reqVolume, List<URI> snapshotURIs,
            String snapshotType, Boolean createInactive, Boolean readOnly, String taskId);

    /**
     * Uses the appropriate controller to delete the snapshot.
     * 
     * @param snapshot The snapshot to delete
     * @param taskId The unique task identifier
     */
    public void deleteSnapshot(BlockSnapshot snapshot, String taskId);

    /**
     * Get the snapshots for the passed volume.
     * 
     * @param volume A reference to a volume.
     * 
     * @return The snapshots for the passed volume.
     */
    public List<BlockSnapshot> getSnapshots(Volume volume);

    /**
     * Validates a restore snapshot request.
     * 
     * @param snapshot The snapshot to restore.
     * @param volume
     */
    public void validateRestoreSnapshot(BlockSnapshot snapshot, Volume volume);

    /**
     * Validates a resynchronize snapshot request.
     * 
     * @param snapshot The snapshot to resynchronize.
     * @param volume
     */
    public void validateResynchronizeSnapshot(BlockSnapshot snapshot, Volume volume);

    /**
     * Restore the passed parent volume from the passed snapshot of that parent volume.
     * 
     * @param snapshot The snapshot to restore
     * @param parentVolume The volume to be restored.
     * @param taskId The unique task identifier.
     */
    public void restoreSnapshot(BlockSnapshot snapshot, Volume parentVolume, String taskId);

    /**
     * Resynchronize the passed snapshot.
     * 
     * @param snapshot The snapshot to be resynchronized
     * @param parentVolume The volume to resynchronize from.
     * @param taskId The unique task identifier.
     */
    public void resynchronizeSnapshot(BlockSnapshot snapshot, Volume parentVolume, String taskId);

    /**
     * Returns the maximum number of volumes that are allowed in the passed consistency group.
     * 
     * @param consistencyGroup A reference to the consistency group.
     * 
     * @return The maximum number of volumes that are allowed in the group.
     */
    public int getMaxVolumesForConsistencyGroup(BlockConsistencyGroup consistencyGroup);

    /**
     * Verifies that the name of the CG is valid for the system on which
     * it will be created. Throws an exception when the name is not valid.
     * 
     * @param consistencyGroup A reference to the consistency group.
     */
    public void validateConsistencyGroupName(BlockConsistencyGroup consistencyGroup);

    /**
     * Verifies that the volume can be removed from the CG.
     * 
     * @param volume A reference to the volume
     * @param cgVolumes The active volumes in the CG
     */
    public void verifyRemoveVolumeFromCG(Volume volume, List<Volume> cgVolumes);

    /**
     * Verifies that the volume can be added to the CG.
     * 
     * @param volume A reference to the volume
     * @param cg A reference to the CG
     * @param cgVolumes The active volumes in the CG
     * @param cgStorageSystem A reference to the CG storage controller.
     */
    public void verifyAddVolumeToCG(Volume volume, BlockConsistencyGroup cg,
            List<Volume> cgVolumes, StorageSystem cgStorageSystem);

    /**
     * Verifies replica count of the volumes to be added to CG.
     *
     * @param volumes List of volumes
     * @param cgVolumes List of active volumes in the CG
     * @param volsAlreadyInCG Volumes to be added are the same with those already in CG
     */
    public void verifyReplicaCount(List<Volume> volumes, List<Volume> cgVolumes, boolean volsAlreadyInCG);
}
