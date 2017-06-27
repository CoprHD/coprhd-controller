/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.externaldevice.taskcompleters;

import static com.emc.storageos.volumecontroller.impl.externaldevice.taskcompleters.utils.RemoteReplicationTaskCompleterUtils.configTaskCompleter;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.externaldevice.RemoteReplicationElement;

@SuppressWarnings("serial")
public class RemoteReplicationResumeCompleter extends TaskCompleter {

    private static final Logger _logger = LoggerFactory.getLogger(RemoteReplicationResumeCompleter.class);

    private DbClient dbClient;
    private RemoteReplicationSet.ElementType elementType;
    private URI elementURI;

    protected void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    protected DbClient getDbClient() {
        return dbClient;
    }

    public RemoteReplicationResumeCompleter(RemoteReplicationElement remoteReplicationElement, String opId) {
        elementType = remoteReplicationElement.getType();
        elementURI = remoteReplicationElement.getElementUri();

        configTaskCompleter(this, elementType, elementURI, opId);
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        _logger.info("Complete operation for {} with id {} and status {}", elementType, elementURI, status);
        setDbClient(dbClient);
        setStatus(dbClient, status, coded);
        updateWorkflowStatus(status, coded);
    }
}
