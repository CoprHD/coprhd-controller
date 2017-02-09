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

import com.emc.storageos.model.orchestration.OrchestrationWorkflowList;
import com.emc.storageos.model.orchestration.OrchestrationWorkflowRestRep;
import com.emc.storageos.model.orchestration.OrchestrationWorkflowCreateParam;
import com.emc.storageos.model.orchestration.OrchestrationWorkflowUpdateParam;
import com.emc.storageos.model.orchestration.PrimitiveList;
import com.emc.storageos.model.orchestration.PrimitiveCreateParam;
import com.emc.storageos.model.orchestration.PrimitiveRestRep;
import com.emc.storageos.model.orchestration.PrimitiveResourceRestRep;
import com.emc.vipr.client.catalog.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

import javax.ws.rs.core.UriBuilder;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;

/**
 * Client for OE APIs - primitives, workflows
 */
public class OrchestrationClient {
    private final ViPRCatalogClient2 parent;
    private final RestClient client;

    public OrchestrationClient(final ViPRCatalogClient2 parent, final RestClient client) {
        this.parent = parent;
        this.client = client;
    }

    public ViPRCatalogClient2 getParent() {
        return parent;
    }

    public RestClient getClient() {
        return client;
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

    public OrchestrationWorkflowList getWorkflows() {
        final UriBuilder builder = client.uriBuilder(PathConstants.OE_WORKFLOWS);
        return client.getURI(OrchestrationWorkflowList.class, builder.build());
    }

    public OrchestrationWorkflowRestRep getWorkflow(final URI id) {
        return client.get(OrchestrationWorkflowRestRep.class, PathConstants.OE_WORKFLOW, id);
    }

    public OrchestrationWorkflowRestRep validateWorkflow(final URI id) {
        return client.post(OrchestrationWorkflowRestRep.class,PathConstants.OE_WORKFLOW_VALIDATE,id);
    }

    public OrchestrationWorkflowRestRep publishWorkflow(final URI id) {
        return client.post(OrchestrationWorkflowRestRep.class,PathConstants.OE_WORKFLOW_PUBLISH,id);
    }

    public OrchestrationWorkflowRestRep unpublishWorkflow(final URI id) {
        return client.post(OrchestrationWorkflowRestRep.class,PathConstants.OE_WORKFLOW_UNPUBLISH,id);
    }

    public OrchestrationWorkflowRestRep createWorkflow(final OrchestrationWorkflowCreateParam param) {
        final UriBuilder builder = client.uriBuilder(PathConstants.OE_WORKFLOWS);
        return client.postURI(OrchestrationWorkflowRestRep.class,param, builder.build());
    }

    public OrchestrationWorkflowRestRep editWorkflow(final URI id, final OrchestrationWorkflowUpdateParam param) {
        return client.put(OrchestrationWorkflowRestRep.class,param,PathConstants.OE_WORKFLOW,id);
    }

    public void deleteWorkflow(final URI id) {
        client.post(String.class,PathConstants.OE_WORKFLOW_DELETE,id);
    }
}
