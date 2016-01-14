/*
 * Copyright (c) 2012 EMC Corporation
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
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;

/**
 * Task completer invoked when SMI-S request to unlink a target from
 * an array snapshot completes.
 */
@SuppressWarnings("serial")
public class BlockSnapshotSessionUnlinkTargetCompleter extends BlockSnapshotSessionCompleter {

    // The URI of the BlockSnapshotSession representing the array snapshot.
    private final URI _snapshotURI;

    // Whether or not the target is deleted when unlinked.
    private final Boolean _deleteTarget;

    // A logger.
    private static final Logger s_logger = LoggerFactory.getLogger(BlockSnapshotSessionUnlinkTargetCompleter.class);

    /**
     * Constructor
     * 
     * @param snapSessionURI The id of the BlockSnapshotSession instance in the database.
     * @param snapshotURI The id of the BlockSnapshot instance representing the target.
     * @param deleteTarget True if the target volume should be deleted.
     * @param stepId The id of the WF step in which the target is being unlinked.
     */
    public BlockSnapshotSessionUnlinkTargetCompleter(URI snapSessionURI, URI snapshotURI, Boolean deleteTarget, String stepId) {
        super(snapSessionURI, stepId);
        _snapshotURI = snapshotURI;
        _deleteTarget = deleteTarget;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            switch (status) {
                case error:
                    break;
                case ready:
                    // Remove the linked targets from the linked targets for the session.
                    List<BlockSnapshot> snapshots = new ArrayList<>();
                    BlockSnapshot snapshotObj = dbClient.queryObject(BlockSnapshot.class, _snapshotURI);
                    BlockSnapshotSession snapSession = dbClient.queryObject(BlockSnapshotSession.class, getId());
                    StringSet linkedTargets = snapSession.getLinkedTargets();

                    if (snapshotObj.hasConsistencyGroup()) {
                        snapshots.addAll(ControllerUtils.getSnapshotsPartOfReplicationGroup(
                                snapshotObj.getReplicationGroupInstance(), dbClient));
                    } else {
                        snapshots.add(snapshotObj);
                    }

                    for (BlockSnapshot snapshot : snapshots) {
                        snapshot.setInactive(true);
                        if ((linkedTargets != null) && (linkedTargets.contains(snapshot.getId().toString()))) {
                            linkedTargets.remove(snapshot.getId().toString());
                        }
                    }

                    // Note that even if the target is not deleted, mark the associated
                    // BlockSnapshot inactive. Since the target is no longer associated
                    // with an array snapshot, it is really no longer a BlockSnapshot
                    // instance in ViPR. In the unlink job we have created a ViPR Volume
                    // to represent the former snapshot target volume. So here we mark the
                    // BlockSnapshot inactive so it is garbage collected.
                    dbClient.updateObject(snapshots);
                    dbClient.updateObject(snapSession);
                    break;
                default:
                    String errMsg = String.format("Unexpected status %s for completer for step %s", status.name(), getOpId());
                    s_logger.info(errMsg);
                    throw DeviceControllerException.exceptions.unexpectedCondition(errMsg);
            }
            s_logger.info("Done unlink targets from snapshot session step {} with status: {}", getOpId(), status.name());
        } catch (Exception e) {
            s_logger.error("Failed updating status for unlink targets from snapshot session step {}", getOpId(), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }

    /**
     * Gets if the target is to be deleted.
     * 
     * @return true if the target is to be deleted, false otherwise.
     */
    public boolean getDeleteTarget() {
        return _deleteTarget;
    }

    /**
     * Gets the URI of the snapshot representing the linked target.
     * 
     * @return The snapshot URI.
     */
    public URI getSnapshotURI() {
        return _snapshotURI;
    }
}
