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
import com.emc.storageos.vmax.VMAXConstants;
import com.emc.storageos.vmax.restapi.model.response.NDMMigrationEnvironmentResponse;
import com.emc.storageos.xtremio.restapi.XtremIOConstants;
import com.emc.storageos.xtremio.restapi.errorhandling.XtremIOApiException;
import com.emc.storageos.xtremio.restapi.model.XtremIOAuthInfo;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.WebResource.Builder;

public class VMAXApiClient extends StandardRestClient{
	private static Logger log = LoggerFactory.getLogger(VMAXApiClient.class);

    private URI baseURI;

    public VMAXApiClient(URI baseURI, String username, String password, Client client) {
        _client = client;
        _base = baseURI;
        _username = username;
        _password = password;
        _authToken = "";
    }
    
    @Override
    protected Builder setResourceHeaders(WebResource resource) {
    	return resource.header(XtremIOConstants.AUTH_TOKEN, _authToken);
    }



	@Override
	protected void authenticate() {
        ClientResponse response = null;
        try {
            XtremIOAuthInfo authInfo = new XtremIOAuthInfo();
            authInfo.setPassword(_password);
            authInfo.setUsername(_username);

            String body = getJsonForEntity(authInfo);

            URI requestURI = _base.resolve(URI.create(VMAXConstants.UNIVMAX_BASE_URI));
            response = _client.resource(requestURI).type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class, body);

            if (response.getClientResponseStatus() != ClientResponse.Status.OK
                    && response.getClientResponseStatus() != ClientResponse.Status.CREATED) {
                throw XtremIOApiException.exceptions.authenticationFailure(_base.toString());
            }
            _authToken = response.getHeaders().getFirst(XtremIOConstants.AUTH_TOKEN_HEADER);
        } catch (Exception e) {
            throw XtremIOApiException.exceptions.authenticationFailure(_base.toString());
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
            int xtremIOCode = 0;
            try {
                obj = response.getEntity(JSONObject.class);
                xtremIOCode = obj.getInt(XtremIOConstants.ERROR_CODE);
            } catch (Exception e) {
                extraExceptionInfo = e.getMessage();
                log.error("Parsing the failure response object failed", e);
            }

            if (xtremIOCode == 404 || xtremIOCode == 410) {
                throw XtremIOApiException.exceptions.resourceNotFound(uri.toString());
            } else if (xtremIOCode == 401) {
                throw XtremIOApiException.exceptions.authenticationFailure(uri.toString());
            } else {
                // Sometimes the response object can be null, just set it to empty when it is null.
                String objStr = (obj == null) ? "" : obj.toString();
                // Append extra exception info if present
                if (extraExceptionInfo != null) {
                    objStr = String.format("%s%s", 
                            (objStr.isEmpty()) ? objStr : objStr + " | ", extraExceptionInfo);
                }
                throw XtremIOApiException.exceptions.internalError(uri.toString(), objStr);
            }
        } else {
            return errorCode;
        }
    }
    
    
    
    public NDMMigrationEnvironmentResponse getMigrationEnvironment(String sourceArraySerialNumber, String targetArraySerialNumber) throws Exception{
    	ClientResponse clientResponse = get(URI.create(VMAXConstants.VALIDATE_ENVIRONMENT_URI));
    	NDMMigrationEnvironmentResponse environmentResponse = getResponseObject(NDMMigrationEnvironmentResponse.class, clientResponse);
    	return environmentResponse;
    }



	
}
