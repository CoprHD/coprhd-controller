package com.emc.storageos.hp3par.impl;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.codehaus.jettison.json.JSONObject;

import com.emc.storageos.hp3par.command.SystemCommandResult;
import com.emc.storageos.hp3par.connection.RESTClient;
import com.google.gson.Gson;
import com.google.json.JsonSanitizer;
import com.sun.jersey.api.client.ClientResponse;

import static com.google.json.JsonSanitizer.*;


public class HP3PARApi {
    private final URI _baseUrl;
    private final RESTClient _client;
    private Logger _log = LoggerFactory.getLogger(HP3PARApi.class);
    private String _authToken;
    private String _user; 
    private String _password;

    private static final URI URI_LOGIN = URI.create("/api/v1/credentials");
    private static final String URI_SYSTEM = "/api/v1/system";

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
                throw new HP3PARException("There is no response from 3PAR");
            } else if (clientResp.getStatus() != 201) {
                String errResp = getResponseDetails(clientResp);
                throw new HP3PARException(errResp);
            } else {
                JSONObject jObj = clientResp.getEntity(JSONObject.class);
                authToken = jObj.getString("key");
            }
            this._authToken = authToken;
            this._user = user;
            this._password = password;
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

    public String getAuthToken() throws Exception {
        _log.info("HP3PARApi:getAuthToken enter, after expiry");
        String authToken = null;
        ClientResponse clientResp = null;
        String body= "{\"user\":\"" + _user + "\", \"password\":\"" + _password + "\"}";

        try {
            clientResp = _client.post_json(_baseUrl.resolve(URI_LOGIN), body);
            if (clientResp == null) {
                _log.error("There is no response from 3PAR");
                throw new HP3PARException("There is no response from 3PAR");
            } else if (clientResp.getStatus() != 201) {
                String errResp = getResponseDetails(clientResp);
                throw new HP3PARException(errResp);
            } else {
                JSONObject jObj = clientResp.getEntity(JSONObject.class);
                authToken = jObj.getString("key");
            }
            this._authToken = authToken;
            return authToken;
        } catch (Exception e) {
            throw e;
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.info("HP3PARApi:getAuthToken leave, after expiry");
        } //end try/catch/finally
    }

    public SystemCommandResult getSystemDetails() throws Exception {
        _log.info("HP3PARApi:getSystemDetails enter");
        ClientResponse clientResp = null;

        try {
            clientResp = get(URI_SYSTEM);
            if (clientResp == null) {
                _log.error("There is no response from 3PAR");
                throw new HP3PARException("There is no response from 3PAR");
            } else if (clientResp.getStatus() != 200) {
                String errResp = getResponseDetails(clientResp);
                throw new HP3PARException(errResp);
            } else {
                String responseString = clientResp.getEntity(String.class);
                _log.info("HP3PARApi:getSystemDetails 3PAR response is {}", responseString);
                SystemCommandResult systemRes = new Gson().fromJson(sanitize(responseString),
                        SystemCommandResult.class);
                return systemRes;
            }
        } catch (Exception e) {
            throw e;
        } finally {
            if (clientResp != null) {
                clientResp.close();
            }
            _log.info("HP3PARApi:getSystemDetails leave");
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
    
    private ClientResponse get(final String uri) throws Exception {
        ClientResponse clientResp = _client.get_json(_baseUrl.resolve(uri), _authToken);
        if (clientResp.getStatus() == 403) {
            getAuthToken();
            clientResp = _client.get_json(_baseUrl.resolve(uri), _authToken);
        }
        return clientResp;
    }
}

