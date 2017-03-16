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
package com.emc.storageos.primitives;

import java.util.Arrays;
import java.util.List;

/**
 * Custom Services Constants
 */
public final class CustomServicesConstants {
    // Validation Error Messages
    // TODO: The following messages will be revisited when the UI is ready
    public static final String ERROR_MSG_START_END_NOT_DEFINED = "Start or End Step not defined";
    public static final String ERROR_MSG_WORKFLOW_STEP_NULL = "Workflow Step is null";
    public static final String ERROR_MSG_WORKFLOW_NEXT_STEP_NOT_DEFINED = "Next step not defined for the step";
    public static final String ERROR_MSG_WORKFLOW_PREVIOUS_STEP_NOT_DEFINED = "Previous step not defined for the step";
    public static final String ERROR_MSG_DISPLAY_IS_EMPTY = "Display Name is empty";
    public static final String ERROR_MSG_DISPLAY_NAME_NOT_UNIQUE = "Display Name is not unique";
    public static final String ERROR_MSG_INPUT_NAME_IS_EMPTY = "Input Name is empty";
    public static final String ERROR_MSG_INPUT_NAME_NOT_UNIQUE_IN_STEP = "Input Name is not unique in the step";

    public static final int STEP_ID = 0;
    public static final int INPUT_FIELD = 1;
    public static final String WF_ID = "WorkflowId";

    // SuccessCriteria Constants
    public static final String RETURN_CODE = "code";
    public static final String TASK = "task";
    public static final long TIMEOUT = 3600 * 1000;

    public static final List<String> BODY_REST_METHOD = Arrays.asList("POST", "PUT", "DELETE");
    // Script Execution Constants
    public static final String ANSIBLE_LOCAL_BIN = "/usr/bin/ansible-playbook";
    public static final String SHELL_BIN = "/usr/bin/sh";
    public static final String SHELL_LOCAL_BIN = "/usr/bin/ssh";
    public static final String ORDER_DIR_PATH = "/opt/storageos/CS";
    public static final String EXTRA_VARS = "--extra-vars";
    public static final String UNTAR = "tar";
    public static final String UNTAR_OPTION = "-zxvf";
    public static final String REMOVE = "/bin/rm";
    public static final String REMOVE_OPTION = "-rf";
    // Ansible Options
    public static final String ANSIBLE_BIN = "remote_ansible_bin";
    public static final String ANSIBLE_PLAYBOOK = "remote_node_playbook";
    public static final String ANSIBLE_HOST_FILE = "remote_host_file";
    public static final String ANSIBLE_USER = "remote_ansible_user";
    public static final String ANSIBLE_COMMAND_LINE = "ansible_command_line_arg";
    // Remote ansible connection
    public static final String REMOTE_USER = "remote_node_user";
    public static final String REMOTE_NODE = "remote_node_ip";
    // Keys for Step.InputGroup
    public static final String INPUT_PARAMS = "input_params";
    public static final String CONNECTION_DETAILS = "connection_details";
    public static final String ANSIBLE_OPTIONS = "ansible_options";
    public static final String REST_OPTIONS = "rest_options";

    //REST options
    public static final String PROTOCOL = "protocol";
    public static final String AUTH_TYPE = "AuthType";
    public static final String BASIC_AUTH = "BasicAuth";
    public static final String USER = "user";
    public static final String PASSWORD = "password";
    public static final String ACCEPT_TYPE = "Accept-Type";
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String BODY = "body";
    public static final String METHOD = "method";
    public static final String TARGET = "target";
    public static final String PATH = "path";
    public static final String PORT = "port";
    public static final String HEADERS = "headers";

    public enum restMethods {
        GET, POST, PUT, DELETE;
    }

    public enum InputType {
        FROM_USER("InputFromUser"),
        // ASSET_OPTION_SINGLE_VALUE("AssetOptionSingleValue"),
        // ASSET_OPTION_MULTI_VALUE("AssetOptionMultiValue"),
        ASSET_OPTION("AssetOption"), // TODO: Change this to the above values.
        HARDCODEDVALUE("HardcodedValue"),
        FROM_STEP_INPUT("FromOtherStepInput"),
        FROM_STEP_OUTPUT("FromOtherStepOutput"),
        INVALID("Invalid");

        private final String inputType;

        private InputType(final String inputType) {
            this.inputType = inputType;
        }

        public static InputType fromString(String v) {
            for (InputType e : InputType.values()) {
                if (v.equals(e.inputType))
                    return e;
            }

            return INVALID;
        }

        @Override
        public String toString() {
            return inputType;
        }
    }

}
