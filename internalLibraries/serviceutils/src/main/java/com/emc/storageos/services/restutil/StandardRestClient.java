/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.services.restutil;

import java.net.URI;

import javax.ws.rs.core.MediaType;

import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.services.util.SecurityUtils;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.google.gson.Gson;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

public abstract class StandardRestClient implements RestClientItf {
    protected Client _client;
    protected String _username;
    protected String _password;
    protected String _authToken;
    protected URI _base;
    private static Logger log = LoggerFactory.getLogger(StandardRestClient.class);

    @Override
    public ClientResponse get(URI uri) throws InternalException {
        URI requestURI = _base.resolve(uri);
        ClientResponse response = setResourceHeaders(_client.resource(requestURI)).type(MediaType.APPLICATION_JSON).get(
                ClientResponse.class);
        if (authenticationFailed(response)) {
            closeResponse(response);
            authenticate();
            response = setResourceHeaders(_client.resource(requestURI)).type(MediaType.APPLICATION_JSON).get(ClientResponse.class);
        }
        checkResponse(uri, response);
        return response;
    }

    @Override
    public ClientResponse put(URI uri, String body) throws InternalException {
        URI requestURI = _base.resolve(uri);
        ClientResponse response = setResourceHeaders(_client.resource(requestURI)).type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, body);
        if (authenticationFailed(response)) {
            closeResponse(response);
            authenticate();
            response = setResourceHeaders(_client.resource(requestURI)).type(MediaType.APPLICATION_JSON)
                    .put(ClientResponse.class, body);
        }
        checkResponse(uri, response);
        return response;
    }

    @Override
    public ClientResponse post(URI uri, String body) throws InternalException {
        URI requestURI = _base.resolve(uri);
        ClientResponse response = setResourceHeaders(_client.resource(requestURI)).type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, body);
        if (authenticationFailed(response)) {
            closeResponse(response);
            authenticate();
            response = setResourceHeaders(_client.resource(requestURI)).type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class, body);
        }
        checkResponse(uri, response);
        return response;
    }

    @Override
    public ClientResponse delete(URI uri) throws InternalException {
        URI requestURI = _base.resolve(uri);
        ClientResponse response = setResourceHeaders(_client.resource(requestURI)).type(MediaType.APPLICATION_JSON)
                .delete(ClientResponse.class);
        if (authenticationFailed(response)) {
            closeResponse(response);
            authenticate();
            response = setResourceHeaders(_client.resource(requestURI)).type(MediaType.APPLICATION_JSON)
                    .delete(ClientResponse.class);
        }
        checkResponse(uri, response);
        return response;
    }

    public ClientResponse delete(URI uri, String body) throws InternalException {
        URI requestURI = _base.resolve(uri);
        ClientResponse response = setResourceHeaders(_client.resource(requestURI)).type(MediaType.APPLICATION_JSON)
                .delete(ClientResponse.class, body);
        if (authenticationFailed(response)) {
            closeResponse(response);
            authenticate();
            response = setResourceHeaders(_client.resource(requestURI)).type(MediaType.APPLICATION_JSON)
                    .delete(ClientResponse.class);
        }
        checkResponse(uri, response);
        return response;
    }

    /**
     * Close the entity input stream
     *
     * @param clientRespopnse ClientResponse to be closed
     */
    public void closeResponse(ClientResponse clientResp) {
        if (clientResp != null) {
            clientResp.close();
        }
    }

    @Override
    public void close() throws InternalException {
        _client.destroy();

    }

    private boolean authenticationFailed(ClientResponse response) {
        return response.getClientResponseStatus() == com.sun.jersey.api.client.ClientResponse.Status.UNAUTHORIZED;
    }

    protected <T> T getResponseObject(Class<T> clazz, ClientResponse response) throws Exception {
        JSONObject resp = response.getEntity(JSONObject.class);
        T respObject = new Gson().fromJson(SecurityUtils.sanitizeJsonString(resp.toString()), clazz);
        return respObject;
    }

    protected <T> String getJsonForEntity(T model) throws Exception {
        return new Gson().toJson(model);
    }

    abstract protected WebResource.Builder setResourceHeaders(WebResource resource);

    abstract protected void authenticate();

    protected void authenticate1() {

    }

    abstract protected int checkResponse(URI uri, ClientResponse response);

}
