/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/*
 * Copyright (c) 2012. EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis;

import java.io.Serializable;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

/**
 * Job for Discover.
 */
public class DataCollectionDiscoverJob extends DataCollectionJob implements Serializable {

    private static final long serialVersionUID = -4345688816281981819L;
    private final DataCollectionTaskCompleter _completer;
    private String _namespace;

    public  DataCollectionDiscoverJob(DiscoverTaskCompleter completer, String namespace){
        this(completer, JobOrigin.USER_API,  namespace);
    }

    DataCollectionDiscoverJob(DiscoverTaskCompleter completer, JobOrigin origin, String namespace){
        super(origin);
        _completer = completer;
        _namespace = namespace;
    }

    @Override
    public DataCollectionTaskCompleter getCompleter() {
        return _completer;
    }

    @Override
    public void ready(DbClient dbClient) throws DeviceControllerException{
    	_completer.ready(dbClient);
    }

    @Override
    public void error(DbClient dbClient, ServiceCoded serviceCoded) throws DeviceControllerException{
        _completer.error(dbClient, serviceCoded);
    }

    @Override
    public void schedule(DbClient dbClient){
        _completer.schedule(dbClient);
    }

    @Override
    final public void setTaskError(DbClient dbClient,ServiceCoded code) {
        _completer.statusError(dbClient,code);
    }

    @Override
    final public void setTaskReady(DbClient dbClient,String message){
        _completer.statusReady(dbClient,message);
    }

    @Override
    final public void updateTask(DbClient dbClient, String message){
        _completer.statusPending(dbClient,message);
    }

    public String getType(){
        return _completer.getJobType();
    }

    public String systemString() {
        String sys = null;
        try {
            sys = getCompleter().getId().toString();
        }
        catch (Exception ex) {}
        return sys;
    }

    public String getNamespace() {
        return _namespace;
    }

    public boolean isActiveJob(DbClient dbClient){
        DataObject dbObject = dbClient.queryObject(_completer.getType(),_completer.getId());
        return (dbObject != null && !dbObject.getInactive()) ? true : false;
    }

}
