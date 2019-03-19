/*
 * Copyright (c) 2018 EMC Corporation
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

/**
 * 
 * Class for Diagutil
 *
 */
public class Diagutil {
    private RestClient client;
    private static final String OPTIONS = "options";
    private static final String NODE_ID = "node_id";
    private static final String FILE_NAME = "file_name";

    /**
     * Constructor for Diagutil.
     * 
     * @param client
     *         RestClient object.
     */
    public Diagutil(RestClient client) {
        this.client = client;
    }

    /**
     * Collects the diagutil status.
     * 
     * @param options
     *         List of options.
     * @param param
     *         Object of DiagutilParam.
     */
    public void collect(List<String> options, DiagutilParam param) {
        UriBuilder builder = client.uriBuilder(PathConstants.DIAGUTIL_URL);
        if(options != null) {
            addQueryParam(builder, OPTIONS, options);
        }
        client.postURI(String.class, param, builder.build());
    }

    /**
     * Gets the diagutil Status
     * 
     * @return response
     *           DiagutilInfo object
     *    
     */
    public DiagutilInfo getStatus() {
        UriBuilder builder = client.uriBuilder(PathConstants.DIAGUTIL_STATUS_URL);
        DiagutilInfo response = client.getURI(DiagutilInfo.class, builder.build());
        return response;
    }

    /**
     * Returns an object of InputStream
     * 
     * @param nodeId
     *         ViPR node ID.
     * @param fileName
     *         The file name.
     * @return the input stream of the response.
     *         
     */
    public InputStream getAsStream(String nodeId, String fileName) {
        UriBuilder builder = client.uriBuilder(PathConstants.DIAGUTIL_URL);
        if(nodeId != null && (!nodeId.isEmpty())) {
            addQueryParam(builder, NODE_ID, nodeId);
        }
        if(fileName != null && (!fileName.isEmpty())) {
            addQueryParam(builder, FILE_NAME, fileName);
        }
        ClientResponse response = client.getClient().resource(builder.build()).accept(MediaType.APPLICATION_OCTET_STREAM)
                .get(ClientResponse.class);
        return response.getEntityInputStream();
    }

    /**
     * Cancels the diagutils Job
     */
    public void cancel() {
        client.delete(String.class, PathConstants.DIAGUTIL_URL);
    }

}
