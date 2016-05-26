package com.emc.sa.service.vipr.rackhd;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.util.log.Log;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.ViPRExecutionUtils;
import com.emc.sa.service.vipr.rackhd.gson.AffectedResource;
import com.emc.sa.service.vipr.rackhd.gson.Node;
import com.emc.sa.service.vipr.rackhd.gson.Workflow;
import com.emc.sa.service.vipr.rackhd.gson.Task;
import com.emc.sa.service.vipr.rackhd.gson.ViprTask;
import com.emc.sa.service.vipr.rackhd.gson.ViprOperation;
import com.emc.sa.service.vipr.rackhd.gson.WorkflowDefinition;
import com.emc.sa.service.vipr.rackhd.tasks.RackHdGetWorkflowTasks;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.rackhd.api.restapi.RackHdRestClient;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.model.catalog.AssetOption;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.jersey.api.client.ClientResponse;

//TODO: move log messages to separate file (for internationalization)

public class RackHdUtils {

    //TODO: externalize these values:
    private static final int RACKHD_WORKFLOW_CHECK_INTERVAL = 10; // secs
    private static final int RACKHD_WORKFLOW_CHECK_TIMEOUT = 600; // secs
    private static final int TASK_CHECK_TIMEOUT = 3600;  // mins
    private static final int TASK_CHECK_INTERVAL = 10; // secs

    // TODO: read these possible values from workflow response?
    private static final String WORKFLOW_VALID_STATE = "valid";
    private static final String WORKFLOW_TIMEOUT_STATE = "timeout";
    private static final String WORKFLOW_CANCELLED_STATE = "cancelled";
    private static final String WORKFLOW_FAILED_STATE =  "failed";
    private static final String WORKFLOW_PENDING_STATE = "pending";

    // TODO: move these hard-coded strings out
    public static final String USER = "root";
    public static final String PASSWORD = "ChangeMe1!";
    public static final String RACKHDSCHEME = "http"; // include, else URI.resolve(..) fails
    public static final String RACKHDSERVER = "localhost";
    public static final String RACKHDSERVERPORT = "8080";

    public static final String RACKHD_API_NODE = "/api/1.1/nodes";

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

    public static boolean isWorkflowRunning(String workflowResponse) {
        Workflow rackHdWorkflow = 
                getWorkflowObjFromJson(workflowResponse);  
        String status = rackHdWorkflow.get_status();   
        return status.equalsIgnoreCase(WORKFLOW_PENDING_STATE);
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

    public static String getWorkflowId(String workflowResponse) {
        Workflow rackHdWorkflow = getWorkflowObjFromJson(workflowResponse);  
        return rackHdWorkflow.getInstanceId();
    }

    public static String[] getAnsibleResults(String workflowResponse) {
        Workflow wf = RackHdUtils.getWorkflowObjFromJson(workflowResponse);  
        return  wf.getContext().getAnsibleResultFile();    
    }

    public static void updateAffectedResources(ViprOperation viprOperation) {
        if(viprOperation != null) {
            StringSet currentResources = ExecutionUtils.currentContext().
                    getExecutionState().getAffectedResources();
            for(ViprTask viprTask:viprOperation.getTask()) { 
                if(!currentResources.contains(viprTask.getResource().getId())) {
                    currentResources.add(viprTask.getResource().getId());
                }
            }
        }
    }

    public static void updateAffectedResources(AffectedResource[] affectedResources) {
        if(affectedResources != null) {
            StringSet currentResources = ExecutionUtils.currentContext().
                    getExecutionState().getAffectedResources();
            for(AffectedResource affectedResource : affectedResources) {
                if(!currentResources.contains(affectedResource.getId())) {  
                    ExecutionUtils.currentContext().logInfo("Adding " +
                            " completed resource '" + 
                            affectedResource.getName() + "'");
                    currentResources.add(affectedResource.getId());
                }
            }
        }
    }

    public static List<Task> getTasksInWorkflowDefinition(String workflowJson) {
        WorkflowDefinition wf = gson.fromJson(workflowJson,WorkflowDefinition.class);
        // should return array of one workflow
        return Arrays.asList(wf.getTasks());
    }

    public static ViprOperation parseViprTasks(String ansibleResult) {
        try {  // try parsing result as a ViPR Task
            return gson.fromJson(ansibleResult,ViprOperation.class);
        } catch(JsonSyntaxException e) {
            return null;
        }
    }

    public static AffectedResource[] parseResourceList(String taskResult) {
        try { 
            return gson.fromJson(taskResult,AffectedResource[].class);
        } catch(JsonSyntaxException e) {
            return null;
        }
    }

    public static List<TaskResourceRep> locateTasksInVipr(ViprOperation viprOperation, ViPRCoreClient client) {
        // given a response from RackHD representing an Operation with Tasks started by 
        // a RackHD workflow task, find corresponding tasks running in ViPR
        // (this is useful in cases like: a volume(s) was created from a RackHD workflow
        //  using the ViPR API, and now you want to find the tasks running in ViPR
        //  that corresponds to it/them.)  
        try {
            return client.tasks().getByIds(viprOperation.getTaskIds());
        }
        catch (URISyntaxException e) {
            ExecutionUtils.currentContext().logInfo("Warning: there was a " +
                    "problem locating tasks in ViPR that were initiated in " +
                    "RackHD.  (Task IDs from RackHD are not valid.  " + 
                    e.getMessage());
            return new ArrayList<TaskResourceRep>();
        }  
    }

    public static void waitForTasks(List<URI> tasksStartedByRackHd, ViPRCoreClient client) {
        if( tasksStartedByRackHd.isEmpty()) {
            return;
        }  
        ExecutionUtils.currentContext().logInfo("RackHD Workflow complete.  " +
                "Waiting for Tasks in ViPR to finish that were started by " +
                "RackHD workflow.");
        
        long startTime = System.currentTimeMillis();
        boolean allTasksDone = false;

        while(!allTasksDone) {
            allTasksDone = true;
            for(URI taskId : tasksStartedByRackHd) {              
                String state = client.tasks().get(taskId).getState();
                if(state.equals("error")) {
                    throw new IllegalStateException("One or more tasks " +
                            " started by RackHD reported an error.");
                }
                if(!state.equals("ready")) {
                    allTasksDone = false;
                    break;
                }
            }
            RackHdUtils.sleep(TASK_CHECK_INTERVAL);

            if( (System.currentTimeMillis() - startTime)
                    > TASK_CHECK_TIMEOUT*60*1000 ) {
                throw new IllegalStateException("Task(s) started by RackHD " +
                        "timed out.");
            }
        }
    }
    public static List<AssetOption> getNodeOptions(String workflowJson) { 
        List<AssetOption> assetOptionList = new ArrayList<>(); 
        Node[] nodes =
                gson.fromJson(workflowJson,Node[].class);
        if( (nodes != null) && (nodes.length > 0) ) {
            List<Node> nodeList = Arrays.asList(nodes);
            for(Node node: nodeList) {
                if(node.isComputeNode()) {
                    assetOptionList.add(new AssetOption(node.getId(),
                            node.getName()));
                }
            }
        }
        assetOptionList.add(new AssetOption("null","None"));
        return assetOptionList;
    }

    public static String makeRestCall(String uriString, RackHdRestClient restClient) {
        return makeRestCall(uriString,null,restClient);
    }

    public static String makeRestCall(String uriString, String postBody,
            RackHdRestClient restClient) {

        ClientResponse response = null;
        if(postBody == null) {
            response = restClient.get(URI.create(uriString));
        } else {
            response = restClient.post(URI.create(uriString),postBody);
        }

        String responseString = null;
        try {
            responseString = IOUtils.toString(response.getEntityInputStream(),"UTF-8");
        } catch (IOException e) {
            ExecutionUtils.currentContext().logError("Error getting response " +
                    "from RackHD for: " + uriString + " :: "+ e.getMessage());
            e.printStackTrace();
        }
        return responseString;
    }
}
