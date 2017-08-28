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

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.model.AlternateId;
import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.IndexByKey;
import com.emc.storageos.db.client.model.ModelObject;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.RelationIndex;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.model.valid.EnumType;

/**
 * DB model to represent an custom services workflow document
 */
@Cf("CustomServicesWorkflow")
public class CustomServicesWorkflow extends ModelObject {

    private static final long serialVersionUID = 1L;

    public static final String ID_PREFIX = "urn:storageos:CustomServicesWorkflow:";

    public static final String NAME = "label";
    public static final String DESCRIPTION = "description";
    public static final String STEPS = "steps";
    public static final String STATE = "state";
    public static final String PRIMITIVES = "primitives";
    private static final String ATTRIBUTES = "attributes";
    
    private String name;
    private String description;
    private String steps;
    private String state = CustomServicesWorkflowStatus.NONE.toString();
    private StringSet primitives;
    private StringMap attributes;

    public enum CustomServicesWorkflowStatus {
        NONE,
        VALID,
        INVALID,
        PUBLISHED
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

    @AlternateId("CustomServicesWorkflowStatusIndex")
    @EnumType(CustomServicesWorkflowStatus.class)
    @Name(STATE)
    public String getState() {
        return state == null? CustomServicesWorkflowStatus.NONE.toString() : state;
    }

    public void setState(final String state) {
        this.state = state;
        setChanged(STATE);
    }
    
    @RelationIndex(cf = "CustomServicesWorkflowPrimitives", type = CustomServicesDBPrimitive.class)
    @IndexByKey
    @Name(PRIMITIVES)
    public StringSet getPrimitives() {
        if (primitives == null) {
            primitives = new StringSet();
        }
        return primitives;
    }
    
    public void setPrimitives(final StringSet primitives) {
        this.primitives = primitives;
        setChanged(PRIMITIVES);
    }

    public void addPrimitives(final List<URI> primitives) {
        for (URI primitiveUri : primitives) {
            getPrimitives().add(primitiveUri.toString());
        }
        setChanged(PRIMITIVES);
    }

    public void removePrimitives(final List<URI> primitives) {
        for (URI primitiveUri : primitives) {
            getPrimitives().remove(primitiveUri.toString());
        }
        setChanged(PRIMITIVES);
    }

    @Name(ATTRIBUTES)
    public StringMap getAttributes() {
        return attributes;
    }

    public void setAttributes(final StringMap attributes) {
        this.attributes = attributes;
        setChanged(ATTRIBUTES);
    }

}
