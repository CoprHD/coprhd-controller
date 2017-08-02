package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.BlockMapper.map;

import java.net.URI;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.block.BlockMirrorRestRep;
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

}
