package com.emc.storageos.api.service.impl.resource.vasa2;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.ObjectWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.utils.RESTClientUtil;
import com.emc.storageos.model.pe.CreatePEResponse;
import com.emc.storageos.model.pe.CreateProtocolEndpoint;
import com.emc.storageos.model.pe.CreateStorageGroupParam;
import com.emc.storageos.model.pe.HostOrHostGroupSelection;
import com.emc.storageos.model.pe.PortGroupSelection;
import com.emc.storageos.model.pe.ProtocolEndpointList;
import com.emc.storageos.model.pe.UseExistingHostParam;
import com.emc.storageos.model.pe.UseExistingPortGroupParam;
import com.sun.jersey.api.client.ClientResponse;
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
    
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON } )
    @Path("/protocolendpoint/symmetrix/{symmid}")
    public Response createProtocolEndpoint(@PathParam("symmid") String symmId){
        final String PROTOCOL_ENDPOINT_URI = "/symmetrix/" + symmId + "/protocolendpoint";
//        CreateProtocolEndpoint createProtocolEndpoint = createPayloadForCreatingPE(new CreateProtocolEndpoint());
        String json = createPayloadForCreatingPE(new CreateProtocolEndpoint());
        ClientResponse response = null;
        RESTClientUtil client = RESTClientUtil.getInstance();
        String jsonResponse = null;
        client.set_baseURL(baseURL);
        try {
            client.setLoginCredentials("smc", "smc");
            response = client.queryObjectPostRequest(PROTOCOL_ENDPOINT_URI, CreatePEResponse.class, json);
            jsonResponse = response.getEntity(String.class);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (UniformInterfaceException e) {
            e.printStackTrace();
        }
         return Response.status(response.getStatus()).entity(jsonResponse).build();
    }
    
    private String createPayloadForCreatingPE(CreateProtocolEndpoint createProtocolEndpoint){
        createProtocolEndpoint.setMaskingViewId("test_MV");
        
        HostOrHostGroupSelection hostGrpSelection = new HostOrHostGroupSelection();
        UseExistingHostParam useExistingHostParam = new UseExistingHostParam();
        useExistingHostParam.setHostId("lgly6072_ig");
        hostGrpSelection.setUseExistingHostParam(useExistingHostParam);
        createProtocolEndpoint.setHostOrHostGroupSelection(hostGrpSelection);
        
        CreateStorageGroupParam createStorageGroupParam = new CreateStorageGroupParam();
        createStorageGroupParam.setStorageGroupId("test_SG");
        createProtocolEndpoint.setCreateStorageGroupParam(createStorageGroupParam);
        
        PortGroupSelection portGrpSelection = new PortGroupSelection();
        UseExistingPortGroupParam useExistingPortGroupParam = new UseExistingPortGroupParam();
        useExistingPortGroupParam.setPortGroupId("lgly6072_ig_PG");
        portGrpSelection.setUseExistingPortGroupParam(useExistingPortGroupParam);
        createProtocolEndpoint.setPortGroupSelection(portGrpSelection);
        
        ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
        String json = null;
        try {
            json = ow.writeValueAsString(createProtocolEndpoint);
            _log.info("####################################################");
            _log.info(json);
        } catch (JsonGenerationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (JsonMappingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
 //       return createProtocolEndpoint;
        return json;
    }
    

}
