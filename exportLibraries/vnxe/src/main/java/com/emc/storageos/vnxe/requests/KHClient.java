/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
import com.sun.jersey.api.client.filter.LoggingFilter;
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

    public WebResource getResource() {
        return _resource;
    }

    public WebResource getResource(String url) {
        return _client.resource(url);
    }

    public synchronized Set<NewCookie> get_cookie() {
        return _cookie;
    }

    public synchronized void set_cookie(Set<NewCookie> _cookie) {
        this._cookie = _cookie;
    }

}
