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

import java.net.URI;

import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.StringSet;

public abstract class CustomServicesUserPrimitive extends CustomServicesPrimitive {

    private static final long serialVersionUID = 1L;
    
    private static final String FRIENDLY_NAME = "friendlyName";
    private static final String DESCRIPTION = "description";
    private static final String SUCCESS_CRITERIA = "successCriteria";
    private static final String OUTPUT = "output";

    private String friendlyName;
    private String description;
    private String successCriteria;
    private StringSet output;

    public CustomServicesUserPrimitive() {}
    
    public CustomServicesUserPrimitive(final URI id, final String label,
            final String friendlyName, final String description, final String successCriteria,
            final StringSet output) {
        setId(id);
        setLabel(label);
        this.friendlyName = friendlyName;
        this.description = description;
        this.successCriteria = successCriteria;  
        this.output = output;
        
    }

    @Override
    @Name(FRIENDLY_NAME)
    public String getFriendlyName() {
        return friendlyName;
    }

    public void setFriendlyName(String friendlyName) {
        this.friendlyName = friendlyName;
        setChanged(FRIENDLY_NAME);
    }

    @Override
    @Name(DESCRIPTION)
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        setChanged(DESCRIPTION);
    }

    @Override
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

    public void setOutput(final StringSet output) {
        this.output = output;
    }
    
    public abstract boolean isCustomServiceAnsiblePrimitive();
    public abstract CustomServicesAnsiblePrimitive asCustomServiceAnsiblePrimitive(); 
    public abstract boolean isCustomServiceScriptPrimitive();
    public abstract CustomServicesScriptPrimitive asCustomServiceScriptPrimitive();
    
}
