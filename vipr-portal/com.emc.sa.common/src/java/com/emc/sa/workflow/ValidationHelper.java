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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
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
    final private Set<String> uniqueInputNames = new HashSet<>();
    final private ImmutableMap<String, Step> stepsHash;
    final private CustomServicesWorkflowDocument wfDocument;

    public ValidationHelper(CustomServicesWorkflowDocument wfDocument) {
        final List<Step> steps = wfDocument.getSteps();
        final ImmutableMap.Builder<String, Step> builder = ImmutableMap.builder();
        for (final Step step : steps) {
            builder.put(step.getId(), step);
        }

        this.stepsHash = builder.build();
        this.wfDocument = wfDocument;

    }

    private static CustomServicesValidationResponse.ErrorStep addErrorStep(final String stepId, final String errorString) {
        final CustomServicesValidationResponse.ErrorStep errorStep = new CustomServicesValidationResponse.ErrorStep();
        errorStep.setId(stepId);
        errorStep.setErrorDescription(errorString);
        return errorStep;
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

    public CustomServicesValidationResponse validate() {
        final CustomServicesValidationResponse validationResponse = new CustomServicesValidationResponse();
        validationResponse.setName(wfDocument.getName());
        final CustomServicesValidationResponse.Error error = validateSteps(stepsHash);
        if (StringUtils.isNotBlank(error.getErrorMessage()) || CollectionUtils.isNotEmpty(error.getErrorSteps())) {
            validationResponse.setError(error);
            validationResponse.setStatus(CustomServicesWorkflow.CustomServicesWorkflowStatus.INVALID.toString());
        } else {
            validationResponse.setStatus(CustomServicesWorkflow.CustomServicesWorkflowStatus.VALID.toString());
        }
        return validationResponse;
    }

    private CustomServicesValidationResponse.Error validateSteps(final ImmutableMap<String, Step> stepsHash) {
        final CustomServicesValidationResponse.Error workflowError = new CustomServicesValidationResponse.Error();
        final List<CustomServicesValidationResponse.ErrorStep> errorSteps = new ArrayList<>();
        if (stepsHash.get(StepType.START.toString()) == null || stepsHash.get(StepType.END.toString()) == null) {
            workflowError.setErrorDescription(CustomServicesConstants.ERROR_MSG_START_END_NOT_DEFINED);
        }

        // add Start as child by default
        childSteps.add(StepType.START.toString());

        for (final Step step : stepsHash.values()) {
            final String errorString = validateCurrentStep(step);

            CustomServicesValidationResponse.ErrorStep errorStep = new CustomServicesValidationResponse.ErrorStep();

            if (StringUtils.isNotBlank(errorString))
                errorStep = addErrorStep(step.getId(), errorString);

            logger.debug("Validate step input");

            final Map<String, CustomServicesValidationResponse.InputGroup> errorInputGroup = validateStepInput(step);
            if (!errorInputGroup.isEmpty()) {

                errorStep.setId(step.getId());
                errorStep.setInputGroups(errorInputGroup);
            }

            if (errorStep.getId() != null)
                errorSteps.add(errorStep);

        }

        final Set<String> stepWithoutParent = Sets.difference(stepsHash.keySet(), childSteps);
        // For the steps which does not have parent, add the error to response
        for (final String stepId : stepWithoutParent) {
            errorSteps.add(addErrorStep(stepId, CustomServicesConstants.ERROR_MSG_WORKFLOW_PREVIOUS_STEP_NOT_DEFINED));
        }

        if (CollectionUtils.isNotEmpty(errorSteps)) {
            workflowError.setErrorSteps(errorSteps);
        }
        return workflowError;
    }

    private String validateCurrentStep(final Step step) {
        String errorString = null;
        if (step == null || step.getId() == null) {
            return CustomServicesConstants.ERROR_MSG_WORKFLOW_STEP_NULL;
        }
        if (step.getId().equals(StepType.END.toString())) {
            return errorString;
        }
        if (step.getNext() == null) {
            return CustomServicesConstants.ERROR_MSG_WORKFLOW_NEXT_STEP_NOT_DEFINED;
        } else {
            if (step.getNext().getDefaultStep() == null && step.getNext().getFailedStep() == null) {
                errorString = CustomServicesConstants.ERROR_MSG_WORKFLOW_NEXT_STEP_NOT_DEFINED;
            }
            if (step.getNext().getDefaultStep() != null) { // The current step is a parent. Add the child / children of this parent so
                // that we can find any step that has missing parent
                childSteps.add(step.getNext().getDefaultStep());
            }
            if (step.getNext().getFailedStep() != null) {
                childSteps.add(step.getNext().getFailedStep());
            }

        }
        return errorString;
    }

    private Map<String, CustomServicesValidationResponse.InputGroup> validateStepInput(final Step step)
    {
        final Map<String, CustomServicesWorkflowDocument.InputGroup> input = step.getInputGroups();
        final Map<String, CustomServicesValidationResponse.InputGroup> errorInputGroup = new HashMap<>();

        if (!isInputEmpty(input, CustomServicesConstants.INPUT_PARAMS)) {
            final List<CustomServicesValidationResponse.Input> errorInputList = validateInput(
                    input.get(CustomServicesConstants.INPUT_PARAMS).getInputGroup());
            addErrorInputGroup(errorInputList, errorInputGroup);
        }
        if (!isInputEmpty(input, CustomServicesConstants.CONNECTION_DETAILS)) {
            final List<CustomServicesValidationResponse.Input> errorInputList = validateInput(
                    input.get(CustomServicesConstants.CONNECTION_DETAILS).getInputGroup());
            addErrorInputGroup(errorInputList, errorInputGroup);
        }
        if (!isInputEmpty(input, CustomServicesConstants.ANSIBLE_OPTIONS)) {
            final List<CustomServicesValidationResponse.Input> errorInputList = validateInput(
                    input.get(CustomServicesConstants.ANSIBLE_OPTIONS).getInputGroup());
            addErrorInputGroup(errorInputList, errorInputGroup);
        }

        return errorInputGroup;
    }

    private Map<String, CustomServicesValidationResponse.InputGroup> addErrorInputGroup(
            final List<CustomServicesValidationResponse.Input> errorInputList,
            final Map<String, CustomServicesValidationResponse.InputGroup> errorInputGroup) {
        if (!CollectionUtils.isEmpty(errorInputList)) {
            CustomServicesValidationResponse.InputGroup inputGroup = new CustomServicesValidationResponse.InputGroup() {
                {
                    setInputGroup(errorInputList);
                }
            };
            errorInputGroup.put(CustomServicesConstants.INPUT_PARAMS, inputGroup);
        }
        return errorInputGroup;
    }

    // Todo: Revisit this piece of code. Currently only the input field name being present and unique are enforced. this needs to be
    // revisited based on the amount of UI validation that can take place.
    private List<CustomServicesValidationResponse.Input> validateInput(final List<Input> stepInputList) {
        final List<CustomServicesValidationResponse.Input> errorInputList = new ArrayList<>();
        for (final Input input : stepInputList) {
            final CustomServicesValidationResponse.Input errorInput = new CustomServicesValidationResponse.Input();
            if (StringUtils.isBlank(input.getFriendlyName())) {
                errorInput.setName(input.getName());
                errorInput.setInputError(CustomServicesConstants.ERROR_MSG_DISPLAY_IS_EMPTY);
            } else if (!(input.getType().equals(CustomServicesConstants.InputType.FROM_STEP_INPUT.toString())
                    || input.getType().equals(CustomServicesConstants.InputType.FROM_STEP_OUTPUT.toString())
                    || input.getType().equals(CustomServicesConstants.InputType.HARDCODEDVALUE.toString()))) {
                // Enforce uniqueness only for those input that will be displayed in the order page and need user input/ selection.

                final String addtoSetStr = input.getFriendlyName().toLowerCase().replaceAll("\\s", "");
                if (uniqueInputNames.contains(addtoSetStr)) {
                    errorInput.setName(input.getName());
                    errorInput.setInputError(CustomServicesConstants.ERROR_MSG_DISPLAY_NAME_NOT_UNIQUE);
                } else {

                    uniqueInputNames.add(input.getFriendlyName().toLowerCase());
                }
            }
            if (errorInput.getName() != null)
                errorInputList.add(errorInput);
        }
        return errorInputList;
    }

}