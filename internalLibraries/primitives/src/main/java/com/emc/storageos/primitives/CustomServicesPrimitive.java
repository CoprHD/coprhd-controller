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
package com.emc.storageos.primitives;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.primitives.input.InputParameter;
import com.emc.storageos.primitives.output.OutputParameter;

public interface CustomServicesPrimitive {

    public enum StepType {
        VIPR_REST("vipr"),
        REST("rest"),
        LOCAL_ANSIBLE("ansible"),
        REMOTE_ANSIBLE("remote_ansible"),
        SHELL_SCRIPT("script"),
        WORKFLOW("Workflow"),
        START("Start"),
        END("End");

        private final String stepType;
        private StepType(final String stepType)
        {
            this.stepType = stepType;
        }

        @Override
        public String toString() {
            return stepType;
        }
        public static StepType fromString(String v) {
            for (StepType e : StepType.values())
            {
                if (v.equals(e.stepType))
                    return e;
            }

            return null;
        }
    }
    
    public URI id();
    public String name();
    public String friendlyName();
    public String description();
    public String successCriteria();
    public StepType stepType();
    public Map<String, List<InputParameter>> input();
    public List<OutputParameter> output();
    public Map<String, String> attributes();
    public NamedURI resource();
    
}
