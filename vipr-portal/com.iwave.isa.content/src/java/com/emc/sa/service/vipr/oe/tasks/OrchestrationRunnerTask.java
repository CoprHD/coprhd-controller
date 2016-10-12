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

package com.emc.sa.service.vipr.oe.tasks;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.oe.OrchestrationUtils;
import com.emc.sa.service.vipr.oe.gson.AffectedResource;
import com.emc.sa.service.vipr.oe.gson.OeStatusMessage;
import com.emc.sa.service.vipr.oe.gson.ViprOperation;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.TaskResourceRep;

public class OrchestrationRunnerTask extends ViPRExecutionTask<String> {

    private static final int OE_WORKFLOW_CHECK_INTERVAL = 10; // secs

    String oeOrderJson;
    
    public OrchestrationRunnerTask(String oeOrderJson) {
        super();
        this.oeOrderJson = oeOrderJson;
    }

    int intervals = 0;
    boolean timedOut = false;

    @Override
    public String executeTask() throws Exception {
        
        String workflowResponse = null; 
        
      
        String workflowId = startWorkflow();
        
        // while WF is running, periodically get the status/response from the wf engine
        
        // response may contain log messages for UI
        
        // in the case of ViPRRestCalls, response may contain AffectedResources & ViPRTasks to
        //   be added to the UI  (see sample code below, based on JSON response structure)
        
        List<URI> tasksStartedByOe = new ArrayList<>();
        List<String> messagesLogged = new ArrayList<>();
        do {
            workflowResponse = getOeWorkflowResponse(workflowId); // retrieve current response of running WF

            if(workflowResponse == null) {
                continue;
            }
            
            // here we check the response to see what's in it, looking for useful things
            
            // see if it contains an Operation with ViPR Tasks
            ViprOperation viprOperation = OrchestrationUtils.parseViprTasks(workflowResponse);
            if(viprOperation != null) {
                OrchestrationUtils.updateAffectedResources(viprOperation);
                List<TaskResourceRep> viprTaskIds = OrchestrationUtils.locateTasksInVipr(viprOperation,getClient());
                for(TaskResourceRep task : viprTaskIds ) {
                    if(!tasksStartedByOe.contains(task.getId())) {
                        addOrderIdTag(task.getId());
                        tasksStartedByOe.add(task.getId());
                        ExecutionUtils.currentContext().logInfo("Orchestration Engine started " +
                                " task '" + task.getName()+ "'  " +
                                task.getResource().getName());
                    }
                }
            } 
            // else see if it has a list of affected resources
            AffectedResource[] rsrcList = null;
            if (viprOperation == null) {
                rsrcList = OrchestrationUtils.parseResourceList(workflowResponse);
                if(rsrcList != null) {
                    OrchestrationUtils.updateAffectedResources(rsrcList);  // update UI with rsrcs
                }
            }
            // else see if it has a status message for order log in UI
            OeStatusMessage oeStatusMessage = null;
            if ((viprOperation == null) && (rsrcList == null)) {
                oeStatusMessage = OrchestrationUtils.parseOeStatusMessage(workflowResponse);
                if((oeStatusMessage != null) &&
                        !messagesLogged.contains(oeStatusMessage.getMessage())){
                    ExecutionUtils.currentContext().logInfo(oeStatusMessage.getMessage());
                    messagesLogged.add(oeStatusMessage.getMessage());
                }
            }
            // if unrecognized, log result
            if ((viprOperation == null) && (rsrcList == null) && (oeStatusMessage == null) ) {
                ExecutionUtils.currentContext().logInfo("An orchestration engine Workflow " +
                        "result was not recognized as a ViPR Task or " +
                        "list of Affected Resources in ViPR: " + workflowResponse);
            }

        } while (OrchestrationUtils.isWorkflowRunning(workflowResponse) && !timedOut);
        
        OrchestrationUtils.waitForViprTasks(tasksStartedByOe,getClient());
        
        return workflowResponse;
    }

    private String getOeWorkflowResponse(String workflowId) throws InterruptedException {
        OrchestrationUtils.sleep(OE_WORKFLOW_CHECK_INTERVAL);
        if( OrchestrationUtils.isTimedOut(++intervals) ) {
            ExecutionUtils.currentContext().logError("Orchestration Engine Workflow " +
                    workflowId + " timed out.");
            timedOut = true;
        }   
        
        String workflowStatus = null;  // TODO: get workflow status from OE_Runner
        
        // not sure how status will be structured.  Previously this was in JSON format
        //  and contained ViPR Tasks returned from ViPR API
        
        return workflowStatus; 
    }

    private String startWorkflow() {
        
        // TODO: add code to start workflow (call OE_Runner?)
        
        ExecutionUtils.currentContext().logInfo("Orchestration Engine Runner started.");
        
        return null;  // return WF ID?
    }

  


}
