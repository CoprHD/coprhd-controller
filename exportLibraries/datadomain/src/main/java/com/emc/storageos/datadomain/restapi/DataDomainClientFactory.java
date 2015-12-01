/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.datadomain.restapi;

import java.net.URI;

import org.springframework.stereotype.Component;

import com.emc.storageos.common.http.RestAPIFactory;

/**
 * Created by zeldib on 1/30/14.
 */
@Component
public class DataDomainClientFactory extends RestAPIFactory<DataDomainClient> {

    @Override
    public DataDomainClient getRESTClient(URI endpoint) {
        return null;
    }

    @Override
    public DataDomainClient getRESTClient(URI endpoint, String username, String password) {
        return new DataDomainClient(endpoint, username, password, getRestClient());
    }

}
