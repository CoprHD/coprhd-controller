/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.file;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.impl.vnxe.job.VNXeFSSnapshotTaskCompleter;

public class FileSnapshotCreateWorkflowCompleter extends FileTaskCompleter {
    private static final long serialVersionUID = -1491254877900460861L;
    private static final Logger _logger = LoggerFactory.getLogger(VNXeFSSnapshotTaskCompleter.class);

    public FileSnapshotCreateWorkflowCompleter(URI snapURI, String opId) {
        super(Snapshot.class, snapURI, opId);
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
