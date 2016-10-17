/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.TaskCompleter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;

public abstract class DataCollectionTaskCompleter extends TaskCompleter {

    /**
     * Reference to logger
     */
    private transient static final Logger _log = LoggerFactory.getLogger(DataCollectionTaskCompleter.class);
    private static final long serialVersionUID = -7594173946994954408L;

    /**
     * @param clazz
     * @param id
     * @param opId
     */
    public DataCollectionTaskCompleter(Class clazz, URI id, String opId) {
        super(clazz, id, opId);
    }

    public DataCollectionTaskCompleter(Class clazz, List<URI> ids, String opId) {
        super(clazz, ids, opId);
    }

    public DataCollectionTaskCompleter(AsyncTask task) {
        super(task);
    }

    public void schedule(DbClient dbClient) {
        updateObjectState(dbClient, DiscoveredDataObject.DataCollectionJobStatus.SCHEDULED);
        DataObject dbObject = dbClient.queryObject(getType(), getId());
        if (!dbObject.getOpStatus().containsKey(getOpId())) {
            createDefaultOperation(dbClient);
        }
        _log.info(String.format("Scheduled JobType: %s, Class: %s, Id: %s, OpId: %s",
                getJobType(), getType().toString(), getId().toString(), getOpId()));

        // TODO : Need to record the event of task completion in the DB
        // RecordableEventManager.EventType eventType = ....
        // recordBourneDiscoverEvent(dbClient, eventType, status, "Discover");
    }

    @Override
    public void statusError(DbClient dbClient, ServiceCoded code) {
        DataObject dbObject = dbClient.queryObject(getType(), getId());
        if (!dbObject.getOpStatus().containsKey(getOpId())) {
            createDefaultOperation(dbClient);
        }
        super.statusError(dbClient, code);
        _log.info(String.format("Failed JobType: %s, Class: %s Id: %s, OpId: %s, status: %s, cause: %s",
                getJobType(), getType().toString(), getId().toString(), getOpId(), Status.error.name(), code.getMessage()));
    }

    @Override
    public void statusReady(DbClient dbClient, String message) {
        DataObject dbObject = dbClient.queryObject(getType(), getId());
        if (!dbObject.getOpStatus().containsKey(getOpId())) {
            createDefaultOperation(dbClient);
        }
        super.statusReady(dbClient, message);
        _log.info(String.format("Completed JobType: %s, Class: %s Id: %s, OpId: %s, status: %s, message: %s",
                getJobType(), getType().toString(), getId().toString(), getOpId(), Status.ready.name(), message));
    }

    @Override
    public void statusReady(DbClient dbClient) {
        statusReady(dbClient, (String) null);
    }

    @Override
    public void statusPending(DbClient dbClient, String message) {
        DataObject dbObject = dbClient.queryObject(getType(), getId());
        if (!dbObject.getOpStatus().containsKey(getOpId())) {
            createDefaultOperation(dbClient);
        }
        super.statusPending(dbClient, message);

        _log.info(String.format("Updated JobType: %s, Class: %s Id: %s, OpId: %s, status: %s, message: %s",
                getJobType(), getType().toString(), getId().toString(), getOpId(),
                Status.pending.name(), message));
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        updateObjectState(dbClient, getJobStatus(status));
        long time = System.currentTimeMillis();
        setLastTime(dbClient, time);
        if (status == Status.error) {
            statusError(dbClient, coded);
        }
        else if (status == Status.ready) {
            setSuccessTime(dbClient, time);
            statusReady(dbClient, null);
        }
        // TODO : Need to record the event of task completion in the DB
        // RecordableEventManager.EventType eventType = ....
        // recordBourneDiscoverEvent(dbClient, eventType, status, "Discover");
    }

    private DiscoveredDataObject.DataCollectionJobStatus getJobStatus(Operation.Status status) {
        switch (status) {
            case pending:
                return DiscoveredDataObject.DataCollectionJobStatus.IN_PROGRESS;
            case ready:
                return DiscoveredDataObject.DataCollectionJobStatus.COMPLETE;
            case error:
                return DiscoveredDataObject.DataCollectionJobStatus.ERROR;
            default:
                return DiscoveredDataObject.DataCollectionJobStatus.IN_PROGRESS;
        }
    }

    abstract public String getJobType();

    abstract protected void updateObjectState(DbClient dbClient,
            DiscoveredDataObject.DataCollectionJobStatus jobStatus);

    abstract public void setNextRunTime(DbClient dbClient, long time);

    abstract protected void setLastTime(DbClient dbClient, long time);

    abstract protected void setSuccessTime(DbClient dbClient, long time);

    abstract protected void createDefaultOperation(DbClient dbClient);
}
