/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.emc.storageos.api.mapper.DbObjectMapper;
import com.emc.storageos.db.client.model.Application;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.application.ApplicationCreateParam;
import com.emc.storageos.model.application.ApplicationList;
import com.emc.storageos.model.application.ApplicationRestRep;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;

/**
 * APIs to view, create, modify and remove applications
 */

@Path("/application/block")
@DefaultPermissions(readRoles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, readAcls = { ACL.OWN, ACL.ALL }, writeRoles = {
        Role.TENANT_ADMIN }, writeAcls = { ACL.OWN, ACL.ALL })
public class ApplicationService extends TaskResourceService {
    private static String PROJECT = "project";
    private static String APPLICATION_CREATE = "application_create";

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

    /**
     * Create an application
     * @param param Parameters for creating an application
     * @return created application
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public ApplicationRestRep createApplication(ApplicationCreateParam param) {
        ArgValidator.checkFieldNotNull(param, APPLICATION_CREATE);
        ArgValidator.checkFieldUriType(param.getProject(), Project.class, PROJECT);
        // Get and validate the project.
        Project project = _permissionsHelper.getObjectById(param.getProject(), Project.class);
        ArgValidator.checkEntity(project, param.getProject(), isIdEmbeddedInURL(param.getProject()));
        checkDuplicateLabel(Application.class, param.getName(), "Application");
        Application application = new Application();
        application.setLabel(param.getName());
        application.setDescription(param.getDescription());
        application.setProject(new NamedURI(param.getProject(), param.getName()));
        application.setTenant(new NamedURI(project.getTenantOrg().getURI(), param.getName()));
        _dbClient.createObject(application);
        return DbObjectMapper.map(application);    
    }
    
    /**
     * List an application 
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
}
