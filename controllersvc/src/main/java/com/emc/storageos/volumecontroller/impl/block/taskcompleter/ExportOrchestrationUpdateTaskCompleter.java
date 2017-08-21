/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;

import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class ExportOrchestrationUpdateTaskCompleter extends ExportTaskCompleter {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(ExportOrchestrationUpdateTaskCompleter.class);
    
    public ExportOrchestrationUpdateTaskCompleter(URI id, String opId) {
        super(ExportGroup.class, id, opId);
    }
    
    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            ExportGroup exportGroup = dbClient.queryObject(ExportGroup.class, getId());
            Operation operation = new Operation();
            switch (status) {
                case error:
                    operation.error(coded);
                    break;
                case ready:
                    operation.ready();
                    break;
                case suspended_no_error:
                    operation.suspendedNoError();
                    break;
                case suspended_error:
                    operation.suspendedError(coded);
                    break;
                default:
                    break;
            }

            exportGroup.getOpStatus().updateTaskStatus(getOpId(), operation);
            dbClient.updateObject(exportGroup);

        } catch (Exception e) {
            log.error(String.format("Failed updating status - Id: %s, OpId: %s",
                    getId().toString(), getOpId()), e);

        } finally {
            super.complete(dbClient, status, coded);
        }
    }


}
