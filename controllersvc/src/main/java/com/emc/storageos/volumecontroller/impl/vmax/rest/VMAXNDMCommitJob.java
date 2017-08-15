/*
 * Copyright (c) 2017 DELL EMC
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.vmax.rest;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.Migration;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;

public class VMAXNDMCommitJob extends VMAXJob {
    private static final Logger logger = LoggerFactory.getLogger(VMAXNDMCommitJob.class);
    URI migrationURI;

    public VMAXNDMCommitJob(String jobId, URI storageProviderURI, TaskCompleter taskCompleter, URI migrationURI) {
        super(jobId, storageProviderURI, taskCompleter, "commitMigration");
        this.migrationURI = migrationURI;
    }

    @Override
    public void updateStatus(JobContext jobContext) throws Exception {
        super.updateStatus(jobContext);
        if (isJobInTerminalSuccessState()) {
            Migration migration = jobContext.getDbClient().queryObject(Migration.class, migrationURI);
            // update migration end time
            long currentTime = System.currentTimeMillis();
            migration.setEndTime(String.valueOf(currentTime));
            // migration.setStatus("DONE"); // update migration status as Completed
            jobContext.getDbClient().updateObject(migration);
            logger.info("Updated end time in migration instance");
        }
    }
}
