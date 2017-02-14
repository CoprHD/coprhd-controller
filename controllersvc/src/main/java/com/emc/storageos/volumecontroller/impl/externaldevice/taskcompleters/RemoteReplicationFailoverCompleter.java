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
    private List<URI> groupURIs = null;
    private List<URI> pairURIs = null;
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
                break;
        }
    }


    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded)
            throws DeviceControllerException {
        setDbClient(dbClient);

        _logger.info("Complete operation for {} with id {} and status {}", elementType, elementURI, status);
        List<RemoteReplicationPair> rrPairs;
        try {
            if (status == Operation.Status.ready) {
                switch (elementType) {
                    case REPLICATION_GROUP:
                        RemoteReplicationGroup remoteReplicationGroup = dbClient.queryObject(RemoteReplicationGroup.class, elementURI);
                        _logger.info("Failed over remote replication group: {}", remoteReplicationGroup.getNativeId());
                        remoteReplicationGroup.setReplicationState(RemoteReplicationSet.ReplicationState.FAILED_OVER.toString());
                        dbClient.updateObject(remoteReplicationGroup);
                        if (pairURIs != null) {
                            rrPairs = dbClient.queryObject(RemoteReplicationPair.class, pairURIs);
                            for (RemoteReplicationPair rrPair : rrPairs) {
                                rrPair.setReplicationState(RemoteReplicationSet.ReplicationState.FAILED_OVER.toString());
                                // after failover, replication link is inactive, leave replication direction as is
                            }
                            dbClient.updateObject(rrPairs);
                        } else {
                            _logger.warn("No replication pairs provided for group link operation, group {}",
                                    remoteReplicationGroup.getNativeId());
                        }
                        _logger.info("Completed operation for {} with id {} and status {}", elementType, elementURI, status);
                        break;
                    case REPLICATION_PAIR:
                        RemoteReplicationPair remoteReplicationPair = dbClient.queryObject(RemoteReplicationPair.class, elementURI);
                        _logger.info("Failed over remote replication pair: {}", remoteReplicationPair.getNativeId());
                        remoteReplicationPair.setReplicationState(RemoteReplicationSet.ReplicationState.FAILED_OVER.toString());
                        dbClient.updateObject(remoteReplicationPair);
                        _logger.info("Completed operation for {} with id {} and status {}", elementType, elementURI, status);
                        break;
                    case CONSISTENCY_GROUP:
                        BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, elementURI);
                        _logger.info("Failed over consistency group: {}", cg.getNativeId());
                        if (pairURIs != null) {
                            rrPairs = dbClient.queryObject(RemoteReplicationPair.class, pairURIs);
                            for (RemoteReplicationPair rrPair : rrPairs) {
                                rrPair.setReplicationState(RemoteReplicationSet.ReplicationState.FAILED_OVER.toString());
                                // after failover, replication link is inactive, leave replication direction as is
                            }
                            dbClient.updateObject(rrPairs);
                        } else {
                            _logger.warn("No replication pairs provided for cg link operation, cg {}",
                                    cg.getNativeId());
                        }
                        _logger.info("Completed operation for {} with id {} and status {}", elementType, elementURI, status);
                        break;
                    case REPLICATION_SET:
                        break;
                }
            }
        } catch (Exception e) {
            _logger.error(String.format(
                    "Failed to process failover completion for %s with Id: %s, OpId: %s",
                    elementType, elementURI, getOpId()), e);

        } finally {
            setStatus(dbClient, status, coded);
            updateWorkflowStatus(status, coded);
        }
    }

    public List<URI> getGroupURIs() {
        return groupURIs;
    }

    public void setGroupURIs(List<URI> groupURIs) {
        this.groupURIs = groupURIs;
    }

    public List<URI> getPairURIs() {
        return pairURIs;
    }

    public void setPairURIs(List<URI> pairURIs) {
        this.pairURIs = pairURIs;
    }
}
