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


import com.emc.storageos.primitives.input.InputParameter;
import com.emc.storageos.primitives.output.OutputParameter;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Abstract Class that contains the base properties of a primitive
 */
public abstract class Primitive {

    public enum StepType {
        VIPR_REST("ViPR REST API"),
        REST("REST API"),
        LOCAL_ANSIBLE("Local Ansible"),
        REMOTE_ANSIBLE("Remote Ansible"),
        SHELL_SCRIPT("Shell Script"),
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

    public enum InputType {
        INPUT_PARAMS("input_params"),
        CONNECTION_DETAILS("connection_details"),
        ANSIBLE_OPTIONS("ansible_options");

        private final String inputType;
        private InputType(final String inputType)
        {
            this.inputType = inputType;
        }

        @Override
        public String toString() {
            return inputType;
        }
        public static InputType fromString(String v) {
            for (InputType e : InputType.values())
            {
                if (v.equals(e.inputType))
                    return e;
            }

            return null;
        }
    }
    private final URI id;
    private final String name;
    private final StepType type;
    private final String friendlyName;
    private final String description;
    private final String successCriteria;
    private Map<InputType, List<InputParameter>> input;
    private List<OutputParameter> output;

    public Primitive(final URI id, final String name, final String friendlyName,
            final String description, final String successCriteria,
            final InputParameter[] input, final OutputParameter[] output, final StepType type) {
        this.id = id;
        this.name = name;
        this.friendlyName = friendlyName;
        this.description = description;
        this.successCriteria = successCriteria;
        this.input = new HashMap<InputType, List<InputParameter>>(){{
            put(InputType.INPUT_PARAMS,Arrays.asList(input));
        }};
        this.output = Arrays.asList(output);
        this.type = type;
    }

    public URI getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public StepType getType() {
        return type;
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

    public Map<InputType, List<InputParameter>> getInput() {
        return input;
    }
    
    public List<OutputParameter> getOutput() {
        return output;
    }
}
