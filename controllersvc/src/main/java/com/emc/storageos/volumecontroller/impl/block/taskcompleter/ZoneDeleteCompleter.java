/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.google.common.base.Joiner;

/**
 * A completer that runs after zones are deleted for a set of export masks that belong to the same
 * export group.
 */
@SuppressWarnings("serial")
public class ZoneDeleteCompleter extends AbstractZoneCompleter {
    private static final Logger _log = LoggerFactory.getLogger(ZoneDeleteCompleter.class);

    public ZoneDeleteCompleter(List<URI> emUris, String task) {
        super(ExportMask.class, emUris, task);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            _log.info(String.format("Done ZoneDelete - Ids: %s, OpId: %s, status: %s",
                    getIds() == null ? "None" : Joiner.on(",").join(getIds()),
                    getOpId(),
                    status.name()));
        } catch (Exception e) {
            _log.error(String.format("Failed updating status for ZoneDelete - Ids: %s, OpId: %s",
                    getIds() == null ? "None" : Joiner.on(",").join(getIds()),
                    getOpId()), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }

}
