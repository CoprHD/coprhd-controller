/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.model.ResourceOperationTypeEnum;

public class ArrayAffinityDataCollectionTaskCompleter extends DataCollectionTaskCompleter {
    private static final long serialVersionUID = 7659532197486432647L;
    private static final Logger _log = LoggerFactory
            .getLogger(ArrayAffinityDataCollectionTaskCompleter.class);
    private String _jobType;
    private boolean _isScheduled = false;

    public ArrayAffinityDataCollectionTaskCompleter(Class clazz, List<URI> systemIds, String opId, String jobType, boolean isScheduled) {
        super(clazz, systemIds, opId);
        _jobType = jobType;
        _isScheduled = isScheduled;
    }

    public String getJobType() {
        return _jobType;
    }

    protected void updateObjectState(DbClient dbClient,
            DiscoveredDataObject.DataCollectionJobStatus jobStatus) {
        Iterator<StorageSystem> systems = dbClient.queryIterativeObjects(StorageSystem.class, getIds());
        List<StorageSystem> systemsToUpdate = new ArrayList<StorageSystem>();
        while (systems.hasNext()) {
            StorageSystem system = systems.next();
            if (system != null && !system.getInactive()) {
                system.trackChanges();
                system.setArrayAffinityStatus(jobStatus.toString());
                systemsToUpdate.add(system);
            }
        }

        if (!systemsToUpdate.isEmpty()) {
            dbClient.updateObject(systemsToUpdate);
        }
    }

    @Override
    final public void setNextRunTime(DbClient dbClient, long time) {
        if (!_isScheduled) {
            return;
        }

        Iterator<StorageSystem> systems = dbClient.queryIterativeObjects(StorageSystem.class, getIds());
        List<StorageSystem> systemsToUpdate = new ArrayList<StorageSystem>();
        while (systems.hasNext()) {
            StorageSystem system = systems.next();
            if (system != null && !system.getInactive()) {
                system.trackChanges();
                system.setNextArrayAffinityRunTime(time);
                systemsToUpdate.add(system);
            }
        }

        if (!systemsToUpdate.isEmpty()) {
            dbClient.updateObject(systemsToUpdate);
        }
    }

    @Override
    final public void setLastTime(DbClient dbClient, long time) {
        if (!_isScheduled) {
            return;
        }

        Iterator<StorageSystem> systems = dbClient.queryIterativeObjects(StorageSystem.class, getIds());
        List<StorageSystem> systemsToUpdate = new ArrayList<StorageSystem>();
        while (systems.hasNext()) {
            StorageSystem system = systems.next();
            if (system != null && !system.getInactive()) {
                system.trackChanges();
                system.setLastArrayAffinityRunTime(time);
                systemsToUpdate.add(system);
            }
        }

        if (!systemsToUpdate.isEmpty()) {
            dbClient.updateObject(systemsToUpdate);
        }
    }

    @Override
    final public void setSuccessTime(DbClient dbClient, long time) {
        if (!_isScheduled) {
            return;
        }

        Iterator<StorageSystem> systems = dbClient.queryIterativeObjects(StorageSystem.class, getIds());
        List<StorageSystem> systemsToUpdate = new ArrayList<StorageSystem>();
        while (systems.hasNext()) {
            StorageSystem system = systems.next();
            if (system != null && !system.getInactive()) {
                system.trackChanges();
                system.setSuccessArrayAffinityTime(time);
                systemsToUpdate.add(system);
            }
        }

        if (!systemsToUpdate.isEmpty()) {
            dbClient.updateObject(systemsToUpdate);
        }
    }

    @Override
    protected void createDefaultOperation(DbClient dbClient) {
        ResourceOperationTypeEnum opType = ResourceOperationTypeEnum.ARRAYAFFINITY_STORAGE_SYSTEM;
        Class type = getType();
        String opId = getOpId();

        for (URI id : getIds()) {
            dbClient.createTaskOpStatus(type, id, opId, opType);
        }
    }

    public void setLastStatusMessage(DbClient dbClient, String message) {
        Iterator<StorageSystem> systems = dbClient.queryIterativeObjects(StorageSystem.class, getIds());
        List<StorageSystem> systemsToUpdate = new ArrayList<StorageSystem>();
        while (systems.hasNext()) {
            StorageSystem system = systems.next();
            if (system != null && !system.getInactive()) {
                system.trackChanges();
                system.setLastArrayAffinityStatusMessage(message);;
                systemsToUpdate.add(system);
            }
        }

        if (!systemsToUpdate.isEmpty()) {
            dbClient.updateObject(systemsToUpdate);
        }
    }
}
