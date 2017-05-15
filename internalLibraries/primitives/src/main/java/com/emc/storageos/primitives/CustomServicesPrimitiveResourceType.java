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

import java.net.URI;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.db.client.model.ModelObject;

/**
 * Interface that represents a primitive resource java object
 *
 */
public interface CustomServicesPrimitiveResourceType {
    /**
     * The attributes of the primitive resource type instance
     * 
     * @return A map of attributes for the primitive resource type instance
     */
    public Map<String, Set<String>> attributes();
    
    
    /**
     * The primitive resource object instance as a ModelObject
     * @return
     */
    public ModelObject asModelObject();
    
    
    /**
     * The bytes of the resource
     * 
     * @return Byte array of the primitive resource
     */
    public byte[] resource();
    
    
    /**
     * The name of the primitive resource
     * @return The name of the resource
     */
    public String name();

    /**
     * ParentId of the resource
     * @return The parentId of the resource (Eg., for ansible_inventory, the ansible package URI)
     */
    public URI parentId();
    
    
    /**
     * Suffix of the primitive resource file type
     * @return The suffix of the primitive resource file (e.g. '.tar')
     */
    public String suffix();
}
