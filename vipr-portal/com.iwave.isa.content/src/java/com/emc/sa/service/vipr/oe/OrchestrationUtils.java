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

package com.emc.sa.service.vipr.oe;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.oe.gson.AffectedResource;
import com.emc.sa.service.vipr.oe.gson.OeStatusMessage;
import com.emc.sa.service.vipr.oe.gson.ViprOperation;
import com.emc.sa.service.vipr.oe.gson.ViprTask;
import com.emc.sa.service.vipr.oe.tasks.TaskState;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.oe.api.restapi.OrchestrationEngineRestClient;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.emc.vipr.client.ViPRCoreClient;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.jersey.api.client.ClientResponse;

//TODO: move log messages to separate file (for internationalization)

public class OrchestrationUtils {

    private OrchestrationUtils() {
        // no public constructor allwoed for utility classes
    }
    
    //TODO: externalize these values:
    private static final int OE_WORKFLOW_CHECK_INTERVAL = 10; // secs
    private static final int OE_WORKFLOW_CHECK_TIMEOUT = 600; // secs
    private static final int TASK_CHECK_TIMEOUT = 3600;  // mins
    private static final int TASK_CHECK_INTERVAL = 10; // secs


    // TODO: move these hard-coded strings out
    public static final String USER = "root";
    public static final String PASSWORD = "ChangeMe1!";
    public static final String OE_SCHEME = "http"; // include, else URI.resolve(..) fails
    public static final String OE_SERVER = "localhost";
    public static final String OE_SERVERPORT = "9090";

    public static final String OE_API_NODE = "/api/1.1/nodes";

    public static final String POST = "POST";
    
    private static final Gson gson = new Gson();

    public static boolean isTimedOut(int intervals) {
        return (intervals * OE_WORKFLOW_CHECK_INTERVAL) >= 
                OE_WORKFLOW_CHECK_TIMEOUT;
    }

    public static String makePostBody(Map<String, Object> params, 
            String workflowName, List<String> playbookNameList) {
        // make post body as needed
        return null;
    }

    public static void sleep(int seconds) throws InterruptedException {
        try {
            Thread.sleep(seconds*1000);
        }
        catch (InterruptedException e) {
            throw e;
        } 
    }

    public static boolean isWorkflowRunning(String workflowResponse) {
        // parse response and determine result
        return false;
    }

    public static boolean isWorkflowFailed(String workflowResponse) {
        // parse response and determine result
        return false;
    }

    public static String getFailedWorkflowErrors(String workflowResponse) {
        StringBuffer errMsg = new StringBuffer();
        if(isWorkflowFailed(workflowResponse)) { 
            errMsg.append("Workflow failed.  Response was '" + workflowResponse);
        }   
        return errMsg.toString();
    }

    public static void updateAffectedResources(ViprOperation viprOperation) {
        if(viprOperation != null) {
            StringSet currentResources = ExecutionUtils.currentContext().
                    getExecutionState().getAffectedResources();
            for(ViprTask viprTask:viprOperation.getTask()) { 
                if(!currentResources.contains(viprTask.getResource().getId())) {
                    currentResources.add(viprTask.getResource().getId());
                }
            }
        }
    }

    public static void updateAffectedResources(AffectedResource[] affectedResources) {
        if(affectedResources != null) {
            StringSet currentResources = ExecutionUtils.currentContext().
                    getExecutionState().getAffectedResources();
            for(AffectedResource affectedResource : affectedResources) {
                if(!currentResources.contains(affectedResource.getId())) {  
                    ExecutionUtils.currentContext().logInfo("Adding " +
                            " completed resource '" + 
                            affectedResource.getName() + "'");
                    currentResources.add(affectedResource.getId());
                }
            }
        }
    }

    public static ViprOperation parseViprTasks(final String workflowResponse) {
        // When ViPR API returns Task(s), return them (in a ViPR Operation)
        try {
            // see if result contains array of Tasks
            final ViprOperation o = gson.fromJson(workflowResponse,ViprOperation.class);
            if(o.isValid()) {
                return o;
            }

            // see if response was a single Task
            final ViprTask t = gson.fromJson(workflowResponse,ViprTask.class);
            if(t.isValid()) {
                if (t.getState() != null) {
                    return new ViprOperation(t);
                }
            }
            return null;
        } catch(final JsonSyntaxException e) {
            return null;
        }
    }

    public static AffectedResource[] parseResourceList(String taskResult) {
        try {
            AffectedResource[] rArray = gson.fromJson(taskResult,AffectedResource[].class);
            for(AffectedResource r : rArray) {
                if(!r.isValid()) {
                    return null;
                }
            }
            return rArray;
        } catch(JsonSyntaxException e) {
            return null;
        }
    }

    public static OeStatusMessage parseOeStatusMessage(String workflowResponse) {
        OeStatusMessage oeStatusMessage = null;
        try {
            oeStatusMessage = gson.fromJson(workflowResponse,OeStatusMessage.class);
        } catch(JsonSyntaxException e) {
            return null;
        }
        return oeStatusMessage.isValid() ? oeStatusMessage : null;
    }

    public static List<TaskResourceRep> locateTasksInVipr(ViprOperation viprOperation, ViPRCoreClient client) {
        // given a response from OE representing an Operation with Tasks started by 
        // a OE workflow task, find corresponding tasks running in ViPR
        // (this is useful in cases like: a volume(s) was created from a OE workflow
        //  using the ViPR API, and now you want to find the tasks running in ViPR
        //  that corresponds to it/them.)  
        try {
            return client.tasks().getByIds(viprOperation.getTaskIds());
        }
        catch (URISyntaxException e) {
            ExecutionUtils.currentContext().logInfo("Warning: there was a " +
                    "problem locating tasks in ViPR that were initiated in " +
                    "the Orchestration Engine.  (Task IDs from OE are not valid.  " + 
                    e.getMessage());
            return new ArrayList<TaskResourceRep>();
        }  
    }

    public static Map<URI, String> waitForTasks(final List<URI> tasksStartedByOe, final ViPRCoreClient client) throws InternalServerErrorException {
        if (tasksStartedByOe.isEmpty()) {
		    throw InternalServerErrorException.internalServerErrors.customServiceNoTaskFound("No tasks to wait for");
        }
        ExecutionUtils.currentContext().logInfo("orchestrationService.waitforTask");

        final long startTime = System.currentTimeMillis();
        final TaskState states = new TaskState(client, tasksStartedByOe);

        while(states.hasPending()) {
            states.updateState();
            try {
                checkTimeout(startTime);
            } catch (final InternalServerErrorException e) {
                states.printTaskState();
                throw e;
            }
        }

        return states.getTaskState();
    }

    public static void checkTimeout(final long startTime) throws InternalServerErrorException {
        try {
            OrchestrationUtils.sleep(TASK_CHECK_INTERVAL);
            if ((System.currentTimeMillis() - startTime)
                    > TASK_CHECK_TIMEOUT * 60 * 1000) {
                throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Custom Service Task Timedout");
            }
        } catch (final InterruptedException e) {
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Custom Service Task is interrupted" + e);
        }
    }

    public static String makeRestCall(String uriString, OrchestrationEngineRestClient restClient) {
        return makeRestCall(uriString,null,restClient,null);
    }

    public static String makeRestCall(String uriString, String postBody,
            OrchestrationEngineRestClient restClient, String method) {

        ClientResponse response;
        if(method != null && method.equals("POST")) {
            response = restClient.post(URI.create(uriString),postBody);
        } else {
            response = restClient.get(URI.create(uriString));
        }

        String responseString = null;
        try {
            responseString = IOUtils.toString(response.getEntityInputStream(),"UTF-8");
        } catch (IOException e) {
            ExecutionUtils.currentContext().logError("Error getting response " +
                    "from Orchestration Engine for: " + uriString + " :: "+ e.getMessage());
        }
        return responseString;
    }

    public static String makeOrderJson(Map<String, Object> params) {

        // get workflow ID from input params (it must be associated with the Service and passed in)
        String workflowDefinitionId = params.get("workflow").toString();

        String workflowDefinition = null; // use ID to get definition from DB

        // temporarily insert test JSON here until DB calls are ready (Shane is doing DB work)
        workflowDefinition = "{\"workflow\": \"test JSON Workflow Goes Here\"}";

        CustomServicesWorkflowDocument workflowObj = gson.fromJson(workflowDefinition,CustomServicesWorkflowDocument.class);

        // add code here to insert values of parameters into object model of workflow
        // TODO:  insert params

        return gson.toJson(workflowObj);  // return JSON
    }

}
