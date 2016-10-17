/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class ExportAddInitiatorCompleter extends ExportTaskCompleter {
    private static final org.slf4j.Logger _log = LoggerFactory
            .getLogger(ExportAddInitiatorCompleter.class);
    private static final String EXPORT_INITIATOR_ADDED_MSG = "Initiator with hostname %s and port name %s added to ExportGroup %s";
    private static final String EXPORT_INITIATOR_ADD_FAILED_MSG = "Failed to add Initiator with hostname %s and port name %s to ExportGroup %s";
    private List<URI> _initiatorURIs;

    public ExportAddInitiatorCompleter(URI egUri, List<URI> initiatorURIs,
            String task) {
        super(ExportGroup.class, egUri, task);
        _initiatorURIs = new ArrayList<URI>();
        _initiatorURIs.addAll(initiatorURIs);
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

            _log.info("export_initiator_add: completed");
            _log.info(String.format("Done ExportMaskAddInitiator - Id: %s, OpId: %s, status: %s",
                    getId().toString(), getOpId(), status.name()));

            for (URI initiatorURI : _initiatorURIs) {
                Initiator initiator = dbClient.queryObject(Initiator.class,
                        initiatorURI);
                recordBlockExportOperation(dbClient, OperationTypeEnum.ADD_EXPORT_INITIATOR, status,
                        eventMessage(status, initiator, exportGroup), exportGroup, initiator);

                if (status.name().equals(Operation.Status.error.name())) {
                    exportGroup.removeInitiator(initiator);
                }
            }
            dbClient.updateObject(exportGroup);
        } catch (Exception e) {
            _log.error(String.format("Failed updating status for ExportMaskAddInitiator - Id: %s, OpId: %s",
                    getId().toString(), getOpId()), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }

    private String eventMessage(Operation.Status status, Initiator initiator,
            ExportGroup exportGroup) {
        return (status == Operation.Status.ready) ? String.format(
                EXPORT_INITIATOR_ADDED_MSG, initiator.getHostName(),
                initiator.getInitiatorPort(), exportGroup.getLabel()) : String
                .format(EXPORT_INITIATOR_ADD_FAILED_MSG,
                        initiator.getHostName(), initiator.getInitiatorPort(),
                        exportGroup.getLabel());
    }
}
