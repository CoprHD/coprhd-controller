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
//        String host="urn:storageos:Host:b9e30eed-4fe0-440f-8f28-e701443707bb:vdc1";
        String sourceVolume="urn:storageos:Volume:008b7951-3afb-40c3-a84e-a26c2518d503:vdc1";
//        String targetVolume="urn:storageos:Volume:c9e75c0f-d54c-48f0-b58e-98ca93207da6:vdc1";
        String tragetVPool="urn:storageos:VirtualPool:c4a52dd2-80fb-4ee3-aa0e-9752d373e956:vdc1";
        
        String host="urn:storageos:Host:b9e30eed-4fe0-440f-8f28-e701443707bb:vdc1";
        String targetVolume="urn:storageos:Volume:84c08922-1205-404b-bb52-0eb3cd2133d4:vdc1";
        
        URI hostURI=new URI(host);
        URI sourceVolumeURI=new URI(sourceVolume);
        URI targetVolumeURI=new URI(targetVolume);
        URI targetVpoolId  = new URI(tragetVPool);
//       vipr.migrate(hostURI, sourceVolumeURI, targetVolumeURI);
//        Tasks<VolumeRestRep> volumeRestRep=vipr.createVolume(sourceVolumeURI, targetVpoolId);
//        List<VolumeRestRep> restRep = volumeRestRep.get(); 
//        URI volume= ((VolumeRestRep)restRep.get(0)).getId();
//        vipr.exportVolumeURI(hostURI,volume);
//        Tasks<VolumeRestRep> volumeRestRep=vipr.createVolume(sourceVolumeURI, targetVpoolId);
//        List<VolumeRestRep> volumeList= volumeRestRep.get();
        
//        boolean result = vipr.exportVolumeURI(hostURI, targetVolumeURI);
        boolean result = vipr.doExport(sourceVolumeURI, hostURI, targetVpoolId);
        
        System.out.println(" volume " +result);
       
     }catch(Exception e){
         e.printStackTrace();
     }
     
    }
}
