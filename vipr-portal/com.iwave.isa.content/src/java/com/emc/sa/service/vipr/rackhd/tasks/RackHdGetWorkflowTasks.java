package com.emc.sa.service.vipr.rackhd.tasks;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.apache.commons.io.IOUtils;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.rackhd.RackHdUtils;
import com.emc.sa.service.vipr.rackhd.gson.Task;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.rackhd.api.restapi.RackHdRestClient;
import com.emc.storageos.rackhd.api.restapi.RackHdRestClientFactory;
import com.sun.jersey.api.client.ClientResponse;

public class RackHdGetWorkflowTasks extends ViPRExecutionTask<List<Task>> {

    // Note: this is a ViPR Task, which calls a RackHD workflow
    //     (which has its own RackHD Tasks)
    
    private static final String RACKHD_API_WORKFLOWS = "/api/1.1/workflows/library";
    //TODO: move these hard-coded strings out
    private static final String USER = "root";
    private static final String PASSWORD = "ChangeMe1!";
    private static final String RACKHDSCHEME = "http"; // include, else URI.resolve(..) fails
    private static final String RACKHDSERVER = "localhost";
    private static final String RACKHDSERVERPORT = "8080";

    private String workflowName;
    private RackHdRestClient restClient;

    public RackHdGetWorkflowTasks(String workflowName) {
        super();
        this.workflowName = workflowName;

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
    public List<Task> executeTask() throws Exception {
        
        String apiWorkflowUri = RACKHD_API_WORKFLOWS + "/" + workflowName;

        String workflowResponse = makeRestCall(apiWorkflowUri);

        ExecutionUtils.currentContext().logInfo("Found workflow in RackHD" +
                 workflowResponse + " using API call " + apiWorkflowUri);
        
        return RackHdUtils.getTasksInWorkflowDefinition(workflowResponse);
    }

    
    //TODO: move this duplicated code out to reusable class:
    //  (it's also in other RackHd Task(s)
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
