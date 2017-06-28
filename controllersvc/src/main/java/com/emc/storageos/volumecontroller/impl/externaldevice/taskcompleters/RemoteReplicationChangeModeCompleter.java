/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.externaldevice.taskcompleters;

import java.net.URI;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.externaldevice.RemoteReplicationElement;

@SuppressWarnings("serial")
public class RemoteReplicationChangeModeCompleter extends TaskCompleter {

    private static final Logger log = LoggerFactory.getLogger(RemoteReplicationChangeModeCompleter.class);

    private DbClient dbClient;
    private RemoteReplicationSet.ElementType elementType;
    private URI elementURI;

    public RemoteReplicationChangeModeCompleter(RemoteReplicationElement remoteReplicationElement, String opId) {
        elementType = remoteReplicationElement.getType();
        elementURI = remoteReplicationElement.getElementUri();

        setOpId(opId);
        addIds(Collections.singletonList(elementURI));
        switch (elementType) {
            case REPLICATION_GROUP:
                setType(RemoteReplicationGroup.class);
                break;
            case REPLICATION_PAIR:
                setType(RemoteReplicationPair.class);
                break;
            case CONSISTENCY_GROUP:
                setType(BlockConsistencyGroup.class);
                break;
            case REPLICATION_SET:
                setType(com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet.class);
                break;
            default:
                throw new RuntimeException(String.format("Undefined element type: %s", elementType.toString()));
        }
    }

    protected void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    protected DbClient getDbClient() {
        return dbClient;
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        log.info("Complete operation for {} with id {} and status {}", elementType, elementURI, status);
        setDbClient(dbClient);
        setStatus(dbClient, status, coded);
        updateWorkflowStatus(status, coded);
    }
}
