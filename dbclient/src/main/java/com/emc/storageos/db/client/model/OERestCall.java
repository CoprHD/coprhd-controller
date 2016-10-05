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
package com.emc.storageos.db.client.model;

import java.net.URI;

@Cf("OERestCall")
public class OERestCall extends OEPrimitive {

    private static final long serialVersionUID = 1L;

    private URI _hostname;
    private URI _port;
    private URI _uri;
    private URI _method;
    private URI _scheme;
    private URI _contentType;
    private URI _accept;
    private StringSet _extraHeaders;
    private StringSet _query;
    private URI body;

    @Name("hostname")
    @RelationIndex(cf = "RelationIndex", type = OEAttribute.class)
    public URI getHostname() {
        return _hostname;
    }

    public void setHostname(final URI hostname) {
        _hostname = hostname;
    }

    @Name("port")
    @RelationIndex(cf = "RelationIndex", type = OEAttribute.class)
    public URI getPort() {
        return _port;
    }

    public void setPort(final URI port) {
        _port = port;
    }

    @Name("uri")
    @RelationIndex(cf = "RelationIndex", type = OEAttribute.class)
    public URI getUri() {
        return _uri;
    }

    public void setUri(final URI uri) {
        _uri = uri;
    }

    @Name("method")
    @RelationIndex(cf = "RelationIndex", type = OEAttribute.class)
    public URI getMethod() {
        return _method;
    }

    public void setMethod(final URI method) {
        _method = method;
    }

    @Name("method")
    @RelationIndex(cf = "RelationIndex", type = OEAttribute.class)
    public URI getScheme() {
        return _scheme;
    }

    public void setScheme(final URI scheme) {
        _scheme = scheme;
    }

    @Name("contentType")
    @RelationIndex(cf = "RelationIndex", type = OEAttribute.class)
    public URI getContentType() {
        return _contentType;
    }

    public void setContentType(final URI contentType) {
        this._contentType = contentType;
    }

    @Name("accept")
    @RelationIndex(cf = "RelationIndex", type = OEAttribute.class)
    public URI getAccept() {
        return _accept;
    }

    public void setAccept(final URI accept) {
        this._accept = accept;
    }

    @Name("extraHeaders")
    @RelationIndex(cf = "RelationIndex", type = OEAttribute.class)
    public StringSet getExtraHeaders() {
        return _extraHeaders;
    }

    public void setExtraHeaders(final StringSet headers) {
        _extraHeaders = headers;
    }

    @Name("query")
    @RelationIndex(cf = "RelationIndex", type = OEAttribute.class)
    public StringSet getQuery() {
        return _query;
    }

    public void setQuery(final StringSet query) {
        _query = query;
    }

    @Name("body")
    @RelationIndex(cf = "RelationIndex", type = OEAttribute.class)
    public URI getBody() {
        return body;
    }

    public void setBody(final URI body) {
        this.body = body;
    }

    @Override
    public boolean isRestCall() {
        return true;
    }

    @Override
    public OERestCall asRestCall() {
        return this;
    }
}
