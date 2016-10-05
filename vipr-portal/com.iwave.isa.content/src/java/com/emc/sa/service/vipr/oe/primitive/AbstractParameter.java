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

public abstract class AbstractParameter<T> {

    private static final long serialVersionUID = 1L;

    private final String _name;
    private final String _friendlyName;
    private final boolean _locked;
    private final boolean _required;

    public AbstractParameter(final String name, final String friendlyName,
            final boolean locked, final boolean required) {
        _name = name;
        _friendlyName = friendlyName;
        _locked = locked;
        _required = required;
    }

    public abstract boolean isParameterList();

    public abstract ParameterList asParameterList();

    public abstract boolean isParameter();

    public abstract Parameter asParameter();

    public String name() {
        return _name;
    }

    public abstract T value();

    public boolean locked() {
        return _locked;
    }

    public boolean required() {
        return _required;
    }

    public String friendlyName() {
        return _friendlyName;
    }

}
