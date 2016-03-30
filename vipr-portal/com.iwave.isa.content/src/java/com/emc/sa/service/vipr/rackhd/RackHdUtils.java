package com.emc.sa.service.vipr.rackhd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.ViPRExecutionUtils;
import com.emc.sa.service.vipr.rackhd.gson.AffectedResource;
import com.emc.sa.service.vipr.rackhd.gson.Node;
import com.emc.sa.service.vipr.rackhd.gson.Workflow;
import com.emc.sa.service.vipr.rackhd.gson.Task;
import com.emc.sa.service.vipr.rackhd.gson.WorkflowDefinition;
import com.emc.sa.service.vipr.rackhd.tasks.RackHdGetWorkflowTasks;
import com.emc.storageos.db.client.model.StringSet;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

//TODO: move log messages to separate file (for internationalization)

public class RackHdUtils {

    private static final int RACKHD_WORKFLOW_CHECK_INTERVAL = 10; // secs
    private static final int RACKHD_WORKFLOW_CHECK_TIMEOUT = 600; // secs
    
    // TODO: read these possible values from workflow response?
    private static final String WORKFLOW_VALID_STATE = "valid";
    private static final String WORKFLOW_TIMEOUT_STATE = "timeout";
    private static final String WORKFLOW_CANCELLED_STATE = "cancelled";
    private static final String WORKFLOW_FAILED_STATE =  "failed";
    private static final String WORKFLOW_COMPLETE_STATE = "complete";
    
    private static final Gson gson = new Gson();
    
    public static Workflow getWorkflowObjFromJson(String workflowResponse){
        return gson.fromJson(workflowResponse,Workflow.class);
    }
    
    public static boolean isTimedOut(int intervals) {
        return (intervals * RACKHD_WORKFLOW_CHECK_INTERVAL) >= 
                RACKHD_WORKFLOW_CHECK_TIMEOUT;
    }

    public static String makePostBody(Map<String, Object> params, 
            String workflowName, List<String> playbookNameList) {
        StringBuffer postBody = new StringBuffer();
        postBody.append("{\"name\": \"" + workflowName + "\"");
        if( !params.isEmpty() || 
                ((playbookNameList != null) && !playbookNameList.isEmpty()) ) {
            postBody.append(",\"options\":{");
            
            // assign playbooks to tasks
            //TODO: check that tasks are Ansible Tasks and take playbook
            //TODO: eventually, in UI, allow user to assign playbooks to tasks in WF
            if((playbookNameList != null) && !playbookNameList.isEmpty()){
                List<Task> workflowTasks = getTasksForWorkflow(workflowName);
                int shortestListLength = workflowTasks.size() < playbookNameList.size() ?
                    workflowTasks.size() : playbookNameList.size();
                for(int i=0; i < shortestListLength; i++) {
                    postBody.append("\"" + workflowTasks.get(i).getLabel() + "\":{");
                    postBody.append("\"playbook\":\"" + playbookNameList.get(i).trim() + "\"");   
                    postBody.append("},");
                } 
                postBody.deleteCharAt(postBody.length()-1); // remove last comma
            }    
            if(!params.isEmpty()) {
                postBody.append(",\"defaults\":{");
                postBody.append("\"vars\":{");
                for(String varName : params.keySet()) {
                    postBody.append("\"" + varName + "\":\"" +
                            params.get(varName).toString() + "\",");
                }
                postBody.deleteCharAt(postBody.length()-1); // remove last comma
                postBody.append("}");  // end vars
                postBody.append("}");  // end defaults
            }
            postBody.append("}"); // end options
        }
        postBody.append("}");  // end workflow

        return postBody.toString();
    }
    
    private static List<Task> getTasksForWorkflow(String workflowName) {
        return ViPRExecutionUtils.
                execute(new RackHdGetWorkflowTasks(workflowName));
    }

    public static boolean isWorkflowValid(String workflowResponse) {
        Workflow rackHdWorkflow = 
                getWorkflowObjFromJson(workflowResponse);  
        return rackHdWorkflow.get_status().
                equalsIgnoreCase(WORKFLOW_VALID_STATE);
    }

    public static String getAnyNode(String responseString) {
        Node[] rackHdNodeArray = gson.fromJson(responseString,Node[].class);    
        return getComputeNodeId(rackHdNodeArray);  
    }

    public static String getComputeNodeId(Node[] nodeArray) {
        List<String> nodeIds = new ArrayList<>();
        for(Node rackHdNode : nodeArray) {
            if(rackHdNode.isComputeNode()) {
                nodeIds.add(rackHdNode.getId());  
            }
        }
        if(nodeIds.isEmpty()) {
            return null;
        }
        return nodeIds.get((int)(Math.random() * nodeIds.size()));
    }
    
    public static void sleep(int i) {
        try {
            Thread.sleep(i*1000);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        } 
    }

    public static boolean isWorkflowFailed(String workflowResponse) {
        Workflow rackHdWorkflow = 
                getWorkflowObjFromJson(workflowResponse);  
        String status = rackHdWorkflow.get_status();   
        // TODO: get failure states from workflow response
        return status.equalsIgnoreCase(WORKFLOW_FAILED_STATE) || 
                status.equalsIgnoreCase(WORKFLOW_TIMEOUT_STATE) || 
                status.equalsIgnoreCase(WORKFLOW_CANCELLED_STATE);
    }

    public static String checkForWorkflowFailed(String workflowResponse) {
        StringBuffer errMsg = new StringBuffer();
        if(isWorkflowFailed(workflowResponse)) { 
            Workflow workflowObj = getWorkflowObjFromJson(workflowResponse);
            errMsg.append("Workflow failed.  Status is '" +
                    workflowObj.get_status() +
                    "'.  Message from RackHD was: ");
            for(String ansibleResult : workflowObj.getContext().getAnsibleResultFile()) {
                errMsg.append(ansibleResult + "  ");
            }
        }   
        return errMsg.toString();
    }
    
/**    private static Map<String,String> getFinishedTasksResults(String workflowResponse) {
        Map<String,String> results = new HashMap<>();
        for(FinishedTask t : 
            getWorkflowObjFromJson(workflowResponse).getFinishedTasks()) {
            String taskResult = t.getContext().getAnsibleResultFile();
            if(taskResult != null) {
                results.put(  // tasks may have same name
                        t.getFriendlyName() + "(" + t.getInstanceId() + ")",
                        taskResult);
            }
        }
        return results;
    }
**/
    public static boolean isWorkflowComplete(String workflowResponse) {
        Workflow rackHdWorkflow = 
                getWorkflowObjFromJson(workflowResponse);       
        return rackHdWorkflow.getCompleteEventString().
                equalsIgnoreCase(WORKFLOW_COMPLETE_STATE);
    }

    public static String getWorkflowId(String workflowResponse) {
        Workflow rackHdWorkflow = getWorkflowObjFromJson(workflowResponse);  
        return rackHdWorkflow.getInstanceId();
    }

    public static List<String> updateAffectedResources(String workflowResponse) {
        return updateAffectedResources(workflowResponse,null);
    }

    public static List<String> updateAffectedResources(String workflowResponse,
            List<String> finishedTaskIds) {
        Workflow wf = RackHdUtils.getWorkflowObjFromJson(workflowResponse);  
        String[] resultJsons =  wf.getContext().getAnsibleResultFile();
        for(String resultJson : resultJsons) {
            if( resultJson == null ) {
                break; // skip if no results
            }
            try {
                AffectedResource[] affectedResources = 
                        gson.fromJson(resultJson,AffectedResource[].class);
                for(AffectedResource rsrc:affectedResources ) {
                    StringSet currentResources = ExecutionUtils.
                            currentContext().getExecutionState().
                            getAffectedResources();
                    if(!currentResources.contains(rsrc.getValue())) {  
                        ExecutionUtils.currentContext().logInfo("Adding " +
                                " completed resource '" + rsrc.getKey() + "'");
                        currentResources.add(rsrc.getValue());
                    }
                }
            } catch(JsonSyntaxException e) {
                // ignore syntax exceptions, if not valid JSON
                // there may normally be unexpected results in the response
                ExecutionUtils.currentContext().logInfo("RackHD Workflow " +
                        "result was not valid JSON" + resultJson);
            }
        }
        return finishedTaskIds;
    }

    public static List<Task> getTasksInWorkflowDefinition(String workflowJson) {
        WorkflowDefinition wf = gson.fromJson(workflowJson,WorkflowDefinition.class);
        // should return array of one workflow
        return Arrays.asList(wf.getTasks());
    }
}
