package com.emc.storageos.api.service.impl.resource.blockingestorchestration.context;

import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;

public interface VolumeIngestionContext {

    public abstract UnManagedVolume getUnmanagedVolume();
    
    public abstract Class<? extends BlockObject> getBlockObjectClass();
    
    public boolean isVolumeExported();
    
    public void commit(); 
    
    public void rollback();
}
