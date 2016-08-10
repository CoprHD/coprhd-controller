/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class ExportCreateCompleter extends ExportTaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(ExportCreateCompleter.class);
    private static final String EXPORT_CREATED_MSG = "Volume exported to ExportGroup %s";
    private static final String EXPORT_CREATE_FAILED_MSG = "Failed to export volume to ExportGroup %s";

    public ExportCreateCompleter(URI egUri, String task) {
        super(ExportGroup.class, egUri, task);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            super.complete(dbClient, status, coded);
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
            // If the operation does not complete successfully,
            // then make sure that the inactive flag is set to true
            if (!hasActiveMasks(dbClient, exportGroup)) {
                exportGroup.setInactive(status != Operation.Status.ready && status != Operation.Status.suspended_no_error);
            }
            dbClient.updateObject(exportGroup);

            _log.info("export_create completer: done");
            _log.info(String.format("Done ExportMaskCreate - Id: %s, OpId: %s, status: %s",
                    getId().toString(), getOpId(), status.name()));

            recordBlockExportOperation(dbClient, OperationTypeEnum.CREATE_EXPORT_GROUP, status, eventMessage(status, exportGroup),
                    exportGroup);
        } catch (Exception e) {
            _log.error(String.format("Failed updating status for ExportMaskCreate - Id: %s, OpId: %s",
                    getId().toString(), getOpId()), e);

        }
    }

    private String eventMessage(Operation.Status status, ExportGroup exportGroup) {
        return (status == Operation.Status.ready) ?
                String.format(EXPORT_CREATED_MSG, exportGroup.getLabel()) :
                String.format(EXPORT_CREATE_FAILED_MSG, exportGroup.getLabel());
    }
}
