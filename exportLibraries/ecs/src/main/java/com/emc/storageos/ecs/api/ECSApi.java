package com.emc.storageos.ecs.api;

import java.net.URI;

public class ECSApi {
    private final URI _baseUrl;
    private final RESTClient _client;

    /**
     * Constructor for using http connections
     * 
     * @throws IsilonException
     */
    public ECSApi(URI endpoint, RESTClient client) {
        _baseUrl = endpoint;
        _client = client;
    }

    /**
     * Close client resources
     */
    public void close() {
        _client.close();
    }

}
