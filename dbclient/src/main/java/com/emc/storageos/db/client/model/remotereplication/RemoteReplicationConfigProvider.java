/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.remotereplication;


import com.emc.storageos.db.client.model.AlternateId;
import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import com.emc.storageos.db.client.model.Name;

/**
 * Container to manage discovery cycles for remote replication configuration
 */
@Cf("RemoteReplicationConfigProvider")
public class RemoteReplicationConfigProvider extends DiscoveredSystemObject {

    // uri of the StorageSystemType for this provider
    private String storageSystemType;

    @Name("storageSystemType")
    @AlternateId("AltIdIndex")
    public String getStorageSystemType() {
        return storageSystemType;
    }

    public void setStorageSystemType(String storageSystemType) {
        this.storageSystemType = storageSystemType;
        setChanged("storageSystemType");
    }
}