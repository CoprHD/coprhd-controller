/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.scaleio.api.restapi;

import java.net.URI;

import org.springframework.stereotype.Component;

import com.emc.storageos.common.http.RestAPIFactory;

@Component
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
