/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import java.net.URI;
import java.net.URISyntaxException;

import java.util.Set;

import javax.ws.rs.core.NewCookie;

import org.apache.commons.httpclient.protocol.Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.client.apache.ApacheHttpClient;
import com.sun.jersey.client.apache.config.ApacheHttpClientConfig;
import com.sun.jersey.client.apache.config.DefaultApacheHttpClientConfig;

/*
 * This is the class to communicate with KittyHawk server
 */
public class KHClient {
    private static final Logger _logger = LoggerFactory.getLogger(KHClient.class);
    private static final String PROTOCOL = "https";
    private static final int PORT = 443;
    private ApacheHttpClient _client;
    private URI _uri;
    private WebResource _resource;
    private Set<NewCookie> _cookie;
    private boolean isUnity = false;
    private String _emcCsrfToken = null;

    public KHClient(String host, int port, String username, String password) {

        DefaultApacheHttpClientConfig config = new DefaultApacheHttpClientConfig();
        config.getProperties().put(ApacheHttpClientConfig.PROPERTY_FOLLOW_REDIRECTS, Boolean.FALSE);
        config.getProperties().put(ApacheHttpClientConfig.PROPERTY_HANDLE_COOKIES, Boolean.TRUE);
        config.getState().setCredentials(null, host, port, username, password);
        _client = ApacheHttpClient.create(config);
        // _client.addFilter(new LoggingFilter(System.out));
        Protocol.registerProtocol("https", new Protocol("https", new NonValidatingSocketFactory(), port));

        try {
            _uri = new URI(PROTOCOL,
                    null, host, port, null, null, null);
        } catch (URISyntaxException e) {
            _logger.error("Could not create URI using host :" + host, e);
        }
        _resource = _client.resource(_uri);

    }

    public KHClient(String host, String username, String password) {
        this(host, PORT, username, password);
    }
   
    public KHClient(String host, int port, String username, String password, boolean isUnity) {
        this(host, port, username, password);
        this.isUnity = isUnity;
    }
    public boolean isUnity(){
	return this.isUnity;
    }

    public WebResource getResource() {
        return _resource;
    }

    public WebResource getResource(String url) {
        return _client.resource(url);
    }

    public synchronized Set<NewCookie> getCookie() {
        return _cookie;
    }

    public synchronized void setCookie(Set<NewCookie> _cookie) {
        this._cookie = _cookie;
    }
    public synchronized String getEmcCsrfToken() {
	return _emcCsrfToken;
    }

    public synchronized void setEmcCsrfToken(String _emcCsrfToken){
	this._emcCsrfToken = _emcCsrfToken;
    }

}
