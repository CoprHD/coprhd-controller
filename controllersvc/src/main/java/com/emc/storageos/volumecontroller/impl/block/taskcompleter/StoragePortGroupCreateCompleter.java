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
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;

public class StoragePortGroupCreateCompleter extends TaskCompleter {
    private static final Logger log = LoggerFactory.getLogger(StoragePortGroupCreateCompleter.class);

    public StoragePortGroupCreateCompleter(URI id, String opId) {
        super(StoragePortGroup.class, id, opId);
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            StoragePortGroup portGroup = dbClient.queryObject(StoragePortGroup.class, getId());
            if (status == Status.ready && portGroup != null) {
                URI systemURI = portGroup.getStorageDevice();
                StorageSystem storage = dbClient.queryObject(StorageSystem.class, systemURI);
                portGroup.setNativeGuid(String.format("%s+%s", storage.getNativeGuid(), portGroup.getLabel()));
                portGroup.setInactive(false);
                dbClient.updateObject(portGroup);
                dbClient.ready(StoragePortGroup.class, getId(), getOpId());
            } else if (status == Status.error) {
                log.error("The status is error, remove the storage port group");
                portGroup.setInactive(true);
                dbClient.error(StoragePortGroup.class, getId(), getOpId(), coded);
                dbClient.removeObject(portGroup);
            }

        } catch (Exception e) {
            log.error("Failed updating status", e);
        } finally {
            updateWorkflowStatus(status, coded);
        }
    }
}