/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.xtremio.restapi;

import java.net.URI;
import javax.ws.rs.core.MediaType;
import org.apache.commons.httpclient.util.URIUtil;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.services.restutil.StandardRestClient;
import com.emc.storageos.xtremio.restapi.errorhandling.XtremIOApiException;
import com.emc.storageos.xtremio.restapi.model.XtremIOAuthInfo;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOObjectInfo;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOXMSsInfo;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

public abstract class XtremIOClient extends StandardRestClient implements XtremIODiscoveryClient, XtremIOProvisioningClient {

    private static Logger log = LoggerFactory.getLogger(XtremIOClient.class);

    /**
     * Constructor
     * 
     * @param client
     *            A reference to a Jersey Apache HTTP client.
     * @param username
     *            The user to be authenticated.
     * @param password
     *            The user password for authentication.
     */
    public XtremIOClient(URI baseURI, String username, String password, Client client) {
        _client = client;
        _base = baseURI;
        _username = username;
        _password = password;
        _authToken = "";
    }

    @Override
    protected WebResource.Builder setResourceHeaders(WebResource resource) {
        return resource.header(XtremIOConstants.AUTH_TOKEN, _authToken);
    }

    /**
     * Check whether the given XMS is running a version 2 REST API
     * 
     * @return
     */
    public boolean isVersion2() {
        boolean isV2 = false;
        try {
            ClientResponse response = get(XtremIOConstants.XTREMIO_V2_XMS_URI);
            XtremIOXMSsInfo xmssInfo = getResponseObject(XtremIOXMSsInfo.class, response);
            for (XtremIOObjectInfo xmsInfo : xmssInfo.getXmssInfo()) {
                URI xmsURI = URI.create(URIUtil.getFromPath(xmsInfo.getHref().concat(XtremIOConstants.XTREMIO_XMS_FILTER_STR)));
                log.debug("Trying to get xms details for {}", xmsURI.toString());
                response = get(xmsURI);
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

    @Override
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

    @Override
    protected void authenticate() throws XtremIOApiException {
        try {
            XtremIOAuthInfo authInfo = new XtremIOAuthInfo();
            authInfo.setPassword(_password);
            authInfo.setUsername(_username);

            String body = getJsonForEntity(authInfo);

            URI requestURI = _base.resolve(URI.create(XtremIOConstants.XTREMIO_BASE_STR));
            ClientResponse response = _client.resource(requestURI).type(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class, body);

            if (response.getClientResponseStatus() != ClientResponse.Status.OK
                    && response.getClientResponseStatus() != ClientResponse.Status.CREATED) {
                throw XtremIOApiException.exceptions.authenticationFailure(_base.toString());
            }
            _authToken = response.getHeaders().getFirst(XtremIOConstants.AUTH_TOKEN_HEADER);
        } catch (Exception e) {
            throw XtremIOApiException.exceptions.authenticationFailure(_base.toString());
        }
    }
}
