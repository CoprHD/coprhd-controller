/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.ecs.api;

import java.net.URI;

import org.springframework.stereotype.Component;

import com.emc.storageos.common.http.RestAPIFactory;;

/**
 * ECS API client factory
 */
@Component
public class ECSApiFactory extends RestAPIFactory<ECSApi>{
    
    @Override
    public ECSApi getRESTClient(URI endpoint) {
        return new ECSApi(endpoint, getRestClient());
    }

    @Override
    public ECSApi getRESTClient(URI endpoint, String username, String password) {
        return new ECSApi(endpoint, getRestClient(), username, password);
    }

}