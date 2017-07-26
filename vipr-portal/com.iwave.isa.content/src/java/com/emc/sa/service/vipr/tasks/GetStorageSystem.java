/*
 * Copyright (c) 2012-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.tasks;

import java.net.URI;

import com.emc.storageos.model.systems.StorageSystemRestRep;

public class GetStorageSystem extends ViPRExecutionTask<StorageSystemRestRep> {
    private final URI storageSystem;

    public GetStorageSystem(URI storageSystem) {
        this.storageSystem = storageSystem;
        provideDetailArgs(storageSystem);
    }

    @Override
    public StorageSystemRestRep executeTask() throws Exception {
        return getClient().storageSystems().get(this.storageSystem);
    }
}