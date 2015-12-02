/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.isilon.restapi;

import java.net.URI;

import com.emc.storageos.common.http.RestAPIFactory;

/**
 * Isilon API client factory
 */
public class IsilonApiFactory extends RestAPIFactory<IsilonApi> {
    
    @Override
    public IsilonApi getRESTClient(URI endpoint) {
            return new IsilonApi(endpoint, getRestClient());
    }

    @Override
    public IsilonApi getRESTClient(URI endpoint, String username, String password) {
        return new IsilonApi(endpoint, getRestClient(), username, password);
    }


}
