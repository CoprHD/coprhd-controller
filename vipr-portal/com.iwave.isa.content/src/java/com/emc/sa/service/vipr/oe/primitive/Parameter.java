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

/**
 * Class that represents a single input parameter. The String parameter will
 * also have a type Integer,String, etc.
 */
public class Parameter extends AbstractParameter<String> {

    /**
     * Enumeration that represents the different orchestration parameter types
     */
    public enum Type {
        STRING, INTEGER, ASSET
    };

    private final String _value;
    private final Type _type;

    public Parameter(final String name, final String friendlyName,
            final String value, final Type type, final boolean locked,
            final boolean required) {
        super(name, friendlyName, locked, required);
        _value = value;
        _type = type;
    }

    @Override
    public String value() {
        return _value;
    }

    public Type type() {
        return _type;
    }

    @Override
    public boolean isParameterList() {
        return false;
    }

    @Override
    public ParameterList asParameterList() {
        return null;
    }

    @Override
    public boolean isParameter() {
        return true;
    }

    @Override
    public Parameter asParameter() {
        return this;
    }

}
