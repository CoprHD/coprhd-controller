/*
 * Copyright 2016 Dell Inc. or its subsidiaries.
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
import java.util.Iterator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.emc.sa.model.dao.ModelClient;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.uimodels.OrchestrationWorkflow;


@Component
public class OrchestrationWorkflowManagerImpl implements
        OrchestrationWorkflowManager {
    @Autowired
    private ModelClient client;
 
    @Override
    public OrchestrationWorkflow getById(final URI id) {
        if( id == null ) {
            return null;
        }
        
        return client.orchestrationWorkflows().findById(id);
    }

    @Override
    public List<OrchestrationWorkflow> getByName(final String name) {
        if( null == name) {
            return null;
        }
        
        return client.orchestrationWorkflows().findByName(name);
    }
    
    @Override
    public List<NamedElement> list() {
        return client.orchestrationWorkflows().findAllNames();
    }

    @Override
    public Iterator<OrchestrationWorkflow> getSummaries(List<URI> ids) {
        return client.orchestrationWorkflows().findSummaries(ids);
    }

    @Override
    public void save(final OrchestrationWorkflow workflow) {
        client.save(workflow);
    }
    
    @Override
    public void delete(final OrchestrationWorkflow workflow) {
        client.delete(workflow);
    }
}
