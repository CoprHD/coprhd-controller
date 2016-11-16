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

import java.io.IOException;
import java.net.URI;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.springframework.beans.factory.annotation.Autowired;

import com.emc.sa.catalog.OrchestrationWorkflowManager;
import com.emc.sa.workflow.WorkflowHelper;
import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.db.client.model.uimodels.OEWorkflow;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.orchestration.OrchestrationWorkflowCreateParam;
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
@Path("/workflow")
public class OrchestrationWorkflowService extends CatalogTaggedResourceService {

    @Autowired
    private OrchestrationWorkflowManager orchestrationWorkflowManager;
    
    @GET
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public OrchestrationWorkflowRestRep getWorkflow(@PathParam("id") final URI id) {
        return map(getOEWorkflow(id));
    }
    
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public OrchestrationWorkflowRestRep addWorkflow(final OrchestrationWorkflowCreateParam workflow) {
        checkForDuplicateName(workflow.getDocument().getName(), OEWorkflow.class);
        final OEWorkflow newWorkflow;
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
        final OEWorkflow updated;
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

    @Override
    protected OEWorkflow queryResource(URI id) {
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
    

    private OEWorkflow getOEWorkflow(final URI id) {
        OEWorkflow workflow = queryResource(id);

        ArgValidator.checkEntityNotNull(workflow, id, true);

        return workflow;
    }
    
}
