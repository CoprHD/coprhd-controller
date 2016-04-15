/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.vnxunity;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.VNXeException;
import com.emc.storageos.vnxe.models.Snap;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.emc.storageos.vnxe.models.VNXeLunSnap;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.job.QueueJob;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.vnxe.VNXeSnapshotOperation;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeBlockDeleteSnapshotJob;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeBlockSnapshotCreateJob;

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
        } catch (VNXeException e) {
            log.error("Create volume snapshot got the exception", e);
            taskCompleter.error(_dbClient, e);

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

        } catch (VNXeException e) {
            log.error("Delete volume snapshot got the exception", e);
            taskCompleter.error(_dbClient, e);

        } catch (Exception ex) {
            log.error("Delete volume snapshot got the exception", ex);
            ServiceError error = DeviceControllerErrors.vnxe.jobFailed("DeleteSnapshot", ex.getMessage());
            taskCompleter.error(_dbClient, error);

        }

    }
}
