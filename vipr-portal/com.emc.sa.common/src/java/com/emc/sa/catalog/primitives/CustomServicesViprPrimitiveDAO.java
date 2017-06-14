/*
 * Copyright 2017 Dell Inc. or its subsidiaries.
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
package com.emc.sa.catalog.primitives;

import java.net.URI;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.ModelObject;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveCreateParam;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveRestRep;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveUpdateParam;
import com.emc.storageos.primitives.java.vipr.CustomServicesViPRPrimitive;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

/**
 * Data access object for ViPR primitives
 *
 */
public class CustomServicesViprPrimitiveDAO implements
        CustomServicesPrimitiveDAO<CustomServicesViPRPrimitive> {

    private static final List<NamedElement> EMPTY_RESOURCE_LIST = ImmutableList.<NamedElement> builder().build();
    private final ImmutableMap<URI, CustomServicesViPRPrimitive> PRIMITIVES_MAP;

    public CustomServicesViprPrimitiveDAO(final List<String> viprOperations) {
        final Builder<URI, CustomServicesViPRPrimitive> builder = ImmutableMap.<URI, CustomServicesViPRPrimitive> builder();
        for (final String primitiveClass : viprOperations) {
            Class<?> primitive;
            
            try {
                primitive = Class.forName(primitiveClass);
            } catch (final ClassNotFoundException e1) {
                throw new RuntimeException("Class " + primitiveClass + " not found.", e1);
            }
            
            if(!CustomServicesViPRPrimitive.class.isAssignableFrom(primitive)) {
                throw new RuntimeException("Class " + primitiveClass + " is not a vipr primitive.");
            }
            
            final CustomServicesViPRPrimitive instance;
            try {
                instance = (CustomServicesViPRPrimitive)primitive.newInstance();
            } catch (final IllegalAccessException | InstantiationException e) {
                throw new RuntimeException("Failed to create instance of primitive: " + primitive.getName(), e);
            }

            builder.put(instance.id(), instance);
        }
        PRIMITIVES_MAP = builder.build();
    }

    @Override
    public String getType() {
        return CustomServicesViPRPrimitive.TYPE;
    }

    @Override
    public CustomServicesViPRPrimitive get(URI id) {
        return PRIMITIVES_MAP.get(id);
    }

    @Override
    public CustomServicesViPRPrimitive create(CustomServicesPrimitiveCreateParam param) {
        throw APIException.methodNotAllowed.notSupported();
    }

    @Override
    public CustomServicesViPRPrimitive update(URI id, CustomServicesPrimitiveUpdateParam param) {
        throw APIException.methodNotAllowed.notSupported();
    }

    @Override
    public void deactivate(URI id) {
        throw APIException.methodNotAllowed.notSupported();
    }

    @Override
    public boolean importPrimitive(CustomServicesPrimitiveRestRep operation) {
        throw APIException.methodNotAllowed.notSupported();
    }
    
    @Override
    public List<URI> list() {
        return PRIMITIVES_MAP.keySet().asList();
    }

    @Override
    public String getPrimitiveModel() {
        return CustomServicesViPRPrimitive.class.getSimpleName();
    }

    @Override
    public Iterator<CustomServicesPrimitiveRestRep> bulk(final Collection<URI> ids) {
        ImmutableList.Builder<CustomServicesPrimitiveRestRep> primitives = ImmutableList.<CustomServicesPrimitiveRestRep> builder();
        for (final URI id : ids) {
            final CustomServicesViPRPrimitive primitive = PRIMITIVES_MAP.get(id);
            final ModelObject model = primitive == null ? null : primitive.asModelObject();
            ArgValidator.checkEntityNotNull(model, id, false);
            primitives.add(CustomServicesPrimitiveMapper.map(primitive));
        }

        return primitives.build().iterator();
    }

    @Override
    public CustomServicesViPRPrimitive export(URI id) {
        throw APIException.methodNotAllowed.notSupported();
    }
    
    @Override
    public boolean hasResource() {
        return false;
    }

}
