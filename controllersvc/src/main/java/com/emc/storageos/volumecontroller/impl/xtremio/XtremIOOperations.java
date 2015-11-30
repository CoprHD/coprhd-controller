/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.xtremio;

import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.customconfigcontroller.DataSourceFactory;
import com.emc.storageos.customconfigcontroller.impl.CustomConfigHandler;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.xtremio.restapi.XtremIOV1ClientFactory;
import com.emc.storageos.xtremio.restapi.XtremIOV2ClientFactory;

public class XtremIOOperations {

    protected XtremIOV1ClientFactory xtremioV1RestClientFactory;
    
    protected XtremIOV2ClientFactory xtremioV2RestClientFactory;
    
    protected DbClient dbClient;

    @Autowired
    protected DataSourceFactory dataSourceFactory;
    @Autowired
    protected CustomConfigHandler customConfigHandler;


    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public XtremIOV1ClientFactory getXtremioV1RestClientFactory() {
        return xtremioV1RestClientFactory;
    }


    public void setXtremioV1RestClientFactory(XtremIOV1ClientFactory xtremioV1RestClientFactory) {
        this.xtremioV1RestClientFactory = xtremioV1RestClientFactory;
    }


    public XtremIOV2ClientFactory getXtremioV2RestClientFactory() {
        return xtremioV2RestClientFactory;
    }


    public void setXtremioV2RestClientFactory(XtremIOV2ClientFactory xtremioV2RestClientFactory) {
        this.xtremioV2RestClientFactory = xtremioV2RestClientFactory;
    }

}
