/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014. EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller.impl.cinder.job;

import java.io.Serializable;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cinder.CinderConstants;
import com.emc.storageos.cinder.CinderEndPointInfo;
import com.emc.storageos.cinder.api.CinderApi;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.Job;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.JobPollResult;

/**
 * Cinder Job implementation
 *
 */
public class CinderJob extends  Job implements Serializable
{
	
	private static final long serialVersionUID = -2317072489614631555L;
	private static final Logger logger = LoggerFactory.getLogger(CinderJob.class);
	private CinderEndPointInfo epInfo = null;
	private String jobId = "";
	private URI storageSystemURI = null;
	private String jobName = "";
	private String errorDescription = "";
	private String componentType = "";
	private TaskCompleter taskCompleter = null;
	private long errorTrackingTime = 0L;
	private static final long ERROR_TRACKING_LIMIT = 2*60*60*1000; // tracking limit for transient errors. set for 2 hours
	
	private JobPollResult pollResult = new JobPollResult();
    protected JobStatus status = JobStatus.IN_PROGRESS;

	public CinderJob(String jobId, String jobName, 
							URI storageSystem, String componentType,
							CinderEndPointInfo ep, TaskCompleter taskCompleter) 
	{
		
		this.jobId = jobId;
		this.jobName = jobName;
		this.storageSystemURI = storageSystem;
		this.componentType = componentType;
		this.epInfo = ep;
		this.taskCompleter = taskCompleter;
		
	}
	
	public long getErrorTrackingTime() 
	{
		return errorTrackingTime;
	}

	public void setErrorTrackingTime(long errorTrackingTime) 
	{
		this.errorTrackingTime = errorTrackingTime;
	}
	
	/**
     * Sets the status for the job to the error status and updates the
     * error description with the passed description.
     * 
     * @param errorDescription A description of the error.
     */
    public void setErrorStatus(String errorDescription) 
    {
        status = JobStatus.ERROR;
        this.errorDescription = errorDescription;
    }
    
    public String getJobId() 
    {
		return jobId;
	}

	public void setJobId(String jobId) 
	{
		this.jobId = jobId;
	}

	public String getJobName() 
	{
		return jobName;
	}

	public void setJobName(String jobName) 
	{
		this.jobName = jobName;
	}

	public String getErrorDescription() 
	{
		return errorDescription;
	}

	public void setErrorDescription(String errorDescription) 
	{
		this.errorDescription = errorDescription;
	}

	public JobStatus getStatus() 
	{
		return status;
	}

	public void setStatus(JobStatus status) 
	{
		this.status = status;
	}
	
	public TaskCompleter getTaskCompleter() {
		return taskCompleter;
	}

	public void setTaskCompleter(TaskCompleter taskCompleter) {
		this.taskCompleter = taskCompleter;
	}
	
	public URI getStorageSystemURI() {
		return storageSystemURI;
	}

	public void setStorageSystemURI(URI storageSystemURI) {
		this.storageSystemURI = storageSystemURI;
	}
	
	public CinderEndPointInfo getEndPointInfo() {
		return epInfo;
	}

	public void setEndPointInfo(CinderEndPointInfo epInfo) {
		this.epInfo = epInfo;
	}


	/* (non-Javadoc)
	 * @see com.emc.storageos.volumecontroller.Job#poll(com.emc.storageos.volumecontroller.JobContext, long)
	 */
	@Override
	public JobPollResult poll(JobContext jobContext, long trackingPeriodInMillis) 
	{
		
		String messageId = jobId;
        try 
        {
        	
            StorageSystem storageSystem = jobContext.getDbClient().queryObject(StorageSystem.class, storageSystemURI);
            logger.info("CinderJob: Looking up job: id {}, provider: {} ", messageId, storageSystem.getActiveProviderURI());
            CinderApi cinderApi = jobContext.getCinderApiFactory().getApi(storageSystem.getActiveProviderURI(), this.epInfo);
            
            if (cinderApi == null) 
            {
                String errorMessage = "No Cinder client found for provider ip: " + storageSystem.getActiveProviderURI();
                processTransientError(messageId, trackingPeriodInMillis, errorMessage, null);
            } 
            else
            {
            	//Gets the current status of the task ( volume creation, snapshot creation etc )
                String currentStatus = getCurrentStatus(cinderApi);
                pollResult.setJobName(jobName);
                pollResult.setJobId(jobId);
                
                if (isJobSucceeded(currentStatus)) 
                {
                    status = JobStatus.SUCCESS;
                    pollResult.setJobPercentComplete(100);
                    logger.info("CinderJob: {} succeeded", messageId);
                }
                else if (isJobFailed(currentStatus))
                {
                	status = JobStatus.FAILED;
                	pollResult.setJobPercentComplete(100);
                	logger.error("CinderJob: {} failed; Details: {}", jobName, errorDescription);
                	
                }
                
            }
        } 
        catch (Exception e) 
        {
            processTransientError(messageId, trackingPeriodInMillis, e.getMessage(), e);
        } 
        finally 
        {
        	
            try 
            {
                updateStatus(jobContext);
            } 
            catch (Exception e) 
            {
                setErrorStatus(e.getMessage());
                logger.error("Problem while trying to update status", e);
            }
        }
        
        pollResult.setJobStatus(status);
        pollResult.setErrorDescription(errorDescription);
        return pollResult;
        
	}

	/**
	 * Checks if the given status indicates success for a Job.
	 *  This method has to be over-ridden by sub class Jobs.
	 *  because status AVAILABLE for Volume indicates success for volume create job,
	 *  but it refers to failure in case of volume attach job.
	 *
	 * @param currentStatus the current status
	 * @return true, if the job has succeeded
	 */
	protected boolean isJobSucceeded(String currentStatus) {
	    return (CinderConstants.ComponentStatus.AVAILABLE.getStatus().equalsIgnoreCase(currentStatus)
                || CinderConstants.ComponentStatus.IN_USE.getStatus().equalsIgnoreCase(currentStatus)
                || CinderConstants.ComponentStatus.DELETED.getStatus().equalsIgnoreCase(currentStatus));
    }
	
	protected boolean isJobFailed(String currentStatus) {
        return (CinderConstants.ComponentStatus.ERROR.getStatus().equalsIgnoreCase(currentStatus)
                || CinderConstants.ComponentStatus.ERROR_DELETING.getStatus().equalsIgnoreCase(currentStatus));
    }

    protected String getCurrentStatus(CinderApi cinderApi) throws Exception 
	{		
		return  cinderApi.getTaskStatus(jobId, componentType);
	}
	
	
	/**
	 * Updates the status of the job.
	 * 
	 * @param jobContext
	 * @throws Exception
	 */
	 public void updateStatus(JobContext jobContext) throws Exception 
	 {
		 
	        if (status == JobStatus.SUCCESS)
	        {
	            taskCompleter.ready(jobContext.getDbClient());
	        } 
	        else if (status == JobStatus.FAILED || status == JobStatus.FATAL_ERROR
	        			|| status == JobStatus.ERROR )
	        {	
	            ServiceError error = DeviceControllerErrors.cinder.jobFailed(errorDescription);
	            taskCompleter.error(jobContext.getDbClient(), error);
	        }
	        
	    }
	 
	 
	 private void processTransientError(String jobId, long trackingInterval, String errorMessage, Exception ex) 
	 {
	        status = JobStatus.ERROR;
	        errorDescription = errorMessage;
	        if (ex != null) 
	        {
	            logger.error(String.format("Error while processing CinderJob - Name: %s, ID: %s, Desc: %s Status: %s",
	                                    			jobName, jobId, errorDescription, status), ex);
	        } 
	        else 
	        {
	            logger.error(String.format("Error while processing CinderJob - Name: %s, ID: %s, Desc: %s Status: %s",
	                                                jobName, jobId, errorDescription, status));
	        }

	        // Check if job tracking limit was reached. Set status to FAILED in such a case.
	        setErrorTrackingTime(getErrorTrackingTime()+ trackingInterval);
	        logger.info(String.format("Tracking time of CinderJob in transient error status - %s, Name: %s, ID: %s. Status %s .",
	                							getErrorTrackingTime(), jobName, jobId, status));
	        
	        if (getErrorTrackingTime() > ERROR_TRACKING_LIMIT) 
	        {
	            status = JobStatus.FATAL_ERROR;
	            logger.error(String.format("Reached tracking time limit for CinderJob - Name: %s, ID: %s. Set status to %s .",
	                    							jobName, jobId, status));
	        }
	    }

}
