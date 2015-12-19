package com.emc.storageos.api.service.impl.placement;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
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
        
        createVirtualVol.setContainerId("05786556e3-ebf0-4480-b94c-457080dc9fg6");
        
        InputStream iss = VVolPlacementManagerUtil.marshall(createVirtualVol);
        
        _log.info(getStringFromInputStream(iss));
        
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
