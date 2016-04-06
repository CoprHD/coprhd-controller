/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.vnxunity.job;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.QuotaDirectory;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.FileDeviceController;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeJob;

public class VNXUnityDeleteFileSystemQuotaDirectoryJob extends VNXeJob {
    /**
     * 
     */
    private static final long serialVersionUID = -8484458771124565318L;
    private static final Logger _logger = LoggerFactory.getLogger(VNXUnityCreateFileSystemQuotaDirectoryJob.class);

    public VNXUnityDeleteFileSystemQuotaDirectoryJob(String jobId, URI storageSystemUri, TaskCompleter taskCompleter) {
        super(jobId, storageSystemUri, taskCompleter, "deleteFileSystemQuotaDirectory");
    }

    /**
     * Called to update the job status when the file system Quota Directory delete job completes.
     * 
     * @param jobContext
     *            The job context.
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

            URI quotaId = getTaskCompleter().getId();
            QuotaDirectory quotaObj = dbClient.queryObject(QuotaDirectory.class, quotaId);
            URI fsUri = quotaObj.getParent().getURI();
            FileShare fsObj = dbClient.queryObject(FileShare.class, fsUri);
            StorageSystem storageObj = dbClient.queryObject(StorageSystem.class, getStorageSystemUri());
            String event = null;
            if (_status == JobStatus.SUCCESS && quotaObj != null) {
                quotaObj.setInactive(true);
                dbClient.updateObject(quotaObj);
                event = String.format("Deleted file system quota directory %s successfully", quotaObj.getName());
                logMsgBuilder.append("\n");
                logMsgBuilder.append(event);
            } else if (_status == JobStatus.FAILED && quotaObj != null) {
                event = String.format(
                        "Task %s failed to delete file system quota directory: %s", opId, quotaObj.getName());
                logMsgBuilder.append("\n");
                logMsgBuilder.append(event);
            } else {
                event = "File sytem quota directory has been deleted";
                logMsgBuilder.append(String.format("Could not find the quota directory: %s", quotaId));
            }
            _logger.info(logMsgBuilder.toString());
            FileDeviceController.recordFileDeviceOperation(dbClient, OperationTypeEnum.DELETE_FILE_SYSTEM_QUOTA_DIR, _isSuccess,
                    event, "", quotaObj, fsObj, storageObj);
        } catch (Exception e) {
            _logger.error("Caught an exception while trying to updateStatus for VNXUnityDeleteFileSystemQuotaDirectoryJob", e);
            setErrorStatus("Encountered an internal error during file system quota delete job status processing : " + e.getMessage());
        } finally {
            super.updateStatus(jobContext);
        }
    }

}
