package com.emc.storageos.driver.driversimulator;

import com.sun.jersey.api.client.*;
import com.sun.jersey.api.client.config.ClientConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.concurrent.Future;

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
     * Get resource as a ClientResponse
     *
     * @param url url for the resource to get
     * @return ClientResponse response
     */
    public ClientResponse get(URI url) {
        return _client.resource(url).get(ClientResponse.class);
    }

    public ClientResponse get_json(URI url, String authToken) {
        WebResource r = _client.resource(url);
        return r.header("Content-Type", "application/json").header("X-SDS-AUTH-TOKEN", authToken).get(ClientResponse.class);
    }

    public ClientResponse get_xml(URI url, String authToken) {
        WebResource r = _client.resource(url);
        return r.header("Content-Type", "application/xml").header("X-SDS-AUTH-TOKEN", authToken).get(ClientResponse.class);
    }

    public ClientResponse post_json(URI url, String body) {
        WebResource r = _client.resource(url);
        return r.header("Content-Type", "application/json").post(ClientResponse.class, body);
    }

    public ClientResponse put_json(URI url, String authToken, String body) {
        WebResource r = _client.resource(url);
        return r.header("Content-Type", "application/json").header("X-SDS-AUTH-TOKEN", authToken).put(ClientResponse.class, body);
    }

    /**
     * Close the client
     */
    public void close() {
        _client.destroy();
    }

}

