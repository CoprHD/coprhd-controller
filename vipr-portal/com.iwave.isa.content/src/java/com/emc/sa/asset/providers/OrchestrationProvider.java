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

import org.springframework.stereotype.Component;

import com.emc.sa.asset.AssetOptionsContext;
import com.emc.sa.asset.BaseAssetOptionsProvider;
import com.emc.sa.asset.annotation.Asset;
import com.emc.sa.asset.annotation.AssetNamespace;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.oe.OrchestrationUtils;
import com.emc.sa.service.vipr.oe.gson.AssetOptionPair;
import com.emc.sa.service.vipr.oe.gson.Workflow;
import com.emc.sa.service.vipr.oe.gson.WorkflowDefinition;
import com.emc.storageos.oe.api.restapi.OrchestrationEngineRestClient;
import com.emc.storageos.oe.api.restapi.OrchestrationEngineRestClientFactory;
import com.emc.vipr.model.catalog.AssetOption;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

@Component
@AssetNamespace("oe")
public class OrchestrationProvider extends BaseAssetOptionsProvider {

    // tags defining what this provider supports
    private static final String ASSET_NAMESPACE_TAG = "oe";
    private static final String ASSET_TAG = "all";
    private String thisAssetType = "";

    private OrchestrationEngineRestClient restClient;

    // constants
    private static final String OE_API_WORKFLOWS = "/api/1.1/workflows";
    private static final int OE_WORKFLOW_CHECK_INTERVAL = 1; // secs
    private static final int OE_WORKFLOW_CHECK_TIMEOUT = 30; // secs
    private static final String WORKFLOW_SUCCESS_STATE =  "succeeded";
    private static final String WORKFLOW_TIMEOUT_RESPONSE = "[{\"key\":\"TIMEOUT_ERROR\",\"value\":\"TIMEOUT_ERROR\"}]";
    private static final String OE_API_WORKFLOW_LIBRARY = "/api/1.1/workflows/library/*";

    // special JSON svc descriptors like assetType.oe.node 
    private static final String ASSET_TYPE_NODE = "node";
    private static final String ASSET_TYPE_WORKFLOW = "workflow";
    
    // JSON converter
    private static Gson gson = new Gson();

    /** Note: this is the only provider for all OE AssetOption calls, 
     * so if there are multiple requests (i.e.: multiple drop-down menus
     * in service descriptor) then they will run serially, one after 
     * other.  If it takes 3 seconds to get options list from OE per
     * menu, then ten drop-downs will take 30 secs to process (and browser
     * may timeout).
     */

    public OrchestrationProvider() {
        OrchestrationEngineRestClientFactory factory = new OrchestrationEngineRestClientFactory();
        factory.setMaxConnections(100);
        factory.setMaxConnectionsPerHost(100);
        factory.setNeedCertificateManager(false);
        factory.setSocketConnectionTimeoutMs(3600000);
        factory.setConnectionTimeoutMs(3600000);
        factory.init();
        String endpoint = OrchestrationUtils.OE_SCHEME + "://" +
                OrchestrationUtils.OE_SERVER + ":" + OrchestrationUtils.OE_SERVERPORT;
        restClient = (OrchestrationEngineRestClient) factory.getRESTClient(URI.create(endpoint),
                OrchestrationUtils.USER, OrchestrationUtils.PASSWORD, true);
    }

    @Override
    public boolean isAssetTypeSupported(String assetTypeName) {
        // this provider supports all asset types for 'oe'
        return assetTypeName.startsWith(ASSET_NAMESPACE_TAG + "."); 
    }

    // since getting options is not fast, use raw labels when creating orders
    public boolean useRawLabels(){
        return true;
    }

    Map<String,String> parentAssetParams = new HashMap<>();
    
    @Override
    public List<AssetOption> getAssetOptions(AssetOptionsContext context, String assetTypeName,
            Map<String, String> availableAssets) {
    
        parentAssetParams.clear();
        for(String parentAssetName: getAssetDependencies(assetTypeName,availableAssets.keySet())) {
            String parentAssetValue = availableAssets.get(parentAssetName);

            // TODO: possible bug:  sometimes param values have quotes (same in OrchestrationService)
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
        
        if(!parentAssetParams.isEmpty()) {
            info(assetTypeName + " has parentAssetParams of " + parentAssetParams);
        }

        thisAssetType = assetTypeName.split("\\.")[1];
        assetTypeName = ASSET_NAMESPACE_TAG + "." + ASSET_TAG;  // force to our method name 
        return super.getAssetOptions(context,assetTypeName,availableAssets); 
    } 

    @Override
    public List<String> getAssetDependencies(String assetType, Set<String> availableTypes) {
        // oe.a1.a2 means a1 depends on a2
        // oe.a1.a2.a3 means a1 depends on a2 and a3, etc
        List<String> result = new ArrayList<>();
        String[] assetTypeParts = assetType.split("\\.");
        for(int i=2;i<assetTypeParts.length;i++) {
            String assetDependsOn = assetTypeParts[0] + "." + assetTypeParts[i];
            String fullType = availableTypesContains(availableTypes,
            				assetDependsOn);
            if(fullType != null) {
                result.add(fullType);
            }
        }
        return result;
    }

    // find any available types that start with this one
	private String availableTypesContains(Set<String> availableTypes, 
			String assetDependsOn) {
		for(String availableType : availableTypes) {
			if(availableType.startsWith(assetDependsOn)) {
				return availableType;
			}
		}
		return null;
	}

	@Asset(ASSET_TAG)
    public List<AssetOption> getOrchestrationOptions(AssetOptionsContext ctx) { 

        info("Getting asset options for '" + thisAssetType + "' from OE."); 
                
        // special case (move to new provider or refactor out of here later)
        if(thisAssetType.equalsIgnoreCase(ASSET_TYPE_WORKFLOW)) {
            String workflowJson = OrchestrationUtils.makeRestCall(OE_API_WORKFLOW_LIBRARY,restClient);
            return getWorkflowOptions(workflowJson);
        }

        // special case (move to new provider or refactor out of here later)
        if(thisAssetType.equalsIgnoreCase(ASSET_TYPE_NODE)) {
            String workflowJson = OrchestrationUtils.makeRestCall(OrchestrationUtils.OE_API_NODE,restClient);
            return OrchestrationUtils.getNodeOptions(workflowJson);
        }

        // if node ID available, use in API call
        // TODO: verify that node ID exists & is valid? (user
        //   could randomly create a field called 'node' for something else) 
        String apiUrl = parentAssetParams.containsKey(ASSET_TYPE_NODE) ?
                OrchestrationUtils.OE_API_NODE + "/" + 
                parentAssetParams.get(ASSET_TYPE_NODE) + "/workflows" :
                OE_API_WORKFLOWS;

        // pass a proxy token that OE can use to login to ViPR API
        parentAssetParams.put("ProxyToken", api(ctx).auth().proxyToken());
        
        // Start the OE workflow to get options
        String workflowResponse = OrchestrationUtils.makeRestCall(apiUrl,
                makePostBody(),restClient);

        info("Started Orchestration Engine Workflow " + getWorkflowTaskId(workflowResponse));

        // Get results (waiting for OE workflow to complete)
        int intervals = 0;
        while ( !isWorkflowSuccess(workflowResponse) ) {
            sleep(OE_WORKFLOW_CHECK_INTERVAL);
            workflowResponse = OrchestrationUtils.makeRestCall(OE_API_WORKFLOWS + "/" +
                    getWorkflowTaskId(workflowResponse),restClient);
            if( isFailed(workflowResponse) || isTimedOut(++intervals) ) {
                error("Orchestration Engine workflow " + getWorkflowTaskId(workflowResponse) + " timed out.");
                return jsonToOptions(Arrays.asList(WORKFLOW_TIMEOUT_RESPONSE));
            }
        }        
        List<String> optionListJson = getOeResults(workflowResponse);

        return jsonToOptions(optionListJson);       
    }

    private boolean isFailed(String workflowResponse) {
    	return OrchestrationUtils.isWorkflowFailed(workflowResponse);
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
        return (intervals * OE_WORKFLOW_CHECK_INTERVAL) >= 
                OE_WORKFLOW_CHECK_TIMEOUT;
    }

    private List<String> getOeResults(String workflowResponse) {
        Workflow workflow = getWorkflowObjFromJson(workflowResponse);
        String[] ansibleResultArray = 
                workflow.getContext().getAnsibleResultFile();
        return Arrays.asList(ansibleResultArray);
    }

    private String getWorkflowTaskId(String workflowResponse) {
        Workflow oeWorkflow = getWorkflowObjFromJson(workflowResponse);  
        return oeWorkflow.getInstanceId();
    }

    private boolean isWorkflowSuccess(String workflowResponse) {
        Workflow oeWorkflow = getWorkflowObjFromJson(workflowResponse);
        return oeWorkflow.get_status().equalsIgnoreCase(WORKFLOW_SUCCESS_STATE);
    }

    private Workflow getWorkflowObjFromJson(String workflowResponse){
        return gson.fromJson(workflowResponse,Workflow.class);
    }

    private String makePostBody() {
        StringBuffer postBody = new StringBuffer("{\"name\": \"assetType." + 
                ASSET_NAMESPACE_TAG + "." + thisAssetType + "\"");
        if(!parentAssetParams.isEmpty()) {
           postBody.append(",\"options\":{\"defaults\":{\"vars\":{");
           for(String parentAssetParam : parentAssetParams.keySet()) {
               postBody.append("\"" + parentAssetParam + "\":\"" +
                       parentAssetParams.get(parentAssetParam) +
                       "\",");
           }
           postBody.deleteCharAt(postBody.length()-1); // remove last comma
           postBody.append("}}}");
        }
        postBody.append("}");; 
        return postBody.toString();
    }

    private List<AssetOption> jsonToOptions(List<String> ansibleResultFiles) {
        
        //options will be combined from all ansible result files 
        //  (i.e.: all OE ansible tasks that returned valid results)
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
                getLog().warn("Unable to parse Orchestration Engine task result as valid asset " + 
                        "options.  " + e.getMessage() + "  Unparsable string was: " + 
                ansibleResultFile);
            }
        }
        info("Found " + assetOptionList.size()+ " options from OE: " + 
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
