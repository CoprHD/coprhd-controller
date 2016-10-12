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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.OEAttribute;
import com.emc.storageos.db.client.model.OEPrimitive;
import com.emc.storageos.db.client.model.OERestCall;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.util.NullColumnValueGetter;

/**
 * Helper class to load/save primitives in persistence. This way the
 * UI/Execution engine doesn't need to deal with the primitive inheritance
 * model.
 */
public final class PrimitiveHelper {

    private PrimitiveHelper() {
    };

    /**
     * Load a Primitive from persistence by querying the database model and
     * converting it to the Primitive type that the UI/execution will understand
     */
    public static Primitive query(final NamedURI uri, final DbClient dbClient) {
        final Class<? extends OEPrimitive> type = type(uri);
        final OEPrimitive oePrimitive = dbClient.queryObject(type, uri);

        final PrimitiveBuilder primitiveBuilder = new PrimitiveBuilder();
        primitiveBuilder.name(oePrimitive.getName());
        primitiveBuilder.description(queryAttribute(dbClient,
                oePrimitive.getDescription()));
        primitiveBuilder.parent(oePrimitive.getParent());
        primitiveBuilder.successCriteria(queryAttribute(dbClient,
                oePrimitive.getSuccessCriteria()));
        primitiveBuilder.input(ParameterHelper.toParameterMap(dbClient,
                oePrimitive.getInput()));
        primitiveBuilder.output(ParameterHelper.toParameterMap(dbClient,
                oePrimitive.getOutput()));
        if (oePrimitive.isRestCall()) {
            final RestPrimitiveBuilder builder = new RestPrimitiveBuilder(
                    primitiveBuilder);
            final OERestCall oeRestCall = oePrimitive.asRestCall();
            builder.hostname(queryAttribute(dbClient, oeRestCall.getHostname()));
            builder.port(queryAttribute(dbClient, oeRestCall.getPort()));
            builder.uri(queryAttribute(dbClient, oeRestCall.getUri()));
            builder.method(queryAttribute(dbClient, oeRestCall.getMethod()));
            builder.scheme(queryAttribute(dbClient, oeRestCall.getScheme()));
            builder.contentType(queryAttribute(dbClient,
                    oeRestCall.getContentType()));
            builder.accept(queryAttribute(dbClient, oeRestCall.getAccept()));
            builder.extraHeaders(queryAttributeSet(dbClient,
                    oeRestCall.getExtraHeaders()));
            builder.body(queryAttribute(dbClient, oeRestCall.getBody()));
            builder.query(queryAttributeSet(dbClient, oeRestCall.getQuery()));
            return builder.build();

        } else {
            throw new RuntimeException();
        }
    }

    /**
     * Save a primitive to persistence. Either update it or create it if the
     * name is new
     */
    public static void persist(final Primitive primitive, final DbClient dbClient) {
        final OEPrimitive existing = dbClient.queryObject(OEPrimitive.class,
                primitive.name());

        if (null != existing) {
            dbClient.updateObject(makeOEPrimitive(dbClient, primitive, existing));
        } else {
            final OEPrimitive basePrimitive;
            if (!NullColumnValueGetter.isNullNamedURI(primitive.parent())) {
                basePrimitive = dbClient.queryObject(OEPrimitive.class,
                        primitive.parent());
            } else {
                if (primitive.isRestPrimitive()) {
                    basePrimitive = new OERestCall();
                } else {
                    throw new RuntimeException("Invalid primitive type");
                }
                final OEPrimitive temp = makeOEPrimitive(dbClient, primitive,
                        basePrimitive);
                dbClient.createObject(temp);
            }
        }

    }

    /**
     * Make a primitive persistence object given a Primitive. The primitive
     * persistence object is a 'diff' with the base primitive that is passed in.
     */
    private static OEPrimitive makeOEPrimitive(final DbClient dbClient,
            final Primitive primitive, final OEPrimitive basePrimitive) {

        final OEPrimitive oePrimitive;
        try {
            oePrimitive = basePrimitive.getClass().newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        oePrimitive.setId(primitive.name().getURI());
        oePrimitive.setName(primitive.name());
        oePrimitive.setParent(primitive.parent());
        oePrimitive.setDescription(createOrUpdateAttribute(dbClient,
                primitive.name(), basePrimitive.getDescription(),
                primitive.description()));
        oePrimitive.setSuccessCriteria(createOrUpdateAttribute(dbClient,
                primitive.name(), basePrimitive.getSuccessCriteria(),
                primitive.successCriteria()));

        if (null == basePrimitive.getInput()) {
            basePrimitive.setInput(new StringSet());
        }
        if (null == basePrimitive.getOutput()) {
            basePrimitive.setOutput(new StringSet());
        }
        ParameterHelper.updateParameterStringSet(dbClient, primitive.name(),
                primitive.input(), basePrimitive.getInput());
        oePrimitive.setInput(basePrimitive.getInput());

        ParameterHelper.updateParameterStringSet(dbClient, primitive.name(),
                primitive.output(), basePrimitive.getOutput());
        oePrimitive.setOutput(basePrimitive.getOutput());

        if (primitive.isRestPrimitive()) {
            if (!basePrimitive.isRestCall())
                throw new RuntimeException();
            final OERestCall baseRestCall = basePrimitive.asRestCall();
            final OERestCall oeRestCall = oePrimitive.asRestCall();
            final RestPrimitive restPrimitive = primitive.asRestPrimitive();

            oeRestCall.setHostname(createOrUpdateAttribute(dbClient,
                    primitive.name(), baseRestCall.getHostname(),
                    restPrimitive.hostname()));
            oeRestCall.setPort(createOrUpdateAttribute(dbClient,
                    primitive.name(), baseRestCall.getPort(),
                    restPrimitive.port()));
            oeRestCall.setUri(createOrUpdateAttribute(dbClient,
                    primitive.name(), baseRestCall.getUri(),
                    restPrimitive.uri()));
            oeRestCall.setMethod(createOrUpdateAttribute(dbClient,
                    primitive.name(), baseRestCall.getMethod(),
                    restPrimitive.method()));
            oeRestCall.setScheme(createOrUpdateAttribute(dbClient,
                    primitive.name(), baseRestCall.getScheme(),
                    restPrimitive.scheme()));
            oeRestCall.setContentType(createOrUpdateAttribute(dbClient,
                    primitive.name(), baseRestCall.getContentType(),
                    restPrimitive.contentType()));
            oeRestCall.setAccept(createOrUpdateAttribute(dbClient,
                    primitive.name(), baseRestCall.getAccept(),
                    restPrimitive.accept()));
            oeRestCall.setExtraHeaders(createOrUpdateAttributeSet(dbClient,
                    primitive.name(), baseRestCall.getExtraHeaders(),
                    restPrimitive.extraHeaders()));
            oeRestCall.setQuery(createOrUpdateAttributeSet(dbClient,
                    primitive.name(), baseRestCall.getQuery(),
                    restPrimitive.query()));
            oeRestCall.setBody(createOrUpdateAttribute(dbClient,
                    primitive.name(), baseRestCall.getBody(),
                    restPrimitive.body()));
        }
        return oePrimitive;

    }

    /**
     * Given a Set of attribute values create or update a StringSet of URIs
     */
    private static StringSet createOrUpdateAttributeSet(
            final DbClient dbClient, final NamedURI name, StringSet uris,
            final Set<String> values) {
        final Set<String> present = new HashSet<String>();
        final Set<String> newValues = new HashSet<String>(values);
        final Set<URI> removed = new HashSet<URI>();
        if (null == uris) {
            uris = new StringSet();
        } else if (!uris.isEmpty()) {
            final List<OEAttribute> attributes = dbClient.queryObject(
                    OEAttribute.class, URIUtil.toURIList(uris));

            for (final OEAttribute attribute : attributes) {
                if (values.contains(attribute.getValue())) {
                    present.add(attribute.getValue());
                } else {
                    removed.add(attribute.getId());
                    if (attribute.getPrimitive().equals(name)) {
                        dbClient.markForDeletion(attribute);
                    }
                }
            }
        }
        newValues.removeAll(present);
        for (final String newValue : newValues) {
            final OEAttribute attribute = new OEAttribute();
            attribute.setId(URIUtil.createId(OEAttribute.class));
            attribute.setPrimitive(name);
            attribute.setValue(newValue);
            dbClient.createObject(attribute);
            uris.add(attribute.getId().toString());
        }

        for (final URI removedValue : removed) {
            uris.remove(removedValue);
        }
        return uris;
    }

    /**
     * Given an attribute value and ID create or update the attribute if
     * necessary
     * 
     */
    private static URI createOrUpdateAttribute(final DbClient dbClient,
            final NamedURI primitive, final URI attribute, final String value) {

        OEAttribute oeAttribute = dbClient.queryObject(OEAttribute.class,
                attribute);
        if (null == oeAttribute) {
            oeAttribute = new OEAttribute();
            oeAttribute.setId(URIUtil.createId(OEAttribute.class));
            oeAttribute.setValue(value);
            oeAttribute.setPrimitive(primitive);
            dbClient.createObject(oeAttribute);
        } else if (!oeAttribute.getValue().equals(value)) {
            if (oeAttribute.getPrimitive().equals(primitive)) {
                oeAttribute.setValue(value);
                dbClient.updateObject(oeAttribute);
            } else {
                oeAttribute.setId(URIUtil.createId(OEAttribute.class));
                oeAttribute.setValue(value);
                oeAttribute.setPrimitive(primitive);
                dbClient.createObject(oeAttribute);
            }
        }

        return oeAttribute.getId();
    }

    /**
     * @param dbClient
     * @param query
     * @return
     */
    private static Set<String> queryAttributeSet(final DbClient dbClient,
            final StringSet uris) {
        if (uris == null || uris.isEmpty()) {
            return new HashSet<String>();
        } else {
            final List<OEAttribute> attributes = dbClient.queryObject(
                    OEAttribute.class, URIUtil.toURIList(uris));
            final Set<String> attributeValues = new HashSet<String>();
            for (final OEAttribute attribute : attributes) {
                attributeValues.add(attribute.getValue());
            }
            return attributeValues;
        }
    }

    /**
     * Get the value of an attribute given a URI
     */
    private static String queryAttribute(final DbClient dbClient, final URI uri) {
        final OEAttribute attribute = dbClient.queryObject(OEAttribute.class,
                uri);

        return null == attribute ? "" : attribute.getValue();
    }

    private static Class<? extends OEPrimitive> type(final NamedURI uri) {
        final Class<?> type = URIUtil.getModelClass(uri.getURI());
        if (!OEPrimitive.class.isAssignableFrom(type)) {
            throw new RuntimeException(uri
                    + " is not an identifier for a primitive");
        }
        return type.asSubclass(OEPrimitive.class);
    }

    /**
     * Builder class to build a REST Primitive
     */
    private static class RestPrimitiveBuilder {
        private final PrimitiveBuilder _primitiveBuilder;

        private String _hostname;
        private String _port;
        private String _uri;
        private String _method;
        private String _scheme;
        private String _contentType;
        private String _accept;
        private Set<String> _extraHeaders;
        private String _body;
        private Set<String> _query;

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

        public void extraHeaders(final Set<String> extraHeaders) {
            _extraHeaders = extraHeaders;
        }

        public void body(final String body) {
            _body = body;
        }

        public void query(final Set<String> query) {
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

    /**
     * Builder class to contain the AbstractPrimitive properties
     */
    private static class PrimitiveBuilder {
        private NamedURI _name;
        private NamedURI _parent;
        private String _description;
        private String _successCriteria;
        private Map<String, AbstractParameter<?>> _input;
        private Map<String, AbstractParameter<?>> _output;

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
