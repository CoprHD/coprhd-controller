/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.rackhd;

import com.emc.sa.service.vipr.ViPRExecutionUtils;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.rackhd.tasks.RackHdTask;
import com.google.gson.Gson;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.service.Service;

@Service("RackHdService")
public class RackHdService extends ViPRService {

    //TODO: much of this code is also in RackHdProvider - factor code into utils class

    // Name of parameter containing workflow name:
    private static final String WORKFLOW_PARAM_NAME = "Workflow"; 

    // Name of parameter containing playbook name:
    private static final String PLAYBOOK_PARAM_NAME = "Playbook"; 
    
    private Map<String, Object> params = null;
    private String workflowName = null;
    private String playbookName = null;

    @Override
    public void precheck() throws Exception {
        
        params = ExecutionUtils.currentContext().getParameters();

        // TODO: check empty/blank params and make null?  (empty consistency group in
        //   XML payload was not null and threw invalid URI error

        if(!params.containsKey(WORKFLOW_PARAM_NAME)) {
            throw new IllegalStateException("No workflow specified " +
                    "in param named " + WORKFLOW_PARAM_NAME + "'");
        }
        workflowName = params.get(WORKFLOW_PARAM_NAME).toString();
        params.remove(WORKFLOW_PARAM_NAME);

        if(!params.containsKey(PLAYBOOK_PARAM_NAME)) {
            info("No playbook specified.");
            playbookName = null;
        } else {
            playbookName = params.get(PLAYBOOK_PARAM_NAME).toString();
            params.remove(PLAYBOOK_PARAM_NAME);            
        }

        // TODO: fix: can't detect type, so interpret Storage Sizes in GB
        for( String paramKey : params.keySet() ){
            String p = params.get(paramKey).toString();
            if(p.endsWith("GB")) {
                String pNum = p.substring(0, p.length()-2);
                if(StringUtils.isNumeric(pNum)) {
                    params.put(paramKey, pNum);
                }
            }
        }
    }

    @Override
    public void execute() throws Exception {

        ExecutionUtils.currentContext().logInfo("Starting RackHD Workflow '" +
                workflowName + "'");

        String workflowResponse =
                ViPRExecutionUtils.execute(new RackHdTask(params,workflowName,playbookName));

        String errMsg = RackHdUtils.checkForWorkflowFailed(workflowResponse); 
        if(errMsg != null) {
            ExecutionUtils.currentContext().logError("RackHD Workflow " +
                    "completed, but failed.");
            throw new IllegalStateException(errMsg);
        }
        
        ExecutionUtils.currentContext().logInfo("RackHD Workflow " +
                "completed successfully.");
    }

    
} 
