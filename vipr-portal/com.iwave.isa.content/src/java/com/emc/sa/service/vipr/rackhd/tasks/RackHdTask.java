package com.emc.sa.service.vipr.rackhd.tasks;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.rackhd.RackHdUtils;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.rackhd.api.restapi.RackHdRestClient;
import com.emc.storageos.rackhd.api.restapi.RackHdRestClientFactory;
import com.sun.jersey.api.client.ClientResponse;

public class RackHdTask extends ViPRExecutionTask<String> {

    // Note: this is a ViPR Task, which calls a RackHD workflow
    //     (which has its own RackHD Tasks)
    
    private static final String RACKHD_API_NODES = "/api/1.1/nodes"; //include leading slash
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
    private String playbookName;
    private RackHdRestClient restClient;

    public RackHdTask(Map<String, Object> params, String workflowName, String playbookName) {
        super();
        this.params = params;
        this.workflowName = workflowName;
        this.playbookName = playbookName;

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

    @Override
    public String executeTask() throws Exception {

        String nodeListResponse = makeRestCall(RACKHD_API_NODES);
        String nodeId = RackHdUtils.getAnyNode(nodeListResponse);         

        ExecutionUtils.currentContext().logInfo("RackHD Workflow executing " +
                "against node '" + nodeId + "'");
        
        String apiWorkflowUri = "/api/1.1/nodes/" + nodeId + "/workflows";

        String workflowResponse = makeRestCall(apiWorkflowUri,
                RackHdUtils.makePostBody(params, workflowName,playbookName));

        ExecutionUtils.currentContext().logInfo("RackHD Workflow " +
                RackHdUtils.getWorkflowId(workflowResponse) + 
                " started at " + apiWorkflowUri + " with params " + params);
        
        // Get results - wait for RackHD workflow to complete

        int intervals = 0;
        List<String> finishedTasks = new ArrayList<>();
        do { 
            RackHdUtils.sleep(RACKHD_WORKFLOW_CHECK_INTERVAL);
            workflowResponse = makeRestCall(RACKHD_API_WORKFLOWS + "/" + 
                    RackHdUtils.getWorkflowId(workflowResponse));            
            finishedTasks = RackHdUtils.
                    updateAffectedResources(workflowResponse,finishedTasks);
            if( RackHdUtils.isTimedOut(++intervals) ) {
                ExecutionUtils.currentContext().logError("RackHD Workflow " +
                        RackHdUtils.getWorkflowId(workflowResponse) + 
                        " timed out.");
                break;
            }
        } while ( !RackHdUtils.isWorkflowComplete(workflowResponse) ||  // does complete flag matter?
                // status not updated from 'valid' even when complete?! :
                RackHdUtils.isWorkflowValid(workflowResponse) );
        return workflowResponse;
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
