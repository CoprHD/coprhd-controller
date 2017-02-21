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
package com.emc.storageos.primitives;

import java.util.EnumSet;

import com.emc.storageos.db.client.model.uimodels.CustomServicesAnsiblePrimitive;
import com.emc.storageos.db.client.model.uimodels.CustomServicesPrimitive;
import com.emc.storageos.db.client.model.uimodels.CustomServicesPrimitiveResource;
import com.emc.storageos.db.client.model.uimodels.CustomServicesScriptPrimitive;

public enum CustomServicesPrimitiveType {
    VIPR(ViPRPrimitive.class, 0x02), 
    ANSIBLE(CustomServicesAnsiblePrimitive.class, 0x04), 
    SCRIPT(CustomServicesScriptPrimitive.class, 0x08);

    public final static int ALL_MASK = 0xFF;
    
    private final static EnumSet<CustomServicesPrimitiveType> DYNAMIC_TYPES = EnumSet.of(ANSIBLE, SCRIPT);

    private final Class<? extends CustomServicesPrimitive> type;
    private final Class<? extends CustomServicesPrimitiveResource> resourceType;
    private final int mask;

    private CustomServicesPrimitiveType(final Class<? extends CustomServicesPrimitive> type, 
            final int mask) {
        this(type, null, mask);
    }
    
    private CustomServicesPrimitiveType(final Class<? extends CustomServicesPrimitive> type,
            final Class<? extends CustomServicesPrimitiveResource> resourceType,
            final int mask) {
        this.type = type;
        this.mask = mask;
        this.resourceType = resourceType;
    }

    public static CustomServicesPrimitiveType get(final String primitiveType) {
        try {
            return CustomServicesPrimitiveType.valueOf(primitiveType.toUpperCase());
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }
    
    public static CustomServicesPrimitiveType fromTypeName(final String typeName) {
        for(CustomServicesPrimitiveType primitiveType : values()) {
            if(primitiveType.type().equals(typeName)) {
                return primitiveType;
            }
        }
        return null;
    }
    
    public static CustomServicesPrimitiveType dynamicType(final String type) {
        final CustomServicesPrimitiveType primitiveType = get(type);
        if(null != primitiveType && DYNAMIC_TYPES.contains(primitiveType)) {
            return primitiveType;
        } else {
            return null;
        }
    }
    
    public static EnumSet<CustomServicesPrimitiveType> dynamicTypes() {
        return DYNAMIC_TYPES;
    }

    public Class<? extends CustomServicesPrimitive> type() {
        return type;
    }
    
    public Class<? extends CustomServicesPrimitiveResource> resourceType() {
        return resourceType;
    }
    
    public int mask() {
        return mask;
    }
}
