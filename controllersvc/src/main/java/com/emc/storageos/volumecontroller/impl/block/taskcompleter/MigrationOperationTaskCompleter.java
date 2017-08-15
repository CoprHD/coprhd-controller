/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.Migration;
import com.emc.storageos.db.client.model.Migration.JobStatus;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

/**
 * Completer for migration operations.
 */
public class MigrationOperationTaskCompleter extends TaskLockingCompleter {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(MigrationOperationTaskCompleter.class);
    private String migrationStatus;

    public MigrationOperationTaskCompleter(URI migrationURI, String opId) {
        super(Migration.class, migrationURI, opId);
    }

    public void setMigrationStatus(String migrationStatus) {
        this.migrationStatus = migrationStatus;
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        String opId = getOpId();
        logger.info(String.format("Migration operation for the task %s completed with status %s",
                opId, status.name()));

        try {
            Migration migration = dbClient.queryObject(Migration.class, getId());
            String jobStatus;
            if (migrationStatus != null) {
                migration.setMigrationStatus(migrationStatus);
                // update the migration status in CG as well.
                URI cgURI = migration.getConsistencyGroup();
                if (!NullColumnValueGetter.isNullURI(cgURI)) {
                    BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, cgURI);
                    cg.setMigrationStatus(migrationStatus);
                    dbClient.updateObject(cg);
                }
            }
            switch (status) {
                case ready:
                    jobStatus = JobStatus.COMPLETE.name();
                    break;
                case error:
                    jobStatus = JobStatus.ERROR.name();
                    break;
                default:
                    jobStatus = JobStatus.IN_PROGRESS.name();
            }
            migration.setJobStatus(jobStatus);
            dbClient.updateObject(migration);
        } catch (Exception ex) {
            logger.warn("Problem while updating status in migration. %s", ex.getMessage());
        }

        // Update the task status.
        super.setStatus(dbClient, status, coded);
        super.complete(dbClient, status, coded);

        logger.info("Updated status for migration task on {}", getId());
    }
}
