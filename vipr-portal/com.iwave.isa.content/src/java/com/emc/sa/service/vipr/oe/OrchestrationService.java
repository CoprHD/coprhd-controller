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
import com.emc.sa.service.vipr.oe.gson.WorkflowDefinition;
import com.emc.sa.service.vipr.oe.gson.WorkflowDefinition.Input;
import com.emc.sa.service.vipr.oe.gson.WorkflowDefinition.Step;
import com.emc.sa.service.vipr.oe.gson.WorkflowDefinition.StepAttribute;
import com.emc.sa.service.vipr.oe.OrchestrationServiceConstants.InputType;
import com.emc.sa.service.vipr.oe.OrchestrationServiceConstants.StepType;
import com.emc.sa.service.vipr.ViPRService;

import java.net.URI;
import java.io.FileReader;
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
    final private HashMap<String, Map<String, List<String>>> inputPerStep = new HashMap<String, Map<String, List<String>>>();
    final private HashMap<String, Map<String, List<String>>> outputPerStep = new HashMap<String, Map<String, List<String>>>();

    final private HashMap<String, Step> stepsHash = new HashMap<String, WorkflowDefinition.Step>();

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(OrchestrationService.class);

    private String eval;
    private final List<String> evaluateVal = new ArrayList<String>();
    private int errorCode;
    private int returnCode;
    
    @Override
	public void precheck() throws Exception {

	    // get input params from order form
        params = ExecutionUtils.currentContext().getParameters();

        // validate input params to insure service will run
        
		// add a proxy token that OE can use to login to ViPR API
		params.put("ProxyToken", ExecutionUtils.currentContext().
				getExecutionState().getProxyToken());
		
		// merge params into Workflow Definition JSON to make  Order JSON
		oeOrderJson = OrchestrationUtils.makeOrderJson(params);
		
	}

	@Override
	public void execute() throws Exception {
	    ExecutionUtils.currentContext().logInfo("Starting Orchestration Engine Workflow");
        try {
            workflowDefinitionParser();

            ExecutionUtils.currentContext().logInfo("Orchestration Engine SSuccessfully executed Workflow:"
                    + params.get(OrchestrationServiceConstants.WF_ID));
        } catch (final Exception e)
        {
            ExecutionUtils.currentContext().logError("Orchestration Engine Workflow Failed" + e);
            throw e;
        }
	}


    /**
     * Method to parse Workflow Definition JSON
     * @throws Exception
     */
    public void workflowDefinitionParser() throws Exception
    {
        logger.info("Parsing Workflow Definition");

        //TODO get json from DB
        final Gson gson = new Gson();
        final JsonReader reader = new JsonReader(new FileReader("/data/OEJsosn.json"));
        final WorkflowDefinition obj = gson.fromJson(reader, WorkflowDefinition.class);

        ExecutionUtils.currentContext().logInfo("Orchestration Engine Running " +
                "Workflow: " + obj.getWorkflowName() + "\t Description:" + obj.getDescription());


        //Store Steps in a Map with key as StepId
        final ArrayList<Step> steps = obj.getSteps();
	logger.info("Steps are:{}", obj.getSteps());
        for (Step step : steps) {
	    logger.info("Each Step is:{} and stepId:{}", step, step.getStepId());
            stepsHash.put(step.getStepId(), step);
        }

	logger.info("Stephash is:{}", stepsHash);
        //Get first Step to execute
        Step step = stepsHash.get("Start");
	logger.info("Step is:{}", step);
	logger.info("next step is:{}", step.getNext());
        String next = step.getNext().getDefault();

        while (!next.equals("End"))
        {
            step = stepsHash.get(next);

            ExecutionUtils.currentContext().logInfo("Orchestration Engine Running " +
                    "Step: " + step.getStepId());

            logger.info("executing Step Id: {} of Type: {}", step.getStepId(), step.getType());
            logger.debug("Input param for the Step");

            Map<String, Input> input = step.getInput();

            if (input != null)
                inputPerStep.put(step.getStepId(), makeInputPerStep(input));


            StepAttribute stepAttribute = step.getStepAttribute();
            //TODO implement waitfortask
            String result = null;

	    StepType OBJ = StepType.fromString(step.getType());
            switch(OBJ)
            {
                case VIPR_REST: {
                    ExecutionUtils.currentContext().logInfo("Running REST OpName:{}" + step.getOpName());
                    /* TODO GET from DB
                     * handle Async and Sync
                     */
                    //result = ViPRExecutionUtils.execute(new RunAnsible(step.getOpName()));
                    //List<URI> newViprTasks = OrchestrationUtils.updateOrder(result, getClient());
                    //OrchestrationUtils.waitForViprTasks(newViprTasks, getClient(), stepAttribute.getTimeout());
                    break;
                }
                case REST: {

                    break;
                }
                case ANSIBLE: {
                    ExecutionUtils.currentContext().logInfo("Running Ansible Step");
                    logger.info("Running AnsibleStep:{}", step);
                    //TODO call Ansible
                    //result = ViPRExecutionUtils.execute(new RunAnsible(step.getOpName()));

                    break;
                }
                default:
                    logger.error("Operation Type Not found. Type:{}", step.getType());

                    //throw new IllegalStateException(result);
            }


	result = "{ \n" +
        "   \"task\":[ \n" +
        "      { \n" +
        "         \"name\":\"CREATE VOLUME\",\n" +
        "         \"id\":\"urn:storageos:Task:5aaaced9-4904-49c3-ad40-6140d143e2cb:vdc1\",\n" +
        "         \"link\":{ \n" +
        "            \"rel\":\"self\",\n" +
        "            \"href\":\"/vdc/tasks/urn:storageos:Task:5aaaced9-4904-49c3-ad40-6140d143e2cb:vdc1\"\n" +
        "         },\n" +
        "         \"inactive\":false,\n" +
        "         \"global\":false,\n" +
        "         \"remote\":false,\n" +
        "         \"vdc\":{ \n" +
        "            \"id\":\"urn:storageos:VirtualDataCenter:13c56135-2b07-4a6d-9f94-ec0787c73d28:vdc1\",\n" +
        "            \"link\":{ \n" +
        "               \"rel\":\"self\",\n" +
        "               \"href\":\"/vdc/urn:storageos:VirtualDataCenter:13c56135-2b07-4a6d-9f94-ec0787c73d28:vdc1\"\n" +
        "            }\n" +
        "         },\n" +
        "         \"tags\":[ \n" +
        " \n" +
        " \n" +
        "         ],\n" +
        "         \"internal\":false,\n" +
        "         \"resource\":{ \n" +
        "            \"id\":\"urn:storageos:Volume:fe690733-dccc-4f35-bef5-760dc07eb3c8:vdc1\",\n" +
        "            \"name\":\"mendes-vol-test-1\",\n" +
        "            \"link\":{ \n" +
        "               \"rel\":\"self\",\n" +
        "               \"href\":\"/block/volumes/urn:storageos:Volume:fe690733-dccc-4f35-bef5-760dc07eb3c8:vdc1\"\n" +
        "            }\n" +
        "         },\n" +
        "         \"tenant\":{ \n" +
        "            \"id\":\"urn:storageos:TenantOrg:4f245ed0-dc0c-4ec1-9239-0154f05ef939:global\",\n" +
        "            \"link\":{ \n" +
        "               \"rel\":\"self\",\n" +
        "               \"href\":\"/tenants/urn:storageos:TenantOrg:4f245ed0-dc0c-4ec1-9239-0154f05ef939:global\"\n" +
        "            }\n" +
        "         },\n" +
        "         \"state\":\"pending\",\n" +
        "         \"description\":\"create volume operation\",\n" +
        "         \"progress\":0,\n" +
        "         \"creation_time\":1471980068816,\n" +
        "         \"op_id\":\"e2430440-f609-4547-bef7-3bb139120763\",\n" +
        "         \"associated_resources\":[ \n" +
        " \n" +
        " \n" +
        "         ],\n" +
        "         \"start_time\":1471980068815\n" +
        "      },\n" +
        "      { \n" +
        "         \"name\":\"CREATE VOLUME\",\n" +
        "         \"id\":\"urn:storageos:Task:9498e57b-de0f-4e7d-bb1f-4c5ff4a6d52c:vdc1\",\n" +
        "         \"link\":{ \n" +
        "            \"rel\":\"self\",\n" +
        "            \"href\":\"/vdc/tasks/urn:storageos:Task:9498e57b-de0f-4e7d-bb1f-4c5ff4a6d52c:vdc1\"\n" +
        "         },\n" +
        "         \"inactive\":false,\n" +
        "         \"global\":false,\n" +
        "         \"remote\":false,\n" +
        "         \"vdc\":{ \n" +
        "            \"id\":\"urn:storageos:VirtualDataCenter:13c56135-2b07-4a6d-9f94-ec0787c73d28:vdc1\",\n" +
        "            \"link\":{ \n" +
        "               \"rel\":\"self\",\n" +
        "               \"href\":\"/vdc/urn:storageos:VirtualDataCenter:13c56135-2b07-4a6d-9f94-ec0787c73d28:vdc1\"\n" +
        "            }\n" +
        "         },\n" +
        "         \"tags\":[ \n" +
        " \n" +
        " \n" +
        "         ],\n" +
        "         \"internal\":false,\n" +
        "         \"resource\":{ \n" +
        "            \"id\":\"urn:storageos:Volume:89c53819-c69d-4b79-a136-7d8a501b893e:vdc1\",\n" +
        "            \"name\":\"mendes-vol-test-2\",\n" +
        "            \"link\":{ \n" +
        "               \"rel\":\"self\",\n" +
        "               \"href\":\"/block/volumes/urn:storageos:Volume:89c53819-c69d-4b79-a136-7d8a501b893e:vdc1\"\n" +
        "            }\n" +
        "         },\n" +
        "         \"tenant\":{ \n" +
        "            \"id\":\"urn:storageos:TenantOrg:4f245ed0-dc0c-4ec1-9239-0154f05ef939:global\",\n" +
        "            \"link\":{ \n" +
        "               \"rel\":\"self\",\n" +
        "               \"href\":\"/tenants/urn:storageos:TenantOrg:4f245ed0-dc0c-4ec1-9239-0154f05ef939:global\"\n" +
        "            }\n" +
        "         },\n" +
        "         \"state\":\"pending\",\n" +
        "         \"description\":\"create volume operation\",\n" +
        "         \"progress\":0,\n" +
        "         \"creation_time\":1471980068826,\n" +
        "         \"op_id\":\"e2430440-f609-4547-bef7-3bb139120763\",\n" +
        "         \"associated_resources\":[ \n" +
        " \n" +
        " \n" +
        "         ],\n" +
        "         \"start_time\":1471980068825\n" +
        "      }\n" +
        "   ]\n" +
        "}\n";
            if (step.getOutput() != null) {
		logger.info("makeOutputPerStep is getting called");
                outputPerStep.put(step.getStepId(), makeOutputPerStep(result, step.getOutput()));
	    }

	    if (step.getSuccessCritera() == null) {
		logger.info("step.getSuccessCritera() is null");
		//Evaluate default successcriteria
		next = step.getNext().getDefault();
		ExecutionUtils.currentContext().logInfo("Orchestration Engine successfully ran " +
                        "Step: " + step.getStepId() + ":" + step + "result:" + result);
                continue;
            }

            if(findStatus(step.getSuccessCritera(), result))
            {
                next = step.getNext().getDefault();
                ExecutionUtils.currentContext().logInfo("Orchestration Engine successfully ran " +
                        "Step: " + step.getStepId() + ":" + step + "result:" + result);
                continue;
            }


            next = step.getNext().getFailedStep();
            if (next == null) {
                ExecutionUtils.currentContext().logError("Orchestration Engine failed to run Workflow " +
                        "Step: " + step.getStepId() + ":" + step + "result:" + result);

                throw new IllegalStateException(result);
            }
        }
    }

    /**
     * Method to collect all required inputs per step for execution
     *
     * @param input It is the GSON Object of Step Input
     * @return It returns the the required inputs for a step
     */
    private Map<String, List<String>> makeInputPerStep( final Map<String, Input> input) {

        final Map<String, List<String>> inputs = new HashMap<String, List<String>>();

        final Iterator it = input.keySet().iterator();
        while(it.hasNext())
        {
            String key = it.next().toString();
            logger.info("key:{}", key);
            Input value = input.get(key);
		logger.info("value is:{}", value);
	    InputType OBJ = InputType.fromString(value.getType());
            switch (OBJ)
            {
                case FROM_USER:
                case OTHERS:
                case ASSET_OPTION:
                {
		    //TODO parse this when assent option is available
			logger.info("input type: OTHERS ASSET_OPTION FROM_USER");
			break;
                    /*final String paramVal = params.get(key).toString();
                    List<String> valueList = new ArrayList<String>();
                    valueList.add(paramVal);
                    inputs.put(key, valueList);
                    break;*/
                }
                case FROM_STEP_INPUT:
                {
		    //TODO parse this when assent option is available
			logger.info("input type:FROM_STEP_INPUT");
			break;
                    //TODO if data is still not present waitfortask ... Do some more validation
                    /*final String[] paramVal = value.getOtherStepValue().split(".");
                    final String stepId = paramVal[OrchestrationServiceConstants.STEP_ID];
                    final String attribute = paramVal[OrchestrationServiceConstants.INPUT_FIELD];

                    List<String> data = inputPerStep.get(stepId).get(attribute);

                    inputs.put(key, data);
                    break;*/
                }
                case FROM_STEP_OUTPUT:
                {
		    //TODO parse this when assent option is available
			logger.info("input type:FROM_STEP_OUTPUT");
			break;
                    /*final String[] paramVal = value.getOtherStepValue().split(".");
                    final String stepId = paramVal[OrchestrationServiceConstants.STEP_ID];
                    final String attribute = paramVal[OrchestrationServiceConstants.INPUT_FIELD];
                    List<String> data = outputPerStep.get(stepId).get(attribute);

                    inputs.put(key, data);
                    break;*/
                }
                default:
                    logger.error("Input Type:{} is Invalid", value.getType());
            }
        }
        return inputs;
    }

    private Map<String, List<String>> makeOutputPerStep(final String result, final Map<String, String> output) {

        final Map<String, List<String>> out = new HashMap<String, List<String>>();

	logger.info("In makeOutputPerStep");
        /**
         * Supported output evaluation:
         * "state"
         * "task.resource.id"
         * "task.state"
         */

        Set keyset = output.keySet();
        Iterator it = keyset.iterator();
        while (it.hasNext()) {
            String key = it.next().toString();
            String value = output.get(key);
		logger.info("Key:{} and value:{}", key, value);
            out.put(key, evaluateValue(result, value));
        }
	logger.info("Out is:{}", out);
        return out;
    }

    private List<String> evaluateValue(final String result, String value) {

        final Gson gson = new Gson();
        ViprOperation res = gson.fromJson(result, ViprOperation.class);
        ExpressionParser parser = new SpelExpressionParser();

        logger.debug("Find value of:{}", value);
        List<String> valueList = new ArrayList<String>();

        //Evaluate value
        if (!value.contains(OrchestrationServiceConstants.TASK))
        {
            Expression expr = parser.parseExpression(value);
            EvaluationContext context = new StandardEvaluationContext(res);
            String val = (String) expr.getValue(context);

            valueList.add(val);
            logger.info("output for WF:{}", val);

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



    private boolean findStatus(String successCriteria, final String result) {

	logger.info("Find status for:{}", successCriteria);
        ExpressionParser parser = new SpelExpressionParser();
        Expression e2 = parser.parseExpression(successCriteria);
        EvaluationContext con2 = new StandardEvaluationContext(this);
        /*
        errorCode == 404
        returnCode == 0 This is only for Ansible
        */
        if (successCriteria.contains(OrchestrationServiceConstants.ERROR_CODE) |
                successCriteria.contains(OrchestrationServiceConstants.RETURN_CODE))
        {
            boolean val = e2.getValue(con2, Boolean.class);
            logger.info("Evaluated value for errorCode or returnCode is:{}", val);

            return val;
        }

        /*
        "#task_state == 'pending' and #description == 'create export1'";
        "#state == 'ready'";
        */

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
                    logger.info("Replaced expr:{}", exp1);
                    Expression e = parser.parseExpression(exp1);
                    val2 = val2 && e2.getValue(con2, Boolean.class);
                    logger.info("Value of replaced expr:{} is:{}", exp1, val2);
                }

                successCriteria = successCriteria.replace(exp, val2 + " ");

                logger.info("replaced statement:{}", successCriteria);
            } else {
                List<String> evaluatedValues = evaluateValue(result, condition);
                evaluateVal.add(p, evaluatedValues.get(0));
                successCriteria = successCriteria.replace("#" + condition, "evaluateVal[" + p + "]");
                logger.info("Replaced expr:{}", successCriteria);
                p++;
            }
            k++;
        }

        logger.info("Final expr:{}", successCriteria);

        Expression e1 = parser.parseExpression(successCriteria);
        boolean val1 = e1.getValue(con2, Boolean.class);

        logger.info("Value is:{}", val1);

        return val1;
    }
}


