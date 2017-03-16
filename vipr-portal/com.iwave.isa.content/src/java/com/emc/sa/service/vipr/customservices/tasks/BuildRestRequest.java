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
import org.apache.commons.io.IOUtils;
import org.slf4j.LoggerFactory;

import com.emc.sa.service.vipr.customservices.CustomServicesConstants;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.security.ssl.ViPRX509TrustManager;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.client.urlconnection.HTTPSProperties;

public class BuildRestRequest {

    private final ClientConfig config;
    private final CoordinatorClient coordinator;
    private Client client;
    private WebResource.Builder builder = null;
    private WebResource resource;

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(BuildRestRequest.class);

    public BuildRestRequest(final ClientConfig config, final CoordinatorClient coordinator) {
        this.config = config;
        this.coordinator = coordinator;
    }

    private WebResource.Builder getBuilder() {
        if (builder != null) {
            return builder;
        }
        builder = resource.getRequestBuilder();

        return builder;
    }

    public BuildRestRequest setSSL(final String protocol) throws Exception {

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

        client = Client.create(config);

        return this;
    }

    public BuildRestRequest setHeaders(final CustomServicesWorkflowDocument.Step step, final Map<String, List<String>> input) {
        if (step.getInputGroups() == null || step.getInputGroups().get(CustomServicesConstants.HEADERS) == null) {
            return this;
        }
        final List<CustomServicesWorkflowDocument.Input> inputs = step.getInputGroups().get(CustomServicesConstants.HEADERS)
                .getInputGroup();
        if (inputs == null) {
            return this;
        }
        for (final CustomServicesWorkflowDocument.Input header : inputs) {
            final String name = header.getName();
            final List<String> value = input.get(name);

            if (value == null || StringUtils.isEmpty(value.get(0))) {
                logger.error("Cannot set value for header:{}", name);

                throw InternalServerErrorException.internalServerErrors.
                        customServiceExecutionFailed("Cannot set value for header:" + name);
            }
            final String headerValue = StringUtils.strip(value.get(0).toString(), "\"");
            if (name.equals(CustomServicesConstants.ACCEPT_TYPE)) {
                setAccept(headerValue);
            } else if (name.equals(CustomServicesConstants.CONTENT_TYPE)) {
                setContentType(headerValue);
            } else {
                getBuilder().header(name, headerValue);
            }
        }

        return this;
    }

    private BuildRestRequest setContentType(final String contentType) {
        if (StringUtils.isEmpty(contentType)) {
            return this;
        }
        getBuilder().type(contentType);

        return this;
    }

    private BuildRestRequest setAccept(final String acceptType) {
        if (StringUtils.isEmpty(acceptType)) {
            return this;
        }
        getBuilder().accept(acceptType);

        return this;
    }

    //TODO Implement this.
    public BuildRestRequest setQueryparam(final Map<String, String> queries) {
        for (final Map.Entry<String, String> entry : queries.entrySet()) {
            resource.queryParam(entry.getKey(), entry.getValue());
        }

        return this;
    }

    public BuildRestRequest setUrl(final String url) {
        if (!StringUtils.isEmpty(url)) {
            this.resource = client.resource(url);
        }

        return this;
    }

    public BuildRestRequest setFilter(final String user, final String password) {
        if (!(StringUtils.isEmpty(user) && StringUtils.isEmpty(password))) {
            client.addFilter(new HTTPBasicAuthFilter(user, password));

            return this;
        }

        logger.error("user:{} or password:{} not defined", user, password);

        throw InternalServerErrorException.internalServerErrors.
                customServiceExecutionFailed("User or password not defined");
    }

    public CustomServicesRestTaskResult executeRest(final CustomServicesConstants.restMethods method, final String input) throws Exception {
        ClientResponse response = null;
        switch (method) {
            case GET:
                response = getBuilder().get(ClientResponse.class);
                break;
            case PUT:
                response = getBuilder().put(ClientResponse.class, input);
                break;
            case POST:
                response = getBuilder().post(ClientResponse.class, input);
                break;
            case DELETE:
                response = getBuilder().delete(ClientResponse.class);
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
}