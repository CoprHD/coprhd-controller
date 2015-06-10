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

/**
 * Jersey filter for adding the proxy token to the request.
 */
public class ProxyTokenFilter extends ClientFilter {

    private TokenAccess tokenAccess;

    public ProxyTokenFilter(TokenAccess tokenAccess) {
        this.tokenAccess = tokenAccess;
    }

    @Override
    public ClientResponse handle(ClientRequest request) throws ClientHandlerException {
        addTokenToRequest(request);
        return getNext().handle(request);
    }

    private void addTokenToRequest(ClientRequest request) {
        String proxyToken = tokenAccess.getToken();
        if (proxyToken != null) {
            request.getHeaders().putSingle(Constants.PROXY_TOKEN_KEY, proxyToken);
        }
    }
}
