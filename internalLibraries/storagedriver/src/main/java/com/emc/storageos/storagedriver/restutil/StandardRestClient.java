/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.storagedriver.restutil;

import static com.google.json.JsonSanitizer.sanitize;

import java.net.URI;

import javax.ws.rs.core.MediaType;

import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

public abstract class StandardRestClient {
    protected Client _client;
    protected String _username;
    protected String _password;
    protected String _authToken;
    protected URI _base;
    private static Logger log = LoggerFactory.getLogger(StandardRestClient.class);

    /**
     * GET the resource at the passed URI.
     * 
     * @param uri The unique resource URI.
     * 
     * @return A ClientResponse reference.
     */
    public ClientResponse get(URI uri) {
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

    /**
     * PUT to the resource at the passed URI.
     * 
     * @param uri The unique resource URI.
     * @param body The PUT data.
     * 
     * @return A ClientResponse reference.
     */
    public ClientResponse put(URI uri, String body) {
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

    /**
     * POST to the resource at the passed URI.
     * 
     * @param uri The unique resource URI.
     * @param body The POST data.
     * 
     * @return A ClientResponse reference.
     */
    public ClientResponse post(URI uri, String body) {
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

    /**
     * DELETE to the resource at the passed URI.
     * 
     * @param uri The unique resource URI.
     * 
     * @return A ClientResponse reference.
     */
    public ClientResponse delete(URI uri) {
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

    /**
     * DELETE to the resource at the passed URI.
     * 
     * @param uri The unique resource URI.
     * @param body The POST data.
     * 
     * @return A ClientResponse reference.
     */
    public ClientResponse delete(URI uri, String body) {
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

    /**
     * Close the client
     */
    public void close() {
        _client.destroy();

    }

    private boolean authenticationFailed(ClientResponse response) {
        return response.getClientResponseStatus() == com.sun.jersey.api.client.ClientResponse.Status.UNAUTHORIZED;
    }

    protected <T> T getResponseObject(Class<T> clazz, ClientResponse response) throws Exception {
        JSONObject resp = response.getEntity(JSONObject.class);
        T respObject = new Gson().fromJson(sanitize(resp.toString()), clazz);
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
