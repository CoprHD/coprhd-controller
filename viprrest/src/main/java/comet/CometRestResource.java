package comet;

import java.net.URI;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Tasks;

import comet.vipr.ViperCaller;

/**
 * Root resource (exposed at "myresource" path)
 */
@Path("cometmigrate")
public class CometRestResource {

    /**
     * Method handling HTTP GET requests. The returned object will be sent
     * to the client as "text/plain" media type.
     *
     * @return String that will be returned as a text/plain response.
     */
    
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getIt() {
        return "Got it!";
    }
    
    @GET
    @Path("hosts")
    @Produces(MediaType.TEXT_XML)
    public List<URI> getHosts() {
        ViperCaller vipr = new ViperCaller();
        
       return vipr.getHosts();
        
    }
    
    @GET
    @Path("migrate")
    @Produces(MediaType.TEXT_XML)
    public Tasks<VolumeRestRep> migrateVolume() throws Exception {
        ViperCaller vipr = new ViperCaller();
        URI hostURI=new URI("");
        URI sourceVolumeURI=new URI("");
        URI targetVolumeURI=new URI("");
        
       return vipr.migrate(hostURI, sourceVolumeURI, targetVolumeURI);
        
    }
    
}
