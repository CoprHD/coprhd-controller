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
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.FileDeviceController;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeJob;

public class VNXUnityUpdateFileSystemQuotaDirectoryJob extends VNXeJob {
    /**
     * 
     */
    private static final long serialVersionUID = 3234048635008996400L;
    private static final Logger _logger = LoggerFactory.getLogger(VNXUnityUpdateFileSystemQuotaDirectoryJob.class);

    public VNXUnityUpdateFileSystemQuotaDirectoryJob(String jobId, URI storageSystemUri, TaskCompleter taskCompleter) {
        super(jobId, storageSystemUri, taskCompleter, "updateQuotaDirectory");
    }

    /**
     * Called to update the job status when the file system create job completes.
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
            String event = null;
            if (_status == JobStatus.SUCCESS && quotaObj != null) {
                event = String.format(
                        "update file system quota directory successfully for URI: %s", getTaskCompleter().getId());
            } else if (_status == JobStatus.FAILED && quotaObj != null) {
                if (!quotaObj.getInactive()) {
                    quotaObj.setInactive(true);
                    dbClient.updateObject(quotaObj);
                }
                event = String.format(
                        "Task %s failed to update file system quota directory: %s", opId, quotaObj.getName());
                logMsgBuilder.append("\n");
                logMsgBuilder.append(event);

            } else {
                logMsgBuilder.append(String.format("Could not find the quota directory:%s", quotaId.toString()));
            }
            _logger.info(logMsgBuilder.toString());
            FileDeviceController.recordFileDeviceOperation(dbClient, OperationTypeEnum.UPDATE_FILE_SYSTEM_QUOTA_DIR, _isSuccess,
                    event, "", quotaObj, fsObj);
        } catch (Exception e) {
            _logger.error("Caught an exception while trying to updateStatus for VNXUnityUpdateFileSystemQuotaDirectoryJob", e);
            setErrorStatus("Encountered an internal error during file system quota update job status processing : " + e.getMessage());
        } finally {
            super.updateStatus(jobContext);
        }
    }
}
