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

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cinder.CinderConstants;
import com.emc.storageos.cinder.CinderEndPointInfo;
import com.emc.storageos.cinder.api.CinderApi;
import com.emc.storageos.cinder.errorhandling.CinderException;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;

public class CinderSnapshotDeleteJob extends CinderJob 
{

	private static final long serialVersionUID = 7550689923688901061L;
	private static final Logger _logger = LoggerFactory.getLogger(CinderDeleteVolumeJob.class);
	private String snapshotDeleteStatus = CinderConstants.ComponentStatus.DELETING.getStatus();
	
	public CinderSnapshotDeleteJob(String jobId, String jobName,
			URI storageSystem, String componentType, CinderEndPointInfo ep,
			TaskCompleter taskCompleter) {
		super(jobId, "Delete Snapshot:Snapshot Name:"+jobName, storageSystem, componentType, ep, taskCompleter);
	}

	/**
     * Called to update the job status when the volume delete job completes.     * 
     * @param jobContext The job context.
     */
    public void updateStatus(JobContext jobContext) throws Exception 
    {
        DbClient dbClient = jobContext.getDbClient();
        try 
        {
            if (status == JobStatus.IN_PROGRESS) 
            {
                return;
            }
            
            StringBuilder logMsgBuilder = new StringBuilder();
            
            URI snapshotId = getTaskCompleter().getId();
            BlockSnapshot snapshot = dbClient.queryObject(BlockSnapshot.class, snapshotId);
            if (status == JobStatus.SUCCESS) {
            	
            	snapshot.setInactive(true);
            	dbClient.persistObject(snapshot);
            	
            	if (logMsgBuilder.length() != 0) {
                    logMsgBuilder.append("\n");
                }
                logMsgBuilder.append(String.format("Successfully deleted snapshot %s", snapshot.getId()));
                
            } else if (status == JobStatus.FAILED) {
            	if (logMsgBuilder.length() != 0) {
                    logMsgBuilder.append("\n");
                }
                logMsgBuilder.append(String.format("Failed to delete snapshot %s", snapshot.getId()));
            }
            
            if (logMsgBuilder.length() > 0) {
                _logger.info(logMsgBuilder.toString());
            }
            
            
        } catch (Exception e) {
            setErrorStatus("Encountered an internal error during delete snapshot job status processing: "+ e.getMessage());
            super.updateStatus(jobContext);
            _logger.error("Caught exception while handling updateStatus for delete snapshot job.", e);
        } finally {
        	super.updateStatus(jobContext);
        }
    }

    @Override
    protected boolean isJobSucceeded(String currentStatus) {
        return (CinderConstants.ComponentStatus.DELETED.getStatus().equalsIgnoreCase(currentStatus));
    }

    @Override
    protected boolean isJobFailed(String currentStatus) {
        return (CinderConstants.ComponentStatus.ERROR.getStatus().equalsIgnoreCase(currentStatus)
                || CinderConstants.ComponentStatus.ERROR_DELETING.getStatus().equalsIgnoreCase(currentStatus));
    }

    /**
     * Gets the current status of volume deletion
     */
    protected String getCurrentStatus(CinderApi cinderApi) throws Exception 
	{	
    	_logger.info("Start getCurrentStatus() for CinderSnapshotDeleteJob");
    	try
    	{
    		//As long as the status remains "Deleting", this will go through
    		snapshotDeleteStatus = cinderApi.getTaskStatus(getJobId(), CinderConstants.ComponentType.snapshot.name());
    	}
    	catch(CinderException ce)
    	{
    		//Here means, the snapshot got deleted
    		
    		//check if the earlier status was "deleting", to be sure
    		// that the delete snapshot was attempted
    		if(CinderConstants.ComponentStatus.DELETING.getStatus().equalsIgnoreCase(snapshotDeleteStatus))
    		{
    			snapshotDeleteStatus = CinderConstants.ComponentStatus.DELETED.getStatus();
    		}
    		
    	}
    	
    	_logger.info("End getCurrentStatus() Status is:" + snapshotDeleteStatus);
    	return snapshotDeleteStatus;
		 
	}
}
