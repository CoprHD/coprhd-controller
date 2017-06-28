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

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.primitives.CustomServicesConstants;
import com.emc.storageos.security.ssl.ViPRX509TrustManager;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.client.urlconnection.HTTPSProperties;

public final class BuildRestRequest {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(BuildRestRequest.class);

    private BuildRestRequest() {
    }

    public static Client makeClient(final ClientConfig config, final CoordinatorClient coordinator, final String auth, final String protocol, final String user, final String password)
            throws Exception {

        if (StringUtils.isEmpty(protocol)) {
            throw InternalServerErrorException.internalServerErrors.
                    customServiceExecutionFailed("Protocol not defined" + protocol);

        }
        if (!protocol.equals("https")) {
            logger.error("Only Https is supported. Protocol:{} is not supported", protocol);

            throw InternalServerErrorException.internalServerErrors.
                    customServiceExecutionFailed("Protocol not supported" + protocol);
        }

        final SSLContext context = SSLContext.getInstance("SSL");
        final ViPRX509TrustManager trustManager = new ViPRX509TrustManager(coordinator);

        context.init(null, new TrustManager[] { trustManager }, null);
        config.getProperties().put(HTTPSProperties.PROPERTY_HTTPS_PROPERTIES, new HTTPSProperties(null, context));

        final Client client = Client.create(config);

        if (auth.equals(CustomServicesConstants.AuthType.BASIC.name())) {
            if (!(StringUtils.isEmpty(user) && StringUtils.isEmpty(password))) {
                client.addFilter(new HTTPBasicAuthFilter(user, password));
            } else {
                logger.error("user:{} or password not defined", user);

                throw InternalServerErrorException.internalServerErrors.
                        customServiceExecutionFailed("User or password not defined");
            }
        }

        return client;
    }

    public static WebResource makeWebResource(final Client client, final String url) {
        final WebResource resource;
        if (StringUtils.isEmpty(url)) {
            logger.error("URL:{} cannot be null or empty", url);
            throw InternalServerErrorException.internalServerErrors.
                    customServiceExecutionFailed("URL:{} cannot be null or empty" + url);
        }
        
        return client.resource(url);
    }

    public static WebResource.Builder makeRequestBuilder(final WebResource resource, final CustomServicesWorkflowDocument.Step step, final Map<String, List<String>> input) {
        if (step.getInputGroups() == null || step.getInputGroups().get(CustomServicesConstants.HEADERS) == null) {
            return resource.getRequestBuilder();
        }
        final List<CustomServicesWorkflowDocument.Input> inputs = step.getInputGroups().get(CustomServicesConstants.HEADERS)
                .getInputGroup();
        if (inputs == null) {
            return resource.getRequestBuilder();
        }
        final WebResource.Builder builder = resource.getRequestBuilder();
        for (final CustomServicesWorkflowDocument.Input header : inputs) {
            final String name = header.getName();
            final List<String> value = input.get(name);

            if (value == null || StringUtils.isEmpty(value.get(0)) || StringUtils.isEmpty(StringUtils.strip(value.get(0).toString(), "\""))) {
                logger.warn("Cannot set value for header:{}", name);

                continue;
            }
            final String headerValue = StringUtils.strip(value.get(0).toString(), "\"");

            if (name.equals(CustomServicesConstants.ACCEPT_TYPE)) {
                builder.accept(headerValue);
            } else if (name.equals(CustomServicesConstants.CONTENT_TYPE)) {
                builder.type(headerValue);
            } else {
                builder.header(name, headerValue);
            }
        }

        return builder;
    }
}