package com.emc.sa.service.vipr.oe.tasks;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.oe.OrchestrationUtils;
import com.emc.sa.service.vipr.oe.gson.AffectedResource;
import com.emc.sa.service.vipr.oe.gson.OeStatusMessage;
import com.emc.sa.service.vipr.oe.gson.ViprOperation;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.oe.api.restapi.OrchestrationEngineRestClient;
import com.emc.storageos.oe.api.restapi.OrchestrationEngineRestClientFactory;
import com.sun.jersey.api.client.ClientResponse;

public class OrchestrationTask extends ViPRExecutionTask<String> {

    private static final String OE_API_WORKFLOWS = "";
    private static final int OE_WORKFLOW_CHECK_INTERVAL = 10; // secs

    //TODO: move these hard-coded strings out
    private static final String USER = "root";
    private static final String PASSWORD = "ChangeMe1!";
    private static final String OESCHEME = "http"; // include, else URI.resolve(..) fails
    private static final String OESERVER = "localhost";
    private static final String OESERVERPORT = "9090"; 

    private OrchestrationEngineRestClient restClient;
    Map<String, Object> params;
    
    public OrchestrationTask(Map<String, Object> params) {
        super();
        
        this.params = params;
        
        //init rest client
        OrchestrationEngineRestClientFactory factory = new OrchestrationEngineRestClientFactory();
        factory.setMaxConnections(100);
        factory.setMaxConnectionsPerHost(100);
        factory.setNeedCertificateManager(false);
        factory.setSocketConnectionTimeoutMs(3600000);
        factory.setConnectionTimeoutMs(3600000);
        factory.init();
        String endpoint = OESCHEME + "://" + 
                OESERVER + ":" + OESERVERPORT;
        restClient = (OrchestrationEngineRestClient) factory.
                getRESTClient(URI.create(endpoint), USER, PASSWORD, true);
    }


    int intervals = 0;
    boolean timedOut = false;

    @Override
    public String executeTask() throws Exception {
        String workflowResponse = null; 

        String workflowId = startWorkflow();
        List<URI> tasksStartedByOe = new ArrayList<>();
        List<String> messagesLogged = new ArrayList<>();
        do {
            workflowResponse = getOeWorkflowResponse(workflowId);

            // see if it refers to an Operation with ViPR Tasks
            ViprOperation viprOperation = OrchestrationUtils.parseViprTasks(workflowResponse);
            if(viprOperation != null) {
                OrchestrationUtils.updateAffectedResources(viprOperation);
                List<TaskResourceRep> viprTaskIds = OrchestrationUtils.locateTasksInVipr(viprOperation,getClient());
                for(TaskResourceRep task : viprTaskIds ) {
                    if(!tasksStartedByOe.contains(task.getId())) {
                        addOrderIdTag(task.getId());
                        tasksStartedByOe.add(task.getId());
                        ExecutionUtils.currentContext().logInfo("Orchestration Engine started " +
                                " task '" + task.getName()+ "'  " +
                                task.getResource().getName());
                    }
                }
            } 
            // else see if it's a list of resources
            AffectedResource[] rsrcList = null;
            if (viprOperation == null) {
                rsrcList = OrchestrationUtils.parseResourceList(workflowResponse);
                if(rsrcList != null) {
                    OrchestrationUtils.updateAffectedResources(rsrcList);
                }
            }
            // else see if it's a status message for order log
            OeStatusMessage oeStatusMessage = null;
            if ((viprOperation == null) && (rsrcList == null)) {
                oeStatusMessage = OrchestrationUtils.parseOeStatusMessage(workflowResponse);
                if((oeStatusMessage != null) &&
                        !messagesLogged.contains(oeStatusMessage.getMessage())){
                    ExecutionUtils.currentContext().logInfo(oeStatusMessage.getMessage());
                    messagesLogged.add(oeStatusMessage.getMessage());
                }
            }
            // if neither, log result
            if ((viprOperation == null) && (rsrcList == null) && (oeStatusMessage == null) ) {
                ExecutionUtils.currentContext().logInfo("An orchestration engine Workflow " +
                        "result was not recognized as a ViPR Task or " +
                        "list of Affected Resources in ViPR: " + workflowResponse);
            }


        } while (OrchestrationUtils.isWorkflowRunning(workflowResponse) && !timedOut);
        OrchestrationUtils.waitForTasks(tasksStartedByOe,getClient());
        return workflowResponse;
    }

    private String getOeWorkflowResponse(String workflowId) {
        OrchestrationUtils.sleep(OE_WORKFLOW_CHECK_INTERVAL);
        if( OrchestrationUtils.isTimedOut(++intervals) ) {
            ExecutionUtils.currentContext().logError("Orchestration Engine Workflow " +
                    workflowId + " timed out.");
            timedOut = true;
        }      
        return makeRestCall(OE_API_WORKFLOWS + "/" + workflowId); 
    }

    private String startWorkflow() {
        String workflowName = params.get("workflowName").toString();
        
        // add code to start workflow
        
        return null;  // return WF ID
    }

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
