package comet;

import javax.security.auth.login.Configuration;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerResponse;
import com.sun.jersey.spi.container.ContainerResponseFilter;

public class CorsSupportFilter implements ContainerResponseFilter {

    @Override
    public ContainerResponse filter(ContainerRequest req, ContainerResponse contResp) {

        try { 
       /* System.out.println("######################" + contResp.getEntity().toString());
        
        ResponseBuilder resp = Response.fromResponse(contResp.getResponse());
        
        resp.header("Access-Control-Allow-Origin", "*")
            .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
            .header("Access-Control-Allow-Headers", "x-requested-with, Content-Type, origin, authorization, accept, client-security-token");

        String reqHead = req.getHeaderValue("Access-Control-Request-Headers");

        if(null != reqHead && !reqHead.equals(null)){
            resp.header("Access-Control-Allow-Headers", reqHead);
        }

        contResp.setResponse(resp.build());*/
            
            
            
            contResp.getHttpHeaders().putSingle("Access-Control-Allow-Origin", "*");
            contResp.getHttpHeaders().putSingle("Access-Control-Allow-Credentials", "true");
            contResp.getHttpHeaders().putSingle("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT, OPTIONS, HEAD");
            contResp.getHttpHeaders().putSingle("Access-Control-Allow-Headers", "Content-Type, Accept, X-Requested-With");
        } catch (Exception ex) {
            System.out.println("&&&&&&&&&&&&&&&&&&&&&&& " + ex.getMessage());
        }
        return contResp;
    }

}