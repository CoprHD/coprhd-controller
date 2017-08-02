/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.util.ExportUtils;

@SuppressWarnings("serial")
public class ExportDeleteCompleter extends ExportTaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(ExportDeleteCompleter.class);
    private static final String EXPORT_DELETED_MSG = "Volume Deleted from ExportGroup %s";
    private static final String EXPORT_DELETE_FAILED_MSG = "Failed to delete Volume from ExportGroup %s";
    private boolean checkForActiveMasks = true;

    public ExportDeleteCompleter(URI egUri, String task) {
        super(ExportGroup.class, egUri, task);
    }

    public ExportDeleteCompleter(URI egUri, boolean checkForActiveMasks, String task) {
        super(ExportGroup.class, egUri, task);
        // This flag is used for logic in the complete call. By default, we
        // we will check if the ExportMasks associated with the ExportGroup
        // are active.
        this.checkForActiveMasks = checkForActiveMasks;
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            super.complete(dbClient, status, coded);
            /**
             * remove export mask from export group and delete export mask
             */
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
            if (exportGroup.checkInternalFlags(Flag.DELETION_IN_PROGRESS)) {
                exportGroup.clearInternalFlags(Flag.DELETION_IN_PROGRESS);   
            }
            dbClient.updateObject(exportGroup);

            if (Operation.isTerminalState(status)) {
                // Clean stale references from EG if the status is either ready or error.
                ExportUtils.cleanStaleReferences(exportGroup, dbClient);
            }

            if (operation.getStatus().equals(Operation.Status.ready.name())) {
                if (!checkForActiveMasks) {
                    dbClient.markForDeletion(exportGroup);
                } else { // Check if the associated ExportMasks as a condition of the markForDeletion();
                    if (!hasActiveMasks(dbClient, exportGroup)) {
                        _log.info("export_delete completer: export group is marked for deletion");
                        dbClient.markForDeletion(exportGroup);
                    } else {
                        _log.info("export_delete completer: export group still contains "
                                + "export masks, so not marking for deletion");
                    }
                }
            } else {
                _log.info("export_delete completer: error status - export group is not marked for deletion");
            }

            _log.info(String.format("Done ExportMaskDelete - Id: %s, OpId: %s, status: %s",
                    getId().toString(), getOpId(), status.name()));

            recordBlockExportOperation(dbClient, OperationTypeEnum.DELETE_EXPORT_GROUP, status, eventMessage(status, exportGroup),
                    exportGroup);
        } catch (Exception e) {
            _log.error(String.format("Failed updating status for ExportMaskDelete - Id: %s, OpId: %s",
                    getId().toString(), getOpId()), e);
        }
    }

    private String eventMessage(Operation.Status status, ExportGroup exportGroup) {
        return (status == Operation.Status.ready) ?
                String.format(EXPORT_DELETED_MSG, exportGroup.getLabel()) :
                String.format(EXPORT_DELETE_FAILED_MSG, exportGroup.getLabel());
    }

}
