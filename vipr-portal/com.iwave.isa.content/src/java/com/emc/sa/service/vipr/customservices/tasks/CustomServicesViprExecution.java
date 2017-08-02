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

package com.emc.sa.service.vipr.customservices.tasks;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.UriBuilder;

import org.apache.commons.io.IOUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.xc.JaxbAnnotationIntrospector;
import org.slf4j.LoggerFactory;

import com.emc.sa.catalog.primitives.CustomServicesPrimitiveDAOs;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument.Step;
import com.emc.storageos.primitives.CustomServicesConstants;
import com.emc.storageos.primitives.CustomServicesPrimitiveType;
import com.emc.storageos.primitives.input.InputParameter;
import com.emc.storageos.primitives.java.vipr.CustomServicesViPRPrimitive;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.impl.RestClient;
import com.sun.jersey.api.client.ClientResponse;

/**
 * This provides ability to run ViPR REST APIs
 */
public class CustomServicesViprExecution extends ViPRExecutionTask<CustomServicesTaskResult> {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(CustomServicesViprExecution.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().setAnnotationIntrospector(new JaxbAnnotationIntrospector());
    
    private final Map<String, List<String>> input;
    private final RestClient client;
    private final CustomServicesViPRPrimitive primitive;
    private final CustomServicesWorkflowDocument.Step step;

    public CustomServicesViprExecution(final Map<String, List<String>> input, final CustomServicesWorkflowDocument.Step step,
            final CustomServicesPrimitiveDAOs daos, final RestClient client) {
        this.input = input;
        this.step = step;
        if (daos.get(CustomServicesConstants.VIPR_PRIMITIVE_TYPE) == null) {
            logger.error("ViPR operation DAO not found");
            ExecutionUtils.currentContext().logError("customServicesOperationExecution.logStatus", step.getId(), step.getFriendlyName(), "ViPR operation DAO not found");
            throw InternalServerErrorException.internalServerErrors
                    .customServiceExecutionFailed("ViPR operation DAO not found: " + step.getOperation());
        }
        final CustomServicesPrimitiveType primitive = daos.get(CustomServicesConstants.VIPR_PRIMITIVE_TYPE).get(step.getOperation());

        if (null == primitive) {
            ExecutionUtils.currentContext().logError("customServicesOperationExecution.logStatus", step.getId(), step.getFriendlyName(), "Primitive not found: " + step.getOperation());
            throw InternalServerErrorException.internalServerErrors
                    .customServiceExecutionFailed("Primitive not found: " + step.getOperation());
        }
        this.primitive = (CustomServicesViPRPrimitive) primitive;
        this.client = client;
        provideDetailArgs(step.getId(), step.getFriendlyName());
    }

    @Override
    public CustomServicesTaskResult executeTask() throws Exception {

        final String requestBody;

        final String templatePath = primitive.path();
        final String body = primitive.body();
        final String method = primitive.method();
        final Map<String, List<InputParameter>> inputKeys = primitive.input() == null ? Collections.emptyMap() : primitive.input();
        if (CustomServicesConstants.BODY_REST_METHOD.contains(method) && !body.isEmpty()) {
            requestBody = RESTHelper.makePostBody(body, 0, input);
        } else {
            requestBody = "";
        }

        final List<InputParameter> queryParams = inputKeys.get(CustomServicesConstants.QUERY_PARAMS);
        final UriBuilder builder = client.uriBuilder().path(RESTHelper.makePath(templatePath, input));
        RESTHelper.addQueryParams(builder, queryParams, input);
        
        ExecutionUtils.currentContext().logInfo("customServicesViprExecution.startInfo", step.getId(), primitive.friendlyName());

        CustomServicesTaskResult result = makeRestCall(builder.build(), requestBody, method);

        logger.info("result is:{}", result.getOut());
        ExecutionUtils.currentContext().logInfo("customServicesViprExecution.doneInfo", step.getId(), primitive.friendlyName());

        return result;
    }

    private CustomServicesTaskResult makeRestCall(final URI uri, final Object requestBody, final String method)
            throws InternalServerErrorException {

        ClientResponse response = null;
        String responseString = null;
        CustomServicesConstants.RestMethods restmethod = CustomServicesConstants.RestMethods.valueOf(method);

        try {
            switch (restmethod) {
                case GET:
                    response = client.getURI(ClientResponse.class, uri);
                    break;
                case PUT:
                    response = client.putURI(ClientResponse.class, requestBody, uri);
                    break;
                case POST:
                    response = client.postURI(ClientResponse.class, requestBody, uri);
                    break;
                case DELETE:
                    response = client.deleteURI(ClientResponse.class, uri);
                    break;
                default:
                    throw InternalServerErrorException.internalServerErrors
                            .customServiceExecutionFailed("Invalid REST method type" + method);
            }

            if (response == null) {
                ExecutionUtils.currentContext().logError("customServicesOperationExecution.logStatus", step.getId(), step.getFriendlyName(),
                        "REST Execution Failed. Response returned is null");

                throw InternalServerErrorException.internalServerErrors.
                        customServiceExecutionFailed("REST Execution Failed. Response returned is null");
            }

            logger.info("Status of ViPR REST Operation:{} is :{}", primitive.name(), response.getStatus());

            responseString = IOUtils.toString(response.getEntityInputStream(), "UTF-8");
            if( response.getStatus() >= 300 ) {
                throw InternalServerErrorException.internalServerErrors.
                customServiceExecutionFailed("Failed to Execute ViPR request");
            }
            
            final Class<?> responseEntityType = Class.forName(primitive.response());
            final Map<String, List<String>> output;
            if( responseEntityType.isAssignableFrom(TaskResourceRep.class)) {
                output = getTaskResults(MAPPER.readValue(responseString, TaskResourceRep.class));
            } else if (responseEntityType.isAssignableFrom(TaskList.class)){
                output = getTaskResults(MAPPER.readValue(responseString, TaskList.class));
            } else {
                output = parseViprOutput(MAPPER.readValue(responseString, responseEntityType), step);
            }

            return new CustomServicesTaskResult(responseString, responseString, response.getStatus(), output);

        } catch (final InternalServerErrorException e) {

            logger.warn("Exception received:{}", e);

            if (e.getServiceCode().getCode() == ServiceCode.CUSTOM_SERVICE_NOTASK.getCode()) {
                return new CustomServicesTaskResult(responseString, responseString, response.getStatus(), null);
            }
            ExecutionUtils.currentContext().logError("customServicesOperationExecution.logStatus", step.getId(), step.getFriendlyName(), e);
            throw InternalServerErrorException.internalServerErrors.
                    customServiceExecutionFailed("Failed to Execute REST request" + e.getMessage());
        } catch (final Exception e) {
            logger.warn("Exception:", e);
            ExecutionUtils.currentContext().logError("customServicesOperationExecution.logStatus", step.getId(), step.getFriendlyName(), e);
            throw InternalServerErrorException.internalServerErrors.
                    customServiceExecutionFailed("REST Execution Failed" + e.getMessage());
        }
    }

    
    private Map<String, List<String>> getTaskResults(final TaskResourceRep taskResourceRep) throws Exception {
        final Task<ClientResponse> task = new Task<ClientResponse>(client, taskResourceRep, ClientResponse.class);
        tagTask(task);
        task.waitFor();
        
        return parseViprOutput(task.getTaskResource(), step);
    }
    
    private Map<String, List<String>> getTaskResults(final TaskList taskList) throws Exception {
        final Tasks<ClientResponse> tasks = new Tasks<ClientResponse>(client, taskList.getTaskList(), ClientResponse.class);
        for (final Task<ClientResponse> task : tasks.getTasks()) {
            tagTask(task);
        }
        
        tasks.waitFor();
        
        final TaskList finishedTasks = new TaskList(new ArrayList<TaskResourceRep>());
        for (final Task<ClientResponse> task : tasks.getTasks()) {
            finishedTasks.addTask(task.getTaskResource());
        }
        
        return parseViprOutput(finishedTasks, step);
    }
    
    private void tagTask(final Task<ClientResponse> task ) {
        addOrderIdTag(task.getTaskResource().getId());
        info("Waiting for task to complete: %s on resource: %s", task.getOpId(), task.getResourceId());
    }
    
    private Map<String, List<String>> parseViprOutput(final Object responseEntity, final Step step) throws Exception {
        final List<CustomServicesWorkflowDocument.Output> stepOut = step.getOutput();

        final Map<String, List<String>> output = new HashMap<String, List<String>>();
        for (final CustomServicesWorkflowDocument.Output out : stepOut) {
            final String outName = out.getName();
            logger.debug("output to parse:{}", outName);

            final String[] bits = outName.split("\\.");

            // Start parsing at i=1 because the name of the root
            // element is not included in the JSON
            final List<String> list = RESTHelper.parserOutput(bits, 1, responseEntity);
            if (list != null) {
                output.put(out.getName(), list);
            }
        }

        return output;
    }
    
    
}

