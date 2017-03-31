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

import java.util.List;
import java.util.Map;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.client.WebResource;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.primitives.CustomServicesConstants;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;

public class CustomServicesRESTExecution extends ViPRExecutionTask<CustomServicesTaskResult> {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(CustomServicesRESTExecution.class);

    private final Map<String, List<String>> input;
    private final CustomServicesWorkflowDocument.Step step;

    private final CoordinatorClient coordinator;

    public CustomServicesRESTExecution(final Map<String, List<String>> input,
            final CustomServicesWorkflowDocument.Step step, final CoordinatorClient coordinator) {
        this.input = input;
        this.step = step;
        this.coordinator = coordinator;
    }

    @Override
    public CustomServicesTaskResult executeTask() throws Exception {
        try {
            ExecutionUtils.currentContext().logInfo("customServicesRESTExecution.startInfo", step.getId());

            //TODO get it from primitive which are not runtime variable
            final String authType = getOptions(CustomServicesConstants.AUTH_TYPE, input);
            if (StringUtils.isEmpty(authType)) {
                logger.error("Auth type cannot be undefined");
                throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Cannot find Auth type");
            }

            final Client client = BuildRestRequest.makeClient(new DefaultClientConfig(), coordinator, authType, getOptions(CustomServicesConstants.PROTOCOL, input),
                    getOptions(CustomServicesConstants.USER, input), getOptions(CustomServicesConstants.PASSWORD, input));
            final WebResource webResource = BuildRestRequest.makeWebResource(client, getUrl(), null);
            final WebResource.Builder builder = BuildRestRequest.makeRequestBuilder(webResource, step, input);

            final CustomServicesConstants.RestMethods method =
                    CustomServicesConstants.RestMethods.valueOf(getOptions(CustomServicesConstants.METHOD, input));
            final CustomServicesTaskResult result;
            switch (method) {
                case PUT:
                case POST:
                    final String body = RESTHelper.makePostBody(getOptions(CustomServicesConstants.BODY, input), input);
                    result = executeRest(method, body, builder);
                    break;
                default:
                    result = executeRest(method, null, builder);
            }

            ExecutionUtils.currentContext().logInfo("customServicesRESTExecution.doneInfo", step.getId());
            return result;
        } catch (final Exception e) {
            ExecutionUtils.currentContext().logError("customServicesRESTExecution.doneInfo","Custom Service Task Failed" + e);
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Custom Service Task Failed" + e);
        }
    }

    private CustomServicesRestTaskResult executeRest(final CustomServicesConstants.RestMethods method, final String input, final WebResource.Builder builder) throws Exception {
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
                throw InternalServerErrorException.internalServerErrors.
                        customServiceExecutionFailed("Rest method type not supported. Method:" + method.toString());
        }
        final String output = IOUtils.toString(response.getEntityInputStream(), "UTF-8");

        logger.info("result is:{} headers:{}", output, response.getHeaders());

        return new CustomServicesRestTaskResult(response.getHeaders().entrySet(), output, output, response.getStatus());
    }

    public String getUrl() {
        //TODO get from primitive.
        final String target = getOptions(CustomServicesConstants.TARGET, input);
        final String path = getOptions(CustomServicesConstants.PATH, input);
        final String port = getOptions(CustomServicesConstants.PORT, input);
        final String protocol =  getOptions(CustomServicesConstants.PROTOCOL, input);

        if (StringUtils.isEmpty(target) || StringUtils.isEmpty(path) || StringUtils.isEmpty(port) || StringUtils.isEmpty(protocol)) {
            logger.error("target/path/port is not defined. target:{}, path:{}, port:{}", target, path, port);

            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Cannot build URL");
        }

        return String.format("%s://%s:%s/%s", protocol, target,port, RESTHelper.makePath(path, input));
    }

    private String getOptions(final String key, final Map<String, List<String>> input) {
        if (input.get(key) != null) {
            return StringUtils.strip(input.get(key).get(0).toString(), "\"");
        }

        logger.info("key not defined. key:{}", key);

        return null;
    }
}
