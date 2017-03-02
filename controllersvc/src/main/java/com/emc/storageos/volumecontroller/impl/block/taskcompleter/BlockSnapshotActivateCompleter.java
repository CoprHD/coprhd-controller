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
import com.emc.storageos.db.client.model.ProtectionSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

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
                        // Mark the snapshots
                        snapshot.setInactive(false);
                        snapshot.setIsSyncActive(true);
                        _log.info(String.format(
                                "Updating inactive field to false and isSyncActive field to true for BlockSnapshot %s.",
                                snapshot.getId()));
                        dbClient.updateObject(snapshot);

                        // For RP+VPLEX volumes, we need to fetch the VPLEX volume. The snapshot objects references the
                        // block/back-end volume as its parent. Fetch the VPLEX volume that is created with this volume
                        // as the back-end volume.
                        if (Volume.checkForVplexBackEndVolume(dbClient, volume)) {
                            volume = Volume.fetchVplexVolume(dbClient, volume);
                        }

                        Volume targetVolume = null;

                        // If the personality is SOURCE, then the enable image access request is part of export operation.
                        if (volume.checkPersonality(Volume.PersonalityTypes.SOURCE.toString())) {
                            // Now determine the target volume that corresponds to the site of the snapshot
                            ProtectionSet protectionSet = dbClient.queryObject(ProtectionSet.class, volume.getProtectionSet());
                            targetVolume = ProtectionSet.getTargetVolumeFromSourceAndInternalSiteName(dbClient, protectionSet,
                                    volume,
                                    snapshot.getEmInternalSiteName());
                        } else if (volume.checkPersonality(Volume.PersonalityTypes.TARGET.toString())) {
                            targetVolume = volume;
                        }

                        if (targetVolume != null) {
                            _log.info(String.format("Updating the access state to %s for target volume %s.",
                                    Volume.VolumeAccessState.READWRITE.name(), targetVolume.getId()));
                            targetVolume.setAccessState(Volume.VolumeAccessState.READWRITE.name());
                            dbClient.updateObject(targetVolume);
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
