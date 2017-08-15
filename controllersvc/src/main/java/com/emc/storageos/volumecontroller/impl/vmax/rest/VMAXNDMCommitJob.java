/*
 * Copyright (c) 2017 DELL EMC
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.vmax.rest;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.BlockConsistencyGroup.MigrationStatus;
import com.emc.storageos.db.client.model.Migration;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.MigrationOperationTaskCompleter;

public class VMAXNDMCommitJob extends VMAXJob {
    private static final Logger logger = LoggerFactory.getLogger(VMAXNDMCommitJob.class);
    URI migrationURI;
    String sourceSerialNumber;
    String sgName;

    public VMAXNDMCommitJob(URI migrationURI, String sourceSerialNumber, String sgName, String jobId, URI storageProviderURI,
            TaskCompleter taskCompleter) {
        super(jobId, storageProviderURI, taskCompleter, "commitMigration");
        this.migrationURI = migrationURI;
        this.sourceSerialNumber = sourceSerialNumber;
        this.sgName = sgName;
    }

    @Override
    public void updateStatus(JobContext jobContext) throws Exception {
        try {
            if (isJobInTerminalSuccessState()) {
                Migration migration = jobContext.getDbClient().queryObject(Migration.class, migrationURI);
                // update migration end time
                long currentTime = System.currentTimeMillis();
                migration.setEndTime(String.valueOf(currentTime));
                // migration.setStatus("DONE"); // update migration status as Completed
                jobContext.getDbClient().updateObject(migration);
                logger.info("Updated end time in migration instance");
                if (isJobInTerminalSuccessState()) {
                    ((MigrationOperationTaskCompleter) taskCompleter).setMigrationStatus(MigrationStatus.Migrated.name());
                } else {
                    ((MigrationOperationTaskCompleter) taskCompleter).setMigrationStatus(MigrationStatus.MigFailed.name());
                }
            }

        } catch (Exception e) {
            logger.error("Exception occurred while updating the migration status", e);
        } finally {
            super.updateStatus(jobContext);
        }

    }
}
