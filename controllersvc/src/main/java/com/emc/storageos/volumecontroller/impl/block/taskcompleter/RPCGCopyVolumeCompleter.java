/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

@SuppressWarnings("serial")
public class RPCGCopyVolumeCompleter extends RPCGTaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(RPCGCopyVolumeCompleter.class);

    public RPCGCopyVolumeCompleter(Class<? extends DataObject> clazz, List<URI> uris, String task) {
        super(clazz, uris, task);
    }

    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            // Tell the workflow we're done.
            super.complete(dbClient, status, coded);
            _log.info(String.format("Done RPCGCopyVolume - Id: %s, OpId: %s, status: %s",
                    (getId() == null ? "unknown copy" : getId().toString()), getOpId(), status.name()));

            // Tell the individual objects we're done.
            for (URI id : getIds()) {
                switch (status) {
                    case error:
                        if (URIUtil.isType(id, Volume.class)) {
                            dbClient.error(Volume.class, id, getOpId(), coded);
                        } else if (URIUtil.isType(id, BlockSnapshot.class)) {
                            dbClient.error(BlockSnapshot.class, id, getOpId(), coded);
                        } else if (URIUtil.isType(id, BlockSnapshotSession.class)) {
                            dbClient.error(BlockSnapshotSession.class, id, getOpId(), coded);
                        }
                        break;
                    default:
                        if (URIUtil.isType(id, Volume.class)) {
                            dbClient.ready(Volume.class, id, getOpId());
                        } else if (URIUtil.isType(id, BlockSnapshot.class)) {
                            dbClient.ready(BlockSnapshot.class, id, getOpId());
                        } else if (URIUtil.isType(id, BlockSnapshotSession.class)) {
                            dbClient.ready(BlockSnapshotSession.class, id, getOpId());
                        }
                }
            }
        } catch (DatabaseException e) {
            _log.error(String.format("Failed updating status for RP Volume Copy - Id: %s, OpId: %s",
                    (getId() == null ? "unknown copy" : getId().toString()), getOpId()), e);

        }
    }
}
