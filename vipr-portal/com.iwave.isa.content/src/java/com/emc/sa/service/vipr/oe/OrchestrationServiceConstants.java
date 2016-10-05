/*
 * Copyright 2016
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
package com.emc.sa.service.vipr.oe;

/**
 * Orchestration Engine Constants
 */
public final class OrchestrationServiceConstants {


    public static final int STEP_ID = 0;
    public static final int INPUT_FIELD = 1;
    public static final String WF_ID = "WorkflowId";
    public static final long TIMEOUT = 3600; //min

    public enum InputType {
        FROM_USER("InputFromUser"),
        FROM_STEP("InputFromOtherSTep"),
        OTHERS("Others"),
        ASSET_OPTION("AssetOption"),
        START("Start"),
        END("End");

        private final String inputType;
        private InputType(final String inputType)
        {
            this.inputType = inputType;
        }
    }

    public enum OperationType {
        VIPR_REST("ViPR REST API"),
        REST("REST API"),
        SHELL("Shell Script"),
        PYTHON("Python Script"),
        ANSIBLE("Ansible Script");

        private final String operationType;
        private OperationType(final String operationType)
        {
            this.operationType = operationType;
        }
    }
}
