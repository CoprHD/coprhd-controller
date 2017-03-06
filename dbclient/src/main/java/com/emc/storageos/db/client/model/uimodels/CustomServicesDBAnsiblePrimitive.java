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
package com.emc.storageos.db.client.model.uimodels;

import java.util.Collections;
import java.util.Set;

import com.emc.storageos.db.client.model.Cf;

/**
 * Database column family for ansible primitives
 *
 */
@Cf("CustomServicesDBAnsiblePrimitive")
public class CustomServicesDBAnsiblePrimitive extends CustomServicesDBPrimitive {
    private static final long serialVersionUID = 1L;
    private static final Set<String> ATTRIBUTES = Collections.singleton("playbook");
    private static final Set<String> INPUT_TYPES = Collections.singleton("input_params");

    @Override
    public Set<String> attributeKeys() {
        return ATTRIBUTES;
    }

    @Override
    public Set<String> inputTypes() {
        return INPUT_TYPES;
    }

}
