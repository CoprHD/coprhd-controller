package com.emc.storageos.varraygenerators;

import java.net.URI;

import com.emc.storageos.db.client.model.StorageSystem;

public interface VarrayGeneratorInterface {
    public void generateVarraysForStorageSystem(StorageSystem system);

}
