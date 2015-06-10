/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.tasks;

import java.net.URI;

import com.emc.storageos.model.systems.StorageSystemRestRep;

public class CheckStorageSystemDiscoveryStatus extends ViPRExecutionTask<Void> {
    
    private final URI storageSystemId;
    
    public CheckStorageSystemDiscoveryStatus(URI storageSystemId) {
        this.storageSystemId = storageSystemId;
        provideDetailArgs(storageSystemId);
    }
    
    @Override
    public void execute() throws Exception {
        StorageSystemRestRep system = getClient().storageSystems().get(storageSystemId);
        String discoveryStatus = system.getDiscoveryJobStatus();
        if (discoveryStatus.equalsIgnoreCase("ERROR")) {
            logWarn("check.storage.system.discovery.failed.state", system.getName(), storageSystemId);
        }
    }

}
