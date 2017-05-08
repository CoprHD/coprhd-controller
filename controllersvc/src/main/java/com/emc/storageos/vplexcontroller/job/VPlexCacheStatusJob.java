/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplexcontroller.job;

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;

import com.emc.storageos.volumecontroller.TaskCompleter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.Job;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.impl.JobPollResult;
import com.emc.storageos.vplex.api.VPlexApiClient;
import com.emc.storageos.vplex.api.VPlexApiFactory;
import com.emc.storageos.vplex.api.VPlexCacheStatusInfo;
import com.emc.storageos.vplex.api.VPlexCacheStatusInfo.InvalidateStatus;
import com.emc.storageos.vplexcontroller.completers.CacheStatusTaskCompleter;
import com.emc.storageos.vplexcontroller.utils.VPlexControllerUtils;

/**
 * Job checks the progress of a cache invalidate operation on a VPLEX volume.
 */
public class VPlexCacheStatusJob extends Job implements Serializable {

    // For serialization interface.
    private static final long serialVersionUID = 1L;

    // A reference to the task completer
    private CacheStatusTaskCompleter _taskCompleter;

    // A reference to the poll result.
    private JobPollResult _pollResult = new JobPollResult();

    // A reference to the job status.
    protected JobStatus _status = JobStatus.IN_PROGRESS;

    // A reference to an error description.
    private String _errorDescription = null;

    // Logger reference.
    private static final Logger s_logger = LoggerFactory.getLogger(VPlexCacheStatusJob.class);

    /**
     * Constructor.
     * 
     * @param taskCompleter The task completer.
     */
    public VPlexCacheStatusJob(CacheStatusTaskCompleter taskCompleter) {
        _taskCompleter = taskCompleter;
    }

    /**
     * {@inheritDoc}
     */
    public JobPollResult poll(JobContext jobContext, long trackingPeriodInMillis) {

        s_logger.debug("Polled cache status job");

        // Get the DB client from the job context.
        DbClient dbClient = jobContext.getDbClient();

        // Get the VPLEX volume associated with the cache invalidation job.
        Volume vplexVolume = dbClient.queryObject(Volume.class,
                _taskCompleter.getId());
        String vplexVolumeName = vplexVolume.getDeviceLabel();
        s_logger.debug("VPLEX volume is {}", vplexVolume.getId());

        // Get the VPlex storage system for that VPLEX volume.
        StorageSystem vplexSystem = dbClient.queryObject(StorageSystem.class,
                vplexVolume.getStorageController());
        s_logger.debug("VPlex system is {}", vplexSystem.getId());

        try {
            // Update the job info.
            _pollResult.setJobName(vplexVolumeName);
            _pollResult.setJobId(vplexSystem.getId().toString());
            _pollResult.setJobPercentComplete(0);
            s_logger.debug("Updated poll result");

            // Get the VPlex API client for this VPlex storage system
            // and get the cache invalidation status for the VPLEX volume.
            VPlexApiClient vplexApiClient = VPlexControllerUtils.getVPlexAPIClient(
                    jobContext.getVPlexApiFactory(), vplexSystem, dbClient);
            s_logger.debug("Got VPlex APi Client");
            VPlexCacheStatusInfo cacheStatusInfo = vplexApiClient
                    .getCacheStatus(vplexVolumeName);
            s_logger.debug("Got cache status info from VPlex");

            // Examine the status.
            InvalidateStatus invalidateStatus = cacheStatusInfo.getCacheInvalidateStatus();
            if (InvalidateStatus.SUCCESS.equals(invalidateStatus)) {
                // Completed successfully
                s_logger.info("Cache Invalidate for Volume: {} completed sucessfully",
                        vplexVolume.getId());
                _pollResult.setJobPercentComplete(100);
                _status = JobStatus.SUCCESS;
            } else if (InvalidateStatus.FAILED.equals(invalidateStatus)) {
                // Failed.
                s_logger.info("Cache Invalidate for Volume : {} failed",
                        vplexVolume.getId());
                _pollResult.setJobPercentComplete(100);
                _errorDescription = cacheStatusInfo.getCacheInvalidateFailedMessage();
                _status = JobStatus.FAILED;
            }
        } catch (Exception e) {
            _errorDescription = e.getMessage();
            s_logger.error(String.format(
                    "Unexpected error getting cache status for volume %s on VPlex %s: %s",
                    vplexVolume.getId(), vplexSystem.getId(), _errorDescription), e);
            _status = JobStatus.FAILED;
        } finally {
            s_logger.debug("Updating status {}", _status);
            updateStatus(jobContext);
        }

        _pollResult.setJobStatus(_status);
        _pollResult.setErrorDescription(_errorDescription);

        return _pollResult;
    }

    /**
     * Get the HTTP client for making requests to the VPlex at the endpoint
     * specified in the passed profile.
     * 
     * @param jobContext The job context
     * @param vplexSystem The VPlex storage system
     * 
     * @return A reference to the VPlex API HTTP client.
     * @throws URISyntaxException
     */
    private VPlexApiClient getVPlexAPIClient(JobContext jobContext,
            StorageSystem vplexSystem) throws URISyntaxException {
        // Create the URI to access the VPlex Management Station based
        // on the IP and port for the passed VPlex system.
        URI vplexEndpointURI = new URI("https", null, vplexSystem.getIpAddress(), vplexSystem.getPortNumber(), "/", null, null);
        s_logger.debug("VPlex base URI is {}", vplexEndpointURI.toString());
        VPlexApiFactory vplexApiFactory = jobContext.getVPlexApiFactory();
        s_logger.debug("Got VPlex API factory");
        VPlexApiClient client = vplexApiFactory.getClient(vplexEndpointURI,
                vplexSystem.getUsername(), vplexSystem.getPassword());
        s_logger.debug("Got VPlex API client");
        return client;
    }

    /**
     * Update the status after a poll.
     * 
     * @param jobContext the job context.
     */
    public void updateStatus(JobContext jobContext) {
        try {
            if (_status == JobStatus.SUCCESS) {
                s_logger.debug("Calling task completer for successful job");
                _taskCompleter.ready(jobContext.getDbClient());
            } else if (_status == JobStatus.FAILED) {
                s_logger.debug("Calling task completer for failed job");
                ServiceError error = DeviceControllerErrors.vplex
                        .cacheInvalidateJobFailed(_errorDescription);
                _taskCompleter.error(jobContext.getDbClient(), error);
            }
        } catch (Exception e) {
            s_logger.error("Problem while trying to update status", e);
        }
    }

    public TaskCompleter getTaskCompleter() {
        return _taskCompleter;
    }
}
