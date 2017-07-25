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

package com.emc.sa.service.vipr.customservices;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.customservices.tasks.TaskState;
import com.emc.storageos.customservices.api.restapi.CustomServicesRestClient;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.emc.vipr.client.ViPRCoreClient;
import com.sun.jersey.api.client.ClientResponse;

//TODO: move log messages to separate file (for internationalization)

public class CustomServicesUtils {
    
    private CustomServicesUtils() {
        // no public constructor allwoed for utility classes
    }
    
    //TODO: externalize these values:
    private static final int TASK_CHECK_TIMEOUT = 3600;  // mins
    private static final int TASK_CHECK_INTERVAL = 10; // secs

    public static void sleep(int seconds) throws InterruptedException {
        try {
            Thread.sleep(seconds*1000);
        }
        catch (InterruptedException e) {
            throw e;
        }
    }
    
    public static Map<URI, String> waitForTasks(final List<URI> tasksStartedByOe, final ViPRCoreClient client) throws InternalServerErrorException {
        if (tasksStartedByOe.isEmpty()) {
		    throw InternalServerErrorException.internalServerErrors.customServiceNoTaskFound("No tasks to wait for");
        }
        ExecutionUtils.currentContext().logInfo("customServicesService.waitforTask");

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
            CustomServicesUtils.sleep(TASK_CHECK_INTERVAL);
            if ((System.currentTimeMillis() - startTime)
                    > TASK_CHECK_TIMEOUT * 60 * 1000) {
                throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Custom Service Task Timedout");
            }
        } catch (final InterruptedException e) {
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Custom Service Task is interrupted" + e);
        }
    }

    public static String makeRestCall(String uriString, CustomServicesRestClient restClient) {
        return makeRestCall(uriString,null,restClient,null);
    }

    public static String makeRestCall(String uriString, String postBody,
            CustomServicesRestClient restClient, String method) {

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
                    "from Custom Services for: " + uriString + " :: "+ e.getMessage());
        }
        return responseString;
    }

}
