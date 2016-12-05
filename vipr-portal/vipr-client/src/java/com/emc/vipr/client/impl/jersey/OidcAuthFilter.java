/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.impl.jersey;

import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.filter.ClientFilter;
import com.sun.jersey.core.util.Base64;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.HttpHeaders;
import java.io.UnsupportedEncodingException;

/**
 * The client side filter to redirect OIDC interfactions between provider and vipr.
 */
public class OidcAuthFilter extends ClientFilter {

    private String username;
    private String password;

    public OidcAuthFilter(String username, String password) {
        this.username = username;
        this.password = password;
    }

    @Override
    public ClientResponse handle(ClientRequest request) throws ClientHandlerException {
        ClientResponse response = getNext().handle(request);

        if (isOidcAuthRequest(response)) {
            response = authenticateToOidc(response);
        }

        return response;
    }

    private ClientResponse authenticateToOidc(ClientResponse response) {
        final ClientRequest newRequest = ClientRequest.create().
                header("Authorization", encodeAsBasicAuth(username, password)).
                header("Accept", "*/*").
                build(response.getLocation(), HttpMethod.GET);

        // Call handler to perform redirect to new page
        response = handle(newRequest);
        return response;
    }

    private boolean isOidcAuthRequest(ClientResponse response) {
        if ( response.getStatus() == 307 &&
                response.getHeaders().getFirst(HttpHeaders.LOCATION).contains("authorize") ) {
            return true;
        }
        return false;
    }

    public String encodeAsBasicAuth(final String username, final String password) {
        try {
            return "Basic " + new String(Base64.encode(username + ":" + password), "ASCII");
        } catch (UnsupportedEncodingException ex) {
            // This should never occur
            throw new RuntimeException(ex);
        }
    }
}
