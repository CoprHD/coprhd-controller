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

package com.emc.sa.service.vipr.oe;

import com.emc.storageos.model.orchestration.OrchestrationWorkflowDocument;
import com.emc.storageos.model.orchestration.OrchestrationWorkflowDocument.Input;
import com.emc.storageos.model.orchestration.OrchestrationWorkflowDocument.Step;
import com.emc.storageos.primitives.Primitive;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;

import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ValidateCustomServiceWorkflow {

    private final Map<String, Object> params;
    private final Map<String, Step> stepsHash;
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(ValidateCustomServiceWorkflow.class);

    public ValidateCustomServiceWorkflow(Map<String, Object> params, Map<String, Step> stepsHash) {
        this.params = params;
        this.stepsHash = stepsHash;
    }

    public void validate() throws InternalServerErrorException, IOException {

        if(stepsHash.get(Primitive.StepType.START.toString()) == null || stepsHash.get(Primitive.StepType.END.toString()) == null) {
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Start or End Step not defined");
        }

        for (final Step step1 : stepsHash.values()) {
        	validateStep(step1);
            logger.info("Validate step input");
            validateStepInput(step1);
        }
    }

    private void validateStepInput(final Step step) {
        final Map<String, OrchestrationWorkflowDocument.InputGroup> input = step.getInputGroups();
        if (input == null) {
            logger.info("No Input is defined");
            return;
        }
        final OrchestrationWorkflowDocument.InputGroup inputGroup = input.get(OrchestrationServiceConstants.INPUT_PARAMS);
        if (inputGroup == null) {
            logger.info("No input params defined");
            return;
        }
        final List<Input> listInput = inputGroup.getInputGroup();
        if (listInput == null) {
            logger.info("No input param is defined");
            return;
        }

        for (final Input in : listInput) {
            if (in.getName() == null || in.getName().isEmpty()) {
                throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Input name not defined");
            }
            if (in.getType() == null || in.getType().isEmpty()) {
                throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Input type not defined");
            }
            switch (OrchestrationServiceConstants.InputType.fromString(in.getType())) {
                case FROM_USER:
                case ASSET_OPTION:
                    validateInputUserParams(in);

                    break;
                case FROM_STEP_INPUT:
                case FROM_STEP_OUTPUT:
                    validateOtherStepParams(in);

                    break;
                default:
                    throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Invalid Input type");
            }
        }
    }

    private void validateInputUserParams(final Input in) {
        if (params.get(in.getName()) == null && in.getDefaultValue() == null && in.getRequired()) {
            throw InternalServerErrorException.internalServerErrors.
                    customServiceExecutionFailed("input param is required but no value defined");
        }
    }

    private void validateOtherStepParams(final Input in) {
        if (in.getValue() == null || in.getValue().isEmpty()) {
            throw InternalServerErrorException.internalServerErrors.
                    customServiceExecutionFailed("input from other step value is not defined");
        }
        final String[] paramVal = in.getValue().split("\\.");
        final String stepId = paramVal[0];
        final String attribute = paramVal[1];
        final Step step1 = stepsHash.get(stepId);
        if (step1 == null) {
            throw InternalServerErrorException.internalServerErrors.
                    customServiceExecutionFailed("Step not defined. Cannot get value");
        }
        if (in.getType().equals(OrchestrationServiceConstants.InputType.FROM_STEP_INPUT.toString())) {
            if (step1.getInputGroups() == null
                    || step1.getInputGroups().get(OrchestrationServiceConstants.INPUT_PARAMS) == null
                    || step1.getInputGroups().get(OrchestrationServiceConstants.INPUT_PARAMS).getInputGroup() == null)
            {
                throw InternalServerErrorException.internalServerErrors.
                        customServiceExecutionFailed("Other Step Input param not defined");
            }

            final List<Input> in1 = step1.getInputGroups().get(OrchestrationServiceConstants.INPUT_PARAMS).getInputGroup();

            for (final Input e : in1) {
                if (e.getName().equals(attribute)) {
                    return;
                }
            }
        } else if (in.getType().equals(OrchestrationServiceConstants.InputType.FROM_STEP_OUTPUT.toString())) {
            if (step1.getOutput() == null) {
                throw InternalServerErrorException.internalServerErrors.
                        customServiceExecutionFailed("Other Step Output param not defined");
            }
            for (OrchestrationWorkflowDocument.Output out : step1.getOutput()) {
                if (out.getName().equals(attribute)) {
                    return;
                }
            }
        }
        throw InternalServerErrorException.internalServerErrors.
                customServiceExecutionFailed("Cannot find value for input param" + in.getName());
    }

    private void validateStep(final Step step) {
        if (step == null || step.getId() == null) {
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Workflow Step is null");
        }
        if (step.getId().equals(Primitive.StepType.END.toString())) {
            return;
        }
        if (step.getNext() == null) {
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Next Step Not defined");
        }
        if (step.getNext().getDefaultStep() == null && step.getNext().getFailedStep() == null) {
            throw InternalServerErrorException.internalServerErrors.
                    customServiceExecutionFailed("Next step not defined");
        }
    }
}

