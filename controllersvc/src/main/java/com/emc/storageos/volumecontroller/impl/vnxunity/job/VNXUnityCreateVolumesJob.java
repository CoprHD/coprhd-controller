/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.vnxunity.job;

import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.models.ParametersOut;
import com.emc.storageos.vnxe.models.VNXeBase;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.emc.storageos.vnxe.models.VNXeLun;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeJob;

public class VNXUnityCreateVolumesJob extends VNXeJob {

    private static final Logger logger = LoggerFactory.getLogger(VNXUnityCreateVolumesJob.class);
    private final Map<String, List<URI>> jobIdsMap;
    private final URI storagePool;

    public VNXUnityCreateVolumesJob(Map<String, List<URI>> jobIdsMap, List<String> jobIds, URI storageSystemUri,
            TaskCompleter taskCompleter,
            URI storagePoolUri) {
        super(jobIds, storageSystemUri, taskCompleter, "createVolumes");
        this.storagePool = storagePoolUri;
        this.jobIdsMap = jobIdsMap;
    }

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
            if (_status == JobStatus.SUCCESS) {

                for (Map.Entry<String, List<URI>> jobEntry : jobIdsMap.entrySet()) {
                    VNXeCommandJob vnxeJob = vnxeApiClient.getJob(jobEntry.getKey());
                    ParametersOut output = vnxeJob.getParametersOut();
                    String nativeId = null;
                    List<URI> volumeURIs = jobEntry.getValue();
                    List<Volume> volumes = dbClient.queryObject(Volume.class, volumeURIs);
                    Volume vol = volumes.get(0);
                    if (NullColumnValueGetter.isNotNullValue(vol.getReplicationGroupInstance())) {
                        // volumes in CG
                        processVolumesinCG(vnxeApiClient, volumes, dbClient, logMsgBuilder, now);
                    } else {
                        if (output != null) {
                            VNXeBase storageResource = output.getStorageResource();
                            if (storageResource != null) {
                                nativeId = storageResource.getId();
                            }
                        } else {
                            logger.error(String.format("The volume native id is not founded"));
                            vol.setInactive(true);
                        }
                        processVolume(vnxeApiClient, nativeId, vol, dbClient, logMsgBuilder, now);
                    }
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
            logger.info(logMsgBuilder.toString());
        } catch (Exception e) {
            logger.error("Caught an exception while trying to updateStatus for VNXeCreateVolumesJob", e);
            setErrorStatus("Encountered an internal error during volume create job status processing : " + e.getMessage());
        } finally {
            super.updateStatus(jobContext);

        }
    }

    /**
     * Update volumes in CG with native ID and WWN
     * 
     * @param apiClient - Unity ApiClient
     * @param volumes - Volumes to be updated
     * @param dbClient - DbClient
     * @param logMsgBuilder - Logging message builder
     * @param creationTime - Creation time
     * @throws Exception
     */
    private void processVolumesinCG(VNXeApiClient apiClient, List<Volume> volumes,
            DbClient dbClient, StringBuilder logMsgBuilder, Calendar creationTime) throws Exception {

        for (Volume volume : volumes) {

            // If the volume is inactive, the job failed while the asynchronous work was running.
            // Honor that the job failed and do not commit the volume, which would make it active again.
            if (volume.getInactive()) {
                if (logMsgBuilder.length() != 0) {
                    logMsgBuilder.append("\n");
                }
                logMsgBuilder.append(String.format(
                        "Create volume job failed and volume set to inactive. Volume was likely created successfully and will be left on the array for ingestion. lable: %s, URI: %s",
                        volume.getLabel(),
                        volume.getId()));
                setErrorStatus(logMsgBuilder.toString());
                return;
            }

            volume.setCreationTime(creationTime);
            String cgName = volume.getReplicationGroupInstance();
            String cgId = apiClient.getConsistencyGroupIdByName(cgName);
            VNXeLun vnxeLun = apiClient.getLunByLunGroup(cgId, volume.getNativeGuid());
            if (vnxeLun != null) {
                updateVolume(volume, vnxeLun, dbClient);
                dbClient.updateObject(volume);

                if (logMsgBuilder.length() != 0) {
                    logMsgBuilder.append("\n");
                }
                logMsgBuilder.append(String.format(
                        "Created volume successfully .. NativeId: %s, URI: %s", vnxeLun.getId(), volume.getLabel()));
            } else {
                logger.error("Could not find the lun:{} in the array", volume.getNativeGuid());
            }
        }
    }

    /**
     * Update the volume with native Id and WWN
     * 
     * @param apiClient - Unity api client
     * @param nativeId - Native Id for the volume
     * @param volume - The volume to be updated
     * @param dbClient - DbClient
     * @param logMsgBuilder - Logging message builder
     * @param creationTime - Creation time
     * @throws Exception
     */
    private void processVolume(VNXeApiClient apiClient, String nativeId, Volume volume,
            DbClient dbClient, StringBuilder logMsgBuilder, Calendar creationTime) throws Exception {
        // If the volume is inactive, the job failed while the asynchronous work was running.
        // Honor that the job failed and do not commit the volume, which would make it active again.
        if (volume.getInactive()) {
            if (logMsgBuilder.length() != 0) {
                logMsgBuilder.append("\n");
            }
            logMsgBuilder.append(String.format(
                    "Create volume job failed and volume set to inactive. Volume was likely created successfully and will be left on the array for ingestion. NativeId: %s, URI: %s",
                    nativeId,
                    volume.getId()));
            setErrorStatus(logMsgBuilder.toString());
            return;
        }

        volume.setCreationTime(creationTime);
        VNXeLun vnxeLun = apiClient.getLun(nativeId);

        if (vnxeLun != null) {
            updateVolume(volume, vnxeLun, dbClient);
            if (logMsgBuilder.length() != 0) {
                logMsgBuilder.append("\n");
            }
            logMsgBuilder.append(String.format(
                    "Created volume successfully .. NativeId: %s, URI: %s", nativeId, volume.getLabel()));
        } else {
            logger.error("Could not find the lun: {} in the array", nativeId);
        }

    }

    /**
     * Update the volume with the Unity Lun
     * 
     * @param volume - The volume to be updated
     * @param vnxeLun - The Unity Lun
     * @param dbClient - DbClient
     * @throws Exception
     */
    private void updateVolume(Volume volume, VNXeLun vnxeLun, DbClient dbClient) throws Exception {
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
