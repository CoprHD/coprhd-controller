/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import com.emc.storageos.api.mapper.functions.MapBlockSnapshotSession;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.snapshot.BlockSnapshotSessionManager;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.api.service.impl.response.BulkList.PermissionsEnforcingResourceFilter;
import com.emc.storageos.api.service.impl.response.BulkList.ResourceFilter;
import com.emc.storageos.api.service.impl.response.ProjOwnedSnapResRepFilter;
import com.emc.storageos.api.service.impl.response.ResRepFilter;
import com.emc.storageos.api.service.impl.response.SearchedResRepList;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.ContainmentPrefixConstraint;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.BulkRestRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.BlockSnapshotSessionBulkRep;
import com.emc.storageos.model.block.BlockSnapshotSessionRestRep;
import com.emc.storageos.model.block.SnapshotSessionLinkTargetsParam;
import com.emc.storageos.model.block.SnapshotSessionRelinkTargetsParam;
import com.emc.storageos.model.block.SnapshotSessionUnlinkTargetsParam;
import com.emc.storageos.security.authentication.StorageOSUser;
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
     * @brief Link target volumes to a snapshot session.
     * 
     * @prereq The block snapshot session has been created and the maximum
     *         number of targets has not already been linked to the snapshot sessions
     *         for the source object.
     * 
     * @param id The URI of the BlockSnapshotSession instance to which the
     *            new targets will be linked.
     * @param param The linked target information.
     * 
     * @return A TaskResourceRep representing the snapshot session task.
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/link-targets")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskList linkTargetVolumes(@PathParam("id") URI id, SnapshotSessionLinkTargetsParam param) {
        return getSnapshotSessionManager().linkTargetVolumesToSnapshotSession(id, param);
    }

    /**
     * This method implements the API to re-link a target to either its current
     * snapshot session or to a different snapshot session of the same source.
     * 
     * @brief Relink target volumes to snapshot sessions.
     * 
     * @prereq The target volumes are linked to a snapshot session of the same source object.
     * 
     * @param id The URI of the BlockSnapshotSession instance to which the
     *            the targets will be re-linked.
     * @param param The linked target information.
     * 
     * @return A TaskList representing the snapshot session tasks.
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/relink-targets")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskList relinkTargetVolumes(@PathParam("id") URI id, SnapshotSessionRelinkTargetsParam param) {
        return getSnapshotSessionManager().relinkTargetVolumesToSnapshotSession(id, param);
    }

    /**
     * The method implements the API to unlink target volumes from an existing
     * BlockSnapshotSession instance and optionally delete those target volumes.
     * 
     * @brief Unlink target volumes from a snapshot session.
     * 
     * @prereq A snapshot session is created and target volumes have previously
     *         been linked to that snapshot session.
     * 
     * @param id The URI of the BlockSnapshotSession instance to which the targets are linked.
     * @param param The linked target information.
     * 
     * @return A TaskResourceRep representing the snapshot session task.
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/unlink-targets")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskResourceRep unlinkTargetVolumesForSession(@PathParam("id") URI id, SnapshotSessionUnlinkTargetsParam param) {
        return getSnapshotSessionManager().unlinkTargetVolumesFromSnapshotSession(id, param);
    }

    /**
     * Restores the data on the array snapshot point-in-time copy represented by the
     * BlockSnapshotSession instance with the passed id, to the snapshot session source
     * object.
     * 
     * @brief Restore snapshot session to source
     * 
     * @prereq None
     * 
     * @param id The URI of the BlockSnapshotSession instance to be restored.
     * 
     * @return TaskResourceRep representing the snapshot session task.
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/restore")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskResourceRep restoreSnapshotSession(@PathParam("id") URI id) {
        return getSnapshotSessionManager().restoreSnapshotSession(id);
    }

    /**
     * Deletes the BlockSnapshotSession instance with the passed id.
     * 
     * @brief Delete a block snapshot session.
     * 
     * @prereq The block snapshot session has no linked target volumes.
     * 
     * @param id The URI of the BlockSnapshotSession instance to be deleted.
     * @param type The type of deletion VIPR_ONLY or FULL.
     * 
     * @return TaskList representing the tasks for deleting snapshot sessions.
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/deactivate")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskList deactivateSnapshotSession(@PathParam("id") URI id, @DefaultValue("FULL") @QueryParam("type") String type) {
        return getSnapshotSessionManager().deleteSnapshotSession(id, type);
    }

    /**
     * Get the details of the BlockSnapshotSession instance with the passed id.
     * 
     * @brief Get snapshot session details.
     * 
     * @prereq none
     * 
     * @param id The URI of the BlockSnapshotSession instance.
     * 
     * @return BlockSnapshotSessionRestRep specifying the snapshot session details.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public BlockSnapshotSessionRestRep getSnapshotSession(@PathParam("id") URI id) {
        return getSnapshotSessionManager().getSnapshotSession(id);
    }

    /**
     * Gets the details of the BlockSnapshotSession instances with the ids
     * specified in the passed data.
     * 
     * @brief Gets details for requested block snapshot sessions.
     * 
     * @prereq none
     * 
     * @param param The ids of the desired BlockSnapshotSession instances.
     * 
     * @return A BlockSnapshotSessionBulkRep with the snapshot session details.
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
    public BulkRestRep queryBulkResourceReps(List<URI> ids) {

        Iterator<BlockSnapshotSession> _dbIterator = _dbClient.queryIterativeObjects(getResourceClass(), ids);
        return new BlockSnapshotSessionBulkRep(BulkList.wrapping(_dbIterator, MapBlockSnapshotSession.getInstance(_dbClient)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected BulkRestRep queryFilteredBulkResourceReps(List<URI> ids) {
        Iterator<BlockSnapshotSession> _dbIterator = _dbClient.queryIterativeObjects(getResourceClass(), ids);
        ResourceFilter<BlockSnapshotSession> filter = new BlockSnapshotSessionFilter(getUserFromContext(), _permissionsHelper);
        return new BlockSnapshotSessionBulkRep(BulkList.wrapping(_dbIterator, MapBlockSnapshotSession.getInstance(_dbClient), filter));
    }

    /**
     * Private class defined filter for bulk resources.
     */
    private class BlockSnapshotSessionFilter extends PermissionsEnforcingResourceFilter<BlockSnapshotSession> {

        /**
         * Constructor.
         * 
         * @param user A reference to a user.
         * @param permissionsHelper A reference to a permissions helper.
         */
        protected BlockSnapshotSessionFilter(StorageOSUser user, PermissionsHelper permissionsHelper) {
            super(user, permissionsHelper);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isAccessible(BlockSnapshotSession snapSession) {
            boolean isAccesible = false;
            isAccesible = isTenantAccessible(getTenantOwner(snapSession.getId()));
            if (!isAccesible) {
                NamedURI project = snapSession.getProject();
                if (project != null) {
                    isAccesible = isProjectAccessible(project.getURI());
                }
            }
            return isAccesible;
        }
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public Class<BlockSnapshotSession> getResourceClass() {
        return BlockSnapshotSession.class;
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
     * Get search results by project alone.
     * 
     * @return SearchedResRepList
     */
    @Override
    protected SearchedResRepList getProjectSearchResults(URI projectId) {
        SearchedResRepList resRepList = new SearchedResRepList(getResourceType());
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getProjectBlockSnapshotSessionConstraint(projectId),
                resRepList);
        return resRepList;
    }

    /**
     * Get search results by name in zone or project.
     * 
     * @return SearchedResRepList
     */
    @Override
    protected SearchedResRepList getNamedSearchResults(String name, URI projectId) {
        SearchedResRepList resRepList = new SearchedResRepList(getResourceType());
        if (projectId == null) {
            _dbClient.queryByConstraint(
                    PrefixConstraint.Factory.getLabelPrefixConstraint(getResourceClass(), name),
                    resRepList);
        } else {
            _dbClient.queryByConstraint(
                    ContainmentPrefixConstraint.Factory.getBlockSnapshotSessionUnderProjectConstraint(
                            projectId, name), resRepList);
        }
        return resRepList;
    }

    /**
     * Get object specific permissions filter
     * 
     */
    @Override
    public ResRepFilter<? extends RelatedResourceRep> getPermissionFilter(StorageOSUser user,
            PermissionsHelper permissionsHelper) {
        return new ProjOwnedSnapResRepFilter(user, permissionsHelper, BlockSnapshotSession.class);
    }

    /**
     * Block snapshot session is not a zone level resource
     */
    @Override
    protected boolean isZoneLevelResource() {
        return false;
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
