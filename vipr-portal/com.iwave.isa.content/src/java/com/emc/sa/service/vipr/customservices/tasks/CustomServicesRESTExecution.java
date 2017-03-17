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

import com.emc.sa.engine.ExecutionUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.primitives.CustomServicesConstants;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.sun.jersey.api.client.config.DefaultClientConfig;

public class CustomServicesRESTExecution extends ViPRExecutionTask<CustomServicesTaskResult> {

    @Autowired
    private final CoordinatorClient coordinator;
    private final Map<String, List<String>> input;
    private final CustomServicesWorkflowDocument.Step step;
    private final BuildRestRequest buildrest;
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(CustomServicesRESTExecution.class);

    public CustomServicesRESTExecution(final CoordinatorClient coordinator, final Map<String, List<String>> input,
                                        final CustomServicesWorkflowDocument.Step step) {
        this.coordinator = coordinator;
        this.input = input;
        this.step = step;
        buildrest = new BuildRestRequest(new DefaultClientConfig(), coordinator);
    }

    @Override
    public CustomServicesTaskResult executeTask() throws Exception {
        try {
            ExecutionUtils.currentContext().logInfo("customServicesRESTExecution.startInfo", step.getId());

            BuildRestRequest b = buildrest.setSSL(getOptions(CustomServicesConstants.PROTOCOL, input)).setUrl(getUrl());

            //TODO get it from primitive which are not runtime variable
            final String authType = getOptions(CustomServicesConstants.AUTH_TYPE, input);
            if (StringUtils.isEmpty(authType)) {
                logger.error("Auth type cannot be undefined");
                throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Cannot find Auth type");
            }

            if (authType.equals(CustomServicesConstants.BASIC_AUTH)) {
                b.setFilter(getOptions(CustomServicesConstants.USER, input), getOptions(CustomServicesConstants.PASSWORD, input));
            }

            b.setHeaders(step, input);

            final CustomServicesConstants.restMethods method =
                    CustomServicesConstants.restMethods.valueOf(getOptions(CustomServicesConstants.METHOD, input));
            switch (method) {
                case PUT:
                case POST:
                    final String body = RESTHelper.makePostBody(getOptions(CustomServicesConstants.BODY, input), input);
                    return b.executeRest(method, body);
            }

            ExecutionUtils.currentContext().logInfo("customServicesRESTExecution.doneInfo", step.getId());

            return b.executeRest(method, null);
        } catch (final Exception e) {
            logger.error("Received Exception:{}", e);
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Custom Service Task Failed" + e);
        }
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
