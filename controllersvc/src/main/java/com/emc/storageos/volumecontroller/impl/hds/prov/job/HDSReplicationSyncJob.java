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
package com.emc.storageos.volumecontroller.impl.hds.prov.job;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.hds.api.HDSApiClient;
import com.emc.storageos.hds.api.HDSApiProtectionManager;
import com.emc.storageos.hds.model.ReplicationInfo;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.JobPollResult;
import com.emc.storageos.volumecontroller.impl.hds.prov.utils.HDSUtils;
import com.google.gson.internal.Pair;

public class HDSReplicationSyncJob extends HDSJob {
	
	private static final Logger log = LoggerFactory.getLogger(HDSReplicationSyncJob.class);
	private String sourceNativeId;
	private String targetNativeId;
	private ReplicationStatus expectedStatus;
	private int pollingCount;

	public HDSReplicationSyncJob(URI storageSystem,
			 String sourceNativeId, String targetNativeId, ReplicationStatus expectedStatus, TaskCompleter taskCompleter) {
		super(null, storageSystem, taskCompleter, "WaitForSynchronized");
		this.sourceNativeId = sourceNativeId;
		this.targetNativeId = targetNativeId;
		this.expectedStatus = expectedStatus;
	}
	
	
	public enum ReplicationStatus{
    	/*Status of replication
    	-1 : Unknown
    	0 : Simplex
    	1 : Pair
    	8 : Copying
    	9 : Reverse-Copying
    	16 : Split
    	17 : Error
    	18 : Error in LUSE
    	24 : Suspending
    	25 : Deleting*/
    	UNKNOWN ("-1"),
    	SIMPLEX ("0"),
    	PAIR("1"),
    	COPYING("8"),
    	REVERSE_COPYING("9"),
    	SPLIT("16"),
    	ERROR("17"),
    	ERROR_IN_LUSE("18"),
    	SUSPENDING("24"),
    	DELETEING("25");
    	private final String statusCode;
    	ReplicationStatus(String value){
    		this.statusCode = value;
    	}
    	
    	public String getStatusCode(){
    		return statusCode;
    	}
    	
    	public static ReplicationStatus getReplicationStatusFromCode(String code){
    		log.info("Replication code collected from device manager :{},code");
    		for(ReplicationStatus s: ReplicationStatus.values()){
    			if(s.getStatusCode().equals(code)){
    				return s;
    			}
    		}
    		return UNKNOWN;
    	}
    	
    	public boolean isErrorStatus(){
    		return (this.getStatusCode() == ERROR.getStatusCode() || 
    				this.getStatusCode() == ERROR_IN_LUSE.getStatusCode());
    	}
    }
	
	/**
	 * 
	 */
	@Override
	public JobPollResult poll(JobContext jobContext, long trackingPeriodInMillis) {
        try {
            StorageSystem storageSystem = jobContext.getDbClient().queryObject(StorageSystem.class, getStorageSystemURI());

            //log.info("HDSJob: Looking up job: id {}, provider: {} ", messageId, storageSystem.getActiveProviderURI());
            HDSApiClient hdsApiClient = jobContext.getHdsApiFactory().getClient(HDSUtils.getHDSServerManagementServerInfo(storageSystem), storageSystem.getSmisUserName(), storageSystem.getSmisPassword());
            
            if (hdsApiClient == null) {
                String errorMessage = "No HDS client found for provider ip: " + storageSystem.getActiveProviderURI();
                processTransientError(trackingPeriodInMillis, errorMessage, null);
            } else {
            	HDSApiProtectionManager apiProtectionManager = hdsApiClient.getHdsApiProtectionManager();
            	Pair<ReplicationInfo, String> response = apiProtectionManager.getReplicationInfoFromSystem(sourceNativeId, targetNativeId);
                ReplicationStatus status = ReplicationStatus.UNKNOWN;
                
                if(response != null){
                	status = ReplicationStatus.getReplicationStatusFromCode(response.first.getStatus());
                	log.info("Expected status :{}",expectedStatus.name());
                	log.info("Current replication status :{}",status.name());
                	if(expectedStatus==status){
                        _status = JobStatus.SUCCESS;
                        _pollResult.setJobPercentComplete(100);
                        log.info("HDSReplicationSyncJob: {} {} succeeded", sourceNativeId, targetNativeId);
                    
                	} else if(!status.isErrorStatus()){
                		/**
                		 * HiCommand Device Manager is having issue to get the modified replication info 
                		 * status from pair management server. To get the latest pair status from device manager,
                		 * we have introduced a workaround to trigger pair mgmt server host update call.
                		 * Once Device Manager has a fix for this issue, we can revert this work around.
                		 * 
                		 * Refreshing host (Pair Mgmt Server) for every 10th polling.
                		 */
                		if(++pollingCount % 10 ==0){
                			log.info("Attempting to refresh pair managerment server :{}",response.second);
                        	apiProtectionManager.refreshPairManagementServer(response.second);
                        }
                	}
                }
                
                if(response == null || status.isErrorStatus()){
                	_status = JobStatus.FAILED;
                    _pollResult.setJobPercentComplete(100);
                    _errorDescription = String
                            .format("Replication Status %1$s",
                                    new Object[] {status.name()});
                    log.error("HDSReplicationSyncJob: {} failed; Details: {}", getJobName(), _errorDescription);
            	}
                
            }
        } catch (Exception e) {
            processTransientError(trackingPeriodInMillis, e.getMessage(), e);
        	log.error(e.getMessage(),e);
        } finally {
            try {
            	_postProcessingStatus = JobStatus.SUCCESS;
                updateStatus(jobContext);
                if (_postProcessingStatus == JobStatus.ERROR) {
                    processPostProcessingError(trackingPeriodInMillis, _errorDescription, null);
                }
            } catch (Exception e) {
                setErrorStatus(e.getMessage());
                log.error("Problem while trying to update status", e);
            }
        }
        _pollResult.setJobStatus(_status);
        _pollResult.setErrorDescription(_errorDescription);
        return _pollResult;
    }
	
	protected void processTransientError(long trackingInterval, String errorMessage, Exception ex) {
        _status = JobStatus.ERROR;
        _errorDescription = errorMessage;
        if (ex != null) {
            log.error(String.format("Error while processing HDSReplicationSyncJob - Name: %s, source: %s, target: %s, Desc: %s Status: %s",
                                    getJobName(), sourceNativeId,targetNativeId, _errorDescription, _status), ex);
        } else {
            log.error(String.format("Error while processing HDSReplicationSyncJob - Name: %s, source: %s, target: %s, Desc: %s Status: %s",
                                                getJobName(), sourceNativeId,targetNativeId, _errorDescription, _status));
        }

        // Check if job tracking limit was reached. Set status to FAILED in such a case.
        setErrorTrackingTime(getErrorTrackingTime()+ trackingInterval);
        log.info(String.format("Tracking time of HDSReplicationSyncJob in transient error status - %s, Name: %s, source: %s, target: %s. Status %s .",
                getErrorTrackingTime(), getJobName(), sourceNativeId,targetNativeId, _status));
        if (getErrorTrackingTime() > ERROR_TRACKING_LIMIT) {
            _status = JobStatus.FATAL_ERROR;
            log.error(String.format("Reached tracking time limit for HDSReplicationSyncJob - Name: %s, source: %s, target: %s. Set status to %s .",
                    getJobName(), sourceNativeId,targetNativeId, _status));
        }
    }
	
	private void processPostProcessingError(long trackingInterval, String errorMessage, Exception ex) {
        _postProcessingStatus = JobStatus.ERROR;
        _errorDescription = errorMessage;
        if (ex != null) {
            log.error(String.format("Error while post processing HDSJob - Name: %s, source: %s, target: %s, Desc: %s Status: %s",
                    getJobName(), sourceNativeId,targetNativeId, _errorDescription, _postProcessingStatus), ex);
        } else {
            log.error(String.format("Error while processing HDSJob - Name: %s, source: %s, target: %s, Desc: %s Status: %s",
                    getJobName(), sourceNativeId,targetNativeId, _errorDescription, _postProcessingStatus));
        }

        // Check if job post processing tracking limit was reached. Set post processing status to FAILED in such a case.
        if (getPostProcessingErrorTrackingStartTime() == 0) {
            setPostProcessingErrorTrackingStartTime(System.currentTimeMillis());
        }
        long postProcessingErrorTrackingTime = System.currentTimeMillis() - getPostProcessingErrorTrackingStartTime();
        log.info(String.format("Tracking time of HDSJob in post processing error - %s, Name: %s, source: %s, target: %s, Status: %s, PostProcessing status: %s .",
                postProcessingErrorTrackingTime, getJobName(), sourceNativeId,targetNativeId, _status, _postProcessingStatus));
        if (postProcessingErrorTrackingTime > POST_PROCESSING_ERROR_TRACKING_LIMIT) {
            _postProcessingStatus = JobStatus.FAILED;
            log.error(String.format("Reached tracking time limit for HDSJob post processing - Name: %s, source: %s, target: %s, Set post processing status to %s .",
                    getJobName(), sourceNativeId,targetNativeId, _postProcessingStatus));
        }
    }
}
