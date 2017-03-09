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
package com.emc.sa.catalog;

import java.net.URI;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.emc.sa.model.dao.ModelClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.uimodels.CustomServicesAnsiblePackage;
import com.emc.storageos.db.client.model.uimodels.CustomServicesAnsiblePrimitive;
import com.emc.storageos.db.client.model.uimodels.CustomServicesPrimitiveResource;
import com.emc.storageos.db.client.model.uimodels.CustomServicesScriptPrimitive;
import com.emc.storageos.db.client.model.uimodels.CustomServicesScriptResource;
import com.emc.storageos.db.client.model.uimodels.CustomServicesUserPrimitive;
import com.emc.storageos.db.client.model.uimodels.CustomServicesWorkflow;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

@Component
public class CustomServicesPrimitiveManagerImpl implements CustomServicesPrimitiveManager {

    @Autowired
    private ModelClient client;

    
    @Override
    public void save(final CustomServicesPrimitiveResource resource) {
        client.save(resource);
    }

    @Override
    public CustomServicesUserPrimitive findById(final URI id) {
        // TODO: move down the enum constant defined in PrimitiveService.java and use that here.
        final String type = URIUtil.getTypeName(id);
        
        switch(type) {
        case "CustomServicesAnsiblePrimitive":
            return client.findById(CustomServicesAnsiblePrimitive.class, id);
        case "CustomServicesScriptPrimitive":
            return client.findById(CustomServicesScriptPrimitive.class, id);
        default:
            throw new RuntimeException("Unknown Type " + type);
        }
        
    }

    @Override
    public void save(final CustomServicesUserPrimitive primitive) {
        client.save(primitive);
    }

    @Override
    public void deactivate(final URI id) {
        final CustomServicesUserPrimitive primitive = findById(id);
        if( null == primitive ) {
            throw APIException.notFound.unableToFindEntityInURL(id);
        }
        
        List<NamedElement> workflows = client.customServicesWorkflows().getByPrimitive(id);
        if( null != workflows && !workflows.isEmpty()) {
            throw APIException.badRequests.resourceHasActiveReferencesWithType(primitive.getClass().getSimpleName(), id, CustomServicesWorkflow.class.getSimpleName());
        }
        
        client.delete(primitive);
    }

    @Override
    public CustomServicesPrimitiveResource findResource(final URI id) {
     // TODO: move down the enum constant defined in PrimitiveService.java and use that here.
        final String type = URIUtil.getTypeName(id);
        switch(type) {
        case "CustomServicesAnsiblePackage":
            return client.findById(CustomServicesAnsiblePackage.class, id);
        case "CustomServicesScriptResource":
            return client.findById(CustomServicesScriptResource.class, id);
        default:
            return null;
        }
       
    }
    
    @Override
    public List<URI> findAllAnsibleIds() {
        return client.findByType(CustomServicesAnsiblePrimitive.class);
    }

    @Override
    public List<CustomServicesAnsiblePrimitive> findAllAnsible() {
        final List<URI> ids = findAllAnsibleIds();
        if( null == ids) {
            return null;
        }
        List<CustomServicesAnsiblePrimitive> ansiblePrimitives = client.findByIds(CustomServicesAnsiblePrimitive.class, ids);
        return ansiblePrimitives;
    }


    @Override
    public List<URI> findAllScriptPrimitiveIds() {
        return client.findByType(CustomServicesScriptPrimitive.class);
    }
    
    @Override
    public List<CustomServicesScriptPrimitive> findAllScriptPrimitives() {
        final List<URI> ids = findAllScriptPrimitiveIds();
        if( null == ids) {
            return null;
        }
        List<CustomServicesScriptPrimitive> scriptPrimitives = client.findByIds(CustomServicesScriptPrimitive.class, ids);
        return scriptPrimitives;
    }
    @Override
    public <T extends CustomServicesPrimitiveResource> List<NamedElement> getResources(Class<T> type) {
        return client.customServicesPrimitiveResources().list(type);
    }
}
