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

import com.emc.sa.workflow.WorkflowHelper;
import com.emc.storageos.db.client.model.uimodels.OEWorkflow;
import com.emc.storageos.model.orchestration.OrchestrationWorkflowRestRep;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 *
 */
public class OrchestrationWorkflowMapper {
    public static final OrchestrationWorkflowMapper instance = new OrchestrationWorkflowMapper();

    public static OrchestrationWorkflowMapper getInstance() {
        return instance;
    }

    private OrchestrationWorkflowMapper() {
    }
    
    public static OrchestrationWorkflowRestRep map(OEWorkflow from) {
        OrchestrationWorkflowRestRep to = new OrchestrationWorkflowRestRep(); 
        
        mapDataObjectFields(from, to);
        
        try {
            to.setDocument(WorkflowHelper.toWorkflowDocument(from.getDocument()));
        } catch (IOException e) {
            throw APIException.internalServerErrors.genericApisvcError("Error deserializing workflow", e);
        }
        
        return to;
    }
}
