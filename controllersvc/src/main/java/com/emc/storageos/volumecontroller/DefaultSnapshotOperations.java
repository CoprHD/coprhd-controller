/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerException;

/*
 * Default implementation of SnapshotOperations, so that subclass can just overwrite necessary methods.
 */
public class DefaultSnapshotOperations implements SnapshotOperations {

    @Override
    public void createSingleVolumeSnapshot(StorageSystem storage, URI snapshot, Boolean createInactive, Boolean readOnly,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.operationNotSupported();

    }

    @Override
    public void createGroupSnapshots(StorageSystem storage, List<URI> snapshotList, Boolean createInactive, Boolean readOnly,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        // TODO Auto-generated method stub

    }

    @Override
    public void activateSingleVolumeSnapshot(StorageSystem storage, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.operationNotSupported();

    }

    @Override
    public void activateGroupSnapshots(StorageSystem storage, URI snapshot, TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.operationNotSupported();

    }

    @Override
    public void deleteSingleVolumeSnapshot(StorageSystem storage, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.operationNotSupported();

    }

    @Override
    public void deleteGroupSnapshots(StorageSystem storage, URI snapshot, TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.operationNotSupported();

    }

    @Override
    public void restoreSingleVolumeSnapshot(StorageSystem storage, URI volume, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.operationNotSupported();

    }

    @Override
    public void restoreGroupSnapshots(StorageSystem storage, URI volume, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.operationNotSupported();

    }

    @Override
    public void copySnapshotToTarget(StorageSystem storage, URI snapshot, TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.operationNotSupported();

    }

    @Override
    public void copyGroupSnapshotsToTarget(StorageSystem storage, List<URI> snapshotList, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.operationNotSupported();

    }

    @Override
    public void terminateAnyRestoreSessions(StorageSystem storage, BlockObject from, URI volume, TaskCompleter taskCompleter)
            throws Exception {
        throw DeviceControllerException.exceptions.operationNotSupported();

    }

    @Override
    public void resyncSingleVolumeSnapshot(StorageSystem storage, URI volume, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.operationNotSupported();

    }

    @Override
    public void resyncGroupSnapshots(StorageSystem storage, URI volume, URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.operationNotSupported();

    }

    @Override
    public void establishVolumeSnapshotGroupRelation(StorageSystem storage, URI sourceVolume,
            URI snapshot, TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createSnapshotSession(StorageSystem system, URI snapSessionURI, TaskCompleter completer)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.operationNotSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createGroupSnapshotSession(StorageSystem system, List<URI> snapSessionURIs, TaskCompleter completer)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.operationNotSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void linkSnapshotSessionTarget(StorageSystem system, URI snapSessionURI, URI snapshotURI,
            String copyMode, Boolean targetExists, TaskCompleter completer)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.operationNotSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void relinkSnapshotSessionTarget(StorageSystem system, URI tgtSnapSessionURI, URI snapshotURI,
            TaskCompleter completer) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.operationNotSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unlinkSnapshotSessionTarget(StorageSystem system, URI snapSessionURI, URI snapshotURI,
            Boolean deleteTarget, TaskCompleter completer) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.operationNotSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void restoreSnapshotSession(StorageSystem system, URI snapSessionURI, TaskCompleter completer)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.operationNotSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteSnapshotSession(StorageSystem system, URI snapSessionURI, TaskCompleter completer)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.operationNotSupported();
    }
}
