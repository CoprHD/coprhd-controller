/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.isilon.restapi;

import java.net.URI;

import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.WebResource.Builder;

/**
 * Generic REST client over HTTP
 */
public class RESTClient {
    // A reference to the Jersey Apache HTTP client.
    private Client _client;

    // A reference to the authenticate type
    private String _authType;

    // A reference to the device URI
    private URI _deviceURI;

    // A reference to the Session Id for authentication.
    private String _isisessId;

    // A reference to the CSRF Id for authentication.
    private String _isicsrfId;

    /**
     * Constructor
     * 
     * @param client Jersey client to use
     */
    public RESTClient(Client client) {
        _client = client;
    }

    /**
     * Constructor
     * 
     * @param client reference to the Jersey Apache HTTP client.
     * @param authType reference to the authenticate type
     */
    public RESTClient(Client client, String authType) {
        this(client);
        _authType = authType;
    }

    /**
     * Constructor
     * 
     * @param client reference to the Jersey Apache HTTP client.
     * @param authType reference to the authenticate type
     * @param deviceURI reference to the device URI
     * @param isisessId Session Id for authentication.
     * @param isicsrfId CSRF Id for authentication.
     */
    public RESTClient(Client client, String authType, URI deviceURI, String isisessId, String isicsrfId) {
    	this(client, authType);
        _deviceURI = deviceURI;
        _isisessId = isisessId;
        _isicsrfId = isicsrfId;
    }

    public String get_authType() {
		return _authType;
	}

    /**
     * Get resource as a ClientResponse
     * 
     * @param url url for the resource to get
     * @return ClientResponse response
     */
    public ClientResponse get(URI url) {
    	ClientResponse clientResp = null;

    	if (IsilonApiConstants.AuthType.BASIC.name().equals(_authType))
    		clientResp = _client.resource(url).get(ClientResponse.class);
    	else
    		clientResp = setResourceHeaders(_client.resource(url)).get(ClientResponse.class);
    	return clientResp;
    }

    /**
     * Get attribute information about resource.
     * 
     * @param url url for the resource to execute head
     * @return ClientResponse response
     */
    public ClientResponse head(URI url) {
    	ClientResponse clientResp = null;

    	if (IsilonApiConstants.AuthType.BASIC.name().equals(_authType))
    		clientResp = _client.resource(url).head();
    	else
    		clientResp = setResourceHeaders(_client.resource(url)).head();
    	return clientResp;
    }

    /**
     * Put resource at url
     * 
     * @param url url for the resource to put
     * @param queryParams if any parameters supplied
     * @param body body for the put
     * @return ClientResponse response
     */
    public ClientResponse put(URI url, MultivaluedMap<String, String> queryParams, String body) {
    	ClientResponse clientResp = null;
        WebResource webRes = _client.resource(url);
        
        if (IsilonApiConstants.AuthType.BASIC.name().equals(_authType)) {
	        if (queryParams != null && queryParams.size() > 0) {
	            WebResource.Builder rb = webRes.queryParams(queryParams)
	            		.header("x-isi-ifs-target-type", "container")
	            		.header("x-isi-ifs-access-control", "0755");
	            clientResp = rb.put(ClientResponse.class, body);
	        } else {
	        	clientResp = webRes.header("x-isi-ifs-target-type", "container")
	        			.header("x-isi-ifs-access-control", "0755")
	                    .put(ClientResponse.class, body);
	        }
        } else {
        	clientResp = setPutResourceHeaders(webRes, queryParams).put(ClientResponse.class, body);
        }
        return clientResp;
    }

    /**
     * Delete resource at url
     * 
     * @param url url for the resource to delete
     * @return ClientResponse
     */
    public ClientResponse delete(URI url) {
    	ClientResponse clientResp = null;

    	if (IsilonApiConstants.AuthType.BASIC.name().equals(_authType))
    		clientResp = _client.resource(url).delete(ClientResponse.class);
    	else
    		clientResp = setResourceHeaders(_client.resource(url)).delete(ClientResponse.class);
    	return clientResp;
    }

    /**
     * Post resource at url
     * 
     * @param url url to post to
     * @param body content to post
     * @return ClientResponse response
     */
    public ClientResponse post(URI url, String body) {
    	ClientResponse clientResp = null;

    	if (IsilonApiConstants.AuthType.BASIC.name().equals(_authType))
    		clientResp = _client.resource(url).type(MediaType.APPLICATION_JSON).post(ClientResponse.class, body);
    	else
    		clientResp = setResourceHeaders(_client.resource(url)).type(MediaType.APPLICATION_JSON).post(ClientResponse.class, body);
    	return clientResp;
    }

    /**
     * Close the client
     */
    public void close() {
        _client.destroy();
    }

    /**
     * Sets required headers into the passed WebResource.
     * 
     * @param resource The resource to which headers are added.
     */
    Builder setResourceHeaders(WebResource resource) {
        // Set the headers for the SessionId, CSRFId, and connection.
    	// Set the session id and csrf id cookie. Can be null on first request.
        Builder resBuilder = resource
        		.cookie(new Cookie(IsilonApiConstants.SESSION_COOKIE, _isisessId))
                .header(IsilonApiConstants.CSRF_HEADER, _isicsrfId)
                .header(IsilonApiConstants.REFERER_HEADER, _deviceURI)
                .accept(MediaType.APPLICATION_JSON);

        return resBuilder;
    }

    /**
     * Sets required headers into the passed WebResource for Put.
     * 
     * @param resource The resource to which headers are added.
     */
    Builder setPutResourceHeaders(WebResource resource, MultivaluedMap<String, String> queryParams) {
    	Builder resBuilder = null;
    	
        if (queryParams != null && queryParams.size() > 0) {
            // Set the headers for the SessionId, CSRFId, and connection.
        	// Set the session id and csrf id cookie. Can be null on first request.
            resBuilder = resource
            		.queryParams(queryParams)
            		.cookie(new Cookie(IsilonApiConstants.SESSION_COOKIE, _isisessId))
                    .header(IsilonApiConstants.CSRF_HEADER, _isicsrfId)
                    .header(IsilonApiConstants.REFERER_HEADER, _deviceURI)
                    .header("x-isi-ifs-target-type", "container")
                    .header("x-isi-ifs-access-control", "0755")
                    .accept(MediaType.APPLICATION_JSON);
       } else {
    	   // Set the headers for the SessionId, CSRFId, and connection.
    	   // Set the session id and csrf id cookie. Can be null on first request.
           resBuilder = resource
        		   .cookie(new Cookie(IsilonApiConstants.SESSION_COOKIE, _isisessId))
                   .header(IsilonApiConstants.CSRF_HEADER, _isicsrfId)
                   .header(IsilonApiConstants.REFERER_HEADER, _deviceURI)
                   .header("x-isi-ifs-target-type", "container")
                   .header("x-isi-ifs-access-control", "0755")
                   .accept(MediaType.APPLICATION_JSON);
       }

       return resBuilder;
    }

}
