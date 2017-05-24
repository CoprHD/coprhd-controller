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

import org.springframework.beans.factory.annotation.Autowired;

import com.emc.sa.catalog.CustomServicesPrimitiveManager;
import com.emc.sa.model.dao.ModelClient;
import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.uimodels.CustomServicesDBAnsibleInventoryResource;
import com.emc.storageos.db.client.model.uimodels.CustomServicesDBAnsibleResource;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveResourceRestRep;
import com.emc.storageos.primitives.CustomServicesConstants;
import com.emc.storageos.primitives.db.ansible.CustomServicesAnsibleInventoryResource;
import com.emc.storageos.primitives.db.ansible.CustomServicesAnsibleResource;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * Data access object for ansible primitive inventory resource
 *
 */
public class CustomServicesAnsibleInventoryResourceDAO implements CustomServicesResourceDAO<CustomServicesAnsibleInventoryResource> {

    @Autowired
    private CustomServicesPrimitiveManager primitiveManager;
    @Autowired
    private ModelClient client;
    @Autowired
    private DbClient dbClient;

    @Override
    public String getType() {
        return CustomServicesConstants.ANSIBLE_INVENTORY_TYPE;
    }

    @Override
    public CustomServicesAnsibleInventoryResource getResource(final URI id) {
        return CustomServicesDBHelper.getResource(CustomServicesAnsibleInventoryResource.class,
                CustomServicesDBAnsibleInventoryResource.class, primitiveManager, id);
    }

    @Override
    public CustomServicesAnsibleInventoryResource createResource(final String name,
            final byte[] stream, final String parentId) {
        final StringSetMap attributes = new StringSetMap();
        ArgValidator.checkFieldNotNull(parentId, "parentId");
        final URI parentIdURI = URI.create(parentId);

        // Verify that there is an ansible resource associated with the parent id that is passed in the request
        CustomServicesAnsibleResource parentResource = CustomServicesDBHelper.getResource(CustomServicesAnsibleResource.class,
                CustomServicesDBAnsibleResource.class, primitiveManager, parentIdURI);
        if (null == parentResource) {
            throw APIException.notFound.unableToFindEntityInURL(parentIdURI);
        }

        return CustomServicesDBHelper.createResource(CustomServicesAnsibleInventoryResource.class,
                CustomServicesDBAnsibleInventoryResource.class,
                primitiveManager, name, stream, attributes, parentIdURI);
    }

    @Override
    public CustomServicesAnsibleInventoryResource updateResource(final URI id, final String name, final byte[] stream, final String parentId) {
        ArgValidator.checkFieldNotNull(parentId, "parentId");
        final URI parentIdURI = URI.create(parentId);
        CustomServicesAnsibleResource parentResource = CustomServicesDBHelper.getResource(CustomServicesAnsibleResource.class,
                CustomServicesDBAnsibleResource.class, primitiveManager, parentIdURI);
        if (null == parentResource) {
            throw APIException.notFound.unableToFindEntityInURL(parentIdURI);
        }
        return CustomServicesDBHelper.updateResource(CustomServicesAnsibleInventoryResource.class,
                CustomServicesDBAnsibleInventoryResource.class,
                primitiveManager, id, name, stream, null, parentIdURI, client, null, null,null,null);
    }

    @Override
    public String getResourceModel() {
        return CustomServicesDBAnsibleInventoryResource.class.getSimpleName();
    }

    @Override
    public void deactivateResource(URI id) {
        // There are no primitives or resource that has the inventory resource as the parent. hence passing null for the last 4 params
        CustomServicesDBHelper.deactivateResource(CustomServicesDBAnsibleInventoryResource.class, primitiveManager, client, id, null, null,
                null, null);
    }

    @Override
    public List<NamedElement> listResources(final String filterByParentId) {
        return CustomServicesDBHelper.listResources(CustomServicesDBAnsibleInventoryResource.class, client,
                CustomServicesDBAnsibleInventoryResource.PARENTID, filterByParentId);
    }

    @Override
    public List<NamedElement> listRelatedResources(final URI parentId ) {
        return CustomServicesDBHelper.EMPTY_ELEMENT_LIST;
    }
    
    @Override
    public Class<CustomServicesAnsibleInventoryResource> getResourceType() {
        return CustomServicesAnsibleInventoryResource.class;
    }

    @Override
    public boolean importResource(final CustomServicesPrimitiveResourceRestRep resource, final byte[] bytes) {
        return CustomServicesDBHelper.importResource(CustomServicesDBAnsibleInventoryResource.class, resource, bytes, client);
    }
}
