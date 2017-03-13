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
import com.emc.storageos.primitives.java.CustomServicesNoResourceType;
import com.emc.storageos.primitives.java.vipr.CustomServicesViPRPrimitive;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

/**
 * Data access object for ViPR primitives
 *
 */
public class CustomServicesViprResourceDAO implements CustomServicesResourceDAO<CustomServicesNoResourceType> {

    private static final List<NamedElement> EMPTY_RESOURCE_LIST = ImmutableList.<NamedElement>builder().build();


    @Override
    public String getType() {
        return CustomServicesViPRPrimitive.TYPE;
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
