/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class ExportRemoveInitiatorCompleter extends ExportTaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(ExportRemoveInitiatorCompleter.class);
    private static final String EXPORT_INITIATOR_REMOVED_MSG = "Initiator %s removed from ExportGroup %s";
    private static final String EXPORT_INITIATOR_REMOVE_FAILED_MSG = "Failed to remove Initiator %s from ExportGroup %s";

    private List<URI> _initiatorURIs;
    private List<URI> _targetPorts;

    public ExportRemoveInitiatorCompleter(URI egUri, URI sdUri, URI initiatorURI, List<URI> targetPorts, String task) {
        super(ExportGroup.class, egUri, task);
        _initiatorURIs = new ArrayList<URI>();
        _initiatorURIs.add(initiatorURI);
        _targetPorts = targetPorts;
    }

    public ExportRemoveInitiatorCompleter(URI egUri, List<URI> initiatorURIs,
            String task) {
        super(ExportGroup.class, egUri, task);
        _initiatorURIs = new ArrayList<URI>();
        _initiatorURIs.addAll(initiatorURIs);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            ExportGroup exportGroup = dbClient.queryObject(ExportGroup.class, getId());
            for (URI initiatorURI : _initiatorURIs) {
                Initiator initiator = dbClient.queryObject(Initiator.class, initiatorURI);
                if (status == Operation.Status.ready) {
                    exportGroup.removeInitiator(initiator);
                }
                _log.info("export_initiator_remove: completed");
                _log.info(String.format("Done ExportMaskRemoveInitiator - Id: %s, OpId: %s, status: %s",
                        getId().toString(), getOpId(), status.name()));
                recordBlockExportOperation(dbClient, OperationTypeEnum.DELETE_EXPORT_INITIATOR, status,
                        eventMessage(status, initiator, exportGroup), exportGroup, initiator);
            }
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
            _log.error(String.format("Failed updating status for ExportMaskRemoveInitiator - Id: %s, OpId: %s",
                    getId().toString(), getOpId()), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }

    private String eventMessage(Operation.Status status, Initiator initiator, ExportGroup exportGroup) {
        return (status == Operation.Status.ready) ?
                String.format(EXPORT_INITIATOR_REMOVED_MSG, initiator.getHostName(), exportGroup.getLabel()) :
                String.format(EXPORT_INITIATOR_REMOVE_FAILED_MSG, initiator.getHostName(), exportGroup.getLabel());
    }
}
