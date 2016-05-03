package com.emc.storageos.hp3par.impl;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.hp3par.connection.RESTClient;
import com.sun.jersey.api.client.ClientResponse;


public class HP3PARApi {
    private final URI _baseUrl;
    private final RESTClient _client;
    private Logger _log = LoggerFactory.getLogger(HP3PARApi.class);


    public HP3PARApi(URI endpoint, RESTClient client) {
        _baseUrl = endpoint;
        _client = client;
    }

    /**
     * Close client resources
     */
    public void close() {
        _client.close();
    }
    
    public String getAuthToken() {
        ///////////////////To be updated
        _log.info("ONEApi:getAuthToken enter");
        List<String> authTokenList = null;
        ClientResponse clientResp = null;
        String body= "{\"user\":\"superme\", \"password\":\"superme\"}";

        clientResp = _client.post_json(_baseUrl.resolve("/api/v1/credentials"), body);
        if (clientResp.getStatus() != 201) {
            _log.info("11111111");
            return null;
        }

        String responseString = clientResp.getEntity(String.class);
        return responseString;

    }
}
