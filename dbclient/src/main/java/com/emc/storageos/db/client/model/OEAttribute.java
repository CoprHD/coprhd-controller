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
package com.emc.storageos.db.client.model;

/**
 * Class that contains a primitive attribute The attributes can be defined in a
 * primitive or in a parent primitive so each attribute has a primitive URI
 * attribute associated with it so that the 'owner' of this attribute value can
 * be tracked
 */
@Cf("OEAttribute")
public class OEAttribute extends DataObject {

    private NamedURI _primitive;
    private String _name;
    private String _value;

    @Name("value")
    public String getValue() {
        return _value;
    }

    public void setValue(final String value) {
        _value = value;
    }

    @Name("primitive")
    public NamedURI getPrimitive() {
        return _primitive;
    }

    public void setPrimitive(final NamedURI primitive) {
        _primitive = primitive;
    }
}
