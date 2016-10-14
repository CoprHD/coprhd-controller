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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.constraint.impl.AlternateIdConstraintImpl;
import com.emc.storageos.db.client.impl.TypeMap;
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

    private PrimitiveHelper() {};

    
    public static <T extends Primitive> T query(final String name, final Class<T> clazz, final DbClient dbClient) {
        
        final URI id = getPrimitiveId(name, getDBModel(clazz), dbClient);

        return id == null ? null : clazz.cast(query(id, dbClient));
    }
    
    /**
     * Load a Primitive from persistence by querying the database model and
     * converting it to the Primitive type that the UI/execution will understand
     */
    public static Primitive query(final URI uri, final DbClient dbClient) {
        final Class<? extends OEPrimitive> type = type(uri);
        final OEPrimitive oePrimitive = dbClient.queryObject(type, uri);
        if (null == oePrimitive) {
            return null;
        }

        final PrimitiveBuilder primitiveBuilder = new PrimitiveBuilder();
        primitiveBuilder.id(oePrimitive.getId());
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
    public static void persist(final Primitive primitive,
            final DbClient dbClient) {

        final OEPrimitive existing = dbClient.queryObject(OEPrimitive.class,
                primitive.id());
        if (null == existing) {
            createPrimitive(primitive, dbClient);
        } else {
            updatePrimitive(primitive, existing, dbClient);
        }
    }

    /**
     * Get the ID for a primitive given the name and type.
     * 
     * @throws IllegalStateException if more than one primitive exists with the name
     * 
     * @return URI id of the primitive
     */
    private static URI getPrimitiveId(final String name, final Class<? extends OEPrimitive> clazz, final DbClient dbClient) {
        
        final AlternateIdConstraint constraint = new AlternateIdConstraintImpl(
                TypeMap.getDoType(clazz).getColumnField("name"), name);
        final NamedElementQueryResultList results = new NamedElementQueryResultList();
        
        dbClient.queryByConstraint(constraint, results);
        
        final Iterator<NamedElement> it = results.iterator();
        
        if( null == it || !it.hasNext()) {
            return null;
        }
        
        final NamedElement element = it.next();
        
        if( it.hasNext() ) {
            throw new IllegalStateException("Multiple primitives with name " + element.getName());
        }
        
        return element.getId();
    }
    
    /**
     * Create a new primitive database object
     */
    private static void createPrimitive(final Primitive primitive,
            final DbClient dbClient) {

        if (null != getPrimitiveId(primitive.name(), getDBModel(primitive.getClass()), dbClient)) {
            throw new IllegalStateException("Primitive with name "
                    + primitive.name() + " already exists");
        }
        final OEPrimitive basePrimitive;
        if (!NullColumnValueGetter.isNullURI(primitive.parent())) {
            basePrimitive = dbClient.queryObject(OEPrimitive.class,
                    primitive.parent());
        } else {
            if (primitive.isRestPrimitive()) {
                basePrimitive = new OERestCall();
            } else {
                throw new IllegalStateException("Invalid primitive type");
            }
        }
        dbClient.createObject(makeOEPrimitive(dbClient, primitive,
                basePrimitive));
    }
    
    private static Class<? extends OEPrimitive> getDBModel(final Class<? extends Primitive> clazz) {
        if(clazz.isAssignableFrom(RestPrimitive.class)) {
            return OERestCall.class;
        }
        return null;
    }

    /**
     * Update the primitive database object
     */
    private static void updatePrimitive(final Primitive primitive,
            final OEPrimitive existing, final DbClient dbClient) {
        if (!primitive.id().equals(existing.getId())) {
            throw new IllegalStateException("Cannot update the primitive ID");
        } else if (!primitive.parent().equals(existing.getParent())) {
            throw new IllegalStateException(
                    "Cannot update the primitive parent ID");
        }
        dbClient.updateObject(makeOEPrimitive(dbClient, primitive, existing));
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
        oePrimitive.setId(primitive.id());
        oePrimitive.setName(primitive.name());
        oePrimitive.setParent(primitive.parent());
        oePrimitive.setDescription(createOrUpdateAttribute(dbClient,
                primitive.id(), basePrimitive.getDescription(),
                primitive.description()));
        oePrimitive.setSuccessCriteria(createOrUpdateAttribute(dbClient,
                primitive.id(), basePrimitive.getSuccessCriteria(),
                primitive.successCriteria()));

        final StringSet input = basePrimitive.getInput() == null ? new StringSet() : basePrimitive.getInput();
        ParameterHelper.updateParameterStringSet(dbClient, primitive.id(),
                primitive.input(), input);
        oePrimitive.setInput(input);

        final StringSet output = basePrimitive.getOutput() == null ? new StringSet() : basePrimitive.getOutput();
        ParameterHelper.updateParameterStringSet(dbClient, primitive.id(),
                primitive.output(), output);
        oePrimitive.setOutput(output);

        if (primitive.isRestPrimitive()) {
            if (!basePrimitive.isRestCall())
                throw new RuntimeException();
            final OERestCall baseRestCall = basePrimitive.asRestCall();
            final OERestCall oeRestCall = oePrimitive.asRestCall();
            final RestPrimitive restPrimitive = primitive.asRestPrimitive();

            oeRestCall.setHostname(createOrUpdateAttribute(dbClient,
                    primitive.id(), baseRestCall.getHostname(),
                    restPrimitive.hostname()));
            oeRestCall.setPort(createOrUpdateAttribute(dbClient,
                    primitive.id(), baseRestCall.getPort(),
                    restPrimitive.port()));
            oeRestCall.setUri(createOrUpdateAttribute(dbClient, primitive.id(),
                    baseRestCall.getUri(), restPrimitive.uri()));
            oeRestCall.setMethod(createOrUpdateAttribute(dbClient,
                    primitive.id(), baseRestCall.getMethod(),
                    restPrimitive.method()));
            oeRestCall.setScheme(createOrUpdateAttribute(dbClient,
                    primitive.id(), baseRestCall.getScheme(),
                    restPrimitive.scheme()));
            oeRestCall.setContentType(createOrUpdateAttribute(dbClient,
                    primitive.id(), baseRestCall.getContentType(),
                    restPrimitive.contentType()));
            oeRestCall.setAccept(createOrUpdateAttribute(dbClient,
                    primitive.id(), baseRestCall.getAccept(),
                    restPrimitive.accept()));
            final StringSet extraHeaders = baseRestCall.getExtraHeaders() == null ? new StringSet() : baseRestCall.getExtraHeaders();
            oeRestCall.setExtraHeaders(createOrUpdateAttributeSet(dbClient,
                    primitive.id(), extraHeaders,
                    restPrimitive.extraHeaders()));
            final StringSet query = baseRestCall.getQuery() == null ? new StringSet() : baseRestCall.getQuery();
            oeRestCall.setQuery(createOrUpdateAttributeSet(dbClient,
                    primitive.id(), query,
                    restPrimitive.query()));
            oeRestCall.setBody(createOrUpdateAttribute(dbClient,
                    primitive.id(), baseRestCall.getBody(),
                    restPrimitive.body()));
        }
        return oePrimitive;

    }

    /**
     * Given a Set of attribute values create or update a StringSet of URIs
     */
    private static StringSet createOrUpdateAttributeSet(
            final DbClient dbClient, final URI primitive, final StringSet uris,
            final Set<String> values) {
        final Set<String> present = new HashSet<String>();
        final Set<String> newValues = new HashSet<String>(values);
        final Set<URI> removed = new HashSet<URI>();

        if (!uris.isEmpty()) {
            final List<OEAttribute> attributes = dbClient.queryObject(
                    OEAttribute.class, URIUtil.toURIList(uris));

            for (final OEAttribute attribute : attributes) {
                if (values.contains(attribute.getValue())) {
                    present.add(attribute.getValue());
                } else {
                    removed.add(attribute.getId());
                    if (attribute.getPrimitive().equals(primitive)) {
                        dbClient.markForDeletion(attribute);
                    }
                }
            }
        }
        newValues.removeAll(present);
        for (final String newValue : newValues) {
            final OEAttribute attribute = new OEAttribute();
            attribute.setId(URIUtil.createId(OEAttribute.class));
            attribute.setPrimitive(primitive);
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
            final URI primitive, final URI attribute, final String value) {

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

    private static Class<? extends OEPrimitive> type(final URI uri) {
        final Class<?> type = URIUtil.getModelClass(uri);
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
            return new RestPrimitive(_primitiveBuilder.id(),
                    _primitiveBuilder.name(), _primitiveBuilder.parent(),
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
        private URI _id;
        private String _name;
        private URI _parent;
        private String _description;
        private String _successCriteria;
        private Map<String, AbstractParameter<?>> _input;
        private Map<String, AbstractParameter<?>> _output;

        public URI id() {
            return _id;
        }

        public void id(final URI id) {
            _id = id;
        }

        public String name() {
            return _name;
        }

        public void name(final String name) {
            _name = name;
        }

        public URI parent() {
            return _parent;
        }

        public void parent(final URI parent) {
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
