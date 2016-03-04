package comet;

import java.net.URI;
import java.util.List;
import java.util.Map;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import org.glassfish.jersey.process.internal.RequestScoped;

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
    @Produces(MediaType.TEXT_PLAIN)
    public String getHosts() {
        
        System.out.println(" Value got");
        ViperCaller vipr = new ViperCaller();
        System.out.println(" Client got");
//        List<String> output = vipr.getHosts();
        Map<String,String> output = vipr.getHostsMap();
        System.out.println(" HOSTs got"+output);
        Response response = Response.status(200).entity(output).build();
        System.out.println(" response formed "+response);
        System.out.println(output);
        
       return output.toString();
    }
    
    @GET
    @Path("volumes")
    @Produces(MediaType.TEXT_PLAIN)
    public String getVolumes() {
        
        System.out.println(" Value got");
        ViperCaller vipr = new ViperCaller();
        System.out.println(" Client got");
        List<URI> output = vipr.getVolumes();
//        Map<String,URI> output= vipr.getVolumeMap();
        System.out.println(" Volumes got"+output);
        System.out.println(output);
        
       return output.toString();
       
    }
    
    @GET
    @Path("blockvpools")
    @Produces(MediaType.TEXT_PLAIN)
    public String getVpools() {
        
        System.out.println(" Value got");
        ViperCaller vipr = new ViperCaller();
        System.out.println(" Client got");
        Map<String,String> output = vipr.getBlockVPool();
//        List<URI> output = vipr.getVolumes();
//        Map<String,URI> output= vipr.getVolumeMap();
        System.out.println(" vPools got"+output);
        
       return output.toString();
       
    }
    
    
    
    @GET
    @Path("migrate")
    @Produces(MediaType.APPLICATION_XML)
    public Tasks<VolumeRestRep> migrateVolume() throws Exception {
        
        System.out.println(" called migrate");
        ViperCaller vipr = new ViperCaller();
        String host="urn:storageos:Host:b9e30eed-4fe0-440f-8f28-e701443707bb:vdc1";
        String sourceVolume="urn:storageos:Volume:008b7951-3afb-40c3-a84e-a26c2518d503:vdc1";
        String targetVolume="urn:storageos:Volume:c9e75c0f-d54c-48f0-b58e-98ca93207da6:vdc1";
        URI hostURI=new URI(host);
        URI sourceVolumeURI=new URI(sourceVolume);
        URI targetVolumeURI=new URI(targetVolume);
        
        Tasks<VolumeRestRep> volumeTasks = vipr.migrate(hostURI, sourceVolumeURI, targetVolumeURI);
        VolumeRestRep volume = volumeTasks.get().get(0);
        Response response = Response.status(200).entity(volume).build();
        
       return vipr.migrate(hostURI, sourceVolumeURI, targetVolumeURI);
        
    }
    
    @GET
    @Path("migrateTest")
    @Produces(MediaType.TEXT_PLAIN)
    @RequestScoped
    public String  test(@DefaultValue("All")  @QueryParam(value="host") String host,@QueryParam(value="sourceVolume") String sourceVolume,@QueryParam(value="targetVolume") String targetVolume) throws Exception {
        
        System.out.println(" called migrate");
        ViperCaller vipr = new ViperCaller();
//        String host="urn:storageos:Host:b9e30eed-4fe0-440f-8f28-e701443707bb:vdc1";
//        String sourceVolume="urn:storageos:Volume:008b7951-3afb-40c3-a84e-a26c2518d503:vdc1";
//        String targetVolume="urn:storageos:Volume:c9e75c0f-d54c-48f0-b58e-98ca93207da6:vdc1";
//        URI hostURI=new URI(host);
//        URI sourceVolumeURI=new URI(sourceVolume);
//        URI targetVolumeURI=new URI(targetVolume);
//        
//        Tasks<VolumeRestRep> volumeTasks = vipr.migrate(hostURI, sourceVolumeURI, targetVolumeURI);
//        VolumeRestRep volume = volumeTasks.get().get(0);
//        Response response = Response.status(200).entity(volume).build();
//        
//       return vipr.migrate(hostURI, sourceVolumeURI, targetVolumeURI);
//
        System.out.println( "Host " +host + " sourceVolume "+sourceVolume+ " targetVolume"+targetVolume);
        return "migrated";
    }
    
    @GET
    @Path("migrateVolume")
    @Produces(MediaType.TEXT_PLAIN)
    public String  migrateVolume(@DefaultValue("All")  @QueryParam(value="host") String host,@QueryParam(value="sourceVolume") String sourceVolume,@QueryParam(value="targetVPool") String targetVpoolId) throws Exception {
        
        System.out.println(" called migrate");
        ViperCaller vipr = new ViperCaller();
        URI hostURI=new URI(host.trim());
        URI sourceVolumeURI=new URI(sourceVolume.trim());
        URI targetVpool=new URI(targetVpoolId.trim());
        
        
        boolean result = vipr.doExport(sourceVolumeURI, hostURI, targetVpool);

        System.out.println( "Host " +host + " sourceVolume "+sourceVolume+ " targetVolume"+targetVpoolId);
        return "new Volume Created "+ result;
        
    }
    
    
    
}
