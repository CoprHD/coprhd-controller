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
package com.emc.storageos.primitives.input;

import java.util.Arrays;
import java.util.List;

import com.emc.storageos.primitives.Parameter;
import com.emc.storageos.primitives.Parameter.ParameterType;
import com.emc.storageos.primitives.input.BasicInputParameter.InputColumn;

/**
 * Input parameter type that represents a table of input parameters
 */
public class TableInputParameter extends InputParameter {

    private final List<InputColumn<?, ?>> columns;

    public TableInputParameter(String name, InputColumn<?, ?>[] columns) {
        super(name);
        this.columns = Arrays.asList(columns);
    }

    @Override 
    public ParameterType getType() {
        return ParameterType.TABLE;
    }

    public List<InputColumn<?, ?>> getColumns() {
        return columns;
    }

    @Override 
    public boolean isBasicInputParameter() {
        return false;
    }

    @Override 
    public BasicInputParameter<?> asBasicInputParameter() {
        return null;
    }

    @Override 
    public boolean isTableInputParameter() {
        return true;
    }

    @Override 
    public TableInputParameter asTableInputParameter() {
        return this;
    }
}
