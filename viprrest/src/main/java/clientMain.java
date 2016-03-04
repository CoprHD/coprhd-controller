import java.net.URI;
import java.util.List;

import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Tasks;

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
        
       
     }catch(Exception e){
         e.printStackTrace();
     }
     
    }
}

