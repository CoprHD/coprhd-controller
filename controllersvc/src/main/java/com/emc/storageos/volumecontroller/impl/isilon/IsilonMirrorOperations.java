/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.isilon;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.isilon.restapi.IsilonApi;
import com.emc.storageos.isilon.restapi.IsilonApiFactory;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.file.FileMirrorOperations;

public class IsilonMirrorOperations implements FileMirrorOperations {
    private static final Logger log = LoggerFactory.getLogger(IsilonMirrorOperations.class);

    private DbClient _dbClient;
    private IsilonApiFactory _factory;

    public IsilonApiFactory getIsilonApiFactory() {
        return _factory;
    }

    /**
     * Set Isilon API factory
     * 
     * @param factory
     */
    public void setIsilonApiFactory(IsilonApiFactory factory) {
        _factory = factory;
    }

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    @Override
    public void createMirrorFileShare(StorageSystem storage, URI mirror,
            Boolean createInactive, TaskCompleter taskCompleter) throws DeviceControllerException {

    }

    @Override
    public void deleteMirrorFileShare(StorageSystem storage, URI mirror,
            TaskCompleter taskCompleter) throws DeviceControllerException {
    }
    
    IsilonApi getIsilonClient(StorageSystem storage) {
        return getIsilonApiFactory().getRESTClient(storage.getId());
    }
    
    
}
