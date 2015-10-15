/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.model.SynchronizationState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.google.common.base.Joiner;

public class BlockMirrorFractureCompleter extends BlockMirrorTaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(BlockMirrorCreateCompleter.class);
    public static final String MIRROR_FRACTURED_MSG = "Mirror %s fractured for volume %s";
    public static final String MIRROR_FRACTURE_FAILED_MSG = "Failed to fracture mirror %s for volume %s";

    public BlockMirrorFractureCompleter(URI mirror, String opId) {
        super(BlockMirror.class, mirror, opId);
    }

    public BlockMirrorFractureCompleter(List<URI> mirrorList, String opId) {
        super(BlockMirror.class, mirrorList, opId);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            super.complete(dbClient, status, coded);
            List<BlockMirror> mirrorList = dbClient.queryObject(BlockMirror.class, getIds());
            for (BlockMirror mirror : mirrorList) {
                Volume volume = dbClient.queryObject(Volume.class, mirror.getSource());
                switch (status) {
                    case error:
                        dbClient.error(Volume.class, volume.getId(), getOpId(), coded);
                        break;
                    default:
                        mirror.setSyncState(SynchronizationState.FRACTURED.toString());
                        dbClient.persistObject(mirror);
                        dbClient.ready(Volume.class, volume.getId(), getOpId());
                }
                recordBlockMirrorOperation(dbClient, OperationTypeEnum.FRACTURE_VOLUME_MIRROR,
                        Status.ready, eventMessage(status, volume, mirror), mirror, volume);
            }
        } catch (Exception e) {
            _log.error("Failed updating status. BlockMirrorCreate {}, for task " + getOpId(), Joiner.on("\t").join(getIds()), e);
        }
    }

    private String eventMessage(Operation.Status status, Volume volume, BlockMirror mirror) {
        return (Operation.Status.ready == status) ?
                String.format(MIRROR_FRACTURED_MSG, mirror.getLabel(), volume.getLabel()) :
                String.format(MIRROR_FRACTURE_FAILED_MSG, mirror.getLabel(), volume.getLabel());
    }
}
