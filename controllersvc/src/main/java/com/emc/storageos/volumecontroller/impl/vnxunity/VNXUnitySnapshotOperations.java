/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.vnxunity;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.models.Snap;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.job.QueueJob;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.vnxe.VNXeSnapshotOperation;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeBlockSnapshotCreateJob;
import com.emc.storageos.volumecontroller.impl.vnxunity.job.VNXUnityCreateCGSnapshotJob;
import com.emc.storageos.volumecontroller.impl.vnxunity.job.VNXUnityRestoreSnapshotJob;

public class VNXUnitySnapshotOperations extends VNXeSnapshotOperation {
    private static final Logger log = LoggerFactory.getLogger(VNXUnitySnapshotOperations.class);
    @Override
    protected VNXeApiClient getVnxeClient(StorageSystem storage) {
        VNXeApiClient client = _clientFactory.getUnityClient(storage.getIpAddress(),
                storage.getPortNumber(), storage.getUsername(),
                storage.getPassword());

        return client;

    }
    
    @Override
    public void createSingleVolumeSnapshot(StorageSystem storage, URI snapshot,
            Boolean createInactive, Boolean readOnly, TaskCompleter taskCompleter)
            throws DeviceControllerException {

        try {
            BlockSnapshot snapshotObj = _dbClient.queryObject(BlockSnapshot.class, snapshot);
            if (readOnly) {
                snapshotObj.setIsReadOnly(readOnly);
                _dbClient.updateObject(snapshotObj);
            }
            Volume volume = _dbClient.queryObject(Volume.class, snapshotObj.getParent());
            TenantOrg tenant = _dbClient.queryObject(TenantOrg.class, volume.getTenant().getURI());
            String tenantName = tenant.getLabel();
            String snapLabelToUse =
                    _nameGenerator.generate(tenantName, snapshotObj.getLabel(),
                            snapshot.toString(), '-', SmisConstants.MAX_SNAPSHOT_NAME_LENGTH);

            VNXeApiClient apiClient = getVnxeClient(storage);
            VNXeCommandJob job = apiClient.createSnap(volume.getNativeId(), snapLabelToUse, readOnly);
            if (job != null) {
                ControllerServiceImpl.enqueueJob(
                        new QueueJob(new VNXeBlockSnapshotCreateJob(job.getId(),
                                storage.getId(), !createInactive, taskCompleter)));
            }
        } catch (Exception ex) {
            log.error("Create volume snapshot got the exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("CreateVolumeSnapshot", ex.getMessage());
            taskCompleter.error(_dbClient, error);

        }

    }
    
    @Override
    public void deleteSingleVolumeSnapshot(StorageSystem storage, URI snapshot,
            TaskCompleter taskCompleter) throws DeviceControllerException {

        try {
            BlockSnapshot snap = _dbClient.queryObject(BlockSnapshot.class, snapshot);
            VNXeApiClient apiClient = getVnxeClient(storage);
            Snap lunSnap = apiClient.getSnapshot(snap.getNativeId());
            if (lunSnap != null) {
                apiClient.deleteSnap(lunSnap.getId());
            } 
            snap.setInactive(true);
            snap.setIsSyncActive(false);
            _dbClient.updateObject(snap);
            taskCompleter.ready(_dbClient);

        } catch (Exception ex) {
            log.error("Delete volume snapshot got the exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("DeleteSnapshot", ex.getMessage());
            taskCompleter.error(_dbClient, error);

        }

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
                    _nameGenerator.generate(tenantName, snapshotObj.getSnapsetLabel(),
                            snapshot.toString(), '-', SmisConstants.MAX_SNAPSHOT_NAME_LENGTH);
            String groupName = volume.getReplicationGroupInstance();
            if (NullColumnValueGetter.isNotNullValue(groupName)) {
                VNXeApiClient apiClient = getVnxeClient(storage);
                String cgId = apiClient.getConsistencyGroupIdByName(groupName);
                VNXeCommandJob job = apiClient.createSnap(cgId, snapLabelToUse, readOnly);
                if (job != null) {
                    ControllerServiceImpl.enqueueJob(
                            new QueueJob(new VNXUnityCreateCGSnapshotJob(job.getId(),
                                    storage.getId(), readOnly, taskCompleter)));
                }
            } else {
                String errorMsg = "Unable to find consistency group id when creating snapshot";
                log.error(errorMsg);
                ServiceError error = DeviceControllerErrors.vnxe.jobFailed("CreateCGSnapshot", errorMsg);
                taskCompleter.error(_dbClient, error);
            }

        } catch (Exception ex) {
            log.error("Create volume snapshot got the exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("CreateCGSnapshot", ex.getMessage());
            taskCompleter.error(_dbClient, error);

        }

    }

    @Override
    public void deleteGroupSnapshots(StorageSystem storage, URI snapshot,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        try {
            BlockSnapshot snapshotObj = _dbClient.queryObject(BlockSnapshot.class, snapshot);

            VNXeApiClient apiClient = getVnxeClient(storage);
            String groupId = snapshotObj.getReplicationGroupInstance();
            Snap snapGroup = apiClient.getSnapshot(groupId);
            if (snapGroup != null) {
                apiClient.deleteSnap(groupId);
            }
            List<BlockSnapshot> snaps = ControllerUtils.getSnapshotsPartOfReplicationGroup(snapshotObj, _dbClient);
            if (snaps != null) {
                for (BlockSnapshot snap : snaps) {
                    snap.setInactive(true);
                    snap.setIsSyncActive(false);
                    _dbClient.updateObject(snap);
                }
            }
            taskCompleter.ready(_dbClient);

        } catch (Exception ex) {
            log.error("Delete group snapshot got the exception", ex);
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
            Snap snap = apiClient.getSnapshot(snapshotObj.getNativeId());
            // Error out if the snapshot is attached
            if (snap.isAttached()) {
                log.error("Snapshot {})is attached and cannot be used for restore, please unexport it first", snapshotObj.getLabel());
                ServiceError error = DeviceControllerErrors.vnxe.cannotRestoreAttachedSnapshot(snapshot.toString());
                taskCompleter.error(_dbClient, error);
            }

            VNXeCommandJob job = apiClient.restoreSnap(snap.getId());
            if (job != null) {
                ControllerServiceImpl.enqueueJob(
                        new QueueJob(new VNXUnityRestoreSnapshotJob(job.getId(),
                                storage.getId(), taskCompleter)));
            }
        } catch (Exception ex) {
            log.error("Restore snapshot got the exception", ex);
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
            Snap groupSnap = apiClient.getSnapshot(snapshotObj.getReplicationGroupInstance());
            Snap snap = apiClient.getSnapshot(snapshotObj.getNativeId());
            // Error out if the snapshot is attached
            if (snap.isAttached()) {
                log.error("Snapshot {})is attached and cannot be used for restore", snapshotObj.getLabel());
                ServiceError error = DeviceControllerErrors.vnxe.cannotRestoreAttachedSnapshot(snapshot.toString());
                taskCompleter.error(_dbClient, error);
            }
            VNXeCommandJob job = apiClient.restoreSnap(groupSnap.getId());
            if (job != null) {
                ControllerServiceImpl.enqueueJob(
                        new QueueJob(new VNXUnityRestoreSnapshotJob(job.getId(),
                                storage.getId(), taskCompleter)));
            }

        } catch (Exception ex) {
            log.error("Restore group snapshot got the exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("RestoreSnapshotJob", ex.getMessage());
            taskCompleter.error(_dbClient, error);

        }

    }
}
