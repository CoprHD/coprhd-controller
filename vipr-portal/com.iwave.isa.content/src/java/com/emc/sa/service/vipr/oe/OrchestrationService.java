/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.oe;

import com.emc.sa.service.vipr.ViPRExecutionUtils;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.oe.tasks.OrchestrationRunnerTask;
import com.emc.sa.service.vipr.oe.gson.OEJson;

import java.util.Map;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.service.Service;
import com.google.gson.Gson;
import java.lang.reflect.Type;
import java.util.*;

@Service("OrchestrationService")
public class OrchestrationService extends ViPRService {

    Map<String, Object> params = null;
    String oeOrderJson;
    
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
                    final String json = "{ \n" +
                    "   \"WorkflowName\":\"SampleWorkflow\", \n" +
                    "   \"inputParams\": \n" +
                    "      {\"volumeName\" : \"\",\"numOfVolume\": \"1\"}\n" +
                    "   ,\n" +
                    "   \"Steps\":[\n" +
                    "      {\n" +
                    "         \"StepId\" : \"Start\",\n" +
                    "        \"Next\" : { \n" +
                    "        \"Default\" : \"1\" }" +
                    "      }, \n" +
                    "      {\n" +
                    "      \n" +
                    "            \"StepId\":\"1\",\n" +
                    "            \"OpName\":\"createVolume\",\n" +
                    "            \"Description\":\"Create Volume\",\n" +
                    "            \"Type\":\"ViPR REST API\",\n" +
                    "            \"inputParams\":\n" +
                    "               {\n" +
                    "                  \"size\":\"1\"\n" +
                    "               ,\n" +
                    "               \n" +
                    "                  \"numOfVolume\":\"10\"\n" +
                    "               ,\n" +
                    "               \n" +
                    "                  \"vArray\":\"Varray-1\"\n" +
                    "               ,\n" +
                    "               \n" +
                    "                  \"vPool\":\"vPool-1\"\n" +
                    "               ,\n" +
                    "               \n" +
                    "                  \"project\":\"Project-1\"\n" +
                    "               \n" +
                    "            },\n" +
                    "            \"inputFromOtherSteps\":null,\n" +
                    "            \"output\":[ \n" +
                    "               \"ALL task.resource.id\"\n" +
                    "            ],\n" +
                    "            \"SuccessCriteria\": \"AND  ALL task.resource.id == 10,ALL task.resource.state == ready\",\n" +
                    "            \"Next\": {\n" +
                    "\t\t\"Default\" : \"3\",\n" +
                    "\t\t\"condition\" : \"status=failed stepId=2\"\n" +
                    "            }\n" +
                    "      },\n" +
                    "      {\n" +
                    "         \n" +
                    "            \"StepId\":\"2\",\n" +
                    "            \"OpName\":\"deleteVolumes\",\n" +
                    "            \"Description\":\" Delete the volumes\",\n" +
                    "            \"Type\":\"ViPR REST API\",\n" +
                    "            \"inputParams\":null,\n" +
                    "            \"inputFromOtherSteps\":\n" +
                    "               {\n" +
                    "                  \"1\" : \"ALL task.resource.id\"               \n" +
                    "            },\n" +
                    "            \"output\":[ \n" +
                    "               \"ALL task.resource.id\"\n" +
                    "            ],\n" +
                    "            \"Successcriteria\":null,\n" +
                    "            \"Next\": {\n" +
                    "               \"Default\": \"3\"\n" +
                    "               }\n" +
                    "            \n" +
                    "         \n" +
                    "      },\n" +
                    "      {\n" +
                    "         \n" +
                    "            \"StepId\":\"3\",\n" +
                    "            \"OpName\":\"sendEmail\",\n" +
                    "            \"Description\":\"Generic Shell Primitive\",\n" +
                    "            \"Type\":\"Shell Script\",\n" +
                    "            \"inputParams\":null,\n" +
                    "            \"inputFromOtherSteps\":\n" +
                    "               { \n" +
                    "                  \"1\" : \"ALL taskresource+id\",\n" +
                    "                  \"2\" : \"ALL task.resource+id\"  \n" +
                    "               },\n" +
                    "            \n" +
                    "            \"output\":null,\n" +
                    "            \"Successcriteria\":null,\n" +
                    "            \"Next\": {\n" +
                    "               \"Default\": \"END\" \n" +
                    "            }\n" +
                    "         \n" +
                    "      },\n" +
                    "      {\n" +
                    "         \n" +
                    "             \"StepId\": \"END\"\n" +
                    "         \n" +
                    "      }\n" +
                    "   ]\n" +
                    "}";
            // Get from Context
            //Thread th = new Thread(new WFExecutor(json));
            //th.start();
            System.out.print("calling parser");
            OEParser(json);

	    
	    // how to queue/start a task in ViPR (start OE RUnner like this?)
//		String workflowResponse =
//				ViPRExecutionUtils.execute(new OrchestrationRunnerTask(oeOrderJson));

		// how to fail workflow: throw exception:
//	    if(workflowResponse == null ) {
//	        throw new IllegalStateException("Workflow did not return any response.");
	   // }

	//    ExecutionUtils.currentContext().logInfo("Orchestration Engine Workflow " +
	  //          "completed successfully.  Response was: " + workflowResponse);
	}

public String OEParser(final String pattern) throws Exception
        {
            System.out.print("in parser");


            Gson gson = new Gson();
            OEJson obj = gson.fromJson(pattern, OEJson.class);

            System.out.print("Global Input param");
            Map<String, String> str = obj.getInputParams();

            ArrayList<OEJson.Step> steps = obj.getSteps();
            HashMap<String, OEJson.Step> stepsHash = new HashMap<String, OEJson.Step>();
            for (OEJson.Step step : steps)
            {
                stepsHash.put(step.getStepId(), step);
            }

            OEJson.Step step = stepsHash.get("Start");

            OEJson.Next next = step.getNext();
            String next1 = next.getDefault();
            while(!next1.equals("END"))
            {
                step = stepsHash.get(next1);
                System.out.println("StepId" + step.getStepId() + "\n");

                    System.out.print("OpName" + step.getOpName() + "\n");
                    System.out.print("Description" + step.getDescription() + "\n");
                    System.out.print("Type" + step.getType() + "\n");
                    System.out.println("Input param for the Step");
                    Map<String, String> input = step.getInputParams();
                    System.out.print(input);
                    Map<String, String> finalInput = new HashMap<String, String>();


                    if (input != null) {
                        finalInput.putAll(input);
                        Set keyset = input.keySet();
                        Iterator it = keyset.iterator();
                        while (it.hasNext()) {
                            String key = it.next().toString();
                            System.out.println("Key" + key);
                            System.out.print("Value" + input.get(key));
                        }
                    }


                    System.out.println("Input from other Steps");
                    input = step.getInputFromOtherSteps();
                System.out.print(input);

                if (input != null) {
                    finalInput.putAll(input);
                    Set keyset = input.keySet();
                    Iterator it = keyset.iterator();
                    while (it.hasNext()) {
                        String key = it.next().toString();
                        System.out.println("Key" + key);
                        System.out.print("Value" + input.get(key));
                    }
                }

                    System.out.print("output");
                    if (step.getOutput() != null) {
                        for (String out : step.getOutput()) {
                            System.out.println(out);
                        }
                    }

                    OEJson.Next n = step.getNext();
                    //TODO evaluate condition
                    next1 = n.getDefault();
                    System.out.println("Next step" + next);
                    System.out.print("SuccessCritera" + step.getSuccessCritera() + "\n");

                    finalInput.putAll(str);
                    inputPerStep.put(step.getStepId(), finalInput);

                if (step.getType().equals("REST") || step.getType().equals("ViPR REST"))
                {
			System.out.println("Running REST");
                    //RunREST run = new RunREST();
                    //Map<String, String> result = run.runREST(step, inputPerStep, getModelClient(), getClient());

                    //outputPerStep.put(step.getStepId(), result);
                }
                else
                {
			System.out.println("Running Ansible");
                    //RunAnsible run = new RunAnsible();
                    //Map<String, String> result = run.runAnsible(step, inputPerStep);

                    //outputPerStep.put(step.getStepId(), result);
                }
            }

            //send back the result
            return pattern;
        }


        HashMap<String, Map<String, String>> inputPerStep = new HashMap<String, Map<String, String>>();
        HashMap<String, Map<String, String>> outputPerStep = new HashMap<String, Map<String, String>>();
}

