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

import com.emc.sa.service.vipr.ViPRExecutionUtils;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.oe.tasks.OrchestrationRunnerTask;

import java.util.Map;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.service.Service;

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
	    
	    // how to queue/start a task in ViPR (start OE RUnner like this?)
		String workflowResponse =
				ViPRExecutionUtils.execute(new OrchestrationRunnerTask(oeOrderJson));

		// how to fail workflow: throw exception:
	    if(workflowResponse == null ) {
	        throw new IllegalStateException("Workflow did not return any response.");
	    }

	    ExecutionUtils.currentContext().logInfo("Orchestration Engine Workflow " +
	            "completed successfully.  Response was: " + workflowResponse);
	} 
} 
