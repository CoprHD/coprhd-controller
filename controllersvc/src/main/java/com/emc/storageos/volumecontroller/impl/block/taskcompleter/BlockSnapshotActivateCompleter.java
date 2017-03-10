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
public class BlockSnapshotActivateCompleter extends BlockSnapshotTaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(BlockSnapshotActivateCompleter.class);
    private static final String SNAPSHOT_ACTIVATED_MSG = "Snapshot %s activated for volume %s";
    private static final String SNAPSHOT_ACTIVATE_FAILED_MSG = "Failed to activate snapshot %s for volume %s";
    private final List<URI> _snapshotURIs;

    public List<URI> getSnapshotURIs() {
        return _snapshotURIs;
    }

    public BlockSnapshotActivateCompleter(List<URI> snaps, String task) {
        super(BlockSnapshot.class, snaps.get(0), task);
        _snapshotURIs = new ArrayList<URI>();
        for (URI thisOne : snaps) {
            _snapshotURIs.add(thisOne);
        }
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            for (URI snapshotUri : _snapshotURIs) {
                BlockSnapshot snapshot = dbClient.queryObject(BlockSnapshot.class, snapshotUri);
                Volume volume = dbClient.queryObject(Volume.class, snapshot.getParent());

                switch (status) {
                    case error:
                        dbClient.error(BlockSnapshot.class, snapshotUri, getOpId(), coded);
                        dbClient.error(Volume.class, volume.getId(), getOpId(), coded);
                        break;
                    case ready:
                        // Only execute the following logic if the snapshot is tied to a bookmark
                        if (NullColumnValueGetter.isNotNullValue(snapshot.getEmName())) {
                            // Update the target volume's access state
                            RPHelper.updateRPSnapshotPostImageAccessChange(snapshot, volume, Volume.VolumeAccessState.READWRITE,
                                    true, dbClient);
                        }
                    default:
                        dbClient.ready(BlockSnapshot.class, snapshotUri, getOpId());
                        dbClient.ready(Volume.class, volume.getId(), getOpId());
                        break;
                }

                recordBlockSnapshotOperation(dbClient, OperationTypeEnum.ACTIVATE_VOLUME_SNAPSHOT, status,
                        eventMessage(status, volume, snapshot), snapshot, volume);
            }
            _log.info("Done SnapshotActivate {}, with Status: {}", getOpId(), status.name());
        } catch (Exception e) {
            _log.error("Failed updating status. SnapshotActivate {}, for task " + getOpId(), getId(), e);
        }
    }

    private String eventMessage(Operation.Status status, Volume volume, BlockSnapshot snapshot) {
        return (status == Operation.Status.ready) ?
                String.format(SNAPSHOT_ACTIVATED_MSG, snapshot.getLabel(), volume.getLabel()) :
                String.format(SNAPSHOT_ACTIVATE_FAILED_MSG, snapshot.getLabel(), volume.getLabel());
    }
}
