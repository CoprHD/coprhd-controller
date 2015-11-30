/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.common.http;

import com.sun.jersey.api.client.ClientResponse;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import java.net.URI;
import java.util.Map;

import javax.ws.rs.core.MultivaluedMap;

public interface RestClientItf {

    /**
     * GET the resource at the passed URI.
     * 
     * @param uri The unique resource URI.
     * 
     * @return A ClientResponse reference.
     */
    ClientResponse get(URI uri) throws InternalException;

    ClientResponse get(URI url, String username, String password);

    ClientResponse get(URI url, Map<String, String> headers);

    ClientResponse get(URI url, Map<String, String> headers, String username, String password);
    
    ClientResponse get(URI url, MultivaluedMap<String, String> queryParams);
    
    ClientResponse get(URI url, MultivaluedMap<String, String> queryParams, String username, String password);

    ClientResponse get(URI url, MultivaluedMap<String, String> queryParams, Map<String, String> headers);
    
    ClientResponse get(URI url, MultivaluedMap<String, String> queryParams, Map<String, String> headers, String username, String password);

    /**
     * PUT to the resource at the passed URI.
     * 
     * @param uri The unique resource URI.
     * @param body The PUT data.
     * 
     * @return A ClientResponse reference.
     */
    ClientResponse put(URI uri, String body) throws InternalException;

    ClientResponse put(URI uri, String body, String username, String password) throws InternalException;

    ClientResponse put(URI url, Map<String, String> headers, String body);

    ClientResponse put(URI url, Map<String, String> headers, String body, String username, String password);

    ClientResponse put(URI url, MultivaluedMap<String, String> queryParams, Map<String, String> headers,
            String body);

    ClientResponse put(URI url, MultivaluedMap<String, String> queryParams, Map<String, String> headers,
            String body, String username, String password);

    /**
     * POST to the resource at the passed URI.
     * 
     * @param uri The unique resource URI.
     * @param body The POST data.
     * 
     * @return A ClientResponse reference.
     */
    ClientResponse post(URI uri, String body) throws InternalException;

    ClientResponse post(URI uri, String body, String username, String password) throws InternalException;

    ClientResponse post(URI url, Map<String, String> headers, String body);

    ClientResponse post(URI url, Map<String, String> headers, String body, String username, String password);

    /**
     * DELETE to the resource at the passed URI.
     * 
     * @param uri The unique resource URI.
     * @param body The POST data.
     * 
     * @return A ClientResponse reference.
     */
    ClientResponse delete(URI uri) throws InternalException;
    
    ClientResponse delete(URI uri, String body) throws InternalException;

    ClientResponse delete(URI url, String username, String password);
    
    ClientResponse delete(URI url, String body, String username, String password);
    
    ClientResponse delete(URI url, Map<String, String> headers);
    
    ClientResponse delete(URI url, Map<String, String> headers, String body);
    
    ClientResponse delete(URI url, Map<String, String> headers, String username, String password);
    
    ClientResponse delete(URI url, Map<String, String> headers, String body, String username, String password);

    ClientResponse head(URI url);

    ClientResponse head(URI url, String username, String password);

}
