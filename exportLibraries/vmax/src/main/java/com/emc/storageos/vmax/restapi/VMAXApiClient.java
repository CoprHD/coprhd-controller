/*
 * Copyright (c) 2017 DELL EMC
 * All Rights Reserved
 */
package com.emc.storageos.vmax.restapi;

import java.net.URI;

import javax.ws.rs.core.MediaType;

import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.services.restutil.StandardRestClient;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.vmax.VMAXConstants;
import com.emc.storageos.vmax.restapi.errorhandling.VMAXException;
import com.emc.storageos.vmax.restapi.model.VMAXAuthInfo;
import com.emc.storageos.vmax.restapi.model.response.migration.MigrationEnvironmentResponse;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.WebResource.Builder;

public class VMAXApiClient extends StandardRestClient {
    private static Logger log = LoggerFactory.getLogger(VMAXApiClient.class);

    public VMAXApiClient(URI baseURI, String username, String password, Client client) {
        _client = client;
        _base = baseURI;
        _username = username;
        _password = password;
        _authToken = "";
    }

    @Override
    protected Builder setResourceHeaders(WebResource resource) {
        return resource.header(VMAXConstants.AUTH_TOKEN, _authToken);
    }

    @Override
    protected void authenticate() {
        ClientResponse response = null;
        try {
            VMAXAuthInfo authInfo = new VMAXAuthInfo();
            authInfo.setPassword(_password);
            authInfo.setUsername(_username);

            String body = getJsonForEntity(authInfo);

            URI requestURI = _base.resolve(URI.create(VMAXConstants.UNIVMAX_BASE_URI));
            response = _client.resource(requestURI).type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class, body);

            if (response.getClientResponseStatus() != ClientResponse.Status.OK
                    && response.getClientResponseStatus() != ClientResponse.Status.CREATED) {
                throw VMAXException.exceptions.authenticationFailure(_base.toString());
            }
            _authToken = response.getHeaders().getFirst(VMAXConstants.AUTH_TOKEN_HEADER);
        } catch (Exception e) {
            throw VMAXException.exceptions.authenticationFailure(_base.toString());
        } finally {
            closeResponse(response);
        }
    }

    @Override
    protected int checkResponse(URI uri, ClientResponse response) {
        ClientResponse.Status status = response.getClientResponseStatus();
        int errorCode = status.getStatusCode();
        if (errorCode >= 300) {
            JSONObject obj = null;
            String extraExceptionInfo = null;
            /*
             * try {
             * obj = response.getEntity(JSONObject.class);
             * code = obj.getInt(XtremIOConstants.ERROR_CODE);
             * } catch (Exception e) {
             * extraExceptionInfo = e.getMessage();
             * log.error("Parsing the failure response object failed", e);
             * }
             */

            if (errorCode == 404 || errorCode == 410) {
                throw VMAXException.exceptions.resourceNotFound(uri.toString());
            } else if (errorCode == 401) {
                throw VMAXException.exceptions.authenticationFailure(uri.toString());
            } else {
                // Sometimes the response object can be null, just set it to empty when it is null.
                String objStr = (obj == null) ? "" : obj.toString();
                // Append extra exception info if present
                if (extraExceptionInfo != null) {
                    objStr = String.format("%s%s",
                            (objStr.isEmpty()) ? objStr : objStr + " | ", extraExceptionInfo);
                }
                throw VMAXException.exceptions.internalError(uri.toString(), objStr);
            }
        } else {
            return errorCode;
        }
    }

    @Override
    public ClientResponse post(URI uri, String body) throws InternalException {
        ClientResponse response = null;
        log.info(String.format("Calling POST %s with data %s", uri.toString(), body));
        response = super.post(uri, body);
        return response;
    }

    @Override
    public ClientResponse get(URI uri) throws InternalException {
        ClientResponse response = null;
        log.info("Calling GET {}", uri.toString());
        response = super.get(uri);
        return response;
    }

    /**
     * Wrapper of post method to ignore the response
     *
     * @param uri URI
     * @param body request body string
     * @return null
     * @throws InternalException
     */
    public ClientResponse postIgnoreResponse(URI uri, String body) throws InternalException {
        ClientResponse response = null;
        try {
            log.info(String.format("Calling POST %s with data %s", uri.toString(), body));
            response = super.post(uri, body);
        } finally {
            closeResponse(response);
        }
        return null;
    }

    @Override
    public ClientResponse put(URI uri, String body) throws InternalException {
        ClientResponse response = null;
        try {
            log.info(String.format("Calling PUT %s with data %s", uri.toString(), body));
            response = super.put(uri, body);
        } finally {
            closeResponse(response);
        }
        return null;
    }

    @Override
    public ClientResponse delete(URI uri) throws InternalException {
        ClientResponse response = null;
        try {
            log.info("Calling DELETE {}", uri.toString());
            response = super.delete(uri);
        } finally {
            closeResponse(response);
        }
        return null;
    }

    @Override
    public ClientResponse delete(URI uri, String body) throws InternalException {
        ClientResponse response = null;
        try {
            log.info(String.format("Calling DELETE %s with data %s", uri.toString(), body));
            response = super.delete(uri, body);
        } finally {
            closeResponse(response);
        }
        return null;
    }

    public MigrationEnvironmentResponse getMigrationEnvironment(String sourceArraySerialNumber, String targetArraySerialNumber)
            throws Exception {
        ClientResponse clientResponse = get(
                URI.create(VMAXConstants.getValidateEnvironmentURI(sourceArraySerialNumber, targetArraySerialNumber)));
        MigrationEnvironmentResponse environmentResponse = getResponseObject(MigrationEnvironmentResponse.class, clientResponse);
        return environmentResponse;
    }

}
