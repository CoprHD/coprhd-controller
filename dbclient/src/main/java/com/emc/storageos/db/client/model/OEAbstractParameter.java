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

import java.net.URI;

/**
 * DB model class that contains the base properties for a primitive
 * 
 * A parameter can be 'owned' by a primitive or a parent of the primitive. So
 * each parameter has a primitive URI so that the owner of the parameter can be
 * tracked
 */
public abstract class OEAbstractParameter extends DataObject {

    private URI _primitive;
    private String _name;
    private String _friendlyName;
    private boolean _locked;
    private boolean _required;

    @Name("primitive")
    public URI getPrimitive() {
        return _primitive;
    }

    public void setPrimitive(final URI primitive) {
        _primitive = primitive;
        setChanged("primitive");
    }

    @Name("name")
    public String getName() {
        return _name;
    }

    public void setName(final String name) {
        _name = name;
        setChanged("name");
    }

    @Name("locked")
    public boolean getLocked() {
        return _locked;
    }

    public void setLocked(final boolean locked) {
        _locked = locked;
        setChanged("locked");
    }

    @Name("required")
    public boolean getRequired() {
        return _required;
    }

    public void setRequired(final boolean required) {
        _required = required;
        setChanged("required");
    }

    @Name("friendlyName")
    public String getFriendlyName() {
        return _friendlyName;
    }

    public void setFriendlyName(final String friendlyName) {
        _friendlyName = friendlyName;
        setChanged("friendlyName");
    }

    public abstract boolean isParameter();

    public abstract OEParameter asParameter();

    public abstract boolean isParameterList();

    public abstract OEParameterList asParameterList();

}
