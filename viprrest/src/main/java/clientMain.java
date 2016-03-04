import java.net.URI;
import java.util.Map;

import comet.vipr.ViperCaller;

public class clientMain {

    public static void main(String args[]){
        
     try {   
        System.out.println(" called migrate");
        ViperCaller vipr = new ViperCaller();

        URI hostURI=new URI(args[0]);
        URI sourceVolumeURI=new URI(args[1]);
        URI targetVpoolId  = new URI(args[2]);

        boolean result = vipr.doExport(sourceVolumeURI, hostURI, targetVpoolId);
        
        
        System.out.println(" volume " +result);
        Map<String,String>vpool = vipr.getBlockVPool();
        System.out.println(" Value is "+ vpool);
        
        
        /*URI host= new URI("urn:storageos:Host:b9e30eed-4fe0-440f-8f28-e701443707bb:vdc1");
        UR ("urn:storageos:Volume:4d25cf9b-6043-4400-af12-ea71e73bf17a:vdc1");
        "urn:storageos:VirtualPool:c4a52dd2-80fb-4ee3-aa0e-9752d373e956:vdc1"*/

       
     }catch(Exception e){
         e.printStackTrace();
     }
     
    }
}

