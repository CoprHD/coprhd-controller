/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class MigrationWorkflowCompleter extends TaskLockingCompleter {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(MigrationWorkflowCompleter.class);

    public MigrationWorkflowCompleter(Class clazz, URI id, String opId) {
        super(clazz, id, opId);
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        log.info("START MigrationWorkflowCompleter complete");

        super.setStatus(dbClient, status, coded);
        super.complete(dbClient, status, coded);

        log.info("END MigrationWorkflowCompleter complete");
    }
}
