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
@Path("powermigrate")
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
        String host="urn:storageos:Host:b9e30eed-4fe0-440f-8f28-e701443707bb:vdc1";
        String sourceVolume="urn:storageos:Volume:008b7951-3afb-40c3-a84e-a26c2518d503:vdc1";
        String targetVolume="urn:storageos:Volume:c9e75c0f-d54c-48f0-b58e-98ca93207da6:vdc1";
        URI hostURI=new URI(host);
        URI sourceVolumeURI=new URI(sourceVolume);
        URI targetVolumeURI=new URI(targetVolume);
        
       return vipr.migrate(hostURI, sourceVolumeURI, targetVolumeURI);
        
    }
    
}
