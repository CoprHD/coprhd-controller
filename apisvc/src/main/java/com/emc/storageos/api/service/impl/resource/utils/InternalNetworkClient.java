/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.model.varray.NetworkEndpointParam;
import com.emc.storageos.model.varray.NetworkRestRep;
import com.emc.storageos.security.helpers.BaseServiceClient;
import com.sun.jersey.api.client.WebResource;

/*
 * Client for connecting to the internal network service
 */
public class InternalNetworkClient extends BaseServiceClient {
    private static final Logger _log = LoggerFactory.getLogger(InternalNetworkClient.class);

    private static final String INTERNAL_TRANSPORTZONE_ROOT = "/internal/vdc/networks/";
    private static final String ENDPOINTS = "/endpoints";
    private static final String PROTOCOL = "https://";
    private static final String PORT = ":8443";

    @Override
    public void setServer(String server) {
        setServiceURI(URI.create(PROTOCOL + server + PORT));
    }

    /***
     * Method for updating the network's endpoints
     * @param id  the URN of a ViPR network
     * @param param  The ips to add or remove, and whether to add or remove
     * @return network info
     */
    public NetworkRestRep updateNetworkEndpoints(URI id, NetworkEndpointParam param) {
        WebResource rRoot = createRequest(INTERNAL_TRANSPORTZONE_ROOT + id + ENDPOINTS);
        NetworkRestRep resp = addSignature(rRoot)
                .post(NetworkRestRep.class, param);
        return resp;
    }
}
