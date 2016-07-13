/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.vnxe.job;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.models.ParametersOut;
import com.emc.storageos.vnxe.models.VNXeBase;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.emc.storageos.vnxe.models.VNXeLun;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;

public class VNXeCreateVolumesJob extends VNXeJob {

    private static final long serialVersionUID = 485930354573814000L;
    private static final Logger _logger = LoggerFactory.getLogger(VNXeCreateFileSystemJob.class);
    private final URI storagePool;
    private final boolean isConsistencyGroup;

    public VNXeCreateVolumesJob(List<String> jobIds, URI storageSystemUri, TaskCompleter taskCompleter,
            URI storagePoolUri, boolean isConsistencyGroup) {
        super(jobIds, storageSystemUri, taskCompleter, "createVolumes");
        this.storagePool = storagePoolUri;
        this.isConsistencyGroup = isConsistencyGroup;
    }

    /**
     * Called to update the job status when the volumes create job completes.
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

            // VNXeCommandJob job = vnxeApiClient.getJob(getJobIds().get(0));

            // If terminal state update storage pool capacity
            if (_status == JobStatus.SUCCESS || _status == JobStatus.FAILED) {
                List<URI> volUris = getTaskCompleter().getIds();
                List<String> volsInPool = new ArrayList<String>();
                for (URI voluri : volUris) {
                    volsInPool.add(voluri.toString());
                }
                VNXeJob.updateStoragePoolCapacity(dbClient, vnxeApiClient, storagePool, volsInPool);
            }
            Calendar now = Calendar.getInstance();
            int volumeCount = 0;
            if (_status == JobStatus.SUCCESS) {
                if (!isConsistencyGroup) {
                    for (String jobId : getJobIds()) {
                        VNXeCommandJob vnxeJob = vnxeApiClient.getJob(jobId);
                        ParametersOut output = vnxeJob.getParametersOut();
                        String nativeId = null;
                        URI volumeId = getTaskCompleter().getId(volumeCount);
                        if (output != null) {
                            VNXeBase storageResource = output.getStorageResource();
                            if (storageResource != null) {
                                nativeId = storageResource.getId();
                            }
                        }
                        processVolume(vnxeApiClient, nativeId, volumeId, dbClient, logMsgBuilder, now);

                        volumeCount++;
                    }
                } else {
                    List<URI> volIds = getTaskCompleter().getIds();
                    processVolumesinConsistencyGroup(vnxeApiClient, volIds, dbClient, logMsgBuilder, now);
                }

            } else if (_status == JobStatus.FAILED) {
                List<URI> volIds = getTaskCompleter().getIds();
                for (URI volId : volIds) {
                    Volume volume = dbClient.queryObject(Volume.class, volId);
                    volume.setInactive(true);
                    dbClient.updateObject(volume);
                    if (logMsgBuilder.length() != 0) {
                        logMsgBuilder.append("\n");
                    }
                    logMsgBuilder.append(String.format(
                            "Task %s failed to create volume: %s", opId, volId));
                }

            }
            _logger.info(logMsgBuilder.toString());
        } catch (Exception e) {
            _logger.error("Caught an exception while trying to updateStatus for VNXeCreateVolumesJob", e);
            setErrorStatus("Encountered an internal error during volume create job status processing : " + e.getMessage());
        } finally {
            super.updateStatus(jobContext);

        }
    }

    private void processVolume(VNXeApiClient apiClient, String nativeId, URI volumeId,
            DbClient dbClient, StringBuilder logMsgBuilder, Calendar creationTime) throws IOException, DeviceControllerException {
        Volume volume = dbClient.queryObject(Volume.class, volumeId);
        volume.setCreationTime(creationTime);
        VNXeLun vnxeLun = apiClient.getLun(nativeId);

        if (vnxeLun != null) {
            updateVolume(volume, vnxeLun, dbClient);
            if (logMsgBuilder.length() != 0) {
                logMsgBuilder.append("\n");
            }
            logMsgBuilder.append(String.format(
                    "Created volume successfully .. NativeId: %s, URI: %s", nativeId, volumeId.toString()));
        } else {
            _logger.error("Could not find the lun: {} in the array", nativeId);
        }

    }

    private void processVolumesinConsistencyGroup(VNXeApiClient apiClient, List<URI> volIds,
            DbClient dbClient, StringBuilder logMsgBuilder, Calendar creationTime) throws IOException {
        BlockConsistencyGroup group = null;
        for (URI volId : volIds) {
            Volume volume = dbClient.queryObject(Volume.class, volId);
            volume.setCreationTime(creationTime);
            if (group == null) {
                group = dbClient.queryObject(BlockConsistencyGroup.class, volume.getConsistencyGroup());
            }
            String cgId = null;
            if (apiClient.isUnityClient()) {
                String cgName = volume.getReplicationGroupInstance();
                cgId = apiClient.getConsistencyGroupIdByName(cgName);
            } else {
                cgId = group.getCgNameOnStorageSystem(volume.getStorageController());
            }
            VNXeLun vnxeLun = apiClient.getLunByLunGroup(cgId, volume.getNativeGuid());
            if (vnxeLun != null) {
                updateVolume(volume, vnxeLun, dbClient);
                dbClient.updateObject(volume);

                if (logMsgBuilder.length() != 0) {
                    logMsgBuilder.append("\n");
                }
                logMsgBuilder.append(String.format(
                        "Created volume successfully .. NativeId: %s, URI: %s", vnxeLun.getId(), volId.toString()));
            } else {
                _logger.error("Could not find the lun:{} in the array", volume.getNativeGuid());
            }
        }
    }

    private void updateVolume(Volume volume, VNXeLun vnxeLun, DbClient dbClient) throws IOException {
        volume.setWWN(vnxeLun.getWwn());
        volume.setInactive(false);
        volume.setProvisionedCapacity(vnxeLun.getSizeTotal());
        volume.setAllocatedCapacity(vnxeLun.getSizeAllocated());
        volume.setNativeId(vnxeLun.getId());
        volume.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(dbClient, volume));
        volume.setDeviceLabel(vnxeLun.getName());
        dbClient.updateObject(volume);
    }
}
