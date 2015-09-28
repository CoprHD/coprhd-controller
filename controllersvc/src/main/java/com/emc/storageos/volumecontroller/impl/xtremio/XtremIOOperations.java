/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.xtremio;

import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.customconfigcontroller.DataSourceFactory;
import com.emc.storageos.customconfigcontroller.impl.CustomConfigHandler;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.xtremio.restapi.XtremIOClientFactory;

public class XtremIOOperations {

    protected XtremIOClientFactory xtremioRestClientFactory;
    protected DbClient dbClient;

    @Autowired
    protected DataSourceFactory dataSourceFactory;
    @Autowired
    protected CustomConfigHandler customConfigHandler;

    public void setXtremioRestClientFactory(XtremIOClientFactory xtremioRestClientFactory) {
        this.xtremioRestClientFactory = xtremioRestClientFactory;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

}
