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
package com.emc.storageos.primitives.output;

import java.util.Arrays;
import java.util.List;

import com.emc.storageos.primitives.Parameter;
import com.emc.storageos.primitives.Parameter.ParameterType;

/**
 * Class that represents a table of output parameters
 */
public class TableOutputParameter extends OutputParameter {

    private final List<BasicOutputParameter> columns;

    public TableOutputParameter(String name, BasicOutputParameter[] columns) {
        super(name);
        this.columns = Arrays.asList(columns);
    }

    @Override 
    public boolean isBasicOutputParameter() {
        return false;
    }
    
    @Override 
    public BasicOutputParameter asBasicOutputParameter() {
        return null;
    }

    @Override 
    public boolean isTableOutputParameter() {
        return true;
    }

    @Override 
    public TableOutputParameter asTableOutputParameter() {
        return this;
    }
    
    @Override 
    public ParameterType getType() {
        return ParameterType.TABLE;
    }

    public List<BasicOutputParameter> getColumns() {
        return columns;
    }

}
