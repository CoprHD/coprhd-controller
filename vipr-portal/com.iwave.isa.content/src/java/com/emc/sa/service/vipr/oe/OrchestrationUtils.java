package com.emc.sa.service.vipr.oe;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.ViPRExecutionUtils;
import com.emc.sa.service.vipr.oe.gson.AffectedResource;
import com.emc.sa.service.vipr.oe.gson.Node;
import com.emc.sa.service.vipr.oe.gson.OeStatusMessage;
import com.emc.sa.service.vipr.oe.gson.Task;
import com.emc.sa.service.vipr.oe.gson.ViprOperation;
import com.emc.sa.service.vipr.oe.gson.ViprTask;
import com.emc.sa.service.vipr.oe.gson.Workflow;
import com.emc.sa.service.vipr.oe.gson.WorkflowDefinition;
import com.emc.sa.service.vipr.oe.tasks.OrchestrationGetWorkflowTasks;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.oe.api.restapi.OrchestrationEngineRestClient;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.model.catalog.AssetOption;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.jersey.api.client.ClientResponse;

//TODO: move log messages to separate file (for internationalization)

public class OrchestrationUtils {

    //TODO: externalize these values:
    private static final int OE_WORKFLOW_CHECK_INTERVAL = 10; // secs
    private static final int OE_WORKFLOW_CHECK_TIMEOUT = 600; // secs
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
    public static final String OE_SCHEME = "http"; // include, else URI.resolve(..) fails
    public static final String OE_SERVER = "localhost";
    public static final String OE_SERVERPORT = "9090";

    public static final String OE_API_NODE = "/api/1.1/nodes";

    private static final Gson gson = new Gson();

    public static Workflow getWorkflowObjFromJson(String workflowResponse){
        return gson.fromJson(workflowResponse,Workflow.class);
    }

    public static boolean isTimedOut(int intervals) {
        return (intervals * OE_WORKFLOW_CHECK_INTERVAL) >= 
                OE_WORKFLOW_CHECK_TIMEOUT;
    }

    public static String makePostBody(Map<String, Object> params, 
            String workflowName, List<String> playbookNameList) {
        // TODO: refactor to use GSON libs to wwrite JSON (instead of writing JSON manually)
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
                if(postBody.charAt(postBody.length()-1) == '}') {
                    postBody.append(",");
                }
                postBody.append("\"defaults\":{");
                postBody.append("\"vars\":{");
                for(String varName : params.keySet()) { 
                    // assuming param is String or String[]
                    if(params.get(varName).getClass().isArray()){	
                        String[] paramArray = (String[])params.get(varName);
                        StringBuffer sb = new StringBuffer();
                        sb.append("[");
                        for(int i=0; i<paramArray.length; i++){
                            sb.append("\"" + paramArray[i] + "\",");
                        }
                        sb.deleteCharAt(sb.length()-1);
                        sb.append("]");
                        postBody.append("\"" + varName + "\":" +
                                sb.toString() + ",");
                    }
                    else {
                        postBody.append("\"" + varName + "\":\"" +
                                params.get(varName).toString() + "\",");
                    }
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
                execute(new OrchestrationGetWorkflowTasks(workflowName));
    }

    public static boolean isWorkflowValid(String workflowResponse) {
        Workflow oeWorkflow = 
                getWorkflowObjFromJson(workflowResponse);  
        return oeWorkflow.get_status().
                equalsIgnoreCase(WORKFLOW_VALID_STATE);
    }

    public static String getAnyNode(String responseString) {
        Node[] oeNodeArray = gson.fromJson(responseString,Node[].class);    
        return getComputeNodeId(oeNodeArray);  
    }

    public static String getComputeNodeId(Node[] nodeArray) {
        List<String> nodeIds = new ArrayList<>();
        for(Node oeNode : nodeArray) {
            if(oeNode.isComputeNode()) {
                nodeIds.add(oeNode.getId());  
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
        Workflow oeWorkflow = 
                getWorkflowObjFromJson(workflowResponse);  
        
        //TODO: IF NOT RACKHD REPONSE, TRY PARSING AS ANSIBLE RESPONSE
        
        String status = oeWorkflow.get_status();   
        return status.equalsIgnoreCase(WORKFLOW_PENDING_STATE);
    }

    public static boolean isWorkflowFailed(String workflowResponse) {
        Workflow oeWorkflow = 
                getWorkflowObjFromJson(workflowResponse);  
        String status = oeWorkflow.get_status();   
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
                    "'.  Message from Orchestration Engine was: ");
            for(String ansibleResult : workflowObj.getContext().getAnsibleResultFile()) {
                errMsg.append(ansibleResult + "  ");
            }
        }   
        return errMsg.toString();
    }

    public static String getWorkflowId(String workflowResponse) {
        Workflow oeWorkflow = getWorkflowObjFromJson(workflowResponse);  
        return oeWorkflow.getInstanceId();
    }

    public static String[] getAnsibleResults(String workflowResponse) {
        Workflow wf = OrchestrationUtils.getWorkflowObjFromJson(workflowResponse);  
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
        try {  // try parsing result as a ViPR Task(s)
            // see if result contains array of Tasks
            ViprOperation o = gson.fromJson(ansibleResult,ViprOperation.class);
            if(o.isValid()) {
                return o;
            }
            // see if response was a single Task
            ViprTask t = gson.fromJson(ansibleResult,ViprTask.class);
            if(t.isValid()) {
                return new ViprOperation(t);
            }
            return null;
        } catch(JsonSyntaxException e) {
            return null;
        }
    }

    public static String[] parseObjectList(String objectList) {
        // ansible result could be a single object, or multiple objects (comma separated)
        // add brackets to form array and parse
        List<String> results = new ArrayList<>();
        try {
            Object[] oList =  gson.fromJson("["+objectList+"]",Object[].class);
            for(Object o : oList) {
                if(o != null) {
                    results.add(gson.toJson(o));
                }
            }
            return results.toArray(new String[results.size()]);
        } catch(JsonSyntaxException e) {
            return new String[] {objectList};
        }
    }

    public static AffectedResource[] parseResourceList(String taskResult) {
        try {
            AffectedResource[] rArray = gson.fromJson(taskResult,AffectedResource[].class);
            for(AffectedResource r : rArray) {
                if(!r.isValid()) {
                    return null;
                }
            }
            return rArray;
        } catch(JsonSyntaxException e) {
            return null;
        }
    }

    public static OeStatusMessage parseOeStatusMessage(String ansibleResult) {
        OeStatusMessage oeStatusMessage = null;
        try {
            oeStatusMessage = gson.fromJson(ansibleResult,OeStatusMessage.class);
        } catch(JsonSyntaxException e) {
            return null;
        }
        return oeStatusMessage.isValid() ? oeStatusMessage : null;
    }

    public static List<TaskResourceRep> locateTasksInVipr(ViprOperation viprOperation, ViPRCoreClient client) {
        // given a response from OE representing an Operation with Tasks started by 
        // a OE workflow task, find corresponding tasks running in ViPR
        // (this is useful in cases like: a volume(s) was created from a OE workflow
        //  using the ViPR API, and now you want to find the tasks running in ViPR
        //  that corresponds to it/them.)  
        try {
            return client.tasks().getByIds(viprOperation.getTaskIds());
        }
        catch (URISyntaxException e) {
            ExecutionUtils.currentContext().logInfo("Warning: there was a " +
                    "problem locating tasks in ViPR that were initiated in " +
                    "the Orchestration Engine.  (Task IDs from OE are not valid.  " + 
                    e.getMessage());
            return new ArrayList<TaskResourceRep>();
        }  
    }

    public static void waitForTasks(List<URI> tasksStartedByOe, ViPRCoreClient client) {
        if( tasksStartedByOe.isEmpty()) {
            return;
        }  
        ExecutionUtils.currentContext().logInfo("Orchestration Engine Workflow complete.  " +
                "Waiting for Tasks in ViPR to finish that were started by " +
                "Orchestration Engine workflow.");

        long startTime = System.currentTimeMillis();
        boolean allTasksDone = false;

        while(!allTasksDone) {
            allTasksDone = true;
            for(URI taskId : tasksStartedByOe) {              
                String state = client.tasks().get(taskId).getState();
                if(state.equals("error")) {
                    throw new IllegalStateException("One or more tasks " +
                            " started by Orchestration Engine reported an error.");
                }
                if(!state.equals("ready")) {
                    allTasksDone = false;
                    break;
                }
            }
            OrchestrationUtils.sleep(TASK_CHECK_INTERVAL);

            if( (System.currentTimeMillis() - startTime)
                    > TASK_CHECK_TIMEOUT*60*1000 ) {
                throw new IllegalStateException("Task(s) started by Orchestration Engine " +
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
        return assetOptionList;
    }

    public static String makeRestCall(String uriString, OrchestrationEngineRestClient restClient) {
        return makeRestCall(uriString,null,restClient);
    }

    public static String makeRestCall(String uriString, String postBody,
            OrchestrationEngineRestClient restClient) {

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
                    "from Orchestration Engine for: " + uriString + " :: "+ e.getMessage());
            e.printStackTrace();
        }
        return responseString;
    }

    //TODO: make startAnsible() so it works in both these cases (for Service & Provider)
    
    public static String startAnsible(Map<String, Object> params, String workflowName, String playbookNameList) {
        // TODO Auto-generated method stub
        return null;
    }

    public static String startAnsible2(Map<String, String> parentAssetParams, String thisAssetType,
            String thisAssetType2) {
        // TODO Auto-generated method stub
        return null;
        
    }

    public static String getAnsibleStatus(String workflowResponse) {
        //TODO: use workflowResponse to locate ansible file with results, and get status and/or contents of result file(s)
        return null;
    }
}
