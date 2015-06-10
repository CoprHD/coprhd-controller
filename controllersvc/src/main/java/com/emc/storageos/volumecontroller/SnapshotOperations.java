/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.volumecontroller.TaskCompleter;

/**
 * Interfaces for block snapshot operations
 *  - There will be operations for create, delete, and restore
 *  - There will be different interfaces for single volume snaps versus snaps for
 *    volumes that are in a consistency group.
 */
public interface SnapshotOperations {
	public static final String CREATE_ERROR_MSG_FORMAT = "Failed to create single snapshot %s";
	public static final String DELETE_ERROR_MSG_FORMAT = "Failed to delete single snapshot %s";
    /**
     * Should implement creation of a single volume snapshot. That is a volume that
     * is not in any consistency group.
     *
     * @param storage       [required] - StorageSystem object representing the array
     * @param snapshot      [required] - BlockSnapshot URI representing the previously created
     *                      snap for the volume
     * @param taskCompleter - TaskCompleter object used for the updating operation status.
     * @throws DeviceControllerException
     */
    void createSingleVolumeSnapshot(StorageSystem storage, URI snapshot, Boolean createInactive, TaskCompleter taskCompleter) throws DeviceControllerException;

    /**
     * Should implement create of a snapshot from a source volume that is part of a
     * consistency group.
     *
     * @param storage       [required] - StorageSystem object representing the array
     * @param snapshotList      [required] - BlockSnapshot URI representing the previously created
     *                      snap for the volume
     * @param taskCompleter - TaskCompleter object used for the updating operation status.
     * @throws DeviceControllerException
     */
    void createGroupSnapshots(StorageSystem storage, List<URI> snapshotList, Boolean createInactive, TaskCompleter taskCompleter) throws DeviceControllerException;

    /**
     * This interface is for the snapshot active. The createSnapshot may have done
     * whatever is necessary to setup the snapshot for this call. The goal is to
     * make this a quick operation and the create operation has already done a lot
     * of the "heavy lifting".
     *
     * @param storage       [required] - StorageSystem object representing the array
     * @param snapshot      [required] - BlockSnapshot URI representing the previously created
     *                      snap for the volume
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
     * @param storage       [required] - StorageSystem object representing the array
     * @param snapshot      [required] - BlockSnapshot URI representing the previously created
     *                      snap for the volume
     * @param taskCompleter - TaskCompleter object used for the updating operation status.
     * @throws DeviceControllerException
     */
    void activateGroupSnapshots(StorageSystem storage, URI snapshot, TaskCompleter taskCompleter) throws DeviceControllerException;

    /**
     * Should implement deletion of single volume snapshot. That is, deleting a snap that was
     * created independent of other volumes.
     *
     * @param storage       [required] - StorageSystem object representing the array
     * @param snapshot      [required] - BlockSnapshot URI representing the previously created
     *                      snap for the volume
     * @param taskCompleter - TaskCompleter object used for the updating operation status.
     * @throws DeviceControllerException
     */
    void deleteSingleVolumeSnapshot(StorageSystem storage, URI snapshot, TaskCompleter taskCompleter) throws DeviceControllerException;

    /**
     * Should implement clean up of all the snapshots in a volume consistency
     * group 'snap-set'. The 'snap-set' is a set of block snapshots created for a
     * set of volumes in a consistency group.
     *
     * @param storage              [required] - StorageSystem object representing the array
     * @param snapshot             [required] - BlockSnapshot object representing the previously created
     *                             snap for the volume
     * @param taskCompleter        - TaskCompleter object used for the updating operation status.
     * @throws DeviceControllerException
     */
    void deleteGroupSnapshots(StorageSystem storage, URI snapshot, TaskCompleter taskCompleter) throws DeviceControllerException;

    /**
     * Implementation for restoring of a single volume snapshot restore. That is, this
     * volume is independent of other volumes and a snapshot was taken previously, and
     * now we want to restore that snap to the original volume.
     *
     * @param storage       [required] - StorageSystem object representing the array
     * @param volume        [required] - Volume URI for the volume to be restored
     * @param snapshot      [required] - BlockSnapshot URI representing the previously created
     *                      snap for the volume
     * @param taskCompleter - TaskCompleter object used for the updating operation status.
     * @throws DeviceControllerException
     */
    void restoreSingleVolumeSnapshot(StorageSystem storage, URI volume, URI snapshot, TaskCompleter taskCompleter) throws DeviceControllerException;

    /**
     * Implementation should restore the set of snapshots that were taken for a set of
     * volumes in a consistency group. That is, at some time there was a consistency
     * group of volumes created and snapshot was taken of these; these snapshots would
     * belong to a "snap-set". This restore operation, will restore the volumes in the
     * consistency group from this snap-set. Any snapshot from the snap-set can be
     * provided to restore the whole snap-set.
     *
     * @param storage              [required] - StorageSystem object representing the array
     * @param snapshot             [required] - BlockSnapshot URI representing the previously created
     *                             snap for the volume
     * @param taskCompleter        - TaskCompleter object used for the updating operation status.
     * @throws DeviceControllerException
     */
    void restoreGroupSnapshots(StorageSystem storage, URI volume, URI snapshot, TaskCompleter taskCompleter) throws DeviceControllerException;

    /**
     * Implementation should attach an inactive block snapshot to copy-to-target
     *
     * @param storage        [required] - StorageSystem object representing the array
     * @param taskCompleter  - TaskCompleter object used for the updating operation status.
     * @throws DeviceControllerException
     */
    void copySnapshotToTarget(StorageSystem storage, URI snapshot,
                              TaskCompleter taskCompleter) throws
            DeviceControllerException;

    /**
     * Implementation should attach an inactive CG blocksnapshot set to target devices.
     *
     *
     * @param storage        [required] - StorageSystem object representing the array
     * @param taskCompleter  - TaskCompleter object used for the updating operation status.
     * @throws DeviceControllerException
     */
    void copyGroupSnapshotsToTarget(StorageSystem storage, List<URI> snapshotList,
                                    TaskCompleter taskCompleter) throws
            DeviceControllerException;

    /**
     * Given a snapshot and a URI of its parent volume, look up any existing restore sessions and
     * terminate them.
     *
     * @param storage       [in] - StorageSystem object representing the array
     * @param from          [in] - Should be the snapshot object
     * @param volume        [in] - Should be the volume URI
     * @param taskCompleter [in] - TaskCompleter used for updating status of operation
     * @throws Exception
     */
    public void terminateAnyRestoreSessions(StorageSystem storage, BlockObject from, URI volume,
                                            TaskCompleter taskCompleter) throws Exception;
}
