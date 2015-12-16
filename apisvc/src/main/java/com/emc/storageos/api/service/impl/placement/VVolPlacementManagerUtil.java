package com.emc.storageos.api.service.impl.placement;

import java.io.InputStream;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.vasa.CreateVirtualVolume;

public class VVolPlacementManagerUtil {
    
    private static final Logger _log = LoggerFactory.getLogger(VVolPlacementManagerUtil.class);
    
    public void marshall(){
        
    }
    
    public static void unmarshall(InputStream is){
        
        try {
            
            JAXBContext jc = JAXBContext.newInstance(CreateVirtualVolume.class);
            Unmarshaller um = jc.createUnmarshaller();
            CreateVirtualVolume createVirtualVol = (CreateVirtualVolume)um.unmarshal(is);
            _log.info("####### Virtual Volume Create Request Information ###########");
            _log.info("ContainerId : " + createVirtualVol.getContainerId());
            _log.info("VVolType : " + createVirtualVol.getVvolType());
            _log.info("Size " + createVirtualVol.getSizeInMB());
            
        } catch(Exception e){
            e.printStackTrace();
        }
    }

}
