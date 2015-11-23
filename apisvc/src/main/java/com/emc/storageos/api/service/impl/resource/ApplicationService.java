/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.emc.storageos.api.mapper.DbObjectMapper;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.Application;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.application.ApplicationCreateParam;
import com.emc.storageos.model.application.ApplicationList;
import com.emc.storageos.model.application.ApplicationRestRep;
import com.emc.storageos.model.application.ApplicationUpdateParam;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * APIs to view, create, modify and remove applications
 */

@Path("/applications/block")
@DefaultPermissions(readRoles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, readAcls = { ACL.OWN, ACL.ALL }, writeRoles = {
        Role.TENANT_ADMIN }, writeAcls = { ACL.OWN, ACL.ALL })
public class ApplicationService extends TaskResourceService {
    private static final String APPLICATION_NAME = "name";
    private static final String APPLICATION_ROLES = "roles";
    private static final String EVENT_SERVICE_TYPE = "application";

    @Override
    protected DataObject queryResource(URI id) {
        ArgValidator.checkUri(id);
        Application application = _permissionsHelper.getObjectById(id, Application.class);
        ArgValidator.checkEntityNotNull(application, id, isIdEmbeddedInURL(id));
        return application;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.APPLICATION;
    }

    @Override
    protected URI getTenantOwner(final URI id) {
        return null;
    }

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    /**
     * Create an application
     * 
     * @param param Parameters for creating an application
     * @return created application
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public ApplicationRestRep createApplication(ApplicationCreateParam param) {
        ArgValidator.checkFieldNotEmpty(param.getName(), APPLICATION_NAME);
        checkDuplicateLabel(Application.class, param.getName(), "Application");
        Set<String> roles = param.getRoles();
        ArgValidator.checkFieldNotEmpty(roles, APPLICATION_ROLES);
        for (String role : roles) {
            ArgValidator.checkFieldValueFromEnum(role, APPLICATION_ROLES, Application.ApplicationRole.class);
        }
        Application application = new Application();
        application.setId(URIUtil.createId(Application.class));
        application.setLabel(param.getName());
        application.setDescription(param.getDescription());
        application.addRoles(param.getRoles());
        _dbClient.createObject(application);
        auditOp(OperationTypeEnum.CREATE_APPLICATION, true, null, application.getId().toString(),
                application.getLabel());
        return DbObjectMapper.map(application);
    }

    /**
     * List an application
     * 
     * @param id Application Id
     * @return ApplicationRestRep
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    public ApplicationRestRep getApplication(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, Application.class, "id");
        Application application = (Application) queryResource(id);
        return DbObjectMapper.map(application);
    }

    /**
     * List applications.
     * 
     * @return A reference to applicationList.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public ApplicationList getApplications() {
        ApplicationList applicationList = new ApplicationList();

        List<URI> ids = _dbClient.queryByType(Application.class, true);
        Iterator<Application> iter = _dbClient.queryIterativeObjects(Application.class, ids);
        while (iter.hasNext()) {
            applicationList.getApplications().add(toNamedRelatedResource(iter.next()));
        }
        return applicationList;
    }

    /**
     * Delete the application.
     * When an application is deleted it will move to a "marked for deletion" state.
     *
     * @param id the URN of the application
     * @brief Deactivate application
     * @return No data returned in response body
     */
    @POST
    @Path("/{id}/deactivate")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public Response deactivateApplication(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, Application.class, "id");
        Application application = (Application) queryResource(id);
        ArgValidator.checkReference(Application.class, id, checkForDelete(application));
        // TODO check on application volumes
        /*
         * if (!application.getVolumes().isEmpty()) {
         * // application could not be deleted if it has volumes
         * throw APIException.badRequests.applicationWithVolumesCantBeDeleted(application.getLabel());
         * }
         */
        _dbClient.markForDeletion(application);

        auditOp(OperationTypeEnum.DELETE_CONFIG, true, null, id.toString(),
                application.getLabel());
        return Response.ok().build();
    }

    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public ApplicationRestRep updateApplication(@PathParam("id") final URI id,
            final ApplicationUpdateParam param) {
        ArgValidator.checkFieldUriType(id, Application.class, "id");
        Application application = (Application) queryResource(id);
        if (application.getInactive()) {
            throw APIException.badRequests.applicationCantBeUpdated(application.getLabel(), "The application has been deleted");
        }
        String apname = param.getName();
        if (apname != null && !apname.isEmpty()) {
            checkDuplicateLabel(Application.class, apname, "Application");
            application.setLabel(apname);
        }
        String description = param.getDescription();
        if (description != null && !description.isEmpty()) {
            application.setDescription(description);
        }
        _dbClient.updateObject(application);
        auditOp(OperationTypeEnum.UPDATE_APPLICATION, true, null, application.getId().toString(),
                application.getLabel());
        return DbObjectMapper.map(application);
    }
}
