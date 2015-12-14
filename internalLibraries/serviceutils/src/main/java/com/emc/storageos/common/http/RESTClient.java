/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.common.http;

import java.net.URI;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.MultivaluedMap;

import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.client.apache.ApacheHttpClient;

// TODO: Auto-generated Javadoc
/**
 * Generic REST client.
 */
public class RESTClient implements RestClientItf {

    /** The _client. */
    private ApacheHttpClient _client;

    /**
     * Instantiates a new REST client.
     *
     * @param client the client
     */
    public RESTClient(ApacheHttpClient client) {
        _client = client;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#get(java.net.URI)
     */
    @Override
    public ClientResponse get(URI url) throws InternalException {
        return get(url, null, null, null, null, null);
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#get(java.net.URI, java.util.List)
     */
    @Override
    public ClientResponse get(URI url, List<Cookie> cookies) throws InternalException {
        return get(url, null, null, cookies, null, null);
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#get(java.net.URI, java.lang.String, java.lang.String)
     */
    @Override
    public ClientResponse get(URI url, String username, String password) throws InternalException {
        return get(url, null, null, null, username, password);
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#get(java.net.URI, java.util.List, java.lang.String, java.lang.String)
     */
    @Override
    public ClientResponse get(URI url, List<Cookie> cookies, String username, String password) throws InternalException {
        return get(url, null, null, cookies, username, password);
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#get(java.net.URI, java.util.Map)
     */
    @Override
    public ClientResponse get(URI url, Map<String, String> headers) throws InternalException {
        return get(url, null, headers, null, null, null);
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#get(java.net.URI, java.util.Map, java.util.List)
     */
    @Override
    public ClientResponse get(URI url, Map<String, String> headers, List<Cookie> cookies) throws InternalException {
        return get(url, null, headers, cookies, null, null);
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#get(java.net.URI, java.util.Map, java.lang.String, java.lang.String)
     */
    @Override
    public ClientResponse get(URI url, Map<String, String> headers, String username, String password) throws InternalException {
        return get(url, null, headers, null, username, password);
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#get(java.net.URI, java.util.Map, java.util.List, java.lang.String, java.lang.String)
     */
    @Override
    public ClientResponse get(URI url, Map<String, String> headers, List<Cookie> cookies, String username, String password)
            throws InternalException {
        return get(url, null, headers, cookies, username, password);
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#get(java.net.URI, javax.ws.rs.core.MultivaluedMap)
     */
    @Override
    public ClientResponse get(URI url, MultivaluedMap<String, String> queryParams) throws InternalException {
        return get(url, queryParams, null, null, null, null);
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#get(java.net.URI, javax.ws.rs.core.MultivaluedMap, java.util.List)
     */
    @Override
    public ClientResponse get(URI url, MultivaluedMap<String, String> queryParams, List<Cookie> cookies) throws InternalException {
        return get(url, queryParams, null, cookies, null, null);
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#get(java.net.URI, javax.ws.rs.core.MultivaluedMap, java.lang.String, java.lang.String)
     */
    @Override
    public ClientResponse get(URI url, MultivaluedMap<String, String> queryParams, String username, String password)
            throws InternalException {
        return get(url, queryParams, null, null, username, password);
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#get(java.net.URI, javax.ws.rs.core.MultivaluedMap, java.util.List, java.lang.String, java.lang.String)
     */
    @Override
    public ClientResponse get(URI url, MultivaluedMap<String, String> queryParams, List<Cookie> cookies, String username, String password)
            throws InternalException {
        return get(url, queryParams, null, cookies, username, password);
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#get(java.net.URI, javax.ws.rs.core.MultivaluedMap, java.util.Map)
     */
    @Override
    public ClientResponse get(URI url, MultivaluedMap<String, String> queryParams, Map<String, String> headers) throws InternalException {
        return get(url, queryParams, headers, null, null, null);
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#get(java.net.URI, javax.ws.rs.core.MultivaluedMap, java.util.Map, java.util.List)
     */
    @Override
    public ClientResponse get(URI url, MultivaluedMap<String, String> queryParams, Map<String, String> headers, List<Cookie> cookies)
            throws InternalException {
        return get(url, queryParams, headers, cookies, null, null);
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#get(java.net.URI, javax.ws.rs.core.MultivaluedMap, java.util.Map, java.lang.String, java.lang.String)
     */
    @Override
    public ClientResponse get(URI url, MultivaluedMap<String, String> queryParams, Map<String, String> headers, String username,
            String password) throws InternalException {
        return get(url, queryParams, headers, null, username, password);
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#get(java.net.URI, javax.ws.rs.core.MultivaluedMap, java.util.Map, java.util.List, java.lang.String, java.lang.String)
     */
    @Override
    public ClientResponse get(URI url, MultivaluedMap<String, String> queryParams, Map<String, String> headers, List<Cookie> cookies,
            String username, String password) throws InternalException {
        WebResource r = _client.resource(url);

        if (queryParams != null && queryParams.size() > 0) {
            r = r.queryParams(queryParams);
        }

        WebResource.Builder rb = getResourceBuilderWithOptions(headers, cookies, username, password, r);
        
        return (rb != null) ? rb.get(ClientResponse.class) : r.put(ClientResponse.class);
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#put(java.net.URI, java.lang.String)
     */
    @Override
    public ClientResponse put(URI uri, String body) throws InternalException {
        return put(uri, null, null, null, body, null, null);
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#put(java.net.URI, java.util.List, java.lang.String)
     */
    @Override
    public ClientResponse put(URI url, List<Cookie> cookies, String body) throws InternalException {
        return put(url, null, null, cookies, body, null, null);
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#put(java.net.URI, java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public ClientResponse put(URI url, String body, String username, String password) throws InternalException {
        return put(url, null, null, null, body, username, password);
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#put(java.net.URI, java.util.List, java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public ClientResponse put(URI url, List<Cookie> cookies, String body, String username, String password) throws InternalException {
        return put(url, null, null, cookies, body, username, password);
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#put(java.net.URI, java.util.Map, java.lang.String)
     */
    @Override
    public ClientResponse put(URI url, Map<String, String> headers, String body) throws InternalException {
        return put(url, null, headers, null, body, null, null);
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#put(java.net.URI, java.util.Map, java.util.List, java.lang.String)
     */
    @Override
    public ClientResponse put(URI url, Map<String, String> headers, List<Cookie> cookies, String body) throws InternalException {
        return put(url, null, headers, cookies, body, null, null);
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#put(java.net.URI, java.util.Map, java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public ClientResponse put(URI url, Map<String, String> headers, String body, String username, String password)
            throws InternalException {
        return put(url, null, headers, null, body, username, password);
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#put(java.net.URI, java.util.Map, java.util.List, java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public ClientResponse put(URI url, Map<String, String> headers, List<Cookie> cookies, String body, String username, String password)
            throws InternalException {
        return put(url, null, headers, cookies, body, username, password);
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#put(java.net.URI, javax.ws.rs.core.MultivaluedMap, java.util.Map, java.lang.String)
     */
    @Override
    public ClientResponse put(URI url, MultivaluedMap<String, String> queryParams, Map<String, String> headers, String body)
            throws InternalException {
        return put(url, queryParams, headers, null, body, null, null);
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#put(java.net.URI, javax.ws.rs.core.MultivaluedMap, java.util.Map, java.util.List, java.lang.String)
     */
    @Override
    public ClientResponse put(URI url, MultivaluedMap<String, String> queryParams, Map<String, String> headers, List<Cookie> cookies,
            String body) throws InternalException {
        return put(url, queryParams, headers, cookies, body, null, null);
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#put(java.net.URI, javax.ws.rs.core.MultivaluedMap, java.util.Map, java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public ClientResponse put(URI url, MultivaluedMap<String, String> queryParams, Map<String, String> headers,
            String body, String username, String password) throws InternalException {
        return put(url, queryParams, headers, null, body, username, password);
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#put(java.net.URI, javax.ws.rs.core.MultivaluedMap, java.util.Map, java.util.List, java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public ClientResponse put(URI url, MultivaluedMap<String, String> queryParams, Map<String, String> headers, List<Cookie> cookies,
            String body, String username, String password) throws InternalException {
        WebResource r = _client.resource(url);
        if (queryParams != null && queryParams.size() > 0) {
            r = r.queryParams(queryParams);
        }
        
        WebResource.Builder rb = getResourceBuilderWithOptions(headers, cookies, username, password, r);
        
        return (rb != null) ? rb.put(ClientResponse.class, body) : r.put(ClientResponse.class, body);
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#post(java.net.URI, java.lang.String)
     */
    @Override
    public ClientResponse post(URI uri, String body) throws InternalException {
        return post(uri, null, null, body, null, null);
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#post(java.net.URI, java.util.List, java.lang.String)
     */
    @Override
    public ClientResponse post(URI url, List<Cookie> cookies, String body) throws InternalException {
        return post(url, null, cookies, body, null, null);
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#post(java.net.URI, java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public ClientResponse post(URI url, String body, String username, String password) throws InternalException {
        return post(url, null, null, body, username, password);
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#post(java.net.URI, java.util.List, java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public ClientResponse post(URI url, List<Cookie> cookies, String body, String username, String password) throws InternalException {
        return post(url, null, cookies, body, username, password);
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#post(java.net.URI, java.util.Map, java.lang.String)
     */
    @Override
    public ClientResponse post(URI url, Map<String, String> headers, String body) throws InternalException {
        return post(url, headers, null, body, null, null);
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#post(java.net.URI, java.util.Map, java.util.List, java.lang.String)
     */
    @Override
    public ClientResponse post(URI url, Map<String, String> headers, List<Cookie> cookies, String body) throws InternalException {
        return post(url, headers, cookies, body, null, null);
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#post(java.net.URI, java.util.Map, java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public ClientResponse post(URI url, Map<String, String> headers, String body, String username, String password)
            throws InternalException {
        return post(url, headers, null, body, username, password);
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#post(java.net.URI, java.util.Map, java.util.List, java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public ClientResponse post(URI url, Map<String, String> headers, List<Cookie> cookies, String body, String username, String password)
            throws InternalException {
        WebResource r = _client.resource(url);
        WebResource.Builder rb = getResourceBuilderWithOptions(headers, cookies, username, password, r);
        
        return (rb != null) ? rb.post(ClientResponse.class, body) : r.post(ClientResponse.class, body);
    }

    
    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#delete(java.net.URI)
     */
    @Override
    public ClientResponse delete(URI url) throws InternalException {
        return delete(url, null, null, null, null, null);
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#delete(java.net.URI, java.util.List)
     */
    @Override
    public ClientResponse delete(URI url, List<Cookie> cookies) throws InternalException {
        return delete(url, null, cookies, null, null, null);
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#delete(java.net.URI, java.lang.String)
     */
    @Override
    public ClientResponse delete(URI url, String body) throws InternalException {
        return delete(url, null, null, body, null, null);
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#delete(java.net.URI, java.util.List, java.lang.String)
     */
    @Override
    public ClientResponse delete(URI url, List<Cookie> cookies, String body) throws InternalException {
        return delete(url, null, cookies, body, null, null);
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#delete(java.net.URI, java.lang.String, java.lang.String)
     */
    @Override
    public ClientResponse delete(URI url, String username, String password) throws InternalException {
        return delete(url, null, null, null, username, password);
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#delete(java.net.URI, java.util.List, java.lang.String, java.lang.String)
     */
    @Override
    public ClientResponse delete(URI url, List<Cookie> cookies, String username, String password) throws InternalException {
        return delete(url, null, cookies, null, username, password);
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#delete(java.net.URI, java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public ClientResponse delete(URI url, String body, String username, String password) throws InternalException {
        return delete(url, null, null, body, username, password);
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#delete(java.net.URI, java.util.List, java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public ClientResponse delete(URI url, List<Cookie> cookies, String body, String username, String password) throws InternalException {
        return delete(url, null, cookies, body, username, password);
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#delete(java.net.URI, java.util.Map)
     */
    @Override
    public ClientResponse delete(URI url, Map<String, String> headers) throws InternalException {
        return delete(url, headers, null, null, null, null);
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#delete(java.net.URI, java.util.Map, java.util.List)
     */
    @Override
    public ClientResponse delete(URI url, Map<String, String> headers, List<Cookie> cookies) throws InternalException {
        return delete(url, headers, cookies, null, null, null);
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#delete(java.net.URI, java.util.Map, java.lang.String)
     */
    @Override
    public ClientResponse delete(URI url, Map<String, String> headers, String body) throws InternalException {
        return delete(url, headers, null, body, null, null);
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#delete(java.net.URI, java.util.Map, java.util.List, java.lang.String)
     */
    @Override
    public ClientResponse delete(URI url, Map<String, String> headers, List<Cookie> cookies, String body) throws InternalException {
        return delete(url, headers, cookies, body, null, null);
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#delete(java.net.URI, java.util.Map, java.lang.String, java.lang.String)
     */
    public ClientResponse delete(URI url, Map<String, String> headers, String username, String password) throws InternalException {
        return delete(url, headers, null, null, username, password);
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#delete(java.net.URI, java.util.Map, java.util.List, java.lang.String, java.lang.String)
     */
    @Override
    public ClientResponse delete(URI url, Map<String, String> headers, List<Cookie> cookies, String username, String password)
            throws InternalException {
        return delete(url, headers, cookies, null, username, password);
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#delete(java.net.URI, java.util.Map, java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public ClientResponse delete(URI url, Map<String, String> headers, String body, String username, String password)
            throws InternalException {
        return delete(url, headers, null, body, username, password);
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#delete(java.net.URI, java.util.Map, java.util.List, java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public ClientResponse delete(URI url, Map<String, String> headers, List<Cookie> cookies, String body, String username, String password)
            throws InternalException {
        WebResource r = _client.resource(url);
        WebResource.Builder rb = getResourceBuilderWithOptions(headers, cookies, username, password, r);

        if (body != null)
            return (rb != null) ? rb.delete(ClientResponse.class) : r.delete(ClientResponse.class);
        else
            return (rb != null) ? rb.delete(ClientResponse.class, body) : r.delete(ClientResponse.class, body);
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#head(java.net.URI)
     */
    @Override
    public ClientResponse head(URI url) throws InternalException {
        return head(url, null, null, null);
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#head(java.net.URI, java.util.List)
     */
    @Override
    public ClientResponse head(URI url, List<Cookie> cookies) throws InternalException {
        return head(url, cookies, null, null);
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#head(java.net.URI, java.lang.String, java.lang.String)
     */
    @Override
    public ClientResponse head(URI url, String username, String password) throws InternalException {
        return head(url, null, username, password);
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.common.http.RestClientItf#head(java.net.URI, java.util.List, java.lang.String, java.lang.String)
     */
    @Override
    public ClientResponse head(URI url, List<Cookie> cookies, String username, String password) throws InternalException {
        WebResource r = _client.resource(url);
        WebResource.Builder rb = getResourceBuilderWithOptions(null, cookies, username, password, r);
        
        return (rb != null) ? rb.head() : r.head();
    }

    /**
     * Adds the auth filter if necessary.
     *
     * @param username the username
     * @param password the password
     * @param resource the resource
     * @throws InternalException the internal exception
     */
    private void addAuthFilterIfNecessary(String username, String password, WebResource resource) throws InternalException {
        if (username != null && password != null)
            resource.addFilter(new HTTPBasicAuthFilter(username, password));
    }

    /**
     * Gets the builder with headers.
     *
     * @param headers the headers
     * @param r the r
     * @return the builder with headers
     * @throws InternalException the internal exception
     */
    private WebResource.Builder getBuilderWithHeaders(Map<String, String> headers, WebResource r) throws InternalException {
        WebResource.Builder rb = null;
        if (headers != null && headers.size() > 0)
            for (String headerKey : headers.keySet()) {
                rb = r.header(headerKey, headers.get(headerKey));
            }
        return rb;
    }
    
    
    /**
     * Gets the builder with cookies.
     *
     * @param cookies the cookies
     * @param r the r
     * @param rb the rb
     * @return the builder with cookies
     * @throws InternalException the internal exception
     */
    private WebResource.Builder getBuilderWithCookies(List<Cookie> cookies, WebResource r, WebResource.Builder rb) throws InternalException {
        if (cookies != null && cookies.size() > 0)
            if(rb == null) {
                rb = r.header("1", "2");//Dummy Header
            }
            for (Cookie cookie : cookies) {
                rb = rb.cookie(cookie);
            }
        return rb;
    }

    /**
     * Gets the resource builder with options.
     *
     * @param headers the headers
     * @param cookies the cookies
     * @param username the username
     * @param password the password
     * @param r the r
     * @return the resource builder with options
     */
    private WebResource.Builder getResourceBuilderWithOptions(Map<String, String> headers, List<Cookie> cookies, String username,
            String password, WebResource r) {
        addAuthFilterIfNecessary(username, password, r);
        WebResource.Builder rb = getBuilderWithHeaders(headers, r);
        rb = getBuilderWithCookies(cookies, r, rb);
        
        return rb;
    }
    
    /**
     * Gets the client.
     *
     * @return the client
     */
    public ApacheHttpClient getClient() {
        return _client;
    }

    /**
     * Sets the client.
     *
     * @param _client the new client
     */
    public void setClient(ApacheHttpClient _client) {
        this._client = _client;
    }
    
}