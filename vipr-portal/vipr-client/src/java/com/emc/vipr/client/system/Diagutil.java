/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.system;

import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.client.system.impl.PathConstants;
import com.emc.vipr.model.sys.diagutil.DiagutilInfo;
import com.emc.vipr.model.sys.diagutil.DiagutilParam;
import com.sun.jersey.api.client.ClientResponse;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;
import java.io.InputStream;

public class Diagutil {
    private RestClient client;

    public Diagutil(RestClient client) {
        this.client = client;
    }

    public void collect(DiagutilParam param) {
        client.post(String.class, param, PathConstants.DIAGUTIL_URL);
    }

    public DiagutilInfo getStatus() {
        UriBuilder builder = client.uriBuilder(PathConstants.DIAGUTIL_STATUS_URL);
        DiagutilInfo response = client.getURI(DiagutilInfo.class, builder.build());
        return response;
    }

    public InputStream getAsStream() {
        ClientResponse response = client.getClient().resource(PathConstants.DIAGUTIL_URL).accept(MediaType.APPLICATION_XML)
                .get(ClientResponse.class);
        return response.getEntityInputStream();
    }

    public InputStream getAsText() {
        ClientResponse response = client.getClient().resource(PathConstants.DIAGUTIL_URL).accept(MediaType.TEXT_PLAIN)
                .get(ClientResponse.class);
        return response.getEntityInputStream();
    }

    public void cancel() {
        client.delete(String.class, PathConstants.DIAGUTIL_URL);
    }

}
