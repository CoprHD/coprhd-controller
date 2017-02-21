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
package com.emc.storageos.primitives;

import java.net.URI;
import java.util.Set;

import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.uimodels.CustomServicesPrimitive;
import com.emc.storageos.db.client.model.uimodels.CustomServicesUserPrimitive;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

/**
 * Helper class to load primitives
 */
public final class CustomServicesPrimitiveHelper {
    
    private static final ImmutableMap<URI, CustomServicesStaticPrimitive> PRIMITIVES_MAP;

    static {
        Set<Class<? extends ViPRPrimitive>> primitives = 
                new Reflections("com.emc.storageos", new SubTypesScanner()).getSubTypesOf(ViPRPrimitive.class);
        final Builder<URI, CustomServicesStaticPrimitive> builder = ImmutableMap.<URI, CustomServicesStaticPrimitive>builder();
        for( final Class<? extends ViPRPrimitive> primitive : primitives) {
            final ViPRPrimitive instance;
            try {
                instance = primitive.newInstance();
            } catch (final IllegalAccessException | InstantiationException e) {
                throw new RuntimeException("Failed to create instance of primitive: "+primitive.getName(), e);
            }
        
            builder.put(instance.getId(), instance);
        }
        PRIMITIVES_MAP = builder
                .build();
    }
    
    
    private static final ImmutableList<CustomServicesStaticPrimitive> PRIMITIVES_LIST = ImmutableList.<CustomServicesStaticPrimitive>builder()
            .addAll((PRIMITIVES_MAP.values()))
            .build();
    
    public static ImmutableList<CustomServicesStaticPrimitive> list() {
        return PRIMITIVES_LIST;
    }
    
    public static CustomServicesStaticPrimitive get(final URI id) {
        return PRIMITIVES_MAP.get(id);
    }
    
    public static boolean isStatic(final CustomServicesPrimitiveType type) {
        return type.type().isAssignableFrom(CustomServicesStaticPrimitive.class);
    }
    
    public static boolean isCustomServicesUserPrimitive(final CustomServicesPrimitiveType type) {
        return type.type().isAssignableFrom(CustomServicesUserPrimitive.class);
    }
    
    public static Class<? extends CustomServicesUserPrimitive> userModel(final CustomServicesPrimitiveType type) {
        if( isCustomServicesUserPrimitive(type)) {
            return type.type().asSubclass(CustomServicesUserPrimitive.class);
        }
        return null;
    }
    
    public static <T extends CustomServicesPrimitive> T toModel(final Class<T> clazz, final CustomServicesPrimitive primitive) {
        if(primitive.getClass().isAssignableFrom(clazz)) {
            return clazz.cast(primitive);
        }
        
        return null;
    }

    public static CustomServicesPrimitiveType typeFromId(URI id) {
        final String typeName = URIUtil.getTypeName(id);
        for(CustomServicesPrimitiveType primitiveType : CustomServicesPrimitiveType.values()) {
            if(primitiveType.type().equals(typeName)) {
                return primitiveType;
            }
        }
        return null;
    }
    
    public static Class<? extends CustomServicesUserPrimitive > getUserModel(final URI id) {
        CustomServicesPrimitiveType type = typeFromId(id);
        if( type == null || isStatic(type)) {
            return null;
        } else {
            return userModel(type);
        }
    }
}
