/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.scaleio.api.restapi;

import com.emc.storageos.driver.scaleio.serviceutils.restutil.RestClientFactory;
import com.emc.storageos.driver.scaleio.serviceutils.restutil.RestClientItf;
import com.sun.jersey.api.client.Client;

import java.net.URI;

public class ScaleIORestClientFactory extends RestClientFactory {

    @Override
    protected RestClientItf createNewRestClient(URI endpoint, String username,
            String password, Client client) {
        return new ScaleIORestClient(endpoint, username, password, client);
    }

}
