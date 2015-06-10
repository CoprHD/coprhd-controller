/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.impl.jersey;

import com.emc.vipr.client.impl.Constants;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.filter.ClientFilter;
import javax.ws.rs.core.HttpHeaders;
import java.net.URI;

/**
 * Jersey filter for accessing and sending Storage OS Auth Token. This also implements redirection manually 302.
 *
 * If login is required, you are redirected to the login page. Basic Auth is taken there and a token is provided back.
 * This is sent back as a 302 to redirect you to your original page. The issue is that the 302 contains the response
 * header with the token. Standard redirect processing will lose this token when you redirect to the real page.
 */
public class AuthTokenFilter extends ClientFilter {

    private TokenAccess sessionAccess;
    
    public AuthTokenFilter(TokenAccess sessionAccess) {
        this.sessionAccess = sessionAccess;
    }
    
    @Override
    public ClientResponse handle(ClientRequest request) throws ClientHandlerException {
        addTokenToRequest(request);

        ClientResponse response = getNext().handle(request);

        // Handle a redirect
        if (response.getClientResponseStatus() == ClientResponse.Status.FOUND) {
            if (response.getHeaders().containsKey(HttpHeaders.LOCATION)) {
                String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);

                final ClientRequest newRequest = ClientRequest.create().build(URI.create(location), request.getMethod());

                // Handle the token from the existing response, add to this new request
                checkResponseForToken(response);
                addTokenToRequest(newRequest);

                // Call handler to perform redirect to new page
                response = handle(newRequest);
            }
        }

        checkResponseForToken(response);
        return response;
    }

    private void addTokenToRequest(ClientRequest request) {
        if (sessionAccess.getToken() != null) {
            request.getHeaders().putSingle(Constants.AUTH_TOKEN_KEY, sessionAccess.getToken());
        }
    }

    private void checkResponseForToken(ClientResponse response) {
        Object token = response.getHeaders().getFirst(Constants.AUTH_TOKEN_KEY);
        if (token != null && token instanceof String) {
            sessionAccess.setToken((String) token);
        }
    }
}
