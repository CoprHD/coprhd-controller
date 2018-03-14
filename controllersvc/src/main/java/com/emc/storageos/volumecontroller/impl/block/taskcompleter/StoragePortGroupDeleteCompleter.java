/*
 * Copyright (c) 2017 Dell-EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.StoragePortGroup;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;

public class StoragePortGroupDeleteCompleter extends TaskCompleter {
    private static final Logger log = LoggerFactory.getLogger(StoragePortGroupDeleteCompleter.class);

    public StoragePortGroupDeleteCompleter(URI id, String opId) {
        super(StoragePortGroup.class, id, opId);
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            StoragePortGroup portGroup = dbClient.queryObject(StoragePortGroup.class, getId());
            if (status == Status.ready && portGroup != null) {
                dbClient.ready(StoragePortGroup.class, getId(), getOpId());
                dbClient.removeObject(portGroup);
            } else if (status == Status.error) {
                log.error("The status is error.");
                dbClient.error(StoragePortGroup.class, getId(), getOpId(), coded);
            }

        } catch (Exception e) {
            log.error("Failed updating status", e);
        } finally {
            updateWorkflowStatus(status, coded);
        }
    }

}