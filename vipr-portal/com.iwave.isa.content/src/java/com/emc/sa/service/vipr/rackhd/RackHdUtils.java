package com.emc.sa.service.vipr.rackhd;

import java.util.Map;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.rackhd.gson.RackHdNode;
import com.emc.sa.service.vipr.rackhd.gson.RackHdWorkflow;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
        ExecutionUtils.currentContext().logInfo("RackHD workflow status " +
                "(isValid?) =" + rackHdWorkflow.get_status());
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
        ExecutionUtils.currentContext().logInfo("RackHD workflow status " +
                "(isFailed?)=" + rackHdWorkflow.get_status());
        String status = rackHdWorkflow.get_status();   
        // TODO: get failure states from workflow response
        return status.equalsIgnoreCase(WORKFLOW_FAILED_STATE) || 
                status.equalsIgnoreCase(WORKFLOW_TIMEOUT_STATE) || 
                status.equalsIgnoreCase(WORKFLOW_CANCELLED_STATE);
    }

    public static void checkForWorkflowFailed(String workflowResponse) {
        if(isWorkflowFailed(workflowResponse)) { 
            String errMsg = "Workflow failed.  Status is '" +
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
            throw new IllegalStateException(errMsg);
        }
    }
    
    public static boolean isWorkflowComplete(String workflowResponse) {
        RackHdWorkflow rackHdWorkflow = 
                getWorkflowObjFromJson(workflowResponse);  
        ExecutionUtils.currentContext().logInfo("RackHD workflow complete=" + 
                rackHdWorkflow.getCompleteEventString());       
        return rackHdWorkflow.getCompleteEventString().
                equalsIgnoreCase(WORKFLOW_COMPLETE_STATE);
    }

    public static String getWorkflowTaskId(String workflowResponse) {
        RackHdWorkflow rackHdWorkflow = getWorkflowObjFromJson(workflowResponse);  
        return rackHdWorkflow.getId();
    }

}
