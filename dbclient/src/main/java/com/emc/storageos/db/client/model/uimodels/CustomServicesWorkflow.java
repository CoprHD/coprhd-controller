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
import com.emc.storageos.db.client.model.IndexByKey;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.RelationIndex;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.model.valid.EnumType;

/**
 * DB model to represent an orchestration engine workflow document
 */
@Cf("OrchestrationWorkflow")
public class CustomServicesWorkflow extends ModelObjectWithACLs {

    public static final String NAME = "name";
    public static final String DESCRIPTION = "description";
    public static final String STEPS = "steps";
    public static final String STATE = "state";
    public static final String PRIMITIVES = "primitives";
    
    private String name;
    private String description;
    private String steps;
    private String state = OrchestrationWorkflowStatus.NONE.toString();
    private StringSet primitives;

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
    @Name(STATE)
    public String getState() {
        return state == null? OrchestrationWorkflowStatus.NONE.toString() : state;
    }

    public void setState(final String state) {
        this.state = state;
        setChanged(STATE);
    }
    
    @RelationIndex(cf = "WorkflowPrimitives", type = UserPrimitive.class)
    @IndexByKey
    @Name(PRIMITIVES)
    public StringSet getUserPrimitives() {
        return primitives;
    }
    
    public void setPrimitives(final StringSet primitives) {
        this.primitives = primitives;
        setChanged(PRIMITIVES);
    }
}
