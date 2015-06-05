/**
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

import java.net.URI;
import java.util.Calendar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cinder.CinderConstants;
import com.emc.storageos.cinder.CinderEndPointInfo;
import com.emc.storageos.cinder.api.CinderApi;
import com.emc.storageos.cinder.model.SnapshotCreateResponse;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;

public class CinderSnapshotCreateJob extends CinderJob 
{

	private static final long serialVersionUID = 1613096868080488959L;
	private static final Logger _logger = LoggerFactory.getLogger(CinderSnapshotCreateJob.class);
	
	/**
	 * @param jobId
	 * @param jobName
	 * @param storageSystem
	 * @param componentType
	 * @param ep
	 * @param taskCompleter
	 */
	public CinderSnapshotCreateJob(String jobId, String jobName,
															 URI storageSystem, String componentType, 
															 CinderEndPointInfo ep,
															 TaskCompleter taskCompleter) 
	{
		super(jobId, jobName, storageSystem, componentType, ep, taskCompleter);
	}
	
	@Override
	public void updateStatus(JobContext jobContext) throws Exception 
	{
		DbClient dbClient = jobContext.getDbClient();
        try 
        {
        	//Do nothing if the job is not completed yet
            if (status == JobStatus.IN_PROGRESS)
            {
                return;
            }
            
            String opId = getTaskCompleter().getOpId();
            StringBuilder logMsgBuilder = new StringBuilder(
            		String.format("Updating status of job %s to %s", opId, status.name()));
            StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, getStorageSystemURI());
            
            CinderApi cinderApi = jobContext.getCinderApiFactory().getApi(
            		storageSystem.getActiveProviderURI(), getEndPointInfo());

            URI snapshotId = getTaskCompleter().getId(0);

            if (status == JobStatus.SUCCESS) 
            {
            	SnapshotCreateResponse snapshotDetails = cinderApi.showSnapshot(getJobId());
            	
            	BlockSnapshot snapshot = dbClient.queryObject(BlockSnapshot.class, snapshotId);
            	snapshot.setNativeId(snapshotDetails.snapshot.id);
            	snapshot.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(storageSystem, snapshot));
            	snapshot.setInactive(false);
            	snapshot.setCreationTime(Calendar.getInstance());
            	dbClient.persistObject(snapshot);

            	if (logMsgBuilder.length() != 0) 
                {
                    logMsgBuilder.append("\n");
                }
                logMsgBuilder.append(String.format(
                        "Created Snapshot successfully .. NativeId: %s, URI: %s", snapshot.getNativeId(),
                        getTaskCompleter().getId()));
            } 
            else if (status == JobStatus.FAILED) 
            {
            	logMsgBuilder.append("\n");
                logMsgBuilder.append(String.format(
                        "Task %s failed to create volume: %s", opId, getTaskCompleter().getId().toString()));
                Snapshot snapshot = dbClient.queryObject(Snapshot.class, snapshotId);
                snapshot.setInactive(true);
                dbClient.persistObject(snapshot);
            }
            _logger.info(logMsgBuilder.toString());
        } 
        catch (Exception e) 
        {
        	_logger.error("Caught an exception while trying to updateStatus for CinderCreateSnapshotJob", e);
            setErrorStatus("Encountered an internal error during snapshot create job status processing : " + e.getMessage());
        } 
        finally 
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
}
