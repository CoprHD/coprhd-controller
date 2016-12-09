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

import com.emc.sa.catalog.WorkflowDirectoryManager;
import com.emc.storageos.api.service.impl.resource.TaggedResource;
import com.emc.storageos.api.service.impl.response.RestLinkFactory;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.uimodels.WFDirectory;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.RestLinkRep;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.vipr.model.catalog.WFBulkRep;
import com.emc.vipr.model.catalog.WFDirectoryList;
import com.emc.vipr.model.catalog.WFDirectoryParam;
import com.emc.vipr.model.catalog.WFDirectoryRestRep;
import com.emc.vipr.model.catalog.WFDirectoryUpdateParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFields;

import javax.annotation.PostConstruct;
import javax.ws.rs.Path;
import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.POST;
import javax.ws.rs.Consumes;
import javax.ws.rs.PathParam;
import javax.ws.rs.PUT;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;

/**
 * WF Directory API service
 */
@DefaultPermissions(
        readRoles = { Role.SYSTEM_MONITOR, Role.SYSTEM_ADMIN },
        writeRoles = { Role.TENANT_ADMIN },
        readAcls = { ACL.ANY })
@Path("/workflow/directory")
public class WFDirectoryService extends TaggedResource {

    private static final Logger log = LoggerFactory.getLogger(WFDirectoryService.class);
    private static final String EVENT_SERVICE_TYPE = "wf-directory";

    @PostConstruct
    public void init() {
        log.info("Initializing WF Directory service");
    }

    @Autowired
    private WorkflowDirectoryManager wfDirectoryManager;

    /**
     * Get workflow directories
     *
     * @prereq none
     * @brief Get workflow directories
     * @return List of workflow directories
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public WFDirectoryList getWFDirectories() {
        List<WFDirectory> wfDirectories = wfDirectoryManager.getWFDirectories();
        WFDirectoryList wfDirectoryList = new WFDirectoryList();
        for (WFDirectory dir : wfDirectories) {
            NamedRelatedResourceRep wfDirectoryRestRep = toNamedRelatedResource(ResourceTypeEnum.WF_DIRECTORY,
                    dir.getId(), dir.getLabel());
            wfDirectoryList.getWFDirectories().add(wfDirectoryRestRep);
        }

        return wfDirectoryList;
    }

    /**
     * Retrieve resource representations based on input ids.
     *
     * @prereq none
     * @brief List of workflow directories
     * @return list of representations
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/bulk")
    @Override
    public WFBulkRep getBulkResources(BulkIdParam param) {
        return (WFBulkRep)super.getBulkResources(param);
    }

    /**
     * Get workflow directory by ID
     *
     * @prereq none
     * @brief Get workflow directory by ID
     * @return Workflow directory
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    public WFDirectoryRestRep getWFDirectory(@PathParam("id") URI id) {
        WFDirectory wfDirectory = wfDirectoryManager.getWFDirectoryById(id);
        return map(wfDirectory);
    }

    /**
     * Create workflow directory
     *
     * @prereq none
     * @brief Create workflow directory
     * @return Created workflow directory
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN })
    public WFDirectoryRestRep createWFDirectory(WFDirectoryParam wfDirectoryParam) {
        log.info("Creating wf directory");
        //Check for null fields
        // get,set workflows
        WFDirectory wfDirectory = new WFDirectory();
        wfDirectory.setLabel(wfDirectoryParam.getName());
        wfDirectory.setParent(wfDirectoryParam.getParent());
        wfDirectory.setWorkflows(StringSetUtil.uriListToStringSet(wfDirectoryParam.getWorkflows()));
        wfDirectoryManager.createWFDirectory(wfDirectory);
        return map(wfDirectory);
    }

    /**
     * Deactivate workflow directory
     *
     * @prereq none
     * @brief Deactivate workflow directory
     * @return No data returned in response body
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN })
    @Path("/{id}/deactivate")
    public Response deactivateWFDirectory(@PathParam("id") URI id) {
        log.info("Deactivate wf directory");
        wfDirectoryManager.deactivateWFDirectory(id);
        return Response.ok().build();
    }

    /**
     * Update workflow directory (name, parent, workflows)
     *
     * @prereq none
     * @brief Update workflow directory
     * @return Updated workflow directory
     */
    @PUT
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN })
    @Path("/{id}")
    public WFDirectoryRestRep updateWFDirectory(@PathParam("id") URI id, WFDirectoryUpdateParam param) {
        WFDirectory wfDirectory = wfDirectoryManager.getWFDirectoryById(id);
        //update object
        // label cannot be null/empty
        if (null != param.getName()) {
            wfDirectory.setLabel(param.getName());
        }
        // Parent can be null for top level folder
        wfDirectory.setParent(param.getParent());
        if (null != param.getWorkflows()) {
            wfDirectory.addWorkflows(param.getWorkflows().getAdd());
            wfDirectory.removeWorkflows(param.getWorkflows().getRemove());
        }
        log.debug(wfDirectory.getWorkflows().toString());
        wfDirectoryManager.updateWFDirectory(wfDirectory);
        return map(wfDirectory);
    }


    private WFDirectoryRestRep map(WFDirectory from) {
        WFDirectoryRestRep to = new WFDirectoryRestRep();
        mapDataObjectFields(from, to);
        if (null != from.getParent()) {
            to.setParent(new RelatedResourceRep(from.getParent(), new RestLinkRep("self", RestLinkFactory.newLink(ResourceTypeEnum.WF_DIRECTORY, from.getParent()))));
        }
        if (null != from.getWorkflows()) {
            to.setWorkflows(StringSetUtil.stringSetToUriList(from.getWorkflows()));
        }
        return to;
    }


    @Override
    protected DataObject queryResource(URI id) {
        return null;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<WFDirectory> getResourceClass() {
        return WFDirectory.class;
    }

    @Override
    public WFBulkRep queryBulkResourceReps(List<URI> ids) {
        List<WFDirectoryRestRep> wfDirectoryRestReps =
                new ArrayList<>();
        List<WFDirectory> wfDirectories = wfDirectoryManager.getWFDirectories(ids);
        for (WFDirectory wfd : wfDirectories) {
            wfDirectoryRestReps.add(map(wfd));
        }
        return new WFBulkRep(wfDirectoryRestReps);
    }
}
