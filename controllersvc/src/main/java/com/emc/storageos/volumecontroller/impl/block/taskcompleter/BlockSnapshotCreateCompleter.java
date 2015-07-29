/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) 2012. EMC Corporation
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
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class BlockSnapshotCreateCompleter extends BlockSnapshotTaskCompleter {
    public static final String SNAPSHOT_CREATED_MSG = "Snapshot %s created for volume %s";
    public static final String SNAPSHOT_CREATE_FAILED_MSG = "Failed to create snapshot %s for volume %s";
    private static final Logger _log = LoggerFactory.getLogger(BlockSnapshotCreateCompleter.class);
    private List<URI> _snapshotURIs;

    public BlockSnapshotCreateCompleter(List<URI> snaps, String task) {
        super(BlockSnapshot.class, snaps.get(0), task);
        _snapshotURIs = new ArrayList<URI>();
        for (URI thisOne : snaps) {
            _snapshotURIs.add(thisOne);
        }
    }

    public List<URI> getSnapshotURIs() {
        return _snapshotURIs;
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            for (URI thisOne : _snapshotURIs) {
                BlockSnapshot snapshot = dbClient.queryObject(BlockSnapshot.class, thisOne);
                Volume volume = dbClient.queryObject(Volume.class, snapshot.getParent());
                // For VPLEX volume snaps, the snap parent is not the VPLEX volume,
                // but instead the backend volume that is natively snapped. We need
                // the VPLEX volume to update the task that was created on the VPLEX
                // volume.
                URIQueryResultList queryResults = new URIQueryResultList();
                dbClient.queryByConstraint(AlternateIdConstraint.Factory
                        .getVolumeByAssociatedVolumesConstraint(volume.getId().toString()),
                        queryResults);
                if (queryResults.iterator().hasNext()) {
                    volume = dbClient.queryObject(Volume.class, queryResults.iterator().next());
                }
                switch (status) {
                    case error:
                        setErrorOnDataObject(dbClient, BlockSnapshot.class, thisOne, coded);
                        setErrorOnDataObject(dbClient, Volume.class, volume, coded);
                        snapshot.setInactive(true);
                        dbClient.persistObject(snapshot);
                        break;
                    default:
                        setReadyOnDataObject(dbClient, BlockSnapshot.class, thisOne);
                        setReadyOnDataObject(dbClient, Volume.class, volume);
                }

                recordBlockSnapshotOperation(dbClient, OperationTypeEnum.CREATE_VOLUME_SNAPSHOT, status,
                        eventMessage(status, volume, snapshot), snapshot, volume);
            }
            if (isNotifyWorkflow()) {
                // If there is a workflow, update the step to complete.
                updateWorkflowStatus(status, coded);
            }
            _log.info("Done SnapshotCreate {}, with Status: {}", getOpId(), status.name());
        } catch (Exception e) {
            _log.error("Failed updating status. SnapshotCreate {}, for task " + getOpId(), getId(), e);
        }
    }

    private String eventMessage(Operation.Status status, Volume volume, BlockSnapshot snapshot) {
        return (status == Operation.Status.ready) ?
                String.format(SNAPSHOT_CREATED_MSG, snapshot.getLabel(), volume.getLabel()) :
                String.format(SNAPSHOT_CREATE_FAILED_MSG, snapshot.getLabel(), volume.getLabel());
    }
}
