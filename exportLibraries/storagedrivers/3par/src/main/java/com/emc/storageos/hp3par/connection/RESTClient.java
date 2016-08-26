/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.connection;

import java.net.URI;
import com.sun.jersey.api.client.*;
import javax.ws.rs.core.MediaType;

/*
 * REST communication with 3PAR storage device 
 */
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

    public ClientResponse post_json(URI url, String body) {
        WebResource r = _client.resource(url);
        return r.header("Content-Type", "application/json").post(ClientResponse.class, body);
    }

    public ClientResponse post_json(URI url, String authToken, String body) {
        WebResource r = _client.resource(url);
        return r.header("Content-Type", "application/json").header("X-HP3PAR-WSAPI-SessionKey", authToken)
                .post(ClientResponse.class, body);
    }
    
    public ClientResponse get_json(URI url, String authToken) {
        WebResource r = _client.resource(url);
        return r.header("Content-Type", "application/json").header("X-HP3PAR-WSAPI-SessionKey", authToken)
                .get(ClientResponse.class);
    }

    public ClientResponse put_json(URI url, String authToken, String body) {
        WebResource r = _client.resource(url);
        return r.header("Content-Type", "application/json").header("X-HP3PAR-WSAPI-SessionKey", authToken)
                .put(ClientResponse.class, body);
    }

    public ClientResponse delete_json(URI url, String authToken) {
        WebResource r = _client.resource(url);
        return r.header("Content-Type", "application/json").header("X-HP3PAR-WSAPI-SessionKey", authToken)
                .delete(ClientResponse.class);
    }

    /**
     * Close the client
     */
    public void close() {
        _client.destroy();
    }
}
