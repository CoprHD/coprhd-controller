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
package com.emc.vipr.client;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.List;

import javax.ws.rs.core.UriBuilder;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveBulkRestRep;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveCreateParam;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveList;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveResourceRestRep;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveResourceList;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveRestRep;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveUpdateParam;
import com.emc.storageos.model.customservices.CustomServicesValidationResponse;
import com.emc.storageos.model.customservices.CustomServicesWorkflowCreateParam;
import com.emc.storageos.model.customservices.CustomServicesWorkflowList;
import com.emc.storageos.model.customservices.CustomServicesWorkflowRestRep;
import com.emc.storageos.model.customservices.CustomServicesWorkflowUpdateParam;
import com.emc.vipr.client.catalog.AbstractCatalogBulkResources;
import com.emc.vipr.client.catalog.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;
import com.sun.jersey.api.client.ClientResponse;

/**
 * Client for Custom Services APIs - primitives, workflows
 */
public class CustomServicesClient extends AbstractCatalogBulkResources<CustomServicesPrimitiveRestRep> {

    public CustomServicesClient(final ViPRCatalogClient2 parent, final RestClient client) {
        super(parent, client, CustomServicesPrimitiveRestRep.class, PathConstants.CUSTOM_SERVICES_PRIMITIVES);
    }

    public ViPRCatalogClient2 getParent() {
        return parent;
    }

    public RestClient getClient() {
        return client;
    }

    @Override
    protected List<CustomServicesPrimitiveRestRep> getBulkResources(BulkIdParam input) {
        CustomServicesPrimitiveBulkRestRep response = client.post(CustomServicesPrimitiveBulkRestRep.class, input, getBulkUrl());
        return defaultList(response.getPrimitives());
    }

    public CustomServicesPrimitiveList getPrimitives() {
        final UriBuilder builder = client.uriBuilder(PathConstants.CUSTOM_SERVICES_PRIMITIVES);
        return client.getURI(CustomServicesPrimitiveList.class, builder.build());
    }

    public CustomServicesPrimitiveList getPrimitivesByType(final String type) {
        final UriBuilder builder = client.uriBuilder(PathConstants.CUSTOM_SERVICES_PRIMITIVES);
        builder.queryParam("type", type);
        return client.getURI(CustomServicesPrimitiveList.class, builder.build());
    }

    public CustomServicesPrimitiveResourceList getPrimitiveResourcesByType(final String type, final URI parentId) {
        final UriBuilder builder = client.uriBuilder(PathConstants.CUSTOM_SERVICES_PRIMITIVE_RESOURCES);
        builder.queryParam("type", type);
        if (null != parentId) {
            builder.queryParam("parentId", parentId);
        }
        return client.getURI(CustomServicesPrimitiveResourceList.class, builder.build());
    }

    public CustomServicesPrimitiveResourceRestRep createPrimitiveResource(final String resourceType, final File resource,
            final String resourceName) throws IOException {
        return createPrimitiveResource(resourceType, resource, resourceName, null);
    }

    public CustomServicesPrimitiveResourceRestRep createPrimitiveResource(final String resourceType, final File resource,
            final String resourceName, final URI ansiblePackageId) throws IOException {
        final UriBuilder builder = client.uriBuilder(PathConstants.CUSTOM_SERVICES_PRIMITIVE_RESOURCES);
        builder.queryParam("name", resourceName);
        builder.queryParam("type", resourceType);
        if (null != ansiblePackageId) {
            builder.queryParam("parentId", ansiblePackageId);
        }
        return client.postURIOctet(CustomServicesPrimitiveResourceRestRep.class, new FileInputStream(resource), builder.build());
    }

    public CustomServicesPrimitiveRestRep createPrimitive(final CustomServicesPrimitiveCreateParam param) {
        final UriBuilder builder = client.uriBuilder(PathConstants.CUSTOM_SERVICES_PRIMITIVES);
        return client.postURI(CustomServicesPrimitiveRestRep.class, param, builder.build());
    }

    public CustomServicesPrimitiveRestRep getPrimitive(final URI id) {
        return client.get(CustomServicesPrimitiveRestRep.class, PathConstants.CUSTOM_SERVICES_PRIMITIVE, id);
    }

    public CustomServicesPrimitiveRestRep updatePrimitive(final URI id, CustomServicesPrimitiveUpdateParam param) {
        return client.put(CustomServicesPrimitiveRestRep.class, param, PathConstants.CUSTOM_SERVICES_PRIMITIVE, id);
    }

    public CustomServicesWorkflowList getWorkflows() {
        final UriBuilder builder = client.uriBuilder(PathConstants.CUSTOM_SERVICES_WORKFLOWS);
        return client.getURI(CustomServicesWorkflowList.class, builder.build());
    }

    public CustomServicesWorkflowList getWorkflows(String primitiveId) {
        final UriBuilder builder = client.uriBuilder(PathConstants.CUSTOM_SERVICES_WORKFLOWS);
        builder.queryParam("primitiveId", primitiveId);
        return client.getURI(CustomServicesWorkflowList.class, builder.build());
    }

    public CustomServicesWorkflowRestRep getWorkflow(final URI id) {
        return client.get(CustomServicesWorkflowRestRep.class, PathConstants.CUSTOM_SERVICES_WORKFLOW, id);
    }

    public CustomServicesValidationResponse validateWorkflow(final URI id) {
        return client.post(CustomServicesValidationResponse.class, PathConstants.CUSTOM_SERVICES_WORKFLOW_VALIDATE, id);
    }

    public CustomServicesWorkflowRestRep publishWorkflow(final URI id) {
        return client.post(CustomServicesWorkflowRestRep.class, PathConstants.CUSTOM_SERVICES_WORKFLOW_PUBLISH, id);
    }

    public CustomServicesWorkflowRestRep unpublishWorkflow(final URI id) {
        return client.post(CustomServicesWorkflowRestRep.class, PathConstants.CUSTOM_SERVICES_WORKFLOW_UNPUBLISH, id);
    }

    public CustomServicesWorkflowRestRep createWorkflow(final CustomServicesWorkflowCreateParam param) {
        final UriBuilder builder = client.uriBuilder(PathConstants.CUSTOM_SERVICES_WORKFLOWS);
        return client.postURI(CustomServicesWorkflowRestRep.class, param, builder.build());
    }

    public CustomServicesWorkflowRestRep editWorkflow(final URI id, final CustomServicesWorkflowUpdateParam param) {
        return client.put(CustomServicesWorkflowRestRep.class, param, PathConstants.CUSTOM_SERVICES_WORKFLOW, id);
    }

    public void deleteWorkflow(final URI id) {
        client.post(String.class, PathConstants.CUSTOM_SERVICES_WORKFLOW_DELETE, id);
    }

    public ClientResponse deletePrimitive(final URI id) {
        final ClientResponse response = client.post(ClientResponse.class, PathConstants.CUSTOM_SERVICES_PRIMITIVE_DELETE, id);
        return response;
    }

    public ClientResponse deletePrimitiveResource(final URI id) {
        final ClientResponse response = client.post(ClientResponse.class, PathConstants.CUSTOM_SERVICES_PRIMITIVE_RESOURCE_DELETE, id);
        return response;
    }

    public CustomServicesPrimitiveResourceRestRep getPrimitiveResource(final URI id) {
        return client.get(CustomServicesPrimitiveResourceRestRep.class, PathConstants.CUSTOM_SERVICES_PRIMITIVE_RESOURCE, id);
    }

    public ClientResponse exportWorkflow(final URI workflowId) {
        return client.get(ClientResponse.class, PathConstants.CUSTOM_SERVICES_WORKFLOW_EXPORT, workflowId);
    }

    public CustomServicesWorkflowRestRep importWorkflow(final URI directoryId, final File workflow) throws IOException {
        final UriBuilder builder = client.uriBuilder(PathConstants.CUSTOM_SERVICES_WORKFLOW_IMPORT);
        if (null != directoryId) {
            builder.queryParam("directory", directoryId);
        }
        return client.postURIOctet(CustomServicesWorkflowRestRep.class, new FileInputStream(workflow), builder.build());
    }
}
