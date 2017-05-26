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
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.uimodels.CustomServicesWorkflow;
import com.emc.storageos.model.customservices.CustomServicesValidationResponse;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument.Input;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument.Output;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument.Step;
import com.emc.storageos.primitives.CustomServicesConstants;
import com.emc.storageos.primitives.CustomServicesConstants.InputFieldType;
import com.emc.storageos.primitives.CustomServicesPrimitive.StepType;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

public class ValidationHelper {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(ValidationHelper.class);
    final Map<String, List<String>> wfAdjList = new HashMap<String, List<String>>();
    final private Set<String> uniqueFriendlyInputNames = new HashSet<>();
    final private ImmutableMap<String, Step> stepsHash;
    final private String EMPTY_STRING = "";
    final private Map<String, Set<String>> descendant = new HashMap();
    final private Map<String, String> nodeTraverseMap = new HashMap();
    final private String NODE_NOT_VISITED = "not visited";
    final private String NODE_VISITED = "visited";
    final private String NODE_IN_PATH = "to be visited";
    private boolean foundCycle = false;

    public ValidationHelper(final CustomServicesWorkflowDocument wfDocument) {

        final List<Step> steps = wfDocument.getSteps();
        final ImmutableMap.Builder<String, Step> builder = ImmutableMap.builder();
        try {
            for (final Step step : steps) {
                if (step != null && StringUtils.isNotBlank(step.getId())) {
                    builder.put(step.getId(), step);
                } else {
                    throw APIException.badRequests.requiredParameterMissingOrEmpty("step.id in workflow document");
                }
            }
            this.stepsHash = builder.build();

        } catch (final APIException ae) {
            throw ae;
        } catch (final Exception e) {
            throw new RuntimeException("Failed to build the steps from CustomServicesWorkflow: " + e);
        }
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

        for (final Step step : stepsHash.values()) {
            final String errorString = validateCurrentStep(step);

            final CustomServicesValidationResponse.ErrorStep errorStep = new CustomServicesValidationResponse.ErrorStep();

            if (StringUtils.isNotBlank(errorString)) {
                errorStep.setErrorMessages(new ArrayList<>(Arrays.asList(errorString)));
                errorSteps.put(step.getId(), errorStep);
            }
        }
        addErrorStepsWithoutParent(errorSteps);

        validateStepInputs(errorSteps);

        if (MapUtils.isNotEmpty(errorSteps)) {
            workflowError.setErrorSteps(errorSteps);
        }
        return workflowError;
    }

    // For the steps which does not have parent, add the error to response
    private void addErrorStepsWithoutParent(final Map<String, CustomServicesValidationResponse.ErrorStep> errorSteps) {

        final Set<String> childSteps = new HashSet<>();
        // add Start as child by default
        childSteps.add(StepType.START.toString());
        for (final List<String> adjchildStep : wfAdjList.values()) {
            childSteps.addAll(adjchildStep);
        }

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

        if (CollectionUtils.isEmpty(stepWithoutParent)) {
            validateCycle(errorSteps);
        }
    }

    private void validateCycle(final Map<String, CustomServicesValidationResponse.ErrorStep> errorSteps) {
        String backEdgeNode = EMPTY_STRING;
        // initialize all nodes to be not visited before traversing the nodes in the workflow
        for (final String step : wfAdjList.keySet()) {
            nodeTraverseMap.put(step, NODE_NOT_VISITED);
        }

        // Traverse all nodes to identify any dis-joint forest that might exist
        for (final String step : wfAdjList.keySet()) {
            if (nodeTraverseMap.get(step).equals(NODE_NOT_VISITED)) {
                backEdgeNode = graphTraverse(step);
            }
            if (foundCycle) {
                break;
            }
        }

        if (foundCycle) {
            final String error = StringUtils.isNotBlank(backEdgeNode) ? String.format("%s - directed edge from/to %s results in cycle",
                    CustomServicesConstants.ERROR_MSG_WORKFLOW_CYCLE_EXISTS,
                    stepsHash.get(backEdgeNode).getFriendlyName()) : CustomServicesConstants.ERROR_MSG_WORKFLOW_CYCLE_EXISTS;

            if (errorSteps.containsKey(backEdgeNode)) {
                final List<String> errorMsgs = errorSteps.get(backEdgeNode).getErrorMessages();
                if (CollectionUtils.isNotEmpty(errorMsgs)) {
                    errorMsgs.add(error);
                } else {
                    errorSteps.get(backEdgeNode).setErrorMessages(
                            new ArrayList<>(Arrays.asList(error)));
                }
            } else {
                final CustomServicesValidationResponse.ErrorStep errorStep = new CustomServicesValidationResponse.ErrorStep();
                errorStep.setErrorMessages(
                        new ArrayList<>(Arrays.asList(error)));
                errorSteps.put(backEdgeNode, errorStep);
            }
        }

    }

    private String graphTraverse(final String node) {

        nodeTraverseMap.put(node, NODE_IN_PATH);
        if (wfAdjList.get(node) == null) {
            return EMPTY_STRING;
        }

        for (final String child : wfAdjList.get(node)) {

            if (node.equals(StepType.END)) {
                continue;
            }

            if (wfAdjList.get(child) == null) {
                continue;
            }

            if (nodeTraverseMap.get(child).equals(NODE_IN_PATH)) {
                // back edge
                foundCycle = true;
                return node;
            }
            if (nodeTraverseMap.get(child).equals(NODE_NOT_VISITED)) {
                String errorNode = graphTraverse(child);
                if (foundCycle) {
                    return errorNode;
                }
            }
            if (!foundCycle) {
                List<String> cChildren = wfAdjList.get(child);
                if (cChildren != null) {
                    Set<String> cSet = new HashSet<>(cChildren);
                    cSet.addAll(wfAdjList.get(node));
                    if (descendant.get(child) != null) {
                        cSet.addAll(descendant.get(child));
                    }

                    if (descendant.get(node) != null) {
                        cSet.addAll(descendant.get(node));
                    }
                    cSet.remove("");
                    descendant.put(node, cSet);
                }
            }
        }
        nodeTraverseMap.put(node, NODE_VISITED);
        return EMPTY_STRING;
    }

    private String validateCurrentStep(final Step step) {
        String errorString;

        final List<String> adjacentNodes = new ArrayList<>();
        wfAdjList.put(step.getId(), adjacentNodes);

        if (step == null || StringUtils.isBlank(step.getId())) {
            return CustomServicesConstants.ERROR_MSG_WORKFLOW_STEP_NULL;
        }
        errorString = validateStartEndStep(step, adjacentNodes);
        if (StringUtils.isNotBlank(errorString)) {
            return errorString;
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

            // The current step is a parent. Add the child / children of this
            // parent so that we can find any step that has missing parent
            if (StringUtils.isNotBlank(step.getNext().getDefaultStep())) {
                if (step.getId().equals(StepType.START.toString()) && step.getNext().getDefaultStep().equals(StepType.END.toString())) {
                    errorString = CustomServicesConstants.ERROR_MSG_WORKFLOW_START_END_CONNECTED;
                }
                if (stepsHash.keySet().contains(step.getNext().getDefaultStep())) {
                    adjacentNodes.add(step.getNext().getDefaultStep());
                } else {
                    errorString = CustomServicesConstants.ERROR_MSG_WORKFLOW_STEP_NOT_FOUND;
                }
            }
            if (StringUtils.isNotBlank(step.getNext().getFailedStep())) {
                if (stepsHash.keySet().contains(step.getNext().getFailedStep())) {
                    adjacentNodes.add(step.getNext().getFailedStep());
                } else {
                    errorString = CustomServicesConstants.ERROR_MSG_WORKFLOW_STEP_NOT_FOUND;
                }

            }
            return errorString;
        }
    }

    private String validateStartEndStep(final Step step, final List<String> adjacentNodes) {

        if (StringUtils.isNotBlank(step.getId()) && step.getNext() != null) {
            if (step.getId().equals(StepType.END.toString()) && (StringUtils.isNotBlank(step.getNext().getDefaultStep())
                    || StringUtils.isNotBlank(step.getNext().getFailedStep()))) {
                return CustomServicesConstants.ERROR_MSG_WORKFLOW_NEXT_STEP_NOT_ALLOWED_FOR_END;
            }

            if (step.getId().equals(StepType.START.toString())
                    && (StringUtils.isBlank(step.getNext().getDefaultStep()) || StringUtils.isNotBlank(step.getNext().getFailedStep()))) {
                adjacentNodes.add(step.getNext().getFailedStep());
                return CustomServicesConstants.ERROR_MSG_WORKFLOW_FAILURE_PATH_NOT_ALLOWED_FOR_START;
            }
        }

        return EMPTY_STRING;
    }

    private void validateStepInputs(Map<String, CustomServicesValidationResponse.ErrorStep> errorSteps) {
        for (final Step step : stepsHash.values()) {
            final CustomServicesValidationResponse.ErrorStep errorStep = new CustomServicesValidationResponse.ErrorStep();
            final Map<String, CustomServicesValidationResponse.ErrorInputGroup> errorInputGroup = validateStepInput(step);
            if (!errorInputGroup.isEmpty()) {
                if (errorSteps.containsKey(step.getId())) {
                    errorSteps.get(step.getId()).setInputGroups(errorInputGroup);
                } else {
                    errorStep.setInputGroups(errorInputGroup);
                    errorSteps.put(step.getId(), errorStep);
                }
            }
        }
    }

    private Map<String, CustomServicesValidationResponse.ErrorInputGroup> validateStepInput(final Step step) {
        final Map<String, CustomServicesWorkflowDocument.InputGroup> inputGroups = step.getInputGroups();
        final Map<String, CustomServicesValidationResponse.ErrorInputGroup> errorInputGroup = new HashMap<>();
        if (inputGroups != null) {
            for (final String inputGroupKey : step.getInputGroups().keySet()) {
                if (!isInputEmpty(inputGroups, inputGroupKey)) {
                    final Map<String, CustomServicesValidationResponse.ErrorInput> errorInputMap = validateInput(step.getId(),
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

    private Map<String, CustomServicesValidationResponse.ErrorInput> validateInput(final String stepId, final List<Input> stepInputList) {
        final Map<String, CustomServicesValidationResponse.ErrorInput> errorInputMap = new HashMap<>();
        final Set<String> uniqueInputNames = new HashSet<>();

        for (final Input input : stepInputList) {
            final CustomServicesValidationResponse.ErrorInput errorInput = new CustomServicesValidationResponse.ErrorInput();
            final List<String> errorMessages = new ArrayList<>();

            final String inputTypeErrorMessage = checkInputType(stepId, input);

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

            // Enforce uniqueness for all input names in the input group
            // TODO: This might be revisited based on the discussion of unique names in step vs step input group
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

    private String checkInputType(final String stepId, final Input input) {
        String errorMessage;
        if (StringUtils.isBlank(input.getType())
                || CustomServicesConstants.InputType.fromString(input.getType()).equals(CustomServicesConstants.InputType.INVALID)) {
            // Input type is required and should be one of valid values. if user does not want to set type, set it to "Disabled"
            final EnumSet<CustomServicesConstants.InputType> validInputTypes = EnumSet.allOf(CustomServicesConstants.InputType.class);
            validInputTypes.remove(CustomServicesConstants.InputType.INVALID);
            errorMessage = String.format("%s - Valid Input Types %s", CustomServicesConstants.ERROR_MSG_INPUT_TYPE_IS_NOT_DEFINED,
                    validInputTypes);
        } else if (input.getRequired() && input.getType().equals(CustomServicesConstants.InputType.DISABLED.toString())) {
            // Input type is required if the input is marked required
            errorMessage = CustomServicesConstants.ERROR_MSG_INPUT_TYPE_IS_REQUIRED;
        } else if (input.getType().equals(CustomServicesConstants.InputType.FROM_USER.toString())) {
            errorMessage = checkUserInputType(input);
        } else if (input.getType().equals(CustomServicesConstants.InputType.FROM_USER_MULTI.toString())
                && StringUtils.isBlank(input.getDefaultValue()) && MapUtils.isEmpty(input.getOptions())) {
            errorMessage = String.format("%s - %s", CustomServicesConstants.ERROR_MSG_DEFAULT_VALUE_REQUIRED_FOR_INPUT_TYPE,
                    input.getType());
        } else {
            errorMessage = checkOtherInputType(stepId, input);
        }
        return errorMessage;
    }

    private String validateOtherStepInput(final Step step, final String attribute) {
        if (step.getInputGroups() == null
                || step.getInputGroups().get(CustomServicesConstants.INPUT_PARAMS) == null
                || step.getInputGroups().get(CustomServicesConstants.INPUT_PARAMS).getInputGroup() == null) {
            // TODO: currently the input params is only mapped to other steps. this might be changed
            return CustomServicesConstants.ERROR_MSG_OTHER_STEP_INPUT_GROUP_OR_PARAM_NOT_DEFINED;

        }

        final List<Input> inputs = step.getInputGroups().get(CustomServicesConstants.INPUT_PARAMS).getInputGroup();

        for (final Input input : inputs) {
            if (StringUtils.isNotBlank(input.getName()) && input.getName().equals(attribute)) {
                return EMPTY_STRING;
            }
        }

        return String.format("%s %s(%s) - %s", CustomServicesConstants.ERROR_MSG_INPUT_NOT_DEFINED_IN_OTHER_STEP, step.getDescription(),
                step.getId(), attribute);
    }

    private String checkOtherInputType(final String stepId, final Input input) {

        switch (CustomServicesConstants.InputType.fromString(input.getType())) {
            case ASSET_OPTION_SINGLE:
            case ASSET_OPTION_MULTI:
            case FROM_STEP_INPUT:
            case FROM_STEP_OUTPUT:
                if (!StringUtils.isNotBlank(input.getValue())) {
                    return String.format("%s - %s", CustomServicesConstants.ERROR_MSG_NO_INPUTVALUE_FOR_INPUT_TYPE, input.getType());
                } else if (StringUtils.isNotBlank(input.getDefaultValue())) {
                    // no default value for asset options and step input/ output
                    return String.format("%s %s", CustomServicesConstants.ERROR_MSG_DEFAULTVALUE_PASSED_FOR_INPUT_TYPE, input.getType());
                }

                if (input.getType().equals(CustomServicesConstants.InputType.FROM_STEP_INPUT.toString()) ||
                        input.getType().equals(CustomServicesConstants.InputType.FROM_STEP_OUTPUT.toString())) {
                    // check valid step input/ output
                    return checkOtherStepType(stepId, input);

                }

        }

        return EMPTY_STRING;
    }

    private String checkOtherStepType(final String inputStepId, final Input input) {
        String errorMessage = EMPTY_STRING;
        if (StringUtils.isEmpty(input.getValue())) {
            return CustomServicesConstants.ERROR_MSG_INPUT_FROM_OTHER_STEP_NOT_DEFINED;
        }

        final String[] paramVal = input.getValue().split("\\.", 2);
        final String stepId = paramVal[CustomServicesConstants.STEP_ID];
        final String attribute = paramVal[CustomServicesConstants.INPUT_FIELD];

        final Step referredStep = stepsHash.get(stepId);
        if (referredStep == null) {
            return String.format("%s - %s", CustomServicesConstants.ERROR_MSG_STEP_NOT_DEFINED, stepId);
        } else {
            if (foundCycle) {
                // if cycle is found the ancestors of a step cannot be determined correctly hence we will pass the following check until the
                // cycle is resolved
                return errorMessage;
            }
            if (!getKeysByValue(descendant, inputStepId).contains(stepId)) {
                return String.format("%s(%s) - %s", referredStep.getFriendlyName(), stepId,
                        CustomServicesConstants.ERROR_MSG_STEP_IS_NOT_ANCESTER);
            }
        }

        if (input.getType().equals(CustomServicesConstants.InputType.FROM_STEP_INPUT.toString())) {
            errorMessage = validateOtherStepInput(referredStep, attribute);
        } else if (input.getType().equals(CustomServicesConstants.InputType.FROM_STEP_OUTPUT.toString())) {
            errorMessage = validateOtherStepOutput(referredStep, attribute);
        }

        return errorMessage;
    }

    private Set<String> getKeysByValue(Map<String, Set<String>> map, String value) {
        return map.entrySet()
                .stream()
                .filter(entry -> entry.getValue().contains(value))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private String checkUserInputType(final Input input) {
        final EnumSet<InputFieldType> inputFieldTypes = EnumSet.allOf(InputFieldType.class);
        final String error = String.format("%s - Valid Input Field Types %s",
                CustomServicesConstants.ERROR_MSG_INPUT_FIELD_TYPE_IS_REQUIRED,
                inputFieldTypes);
        try {
            if (StringUtils.isBlank(input.getInputFieldType())
                    || !inputFieldTypes.contains(InputFieldType.valueOf(input.getInputFieldType().toUpperCase()))) {
                return error;
            } else if (StringUtils.isNotBlank(input.getDefaultValue())) {
                return checkDefaultvalues(input.getDefaultValue(), input.getInputFieldType());
            }

        } catch (final IllegalArgumentException e) {
            return error;
        }

        return EMPTY_STRING;
    }

    private String checkUniqueNames(final boolean checkFriendlyName, final String name, final Set<String> uniqueNames) {
        if (StringUtils.isBlank(name)) {
            if (checkFriendlyName) {
                return CustomServicesConstants.ERROR_MSG_DISPLAY_IS_EMPTY;
            } else {
                return CustomServicesConstants.ERROR_MSG_INPUT_NAME_IS_EMPTY;
            }
        } else {
            final String addtoSetStr = name.toLowerCase().replaceAll("\\s+", "");
            if (uniqueNames.contains(addtoSetStr)) {
                if (checkFriendlyName) {
                    return CustomServicesConstants.ERROR_MSG_DISPLAY_NAME_NOT_UNIQUE;
                } else {
                    return CustomServicesConstants.ERROR_MSG_INPUT_NAME_NOT_UNIQUE_IN_STEP;
                }
            } else {
                uniqueNames.add(addtoSetStr);
                return EMPTY_STRING;
            }
        }
    }

    private String checkDefaultvalues(final String defaultValue, final String inputFieldType) {
        if (inputFieldType.toUpperCase().equals(CustomServicesConstants.InputFieldType.BOOLEAN.toString())) {
            if (!(defaultValue.toLowerCase().equals("true") || defaultValue.toLowerCase().equals("false"))) {
                return CustomServicesConstants.ERROR_MSG_INVALID_DEFAULT_BOOLEAN_INPUT_FIELD_TYPE;
            }
        }

        if (inputFieldType.toUpperCase().equals(InputFieldType.NUMBER.toString())) {
            if (!StringUtils.isNumeric(defaultValue)) {
                return CustomServicesConstants.ERROR_MSG_INVALID_DEFAULT_NUMBER_INPUT_FIELD_TYPE;
            }
        }

        return EMPTY_STRING;
    }

    private String validateOtherStepOutput(final Step step, final String attribute) {

        if (step.getOutput() == null) {
            return CustomServicesConstants.ERROR_MSG_OTHER_STEP_OUTPUT_NOT_DEFINED;

        }
        for (final Output output : step.getOutput()) {
            if (StringUtils.isNotBlank(output.getName()) && output.getName().equals(attribute)) {
                return EMPTY_STRING;
            }
        }

        return String.format("%s %s(%s) - %s",
                CustomServicesConstants.ERROR_MSG_OUTPUT_NOT_DEFINED_IN_OTHER_STEP, step.getDescription(),
                step.getId(), attribute);
    }
}