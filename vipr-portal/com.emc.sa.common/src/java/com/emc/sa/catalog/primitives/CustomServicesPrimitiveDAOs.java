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
 * Bean class that has maps of the primitive DAO types
 *
 */
public class CustomServicesPrimitiveDAOs {
    
    private final ImmutableMap<String, CustomServicesPrimitiveDAO<?>> typeMap;
    
    private final ImmutableMap<String, CustomServicesPrimitiveDAO<?>>  modelMap;
    
    public CustomServicesPrimitiveDAOs(final List<CustomServicesPrimitiveDAO<?>> daos) {
        final ImmutableMap.Builder<String, CustomServicesPrimitiveDAO<?>> typeMapBuilder = ImmutableMap.<String, CustomServicesPrimitiveDAO<?>>builder();
        final ImmutableMap.Builder<String, CustomServicesPrimitiveDAO<?>> modelMapBuilder= ImmutableMap.<String, CustomServicesPrimitiveDAO<?>>builder();
        for( final CustomServicesPrimitiveDAO<?> dao : daos ) {
            typeMapBuilder.put(dao.getType().toLowerCase(), dao);
            modelMapBuilder.put(dao.getPrimitiveModel(), dao);
        }

        typeMap = typeMapBuilder.build();
        modelMap = modelMapBuilder.build();
    }

    public CustomServicesPrimitiveDAO<?> get(final String type) {
        return typeMap.get(type.toLowerCase());
    }
    
    public Set<String> getTypes() {
        return typeMap.keySet();
    }

    public CustomServicesPrimitiveDAO<?> getByModel(String typeName) {
        return modelMap.get(typeName);
    }
    
}
