/*
 * Copyright (c) 2013 EMC Corporation
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
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.util.ExportUtils;

public class ExportMaskRemoveInitiatorCompleter extends ExportTaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(ExportMaskRemoveInitiatorCompleter.class);

    private List<URI> _initiatorURIs;

    public ExportMaskRemoveInitiatorCompleter(URI egUri, URI emUri, List<URI> initiatorURIs,
            String task) {
        super(ExportGroup.class, egUri, emUri, task);
        _initiatorURIs = new ArrayList<URI>();
        _initiatorURIs.addAll(initiatorURIs);
    }

    @Override
    public void ready(DbClient dbClient) throws DeviceControllerException {
        try {
            ExportGroup exportGroup = dbClient.queryObject(ExportGroup.class, getId());
            ExportMask exportMask = (getMask() != null) ?
                    dbClient.queryObject(ExportMask.class, getMask()) : null;
            if (exportMask != null) {
                List<Initiator> initiators =
                        dbClient.queryObject(Initiator.class, _initiatorURIs);
                exportMask.removeInitiators(initiators);
                exportMask.removeFromUserCreatedInitiators(initiators);
                if (exportMask.getInitiators() == null ||
                        exportMask.getInitiators().isEmpty()) {
                    exportGroup.removeExportMask(exportMask.getId());
                    dbClient.markForDeletion(exportMask);
                    dbClient.updateObject(exportGroup);
                } else {
                    List<URI> targetPorts = ExportUtils.getRemoveInitiatorStoragePorts(exportMask, initiators, dbClient);
                    if (targetPorts != null && !targetPorts.isEmpty()) {
                        for (URI targetPort : targetPorts) {
                            exportMask.removeTarget(targetPort);
                        }
                    }
                    dbClient.updateObject(exportMask);
                }
                _log.info(String.format(
                        "Done ExportMaskRemoveInitiator - Id: %s, OpId: %s, status: %s",
                        getId().toString(), getOpId(), Operation.Status.ready.name()));
            }
        } catch (Exception e) {
            _log.error(String.format(
                    "Failed updating status for ExportMaskRemoveInitiator - Id: %s, OpId: %s",
                    getId().toString(), getOpId()), e);
        } finally {
            super.ready(dbClient);
        }
    }

    public boolean removeInitiator(URI initiator) {
        return _initiatorURIs.remove(initiator);
    }
}
