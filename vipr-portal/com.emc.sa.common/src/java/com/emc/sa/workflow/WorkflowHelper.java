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
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.uimodels.CustomServicesWorkflow;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument.Step;
import com.emc.storageos.primitives.Primitive.StepType;

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
    public static CustomServicesWorkflow create(final CustomServicesWorkflowDocument document) throws JsonGenerationException, JsonMappingException, IOException {
        final CustomServicesWorkflow workflow = new CustomServicesWorkflow();
        
        workflow.setId(URIUtil.createId(CustomServicesWorkflow.class));
        workflow.setLabel(document.getName());
        workflow.setName(document.getName());
        workflow.setDescription(document.getDescription());
        workflow.setSteps(toStepsJson(document.getSteps()));
        
        final StringSet primitives = new StringSet();
        for(final Step step : document.getSteps()) {
            switch(StepType.fromString(step.getType())) {
            case VIPR_REST:
                break;
            default:
                primitives.add(step.getOperation().toString());
            }
        }

        workflow.setPrimitives(primitives);
        return workflow;
    }
    
    /**
     * Update a workflow definition
     * @throws IOException 
     * @throws JsonMappingException 
     * @throws JsonGenerationException 
     */
    public static CustomServicesWorkflow update(final CustomServicesWorkflow oeWorkflow, final CustomServicesWorkflowDocument document) throws JsonGenerationException, JsonMappingException, IOException {

        if(document.getDescription() != null) {
            oeWorkflow.setDescription(document.getDescription());
        }

        if(document.getName() != null) {
            oeWorkflow.setName(document.getName());
            oeWorkflow.setLabel(document.getName());
        }
        
        oeWorkflow.setSteps(toStepsJson(document.getSteps()));
        return oeWorkflow;
    }

    public static CustomServicesWorkflow updateState(final CustomServicesWorkflow oeWorkflow, final String state) {
        oeWorkflow.setState(state);
        return oeWorkflow;
    }
    
    public static CustomServicesWorkflowDocument toWorkflowDocument(final CustomServicesWorkflow workflow) throws JsonParseException, JsonMappingException, IOException {
        final CustomServicesWorkflowDocument document = new CustomServicesWorkflowDocument();
        document.setName(workflow.getName());
        document.setDescription(workflow.getDescription());
        document.setSteps(toDocumentSteps(workflow.getSteps()));
        
        return document;
    }
    
    public static CustomServicesWorkflowDocument toWorkflowDocument(final String workflow) throws JsonParseException, JsonMappingException, IOException {
        return MAPPER.readValue(workflow, CustomServicesWorkflowDocument.class);
    }
    
    public static String toWorkflowDocumentJson( CustomServicesWorkflow workflow) throws JsonGenerationException, JsonMappingException, JsonParseException, IOException {
        return MAPPER.writeValueAsString(toWorkflowDocument(workflow));
    }
    
    private static List<CustomServicesWorkflowDocument.Step> toDocumentSteps(final String steps) throws JsonParseException, JsonMappingException, IOException {
        return steps == null ? null :  MAPPER.readValue(steps, MAPPER.getTypeFactory().constructCollectionType(List.class, CustomServicesWorkflowDocument.Step.class));
    }
    
    private static String toStepsJson(final List<CustomServicesWorkflowDocument.Step> steps) throws JsonGenerationException, JsonMappingException, IOException {
        return MAPPER.writeValueAsString(steps);
    }
}
