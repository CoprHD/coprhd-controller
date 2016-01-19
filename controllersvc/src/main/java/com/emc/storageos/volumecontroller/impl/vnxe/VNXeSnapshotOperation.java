/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.vnxe;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NameGenerator;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.VNXeException;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.emc.storageos.vnxe.models.VNXeLunGroupSnap;
import com.emc.storageos.vnxe.models.VNXeLunSnap;
import com.emc.storageos.volumecontroller.SnapshotOperations;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.job.QueueJob;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeBlockCreateCGSnapshotJob;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeBlockDeleteSnapshotJob;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeBlockRestoreSnapshotJob;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeBlockSnapshotCreateJob;

public class VNXeSnapshotOperation extends VNXeOperations implements SnapshotOperations {

    private static final Logger _log = LoggerFactory.getLogger(VNXeSnapshotOperation.class);
    protected NameGenerator _nameGenerator;
    public static final int MAX_SNAPSHOT_NAME_LENGTH = 85;

    public void setNameGenerator(NameGenerator nameGenerator) {
        _nameGenerator = nameGenerator;
    }

    @Override
    public void createGroupSnapshots(StorageSystem storage,
            List<URI> snapshotList, Boolean createInactive, Boolean readOnly,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        try {
            URI snapshot = snapshotList.get(0);
            BlockSnapshot snapshotObj = _dbClient.queryObject(BlockSnapshot.class, snapshot);

            Volume volume = _dbClient.queryObject(Volume.class, snapshotObj.getParent());
            TenantOrg tenant = _dbClient.queryObject(TenantOrg.class, volume.getTenant().getURI());
            String tenantName = tenant.getLabel();
            String snapLabelToUse =
                    _nameGenerator.generate(tenantName, snapshotObj.getLabel(),
                            snapshot.toString(), '-', SmisConstants.MAX_SNAPSHOT_NAME_LENGTH);
            String groupName = getConsistencyGroupName(snapshotObj);
            VNXeApiClient apiClient = getVnxeClient(storage);
            VNXeCommandJob job = apiClient.createLunGroupSnap(groupName, snapLabelToUse);
            if (job != null) {
                ControllerServiceImpl.enqueueJob(
                        new QueueJob(new VNXeBlockCreateCGSnapshotJob(job.getId(),
                                storage.getId(), !createInactive, taskCompleter)));
            }

        } catch (VNXeException e) {
            _log.error("Create volume snapshot got the exception", e);
            taskCompleter.error(_dbClient, e);

        } catch (Exception ex) {
            _log.error("Create volume snapshot got the exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("CreateCGSnapshot", ex.getMessage());
            taskCompleter.error(_dbClient, error);

        }

    }

    @Override
    public void activateSingleVolumeSnapshot(StorageSystem storage,
            URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void activateGroupSnapshots(StorageSystem storage, URI snapshot,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void deleteSingleVolumeSnapshot(StorageSystem storage, URI snapshot,
            TaskCompleter taskCompleter) throws DeviceControllerException {

        try {
            BlockSnapshot snap = _dbClient.queryObject(BlockSnapshot.class, snapshot);
            VNXeApiClient apiClient = getVnxeClient(storage);
            VNXeLunSnap lunSnap = apiClient.getLunSnapshot(snap.getNativeId());
            if (lunSnap != null) {
                VNXeCommandJob job = apiClient.deleteLunSnap(lunSnap.getId());
                if (job != null) {
                    ControllerServiceImpl.enqueueJob(
                            new QueueJob(new VNXeBlockDeleteSnapshotJob(job.getId(),
                                    storage.getId(), taskCompleter)));
                }
            } else {
                // Perhaps, it's already been deleted or was deleted on the array.
                // In that case, we'll just say all is well, so that this operation
                // is idempotent.
                snap.setInactive(true);
                snap.setIsSyncActive(false);
                _dbClient.persistObject(snap);
                taskCompleter.ready(_dbClient);
            }

        } catch (VNXeException e) {
            _log.error("Delete volume snapshot got the exception", e);
            taskCompleter.error(_dbClient, e);

        } catch (Exception ex) {
            _log.error("Delete volume snapshot got the exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("DeleteSnapshot", ex.getMessage());
            taskCompleter.error(_dbClient, error);

        }

    }

    @Override
    public void deleteGroupSnapshots(StorageSystem storage, URI snapshot,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        try {
            List<BlockSnapshot> snapshots = _dbClient.queryObject(BlockSnapshot.class, Arrays.asList(snapshot));
            BlockSnapshot snapshotObj = snapshots.get(0);

            VNXeApiClient apiClient = getVnxeClient(storage);
            VNXeLunGroupSnap lunGroupSnap = apiClient.getLunGroupSnapshot(snapshotObj.getReplicationGroupInstance());
            if (lunGroupSnap != null) {
                VNXeCommandJob job = apiClient.deleteLunGroupSnap(lunGroupSnap.getId());
                if (job != null) {
                    ControllerServiceImpl.enqueueJob(
                            new QueueJob(new VNXeBlockDeleteSnapshotJob(job.getId(),
                                    storage.getId(), taskCompleter)));
                }
            }

        } catch (VNXeException e) {
            _log.error("Delete group snapshot got the exception", e);
            taskCompleter.error(_dbClient, e);

        } catch (Exception ex) {
            _log.error("Delete group snapshot got the exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("DeletGroupSnapshot", ex.getMessage());
            taskCompleter.error(_dbClient, error);

        }

    }

    @Override
    public void restoreSingleVolumeSnapshot(StorageSystem storage, URI volume,
            URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {

        try {
            BlockSnapshot snapshotObj = _dbClient.queryObject(BlockSnapshot.class, snapshot);

            VNXeApiClient apiClient = getVnxeClient(storage);
            VNXeLunSnap lunSnap = apiClient.getLunSnapshot(snapshotObj.getNativeId());
            // Error out if the snapshot is attached
            if (lunSnap.getIsAttached()) {
                _log.error("Snapshot {})is attached and cannot be used for restore", snapshotObj.getLabel());
                ServiceError error = DeviceControllerErrors.vnxe.cannotRestoreAttachedSnapshot(snapshot.toString());
                taskCompleter.error(_dbClient, error);
            }

            VNXeCommandJob job = apiClient.restoreLunSnap(lunSnap.getId());
            if (job != null) {
                ControllerServiceImpl.enqueueJob(
                        new QueueJob(new VNXeBlockRestoreSnapshotJob(job.getId(),
                                storage.getId(), taskCompleter)));
            }
        } catch (VNXeException e) {
            _log.error("Restore snapshot got the exception", e);
            taskCompleter.error(_dbClient, e);

        } catch (Exception ex) {
            _log.error("Restore snapshot got the exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("RestoreSnapshotJob", ex.getMessage());
            taskCompleter.error(_dbClient, error);

        }

    }

    @Override
    public void restoreGroupSnapshots(StorageSystem storage, URI volume,
            URI snapshot, TaskCompleter taskCompleter)
            throws DeviceControllerException {

        try {
            BlockSnapshot snapshotObj = _dbClient.queryObject(BlockSnapshot.class, snapshot);

            VNXeApiClient apiClient = getVnxeClient(storage);
            VNXeLunGroupSnap lunGroupSnap = apiClient.getLunGroupSnapshot(snapshotObj.getReplicationGroupInstance());
            // Error out if the snapshot is attached
            if (lunGroupSnap.getIsAttached()) {
                _log.error("Snapshot {})is attached and cannot be used for restore", snapshotObj.getLabel());
                ServiceError error = DeviceControllerErrors.vnxe.cannotRestoreAttachedSnapshot(snapshot.toString());
                taskCompleter.error(_dbClient, error);
            }
            VNXeCommandJob job = apiClient.restoreLunGroupSnap(lunGroupSnap.getId());
            if (job != null) {
                ControllerServiceImpl.enqueueJob(
                        new QueueJob(new VNXeBlockRestoreSnapshotJob(job.getId(),
                                storage.getId(), taskCompleter)));
            }

        } catch (VNXeException e) {
            _log.error("Restore group snapshot got the exception", e);
            taskCompleter.error(_dbClient, e);

        } catch (Exception ex) {
            _log.error("Restore group snapshot got the exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("RestoreSnapshotJob", ex.getMessage());
            taskCompleter.error(_dbClient, error);

        }

    }

    @Override
    public void createSingleVolumeSnapshot(StorageSystem storage, URI snapshot,
            Boolean createInactive, Boolean readOnly, TaskCompleter taskCompleter)
            throws DeviceControllerException {

        try {
            BlockSnapshot snapshotObj = _dbClient.queryObject(BlockSnapshot.class, snapshot);

            Volume volume = _dbClient.queryObject(Volume.class, snapshotObj.getParent());
            TenantOrg tenant = _dbClient.queryObject(TenantOrg.class, volume.getTenant().getURI());
            String tenantName = tenant.getLabel();
            String snapLabelToUse =
                    _nameGenerator.generate(tenantName, snapshotObj.getLabel(),
                            snapshot.toString(), '-', SmisConstants.MAX_SNAPSHOT_NAME_LENGTH);

            VNXeApiClient apiClient = getVnxeClient(storage);
            VNXeCommandJob job = apiClient.createLunSnap(volume.getNativeId(), snapLabelToUse);
            if (job != null) {
                ControllerServiceImpl.enqueueJob(
                        new QueueJob(new VNXeBlockSnapshotCreateJob(job.getId(),
                                storage.getId(), !createInactive, taskCompleter)));
            }
        } catch (VNXeException e) {
            _log.error("Create volume snapshot got the exception", e);
            taskCompleter.error(_dbClient, e);

        } catch (Exception ex) {
            _log.error("Create volume snapshot got the exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("CreateVolumeSnapshot", ex.getMessage());
            taskCompleter.error(_dbClient, error);

        }

    }

    @Override
    public void copySnapshotToTarget(StorageSystem storage, URI snapshot,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.operationNotSupported();
    }

    @Override
    public void copyGroupSnapshotsToTarget(StorageSystem storage,
            List<URI> snapshotList, TaskCompleter taskCompleter)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.operationNotSupported();

    }

    @Override
    public void terminateAnyRestoreSessions(StorageSystem storage, BlockObject from, URI volume,
            TaskCompleter taskCompleter) throws Exception {
        throw DeviceControllerException.exceptions.operationNotSupported();
    }

    public String getConsistencyGroupName(BlockObject bo) {
        if (bo.getConsistencyGroup() == null) {
            return null;
        }
        final BlockConsistencyGroup group = _dbClient.queryObject(BlockConsistencyGroup.class,
                bo.getConsistencyGroup());
        return getConsistencyGroupName(group, bo);
    }

    public String getConsistencyGroupName(final BlockConsistencyGroup group, BlockObject bo) {
        String groupName = null;

        if (group != null && bo != null) {
            groupName = group.getCgNameOnStorageSystem(bo.getStorageController());
        }

        return groupName;
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
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createGroupSnapshotSession(StorageSystem system, URI snapSessionURI, String groupName, TaskCompleter completer)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void linkSnapshotSessionTarget(StorageSystem system, URI snapSessionURI, URI snapshotURI,
            String copyMode, Boolean targetExists, TaskCompleter completer)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    @Override
    public void linkSnapshotSessionTargetGroup(StorageSystem system, URI snapshotSessionURI, List<URI> snapSessionSnapshotURIs,
            String copyMode, Boolean targetsExist, TaskCompleter completer) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void relinkSnapshotSessionTarget(StorageSystem system, URI tgtSnapSessionURI, URI snapshotURI,
            TaskCompleter completer) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unlinkSnapshotSessionTarget(StorageSystem system, URI snapSessionURI, URI snapshotURI,
            Boolean deleteTarget, TaskCompleter completer) throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void restoreSnapshotSession(StorageSystem system, URI snapSessionURI, TaskCompleter completer)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteSnapshotSession(StorageSystem system, URI snapSessionURI, String groupName, TaskCompleter completer)
            throws DeviceControllerException {
        throw DeviceControllerException.exceptions.blockDeviceOperationNotSupported();
    }
}
