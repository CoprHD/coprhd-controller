/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.BlockMapper.map;

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

import com.emc.storageos.api.mapper.functions.MapBlockMirror;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.api.service.impl.response.BulkList.PermissionsEnforcingResourceFilter;
import com.emc.storageos.api.service.impl.response.BulkList.ResourceFilter;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.BulkRestRep;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.block.BlockMirrorBulkRep;
import com.emc.storageos.model.block.BlockMirrorRestRep;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;

@Path("/block/continuous-copies")
@DefaultPermissions(readRoles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, readAcls = { ACL.ANY }, writeRoles = {
        Role.TENANT_ADMIN }, writeAcls = { ACL.ANY })
public class BlockContinuousCopyService extends TaskResourceService {

	@Override
	protected DataObject queryResource(URI id) {
		ArgValidator.checkUri(id);
        BlockObject blockObj = BlockObject.fetch(_dbClient, id);
        ArgValidator.checkEntityNotNull(blockObj, id, isIdEmbeddedInURL(id));
        return blockObj;
	}

	@Override
	protected URI getTenantOwner(URI id) {
		BlockMirror mirror = (BlockMirror) queryResource(id);
        URI projectUri = mirror.getProject().getURI();
        ArgValidator.checkUri(projectUri);

        Project project = _permissionsHelper.getObjectById(projectUri, Project.class);
        ArgValidator.checkEntityNotNull(project, projectUri, isIdEmbeddedInURL(projectUri));
        return project.getTenantOrg().getURI();
	}

	@Override
	protected ResourceTypeEnum getResourceType() {
		return ResourceTypeEnum.BLOCK_MIRROR;
	}
	
	/**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public Class<BlockMirror> getResourceClass() {
        return BlockMirror.class;
    }
	
	/**
     * Get continuous copy details
     * 
     * @prereq none
     * @param id the URN of a ViPR continuous copy
     * @brief Show continuous copy
     * @return BlockMirror
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public BlockMirrorRestRep getContinuousCopy(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, BlockMirror.class, "id");
        BlockMirror mirror = (BlockMirror) queryResource(id);
        return map(_dbClient, mirror);
    }
    
    /**
     * Gets the details of the BlockMirror instances with the ids
     * specified in the passed data.
     * 
     * @brief Get details for requested block mirror.
     * 
     * @prereq none
     * 
     * @param param The ids of the desired BlockMirror instances.
     * 
     * @return A BlockMirrorBulkRep with the mirror details.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public BlockMirrorBulkRep getBulkResources(BulkIdParam param) {
        return (BlockMirrorBulkRep) super.getBulkResources(param);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BulkRestRep queryBulkResourceReps(List<URI> ids) {

        Iterator<BlockMirror> _dbIterator = _dbClient.queryIterativeObjects(getResourceClass(), ids);
        return new BlockMirrorBulkRep(BulkList.wrapping(_dbIterator, MapBlockMirror.getInstance(_dbClient)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected BulkRestRep queryFilteredBulkResourceReps(List<URI> ids) {
        Iterator<BlockMirror> _dbIterator = _dbClient.queryIterativeObjects(getResourceClass(), ids);
        ResourceFilter<BlockMirror> filter = new BlockMirrorFilter(getUserFromContext(), _permissionsHelper);
        return new BlockMirrorBulkRep(BulkList.wrapping(_dbIterator, MapBlockMirror.getInstance(_dbClient), filter));
    }

    /**
     * Private class defined filter for bulk resources.
     */
    private class BlockMirrorFilter extends PermissionsEnforcingResourceFilter<BlockMirror> {

        /**
         * Constructor.
         * 
         * @param user A reference to a user.
         * @param permissionsHelper A reference to a permissions helper.
         */
        protected BlockMirrorFilter(StorageOSUser user, PermissionsHelper permissionsHelper) {
            super(user, permissionsHelper);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isAccessible(BlockMirror blockMirror) {
            boolean isAccesible = false;
            isAccesible = isTenantAccessible(getTenantOwner(blockMirror.getId()));
            if (!isAccesible) {
                NamedURI project = blockMirror.getProject();
                if (project != null) {
                    isAccesible = isProjectAccessible(project.getURI());
                }
            }
            return isAccesible;
        }
    }
}
