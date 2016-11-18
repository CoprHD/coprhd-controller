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
package com.emc.sa.api.mapper;

import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFields;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import com.emc.sa.workflow.WorkflowHelper;
import com.emc.storageos.db.client.model.uimodels.OrchestrationWorkflow;
import com.emc.storageos.model.orchestration.OrchestrationWorkflowBulkRep;
import com.emc.storageos.model.orchestration.OrchestrationWorkflowList;
import com.emc.storageos.model.orchestration.OrchestrationWorkflowRestRep;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.google.common.base.Function;
import com.google.common.collect.Lists;

/**
 *
 */
public class OrchestrationWorkflowMapper implements Function<OrchestrationWorkflow, OrchestrationWorkflowRestRep> {
    public static final OrchestrationWorkflowMapper instance = new OrchestrationWorkflowMapper();

    public static OrchestrationWorkflowMapper getInstance() {
        return instance;
    }

    private OrchestrationWorkflowMapper() {
    }
    
    public static OrchestrationWorkflowRestRep map(OrchestrationWorkflow from) {
        OrchestrationWorkflowRestRep to = new OrchestrationWorkflowRestRep(); 
        
        mapDataObjectFields(from, to);
        
        try {
            to.setDocument(WorkflowHelper.toWorkflowDocument(from));
        } catch (IOException e) {
            throw APIException.internalServerErrors.genericApisvcError("Error deserializing workflow", e);
        }
        
        return to;
    }
    
    public static OrchestrationWorkflowList mapList(List<URI> fromList) {
        return new OrchestrationWorkflowList(fromList);
    }
    
    public static OrchestrationWorkflowBulkRep mapBulk(List<OrchestrationWorkflow> workflows) {
        final List<OrchestrationWorkflowRestRep> workflowRestRepList = Lists.newArrayList();
        for( final OrchestrationWorkflow workflow : workflows) {
            workflowRestRepList.add(map(workflow));
        }
        return new OrchestrationWorkflowBulkRep(workflowRestRepList);
    }

    @Override
    public OrchestrationWorkflowRestRep apply(OrchestrationWorkflow input) {
        return map(input);
    }
}
