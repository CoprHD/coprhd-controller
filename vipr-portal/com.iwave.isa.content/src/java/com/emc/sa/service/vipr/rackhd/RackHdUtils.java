package com.emc.sa.service.vipr.rackhd;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.rackhd.gson.AffectedResource;
import com.emc.sa.service.vipr.rackhd.gson.FinishedTask;
import com.emc.sa.service.vipr.rackhd.gson.RackHdNode;
import com.emc.sa.service.vipr.rackhd.gson.RackHdWorkflow;
import com.emc.storageos.db.client.model.StringSet;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

//TODO: move log messages to separate file (for internationalization)

public class RackHdUtils {

    private static final int RACKHD_WORKFLOW_CHECK_INTERVAL = 10; // secs
    private static final int RACKHD_WORKFLOW_CHECK_TIMEOUT = 300; // secs
    
    // TODO: read these possible values from workflow response?
    private static final String WORKFLOW_VALID_STATE = "valid";
    private static final String WORKFLOW_TIMEOUT_STATE = "timeout";
    private static final String WORKFLOW_CANCELLED_STATE = "cancelled";
    private static final String WORKFLOW_FAILED_STATE =  "failed";
    private static final String WORKFLOW_COMPLETE_STATE = "complete";
    
    private static final Gson gson = new Gson();
    
    public static RackHdWorkflow getWorkflowObjFromJson(String workflowResponse){
        return gson.fromJson(workflowResponse,RackHdWorkflow.class);
    }
    
    public static boolean isTimedOut(int intervals) {
        return (intervals * RACKHD_WORKFLOW_CHECK_INTERVAL) >= 
                RACKHD_WORKFLOW_CHECK_TIMEOUT;
    }

    public static String makePostBody(Map<String, Object> params, 
            String workflowName, String playbookName) {
        StringBuffer postBody = new StringBuffer();
        postBody.append("{\"name\": \"" + workflowName + "\"");
        if( !params.isEmpty() || (playbookName != null) ) {
            postBody.append(",\"options\":{\"defaults\":{");
            if(playbookName != null) {
                postBody.append("\"playbook\":\"" + playbookName + "\"");
            }
            if(!params.isEmpty()) {
                postBody.append(",\"vars\":{");
                for(String varName : params.keySet()) {
                    postBody.append("\"" + varName + "\":" +
                            params.get(varName).toString() + ",");
                }
                postBody.deleteCharAt(postBody.length()-1); // remove last comma
                postBody.append("}");
            }
            postBody.append("}}");
        }
        postBody.append("}");  

        return postBody.toString();
    }
    
    public static boolean isWorkflowValid(String workflowResponse) {
        RackHdWorkflow rackHdWorkflow = 
                getWorkflowObjFromJson(workflowResponse);  
        return rackHdWorkflow.get_status().
                equalsIgnoreCase(WORKFLOW_VALID_STATE);
    }

    public static String getAnyNode(String responseString) {
        RackHdNode[] rackHdNodeArray = gson.fromJson(responseString,RackHdNode[].class);    
        return getComputeNodeId(rackHdNodeArray);  
    }

    public static String getComputeNodeId(RackHdNode[] nodeArray) {
        for(RackHdNode rackHdNode : nodeArray)
            if(rackHdNode.isComputeNode())
                return rackHdNode.getId();  
        return null;
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
        RackHdWorkflow rackHdWorkflow = 
                getWorkflowObjFromJson(workflowResponse);  
        String status = rackHdWorkflow.get_status();   
        // TODO: get failure states from workflow response
        return status.equalsIgnoreCase(WORKFLOW_FAILED_STATE) || 
                status.equalsIgnoreCase(WORKFLOW_TIMEOUT_STATE) || 
                status.equalsIgnoreCase(WORKFLOW_CANCELLED_STATE);
    }

    public static String checkForWorkflowFailed(String workflowResponse) {
        String errMsg = null;
        
        if(isWorkflowFailed(workflowResponse)) { 
            errMsg = "Workflow failed.  Status is '" +
                    getWorkflowObjFromJson(workflowResponse).get_status() +
                    "'.  Message from RackHD was: '" +
                    getWorkflowObjFromJson(workflowResponse).getContext().
                    getAnsibleResultFile() + "'.";
            if( StringUtils.isBlank(getWorkflowObjFromJson(workflowResponse).getContext().
                    getAnsibleResultFile()) ) { 
                JsonParser parser = new JsonParser();
                JsonObject json = parser.parse(workflowResponse).getAsJsonObject();
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String prettyJson = gson.toJson(json);
                errMsg = errMsg + "  Workflow details: " + prettyJson;
            }
        }
        return errMsg;
    }
    
    public static boolean isWorkflowComplete(String workflowResponse) {
        RackHdWorkflow rackHdWorkflow = 
                getWorkflowObjFromJson(workflowResponse);       
        return rackHdWorkflow.getCompleteEventString().
                equalsIgnoreCase(WORKFLOW_COMPLETE_STATE);
    }

    public static String getWorkflowId(String workflowResponse) {
        RackHdWorkflow rackHdWorkflow = getWorkflowObjFromJson(workflowResponse);  
        return rackHdWorkflow.getId();
    }

    public static List<String> updateAffectedResources(String workflowResponse) {
        return updateAffectedResources(workflowResponse,null);
    }
    
    public static List<String> updateAffectedResources(String workflowResponse,
            List<String> finishedTasks) {
        RackHdWorkflow wf = 
               RackHdUtils.getWorkflowObjFromJson(workflowResponse);  
       for(FinishedTask task : Arrays.asList(wf.getFinishedTasks())) {
           if(finishedTasks.contains(task.getInstanceId())) {
               continue;  // skip if already added resources for this task
           }
           String resultJson = 
                   task.getJob().getContext().getAnsibleResultFile();
           ExecutionUtils.currentContext().logInfo("RackHD Workflow " +
                   "result: " + resultJson);
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
                       finishedTasks.add(task.getInstanceId());
                   }
               }
           } catch(JsonSyntaxException e) {
               // ignore syntax exceptions, if not valid JSON
               // there may be an error msg in the response
               ExecutionUtils.currentContext().logInfo("RackHD Workflow " +
                       "result was not valid JSON" + resultJson);
           }
       }
       return finishedTasks;
   }
}
