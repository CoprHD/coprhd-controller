/*
 * Copyright (c) 2017 DELL EMC
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.vmax.rest;

import java.io.Serializable;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.vmax.restapi.VMAXApiClient;
import com.emc.storageos.vmax.restapi.model.AsyncJob;
import com.emc.storageos.volumecontroller.Job;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.JobPollResult;

public class VMAXJob extends Job implements Serializable {

    public static enum AsyncjobStatus {
        CREATED,
        SCHEDULED,
        RUNNING, // job is in progress
        SUCCEEDED,     // terminal condition
        FAILED,      // terminal condition
        ABORTED,// terminal condition
        UNKNOWN,
        VALIDATING,
        VALIDATED,
        VALIDATE_FAILED,
        INVALID, // terminal condition
        RETRIEVING_PICTURE;

        public static boolean isJobInTerminalSuccessState(String status) {
            return (SUCCEEDED.name().equalsIgnoreCase(status) || VALIDATED.name().equalsIgnoreCase(status));
        }

        public static boolean isJobInTerminalFailureState(String status) {
            return (FAILED.name().equalsIgnoreCase(status) || ABORTED.name().equalsIgnoreCase(status)
                    || VALIDATE_FAILED.name().equalsIgnoreCase(status) || INVALID.name().equalsIgnoreCase(status));
        }

    };

    private static final Logger logger = LoggerFactory.getLogger(VMAXJob.class);
    private static final String JOB_ID = "jobid";
    private static final String STORAGE_PROVIDER_URI_NAME = "storageprovideruri";
    private static final String TASK_COMPLETER_NAME = "taskcompleter";
    private static final String JOB_NAME_NAME = "jobname";
    private static final String ERROR_TRACKING_START_TIME = "errortrackingstarttime";  // in milli seconds
    private static final String POST_PROCESSING_ERROR_TRACKING_START_TIME = "postprocessingerrortrackingstarttime";  // in milli seconds
    private static final long ERROR_TRACKING_LIMIT = 2 * 60 * 60 * 1000; // tracking limit for transient errors. set for 2 hours
    private static final long POST_PROCESSING_ERROR_TRACKING_LIMIT = 20 * 60 * 1000; // tracking limit for transient errors in post
                                                                                     // processing, 20 minutes
    protected Map<String, Object> _map = new HashMap<String, Object>();
    protected JobPollResult pollResult = new JobPollResult();
    private String jobId;
    protected TaskCompleter taskCompleter;
    protected String errorDescription = null;
    private String providerIp;
    protected JobStatus status = JobStatus.IN_PROGRESS;
    // status of jog.updateStatus() execution
    protected JobStatus postProcessingStatus = JobStatus.SUCCESS;
    private URI storageProviderURI;
    String jobName;

    public VMAXJob(String jobId, URI storageProviderURI, TaskCompleter taskCompleter, String jobName) {
        _map.put(JOB_ID, jobId);
        _map.put(STORAGE_PROVIDER_URI_NAME, storageProviderURI);
        _map.put(TASK_COMPLETER_NAME, taskCompleter);
        _map.put(JOB_NAME_NAME, jobName);
        _map.put(POST_PROCESSING_ERROR_TRACKING_START_TIME, 0L);
        _map.put(ERROR_TRACKING_START_TIME, 0L);
        this.jobId = jobId;
        this.storageProviderURI = storageProviderURI;
        this.taskCompleter = taskCompleter;
    }

    public boolean isJobInTerminalState() {
        return (getJobStatus() == Job.JobStatus.SUCCESS ||
                getJobStatus() == Job.JobStatus.FAILED || getJobStatus() == Job.JobStatus.FATAL_ERROR) &&
                (getJobPostProcessingStatus() == Job.JobStatus.SUCCESS ||
                        getJobPostProcessingStatus() == Job.JobStatus.FAILED || getJobPostProcessingStatus() == Job.JobStatus.FATAL_ERROR);

    }

    @Override
    public JobPollResult poll(JobContext jobContext, long trackingPeriodInMillis) {

        StorageProvider provider = null;
        try {
            // poll only if job is not in terminal status
            if (status == JobStatus.IN_PROGRESS || status == JobStatus.ERROR) {
                provider = jobContext.getDbClient().queryObject(StorageProvider.class, storageProviderURI);
                if (provider == null) {
                    String errorMessage = "Unable to get provider instance from db for the provider uri: " + storageProviderURI;
                    processTransientError(getJobId(), trackingPeriodInMillis, errorMessage, null);
                }
                providerIp = provider.getIPAddress();
                logger.info("VMAXJob: Looking up job: id {}, provider: {} ", getJobId(), provider.getIPAddress());
                VMAXApiClient vmaxApiClient = jobContext.getVmaxClientFactory().getClient(provider.getIPAddress(), provider.getPortNumber(),
                        provider.getUseSSL(), provider.getUserName(), provider.getPassword());
                if (vmaxApiClient == null) {
                    String errorMessage = "Unable to create VMAX API client for the provider ip: " + provider.getIPAddress();
                    processTransientError(getJobId(), trackingPeriodInMillis, errorMessage, null);
                } else {
                    // reset transient error tracking time
                    setErrorTrackingStartTime(0L);
                    AsyncJob asyncJob = vmaxApiClient.getAsyncJob(getJobId());
                    logger.info("VMAXJob: Response for the job id {} - {}", getJobId(), asyncJob);
                    if (AsyncjobStatus.isJobInTerminalSuccessState(asyncJob.getStatus())) {
                        status = JobStatus.SUCCESS;
                        logger.info("VMAXJob: {} succeeded", getJobId());
                    } else if (AsyncjobStatus.isJobInTerminalFailureState(asyncJob.getStatus())) {
                        status = JobStatus.FAILED;
                        logger.info("VMAXJob: {} returned exception", getJobId());
                    } else {
                        // reset status from previous possible transient error status
                        status = JobStatus.IN_PROGRESS;
                    }

                    if ((status != JobStatus.SUCCESS) && (status != JobStatus.IN_PROGRESS)) {
                        // parse ErrorDescription
                        errorDescription = asyncJob.getResult();
                        status = JobStatus.FAILED;
                        logger.error("VMAXJob: {} failed; Details: {}", getJobName(), asyncJob.getResult());
                        logger.error("Async Job collected from Provider {}", asyncJob);
                    }

                }
            }

        } catch (Exception e) {
            processTransientError(getJobId(), trackingPeriodInMillis, e.getMessage(), e);
        } finally {
            try {
                logger.info("VMAXJob: Post processing job: id {}, provider: {} ", getJobId(), providerIp);
                // reset from previous possible transient error in post processing status.
                postProcessingStatus = JobStatus.SUCCESS;
                updateStatus(jobContext);
                if (postProcessingStatus == JobStatus.ERROR) {
                    processPostProcessingError(getJobId(), trackingPeriodInMillis, errorDescription, null);
                }

            } catch (Exception e) {
                setFatalErrorStatus(e.getMessage());
                setPostProcessingFailedStatus(e.getMessage());
                logger.error("Problem while trying to update status", e);
            } finally {
                if (isJobInTerminalFailedState()) {
                    // Have to process job completion since updateStatus may not did this.
                    ServiceError error = DeviceControllerErrors.vmax.jobFailed(errorDescription);
                    getTaskCompleter().error(jobContext.getDbClient(), error);
                }
            }
        }

        pollResult.setJobStatus(status);
        pollResult.setJobPostProcessingStatus(postProcessingStatus);
        pollResult.setErrorDescription(errorDescription);
        return pollResult;
    }

    public void updateStatus(JobContext jobContext) throws Exception {
        if (isJobInTerminalSuccessState()) {
            getTaskCompleter().ready(jobContext.getDbClient());
        } else if (isJobInTerminalFailedState()) {
            ServiceError error = DeviceControllerErrors.vmax.jobFailed(errorDescription);
            getTaskCompleter().error(jobContext.getDbClient(), error);
        }
    }

    protected void processTransientError(String jobId, long trackingInterval, String errorMessage, Exception ex) {
        status = JobStatus.ERROR;
        errorDescription = errorMessage;
        if (ex != null) {
            logger.error(String.format("Error while processing VMAXJob - Name: %s, ID: %s, Desc: %s Status: %s",
                    getJobName(), jobId, errorDescription, status), ex);
        } else {
            logger.error(String.format("Error while processing VMAXJob - Name: %s, ID: %s, Desc: %s Status: %s",
                    getJobName(), jobId, errorDescription, status));
        }

        // Check if job tracking limit was reached. Set status to FATAL_ERROR in such a case.
        if (getErrorTrackingStartTime() == 0) {
            setErrorTrackingStartTime(System.currentTimeMillis());
        }
        long errorTrackingTime = System.currentTimeMillis() - getErrorTrackingStartTime();
        logger.info(String.format("Tracking time of VMAXJob in transient error status - %s, Name: %s, ID: %s. Status %s .",
                errorTrackingTime, getJobName(), jobId, status));
        if (errorTrackingTime > ERROR_TRACKING_LIMIT) {
            status = JobStatus.FATAL_ERROR;
            logger.error(String.format("Reached tracking time limit for VMAXJob - Name: %s, ID: %s. Set status to %s .",
                    getJobName(), jobId, status));
        }
    }

    protected void processPostProcessingError(String jobId, long trackingInterval, String errorMessage, Exception ex) {
        postProcessingStatus = JobStatus.ERROR;
        errorDescription = errorMessage;
        if (ex != null) {
            logger.error(String.format("Error while post processing VMAXJob - Name: %s, ID: %s, Desc: %s Status: %s",
                    getJobName(), jobId, errorDescription, postProcessingStatus), ex);
        } else {
            logger.error(String.format("Error while processing VMAXJob - Name: %s, ID: %s, Desc: %s Status: %s",
                    getJobName(), jobId, errorDescription, postProcessingStatus));
        }

        // Check if job post processing tracking limit was reached. Set post processing status to FAILED in such a case.
        if (getPostProcessingErrorTrackingStartTime() == 0) {
            setPostProcessingErrorTrackingStartTime(System.currentTimeMillis());
        }
        long postProcessingErrorTrackingTime = System.currentTimeMillis() - getPostProcessingErrorTrackingStartTime();
        logger.info(String.format(
                "Tracking time of VMAXJob in post processing error - %s, Name: %s, ID: %s. Status: %s, PostProcessing status: %s .",
                postProcessingErrorTrackingTime, getJobName(), jobId, status, postProcessingStatus));
        if (postProcessingErrorTrackingTime > POST_PROCESSING_ERROR_TRACKING_LIMIT) {
            postProcessingStatus = JobStatus.FAILED;
            logger.error(String.format(
                    "Reached tracking time limit for VMAXJob post processing - Name: %s, ID: %s. Set post processing status to %s .",
                    getJobName(), jobId, postProcessingStatus));
        }
    }

    public JobStatus getJobStatus() {
        return status;
    }

    public JobStatus getJobPostProcessingStatus() {
        return postProcessingStatus;
    }

    public boolean isJobInTerminalFailedState() {
        return (isJobInTerminalState()
                && (getJobStatus() != Job.JobStatus.SUCCESS || getJobPostProcessingStatus() != Job.JobStatus.SUCCESS));
    }

    public boolean isJobInTerminalSuccessState() {
        return (getJobStatus() == Job.JobStatus.SUCCESS && getJobPostProcessingStatus() == Job.JobStatus.SUCCESS);
    }

    @Override
    public TaskCompleter getTaskCompleter() {
        return (TaskCompleter) _map.get(TASK_COMPLETER_NAME);
    }

    /**
     * Sets the status for the job to the error status and updates the
     * error description with the passed description.
     * 
     * @param errorDescription A description of the error.
     */
    public void setPostProcessingErrorStatus(String errorDescription) {
        this.postProcessingStatus = JobStatus.ERROR;
        this.errorDescription = errorDescription;
    }

    /**
     * Sets the status for the job to the fatal_error status and updates the
     * error description with the passed description.
     * 
     * @param errorDescription A description of the error.
     */
    public void setFatalErrorStatus(String errorDescription) {
        this.status = JobStatus.FATAL_ERROR;
        this.errorDescription = errorDescription;
    }

    /**
     * Sets the status for the job to the failed status and updates the
     * error description with the passed description.
     * 
     * @param errorDescription A description of the error.
     */
    public void setFailedStatus(String errorDescription) {
        this.status = JobStatus.FAILED;
        this.errorDescription = errorDescription;
    }

    /**
     * Sets post processing status of the job to the failed status and updates the
     * error description with the passed description.
     * 
     * @param errorDescription A description of the error.
     */
    public void setPostProcessingFailedStatus(String errorDescription) {
        this.postProcessingStatus = JobStatus.FAILED;
        this.errorDescription = errorDescription;
    }

    public String getJobId() {
        return (String) _map.get(JOB_ID);
    }

    public String getJobName() {
        return (String) _map.get(JOB_NAME_NAME);
    }

    private long getErrorTrackingStartTime() {
        return (Long) _map.get(ERROR_TRACKING_START_TIME);
    }

    public void setErrorTrackingStartTime(long trackingStartTime) {
        _map.put(ERROR_TRACKING_START_TIME, trackingStartTime);
    }

    private long getPostProcessingErrorTrackingStartTime() {
        return (Long) _map.get(POST_PROCESSING_ERROR_TRACKING_START_TIME);
    }

    public void setPostProcessingErrorTrackingStartTime(long trackingStartTime) {
        _map.put(POST_PROCESSING_ERROR_TRACKING_START_TIME, trackingStartTime);
    }

    public URI getStorageProviderURI() {
        return (URI) _map.get(STORAGE_PROVIDER_URI_NAME);
    }

}
