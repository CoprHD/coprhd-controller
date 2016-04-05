/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.vnxunity.job;

import java.io.IOException;
import java.net.URI;
import java.util.Calendar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.QuotaDirectory;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.models.VNXUnityQuotaConfig;
import com.emc.storageos.vnxe.models.VNXUnityTreeQuota;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.FileDeviceController;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeJob;

public class VNXUnityCreateFileSystemQuotaDirectoryJob extends VNXeJob {
    /**
     * 
     */
    private static final long serialVersionUID = -8573089837104542244L;
    private static final Logger _logger = LoggerFactory.getLogger(VNXUnityCreateFileSystemQuotaDirectoryJob.class);

    public VNXUnityCreateFileSystemQuotaDirectoryJob(String jobId, URI storageSystemUri, TaskCompleter taskCompleter) {
        super(jobId, storageSystemUri, taskCompleter, "createFileSystemQuotaDirectory");
    }

    /**
     * Called to update the job status when the file system Quota Directory create job completes.
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

            VNXeApiClient vnxeApiClient = getVNXeClient(jobContext);

            URI quotaId = getTaskCompleter().getId();
            QuotaDirectory quotaObj = dbClient.queryObject(QuotaDirectory.class, quotaId);
            URI fsUri = quotaObj.getParent().getURI();
            FileShare fsObj = dbClient.queryObject(FileShare.class, fsUri);
            String event = null;
            if (_status == JobStatus.SUCCESS && quotaObj != null) {
                updateQuota(quotaObj, dbClient, logMsgBuilder, vnxeApiClient);
                event = String.format(
                        "Create file system quota directory successfully for URI: %s", getTaskCompleter().getId());
            } else if (_status == JobStatus.FAILED && quotaObj != null) {
                if (!quotaObj.getInactive()) {
                    quotaObj.setInactive(true);
                    dbClient.updateObject(quotaObj);
                }
                event = String.format(
                        "Task %s failed to create file system quota directory: %s", opId, quotaObj.getName());
                logMsgBuilder.append("\n");
                logMsgBuilder.append(event);

            } else {
                logMsgBuilder.append(String.format("Could not find the quota directory:%s", quotaId.toString()));
            }
            _logger.info(logMsgBuilder.toString());
            FileDeviceController.recordFileDeviceOperation(dbClient, OperationTypeEnum.CREATE_FILE_SYSTEM_QUOTA_DIR, _isSuccess,
                    event, "", quotaObj, fsObj);
        } catch (Exception e) {
            _logger.error("Caught an exception while trying to updateStatus for VNXeCreateFileSystemQuotaDirectoryJob", e);
            setErrorStatus("Encountered an internal error during file system quota create job status processing : " + e.getMessage());
        } finally {
            super.updateStatus(jobContext);
        }
    }

    /**
     * update quota
     * 
     * @param fsId
     *            fileShare uri in vipr
     * @param dbClient
     *            DbClient
     * @param logMsgBuilder
     *            string builder for logging
     * @param vnxeApiClient
     *            VNXeApiClient
     */
    private void updateQuota(QuotaDirectory quotaObj, DbClient dbClient,
            StringBuilder logMsgBuilder, VNXeApiClient vnxeApiClient) {

        VNXUnityTreeQuota vnxUnityQuota = null;
        vnxUnityQuota = vnxeApiClient.getQuotaByName(quotaObj.getParent().getName(), quotaObj.getName());
        if (vnxUnityQuota != null) {
            VNXUnityQuotaConfig vnxUnityQuotaConfig = vnxeApiClient.getQuotaConfigById(vnxUnityQuota.getQuotaConfigId());
            quotaObj.setInactive(false);
            quotaObj.setCreationTime(Calendar.getInstance());
            quotaObj.setNativeId(vnxUnityQuota.getId());
            String path = "/" + quotaObj.getName();
            quotaObj.setSize(vnxUnityQuota.getHardLimit());
            // converting softlimit back to percentage, 0.5 is for rounding off
            quotaObj.setSoftLimit((int) (vnxUnityQuota.getSoftLimit() * 100.0 / vnxUnityQuota.getHardLimit() + 0.5));
            // converting grace period back into days
            quotaObj.setSoftGrace(vnxUnityQuotaConfig.getGracePeriod() / (60 * 60 * 24));
            quotaObj.setPath(path);
            try {
                quotaObj.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(dbClient, quotaObj, quotaObj.getParent().getName()));
            } catch (IOException e) {
                logMsgBuilder.append("/n");
                logMsgBuilder.append("Exception while setting quota's nativeGuid");
                logMsgBuilder.append(e.getMessage());
            }
            logMsgBuilder.append("/n");
            logMsgBuilder.append(String.format(
                    "Create file system quota directory successfully for NativeId: %s, URI: %s", quotaObj.getNativeId(),
                    getTaskCompleter().getId()));
            dbClient.updateObject(quotaObj);
        } else {
            logMsgBuilder.append("Could not get newly created quota directory in the VNXe, using the quota name: ");
            logMsgBuilder.append(quotaObj.getName());
        }
    }

}
