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

import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFields;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.sa.catalog.WorkflowDirectoryManager;
import com.emc.storageos.api.service.impl.resource.TaggedResource;
import com.emc.storageos.api.service.impl.response.RestLinkFactory;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.uimodels.WFDirectory;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.RestLinkRep;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.vipr.model.catalog.WFBulkRep;
import com.emc.vipr.model.catalog.WFDirectoryList;
import com.emc.vipr.model.catalog.WFDirectoryParam;
import com.emc.vipr.model.catalog.WFDirectoryRestRep;
import com.emc.vipr.model.catalog.WFDirectoryUpdateParam;

/**
 * WF Directory API service
 */

@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN }, readAcls = { ACL.OWN, ACL.ALL }, writeRoles = {
        Role.SYSTEM_ADMIN }, writeAcls = { ACL.OWN, ACL.ALL })
@Path("/customservices/workflows/directory")
public class WFDirectoryService extends TaggedResource {

    private static final Logger log = LoggerFactory.getLogger(WFDirectoryService.class);
    private static final String EVENT_SERVICE_TYPE = "wf-directory";
    private static final String ROOT_LEVEL_FOLDER = "RootLevelFolder";

    @Autowired
    private WorkflowDirectoryManager wfDirectoryManager;

    @PostConstruct
    public void init() {
        log.info("Initializing WF Directory service");
    }

    /**
     * List the workflow directories
     *
     * @prereq none
     * @brief List workflow directories
     * @return List of workflow directories
     */
    @GET
    @CheckPermission(roles = { Role.SYSTEM_ADMIN })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })

    public WFDirectoryList getWorkflowDirectories() {
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
     * @brief List data of workflow directories
     * @return list of representations
     */
    @POST
    @CheckPermission(roles = { Role.SYSTEM_ADMIN })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/bulk")
    @Override
    public WFBulkRep getBulkResources(BulkIdParam param) {
        return (WFBulkRep) super.getBulkResources(param);
    }

    /**
     * Get the workflow directory
     * 
     * @prereq none
     * @brief Show workflow directory
     * @param id the ID of the workflow directory to be retrieved
     * @return
     */
    @GET
    @CheckPermission(roles = { Role.SYSTEM_ADMIN })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    public WFDirectoryRestRep getWFDirectory(@PathParam("id") URI id) {
        WFDirectory wfDirectory = wfDirectoryManager.getWFDirectoryById(id);
        return map(wfDirectory);
    }

    /**
     * Create the workflow directory
     * 
     * @prereq none
     * @brief Create workflow directory
     * @param wfDirectoryParam the workflow directory parameter (name, parent, workflows)
     * @return
     */
    @POST
    @CheckPermission(roles = { Role.SYSTEM_ADMIN })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public WFDirectoryRestRep createWFDirectory(WFDirectoryParam wfDirectoryParam) {
        log.info("Creating wf directory");
        // Check for null fields
        // get,set workflows
        WFDirectory wfDirectory = new WFDirectory();
        final String createParamLabel = wfDirectoryParam.getName();
        final URI parentId = wfDirectoryParam.getParent() == null ? getRootLevelParentId() : wfDirectoryParam.getParent();
        if (null != wfDirectoryParam.getParent()) {
            checkWFDirExists(wfDirectoryParam.getParent());
        }
        if (StringUtils.isNotBlank(createParamLabel)) {
            checkDuplicateLabel(createParamLabel.trim(), parentId);
        } else {
            throw APIException.badRequests.requiredParameterMissingOrEmpty("name");
        }

        wfDirectory.setLabel(wfDirectoryParam.getName());
        wfDirectory.setParent(parentId);
        wfDirectory.setWorkflows(StringSetUtil.uriListToStringSet(wfDirectoryParam.getWorkflows()));
        wfDirectoryManager.createWFDirectory(wfDirectory);
        return map(wfDirectory);
    }

    private void checkDuplicateLabel(final String name, final URI parentId) {
        if (null != parentId) {
            final List<WFDirectory> children = wfDirectoryManager.getWFDirectoryChildren(parentId);
            for (final WFDirectory child : children) {
                if (child.getLabel().equalsIgnoreCase(name)) {
                    throw APIException.badRequests.duplicateLabel(name);
                }
            }
        }
    }

    /**
     * Deactivate the workflow directory
     * 
     * @prereq none
     * @brief Deactivate workflow directory
     * @param id the ID of the workflow directory to be deactivated
     * @return
     */
    @POST
    @CheckPermission(roles = { Role.SYSTEM_ADMIN })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/deactivate")
    public Response deactivateWFDirectory(@PathParam("id") URI id) {
        log.info("Deactivate wf directory");
        wfDirectoryManager.deactivateWFDirectory(id);
        return Response.ok().build();
    }

    /**
     * Update the workflow directory 
     * 
     * @prereq none
     * @brief Update workflow directory
     * @param id the ID of the workflow directory to be updated
     * @param param the workflow directory parameter (name, parent, workflows)
     * @return
     */
    @PUT
    @CheckPermission(roles = { Role.SYSTEM_ADMIN })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    public WFDirectoryRestRep updateWFDirectory(@PathParam("id") URI id, WFDirectoryUpdateParam param) {
        WFDirectory wfDirectory = checkWFDirExists(id);
        // update object
        if (StringUtils.isNotBlank(param.getName())) {
            final String label = param.getName().trim();
            if (!label.equalsIgnoreCase(wfDirectory.getLabel())) {
                final URI parentId = param.getParent() == null ? wfDirectory.getParent() : param.getParent();
                checkDuplicateLabel(label, parentId);
                wfDirectory.setLabel(param.getName());
            }
        }

        if (null != param.getParent()) {
            // no need to set the parent to getRootLevelParentId() since this would have been set during creation
            checkWFDirExists(param.getParent());
            wfDirectory.setParent(param.getParent());
        }

        if (null != param.getWorkflows()) {
            wfDirectory.addWorkflows(param.getWorkflows().getAdd());
            wfDirectory.removeWorkflows(param.getWorkflows().getRemove());
            log.debug(wfDirectory.getWorkflows().toString());
        }
        wfDirectoryManager.updateWFDirectory(wfDirectory);
        return map(wfDirectory);
    }

    private WFDirectory checkWFDirExists(final URI id) {
        final WFDirectory wfDirectory = wfDirectoryManager.getWFDirectoryById(id);
        if (null == wfDirectory) {
            throw APIException.notFound.unableToFindEntityInURL(id);
        } else if (wfDirectory.getInactive()) {
            throw APIException.notFound.entityInURLIsInactive(id);
        }
        return wfDirectory;
    }

    private WFDirectoryRestRep map(WFDirectory from) {
        WFDirectoryRestRep to = new WFDirectoryRestRep();
        mapDataObjectFields(from, to);
        if (null != from.getParent()) {
            if (from.getParent().equals(getRootLevelParentId())) {
                to.setParent(null);// Removing the dummy parent Id that was set during create
            } else {
                to.setParent(new RelatedResourceRep(from.getParent(),
                        new RestLinkRep("self", RestLinkFactory.newLink(ResourceTypeEnum.WF_DIRECTORY, from.getParent()))));
            }
        }
        if (null != from.getWorkflows()) {
            to.setWorkflows(StringSetUtil.stringSetToUriList(from.getWorkflows()));
        }
        return to;
    }

    private URI getRootLevelParentId() {
        return URIUtil.createInternalID(WFDirectory.class, ROOT_LEVEL_FOLDER);
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
        List<WFDirectoryRestRep> wfDirectoryRestReps = new ArrayList<>();
        List<WFDirectory> wfDirectories = wfDirectoryManager.getWFDirectories(ids);
        for (WFDirectory wfd : wfDirectories) {
            wfDirectoryRestReps.add(map(wfd));
        }
        return new WFBulkRep(wfDirectoryRestReps);
    }
}
