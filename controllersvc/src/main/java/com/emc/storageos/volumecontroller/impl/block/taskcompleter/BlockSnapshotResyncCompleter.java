/*
 * Copyright (c) 20125EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;

/**
 * The task completer class for block snapshot resynchronization
 *
 */
public class BlockSnapshotResyncCompleter extends BlockSnapshotTaskCompleter {

    private static final long serialVersionUID = 1L;
    private static final Logger _log = LoggerFactory.getLogger(BlockSnapshotResyncCompleter.class);
    public static final String SNAPSHOT_RESYNCED_MSG = "Resynchronized snapshot %s";
    public static final String SNAPSHOT_RESYNC_FAILED_MSG = "Failed to resynchronize snapshot %s";

    private Boolean _updateAndRecordOp = true;

    public BlockSnapshotResyncCompleter(BlockSnapshot snapshot, String task) {
        super(BlockSnapshot.class, snapshot.getId(), task);
    }

    public BlockSnapshotResyncCompleter(BlockSnapshot snapshot, String task, Boolean updateAndRecordOp) {
        super(BlockSnapshot.class, snapshot.getId(), task);
        _updateAndRecordOp = updateAndRecordOp;
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            BlockSnapshot snapshot = dbClient.queryObject(BlockSnapshot.class, getId());
            Volume volume = dbClient.queryObject(Volume.class, snapshot.getParent());
            if (_updateAndRecordOp) {
                if (snapshot.getConsistencyGroup() != null) {
                    // For snapshot based on a consistency group, set status and send
                    // events for all related snaps
                    List<BlockSnapshot> snaps = ControllerUtils.getSnapshotsPartOfReplicationGroup(snapshot, dbClient);
                    for (BlockSnapshot snap : snaps) {
                        URI uri = snap.getId();
                        BlockSnapshot it = dbClient.queryObject(BlockSnapshot.class, uri);
                        switch (status) {
                            case error:
                                dbClient.error(BlockSnapshot.class, uri, getOpId(), coded);
                                break;
                            default:
                                dbClient.ready(BlockSnapshot.class, uri, getOpId());
                        }

                        recordBlockSnapshotOperation(dbClient, OperationTypeEnum.RESYNCHRONIZE_VOLUME_SNAPSHOT, status,
                                eventMessage(status, volume, it), it, volume);
                        _log.info(String.format("Completed CG snapshot resynchronization of snap %s (%s), with status %s",
                                it.getLabel(), uri.toString(), status.name()));
                    }
                } else {
                    switch (status) {
                        case error:
                            dbClient.error(BlockSnapshot.class, getId(), getOpId(), coded);
                            break;
                        default:
                            dbClient.ready(BlockSnapshot.class, getId(), getOpId());
                    }

                    recordBlockSnapshotOperation(dbClient, OperationTypeEnum.RESYNCHRONIZE_VOLUME_SNAPSHOT, status,
                            eventMessage(status, volume, snapshot), snapshot, volume);
                }
            }
            _log.info("Done Resync Snapshot {}, with Status: {}", getOpId(), status.name());
            super.complete(dbClient, status, coded);
        } catch (Exception e) {
            _log.error("Failed updating status. SnapshotResync {}, for task " + getOpId(), getId(), e);
        }
    }

    private String eventMessage(Operation.Status status, Volume volume, BlockSnapshot snapshot) {
        return (status == Operation.Status.ready) ?
                String.format(SNAPSHOT_RESYNCED_MSG, volume.getLabel(), snapshot.getLabel()) :
                String.format(SNAPSHOT_RESYNC_FAILED_MSG, volume.getLabel(), snapshot.getLabel());
    }

}
