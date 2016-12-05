package com.emc.vipr.client.impl.jersey;

import com.emc.vipr.client.impl.Constants;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.filter.ClientFilter;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.HttpHeaders;

/**
 * Created by wangs12 on 12/2/2016.
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
                header("Authorization", "Basic YWRtaW46Y2hhbmdlbWU=").
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
}
