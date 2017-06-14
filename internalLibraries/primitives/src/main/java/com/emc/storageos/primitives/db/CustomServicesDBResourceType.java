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
package com.emc.storageos.primitives.db;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.db.client.model.ModelObject;
import com.emc.storageos.db.client.model.uimodels.CustomServicesDBResource;
import com.emc.storageos.primitives.CustomServicesPrimitiveResourceType;

/**
 * Class that represents a primitive resource stored in the database as a java object
 *
 */
public abstract class CustomServicesDBResourceType<Resource extends CustomServicesDBResource> implements
        CustomServicesPrimitiveResourceType {
    
    private final Resource resource;
    private final Map<String, Set<String>> attributes;

    public CustomServicesDBResourceType(final Resource resource,
            final Map<String, Set<String>> attributes) {
        this.resource = resource;
        this.attributes = attributes;
    }
    @Override
    public Map<String, Set<String>> attributes() {
        return attributes;
    }

    @Override
    public ModelObject asModelObject() {
        return resource;
    }

    @Override
    public byte[] resource() {
        return resource.getResource();
    }

    @Override
    public String name() {
        return resource.getLabel();
    }

    @Override
    public URI parentId() {
        return resource.getParentId();
    }
}
