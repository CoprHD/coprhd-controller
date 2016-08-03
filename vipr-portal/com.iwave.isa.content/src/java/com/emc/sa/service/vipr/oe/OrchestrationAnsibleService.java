/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.oe;

import com.emc.sa.service.vipr.ViPRExecutionUtils;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.oe.tasks.OrchestrationAnsibleTask;
import java.util.Map;
import java.util.Arrays;
import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.service.Service;

@Service("OrchestrationAnsibleService")
public class OrchestrationAnsibleService extends ViPRService {

	//TODO: some of this code is also in OrchestrationProvider - factor code into utils class

	// Name of parameter containing playbook name:
	private static final String PLAYBOOK_PARAM_NAME = "Playbook"; 

	private Map<String, Object> params = null;
	private String playbook = null;

	@Override
	public void precheck() throws Exception {

		params = ExecutionUtils.currentContext().getParameters();

		// see if some params should be handled as lists
		for( String paramKey : params.keySet() ){
			//TODO: check behavior when list was locked in catalog fields
			//   (see notes below where quotes are removed)
			String csvRegex = "^\\s?\".*(\"\\s?,\\s?\".*)+\"\\s?$";
			String paramValue = params.get(paramKey).toString();
			if(paramValue.matches(csvRegex)) {
				String param = paramValue.trim();
				param = param.substring(1, param.length()-1);
				String[] paramList = param.split("\"\\s?,\\s?\"");
				params.put(paramKey, paramList);
				warn("Replacing param '" + paramKey + "' of value '" + 
						paramValue + "' with list '" + 
						Arrays.toString(paramList) + "'");
			}
		}


		// possible bug:  when catalog form is filled out, params have quotes
		// around them, but when fields are locked in catalog, the quotes are missing.
		for( String paramKey : params.keySet() ){
			
			if(!params.get(paramKey).getClass().equals(String.class)){
				continue; // skip if not string
			}
			
			String paramValue = params.get(paramKey).toString();
			if(paramValue != null) {
				if(paramValue.equals("\"\"")) {
					params.put(paramKey, "");
				}
				else if(paramValue.length() > 2 &&
						paramValue.endsWith("\"") && paramValue.startsWith("\"")) {
					String unquotedParam = paramValue.substring(1, paramValue.length()-1);
					warn("Removing quotes from param " + paramKey + ":" + paramValue +
							"  (Result: " + unquotedParam + ")");
					params.put(paramKey, unquotedParam);
				}
			}
		}


		// TODO: check empty/blank params and make null?  (empty consistency group in
		//   XML payload was not null and threw invalid URI error


		if(!params.containsKey(PLAYBOOK_PARAM_NAME)) {
			info("No playbook specified.");
			playbook = null;
		} else {
			playbook = params.get(PLAYBOOK_PARAM_NAME).toString();
			params.remove(PLAYBOOK_PARAM_NAME);   
		}

		// pass a proxy token that OE can use to login to ViPR API
		params.put("ProxyToken", ExecutionUtils.currentContext().
				getExecutionState().getProxyToken());
	}

	@Override
	public void execute() throws Exception {
		ExecutionUtils.currentContext().logInfo("Starting Orchestration Engine Workflow '" +
		        playbook + "'");
		String workflowResponse =
				ViPRExecutionUtils.execute(new OrchestrationAnsibleTask(params,playbook));
		String errMsg = OrchestrationUtils.checkForWorkflowFailed(workflowResponse); 
		if(StringUtils.isNotBlank(errMsg)) {
			ExecutionUtils.currentContext().logError("Orchestration Engine Workflow " +
					"completed, but failed.");
			throw new IllegalStateException(errMsg);
		}  
		ExecutionUtils.currentContext().logInfo("Orchestration Engine Workflow " +
				"completed successfully.");
	} 
} 
