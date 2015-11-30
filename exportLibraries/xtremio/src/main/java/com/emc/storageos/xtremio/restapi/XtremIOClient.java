/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.common.http.RestClientItf;
import com.emc.storageos.services.util.SecurityUtils;
import com.emc.storageos.xtremio.restapi.errorhandling.XtremIOApiException;
import com.emc.storageos.xtremio.restapi.model.XtremIOAuthInfo;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOObjectInfo;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOXMSsInfo;
import com.google.gson.Gson;
import com.sun.jersey.api.client.ClientResponse;

public abstract class XtremIOClient implements XtremIODiscoveryClient, XtremIOProvisioningClient {

    private static Logger log = LoggerFactory.getLogger(XtremIOClient.class);

    protected RestClientItf client;
    
    private URI uri;
    
    private String username;
    
    private String password;
    
    protected String _authToken;
    
    public XtremIOClient(URI baseURI, String username, String password, RestClientItf client) {
        this.client = client;
        this.uri = baseURI;
        this.username = username;
        this.password = password;
        authenticate();
    }

    /**
     * Check whether the given XMS is running a version 2 REST API
     * 
     * @return
     */
    public boolean isVersion2() {
        boolean isV2 = false;
        Map<String, String> headers = getAuthHeader();
        try {
            ClientResponse response = client.get(XtremIOConstants.XTREMIO_V2_XMS_URI, headers);
            if(authenticationFailed(response)) {
                authenticate();
                response = client.get(XtremIOConstants.XTREMIO_V2_XMS_URI, headers);
            }
            XtremIOXMSsInfo xmssInfo = getResponseObject(XtremIOXMSsInfo.class, response);
            for (XtremIOObjectInfo xmsInfo : xmssInfo.getXmssInfo()) {
                URI xmsURI = URI.create(xmsInfo.getHref().concat(XtremIOConstants.XTREMIO_XMS_FILTER_STR));
                response = client.get(xmsURI, headers);
                if(authenticationFailed(response)) {
                    authenticate();
                    response = client.get(xmsURI, headers);
                }
                log.debug("Got response {} for url {}.", response.getClientResponseStatus(), xmsURI);
                if (response.getClientResponseStatus() != ClientResponse.Status.OK) {
                    isV2 = false;
                } else {
                    isV2 = true;
                }
            }
        } catch (Exception ex) {
            log.warn("Error retrieving xms version info", ex);
            isV2 = false;
        }

        return isV2;
    }

    protected Map<String, String> getAuthHeader() {
        Map<String, String> headers = new HashMap<>();
        headers.put(XtremIOConstants.AUTH_TOKEN_HEADER, _authToken);
        return headers;
    }
    
    protected Map<String, String> getAuthAndJsonHeader() {
        Map<String, String> headers = getAuthHeader();
        headers.put("Content-Type", "application/json");
        return headers;
    }

    protected int checkResponse(URI uri, ClientResponse response) throws XtremIOApiException {
        ClientResponse.Status status = response.getClientResponseStatus();
        int errorCode = status.getStatusCode();
        if (errorCode >= 300) {
            JSONObject obj = null;
            int xtremIOCode = 0;
            try {
                obj = response.getEntity(JSONObject.class);
                xtremIOCode = obj.getInt(XtremIOConstants.ERROR_CODE);
            } catch (Exception e) {
                log.error("Parsing the failure response object failed", e);
            }

            if (xtremIOCode == 404 || xtremIOCode == 410) {
                throw XtremIOApiException.exceptions.resourceNotFound(uri.toString());
            } else if (xtremIOCode == 401) {
                throw XtremIOApiException.exceptions.authenticationFailure(uri.toString());
            } else {
                // Sometimes the response object can be null, just empty when it is null.
                String objStr = (obj == null) ? "" : obj.toString();
                throw XtremIOApiException.exceptions.internalError(uri.toString(), objStr);
            }
        } else {
            return errorCode;
        }
    }

    protected void authenticate() throws XtremIOApiException {
        try {
            XtremIOAuthInfo authInfo = new XtremIOAuthInfo();
            authInfo.setPassword(password);
            authInfo.setUsername(username);
            
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", MediaType.APPLICATION_JSON);

            String body = getJsonForEntity(authInfo);

            URI requestURI = uri.resolve(URI.create(XtremIOConstants.XTREMIO_BASE_STR));
            ClientResponse response = client.post(requestURI, headers, body);
            if(authenticationFailed(response)) {
                authenticate();
                response = client.post(requestURI, headers, body);
            }

            if (response.getClientResponseStatus() != ClientResponse.Status.OK
                    && response.getClientResponseStatus() != ClientResponse.Status.CREATED) {
                throw XtremIOApiException.exceptions.authenticationFailure(uri.toString());
            }
            _authToken = response.getHeaders().getFirst(XtremIOConstants.AUTH_TOKEN_HEADER);
        } catch (Exception e) {
            throw XtremIOApiException.exceptions.authenticationFailure(uri.toString());
        }
    }
    
    protected <T> String getJsonForEntity(T model) throws Exception {
        return new Gson().toJson(model);
    }
    
    protected <T> T getResponseObject(Class<T> clazz, ClientResponse response) throws Exception {
        JSONObject resp = response.getEntity(JSONObject.class);
        T respObject = new Gson().fromJson(SecurityUtils.sanitizeJsonString(resp.toString()), clazz);
        return respObject;
    }
    
    protected boolean authenticationFailed(ClientResponse response) {
        return response.getClientResponseStatus() == com.sun.jersey.api.client.ClientResponse.Status.UNAUTHORIZED;
    }
}
