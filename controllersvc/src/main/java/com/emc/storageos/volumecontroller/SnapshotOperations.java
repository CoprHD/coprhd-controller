/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerException;

/**
 * Interfaces for block snapshot operations
 * - There will be operations for create, delete, and restore
 * - There will be different interfaces for single volume snaps versus snaps for
 * volumes that are in a consistency group.
 */
public interface SnapshotOperations {
    public static final String CREATE_ERROR_MSG_FORMAT = "Failed to create single snapshot %s";
    public static final String DELETE_ERROR_MSG_FORMAT = "Failed to delete single snapshot %s";

    /**
     * Should implement creation of a single volume snapshot. That is a volume that
     * is not in any consistency group.
     * 
     * @param storage [required] - StorageSystem object representing the array
     * @param snapshot [required] - BlockSnapshot URI representing the previously created
     *            snap for the volume
     * @param readOnly create snapshot as read only or writable.
     * @param taskCompleter - TaskCompleter object used for the updating operation status.
     * @throws DeviceControllerException
     */
    void createSingleVolumeSnapshot(StorageSystem storage, URI snapshot, Boolean createInactive, Boolean readOnly,
            TaskCompleter taskCompleter)
            throws DeviceControllerException;

    /**
     * Should implement create of a snapshot from a source volume that is part of a
     * consistency group.
     * 
     * @param storage [required] - StorageSystem object representing the array
     * @param snapshotList [required] - BlockSnapshot URI representing the previously created
     *            snap for the volume
     * @param readOnly create snapshot as read only or writable.
     * @param taskCompleter - TaskCompleter object used for the updating operation status.
     * @throws DeviceControllerException
     */
    void createGroupSnapshots(StorageSystem storage, List<URI> snapshotList, Boolean createInactive, Boolean readOnly,
            TaskCompleter taskCompleter)
            throws DeviceControllerException;

    /**
     * This interface is for the snapshot active. The createSnapshot may have done
     * whatever is necessary to setup the snapshot for this call. The goal is to
     * make this a quick operation and the create operation has already done a lot
     * of the "heavy lifting".
     * 
     * @param storage [required] - StorageSystem object representing the array
     * @param snapshot [required] - BlockSnapshot URI representing the previously created
     *            snap for the volume
     * @param taskCompleter - TaskCompleter object used for the updating operation status.
     * @throws DeviceControllerException
     */
    void activateSingleVolumeSnapshot(StorageSystem storage, URI snapshot, TaskCompleter taskCompleter) throws DeviceControllerException;

    /**
     * This interface is for the snapshot active. The createSnapshot may have done
     * whatever is necessary to setup the snapshot for this call. The goal is to
     * make this a quick operation and the create operation has already done a lot
     * of the "heavy lifting".
     * 
     * @param storage [required] - StorageSystem object representing the array
     * @param snapshot [required] - BlockSnapshot URI representing the previously created
     *            snap for the volume
     * @param taskCompleter - TaskCompleter object used for the updating operation status.
     * @throws DeviceControllerException
     */
    void activateGroupSnapshots(StorageSystem storage, URI snapshot, TaskCompleter taskCompleter) throws DeviceControllerException;

    /**
     * Should implement deletion of single volume snapshot. That is, deleting a snap that was
     * created independent of other volumes.
     * 
     * @param storage [required] - StorageSystem object representing the array
     * @param snapshot [required] - BlockSnapshot URI representing the previously created
     *            snap for the volume
     * @param taskCompleter - TaskCompleter object used for the updating operation status.
     * @throws DeviceControllerException
     */
    void deleteSingleVolumeSnapshot(StorageSystem storage, URI snapshot, TaskCompleter taskCompleter) throws DeviceControllerException;

    /**
     * Should implement clean up of all the snapshots in a volume consistency
     * group 'snap-set'. The 'snap-set' is a set of block snapshots created for a
     * set of volumes in a consistency group.
     * 
     * @param storage [required] - StorageSystem object representing the array
     * @param snapshot [required] - BlockSnapshot object representing the previously created
     *            snap for the volume
     * @param taskCompleter - TaskCompleter object used for the updating operation status.
     * @throws DeviceControllerException
     */
    void deleteGroupSnapshots(StorageSystem storage, URI snapshot, TaskCompleter taskCompleter) throws DeviceControllerException;

    /**
     * Implementation for restoring of a single volume snapshot restore. That is, this
     * volume is independent of other volumes and a snapshot was taken previously, and
     * now we want to restore that snap to the original volume.
     * 
     * @param storage [required] - StorageSystem object representing the array
     * @param volume [required] - Volume URI for the volume to be restored
     * @param snapshot [required] - BlockSnapshot URI representing the previously created
     *            snap for the volume
     * @param taskCompleter - TaskCompleter object used for the updating operation status.
     * @throws DeviceControllerException
     */
    void restoreSingleVolumeSnapshot(StorageSystem storage, URI volume, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException;

    /**
     * Implementation should restore the set of snapshots that were taken for a set of
     * volumes in a consistency group. That is, at some time there was a consistency
     * group of volumes created and snapshot was taken of these; these snapshots would
     * belong to a "snap-set". This restore operation, will restore the volumes in the
     * consistency group from this snap-set. Any snapshot from the snap-set can be
     * provided to restore the whole snap-set.
     * 
     * @param storage [required] - StorageSystem object representing the array
     * @param snapshot [required] - BlockSnapshot URI representing the previously created
     *            snap for the volume
     * @param taskCompleter - TaskCompleter object used for the updating operation status.
     * @throws DeviceControllerException
     */
    void restoreGroupSnapshots(StorageSystem storage, URI volume, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException;

    /**
     * Implementation should attach an inactive block snapshot to copy-to-target
     * 
     * @param storage [required] - StorageSystem object representing the array
     * @param taskCompleter - TaskCompleter object used for the updating operation status.
     * @throws DeviceControllerException
     */
    void copySnapshotToTarget(StorageSystem storage, URI snapshot,
            TaskCompleter taskCompleter) throws
            DeviceControllerException;

    /**
     * Implementation should attach an inactive CG blocksnapshot set to target devices.
     * 
     * 
     * @param storage [required] - StorageSystem object representing the array
     * @param taskCompleter - TaskCompleter object used for the updating operation status.
     * @throws DeviceControllerException
     */
    void copyGroupSnapshotsToTarget(StorageSystem storage, List<URI> snapshotList,
            TaskCompleter taskCompleter) throws
            DeviceControllerException;

    /**
     * Given a snapshot and a URI of its parent volume, look up any existing restore sessions and
     * terminate them.
     * 
     * @param storage [in] - StorageSystem object representing the array
     * @param from [in] - Should be the snapshot object
     * @param volume [in] - Should be the volume URI
     * @param taskCompleter [in] - TaskCompleter used for updating status of operation
     * @throws Exception
     */
    public void terminateAnyRestoreSessions(StorageSystem storage, BlockObject from, URI volume,
            TaskCompleter taskCompleter) throws Exception;

    /**
     * Implementation for a single volume snapshot resynchronization.
     * 
     * @param storage [required] - StorageSystem object representing the array
     * @param volume [required] - Volume URI for the volume to be resynchronized from
     * @param snapshot [required] - BlockSnapshot URI representing the previously created
     *            snap for the volume
     * @param taskCompleter - TaskCompleter object used for the updating operation status.
     * @throws DeviceControllerException
     */
    void resyncSingleVolumeSnapshot(StorageSystem storage, URI volume, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException;

    /**
     * Implementation should resynchronize the set of snapshots that were taken for a set of
     * volumes in a consistency group. That is, at some time there was a consistency
     * group of volumes created and snapshot was taken of these; these snapshots would
     * belong to a "snap-set". This operation will resynchronize the snap-set from the volumes
     * in the consistency group. Any snapshot from the snap-set can be provided to resync the whole snap-set.
     * 
     * @param storage [required] - StorageSystem object representing the array
     * @param volume [required] - URI of the snapshot's parent volume
     * @param snapshot [required] - BlockSnapshot URI representing the previously created
     *            snap for the volume
     * @param taskCompleter - TaskCompleter object used for the updating operation status.
     * @throws DeviceControllerException
     */
    void resyncGroupSnapshots(StorageSystem storage, URI volume, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException;

    /**
     * Establish group relation between volume group and snapshot group.
     * 
     * @param storage the storage
     * @param sourceVolume the source volume
     * @param snapshot the snapshot
     * @param taskCompleter the task completer
     * @throws DeviceControllerException the device controller exception
     */
    void establishVolumeSnapshotGroupRelation(StorageSystem storage, URI sourceVolume,
            URI snapshot, TaskCompleter taskCompleter) throws DeviceControllerException;

    /**
     * Should implement creation of a block snapshot session for a single source object.
     * This is the case where the source object is not in a consistency group.
     * 
     * @param system Reference to the storage system.
     * @param snapSessionURI The URI of the ViPR BlockSnapshotSession instance.
     * @param completer Reference to a task completer to invoke upon completion of the operation.
     * 
     * @throws DeviceControllerException
     */
    public void createSnapshotSession(StorageSystem system, URI snapSessionURI, TaskCompleter completer)
            throws DeviceControllerException;

    /**
     * Should implement creation of a group block snapshot session for a group of source objects.
     * This is the case where the source object(s) is in a consistency group.
     * 
     * @param system Reference to the storage system.
     * @param snapSessionURI The URIs of the ViPR BlockSnapshotSession instances.
     * @param completer Reference to a task completer to invoke upon completion of the operation.
     *
     * @throws DeviceControllerException
     */
    public void createGroupSnapshotSession(StorageSystem system, URI snapSessionURI, TaskCompleter completer)
            throws DeviceControllerException;

    /**
     * Creates a new target volume and links it to an array snapshot on the passed storage system.
     * 
     * @param system A reference to the storage system.
     * @param snapSessionURI The URI of the BlockSnapshotSession instance in ViPR
     *            that represents the array snapshot.
     * @param snapshotURI The URI of the BlockSnapshot instance in ViPR that will represent
     *            the new target volume.
     * @param copyMode The copy mode in which the target is linked to the snapshot.
     * @param targetExists true if the target exists, false if a new one needs to be created.
     * @param completer A reference to the task completer.
     * 
     * @throws DeviceControllerException
     */
    public void linkSnapshotSessionTarget(StorageSystem system, URI snapSessionURI, URI snapshotURI,
            String copyMode, Boolean targetExists, TaskCompleter completer) throws DeviceControllerException;

    /**
     * Creates a new target volume group and links it to an array snapshot on the passed storage system.
     *
     * @param system A reference to the storage system.
     * @param snapshotSessionURI
     *@param snapSessionSnapshotURIs Map of BlockSnapshotSession URI's to their BlockSnapshot instance URI,
     *                               representing the linked target.
     * @param copyMode The copy mode in which the target is linked to the snapshot.
     * @param targetsExist true if the target exists, false if a new one needs to be created.
     * @param completer A reference to the task completer.
*     @throws DeviceControllerException
     */
    public void linkSnapshotSessionTargetGroup(StorageSystem system, URI snapshotSessionURI, List<URI> snapSessionSnapshotURIs,
                                               String copyMode, Boolean targetsExist, TaskCompleter completer) throws DeviceControllerException;

    /**
     * Re-links a target volume to an array snapshot on the passed storage system.
     * 
     * @param system A reference to the storage system.
     * @param tgtSnapSessionURI The URI of the BlockSnapshotSession instance in ViPR
     *            that represents the target array snapshot.
     * @param snapshotURI The URI of the BlockSnapshot instance in ViPR that represents
     *            the target volume.
     * @param completer A reference to the task completer.
     * 
     * @throws DeviceControllerException
     */
    public void relinkSnapshotSessionTarget(StorageSystem system, URI tgtSnapSessionURI, URI snapshotURI,
            TaskCompleter completer) throws DeviceControllerException;

    /**
     * Creates a new target volume and links it to an array snapshot on the passed storage system.
     * 
     * @param system A reference to the storage system.
     * @param snapSessionURI The URI of the BlockSnapshotSession instance in ViPR
     *            that represents the array snapshot.
     * @param snapshotURI The URI of the BlockSnapshot instance in ViPR that represents
     *            the target volume.
     * @param deleteTarget True if the target should also be deleted.
     * @param completer A reference to the task completer.
     * 
     * @throws DeviceControllerException
     */
    public void unlinkSnapshotSessionTarget(StorageSystem system, URI snapSessionURI, URI snapshotURI,
            Boolean deleteTarget, TaskCompleter completer) throws DeviceControllerException;

    /**
     * Restores the data on a snapshot session to its source.
     * 
     * @param system A reference to the storage system.
     * @param snapSessionURI The URI of the BlockSnapshotSession instance in ViPR
     *            that represents the array snapshot.
     * @param completer A reference to the task completer.
     * 
     * @throws DeviceControllerException
     */
    public void restoreSnapshotSession(StorageSystem system, URI snapSessionURI, TaskCompleter completer)
            throws DeviceControllerException;

    /**
     * Deletes the snapshot session.
     * 
     * @param system A reference to the storage system.
     * @param snapSessionURI The URI of the BlockSnapshotSession instance in ViPR
     *            that represents the array snapshot.
     * @param completer A reference to the task completer.
     * 
     * @throws DeviceControllerException
     */
    public void deleteSnapshotSession(StorageSystem system, URI snapSessionURI, TaskCompleter completer)
            throws DeviceControllerException;
}
