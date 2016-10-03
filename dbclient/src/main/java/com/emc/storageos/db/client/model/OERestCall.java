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

@Cf("OERestCall")
public class OERestCall extends OEPrimitive {

    private static final long serialVersionUID = 1L;

    private String _hostname;
    private String _port;
    private String _uri;
    private String _method;
    private String _scheme;
    private String _contentType;
    private String _accept;
    private StringSet _extraHeaders;
    private StringSet _query;
    private String body;

    @Name("hostname")
    public String getHostname() {
        return _hostname;
    }

    public void setHostname(final String hostname) {
        _hostname = hostname;
    }

    @Name("port")
    public String getPort() {
        return _port;
    }

    public void setPort(final String port) {
        _port = port;
    }

    @Name("uri")
    public String getUri() {
        return _uri;
    }

    public void setUri(final String uri) {
        _uri = uri;
    }

    @Name("method")
    public String getMethod() {
        return _method;
    }

    public void setMethod(final String method) {
        _method = method;
    }

    @Name("scheme")
    public String getScheme() {
        return _scheme;
    }

    public void setScheme(final String scheme) {
        _scheme = scheme;
    }

    @Name("contentType")
    public String getContentType() {
        return _contentType;
    }

    public void setContentType(final String contentType) {
        this._contentType = contentType;
    }

    @Name("accept")
    public String getAccept() {
        return _accept;
    }

    public void setAccept(final String accept) {
        this._accept = accept;
    }

    @Name("extraHeaders")
    public StringSet getExtraHeaders() {
        return _extraHeaders;
    }

    public void setExtraHeaders(final StringSet headers) {
        _extraHeaders = headers;
    }

    @Name("query")
    public StringSet getQuery() {
        return _query;
    }

    public void setQuery(final StringSet query) {
        _query = query;
    }

    @Name("body")
    public String getBody() {
        return body;
    }

    public void setBody(final String body) {
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
