/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.datadomain.restapi;

import java.net.URI;

import com.emc.storageos.services.restutil.RestClientFactory;
import com.emc.storageos.services.restutil.RestClientItf;
import com.sun.jersey.api.client.Client;

/**
 * Created by zeldib on 1/30/14.
 */
public class DataDomainClientFactory extends RestClientFactory {

    @Override
    protected RestClientItf createNewRestClient(URI endpoint, String username, String password, Client client) {
        return new DataDomainClient(endpoint, username, password, client);
    }

}
