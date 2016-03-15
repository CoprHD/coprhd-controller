/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.rackhd;

import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.util.DiskSizeConversionUtils;
import com.emc.storageos.db.client.util.SizeUtil;
import com.emc.storageos.rackhd.api.restapi.RackHdRestClient;
import com.emc.storageos.rackhd.api.restapi.RackHdRestClientFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

    private RackHdRestClient restClient;

    //TODO: much of this code is also in RackHdProvider - factor code into utils class

    //TODO: move these hard-coded strings out
    private static final String USER = "root";
    private static final String PASSWORD = "ChangeMe1!";
    private static final String RACKHDSCHEME = "http"; // include, else URI.resolve(..) fails
    private static final String RACKHDSERVER = "lgloc189.lss.emc.com";
    private static final String RACKHDSERVERPORT = "8080";

    // Name of parameter containing workflow name:
    private static final String WORKFLOW_PARAM_NAME = "Workflow"; 

    // Name of parameter containing playbook name:
    private static final String PLAYBOOK_PARAM_NAME = "Playbook"; 

    private static final String RACKHD_API_NODES = "/api/1.1/nodes"; //include leading slash

    private static final String RACKHD_API_WORKFLOWS = "/api/1.1/workflows";
    private static final int RACKHD_WORKFLOW_CHECK_INTERVAL = 10; // secs
    private static final int RACKHD_WORKFLOW_CHECK_TIMEOUT = 300; // secs

    // TDOD: read these possible values from workflow response?
    private static final String WORKFLOW_SUCCESS_STATE =  "succeeded";
    private static final String WORKFLOW_FAILED_STATE =  "failed";
    private static final String WORKFLOW_COMPLETE_STATE = "complete";
    private static final String WORKFLOW_TIMEOUT_STATE = "timeout";
    private static final String WORKFLOW_CANCELLED_STATE = "cancelled";
    private static final String WORKFLOW_VALID_STATE = "valid";

    // JSON converter
    private static Gson gson = null;

    public RackHdService() {
        RackHdRestClientFactory factory = new RackHdRestClientFactory();
        factory.setMaxConnections(100);
        factory.setMaxConnectionsPerHost(100);
        factory.setNeedCertificateManager(false);
        factory.setSocketConnectionTimeoutMs(3600000);
        factory.setConnectionTimeoutMs(3600000);
        factory.init();
        String endpoint = RACKHDSCHEME + "://" + RACKHDSERVER + ":" + RACKHDSERVERPORT;
        restClient = (RackHdRestClient) factory.getRESTClient(URI.create(endpoint), USER, PASSWORD, true);
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
        String nodeListResponse = makeRestCall(RACKHD_API_NODES);
        String nodeId = getAnyNode(nodeListResponse);         
        info("MENDES: Will execute against node ID " + nodeId);

        String apiWorkflowUri = "/api/1.1/nodes/" + nodeId + "/workflows";


        String workflowResponse = 
                makeRestCall(apiWorkflowUri,makePostBody());

        // Get results (wait for RackHD workflow to complete)
        int intervals = 0;
        while ( !isWorkflowComplete(workflowResponse) ||  // does complete flag matter?
                isWorkflowValid(workflowResponse) ) { // status not updated from 'valid' even when complete?!
            sleep(RACKHD_WORKFLOW_CHECK_INTERVAL);
            workflowResponse = makeRestCall(RACKHD_API_WORKFLOWS + "/" + 
                    getWorkflowTaskId(workflowResponse));
            updateAffectedResources(workflowResponse);
            //updateTaskStatus(workflowResponse);
            if( isTimedOut(++intervals) ) {
                error("RackHD workflow " + getWorkflowTaskId(workflowResponse) + " timed out.");
                return;
            }
            //TODO: get tasks completed/total and update UI
        }        

        updateAffectedResources(workflowResponse);
        //updateTaskStatus(workflowResponse);

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

    private void updateAffectedResources(String workflowResponse) {
        info("MENDES: updateAffectedResources  wf Response=" + workflowResponse);
        RackHdWorkflow wf = 
                getWorkflowObjFromJson(workflowResponse);  
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

    private boolean isWorkflowValid(String workflowResponse) {
        RackHdWorkflow rackHdWorkflow = 
                getWorkflowObjFromJson(workflowResponse);  
        info("RackHD workflow status (isValid?) =" + rackHdWorkflow.get_status());
        return rackHdWorkflow.get_status().
                equalsIgnoreCase(WORKFLOW_VALID_STATE);
    }

    private String makePostBody() {
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

    private boolean isTimedOut(int intervals) {
        return (intervals * RACKHD_WORKFLOW_CHECK_INTERVAL) >= 
                RACKHD_WORKFLOW_CHECK_TIMEOUT;
    }

    private String getWorkflowTaskId(String workflowResponse) {
        RackHdWorkflow rackHdWorkflow = getWorkflowObjFromJson(workflowResponse);  
        return rackHdWorkflow.getId();
    }

    private RackHdWorkflow getWorkflowObjFromJson(String workflowResponse){
        return gson.fromJson(workflowResponse,RackHdWorkflow.class);
    }

    private boolean isWorkflowFailed(String workflowResponse) {
        RackHdWorkflow rackHdWorkflow = 
                getWorkflowObjFromJson(workflowResponse);  
        info("RackHD workflow status (isFailed?)=" + rackHdWorkflow.get_status());
        String status = rackHdWorkflow.get_status();   
        // TODO: get failure states from workflow response
        return status.equalsIgnoreCase(WORKFLOW_FAILED_STATE) || 
                status.equalsIgnoreCase(WORKFLOW_TIMEOUT_STATE) || 
                status.equalsIgnoreCase(WORKFLOW_CANCELLED_STATE);
    }

    private boolean isWorkflowComplete(String workflowResponse) {
        RackHdWorkflow rackHdWorkflow = 
                getWorkflowObjFromJson(workflowResponse);  
        info("RackHD workflow complete=" + rackHdWorkflow.getCompleteEventString());
        return rackHdWorkflow.getCompleteEventString().
                equalsIgnoreCase(WORKFLOW_COMPLETE_STATE);
    }

    private void sleep(int i) {
        try {
            Thread.sleep(i*1000);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        } 
    }

    private String makeRestCall(String uriString) {
        return makeRestCall(uriString,null);
    }

    private String makeRestCall(String uriString, String postBody) {
        info("RackHD request uri: " + uriString);

        ClientResponse response = null;
        if(postBody == null) {
            response = restClient.get(uri(uriString));
        } else {
            info("RackHD request post body: " + postBody);
            response = restClient.post(uri(uriString),postBody);
        }

        String responseString = null;
        try {
            responseString = IOUtils.toString(response.getEntityInputStream(),"UTF-8");
        } catch (IOException e) {
            error("Error getting response from RackHD for: " + uriString +
                    " :: "+ e.getMessage());
            e.printStackTrace();
        }
        return responseString;
    }

    private String getAnyNode(String responseString) {
        RackHdNode[] rackHdNodeArray = gson.fromJson(responseString,RackHdNode[].class);    
        return getComputeNodeId(rackHdNodeArray);  
    }

    private class RackHdNode {
        private String id;
        private String type;

        private String getId() {
            return id;
        }
        private void setId(String id) {
            this.id = id;
        }
        private String getType() {
            return type;
        }
        private void setType(String type) {
            this.type = type;
        }

        private boolean isComputeNode() {
            return (getType() != null) && getType().equals("compute");
        }
    }

    private static String getComputeNodeId(RackHdNode[] nodeArray) {
        for(RackHdNode rackHdNode : nodeArray)
            if(rackHdNode.isComputeNode())
                return rackHdNode.getId();  
        return null;
    }

    private class RackHdWorkflow {
        private String _status;
        private String id;
        private Context context; 
        private String completeEventString;
        private FinishedTask[] finishedTasks;

        public FinishedTask[] getFinishedTasks() {
            return finishedTasks;
        }
        public void setFinishedTasks(FinishedTask[] finishedTasks) {
            this.finishedTasks = finishedTasks;
        }
        public String getCompleteEventString() {
            return completeEventString;
        }
        public void setCompleteEventString(String completeEventString) {
            this.completeEventString = completeEventString;
        }
        public Context getContext() {
            return context;
        }
        public void setContext(Context context) {
            this.context = context;
        }
        public String getId() {
            return id;
        }
        public void setId(String id) {
            this.id = id;
        }
        public String get_status() {
            return _status;
        }
        public void set_status(String _status) {
            this._status = _status;
        }
    }

    private class Context {
        private String ansibleResultFile;
        public String getAnsibleResultFile() {
            return ansibleResultFile;
        }
        public void setAnsibleResultFile(String ansibleResultFile) {
            this.ansibleResultFile = ansibleResultFile;
        }
    }

    private class FinishedTask {
        private Job job;
        public Job getJob() {
            return job;
        }
        public void setJob(Job job) {
            this.job = job;
        }     
    }

    private class Job {
        private Context context;
        public Context getContext() {
            return context;
        }
        public void setContext(Context context) {
            this.context = context;
        }
    }

    private class AffectedResource {
        private String key;
        private String value;

        public String getKey() {
            return key;
        }
        public void setKey(String key) {
            this.key = key;
        }
        public String getValue() {
            return value;
        }
        public void setValue(String value) {
            this.value = value;
        }
    }
} 
