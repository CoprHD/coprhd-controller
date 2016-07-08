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
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.emc.storageos.vnxe.models.VNXeFileSystem;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.FileDeviceController;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;

/**
 * This class is for create file system job
 */
public class VNXeCreateFileSystemJob extends VNXeJob {

    private static final long serialVersionUID = -6248728897770826163L;
    private static final Logger _logger = LoggerFactory.getLogger(VNXeCreateFileSystemJob.class);
    private URI _storagePool;

    public VNXeCreateFileSystemJob(String jobId, URI storageSystemUri, TaskCompleter taskCompleter,
            URI storagePoolUri) {
        super(jobId, storageSystemUri, taskCompleter, "createFileSystem");
        _storagePool = storagePoolUri;
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

            VNXeCommandJob job = vnxeApiClient.getJob(getJobIds().get(0));

            // If terminal state update storage pool capacity
            if (_status == JobStatus.SUCCESS || _status == JobStatus.FAILED) {
                VNXeJob.updateStoragePoolCapacity(dbClient, vnxeApiClient, _storagePool, null);
            }
            URI fsId = getTaskCompleter().getId();
            FileShare fsObj = dbClient.queryObject(FileShare.class, fsId);
            if (_status == JobStatus.SUCCESS && fsObj != null) {
                _isSuccess = true;
                updateFS(fsObj, dbClient, job, logMsgBuilder, vnxeApiClient);
            } else if (_status == JobStatus.FAILED && fsObj != null) {
                logMsgBuilder.append("\n");
                logMsgBuilder.append(String.format(
                        "Task %s failed to create file system: %s", opId, fsId.toString()));
                fsObj.setInactive(true);
                dbClient.persistObject(fsObj);

            } else {
                logMsgBuilder.append(String.format("The file system: %s is not found anymore", fsId));
            }
            _logger.info(logMsgBuilder.toString());

            FileDeviceController.recordFileDeviceOperation(dbClient, OperationTypeEnum.CREATE_FILE_SYSTEM, _isSuccess, "", "", fsObj);
        } catch (Exception e) {
            _logger.error("Caught an exception while trying to updateStatus for VNXeCreateFileSystemJob", e);
            setErrorStatus("Encountered an internal error during file system create job status processing : " + e.getMessage());
        } finally {
            super.updateStatus(jobContext);
        }
    }

    /**
     * update FileShare after it is created in VNXe
     * 
     * @param fsId fileShare uri in vipr
     * @param dbClient DbClient
     * @param job VNXeCommandJob
     * @param now creation time.
     * @param logMsgBuilder string builder for logging
     * @param vnxeApiClient VNXeApiClient
     */
    private void updateFS(FileShare fsObj, DbClient dbClient, VNXeCommandJob job,
            StringBuilder logMsgBuilder, VNXeApiClient vnxeApiClient) {
        try {
            fsObj.setCreationTime(Calendar.getInstance());
            // TODO: currently, KH API does not return resourceId in the job. get around it for now.
            // String resourceId = job.getResourceId();
            VNXeFileSystem vnxeFS = null;
            /*
             * if (resourceId == null || resourceId.isEmpty()) {
             * _logger.info("The job did not return the resourceId for created file system.");
             * _logger.info("Getting the fs info by its name: " + fsObj.getName());
             * vnxeFS = vnxeApiClient.getFileSystemByFSName(fsObj.getName());
             * } else {
             * vnxeFS = vnxeApiClient.getFileSystemByStorageResourceId(resourceId);
             * }
             */
            vnxeFS = vnxeApiClient.getFileSystemByFSName(fsObj.getName());
            if (vnxeFS != null) {
                fsObj.setNativeId(vnxeFS.getId());
                fsObj.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(dbClient, fsObj));
                fsObj.setInactive(false);
                String mountPath = "/" + fsObj.getName();
                fsObj.setMountPath(mountPath);
                fsObj.setPath(mountPath);

                dbClient.persistObject(fsObj);
                if (logMsgBuilder.length() != 0) {
                    logMsgBuilder.append("\n");
                }
                logMsgBuilder.append(String.format(
                        "Created file system successfully .. NativeId: %s, URI: %s", fsObj.getNativeId(),
                        getTaskCompleter().getId()));

            } else {
                logMsgBuilder.append("Could not find corresponding file system in the VNXe, using the fs name: ");
                logMsgBuilder.append(fsObj.getName());
            }
        } catch (IOException e) {
            _logger.error("Caught an exception while trying to update file system attributes", e);
        }

    }

}
