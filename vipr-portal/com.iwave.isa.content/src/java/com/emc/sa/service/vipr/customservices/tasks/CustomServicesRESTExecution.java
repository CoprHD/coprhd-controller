/*
 * Copyright 2017 Dell Inc. or its subsidiaries.
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
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.UriBuilder;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.primitives.CustomServicesConstants;
import com.emc.storageos.primitives.db.restapi.CustomServicesRESTApiPrimitive;
import com.emc.storageos.primitives.input.InputParameter;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.uri.UriBuilderImpl;



public class CustomServicesRESTExecution extends ViPRExecutionTask<CustomServicesTaskResult> {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(CustomServicesRESTExecution.class);

    private final Map<String, List<String>> input;
    private final CustomServicesWorkflowDocument.Step step;

    private final CoordinatorClient coordinator;
    private final CustomServicesRESTApiPrimitive primitive;

    public CustomServicesRESTExecution(final Map<String, List<String>> input,
            final CustomServicesWorkflowDocument.Step step, final CoordinatorClient coordinator, final CustomServicesRESTApiPrimitive primitive) {
        this.input = input;
        this.step = step;
        this.coordinator = coordinator;
        this.primitive = primitive;

        provideDetailArgs(step.getId(), step.getFriendlyName());
    }

    @Override
    public CustomServicesTaskResult executeTask() throws Exception {
        try {
            ExecutionUtils.currentContext().logInfo("customServicesRESTExecution.startInfo", step.getId(), step.getFriendlyName());

            final Map<String, String> attrs = primitive.attributes();

            final String authType = attrs.get(CustomServicesConstants.AUTH_TYPE);
            if (StringUtils.isEmpty(authType)) {
                logger.error("Auth type cannot be undefined");
                ExecutionUtils.currentContext().logError("customServicesOperationExecution.logStatus", step.getId(), step.getFriendlyName(),
                        "Auth type cannot be undefined");
                throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Cannot find Auth type");
            }

            final Client client = BuildRestRequest.makeClient(new DefaultClientConfig(), coordinator, authType, attrs.get(CustomServicesConstants.PROTOCOL),
                    AnsibleHelper.getOptions(CustomServicesConstants.USER, input), AnsibleHelper.getOptions(CustomServicesConstants.PASSWORD, input));
            final Map<String, List<InputParameter>> inputKeys = primitive.input() == null ? Collections.emptyMap() : primitive.input();
            final List<InputParameter> queryParams = inputKeys.get(CustomServicesConstants.QUERY_PARAMS);
            final WebResource webResource = BuildRestRequest.makeWebResource(client, getUrl(primitive, queryParams).toString());
            final WebResource.Builder builder = BuildRestRequest.makeRequestBuilder(webResource, step, input);

            final CustomServicesConstants.RestMethods method =
                    CustomServicesConstants.RestMethods.valueOf(attrs.get(CustomServicesConstants.METHOD));

            final CustomServicesTaskResult result;
            switch (method) {
                case PUT:
                case POST:
                    final String body = RESTHelper.makePostBody(primitive.attributes().get(CustomServicesConstants.BODY),0, input);

                    result = executeRest(method, body, builder);
                    break;
                default:
                    result = executeRest(method, null, builder);
            }

            ExecutionUtils.currentContext().logInfo("customServicesRESTExecution.doneInfo", step.getId(), step.getFriendlyName());
            return result;
        } catch (final Exception e) {
            ExecutionUtils.currentContext().logError("customServicesRESTExecution.doneInfo", step.getId(), step.getFriendlyName() +
                    "Custom Service Task Failed" + e);
            logger.error("Exception:", e);
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Custom Service Task Failed" + e);
        }
    }

    private CustomServicesRestTaskResult executeRest(final CustomServicesConstants.RestMethods method, final String input,
            final WebResource.Builder builder) throws Exception {
        final ClientResponse response;
        switch (method) {
            case GET:
                response = builder.get(ClientResponse.class);
                break;
            case PUT:
                response = builder.put(ClientResponse.class, input);
                break;
            case POST:
                response = builder.post(ClientResponse.class, input);
                break;
            case DELETE:
                response = builder.delete(ClientResponse.class);
                break;
            default:
                logger.error("Rest method:{} type not supported", method.toString());
                throw InternalServerErrorException.internalServerErrors
                        .customServiceExecutionFailed("Rest method type not supported. Method:" + method.toString());
        }
        final String output = IOUtils.toString(response.getEntityInputStream(), "UTF-8");

        logger.info("result is:{} headers:{}", output, response.getHeaders());

        return new CustomServicesRestTaskResult(response.getHeaders().entrySet(), output, output, response.getStatus());
    }

    public URI getUrl(final CustomServicesRESTApiPrimitive primitive, final List<InputParameter> queryParams) {

        final String target = AnsibleHelper.getOptions(CustomServicesConstants.TARGET, input);
        final String path = primitive.attributes().get(CustomServicesConstants.PATH);
        String port = AnsibleHelper.getOptions(CustomServicesConstants.PORT, input);
        final String protocol =  primitive.attributes().get(CustomServicesConstants.PROTOCOL);


        if (StringUtils.isEmpty(target) || StringUtils.isEmpty(path) || StringUtils.isEmpty(protocol)) {
            logger.error("target/path/port is not defined. target:{}, path:{}, port:{}", target, path);

            ExecutionUtils.currentContext().logError("customServicesOperationExecution.logStatus", step.getId(), step.getFriendlyName(), "Cannot build URL");
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Cannot build URL");
        }

        if (StringUtils.isEmpty(port)) {
            logger.debug("port is not set. use default port: {}", CustomServicesConstants.DEFAULT_HTTPS_PORT);
            port = CustomServicesConstants.DEFAULT_HTTPS_PORT;
        }
        
        final UriBuilder builder = new UriBuilderImpl();
        builder.path(RESTHelper.makePath(path, input));
        builder.scheme(protocol);
        builder.host(target);
        builder.port(Integer.parseUnsignedInt(port));
        RESTHelper.addQueryParams(builder, queryParams, input);
        return builder.build();
    }
}
