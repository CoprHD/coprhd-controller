/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.oe;

import com.emc.sa.service.vipr.ViPRService;
import java.util.Map;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.service.Service;

@Service("OrchestrationService")
public class OrchestrationService extends ViPRService {

    @Override
	public void precheck() throws Exception {

	    // get input params from order form
        Map<String, Object> params = ExecutionUtils.currentContext().getParameters();

		// pass a proxy token that OE can use to login to ViPR API
		params.put("ProxyToken", ExecutionUtils.currentContext().
				getExecutionState().getProxyToken());
	}

	@Override
	public void execute() throws Exception {
		
	    
	    ExecutionUtils.currentContext().logInfo("Starting Orchestration Engine Workflow");
	    
	    // how to queue/start a task in ViPR
		//String workflowResponse =
		//		ViPRExecutionUtils.execute(new OrchestrationTask(params,workflowName,playbookNameList,nodeId));

		// how to fila workflow: throw exception:
	    //throw new IllegalStateException(errMsg);

	    ExecutionUtils.currentContext().logInfo("Orchestration Engine Workflow " +
	            "completed successfully.");
	} 
} 
