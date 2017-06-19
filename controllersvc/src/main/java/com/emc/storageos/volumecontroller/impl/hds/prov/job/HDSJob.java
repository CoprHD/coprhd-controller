/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.hds.prov.job;

import java.io.Serializable;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.httpclient.NoHttpResponseException;
import org.milyn.payload.JavaResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.hds.HDSConstants;
import com.emc.storageos.hds.api.HDSApiClient;
import com.emc.storageos.hds.model.EchoCommand;
import com.emc.storageos.hds.model.Error;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.Job;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.JobPollResult;
import com.emc.storageos.volumecontroller.impl.hds.prov.utils.HDSUtils;

/**
 * An HDS job
 */
public class HDSJob extends Job implements Serializable
{
    private static final Logger logger = LoggerFactory.getLogger(HDSJob.class);
    private static final String HDS_OBJECT_NAME = "hdsobject";
    private static final String STORAGE_SYSTEM_URI_NAME = "storagesystemuri";
    private static final String TASK_COMPLETER_NAME = "taskcompleter";
    private static final String JOB_NAME_NAME = "jobname";
    private static final String ERROR_TRACKING_TIME = "errortrackingtime";  // in milli seconds
    private static final String POST_PROCESSING_ERROR_TRACKING_START_TIME = "postprocessingerrortrackingstarttime";  // in milli seconds
    public static final long ERROR_TRACKING_LIMIT = 2 * 60 * 60 * 1000; // tracking limit for transient errors. set for 2 hours
    protected static final long POST_PROCESSING_ERROR_TRACKING_LIMIT = 20 * 60 * 1000; // tracking limit for transient errors in post
                                                                                       // processing, 20 minutes
    public JobPollResult _pollResult = new JobPollResult();
    protected JavaResult _javaResult;
    private String _id;
    public String _errorDescription = null;
    protected JobStatus _status = JobStatus.IN_PROGRESS;
    // status of job.updateStatus() execution
    protected JobStatus _postProcessingStatus = JobStatus.SUCCESS;
    protected Map<String, Object> _map = new HashMap<String, Object>();

    public HDSJob(String messageId, URI storageSystem, TaskCompleter taskCompleter, String jobName) {
        _map.put(HDS_OBJECT_NAME, messageId);
        _map.put(STORAGE_SYSTEM_URI_NAME, storageSystem);
        _map.put(TASK_COMPLETER_NAME, taskCompleter);
        _map.put(JOB_NAME_NAME, jobName);
        _map.put(POST_PROCESSING_ERROR_TRACKING_START_TIME, 0L);
        _map.put(ERROR_TRACKING_TIME, 0L);
    }

    public void setHDSJob(String messageId) {
        _map.put(HDS_OBJECT_NAME, messageId);
    }

    public String getHDSJobMessageId() {
        return _map.get(HDS_OBJECT_NAME).toString();
    }

    public URI getStorageSystemURI() {
        return (URI) _map.get(STORAGE_SYSTEM_URI_NAME);
    }

    @Override
    public TaskCompleter getTaskCompleter() {
        return (TaskCompleter) _map.get(TASK_COMPLETER_NAME);
    }

    public String getJobName() {
        return (String) _map.get(JOB_NAME_NAME);
    }

    public long getErrorTrackingTime() {
        return (Long) _map.get(ERROR_TRACKING_TIME);
    }

    public void setErrorTrackingTime(long trackingTime) {
        _map.put(ERROR_TRACKING_TIME, trackingTime);
    }

    protected long getPostProcessingErrorTrackingStartTime() {
        return (Long) _map.get(POST_PROCESSING_ERROR_TRACKING_START_TIME);
    }

    public void setPostProcessingErrorTrackingStartTime(long trackingStartTime) {
        _map.put(POST_PROCESSING_ERROR_TRACKING_START_TIME, trackingStartTime);
    }

    /**
     * Sets the status for the job to the fatal_error status and updates the
     * error description with the passed description.
     * 
     * @param errorDescription A description of the error.
     */
    public void setFatalErrorStatus(String errorDescription) {
        _status = JobStatus.FATAL_ERROR;
        _errorDescription = errorDescription;
    }

    /**
     * Sets post processing status of the job to the failed status and updates the
     * error description with the passed description.
     * 
     * @param errorDescription A description of the error.
     */
    public void setPostProcessingFailedStatus(String errorDescription) {
        _postProcessingStatus = JobStatus.FAILED;
        _errorDescription = errorDescription;
    }

    /**
     * Sets the status for the job to the error status and updates the
     * error description with the passed description.
     * 
     * @param errorDescription A description of the error.
     */
    public void setErrorStatus(String errorDescription) {
        _status = JobStatus.ERROR;
        _errorDescription = errorDescription;
    }

    /**
     * Sets the status for the job to the failed status and updates the
     * error description with the passed description.
     * 
     * @param errorDescription A description of the error.
     */
    public void setFailedStatus(String errorDescription) {
        _status = JobStatus.FAILED;
        _errorDescription = errorDescription;
    }

    @Override
    public JobPollResult poll(JobContext jobContext, long trackingPeriodInMillis) {
        String messageId = getHDSJobMessageId();
        try {
            StorageSystem storageSystem = jobContext.getDbClient().queryObject(StorageSystem.class, getStorageSystemURI());

            logger.info("HDSJob: Looking up job: id {}, provider: {} ", messageId, storageSystem.getActiveProviderURI());
            HDSApiClient hdsApiClient = jobContext.getHdsApiFactory().getClient(HDSUtils.getHDSServerManagementServerInfo(storageSystem),
                    storageSystem.getSmisUserName(), storageSystem.getSmisPassword());
            _pollResult.setJobName(getJobName());
            _pollResult.setJobId(messageId);
            if (hdsApiClient == null) {
                String errorMessage = "No HDS client found for provider ip: " + storageSystem.getActiveProviderURI();
                processTransientError(messageId, trackingPeriodInMillis, errorMessage, null);
            } else {
                JavaResult javaResult = hdsApiClient.checkAsyncTaskStatus(messageId);
                if (null == javaResult) {
                    _pollResult.setJobPercentComplete(100);
                    _errorDescription = String
                            .format("Async task failed for messageID %s due to no response from server",
                                    messageId);
                    _status = JobStatus.FAILED;
                    logger.error("HDSJob: {} failed; Details: {}", getJobName(), _errorDescription);
                } else {
                    EchoCommand command = javaResult.getBean(EchoCommand.class);
                    if (HDSConstants.COMPLETED_STR.equalsIgnoreCase(command.getStatus())) {
                        _status = JobStatus.SUCCESS;
                        _pollResult.setJobPercentComplete(100);
                        _javaResult = javaResult;
                        logger.info("HDSJob: {} succeeded", messageId);
                    } else if (HDSConstants.FAILED_STR.equalsIgnoreCase(command.getStatus())) {
                        Error error = javaResult.getBean(Error.class);
                        _pollResult.setJobPercentComplete(100);
                        _errorDescription = String
                                .format("Async task failed for messageID %s due to %s with error code: %d",
                                        messageId, error.getDescription(),
                                        error.getCode());
                        _status = JobStatus.FAILED;
                        logger.error("HDSJob: {} failed; Details: {}", getJobName(), _errorDescription);
                    }
                }
            }
        } catch (NoHttpResponseException ex) {
            _status = JobStatus.FAILED;
            _pollResult.setJobPercentComplete(100);
            _errorDescription = ex.getMessage();
            logger.error(String.format("HDS job not found. Marking as failed as we cannot determine status. " +
                    "User may retry the operation to be sure: Name: %s, ID: %s, Desc: %s",
                    getJobName(), messageId, _errorDescription), ex);
        } catch (Exception e) {
            processTransientError(messageId, trackingPeriodInMillis, e.getMessage(), e);
        } finally {
            try {
                _postProcessingStatus = JobStatus.SUCCESS;
                updateStatus(jobContext);
                if (_postProcessingStatus == JobStatus.ERROR) {
                    processPostProcessingError(messageId, trackingPeriodInMillis, _errorDescription, null);
                }
            } catch (Exception e) {
                setFatalErrorStatus(e.getMessage());
                setPostProcessingFailedStatus(e.getMessage());
                logger.error("Problem while trying to update status", e);
            } finally {
                if (isJobInTerminalFailedState()) {
                    // Have to process job completion since updateStatus may not did this.
                    ServiceError error = DeviceControllerErrors.hds.jobFailed(_errorDescription);
                    getTaskCompleter().error(jobContext.getDbClient(), error);
                }
            }
        }
        _pollResult.setJobStatus(_status);
        _pollResult.setJobPostProcessingStatus(_postProcessingStatus);
        _pollResult.setErrorDescription(_errorDescription);
        return _pollResult;
    }

    public void updateStatus(JobContext jobContext) throws Exception {

        if (isJobInTerminalSuccessState()) {
            getTaskCompleter().ready(jobContext.getDbClient());
        } else if (isJobInTerminalFailedState()) {
            ServiceError error = DeviceControllerErrors.hds.jobFailed(_errorDescription);
            getTaskCompleter().error(jobContext.getDbClient(), error);
        }

        /*
         * if (_status == JobStatus.SUCCESS) {
         * getTaskCompleter().ready(jobContext.getDbClient());
         * } else if (_status == JobStatus.FAILED || _status == JobStatus.FATAL_ERROR) {
         * ServiceError error = DeviceControllerErrors.hds.jobFailed(_errorDescription);
         * getTaskCompleter().error(jobContext.getDbClient(), error);
         * }
         */
        // else {
        // do nothing
        // }
    }

    public String getJobID() {
        return _id;
    }

    public int getJobPercentComplete() {
        if (_pollResult == null) {
            return 0;
        }
        return _pollResult.getJobPercentComplete();
    }

    public boolean isSuccess() {
        return (_status == JobStatus.SUCCESS);
    }

    protected Operation.Status getOpStatus() {
        switch (_status) {
            case SUCCESS:
                return Operation.Status.ready;
            case FAILED:
                return Operation.Status.error;
            default:
                return Operation.Status.pending;
        }
    }

    protected String getMessage() {
        switch (_status) {
            case SUCCESS:
                return "Job succeeded";
            case FAILED:
                return "Job failed with error:" + _errorDescription;
            case ERROR:
                return "Transient error checking job status - internal error:" + _errorDescription;
            case IN_PROGRESS:
                return "Job in progress: " + getJobPercentComplete() + "% complete...";
            default:
                return "Undefined status";
        }
    }

    private void processTransientError(String jobId, long trackingInterval, String errorMessage, Exception ex) {
        _status = JobStatus.ERROR;
        _errorDescription = errorMessage;
        if (ex != null) {
            logger.error(String.format("Error while processing HDSJob - Name: %s, ID: %s, Desc: %s Status: %s",
                    getJobName(), jobId, _errorDescription, _status), ex);
        } else {
            logger.error(String.format("Error while processing HDSJob - Name: %s, ID: %s, Desc: %s Status: %s",
                    getJobName(), jobId, _errorDescription, _status));
        }

        // Check if job tracking limit was reached. Set status to FAILED in such a case.
        setErrorTrackingTime(getErrorTrackingTime() + trackingInterval);
        logger.info(String.format("Tracking time of HDSJob in transient error status - %s, Name: %s, ID: %s. Status %s .",
                getErrorTrackingTime(), getJobName(), jobId, _status));
        if (getErrorTrackingTime() > ERROR_TRACKING_LIMIT) {
            _status = JobStatus.FATAL_ERROR;
            logger.error(String.format("Reached tracking time limit for HDSJob - Name: %s, ID: %s. Set status to %s .",
                    getJobName(), jobId, _status));
        }
    }

    private void processPostProcessingError(String jobId, long trackingInterval, String errorMessage, Exception ex) {
        _postProcessingStatus = JobStatus.ERROR;
        _errorDescription = errorMessage;
        if (ex != null) {
            logger.error(String.format("Error while post processing HDSJob - Name: %s, ID: %s, Desc: %s Status: %s",
                    getJobName(), jobId, _errorDescription, _postProcessingStatus), ex);
        } else {
            logger.error(String.format("Error while processing HDSJob - Name: %s, ID: %s, Desc: %s Status: %s",
                    getJobName(), jobId, _errorDescription, _postProcessingStatus));
        }

        // Check if job post processing tracking limit was reached. Set post processing status to FAILED in such a case.
        if (getPostProcessingErrorTrackingStartTime() == 0) {
            setPostProcessingErrorTrackingStartTime(System.currentTimeMillis());
        }
        long postProcessingErrorTrackingTime = System.currentTimeMillis() - getPostProcessingErrorTrackingStartTime();
        logger.info(String.format(
                "Tracking time of HDSJob in post processing error - %s, Name: %s, ID: %s. Status: %s, PostProcessing status: %s .",
                postProcessingErrorTrackingTime, getJobName(), jobId, _status, _postProcessingStatus));
        if (postProcessingErrorTrackingTime > POST_PROCESSING_ERROR_TRACKING_LIMIT) {
            _postProcessingStatus = JobStatus.FAILED;
            logger.error(String.format(
                    "Reached tracking time limit for HDSJob post processing - Name: %s, ID: %s. Set post processing status to %s .",
                    getJobName(), jobId, _postProcessingStatus));
        }
    }

    public JobStatus getJobStatus() {
        return _status;
    }

    public JobStatus getJobPostProcessingStatus() {
        return _postProcessingStatus;
    }

    /**
     * Sets the status for the job to the error status and updates the
     * error description with the passed description.
     * 
     * @param errorDescription A description of the error.
     */
    public void setPostProcessingErrorStatus(String errorDescription) {
        _postProcessingStatus = JobStatus.ERROR;
        _errorDescription = errorDescription;
    }

    public boolean isJobInTerminalState() {
        return (getJobStatus() == Job.JobStatus.SUCCESS || getJobStatus() == Job.JobStatus.ERROR ||
                getJobStatus() == Job.JobStatus.FAILED || getJobStatus() == Job.JobStatus.FATAL_ERROR) &&
                (getJobPostProcessingStatus() == Job.JobStatus.SUCCESS || getJobPostProcessingStatus() == Job.JobStatus.ERROR ||
                        getJobPostProcessingStatus() == Job.JobStatus.FAILED || getJobPostProcessingStatus() == Job.JobStatus.FATAL_ERROR);

    }

    public boolean isJobInTerminalFailedState() {
        return (isJobInTerminalState() && (getJobStatus() != Job.JobStatus.SUCCESS || getJobPostProcessingStatus() != Job.JobStatus.SUCCESS));
    }

    public boolean isJobInTerminalSuccessState() {
        return (getJobStatus() == Job.JobStatus.SUCCESS && getJobPostProcessingStatus() == Job.JobStatus.SUCCESS);
    }

}
