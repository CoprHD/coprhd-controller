/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.scaleio.api.restapi;

import java.net.URI;

import com.emc.storageos.services.restutil.RestClientFactory;
import com.emc.storageos.services.restutil.RestClientItf;
import com.sun.jersey.api.client.Client;

public class ScaleIORestClientFactory extends RestClientFactory {

    @Override
    protected RestClientItf createNewRestClient(URI endpoint, String username,
            String password, Client client) {
        return new ScaleIORestClient(endpoint, username, password, client);
    }

}
