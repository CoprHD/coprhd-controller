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

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.uimodels.OEWorkflow;
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
    public static OEWorkflow create(final OrchestrationWorkflowDocument document) throws JsonGenerationException, JsonMappingException, IOException {
        final OEWorkflow oeWorkflow = new OEWorkflow();
        
        oeWorkflow.setId(URIUtil.createId(OEWorkflow.class));
        oeWorkflow.setName(document.getName());
        oeWorkflow.setDocument(toJson(document));

        return oeWorkflow;
    }
    
    /**
     * Update a workflow definition
     * @throws IOException 
     * @throws JsonMappingException 
     * @throws JsonGenerationException 
     */
    public static OEWorkflow update(final OEWorkflow oeWorkflow, final OrchestrationWorkflowDocument document) throws JsonGenerationException, JsonMappingException, IOException {

        oeWorkflow.setDocument(toJson(document));
        return oeWorkflow;
    }
    
    public static OrchestrationWorkflowDocument toWorkflowDocument(final String rawJson) throws JsonParseException, JsonMappingException, IOException {
        
        return MAPPER.readValue(rawJson, OrchestrationWorkflowDocument.class);
    }
    
    public static String toJson(final OrchestrationWorkflowDocument document) throws JsonGenerationException, JsonMappingException, IOException {
        return MAPPER.writeValueAsString(document);
    }
}
