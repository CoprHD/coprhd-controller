/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.keystone.restapi;

import java.net.URI;

import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.keystone.KeystoneConstants;
import com.emc.storageos.keystone.restapi.errorhandling.KeystoneApiException;
import com.emc.storageos.keystone.restapi.model.request.AuthTokenRequest;
import com.emc.storageos.keystone.restapi.model.request.PassWordCredentials;
import com.emc.storageos.keystone.restapi.model.response.AuthTokenResponse;
import com.emc.storageos.services.restutil.StandardRestClient;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.WebResource.Builder;

/**
 * Keystone API client to execute rest APIs on
 * keystone service.
 * 
 */
public class KeystoneApiClient extends StandardRestClient {
    private static Logger log = LoggerFactory.getLogger(KeystoneApiClient.class);

    private String _tenantName;

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
    public KeystoneApiClient(URI baseURI, String username,
            String password, Client client) {
        _client = client;
        _base = baseURI;
        _username = username;
        _password = password;
        _authToken = "";
    }

    @Override
    protected Builder setResourceHeaders(WebResource resource) {
        log.info("Setting the resource header " + _authToken);
        return resource.header(KeystoneConstants.AUTH_TOKEN, _authToken);
    }

    @Override
    protected void authenticate() throws KeystoneApiException {
        // Construct the Java pojo request object
        AuthTokenRequest tokenRequest = new AuthTokenRequest();
        tokenRequest.auth.setTenantName(_tenantName);
        PassWordCredentials creds = new PassWordCredentials();
        creds.setUsername(_username);
        creds.setPassword(_password);
        tokenRequest.auth.setPasswordCreds(creds);

        String body = "";
        try {
            // Convert java pojo to json request body
            body = getJsonForEntity(tokenRequest);
        } catch (Exception e) {
            throw KeystoneApiException.exceptions.requestJsonPayloadParseFailure(tokenRequest.toString());
        }

        // invoke the API to authenticate
        URI requestURI = _base.resolve(URI.create(KeystoneConstants.URI_TOKENS));
        ClientResponse response = _client.resource(requestURI).type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, body);

        if (response.getClientResponseStatus() != ClientResponse.Status.OK
                && response.getClientResponseStatus() != ClientResponse.Status.CREATED) {
            throw KeystoneApiException.exceptions.authenticationFailure(requestURI.toString());
        }

        AuthTokenResponse responseBody = getAuthTokenResponce(response);

        _authToken = responseBody.getAccess().getToken().getId();

    }

    private AuthTokenResponse getAuthTokenResponce(ClientResponse response) {

        log.debug("START - getAuthTokenResponce");
        // Read the response and get the auth token
        AuthTokenResponse responseBody = null;
        try {
            responseBody = getResponseObject(AuthTokenResponse.class, response);
        } catch (Exception e) {
            log.error("Failed to parse the token validation response");
            throw KeystoneApiException.exceptions.responseJsonParseFailure(response.toString());
        }

        log.debug("END - getAuthTokenResponce");
        return responseBody;
    }

    public void authenticate_keystone() {
        authenticate();
    }

    public String getTenantName() {
        return _tenantName;
    }

    public void setTenantName(String _tenantName) {
        this._tenantName = _tenantName;
    }

    public String getAuthToken() {
        return _authToken;
    }

    public void setAuthToken(String token) {
        this._authToken = token;
    }

    /**
     * Validates the token
     * If valid - returns the token response
     * 
     * @param userToken
     * @return
     */
    public AuthTokenResponse validateUserToken(String userToken) {
        String tokenValidateUri = String.format(KeystoneConstants.VALIDATE_TOKEN,
                new Object[] { userToken });

        log.info("Invoking token validation api " + _base.resolve(URI.create(tokenValidateUri)).toString());

        ClientResponse response = get(_base.resolve(URI.create(tokenValidateUri)));
        AuthTokenResponse responseBody = getAuthTokenResponce(response);

        log.debug("Got the response -" + responseBody.toString());
        return responseBody;

    }

    @Override
    protected int checkResponse(URI uri, ClientResponse response) {
        log.debug("START - checkresponse");
        ClientResponse.Status status = response.getClientResponseStatus();
        int responseCode = 0;

        if (status != ClientResponse.Status.OK
                && status != ClientResponse.Status.ACCEPTED
                && status != ClientResponse.Status.CREATED) {
            log.error("Keystone API failed to execute");
            throw KeystoneApiException.exceptions.apiExecutionFailed(response.toString());
        } else {
            responseCode = status.getStatusCode();
        }

        log.info("The response code is - " + String.valueOf(responseCode));
        log.debug("END - checkresponse");
        return responseCode;
    }

}
