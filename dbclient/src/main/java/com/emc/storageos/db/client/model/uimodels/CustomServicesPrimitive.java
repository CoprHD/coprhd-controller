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

import com.emc.storageos.db.client.model.ModelObject;

public abstract class CustomServicesPrimitive extends ModelObject {

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
    
    public abstract String getFriendlyName();
    public abstract String getDescription();
    public abstract String getSuccessCriteria();
    public abstract StepType getType();
    
}
