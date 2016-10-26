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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.emc.sa.service.DummyDbClient;
import com.emc.sa.service.vipr.oe.gson.WorkflowDefinition;
import com.emc.storageos.db.client.DbClient;
import com.google.gson.Gson;

/**
 * Test class for the workflow helper
 */
public class WorkflowTest {

    private DbClient dbClient;
    private final static Gson GSON = new Gson();
    
    @Before
    public void setUp() {
        dbClient = new DummyDbClient();
        dbClient.start();
    }

    @Test
    public void createWorkflow() {
        final String wfJson = "{ \"WorkflowName\": \"sample\"}";
        final WorkflowDefinition wfDefinition = GSON.fromJson(wfJson, WorkflowDefinition.class);
        
        WorkflowHelper.persist(wfDefinition, dbClient);
        
        WorkflowDefinition result = WorkflowHelper.query("sample", dbClient);

        Assert.assertEquals(wfJson, wfDefinition.getWorkflowName(), result.getWorkflowName());
        
        
    }
}
