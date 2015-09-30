/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplexcontroller.completers;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;

public class MigrationOperationTaskCompleter extends TaskCompleter {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private static final Logger s_logger = LoggerFactory.getLogger(MigrationOperationTaskCompleter.class);

    public MigrationOperationTaskCompleter(URI volURI, String opId) {
        super(Volume.class, volURI, opId);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {

        URI migrationURI = getId();
        String opId = getOpId();
        s_logger.info(String.format(
                "Migration %s for task %s completed with status %s", migrationURI,
                opId, status.name()));

        // Update the task status.
        setStatus(dbClient, status, coded);
        s_logger.info("Updated status for migration task");
    }

}
