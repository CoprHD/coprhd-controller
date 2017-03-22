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
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.uimodels.CustomServicesDBAnsibleInventoryResource;
import com.emc.storageos.primitives.CustomServicesConstants;
import com.emc.storageos.primitives.db.ansible.CustomServicesAnsibleInventoryResource;

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
            final byte[] stream, final URI parentId) {
        final StringSetMap attributes = new StringSetMap();
        return CustomServicesDBHelper.createResource(CustomServicesAnsibleInventoryResource.class,
                CustomServicesDBAnsibleInventoryResource.class,
                primitiveManager, name, stream, attributes, parentId);
    }

    @Override
    public CustomServicesAnsibleInventoryResource updateResource(final URI id, final String name, final byte[] stream) {
        return CustomServicesDBHelper.updateResource(CustomServicesAnsibleInventoryResource.class,
                CustomServicesDBAnsibleInventoryResource.class,
                primitiveManager, id, name, stream, null);
    }

    @Override
    public void deactivateResource(URI id) {
        CustomServicesDBHelper.deactivateResource(CustomServicesDBAnsibleInventoryResource.class, primitiveManager, client, id);
    }

    @Override
    public List<NamedElement> listResources() {
        return client.customServicesPrimitiveResources().list(CustomServicesDBAnsibleInventoryResource.class);
    }

    @Override
    public List<NamedElement> listResourcesByParentId(final URI parentId) {
        return client.customServicesPrimitiveResources().listAllResourceByRefId(CustomServicesDBAnsibleInventoryResource.class,
                CustomServicesDBAnsibleInventoryResource.PARENTID, parentId);
    }

    @Override
    public Class<CustomServicesAnsibleInventoryResource> getResourceType() {
        return CustomServicesAnsibleInventoryResource.class;
    }

    @Override
    public boolean hasResource() {
        return true;
    }

}
