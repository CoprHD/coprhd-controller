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

import static com.emc.sa.api.mapper.CustomServicesWorkflowMapper.map;
import static com.emc.sa.api.mapper.CustomServicesWorkflowMapper.mapList;

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
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.springframework.beans.factory.annotation.Autowired;

import com.emc.sa.api.mapper.CustomServicesWorkflowFilter;
import com.emc.sa.api.mapper.CustomServicesWorkflowMapper;
import com.emc.sa.catalog.CustomServicesWorkflowManager;
import com.emc.sa.workflow.ValidationHelper;
import com.emc.sa.workflow.WorkflowHelper;
import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.api.service.impl.response.BulkList.ResourceFilter;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.uimodels.CustomServicesWorkflow;
import com.emc.storageos.db.client.model.uimodels.CustomServicesWorkflow.CustomServicesWorkflowStatus;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.customservices.CustomServicesValidationResponse;
import com.emc.storageos.model.customservices.CustomServicesWorkflowBulkRep;
import com.emc.storageos.model.customservices.CustomServicesWorkflowCreateParam;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.model.customservices.CustomServicesWorkflowList;
import com.emc.storageos.model.customservices.CustomServicesWorkflowRestRep;
import com.emc.storageos.model.customservices.CustomServicesWorkflowUpdateParam;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

@DefaultPermissions(readRoles = { Role.TENANT_ADMIN, Role.SYSTEM_MONITOR, Role.SYSTEM_ADMIN }, writeRoles = {
        Role.TENANT_ADMIN }, readAcls = { ACL.ANY })
@Path("/workflows")
public class CustomServicesWorkflowService extends CatalogTaggedResourceService {

    @Autowired
    private CustomServicesWorkflowManager customServicesWorkflowManager;

    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public CustomServicesWorkflowList getWorkflows(@QueryParam("status") String status) {
        List<NamedElement> elements;
        if (null != status) {
            ArgValidator.checkFieldValueFromEnum(status, "status", CustomServicesWorkflowStatus.class);
            elements = customServicesWorkflowManager.listByStatus(CustomServicesWorkflowStatus.valueOf(status));
        } else {
            elements = customServicesWorkflowManager.list();
        }
        return mapList(elements);
    }

    @GET
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public CustomServicesWorkflowRestRep getWorkflow(@PathParam("id") final URI id) {
        return map(getCustomServicesWorkflow(id));
    }

    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public CustomServicesWorkflowRestRep addWorkflow(final CustomServicesWorkflowCreateParam workflow) {
        checkForDuplicateName(workflow.getDocument().getName(), CustomServicesWorkflow.class);
        final CustomServicesWorkflow newWorkflow;
        try {
            newWorkflow = WorkflowHelper.create(workflow.getDocument());
        } catch (IOException e) {
            throw APIException.internalServerErrors.genericApisvcError("Error serializing workflow", e);
        }

        customServicesWorkflowManager.save(newWorkflow);
        return map(newWorkflow);
    }

    @PUT
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public CustomServicesWorkflowRestRep updateWorkflow(@PathParam("id") final URI id, final CustomServicesWorkflowUpdateParam workflow) {
        final CustomServicesWorkflow updated;
        try {
            CustomServicesWorkflow customServicesWorkflow = getCustomServicesWorkflow(id);

            switch (CustomServicesWorkflowStatus.valueOf(customServicesWorkflow.getState())) {
                case PUBLISHED:
                    throw APIException.methodNotAllowed.notSupportedWithReason("Published workflow cannot be edited.");
                default:
                    updated = WorkflowHelper.update(customServicesWorkflow, workflow.getDocument());

                    // On update, if there is any change to steps, resetting workflow status to initial state -NONE
                    if (!customServicesWorkflow.getSteps().equals(updated.getSteps())) {
                        updated.setState(CustomServicesWorkflowStatus.NONE.toString());
                    }
            }

        } catch (IOException e) {
            throw APIException.internalServerErrors.genericApisvcError("Error serializing workflow", e);
        }
        customServicesWorkflowManager.save(updated);
        return map(updated);
    }

    @POST
    @Path("/{id}/deactivate")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public Response deactivateWorkflow(@PathParam("id") final URI id) {
        CustomServicesWorkflow customServicesWorkflow = getCustomServicesWorkflow(id);

        switch (CustomServicesWorkflowStatus.valueOf(customServicesWorkflow.getState())) {
            case PUBLISHED:
                // Published workflow cannot be deleted
                throw APIException.methodNotAllowed.notSupportedWithReason("Published workflow cannot be deleted.");
            default:
                customServicesWorkflowManager.delete(customServicesWorkflow);
                return Response.ok().build();
        }
    }

    @POST
    @Path("/{id}/publish")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public CustomServicesWorkflowRestRep publishWorkflow(@PathParam("id") final URI id) {
        CustomServicesWorkflow customServicesWorkflow = getCustomServicesWorkflow(id);
        switch (CustomServicesWorkflowStatus.valueOf(customServicesWorkflow.getState())) {
            case PUBLISHED:
                // If worklow is already in published state, ignoring
                return map(customServicesWorkflow);
            case VALID:
                // Workflow can only be published when it is in VALID state
                CustomServicesWorkflow updated = WorkflowHelper.updateState(customServicesWorkflow,
                        CustomServicesWorkflowStatus.PUBLISHED.toString());
                customServicesWorkflowManager.save(updated);
                return map(updated);
            default:
                throw APIException.methodNotAllowed.notSupportedWithReason(
                        String.format("Worklow cannot be published with its current state: %s", customServicesWorkflow.getState()));
        }
    }

    @POST
    @Path("/{id}/unpublish")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public CustomServicesWorkflowRestRep unpublishWorkflow(@PathParam("id") final URI id) {
        CustomServicesWorkflow customServicesWorkflow = getCustomServicesWorkflow(id);
        // Workflow can only be unpublished when it is in PUBLISHED state
        switch (CustomServicesWorkflowStatus.valueOf(customServicesWorkflow.getState())) {
            case VALID:
                // workflow is not published, ignoring
                return map(customServicesWorkflow);
            case PUBLISHED:
                // Check if there are any existing services created from this WF
                if (customServicesWorkflowManager.hasCatalogServices(customServicesWorkflow.getName())) {
                    throw APIException.methodNotAllowed
                            .notSupportedWithReason("Cannot unpublish workflow. It has associated catalog services");
                }
                CustomServicesWorkflow updated = WorkflowHelper.updateState(customServicesWorkflow,
                        CustomServicesWorkflowStatus.VALID.toString());
                customServicesWorkflowManager.save(updated);
                return map(updated);
            default:
                throw APIException.methodNotAllowed.notSupportedWithReason(
                        String.format("Worklow cannot be unpublished with its current state: %s", customServicesWorkflow.getState()));
        }
    }

    @POST
    @Path("/{id}/validate")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public CustomServicesValidationResponse validateWorkflow(@PathParam("id") final URI id) {
        try {
            final CustomServicesWorkflowDocument wfDocument = WorkflowHelper.toWorkflowDocument(getCustomServicesWorkflow(id));
            final ValidationHelper customServicesValidationHelper = new ValidationHelper(wfDocument);
            final CustomServicesValidationResponse validationResponse = customServicesValidationHelper.validate(id);
            // update the status of workflow VALID / INVALID in the DB
            final CustomServicesWorkflow wfstatusUpdated = WorkflowHelper.updateState(getCustomServicesWorkflow(id),
                    validationResponse.getStatus());
            customServicesWorkflowManager.save(wfstatusUpdated);
            return validationResponse;

        } catch (final IOException e) {
            throw APIException.internalServerErrors.genericApisvcError("Failed to deserialize workflow document", e);
        }
    }

    @POST
    @Path("/bulk")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public CustomServicesWorkflowBulkRep bulkGetWorkflows(final BulkIdParam ids) {
        return (CustomServicesWorkflowBulkRep) super.getBulkResources(ids);
    }

    @Override
    protected CustomServicesWorkflow queryResource(URI id) {
        return customServicesWorkflowManager.getById(id);
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.CUSTOM_SERVICES_WORKFLOW;
    }

    @Override
    public Class<CustomServicesWorkflow> getResourceClass() {
        return CustomServicesWorkflow.class;
    }

    @Override
    public CustomServicesWorkflowBulkRep queryBulkResourceReps(List<URI> ids) {
        Iterator<CustomServicesWorkflow> it = customServicesWorkflowManager.getSummaries(ids);
        return new CustomServicesWorkflowBulkRep(BulkList.wrapping(it, CustomServicesWorkflowMapper.getInstance()));
    }

    @Override
    public CustomServicesWorkflowBulkRep queryFilteredBulkResourceReps(List<URI> ids) {

        Iterator<CustomServicesWorkflow> it = customServicesWorkflowManager.getSummaries(ids);
        ResourceFilter<CustomServicesWorkflow> filter = new CustomServicesWorkflowFilter(getUserFromContext(), _permissionsHelper);

        return new CustomServicesWorkflowBulkRep(BulkList.wrapping(it, CustomServicesWorkflowMapper.getInstance(), filter));
    }

    private CustomServicesWorkflow getCustomServicesWorkflow(final URI id) {
        CustomServicesWorkflow workflow = queryResource(id);

        ArgValidator.checkEntityNotNull(workflow, id, true);

        return workflow;
    }

}
