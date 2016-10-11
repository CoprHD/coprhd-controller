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

/**
 * Class that represents a list of parameters. A parameter list contains a map
 * of parameters that could be further lists or single parameters
 */
public class ParameterList extends
        AbstractParameter<Map<String, AbstractParameter<?>>> {

    private final Map<String, AbstractParameter<?>> _parameters;

    public ParameterList(final String name, final String friendlyName,
            final Map<String, AbstractParameter<?>> parameters,
            final boolean locked, final boolean required) {
        super(name, friendlyName, locked, required);
        _parameters = parameters;
    }

    @Override
    public Map<String, AbstractParameter<?>> value() {
        return _parameters;
    }

    @Override
    public boolean isParameterList() {
        return true;
    }

    @Override
    public ParameterList asParameterList() {
        // TODO Auto-generated method stub
        return this;
    }

    @Override
    public boolean isParameter() {
        return false;
    }

    @Override
    public Parameter asParameter() {
        return null;
    }

}
