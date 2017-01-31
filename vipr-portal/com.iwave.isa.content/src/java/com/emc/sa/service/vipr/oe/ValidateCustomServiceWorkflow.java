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

    public void validateInputs() throws CustomServiceException, IOException {

        if(stepsHash.get(Primitive.StepType.START.toString()) == null) {
            throw InternalServerErrorException.internalServerErrors.customeServiceExecutionFailed("No Start Step defined");
        }

        if(stepsHash.get(Primitive.StepType.END.toString()) == null) {
            throw InternalServerErrorException.internalServerErrors.customeServiceExecutionFailed("No End Step defined");
        }

        for (final Step step1 : stepsHash.values()) {
        	validateStep(step1);
            logger.info("Validate step input");
            validateStepInput(step1);
        }
    }

    private void validateStepInput(final Step step) {
        final Map<String, List<Input>> input = step.getInput();
        if (input == null) {
            logger.info("No Input is defined");

            return;
        }
        final List<Input> listInput = input.get(OrchestrationServiceConstants.INPUT_PARAMS);
        if (listInput == null) {
            logger.info("No input param is defined");

            return;
        }

	logger.info("validateStepInput");
        for (Input in : listInput) {
		logger.info("input is:{}", in);
            final String name = in.getName();
            if (name == null || name.isEmpty()) {
		logger.info("name == null || name.isEmpty()");
                //throw CustomServiceException.exceptions.customServiceException("input name not defined");
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
                    logger.error("Invalid Input type");
                    //throw CustomServiceException.exceptions.customServiceException("input type not supported");
            }
        }
    }

    private void validateInputUserParams(Input in) {
	logger.info("validateInputUserParams");
        if (params.get(in.getName()) == null && in.getDefaultValue() == null && in.getRequired()) {
		logger.info("params.get(in.getName()) == null && in.getDefaultValue() == null && in.getRequired()");
            //throw CustomServiceException.exceptions.customServiceException("input param is not there");
        }
    }

    private void validateOtherStepParams(Input in) {
        if (in.getValue() == null || in.getValue().isEmpty()) {
            //throw CustomServiceException.exceptions.customServiceException("input value is not defined");
        }
        final String[] paramVal = in.getValue().split("\\.");
        final String stepId = paramVal[0];
        final String attribute = paramVal[1];
        Step step1 = stepsHash.get(stepId);
        if (step1 == null) {
            //throw CustomServiceException.exceptions.customServiceException("step for type not defined");
        }
        if (in.getType().equals(OrchestrationServiceConstants.InputType.FROM_STEP_INPUT.toString())) {
            if (step1.getInput() == null || step1.getInput().get(OrchestrationServiceConstants.INPUT_PARAMS) == null) {
                //throw CustomServiceException.exceptions.customServiceException("step for type not defined");
            }

            List<Input> in1 = step1.getInput().get(OrchestrationServiceConstants.INPUT_PARAMS);
            boolean found = false;
            for (Input e : in1) {
                if (e.getName().equals(attribute)) {
                    found = true;
                }
            }
            if (!found) {
                //throw CustomServiceException.exceptions.customServiceException("step for type not defined");
            }
        } else if (in.getType().equals(OrchestrationServiceConstants.InputType.FROM_STEP_OUTPUT.toString())) {
            if (step1.getOutput() == null) {
                //throw CustomServiceException.exceptions.customServiceException("step for type not defined");
            }

            if (step1.getOutput().get(attribute) == null) {
                //throw CustomServiceException.exceptions.customServiceException("step for type not defined");
            }
        }
    }
    private void validateStep(final Step step) {
        if (step == null || step.getId() == null) {
            throw InternalServerErrorException.internalServerErrors.customeServiceExecutionFailed("Workflow Step is null");
        }
        if (step.getId().equals(Primitive.StepType.END.toString())) {
            return;
        }
        if (step.getNext() == null) {
            throw InternalServerErrorException.internalServerErrors.customeServiceExecutionFailed("Next Step Not defined");
        }
        if (step.getNext().getDefaultStep() == null && step.getNext().getFailedStep() == null) {
            throw InternalServerErrorException.internalServerErrors.
                    customeServiceExecutionFailed("Next step not defined");
        }
    }
}
