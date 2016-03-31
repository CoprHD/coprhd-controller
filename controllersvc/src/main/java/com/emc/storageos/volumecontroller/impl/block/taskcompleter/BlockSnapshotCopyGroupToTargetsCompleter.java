/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.workflow.WorkflowStepCompleter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;

public class BlockSnapshotCopyGroupToTargetsCompleter extends
        BlockSnapshotTaskCompleter {
    private static final Logger _log =
            LoggerFactory.getLogger(BlockSnapshotCopyGroupToTargetsCompleter.class);

    public BlockSnapshotCopyGroupToTargetsCompleter(Class clazz, List<URI> ids,
            String opId) {
        super(clazz, ids, opId);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            BlockSnapshot snapshot = dbClient.queryObject(BlockSnapshot.class, getId());
            if (snapshot.getConsistencyGroup() != null) {
                // For snapshot based on a consistency group, set status and send
                // events for all related snaps
                List<BlockSnapshot> snaps = ControllerUtils.getSnapshotsPartOfReplicationGroup(snapshot, dbClient);
                for (BlockSnapshot snap : snaps) {
                    URI uri = snap.getId();
                    switch (status) {
                        case error:
                            dbClient.error(BlockSnapshot.class, uri, getOpId(), coded);
                            break;
                        default:
                            dbClient.ready(BlockSnapshot.class, uri, getOpId());
                    }
                }
            } else {
                switch (status) {
                    case error:
                        dbClient.error(BlockSnapshot.class, getId(), getOpId(), coded);
                        break;
                    default:
                        dbClient.ready(BlockSnapshot.class, getId(), getOpId());
                }
            }
        } catch (Exception e) {
            _log.error("Failed updating status. SnapshotCopyGroupToTargets {}, for task " + getOpId(), getId(), e);
        } finally {
            WorkflowStepCompleter.stepSucceded(getOpId());
        }
    }
}
