/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.vnxe.job;

import java.net.URI;
import java.util.Calendar;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.models.ParametersOut;
import com.emc.storageos.vnxe.models.Snap;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.emc.storageos.vnxe.models.VNXeLun;
import com.emc.storageos.vnxe.models.VNXeLunSnap;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotCreateCompleter;

public class VNXeBlockSnapshotCreateJob extends VNXeJob {

    private static final long serialVersionUID = -1111309699098374228L;
    private static final Logger _logger = LoggerFactory.getLogger(VNXeBlockSnapshotCreateJob.class);
    private boolean createInactive;

    public VNXeBlockSnapshotCreateJob(String jobId, URI storageSystemUri,
            Boolean createInactive, TaskCompleter taskCompleter) {
        super(jobId, storageSystemUri, taskCompleter, "createBlockSnapshot");
        this.createInactive = createInactive;
    }

    public void updateStatus(JobContext jobContext) throws Exception {
        DbClient dbClient = jobContext.getDbClient();
        try {
            if (_status == JobStatus.IN_PROGRESS) {
                return;
            }
            BlockSnapshotCreateCompleter completer = (BlockSnapshotCreateCompleter) getTaskCompleter();
            List<BlockSnapshot> snapshots = dbClient.queryObject(BlockSnapshot.class, completer.getSnapshotURIs());

            if (_status == JobStatus.SUCCESS) {
                _logger.info(String.format(
                        "Post-processing successful snap creation task:%s. Expected: snapshot.size() = 1; Actual: snapshots.size() = %d",
                        getTaskCompleter().getOpId(), snapshots.size()));

                StorageSystem storage = dbClient.queryObject(StorageSystem.class, getStorageSystemUri());

                VNXeApiClient vnxeApiClient = getVNXeClient(jobContext);
                VNXeCommandJob vnxeJob = vnxeApiClient.getJob(getJobIds().get(0));
                ParametersOut output = vnxeJob.getParametersOut();
                BlockSnapshot snapshot = snapshots.get(0);
                Volume volume = dbClient.queryObject(Volume.class, snapshot.getParent().getURI());
                snapshot.setNativeId(output.getId());
                snapshot.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(storage, snapshot));
                snapshot.setIsSyncActive(true);
                snapshot.setInactive(false);
                snapshot.setCreationTime(Calendar.getInstance());
                
                if (vnxeApiClient.isUnityClient()) {
                    Snap snap = vnxeApiClient.getSnapshot(output.getId());
                    snapshot.setDeviceLabel(snap.getName());
                    snapshot.setAllocatedCapacity(snap.getSize());
                    snapshot.setProvisionedCapacity(snap.getSize());
                } else {
                    VNXeLunSnap vnxeLunSnap = vnxeApiClient.getLunSnapshot(output.getId());
                    VNXeLun lun = vnxeApiClient.getLun(vnxeLunSnap.getLun().getId());
                    snapshot.setDeviceLabel(vnxeLunSnap.getName());
                    snapshot.setAllocatedCapacity(lun.getSnapsSizeAllocated());
                    snapshot.setProvisionedCapacity(lun.getSnapsSize());
                }

                _logger.info(String.format("Going to set blocksnapshot %1$s nativeId to %2$s. Associated volume is %3$s (%4$s)",
                        snapshot.getId().toString(), output.getId(), volume.getNativeId(), volume.getLabel()));
                dbClient.updateObject(snapshot);
                getTaskCompleter().ready(dbClient);

            } else if (_status == JobStatus.FAILED) {
                _logger.info("Failed to create snapshot");
                BlockSnapshot snapshot = snapshots.get(0);
                snapshot.setInactive(true);
                dbClient.updateObject(snapshot);
            }
        } catch (Exception e) {
            _logger.error("Caught an exception while trying to updateStatus for VNXeBlockSnapshotCreateJob", e);
            setErrorStatus("Encountered an internal error during volume snapshot create job status processing : " + e.getMessage());
        } finally {
            super.updateStatus(jobContext);
        }
    }
}
