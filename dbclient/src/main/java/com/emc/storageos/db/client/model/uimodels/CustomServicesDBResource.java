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

import com.emc.storageos.db.client.model.AlternateId;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.StringSetMap;

/**
 * Base class for primitive resources that will be stored in the database
 *
 */
public abstract class CustomServicesDBResource extends CustomServicesPrimitiveResourceModel {

    public static final String RESOURCE = "resource";
    public static final String ATTRIBUTES = "attributes";
    public static final String PARENTID = "parentId";
    private static final long serialVersionUID = 1L;
    private byte[] resource;
    private StringSetMap attributes;
    private URI parentId;

    @Name(RESOURCE)
    public byte[] getResource() {
        return resource;
    }

    public void setResource(final byte[] resource) {
        this.resource = resource;
        setChanged(RESOURCE);
    }

    @Name(ATTRIBUTES)
    public StringSetMap getAttributes() {
        return attributes;
    }

    public void setAttributes(final StringSetMap attributes) {
        this.attributes = attributes;
        setChanged(ATTRIBUTES);
    }

    @Name(PARENTID)
    @AlternateId("CustomServicesDBResourceParentIdIndex")
    public URI getParentId() {
        return parentId;
    }

    public void setParentId(final URI parentId) {
        this.parentId = parentId;
        setChanged(PARENTID);
    }
}
