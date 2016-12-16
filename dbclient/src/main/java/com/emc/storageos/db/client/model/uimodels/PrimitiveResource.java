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

import com.emc.storageos.db.client.model.ModelObject;
import com.emc.storageos.db.client.model.Name;

/**
 *
 */
public class PrimitiveResource extends ModelObject {

    private static final long serialVersionUID = 1L;

    public static final String TYPE = "type";
    public static final String RESOURCE = "resource";
    public static final String METADATA = "metadata";

    private String type;
    private byte[] resource;
    private URI metadata;

    @Name(TYPE)
    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
        setChanged(type);
    }

    @Name(RESOURCE)
    public byte[] getResource() {
        return resource;
    }

    public void setResource(byte[] resource) {
        this.resource = resource;
        setChanged(RESOURCE);
    }

    @Name(METADATA)
    public URI getMetadata() {
        return metadata;
    }

    public void setMetadata(final URI metadata) {
        this.metadata = metadata;
        setChanged(METADATA);
    }
}
