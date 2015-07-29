/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class RPCGDiscoverCompleter extends RPCGTaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(RPCGCreateCompleter.class);

    public RPCGDiscoverCompleter(URI uri, String task) {
        super(Volume.class, uri, task);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            _log.info(String.format("Done RPCGDiscover - Id: %s, OpId: %s, status: %s",
                    getId().toString(), getOpId(), status.name()));

            // Tell the individual objects we're done.
            for (URI id : getIds()) {
                switch (status) {
                    case error:
                        dbClient.error(Volume.class, id, getOpId(), coded);
                        break;
                    default:
                        dbClient.ready(Volume.class, id, getOpId());
                }
            }
        } catch (DatabaseException e) {
            _log.error(String.format("Failed updating status for CG Create - Id: %s, OpId: %s",
                    getId().toString(), getOpId()), e);
        }
    }
}
