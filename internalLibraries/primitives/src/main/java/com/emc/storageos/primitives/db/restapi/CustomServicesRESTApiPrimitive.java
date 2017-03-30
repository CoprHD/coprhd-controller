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
package com.emc.storageos.primitives.db.restapi;

import java.util.List;
import java.util.Map;

import com.emc.storageos.db.client.model.uimodels.CustomServicesDBRESTApiPrimitive;
import com.emc.storageos.primitives.CustomServicesConstants;
import com.emc.storageos.primitives.db.CustomServicesDBPrimitiveType;
import com.emc.storageos.primitives.input.InputParameter;
import com.emc.storageos.primitives.output.OutputParameter;

public class CustomServicesRESTApiPrimitive extends CustomServicesDBPrimitiveType {

    public CustomServicesRESTApiPrimitive(CustomServicesDBRESTApiPrimitive primitive, 
            Map<String, List<InputParameter>> input,
            Map<String, String> attributes, 
            List<OutputParameter> output) {
        super(primitive, input, attributes, output);
    }

    @Override
    public StepType stepType() {
        return StepType.REST;
    }

    @Override
    public String type() {
        return CustomServicesConstants.REST_API_PRIMITIVE_TYPE;
    }

}
