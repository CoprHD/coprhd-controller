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
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPHelper;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

@SuppressWarnings("serial")
public class BlockSnapshotDeactivateCompleter extends BlockSnapshotTaskCompleter {
    private static final Logger _log = LoggerFactory
            .getLogger(BlockSnapshotDeactivateCompleter.class);
    private static final String SNAPSHOT_DEACTIVATED_MSG = "Snapshot %s deactivated for volume %s";
    private static final String SNAPSHOT_DEACTIVATE_FAILED_MSG = "Failed to deactivate snapshot %s for volume %s";
    private final List<URI> snapshotURIs;
    private final boolean setSnapshotsInactive;
    private final boolean setSnapshotSyncActive;

    public BlockSnapshotDeactivateCompleter(List<URI> snaps, boolean setSnapshotsInactive, boolean setSnapshotSyncActive, String task) {
        super(BlockSnapshot.class, snaps.get(0), task);
        snapshotURIs = new ArrayList<URI>();
        this.setSnapshotsInactive = setSnapshotsInactive;
        this.setSnapshotSyncActive = setSnapshotSyncActive;

        for (URI snapshotUri : snaps) {
            snapshotURIs.add(snapshotUri);
        }
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            List<BlockSnapshot> snapshots = dbClient.queryObject(BlockSnapshot.class, snapshotURIs);

            for (BlockSnapshot snapshot : snapshots) {
                Volume volume = dbClient.queryObject(Volume.class, snapshot.getParent());

                switch (status) {
                    case error:
                        setErrorOnDataObject(dbClient, BlockSnapshot.class, snapshot, coded);
                        setErrorOnDataObject(dbClient, Volume.class, volume.getId(), coded);
                        break;
                    case ready:
                        // Only execute the following logic if the snapshot is tied to a bookmark
                        if (NullColumnValueGetter.isNotNullValue(snapshot.getEmName())) {
                            // Update the target volume's access state
                            RPHelper.updateAccessState(snapshot, volume, Volume.VolumeAccessState.NOT_READY, dbClient);
                        }
                    default:
                        setReadyOnDataObject(dbClient, BlockSnapshot.class, snapshot);
                        setReadyOnDataObject(dbClient, Volume.class, volume.getId());
                        break;
                }

                recordBlockSnapshotOperation(dbClient, OperationTypeEnum.DEACTIVATE_VOLUME_SNAPSHOT, status,
                        eventMessage(status, volume, snapshot), snapshot);
            }

            super.complete(dbClient, status, coded);
            _log.info("Done deactivate {}, with Status: {}", getOpId(), status.name());
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
