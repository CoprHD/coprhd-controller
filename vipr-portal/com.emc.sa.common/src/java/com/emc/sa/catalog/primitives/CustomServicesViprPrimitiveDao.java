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
import java.util.Set;

import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;

import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.ModelObject;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveCreateParam;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveRestRep;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveUpdateParam;
import com.emc.storageos.primitives.CustomServicesNoResourceType;
import com.emc.storageos.primitives.ViPRPrimitive;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

public class CustomServicesViprPrimitiveDao implements
        CustomServicesPrimitiveDAO<ViPRPrimitive, CustomServicesNoResourceType> {

    private static final List<NamedElement> EMPTY_RESOURCE_LIST = ImmutableList.<NamedElement>builder().build();
    private final ImmutableMap<URI, ViPRPrimitive> PRIMITIVES_MAP;
    
    public CustomServicesViprPrimitiveDao() {
     
        final Set<Class<? extends ViPRPrimitive>> primitives = 
                new Reflections("com.emc.storageos", new SubTypesScanner()).getSubTypesOf(ViPRPrimitive.class);
        final Builder<URI, ViPRPrimitive> builder = ImmutableMap.<URI, ViPRPrimitive>builder();
        for( final Class<? extends ViPRPrimitive> primitive : primitives) {
            final ViPRPrimitive instance;
            try {
                instance = primitive.newInstance();
            } catch (final IllegalAccessException | InstantiationException e) {
                throw new RuntimeException("Failed to create instance of primitive: "+primitive.getName(), e);
            }

            builder.put(instance.id(), instance);
        }
        PRIMITIVES_MAP = builder.build();
    }
  
    @Override 
    public String getType() {
        return "vipr";
    }
    
    @Override
    public ViPRPrimitive get(URI id) {
        return PRIMITIVES_MAP.get(id);
    }


    @Override
    public ViPRPrimitive create(CustomServicesPrimitiveCreateParam param) {
        throw APIException.methodNotAllowed.notSupported();
    }

    @Override
    public ViPRPrimitive update(URI id, CustomServicesPrimitiveUpdateParam param) {
        throw APIException.methodNotAllowed.notSupported();
    }

    @Override
    public void deactivate(URI id) {
        throw APIException.methodNotAllowed.notSupported();
    }

    @Override
    public List<URI> list() {
        return PRIMITIVES_MAP.keySet().asList();
    }

    @Override
    public String getPrimitiveModel() {
        return ViPRPrimitive.class.getSimpleName();
    }

    @Override
    public Iterator<CustomServicesPrimitiveRestRep> bulk(final Collection<URI> ids) {
        ImmutableList.Builder<CustomServicesPrimitiveRestRep> primitives = ImmutableList.<CustomServicesPrimitiveRestRep>builder();
        for(final URI id : ids ) {
            final ViPRPrimitive primitive = PRIMITIVES_MAP.get(id);
            final ModelObject model = primitive == null ? null : primitive.asModelObject(); 
            ArgValidator.checkEntityNotNull(model, id, false);
            primitives.add(CustomServicesPrimitiveMapper.map(primitive));
        }
        
        return primitives.build().iterator();
    }

    @Override
    public CustomServicesNoResourceType getResource(URI id) {
        return null;
    }

    @Override
    public CustomServicesNoResourceType createResource(String name,
            byte[] stream) {
        throw APIException.methodNotAllowed.notSupported();
    }

    @Override
    public CustomServicesNoResourceType updateResource(URI id, String name,
            byte[] stream) {
        throw APIException.methodNotAllowed.notSupported();
    }


    @Override
    public void deactivateResource(URI id) {
        throw APIException.methodNotAllowed.notSupported();
        
    }

    @Override
    public List<NamedElement> listResources() {
        return EMPTY_RESOURCE_LIST;
    }

    @Override
    public Class<CustomServicesNoResourceType> getResourceType() {
        return CustomServicesNoResourceType.class;
    }

    @Override
    public boolean hasResource() {
        return false;
    }
    
}
