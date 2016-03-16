/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.scaleio.serviceutils.restutil;

import java.net.URI;

import com.emc.storageos.driver.scaleio.errorhandling.resources.InternalException;
import com.sun.jersey.api.client.ClientResponse;

public interface RestClientItf {

    /**
     * GET the resource at the passed URI.
     * 
     * @param uri The unique resource URI.
     * 
     * @return A ClientResponse reference.
     */
    ClientResponse get(URI uri) throws InternalException;

    /**
     * PUT to the resource at the passed URI.
     * 
     * @param uri The unique resource URI.
     * @param body The PUT data.
     * 
     * @return A ClientResponse reference.
     */
    ClientResponse put(URI uri, String body) throws InternalException;

    /**
     * POST to the resource at the passed URI.
     * 
     * @param uri The unique resource URI.
     * @param body The POST data.
     * 
     * @return A ClientResponse reference.
     */
    ClientResponse post(URI uri, String body) throws InternalException;

    /**
     * DELETE to the resource at the passed URI.
     * 
     * @param uri The unique resource URI.
     * @param body The POST data.
     * 
     * @return A ClientResponse reference.
     */
    ClientResponse delete(URI uri) throws InternalException;

    /**
     * Close the client
     */
    void close() throws InternalException;

}
