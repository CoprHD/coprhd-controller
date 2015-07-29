/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.hds.prov.job;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportTaskCompleter;

@SuppressWarnings("serial")
public class HDSExportMaskDeleteCompleter extends ExportTaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(HDSExportMaskDeleteCompleter.class);

    public HDSExportMaskDeleteCompleter(URI egUri, URI emUri, String task) {
        super(ExportGroup.class, egUri, emUri, task);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {

            _log.info(String.format("Done ExportMaskDelete - Id: %s, OpId: %s, status: %s",
                    getId().toString(), getOpId(), status.name()));
        } catch (Exception e) {
            _log.error(String.format("Failed updating status for ExportMaskDelete - Id: %s, OpId: %s",
                    getId().toString(), getOpId()), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }

}
