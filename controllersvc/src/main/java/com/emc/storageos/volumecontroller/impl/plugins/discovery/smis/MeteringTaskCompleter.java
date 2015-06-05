/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis;

import java.net.URI;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;

public class MeteringTaskCompleter extends DataCollectionTaskCompleter {

    private static final long serialVersionUID = -150746701650259111L;

    public MeteringTaskCompleter(Class clazz, URI id, String opId) {
        super(clazz, id, opId);
    }

    public MeteringTaskCompleter(AsyncTask task)  {
        super(task);
    }

    public String getJobType(){
        return ControllerServiceImpl.METERING;
    }

    protected void  updateObjectState(DbClient dbClient,
                                      DiscoveredDataObject.DataCollectionJobStatus jobStatus) {
        Class type = getType();
        if (DiscoveredSystemObject.class.isAssignableFrom(type))   {
            try {
                DiscoveredSystemObject dbObject = (DiscoveredSystemObject) DataObject.createInstance(type,getId());
                dbObject.trackChanges();
                dbObject.setMeteringStatus(jobStatus.toString());
                dbClient.persistObject(dbObject);
            }
            catch (InstantiationException ex) {
                DatabaseException.fatals.queryFailed(ex);
            }
            catch(IllegalAccessException ex)       {
                DatabaseException.fatals.queryFailed(ex);
            }
        }
        else {
            throw new RuntimeException("Unsupported system Type : " + type.toString());
        }
    }

    @Override
    final public void setNextRunTime(DbClient dbClient, long time) {
        Class type = getType();
        if (DiscoveredSystemObject.class.isAssignableFrom(type))   {
            try {
                DiscoveredSystemObject dbObject = (DiscoveredSystemObject) DataObject.createInstance(type,getId());
                dbObject.trackChanges();
                dbObject.setNextMeteringRunTime(time);
                dbClient.persistObject(dbObject);
            }
            catch (InstantiationException ex) {
                DatabaseException.fatals.queryFailed(ex);
            }
            catch(IllegalAccessException ex)       {
                DatabaseException.fatals.queryFailed(ex);
            }
        }
        else {
            throw new RuntimeException("Unsupported system Type : " + type.toString());
        }
    }

    @Override
    final public void setLastTime(DbClient dbClient, long time){
        Class type = getType();
        if (DiscoveredSystemObject.class.isAssignableFrom(type))   {
            try {
                DiscoveredSystemObject dbObject = (DiscoveredSystemObject) DataObject.createInstance(type,getId());
                dbObject.trackChanges();
                dbObject.setLastMeteringRunTime(time);
                dbClient.persistObject(dbObject);
            }
            catch (InstantiationException ex) {
                DatabaseException.fatals.queryFailed(ex);
            }
            catch(IllegalAccessException ex)       {
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
        if (DiscoveredSystemObject.class.isAssignableFrom(type))   {
            try {
                DiscoveredSystemObject dbObject = (DiscoveredSystemObject) DataObject.createInstance(type,getId());
                dbObject.trackChanges();
                dbObject.setSuccessMeteringTime(time);
                dbClient.persistObject(dbObject);
            }
            catch (InstantiationException ex) {
                DatabaseException.fatals.queryFailed(ex);
            }
            catch(IllegalAccessException ex)       {
                DatabaseException.fatals.queryFailed(ex);
            }
        }
        else {
            throw new RuntimeException("Unsupported system Type : " + type.toString());
        }

    }

    @Override
    final protected void createDefaultOperation(DbClient dbClient){
        dbClient.createTaskOpStatus(getType(),getId(),getOpId(),
                ResourceOperationTypeEnum.METERING_STORAGE_SYSTEM);
    }
}
