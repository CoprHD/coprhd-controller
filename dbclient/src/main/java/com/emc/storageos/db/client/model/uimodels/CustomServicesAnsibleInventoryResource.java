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
import com.emc.storageos.db.client.model.ModelObject;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.StringSet;

import java.net.URI;

/**
 * Column family that contains details for an ansible primitive definition
 */
@Cf("CustomServicesAnsibleInventoryResource")
public class CustomServicesAnsibleInventoryResource extends CustomServicesDBResource {

    private static final long serialVersionUID = 1L;

    private static final String ANSIBLEPACKAGE = "ansiblePackage";
    private URI ansiblePackage;

    @Name(ANSIBLEPACKAGE)
    public URI getAnsiblePackage() {
        return ansiblePackage;
    }

    public void setAnsiblePackage( final URI ansiblePackage ) {
        this.ansiblePackage = ansiblePackage;
        setChanged(ANSIBLEPACKAGE);
    }

}
