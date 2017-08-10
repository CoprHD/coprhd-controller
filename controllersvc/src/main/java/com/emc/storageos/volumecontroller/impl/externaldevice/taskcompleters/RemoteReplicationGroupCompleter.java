/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.externaldevice.taskcompleters;

import java.net.URI;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;

public class RemoteReplicationGroupCompleter extends TaskCompleter {

    private static final Logger _logger = LoggerFactory.getLogger(RemoteReplicationGroupCompleter.class);

    private DbClient dbClient;

    protected void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    protected DbClient getDbClient() {
        return dbClient;
    }

    /**
     * Constructor for group completer
     *
     * @param groupURI
     * @param opId
     */
    public RemoteReplicationGroupCompleter(URI groupURI, String opId) {
        super(RemoteReplicationGroup.class, Collections.singletonList(groupURI), opId);
    }


    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded)
            throws DeviceControllerException {
        setDbClient(dbClient);
        setStatus(dbClient, status, coded);
        updateWorkflowStatus(status, coded);
    }
}
