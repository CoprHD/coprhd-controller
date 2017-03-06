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


/**
 * Helper class to load primitives
 */
public final class CustomServicesPrimitiveHelper {
    
//    private static final ImmutableMap<URI, CustomServicesStaticPrimitiveModel> PRIMITIVES_MAP;
//    private static final ImmutableMap<String, CustomServicesPrimitiveType> TYPES;
//    static {
//        Set<Class<? extends CustomServicesPrimitiveType>> types = new Reflections("com.emc.storageos", 
//                new SubTypesScanner()).getSubTypesOf(CustomServicesPrimitiveType.class);
//        final Builder<String, CustomServicesPrimitiveType> typeBuilder = ImmutableMap.<String, CustomServicesPrimitiveType>builder();
//        for( final Class<? extends CustomServicesPrimitiveType> type : types ) {
//            final CustomServicesPrimitiveType<?,?> typeInstance;
//            try {
//                typeInstance = type.newInstance();
//            } catch(final IllegalAccessException | InstantiationException e) {
//                throw new RuntimeException("Failed to create instance of type: " + type.getName(), e);
//            }
//            typeBuilder.put(typeInstance.name(), typeInstance);
//        }
//        TYPES = typeBuilder.build();
//        
//        Set<Class<? extends ViPRPrimitive>> primitives = 
//                new Reflections("com.emc.storageos", new SubTypesScanner()).getSubTypesOf(ViPRPrimitive.class);
//        final Builder<URI, CustomServicesStaticPrimitiveModel> builder = ImmutableMap.<URI, CustomServicesStaticPrimitiveModel>builder();
//        for( final Class<? extends ViPRPrimitive> primitive : primitives) {
//            final ViPRPrimitive instance;
//            try {
//                instance = primitive.newInstance();
//            } catch (final IllegalAccessException | InstantiationException e) {
//                throw new RuntimeException("Failed to create instance of primitive: "+primitive.getName(), e);
//            }
//        
//            builder.put(instance.getId(), instance);
//        }
//        PRIMITIVES_MAP = builder
//                .build();
//    }
//    
//    
//    private static final ImmutableList<CustomServicesStaticPrimitiveModel> PRIMITIVES_LIST = ImmutableList.<CustomServicesStaticPrimitiveModel>builder()
//            .addAll((PRIMITIVES_MAP.values()))
//            .build();
//    
//    public static ImmutableList<CustomServicesStaticPrimitiveModel> list() {
//        return PRIMITIVES_LIST;
//    }
//    
//    public static CustomServicesStaticPrimitiveModel get(final URI id) {
//        return PRIMITIVES_MAP.get(id);
//    }
//    
//    public static boolean isStatic(final CustomServicesPrimitiveTypeSave type) {
//        return type.type().isAssignableFrom(CustomServicesStaticPrimitiveModel.class);
//    }
//    
//    public static boolean isCustomServicesUserPrimitive(final CustomServicesPrimitiveTypeSave type) {
//        return type.type().isAssignableFrom(CustomServicesDBPrimitive.class);
//    }
//    
//    public static Class<? extends CustomServicesDBPrimitive> userModel(final CustomServicesPrimitiveTypeSave type) {
//        if( isCustomServicesUserPrimitive(type)) {
//            return type.type().asSubclass(CustomServicesDBPrimitive.class);
//        }
//        return null;
//    }
//    
//    public static <T extends CustomServicesPrimitive> T toModel(final Class<T> clazz, final CustomServicesPrimitive primitive) {
//        if(primitive.getClass().isAssignableFrom(clazz)) {
//            return clazz.cast(primitive);
//        }
//        
//        return null;
//    }
//
//    public static CustomServicesPrimitiveTypeSave typeFromId(URI id) {
//        final String typeName = URIUtil.getTypeName(id);
//        for(CustomServicesPrimitiveTypeSave primitiveType : CustomServicesPrimitiveTypeSave.values()) {
//            if(primitiveType.type().equals(typeName)) {
//                return primitiveType;
//            }
//        }
//        return null;
//    }
//    
//    public static Class<? extends CustomServicesDBPrimitive > getUserModel(final URI id) {
//        CustomServicesPrimitiveTypeSave type = typeFromId(id);
//        if( type == null || isStatic(type)) {
//            return null;
//        } else {
//            return userModel(type);
//        }
//    }
//    
//    public static CustomServicesPrimitiveType get(final String type) {
//        return TYPES.get(type);
//    }
}
