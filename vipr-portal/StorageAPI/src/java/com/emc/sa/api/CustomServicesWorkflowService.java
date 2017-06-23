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

import static com.emc.sa.workflow.CustomServicesWorkflowMapper.map;
import static com.emc.sa.workflow.CustomServicesWorkflowMapper.mapList;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.sa.api.mapper.CustomServicesWorkflowFilter;
import com.emc.sa.catalog.CustomServicesWorkflowManager;
import com.emc.sa.catalog.WorkflowDirectoryManager;
import com.emc.sa.catalog.primitives.CustomServicesPrimitiveDAOs;
import com.emc.sa.catalog.primitives.CustomServicesResourceDAOs;
import com.emc.sa.model.dao.ModelClient;
import com.emc.sa.workflow.CustomServicesWorkflowMapper;
import com.emc.sa.workflow.ValidationHelper;
import com.emc.sa.workflow.WorkflowHelper;
import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.api.service.impl.response.BulkList.ResourceFilter;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.uimodels.CustomServicesWorkflow;
import com.emc.storageos.db.client.model.uimodels.CustomServicesWorkflow.CustomServicesWorkflowStatus;
import com.emc.storageos.db.client.model.uimodels.WFDirectory;
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
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.security.keystore.impl.KeyStoreUtil;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN }, readAcls = { ACL.OWN, ACL.ALL }, writeRoles = {
        Role.SYSTEM_ADMIN }, writeAcls = { ACL.OWN, ACL.ALL })
@Path("/customservices/workflows")
public class CustomServicesWorkflowService extends CatalogTaggedResourceService {

    private static final Logger log = LoggerFactory.getLogger(CustomServicesWorkflowService.class);
    private static final WFDirectory NO_DIR = new WFDirectory();
    private static final String EXPORT_EXTENSION = ".wf";
    @Autowired
    private ModelClient client;
    @Autowired
    private CustomServicesWorkflowManager customServicesWorkflowManager;
    @Autowired
    private WorkflowDirectoryManager wfDirectoryManager;
    @Autowired
    private CustomServicesPrimitiveDAOs daos;
    @Autowired
    private CustomServicesResourceDAOs resourceDAOs;
    @Autowired
    private CoordinatorClient coordinator;

    @GET
    @CheckPermission(roles = { Role.SYSTEM_ADMIN })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public CustomServicesWorkflowList getWorkflows(@QueryParam("status") String status, @QueryParam("primitiveId") String primitiveId) {
        List<NamedElement> elements;
        if (null != status && null != primitiveId) {
            // TODO: currently throwing exception. Implement if both status and primitive id are passed, get the workflows that are in the
            // requested status state and that uses the primitiveId
            throw APIException.methodNotAllowed
                    .notSupportedWithReason("Querying workflow by both status and primitives are not supported currently.");
        }
        if (null != status) {
            ArgValidator.checkFieldValueFromEnum(status, "status", CustomServicesWorkflowStatus.class);
            elements = customServicesWorkflowManager.listByStatus(CustomServicesWorkflowStatus.valueOf(status));
        } else if (null != primitiveId) {
            elements = customServicesWorkflowManager.listByPrimitiveUsed(URI.create(primitiveId));
        } else {
            elements = customServicesWorkflowManager.list();
        }
        return mapList(elements);
    }

    @GET
    @CheckPermission(roles = { Role.SYSTEM_ADMIN })
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public CustomServicesWorkflowRestRep getWorkflow(@PathParam("id") final URI id) {
        return map(getCustomServicesWorkflow(id));
    }

    @POST
    @CheckPermission(roles = { Role.SYSTEM_ADMIN })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public CustomServicesWorkflowRestRep addWorkflow(final CustomServicesWorkflowCreateParam workflow) {
        if (StringUtils.isNotBlank(workflow.getDocument().getName())) {
            final String label = workflow.getDocument().getName().trim();
            checkForDuplicateName(label, CustomServicesWorkflow.class);
            if (customServicesWorkflowManager.hasCatalogServices(label)) {
                throw APIException.badRequests.duplicateLabel(label + " (Workflow name cannot be same as the existing Catalog Base Service)");
            }
        } else {
            throw APIException.badRequests.requiredParameterMissingOrEmpty("name");
        }

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
    @CheckPermission(roles = { Role.SYSTEM_ADMIN })
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public CustomServicesWorkflowRestRep updateWorkflow(@PathParam("id") final URI id, final CustomServicesWorkflowUpdateParam workflow) {
        final CustomServicesWorkflow customServicesWorkflow;
        try {
            customServicesWorkflow = getCustomServicesWorkflow(id);
            if (null == customServicesWorkflow) {
                throw APIException.notFound.unableToFindEntityInURL(id);
            } else if (customServicesWorkflow.getInactive()) {
                throw APIException.notFound.entityInURLIsInactive(id);
            }

            switch (CustomServicesWorkflowStatus.valueOf(customServicesWorkflow.getState())) {
                case PUBLISHED:
                    throw APIException.methodNotAllowed.notSupportedWithReason("Published workflow cannot be edited.");
                default:
                    if (StringUtils.isNotBlank(workflow.getDocument().getName())) {
                        final String label = workflow.getDocument().getName().trim();
                        if (!label.equalsIgnoreCase(customServicesWorkflow.getLabel())) {
                            checkForDuplicateName(label, CustomServicesWorkflow.class);
                            if (customServicesWorkflowManager.hasCatalogServices(label)) {
                                throw APIException.badRequests.duplicateLabel(label + " (Workflow name cannot be same as the existing Catalog Base Service)");
                            }
                        }
                    }

                    final String currentSteps = customServicesWorkflow.getSteps();

                    WorkflowHelper.update(customServicesWorkflow, workflow.getDocument());

                    // On update, if there is any change to steps, resetting workflow status to initial state -NONE
                    if (StringUtils.isNotBlank(currentSteps) && StringUtils.isNotBlank(customServicesWorkflow.getSteps())
                            && !currentSteps.equals(customServicesWorkflow.getSteps())) {
                        customServicesWorkflow.setState(CustomServicesWorkflowStatus.NONE.toString());
                    }
            }

        } catch (IOException e) {
            throw APIException.internalServerErrors.genericApisvcError("Error serializing workflow", e);
        }
        customServicesWorkflowManager.save(customServicesWorkflow);
        return map(customServicesWorkflow);
    }

    @POST
    @CheckPermission(roles = { Role.SYSTEM_ADMIN })
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
    @CheckPermission(roles = { Role.SYSTEM_ADMIN })
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
    @CheckPermission(roles = { Role.SYSTEM_ADMIN })
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
                if (customServicesWorkflowManager.hasCatalogServices(customServicesWorkflow.getLabel())) {
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
    @CheckPermission(roles = { Role.SYSTEM_ADMIN })
    @Path("/{id}/validate")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public CustomServicesValidationResponse validateWorkflow(@PathParam("id") final URI id) {
        try {
            final CustomServicesWorkflowDocument wfDocument = WorkflowHelper.toWorkflowDocument(getCustomServicesWorkflow(id));
            final ValidationHelper customServicesValidationHelper = new ValidationHelper(wfDocument);
            final CustomServicesValidationResponse validationResponse = customServicesValidationHelper.validate(id, client);
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
    @CheckPermission(roles = { Role.SYSTEM_ADMIN })
    @Path("/bulk")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public CustomServicesWorkflowBulkRep bulkGetWorkflows(final BulkIdParam ids) {
        return (CustomServicesWorkflowBulkRep) super.getBulkResources(ids);
    }

    @POST
    @CheckPermission(roles = { Role.SYSTEM_ADMIN })
    @Consumes({ MediaType.APPLICATION_OCTET_STREAM })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/import")
    public CustomServicesWorkflowRestRep importWorkflow(
            @Context final HttpServletRequest request,
            @QueryParam("directory") final URI directory) {
        final WFDirectory wfDirectory;
        if (URIUtil.isNull(directory) || directory.toString().isEmpty()) {
            wfDirectory = NO_DIR;
        } else {
            wfDirectory = wfDirectoryManager.getWFDirectoryById(directory);    
        }
        final InputStream in;
        try {
            in = request.getInputStream();
        } catch (final IOException e) {
            throw APIException.internalServerErrors.genericApisvcError("Failed to open servlet input stream", e);
        }
        return map(WorkflowHelper.importWorkflow(in, wfDirectory, client, daos, resourceDAOs));

    }

    /**
     * Download the resource and set it in the response header
     * 
     * @param id The ID of the resource to download
     * @param response HttpServletResponse the servlet response to update with the file octet stream
     * @return Response containing the octet stream of the primitive resource
     */
    @GET
    @CheckPermission(roles = { Role.SYSTEM_ADMIN })
    @Path("{id}/export")
    public Response download(@PathParam("id") final URI id,
            @Context final HttpServletResponse response) {
        final CustomServicesWorkflow customServicesWorkflow = getCustomServicesWorkflow(id);
        switch (CustomServicesWorkflowStatus.valueOf(customServicesWorkflow.getState())) {
            case PUBLISHED:
                final byte[] bytes;
                try {
                    bytes = WorkflowHelper.exportWorkflow(id, client, daos, resourceDAOs, KeyStoreUtil.getViPRKeystore(coordinator));
                } catch (final GeneralSecurityException | IOException | InterruptedException e) {
                    throw APIException.internalServerErrors.genericApisvcError("Failed to open keystore ", e);
                }

                response.setContentLength(bytes.length);

                response.setHeader("Content-Disposition", "attachment; filename=" +
                        customServicesWorkflow.getLabel().toString() + EXPORT_EXTENSION);
                return Response.ok(bytes).build();

            default:
                throw APIException.methodNotAllowed.notSupportedForUnpublishedWorkflow(customServicesWorkflow.getState());
        }
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
