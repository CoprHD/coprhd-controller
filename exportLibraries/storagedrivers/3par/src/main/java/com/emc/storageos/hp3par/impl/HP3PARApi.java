package com.emc.storageos.hp3par.impl;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.codehaus.jettison.json.JSONObject;

import com.emc.storageos.hp3par.connection.RESTClient;
import com.sun.jersey.api.client.ClientResponse;


public class HP3PARApi {
    private final URI _baseUrl;
    private final RESTClient _client;
    private Logger _log = LoggerFactory.getLogger(HP3PARApi.class);

    private static final URI URI_LOGIN = URI.create("/api/v1/credentials");

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
    
    public String getAuthToken(String user, String password) throws Exception {
        _log.info("HP3PARApi:getAuthToken enter");
        String authToken = null;
        ClientResponse clientResp = null;
        String body= "{\"user\":\"" + user + "\", \"password\":\"" + password + "\"}";

        try {
            clientResp = _client.post_json(_baseUrl.resolve(URI_LOGIN), body);
            if (clientResp == null) {
                _log.error("There is no response from 3PAR");
            } else if (clientResp.getStatus() != 201) {
                String errResp = getResponseDetails(clientResp);
                throw new HP3PARException(errResp);
            } else {
                JSONObject jObj = clientResp.getEntity(JSONObject.class);
                authToken = jObj.getString("key");
            }
            return authToken;
        } catch (Exception e) {
            throw e;
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.info("HP3PARApi:getAuthToken leave");
        } //end try/catch/finally
    }
    
    private String getResponseDetails(ClientResponse clientResp) {
        String detailedResponse = null, ref=null;;
        try {
            JSONObject jObj = clientResp.getEntity(JSONObject.class);
            detailedResponse = String.format("3PAR error code: %s, Description: %s",
                    jObj.getString("code"), jObj.getString("desc"));
            if (jObj.has("ref")) {
                ref = String.format(", refer:%s", jObj.getString("ref"));
                detailedResponse = detailedResponse + ref;
            }
            _log.error(String.format("HTTP error code: %d, Complete 3PAR error response: %s", clientResp.getStatus(),
                    jObj.toString()));
        } catch (Exception e) {
            _log.error("Unable to get 3PAR error details");
            detailedResponse = String.format("%1$s", (clientResp == null) ? "" : clientResp);
        }
        return detailedResponse;
    }
}
