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

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.sa.catalog.CustomServicesPrimitiveManager;
import com.emc.sa.model.dao.ModelClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.uimodels.CustomServicesDBScriptPrimitive;
import com.emc.storageos.db.client.model.uimodels.CustomServicesDBScriptResource;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveResourceRestRep;
import com.emc.storageos.primitives.db.script.CustomServicesScriptPrimitive;
import com.emc.storageos.primitives.db.script.CustomServicesScriptResource;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * Data access object for script primitive resources
 *
 */
public class CustomServicesScriptResourceDAO implements CustomServicesResourceDAO<CustomServicesScriptResource> {

    @Autowired
    private CustomServicesPrimitiveManager primitiveManager;
    @Autowired
    private ModelClient client;
    @Autowired
    private DbClient dbClient;
    
    @Override
    public String getType() {
        return CustomServicesScriptPrimitive.TYPE;
    }

    @Override
    public CustomServicesScriptResource getResource(URI id) {
        return CustomServicesDBHelper.getResource(CustomServicesScriptResource.class, CustomServicesDBScriptResource.class,
                primitiveManager, id);
    }

    @Override
    public CustomServicesScriptResource createResource(final String name,
            final byte[] stream, final String parentId) {
        final StringSetMap attributes = new StringSetMap();

        return CustomServicesDBHelper.createResource(CustomServicesScriptResource.class, CustomServicesDBScriptResource.class,
                primitiveManager, name, stream, attributes, null);
    }

    @Override
    public CustomServicesScriptResource updateResource(final URI id, final String name, final byte[] stream, final String parentId) {
        return CustomServicesDBHelper.updateResource(CustomServicesScriptResource.class, CustomServicesDBScriptResource.class,
                primitiveManager, id, name, stream, null, null, client, null,null,
                CustomServicesDBScriptPrimitive.class, CustomServicesDBScriptPrimitive.RESOURCE);
    }

    @Override
    public String getResourceModel() {
        return CustomServicesDBScriptResource.class.getSimpleName();
    }

    @Override
    public void deactivateResource(final URI id) {
        // There Script resource is referenced by the script primitive. There are no resource that is dependent on it.
        CustomServicesDBHelper.deactivateResource(CustomServicesDBScriptResource.class, primitiveManager, client, id, null, null,
                CustomServicesDBScriptPrimitive.class, CustomServicesDBScriptPrimitive.RESOURCE);
    }

    @Override
    public List<NamedElement> listResources(final String parentId) {
        // Parent does not exist for Script Resource
        if (StringUtils.isNotBlank(parentId)) {
            throw APIException.badRequests.invalidField(CustomServicesDBScriptResource.PARENTID, parentId);
        }
        return CustomServicesDBHelper.listResources(CustomServicesDBScriptResource.class, client,
                CustomServicesDBScriptResource.PARENTID, parentId);
    }
    
    @Override
    public List<NamedElement> listRelatedResources(final URI parentId ) {
        return CustomServicesDBHelper.EMPTY_ELEMENT_LIST;
    }

    @Override
    public Class<CustomServicesScriptResource> getResourceType() {
        return CustomServicesScriptResource.class;
    }
    
    @Override
    public boolean importResource(final CustomServicesPrimitiveResourceRestRep resource, final byte[] bytes) {
        return CustomServicesDBHelper.importResource(CustomServicesDBScriptResource.class, resource, bytes, client);
    }
}
