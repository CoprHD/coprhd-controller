/*
 * Copyright 2016
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
import com.emc.sa.service.vipr.oe.gson.OEJson;
import com.emc.sa.service.vipr.oe.gson.OEJson.Input;
import com.emc.sa.service.vipr.oe.gson.OEJson.Step;
import com.emc.sa.service.vipr.oe.gson.OEJson.StepAttribute;
import com.emc.sa.service.vipr.oe.OrchestrationServiceConstants.InputType;
import com.emc.sa.service.vipr.oe.OrchestrationServiceConstants.OperationType;
import com.emc.sa.service.vipr.ViPRService;

import java.net.URI;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import org.slf4j.LoggerFactory;

/**
 * Orchestration Runner to parse Workflow definition
 */

@Service("OrchestrationService")
public class OrchestrationService extends ViPRService
{

    Map<String, Object> params;
    final HashMap<String, Map<String, String>> inputPerStep = new HashMap<String, Map<String, String>>();
    final HashMap<String, Map<String, String>> outputPerStep = new HashMap<String, Map<String, String>>();
    final HashMap<String, OEJson.Step> stepsHash = new HashMap<String, OEJson.Step>();

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(OrchestrationService.class);

    //TODO Implement validation
    @Override
    public void precheck() throws Exception {

        // get input params from order form
        params = ExecutionUtils.currentContext().getParameters();

        // add a proxy token that OE can use to login to ViPR API
        params.put("ProxyToken", ExecutionUtils.currentContext().
                getExecutionState().getProxyToken());
        
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
        final OEJson obj = gson.fromJson(reader, OEJson.class);

        ExecutionUtils.currentContext().logInfo("Orchestration Engine Running " +
                "Workflow: " + obj.getWorkflowName() + "\t Description:" + obj.getDescription());


        //Store Steps in a Map with key as StepId
        final ArrayList<Step> steps = obj.getSteps();
        for (Step step : steps) {
            stepsHash.put(step.getStepId(), step);
        }

        //Get first Step to execute
        Step step = stepsHash.get(InputType.START);
        String next = step.getNext().getDefault();


        while (!next.equals(InputType.END))
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

            switch(OperationType.valueOf(step.getType()))
            {
                case VIPR_REST: {
                    ExecutionUtils.currentContext().logInfo("Running REST OpName:{}" + step.getOpName());
                    /* TODO GET from DB
                     * handle Async and Sync
                     */
                    List<URI> newViprTasks = OrchestrationUtils.updateOrder(result, getClient());
                    OrchestrationUtils.waitForViprTasks(newViprTasks, getClient(), stepAttribute.getTimeout());
                    break;
                }
                case REST: {

                    break;
                }
                case SHELL: {

                    break;
                }
                case PYTHON: {

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
                    throw new IllegalStateException(result);
                    //throw new OrchestrationEngineException();
            }

            next = findNext(result, step);
            if (next == null) {
                ExecutionUtils.currentContext().logError("Orchestration Engine failed to run Workflow " +
                        "Step: " + step.getStepId() + ":" + step + "result:" + result);

                throw new IllegalStateException(result);
                //throw OrchestrationEngineException();
            }

            ExecutionUtils.currentContext().logInfo("Orchestration Engine successfully ran " +
                    "Step: " + step.getStepId() + ":" + step + "result:" + result);

            logger.info("Next step: {}", next);

        }
    }

    /**
     * Method to collect all required inputs per step for execution
     *
     * @param input It is the GSON Object of Step Input
     * @return It returns the the required inputs for a step
     */
    private Map<String, String> makeInputPerStep( final Map<String, Input> input)
    {
        final Map<String, String> inputs = new HashMap<String, String>();

        final Iterator it = input.keySet().iterator();
        while(it.hasNext())
        {
            String key = it.next().toString();
            logger.info("key:{}", key);
            Input value = input.get(it.next().toString());

            switch (InputType.valueOf(value.getType()))
            {
                case FROM_USER:
                case OTHERS:
                case ASSET_OPTION:
                {
                    final String paramVal = params.get(key).toString();
                    inputs.put(key, paramVal);
                    break;
                }
                case FROM_STEP:
                {
                    //TODO if data is still not present waitfortask ... Do some more validation
                    final String[] paramVal = value.getOtherStepValue().split(".");
                    String data = inputPerStep.get(paramVal[OrchestrationServiceConstants.STEP_ID])
                            .get(paramVal[OrchestrationServiceConstants.INPUT_FIELD]);
                    if (data == null) {
                        data = outputPerStep.get(paramVal[OrchestrationServiceConstants.STEP_ID])
                                .get(paramVal[OrchestrationServiceConstants.INPUT_FIELD]);
                    }

                    inputs.put(key, data);
                    break;
                }
                default:
                    logger.error("Input Type:{} is Invalid", value.getType());
            }
        }
        return inputs;
    }

    /**
     * Method to parse result for ViPR REST API
     * Update output per step
     * Evaluate SuccessCriteria and find next step
     * @param result result of the step execution
     * @param step workflow step
     * @return stepId of the next step to execute
     */
    private String findNext(String result, Step step)
    {
        //TODO Evaluate Expression language for Output and SuccessCritera
        //Get Output and update result
        //outputPerStep.put(step.getStepId(), null);
        //TODO Parse the result
        //TODO Evaluate condition and return next step
        logger.info("SuccessCritera: {}", step.getSuccessCritera());
        OEJson.Next n = step.getNext();
        return n.getDefault();
    }

}


