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

import com.emc.storageos.primitives.input.BasicInputParameter;
import com.emc.storageos.primitives.input.InputParameter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Custom Services Constants
 */
public final class CustomServicesConstants {
    // Validation Error Messages
    public static final String ERROR_MSG_START_END_NOT_DEFINED = "Start or End Step not defined";
    public static final String ERROR_MSG_WORKFLOW_STEP_NULL = "Workflow Step is null";
    public static final String ERROR_MSG_WORKFLOW_NEXT_STEP_NOT_DEFINED = "Next step not defined for the step";
    public static final String ERROR_MSG_WORKFLOW_NEXT_STEP_NOT_ALLOWED_FOR_END = "Next step not allowed for the End step";
    public static final String ERROR_MSG_WORKFLOW_STEP_NOT_FOUND = "Next step not found for the step";
    public static final String ERROR_MSG_WORKFLOW_CYCLE_EXISTS = "Cycles a.k.a loops are not allowed in workflow";
    public static final String ERROR_MSG_WORKFLOW_FAILURE_PATH_NOT_ALLOWED_FOR_START = "Failure path not allowed for the Start step";
    public static final String ERROR_MSG_WORKFLOW_START_END_CONNECTED = "Start is connected to End.";
    public static final String ERROR_MSG_WORKFLOW_PREVIOUS_STEP_NOT_DEFINED = "Previous step not defined for the step";
    public static final String ERROR_MSG_INPUT_TYPE_IS_NOT_DEFINED = "InputType is not defined";
    public static final String ERROR_MSG_INPUT_TYPE_IS_REQUIRED = "InputType is mandated for required fields";
    public static final String ERROR_MSG_INPUT_FIELD_TYPE_IS_REQUIRED = "InputFieldType is mandated for InputFromUser type";
    public static final String ERROR_MSG_INVALID_DEFAULT_BOOLEAN_INPUT_FIELD_TYPE = "Invalid default value for boolean InputFieldType - Valid boolean values are 'true' / 'false'";
    public static final String ERROR_MSG_INVALID_DEFAULT_NUMBER_INPUT_FIELD_TYPE = "Invalid default value for integer InputFieldType";
    public static final String ERROR_MSG_DISPLAY_IS_EMPTY = "Display Name is empty";
    public static final String ERROR_MSG_DISPLAY_NAME_NOT_UNIQUE = "Display Name is not unique";
    public static final String ERROR_MSG_INPUT_NAME_IS_EMPTY = "Input Name is empty";
    public static final String ERROR_MSG_INPUT_NAME_NOT_UNIQUE_IN_STEP = "Input Name is not unique in the step";
    public static final String ERROR_MSG_DEFAULT_VALUE_REQUIRED_FOR_INPUT_TYPE = "No default value for Input Type";
    public static final String ERROR_MSG_INVENTORY_FILE_NOT_MAPPED = "No inventory files exist for Ansible operation. Please edit the operation and upload at least one inventory file.";
    public static final String ERROR_MSG_NO_INPUTVALUE_FOR_INPUT_TYPE = "No input value for Input Type";
    public static final String ERROR_MSG_DEFAULTVALUE_PASSED_FOR_INPUT_TYPE = "Default value passed for Input Type";
    public static final String ERROR_MSG_INPUT_FROM_OTHER_STEP_NOT_DEFINED = "Input from other step value is not defined";
    public static final String ERROR_MSG_STEP_NOT_DEFINED = "Step not defined. Cannot find the step";
    public static final String ERROR_MSG_STEP_IS_NOT_ANCESTER = "Step is not an ancestor for the current step";
    public static final String ERROR_MSG_OTHER_STEP_INPUT_GROUP_OR_PARAM_NOT_DEFINED = "Other Step Input group/ param not defined";
    public static final String ERROR_MSG_INPUT_NOT_DEFINED_IN_OTHER_STEP = "Input not defined in other Step";
    public static final String ERROR_MSG_OTHER_STEP_OUTPUT_NOT_DEFINED = "Other Step Output not defined";
    public static final String ERROR_MSG_OUTPUT_NOT_DEFINED_IN_OTHER_STEP = "Output not defined in other Step";
    public static final String ERROR_MSG_STEP_OPERATION_REQUIRED = "Operation should be defined for step";
    public static final String ERROR_MSG_STEP_OPERATION_DOES_NOT_EXISTS = "Operation does not exist in database or is recently deleted";
    public static final String ERROR_MSG_STEP_TYPE_REQUIRED = "Type should be defined for step";
    public static final String ERROR_MSG_STEP_TYPE_INVALID = "Invalid type specified for step";

    public static final int STEP_ID = 0;
    public static final int INPUT_FIELD = 1;
    public static final String WF_ID = "WorkflowId";

    // Primitive/resource types
    public static final String VIPR_PRIMITIVE_TYPE = "vipr";
    public static final String SCRIPT_PRIMITIVE_TYPE = "script";
    public static final String ANSIBLE_PRIMITIVE_TYPE = "ansible";
    public static final String REST_API_PRIMITIVE_TYPE = "rest";
    public static final String REMOTE_ANSIBLE_PRIMTIVE_TYPE = "remote_ansible";
    public static final String ANSIBLE_INVENTORY_TYPE = "ansible_inventory";

    // SuccessCriteria Constants
    public static final String RETURN_CODE = "code";
    public static final String TASK = "task";
    public static final long WORKFLOW_TIMEOUT = 8 * 3600 * 1000;
    public static final long OPERATION_TIMEOUT = 4 * 3600 * 1000;

    public static final List<String> BODY_REST_METHOD = Arrays.asList("POST", "PUT", "DELETE");
    // Script Execution Constants
    public static final String ANSIBLE_LOCAL_BIN = "/usr/bin/ansible-playbook";
    public static final String SHELL_BIN = "/usr/bin/sh";
    public static final String SHELL_LOCAL_BIN = "/usr/bin/ssh";
    public static final String ORDER_DIR_PATH = "/opt/storageos/customroot/CS";
    public static final String CHROOT_ORDER_DIR_PATH = "/CS";
    public static final String CHROOT_CMD = "/usr/bin/chroot";
    public static final String CHROOT_DIR = "/opt/storageos/customroot";
    public static final String CHMOD_CMD = "/usr/bin/chmod";
    public static final String SOFTLINK_CMD = "/usr/bin/ln";
    public static final String UNLINK_CMD = "/usr/bin/unlink";
    public static final String SUDO_CMD = "sudo";
    public static final String CHMOD_OPTION = "777";
    public static final String SOFTLINK_OPTION = "-s";
    public static final String EXTRA_VARS = "--extra-vars";
    public static final String UNTAR = "tar";
    public static final String UNTAR_OPTION = "-zxvf";
    public static final String REMOVE = "/bin/rm";
    public static final String REMOVE_OPTION = "-rf";
    public static final String BIN_BASH = "/bin/bash";
    public static final String BIN_BASH_OPTION = "-c";

    // Ansible Options
    public static final String ANSIBLE_BIN = "remote_ansible_bin";
    public static final String ANSIBLE_PLAYBOOK = "playbook";
    public static final String ANSIBLE_HOST_FILE = "inventory_file";
    public static final String ANSIBLE_USER = "remote_ansible_user";
    public static final String ANSIBLE_COMMAND_LINE = "ansible_command_line_arg";
    // Remote ansible connection
    public static final String REMOTE_USER = "remote_node_user";
    public static final String REMOTE_PASSWORD = "remote_node_password";
    public static final String REMOTE_NODE = "remote_node_ip";
    // Keys for Step.InputGroup
    public static final String PATH_PARAMS = "path_params";
    public static final String INPUT_PARAMS = "input_params";
    public static final String CONNECTION_DETAILS = "connection_details";
    public static final String ANSIBLE_OPTIONS = "ansible_options";
    public static final String REST_OPTIONS = "rest_options";
    public static final String QUERY_PARAMS = "query_params";
    public static final String CREDENTIALS = "credentials";
    public static final String HEADERS = "headers";
    // Common ansible_options group
    public static final ImmutableMap<String, ImmutableList<InputParameter>> ANSIBLE_OPTIONS_INPUT_GROUP = 
            ImmutableMap.<String, ImmutableList<InputParameter>>of(
                    CustomServicesConstants.ANSIBLE_OPTIONS, ImmutableList.<InputParameter>builder()
                    .add(new BasicInputParameter.StringParameter(CustomServicesConstants.ANSIBLE_HOST_FILE, true, null))
                    .add(new BasicInputParameter.StringParameter(CustomServicesConstants.ANSIBLE_COMMAND_LINE, false, null))
                    .build());

    // REST options
    public static final String PROTOCOL = "protocol";
    public static final String AUTH_TYPE = "auth";
    public static final String USER = "user";
    public static final String PASSWORD = "password";
    public static final String ACCEPT_TYPE = "Accept-Type";
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String BODY = "body";
    public static final String METHOD = "method";
    public static final String TARGET = "target";
    public static final String PATH = "path";
    public static final String PORT = "port";
    public static final String DEFAULT_HTTPS_PORT = "443";

    // Execution Result
    public static final String OPERATION_OUTPUT = "operation_output";
    public static final String OPERATION_ERROR = "operation_error";
    public static final String OPERATION_RETURNCODE = "operation_returncode";

    public static final String SPACES_REGEX = "\\s";

    // Supported REST methods for Custom Service
    public enum RestMethods {
        GET, POST, PUT, DELETE;
    }

    public enum AuthType {
        NONE, BASIC, TOKEN;
    }

    public enum InputFieldType {
        NUMBER, BOOLEAN, TEXT, PASSWORD;
    }

    public enum InputType {
        FROM_USER("InputFromUser"),
        FROM_USER_MULTI("InputFromUserMulti"),
        ASSET_OPTION_SINGLE("AssetOptionSingle"),
        ASSET_OPTION_MULTI("AssetOptionMulti"),
        FROM_STEP_INPUT("FromOtherStepInput"),
        FROM_STEP_OUTPUT("FromOtherStepOutput"),
        INVALID("Invalid"),
        DISABLED("Disabled");

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
