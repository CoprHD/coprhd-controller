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

import java.io.File;
import java.io.IOException;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.junit.Assert;
import org.junit.Test;
import org.testng.reporters.Files;

import com.emc.sa.workflow.WorkflowHelper;
import com.emc.storageos.db.client.model.uimodels.CustomServicesWorkflow;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;

/**
 * Test class for the workflow helper
 */
public class WorkflowTest {

    @Test
    public void createWorkflow() throws JsonGenerationException, JsonMappingException, IOException {
        final String json = Files.readFile(new File("/Users/ssulliva/Documents/OEjson_sample.json"));

        final CustomServicesWorkflowDocument wfDefinition = WorkflowHelper.toWorkflowDocument(json);
        
        CustomServicesWorkflow workflow = WorkflowHelper.create(wfDefinition);
        
        CustomServicesWorkflowDocument result = WorkflowHelper.toWorkflowDocument(workflow.getSteps());

        Assert.assertEquals(wfDefinition.getName(), result.getName());
        
        
    }
}
