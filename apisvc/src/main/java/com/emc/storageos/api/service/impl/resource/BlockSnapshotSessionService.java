/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
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

import com.emc.storageos.api.service.impl.resource.snapshot.BlockSnapshotSessionManager;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.BlockSnapshotSessionBulkRep;
import com.emc.storageos.model.block.BlockSnapshotSessionRestRep;
import com.emc.storageos.model.block.SnapshotSessionTargetsParam;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;

/**
 * Service class that implements APIs on instances of BlockSnapshotSession.
 */
@Path("/block/snapshot-sessions")
@DefaultPermissions(readRoles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, readAcls = { ACL.ANY },
        writeRoles = { Role.TENANT_ADMIN }, writeAcls = { ACL.ANY })
public class BlockSnapshotSessionService extends TaskResourceService {

    /**
     * The method implements the API to create and link new target
     * volumes to an existing BlockSnapshotSession instance.
     * 
     * @brief Link target volumes to snapshot session.
     * 
     * @prereq The block snapshot session has been created and the maximum
     *         number of targets has not already been linked to the snapshot sessions
     *         for the source object.
     * 
     * @param id The URI of the BlockSnapshotSession instance to which the
     *            new targets will be linked.
     * @param param The linked target information.
     * 
     * @return A TaskList.
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/link-targets")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskResourceRep linkTargetVolumes(@PathParam("id") URI id, SnapshotSessionTargetsParam param) {
        return getSnapshotSessionManager().linkNewTargetVolumesToSnapshotSession(id, param);
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

    /**
     * Creates and returns an instance of the block snapshot session manager to handle
     * a snapshot session requests.
     * 
     * @return BlockSnapshotSessionManager
     */
    private BlockSnapshotSessionManager getSnapshotSessionManager() {
        BlockSnapshotSessionManager snapshotSessionManager = new BlockSnapshotSessionManager(_dbClient,
                _permissionsHelper, _auditMgr, _coordinator, sc, uriInfo, _request);
        return snapshotSessionManager;
    }
}
