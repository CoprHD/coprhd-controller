/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
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
import com.emc.storageos.volumecontroller.impl.ControllerUtils;

@SuppressWarnings("serial")
public class BlockSnapshotDeleteCompleter extends BlockSnapshotTaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(BlockSnapshotDeleteCompleter.class);
    public static final String SNAPSHOT_DELETED_MSG = "Deleted snapshot %s on volume %s";
    public static final String SNAPSHOT_DELETE_FAILED_MSG = "Failed to delete snapshot %s on volume %s";

    // Make this private, so that we force the use of the createCompleter() method
    private BlockSnapshotDeleteCompleter(BlockSnapshot snapshot, String task) {
        super(BlockSnapshot.class, snapshot.getId(), task);
    }

    /**
     * This method is for creating the BlockSnapshotDeleteCompleter. This a bit unique from
     * the other completers because of the way that we need to complete the task. For the
     * case of CG snapshots, there will be multiple BlockSnapshots associated, even though
     * the request to delete the snapshot was against only one of the CG snaps. Here we will
     * make sure that the task completer has references to all the CG snaps, so that they
     * can be referred to in the completer. Main reason is that if a BlockSnapshot is marked
     * inactive, it can no longer be retrieved using AltIndex queries.
     * 
     * @param dbClient [in] - DbClient for querying ViPR DB
     * @param snapshot [in] - BlockSnapshot object
     * @param task [in] - Task UUID for the snapshot delete operation
     * @return a new BlockSnapshotDeleteCompleter object
     */
    public static BlockSnapshotDeleteCompleter createCompleter(DbClient dbClient, BlockSnapshot snapshot, String task) {
        BlockSnapshotDeleteCompleter completer = new BlockSnapshotDeleteCompleter(snapshot, task);
        if (snapshot.getConsistencyGroup() != null) {
            // For snapshot based on a consistency group, set status and send
            // events for all related snaps
            List<URI> snapIds = new ArrayList<URI>();
            List<BlockSnapshot> snaps = ControllerUtils.getSnapshotsPartOfReplicationGroup(snapshot, dbClient);
            for (BlockSnapshot snap : snaps) {
                snapIds.add(snap.getId());
            }
            completer.addIds(snapIds);
        }
        return completer;
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            List<BlockSnapshot> blockSnapshots = dbClient.queryObject(BlockSnapshot.class, getIds());

            for (BlockSnapshot snapshot : blockSnapshots) {
                Volume parent = dbClient.queryObject(Volume.class, snapshot.getParent().getURI());

                switch (status) {
                    case error:
                        setErrorOnDataObject(dbClient, BlockSnapshot.class, snapshot.getId(), coded);
                        setErrorOnDataObject(dbClient, Volume.class, parent.getId(), coded);
                        break;
                    case ready:
                        setReadyOnDataObject(dbClient, BlockSnapshot.class, snapshot);
                        setReadyOnDataObject(dbClient, Volume.class, parent.getId());
                }

                recordBlockSnapshotOperation(dbClient, OperationTypeEnum.DELETE_VOLUME_SNAPSHOT, status,
                        eventMessage(status, parent, snapshot), snapshot);
                _log.info("Done SnapshotDelete {}, with Status: {}", getOpId(), status.name());
            }

            super.complete(dbClient, status, coded);
        } catch (Exception e) {
            _log.error("Failed updating status. SnapshotDelete {}, for task " + getOpId(), getId(), e);
        }
    }

    private String eventMessage(Operation.Status status, Volume volume, BlockSnapshot snapshot) {
        return (status == Operation.Status.ready) ?
                String.format(SNAPSHOT_DELETED_MSG, snapshot.getLabel(), volume.getLabel()) :
                String.format(SNAPSHOT_DELETE_FAILED_MSG, snapshot.getLabel(), volume.getLabel());
    }
}
