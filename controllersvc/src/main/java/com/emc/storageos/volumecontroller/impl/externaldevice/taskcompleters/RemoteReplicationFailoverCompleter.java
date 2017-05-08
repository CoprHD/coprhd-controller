package com.emc.storageos.volumecontroller.impl.externaldevice.taskcompleters;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.NamedURI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.externaldevice.RemoteReplicationElement;

public class RemoteReplicationFailoverCompleter extends TaskCompleter {

    private static final Logger _logger = LoggerFactory.getLogger(RemoteReplicationFailoverCompleter.class);

    private DbClient dbClient;

    protected void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    protected DbClient getDbClient() {
        return dbClient;
    }

    private RemoteReplicationSet.ElementType elementType;
    private URI elementURI;
    /**
     * Constructor for failover completer
     *
     * @param remoteReplicationElement
     * @param opId
     */
    public RemoteReplicationFailoverCompleter(RemoteReplicationElement remoteReplicationElement, String opId) {
        elementType = remoteReplicationElement.getType();
        elementURI = remoteReplicationElement.getElementUri();

        setOpId(opId);
        addIds(Collections.singletonList(elementURI));
        switch (elementType) {
            case REPLICATION_GROUP:
                setType(RemoteReplicationGroup.class);
                break;
            case REPLICATION_PAIR:
                break;
            case CONSISTENCY_GROUP:
                setType(BlockConsistencyGroup.class);
                break;
            case REPLICATION_SET:
                setType(com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet.class);
                break;
        }
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded)
            throws DeviceControllerException {
        _logger.info("Complete operation for {} with id {} and status {}", elementType, elementURI, status);
        setDbClient(dbClient);
        setStatus(dbClient, status, coded);
        updateWorkflowStatus(status, coded);
    }

}
