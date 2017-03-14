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
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import com.emc.sa.catalog.CustomServicesPrimitiveManager;
import com.emc.sa.model.dao.ModelClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.uimodels.CustomServicesDBScriptPrimitive;
import com.emc.storageos.db.client.model.uimodels.CustomServicesDBScriptResource;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveCreateParam;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveRestRep;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveUpdateParam;
import com.emc.storageos.primitives.db.script.CustomServicesScriptPrimitive;
import com.emc.storageos.primitives.db.script.CustomServicesScriptResource;

/**
 * Data access object for script primitives
 *
 */
public class CustomServicesScriptPrimitiveDAO implements CustomServicesPrimitiveDAO<CustomServicesScriptPrimitive, CustomServicesScriptResource>{

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
    public CustomServicesScriptPrimitive get(final URI id) {
        return CustomServicesDBHelper.get(CustomServicesScriptPrimitive.class, CustomServicesDBScriptPrimitive.class, primitiveManager, id);
    }

    @Override
    public CustomServicesScriptPrimitive create(
            CustomServicesPrimitiveCreateParam param) {
        return CustomServicesDBHelper.create(CustomServicesScriptPrimitive.class, CustomServicesDBScriptPrimitive.class, 
                CustomServicesDBScriptResource.class, primitiveManager, param);
    }

    @Override
    public CustomServicesScriptPrimitive update(URI id,
            CustomServicesPrimitiveUpdateParam param) {
        return CustomServicesDBHelper.update(CustomServicesScriptPrimitive.class, CustomServicesDBScriptPrimitive.class, CustomServicesDBScriptResource.class, primitiveManager, client, param, id);
    }

    @Override
    public void deactivate(URI id) {
        CustomServicesDBHelper.deactivate(CustomServicesDBScriptPrimitive.class, primitiveManager, client, id);
    }

    @Override
    public String getPrimitiveModel() {
        return CustomServicesDBScriptPrimitive.class.getSimpleName();
    }

    @Override
    public List<URI> list() {
        return CustomServicesDBHelper.list(CustomServicesDBScriptPrimitive.class, client);
    }

    @Override
    public CustomServicesScriptResource getResource(URI id) {
        return CustomServicesDBHelper.getResource(CustomServicesScriptResource.class, CustomServicesDBScriptResource.class, primitiveManager, id);
    }

    @Override
    public CustomServicesScriptResource createResource(final String name,
            final byte[] stream) {
        final StringSetMap attributes = new StringSetMap();
  
        return CustomServicesDBHelper.createResource(CustomServicesScriptResource.class, CustomServicesDBScriptResource.class, 
                primitiveManager, name, stream, attributes);
    }

    @Override
    public CustomServicesScriptResource updateResource(final URI id, final String name,final byte[] stream) {
        return CustomServicesDBHelper.updateResource(CustomServicesScriptResource.class, CustomServicesDBScriptResource.class, 
                primitiveManager, id, name, stream, null);
    }

    @Override
    public void deactivateResource(final URI id) {
        CustomServicesDBHelper.deactivateResource(CustomServicesDBScriptResource.class, primitiveManager, client, id);
    }

    @Override
    public List<NamedElement> listResources() {
        return client.customServicesPrimitiveResources().list(CustomServicesDBScriptResource.class);
    }

    @Override
    public Class<CustomServicesScriptResource> getResourceType() {
        return CustomServicesScriptResource.class;
    }

    @Override
    public boolean hasResource() {
        return true;
    }

    @Override
    public Iterator<CustomServicesPrimitiveRestRep> bulk(Collection<URI> ids) {
        return CustomServicesDBHelper.bulk(ids, CustomServicesScriptPrimitive.class, CustomServicesDBScriptPrimitive.class, dbClient);
    }

}
