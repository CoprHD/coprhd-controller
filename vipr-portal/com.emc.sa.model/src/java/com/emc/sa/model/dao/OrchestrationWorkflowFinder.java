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
package com.emc.sa.model.dao;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.uimodels.OrchestrationWorkflow;
import com.emc.storageos.db.client.model.uimodels.OrchestrationWorkflow.OrchestrationWorkflowStatus;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class OrchestrationWorkflowFinder extends ModelFinder<OrchestrationWorkflow> {

    private final static ImmutableList<String> SUMMARY_FIELDS = ImmutableList.<String>builder().add("label", OrchestrationWorkflow.NAME, OrchestrationWorkflow.DESCRIPTION).build();
    public OrchestrationWorkflowFinder(DBClientWrapper client) {
        super(OrchestrationWorkflow.class, client);
    }
    
    public List<OrchestrationWorkflow> findByName(final String name) {

        List<OrchestrationWorkflow> results = Lists.newArrayList();

        List<NamedElement> workflows = client.findByAlternateId(OrchestrationWorkflow.class, OrchestrationWorkflow.NAME, name);
        if (workflows != null) {
            results.addAll(findByIds(toURIs(workflows)));
        }

        return results;
    }
    
    public Iterator<OrchestrationWorkflow> findSummaries(final List<URI> ids) {
        return client.findAllFields(clazz, ids, SUMMARY_FIELDS); 
    }
    
    public List<NamedElement> findAllNames() {
        return prepareNamedElementFromURI(client.findAllIds(clazz));
    }

    public List<NamedElement> findAllNamesByStatus(final OrchestrationWorkflowStatus status) {
        return prepareNamedElementFromURI(findIDsByStatus(status));
    }

    private List<URI> findIDsByStatus(final OrchestrationWorkflowStatus status) {
        final List<URI> out = Lists.newArrayList();
        if (null != status) {
            final List<NamedElement> results = client.findByAlternateId(clazz, OrchestrationWorkflow.STATE, status.toString());
            if (results != null) {
                for (NamedElement namedElement : results) {
                    out.add(namedElement.getId());
                }
            }
        }
        return out;
    }

    private List<NamedElement> prepareNamedElementFromURI(final List<URI> ids) {
        final Iterator<OrchestrationWorkflow> it = client.findAllFields(clazz, ids, ImmutableList.<String>builder().add("label").build());
        final List<NamedElement> results = new ArrayList<NamedElement>();

        while(it.hasNext()) {
            final OrchestrationWorkflow element = it.next();
            results.add(NamedElement.createElement(element.getId(), element.getLabel()));
        }

        return results;
    }
}
