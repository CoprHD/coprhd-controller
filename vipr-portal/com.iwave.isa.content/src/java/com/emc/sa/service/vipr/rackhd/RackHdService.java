/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.rackhd;

import com.emc.sa.service.vipr.ViPRExecutionUtils;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.rackhd.gson.AffectedResource;
import com.emc.sa.service.vipr.rackhd.gson.Context;
import com.emc.sa.service.vipr.rackhd.gson.FinishedTask;
import com.emc.sa.service.vipr.rackhd.gson.Job;
import com.emc.sa.service.vipr.rackhd.gson.RackHdWorkflow;
import com.emc.sa.service.vipr.rackhd.tasks.RackHdTask;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.rackhd.api.restapi.RackHdRestClient;
import com.emc.storageos.rackhd.api.restapi.RackHdRestClientFactory;
import com.emc.vipr.client.Tasks;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.jersey.api.client.ClientResponse;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;

import org.apache.commons.io.IOUtils;
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


    
    // JSON converter
    private static Gson gson = null;

    public RackHdService() {
         gson = new Gson();
    }

    Map<String, Object> params = null;
    String workflowName = null;
    String playbookName = null;

    @Override
    public void precheck() throws Exception {
        params = ExecutionUtils.currentContext().getParameters();

        for( String paramKey : params.keySet() ){
            info("RackHDService params: " + paramKey + " = " + params.get(paramKey));
        }

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
      
       String workflowResponse =
               ViPRExecutionUtils.execute(new RackHdTask(params,workflowName,playbookName));
        
        updateAffectedResources(workflowResponse);

        RackHdUtils.checkForWorkflowFailed(workflowResponse);  
    }

    private void updateAffectedResources(String workflowResponse) {
        info("MENDES: updateAffectedResources  wf Response=" + workflowResponse);
        RackHdWorkflow wf = 
                RackHdUtils.getWorkflowObjFromJson(workflowResponse);  
        for(FinishedTask task : Arrays.asList(wf.getFinishedTasks())) {
            //String resultJson = 
            //        task.getJob().getContext().getAnsibleResultFile();
            Job j = task.getJob();
            Context c = j.getContext();
            String resultJson = c.getAnsibleResultFile();
            info("MENDES: updateAffectedResources resultJson=" + resultJson);

            try {
                AffectedResource[] affectedResources = 
                        gson.fromJson(resultJson,AffectedResource[].class);
                for(AffectedResource rsrc:affectedResources ) {
                    this.addAffectedResource(rsrc.getValue());
                }
            } catch(JsonSyntaxException e) {
                // ignore syntax exceptions, if not valid JSON
                // there may be an error msg in the response
                warn("Response from RackHD did not contain valid Json.  "  + 
                        "It was: " + resultJson);
            }
        }
    }
} 
