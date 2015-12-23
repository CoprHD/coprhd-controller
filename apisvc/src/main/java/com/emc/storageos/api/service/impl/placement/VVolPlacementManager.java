package com.emc.storageos.api.service.impl.placement;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageContainer;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.vasa.CreateVirtualVolume;

public class VVolPlacementManager {
    
    private static final Logger _log = LoggerFactory.getLogger(VVolPlacementManager.class);
    
    private DbClient dbClient;

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }
    
    public void placementLogicForVVol(InputStream is){
        CreateVirtualVolume createVirtualVol = VVolPlacementManagerUtil.unmarshall(is);
        
        String nativeContainerId = getNativeContainerId(createVirtualVol.getContainerId(), createVirtualVol.getSizeInMB());
        if(nativeContainerId != null){
            createVirtualVol.setContainerId(nativeContainerId);
        }else{
            // throw APIBadrequest exception
        }
        InputStream iss = VVolPlacementManagerUtil.marshall(createVirtualVol);
        
        _log.info(getStringFromInputStream(iss));
        
    }
    
    private String getNativeContainerId(String containerId, long size) {
        String nativeContainerId = null;
        URI containerIdUri = URI.create(containerId);
        StorageContainer storageContainer = dbClient.queryObject(StorageContainer.class, containerIdUri);
        Set<String> physicalStorageContainers = storageContainer.getPhysicalStorageContainers();
        if(physicalStorageContainers != null){
            for(String physicalStorageContainer : physicalStorageContainers){
                URI physicalStorageContainerURI = URI.create(physicalStorageContainer);
                StorageContainer physicalStorageContainerObj = dbClient.queryObject(StorageContainer.class, physicalStorageContainerURI);
                if(physicalStorageContainerObj != null && physicalStorageContainerObj.getMaxVvolSizeMB() > size){
                    nativeContainerId = physicalStorageContainerObj.getId().toString();
                    break;
                }
            }
        }
        return nativeContainerId;
    }

    private static String getStringFromInputStream(InputStream is) {

        BufferedReader br = null;
        StringBuilder sb = new StringBuilder();

        String line;
        try {

            br = new BufferedReader(new InputStreamReader(is));
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return sb.toString();

    }

}
