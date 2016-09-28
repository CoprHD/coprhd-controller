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
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.models.VNXeFileSystem;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.FileDeviceController;

public class VNXeExpandFileSystemJob extends VNXeJob {
    private static final long serialVersionUID = -2093184051245593372L;
    private static final Logger _logger = LoggerFactory.getLogger(VNXeExpandFileSystemJob.class);

    public VNXeExpandFileSystemJob(String jobId, URI storageSystemUri, TaskCompleter taskCompleter) {
        super(jobId, storageSystemUri, taskCompleter, "expandFileSystem");
    }

    /**
     * Called to update the job status when the file system create job completes.
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
            URI fsId = getTaskCompleter().getId();
            FileShare fsObj = dbClient.queryObject(FileShare.class, fsId);
            // If terminal state update storage pool capacity
            if (_status == JobStatus.SUCCESS || _status == JobStatus.FAILED) {
                VNXeJob.updateStoragePoolCapacity(dbClient, vnxeApiClient, fsObj.getPool(), null);
            }

            if (_status == JobStatus.SUCCESS && fsObj != null) {
                updateFS(fsObj, dbClient, logMsgBuilder, vnxeApiClient);
            } else if (_status == JobStatus.FAILED && fsObj != null) {
                logMsgBuilder.append("\n");
                logMsgBuilder.append(String.format(
                        "Task %s failed to expand file system: %s", opId, fsId.toString()));

            } else {
                logMsgBuilder.append(String.format("The file system: %s is not found anymore", fsId));
            }
            _logger.info(logMsgBuilder.toString());
            FileDeviceController.recordFileDeviceOperation(dbClient, OperationTypeEnum.EXPAND_FILE_SYSTEM,
                    _isSuccess, logMsgBuilder.toString(), "", fsObj, String.valueOf(fsObj.getCapacity()));
        } catch (Exception e) {
            _logger.error("Caught an exception while trying to updateStatus for VNXeCreateFileSystemJob", e);
            setErrorStatus("Encountered an internal error during file system create job status processing : " + e.getMessage());
        } finally {
            super.updateStatus(jobContext);
        }
    }

    /**
     * update FileShare after expanded in VNXe
     * 
     * @param fsId fileShare uri in vipr
     * @param dbClient DbClient
     * @param logMsgBuilder string builder for logging
     * @param vnxeApiClient VNXeApiClient
     */
    private void updateFS(FileShare fsObj, DbClient dbClient,
            StringBuilder logMsgBuilder, VNXeApiClient vnxeApiClient) {

        VNXeFileSystem vnxeFS = null;
        vnxeFS = vnxeApiClient.getFileSystemByFSName(fsObj.getName());
        if (vnxeFS != null) {
            fsObj.setCapacity(vnxeFS.getSizeTotal());
            logMsgBuilder.append(String.format(
                    "Expand file system successfully for NativeId: %s, URI: %s", fsObj.getNativeId(),
                    getTaskCompleter().getId()));
            dbClient.persistObject(fsObj);
        } else {
            logMsgBuilder.append("Could not find corresponding file system in the VNXe, using the fs name: ");
            logMsgBuilder.append(fsObj.getName());
        }
    }

}
