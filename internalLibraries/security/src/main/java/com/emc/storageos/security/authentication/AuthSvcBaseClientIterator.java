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
package com.emc.storageos.security.authentication;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.security.helpers.ClientRequestHelper;
import com.sun.jersey.api.client.Client;

/**
 * Base utility class to provide endpoints to authsvc
 */
// GEO-TODO: rename this after the merge with the geo branch so it won't include Auth in
// the name
    public abstract class AuthSvcBaseClientIterator implements Iterator<URI> {
    private static final Logger _log = LoggerFactory.getLogger(AuthSvcBaseClientIterator.class);
    protected final URI[] _authSvcEndpoints;
    protected final int _size;
    protected int _currentIndex = 0;
    protected ClientRequestHelper _clientHelper;
    protected Client _client;

    /**
     * Initializes the list of endpoints
     * @param endPointLocator
     */
    public AuthSvcBaseClientIterator(EndPointLocator endPointLocator) {
        List<URI> endpoints = endPointLocator.getServiceEndpointList();
        _size  = endpoints.size();
        _authSvcEndpoints = endpoints.toArray(new URI[_size]);    
    }

    protected void setClientRequestHelper(ClientRequestHelper helper) {
        _clientHelper = helper;
        _client = helper.createClient();
    }

    /*
     * @see java.util.Iterator#hasNext()
     */
    @Override
    public boolean hasNext() {        
        return _currentIndex < _size && null !=  _authSvcEndpoints[_currentIndex];
    }

    /*
     * @see java.util.Iterator#next()
     */
    @Override
    public URI next() {
        return _authSvcEndpoints[_currentIndex++];
    }

    /*
     * @see java.util.Iterator#remove()
     */
    @Override
    public void remove() {
        // Do nothing
    }

    /**
     * Peek at the current URI in the list but do not advance the index
     * @return the current URI in the list
     */
    public URI peek() {
        return _authSvcEndpoints[_currentIndex];
    }
}
