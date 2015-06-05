/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/*
 * Copyright (c) $today_year. EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller.impl.smis.job;

import java.net.URI;

import javax.cim.CIMObjectPath;
import javax.wbem.CloseableIterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;

/**
 * A job used for deleting VDEVs for a snapshot target group
 */
public class SmisDeleteVmaxCGTargetVolumesJob extends SmisJob {
    private static final Logger _log = LoggerFactory.getLogger(SmisDeleteVmaxCGTargetVolumesJob.class);

    private String[] deviceIds;
    
    public SmisDeleteVmaxCGTargetVolumesJob(CIMObjectPath cimJob, URI storageSystem, String[] deviceIds, TaskCompleter taskCompleter) {
    	super(cimJob, storageSystem, taskCompleter, "DeleteVdevVolumes");
        this.deviceIds=deviceIds;
    }

    public void updateStatus(JobContext jobContext) throws Exception {
        CloseableIterator<CIMObjectPath> iterator = null;
        DbClient dbClient = jobContext.getDbClient();
        JobStatus jobStatus = getJobStatus();
        try {
            if (jobStatus == JobStatus.SUCCESS) {
            	_log.info("Vmax target volumes successfully removed: {}", deviceIds);        
            } else if (jobStatus == JobStatus.FAILED || jobStatus == JobStatus.FATAL_ERROR) {
                _log.info("Failed to delete target volumes: {}", deviceIds);
            }
        } catch (Exception e) {
            setPostProcessingFailedStatus(e.getMessage());
            _log.error("Caught an exception while trying to updateStatus for SmisDeleteVmaxCGTargetVolumesJob", e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("updateStatus", e.getMessage());
            getTaskCompleter().error(dbClient, error);
        } finally {
            super.updateStatus(jobContext);
            if (iterator != null) {
                iterator.close();
            }
        }
    }
    
}




