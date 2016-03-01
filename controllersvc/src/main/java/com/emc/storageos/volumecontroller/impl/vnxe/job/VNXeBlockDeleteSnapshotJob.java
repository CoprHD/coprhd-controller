/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.vnxe.job;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshot;

import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;

public class VNXeBlockDeleteSnapshotJob extends VNXeJob {

    private static final long serialVersionUID = -2441188595854598851L;
    private static final Logger _logger = LoggerFactory.getLogger(VNXeBlockDeleteSnapshotJob.class);

    public VNXeBlockDeleteSnapshotJob(String jobId, URI storageSystemUri,
            TaskCompleter taskCompleter) {
        super(jobId, storageSystemUri, taskCompleter, "deleteBlockSnapshot");
    }

    /**
     * Called to update the job status when the snapshot delete job completes.
     * 
     * @param jobContext The job context.
     */
    @Override
    public void updateStatus(JobContext jobContext) throws Exception {

        DbClient dbClient = jobContext.getDbClient();
        try {
            if (_status == JobStatus.IN_PROGRESS) {
                return;
            }

            String opId = getTaskCompleter().getOpId();
            _logger.info(String.format("Updating status of job %s to %s", opId, _status.name()));

            URI snapId = getTaskCompleter().getId();
            BlockSnapshot snapshotObj = dbClient.queryObject(BlockSnapshot.class, snapId);
            if (_status == JobStatus.SUCCESS && snapshotObj != null) {
                if (snapshotObj.getConsistencyGroup() != null) {
                    // Set inactive=true for all snapshots in the lun group
                    List<BlockSnapshot> snapshots = ControllerUtils.getSnapshotsPartOfReplicationGroup(snapshotObj, dbClient);
                    for (BlockSnapshot snapshot : snapshots) {
                        processSnapshot(snapshot, dbClient);
                    }

                } else {
                    processSnapshot(snapshotObj, dbClient);
                }

                getTaskCompleter().ready(dbClient);
            } else if (_status == JobStatus.FAILED && snapshotObj != null) {
                _logger.info(String.format(
                        "Task %s failed to delete volume snapshot: %s", opId, snapshotObj.getLabel()));
            }
        } catch (Exception e) {
            _logger.error("Caught an exception while trying to updateStatus for VNXeBlockDeleteSnapshotJob", e);
            setErrorStatus("Encountered an internal error during volume snapshot delete job status processing : " + e.getMessage());
        } finally {
            super.updateStatus(jobContext);
        }
    }

    private void processSnapshot(BlockSnapshot snapshotObj, DbClient dbClient) {
        snapshotObj.setInactive(true);
        snapshotObj.setIsSyncActive(false);
        _logger.info(String.format("Deleted volume snapshot %s successfully", snapshotObj.getLabel()));

        dbClient.persistObject(snapshotObj);
    }

}
