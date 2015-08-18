/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.api.service.impl.resource;

import java.net.URI;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.block.BlockSnapshotSessionBulkRep;
import com.emc.storageos.model.block.BlockSnapshotSessionRestRep;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;

/**
 * 
 */
@Path("/block/snapshot-sessions")
@DefaultPermissions(readRoles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, readAcls = { ACL.ANY },
        writeRoles = { Role.TENANT_ADMIN }, writeAcls = { ACL.ANY })
public class BlockSnapshotSessionService extends TaskResourceService {

    /**
     * 
     * 
     * @brief
     * 
     * @prereq
     * 
     * @param id
     * 
     * @return
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/link-targets")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskList linkTargetVolumes(@PathParam("id") URI id) {
        return null;
    }

    /**
     * 
     * 
     * @brief
     * 
     * @prereq
     * 
     * @param id
     * 
     * @return
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/relink-targets")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskList relinkTargetVolumes(@PathParam("id") URI id) {
        return null;
    }

    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/unlink-targets")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskList unlinkTargetVolumes(@PathParam("id") URI id) {
        return null;
    }

    /**
     * 
     * 
     * @brief
     * 
     * @prereq
     * 
     * @param id
     * 
     * @return
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/restore")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskList restoreSnapshotSession(@PathParam("id") URI id) {
        return null;
    }

    /**
     * 
     * 
     * @brief
     * 
     * @prereq
     * 
     * @param id
     * 
     * @return
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/activate")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskList activateSnapshotSession(@PathParam("id") URI id) {
        return null;
    }

    /**
     * 
     * 
     * @brief
     * 
     * @prereq
     * 
     * @param id
     * 
     * @return
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/full-copies")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskList createFullCopy(@PathParam("id") URI id) {
        return null;
    }

    /**
     * 
     * 
     * @brief
     * 
     * @prereq
     * 
     * @param id
     * 
     * @return
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/deactivate")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskList deactivateSnapshotSession(@PathParam("id") URI id) {
        return null;
    }

    /**
     * 
     * 
     * @brief
     * 
     * @prereq
     * 
     * @param id
     * 
     * @return
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public BlockSnapshotSessionRestRep getSnapshotSession(@PathParam("id") URI id) {
        return null;
    }

    /**
     * 
     * @brief
     * 
     * @prereq
     * 
     * @param
     * 
     * @return
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public BlockSnapshotSessionBulkRep getBulkResources(BulkIdParam param) {
        return (BlockSnapshotSessionBulkRep) super.getBulkResources(param);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected BlockSnapshotSession queryResource(URI id) {
        ArgValidator.checkUri(id);
        BlockSnapshotSession blockSnapshotSession = _permissionsHelper.getObjectById(id, BlockSnapshotSession.class);
        ArgValidator.checkEntityNotNull(blockSnapshotSession, id, isIdEmbeddedInURL(id));
        return blockSnapshotSession;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected URI getTenantOwner(URI id) {
        BlockSnapshotSession snapshotSession = queryResource(id);
        URI projectUri = snapshotSession.getProject().getURI();
        ArgValidator.checkUri(projectUri);
        Project project = _permissionsHelper.getObjectById(projectUri, Project.class);
        ArgValidator.checkEntityNotNull(project, projectUri, isIdEmbeddedInURL(projectUri));
        return project.getTenantOrg().getURI();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.BLOCK_SNAPSHOT_SESSION;
    }
}
