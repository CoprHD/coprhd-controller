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
package com.emc.storageos.primitives;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import com.emc.storageos.primitives.input.InputParameter;
import com.emc.storageos.primitives.output.OutputParameter;

/**
 * Abstract Class that contains the base properties of a primitive
 */
public abstract class Primitive {

    private final URI id;
    private final String name;
    private final String friendlyName;
    private final String description;
    private final String successCriteria;
    private final List<InputParameter> input;
    private final List<OutputParameter> output;

    public Primitive(final URI id, final String name,
            final String friendlyName, final String description,
            final String successCriteria, final InputParameter[] input,
            final OutputParameter[] output) {
        this.id = id;
        this.name = name;
        this.friendlyName = friendlyName;
        this.description = description;
        this.successCriteria = successCriteria;
        this.input = Arrays.asList(input);
        this.output = Arrays.asList(output);
    }

    public URI getId() {
        return id;
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
