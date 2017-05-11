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
package com.emc.sa.workflow;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.uimodels.CustomServicesWorkflow;
import com.emc.storageos.model.customservices.CustomServicesValidationResponse;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument.Input;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument.Step;
import com.emc.storageos.primitives.CustomServicesConstants;
import com.emc.storageos.primitives.CustomServicesConstants.InputFieldType;
import com.emc.storageos.primitives.CustomServicesPrimitive.StepType;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

public class ValidationHelper {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(ValidationHelper.class);
    final private Set<String> childSteps = new HashSet<>();
    final private Set<String> uniqueFriendlyInputNames = new HashSet<>();
    final private ImmutableMap<String, Step> stepsHash;
    final private String EMPTY_STRING = "";

    public ValidationHelper(final CustomServicesWorkflowDocument wfDocument) {
        final List<Step> steps = wfDocument.getSteps();
        final ImmutableMap.Builder<String, Step> builder = ImmutableMap.builder();
        for (final Step step : steps) {
            builder.put(step.getId(), step);
        }

        this.stepsHash = builder.build();
    }

    private static boolean isInputEmpty(final Map<String, CustomServicesWorkflowDocument.InputGroup> inputGroups,
            final String inputGroupName) {
        if (inputGroups == null) {
            // This is a valid case. Not error. The input can be empty for a step. eg., Show / get primitives
            logger.debug("No Input is defined");
            return true;
        }
        final CustomServicesWorkflowDocument.InputGroup inputGroup = inputGroups.get(inputGroupName);
        if (inputGroup == null) {
            logger.debug("No input params defined");
            return true;
        }
        final List<Input> listInput = inputGroup.getInputGroup();
        if (listInput == null) {
            logger.debug("No input param is defined");
            return true;
        }

        return false;
    }

    public CustomServicesValidationResponse validate(final URI id) {
        final CustomServicesValidationResponse validationResponse = new CustomServicesValidationResponse();
        validationResponse.setId(id);
        final CustomServicesValidationResponse.Error error = validateSteps();
        if (StringUtils.isNotBlank(error.getErrorMessage()) || MapUtils.isNotEmpty(error.getErrorSteps())) {
            validationResponse.setError(error);
            validationResponse.setStatus(CustomServicesWorkflow.CustomServicesWorkflowStatus.INVALID.toString());
        } else {
            validationResponse.setStatus(CustomServicesWorkflow.CustomServicesWorkflowStatus.VALID.toString());
        }

        logger.debug("CustomService workflow validation response is {}", validationResponse);
        return validationResponse;
    }

    private CustomServicesValidationResponse.Error validateSteps() {
        final CustomServicesValidationResponse.Error workflowError = new CustomServicesValidationResponse.Error();
        final Map<String, CustomServicesValidationResponse.ErrorStep> errorSteps = new HashMap<>();
        if (stepsHash.get(StepType.START.toString()) == null || stepsHash.get(StepType.END.toString()) == null) {
            workflowError.setErrorMessage(CustomServicesConstants.ERROR_MSG_START_END_NOT_DEFINED);
        }

        // add Start as child by default
        childSteps.add(StepType.START.toString());

        for (final Step step : stepsHash.values()) {
            final String errorString = validateCurrentStep(step);
            boolean addErrorStep = false;

            final CustomServicesValidationResponse.ErrorStep errorStep = new CustomServicesValidationResponse.ErrorStep();

            if (StringUtils.isNotBlank(errorString)) {
                if (errorSteps.containsKey(step.getId())) {
                    final List<String> errorMsgs = errorStep.getErrorMessages();
                    errorMsgs.add(errorString);
                } else {
                    errorStep.setErrorMessages(new ArrayList<>(Arrays.asList(errorString)));
                }
                addErrorStep = true;
            }

            logger.debug("Validate step input");

            final Map<String, CustomServicesValidationResponse.ErrorInputGroup> errorInputGroup = validateStepInput(step);
            if (!errorInputGroup.isEmpty()) {
                errorStep.setInputGroups(errorInputGroup);
                addErrorStep = true;
            }

            if (addErrorStep) {
                errorSteps.put(step.getId(), errorStep);
            }

        }
        if (MapUtils.isNotEmpty(addErrorStepsWithoutParent(errorSteps))) {
            workflowError.setErrorSteps(errorSteps);
        }
        return workflowError;
    }

    // For the steps which does not have parent, add the error to response
    private Map<String, CustomServicesValidationResponse.ErrorStep>
            addErrorStepsWithoutParent(final Map<String, CustomServicesValidationResponse.ErrorStep> errorSteps) {
        final Set<String> stepWithoutParent = Sets.difference(stepsHash.keySet(), childSteps);
        for (final String stepId : stepWithoutParent) {
            if (errorSteps.containsKey(stepId)) {
                final List<String> errorMsgs = errorSteps.get(stepId).getErrorMessages();
                if (CollectionUtils.isNotEmpty(errorMsgs)) {
                    errorMsgs.add(CustomServicesConstants.ERROR_MSG_WORKFLOW_PREVIOUS_STEP_NOT_DEFINED);
                } else {
                    errorSteps.get(stepId).setErrorMessages(
                            new ArrayList<>(Arrays.asList(CustomServicesConstants.ERROR_MSG_WORKFLOW_PREVIOUS_STEP_NOT_DEFINED)));
                }
            } else {
                final CustomServicesValidationResponse.ErrorStep errorStep = new CustomServicesValidationResponse.ErrorStep();
                errorStep.setErrorMessages(
                        new ArrayList<>(Arrays.asList(CustomServicesConstants.ERROR_MSG_WORKFLOW_PREVIOUS_STEP_NOT_DEFINED)));
                errorSteps.put(stepId, errorStep);
            }

        }
        return errorSteps;
    }

    private String validateCurrentStep(final Step step) {
        String errorString = null;
        if (step == null || StringUtils.isBlank(step.getId())) {
            return CustomServicesConstants.ERROR_MSG_WORKFLOW_STEP_NULL;
        }
        if (step.getId().equals(StepType.END.toString())) {
            return errorString;
        }
        if (step.getNext() == null) {
            return CustomServicesConstants.ERROR_MSG_WORKFLOW_NEXT_STEP_NOT_DEFINED;
        } else {
            if (StringUtils.isBlank(step.getNext().getDefaultStep()) && StringUtils.isBlank(step.getNext().getFailedStep())) {
                errorString = CustomServicesConstants.ERROR_MSG_WORKFLOW_NEXT_STEP_NOT_DEFINED;
            }
            if (StringUtils.isNotBlank(step.getNext().getDefaultStep())) { // The current step is a parent. Add the child / children of this
                                                                           // parent so
                // that we can find any step that has missing parent
                childSteps.add(step.getNext().getDefaultStep());
            }
            if (StringUtils.isNotBlank(step.getNext().getFailedStep())) {
                childSteps.add(step.getNext().getFailedStep());
            }

        }
        return errorString;
    }

    private Map<String, CustomServicesValidationResponse.ErrorInputGroup> validateStepInput(final Step step) {
        final Map<String, CustomServicesWorkflowDocument.InputGroup> inputGroups = step.getInputGroups();
        final Map<String, CustomServicesValidationResponse.ErrorInputGroup> errorInputGroup = new HashMap<>();
        if (inputGroups != null) {
            for (final String inputGroupKey : step.getInputGroups().keySet()) {
                if (!isInputEmpty(inputGroups, inputGroupKey)) {
                    final Map<String, CustomServicesValidationResponse.ErrorInput> errorInputMap = validateInput(
                            inputGroups.get(inputGroupKey).getInputGroup());
                    addErrorInputGroup(errorInputMap, errorInputGroup, inputGroupKey);
                }
            }
        }

        return errorInputGroup;
    }

    private Map<String, CustomServicesValidationResponse.ErrorInputGroup> addErrorInputGroup(
            final Map<String, CustomServicesValidationResponse.ErrorInput> errorInputMap,
            final Map<String, CustomServicesValidationResponse.ErrorInputGroup> errorInputGroup, final String inputGroupKey) {
        if (!errorInputMap.isEmpty()) {
            CustomServicesValidationResponse.ErrorInputGroup inputGroup = new CustomServicesValidationResponse.ErrorInputGroup();
            inputGroup.setErrorInputs(errorInputMap);
            errorInputGroup.put(inputGroupKey, inputGroup);
        }
        return errorInputGroup;
    }

    // Todo: Revisit this piece of code. Currently only the input field name being present and unique are enforced. This piece will be
    // revisited in COP-28892
    private Map<String, CustomServicesValidationResponse.ErrorInput> validateInput(final List<Input> stepInputList) {
        final Map<String, CustomServicesValidationResponse.ErrorInput> errorInputMap = new HashMap<>();
        final Set<String> uniqueInputNames = new HashSet<>();

        for (final Input input : stepInputList) {
            final CustomServicesValidationResponse.ErrorInput errorInput = new CustomServicesValidationResponse.ErrorInput();
            final List<String> errorMessages = new ArrayList<>();

            final String inputTypeErrorMessage = checkInputType(input);

            if (!inputTypeErrorMessage.isEmpty()) {
                errorMessages.add(inputTypeErrorMessage);
            }

            // Enforce friendly name uniqueness only for those input that will be displayed in the order page and need user input selection.
            if (StringUtils.isNotBlank(input.getType())
                    && !(input.getType().equals(CustomServicesConstants.InputType.FROM_STEP_INPUT.toString())
                            || input.getType().equals(CustomServicesConstants.InputType.FROM_STEP_OUTPUT.toString())
                            || input.getType().equals(CustomServicesConstants.InputType.DISABLED.toString()))) {
                final String uniqueFriendlyNameErrorMessage = checkUniqueNames(true, input.getFriendlyName(), uniqueFriendlyInputNames);

                if (!uniqueFriendlyNameErrorMessage.isEmpty()) {
                    errorMessages.add(uniqueFriendlyNameErrorMessage);
                }
            }

            // Enforce uniqueness for all input names in the step
            final String uniqueInputNameErrorMessage = checkUniqueNames(false, input.getName(), uniqueInputNames);

            if (!uniqueInputNameErrorMessage.isEmpty()) {
                errorMessages.add(uniqueInputNameErrorMessage);
            }

            if (!errorMessages.isEmpty()) {
                errorInput.setErrorMessages(errorMessages);
                errorInputMap.put(input.getName(), errorInput);
            }
        }
        return errorInputMap;
    }

    private String checkInputType(final Input input) {
        String errorMessage = EMPTY_STRING;
        if (StringUtils.isBlank(input.getType())
                || CustomServicesConstants.InputType.fromString(input.getType()).equals(CustomServicesConstants.InputType.INVALID)) {
            // Input type is required and should be one of valid values. if user does not want to set type, set it to "Disabled"
            errorMessage = String.format("%s - Valid Input Types %s", CustomServicesConstants.ERROR_MSG_INPUT_TYPE_IS_NOT_DEFINED,
                    EnumSet.allOf(CustomServicesConstants.InputType.class));
        } else if (input.getRequired() && input.getType().equals(CustomServicesConstants.InputType.DISABLED.toString())) {
            // Input type is required if the input is marked required
            errorMessage = CustomServicesConstants.ERROR_MSG_INPUT_TYPE_IS_REQUIRED;
        } else if (input.getType().equals(CustomServicesConstants.InputType.FROM_USER.toString())) {
            final EnumSet<InputFieldType> inputFieldTypes = EnumSet.allOf(InputFieldType.class);
            if (StringUtils.isBlank(input.getInputFieldType())
                    || !inputFieldTypes.contains(InputFieldType.valueOf(input.getInputFieldType().toUpperCase()))) {
                errorMessage = String.format("%s - Valid Input Field Types %s",
                        CustomServicesConstants.ERROR_MSG_INPUT_FIELD_TYPE_IS_REQUIRED,
                        inputFieldTypes);
            } else if (StringUtils.isNotBlank(input.getDefaultValue())) {
                final String defaultValueErrorMessage = checkDefaultvalues(input.getDefaultValue(), input.getInputFieldType());
                if (!defaultValueErrorMessage.isEmpty()) {
                    errorMessage = defaultValueErrorMessage;
                }
            }
        } else if ((input.getType().equals(CustomServicesConstants.InputType.ASSET_OPTION_MULTI.toString()) ||
                input.getType().equals(CustomServicesConstants.InputType.ASSET_OPTION_SINGLE.toString()))
                && StringUtils.isNotBlank(input.getDefaultValue())) {
            errorMessage = CustomServicesConstants.ERROR_MSG_NO_DEFAULTVALUE_FOR_ASSET_INPUT_TYPE;
        }

        return errorMessage;
    }

    private String checkUniqueNames(final boolean checkFriendlyName, final String name, final Set<String> uniqueNames) {
        if (StringUtils.isBlank(name)) {
            if (checkFriendlyName) {
                return CustomServicesConstants.ERROR_MSG_DISPLAY_IS_EMPTY;
            } else {
                return CustomServicesConstants.ERROR_MSG_INPUT_NAME_IS_EMPTY;
            }
        } else {
            final String addtoSetStr = name.toLowerCase().replaceAll("\\s", "");
            if (uniqueNames.contains(addtoSetStr)) {
                if (checkFriendlyName) {
                    return CustomServicesConstants.ERROR_MSG_DISPLAY_NAME_NOT_UNIQUE;
                } else {
                    return CustomServicesConstants.ERROR_MSG_INPUT_NAME_NOT_UNIQUE_IN_STEP;
                }
            } else {
                uniqueNames.add(name.toLowerCase());
                return EMPTY_STRING;
            }
        }
    }

    private String checkDefaultvalues(final String defaultValue, final String inputFieldType) {
        if (inputFieldType.toUpperCase().equals(CustomServicesConstants.InputFieldType.BOOLEAN.toString())) {
            boolean error = defaultValue.toLowerCase().equals("true") || defaultValue.toLowerCase().equals("false") ? true : false;
            if (!error) {
                return CustomServicesConstants.ERROR_MSG_INVALID_BOOLEAN_INPUT_FIELD_TYPE;
            }
        }

        if (inputFieldType.toUpperCase().equals(InputFieldType.NUMBER.toString())) {
            boolean error = StringUtils.isNumeric(defaultValue) ? true : false;
            if (!error) {
                return CustomServicesConstants.ERROR_MSG_INVALID_NUMBER_INPUT_FIELD_TYPE;
            }
        }

        return EMPTY_STRING;
    }
}