/*
 * Copyright 2017 Dell Inc. or its subsidiaries.
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

import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.NamedRelationIndex;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;

/**
 * Base class for primitives that will be stored in the database
 *
 */
public abstract class CustomServicesDBPrimitive extends CustomServicesPrimitiveModel {

    private static final long serialVersionUID = 1L;
    
    private static final String FRIENDLY_NAME = "friendlyName";
    private static final String DESCRIPTION = "description";
    private static final String SUCCESS_CRITERIA = "successCriteria";
    private static final String ATTRIBUTES = "attributes";
    private static final String INPUT = "input";
    private static final String OUTPUT = "output";
    public static final String RESOURCE = "resource";
    
    private String friendlyName;
    private String description;
    private String successCriteria;
    private StringMap attributes;
    private StringSetMap input;
    private StringSet output;
    private NamedURI resource;

    public CustomServicesDBPrimitive() {}

    @Name(FRIENDLY_NAME)
    public String getFriendlyName() {
        return friendlyName;
    }

    public void setFriendlyName(String friendlyName) {
        this.friendlyName = friendlyName;
        setChanged(FRIENDLY_NAME);
    }

    @Name(DESCRIPTION)
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        setChanged(DESCRIPTION);
    }

    @Name(SUCCESS_CRITERIA)
    public String getSuccessCriteria() {
        return successCriteria;
    }

    public void setSuccessCriteria(String successCriteria) {
        this.successCriteria = successCriteria;
        setChanged(SUCCESS_CRITERIA);
    }

    @Name(OUTPUT)
    public StringSet getOutput() {
        return output;

    }
    
    @Name(ATTRIBUTES)
    public StringMap getAttributes() {
        return attributes;
    }
    
    public void setAttributes(final StringMap attributes) {
        this.attributes = attributes;
        setChanged(ATTRIBUTES);
    }

    public void setOutput(final StringSet output) {
        this.output = output;
        setChanged(OUTPUT);
    }
    
    @Name(INPUT)
    public StringSetMap getInput() {
        return input;
    }
    
    public void setInput(final StringSetMap input) {
        this.input = input;
        setChanged(INPUT);
    }

    @NamedRelationIndex(cf = "NamedRelation", type = CustomServicesDBResource.class)
    @Name(RESOURCE)
    public NamedURI getResource() {
        return resource;
    }
    
    public void setResource( final NamedURI resource ) {
        this.resource = resource;
        setChanged(RESOURCE);
    }
}
