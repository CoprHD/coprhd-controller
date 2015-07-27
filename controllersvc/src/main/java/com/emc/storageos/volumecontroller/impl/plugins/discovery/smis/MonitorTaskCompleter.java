/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis;

import java.io.IOException;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DataCollectionJobStatus;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;

public class MonitorTaskCompleter extends DataCollectionTaskCompleter  {
    private static final long serialVersionUID = 1L;
    
    public MonitorTaskCompleter(AsyncTask task) {
        super(task);
        // TODO Auto-generated constructor stub
    }

    @Override
    final public String getJobType() {
          return ControllerServiceImpl.MONITORING;
    }

    @Override
    protected void updateObjectState(DbClient dbClient,
                                     DataCollectionJobStatus jobStatus) {
        // TODO provider monitoring status = true or false

    }

    @Override
    final public void setNextRunTime(DbClient dbClient, long time) {
        // TODO Auto-generated method stub
        
    }

    @Override
    final public void setLastTime(DbClient dbClient, long time) {
        // TODO Auto-generated method stub
    }

    @Override
    final public void setSuccessTime(DbClient dbClient, long time) {
        // TODO Auto-generated method stub
    }

    @Override
    final protected void createDefaultOperation(DbClient dbClient){
        // TODO Auto-generated method stub
    }

}
