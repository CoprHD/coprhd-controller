package com.emc.sa.service.vipr.oe;

import com.emc.storageos.model.orchestration.OrchestrationWorkflowDocument.Input;
import com.emc.storageos.model.orchestration.OrchestrationWorkflowDocument.Step;
import com.emc.storageos.primitives.Primitive;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Created by sonalisahu on 1/30/17.
 */
public class ValidateCustomServiceWorkflow {

    private final Map<String, Object> params;
    private final Map<String, Step> stepsHash;
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(ValidateCustomServiceWorkflow.class);

    public ValidateCustomServiceWorkflow(Map<String, Object> params, Map<String, Step> stepsHash) {
        this.params = params;
        this.stepsHash = stepsHash;
    }

    public void validateInputs() throws CustomServiceException, IOException {

        Step step = stepsHash.get(Primitive.StepType.START.toString());

        String next = validateStep(step);

        while (next != null && !next.equals(Primitive.StepType.END.toString())) {
            step = stepsHash.get(next);
            validateStepInput(step);

        }
    }

    private void validateStepInput(Step step) {
        Map<String, List<Input>> input = step.getInput();
        if (input == null) {
            logger.info("No Input is defined");

            return;
        }
        List<Input> listInput = input.get(OrchestrationServiceConstants.INPUT_PARAMS);
        if (listInput == null) {
            logger.info("No input param is defined");

            return;
        }
        for (Input in : listInput) {
            final String name = in.getName();
            if (name == null || name.isEmpty()) {
                throw CustomServiceException.exceptions.customServiceException("input name not defined");
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
                    throw CustomServiceException.exceptions.customServiceException("input type not supported");
            }
        }
    }

    private void validateInputUserParams(Input in) {
        if (params.get(in.getName()) == null && in.getDefaultValue() == null && in.getRequired()) {
            throw CustomServiceException.exceptions.customServiceException("input param is not there");
        }
    }

    private void validateOtherStepParams(Input in) {
        if (in.getValue() == null || in.getValue().isEmpty()) {
            throw CustomServiceException.exceptions.customServiceException("input value is not defined");
        }
        final String[] paramVal = in.getValue().split("\\.");
        final String stepId = paramVal[0];
        final String attribute = paramVal[1];
        Step step1 = stepsHash.get(stepId);
        if (step1 == null) {
            throw CustomServiceException.exceptions.customServiceException("step for type not defined");
        }
        if (in.getType().equals(OrchestrationServiceConstants.InputType.FROM_STEP_INPUT.toString())) {
            if (step1.getInput() == null || step1.getInput().get(OrchestrationServiceConstants.INPUT_PARAMS) == null) {
                throw CustomServiceException.exceptions.customServiceException("step for type not defined");
            }

            List<Input> in1 = step1.getInput().get(OrchestrationServiceConstants.INPUT_PARAMS);
            boolean found = false;
            for (Input e : in1) {
                if (e.getName().equals(attribute)) {
                    found = true;
                }
            }
            if (!found) {
                throw CustomServiceException.exceptions.customServiceException("step for type not defined");
            }
        } else if (in.getType().equals(OrchestrationServiceConstants.InputType.FROM_STEP_OUTPUT.toString())) {
            if (step1.getOutput() == null) {
                throw CustomServiceException.exceptions.customServiceException("step for type not defined");
            }

            if (step1.getOutput().get(attribute) == null) {
                throw CustomServiceException.exceptions.customServiceException("step for type not defined");
            }
        }
    }

    private String validateStep(Step step) {
        if (step == null || step.getNext() == null || step.getNext().getDefaultStep() == null) {
            throw CustomServiceException.exceptions.customServiceException("Start Step could not be found");
        }

        return step.getNext().getDefaultStep();
    }
}
