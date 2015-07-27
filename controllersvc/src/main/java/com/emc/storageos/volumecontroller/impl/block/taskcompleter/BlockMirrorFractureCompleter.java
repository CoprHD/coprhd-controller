/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import com.emc.storageos.services.OperationTypeEnum;

public class BlockMirrorFractureCompleter extends BlockMirrorTaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(BlockMirrorCreateCompleter.class);
    public static final String MIRROR_FRACTURED_MSG = "Mirror %s fractured for volume %s";
    public static final String MIRROR_FRACTURE_FAILED_MSG = "Failed to fracture mirror %s for volume %s";

    public BlockMirrorFractureCompleter(URI mirror, String opId) {
        super(BlockMirror.class, mirror, opId);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            super.complete(dbClient, status, coded);
            BlockMirror mirror = dbClient.queryObject(BlockMirror.class, getMirrorURI());
            Volume volume = dbClient.queryObject(Volume.class, mirror.getSource());

            switch (status) {
            case error:
                dbClient.error(Volume.class, volume.getId(), getOpId(), coded);
                break;
            default:
                mirror.setSyncState(BlockMirror.SynchronizationState.FRACTURED.toString());
                dbClient.persistObject(mirror);
                dbClient.ready(Volume.class, volume.getId(), getOpId());
            }
            recordBlockMirrorOperation(dbClient, OperationTypeEnum.FRACTURE_VOLUME_MIRROR,
                    Status.ready, eventMessage(status, volume, mirror), mirror, volume);
        } catch (Exception e) {
            _log.error("Failed updating status. BlockMirrorCreate {}, for task " + getOpId(), getId(), e);
        }
    }

    private String eventMessage(Operation.Status status, Volume volume, BlockMirror mirror) {
        return (Operation.Status.ready == status) ?
                String.format(MIRROR_FRACTURED_MSG, mirror.getLabel(), volume.getLabel()) :
                String.format(MIRROR_FRACTURE_FAILED_MSG, mirror.getLabel(), volume.getLabel());
    }
}
