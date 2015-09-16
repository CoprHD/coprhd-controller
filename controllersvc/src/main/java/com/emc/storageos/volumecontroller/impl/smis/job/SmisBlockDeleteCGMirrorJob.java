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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SmisBlockDeleteCGMirrorJob extends SmisBlockMirrorJob {
    private static final long serialVersionUID = 1L;
    private static Logger _log = LoggerFactory.getLogger(SmisBlockDeleteCGMirrorJob.class);

    public SmisBlockDeleteCGMirrorJob(CIMObjectPath cimJob, URI storageSystem, TaskCompleter taskCompleter) {
        super(cimJob, storageSystem, taskCompleter, "DeleteGroupMirrors");
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
            List<BlockMirror> mirrors = dbClient.queryObject(BlockMirror.class, completer.getIds());

            // If terminal state update storage pool capacity
            if (jobStatus == JobStatus.SUCCESS || jobStatus == JobStatus.FAILED || jobStatus == JobStatus.FATAL_ERROR) {
                CIMConnectionFactory cimConnectionFactory = jobContext.getCimConnectionFactory();
                WBEMClient client = getWBEMClient(dbClient, cimConnectionFactory);
                Set<URI> poolURIs = new HashSet<URI>();
                for (BlockMirror mirror : mirrors) {
                    poolURIs.add(mirror.getPool());
                }

                for (URI poolURI : poolURIs) {
                    // Update capacity of storage pools.
                    SmisUtils.updateStoragePoolCapacity(dbClient, client, poolURI);
                }
            }

            if (jobStatus == JobStatus.SUCCESS) {
                _log.info("Group mirror delete success");
                dbClient.markForDeletion(mirrors);
            } else if (jobStatus == JobStatus.FATAL_ERROR || jobStatus == JobStatus.FAILED) {
                String msg = String.format("Failed to delete group mirrors");
                _log.error(msg);
                getTaskCompleter().error(dbClient, DeviceControllerErrors.smis.jobFailed(msg));
            }
        } catch (Exception e) {
            setFatalErrorStatus("Encountered an internal error during block delete group mirror job status processing: " + e.getMessage());
            _log.error("Caught an exception while trying to updateStatus for SmisBlockDeleteCGMirrorJob", e);
            getTaskCompleter().error(dbClient, DeviceControllerErrors.smis.jobFailed(e.getMessage()));
        } finally {
            super.updateStatus(jobContext);
        }
    }
}
