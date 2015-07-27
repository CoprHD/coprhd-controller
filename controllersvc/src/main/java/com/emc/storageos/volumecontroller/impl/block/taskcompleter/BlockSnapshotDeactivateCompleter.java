/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.services.OperationTypeEnum;

@SuppressWarnings("serial")
public class BlockSnapshotDeactivateCompleter extends BlockSnapshotTaskCompleter {
    private static final Logger _log = LoggerFactory
            .getLogger(BlockSnapshotDeactivateCompleter.class);
    private static final String SNAPSHOT_DEACTIVATED_MSG = "Snapshot %s deactivated for volume %s";
    private static final String SNAPSHOT_DEACTIVATE_FAILED_MSG = "Failed to deactivate snapshot %s for volume %s";
    private final List<URI> _snapshotURIs;

    public List<URI> getSnapshotURIs() {
        return _snapshotURIs;
    }

    public BlockSnapshotDeactivateCompleter(List<URI> snaps, String task) {
        super(BlockSnapshot.class, snaps.get(0), task);
        _snapshotURIs = new ArrayList<URI>();
        for (URI thisOne : snaps) {
            _snapshotURIs.add(thisOne);
        }
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            for (URI thisOne : _snapshotURIs) {
                switch (status) {
                case error:
                    dbClient.error(BlockSnapshot.class, thisOne, getOpId(), coded);
                    break;
                default:
                    dbClient.ready(BlockSnapshot.class, thisOne, getOpId());
                }
                BlockSnapshot snapshot = dbClient.queryObject(BlockSnapshot.class, thisOne);
                Volume volume = dbClient.queryObject(Volume.class, snapshot.getParent());
                switch (status) {
                case error:
                    dbClient.error(Volume.class, volume.getId(), getOpId(), coded);
                    break;
                default:
                    dbClient.ready(Volume.class, volume.getId(), getOpId());
                }

                recordBlockSnapshotOperation(dbClient, OperationTypeEnum.DEACTIVATE_VOLUME_SNAPSHOT, status, eventMessage(status, volume, snapshot), snapshot);
            }
            _log.info("Done dectivate {}, with Status: {}", getOpId(), status.name());
        } catch (Exception e) {
            _log.error("Failed updating status. SnapshotDeactivate {}, for task " + getOpId(), getId(), e);
        }
    }

    private String eventMessage(Operation.Status status, Volume volume, BlockSnapshot snapshot) {
        return (status == Operation.Status.ready) ? String.format(SNAPSHOT_DEACTIVATED_MSG,
                snapshot.getLabel(), volume.getLabel()) : String.format(
                SNAPSHOT_DEACTIVATE_FAILED_MSG, snapshot.getLabel(), volume.getLabel());
    }
}
