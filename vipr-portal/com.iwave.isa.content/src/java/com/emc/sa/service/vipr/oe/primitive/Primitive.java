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

import java.util.Map;

import com.emc.storageos.db.client.model.NamedURI;

/**
 * Abstract Class that contains the base properties of a primitive
 */
public abstract class Primitive {

    private final NamedURI _name;
    private final NamedURI _parent;
    private final String _description;
    private final String _successCriteria;
    private final Map<String, AbstractParameter<?>> _input;
    private final Map<String, AbstractParameter<?>> _output;

    public abstract boolean isRestPrimitive();

    public abstract RestPrimitive asRestPrimitive();

    public Primitive(final NamedURI name, final NamedURI parent,
            final String description, final String successCriteria,
            final Map<String, AbstractParameter<?>> input,
            final Map<String, AbstractParameter<?>> output) {
        _name = name;
        _parent = parent;
        _description = description;
        _successCriteria = successCriteria;
        _input = input;
        _output = output;
    }

    public NamedURI name() {
        return _name;
    }

    public NamedURI parent() {
        return _parent;
    }

    public String description() {
        return _description;
    }

    public String successCriteria() {
        return _successCriteria;
    }

    public Map<String, AbstractParameter<?>> input() {
        return _input;
    }

    public Map<String, AbstractParameter<?>> output() {
        return _output;
    }

}
