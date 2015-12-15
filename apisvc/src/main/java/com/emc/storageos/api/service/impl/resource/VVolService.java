package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.VasaObjectMapper.toVVol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.VVol;
import com.emc.storageos.model.vasa.VVolBulkResponse;
import com.emc.storageos.model.vasa.VVolCreateRequestParam;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;


@Path("/vasa/vvol")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        readAcls = { ACL.USE },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class VVolService extends AbstractVasaService{

    private static final Logger _log = LoggerFactory.getLogger(VVolService.class);
    
    @POST
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public Response createVVol(InputStream input){
        
        String result = getStringFromInputStream(input);
        
        _log.info("************ SOAP Request : " + result +  " **********************");
        
        return Response.status(201).build();
        
    }
    
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public VVolBulkResponse getVVols(){
        List<URI> vVolUris = _dbClient.queryByType(VVol.class, true);
        List<VVol> vVols = _dbClient.queryObject(VVol.class, vVolUris);
        VVolBulkResponse vVolBulkResponse = new VVolBulkResponse();
        if(null != vVols){
            for(VVol vVol : vVols){
                if(null != vVol){
                    vVolBulkResponse.getvVols().add(toVVol(vVol));
                }
            }
        }
        return vVolBulkResponse;
        
    }
    
    private static String getStringFromInputStream(InputStream is) {

        BufferedReader br = null;
        StringBuilder sb = new StringBuilder();

        String line;
        try {

            br = new BufferedReader(new InputStreamReader(is));
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return sb.toString();

    }
}
