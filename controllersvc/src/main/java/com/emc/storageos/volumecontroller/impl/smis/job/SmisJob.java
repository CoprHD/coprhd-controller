/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation;

import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.Job;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.JobPollResult;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMArgument;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.cim.UnsignedInteger16;
import javax.wbem.WBEMException;
import javax.wbem.client.WBEMClient;
import java.io.Serializable;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * An SMI-S job
 */
public class SmisJob extends Job implements Serializable
{
    private static final Logger _logger = LoggerFactory.getLogger(SmisJob.class);
    private static final String CIM_OBJECT_NAME = "cimobject";
    private static final String STORAGE_SYSTEM_URI_NAME = "storagesystemuri";
    private static final String TASK_COMPLETER_NAME = "taskcompleter";
    private static final String JOB_NAME_NAME = "jobname";
    private static final String ERROR_TRACKING_START_TIME = "errortrackingstarttime";  // in milli seconds
    private static final String POST_PROCESSING_ERROR_TRACKING_START_TIME = "postprocessingerrortrackingstarttime";  // in milli seconds
    private static final String JOB_PROPERTY_KEY_PERCENT_COMPLETE = "PercentComplete";
    private static final String JOB_PROPERTY_KEY_OPERATIONAL_STS = "OperationalStatus";
    private static final String JOB_PROPERTY_KEY_ERROR_DESC = "ErrorDescription";
    private static final long ERROR_TRACKING_LIMIT = 2 * 60 * 60 * 1000; // tracking limit for transient errors. set for 2 hours
    private static final long POST_PROCESSING_ERROR_TRACKING_LIMIT = 20 * 60 * 1000; // tracking limit for transient errors in post
                                                                                     // processing, 20 minutes
    protected JobPollResult _pollResult = new JobPollResult();
    private String _id;
    protected String _errorDescription = null;
    protected JobStatus _status = JobStatus.IN_PROGRESS;
    // status of jog.updateStatus() execution
    protected JobStatus _postProcessingStatus = JobStatus.SUCCESS;
    private int _percentComplete = 0;

    protected Map<String, Object> _map = new HashMap<String, Object>();
    private String _smisIPAddress = null;

    public SmisJob(CIMObjectPath cimJob, URI storageSystem, TaskCompleter taskCompleter, String jobName) {
        _map.put(CIM_OBJECT_NAME, cimJob);
        _map.put(STORAGE_SYSTEM_URI_NAME, storageSystem);
        _map.put(TASK_COMPLETER_NAME, taskCompleter);
        _map.put(JOB_NAME_NAME, jobName);
        _map.put(POST_PROCESSING_ERROR_TRACKING_START_TIME, 0L);
        _map.put(ERROR_TRACKING_START_TIME, 0L);
    }

    public void setCimJob(CIMObjectPath cimJob) {
        _map.put(CIM_OBJECT_NAME, cimJob);
    }

    public CIMObjectPath getCimJob() {
        return (CIMObjectPath) _map.get(CIM_OBJECT_NAME);
    }

    public URI getStorageSystemURI() {
        return (URI) _map.get(STORAGE_SYSTEM_URI_NAME);
    }

    public TaskCompleter getTaskCompleter() {
        return (TaskCompleter) _map.get(TASK_COMPLETER_NAME);
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
     * Sets the status for the job to the failed status and updates the
     * error description with the passed description.
     * 
     * @param errorDescription A description of the error.
     */
    public void setFailedStatus(String errorDescription) {
        _status = JobStatus.FAILED;
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

    public JobPollResult poll(JobContext jobContext, long trackingPeriodInMillis) {
        CIMProperty<Object> instanceID = null;
        try {
            CIMObjectPath cimJob = getCimJob();
            instanceID = (CIMProperty<Object>) cimJob.getKey("InstanceID");
            _pollResult.setJobName(getJobName());
            _pollResult.setJobId(instanceID.getValue().toString());
            _pollResult.setJobPercentComplete(_percentComplete);
            if (_smisIPAddress == null) {
                StorageSystem storageSystem = jobContext.getDbClient().queryObject(StorageSystem.class, getStorageSystemURI());
                _smisIPAddress = storageSystem.getSmisProviderIP();
            }
            // poll only if job is not in terminal status
            if (_status == JobStatus.IN_PROGRESS || _status == JobStatus.ERROR) {
                String[] jobPathPropertyKeys =
                { JOB_PROPERTY_KEY_PERCENT_COMPLETE, JOB_PROPERTY_KEY_OPERATIONAL_STS, JOB_PROPERTY_KEY_ERROR_DESC };
                CIMInstance jobPathInstance = null;
                _logger.info("SmisJob: Looking up job: id {}, provider: {} ", instanceID.getValue(), _smisIPAddress);
                WBEMClient wbemClient = getWBEMClient(jobContext.getDbClient(), jobContext.getCimConnectionFactory());
                if (wbemClient == null) {
                    String errorMessage = "No CIMOM client found for provider ip: " + _smisIPAddress;
                    processTransientError(instanceID.getValue().toString(), trackingPeriodInMillis, errorMessage, null);
                } else {
                    jobPathInstance = wbemClient.getInstance(getCimJob(), false, false, jobPathPropertyKeys);
                    CIMProperty<UnsignedInteger16> percentComplete =
                            (CIMProperty<UnsignedInteger16>) jobPathInstance.getProperty(JOB_PROPERTY_KEY_PERCENT_COMPLETE);
                    _pollResult.setJobPercentComplete(percentComplete.getValue().intValue());
                    _percentComplete = _pollResult.getJobPercentComplete();
                    // reset transient error tracking time
                    setErrorTrackingStartTime(0L);
                    if (_pollResult.getJobPercentComplete() == 100) {
                        CIMProperty<UnsignedInteger16[]> operationalStatus =
                                (CIMProperty<UnsignedInteger16[]>) jobPathInstance.getProperty(JOB_PROPERTY_KEY_OPERATIONAL_STS);
                        UnsignedInteger16[] statusValues = operationalStatus.getValue();
                        if (statusValues != null) {
                            for (int j = 0; j < statusValues.length; j++) {
                                _logger.info("Status value[{}]: {}", j, statusValues[j].intValue());
                                if (statusValues[j].intValue() == 2) {
                                    _status = JobStatus.SUCCESS;
                                    _logger.info("SmisJob: {} succeeded", instanceID.getValue());
                                }
                            }
                        }
                        if (_status != JobStatus.SUCCESS) {
                            // parse ErrorDescription
                            CIMProperty<String> errorDescription =
                                    (CIMProperty<String>) jobPathInstance.getProperty(JOB_PROPERTY_KEY_ERROR_DESC);
                            _errorDescription = errorDescription.toString();
                            _status = JobStatus.FAILED;
                            _logger.error("SmisJob: {} failed; Details: {}", getJobName(), _errorDescription);
                            CIMArgument[] pOutputArguments = new CIMArgument[1];
                            try {
                            Object errorReponse = wbemClient.invokeMethod(getCimJob(), "GetErrors", null, pOutputArguments);
                            _logger.error("GetErrors() response :{}", pOutputArguments);
                            } catch (Exception ex) {
                                _logger.error("Error retreiving errorResponse", ex);
                            }
                        }
                    } else {
                        // reset status from previous possible transient error status
                        _status = JobStatus.IN_PROGRESS;
                    }
                }
            }
        } catch (WBEMException we) {
            if (we.getID() == WBEMException.CIM_ERR_NOT_FOUND) {
                _status = JobStatus.FAILED;
                _errorDescription = we.getMessage();
                _logger.error(String.format("SMI-S job not found. Marking as failed as we cannot determine status. " +
                        "User may retry the operation to be sure: Name: %s, ID: %s, Desc: %s",
                        getJobName(), instanceID.getValue().toString(), _errorDescription), we);
            } else {
                processTransientError(instanceID.getValue().toString(), trackingPeriodInMillis, we.getMessage(), we);
            }
        } catch (Exception e) {
            processTransientError(instanceID.getValue().toString(), trackingPeriodInMillis, e.getMessage(), e);
        } finally {
            try {
                _logger.info("SmisJob: Post processing job: id {}, provider: {} ", instanceID.getValue(), _smisIPAddress);
                // reset from previous possible transient error in post processing status.
                _postProcessingStatus = JobStatus.SUCCESS;
                updateStatus(jobContext);
                if (_postProcessingStatus == JobStatus.ERROR) {
                    processPostProcessingError(instanceID.getValue().toString(), trackingPeriodInMillis, _errorDescription, null);
                }
            } catch (WBEMException we) {
                if (we.getID() == WBEMException.CIM_ERR_NOT_FOUND) {
                    _postProcessingStatus = JobStatus.FAILED;
                    _errorDescription = we.getMessage();
                    _logger.error(String.format("SMI-S job not found. Marking as failed as we cannot determine status. " +
                            "User may retry the operation to be sure: Name: %s, ID: %s, Desc: %s",
                            getJobName(), instanceID.getValue().toString(), _errorDescription), we);
                } else {
                    processPostProcessingError(instanceID.getValue().toString(), trackingPeriodInMillis, we.getMessage(), we);
                }
            } catch (Exception e) {
                setFatalErrorStatus(e.getMessage());
                _logger.error("Problem while trying to update status", e);
            } finally {
                if (isJobInTerminalFailedState()) {
                    // Have to process job completion since updateStatus may not did this.
                    ServiceError error = DeviceControllerErrors.smis.jobFailed(_errorDescription);
                    getTaskCompleter().error(jobContext.getDbClient(), error);
                }
            }
        }
        _pollResult.setJobStatus(_status);
        _pollResult.setJobPostProcessingStatus(_postProcessingStatus);
        _pollResult.setErrorDescription(_errorDescription);
        return _pollResult;
    }

    public WBEMClient getWBEMClient(DbClient dbClient, CIMConnectionFactory cimConnectionFactory) {
        StorageSystem system = null;
        WBEMClient client = null;
        try {
            system = dbClient.queryObject(StorageSystem.class, getStorageSystemURI());
        } catch (Exception e) {
            _logger.error("Error while reading storage system:", e);
        }

        client = cimConnectionFactory.getConnection(system.getSmisProviderIP(), system.getSmisPortNumber().toString())
                .getCimClient();
        return client;
    }

    public void updateStatus(JobContext jobContext) throws Exception {
        if (isJobInTerminalSuccessState()) {
            getTaskCompleter().ready(jobContext.getDbClient());
        } else if (isJobInTerminalFailedState()) {
            ServiceError error = DeviceControllerErrors.smis.jobFailed(_errorDescription);
            getTaskCompleter().error(jobContext.getDbClient(), error);
        }
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

    public JobStatus getJobStatus() {
        return _status;
    }

    public JobStatus getJobPostProcessingStatus() {
        return _postProcessingStatus;
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

    protected void processTransientError(String jobId, long trackingInterval, String errorMessage, Exception ex) {
        _status = JobStatus.ERROR;
        _errorDescription = errorMessage;
        if (ex != null) {
            _logger.error(String.format("Error while processing SmisJob - Name: %s, ID: %s, Desc: %s Status: %s",
                    getJobName(), jobId, _errorDescription, _status), ex);
        } else {
            _logger.error(String.format("Error while processing SmisJob - Name: %s, ID: %s, Desc: %s Status: %s",
                    getJobName(), jobId, _errorDescription, _status));
        }

        // Check if job tracking limit was reached. Set status to FATAL_ERROR in such a case.
        if (getErrorTrackingStartTime() == 0) {
            setErrorTrackingStartTime(System.currentTimeMillis());
        }
        long errorTrackingTime = System.currentTimeMillis() - getErrorTrackingStartTime();
        _logger.info(String.format("Tracking time of SmisJob in transient error status - %s, Name: %s, ID: %s. Status %s .",
                errorTrackingTime, getJobName(), jobId, _status));
        if (errorTrackingTime > ERROR_TRACKING_LIMIT) {
            _status = JobStatus.FATAL_ERROR;
            _logger.error(String.format("Reached tracking time limit for SmisJob - Name: %s, ID: %s. Set status to %s .",
                    getJobName(), jobId, _status));
        }
    }

    protected void processPostProcessingError(String jobId, long trackingInterval, String errorMessage, Exception ex) {
        _postProcessingStatus = JobStatus.ERROR;
        _errorDescription = errorMessage;
        if (ex != null) {
            _logger.error(String.format("Error while post processing SmisJob - Name: %s, ID: %s, Desc: %s Status: %s",
                    getJobName(), jobId, _errorDescription, _postProcessingStatus), ex);
        } else {
            _logger.error(String.format("Error while processing SmisJob - Name: %s, ID: %s, Desc: %s Status: %s",
                    getJobName(), jobId, _errorDescription, _postProcessingStatus));
        }

        // Check if job post processing tracking limit was reached. Set post processing status to FAILED in such a case.
        if (getPostProcessingErrorTrackingStartTime() == 0) {
            setPostProcessingErrorTrackingStartTime(System.currentTimeMillis());
        }
        long postProcessingErrorTrackingTime = System.currentTimeMillis() - getPostProcessingErrorTrackingStartTime();
        _logger.info(String.format(
                "Tracking time of SmisJob in post processing error - %s, Name: %s, ID: %s. Status: %s, PostProcessing status: %s .",
                postProcessingErrorTrackingTime, getJobName(), jobId, _status, _postProcessingStatus));
        if (postProcessingErrorTrackingTime > POST_PROCESSING_ERROR_TRACKING_LIMIT) {
            _postProcessingStatus = JobStatus.FAILED;
            _logger.error(String.format(
                    "Reached tracking time limit for SmisJob post processing - Name: %s, ID: %s. Set post processing status to %s .",
                    getJobName(), jobId, _postProcessingStatus));
        }
    }

    public boolean isJobInTerminalState() {
        return (getJobStatus() == Job.JobStatus.SUCCESS ||
                getJobStatus() == Job.JobStatus.FAILED || getJobStatus() == Job.JobStatus.FATAL_ERROR) &&
                (getJobPostProcessingStatus() == Job.JobStatus.SUCCESS ||
                        getJobPostProcessingStatus() == Job.JobStatus.FAILED || getJobPostProcessingStatus() == Job.JobStatus.FATAL_ERROR);

    }

    public boolean isJobInTerminalFailedState() {
        return (isJobInTerminalState() && (getJobStatus() != Job.JobStatus.SUCCESS || getJobPostProcessingStatus() != Job.JobStatus.SUCCESS));
    }

    public boolean isJobInTerminalSuccessState() {
        return (getJobStatus() == Job.JobStatus.SUCCESS && getJobPostProcessingStatus() == Job.JobStatus.SUCCESS);
    }
}
