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
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;


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

    private String name;
    private StepType type;
    private String friendlyName;
    private String description;
    private String successCriteria;
    private Map<String,InputParameter> input;
    private Map<String,OutputParameter> output;

    public Primitive(final String name, final String friendlyName,
            final String description, final String successCriteria,
            final InputParameter[] input, final OutputParameter[] output, final StepType type) {
        this.name = name;
        this.friendlyName = friendlyName;
        this.description = description;
        this.successCriteria = successCriteria;
        this.input = Arrays.asList(input).stream().collect(
                Collectors.toMap(InputParameter::getName, elem -> elem));
        this.output = Arrays.asList(output).stream().collect(
                Collectors.toMap(OutputParameter::getName, elem -> elem));;
        this.type = type;
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

    public Map<String,InputParameter> getInput() {
        return input;
    }
    
    public Map<String,OutputParameter> getOutput() {
        return output;
    }
}
