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
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import com.emc.storageos.services.OperationTypeEnum;

public class BlockMirrorDeactivateCompleter extends BlockMirrorTaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(BlockMirrorDeactivateCompleter.class);
    public static final String MIRROR_DEACTIVATED_MSG = "Mirror %s deactivated for volume %s";
    public static final String MIRROR_DEACTIVATE_FAILED_MSG = "Failed to deactivate mirror %s for volume %s";

    public BlockMirrorDeactivateCompleter(URI mirror, String opId) {
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
                dbClient.error(BlockMirror.class, mirror.getId(), getOpId(), coded);
                dbClient.error(Volume.class, volume.getId(), getOpId(), coded);
                break;
            default:
                dbClient.ready(BlockMirror.class, mirror.getId(), getOpId());
                dbClient.ready(Volume.class, volume.getId(), getOpId());
            }

            recordBlockMirrorOperation(dbClient, OperationTypeEnum.DEACTIVATE_VOLUME_MIRROR,
                    status, eventMessage(status, volume, mirror), mirror, volume);
        } catch (Exception e) {
            _log.error("Failed updating status. BlockMirrorDeactivate {}, for task " + getOpId(), getId(), e);
        }
    }

    private String eventMessage(Operation.Status status, Volume volume, BlockMirror mirror) {
        return (Operation.Status.ready == status) ?
                String.format(MIRROR_DEACTIVATED_MSG, mirror.getLabel(), volume.getLabel()) :
                String.format(MIRROR_DEACTIVATE_FAILED_MSG, mirror.getLabel(), volume.getLabel());
    }
}
