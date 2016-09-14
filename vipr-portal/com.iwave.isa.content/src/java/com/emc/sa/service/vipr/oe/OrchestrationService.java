/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.oe;

import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.oe.tasks.OrchestrationRunnerTask;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.service.Service;

@Service("OrchestrationService")
public class OrchestrationService extends ViPRService {

    Map<String, Object> params = null;

    @Override
    public void precheck() throws Exception {

        // get input params from order form
        params = ExecutionUtils.currentContext().getParameters();

        // validate input params to insure service will run

        // add a proxy token that OE can use to login to ViPR API
        params.put("ProxyToken", ExecutionUtils.currentContext().
                getExecutionState().getProxyToken());

    }

    @Override
    public void execute() throws Exception {

        ExecutionUtils.currentContext().logInfo("Starting Orchestration Engine Workflow");

        List<URI> tasksStartedByOe = new ArrayList<>();  //  tasks started by steps in OE
        
        // start workflow

        String[] steps = new String[]{"Step1","Step2","Step3"};  // fake steps for test
       
        for(String step: steps) {  // loop over steps
            
            ExecutionUtils.currentContext().logInfo("Starting OrchestrationEngine Step: '" + step + "'");

            // execute step and get results
            OrchestrationRunnerTask oeTask = new OrchestrationRunnerTask(step);         
            execute(oeTask);
            String stepResults = oeTask.getResult();
            
            List<URI> newViprTasks = OrchestrationUtils.updateOrder(stepResults,getClient());  // check results from step
            tasksStartedByOe.addAll(newViprTasks);
            
        }

        // workflow ends

        OrchestrationUtils.waitForViprTasks(tasksStartedByOe, getClient());

        boolean success = true; // result of workflow execution
        if(!success) {  // test result
            throw new IllegalStateException("Not implemented.");  // order fails
        }

        ExecutionUtils.currentContext().logInfo("Orchestration Engine Workflow " +
                "completed successfully.");
    } 
} 
