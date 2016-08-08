/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.vnxe.job;

import java.net.URI;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.models.VNXeLun;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;

public class VNXeExpandVolumeJob extends VNXeJob {

    private static final long serialVersionUID = 7215022240972292798L;
    private static final Logger _logger = LoggerFactory.getLogger(VNXeExpandVolumeJob.class);

    public VNXeExpandVolumeJob(String jobId, URI storageSystemUri,
            TaskCompleter taskCompleter) {
        super(jobId, storageSystemUri, taskCompleter, "expandVolume");
    }

    /**
     * Called to update the job status when the volume expand job completes.
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
            URI volumeId = getTaskCompleter().getId();
            Volume volumeObj = dbClient.queryObject(Volume.class, volumeId);
            // If terminal state update storage pool capacity
            if (_status == JobStatus.SUCCESS || _status == JobStatus.FAILED) {
                VNXeJob.updateStoragePoolCapacity(dbClient, vnxeApiClient, volumeObj.getPool(), 
                        Arrays.asList(volumeObj.getId().toString()));
            }

            if (_status == JobStatus.SUCCESS && volumeObj != null) {
                updateVolume(volumeObj, dbClient, logMsgBuilder, vnxeApiClient);
            } else if (_status == JobStatus.FAILED && volumeObj != null) {
                logMsgBuilder.append("\n");
                logMsgBuilder.append(String.format(
                        "Task %s failed to expand volume: %s", opId, volumeId.toString()));

            } else {
                logMsgBuilder.append(String.format("The volume: %s is not found anymore", volumeId));
            }
            _logger.info(logMsgBuilder.toString());
        } catch (Exception e) {
            _logger.error("Caught an exception while trying to updateStatus for VNXeExpandVolumeJob", e);
            setErrorStatus("Encountered an internal error during expand volume job status processing : " + e.getMessage());
        } finally {
            super.updateStatus(jobContext);
        }
    }

    /**
     * update Volume after expanded in VNXe
     * 
     * @param volumeObj volume in vipr
     * @param dbClient DbClient
     * @param logMsgBuilder string builder for logging
     * @param vnxeApiClient VNXeApiClient
     */
    private void updateVolume(Volume volumeObj, DbClient dbClient,
            StringBuilder logMsgBuilder, VNXeApiClient vnxeApiClient) {

        VNXeLun vnxeLun = null;
        vnxeLun = vnxeApiClient.getLun(volumeObj.getNativeId());
        if (vnxeLun != null) {
            volumeObj.setCapacity(vnxeLun.getSizeTotal());
            volumeObj.setAllocatedCapacity(vnxeLun.getSizeAllocated());
            volumeObj.setProvisionedCapacity(vnxeLun.getSizeTotal());
            logMsgBuilder.append(String.format(
                    "Expand volume successfully for NativeId: %s, URI: %s", volumeObj.getNativeId(),
                    getTaskCompleter().getId()));
            dbClient.updateObject(volumeObj);
        } else {
            logMsgBuilder.append("Could not find corresponding volume in the VNXe, using the resource ID: ");
            logMsgBuilder.append(volumeObj.getNativeId());
        }
    }

}
