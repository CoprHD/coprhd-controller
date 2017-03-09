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

import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Name;

public abstract class CustomServicesPrimitiveResource extends DataObject {

    private static final long serialVersionUID = 1L;
    public static final String RESOURCE = "resource";

    private byte[] resource;

    public abstract boolean isCustomServiceAnsiblePackage();
    public abstract CustomServicesAnsiblePackage asCustomServiceAnsiblePackage();
    public abstract boolean isCustomServiceScriptResource();
    public abstract CustomServicesScriptResource asCustomServiceScriptResource();
    public abstract String suffix();

    @Name(RESOURCE)
    public byte[] getResource() {
        return resource;
    }

    public void setResource(final byte[] resource) {
        this.resource = resource;
        setChanged(RESOURCE);
    }
}
