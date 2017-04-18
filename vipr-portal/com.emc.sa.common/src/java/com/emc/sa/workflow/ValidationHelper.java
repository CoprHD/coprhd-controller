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
import com.emc.storageos.primitives.CustomServicesPrimitive.StepType;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

public class ValidationHelper {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(ValidationHelper.class);
    final private Set<String> childSteps = new HashSet<>();
    final private Set<String> uniqueFriendlyInputNames = new HashSet<>();
    final private ImmutableMap<String, Step> stepsHash;

    public ValidationHelper(final CustomServicesWorkflowDocument wfDocument) {
        final List<Step> steps = wfDocument.getSteps();
        final ImmutableMap.Builder<String, Step> builder = ImmutableMap.builder();
        for (final Step step : steps) {
            builder.put(step.getId(), step);
        }

        this.stepsHash = builder.build();
    }

    private static boolean isInputEmpty(final Map<String, CustomServicesWorkflowDocument.InputGroup> input, final String type) {
        if (input == null) {
            // This is a valid case. Not error. The input can be empty for a step. eg., Show / get primitives
            logger.debug("No Input is defined");
            return true;
        }
        final CustomServicesWorkflowDocument.InputGroup inputGroup = input.get(type);
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
    private Map<String, CustomServicesValidationResponse.ErrorStep> addErrorStepsWithoutParent(final Map<String, CustomServicesValidationResponse.ErrorStep> errorSteps){
        final Set<String> stepWithoutParent = Sets.difference(stepsHash.keySet(), childSteps);
        for (final String stepId : stepWithoutParent) {
            if (errorSteps.containsKey(stepId)) {
                final List<String> errorMsgs = errorSteps.get(stepId).getErrorMessages();
                errorMsgs.add(CustomServicesConstants.ERROR_MSG_WORKFLOW_PREVIOUS_STEP_NOT_DEFINED);
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
        final Map<String, CustomServicesWorkflowDocument.InputGroup> input = step.getInputGroups();
        final Map<String, CustomServicesValidationResponse.ErrorInputGroup> errorInputGroup = new HashMap<>();
        if (step.getInputGroups() != null) {
            for (final String inputGroupKey : step.getInputGroups().keySet()) {
                if (!isInputEmpty(input, inputGroupKey)) {
                    final List<CustomServicesValidationResponse.ErrorInput> errorInputList = validateInput(
                            input.get(inputGroupKey).getInputGroup());
                    addErrorInputGroup(errorInputList, errorInputGroup, inputGroupKey);
                }
            }
        }

        return errorInputGroup;
    }

    private Map<String, CustomServicesValidationResponse.ErrorInputGroup> addErrorInputGroup(
            final List<CustomServicesValidationResponse.ErrorInput> errorInputList,
            final Map<String, CustomServicesValidationResponse.ErrorInputGroup> errorInputGroup, final String inputGroupKey) {
        if (!CollectionUtils.isEmpty(errorInputList)) {
            CustomServicesValidationResponse.ErrorInputGroup inputGroup = new CustomServicesValidationResponse.ErrorInputGroup() {
                {
                    setErrorInputGroup(errorInputList);
                }
            };
            errorInputGroup.put(inputGroupKey, inputGroup);
        }
        return errorInputGroup;
    }

    // Todo: Revisit this piece of code. Currently only the input field name being present and unique are enforced. This piece will be
    // revisited in COP-28892
    private List<CustomServicesValidationResponse.ErrorInput> validateInput(final List<Input> stepInputList) {
        final List<CustomServicesValidationResponse.ErrorInput> errorInputList = new ArrayList<>();
        final Set<String> uniqueInputNames = new HashSet<>();
        for (final Input input : stepInputList) {
            final CustomServicesValidationResponse.ErrorInput errorInput = new CustomServicesValidationResponse.ErrorInput();
            if (!(input.getType().equals(CustomServicesConstants.InputType.FROM_STEP_INPUT.toString())
                    || input.getType().equals(CustomServicesConstants.InputType.FROM_STEP_OUTPUT.toString()))) {
                // Enforce uniqueness only for those input that will be displayed in the order page and need user input/ selection.
                if (StringUtils.isBlank(input.getFriendlyName())) {
                    errorInput.setName(input.getName());
                    errorInput.setErrorMessage(CustomServicesConstants.ERROR_MSG_DISPLAY_IS_EMPTY);
                } else {
                    final String addtoSetStr = input.getFriendlyName().toLowerCase().replaceAll("\\s", "");
                    if (uniqueFriendlyInputNames.contains(addtoSetStr)) {
                        errorInput.setName(input.getName());
                        errorInput.setErrorMessage(CustomServicesConstants.ERROR_MSG_DISPLAY_NAME_NOT_UNIQUE);
                    } else {
                        uniqueFriendlyInputNames.add(input.getFriendlyName().toLowerCase());
                    }
                }
            }
            // Enforce uniqueness for all input names in the step to be present and unique
            if (StringUtils.isBlank(input.getName())) {
                errorInput.setName(input.getName());
                errorInput.setErrorMessage(CustomServicesConstants.ERROR_MSG_INPUT_NAME_IS_EMPTY);
            } else {
                final String addtoSetStr = input.getName().toLowerCase().replaceAll("\\s", "");
                if (uniqueInputNames.contains(addtoSetStr)) {
                    errorInput.setName(input.getName());
                    errorInput.setErrorMessage(CustomServicesConstants.ERROR_MSG_INPUT_NAME_NOT_UNIQUE_IN_STEP);
                } else {
                    uniqueInputNames.add(input.getName().toLowerCase());
                }
            }

            if (errorInput.getName() != null){
                errorInputList.add(errorInput);
            }
        }
        return errorInputList;
    }

}