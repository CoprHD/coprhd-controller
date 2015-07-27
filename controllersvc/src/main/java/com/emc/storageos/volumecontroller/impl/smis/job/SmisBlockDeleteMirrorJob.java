/*
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

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockMirrorDeleteCompleter;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMObjectPath;
import javax.wbem.client.WBEMClient;
import java.net.URI;

public class SmisBlockDeleteMirrorJob extends SmisBlockMirrorJob {
    private static Logger _log = LoggerFactory.getLogger(SmisBlockDeleteMirrorJob.class);

    public SmisBlockDeleteMirrorJob(CIMObjectPath cimJob, URI storageSystem, TaskCompleter taskCompleter) {
        super(cimJob, storageSystem, taskCompleter, "DeleteMirror");
    }

    @Override
    public void updateStatus(JobContext jobContext) throws Exception {
        DbClient dbClient = jobContext.getDbClient();
        JobStatus jobStatus = getJobStatus();
        try {
            if (jobStatus == JobStatus.IN_PROGRESS) {
                return;
            }
            BlockMirrorDeleteCompleter completer = (BlockMirrorDeleteCompleter) getTaskCompleter();
            BlockMirror mirror = dbClient.queryObject(BlockMirror.class, completer.getMirrorURI());

            // If terminal state update storage pool capacity
            if (jobStatus == JobStatus.SUCCESS || jobStatus == JobStatus.FAILED || jobStatus == JobStatus.FATAL_ERROR) {
                CIMConnectionFactory cimConnectionFactory = jobContext.getCimConnectionFactory();
                WBEMClient client = getWBEMClient(dbClient, cimConnectionFactory);
                URI poolURI = mirror.getPool();
                // Update capacity of storage pools.
                SmisUtils.updateStoragePoolCapacity(dbClient, client, poolURI);
            }

            if (jobStatus == JobStatus.SUCCESS) {
                _log.info("Mirror delete success");
                Volume volume = dbClient.queryObject(Volume.class, mirror.getSource().getURI());

                dbClient.markForDeletion(mirror);
                _log.info(String.format("Deleted BlockMirror %s on Volume %s", mirror, volume));
            } else if (jobStatus == JobStatus.FATAL_ERROR || jobStatus == JobStatus.FAILED) {
                String msg = String.format("Failed to delete mirror %s", mirror.getId());
                _log.error(msg);
                getTaskCompleter().error(dbClient, DeviceControllerErrors.smis.jobFailed(msg));
            }
        } catch (Exception e) {
            setFatalErrorStatus("Encountered an internal error during block delete mirror job status processing: " + e.getMessage());
            _log.error("Caught an exception while trying to updateStatus for SmisBlockDeleteMirrorJob", e);
            getTaskCompleter().error(dbClient, DeviceControllerErrors.smis.jobFailed(e.getMessage()));
        } finally {
            super.updateStatus(jobContext);
        }
    }
}
