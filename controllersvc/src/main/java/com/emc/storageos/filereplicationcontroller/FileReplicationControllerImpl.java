/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.filereplicationcontroller;
import java.net.URI;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.Controller;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import com.emc.storageos.impl.AbstractDiscoveredSystemController;
import com.emc.storageos.volumecontroller.impl.Dispatcher;

public class FileReplicationControllerImpl extends AbstractDiscoveredSystemController implements  FileReplicationController{
	private static final Logger log = LoggerFactory.getLogger(FileReplicationControllerImpl.class);
    
	private Set<FileReplicationController> deviceImpl;
    private Dispatcher dispatcher;
    private DbClient dbClient;
    
    public Set<FileReplicationController> getDeviceImpl() {
        return deviceImpl;
    }

    public void setDeviceImpl(Set<FileReplicationController> deviceImpl) {
        this.deviceImpl = deviceImpl;
    }

    public Dispatcher getDispatcher() {
        return dispatcher;
    }

    public void setDispatcher(Dispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }
	
	@Override
	protected Controller lookupDeviceController(DiscoveredSystemObject device) {
		// TODO Auto-generated method stub
		return deviceImpl.iterator().next();
	}

}
