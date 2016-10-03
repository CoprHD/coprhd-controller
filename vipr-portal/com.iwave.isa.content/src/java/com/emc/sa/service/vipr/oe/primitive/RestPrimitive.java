/*
 * Copyright 2016 Dell Inc. or its subsidiaries.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.emc.sa.service.vipr.oe.primitive;

import java.io.IOException;
import java.util.Map;

import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.OERestCall;
import com.emc.storageos.db.client.model.StringSet;

public class RestPrimitive extends Primitive {

    private final String _hostname;
    private final String _port;
    private final String _uri;
    private final String _method;
    private final String _scheme;
    private final String _contentType;
    private final String _accept;
    private final StringSet _extraHeaders;
    private final String _body;
    private final StringSet _query;

    public RestPrimitive(final OERestCall restPrimitive)
            throws ClassNotFoundException, IOException {
        super(restPrimitive);
        _hostname = restPrimitive.getHostname();
        _port = restPrimitive.getPort();
        _uri = restPrimitive.getUri();
        _method = restPrimitive.getMethod();
        _scheme = restPrimitive.getScheme();
        _contentType = restPrimitive.getContentType();
        _accept = restPrimitive.getAccept();
        _extraHeaders = restPrimitive.getExtraHeaders();
        _body = restPrimitive.getBody();
        _query = restPrimitive.getQuery();
    }

    public RestPrimitive(final NamedURI name, final NamedURI parent,
            final String description, final String successCriteria,
            final Map<String, AbstractParameter<?>> input,
            final Map<String, AbstractParameter<?>> output,
            final String hostname, final String port, final String uri,
            final String method, final String scheme, final String contentType,
            final String accept, final StringSet extraHeaders,
            final String body, final StringSet query) {
        super(name, parent, description, successCriteria, input, output);
        _hostname = hostname;
        _port = port;
        _uri = uri;
        _method = method;
        _scheme = scheme;
        _contentType = contentType;
        _accept = accept;
        _extraHeaders = extraHeaders;
        _body = body;
        _query = query;
    }

    @Override
    public boolean isRestPrimitive() {
        return true;
    }

    @Override
    public RestPrimitive asRestPrimitive() {
        return this;
    }

    public String hostname() {
        return _hostname;
    }

    public String port() {
        return _port;
    }

    public String uri() {
        return _uri;
    }

    public String method() {
        return _method;
    }

    public String scheme() {
        return _scheme;
    }

    public String contentType() {
        return _contentType;
    }

    public String accept() {
        return _accept;
    }

    public StringSet extraHeaders() {
        return _extraHeaders;
    }

    public String body() {
        return _body;
    }

    public StringSet query() {
        return _query;
    }
}
