import java.util.Map;

import comet.vipr.ViperCaller;

public class clientMain {

    public static void main(String args[]){
        
     try {   
        System.out.println(" called migrate");
        ViperCaller vipr = new ViperCaller();

//        URI hostURI=new URI(args[0]);
//        URI sourceVolumeURI=new URI(args[1]);
//        URI targetVpoolId  = new URI(args[2]);
//
//        boolean result = vipr.doExport(sourceVolumeURI, hostURI, targetVpoolId);
//        
        
//        System.out.println(" volume " +result);
        Map<String,String>vpool = vipr.getBlockVPool();
        System.out.println(" Value is "+ vpool);
       
     }catch(Exception e){
         e.printStackTrace();
     }
     
    }
}

