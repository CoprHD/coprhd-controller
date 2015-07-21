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
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

@SuppressWarnings("serial")
public class RPCGExportDeleteCompleter extends RPCGTaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(RPCGExportDeleteCompleter.class);

    public RPCGExportDeleteCompleter(URI uri, String task) {
        super(ExportGroup.class, uri, task);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            // Tell the workflow we're done.
            super.complete(dbClient, status, coded);
            _log.info("cg_export_delete completer: done");
            _log.info(String.format("Done RPCGExportDelete - Id: %s, OpId: %s, status: %s",
                    getId().toString(), getOpId(), status.name()));

            // Tell the individual objects we're done.
            for (URI id : getIds()) {
                switch (status) {
                case error:
                    dbClient.error(ExportGroup.class, id, getOpId(), coded);
                    break;
                default:
                    dbClient.ready(ExportGroup.class, id, getOpId());
                }
            }
        } catch (Exception e) {
            _log.error(String.format("Failed updating status for CG Export Delete - Id: %s, OpId: %s",
                    getId().toString(), getOpId()), e);

        }
    }
}
