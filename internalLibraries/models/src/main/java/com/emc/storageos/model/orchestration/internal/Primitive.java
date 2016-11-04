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
package com.emc.storageos.model.orchestration.internal;

import java.util.Arrays;
import java.util.List;



/**
 * Abstract Class that contains the base properties of a primitive
 */
public abstract class Primitive {

    private String name;
    private String friendlyName;
    private String description;
    private String successCriteria;
    private List<InputParameter> input;
    private List<OutputParameter> output;

    public Primitive(final String name, final String friendlyName,
            final String description, final String successCriteria,
            final InputParameter[] input, final OutputParameter[] output) {
        this.name = name;
        this.friendlyName = friendlyName;
        this.description = description;
        this.successCriteria = successCriteria;
        this.input = Arrays.asList(input);
        this.output = Arrays.asList(output);
    }


    public String getName() {
        return name;
    }

    public String getFriendlyName() {
        return friendlyName;
    }
    
    public String getDescription() {
        return description;
    }

    public String getSuccessCriteria() {
        return successCriteria;
    }

    public List<InputParameter> getInput() {
        return input;
    }
    
    public List<OutputParameter> getOutput() {
        return output;
    }
}
