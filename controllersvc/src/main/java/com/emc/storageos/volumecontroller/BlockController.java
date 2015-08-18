/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

/**
 * Main block volume controller interfaces.
 * 
 * URI volume: The following information will be available from volume URI lookup.
 * name: Friendly name of the volume.
 * capacity: Size of the volume.
 * thinProvision: Whether volume is thinly provisioned.
 * cosParams: Class-of-service parameters specified as one or more key-value pairs.
 * TBD: list COS parameters for supported storage controllers
 * 
 * URI snapshot: Snapshot and volumes are identical from storage controller perspective (for now).
 */
public interface BlockController extends BlockStorageManagementController {

    /**
     * Creates one or more volumes in the same storage pool. Volume objects are
     * in pending state. Block controller is responsible for changing volume
     * object state for all volumes to ready to signal volume creation
     * completion.
     * 
     * @param storage URI of storage controller.
     * @param pool URI of pool where the volume belongs.
     * @param volumeURIs The URIs of the volumes to be created.
     * @param opId The unique operation identifier.
     * @param capabilitiesValues wrapper around virtual pool attribute values
     * 
     * @throws InternalException When an exception occurs creating the volumes.
     */
    public void createVolumes(URI storage, URI pool, List<URI> volumeURIs,
            VirtualPoolCapabilityValuesWrapper capabilitiesValues, String opId) throws InternalException;

    /**
     * Create meta volumes.
     * 
     * @param storage URI of storage controller.
     * @param pool URI of pool where the volume belongs.
     * @param volumeURIs The URIs of the volumes to be created.
     * @param opId The unique operation identifier.
     * @param capabilitiesValues wrapper around virtual pool attribute values
     * 
     * @throws InternalException When an exception occurs creating the volumes.
     */
    public void createMetaVolumes(URI storage, URI pool, List<URI> volumeURIs,
            VirtualPoolCapabilityValuesWrapper capabilitiesValues, String opId) throws InternalException;

    /**
     * Create meta volume.
     * 
     * @param storage URI of storage controller.
     * @param pool URI of pool where the volume belongs.
     * @param volumeURI The URI of the volume to be created.
     * @param opId The unique operation identifier.
     * @param capabilitiesValues wrapper around virtual pool attribute values
     * 
     * @throws InternalException When an exception occurs creating the volumes.
     */
    public void createMetaVolume(URI storage, URI pool, URI volumeURI,
            VirtualPoolCapabilityValuesWrapper capabilitiesValues, String opId) throws InternalException;

    /**
     * WF for block volume expand request.
     * 
     * @param storage URI of storage controller.
     * @param pool URI of pool where the volume belongs.
     * @param volume URI of volume to be created.
     * @param size new volume size.
     * @param opId
     * @throws InternalException When an exception occurs expanding the volume
     */
    public void expandBlockVolume(URI storage, URI pool, URI volume, Long size, String opId) throws InternalException;

    /**
     * Block volume expand request.
     * 
     * @param storage URI of storage controller.
     * @param pool URI of pool where the volume belongs.
     * @param volume URI of volume to be created.
     * @param size new volume size.
     * @param opId
     * @throws InternalException When an exception occurs expanding the volume
     */
    public void expandVolume(URI storage, URI pool, URI volume, Long size, String opId) throws InternalException;

    /**
     * Block volume or snapshot delete. Deletes one or more volumes on
     * the same storage system.
     * 
     * @param storage URI of storage controller.
     * @param volumeURIs URIs of the volumes or snapshots being deleted.
     * @param opId The unique operation id.
     * 
     * @throws InternalException When an exception occurs deleting the volumes.
     */
    public void deleteVolumes(URI storage, List<URI> volumeURIs, String opId) throws InternalException;

    /**
     * Create a snapshot or snapshots of a volume or volumes.
     * Open issue: need to allow snapshots with different CoS & pool
     * 
     * @param storage URI of the storage controller.
     * @param snapshotList URIs of the snapshots.
     * @param createInactive Create the snapshot, but do not activate it (if supported by array)
     * @param opId Operation ID.
     * @throws InternalException When an exception occurs creating the snapshot
     */
    public void createSnapshot(URI storage, List<URI> snapshotList, Boolean createInactive, String opId) throws InternalException;

    /**
     * This interface is for the snapshot active. The createSnapshot may have done
     * whatever is necessary to setup the snapshot for this call. The goal is to
     * make this a quick operation and the create operation has already done a lot
     * of the "heavy lifting".
     * 
     * @param storage URI of the storage controller.
     * @param snapshotList URIs of the snapshots.
     * @param opId Operation ID.
     * @throws InternalException When an exception occurs activating the snapshot
     */
    public void activateSnapshot(URI storage, List<URI> snapshotList, String opId) throws InternalException;

    /**
     * Delete a snapshot or snapshots of a volume or volumes.
     * Open issue: need to allow snapshots with different CoS & pool
     * 
     * @param storage URI of the storage controller.
     * @param snapshotList URIs of the snapshots.
     * @param opId Operation ID.
     * @throws InternalException When an exception occurs deleting the snapshot
     */
    public void deleteSnapshot(URI storage, URI snapshot, String opId) throws InternalException;

    /**
     * Restore contents of a volume from a given snapshot.
     * 
     * @param storage URI of storage controller.
     * @param pool URI of pool where the volume belongs.
     * @param volume URI of volume to be restored.
     * @param snapshot URI of snapshot used for restoration.
     * @param opId Operation ID
     * @throws InternalException When an exception occurs restoring the volume
     */
    public void restoreVolume(URI storage, URI pool, URI volume, URI snapshot, Boolean updateOpStatus, String opId)
            throws InternalException;

    /**
     * Resynchronize clone from its source volume
     * 
     * @param storage
     * @param clones
     * @param updateOpStatus
     * @param opId
     * @throws InternalException
     */
    public void resyncFullCopy(URI storage, List<URI> clones, Boolean updateOpStatus, String opId) throws InternalException;

    /**
     * Restore contents of a volume from full copies.
     * 
     * @param storage URI of storage controller.
     * @param clones list of URI of clone used for restoration.
     * @param opId Operation ID
     * @throws InternalException When an exception occurs restoring the volume
     */
    public void restoreFromFullCopy(URI storage, List<URI> clones, Boolean updateOpStatus, String opId) throws InternalException;

    /**
     * Create a mirror of a volume
     * 
     * @param storage URI of storage controller
     * @param mirror URI of block mirror
     * @param createInactive value of WaitForCopyState
     * @param opId Operation ID
     * @throws InternalException When an exception occurs creating the mirror
     */
    public void createMirror(URI storage, URI mirror, Boolean createInactive, String opId) throws InternalException;

    /**
     * Attach new mirror(s) for the given volume
     * 
     * @param storage
     * @param sourceVolume
     * @param opId
     * @throws InternalException
     */
    public void attachNativeContinuousCopies(URI storage, URI sourceVolume, String opId) throws InternalException;

    /**
     * Detach the given mirrors
     * 
     * @param storage
     * @param mirrors
     * @param promotees
     * @param opId
     * @throws InternalException
     */
    public void detachNativeContinuousCopies(URI storage, List<URI> mirrors, List<URI> promotees,
            String opId) throws InternalException;

    /**
     * Fracture a mirror or mirrors of a volume or volumes.
     * 
     * @param storage URI of storage controller.
     * @param mirrors List of block mirror URI's
     * @param opId Operation ID
     * @throws InternalException When an exception occurs fracturing the mirror
     */
    public void pauseNativeContinuousCopies(URI storage, List<URI> mirrors, Boolean sync,
            String opId) throws InternalException;

    /**
     * Resume one or more mirrors.
     * 
     * @param storage
     * @param mirrors
     * @param opId
     * @throws InternalException
     */
    public void resumeNativeContinuousCopies(URI storage, List<URI> mirrors, String opId) throws InternalException;

    /**
     * Detach a mirror or mirrors of a volume or volumes.
     * 
     * @param storage URI of storage controller.
     * @param mirror URI of block mirror
     * @param opId Operation ID
     * @throws InternalException When an exception occurs detaching the mirror
     */
    public void detachMirror(URI storage, URI mirror, String opId) throws InternalException;

    /**
     * Delete a mirror or mirrors of a volume or volumes.
     * 
     * @param storage URI of storage controller.
     * @param mirror URI of block mirror
     * @param opId Operation ID
     * @throws InternalException When an exception occurs deleting the mirror
     */
    public void deleteMirror(URI storage, URI mirror, String opId) throws InternalException;

    /**
     * Detach and delete a mirror or mirrors of a volume or volumes.
     * 
     * @param storage URI of storage controller.
     * @param mirror URI of block mirror
     * @param opId Operation ID
     * @throws InternalException When an exception occurs deactivating the mirror
     */
    public void deactivateMirror(URI storage, URI mirror, String opId) throws InternalException;

    /**
     * Orchestrates the creation of full copy volumes
     * 
     * @param storage URI of storage controller.
     * @param fullCopyVolumes URIs of full copy volumes
     * @param createInactive Create the target full copy, but do not activate it
     * @param opId Operation ID @throws InternalException When an exception occurs creating full copies of the volumes
     */
    public void createFullCopy(URI storage, List<URI> fullCopyVolumes, Boolean createInactive, String opId)
            throws InternalException;

    /**
     * Delete a Consistency Group
     * 
     * @param storage URI of storage controller
     * @param consistencyGroup URI of block consistency group
     * @param markInactive true if the CG should be marked inactive
     * @param opId Operation ID
     * @throws InternalException When an exception occurs deleting a consistency group
     */
    public void deleteConsistencyGroup(URI storage, URI consistencyGroup, Boolean markInactive, String opId) throws InternalException;

    /**
     * Create a Consistency Group
     * 
     * @param storage URI of storage controller
     * @param consistencyGroup URI of block consistency group
     * @param opId Operation ID
     * 
     * @throws InternalException
     */
    public void createConsistencyGroup(URI storage, URI consistencyGroup, String opId) throws InternalException;

    /**
     * Activate a full copy volume.
     * 
     * @param storage
     * @param fullCopy
     * @param opId
     */
    public void activateFullCopy(URI storage, List<URI> fullCopy, String opId);

    /**
     * Detach a full copy volume from its source volume.
     * 
     * @param storage
     * @param fullCopy
     * @param opId
     */
    public void detachFullCopy(URI storage, List<URI> fullCopy, String opId);

    /**
     * Rollback step for create meta volume
     * 
     * @param systemURI
     * @param volumeURI
     * @param createStepId
     * @param opId workflow step id
     * @throws ControllerException
     */
    public void rollBackCreateMetaVolume(URI systemURI, URI volumeURI, String createStepId, String opId) throws InternalException;

    /**
     * Rollback step for create volumes
     * 
     * @param systemURI
     * @param volumeURIs
     * @param opId workflow step id
     * @throws ControllerException
     */
    public void rollBackCreateVolumes(URI systemURI, List<URI> volumeURIs, String opId) throws InternalException;

    /**
     * Rollback step for expand volume
     * 
     * @param systemURI
     * @param volumeURI
     * @param expandStepId
     * @param opId workflow step id
     * @throws ControllerException
     */
    public void rollBackExpandVolume(URI systemURI, URI volumeURI, String expandStepId, String opId) throws InternalException;

    /**
     * Gets the synchronization progress between source and target block objects.
     * 
     * @param storage
     * @param source
     * @param target
     * @return Percent of synchronization
     */
    public Integer checkSyncProgress(URI storage, URI source, URI target, String task);

    /**
     * This method can be used if any of the created workflow step doesn't need
     * roll back. with current implementation if the workflow node needs to be
     * rolled back but no rollback method is provided, the entire workflow will
     * now not generate a rollback set of steps.
     * 
     * */
    public void noActionRollBackStep(URI deviceURI, String opID);

    /**
     * Function to update a consistency group with new 'addVolumesList' members and
     * remove 'removeVolumsList' members.
     * 
     * @param storage
     * @param consistencyGroup
     * @param addVolumesList
     * @param removeVolumesList
     * @param task
     */
    public void updateConsistencyGroup(URI storage,
            URI consistencyGroup,
            List<URI> addVolumesList,
            List<URI> removeVolumesList, String task);

    /**
     * Function to modify volume.
     * 
     * @param systemURI
     * @param poolURI
     * @param volumeURIs
     * @param opId
     * @throws ControllerException
     */
    public void modifyVolumes(URI systemURI,
            URI poolURI,
            List<URI> volumeURIs,
            String opId) throws ControllerException;

    /**
     * 
     * @param systemURI
     * @param snapSessionURIs
     * @param sessionSnapshotURIMap
     * @param copyMode
     * @param opId
     * @throws InternalException
     */
    public void createSnapshotSession(URI systemURI, List<URI> snapSessionURIs,
            Map<URI, List<URI>> sessionSnapshotURIMap, String copyMode, String opId)
            throws InternalException;
}
