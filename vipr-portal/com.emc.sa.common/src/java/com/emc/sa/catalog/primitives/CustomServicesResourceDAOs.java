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

import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableMap;


/**
 * Bean class that has maps of the primitive resource DAO types
 *
 */
public class CustomServicesResourceDAOs {

    private final ImmutableMap<String, CustomServicesResourceDAO<?>> typeMap;
    private final ImmutableMap<String, CustomServicesResourceDAO<?>> modelMap;


    public CustomServicesResourceDAOs(final List<CustomServicesResourceDAO<?>> daos) {
        final ImmutableMap.Builder<String, CustomServicesResourceDAO<?>> typeMapBuilder = ImmutableMap.<String, CustomServicesResourceDAO<?>>builder();
        final ImmutableMap.Builder<String, CustomServicesResourceDAO<?>> modelMapBuilder = ImmutableMap
                .<String, CustomServicesResourceDAO<?>> builder();
        for( final CustomServicesResourceDAO<?> dao : daos ) {
            typeMapBuilder.put(dao.getType().toLowerCase(), dao);
            modelMapBuilder.put(dao.getResourceModel(), dao);
        }

        typeMap = typeMapBuilder.build();
        modelMap = modelMapBuilder.build();
    }

    public CustomServicesResourceDAO<?> get(final String type) {
        return typeMap.get(type.toLowerCase());
    }
    
    public Set<String> getTypes() {
        return typeMap.keySet();
    }

    public CustomServicesResourceDAO<?> getByModel(String typeName) {
        return modelMap.get(typeName);
    }

}
