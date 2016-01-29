/*
 * Copyright 2015 EMC Corporation
 * Copyright 2016 Intel Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.emc.storageos.keystone.restapi;

import java.net.URI;

import javax.ws.rs.core.MediaType;

import com.emc.storageos.keystone.restapi.model.request.CreateEndpointRequest;
import com.emc.storageos.keystone.restapi.model.response.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.keystone.KeystoneConstants;
import com.emc.storageos.keystone.restapi.errorhandling.KeystoneApiException;
import com.emc.storageos.keystone.restapi.model.request.AuthTokenRequest;
import com.emc.storageos.keystone.restapi.model.request.PassWordCredentials;
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

    /**
     * Retrieves Keystone endpoints from the Keystone API.
     *
     * @return EndpointResponse object filled with Keystone endpoints data.
     */
    public EndpointResponse getKeystoneEndpoints() throws KeystoneApiException{

        log.debug("START - getKeystoneEndpoints");

        // Authenticate user if there is no token available.
        if(_authToken == null){
            authenticate_keystone();
        }

        // Send a request to Keystone API.
        URI requestURI = _base.resolve(URI.create(KeystoneConstants.URI_ENDPOINTS));
        ClientResponse response = _client.resource(requestURI).accept(MediaType.APPLICATION_JSON).header(KeystoneConstants.AUTH_TOKEN, _authToken).get(ClientResponse.class);

        // Throw an exception when response code is other than OK
        if (response.getClientResponseStatus() != ClientResponse.Status.OK) {
            throw KeystoneApiException.exceptions.endpointRequestFailure(response.getClientResponseStatus().name());
        }

        // Parse response to Java object.
        EndpointResponse endpointResponse;
        log.debug("Parsing endpoint request results to Java object");
        try {
            endpointResponse = getResponseObject(EndpointResponse.class, response);
        } catch (Exception e) {
            log.error("Failed to parse the endpoint validation response");
            throw KeystoneApiException.exceptions.responseJsonParseFailure(response.toString());
        }

        log.debug("END - getKeystoneEndpoints");
        return endpointResponse;
    }

    /**
     * Retrieves Keystone services from the Keystone API.
     *
     * @return ServiceResponse object filled with Keystone services data.
     */
    public ServiceResponse getKeystoneServices() throws KeystoneApiException{

        log.debug("START - getKeystoneServices");

        // Authenticate user if there is no token available.
        if(_authToken == null){
            authenticate_keystone();
        }

        // Send a request to Keystone API.
        URI requestURI = _base.resolve(URI.create(KeystoneConstants.URI_SERVICES));
        ClientResponse response = _client.resource(requestURI).accept(MediaType.APPLICATION_JSON).header(KeystoneConstants.AUTH_TOKEN, _authToken).get(ClientResponse.class);

        // Throw an exception when response code is other than OK.
        if (response.getClientResponseStatus() != ClientResponse.Status.OK) {
            throw KeystoneApiException.exceptions.serviceRequestFailure(response.getClientResponseStatus().name());
        }

        // Parse response to Java object.
        ServiceResponse serviceResponse;
        log.debug("Parsing service request results to Java object");
        try {
            serviceResponse = getResponseObject(ServiceResponse.class, response);
        } catch (Exception e) {
            log.error("Failed to parse the service validation response");
            throw KeystoneApiException.exceptions.responseJsonParseFailure(response.toString());
        }

        log.debug("END - getKeystoneServices");
        return serviceResponse;
    }

    /**
     * Deletes Keystone endpoint with given ID.
     *
     * @param endpointId Keystone endpoint ID to delete.
     */
    public void deleteKeystoneEndpoint(String endpointId){

        log.debug("START - deleteKeystoneEndpoint");

        // Authenticate user if there is no token available.
        if(_authToken == null){
            authenticate_keystone();
        }

        // Create correct delete URI.
        String uri = KeystoneConstants.URI_ENDPOINTS + "/" + endpointId;
        URI requestURI = _base.resolve(URI.create(uri));
        // Send a delete request to Keystone API.
        ClientResponse response = _client.resource(requestURI).header(KeystoneConstants.AUTH_TOKEN, _authToken).delete(ClientResponse.class);

        // Throw an exception when response code is other than NO_CONTENT.
        if (response.getClientResponseStatus() != ClientResponse.Status.NO_CONTENT) {
            throw KeystoneApiException.exceptions.endpointRequestFailure(response.getClientResponseStatus().name());
        }

        log.debug("END - deleteKeystoneEndpoint");
    }

    /**
     * Creates a new Keystone endpoint.
     *
     * @param endpoint A new endpoint to create filled with information.
     * @return CreateResponse object with created Keystone endpoint.
     */
    public CreateResponse createKeystoneEndpoint(EndpointV2 endpoint){

        log.debug("START - createKeystoneEndpoint");

        // Authenticate user if there is no token available.
        if(_authToken == null){
            authenticate_keystone();
        }

        // Construct the Java pojo request object
        CreateEndpointRequest endpointRequest = new CreateEndpointRequest();
        endpointRequest.setEndpoint(endpoint);

        String body = "";
        try {
            // Convert java pojo to json request body
            body = getJsonForEntity(endpointRequest);
        } catch (Exception e) {
            throw KeystoneApiException.exceptions.requestJsonPayloadParseFailure(endpointRequest.toString());
        }

        // Create a new URI for endpoint creation.
        String uri = KeystoneConstants.URI_ENDPOINTS;
        URI requestURI = _base.resolve(URI.create(uri));

        // Send a create request to Keystone API.
        ClientResponse response = _client.resource(requestURI).type(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                                        .header(KeystoneConstants.AUTH_TOKEN, _authToken).post(ClientResponse.class, body);

        // Throw an exception when response code is other than OK or CREATED.
        if (response.getClientResponseStatus() != ClientResponse.Status.OK
                && response.getClientResponseStatus() != ClientResponse.Status.CREATED) {
            throw KeystoneApiException.exceptions.endpointRequestFailure(response.getClientResponseStatus().name());
        }

        // Parse response to Java object.
        CreateResponse createResponse;
        log.debug("Parsing service request results to Java object");
        try {
            createResponse = getResponseObject(CreateResponse.class, response);
        } catch (Exception e) {
            log.error("Failed to parse the endpoint validation response");
            throw KeystoneApiException.exceptions.responseJsonParseFailure(response.toString());
        }

        log.debug("END - createKeystoneEndpoint");
        return createResponse;
    }

    /**
     * Retrieves Keystone tenants from the Keystone API.
     *
     * @return TenantResponse object filled with Keystone tenants data.
     */
    public TenantResponse getKeystoneTenants() throws KeystoneApiException{

        log.debug("START - getKeystoneTenants");

        // Authenticate user if there is no token available.
        if(_authToken == null){
            authenticate_keystone();
        }

        // Send a request to Keystone API.
        URI requestURI = _base.resolve(URI.create(KeystoneConstants.URI_TENANTS));
        ClientResponse response = _client.resource(requestURI).accept(MediaType.APPLICATION_JSON).header(KeystoneConstants.AUTH_TOKEN, _authToken).get(ClientResponse.class);

        // Throw an exception when response code is other than OK.
        if (response.getClientResponseStatus() != ClientResponse.Status.OK) {
            throw KeystoneApiException.exceptions.tenantRequestFailure(response.getClientResponseStatus().name());
        }

        // Parse response to Java object.
        TenantResponse tenantResponse;
        log.debug("Parsing service request results to Java object");
        try {
            tenantResponse = getResponseObject(TenantResponse.class, response);
        } catch (Exception e) {
            log.error("Failed to parse the tenant validation response");
            throw KeystoneApiException.exceptions.responseJsonParseFailure(response.toString());
        }

        log.debug("END - getKeystoneTenants");
        return tenantResponse;
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
