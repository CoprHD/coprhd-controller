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
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.UriBuilder;

import com.emc.storageos.db.client.model.Task;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.xc.JaxbAnnotationIntrospector;
import org.slf4j.LoggerFactory;

import com.emc.sa.catalog.primitives.CustomServicesPrimitiveDAOs;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.customservices.CustomServicesUtils;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.primitives.CustomServicesConstants;
import com.emc.storageos.primitives.CustomServicesPrimitiveType;
import com.emc.storageos.primitives.input.InputParameter;
import com.emc.storageos.primitives.java.vipr.CustomServicesViPRPrimitive;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.vipr.client.impl.RestClient;
import com.google.gson.Gson;
import com.sun.jersey.api.client.ClientResponse;

/**
 * This provides ability to run ViPR REST APIs
 */
public class CustomServicesViprExecution extends ViPRExecutionTask<CustomServicesTaskResult> {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(CustomServicesViprExecution.class);

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

    private Map<URI, String> waitForTask(final String result) throws Exception {
        final List<URI> uris = new ArrayList<URI>();

        final String classname = primitive.response();

        if (classname.contains(RESTHelper.TASKLIST)) {
            final ObjectMapper mapper = new ObjectMapper();
            mapper.setAnnotationIntrospector(new JaxbAnnotationIntrospector());
            final Class<?> clazz = Class.forName(classname);

            final Object taskList = mapper.readValue(result, clazz.newInstance().getClass());
            List<TaskResourceRep> resources = ((TaskList) taskList).getTaskList();

            for (TaskResourceRep res : resources) {
                uris.add(res.getId());
            }

        }
        if (!uris.isEmpty()) {
            return CustomServicesUtils.waitForTasks(uris, getClient());
        }

        return null;
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

            final Map<URI, String> taskState = waitForTask(responseString);
            //update state
            final String classname = primitive.response();
            if (classname.contains(RESTHelper.TASKLIST)) {
                responseString = updateState(responseString, taskState);
            }
            final boolean isSuccess = isSuccess(taskState, response.getStatus());
            if (!isSuccess) {
                throw InternalServerErrorException.internalServerErrors.
                        customServiceExecutionFailed("Failed to Execute ViPR request");
            }
            return new CustomServicesTaskResult(responseString, responseString, response.getStatus(), taskState);

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

    private boolean isSuccess(final Map<URI, String> states, final int returnCode) {

        if (states != null) {
            for (Map.Entry<URI, String> e : states.entrySet()) {
                if (!StringUtils.isEmpty(e.getValue())) {
                    if (e.getValue().equals(Task.Status.error.toString())) {
                        ExecutionUtils.currentContext().logError("customServicesService.logStatus",
                                "Step Id: " + step.getId() + "\t Step Name: " + step.getFriendlyName()
                                        + " Task Failed TaskId: " + e.getKey() + " State:" + e.getValue());
                        return false;
                    }
                }
            }
        }
        if (!(returnCode >= 200 && returnCode < 300)) {
            ExecutionUtils.currentContext().logError("customServicesService.logStatus",
                    "Step Id: " + step.getId() + "\t Step Name: " + step.getFriendlyName()
                            + " Operation Failed ReturnCode: " + returnCode);

            return false;
        }

        return true;

    }

    private String updateState(final String response, final Map<URI, String> uristates) {
        logger.info("response is:{}", response);
        final Gson gson = new Gson();
        final ViprOperation obj = gson.fromJson(response, ViprOperation.class);

        final List<ViprOperation.ViprTask> tasks = obj.getTask();
        for (final Map.Entry<URI, String> e : uristates.entrySet()) {
            logger.debug("uri:{} value:{}", e.getKey(), e.getValue());
            final URI uri = e.getKey();
            for (final ViprOperation.ViprTask t : tasks) {
                if(!StringUtils.isEmpty(t.getId()) && t.getId().equals(uri.toString())) {
                    logger.debug("Update the state");
                    t.setState(e.getValue());
                }
            }
        }

        final String finalResponse = gson.toJson(obj);
        logger.info("New result" + finalResponse);

        return finalResponse;
    }
}

