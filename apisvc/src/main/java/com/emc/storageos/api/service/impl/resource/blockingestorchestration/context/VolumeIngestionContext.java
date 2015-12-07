/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration.context;

import java.util.List;

import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;

public interface VolumeIngestionContext {

    public UnManagedVolume getUnmanagedVolume();
    
    public boolean isVolumeExported();
    
    public void commit(); 
    
    public void rollback();
    
    public List<String> getErrorMessages();
}
