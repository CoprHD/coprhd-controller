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
import static com.emc.vipr.client.impl.jersey.ClientUtils.addQueryParam;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;
import java.io.InputStream;
import java.util.List;

public class Diagutil {
    private RestClient client;
    private static final String OPTIONS = "options";
    private static final String NODE_ID = "node_id";
    private static final String FILE_NAME = "file_name";

    public Diagutil(RestClient client) {
        this.client = client;
    }

    public void collect(List<String> options, DiagutilParam param) {
        UriBuilder builder = client.uriBuilder(PathConstants.DIAGUTIL_URL);
        if(options != null) {
            addQueryParam(builder, OPTIONS, options);
        }
        client.postURI(String.class, param, builder.build());
    }

    public DiagutilInfo getStatus() {
        UriBuilder builder = client.uriBuilder(PathConstants.DIAGUTIL_STATUS_URL);
        DiagutilInfo response = client.getURI(DiagutilInfo.class, builder.build());
        return response;
    }

    public InputStream getAsStream(String nodeId, String fileName) {
        UriBuilder builder = client.uriBuilder(PathConstants.DIAGUTIL_URL);
        if(nodeId != null && (!nodeId.isEmpty())) {
            addQueryParam(builder, NODE_ID, nodeId);
        }
        if(fileName != null && (!fileName.isEmpty())) {
            addQueryParam(builder, FILE_NAME, fileName);
        }
        ClientResponse response = client.getClient().resource(PathConstants.DIAGUTIL_URL).accept(MediaType.APPLICATION_OCTET_STREAM)
                .get(ClientResponse.class);
        return response.getEntityInputStream();
    }

/*    public InputStream getAsText() {
        ClientResponse response = client.getClient().resource(PathConstants.DIAGUTIL_URL).accept(MediaType.TEXT_PLAIN)
                .get(ClientResponse.class);
        return response.getEntityInputStream();
    }*/

    public void cancel() {
        client.delete(String.class, PathConstants.DIAGUTIL_URL);
    }

}
