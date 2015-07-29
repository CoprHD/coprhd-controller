/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) 2012. EMC Corporation
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
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockSnapshotDeleteCompleter;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMObjectPath;
import javax.wbem.client.WBEMClient;
import java.net.URI;

public class SmisBlockDeleteSnapshotJob extends SmisJob {
    private static final Logger _log = LoggerFactory.getLogger(SmisBlockDeleteSnapshotJob.class);

    public SmisBlockDeleteSnapshotJob(CIMObjectPath cimJob,
            URI storageSystem,
            TaskCompleter taskCompleter) {
        super(cimJob, storageSystem, taskCompleter, "DeleteBlockSnapshot");
    }

    public void updateStatus(JobContext jobContext) throws Exception {
        DbClient dbClient = jobContext.getDbClient();
        JobStatus jobStatus = getJobStatus();
        try {
            BlockSnapshotDeleteCompleter completer = (BlockSnapshotDeleteCompleter) getTaskCompleter();
            BlockSnapshot snapshot = dbClient.queryObject(BlockSnapshot.class, completer.getId());

            if (jobStatus == JobStatus.IN_PROGRESS) {
                return;
            }

            // If terminal state update storage pool capacity
            if (jobStatus == JobStatus.SUCCESS || jobStatus == JobStatus.FAILED || jobStatus == JobStatus.FATAL_ERROR) {
                CIMConnectionFactory cimConnectionFactory = jobContext.getCimConnectionFactory();
                WBEMClient client = getWBEMClient(dbClient, cimConnectionFactory);
                NamedURI volumeURI = snapshot.getParent();
                Volume volume = dbClient.queryObject(Volume.class, volumeURI);
                URI poolURI = volume.getPool();
                // Update capacity of storage pools.
                SmisUtils.updateStoragePoolCapacity(dbClient, client, poolURI);
            }

            if (jobStatus == JobStatus.SUCCESS) {
                _log.info("Deleting snapshot job was successful.");
                snapshot.setInactive(true);
                dbClient.persistObject(snapshot);
            } else if (jobStatus == JobStatus.FAILED || jobStatus == JobStatus.FATAL_ERROR) {
                _log.info("Failed to delete snapshot: {}", getTaskCompleter().getId());
            }
        } catch (Exception e) {
            setFatalErrorStatus(e.getMessage());
            _log.error("Caught an exception while trying to updateStatus for SmisBlockDeleteSnapshotJob", e);
            Operation updateOp = new Operation();
            updateOp.setStatus("Encountered an internal error during block delete snapshot job status processing: " + e.getMessage());
            dbClient.updateTaskOpStatus(BlockSnapshot.class, getTaskCompleter().getId(), getTaskCompleter().getOpId(),
                    updateOp);
        } finally {
            super.updateStatus(jobContext);
        }
    }

}