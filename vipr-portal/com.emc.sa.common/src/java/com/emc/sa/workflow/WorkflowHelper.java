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
package com.emc.sa.workflow;

import java.io.IOException;
import java.util.List;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.uimodels.OrchestrationWorkflow;
import com.emc.storageos.model.orchestration.OrchestrationWorkflowDocument;

/**
 * Helper class to perform CRUD operations on a workflow
 */
public final class WorkflowHelper {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    private WorkflowHelper() {}
    
    /**
     * Create a workflow definition
     * @throws IOException 
     * @throws JsonMappingException 
     * @throws JsonGenerationException 
     */
    public static OrchestrationWorkflow create(final OrchestrationWorkflowDocument document) throws JsonGenerationException, JsonMappingException, IOException {
        final OrchestrationWorkflow oeWorkflow = new OrchestrationWorkflow();
        
        oeWorkflow.setId(URIUtil.createId(OrchestrationWorkflow.class));
        oeWorkflow.setLabel(document.getName());
        oeWorkflow.setName(document.getName());
        oeWorkflow.setDescription(document.getDescription());
        oeWorkflow.setSteps(toStepsJson(document.getSteps()));

        return oeWorkflow;
    }
    
    /**
     * Update a workflow definition
     * @throws IOException 
     * @throws JsonMappingException 
     * @throws JsonGenerationException 
     */
    public static OrchestrationWorkflow update(final OrchestrationWorkflow oeWorkflow, final OrchestrationWorkflowDocument document) throws JsonGenerationException, JsonMappingException, IOException {

        if(document.getDescription() != null) {
            oeWorkflow.setDescription(document.getDescription());
        }
        
        oeWorkflow.setSteps(toStepsJson(document.getSteps()));
        return oeWorkflow;
    }
    
    public static OrchestrationWorkflowDocument toWorkflowDocument(final OrchestrationWorkflow workflow) throws JsonParseException, JsonMappingException, IOException {
        final OrchestrationWorkflowDocument document = new OrchestrationWorkflowDocument();
        document.setName(workflow.getName());
        document.setDescription(workflow.getDescription());
        document.setSteps(toDocumentSteps(workflow.getSteps()));
        
        return document;
    }
    
    public static OrchestrationWorkflowDocument toWorkflowDocument(final String workflow) throws JsonParseException, JsonMappingException, IOException {
        return MAPPER.readValue(workflow, OrchestrationWorkflowDocument.class);
    }
    
    public static String toWorkflowDocumentJson( OrchestrationWorkflow workflow) throws JsonGenerationException, JsonMappingException, JsonParseException, IOException {
        return MAPPER.writeValueAsString(toWorkflowDocument(workflow));
    }
    
    private static List<OrchestrationWorkflowDocument.Step> toDocumentSteps(final String steps) throws JsonParseException, JsonMappingException, IOException {
        return steps == null ? null :  MAPPER.readValue(steps, MAPPER.getTypeFactory().constructCollectionType(List.class, OrchestrationWorkflowDocument.Step.class));
    }
    
    public static String toStepsJson(final List<OrchestrationWorkflowDocument.Step> steps) throws JsonGenerationException, JsonMappingException, IOException {
        return MAPPER.writeValueAsString(steps);
    }
}
