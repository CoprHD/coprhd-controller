/*
 * Copyright 2016 Dell Inc. or its subsidiaries.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRExecutionUtils;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.oe.OrchestrationServiceConstants.InputType;
import com.emc.sa.service.vipr.oe.gson.ViprOperation;
import com.emc.sa.service.vipr.oe.gson.ViprTask;
import com.emc.sa.service.vipr.oe.tasks.OrchestrationTaskResult;
import com.emc.sa.service.vipr.oe.tasks.RunAnsible;
import com.emc.sa.service.vipr.oe.tasks.RunViprREST;
import com.emc.sa.workflow.WorkflowHelper;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument.Input;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument.Step;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument.StepAttribute;
import com.emc.storageos.primitives.Primitive;
import com.emc.storageos.primitives.Primitive.StepType;
import com.emc.storageos.primitives.PrimitiveHelper;
import com.emc.storageos.primitives.ViPRPrimitive;
import com.google.gson.Gson;

@Service("OrchestrationService")
public class OrchestrationService extends ViPRService {

    private Map<String, Object> params;
    private String oeOrderJson;

    @Autowired
    private DbClient dbClient;
    
    //<StepId, {"key" : "values...", "key" : "values ..."} ...>
    final private Map<String, Map<String, List<String>>> inputPerStep = new HashMap<String, Map<String, List<String>>>();
    final private Map<String, Map<String, List<String>>> outputPerStep = new HashMap<String, Map<String, List<String>>>();

    final private Map<String, Step> stepsHash = new HashMap<String, CustomServicesWorkflowDocument.Step>();

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(OrchestrationService.class);

    private int code;

    @Override
    public void precheck() throws Exception {

        // get input params from order form
        params = ExecutionUtils.currentContext().getParameters();

    }

    @Override
    public void execute() throws Exception {
        ExecutionUtils.currentContext().logInfo("orchestrationService.title");
        try {
            wfExecutor();
            ExecutionUtils.currentContext().logInfo("orchestrationService.successStatus");
        } catch (final Exception e) {
            ExecutionUtils.currentContext().logError("orchestrationService.failedStatus");

            throw e;
        }
    }


    /**
     * Method to parse Workflow Definition JSON
     *
     * @throws Exception
     */
    public void wfExecutor() throws Exception {

        logger.info("Parsing Workflow Definition");
        
        final String raw = ExecutionUtils.currentContext().getOrder().getWorkflowDocument();
        if( null == raw) {
            throw new IllegalStateException("Invalid orchestration service.  Workflow document cannot be null");
        }
        
        final CustomServicesWorkflowDocument obj = WorkflowHelper.toWorkflowDocument(raw);

        ExecutionUtils.currentContext().logInfo("orchestrationService.status", obj.getName(), obj.getDescription());

        final List<Step> steps = obj.getSteps();
        for (Step step : steps)
            stepsHash.put(step.getId(), step);

        Step step = stepsHash.get(StepType.START.toString());
        String next = step.getNext().getDefaultStep();

        while (next != null && !next.equals(StepType.END.toString())) {
            step = stepsHash.get(next);

            ExecutionUtils.currentContext().logInfo("orchestrationService.stepStatus", step.getId(), step.getType());

            updateInputPerStep(step);

            //TODO implement waitfortask
            StepAttribute stepAttribute = step.getAttributes();

            final OrchestrationTaskResult res;

            StepType type = StepType.fromString(step.getType());
            switch (type) {
                case VIPR_REST: {
            	    Primitive primitive = PrimitiveHelper.get(step.getOperation());
                    if( null == primitive) {
                        //TODO fail workflow
                        throw new IllegalStateException("Primitive not found: " + step.getOperation());
                    }

                    res = ViPRExecutionUtils.execute(new RunViprREST((ViPRPrimitive)(primitive),
                            getClient().getRestClient(), inputPerStep.get(step.getId())));

                    break;
                }
                case REST: {
                    //TODO implement other REST Execution
                    res = null;
                    break;
                }
                case LOCAL_ANSIBLE:
                case SHELL_SCRIPT:
                case REMOTE_ANSIBLE: {
                    res = ViPRExecutionUtils.execute(new RunAnsible(step, inputPerStep.get(step.getId()), params));

                    break;
                }
                default:
                    logger.error("Operation Type Not found. Type:{}", step.getType());

                    throw new IllegalStateException("Operation Type not supported" + type);
            }

            boolean isSuccess = isSuccess(step, res);
            if (isSuccess) {
                try {
                    updateOutputPerStep(step, res.getOut());
                } catch (final Exception e) {
                    logger.info("Failed to parse output" + e);

                    isSuccess = false;
                }
            }
	    next = getNext(isSuccess, res, step);

            if (next == null) {
                logger.error("Orchestration Engine failed to retrieve next step:{}", step.getId());

                throw new IllegalStateException("Failed to retrieve Next Step");
            }
        }
    }

    private boolean isSuccess(Step step, OrchestrationTaskResult result)
    {
        if (result == null)
            return false;
        if (step.getSuccessCriteria() == null) {
            return evaluateDefaultValue(step, result.getReturnCode());
        } else {
            return findStatus(step.getSuccessCriteria(), result);
        }
    }

    private String getNext(final boolean status, final OrchestrationTaskResult result, final Step step) {
        if (status) {
            ExecutionUtils.currentContext().logInfo("orchestrationService.stepSuccessStatus", step, result.getReturnCode());

            return step.getNext().getDefaultStep();
        }

        ExecutionUtils.currentContext().logError("orchestrationService.stepFailedStatus", step);

        return step.getNext().getFailedStep();
    }

    /**
     * Method to collect all required inputs per step for execution
     *
     * @param step It is the JSON Object of Step
     */
    private void updateInputPerStep(final Step step) throws Exception {
        if (step.getInputGroups() == null) {
            return;
        }

        final List<Input> input = step.getInputGroups().get(OrchestrationServiceConstants.INPUT_PARAMS).getInputGroup();

        if (input == null) {
            return;
        }

        final Map<String, List<String>> inputs = new HashMap<String, List<String>>();

        for(final Input value : input) {
            final String name = value.getName();

            switch (InputType.fromString(value.getType())) {
                case FROM_USER:
                case OTHERS:
                case ASSET_OPTION: {
                    //TODO handle multiple , separated values
                    final String paramVal = (params.get(name) != null) ? (StringUtils.strip(params.get(name).toString(), "\"")) : (value.getDefaultValue());

                    if (paramVal == null) {
                        if (value.getRequired()) {
                            logger.error("Can't retrieve input:{} to execute step:{}", name, step.getId());

                            throw new IllegalStateException();
                        }
                        break;
                    }

                    inputs.put(name, Arrays.asList(paramVal));

                    break;
                }
                case FROM_STEP_INPUT:
                case FROM_STEP_OUTPUT: {
                    
                    if(!isValidinput(value))
                    {
                        logger.error("Can't retrieve input:{} to execute step:{}", name, step.getId());
                        
                        throw new IllegalStateException();
                    }

                    if (value.getValue() != null) {
                        final String[] paramVal = value.getValue().split("\\.");
                        final String stepId = paramVal[OrchestrationServiceConstants.STEP_ID];
                        final String attribute = paramVal[OrchestrationServiceConstants.INPUT_FIELD];

                        Map<String, List<String>> stepInput;
                        if (value.getType().equals(InputType.FROM_STEP_INPUT.toString()))
                            stepInput = inputPerStep.get(stepId);
                        else
                            stepInput = outputPerStep.get(stepId);

                        if (stepInput == null) {
                            logger.info("stepInput is null {}", attribute);
                            //TODO waitfortask
                            
                            break;
                            
                        } else {
                            logger.info("value is:{}", stepInput.get(attribute));
                            if (stepInput.get(attribute) != null) {
                                inputs.put(name, stepInput.get(attribute));

                                break;
                            }
                        }   
                    }
                    if (value.getDefaultValue() != null) {
                        inputs.put(name, Arrays.asList(value.getDefaultValue()));
                        logger.info("value default is:{}", Arrays.asList(value.getDefaultValue()));
                        break;
                    }

                    if (false == value.getRequired()) 
                        break;

                    logger.error("Can't retrieve input:{} to execute step:{}", name, step.getId());

                    throw new IllegalStateException();

                }
                default:
                    logger.error("Input Type:{} is Invalid", value.getType());

                    throw new IllegalStateException();
            }
        }

        inputPerStep.put(step.getId(), inputs);
    }
    
    private boolean isValidinput(Input value)
    {
        if (value.getValue() == null && value.getDefaultValue() == null && value.getRequired()) {
            return false;
        }
        
        return true;

    }
    private List<String> evaluateAnsibleOut(final String result, final String key) throws Exception
    {
        final List<String> out = new ArrayList<String>();

        final JsonNode arrNode = new ObjectMapper().readTree(result).get(key);

        if (arrNode.isNull())
            throw new IllegalStateException("Could not parse the output" + key);

        if (arrNode.isArray()) {
            for (final JsonNode objNode : arrNode) {
                out.add(objNode.toString());
            }
        } else {
            out.add(arrNode.toString());
        }

        return out;
    }
    

    /**
     * Parse REST Response and get output values as specified by the user in the workflow definition
     * <p/>
     * Example of Supported output variable formats:
     * "state"
     * "task.resource.id"
     * "task.state"
     *
     * @param step
     * @param result
     */
    private void updateOutputPerStep(final Step step, final String result) throws Exception {
        final List<CustomServicesWorkflowDocument.Output> output = step.getOutput();
        if (output == null) 
            return;

        final Map<String, List<String>> out = new HashMap<String, List<String>>();

        for(CustomServicesWorkflowDocument.Output o : output) {
            if (isAnsible(step)) {
                out.put(o.getName(), evaluateAnsibleOut(result, o.getName()));
            } else {
                out.put(o.getName(), evaluateValue(result, o.getName()));
            }
        }

        outputPerStep.put(step.getId(), out);
    }

    private boolean isAnsible(final Step step)
    {
        if (step.getType().equals(StepType.LOCAL_ANSIBLE.toString()) || step.getType().equals(StepType.REMOTE_ANSIBLE.toString())
                || step.getType().equals(StepType.SHELL_SCRIPT.toString()))
            return true;

        return false;
    }
    /**
     * Evaluate
     *
     * @param step
     * @param returnCode
     * @return
     */
    private boolean evaluateDefaultValue(final Step step, final int returnCode) {
        if (isAnsible(step)) {
            if (returnCode == 0)
                return true;

            return false;
        }

        //TODO get returncode for REST API from DB. Now it is hard coded.
        int code = 200;
        if (returnCode == code)
            return true;

        return false;
    }

    /**
     * This evaluates the expression from ViPR GSON structure.
     * e.g: It evaluates "task.resource.id" from ViPR REST Response
     *
     * @param result
     * @param value
     * @return
     */
    private List<String> evaluateValue(final String result, String value) throws Exception {

        final Gson gson = new Gson();
        final ViprOperation res = gson.fromJson(result, ViprOperation.class);
        final ExpressionParser parser = new SpelExpressionParser();

        logger.debug("Find value of:{}", value);
        List<String> valueList = new ArrayList<String>();

        if (!value.contains(OrchestrationServiceConstants.TASK)) {
            Expression expr = parser.parseExpression(value);
            EvaluationContext context = new StandardEvaluationContext(res);
            String val = (String) expr.getValue(context);

            valueList.add(val);

        } else {

            String[] values = value.split("task.", 2);
            if (values.length != 2) {
                logger.error("Cannot evaluate values with statement:{}", value);

                throw new IllegalStateException();
            }
            value = values[1];
            Expression expr = parser.parseExpression(value);

            ViprTask[] tasks = res.getTask();
            for (ViprTask task : tasks) {
                EvaluationContext context = new StandardEvaluationContext(task);
                String v = (String) expr.getValue(context);
                valueList.add(v);
            }

            logger.info("valueList is:{}", valueList);
        }
        
        return valueList;
    }

    /**
     * This evaluates the status of a step from the SuccessCriteria mentioned in workflow definition JSON
     * e.g: Supported Expression Language for SuccessCriteria
     * Supported condition type code == x [x can be any number]
     * "returnCode == 404"
     * "returnCode == 0"
     * "task_state == 'pending' and description == 'create export1' and returnCode == 400"
     * "state == 'ready'";
     * Note: and, or cannot be part of lvalue or rvalue
     *
     * @param successCriteria
     * @param res
     * @return
     */
    private boolean findStatus(String successCriteria, final OrchestrationTaskResult res) {
        try {

            if (successCriteria == null)
                return true;

            if (successCriteria != null && res == null)
                return false;

            String result = res.getOut();

            SuccessCriteria sc = new SuccessCriteria();
            ExpressionParser parser = new SpelExpressionParser();
            EvaluationContext con2 = new StandardEvaluationContext(sc);
            String[] statements = successCriteria.split("\\bor\\b|\\band\\b");

            if (statements.length == 0)
                return false;

            int p = 0;
            for (String statement : statements) {
                if (statement.trim().startsWith(OrchestrationServiceConstants.RETURN_CODE)) {
                    Expression e2 = parser.parseExpression(statement);

                    sc.setCode(res.getReturnCode());
                    boolean val = e2.getValue(con2, Boolean.class);
                    logger.info("Evaluated value for errorCode or returnCode is:{}", val);

                    successCriteria = successCriteria.replace(statement, " " + val + " ");

                    continue;
                }

                String arr[] = StringUtils.split(statement);
                String lvalue = arr[0];

                if (!lvalue.contains(OrchestrationServiceConstants.TASK)) {

                    List<String> evaluatedValues = evaluateValue(result, lvalue);
                    sc.setEvaluateVal(evaluatedValues.get(0), p);
                    successCriteria = successCriteria.replace(lvalue, " evaluateVal[" + p + "]");
                    p++;

                    continue;
                }

                //TODO accepted format is task_state but spel expects task.state. Could not find a regex for that
                String lvalue1 = lvalue.replace("_", ".");

                List<String> evaluatedValues = evaluateValue(result, lvalue1);

                boolean val2 = true;

                if (evaluatedValues.isEmpty())
                    return false;

                String exp1 = statement.replace(lvalue, "eval");
                for (String evaluatedValue : evaluatedValues) {
                    sc.setEval(evaluatedValue);
                    Expression e = parser.parseExpression(exp1);
                    val2 = val2 && e.getValue(con2, Boolean.class);
                }

                successCriteria = successCriteria.replace(statement, " " + val2 + " ");
            }

            logger.info("Success Criteria to evaluate:{}", successCriteria);
            Expression e1 = parser.parseExpression(successCriteria);
            boolean val1 = e1.getValue(con2, Boolean.class);

            logger.info("Evaluated Value is:{}" + val1);

            return val1;
        } catch (final Exception e) {
            logger.error("Cannot evaluate success Criteria:{} Exception:{}", successCriteria, e);

            return false;
        }
    }
}
