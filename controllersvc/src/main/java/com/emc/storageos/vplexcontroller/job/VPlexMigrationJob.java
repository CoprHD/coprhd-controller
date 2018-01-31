/*
 * Copyright (c) 2013 EMC Corporation
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
import com.emc.storageos.db.client.model.Migration;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.volumecontroller.Job;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.impl.JobPollResult;
import com.emc.storageos.vplex.api.VPlexApiClient;
import com.emc.storageos.vplex.api.VPlexApiFactory;
import com.emc.storageos.vplex.api.VPlexMigrationInfo;
import com.emc.storageos.vplexcontroller.VPlexControllerUtils;
import com.emc.storageos.vplexcontroller.completers.MigrationTaskCompleter;

/**
 * Job keeps track of the status of a VPlex migration.
 */
public class VPlexMigrationJob extends Job implements Serializable {

    // How long we will wait for a successful check on the
    // migration status.
    private static final int MAX_TIME_FOR_SUCCESSFUL_STATUS_CHECK_MS = 3600000;

    // For serialization interface.
    private static final long serialVersionUID = 1L;

    // A reference to the task completer
    private MigrationTaskCompleter _taskCompleter;

    // A reference to the poll result.
    private JobPollResult _pollResult = new JobPollResult();

    // A reference to the job status.
    protected JobStatus _status = JobStatus.IN_PROGRESS;

    // A reference to an error description.
    private String _errorDescription = null;

    // The maximum number of times we will try and get the migration status
    // before we give up and fail. It is determined by dividing the maximum
    // time we will wait by the interval at which the job is polled.
    private Integer _maxRetries = null;

    // Keeps track of how many times we have tried to get the migration status
    // since the last successful attempt to get the status.
    private int _retryCount = 0;

    // Logger reference.
    private static final Logger s_logger = LoggerFactory.getLogger(VPlexMigrationJob.class);

    /**
     * Constructor.
     * 
     * @param taskCompleter The task completer.
     */
    public VPlexMigrationJob(MigrationTaskCompleter taskCompleter) {
        _taskCompleter = taskCompleter;
    }

    /**
     * {@inheritDoc}
     */
    public JobPollResult poll(JobContext jobContext, long trackingPeriodInMillis) {

        s_logger.info("Polled migration job");

        Migration migration = null;
        StorageSystem vplexSystem = null;
        try {
            // Set max retries based on the tracking period. The default
            // tracking period is 15 seconds, and we'll wait for an hour
            // to make a successful status check before failing the job.
            // Therefore, the default max retries is 240.
            if (_maxRetries == null) {
                if (trackingPeriodInMillis < MAX_TIME_FOR_SUCCESSFUL_STATUS_CHECK_MS) {
                    _maxRetries = new Integer(
                            Math.round(MAX_TIME_FOR_SUCCESSFUL_STATUS_CHECK_MS
                                    / trackingPeriodInMillis));
                } else {
                    _maxRetries = new Integer(1);
                }
            }

            // Get the DB client from the job context.
            DbClient dbClient = jobContext.getDbClient();

            // Get the migration associated with this job.
            migration = dbClient.queryObject(Migration.class,
                    _taskCompleter.getId());
            String migrationName = migration.getLabel();
            s_logger.info("Migration is {}", migration.getId());

            // Get the virtual volume associated with the migration
            // and then get the VPlex storage system for that virtual
            // volume.
            Volume virtualVolume = dbClient.queryObject(Volume.class,
                    migration.getVolume());
            s_logger.info("Virtual volume is {}", virtualVolume.getId());

            vplexSystem = dbClient.queryObject(StorageSystem.class,
                    virtualVolume.getStorageController());
            s_logger.info("VPlex system is {}", vplexSystem.getId());

            // Get the VPlex API client for this VPlex storage system
            // and get the latest info for the migration.
            VPlexApiClient vplexApiClient = VPlexControllerUtils.getVPlexAPIClient(
                    jobContext.getVPlexApiFactory(), vplexSystem, dbClient);

            s_logger.debug("Got VPlex APi Client");
            VPlexMigrationInfo migrationInfo = vplexApiClient
                    .getMigrationInfo(migrationName);
            s_logger.info("Got migration info from VPlex");

            // Update the migration in the database to reflect the
            // current status and percent done.
            String migrationStatus = migrationInfo.getStatus();
            s_logger.debug("Migration status is {}", migrationStatus);
            migration.setMigrationStatus(migrationStatus);
            int percentDone = getMigrationPercentDone(migrationInfo.getPercentageDone());
            s_logger.debug("Migration percent done is {}", percentDone);
            migration.setPercentDone(String.valueOf(percentDone));
            dbClient.persistObject(migration);

            // Update the job info.
            _pollResult.setJobName(migrationName);
            _pollResult.setJobId(virtualVolume.getId().toString());
            _pollResult.setJobPercentComplete(percentDone);
            s_logger.info("Updated poll result");

            // Examine the status.
            if (VPlexMigrationInfo.MigrationStatus.COMPLETE.getStatusValue().equals(
                    migrationStatus)) {
                // Completed successfully
                s_logger.info("Migration: {} completed sucessfully", migration.getId());
                _status = JobStatus.SUCCESS;
            } else if (VPlexMigrationInfo.MigrationStatus.COMMITTED.getStatusValue()
                    .equals(migrationStatus)) {
                // The migration job completed and somehow it was committed
                // outside the scope of the workflow that created the
                // migration job. We return success here to ensure that there
                // is no rollback in the workflow that could end up deleting
                // the target volume of the migration.
                s_logger.info("Migration: {} completed and was committed",
                        migration.getId());
                _status = JobStatus.SUCCESS;
            } else if (VPlexMigrationInfo.MigrationStatus.CANCELLED.getStatusValue()
                    .equals(migrationStatus)) {
                // The migration job was cancelled outside the scope of the
                // workflow that created the migration job.
                _errorDescription = "The migration was cancelled";
                s_logger.info("Migration: {} was cancelled prior to completion",
                        migration.getId());
                _status = JobStatus.FAILED;
            } else if (VPlexMigrationInfo.MigrationStatus.ERROR.getStatusValue().equals(
                    migrationStatus)) {
                // The migration failed.
                _errorDescription = "The migration failed";
                s_logger.error("Migration {} failed prior to completion",
                        migration.getId());
                _status = JobStatus.FAILED;
            }

            // We had a successful check of the status. Reset the retry
            // count in case the job is still in progress and the next
            // attempt to check the status fails.
            _retryCount = 0;
        } catch (Exception e) {
            s_logger.error(String.format(
                    "Unexpected error getting status of migration %s on VPlex %s: %s",
                    (migration != null ? migration.getId() : "null"),
                    (vplexSystem != null ? vplexSystem.getId() : "null"),
                    _errorDescription), e);
            if (++_retryCount > _maxRetries) {
                _errorDescription = e.getMessage();
                _status = JobStatus.FAILED;
            }
        } finally {
            s_logger.info("Updating status {}", _status);
            updateStatus(jobContext);
        }

        _pollResult.setJobStatus(_status);
        _pollResult.setErrorDescription(_errorDescription);
        return _pollResult;
    }

    /**
     * Get the HTTP client for making requests to the VPlex at the
     * endpoint specified in the passed profile.
     * 
     * @param jobContext The job context
     * @param vplexSystem The VPlex storage system
     * 
     * @return A reference to the VPlex API HTTP client.
     * @throws URISyntaxException
     */
    private VPlexApiClient getVPlexAPIClient(JobContext jobContext, StorageSystem vplexSystem) throws URISyntaxException {
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
     * Return the passed percent done as an integer. Returns 0 when the passed
     * value cannot be parsed to an integer.
     * 
     * @param percentDoneStr Percent done value as a string.
     * 
     * @return The percent done as an integer.
     */
    private int getMigrationPercentDone(String percentDoneStr) {
        int percentDone = 0;
        try {
            percentDone = Integer.parseInt(percentDoneStr);
        } catch (NumberFormatException nfe) {
            s_logger.warn("Migration percentage {} is not a number.", percentDoneStr);
        }
        return percentDone;
    }

    /**
     * Update the status after a poll.
     * 
     * @param jobContext the job context.
     */
    public void updateStatus(JobContext jobContext) {
        try {
            if (_status == JobStatus.SUCCESS) {
                s_logger.info("Calling task completer for successful job");
                _taskCompleter.ready(jobContext.getDbClient());
            } else if (_status == JobStatus.FAILED) {
                s_logger.info("Calling task completer for failed job");
                ServiceError error = DeviceControllerErrors.vplex.migrationJobFailed(_errorDescription);
                _taskCompleter.error(jobContext.getDbClient(), error);
            }
        } catch (Exception e) {
            s_logger.error("Problem while trying to update status", e);
        }
    }

    /**
     * Determines the operation status from the job status.
     * 
     * @return The operation status, based on the job status.
     */
    protected Operation.Status getOperationStatus() {
        switch (_status) {
            case SUCCESS:
                return Operation.Status.ready;
            case FAILED:
                return Operation.Status.error;
            case ERROR:
            default:
                return Operation.Status.pending;
        }
    }

    /**
     * Returns the operation message based on the job status.
     * 
     * @return The operation message.
     */
    protected String getOperationMessage() {
        switch (_status) {
            case SUCCESS:
                return "Migration succeeded";
            case FAILED:
                return "Migration failed with error:" + _errorDescription;
            case ERROR:
                return "Migration encountered internal error:" + _errorDescription;
            case IN_PROGRESS:
                return "Migration in progress: " + getJobPercentDone() + "% complete...";
            default:
                return "Undefined migration job status";
        }
    }

    /**
     * Returns the operation service code based on the job status.
     * 
     * @return The operation service code.
     */
    protected ServiceCode getOperationServiceCode() {
        switch (_status) {
            case ERROR:
                return ServiceCode.CONTROLLER_ERROR;
            default:
                return null;
        }
    }

    /**
     * Gets the job percentage complete.
     * 
     * @return The job percentage complete.
     */
    public int getJobPercentDone() {
        if (_pollResult == null) {
            return 0;
        }
        return _pollResult.getJobPercentComplete();
    }

    public TaskCompleter getTaskCompleter() {
        return _taskCompleter;
    }
}
