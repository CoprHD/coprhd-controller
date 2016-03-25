/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.asset.providers;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Component;

import com.emc.sa.asset.AssetOptionsContext;
import com.emc.sa.asset.BaseAssetOptionsProvider;
import com.emc.sa.asset.annotation.Asset;
import com.emc.sa.asset.annotation.AssetNamespace;
import com.emc.sa.service.vipr.rackhd.gson.AssetOptionPair;
import com.emc.sa.service.vipr.rackhd.gson.FinishedTask;
import com.emc.sa.service.vipr.rackhd.gson.Node;
import com.emc.sa.service.vipr.rackhd.gson.Workflow;
import com.emc.sa.service.vipr.rackhd.gson.WorkflowDefinition;
import com.emc.sa.service.vipr.rackhd.gson.WorkflowTask;
import com.emc.sa.service.vipr.rackhd.gson.WorkflowTasks;
import com.emc.storageos.rackhd.api.restapi.RackHdRestClient;
import com.emc.storageos.rackhd.api.restapi.RackHdRestClientFactory;
import com.emc.vipr.model.catalog.AssetOption;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.jersey.api.client.ClientResponse;

@Component
@AssetNamespace("rackhd")
public class RackHdProvider extends BaseAssetOptionsProvider {

    // tags defining what this provider supports
    private static final String ASSET_NAMESPACE_TAG = "rackhd";
    private static final String ASSET_TAG = "all";
    private String thisAssetType = "";

    private RackHdRestClient restClient;

    // TODO: move these hard-coded strings out
    private static final String USER = "root";
    private static final String PASSWORD = "ChangeMe1!";
    private static final String RACKHDSCHEME = "http"; // include, else URI.resolve(..) fails
    private static final String RACKHDSERVER = "lgloc189.lss.emc.com";
    private static final String RACKHDSERVERPORT = "8080";

    // constants
//    private static final String RACKHD_API_NODES = "/api/1.1/nodes"; //include leading slash
    private static final String RACKHD_API_WORKFLOWS = "/api/1.1/workflows";
    private static final int RACKHD_WORKFLOW_CHECK_INTERVAL = 1; // secs
    private static final int RACKHD_WORKFLOW_CHECK_TIMEOUT = 30; // secs
    private static final String WORKFLOW_SUCCESS_STATE =  "succeeded";
    private static final String WORKFLOW_TIMEOUT_RESPONSE = "[{\"key\":\"TIMEOUT_ERROR\",\"value\":\"TIMEOUT_ERROR\"}]";
    private static final String RACKHD_API_WORKFLOW_LIBRARY = "/api/1.1/workflows/library/*";

    // JSON converter
    private static Gson gson = new Gson();

    /** Note: this is the only provider for all RackHD AssetOption calls, 
     * so if there are multiple requests (i.e.: multiple drop-down menus
     * in service descriptor) then they will run serially, one after 
     * other.  If it takes 3 seconds to get options list from RackHD per
     * menu, then ten drop-downs will take 30 secs to process (and browser
     * may timeout).
     */

    public RackHdProvider() {
        RackHdRestClientFactory factory = new RackHdRestClientFactory();
        factory.setMaxConnections(100);
        factory.setMaxConnectionsPerHost(100);
        factory.setNeedCertificateManager(false);
        factory.setSocketConnectionTimeoutMs(3600000);
        factory.setConnectionTimeoutMs(3600000);
        factory.init();
        String endpoint = RACKHDSCHEME + "://" + RACKHDSERVER + ":" + RACKHDSERVERPORT;
        restClient = (RackHdRestClient) factory.getRESTClient(URI.create(endpoint), USER, PASSWORD, true);
    }

    @Override
    public boolean isAssetTypeSupported(String assetTypeName) {
        // this provider supports all asset types for 'rackhd'
        return assetTypeName.startsWith(ASSET_NAMESPACE_TAG + "."); 
    }

    @Override
    public List<AssetOption> getAssetOptions(AssetOptionsContext context, String assetTypeName,
            Map<String, String> availableAssets) { 
        thisAssetType = assetTypeName.substring(ASSET_NAMESPACE_TAG.length() + 1); 
        assetTypeName = ASSET_NAMESPACE_TAG + "." + ASSET_TAG;  // force to our method name 
        return super.getAssetOptions(context,assetTypeName,availableAssets); 
    } 

    @Override
    public List<String> getAssetDependencies(String assetType, Set<String> availableTypes) {
        assetType =  ASSET_NAMESPACE_TAG + "." + ASSET_TAG;  // force to our method name 
        return super.getAssetDependencies(assetType,availableTypes); 
    }

    @Asset(ASSET_TAG)
    public List<AssetOption> getRackHdOptions(AssetOptionsContext ctx) { 

        info("Getting asset options for '" + thisAssetType + "' from RackHD.");

        // special case! move to new provider or refactor out of here later
        if(thisAssetType.equalsIgnoreCase("workflow")) {
            // returns list of available RackHD workflows as options
            String workflowJson = makeRestCall(RACKHD_API_WORKFLOW_LIBRARY);
            return getWorkflowOptions(workflowJson);
        }

        // Provider needs the ID of a node in RackHD to run a WF, but it does 
        //  not matter which node we run the WF against, since the WF  
        //  will run locally on the RackHD server.
// REMOVED IN LIEU OF NODE-AGNOSTIC CALL!!
        //String nodeListResponse = makeRestCall(RACKHD_API_NODES);
        //String nodeId = getAnyNode(nodeListResponse);

        // Start the workflow to get options
        String workflowResponse = makeRestCall(RACKHD_API_WORKFLOWS,
                makePostBody(thisAssetType));

        // Get results (wait for RackHD workflow to complete)
        int intervals = 0;
        while ( !isWorkflowSuccess(workflowResponse) ) {
            sleep(RACKHD_WORKFLOW_CHECK_INTERVAL);
            workflowResponse = makeRestCall(RACKHD_API_WORKFLOWS + "/" + 
                    getWorkflowTaskId(workflowResponse));
            if( isTimedOut(++intervals) ) {
                error("RackHD workflow " + getWorkflowTaskId(workflowResponse) + " timed out.");
                return jsonToOptions(Arrays.asList(WORKFLOW_TIMEOUT_RESPONSE));
            }
        }        
        List<String> optionListJson = getRackHdResults(workflowResponse);

        return jsonToOptions(optionListJson);       
    }

    private List<AssetOption> getWorkflowOptions(String workflowJson) {
        WorkflowDefinition[] wfDescrs = getRackHdWfLib(workflowJson);
        List<WorkflowDefinition> wfList = Arrays.asList(wfDescrs);
        List<AssetOption> assetOptionList = new ArrayList<>();
        for(WorkflowDefinition wfDef: wfList) {
            assetOptionList.add(new AssetOption(wfDef.getInjectableName(),
                    wfDef.getFriendlyName()));
        }
        return assetOptionList;
    }

    private boolean isTimedOut(int intervals) {
        return (intervals * RACKHD_WORKFLOW_CHECK_INTERVAL) >= 
                RACKHD_WORKFLOW_CHECK_TIMEOUT;
    }

    private List<String> getRackHdResults(String workflowResponse) {
        Workflow workflow = getWorkflowObjFromJson(workflowResponse);
        String[] ansibleResultArray = 
                workflow.getContext().getAnsibleResultFile();
        return Arrays.asList(ansibleResultArray);
    }

    private String getWorkflowTaskId(String workflowResponse) {
        Workflow rackHdWorkflow = getWorkflowObjFromJson(workflowResponse);  
        return rackHdWorkflow.getInstanceId();
    }

    private boolean isWorkflowSuccess(String workflowResponse) {
        Workflow rackHdWorkflow = getWorkflowObjFromJson(workflowResponse);  
        info("RackHD workflow status=" + rackHdWorkflow.get_status());
        return rackHdWorkflow.get_status().equalsIgnoreCase(WORKFLOW_SUCCESS_STATE);
    }

    private Workflow getWorkflowObjFromJson(String workflowResponse){
        return gson.fromJson(workflowResponse,Workflow.class);
    }

    private String makePostBody(String thisAssetType) {
        return "{\"name\": \"assetType." + ASSET_NAMESPACE_TAG + 
                "." + thisAssetType + "\"}";
    }

    private String getAnyNode(String responseString) {
        Node[] rackHdNodeArray = gson.fromJson(responseString,Node[].class);    
        return getComputeNodeId(rackHdNodeArray);  
    }

    private List<AssetOption> jsonToOptions(List<String> ansibleResultFiles) {
        
        //options will be combined from all ansible result files 
        //  (i.e.: all RackHD ansible tasks that returned valid results)
        List<AssetOption> assetOptionList = new ArrayList<>();
        for(String ansibleResultFile : ansibleResultFiles) {
            AssetOptionPair[] assetOptionArray = null;
            try {
                assetOptionArray = gson.fromJson(ansibleResultFile,AssetOptionPair[].class);  
                for(AssetOptionPair aop: assetOptionArray) {
                    assetOptionList.add(new AssetOption(aop.getKey(),aop.getValue()));
                }  
            } catch(JsonSyntaxException e) {
                // not all task results will always be asset options
                getLog().warn("Unable to parse RackHD task result as valid asset " + 
                        "options.  " + e.getMessage() + "  Unparsable string was: " + 
                ansibleResultFile);
            }
        }
        info("Found " + assetOptionList.size()+ " options from RackHD: " + 
                assetOptionList);   
        return assetOptionList;
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

    private static String getComputeNodeId(Node[] nodeArray) {
        for(Node rackHdNode : nodeArray)
            if(rackHdNode.isComputeNode())
                return rackHdNode.getId();	
        return null;
    }

    private WorkflowDefinition[] getRackHdWfLib(String responseString) {
        WorkflowDefinition[] wfs = 
                gson.fromJson(responseString,WorkflowDefinition[].class);    
        return wfs;  
    }

}
