package com.emc.sa.service.vipr.rackhd.tasks;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.rackhd.RackHdUtils;
import com.emc.sa.service.vipr.rackhd.gson.AffectedResource;
import com.emc.sa.service.vipr.rackhd.gson.ViprTask;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.rackhd.api.restapi.RackHdRestClient;
import com.emc.storageos.rackhd.api.restapi.RackHdRestClientFactory;
import com.sun.jersey.api.client.ClientResponse;

public class RackHdTask extends ViPRExecutionTask<String> {

    private static final String RACKHD_API_WORKFLOWS = "/api/1.1/workflows";
    private static final int RACKHD_WORKFLOW_CHECK_INTERVAL = 10; // secs

    //TODO: move these hard-coded strings out
    private static final String USER = "root";
    private static final String PASSWORD = "ChangeMe1!";
    private static final String RACKHDSCHEME = "http"; // include, else URI.resolve(..) fails
    private static final String RACKHDSERVER = "lgloc189.lss.emc.com";
    private static final String RACKHDSERVERPORT = "8080";

    private Map<String, Object> params;
    private String workflowName;
    private List<String> playbookNameList;
    private RackHdRestClient restClient;

    public RackHdTask(Map<String, Object> params, String workflowName, List<String> playbookNameList) {
        super();
        this.params = params;
        this.workflowName = workflowName;
        this.playbookNameList = playbookNameList;

        //init rest client
        RackHdRestClientFactory factory = new RackHdRestClientFactory();
        factory.setMaxConnections(100);
        factory.setMaxConnectionsPerHost(100);
        factory.setNeedCertificateManager(false);
        factory.setSocketConnectionTimeoutMs(3600000);
        factory.setConnectionTimeoutMs(3600000);
        factory.init();
        String endpoint = RACKHDSCHEME + "://" + 
                RACKHDSERVER + ":" + RACKHDSERVERPORT;
        restClient = (RackHdRestClient) factory.
                getRESTClient(URI.create(endpoint), USER, PASSWORD, true);
    }


    int intervals = 0;
    boolean timedOut = false;

    @Override
    public String executeTask() throws Exception {
        String workflowResponse = null; 

        String workflowId = startWorkflow();
        List<URI> tasksStartedByRackHd = new ArrayList<>();
        do {      
            workflowResponse = getRackHdWorkflowResponse(workflowId);

            for(String rackhdTaskResult : RackHdUtils.getRackHdTaskResults(workflowResponse)){
                // see if it refers to Task in ViPR
                ViprTask viprTaskResult = RackHdUtils.parseViprTask(rackhdTaskResult);
                if(viprTaskResult != null) {
                    RackHdUtils.updateAffectedResources(viprTaskResult);
                    URI viprTaskId = RackHdUtils.locateTaskInVipr(viprTaskResult,getClient());
                    if(viprTaskId != null) {
                        addOrderIdTag(viprTaskId);
                        tasksStartedByRackHd.add(viprTaskId);
                    }
                } 
                // else see if it's list of resources
                AffectedResource[] rsrcList = null;
                if (viprTaskResult == null) {
                    rsrcList = RackHdUtils.parseResourceList(rackhdTaskResult);
                    if(rsrcList != null) {
                        RackHdUtils.updateAffectedResources(rsrcList);
                    } 
                }
                // if neither, log result
                if ((viprTaskResult == null) && (rsrcList == null)) {
                    ExecutionUtils.currentContext().logInfo("A RackHD Workflow " + 
                            "result was not recognized as a ViPR Task or " +
                            "list of Affected Resources in ViPR: " + rackhdTaskResult);
                }
            }
        } while (RackHdUtils.isWorkflowRunning(workflowResponse) && !timedOut);

        RackHdUtils.waitForTasks(tasksStartedByRackHd,getClient());

        return workflowResponse;
    }

    private String getRackHdWorkflowResponse(String workflowId) {
        RackHdUtils.sleep(RACKHD_WORKFLOW_CHECK_INTERVAL);
        if( RackHdUtils.isTimedOut(++intervals) ) {
            ExecutionUtils.currentContext().logError("RackHD Workflow " +
                    workflowId + " timed out.");
            timedOut = true;
        }      
        return makeRestCall(RACKHD_API_WORKFLOWS + "/" + workflowId); 
    }

    private String startWorkflow() {
        String apiWorkflowUri = RACKHD_API_WORKFLOWS;
        String postBody = RackHdUtils.makePostBody(params,workflowName,playbookNameList);
        String workflowResponse = makeRestCall(apiWorkflowUri, postBody);
        String workflowId = RackHdUtils.getWorkflowId(workflowResponse);
        ExecutionUtils.currentContext().logInfo("Started Workflow on RackHD.  " +
                "ID " + workflowId + "  API Call: POST " + apiWorkflowUri + 
                " with body " + postBody);
        return workflowId;
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


}
