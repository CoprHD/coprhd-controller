/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.services.restutil.RestClientFactory;
import com.emc.storageos.services.restutil.RestClientItf;
import com.emc.storageos.xtremio.restapi.errorhandling.XtremIOApiException;
import com.sun.jersey.api.client.Client;

public class XtremIOClientFactory extends RestClientFactory {

    private Logger log = LoggerFactory.getLogger(XtremIOClientFactory.class);

    private static final String DOT_OPERATOR = "\\.";
    private static final Integer XIO_MIN_4X_VERSION = 4;

    @Override
    protected RestClientItf createNewRestClient(URI endpoint, String username,
            String password, Client client) {
        // We will first create a version 2 client and check for version 2 api. If yes, return the version api client.
        // Otherwise create a version 1 client and return it.
        XtremIOClient version2Client = new XtremIOV2Client(endpoint, username, password, client);
        if (version2Client.isVersion2()) {
            return version2Client;
        } else {
            version2Client = null;
            return new XtremIOV1Client(endpoint, username, password, client);
        }
    }

    @Override
    protected RestClientItf createNewRestClient(URI endpoint, String username,
            String password, Client client, String model) {
        // We will first create a version 2 client and check for version 2 api. If yes, return the version api client.
        // Otherwise create a version 1 client and return it.
        XtremIOClient version2Client = new XtremIOV2Client(endpoint, username, password, client);
        if (version2Client.isVersion2()) {
            return version2Client;
        } else if (null != model && Integer.valueOf(model.split(DOT_OPERATOR)[0]) > XIO_MIN_4X_VERSION) {
            log.error("Not able to get the v2 client for xio system model {}", model);
            throw XtremIOApiException.exceptions.noConnectionFound(endpoint.toString());
        } else {
            version2Client = null;
            return new XtremIOV1Client(endpoint, username, password, client);
        }
    }

}
