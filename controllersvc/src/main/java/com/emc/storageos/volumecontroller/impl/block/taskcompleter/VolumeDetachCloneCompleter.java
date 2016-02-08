/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class VolumeDetachCloneCompleter extends VolumeTaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(VolumeDetachCloneCompleter.class);

    public VolumeDetachCloneCompleter(URI cloneId, String opId) {
        super(Volume.class, cloneId, opId);
    }

    public VolumeDetachCloneCompleter(List<URI> clones, String opId) {
        super(Volume.class, clones, opId);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) {
        _log.info("START VolumeDetachCloneCompleter");
        try {
            super.complete(dbClient, status, coded);

            List<Volume> cloneVolumes = dbClient.queryObject(Volume.class, getIds());
            for (Volume clone : cloneVolumes) {
                switch (status) {
                    case error:
                        dbClient.error(Volume.class, clone.getId(), getOpId(), coded);
                        break;
                    default:
                        boolean needUpdate = false;

                        if (NullColumnValueGetter.isNotNullValue(clone.getFullCopySetName())) {
                            clone.setFullCopySetName(NullColumnValueGetter.getNullStr());
                            needUpdate = true;
                        }

                        if (needUpdate) {
                            dbClient.updateObject(clone);
                        }
                        dbClient.ready(Volume.class, clone.getId(), getOpId());
                }
            }

            recordBlockVolumeOperation(dbClient, OperationTypeEnum.DETACH_VOLUME_FULL_COPY, status, "TEST");
        } catch (Exception e) {
            _log.error("Failed to update status for detach volume clone", e);
        }
    }
}
