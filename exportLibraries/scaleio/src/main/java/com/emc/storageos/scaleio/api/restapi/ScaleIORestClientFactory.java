/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.scaleio.api.restapi;

import java.net.URI;

import com.emc.storageos.common.http.RestAPIFactory;

public class ScaleIORestClientFactory extends RestAPIFactory<ScaleIORestClient> {

    @Override
    public ScaleIORestClient getRESTClient(URI endpoint) {
        return null;
    }

    @Override
    public ScaleIORestClient getRESTClient(URI endpoint, String username, String password) {
        return new ScaleIORestClient(endpoint, username, password, getRestClient());
    }

}
