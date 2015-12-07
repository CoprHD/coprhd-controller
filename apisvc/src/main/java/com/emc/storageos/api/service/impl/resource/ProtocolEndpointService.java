package com.emc.storageos.api.service.impl.resource;

import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;

@Path("/vasa/protocolendpoint")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        readAcls = { ACL.USE },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class ProtocolEndpointService {

    private static final Logger _log = LoggerFactory.getLogger(ProtocolEndpointService.class);
    
    public Response getProtocolEndpoints(){
        _log.info("@@@@@@@@ Getting protocol endpoints @@@@@@@@@@@");
        return Response.status(200).build();
    }
}
