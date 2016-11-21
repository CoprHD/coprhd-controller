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
package com.emc.sa.api;

import static com.emc.sa.api.mapper.OrchestrationWorkflowMapper.map;
import static com.emc.sa.api.mapper.OrchestrationWorkflowMapper.mapList;

import java.io.IOException;
import java.net.URI;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.springframework.beans.factory.annotation.Autowired;

import com.emc.sa.api.mapper.OrchestrationWorkflowFilter;
import com.emc.sa.api.mapper.OrchestrationWorkflowMapper;
import com.emc.sa.catalog.OrchestrationWorkflowManager;
import com.emc.sa.workflow.WorkflowHelper;
import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.api.service.impl.response.BulkList.ResourceFilter;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.uimodels.OrchestrationWorkflow;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.orchestration.OrchestrationWorkflowBulkRep;
import com.emc.storageos.model.orchestration.OrchestrationWorkflowCreateParam;
import com.emc.storageos.model.orchestration.OrchestrationWorkflowList;
import com.emc.storageos.model.orchestration.OrchestrationWorkflowRestRep;
import com.emc.storageos.model.orchestration.OrchestrationWorkflowUpdateParam;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

@DefaultPermissions(
        readRoles = { Role.TENANT_ADMIN, Role.SYSTEM_MONITOR, Role.SYSTEM_ADMIN },
        writeRoles = { Role.TENANT_ADMIN },
        readAcls = { ACL.ANY })
@Path("/workflows")
public class OrchestrationWorkflowService extends CatalogTaggedResourceService {

    @Autowired
    private OrchestrationWorkflowManager orchestrationWorkflowManager;
    
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public OrchestrationWorkflowList getWorkflows() {
        List<NamedElement> elements = orchestrationWorkflowManager.list();
        return mapList(elements);
    }
    
    @GET
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public OrchestrationWorkflowRestRep getWorkflow(@PathParam("id") final URI id) {
        return map(getOEWorkflow(id));
    }
    
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public OrchestrationWorkflowRestRep addWorkflow(final OrchestrationWorkflowCreateParam workflow) {
        checkForDuplicateName(workflow.getDocument().getName(), OrchestrationWorkflow.class);
        final OrchestrationWorkflow newWorkflow;
        try {
            newWorkflow = WorkflowHelper.create(workflow.getDocument());
        } catch (IOException e) {
            throw APIException.internalServerErrors.genericApisvcError("Error serializing workflow", e);
        }
        
        orchestrationWorkflowManager.save(newWorkflow);
        return map(newWorkflow);
    }
    
    @PUT
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public OrchestrationWorkflowRestRep updateWorkflow(@PathParam("id") final URI id, final OrchestrationWorkflowUpdateParam workflow) {  
        final OrchestrationWorkflow updated;
        try {
            updated = WorkflowHelper.update(getOEWorkflow(id), workflow.getDocument()); 
        } catch (IOException e) {
            throw APIException.internalServerErrors.genericApisvcError("Error serializing workflow", e);
        }
        orchestrationWorkflowManager.save(updated);
        return map(updated);
    }
    
    @POST
    @Path("/{id}/deactivate")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public Response deactivateWorkflow(@PathParam("id") final URI id) {
        orchestrationWorkflowManager.delete(getOEWorkflow(id));
        
        return Response.ok().build();
    }

    @POST
    @Path("/bulk")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public OrchestrationWorkflowBulkRep bulkGetWorkflows(final BulkIdParam ids) {
        return (OrchestrationWorkflowBulkRep) super.getBulkResources(ids);
    }
    
    @Override
    protected OrchestrationWorkflow queryResource(URI id) {
        return orchestrationWorkflowManager.getById(id);
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.ORCHESTRATION_WORKFLOW;
    }
    
    @Override
    public Class<OrchestrationWorkflow> getResourceClass() {
        return OrchestrationWorkflow.class;
    }

    @Override
    public OrchestrationWorkflowBulkRep queryBulkResourceReps(List<URI> ids) {
        Iterator<OrchestrationWorkflow> it = orchestrationWorkflowManager.getSummaries(ids);
        return new OrchestrationWorkflowBulkRep(BulkList.wrapping(it, OrchestrationWorkflowMapper.getInstance()));
    }
    
    @Override
    public OrchestrationWorkflowBulkRep queryFilteredBulkResourceReps(List<URI> ids) {

        Iterator<OrchestrationWorkflow> it = orchestrationWorkflowManager.getSummaries(ids);
        ResourceFilter<OrchestrationWorkflow> filter = new OrchestrationWorkflowFilter(getUserFromContext(), _permissionsHelper);
        
        return new OrchestrationWorkflowBulkRep(BulkList.wrapping(it, OrchestrationWorkflowMapper.getInstance(), filter));
    }
    
    private OrchestrationWorkflow getOEWorkflow(final URI id) {
        OrchestrationWorkflow workflow = queryResource(id);

        ArgValidator.checkEntityNotNull(workflow, id, true);

        return workflow;
    }
    
}
