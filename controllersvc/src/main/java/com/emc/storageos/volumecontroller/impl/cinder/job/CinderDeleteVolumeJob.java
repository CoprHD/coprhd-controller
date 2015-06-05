/**
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cinder.CinderConstants;
import com.emc.storageos.cinder.CinderEndPointInfo;
import com.emc.storageos.cinder.api.CinderApi;
import com.emc.storageos.cinder.errorhandling.CinderException;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.cinder.CinderUtils;

public class CinderDeleteVolumeJob extends CinderJob 
{
	private static final Logger logger = LoggerFactory.getLogger(CinderDeleteVolumeJob.class);
	private static final long serialVersionUID = -1477978360849416445L;
	private String volumeDeleteStatus = CinderConstants.ComponentStatus.DELETING.getStatus();

	/**
	 * @param jobId
	 * @param jobName
	 * @param storageSystem
	 * @param componentType
	 * @param ep
	 * @param taskCompleter
	 */
	public CinderDeleteVolumeJob(String jobId, String jobName,
												URI storageSystem, String componentType, 
												CinderEndPointInfo ep, TaskCompleter taskCompleter) 
	{
		super(jobId, "DeleteVolume:VolumeName:"+jobName, storageSystem, componentType, ep, taskCompleter);		
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
            StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, getStorageSystemURI());
            CinderApi cinderApi = jobContext.getCinderApiFactory().getApi(storageSystem.getActiveProviderURI(), getEndPointInfo());

            // Get list of volumes; get set of storage pool ids to which they belong.
            List<Volume> volumes = new ArrayList<Volume>();
            Set<URI> poolURIs = new HashSet<URI>();
            long deletedVolumesTotCapacity = 0L;
            for (URI id : getTaskCompleter().getIds()) 
            {
                Volume volume = dbClient.queryObject(Volume.class, id);
                volumes.add(volume);
                poolURIs.add(volume.getPool());
                
                deletedVolumesTotCapacity += volume.getCapacity();
            }

            // If terminal state update storage pool capacity
            if (status == JobStatus.SUCCESS) 
            {
                // Update capacity of storage pools.
                for (URI poolURI : poolURIs) 
                {
                    StoragePool storagePool = dbClient.queryObject(StoragePool.class, poolURI);
                    CinderUtils.updateStoragePoolCapacity(dbClient, cinderApi, storagePool,
                    		String.valueOf(deletedVolumesTotCapacity/CinderConstants.BYTES_TO_GB), true);
                }
            }

            StringBuilder logMsgBuilder = new StringBuilder();
            if (status == JobStatus.SUCCESS) 
            {
                for (Volume volume : volumes) 
                {
                    volume.setInactive(true);
                    dbClient.persistObject(volume);
                    dbClient.ready(Volume.class, volume.getId(), getTaskCompleter().getOpId());
                    
                    if (logMsgBuilder.length() != 0) 
                    {
                        logMsgBuilder.append("\n");
                    }
                    logMsgBuilder.append(String.format("Successfully deleted volume %s", volume.getId()));
                }

            } 
            else if (status == JobStatus.FAILED) 
            {
                for (URI id : getTaskCompleter().getIds()) 
                {
                    if (logMsgBuilder.length() != 0) 
                    {
                        logMsgBuilder.append("\n");
                    }
                    logMsgBuilder.append(String.format("Failed to delete volume: %s", id));
                }
            }
            
            if (logMsgBuilder.length() > 0) 
            {
                logger.info(logMsgBuilder.toString());
            }
        } 
        catch (Exception e) 
        {
            setErrorStatus("Encountered an internal error during delete volume job status processing: "+ e.getMessage());
            logger.error("Caught exception while handling updateStatus for delete volume job.", e);
        }
        finally
        {
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
    	logger.info("Start getCurrentStatus()");
    	try
    	{
    		//As long as the status remains "Deleting", this will go through
    		volumeDeleteStatus = cinderApi.getTaskStatus(getJobId(), CinderConstants.ComponentType.volume.name());
    	}
    	catch(CinderException ce)
    	{
    		//Here means, the volume got deleted
    		
    		//check if the earlier status was "deleting", to be sure
    		// that the delete volume was attempted
    		if(CinderConstants.ComponentStatus.DELETING.getStatus().equalsIgnoreCase(volumeDeleteStatus))
    		{
    			volumeDeleteStatus = CinderConstants.ComponentStatus.DELETED.getStatus();
    		}
    		
    	}
    	
    	logger.info("End getCurrentStatus() Status is:"+volumeDeleteStatus);
    	return volumeDeleteStatus;
		 
	}

}
