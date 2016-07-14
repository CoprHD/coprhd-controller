/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.security.helpers;

import com.emc.storageos.keystone.KeystoneConstants;
import com.emc.storageos.services.restutil.StandardRestClient;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class SamlIdpApiClient extends StandardRestClient {
    private static Logger log = LoggerFactory.getLogger(SamlIdpApiClient.class);

    @Override
    protected WebResource.Builder setResourceHeaders(WebResource resource) {
        return resource.getRequestBuilder();
    }

    @Override
    protected void authenticate() {

    }

    @Override
    protected int checkResponse(URI uri, ClientResponse response) {
        log.debug("START - checkresponse");
        ClientResponse.Status status = response.getClientResponseStatus();
        int responseCode = 0;

        if (status != ClientResponse.Status.OK
                && status != ClientResponse.Status.ACCEPTED
                && status != ClientResponse.Status.CREATED) {

        } else {
            responseCode = status.getStatusCode();
        }

        log.info("The response code is - " + String.valueOf(responseCode));
        log.debug("END - checkresponse");
        return responseCode;
    }
}
