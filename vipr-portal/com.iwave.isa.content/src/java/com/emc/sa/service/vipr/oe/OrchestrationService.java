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

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRExecutionUtils;
import com.emc.sa.service.vipr.oe.gson.WorkflowDefinition;
import com.emc.sa.service.vipr.oe.gson.WorkflowDefinition.Input;
import com.emc.sa.service.vipr.oe.gson.WorkflowDefinition.Step;
import com.emc.sa.service.vipr.oe.gson.WorkflowDefinition.StepAttribute;
import com.emc.sa.service.vipr.oe.OrchestrationServiceConstants.InputType;
import com.emc.sa.service.vipr.oe.OrchestrationServiceConstants.StepType;
import com.emc.sa.service.vipr.ViPRService;

import java.net.URI;
import java.io.FileReader;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.emc.sa.service.vipr.oe.gson.ViprOperation;
import com.emc.sa.service.vipr.oe.gson.ViprTask;
import com.emc.sa.service.vipr.oe.tasks.RunREST;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

@Service("OrchestrationService")
public class OrchestrationService extends ViPRService {

    private Map<String, Object> params;
    private String oeOrderJson;

    //<StepId, {"key" : "values...", "key" : "values ..."} ...>
    final private Map<String, Map<String, List<String>>> inputPerStep = new HashMap<String, Map<String, List<String>>>();
    final private Map<String, Map<String, List<String>>> outputPerStep = new HashMap<String, Map<String, List<String>>>();

    final private Map<String, Step> stepsHash = new HashMap<String, WorkflowDefinition.Step>();

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(OrchestrationService.class);

    //Variables for Evaluation of SuccessCriteria and Output
    private String eval;
    private final List<String> evaluateVal = new ArrayList<String>();
    private int code;

    public String getEval() {
        return eval;
    }

    public List<String> getEvaluateVal() {
        return evaluateVal;
    }

    public void setCode(int code)
    {
        this.code = code;
    }

    @Override
    public void precheck() throws Exception {

        // get input params from order form
        params = ExecutionUtils.currentContext().getParameters();

        // validate input params to insure service will run

        // add a proxy token that OE can use to login to ViPR API
        params.put("ProxyToken", ExecutionUtils.currentContext().
                getExecutionState().getProxyToken());
        //Remove after integration with order form
        params.put("size", "1");
        params.put("name", "Vol-1");
        params.put("count", "1");
        params.put("varray", "Varray-1");
        params.put("vpool", "Vpool-1");
        params.put("project", "Project-1");
	params.put("consistency_group", "");

	ExecutionUtils.currentContext().logInfo("In PreCheck");
    }
    private void initparam()
    {
        params = ExecutionUtils.currentContext().getParameters();

        // validate input params to insure service will run

        // add a proxy token that OE can use to login to ViPR API
        params.put("ProxyToken", ExecutionUtils.currentContext().
                getExecutionState().getProxyToken());
        //Remove after integration with order form
        params.put("Size", "1");
        params.put("volumeName", "Vol-1");
        params.put("numOfVolume", "1");
        params.put("vArray", "Varray-1");
        params.put("vPool", "Vpool-1");
        params.put("project", "Project-1");

    }

    @Override
    public void execute() throws Exception {
        ExecutionUtils.currentContext().logInfo("Starting Orchestration Engine Workflow");
        try {
	    initparam();
            wfExecutor();

            ExecutionUtils.currentContext().logInfo("Orchestration Engine Successfully executed Workflow:"
                    + params.get(OrchestrationServiceConstants.WF_ID));
        } catch (final Exception e) {
            ExecutionUtils.currentContext().logError("Orchestration Engine Workflow Failed" + e);
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

        //TODO get json from DB
        final Gson gson = new Gson();
        final JsonReader reader = new JsonReader(new FileReader("/data/OEJsosn.json"));
        final WorkflowDefinition obj = gson.fromJson(reader, WorkflowDefinition.class);

        ExecutionUtils.currentContext().logInfo("Orchestration Engine Running " +
                "Workflow: " + obj.getWorkflowName() + "\t Description:" + obj.getDescription());


        final ArrayList<Step> steps = obj.getSteps();
        for (Step step : steps)
            stepsHash.put(step.getStepId(), step);

        Step step = stepsHash.get(StepType.START.toString());
        String next = step.getNext().getDefault();

        while (next != null && !next.equals(StepType.END.toString())) {
            step = stepsHash.get(next);

            ExecutionUtils.currentContext().logInfo("Orchestration Engine Running " +
                    "Step: " + step.getStepId());

            updateInputPerStep(step);

            //TODO implement waitfortask
            StepAttribute stepAttribute = step.getStepAttribute();

            String result = null;

            StepType type = StepType.fromString(step.getType());
            switch (type) {
                case VIPR_REST: {
                    ExecutionUtils.currentContext().logInfo("Running REST OpName:{}" + step.getOpName() + inputPerStep.get(step.getStepId()));
                    result = ViPRExecutionUtils.execute(new RunREST(step.getOpName(), params.get("ProxyToken").toString(), inputPerStep.get(step.getStepId())));

                    break;
                }
                case REST: {

                    break;
                }
                case ANSIBLE: {
                    ExecutionUtils.currentContext().logInfo("Running Ansible Step");

                    break;
                }
                default:
                    logger.error("Operation Type Not found. Type:{}", step.getType());

                    throw new IllegalStateException(result);
            }

            updateOutputPerStep(step, result);


            if ((step.getSuccessCritera() == null && evaluateDefaultValue(step, code)) || (step.getSuccessCritera() != null && findStatus(step.getSuccessCritera(), result))) {
                next = updateResult(true, result, step);
            } else {
                next = updateResult(false, result, step);
            }

            if (next == null) {
                ExecutionUtils.currentContext().logError("Orchestration Engine failed to retrieve next step " +
                        "Step: " + step.getStepId() + ":" + step);

                throw new IllegalStateException(result);
            }
        }
    }

    private String updateResult(final boolean status, final String result, final Step step) {
        if (status) {
            ExecutionUtils.currentContext().logInfo("Orchestration Engine successfully ran " +
                    "Step: " + step.getStepId() + ":" + step + "result:" + result);

            return step.getNext().getDefault();
        }

        ExecutionUtils.currentContext().logError("Orchestration Engine failed to run step " +
                "Step: " + step.getStepId() + ":" + step + "result:" + result);

        return step.getNext().getFailedStep();

    }

    /**
     * Method to collect all required inputs per step for execution
     *
     * @param step It is the GSON Object of Step
     */
    private void updateInputPerStep(final Step step) throws Exception {
        logger.info("executing Step Id: {} of Type: {}", step.getStepId(), step.getType());

        Map<String, Input> input = step.getInput();
        if (input == null)
            return;

        final Map<String, List<String>> inputs = new HashMap<String, List<String>>();

        final Iterator it = input.keySet().iterator();
        while (it.hasNext()) {
            String key = it.next().toString();
            Input value = input.get(key);
	    logger.info("key is:{} and value is:{}", key, value);
            if (value == null) {
                logger.error("Wrong key for input:{} Can't get input to execute the step:{}", key, step.getStepId());

                throw new IllegalStateException();
            }

            switch (InputType.fromString(value.getType())) {
                case FROM_USER:
                case OTHERS:
                case ASSET_OPTION: {
                    //TODO handle multiple , separated values
                    final String paramVal = (params.get(key) == null) ? (params.get(key).toString()) : (value.getDefault());

		    logger.info("paramVal is:{} and key:{}", paramVal, key);
                    if (paramVal == null) {
			logger.info("paramval is null: for key:{}", key);
                        if (value.getRequired().equals("true")) {
                            logger.error("Can't retrieve input:{} to execute step:{}", key, step.getStepId());

                            throw new IllegalStateException();
                        }
                        break;
                    }

                    inputs.put(key, Arrays.asList(paramVal));

                    break;
                }
                case FROM_STEP_INPUT:
                case FROM_STEP_OUTPUT: {
                    if (value.getOtherStepValue() == null && value.getDefault() == null && value.getRequired().equals("true")) {
                        logger.error("Can't retrieve input:{} to execute step:{}", key, step.getStepId());

                        throw new IllegalStateException();
                    }

                    if (value.getOtherStepValue() == null) {
                        if (value.getDefault() != null) {
                            inputs.put(key, Arrays.asList(value.getDefault()));
                            break;
                        }
                        logger.info("Could not get input value for:{}", value.getOtherStepValue());
                        break;
                    }

                    final String[] paramVal = value.getOtherStepValue().split("\\.");
                    final String stepId = paramVal[OrchestrationServiceConstants.STEP_ID];
                    final String attribute = paramVal[OrchestrationServiceConstants.INPUT_FIELD];
                    Map<String, List<String>> stepInput;
                    if (value.getType().equals(InputType.FROM_STEP_INPUT.toString()))
                        stepInput = inputPerStep.get(stepId);
                    else
                        stepInput = outputPerStep.get(stepId);

                    if (stepInput == null || (stepInput != null && stepInput.get(attribute) == null)) {

                        if (value.getDefault() != null) {
                            inputs.put(key, Arrays.asList(value.getDefault()));
                            break;
                        }

                        if (value.getRequired().equals("false"))
                            break;

                        //TODO if data is still not present waitfortask ... Do some more validation

                        logger.error("Can't retrieve input:{} to execute step:{}", key, step.getStepId());

                        throw new IllegalStateException();
                    }

                    inputs.put(key, stepInput.get(attribute));
                    break;
                }
                default:
                    logger.error("Input Type:{} is Invalid", value.getType());

                    throw new IllegalStateException();
            }
        }

        inputPerStep.put(step.getStepId(), inputs);
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
    private void updateOutputPerStep(final Step step, final String result) {
        final Map<String, String> output = step.getOutput();
        if (output == null)
            return;

        final Map<String, List<String>> out = new HashMap<String, List<String>>();

        Set keyset = output.keySet();
        Iterator it = keyset.iterator();
        while (it.hasNext()) {
            String key = it.next().toString();
            String value = output.get(key);
            out.put(key, evaluateValue(result, value));
        }

        outputPerStep.put(step.getStepId(), out);
    }

    /**
     * Evaluate
     *
     * @param step
     * @param returnCode
     * @return
     */
    private boolean evaluateDefaultValue(final Step step, final int returnCode) {
        if (step.getType().equals(StepType.ANSIBLE.toString())) {
            if (returnCode == 0)
                return true;

            return false;
        }

        String opName = step.getOpName();
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
    private List<String> evaluateValue(final String result, String value) {

        final Gson gson = new Gson();
        ViprOperation res = gson.fromJson(result, ViprOperation.class);
        ExpressionParser parser = new SpelExpressionParser();

        logger.debug("Find value of:{}", value);
        List<String> valueList = new ArrayList<String>();

        if (!value.contains(OrchestrationServiceConstants.TASK)) {
            Expression expr = parser.parseExpression(value);
            EvaluationContext context = new StandardEvaluationContext(res);
            String val = (String) expr.getValue(context);

            valueList.add(val);

            return valueList;
        }

        value = value.split("task.", 2)[1];
        Expression expr = parser.parseExpression(value);

        ViprTask[] tasks = res.getTask();
        for (ViprTask task : tasks) {
            EvaluationContext context = new StandardEvaluationContext(task);
            String v = (String) expr.getValue(context);
            valueList.add(v);
        }

        logger.info("valueList is:{}", valueList);

        return valueList;
    }

    /**
     * This evaluates the status of a step from the SuccessCriteria mentioned in workflow definition JSON
     * e.g: Supported Expression Language for SuccessCriteria
     * Supported condition type code == x [x can be any number]
     * code == 404
     * code == 0
     * "#task_state == 'pending' and #description == 'create export1'";
     * "#state == 'ready'";
     *
     * @param successCriteria
     * @param result
     * @return
     */
    private boolean findStatus(String successCriteria, final String result) {

        if (successCriteria == null || result == null) {
            logger.info("Nothing to evaluate");
            return true;
        }
        logger.info("Find status for:{}", successCriteria);
        ExpressionParser parser = new SpelExpressionParser();
        Expression e2 = parser.parseExpression(successCriteria);
        EvaluationContext con2 = new StandardEvaluationContext(this);

        if (successCriteria.contains(OrchestrationServiceConstants.RETURN_CODE)) {
            boolean val = e2.getValue(con2, Boolean.class);
            logger.info("Evaluated value for errorCode or returnCode is:{}", val);

            return val;
        }

        String[] statemets = successCriteria.split("or|and");
        Matcher matcher = Pattern.compile("#(\\w+)").matcher(successCriteria);

        int k = 0;
        int p = 0;
        while (matcher.find()) {
            String condition = matcher.group(1);
            logger.info("Find value of:{}", matcher.group(1));

            if (condition.contains(OrchestrationServiceConstants.TASK)) {

                //TODO accepted format is task_state but spel expects task.state. Couldnot find a regex for that
                String condition1 = condition.replace("_", ".");
                List<String> evaluatedValues = evaluateValue(result, condition1);
                boolean val2 = true;
                if (statemets.length == 0)
                    return false;
                String exp = statemets[k];

                if (evaluatedValues.isEmpty())
                    return false;

                for (String evaluatedValue : evaluatedValues) {
                    eval = evaluatedValue;
                    String exp1 = exp.replace("#" + condition, "eval");
                    Expression e = parser.parseExpression(exp1);
                    val2 = val2 && e.getValue(con2, Boolean.class);
                }

                successCriteria = successCriteria.replace(exp, val2 + " ");

            } else {
                List<String> evaluatedValues = evaluateValue(result, condition);
                evaluateVal.add(p, evaluatedValues.get(0));
                successCriteria = successCriteria.replace("#" + condition, "evaluateVal[" + p + "]");
                p++;
            }
            k++;
        }

        Expression e1 = parser.parseExpression(successCriteria);
        boolean val1 = e1.getValue(con2, Boolean.class);

        logger.info("Value is:{}", val1);

        return val1;
    }
}



