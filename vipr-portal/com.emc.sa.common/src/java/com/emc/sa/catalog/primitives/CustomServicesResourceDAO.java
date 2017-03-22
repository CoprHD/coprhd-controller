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
import java.util.List;

import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.primitives.CustomServicesPrimitiveResourceType;

/**
 * Interface for a primitive data access object. The DAO is an interface for the CRUD
 * of a primitive type and it's resource
 *
 * @param <Resource> The type of resource for the primitive
 */
public interface CustomServicesResourceDAO<Resource extends CustomServicesPrimitiveResourceType> {
    /**
     * @return The type of the primitive
     */
    public String getType();

    /**
     * Get a primitive resource of the given ID
     * 
     * @param id The ID of the resource to get
     * @return The primitive resource null if not found
     */
    public Resource getResource(final URI id);

    /**
     * Create a primitive resource with the given name and bytes
     * 
     * @param name Name of the resource
     * @param stream The resource bytes
     * @return The created resource
     */
    public Resource createResource(final String name, final byte[] stream, final URI parentId);
    // public Resource createResource(final String name, byte[] stream, String parentId);

    /**
     * Update a resource with the given ID
     * 
     * @param id The ID of the resource to update
     * @param name A new name for the resource, it could be null
     * @param stream New bytes for the resource. They could be null
     * @return The updated resource
     */
    public Resource updateResource(URI id, String name, byte[] stream);

    /**
     * Deactivate a resource with the given ID
     * 
     * @param id The ID of the resource to deactivate
     */
    public void deactivateResource(final URI id);

    /**
     * A list of the resources of the type supported by this DAO
     * 
     * @return A named element list of resources
     */
    public List<NamedElement> listResources();

    /**
     * Get the resource model type that this dao supports
     * 
     * @return the primitive resource model type
     */
    public Class<Resource> getResourceType();

    /**
     * Check if this DAO has a resource type associated with it
     * 
     * @return true if the DAO supports a resource, false if there is no resource for the primitive
     */
    public boolean hasResource();

    public List<NamedElement> listResourcesByParentId(final URI parentId);
}
