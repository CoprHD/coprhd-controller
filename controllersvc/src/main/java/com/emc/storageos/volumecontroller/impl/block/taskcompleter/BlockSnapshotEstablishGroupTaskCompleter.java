/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

/**
 * The Class BlockSnapshotEstablishGroupTaskCompleter.
 *  - establish group relation between volume group and snapshot group.
 */
public class BlockSnapshotEstablishGroupTaskCompleter extends BlockSnapshotTaskCompleter {
	private static final long serialVersionUID = 1L;
	private static final Logger logger = LoggerFactory.getLogger(BlockSnapshotEstablishGroupTaskCompleter.class);

    public BlockSnapshotEstablishGroupTaskCompleter(URI id, String opId) {
        super(BlockSnapshot.class, id, opId);
    }

    public BlockSnapshotEstablishGroupTaskCompleter(List<URI> ids, String opId) {
        super(BlockSnapshot.class, ids, opId);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            super.complete(dbClient, status, coded);
            switch (status) {
                case error:
                    dbClient.error(BlockSnapshot.class, getId(), getOpId(), coded);
                    break;
                default:
                    dbClient.ready(BlockSnapshot.class, getId(), getOpId());
            }
            logger.info("Done Establish Volume-Snapshot group relation {}, with Status: {}", getOpId(), status.name());
        } catch (Exception e) {
            logger.error("Failed updating status. Establish Volume-Snapshot group relation {}, for task " + getOpId(), getId(), e);
        }
    }
}
