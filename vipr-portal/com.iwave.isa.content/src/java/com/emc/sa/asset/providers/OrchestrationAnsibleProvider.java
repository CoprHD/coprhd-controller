/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.asset.providers;

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
import com.emc.sa.service.vipr.oe.OrchestrationUtils;
import com.emc.sa.service.vipr.oe.gson.AssetOptionPair;
import com.emc.sa.service.vipr.oe.gson.Workflow;
import com.emc.vipr.model.catalog.AssetOption;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

@Component
@AssetNamespace("oe-ansible")
public class OrchestrationAnsibleProvider extends BaseAssetOptionsProvider {

    // tags defining what this provider supports
    private static final String ASSET_NAMESPACE_TAG = "oe-ansible";
    private static final String ASSET_TAG = "all";
    private String thisAssetType = "";

    // constants
    private static final int OE_WORKFLOW_CHECK_INTERVAL = 1; // secs
    private static final int OE_WORKFLOW_CHECK_TIMEOUT = 30; // secs
    private static final String WORKFLOW_SUCCESS_STATE =  "succeeded";
    private static final String WORKFLOW_TIMEOUT_RESPONSE = "[{\"key\":\"TIMEOUT_ERROR\",\"value\":\"TIMEOUT_ERROR\"}]";
    
    // JSON converter
    private static Gson gson = new Gson();

    /** Note: this is the only provider for all OE AssetOption calls, 
     * so if there are multiple requests (i.e.: multiple drop-down menus
     * in service descriptor) then they will run serially, one after 
     * other.  If it takes 3 seconds to get options list from OE per
     * menu, then ten drop-downs will take 30 secs to process (and browser
     * may timeout).
     */

    public OrchestrationAnsibleProvider() { }

    @Override
    public boolean isAssetTypeSupported(String assetTypeName) {
        // this provider supports all asset types for 'oe-ansible'
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

        // pass a proxy token that OE can use to login to ViPR API
        parentAssetParams.put("ProxyToken", api(ctx).auth().proxyToken());
        
        // Start the OE workflow to get options
        String workflowResponse = OrchestrationUtils.startAnsible2(parentAssetParams, thisAssetType, thisAssetType);  
        
        info("Started Orchestration Engine Workflow ");

        // Get results (waiting for OE workflow to complete)
        int intervals = 0;
        while ( !isWorkflowSuccess(workflowResponse) ) {
            sleep(OE_WORKFLOW_CHECK_INTERVAL);
            workflowResponse = OrchestrationUtils.getAnsibleStatus(workflowResponse);
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
