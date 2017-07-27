/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;

/**
 * Completer for migration environment create/delete operations.
 */
public class MigrationEnvironmentTaskCompleter extends TaskCompleter {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(MigrationEnvironmentTaskCompleter.class);

    public MigrationEnvironmentTaskCompleter(URI sourceSystemURI, String opId) {
        super(StorageSystem.class, sourceSystemURI, opId);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        String opId = getOpId();
        logger.info(String.format("Migration environment operation for the task %s completed with status %s",
                opId, status.name()));

        // Update the task status.
        setStatus(dbClient, status, coded);
        logger.info("Updated status for migration environment task on {}", getId());
    }
}
