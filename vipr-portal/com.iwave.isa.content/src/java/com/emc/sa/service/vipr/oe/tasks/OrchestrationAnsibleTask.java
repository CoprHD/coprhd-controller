package com.emc.sa.service.vipr.oe.tasks;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.oe.OrchestrationUtils;
import com.emc.sa.service.vipr.oe.gson.AffectedResource;
import com.emc.sa.service.vipr.oe.gson.OeStatusMessage;
import com.emc.sa.service.vipr.oe.gson.ViprOperation;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.TaskResourceRep;

public class OrchestrationAnsibleTask extends ViPRExecutionTask<String> {


    private static final int OE_WORKFLOW_CHECK_INTERVAL = 10; // secs

    private Map<String, Object> params;
    private String workflowName;
    private String playbookNameList;
    public OrchestrationAnsibleTask(Map<String, Object> params, String playbook) {
        super();
        this.params = params;
        this.playbookNameList = playbook;
    }

    int intervals = 0;
    boolean timedOut = false;

    @Override
    public String executeTask() throws Exception {
        String workflowResponse = null;  
        // TODO: NOT SURE WHAT ANSIBLE RESPONSE IS.  MAYBE THE RESULT FILE, AND INDICATION WHETHER ANSIBLE COMPLETED, AND HAS FAILED/SUCCEEDED

        String workflowId = startWorkflow();
        List<URI> tasksStartedByOe = new ArrayList<>();
        List<String> messagesLogged = new ArrayList<>();
        do {
            workflowResponse = getOeWorkflowResponse(workflowId);


            // get results (maybe multiple, if multiple tasks were in WF)
            String[] ansibleResultFromWfArray = OrchestrationUtils.getAnsibleResults(workflowResponse);

            for(String ansibleResultFromWf : ansibleResultFromWfArray) {

                // result may contain multiple JSON objects:
                for(String ansibleResult:
                    OrchestrationUtils.parseObjectList(ansibleResultFromWf)) {

                    // see if it refers to an Operation with ViPR Tasks
                    ViprOperation viprOperation = OrchestrationUtils.parseViprTasks(ansibleResult);
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
                    // else see if it's a list of resources
                    AffectedResource[] rsrcList = null;
                    if (viprOperation == null) {
                        rsrcList = OrchestrationUtils.parseResourceList(ansibleResult);
                        if(rsrcList != null) {
                            OrchestrationUtils.updateAffectedResources(rsrcList);
                        }
                    }
                    // else see if it's a status message for order log
                    OeStatusMessage oeStatusMessage = null;
                    if ((viprOperation == null) && (rsrcList == null)) {
                        oeStatusMessage = OrchestrationUtils.parseOeStatusMessage(ansibleResult);
                        if((oeStatusMessage != null) &&
                                !messagesLogged.contains(oeStatusMessage.getMessage())){
                            ExecutionUtils.currentContext().logInfo(oeStatusMessage.getMessage());
                            messagesLogged.add(oeStatusMessage.getMessage());
                        }
                    }
                    // if neither, log result
                    if ((viprOperation == null) && (rsrcList == null) && (oeStatusMessage == null) ) {
                        ExecutionUtils.currentContext().logInfo("An orchestration engine Workflow " +
                                "result was not recognized as a ViPR Task or " +
                                "list of Affected Resources in ViPR: " + ansibleResult);
                    }
                }
            }
        } while (OrchestrationUtils.isWorkflowRunning(workflowResponse) && !timedOut);
        OrchestrationUtils.waitForTasks(tasksStartedByOe,getClient());
        return workflowResponse;
    }

    private String getOeWorkflowResponse(String workflowId) {
        OrchestrationUtils.sleep(OE_WORKFLOW_CHECK_INTERVAL);
        if( OrchestrationUtils.isTimedOut(++intervals) ) {
            ExecutionUtils.currentContext().logError("Orchestration Engine Workflow " +
                    workflowId + " timed out.");
            timedOut = true;
        }      
        return getAnsibleResult(); 
    }

    private String getAnsibleResult() {
        // TODO Auto-generated method stub
        
        // TODO: RETRIEVE ANSIBLE RESULT FILE (SO WE CAN PARSE FOR MESSAGES AND STATUS)
        
        return null;
    }

    private String startWorkflow() {

        //String postBody = OrchestrationUtils.makePostBody(params,workflowName,playbookNameList);
        //String workflowResponse = makeRestCall(apiWorkflowUri, postBody);

        String workflowResponse = OrchestrationUtils.startAnsible(params,workflowName,playbookNameList);      
        
        String workflowId = OrchestrationUtils.getWorkflowId(workflowResponse);
        ExecutionUtils.currentContext().logInfo("Started Workflow on Orchestration Engine.  " +
                "ID " + workflowId);
        return workflowId;
    }

}
