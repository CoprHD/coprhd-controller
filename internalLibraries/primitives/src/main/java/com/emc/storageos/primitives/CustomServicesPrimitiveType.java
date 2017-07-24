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

import com.emc.storageos.db.client.model.ModelObject;


/**
 * Abstract class that is the java object representation of a primitive type
 *
 */
public abstract class CustomServicesPrimitiveType implements CustomServicesPrimitive {

    /**
     * Get the type name of the primitive
     * @return The type name of the primitive
     */
    public abstract String type();
    
    
    /**
     * The primitive type as a model object
     * @return The primitive represented as a model object
     */
    public abstract ModelObject asModelObject();
}
