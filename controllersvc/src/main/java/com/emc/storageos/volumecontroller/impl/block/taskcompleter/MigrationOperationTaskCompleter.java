/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Migration;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;

/**
 * Completer for migration operations.
 */
public class MigrationOperationTaskCompleter extends TaskCompleter {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(MigrationOperationTaskCompleter.class);

    public MigrationOperationTaskCompleter(URI migrationURI, String opId) {
        super(Migration.class, migrationURI, opId);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        String opId = getOpId();
        logger.info(String.format("Migration operation for the task %s completed with status %s",
                opId, status.name()));

        // Update the task status.
        setStatus(dbClient, status, coded);
        logger.info("Updated status for migration task on {}", getId());
    }
}
