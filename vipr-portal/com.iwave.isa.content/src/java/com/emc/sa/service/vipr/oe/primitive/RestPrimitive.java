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

import java.net.URI;
import java.util.Map;
import java.util.Set;

/**
 * Class to contain the REST primitive properties
 */
public class RestPrimitive extends Primitive {

    private final String _hostname;
    private final String _port;
    private final String _uri;
    private final String _method;
    private final String _scheme;
    private final String _contentType;
    private final String _accept;
    private final Set<String> _extraHeaders;
    private final String _body;
    private final Set<String> _query;

    public RestPrimitive(final URI id, final String name, final URI parent,
            final String description, final String successCriteria,
            final Map<String, AbstractParameter<?>> input,
            final Map<String, AbstractParameter<?>> output,
            final String hostname, final String port, final String uri,
            final String method, final String scheme, final String contentType,
            final String accept, final Set<String> extraHeaders,
            final String body, final Set<String> query) {
        super(id, name, parent, description, successCriteria, input, output);
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

    public Set<String> extraHeaders() {
        return _extraHeaders;
    }

    public String body() {
        return _body;
    }

    public Set<String> query() {
        return _query;
    }
}
