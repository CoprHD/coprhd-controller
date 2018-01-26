/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.vmax;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.vmax.restapi.VMAXApiClientFactory;

public abstract class VMAXOperations {
    protected DbClient dbClient;
    protected VMAXApiClientFactory vmaxClientFactory;

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void setVmaxClientFactory(VMAXApiClientFactory vmaxClientFactory) {
        this.vmaxClientFactory = vmaxClientFactory;
    }
}
