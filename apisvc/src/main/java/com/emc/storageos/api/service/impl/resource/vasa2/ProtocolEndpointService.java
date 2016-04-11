package com.emc.storageos.api.service.impl.resource.vasa2;

import java.security.NoSuchAlgorithmException;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.utils.RESTClientUtil;
import com.emc.storageos.cinder.model.ProtocolEndpointList;
import com.sun.jersey.api.client.UniformInterfaceException;

@Path("/vasa/vvol")
public class ProtocolEndpointService {
    
    private static final Logger _log = LoggerFactory.getLogger(ProtocolEndpointService.class);
    
    private String baseURL = "https://lgly7066.lss.emc.com:8443/univmax/restapi/81/vvol";
    
    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON } )
    @Path("/protocolendpoint/symmetrix/{symmid}")
    public ProtocolEndpointList getProtocolEndpointList(@PathParam("symmid") String symmId){
        
        final String PROTOCOL_ENDPOINT_URI = "/symmetrix/" + symmId + "/protocolendpoint";
        ProtocolEndpointList list = null;
        RESTClientUtil client = RESTClientUtil.getInstance();
        client.set_baseURL(baseURL);
        try {
            client.setLoginCredentials("smc", "smc");
            list = client.queryObject(PROTOCOL_ENDPOINT_URI, ProtocolEndpointList.class);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (UniformInterfaceException e) {
            e.printStackTrace();
        }
        
        return list;
        
    }
    

}
