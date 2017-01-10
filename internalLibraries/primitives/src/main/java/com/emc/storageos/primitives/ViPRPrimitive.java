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

import com.emc.storageos.primitives.input.InputParameter;
import com.emc.storageos.primitives.output.OutputParameter;

/**
 * Base class for a primitive that represents a ViPR API call
 */
public abstract class ViPRPrimitive extends Primitive {

    private final static StepType TYPE = StepType.VIPR_REST;
    
    public ViPRPrimitive(final URI id, final String name, final String friendlyName, final String description,
            final String successCriteria, final InputParameter[] input,
            OutputParameter[] output) {
        super(id, name, friendlyName, description, successCriteria, input, output,TYPE);
    }

    public abstract String path();

    public abstract String method();

    public abstract String body();

}
