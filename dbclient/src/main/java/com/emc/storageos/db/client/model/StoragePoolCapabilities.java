/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

@Cf("StoragePoolCapabilities")
public class StoragePoolCapabilities extends DiscoveredDataObject {
    private URI _storageSystem;

    private URI _storagePool;
    /**
     * StoragePoolSetting unique identifier.
     */
    private String _poolcapabilitiesID;

    public void setStorageSystem(URI storageSystem) {
        _storageSystem = storageSystem;
        setChanged("storageSystem");
    }

    @RelationIndex(cf = "RelationIndex", type = StorageSystem.class)
    @Name("storageSystem")
    public URI getStorageSystem() {
        return _storageSystem;
    }

    public void setStoragePool(URI storagePool) {
        _storagePool = storagePool;
        setChanged("storagePool");
    }

    @RelationIndex(cf = "RelationIndex", type = StoragePool.class)
    @Name("storagePool")
    public URI getStoragePool() {
        return _storagePool;
    }

    public void setPoolCapabilitiesID(String poolcapabilitiesID) {
        _poolcapabilitiesID = poolcapabilitiesID;
        setChanged("poolcapabilitiesID");
    }

    @AlternateId("AltIdIndex")
    @Name("poolcapabilitiesID")
    public String getPoolCapabilitiesID() {
        return _poolcapabilitiesID;
    }
}
