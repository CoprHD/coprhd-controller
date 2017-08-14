/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.vnxe.job;

import java.io.IOException;
import java.net.URI;
import java.util.Calendar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.models.VNXeFileSystemSnap;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.FileDeviceController;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;

public class VNXeCreateFileSystemSnapshotJob extends VNXeJob {

    private static final long serialVersionUID = 7902323147091720210L;
    private static final Logger _logger = LoggerFactory.getLogger(VNXeCreateFileSystemSnapshotJob.class);

    public VNXeCreateFileSystemSnapshotJob(String jobId, URI storageSystemUri, TaskCompleter taskCompleter) {
        super(jobId, storageSystemUri, taskCompleter, "createFileSystemSnapshot");
    }

    /**
     * Called to update the job status when the file system snapshot create job completes.
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

            VNXeApiClient vnxeApiClient = getVNXeClient(jobContext);

            URI snapId = getTaskCompleter().getId();
            Snapshot snapshotObj = dbClient.queryObject(Snapshot.class, snapId);
            URI fsUri = snapshotObj.getParent().getURI();
            FileShare fsObj = dbClient.queryObject(FileShare.class, fsUri);
            String event = null;
            if (_status == JobStatus.SUCCESS && snapshotObj != null) {
                updateSnapshot(snapshotObj, dbClient, logMsgBuilder, vnxeApiClient);
                event = String.format(
                        "Create file system snapshot successfully for URI: %s", getTaskCompleter().getId());
            } else if (_status == JobStatus.FAILED && snapshotObj != null) {
                if (!snapshotObj.getInactive()) {
                    snapshotObj.setInactive(true);
                    dbClient.updateObject(snapshotObj);
                }
                event = String.format(
                        "Task %s failed to create file system snapshot: %s", opId, snapshotObj.getName());
                logMsgBuilder.append("\n");
                logMsgBuilder.append(event);

            } else {
                logMsgBuilder.append(String.format("Could not find the snapshot:%s", snapId.toString()));
            }
            _logger.info(logMsgBuilder.toString());
            FileDeviceController.recordFileDeviceOperation(dbClient, OperationTypeEnum.CREATE_FILE_SYSTEM_SNAPSHOT, _isSuccess,
                    event, "", snapshotObj, fsObj);
        } catch (Exception e) {
            _logger.error("Caught an exception while trying to updateStatus for VNXeCreateFileSystemSnapshotJob", e);
            setErrorStatus("Encountered an internal error during file system snapshot create job status processing : " + e.getMessage());
        } finally {
            super.updateStatus(jobContext);
        }
    }

    /**
     * update snapshot
     * 
     * @param fsId fileShare uri in vipr
     * @param dbClient DbClient
     * @param logMsgBuilder string builder for logging
     * @param vnxeApiClient VNXeApiClient
     */
    private void updateSnapshot(Snapshot snapObj, DbClient dbClient,
            StringBuilder logMsgBuilder, VNXeApiClient vnxeApiClient) {

        VNXeFileSystemSnap vnxeSnap = null;
        vnxeSnap = vnxeApiClient.getSnapshotByName(snapObj.getName());
        if (vnxeSnap != null) {
            snapObj.setInactive(false);
            snapObj.setCreationTime(Calendar.getInstance());
            snapObj.setNativeId(vnxeSnap.getId());
            String path = "/" + snapObj.getName();
            // Set path & mountpath
            snapObj.setMountPath(path);
            snapObj.setPath(path);
            try {
                snapObj.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(dbClient, snapObj));
            } catch (IOException e) {
                logMsgBuilder.append("/n");
                logMsgBuilder.append("Exception while setting snapshot's nativeGuid");
                logMsgBuilder.append(e.getMessage());
            }
            logMsgBuilder.append("/n");
            logMsgBuilder.append(String.format(
                    "Create file system snapshot successfully for NativeId: %s, URI: %s", snapObj.getNativeId(),
                    getTaskCompleter().getId()));
            dbClient.updateObject(snapObj);
        } else {
            logMsgBuilder.append("Could not get newly created snapshot in the VNXe, using the snapshot name: ");
            logMsgBuilder.append(snapObj.getName());
        }
    }

}
