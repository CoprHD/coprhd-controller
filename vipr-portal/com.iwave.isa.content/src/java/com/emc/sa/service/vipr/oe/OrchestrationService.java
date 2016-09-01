/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.oe;

import com.emc.sa.service.vipr.ViPRExecutionUtils;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.oe.tasks.OrchestrationTask;

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

        // check input params & validate things to insure service will run
        
		// add a proxy token that OE can use to login to ViPR API
		params.put("ProxyToken", ExecutionUtils.currentContext().
				getExecutionState().getProxyToken());
	}

	@Override
	public void execute() throws Exception {
		
	    ExecutionUtils.currentContext().logInfo("Starting Orchestration Engine Workflow");
	    
	    // how to queue/start a task in ViPR (start OE RUnner like this?)
		String workflowResponse =
				ViPRExecutionUtils.execute(new OrchestrationTask(params));

		// how to fail workflow: throw exception:
	    if(workflowResponse == null ) {
	        throw new IllegalStateException("Workflow failed");
	    }

	    ExecutionUtils.currentContext().logInfo("Orchestration Engine Workflow " +
	            "completed successfully.  Response was: " + workflowResponse);
	} 
} 
