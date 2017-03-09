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

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.StringSet;

/**
 * Column family that contains an ansible package
 */
@Cf("CustomServiceAnsiblePackage")
public class CustomServicesAnsiblePackage extends CustomServicesPrimitiveResource {

    private static final long serialVersionUID = 1L;

    public static final String PLAYBOOKS = "playbooks";

    private StringSet playbooks;
    
    @Name(PLAYBOOKS)
    public StringSet getPlaybooks() {
        return playbooks;
    }
    
    public void setPlaybooks(final StringSet playbooks) {
        this.playbooks = playbooks;
        setChanged(PLAYBOOKS);
    }

    @Override
    public boolean isCustomServiceAnsiblePackage() {
        return true;
    }

    @Override
    public CustomServicesAnsiblePackage asCustomServiceAnsiblePackage() {
        return this;
    }

    @Override
    public boolean isCustomServiceScriptResource() {
        return false;
    }

    @Override
    public CustomServicesScriptResource asCustomServiceScriptResource() {
        return null;
    }

    @Override
    public String suffix() {
        return ".tar";
    }

}
