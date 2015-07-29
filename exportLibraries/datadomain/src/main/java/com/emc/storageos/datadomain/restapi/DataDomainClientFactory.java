/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.datadomain.restapi;

import com.emc.storageos.services.restutil.RestClientItf;
import com.emc.storageos.services.restutil.RestClientFactory;

import java.net.URI;

/**
 * Created by zeldib on 1/30/14.
 */
public class DataDomainClientFactory extends RestClientFactory {

    @Override
    protected RestClientItf createNewRestClient(URI endpoint, String username, String password, com.sun.jersey.api.client.Client client) {
        return new DataDomainClient(endpoint, username, password, client);
    }
}
