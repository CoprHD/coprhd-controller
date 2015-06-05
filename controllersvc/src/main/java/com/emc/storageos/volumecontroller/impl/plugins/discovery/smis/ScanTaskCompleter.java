/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DataCollectionJobStatus;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;

public class ScanTaskCompleter extends DataCollectionTaskCompleter {

    /**
     * Reference to logger
     */
    private transient static final Logger _log = LoggerFactory.getLogger(ScanTaskCompleter.class);
    private static final long serialVersionUID = -3243938417237476539L;

    public ScanTaskCompleter(Class clazz, URI id, String opId) {
        super(clazz, id, opId);
    }

    public ScanTaskCompleter(AsyncTask task)  {
        super(task);
    }

    public String getJobType(){
        return ControllerServiceImpl.SCANNER;
    }

    protected void setLastTime(StorageProvider dbObject){
        dbObject.setLastScanTime(System.currentTimeMillis());
    }

    @Override
    protected void  updateObjectState(DbClient dbClient,
                                      DataCollectionJobStatus jobStatus) {
        Class type = getType();
        if ( StorageProvider.class.isAssignableFrom(type)) {
            try {
            	StorageProvider dbObject = (StorageProvider) DataObject.createInstance(type,getId());
                dbObject.trackChanges();
                dbObject.setScanStatus(jobStatus.toString());
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
        if ( StorageProvider.class.isAssignableFrom(type)) {
            try {
            	StorageProvider dbObject = (StorageProvider) DataObject.createInstance(type,getId());
                dbObject.trackChanges();
                dbObject.setNextScanTime(time);
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
            throw DeviceControllerException.exceptions.invalidSystemType(type.toString());
        }
    }

    @Override
    final public void setLastTime(DbClient dbClient, long time){
        Class type = getType();
        if ( StorageProvider.class.isAssignableFrom(type)) {
            try {
            	StorageProvider dbObject = (StorageProvider) DataObject.createInstance(type,getId());
                dbObject.trackChanges();
                dbObject.setLastScanTime(time);
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
            throw DeviceControllerException.exceptions.invalidSystemType(type.toString());
        }
    }

    @Override
    final public void setSuccessTime(DbClient dbClient, long time){
        Class type = getType();
        if ( StorageProvider.class.isAssignableFrom(type)) {
            try {
            	StorageProvider dbObject = (StorageProvider) DataObject.createInstance(type,getId());
                dbObject.trackChanges();
                dbObject.setSuccessScanTime(time);
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
            throw DeviceControllerException.exceptions.invalidSystemType(type.toString());
        }
    }

    @Override
    final protected void createDefaultOperation(DbClient dbClient){
        dbClient.createTaskOpStatus(getType(),getId(),getOpId(),
                                    ResourceOperationTypeEnum.SCAN_STORAGEPROVIDER);
    }
}