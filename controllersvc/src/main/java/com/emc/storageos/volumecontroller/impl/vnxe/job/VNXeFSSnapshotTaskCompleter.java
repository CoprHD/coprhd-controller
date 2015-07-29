/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller.impl.vnxe.job;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;

public class VNXeFSSnapshotTaskCompleter extends TaskCompleter {

    private static final long serialVersionUID = -1491254877900460861L;
    private static final Logger _logger = LoggerFactory.getLogger(VNXeFSSnapshotTaskCompleter.class);

    public VNXeFSSnapshotTaskCompleter(Class clazz, URI snapId, String opId) {
        super(clazz, snapId, opId);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {

        Snapshot snapshot = dbClient.queryObject(Snapshot.class, getId());
        FileShare fsObj = dbClient.queryObject(FileShare.class, snapshot.getParent());
        switch (status) {
            case error:
                dbClient.error(Snapshot.class, getId(), getOpId(), coded);
                if (fsObj != null) {
                    dbClient.error(FileShare.class, fsObj.getId(), getOpId(), coded);
                }
                break;
            default:
                dbClient.ready(Snapshot.class, getId(), getOpId());
                if (fsObj != null) {
                    dbClient.ready(FileShare.class, fsObj.getId(), getOpId());
                }
        }

        _logger.info("Done Snapshot operation {}, with Status: {}", getOpId(), status.name());

    }

}
