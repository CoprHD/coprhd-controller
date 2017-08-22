/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.vnxe.job;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.FileDeviceController;

public class VNXeDeleteFileSystemSnapshotJob extends VNXeJob {

    /**
     * 
     */
    private static final long serialVersionUID = 4942134855002932670L;
    private static final Logger _logger = LoggerFactory.getLogger(VNXeCreateFileSystemSnapshotJob.class);

    public VNXeDeleteFileSystemSnapshotJob(String jobId, URI storageSystemUri, TaskCompleter taskCompleter) {
        super(jobId, storageSystemUri, taskCompleter, "deleteFileSystemSnapshot");
    }

    /**
     * Called to update the job status when the file system snapshot delete job completes.
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
            StringBuilder logMsgBuilder = new StringBuilder(String.format("Updating status of job %s to %s", opId, _status.name()));

            URI snapId = getTaskCompleter().getId();
            Snapshot snapshotObj = dbClient.queryObject(Snapshot.class, snapId);
            URI fsUri = snapshotObj.getParent().getURI();
            FileShare fsObj = dbClient.queryObject(FileShare.class, fsUri);
            StorageSystem storageObj = dbClient.queryObject(StorageSystem.class, getStorageSystemUri());
            String event = null;
            if (_status == JobStatus.SUCCESS && snapshotObj != null) {
                snapshotObj.setInactive(true);
                dbClient.updateObject(snapshotObj);
                event = String.format("Deleted file sytem snapshot %s successfully", snapshotObj.getName());
                logMsgBuilder.append("\n");
                logMsgBuilder.append(event);
            } else if (_status == JobStatus.FAILED && snapshotObj != null) {
                event = String.format(
                        "Task %s failed to delete file system snapshot: %s", opId, snapshotObj.getName());
                logMsgBuilder.append("\n");
                logMsgBuilder.append(event);
            } else {
                event = "File sytem snapshot has been deleted";
                logMsgBuilder.append(String.format("Could not find the snapshot: %s", snapId));
            }
            _logger.info(logMsgBuilder.toString());
            FileDeviceController.recordFileDeviceOperation(dbClient, OperationTypeEnum.DELETE_FILE_SNAPSHOT, _isSuccess,
                    event, "", snapshotObj, fsObj, storageObj);
        } catch (Exception e) {
            _logger.error("Caught an exception while trying to updateStatus for VNXeDeleteFileSystemSnapshotJob", e);
            setErrorStatus("Encountered an internal error during file system snapshot delete job status processing : " + e.getMessage());
        } finally {
            super.updateStatus(jobContext);
        }
    }

}
