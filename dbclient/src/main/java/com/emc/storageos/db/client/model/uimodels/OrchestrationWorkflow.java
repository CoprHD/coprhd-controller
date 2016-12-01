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
package com.emc.storageos.db.client.model.uimodels;

import com.emc.storageos.db.client.model.AlternateId;
import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.model.valid.EnumType;

/**
 * DB model to represent an orchestration engine workflow document
 */
@Cf("OrchestrationWorkflow")
public class OrchestrationWorkflow extends ModelObjectWithACLs {

    public static final String NAME = "name";
    public static final String DESCRIPTION = "description";
    public static final String STEPS = "steps";
    public static final String STATUS = "status";

    
    private String name;
    private String description;
    private String steps;
    private String status = OrchestrationWorkflowStatus.NONE.toString();

    public enum OrchestrationWorkflowStatus {
        NONE,
        VALID,
        INVALID,
        PUBLISHED
    }


    @Name(NAME)
    @AlternateId("OrchestrationWorkflowNameIndex")
    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
        setChanged(NAME);
    }
    

    @Name(DESCRIPTION)
    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
        setChanged(DESCRIPTION);
    }

    @Name(STEPS)
    public String getSteps() {
        return steps;
    }

    public void setSteps(final String steps) {
        this.steps = steps;
        setChanged(STEPS);
    }

    @Override
    public Object[] auditParameters() {
        // TODO Auto-generated method stub
        return null;
    }

    @AlternateId("OrchestrationWorkflowStatusIndex")
    @EnumType(OrchestrationWorkflowStatus.class)
    @Name(STATUS)
    public String getStatus() {
        return status == null? OrchestrationWorkflowStatus.NONE.toString() : status;
    }

    public void setStatus(final String status) {
        this.status = status;
        setChanged(STATUS);
    }
}
