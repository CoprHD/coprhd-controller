/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.cloudarray.api.restapi;

import com.emc.storageos.services.restutil.RestClientFactory;
import com.emc.storageos.services.restutil.RestClientItf;
import com.sun.jersey.api.client.Client;

import java.net.URI;

/**
 * Created by Ameer on 11/19/2015.
 */
public class CloudArrayRestClientFactory extends RestClientFactory {
    @Override
    protected RestClientItf createNewRestClient(URI endpoint, String username, String password, Client client) {
        return new CloudArrayRestClient(endpoint, username, password, client);
    }
}
