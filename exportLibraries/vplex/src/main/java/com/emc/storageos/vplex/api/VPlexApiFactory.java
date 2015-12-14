/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplex.api;

import java.net.URI;

import com.emc.storageos.common.http.RestAPIFactory;

/**
 * VPlex API client factory
 */
public class VPlexApiFactory extends RestAPIFactory<VPlexApiClient> {

    @Override
    public VPlexApiClient getRESTClient(URI endpoint) {
        return null;
    }

    @Override
    public VPlexApiClient getRESTClient(URI endpoint, String username, String password) {
        _log.info("Creating new VPLEX client for the management server {}", endpoint);

        return new VPlexApiClient(endpoint, getRestClient(), username, password);
    }
}
