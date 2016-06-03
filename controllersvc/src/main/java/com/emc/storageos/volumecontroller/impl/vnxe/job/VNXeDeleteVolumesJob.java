/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.vnxe.job;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;

public class VNXeDeleteVolumesJob extends VNXeJob {

    private static final long serialVersionUID = 4957354153412227028L;
    private static final Logger _logger = LoggerFactory.getLogger(VNXeDeleteVolumesJob.class);

    public VNXeDeleteVolumesJob(List<String> jobIds, URI storageSystemUri,
            TaskCompleter taskCompleter) {
        super(jobIds, storageSystemUri, taskCompleter, "DeleteVolumes");
    }

    /**
     * Called to update the job status when the volumes delete job completes.
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

            // Get list of volumes; get set of storage pool ids to which they belong.
            List<Volume> volumes = dbClient.queryObject(Volume.class, getTaskCompleter().getIds());
            Set<URI> poolURIs = new HashSet<URI>();
            for (Volume volume : volumes) {
                poolURIs.add(volume.getPool());
            }

            VNXeApiClient vnxeApiClient = getVNXeClient(jobContext);

            // If terminal state update storage pool capacity
            if (_status == JobStatus.SUCCESS || _status == JobStatus.FAILED) {
                for (URI poolURI : poolURIs) {
                    VNXeJob.updateStoragePoolCapacity(dbClient, vnxeApiClient, poolURI, null);
                }
            }
            if (_status == JobStatus.SUCCESS) {
                for (Volume volume : volumes) {
                    volume.setInactive(true);
                    volume.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                    dbClient.updateObject(volume);
                    if (logMsgBuilder.length() != 0) {
                        logMsgBuilder.append("\n");
                    }
                    logMsgBuilder.append(String.format("Successfully deleted volume %s", volume.getId()));
                }

            } else if (_status == JobStatus.FAILED) {
                for (URI id : getTaskCompleter().getIds()) {
                    if (logMsgBuilder.length() != 0) {
                        logMsgBuilder.append("\n");
                    }
                    logMsgBuilder.append(String.format("Failed to delete volume: %s", id));
                }

            }
            _logger.info(logMsgBuilder.toString());
        } catch (Exception e) {
            _logger.error("Caught an exception while trying to updateStatus for VNXeDeleteVolumesJob", e);
            setErrorStatus("Encountered an internal error during volume delete job status processing : " + e.getMessage());
        } finally {
            super.updateStatus(jobContext);

        }
    }

}
