/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.vnxe;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.VNXeApiClientFactory;

public class VNXeOperations {

    protected VNXeApiClientFactory _clientFactory;
    protected DbClient _dbClient;
    
    public VNXeApiClientFactory getVnxeApiClientFactory() {
        return _clientFactory;
    }

    public void setVnxeApiClientFactory(VNXeApiClientFactory clientFactory) {
        this._clientFactory = clientFactory;
    }
    
    
    public DbClient getDbClient() {
        return _dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this._dbClient = dbClient;
    }
    
    /**
     * Get the Vnxe service client for making requests to the Vnxe based
     * on the passed profile.
     * 
     * @param accessProfile A reference to the access profile.
     * 
     * @return A reference to the Vnxe service client.
     */
    protected VNXeApiClient getVnxeClient(StorageSystem storage) {
        VNXeApiClient client = _clientFactory.getClient(storage.getIpAddress(),
                storage.getPortNumber(),storage.getUsername(), 
                storage.getPassword());
        
        return client;
        
    }
    
}
