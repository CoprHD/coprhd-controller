/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller.impl.cinder.job;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cinder.CinderConstants;
import com.emc.storageos.cinder.CinderEndPointInfo;
import com.emc.storageos.cinder.api.CinderApi;
import com.emc.storageos.cinder.model.VolumeShowResponse;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.cinder.CinderUtils;

/**
 * Abstract Implementation for cinder volume creation status
 * check and update the corresponding objects in the Database.
 * 
 */
public abstract class AbstractCinderVolumeCreateJob extends CinderJob
{

    private static final long serialVersionUID = -5488768452792486576L;
    private static final Logger logger = LoggerFactory.getLogger(AbstractCinderVolumeCreateJob.class);
    private URI storagePoolUri = null;
    private Map<String, URI> volumeIds = null;

    /**
     * @param jobId
     * @param jobName
     * @param storageSystem
     * @param componentType
     * @param ep
     * @param taskCompleter
     */
    public AbstractCinderVolumeCreateJob(String jobId, String jobName,
            URI storageSystem, String componentType,
            CinderEndPointInfo ep, TaskCompleter taskCompleter,
            URI storagePoolUri, Map<String, URI> volumeIds)
    {
        super(jobId, jobName, storageSystem, componentType, ep, taskCompleter);
        this.storagePoolUri = storagePoolUri;
        this.volumeIds = volumeIds;
    }

    /**
     * Called to update the job status when the volume create job completes.
     * This is common update code for volume create operations.
     * 
     * @param jobContext The job context.
     */
    @Override
    public void updateStatus(JobContext jobContext) throws Exception
    {
        DbClient dbClient = jobContext.getDbClient();
        try
        {
            // Do nothing if the job is not completed yet
            if (status == JobStatus.IN_PROGRESS)
            {
                return;
            }

            String opId = getTaskCompleter().getOpId();
            StringBuilder logMsgBuilder = new StringBuilder(String.format("Updating status of job %s to %s", opId, status.name()));
            StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, getStorageSystemURI());

            CinderApi cinderApi = jobContext.getCinderApiFactory().getApi(storageSystem.getActiveProviderURI(), getEndPointInfo());

            // If terminal state update storage pool capacity and remove reservation for volumes capacity
            // from pool's reserved capacity map.
            StoragePool storagePool = null;
            if (status == JobStatus.SUCCESS || status == JobStatus.FAILED)
            {
                storagePool = dbClient.queryObject(StoragePool.class, storagePoolUri);
                StringMap reservationMap = storagePool.getReservedCapacityMap();
                for (URI volumeId : getTaskCompleter().getIds())
                {
                    // remove from reservation map
                    reservationMap.remove(volumeId.toString());
                }
                dbClient.persistObject(storagePool);
            }

            if (status == JobStatus.SUCCESS)
            {
                List<URI> volumes = new ArrayList<URI>();
                Calendar now = Calendar.getInstance();
                URI volumeId = getTaskCompleter().getId();
                volumes.add(volumeId);

                for (Map.Entry<String, URI> entry : volumeIds.entrySet()) {
                    VolumeShowResponse volumeDetails = cinderApi.showVolume(entry.getKey());
                    processVolume(entry.getValue(), volumeDetails, dbClient, now, logMsgBuilder);

                    // Adjust the storage pool's capacity
                    CinderUtils.updateStoragePoolCapacity(dbClient, cinderApi, storagePool, volumeDetails.volume.size, false);
                }

            }
            else if (status == JobStatus.FAILED)
            {
                for (URI id : getTaskCompleter().getIds())
                {
                    logMsgBuilder.append("\n");
                    logMsgBuilder.append(String.format(
                            "Task %s failed to create volume: %s", opId, id.toString()));
                    Volume volume = dbClient.queryObject(Volume.class, id);
                    volume.setInactive(true);
                    dbClient.persistObject(volume);
                }

            }
            logger.info(logMsgBuilder.toString());
        } catch (Exception e)
        {
            logger.error("Caught an exception while trying to updateStatus for CinderCreateVolumeJob", e);
            setErrorStatus("Encountered an internal error during volume create job status processing : " + e.getMessage());
        } finally
        {
            super.updateStatus(jobContext);
        }
    }

    @Override
    protected boolean isJobSucceeded(String currentStatus) {
        return (CinderConstants.ComponentStatus.AVAILABLE.getStatus().equalsIgnoreCase(currentStatus));
    }

    @Override
    protected boolean isJobFailed(String currentStatus) {
        return (CinderConstants.ComponentStatus.ERROR.getStatus().equalsIgnoreCase(currentStatus));
    }

    /**
     * Process the Volume details received from cinder by setting the
     * volume attributes in Volume DB object.
     * 
     * @param volume
     * @param volDetails
     * @param now
     */
    private void processVolume(URI volumeId, VolumeShowResponse volDetails, DbClient dbClient, Calendar now, StringBuilder logMsgBuilder)
    {
        try
        {
            Volume volume = dbClient.queryObject(Volume.class, volumeId);
            volume.setCreationTime(now);
            volume.setNativeId(volDetails.volume.id);
            volume.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(dbClient, volume));
            long capacityInBytes = Long.valueOf(volDetails.volume.size) * 1024L * 1024L * 1024L;
            volume.setAllocatedCapacity(capacityInBytes);
            // @TODO currently we don't get wwn for volumes. Setting the id generated as wwn
            volume.setWWN(volDetails.volume.id);
            volume.setProvisionedCapacity(capacityInBytes);
            volume.setInactive(false);
            dbClient.persistObject(volume);

            if (logMsgBuilder.length() != 0)
            {
                logMsgBuilder.append("\n");
            }
            logMsgBuilder.append(String.format(
                    "Created volume successfully .. NativeId: %s, URI: %s", volume.getNativeId(),
                    getTaskCompleter().getId()));
        } catch (IOException e)
        {
            logger.error("Caught an exception while trying to update volume attributes", e);
        }
    }

}
