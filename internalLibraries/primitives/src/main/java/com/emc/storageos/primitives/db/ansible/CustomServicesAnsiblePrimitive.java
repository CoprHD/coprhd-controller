/*
 * Copyright 2017 Dell Inc. or its subsidiaries.
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
package com.emc.storageos.primitives.db.ansible;

import java.util.List;
import java.util.Map;

import com.emc.storageos.db.client.model.uimodels.CustomServicesDBAnsiblePrimitive;
import com.emc.storageos.primitives.CustomServicesConstants;
import com.emc.storageos.primitives.db.CustomServicesDBPrimitiveType;
import com.emc.storageos.primitives.input.InputParameter;
import com.emc.storageos.primitives.output.OutputParameter;

/**
 * Class that represents an ansible primitive as a java object
 *
 */
public class CustomServicesAnsiblePrimitive extends
        CustomServicesDBPrimitiveType {

    public static final String TYPE = CustomServicesConstants.ANSIBLE_PRIMITIVE_TYPE;

    public CustomServicesAnsiblePrimitive(final CustomServicesDBAnsiblePrimitive primitive,
            final Map<String, List<InputParameter>> input,
            final Map<String, String> attributes,
            final List<OutputParameter> output) {
        super(primitive, input, attributes, output);
    }

    @Override
    public StepType stepType() {
        return StepType.LOCAL_ANSIBLE;
    }

    @Override
    public String type() {
        return TYPE;
    }
}
