/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import java.net.URI;
import java.util.Arrays;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.emc.storageos.api.service.impl.placement.PlacementManager;
import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyManager;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;

@Path("/block/full-copies")
@DefaultPermissions(readRoles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, readAcls = {
        ACL.OWN, ACL.ALL }, writeRoles = { Role.TENANT_ADMIN }, writeAcls = { ACL.OWN,
        ACL.ALL })
public class BlockFullCopyService extends TaskResourceService {

    // A reference to the tenants service.
    private TenantsService _tenantsService;

    // A reference to the placement manager.
    private PlacementManager _placementManager;

    /**
     * Setter for the placement manager for Spring configuration.
     * 
     * @param placementManager A reference to the placement manager.
     */
    public void setPlacementManager(PlacementManager placementManager) {
        _placementManager = placementManager;
    }

    /**
     * Setter for the tenants service for Spring configuration.
     * 
     * @param tenantsService A reference to the tenants service.
     */
    public void setTenantsService(TenantsService tenantsService) {
        _tenantsService = tenantsService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected DataObject queryResource(URI id) {
        ArgValidator.checkUri(id);
        Volume fullCopy = _permissionsHelper.getObjectById(id, Volume.class);
        ArgValidator.checkEntityNotNull(fullCopy, id, isIdEmbeddedInURL(id));
        return fullCopy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected URI getTenantOwner(URI id) {
        Volume fullCopy = (Volume) queryResource(id);
        URI projectURI = fullCopy.getProject().getURI();
        ArgValidator.checkUri(projectURI);

        Project project = _permissionsHelper.getObjectById(projectURI, Project.class);
        ArgValidator.checkEntityNotNull(project, projectURI,
                isIdEmbeddedInURL(projectURI));
        return project.getTenantOrg().getURI();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.VOLUME;
    }

    /**
     * Activate a full copy. For supported platforms, if the full copy source is
     * a volume and that volume is part of a consistency group, this call will
     * activate the corresponding full copies for all volumes in the consistency
     * group.
     * 
     * 
     * @prereq Create full copy as inactive
     * 
     * @param fullCopyURI The URI of the full copy volume.
     * 
     * @brief Activate full copy
     * 
     * @return TaskList
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/activate")
    public TaskList activateFullCopy(@PathParam("id") URI fullCopyURI)
            throws InternalException {
        Volume fullCopyVolume = queryFullCopy(fullCopyURI);
        
        // Make sure that we don't have some pending
        // operation against the volume
        checkForPendingTasks(Arrays.asList(fullCopyVolume.getTenant().getURI()), Arrays.asList(fullCopyVolume));

        return getFullCopyManager().activateFullCopy(
                fullCopyVolume.getAssociatedSourceVolume(), fullCopyURI);
    }

    /**
     * Detach a full copy from its source volume. For supported platforms, if
     * the full copy source is a volume and that volume is part of a consistency
     * group, this call will detach the corresponding full copies for all
     * volumes in the consistency group.
     * 
     * @prereq Create full copy as inactive
     * @prereq Activate full copy
     * 
     * @param fullCopyURI The URI of the full copy volume.
     * 
     * @brief Detach full copy
     * 
     * @return TaskList
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/detach")
    public TaskList detachFullCopy(@PathParam("id") URI fullCopyURI)
            throws InternalException {
        Volume fullCopyVolume = queryFullCopy(fullCopyURI);
        return getFullCopyManager().detachFullCopy(
                fullCopyVolume.getAssociatedSourceVolume(), fullCopyURI);
    }

    /**
     * Restores the full copy source with the data on the full copy with the
     * passed URI. For supported platforms, if the full copy source is a volume
     * and that volume is part of a consistency group, this call will restore
     * all volumes in the consistency group with their corresponding full
     * copies.
     * 
     * @prereq Full copy is not detached from source volume.
     * 
     * @param fullCopyURI The URI of the full copy volume.
     * 
     * @brief Restore full copy
     * 
     * @return TaskList
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/restore")
    public TaskList restoreFullCopy(@PathParam("id") URI fullCopyURI)
            throws InternalException {
        Volume fullCopyVolume = queryFullCopy(fullCopyURI);
        
        // Make sure that we don't have some pending
        // operation against the volume
        checkForPendingTasks(Arrays.asList(fullCopyVolume.getTenant().getURI()), Arrays.asList(fullCopyVolume));
        
        return getFullCopyManager().restoreFullCopy(
                fullCopyVolume.getAssociatedSourceVolume(), fullCopyURI);
    }

    /**
     * Resynchronizes the full copy with the passed URI with the latest data on
     * its source. For supported platforms, if the full copy source is a volume
     * and that volume is part of a consistency group, this call will
     * resynchronize the corresponding full copies for all volumes in the
     * consistency group.
     * 
     * @prereq Full copy is not detached from source volume.
     * 
     * @param fullCopyURI The URI of the full copy volume.
     * 
     * @brief Resynchronize full copy
     * 
     * @return TaskList
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/resynchronize")
    public TaskList resynchronizeFullCopy(@PathParam("id") URI fullCopyURI)
            throws InternalException {
        Volume fullCopyVolume = queryFullCopy(fullCopyURI);
        
        // Make sure that we don't have some pending
        // operation against the volume
        checkForPendingTasks(Arrays.asList(fullCopyVolume.getTenant().getURI()), Arrays.asList(fullCopyVolume));
        
        return getFullCopyManager().resynchronizeFullCopy(
                fullCopyVolume.getAssociatedSourceVolume(), fullCopyURI);
    }

    /**
     * Start Full Copy
     * 
     * Generates a group synchronized between volume Replication group
     * and clone Replication group.
     * 
     * @prereq There should be existing Storage synchronized relations
     * between volumes and clones.
     * 
     * @param fullCopyURI The URI of the full copy volume.
     * 
     * @brief Start replication group synchronization between volume and clone
     * @return TaskList
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/start")
    public TaskList startFullCopy(@PathParam("id") URI fullCopyURI)
        throws InternalException {
        Volume fullCopyVolume = queryFullCopy(fullCopyURI);
        return getFullCopyManager().startFullCopy(
            fullCopyVolume.getAssociatedSourceVolume(), fullCopyURI);
    }

    /**
     * Show synchronization progress for a full copy.
     * 
     * @prereq none
     * 
     * @param fullCopyURI The URI of the full copy volume.
     * 
     * @brief Show full copy synchronization progress
     * 
     * @return VolumeRestRep
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/check-progress")
    public VolumeRestRep checkFullCopyProgress(@PathParam("id") URI fullCopyURI)
            throws InternalException {
        Volume fullCopyVolume = queryFullCopy(fullCopyURI);
        return getFullCopyManager().checkFullCopyProgress(
                fullCopyVolume.getAssociatedSourceVolume(), fullCopyURI);
    }

    /**
     * Creates and returns an instance of the block full copy manager to handle
     * a full copy request.
     * 
     * @return BlockFullCopyManager
     */
    private BlockFullCopyManager getFullCopyManager() {
        BlockFullCopyManager fcManager = new BlockFullCopyManager(_dbClient,
                _permissionsHelper, _auditMgr, _coordinator, _placementManager, sc, uriInfo,
                _request, _tenantsService);
        return fcManager;
    }

    /**
     * Get the full copy volume with the passed URI.
     * 
     * @param fullCopyURI The URI of a full copy volume
     * 
     * @return A reference to the volume.
     */
    private Volume queryFullCopy(URI fullCopyURI) {
        return (Volume) queryResource(fullCopyURI);
    }
}
