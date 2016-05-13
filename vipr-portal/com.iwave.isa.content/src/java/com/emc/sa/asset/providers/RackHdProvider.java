/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.asset.providers;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

import com.emc.sa.asset.AssetConverter;
import com.emc.sa.asset.AssetOptionsContext;
import com.emc.sa.asset.BaseAssetOptionsProvider;
import com.emc.sa.asset.annotation.Asset;
import com.emc.sa.asset.annotation.AssetNamespace;
import com.emc.sa.service.vipr.rackhd.RackHdUtils;
import com.emc.sa.service.vipr.rackhd.gson.AssetOptionPair;
import com.emc.sa.service.vipr.rackhd.gson.Workflow;
import com.emc.sa.service.vipr.rackhd.gson.WorkflowDefinition;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.rackhd.api.restapi.RackHdRestClient;
import com.emc.storageos.rackhd.api.restapi.RackHdRestClientFactory;
import com.emc.vipr.model.catalog.AssetOption;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

@Component
@AssetNamespace("rackhd")
public class RackHdProvider extends BaseAssetOptionsProvider {

    // tags defining what this provider supports
    private static final String ASSET_NAMESPACE_TAG = "rackhd";
    private static final String ASSET_TAG = "all";
    private String thisAssetType = "";

    private RackHdRestClient restClient;

    // constants
    private static final String RACKHD_API_WORKFLOWS = "/api/1.1/workflows";
    private static final int RACKHD_WORKFLOW_CHECK_INTERVAL = 1; // secs
    private static final int RACKHD_WORKFLOW_CHECK_TIMEOUT = 30; // secs
    private static final String WORKFLOW_SUCCESS_STATE =  "succeeded";
    private static final String WORKFLOW_TIMEOUT_RESPONSE = "[{\"key\":\"TIMEOUT_ERROR\",\"value\":\"TIMEOUT_ERROR\"}]";
    private static final String RACKHD_API_WORKFLOW_LIBRARY = "/api/1.1/workflows/library/*";

    // special JSON svc descriptors like assetType.rackhd.node 
    private static final String ASSET_TYPE_NODE = "node";
    private static final String ASSET_TYPE_WORKFLOW = "workflow";
    
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
        String endpoint = RackHdUtils.RACKHDSCHEME + "://" +
                RackHdUtils.RACKHDSERVER + ":" + RackHdUtils.RACKHDSERVERPORT;
        restClient = (RackHdRestClient) factory.getRESTClient(URI.create(endpoint),
                RackHdUtils.USER, RackHdUtils.PASSWORD, true);
    }

    @Override
    public boolean isAssetTypeSupported(String assetTypeName) {
        // this provider supports all asset types for 'rackhd'
        return assetTypeName.startsWith(ASSET_NAMESPACE_TAG + "."); 
    }

    Map<String,String> parentAssetParams = new HashMap<>();
    
    @Override
    public List<AssetOption> getAssetOptions(AssetOptionsContext context, String assetTypeName,
            Map<String, String> availableAssets) {
    
        parentAssetParams.clear();
        for(String parentAssetName: getAssetDependencies(assetTypeName,availableAssets.keySet())) {
            String parentAssetValue = availableAssets.get(parentAssetName);

            // TODO: possible bug:  sometimes param values have quotes (same in RackHDService)
            if(parentAssetValue != null) {
                if( parentAssetValue.equals("\"\"")) {
                    parentAssetValue = "";
                } else if ( parentAssetValue.length() > 2 &&
                        parentAssetValue.endsWith("\"") && parentAssetValue.startsWith("\"")) {
                    parentAssetValue = parentAssetValue.substring(1, parentAssetValue.length()-1);
                    warn("Removing quotes from param " + parentAssetName + ":" + parentAssetValue +
                            "  (Result: " + parentAssetValue + ")");
                }
            }

            // TODO: consider refactoring the way we handle None/null params
            if(parentAssetValue.equalsIgnoreCase("null")) {  
                continue; // skip "null"
            }

            parentAssetParams.put(parentAssetName.split("\\.")[1], parentAssetValue);
            info(assetTypeName + " depends on " + parentAssetName +
                    " = " + parentAssetValue);
        }
        
        thisAssetType = assetTypeName.split("\\.")[1];
        assetTypeName = ASSET_NAMESPACE_TAG + "." + ASSET_TAG;  // force to our method name 
        return super.getAssetOptions(context,assetTypeName,availableAssets); 
    } 

    @Override
    public List<String> getAssetDependencies(String assetType, Set<String> availableTypes) {
        // rackhd.a1.a2 means a1 depends on a2
        // rackhd.a1.a2.a3 means a1 depends on a2 and a3, etc
        List<String> result = new ArrayList<>();
        String[] assetTypeParts = assetType.split("\\.");
        for(int i=2;i<assetTypeParts.length;i++) {
            String assetDependsOn = assetTypeParts[0] + "." + assetTypeParts[i];
            if(availableTypes.contains(assetDependsOn)) {
                result.add(assetDependsOn);
            }
        }
        return result;
    }

    @Asset(ASSET_TAG)
    public List<AssetOption> getRackHdOptions(AssetOptionsContext ctx) { 

        info("Getting asset options for '" + thisAssetType + "' from RackHD."); 
                
        // special case (move to new provider or refactor out of here later)
        if(thisAssetType.equalsIgnoreCase(ASSET_TYPE_WORKFLOW)) {
            String workflowJson = RackHdUtils.makeRestCall(RACKHD_API_WORKFLOW_LIBRARY,restClient);
            return getWorkflowOptions(workflowJson);
        }

        // special case (move to new provider or refactor out of here later)
        if(thisAssetType.equalsIgnoreCase(ASSET_TYPE_NODE)) {
            String workflowJson = RackHdUtils.makeRestCall(RackHdUtils.RACKHD_API_NODE,restClient);
            return RackHdUtils.getNodeOptions(workflowJson);
        }

        // if node ID available, use in API call
        // TODO: verify that node ID exists & is valid? (user
        //   could randomly create a field called 'node' for something else) 
        String apiUrl = parentAssetParams.containsKey(ASSET_TYPE_NODE) ?
                RackHdUtils.RACKHD_API_NODE + "/" + 
                parentAssetParams.get(ASSET_TYPE_NODE) + "/workflows" :
                RACKHD_API_WORKFLOWS;

        // Start the RackHD workflow to get options
        String workflowResponse = RackHdUtils.makeRestCall(apiUrl,
                makePostBody(),restClient);

        info("Started RackHD Workflow " + getWorkflowTaskId(workflowResponse));

        // Get results (waiting for RackHD workflow to complete)
        int intervals = 0;
        while ( !isWorkflowSuccess(workflowResponse) ) {
            sleep(RACKHD_WORKFLOW_CHECK_INTERVAL);
            workflowResponse = RackHdUtils.makeRestCall(RACKHD_API_WORKFLOWS + "/" +
                    getWorkflowTaskId(workflowResponse),restClient);
            if( isTimedOut(++intervals) ) {
                error("RackHD workflow " + getWorkflowTaskId(workflowResponse) + " timed out.");
                return jsonToOptions(Arrays.asList(WORKFLOW_TIMEOUT_RESPONSE));
            }
        }        
        List<String> optionListJson = getRackHdResults(workflowResponse);

        return jsonToOptions(optionListJson);       
    }

    private List<AssetOption> getWorkflowOptions(String workflowJson) {
        List<AssetOption> assetOptionList = new ArrayList<>();
        WorkflowDefinition[] wfDefs = 
                gson.fromJson(workflowJson,WorkflowDefinition[].class);    
        if( (wfDefs != null) && (wfDefs.length > 0) ) {
            List<WorkflowDefinition> wfDefList = Arrays.asList(wfDefs);
            for(WorkflowDefinition wfDef: wfDefList) {
                assetOptionList.add(new AssetOption(wfDef.getInjectableName(),
                        wfDef.getFriendlyName()));
            }
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
        return rackHdWorkflow.get_status().equalsIgnoreCase(WORKFLOW_SUCCESS_STATE);
    }

    private Workflow getWorkflowObjFromJson(String workflowResponse){
        return gson.fromJson(workflowResponse,Workflow.class);
    }

    private String makePostBody() {
        StringBuffer postBody = new StringBuffer("{\"name\": \"assetType." + 
                ASSET_NAMESPACE_TAG + "." + thisAssetType + "\"");
        if(!parentAssetParams.isEmpty()) {
           postBody.append(",\"options\":{");
           for(String parentAssetParam : parentAssetParams.keySet()) {
               postBody.append("\"" + parentAssetParam + "\":\"" +
                       parentAssetParams.get(parentAssetParam) +
                       "\",");
           }
           postBody.deleteCharAt(postBody.length()-1); // remove last comma
           postBody.append("}");
        }
        postBody.append("}");; 
        return postBody.toString();
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
                    assetOptionList.add(new AssetOption(aop.getId(),aop.getName()));
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


}
