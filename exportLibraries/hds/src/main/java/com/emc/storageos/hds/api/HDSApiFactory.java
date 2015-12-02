/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hds.api;

import java.net.URI;

import com.emc.storageos.common.http.RestAPIFactory;

/**
 * HDS HiCommand Device Manager XML API client factory
 */
public class HDSApiFactory extends RestAPIFactory<HDSApiClient>{

    @Override
    public HDSApiClient getRESTClient(URI endpoint) {
        return new HDSApiClient(endpoint, getRestClient());
    }

    @Override
    public HDSApiClient getRESTClient(URI endpoint, String username, String password) {
        return new HDSApiClient(endpoint, getRestClient(), username, password);
    }

}
