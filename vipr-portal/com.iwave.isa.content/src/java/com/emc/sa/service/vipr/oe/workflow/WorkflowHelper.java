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
package com.emc.sa.service.vipr.oe.workflow;

import java.net.URI;
import java.util.Iterator;

import com.emc.sa.service.vipr.oe.gson.WorkflowDefinition;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.constraint.impl.AlternateIdConstraintImpl;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.OEWorkflow;
import com.google.gson.Gson;

/**
 * Helper class to perform CRUD operations on a workflow
 */
public final class WorkflowHelper {

    private static final Gson GSON = new Gson();
    
    private WorkflowHelper() {}
    
    
    /**
     * Given a workflow name return the WorkflowDefinition
     * @return The workflow definition or null if it does not exist
     */
    public static WorkflowDefinition query(final String name, final DbClient dbClient) {
        final URI id = getWorkflowId(name, dbClient);
        
        if( null == id) return null;
        
        final OEWorkflow oeWorkflow = dbClient.queryObject(OEWorkflow.class, id);
        
        if( null == oeWorkflow || null == oeWorkflow.getDocument()) return null;
        
        return GSON.fromJson(oeWorkflow.getDocument(),WorkflowDefinition.class);
    }
    
    /**
     * Create or update a workflow definition
     */
    public static void persist(final WorkflowDefinition definition, final DbClient dbClient) {
        
        final String name = definition.getWorkflowName();
        final URI id = getWorkflowId(name, dbClient);
        
        if(id == null) {
            
            final OEWorkflow oeWorkflow = new OEWorkflow();
            
            oeWorkflow.setId(URIUtil.createId(OEWorkflow.class));
            oeWorkflow.setName(name);
            oeWorkflow.setDocument(GSON.toJson(definition));
            
            dbClient.createObject(oeWorkflow);
            return;
            
        } else {
            
            final OEWorkflow oeWorkflow = dbClient.queryObject(OEWorkflow.class, id);
            
            oeWorkflow.setDocument(GSON.toJson(definition));
            
            dbClient.updateObject(oeWorkflow);
            return;
        }
    }

    /**
     * Get the ID for a workflow given the name
     * 
     * @throws IllegalStateException if more than one workflow exists with the name
     * 
     * @return URI id of the workflow
     */
    private static URI getWorkflowId(final String name, final DbClient dbClient) {
        
        final AlternateIdConstraint constraint = new AlternateIdConstraintImpl(
                TypeMap.getDoType(OEWorkflow.class).getColumnField("name"), name);
        final NamedElementQueryResultList results = new NamedElementQueryResultList();
        
        dbClient.queryByConstraint(constraint, results);
        
        final Iterator<NamedElement> it = results.iterator();
        
        if( null == it || !it.hasNext()) {
            return null;
        }
        
        final NamedElement element = it.next();
        
        if( it.hasNext() ) {
            throw new IllegalStateException("Multiple workflows with name " + element.getName());
        }
        
        return element.getId();
    }
}
