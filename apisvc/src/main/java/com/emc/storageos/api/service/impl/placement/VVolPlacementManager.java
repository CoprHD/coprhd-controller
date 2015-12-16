package com.emc.storageos.api.service.impl.placement;

import java.io.InputStream;

import com.emc.storageos.db.client.DbClient;

public class VVolPlacementManager {
    
    private DbClient dbClient;

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }
    
    public void placementLogicForVVol(InputStream is){
        VVolPlacementManagerUtil.unmarshall(is);
    }

}
