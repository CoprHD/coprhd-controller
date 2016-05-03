/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.connection;

import java.net.URI;
import com.sun.jersey.api.client.*;
import javax.ws.rs.core.MediaType;

public class RESTClient {
    private Client _client;

    /**
     * Constructor
     *
     * @param client Jersey client to use
     */
    public RESTClient(Client client) {
        _client = client;
    }

    /**
     * Post resource at url
     * 
     * @param url url to post to
     * @param body content to post
     * @return ClientResponse response
     */
    public ClientResponse post_json(URI url, String body) {
        WebResource r = _client.resource(url);
        return r.header("Content-Type", MediaType.APPLICATION_JSON).post(ClientResponse.class, body);
    }

    /**
     * Close the client
     */
    public void close() {
        _client.destroy();
    }

}
