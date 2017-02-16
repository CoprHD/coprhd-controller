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
 * Column family that represents a script that can be used in a custom service workflow
 */
@Cf("CustomServiceScriptPrimitive")
public class CustomServicesScriptPrimitive extends CustomServicesUserPrimitive {

    private static final long serialVersionUID = 1L;
    
    public static final String INPUT = "input";
    public static final String SCRIPT = "script";
    
    private StringSet input;
    private URI script;
    
    @Name(INPUT)
    public StringSet getInput() {
        return input;
    }
    
    public void setInput(final StringSet input) {
        this.input = input;
        setChanged(INPUT);
    }
    
    @Name(SCRIPT)
    public URI getScript() {
        return script;
    }

    public void setScript(final URI script) {
        this.script = script;
        setChanged(SCRIPT);
    }

    @Override
    public boolean isCustomServiceAnsiblePrimitive() {
        return false;
    }

    @Override
    public CustomServicesAnsiblePrimitive asCustomServiceAnsiblePrimitive() {
        return null;
    }

    @Override
    public boolean isCustomServiceScriptPrimitive() {
        return true;
    }

    @Override
    public CustomServicesScriptPrimitive asCustomServiceScriptPrimitive() {
        return this;
    }
}
