/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.vplex.api;

import java.net.URI;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.WebResource.Builder;

import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.MediaType;

/**
 * Generic REST client over HTTP.
 */
public class RESTClient {

    // A reference to the Jersey Apache HTTP client.
    private Client _client;

    // The user to be authenticated for requests made by the client.
    private String _username;

    // The password for user authentication.
    private String _password;

    /**
     * Constructor
     * 
     * @param client A reference to a Jersey Apache HTTP client.
     * @param username The user to be authenticated.
     * @param password The user password for authentication.
     */
    RESTClient(Client client, String username, String password) {
        _client = client;
        _username = username;
        _password = password;
    }

    /**
     * GET the resource at the passed URI.
     * 
     * @param uri The unique resource URI.
     * @param vplexSessionId The VPLEX API session id or null.
     * @param jsonFormat the JSON format to expect in the response.
     * @param cacheControlMaxAge cache control max age
     * 
     * @return A ClientResponse reference.
     */
    ClientResponse get(URI uri, String vplexSessionId, String jsonFormat, String cacheControlMaxAge) {
        return setResourceHeaders(_client.resource(uri), vplexSessionId,
                jsonFormat, cacheControlMaxAge).get(ClientResponse.class);
    }

    /**
     * PUT to the resource at the passed URI.
     * 
     * @param uri The unique resource URI.
     * @param vplexSessionId The VPLEX API session id or null.
     * @param jsonFormat the JSON format to expect in the response.
     * 
     * @return A ClientResponse reference.
     */
    ClientResponse put(URI uri, String vplexSessionId, String jsonFormat) {
        return setResourceHeaders(_client.resource(uri), vplexSessionId,
                jsonFormat, VPlexApiConstants.CACHE_CONTROL_MAXAGE_ZERO).put(
                ClientResponse.class);
    }

    /**
     * PUT to the resource at the passed URI.
     * 
     * @param uri The unique resource URI.
     * @param body The PUT data.
     * @param vplexSessionId The VPLEX API session id or null.
     * @param jsonFormat the JSON format to expect in the response.
     * 
     * @return A ClientResponse reference.
     */
    ClientResponse put(URI uri, String body, String vplexSessionId, String jsonFormat) {
        return setResourceHeaders(_client.resource(uri), vplexSessionId,
                jsonFormat, VPlexApiConstants.CACHE_CONTROL_MAXAGE_ZERO).type(
                MediaType.APPLICATION_JSON).put(ClientResponse.class, body);
    }

    /**
     * POST to the resource at the passed URI.
     * 
     * @param uri The unique resource URI.
     * @param body The POST data.
     * @param vplexSessionId The VPLEX API session id or null.
     * @param jsonFormat the JSON format to expect in the response.
     * 
     * @return A ClientResponse reference.
     */
    ClientResponse post(URI uri, String body, String vplexSessionId, String jsonFormat) {
        return setResourceHeaders(_client.resource(uri), vplexSessionId,
                jsonFormat, VPlexApiConstants.CACHE_CONTROL_MAXAGE_ZERO).type(
                MediaType.APPLICATION_JSON).post(ClientResponse.class, body);
    }

    /**
     * Close the client
     */
    void close() {
        _client.destroy();
    }

    /**
     * Sets required headers into the passed WebResource.
     * 
     * @param resource The resource to which headers are added.
     * @param vplexSessionId The VPLEX API session id or null.
     * @param jsonFormat the JSON format to expect in the response.
     * @param cacheControlMaxAge cache control max age
     */
    Builder setResourceHeaders(WebResource resource, String vplexSessionId, String jsonFormat, String cacheControlMaxAge) {

        // Set the headers for the username, password, and connection.
        Builder resBuilder = resource
                .header(VPlexApiConstants.USER_NAME_HEADER, _username)
                .header(VPlexApiConstants.PASS_WORD_HEADER, _password)
                .header(VPlexApiConstants.CONNECTION_HEADER,
                        VPlexApiConstants.CONNECTION_HEADER_VALUE_CLOSE);

        // Set the session id cookie. Can be null on first request.
        if (vplexSessionId != null) {
            resBuilder.cookie(new Cookie(VPlexApiConstants.SESSION_COOKIE, vplexSessionId));
        }

        // will look like this: application/json;format=1 or format=0
        resBuilder.accept(MediaType.APPLICATION_JSON + jsonFormat);

        // if using JSON response format 1, also set VPLEX API cache-control
        if (VPlexApiConstants.ACCEPT_JSON_FORMAT_1.equals(jsonFormat)) {
            resBuilder.header(VPlexApiConstants.CACHE_CONTROL_HEADER,
                    VPlexApiConstants.CACHE_CONTROL_MAXAGE_KEY + cacheControlMaxAge);
        }

        return resBuilder;
    }
}
