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
package com.emc.storageos.primitives.java;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.db.client.model.ModelObject;
import com.emc.storageos.primitives.CustomServicesPrimitiveResourceType;
import com.google.common.collect.ImmutableMap;

/**
 * Class for primitives that do not have a resource associated with them (e.g. ViPR primitives)
 *
 */
public class CustomServicesNoResourceType implements CustomServicesPrimitiveResourceType {

    private final static ImmutableMap<String, Set<String>> EMPTY = ImmutableMap.<String, Set<String>>builder().build();
    private final static String EMPTY_STRING = "";
    
    @Override
    public Map<String, Set<String>> attributes() {
        return EMPTY;
    }

    @Override
    public ModelObject asModelObject() {
        return null;
    }

    @Override
    public byte[] resource() {
        return null;
    }

    @Override
    public String name() {
        return EMPTY_STRING;
    }

    @Override
    public String suffix() {
        return EMPTY_STRING;
    }

    @Override
    public URI parentId() {
        return null;
    }

}
