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

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.OEPrimitive;
import com.emc.storageos.db.client.model.OERestCall;
import com.emc.storageos.db.client.model.StringSet;

public class PrimitiveHelper {

    private PrimitiveHelper() {
    };

    public static Primitive loadPrimitive(final NamedURI uri,
            final DbClient dbClient) throws InstantiationException,
            IllegalAccessException, ClassNotFoundException, IOException {
        return makePrimitive(uri, dbClient);
    }

    public static void savePrimitive(final Primitive primitive,
            final DbClient dbClient) throws InstantiationException,
            IllegalAccessException, ClassNotFoundException, IOException {
        final Primitive diff;
        if (primitive.parent().toString().isEmpty()) {
            diff = primitive;
        } else {
            diff = diff(primitive, makePrimitive(primitive.parent(), dbClient));
        }
        // final OEPrimitive existing = dbClient.queryObject(
        // oePrimitive.getClass(), oePrimitive.getName());
        // if (null != existing) {
        // oePrimitive.setId(existing.getId());
        // dbClient.updateObject(oePrimitive);
        // } else {
        // oePrimitive.setId(URIUtil.createId(oePrimitive.getClass()));
        // dbClient.createObject(oePrimitive);
        // }
    }

    private static Primitive makePrimitive(final NamedURI uri,
            final DbClient dbClient) throws ClassNotFoundException, IOException {
        final Primitive primitive = query(uri, dbClient);

        return (primitive.parent().toString().isEmpty()) ? primitive : merge(
                primitive, makePrimitive(primitive.parent(), dbClient));
    }

    private static Primitive merge(final Primitive child, final Primitive parent) {
        final PrimitiveBuilder primitiveBuilder = new PrimitiveBuilder();
        primitiveBuilder.name(child.name());
        primitiveBuilder.parent(parent.name());
        primitiveBuilder.description(child.description().isEmpty() ? parent
                .description() : child.description());
        primitiveBuilder
                .successCriteria(child.successCriteria().isEmpty() ? parent
                        .successCriteria() : child.successCriteria());
        primitiveBuilder.input(ParameterHelper.merge(child.input(),
                parent.input()));
        primitiveBuilder.output(ParameterHelper.merge(child.output(),
                parent.output()));
        if (child.isRestPrimitive()) {
            return makeRestPrimitive(primitiveBuilder, child.asRestPrimitive(),
                    parent.asRestPrimitive());
        } else {
            throw new RuntimeException();
        }
    }

    private static RestPrimitive makeRestPrimitive(final PrimitiveBuilder base,
            final RestPrimitive child, final RestPrimitive parent) {
        final RestPrimitiveBuilder builder = new RestPrimitiveBuilder(base);
        builder.hostname(child.hostname().isEmpty() ? parent.hostname() : child
                .hostname());
        builder.port(child.port().isEmpty() ? parent.port() : child.port());
        builder.uri(child.uri().isEmpty() ? parent.uri() : child.uri());
        builder.method(child.method().isEmpty() ? parent.method() : child
                .method());
        builder.scheme(child.scheme().isEmpty() ? parent.scheme() : child
                .scheme());
        builder.contentType(child.contentType().isEmpty() ? parent
                .contentType() : child.contentType());
        builder.accept(child.accept().isEmpty() ? parent.accept() : child
                .accept());
        builder.body(child.body().isEmpty() ? parent.body() : child.body());

        final StringSet extraHeaders = new StringSet(parent.extraHeaders());
        extraHeaders.addAll(child.extraHeaders());
        builder.extraHeaders(extraHeaders);

        final StringSet query = new StringSet(parent.query());
        query.addAll(child.query());
        builder.query(query);

        return builder.build();
    }

    private static Primitive query(final NamedURI uri, final DbClient dbClient)
            throws ClassNotFoundException, IOException {
        final Class<? extends OEPrimitive> type = type(uri);
        final OEPrimitive oePrimitive = dbClient.queryObject(type, uri);
        if (oePrimitive.isRestCall()) {
            return new RestPrimitive(oePrimitive.asRestCall());
        } else {
            throw new RuntimeException();
        }
    }

    private static Class<? extends OEPrimitive> type(final NamedURI uri) {
        final Class<?> type = URIUtil.getModelClass(uri.getURI());
        if (!type.isAssignableFrom(OEPrimitive.class)) {
            throw new RuntimeException(uri
                    + " is not an identifier for a primitive");
        }
        return type.asSubclass(OEPrimitive.class);
    }

    private static Primitive diff(final Primitive child, final Primitive parent) {
        final PrimitiveBuilder primitiveBuilder = new PrimitiveBuilder();
        primitiveBuilder.name(child.name());
        primitiveBuilder.parent(child.parent());
        if (child.description().isEmpty()
                || child.description().equals(parent.description())) {
            primitiveBuilder.description("");
        } else {
            primitiveBuilder.description(child.description());
        }
        // TODO rest of the fields
        // TODO make the rest part
    }

    public static OEPrimitive toOEPrimitive(final Primitive primitive) {
        final OEPrimitive oePrimitive;
        if (primitive.isRestPrimitive()) {
            oePrimitive = makeOERestCall(primitive.asRestPrimitive());

        } else {
            throw new RuntimeException();
        }
        oePrimitive.setParent(primitive.parent());
        oePrimitive.setName(primitive.name());
        oePrimitive.setDescription(primitive.description());
        oePrimitive.setSuccessCriteria(primitive.successCriteria());
        oePrimitive.setInput(ParameterHelper.toStringMap(primitive.input()));
        oePrimitive.setOutput(ParameterHelper.toStringMap(primitive.output()));
        return oePrimitive;
    }

    private static OERestCall makeOERestCall(final RestPrimitive primitive) {
        final OERestCall restCall = new OERestCall();
        restCall.setHostname(primitive.hostname());
        restCall.setPort(primitive.port());
        restCall.setUri(primitive.uri());
        restCall.setMethod(primitive.method());
        restCall.setScheme(primitive.scheme());
        restCall.setContentType(primitive.contentType());
        restCall.setAccept(primitive.accept());
        restCall.setExtraHeaders(primitive.extraHeaders());
        restCall.setQuery(primitive.query());
        return restCall;
    }

    private static class RestPrimitiveBuilder {
        private final PrimitiveBuilder _primitiveBuilder;

        private String _hostname;
        private String _port;
        private String _uri;
        private String _method;
        private String _scheme;
        private String _contentType;
        private String _accept;
        private StringSet _extraHeaders;
        private String _body;
        private StringSet _query;

        public RestPrimitiveBuilder(final PrimitiveBuilder primitiveBuilder) {
            _primitiveBuilder = primitiveBuilder;
        }

        public void hostname(final String hostname) {
            _hostname = hostname;
        }

        public void port(final String port) {
            _port = port;
        }

        public void uri(final String uri) {
            _uri = uri;
        }

        public void method(final String method) {
            _method = method;
        }

        public void scheme(final String scheme) {
            _scheme = scheme;
        }

        public void contentType(final String contentType) {
            _contentType = contentType;
        }

        public void accept(final String accept) {
            _accept = accept;
        }

        public void extraHeaders(final StringSet extraHeaders) {
            _extraHeaders = extraHeaders;
        }

        public void body(final String body) {
            _body = body;
        }

        public void query(final StringSet query) {
            _query = query;
        }

        public RestPrimitive build() {
            return new RestPrimitive(_primitiveBuilder.name(),
                    _primitiveBuilder.parent(),
                    _primitiveBuilder.description(),
                    _primitiveBuilder.successCriteria(),
                    _primitiveBuilder.input(), _primitiveBuilder.output(),
                    _hostname, _port, _uri, _method, _scheme, _contentType,
                    _accept, _extraHeaders, _body, _query);
        }
    }

    private static class PrimitiveBuilder {
        protected NamedURI _name;
        protected NamedURI _parent;
        protected String _description;
        protected String _successCriteria;
        protected Map<String, AbstractParameter<?>> _input;
        protected Map<String, AbstractParameter<?>> _output;

        public NamedURI name() {
            return _name;
        }

        public void name(final NamedURI name) {
            _name = name;
        }

        public NamedURI parent() {
            return _parent;
        }

        public void parent(final NamedURI parent) {
            _parent = parent;
        }

        public String description() {
            return _description;
        }

        public void description(final String description) {
            _description = description;
        }

        public String successCriteria() {
            return _successCriteria;
        }

        public void successCriteria(final String successCriteria) {
            _successCriteria = successCriteria;
        }

        public Map<String, AbstractParameter<?>> input() {
            return _input;
        }

        public void input(final Map<String, AbstractParameter<?>> input) {
            _input = input;
        }

        public Map<String, AbstractParameter<?>> output() {
            return _output;
        }

        public void output(final Map<String, AbstractParameter<?>> output) {
            _output = output;
        }

    }
}
