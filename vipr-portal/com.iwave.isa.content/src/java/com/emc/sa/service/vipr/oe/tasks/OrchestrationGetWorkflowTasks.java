package com.emc.sa.service.vipr.oe.tasks;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.apache.commons.io.IOUtils;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.oe.OrchestrationUtils;
import com.emc.sa.service.vipr.oe.gson.Task;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.oe.api.restapi.OrchestrationEngineRestClient;
import com.emc.storageos.oe.api.restapi.OrchestrationEngineRestClientFactory;
import com.sun.jersey.api.client.ClientResponse;

public class OrchestrationGetWorkflowTasks extends ViPRExecutionTask<List<Task>> {

    // Note: this is a ViPR Task, which calls an OE workflow
    //     (which has its own OE Tasks)
    
    private static final String OE_API_WORKFLOWS = "/api/1.1/workflows/library";
    //TODO: move these hard-coded strings out
    private static final String USER = "root";
    private static final String PASSWORD = "ChangeMe1!";
    private static final String OE_SCHEME = "http"; // include, else URI.resolve(..) fails
    private static final String OE_SERVER = "localhost";
    private static final String OE_SERVERPORT = "9090";

    private String workflowName;
    private OrchestrationEngineRestClient restClient;

    public OrchestrationGetWorkflowTasks(String workflowName) {
        super();
        this.workflowName = workflowName;

        //init rest client
        OrchestrationEngineRestClientFactory factory = new OrchestrationEngineRestClientFactory();
        factory.setMaxConnections(100);
        factory.setMaxConnectionsPerHost(100);
        factory.setNeedCertificateManager(false);
        factory.setSocketConnectionTimeoutMs(3600000);
        factory.setConnectionTimeoutMs(3600000);
        factory.init();
        String endpoint = OE_SCHEME + "://" + 
                OE_SERVER + ":" + OE_SERVERPORT;
        restClient = (OrchestrationEngineRestClient) factory.
                getRESTClient(URI.create(endpoint), USER, PASSWORD, true);
    }

    @Override
    public List<Task> executeTask() throws Exception {
        
        String apiWorkflowUri = OE_API_WORKFLOWS + "/" + workflowName;

        String workflowResponse = makeRestCall(apiWorkflowUri);

        ExecutionUtils.currentContext().logInfo("Found workflow in Orchestration Engine" +
                 workflowResponse + " using API call " + apiWorkflowUri);
        
        return OrchestrationUtils.getTasksInWorkflowDefinition(workflowResponse);
    }

    
    //TODO: move this duplicated code out to reusable class:
    //  (it's also in other OE Task(s)
    private String makeRestCall(String uriString) {
        return makeRestCall(uriString,null);
    }

    private String makeRestCall(String uriString, String postBody) {
        info("OE request uri: " + uriString);

        ClientResponse response = null;
        if(postBody == null) {
            response = restClient.get(uri(uriString));
        } else {
            info("OE request post body: " + postBody);
            response = restClient.post(uri(uriString),postBody);
        }

        String responseString = null;
        try {
            responseString = IOUtils.toString(response.getEntityInputStream(),"UTF-8");
        } catch (IOException e) {
            error("Error getting response from Orchestration Engine for: " + uriString +
                    " :: "+ e.getMessage());
            e.printStackTrace();
        }
        return responseString;
    }


}
