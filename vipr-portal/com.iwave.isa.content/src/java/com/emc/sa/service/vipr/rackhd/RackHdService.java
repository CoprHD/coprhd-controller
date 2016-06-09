/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.rackhd;

import com.emc.sa.service.vipr.ViPRExecutionUtils;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.rackhd.tasks.RackHdTask;
/**import com.emc.storageos.rackhd.api.restapi.RackHdRestClient;
import com.emc.storageos.rackhd.api.restapi.RackHdRestClientFactory;
import com.emc.vipr.client.catalog.AssetOptions;
import com.emc.vipr.model.catalog.AssetOption;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;**/
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.service.Service;

@Service("RackHdService")
public class RackHdService extends ViPRService {

    //TODO: some of this code is also in RackHdProvider - factor code into utils class

    // Name of parameter containing workflow name:
    private static final String WORKFLOW_PARAM_NAME = "Workflow"; 

    // Name of parameter containing playbook name:
    private static final String PLAYBOOK_PARAM_NAME = "Playbook"; 
    
    // Name of parameter containing node to run WF against:
    private static final String PLAYBOOK_PARAM_NODE = "Node";

    private Map<String, Object> params = null;
    private String workflowName = null;
    private String playbookNames = null;
    private List<String> playbookNameList = new ArrayList<>();
    private String nodeId = null;
    String nodeName = null;
    String nodeIp = null;

    @Override
    public void precheck() throws Exception {
        
        params = ExecutionUtils.currentContext().getParameters();

        // possible bug:  when catalog form is filled out, params have quotes
        // around them, but when fields are locked in catalog, the quotes are missing.
        for( String paramKey : params.keySet() ){
            String paramValue = params.get(paramKey).toString();
            if(paramValue != null) {
                if(paramValue.equals("\"\"")) {
                    params.put(paramKey, "");
                }
                else if(paramValue.length() > 2 &&
                        paramValue.endsWith("\"") && paramValue.startsWith("\"")) {
                    String unquotedParam = paramValue.substring(1, paramValue.length()-1);
                    warn("Removing quotes from param " + paramKey + ":" + paramValue +
                            "  (Result: " + unquotedParam + ")");
                    params.put(paramKey, unquotedParam);
                }
            }
        }

        // ignore params with value 'null' 
        //   TODO: find better way to pass in null params
        for(Iterator<Map.Entry<String, Object>> it = params.entrySet().iterator(); it.hasNext();){
            Map.Entry<String, Object> entry = it.next(); 
            if( (entry.getValue() == null) ||
                    entry.getValue().toString().equals("") ||
                    entry.getValue().toString().equalsIgnoreCase("null") ) {
                warn("Ignoring parameter: " + entry.getKey() + "=" + 
                        entry.getValue());
                it.remove();
            }
        }
        
        /**
         * Temporary feature - Services can have only on ServiceDescriptor.
         * All we can do is lock down fields for different uses.
         * But sometimes we need a field to be populated differently, based
         * on the particular use.  For example: 'Node" can be selected from 
         * drop down of all nodes, or else it could be selected from a filtered
         * list of nodes - like a list of all nodes that are running ScaleIO MDM.
         * 
         * Ultimately, we could allow multiple service descriptors per service,
         * but need a way to select them.  Or - we could tag fields somehow
         * to control which fields appear on which service.
         *  
         * For now, we will do this hack: any variable name with an underscore, 
         * like 'Node_MDM', will have its value set on the variable that has the
         * name of the part of the variable that precedes the underscore.  So 
         * 'Node_MDM' will be passed on to RackHD as 'Node'.  (But if there is a 
         * conflict, it will generate an error. 
         */
        Map<String, Object> paramsCopy = new HashMap<String, Object>(params);
        StringBuffer paramErrs = new StringBuffer();
        for(Iterator<Map.Entry<String, Object>> it = paramsCopy.entrySet().iterator(); it.hasNext();){
            Map.Entry<String, Object> entry = it.next();
            if(entry.getKey().contains("_")) {
                String baseVarName = entry.getKey().split("_",2)[0];
                if(params.containsKey(baseVarName)  &&
                        (!params.get(baseVarName).toString().
                        equals(entry.getValue().toString())) ) {
                    paramErrs.append(entry.getKey() + " with value '" +
                            entry.getValue() + "' cannot override " + 
                            baseVarName + " which already has a different " +
                            "value '" + params.get(baseVarName) + "'");
                } 
                params.put(baseVarName, entry.getValue());
                params.remove(entry.getKey());
            }
        }
        if(paramErrs.length()>0){
            throw new IllegalStateException("Error overriding parameters.  " +
                    paramErrs + "  (Params with underscores like 'xxx_yyy' will " +
                    "override params named 'xxx')");
        }
        

        // TODO: check empty/blank params and make null?  (empty consistency group in
        //   XML payload was not null and threw invalid URI error

        if(!params.containsKey(WORKFLOW_PARAM_NAME)) {
            throw new IllegalStateException("No workflow specified " +
                    "in param named " + WORKFLOW_PARAM_NAME + "'");
        }
        workflowName = params.get(WORKFLOW_PARAM_NAME).toString();
        params.remove(WORKFLOW_PARAM_NAME);

        if(!params.containsKey(PLAYBOOK_PARAM_NAME)) {
            info("No playbook specified.");
            playbookNames = null;
        } else {
            playbookNames = params.get(PLAYBOOK_PARAM_NAME).toString();
            params.remove(PLAYBOOK_PARAM_NAME);   
            
            // convert playbook names to list (may be >1)
            //TODO: allow playbooks to be associated with specific tasks in WF
            //TODO: requires reading tasks for selected workflows (dependent AssetOptions providers?)   
            for(String playbook: playbookNames.split(";")){
                playbookNameList.add(playbook);
            }
        }

        if(params.containsKey(PLAYBOOK_PARAM_NODE)) {
            // If RackHD node ID specified, get RackHD name
            nodeId = params.get(PLAYBOOK_PARAM_NODE).toString();
        }

        // pass a proxy token that OE can use to login to ViPR API
        params.put("ProxyToken", ExecutionUtils.currentContext().
                getExecutionState().getProxyToken());
    }

    @Override
    public void execute() throws Exception {
        ExecutionUtils.currentContext().logInfo("Starting RackHD Workflow '" +
                workflowName + "'");
        String workflowResponse =
                ViPRExecutionUtils.execute(new RackHdTask(params,workflowName,playbookNameList,nodeId));
        String errMsg = RackHdUtils.checkForWorkflowFailed(workflowResponse); 
        if(StringUtils.isNotBlank(errMsg)) {
            ExecutionUtils.currentContext().logError("RackHD Workflow " +
                    "completed, but failed.");
            throw new IllegalStateException(errMsg);
        }  
        ExecutionUtils.currentContext().logInfo("RackHD Workflow " +
                "completed successfully.");
    } 
} 
