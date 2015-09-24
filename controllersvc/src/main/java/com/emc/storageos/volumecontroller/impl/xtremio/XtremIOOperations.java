/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.xtremio;

import java.net.URI;

import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.customconfigcontroller.DataSourceFactory;
import com.emc.storageos.customconfigcontroller.impl.CustomConfigHandler;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.xtremio.restapi.XtremIOClient;
import com.emc.storageos.xtremio.restapi.XtremIOClientFactory;
import com.emc.storageos.xtremio.restapi.XtremIOConstants;

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

    /**
     * Get the XtremIO client for making requests to the system based
     * on the passed profile.
     * 
     * @param accessProfile A reference to the access profile.
     * 
     * @return A reference to the xtremio client.
     */
    protected XtremIOClient getXtremIOClient(StorageSystem system) {
        XtremIOClient client = (XtremIOClient) xtremioRestClientFactory
                .getRESTClient(
                        URI.create(XtremIOConstants.getXIOBaseURI(system.getSmisProviderIP(),
                                system.getSmisPortNumber())), system.getSmisUserName(), system.getSmisPassword(), true,
                        system.getFirmwareVersion());
        return client;
    }
}
