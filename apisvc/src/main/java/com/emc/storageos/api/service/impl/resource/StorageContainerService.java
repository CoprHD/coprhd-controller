package com.emc.storageos.api.service.impl.resource;

import java.net.URI;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.VirtualPool.Type;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.vpool.BlockVirtualPoolParam;
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;

@Path("/stroagecontainer")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
readAcls = { ACL.USE },
writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class StorageContainerService extends VirtualPoolService {
	
	private static final Logger _log = LoggerFactory.getLogger(StorageContainerService.class);

	/**
	 * Creates Storage Container
	 * @param param
	 * @return
	 * @throws DatabaseException
	 */
	@POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
	public BlockVirtualPoolRestRep createStorageContainer(
			BlockVirtualPoolParam param) throws DatabaseException {
		return null;

	}
	
	@Override
	protected Type getVirtualPoolType() {		
		return null;
	}

	@Override
	protected URI getTenantOwner(URI id) {
		return null;
	}

}
