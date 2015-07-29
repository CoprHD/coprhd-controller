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

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;

/**
 * HDS Snapshot Delete Job
 */
public class HDSDeleteSnapshotJob extends HDSJob {

    private static final Logger log = LoggerFactory.getLogger(HDSDeleteSnapshotJob.class);

    public HDSDeleteSnapshotJob(String hdsJob, URI storageSystem,
            TaskCompleter taskCompleter) {
        super(hdsJob, storageSystem, taskCompleter, "DeleteSnapshot");
    }

    public HDSDeleteSnapshotJob(String messageId, URI storageSystem,
            TaskCompleter taskCompleter, String name) {
        super(messageId, storageSystem, taskCompleter, name);
    }

    public void updateStatus(JobContext jobContext) throws Exception {
        DbClient dbClient = jobContext.getDbClient();
        try {
            if (_status == JobStatus.IN_PROGRESS) {
                return;
            }

            String opId = getTaskCompleter().getOpId();
            StringBuilder logMsgBuilder = new StringBuilder(
                    String.format("Updating status of job %s to %s", opId, _status.name()));

            URI snapshotId = getTaskCompleter().getId(0);
            log.debug("snapshotId :{}", snapshotId);

            BlockSnapshot snapshotObj = (BlockSnapshot) BlockObject.fetch(dbClient, snapshotId);

            if (_status == JobStatus.SUCCESS) {
                snapshotObj.setInactive(true);
                dbClient.persistObject(snapshotObj);

                if (logMsgBuilder.length() != 0) {
                    logMsgBuilder.append("\n");
                }
                logMsgBuilder.append(String.format("Successfully deleted snapshot %s",
                        snapshotId));
            } else if (_status == JobStatus.FAILED) {
                logMsgBuilder.append(String.format("Failed to delete snapshot: %s", snapshotId));
            }

            if (logMsgBuilder.length() > 0) {
                log.info(logMsgBuilder.toString());
            }
        } catch (Exception e) {
            setErrorStatus("Encountered an internal error during delete snapshot job status processing: "
                    + e.getMessage());
            super.updateStatus(jobContext);
            log.error(
                    "Caught exception while handling updateStatus for delete snapshot job.",
                    e);
        } finally {
            _postProcessingStatus = JobStatus.SUCCESS;
            super.updateStatus(jobContext);
        }
    }
}
