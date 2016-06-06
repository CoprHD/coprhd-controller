/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.vnxunity.job;

import java.net.URI;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotCreateCompleter;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeJob;

public class VNXUnityCreateCGSnapshotJob extends VNXeJob{

    private static final long serialVersionUID = -5563400198981214053L;

    private static final Logger _logger = LoggerFactory.getLogger(VNXUnityCreateCGSnapshotJob.class);

    private Boolean readOnly;
    
    public VNXUnityCreateCGSnapshotJob(String jobId,
            URI storageSystemUri, Boolean readOnly, TaskCompleter taskCompleter) {
        super(jobId, storageSystemUri, taskCompleter, "createCGSnapshot");
        this.readOnly = readOnly;
    }
    
    public void updateStatus(JobContext jobContext) throws Exception {
        DbClient dbClient = jobContext.getDbClient();
        try {
            if (_status == JobStatus.IN_PROGRESS) {
                return;
            }
            BlockSnapshotCreateCompleter completer = (BlockSnapshotCreateCompleter) getTaskCompleter();
            List<BlockSnapshot> snapshots = dbClient.queryObject(BlockSnapshot.class, completer.getSnapshotURIs());
            StorageSystem storage = dbClient.queryObject(StorageSystem.class, getStorageSystemUri());
            if (_status == JobStatus.SUCCESS) {
                VNXeApiClient vnxeApiClient = getVNXeClient(jobContext);
                VNXeCommandJob vnxeJob = vnxeApiClient.getJob(getJobIds().get(0));
                ParametersOut output = vnxeJob.getParametersOut();
                String snapGroupId = output.getId();
                List<Snap> snaps = vnxeApiClient.getSnapshotsBySnapGroup(snapGroupId);
                
                // Create mapping of volume.nativeDeviceId to BlockSnapshot object
                Map<String, BlockSnapshot> volumeToSnapMap = new HashMap<String, BlockSnapshot>();
                
                for (BlockSnapshot snapshot : snapshots) {
                    Volume volume = dbClient.queryObject(Volume.class, snapshot.getParent());
                    volumeToSnapMap.put(volume.getNativeId(), snapshot);
                }
                for (Snap snap : snaps) {
                    String lunId = snap.getLun().getId();
                    BlockSnapshot snapshot = volumeToSnapMap.get(lunId);
                    snapshot.setNativeId(snap.getId());
                    snapshot.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(storage, snapshot));
                    snapshot.setDeviceLabel(snap.getName());
                    snapshot.setReplicationGroupInstance(snapGroupId);
                    snapshot.setIsSyncActive(true);
                    snapshot.setInactive(false);
                    snapshot.setCreationTime(Calendar.getInstance());
                    snapshot.setWWN(snap.getAttachedWWN());
                    snapshot.setAllocatedCapacity(snap.getSize());
                    snapshot.setProvisionedCapacity(snap.getSize());
                    if (readOnly) {
                        snapshot.setIsReadOnly(readOnly);
                    }
                    _logger.info(String.format("Going to set blocksnapshot %1$s nativeId to %2$s (%3$s). Associated lun is %4$s",
                            snapshot.getId().toString(), snap.getId(), snapshot.getLabel(), lunId));
                    dbClient.updateObject(snapshot);
                }

            } else if (_status == JobStatus.FAILED) {
                _logger.info("Failed to create snapshot");
                for (BlockSnapshot snapshot : snapshots) {
                    snapshot.setInactive(true);
                }
                dbClient.updateObject(snapshots);
            }
        } catch (Exception e) {
            _logger.error("Caught an exception while trying to updateStatus for VNXUntiyCreateCGSnapshotJob", e);
            setErrorStatus("Encountered an internal error during group snapshot create job status processing : " + e.getMessage());
        } finally {
            super.updateStatus(jobContext);
        }
    }

}
