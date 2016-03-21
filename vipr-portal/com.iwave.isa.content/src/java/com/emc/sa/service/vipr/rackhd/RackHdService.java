/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.rackhd;

import com.emc.sa.service.vipr.ViPRExecutionUtils;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.rackhd.tasks.RackHdTask;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

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
    private String playbookNames = null;
    private List<String> playbookNameList = new ArrayList<>();

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
            playbookNames = null;
        } else {
            playbookNames = params.get(PLAYBOOK_PARAM_NAME).toString();
            params.remove(PLAYBOOK_PARAM_NAME);   
            
            // convert playbook names to list (may be >1)
            //TODO: allow playbooks to be associated with specific tasks in WF
            //TODO: requires reading tasks for selected workflows (dependent AssetOptions providers?)   
            for(String playbook: playbookNames.split(";")){
                playbookNameList.add(playbook);
            }
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
        
        // possible bug:  when catalog form is filled out, params have quotes 
        // around them, but when fields are locked in catalog, the quotes are missing.
        for( String paramKey : params.keySet() ){
            String paramValue = params.get(paramKey).toString();
            if(paramValue.endsWith("\"") && paramValue.startsWith("\"")) {
                String unquotedParam = paramValue.substring(1, paramValue.length()-1);
                warn("Removing quotes from param " + paramKey + ":" + paramValue + 
                        "  (Result: " + unquotedParam + ")");
                params.put(paramKey, unquotedParam);
            }
        }
    }

    @Override
    public void execute() throws Exception {
        ExecutionUtils.currentContext().logInfo("Starting RackHD Workflow '" +
                workflowName + "'");
        String workflowResponse =
                ViPRExecutionUtils.execute(new RackHdTask(params,workflowName,playbookNameList));
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
