/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis;

import java.net.URI;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ComputeSystem;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.NetworkSystem;
import com.emc.storageos.db.client.model.ProtectionSet;
import com.emc.storageos.db.client.model.Vcenter;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationConfigProvider;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.volumecontroller.AsyncTask;

public class DiscoverTaskCompleter extends DataCollectionTaskCompleter {

    private static final long serialVersionUID = 6012924628969009805L;
    private String _jobType;

    public DiscoverTaskCompleter(Class clazz, URI id, String opId, String jobType) {
        super(clazz, id, opId);
        _jobType = jobType;
    }

    public DiscoverTaskCompleter(AsyncTask task, String jobType) {
        super(task);
        _jobType = jobType;
    }

    public String getJobType() {
        return _jobType;
    }

    protected void updateObjectState(DbClient dbClient,
            DiscoveredDataObject.DataCollectionJobStatus jobStatus) {
        Class type = getType();
        if (DiscoveredSystemObject.class.isAssignableFrom(type)) {
            try {
                DiscoveredSystemObject dbObject = (DiscoveredSystemObject) DataObject.createInstance(type, getId());
                dbObject.trackChanges();
                dbObject.setDiscoveryStatus(jobStatus.toString());
                dbClient.persistObject(dbObject);
            } catch (InstantiationException ex) {
                DatabaseException.fatals.queryFailed(ex);
            } catch (IllegalAccessException ex) {
                DatabaseException.fatals.queryFailed(ex);
            }
        }
        else {
            throw DeviceControllerException.exceptions.invalidSystemType(type.toString());
        }
    }

    @Override
    final public void setNextRunTime(DbClient dbClient, long time) {
        Class type = getType();
        if (DiscoveredSystemObject.class.isAssignableFrom(type)) {
            try {
                DiscoveredSystemObject dbObject = (DiscoveredSystemObject) DataObject.createInstance(type, getId());
                dbObject.trackChanges();
                dbObject.setNextDiscoveryRunTime(time);
                dbClient.persistObject(dbObject);
            } catch (InstantiationException ex) {
                DatabaseException.fatals.queryFailed(ex);
            } catch (IllegalAccessException ex) {
                DatabaseException.fatals.queryFailed(ex);
            }
        }
        else {
            throw new RuntimeException("Unsupported system Type : " + type.toString());
        }
    }

    @Override
    final public void setLastTime(DbClient dbClient, long time) {
        Class type = getType();
        if (DiscoveredSystemObject.class.isAssignableFrom(type)) {
            try {
                DiscoveredSystemObject dbObject = (DiscoveredSystemObject) DataObject.createInstance(type, getId());
                dbObject.trackChanges();
                dbObject.setLastDiscoveryRunTime(time);
                dbClient.persistObject(dbObject);
            } catch (InstantiationException ex) {
                DatabaseException.fatals.queryFailed(ex);
            } catch (IllegalAccessException ex) {
                DatabaseException.fatals.queryFailed(ex);
            }
        }
        else {
            throw new RuntimeException("Unsupported system Type : " + type.toString());
        }
    }

    @Override
    final public void setSuccessTime(DbClient dbClient, long time) {
        Class type = getType();
        if (DiscoveredSystemObject.class.isAssignableFrom(type)) {
            try {
                DiscoveredSystemObject dbObject = (DiscoveredSystemObject) DataObject.createInstance(type, getId());
                dbObject.trackChanges();
                dbObject.setSuccessDiscoveryTime(time);
                dbClient.persistObject(dbObject);
            } catch (InstantiationException ex) {
                DatabaseException.fatals.queryFailed(ex);
            } catch (IllegalAccessException ex) {
                DatabaseException.fatals.queryFailed(ex);
            }
        }
        else {
            throw new RuntimeException("Unsupported system Type : " + type.toString());
        }

    }

    @Override
    protected void createDefaultOperation(DbClient dbClient) {
        ResourceOperationTypeEnum opType = ResourceOperationTypeEnum.DISCOVER_STORAGE_SYSTEM;

        Class type = getType();

        if (Host.class.equals(type)) {
            opType = ResourceOperationTypeEnum.DISCOVER_HOST;
        } else if (Vcenter.class.equals(type)) {
            opType = ResourceOperationTypeEnum.DISCOVER_VCENTER;
        } else if (ComputeSystem.class.equals(type)) {
            opType = ResourceOperationTypeEnum.DISCOVER_COMPUTE_SYSTEM;
        } else if (NetworkSystem.class.equals(type)) {
            opType = ResourceOperationTypeEnum.DISCOVER_NETWORK_SYSTEM;
        } else if (ProtectionSet.class.equals(type)) {
            opType = ResourceOperationTypeEnum.DISCOVER_PROTECTION_SET;
        } else if (RemoteReplicationConfigProvider.class.equals(type)) {
            opType = ResourceOperationTypeEnum.DISCOVER_REMOTE_REPLICATION_CONFIG_PROVIDER;
        }

        dbClient.createTaskOpStatus(getType(), getId(), getOpId(),
                opType);
    }

}
