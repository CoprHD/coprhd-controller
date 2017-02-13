/*
 * Copyright 2016 Dell Inc. or its subsidiaries.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
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
import com.emc.sa.service.vipr.customservices.CustomServicesUtils;
import com.emc.sa.service.vipr.customservices.gson.AssetOptionPair;
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
    private static final int OE_WORKFLOW_CHECK_INTERVAL = 1; // secs
    private static final int OE_WORKFLOW_CHECK_TIMEOUT = 30; // secs
    private static final String WORKFLOW_TIMEOUT_RESPONSE = "[{\"key\":\"TIMEOUT_ERROR\",\"value\":\"TIMEOUT_ERROR\"}]";
    
    // JSON converter
    private static Gson gson = new Gson();

    private Map<String,String> parentAssetParams = new HashMap<>();

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
        String endpoint = CustomServicesUtils.OE_SCHEME + "://" +
                CustomServicesUtils.OE_SERVER + ":" + CustomServicesUtils.OE_SERVERPORT;
        restClient = (OrchestrationEngineRestClient) factory.getRESTClient(URI.create(endpoint),
                CustomServicesUtils.USER, CustomServicesUtils.PASSWORD, true);
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
        // force 'assetTypeName' to our method name 
        return super.getAssetOptions(context,ASSET_NAMESPACE_TAG + "." + ASSET_TAG,availableAssets); 
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
    public List<AssetOption> getOrchestrationOptions(AssetOptionsContext ctx) throws InterruptedException { 

        info("Getting asset options for '" + thisAssetType + "' from OE."); 

        String apiUrl = null;

        // pass a proxy token that OE can use to login to ViPR API
        parentAssetParams.put("ProxyToken", api(ctx).auth().proxyToken());
        
        // Start the OE workflow to get options
        info("OE Provider calling " + apiUrl + "with body " + makePostBody());
        String workflowResponse = CustomServicesUtils.makeRestCall(apiUrl,
                makePostBody(),restClient,CustomServicesUtils.POST);

        info("Started Orchestration Engine Workflow");

        // Get results (waiting for OE workflow to complete)
        int intervals = 0;
        while ( !isWorkflowSuccess(workflowResponse) ) {
            sleep(OE_WORKFLOW_CHECK_INTERVAL);
            // get updated WF reponse 
            workflowResponse = CustomServicesUtils.makeRestCall("/path/to/get/wf/status",restClient);
            if( isFailed(workflowResponse) || isTimedOut(++intervals) ) {
                error("Orchestration Engine workflow timed out.");
                return jsonToOptions(Arrays.asList(WORKFLOW_TIMEOUT_RESPONSE));
            }
        }        
        List<String> optionListJson = Arrays.asList("data for options in json");

        return jsonToOptions(optionListJson);       
    }

    private boolean isWorkflowSuccess(String workflowResponse) {
        // test response to see if WF has finished successfully
        return false;
    }

    private boolean isFailed(String workflowResponse) {
    	return CustomServicesUtils.isWorkflowFailed(workflowResponse);
	}

    private boolean isTimedOut(int intervals) {
        return (intervals * OE_WORKFLOW_CHECK_INTERVAL) >= 
                OE_WORKFLOW_CHECK_TIMEOUT;
    }

    private String makePostBody() {
        // make post body as needed
        return null;
    }

    private List<AssetOption> jsonToOptions(List<String> wfResults) {
        
        //options will be combined from all ansible result files 
        //  (i.e.: all OE ansible tasks that returned valid results)
        List<AssetOption> assetOptionList = new ArrayList<>();
        for(String wfResult : wfResults) {
            AssetOptionPair[] assetOptionArray = null;
            try {
                assetOptionArray = gson.fromJson(wfResult,AssetOptionPair[].class);  
                for(AssetOptionPair aop: assetOptionArray) {
                    assetOptionList.add(new AssetOption(aop.getId(),aop.getName()));
                }  
            } catch(JsonSyntaxException e) {
                // not all task results will always be asset options
                getLog().warn("Unable to parse Orchestration Engine task result as valid asset " + 
                        "options.  " + e.getMessage() + "  Unparsable string was: " + 
                wfResult);
            }
        }
        info("Found " + assetOptionList.size()+ " options from OE: " + 
                assetOptionList);   
        return assetOptionList;
    }

    private void sleep(int seconds) throws InterruptedException {
        try {
            Thread.sleep(seconds*1000);
        }
        catch (InterruptedException e) {
            throw e;
        } 
    } 
}
