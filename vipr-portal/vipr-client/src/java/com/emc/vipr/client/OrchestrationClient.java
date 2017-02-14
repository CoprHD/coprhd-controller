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
import com.emc.storageos.model.customservices.CustomServicesWorkflowCreateParam;
import com.emc.storageos.model.customservices.CustomServicesWorkflowList;
import com.emc.storageos.model.customservices.CustomServicesWorkflowRestRep;
import com.emc.storageos.model.customservices.CustomServicesWorkflowUpdateParam;
import com.emc.storageos.model.customservices.PrimitiveBulkRestRep;
import com.emc.storageos.model.customservices.PrimitiveCreateParam;
import com.emc.storageos.model.customservices.PrimitiveList;
import com.emc.storageos.model.customservices.PrimitiveResourceRestRep;
import com.emc.storageos.model.customservices.PrimitiveRestRep;
import com.emc.vipr.client.catalog.AbstractCatalogBulkResources;
import com.emc.vipr.client.catalog.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

/**
 * Client for OE APIs - primitives, workflows
 */
public class OrchestrationClient extends AbstractCatalogBulkResources<PrimitiveRestRep> {


    public OrchestrationClient(final ViPRCatalogClient2 parent, final RestClient client) {
        super(parent, client, PrimitiveRestRep.class, PathConstants.OE_PRIMITIVES);
    }

    public ViPRCatalogClient2 getParent() {
        return parent;
    }

    public RestClient getClient() {
        return client;
    }
    
    @Override
    protected List<PrimitiveRestRep> getBulkResources(BulkIdParam input) {
        PrimitiveBulkRestRep response = client.post(PrimitiveBulkRestRep.class, input, getBulkUrl());
        return defaultList(response.getPrimitives());
    }
    
    public PrimitiveList getPrimitives() {
        final UriBuilder builder = client.uriBuilder(PathConstants.OE_PRIMITIVES);
        return client.getURI(PrimitiveList.class, builder.build());
    }

    public PrimitiveList getPrimitivesByType(final String type) {
        final UriBuilder builder = client.uriBuilder(PathConstants.OE_PRIMITIVES);
        builder.queryParam("type", type);
        return client.getURI(PrimitiveList.class, builder.build());
    }

    public PrimitiveResourceRestRep createPrimitiveResource(final String resourceType, final File resource, final String resourceName) throws IOException{
        final UriBuilder builder = client.uriBuilder(PathConstants.OE_PRIMITIVE_RESOURCE);
        builder.queryParam("name", resourceName);
        return client.postURIOctet(PrimitiveResourceRestRep.class, new FileInputStream(resource), builder.build(resourceType));
    }

    public PrimitiveRestRep createPrimitive(final PrimitiveCreateParam param) {
        final UriBuilder builder = client.uriBuilder(PathConstants.OE_PRIMITIVES);
        return client.postURI(PrimitiveRestRep.class, param, builder.build());
    }
    
    public PrimitiveRestRep getPrimitive(final URI id) {
        return client.get(PrimitiveRestRep.class, PathConstants.OE_PRIMITIVE, id);
    }

    public CustomServicesWorkflowList getWorkflows() {
        UriBuilder builder = client.uriBuilder(PathConstants.OE_WORKFLOWS);
        return client.getURI(CustomServicesWorkflowList.class, builder.build());
    }

    public CustomServicesWorkflowRestRep getWorkflow(final URI id) {
        return client.get(CustomServicesWorkflowRestRep.class, PathConstants.OE_WORKFLOW, id);
    }

    public CustomServicesWorkflowRestRep validateWorkflow(final URI id) {
        return client.post(CustomServicesWorkflowRestRep.class,PathConstants.OE_WORKFLOW_VALIDATE,id);
    }

    public CustomServicesWorkflowRestRep publishWorkflow(final URI id) {
        return client.post(CustomServicesWorkflowRestRep.class,PathConstants.OE_WORKFLOW_PUBLISH,id);
    }

    public CustomServicesWorkflowRestRep unpublishWorkflow(final URI id) {
        return client.post(CustomServicesWorkflowRestRep.class,PathConstants.OE_WORKFLOW_UNPUBLISH,id);
    }

    public CustomServicesWorkflowRestRep createWorkflow(final CustomServicesWorkflowCreateParam param) {
        final UriBuilder builder = client.uriBuilder(PathConstants.OE_WORKFLOWS);
        return client.postURI(CustomServicesWorkflowRestRep.class,param, builder.build());
    }

    public CustomServicesWorkflowRestRep editWorkflow(final URI id, final CustomServicesWorkflowUpdateParam param) {
        return client.put(CustomServicesWorkflowRestRep.class,param,PathConstants.OE_WORKFLOW,id);
    }

    public void deleteWorkflow(final URI id) {
        client.post(String.class,PathConstants.OE_WORKFLOW_DELETE,id);
    }
}
