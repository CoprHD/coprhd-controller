/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */

/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.services.restutil;

import com.sun.jersey.api.client.ClientResponse;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import java.net.URI;

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
