/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) $today_year. EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.services.OperationTypeEnum;

public class BlockSnapshotActivateCompleter extends BlockSnapshotTaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(BlockSnapshotActivateCompleter.class);
    private static final String SNAPSHOT_ACTIVATED_MSG = "Snapshot %s activated for volume %s";
    private static final String SNAPSHOT_ACTIVATE_FAILED_MSG = "Failed to activate snapshot %s for volume %s";
    private List<URI> _snapshotURIs;

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

                recordBlockSnapshotOperation(dbClient, OperationTypeEnum.ACTIVATE_VOLUME_SNAPSHOT, status, eventMessage(status, volume, snapshot), snapshot, volume);
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
