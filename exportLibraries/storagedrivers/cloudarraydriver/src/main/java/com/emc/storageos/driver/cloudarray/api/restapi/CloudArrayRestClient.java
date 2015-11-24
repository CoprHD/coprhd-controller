/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.cloudarray.api.restapi;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.cloudarray.CloudArrayException;
import com.emc.storageos.driver.cloudarray.api.restapi.response.CloudArrayCache;
import com.emc.storageos.services.restutil.StandardRestClient;
import com.google.gson.Gson;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.core.util.MultivaluedMapImpl;

public class CloudArrayRestClient extends StandardRestClient {

    private static Logger log = LoggerFactory.getLogger(CloudArrayRestClient.class);

    public CloudArrayRestClient(URI endpoint, String username, String password, Client client) {
        _client = client;
        _base = endpoint;
        _username = username;
        _password = password;
        _authToken = "";
    }

    public void setUsername(String username) {
        _username = username;
    }

    public void setPassword(String password) {
        _password = password;
    }

    @Override
    protected WebResource.Builder setResourceHeaders(WebResource resource) {
        return resource.getRequestBuilder();
    }

    @Override
    protected void authenticate() {
        URI requestURI = _base.resolve(URI.create(CloudArrayConstants.API_LOGIN));
        MultivaluedMap<String, String> credentials = new MultivaluedMapImpl();
        credentials.add("username", _username);
        credentials.add("password", _password);
        ClientResponse response = _client.resource(requestURI).type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).post(ClientResponse.class,
                credentials);

        if (response.getClientResponseStatus() != ClientResponse.Status.OK) {
            throw CloudArrayException.exceptions.authenticationFailure(_base.toString());
        }
    }

    @Override
    protected int checkResponse(URI uri, ClientResponse response) {
        ClientResponse.Status status = response.getClientResponseStatus();
        int errorCode = status.getStatusCode();
        if (errorCode >= 300) {
            JSONObject obj = null;
            int code = 0;
            try {
                obj = response.getEntity(JSONObject.class);
                code = obj.getInt(CloudArrayConstants.ERROR_CODE);
            } catch (Exception e) {
                log.error("Parsing the failure response object failed", e);
            }

            if (code == 404 || code == 410) {
                throw CloudArrayException.exceptions.resourceNotFound(uri.toString());
            } else if (code == 401) {
                throw CloudArrayException.exceptions.authenticationFailure(uri.toString());
            } else {
                throw CloudArrayException.exceptions.internalError(uri.toString(), obj.toString());
            }
        }
        return errorCode;
    }

    public List<CloudArrayCache> getCloudArrayCaches() throws Exception {
        ClientResponse response = get(URI.create(CloudArrayConstants.GET_CACHES));
        return getResponseObjects(CloudArrayCache.class, response);
    }

    private <T> List<T> getResponseObjects(Class<T> clazz, ClientResponse response) throws JSONException {
        JSONArray resp = response.getEntity(JSONArray.class);
        List<T> result = null;
        if (resp != null && resp.length() > 0) {
            result = new ArrayList<T>();
            for (int i = 0; i < resp.length(); i++) {
                JSONObject entry = resp.getJSONObject(i);
                T respObject = new Gson().fromJson(entry.toString(), clazz);
                result.add(respObject);
            }
        }
        return result;
    }
}
