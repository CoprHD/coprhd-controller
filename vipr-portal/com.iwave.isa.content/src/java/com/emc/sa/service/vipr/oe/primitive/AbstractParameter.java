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
package com.emc.sa.service.vipr.oe.primitive;

import java.io.Serializable;

public abstract class AbstractParameter<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private String _friendlyName;
    private boolean _locked;
    private boolean _required;

    public abstract boolean isParameterList();

    public abstract ParameterList asParameterList();

    public abstract boolean isParameter();

    public abstract Parameter asParameter();

    public abstract T getValue();

    public abstract void setValue(T value);

    public boolean isLocked() {
        return _locked;
    }

    public void setLocked(final boolean locked) {
        _locked = locked;
    }

    public boolean isRequired() {
        return _required;
    }

    public void setRequired(final boolean required) {
        _required = required;
    }

    public String getFriendlyName() {
        return _friendlyName;
    }

    public void setFriendlyName(final String friendlyName) {
        _friendlyName = friendlyName;
    }
}
