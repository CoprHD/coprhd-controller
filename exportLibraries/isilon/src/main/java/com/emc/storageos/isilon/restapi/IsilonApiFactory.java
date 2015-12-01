/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.isilon.restapi;

import java.net.URI;

import org.springframework.stereotype.Component;

import com.emc.storageos.common.http.RestAPIFactory;

/**
 * Isilon API client factory
 */
@Component
public class IsilonApiFactory extends RestAPIFactory<IsilonApi> {
    
    @Override
    public IsilonApi getRESTClient(URI endpoint) {
            return new IsilonApi(endpoint, getRestClient());
    }

    @Override
    public IsilonApi getRESTClient(URI endpoint, String username, String password) {
        return null;
    }


}
