/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.tasks;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.systems.StorageSystemRestRep;

public class GetStorageSystems extends ViPRExecutionTask<List<StorageSystemRestRep>>{
    private List<URI> storageSystems;
    
    public GetStorageSystems(List<URI> storageSystems) {
        this.storageSystems = storageSystems;
        provideDetailArgs(storageSystems);
    }

    @Override
    public List<StorageSystemRestRep> executeTask() throws Exception {
        return getClient().storageSystems().getByIds(this.storageSystems);
    }

}
