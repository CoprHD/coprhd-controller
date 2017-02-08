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
import com.emc.storageos.db.client.model.uimodels.Ansible;
import com.emc.storageos.db.client.model.uimodels.AnsiblePackage;
import com.emc.storageos.db.client.model.uimodels.CustomServiceScriptPrimitive;
import com.emc.storageos.db.client.model.uimodels.CustomServiceScriptResource;
import com.emc.storageos.db.client.model.uimodels.PrimitiveResource;
import com.emc.storageos.db.client.model.uimodels.UserPrimitive;

@Component
public class PrimitiveManagerImpl implements PrimitiveManager {

    @Autowired
    private ModelClient client;

    @Override
    public void save(final PrimitiveResource resource) {
        client.save(resource);
    }

    @Override
    public UserPrimitive findById(final URI id) {
        // TODO: move down the enum constant defined in PrimitiveService.java and use that here.
        final String type = URIUtil.getTypeName(id);
        
        switch(type) {
        case "Ansible":
            return client.findById(Ansible.class, id);
        case "CustomServiceScriptPrimitive":
            return client.findById(CustomServiceScriptPrimitive.class, id);
        default:
            throw new RuntimeException("Unknown Type " + type);
        }
        
    }

    @Override
    public void save(final UserPrimitive primitive) {
        client.save(primitive);
    }

    @Override
    public PrimitiveResource findResource(final URI id) {
     // TODO: move down the enum constant defined in PrimitiveService.java and use that here.
        final String type = URIUtil.getTypeName(id);
        switch(type) {
        case "AnsiblePackage":
            return client.findById(AnsiblePackage.class, id);
        case "CustomServiceScriptResource":
            return client.findById(CustomServiceScriptResource.class, id);
        default:
            return null;
        }
       
    }
    
    @Override
    public List<URI> findAllAnsibleIds() {
        return client.findByType(Ansible.class);
    }

    @Override
    public List<Ansible> findAllAnsible() {
        final List<URI> ids = findAllAnsibleIds();
        if( null == ids) {
            return null;
        }
        List<Ansible> ansiblePrimitives = client.findByIds(Ansible.class, ids);
        return ansiblePrimitives;
    }


    @Override
    public List<URI> findAllScriptPrimitiveIds() {
        return client.findByType(CustomServiceScriptPrimitive.class);
    }
    
    @Override
    public List<CustomServiceScriptPrimitive> findAllScriptPrimitives() {
        final List<URI> ids = findAllScriptPrimitiveIds();
        if( null == ids) {
            return null;
        }
        List<CustomServiceScriptPrimitive> scriptPrimitives = client.findByIds(CustomServiceScriptPrimitive.class, ids);
        return scriptPrimitives;
    }

}
