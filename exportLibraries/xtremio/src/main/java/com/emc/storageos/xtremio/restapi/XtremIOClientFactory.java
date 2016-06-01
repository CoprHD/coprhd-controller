/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi;

import java.net.URI;

import com.emc.storageos.services.restutil.RestClientFactory;
import com.emc.storageos.services.restutil.RestClientItf;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.client.apache.ApacheHttpClient;

public class XtremIOClientFactory extends RestClientFactory {

    private static final String DOT_OPERATOR = "\\.";
    private static final Integer XIO_MIN_4X_VERSION = 4;

    @Override
    protected RestClientItf createNewRestClient(URI endpoint, String username,
            String password, Client client) {
        return null;
    }

    protected RestClientItf createNewRestClient(URI endpoint, String username,
            String password, String version, Client client) {
        XtremIOClient xioClient = null;
        // Based on the storagesystem/provider version, create v2 or v1 client.
        if (version != null && !version.isEmpty() && Integer.valueOf(version.split(DOT_OPERATOR)[0]) >= XIO_MIN_4X_VERSION) {
            xioClient = new XtremIOV2Client(endpoint, username, password, client);
        } else {
            xioClient = new XtremIOV1Client(endpoint, username, password, client);
        }

        return xioClient;
    }

    public RestClientItf getRESTClient(URI endpoint, String username, String password, String version, boolean authFilter) {
        // removed caching RestClient session as it is not actually a session, just a java RestClient object
        // RestClientItf clientApi = _clientMap.get(endpoint.toString() + ":" + username + ":" + password + ":" + model);

        Client jerseyClient = new ApacheHttpClient(_clientHandler);
        if (authFilter) {
            jerseyClient.addFilter(new HTTPBasicAuthFilter(username, password));
        }
        RestClientItf clientApi = createNewRestClient(endpoint, username, password, version, jerseyClient);
        return clientApi;
    }

    public RestClientItf getXtremIOV1Client(URI endpoint, String username,
            String password, boolean authFilter) {
        Client jerseyClient = super.getBaseClient(endpoint, username, password, authFilter);
        return new XtremIOV1Client(endpoint, username, password, jerseyClient);
    }
}
