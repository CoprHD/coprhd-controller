/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.net.URI;

import com.emc.storageos.model.dr.SiteConfigParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.security.helpers.BaseServiceClient;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;

/**
 * Internal API for communication among sites (within one specific VDC)
 */
public class InternalSiteServiceClient extends BaseServiceClient {

    private static final String INTERNAL_SITE_ROOT = "/site/internal";
    private static final String INTERNAL_SITE_INIT_STANDBY = INTERNAL_SITE_ROOT + "/initstandby";

    final private Logger _log = LoggerFactory
            .getLogger(InternalSiteServiceClient.class);

    /**
     * Client without target hosts
     */
    public InternalSiteServiceClient() {
    }

    /**
     * Client with specific host
     *
     * @param server
     */
    public InternalSiteServiceClient(String server) {
        setServer(server);
    }

    /**
     * Make client associated with this api server host (IP)
     * 
     * @param server IP
     */
    @Override
    public void setServer(String server) {
        setServiceURI(URI.create("https://" + server + ":4443"));
    }

    /**
     * Initialize a to-be resumed target standby
     * 
     * @param configParam the sites configuration
     * @return
     */
    public ClientResponse initStandby(SiteConfigParam configParam) {
        WebResource rRoot = createRequest(INTERNAL_SITE_INIT_STANDBY);
        ClientResponse resp = null;
        try {
            resp = addSignature(rRoot)
                    .put(ClientResponse.class, configParam);
        } catch (UniformInterfaceException e) {
            _log.warn("could not initialize target standby site. Err:{}", e);
        }
        return resp;
    }
}
