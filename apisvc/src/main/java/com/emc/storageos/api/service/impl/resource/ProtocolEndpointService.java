package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.VasaObjectMapper.toProtocolEndpoint;

import java.net.URI;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.ProtocolEndpoint;
import com.emc.storageos.model.vasa.ProtocolEndpointBulkResponse;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;

@Path("/vasa/protocolendpoint")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        readAcls = { ACL.USE },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class ProtocolEndpointService extends AbstractVasaService{

    private static final Logger _log = LoggerFactory.getLogger(ProtocolEndpointService.class);
    
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public ProtocolEndpointBulkResponse getProtocolEndpoints(){
        _log.info("@@@@@@@@ Getting protocol endpoints @@@@@@@@@@@");
        List<URI> protocolEndpointUris = _dbClient.queryByType(ProtocolEndpoint.class, true);
        List<ProtocolEndpoint> protocolEndpoints = _dbClient.queryObject(ProtocolEndpoint.class, protocolEndpointUris);
        ProtocolEndpointBulkResponse protocolEndpointBulkResponse = new ProtocolEndpointBulkResponse();
        if(null != protocolEndpoints){
            for(ProtocolEndpoint protocolEndpoint : protocolEndpoints){
                if(protocolEndpoint != null){
                    protocolEndpointBulkResponse.getProtocolEndpointResponseParam().add(toProtocolEndpoint(protocolEndpoint));
                }
            }
        }
        return protocolEndpointBulkResponse;
    }
}
