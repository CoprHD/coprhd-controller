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

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.StringSet;

/**
 * Column family that contains details for an ansible primitive definition
 */
@Cf("CustomServiceAnsiblePrimitive")
public class CustomServicesAnsiblePrimitive extends CustomServicesUserPrimitive {

    private static final long serialVersionUID = 1L;

    public static final String DESCRIPTION = "description";
    public static final String PLAYBOOK = "playbook";
    public static final String EXTRA_VARS = "extraVars";
    public static final String ARCHIVE = "archive";
    
    private String description;
    private String playbook;
    private StringSet extraVars;
    private URI archive;
    
    @Name(DESCRIPTION)
    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
        setChanged(DESCRIPTION);
    }

    @Name(PLAYBOOK)
    public String getPlaybook() {
        return playbook;
    }

    public void setPlaybook(final String playbok) {
        this.playbook = playbok;
        setChanged(PLAYBOOK);
    }

    @Name(EXTRA_VARS)
    public StringSet getExtraVars() {
        return extraVars;
    }

    public void setExtraVars(final StringSet extraVars) {
        this.extraVars = extraVars;
        setChanged(EXTRA_VARS);
    }
    
    @Name(ARCHIVE)
    public URI getArchive() {
        return archive;
    }
    
    public void setArchive(final URI archive) {
        this.archive = archive;
        setChanged(ARCHIVE);
    }
    
    @Override
    public boolean isCustomServiceAnsiblePrimitive() {
        return true;
    }

    @Override
    public CustomServicesAnsiblePrimitive asCustomServiceAnsiblePrimitive() {
        return this;
    }

    @Override
    public boolean isCustomServiceScriptPrimitive() {
        return false;
    }

    @Override
    public CustomServicesScriptPrimitive asCustomServiceScriptPrimitive() {
        return null;
    }

}
