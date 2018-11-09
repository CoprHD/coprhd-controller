/*
 * Copyright (c) 2018 Dell-EMC
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.services.OperationTypeEnum;

public class BlockSnapshotExpandCompleter extends BlockSnapshotTaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(BlockSnapshotExpandCompleter.class);

    private Long _size;

    public BlockSnapshotExpandCompleter(URI snapUri, Long size, String task) {
        super(BlockSnapshot.class, snapUri, task);
        _size = size;
    }

    public Long getSize() {
        return _size;
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            super.complete(dbClient, status, coded);
            BlockSnapshot blockSnapshot = dbClient.queryObject(BlockSnapshot.class, getId());
            if (Operation.Status.ready == status) {
                dbClient.ready(BlockSnapshot.class, getId(), getOpId());
                _log.info("Done BlockSnapshotExpand - Snapshot Id: {}, NativeId: {}, OpId: {}, status: {}, New size: {}",
                        getId().toString(), blockSnapshot.getNativeId(), getOpId(), status.name(), _size);
            } else if (Operation.Status.error == status) {
                dbClient.error(BlockSnapshot.class, getId(), getOpId(), coded);
                _log.info("BlockSnapshotExpand failed - Snapshot Id: {}, NativeId: {}, OpId: {}, status: {}, New size: {}",
                        getId().toString(), blockSnapshot.getNativeId(), getOpId(), status.name(), _size);
            }
            recordBlockSnapshotOperation(dbClient, OperationTypeEnum.EXPAND_VOLUME_SNAPSHOT, status, getId().toString(), blockSnapshot);
        } catch (Exception e) {
            _log.error("Failed updating status for BlockSnapshotExpand - snapshot Id: {}, OpId: {}",
                    getId().toString(), getOpId(), e);
        }
    }
}
