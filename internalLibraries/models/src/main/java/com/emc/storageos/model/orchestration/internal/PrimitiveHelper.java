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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;

/**
 * Helper class to load primitives
 */
public final class PrimitiveHelper {

    private static final Map<String, Primitive> PRIMITIVES = ImmutableMap.<String, Primitive>builder()
            .put(RestPrimitive.class.getName(), new RestPrimitive())
            .put(LocalAnsible.class.getName(), new LocalAnsible())
            .put(BlockServiceCreateVolume.class.getName(), new BlockServiceCreateVolume())
            .build();
    
    public static List<Primitive> list() {
        return new ArrayList<Primitive>(PRIMITIVES.values());
    }
    
}
